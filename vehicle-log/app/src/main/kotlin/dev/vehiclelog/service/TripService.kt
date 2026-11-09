package dev.vehiclelog.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.TripStore
import dev.vehiclelog.signal.PleosSignals
import dev.vehiclelog.signal.ReplaySignals
import dev.vehiclelog.signal.TripPoint
import dev.vehiclelog.signal.VehicleSignals
import dev.vehiclelog.trip.Gear
import dev.vehiclelog.trip.TireCheck
import dev.vehiclelog.trip.TripDetector
import dev.vehiclelog.trip.TripEvent
import dev.vehiclelog.ui.TripEndActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 화면 없이 돌며 기어 변화로 운행을 감지해 저장한다.
 * 다른 앱이 앞에 떠 있어도 동작해야 하므로 포그라운드 서비스로 둔다.
 */
class TripService : Service() {

    private lateinit var store: TripStore
    private lateinit var signals: VehicleSignals
    private lateinit var detector: TripDetector
    private lateinit var announcer: Announcer
    private val mainHandler by lazy { Handler(mainLooper) }
    private val io: ExecutorService = Executors.newSingleThreadExecutor()

    // 열린 운행 id. io 스레드에서만 읽고 쓴다 (시작·종료 기록이 같은 순서로 직렬 실행되게)
    private var openTripId: Long? = null
    private var started = false

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        store = TripStore.get(this)
        announcer = Announcer(this).also { it.start() }
        signals = if (Settings.replayMode(this)) ReplaySignals.fromAsset(this) else PleosSignals(this)
        AppLog.info("service.created", "signals" to signals.name)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (started) return START_STICKY
        started = true
        // 부팅 전 끝나지 않은 운행을 이어 받은 뒤에 신호를 듣는다
        io.execute {
            val open = store.openTrip()
            openTripId = open?.id
            mainHandler.post {
                detector = TripDetector(tripOpen = open != null)
                AppLog.info("service.recovered", "openTripId" to open?.id)
                signals.start(::onGear)
            }
        }
        return START_STICKY
    }

    private fun onGear(gear: Gear) {
        when (detector.onGear(gear)) {
            TripEvent.STARTED -> signals.readPoint { point ->
                signals.onTripStarted()
                io.execute { recordStart(point) }
                checkTires()
            }
            // TODO: 실차에서 시작 조회가 종료 조회보다 늦게 끝나면 순서가 뒤집힐 수 있다 (조회 제한 시간 3초 안의 짧은 운행)
            TripEvent.ENDED -> signals.readPoint { point -> io.execute { recordEnd(point) } }
            null -> Unit
        }
    }

    private fun recordStart(point: TripPoint) {
        val id = store.startTrip(point)
        openTripId = id
        AppLog.info("trip.started", "id" to id, "odometerKm" to point.odometerKm)
    }

    /** 출발할 때 네 바퀴 공기압을 비교해 이상이 있으면 음성으로 알린다. */
    private fun checkTires() {
        signals.readTires { reading ->
            val result = TireCheck.evaluate(reading)
            val summary = TireCheck.summary(result)
            TireStatus.save(this, summary, System.currentTimeMillis())
            AppLog.info("tire.checked", "measured" to result.measured, "low" to result.low, "uneven" to result.uneven)
            TireCheck.message(result)?.let(announcer::speak)
        }
    }

    private fun recordEnd(point: TripPoint) {
        val id = openTripId ?: run {
            AppLog.warn("trip.end.noOpenTrip")
            return
        }
        openTripId = null
        store.endTrip(id, point)
        AppLog.info("trip.ended", "id" to id, "odometerKm" to point.odometerKm, "hasPlace" to (point.placeName != null))
        mainHandler.post {
            announcer.speak(TRIP_END_SPEECH)
            showTripEnd(id)
        }
    }

    /**
     * 운행 종료 화면을 띄운다. 앱 화면이 떠 있으면 바로 열리고,
     * 백그라운드에서는 시스템이 화면 띄우기를 막으므로 전체 화면 알림이 대신한다.
     */
    private fun showTripEnd(tripId: Long) {
        val intent = TripEndActivity.intent(this, tripId)
        val pending = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, END_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("운행이 끝났습니다")
            .setContentText("사용자와 업무용 여부를 선택해 주세요")
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(TRIP_END_NOTIFICATION_ID, notification)
        runCatching { startActivity(intent) }
            .onFailure { AppLog.warn("trip.end.screen.blocked", "error" to it) }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "운행 감지", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(END_CHANNEL_ID, "운행 종료", NotificationManager.IMPORTANCE_HIGH))
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("운행 감지 중")
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        signals.stop()
        announcer.release()
        mainHandler.removeCallbacksAndMessages(null)
        io.shutdown()
        // store 는 화면과 함께 쓰는 공유 인스턴스라 닫지 않는다
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "trip_watch"
        private const val END_CHANNEL_ID = "trip_end"
        private const val NOTIFICATION_ID = 1
        const val TRIP_END_NOTIFICATION_ID = 2
        private const val TRIP_END_SPEECH = "운행이 끝났습니다. 사용자와 업무용 여부를 확인해 주세요."

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TripService::class.java))
        }
    }
}
