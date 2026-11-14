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
import dev.vehiclelog.trip.DepartureCheck
import dev.vehiclelog.trip.TireCheck
import dev.vehiclelog.trip.WarningCheck
import dev.vehiclelog.trip.TripDetector
import dev.vehiclelog.trip.SerialQueue
import dev.vehiclelog.trip.TripEvent
import dev.vehiclelog.trip.TripMath
import dev.vehiclelog.ui.TripEndActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

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

    // onDestroy 뒤 늦게 도착한 차량 값·io 결과를 버리려고 둔다 (메인 스레드에서만 읽고 쓴다)
    private var destroyed = false

    // 차량 값 조회는 최대 3초 걸린다. 짧은 운행에서 시작·종료 조회가 겹치면 끝나는 순서가 뒤집혀
    // 운행이 닫히지 않고 남을 수 있으므로, 조회를 한 번에 하나씩 기어 변화 순서대로 한다 (메인 스레드)
    private val reads = SerialQueue<Pair<TripEvent, Long>> { (event, at), done -> readAndRecord(event, at, done) }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        store = TripStore.get(this)
        announcer = Announcer(this).also { it.start() }
        signals = if (Settings.replayMode(this)) ReplaySignals.fromAsset(this) else PleosSignals(this)
        AppLog.info("service.created", "signals" to signals.name)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 시스템이 다시 띄웠는데 그 사이 동의를 철회했으면 기록하지 않고 멈춘다
        if (!Consent.agreed(this)) {
            AppLog.info("service.stopped", "reason" to "noConsent")
            stopSelf()
            return START_NOT_STICKY
        }
        if (started) return START_STICKY
        started = true
        // 부팅 전 끝나지 않은 운행을 이어 받은 뒤에 신호를 듣는다
        runIo {
            val open = store.openTrip()
            openTripId = open?.id
            mainHandler.post {
                if (destroyed) return@post
                detector = TripDetector(tripOpen = open != null)
                AppLog.info("service.recovered", "openTripId" to open?.id)
                signals.start(::onGear)
            }
        }
        return START_STICKY
    }

    private fun onGear(gear: Gear) {
        val event = detector.onGear(gear) ?: return
        // 시각은 기어가 바뀐 순간으로 남긴다 (조회가 줄을 서서 늦게 끝나도)
        reads.add(event to System.currentTimeMillis())
    }

    private fun readAndRecord(event: TripEvent, at: Long, done: () -> Unit) {
        signals.readPoint { raw ->
            val point = raw.copy(timeMillis = at)
            when (event) {
                TripEvent.STARTED -> {
                    signals.onTripStarted()
                    runIo { recordStart(point) }
                    checkDeparture(point)
                }
                TripEvent.ENDED -> runIo { recordEnd(point) }
            }
            done()
        }
    }

    /**
     * io 스레드에 맡긴다. 서비스가 끝난 뒤 늦게 온 차량 값 콜백(최대 3초)은 버린다 —
     * 끝난 실행기에 넣으면 RejectedExecutionException 으로 앱이 죽는다.
     */
    private fun runIo(task: () -> Unit) {
        try {
            io.execute(task)
        } catch (e: RejectedExecutionException) {
            AppLog.warn("service.io.rejected", "error" to e)
        }
    }

    private fun recordStart(point: TripPoint) {
        // 철회와 겹친 경우: 동의가 없으면 기록하지 않는다
        if (!Consent.agreed(this)) return
        val id = store.startTrip(point)
        openTripId = id
        AppLog.info("trip.started", "id" to id, "odometerKm" to point.odometerKm)
    }

    /**
     * 출발 점검: 네 바퀴 공기압, 엔진오일·브레이크액·변속기오일, 연료(배터리) 잔량, 계기판 경고등(정비·안전 관련만).
     * 이상이 있으면 한 번에 이어서 말한다 (안내가 여러 번 끊겨 나오지 않게).
     */
    private fun checkDeparture(point: TripPoint) {
        signals.readTires { reading ->
            if (destroyed) return@readTires
            val tires = TireCheck.evaluate(reading)
            val now = System.currentTimeMillis()
            TireStatus.save(this, TireCheck.summary(tires), now)
            AppLog.info("tire.checked", "measured" to tires.measured, "low" to tires.low, "uneven" to tires.uneven)
            signals.readFluids { fluids ->
                if (destroyed) return@readFluids
                val departure = DepartureCheck.evaluate(fluids, point.energy)
                TireStatus.saveDeparture(this, DepartureCheck.summary(departure))
                AppLog.info(
                    "departure.checked",
                    "fluids" to departure.measuredFluids, "low" to departure.lowFluids, "critical" to departure.criticalFluids,
                    "energy" to point.energy?.percent,
                )
                signals.readWarnings { active ->
                    if (destroyed) return@readWarnings
                    val labels = active?.let(WarningCheck::important)
                    WarningStatus.save(this, WarningCheck.summary(labels), System.currentTimeMillis())
                    AppLog.info("warning.checked", "received" to (active != null), "active" to active?.size, "important" to labels)
                    val tireMessage = TireCheck.message(tires)
                    val parts = listOfNotNull(tireMessage, DepartureCheck.message(departure), labels?.let(WarningCheck::message))
                    if (parts.isNotEmpty()) {
                        // 타이어 문구는 "타이어를 확인해 주세요." 로 시작한다. 그것만 없으면 앞에 출발 점검임을 밝힌다
                        val lead = if (tireMessage == null) "출발 점검 결과입니다. " else ""
                        announcer.speak(lead + parts.joinToString(" "))
                    }
                }
            }
        }
    }

    private fun recordEnd(point: TripPoint) {
        if (!Consent.agreed(this)) return
        val id = openTripId ?: run {
            AppLog.warn("trip.end.noOpenTrip")
            return
        }
        openTripId = null
        val start = store.trip(id)
        val km = start?.let { TripMath.tripDistanceKm(it.startOdometerKm, point.odometerKm, it.startTripMeterKm, point.tripMeterKm) }
        if (start != null && TripMath.isTrivialKm(km, point.timeMillis - start.startTime)) {
            // 주차 자리 옮기기 같은 짧은 움직임은 묻지 않고 버린다
            store.deleteTrip(id)
            AppLog.info("trip.discarded", "id" to id, "durationMs" to (point.timeMillis - start.startTime))
            return
        }
        store.endTrip(id, point)
        AppLog.info("trip.ended", "id" to id, "odometerKm" to point.odometerKm, "hasPlace" to (point.placeName != null))
        mainHandler.post {
            if (destroyed) return@post
            announcer.speak(TRIP_END_SPEECH)
            showTripEnd(id)
        }
    }

    /**
     * 운행 종료 화면을 띄운다. 앱 화면이 떠 있거나 "다른 앱 위에 표시"가 허용돼 있으면 바로 열린다.
     * 그 밖에는 시스템이 뒤에서 화면 띄우기를 막으므로 전체 화면 알림이 대신한다 (알림 허용 필요).
     * 화면이 열리면 TripEndActivity 가 알림을 지운다.
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
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .setAutoCancel(true)
            .build()
        AppLog.info(
            "trip.end.screen",
            "overlay" to ScreenAccess.canDrawOverApps(this),
            "notifications" to ScreenAccess.notificationsAllowed(this),
            "fullScreen" to ScreenAccess.fullScreenAllowed(this),
        )
        runCatching { getSystemService(NotificationManager::class.java).notify(TRIP_END_NOTIFICATION_ID, notification) }
            .onFailure { AppLog.warn("trip.end.notify.failed", "error" to it) }
        // 뒤에서 막힌 경우 예외 없이 무시되고 logcat 에 "Background activity launch blocked" 가 남는다
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
        destroyed = true
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

        /** 개인정보 수집에 동의했을 때만 운행 감지를 시작한다. */
        fun start(context: Context) {
            if (!Consent.agreed(context)) {
                AppLog.info("service.notStarted", "reason" to "noConsent")
                return
            }
            context.startForegroundService(Intent(context, TripService::class.java))
        }
    }
}
