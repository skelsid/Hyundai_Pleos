package dev.vehiclelog.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import dev.vehiclelog.trip.TripDetector
import dev.vehiclelog.trip.TripEvent
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
    private val io: ExecutorService = Executors.newSingleThreadExecutor()

    // 열린 운행 id. io 스레드에서만 읽고 쓴다 (시작·종료 기록이 같은 순서로 직렬 실행되게)
    private var openTripId: Long? = null
    private var started = false

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        store = TripStore(this)
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
            Handler(mainLooper).post {
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

    private fun recordEnd(point: TripPoint) {
        val id = openTripId ?: run {
            AppLog.warn("trip.end.noOpenTrip")
            return
        }
        openTripId = null
        store.endTrip(id, point)
        AppLog.info("trip.ended", "id" to id, "odometerKm" to point.odometerKm, "place" to point.placeName)
        // TODO: 운행 종료 화면(사용자·업무용 여부 선택)과 음성 안내를 띄운다
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(CHANNEL_ID, "운행 감지", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("운행 감지 중")
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        signals.stop()
        io.shutdown()
        store.close()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "trip_watch"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TripService::class.java))
        }
    }
}
