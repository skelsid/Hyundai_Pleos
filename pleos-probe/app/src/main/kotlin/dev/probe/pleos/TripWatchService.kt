package dev.probe.pleos

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.stt.constant.ErrorType as SttErrorType
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitializeListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.vehicle.Vehicle
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.util.Locale

/**
 * 화면 없이 도는 서비스에서 차량 조회와 TTS 가 되는지 확인한다.
 * 운행 기록 앱은 다른 앱이 앞에 떠 있는 동안에도 주차를 감지하고 말을 걸어야 한다.
 */
class TripWatchService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var vehicle: Vehicle
    private lateinit var tts: TextToSpeech
    private lateinit var stt: SpeechToText
    private lateinit var fusedLocation: FusedLocation
    private lateinit var navi: NaviHelper

    private val ttsListener = object : EventListener {
        override fun onStart() = report("service.tts.state", "start")
        override fun onReady() = Unit

        override fun onDone() {
            report("service.tts.state", "done")
            // STT 인식 중엔 TTS 가 재생되지 않으므로 말하기가 끝난 뒤에 듣는다
            mainHandler.post(::listen)
        }

        override fun onError(error: ErrorType) = report("service.tts.error", error.toString())
        override fun onStop() = report("service.tts.state", "stop")
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private val sttListener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) = report("service.stt.text", "final=$isFinal text=$text")
        override fun onUpdatedEpdData(start: Long, end: Long) = Unit
        override fun onStartedRecognition() = report("service.stt.state", "started")
        override fun onEndedRecognition() = report("service.stt.state", "ended")
        override fun onError(error: SttErrorType) = report("service.stt.error", error.toString())
        override fun onReady() = report("service.stt.state", "ready")
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) =
            report("service.location", location?.let { "lat=${it.latitude} lon=${it.longitude}" } ?: "null")

        override fun onError(e: Exception) = report("service.location.error", e.toString())
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) = report("service.navi.location", "lat=${info.latitude} lon=${info.longitude}")
        override fun onError(code: NaviErrorCode) = report("service.navi.error", code.toString())
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        report("service.started", "foreground")
        vehicle = Vehicle(this).also { it.initialize() }
        tts = TextToSpeech(this, Mode.ON_DEVICE)
        stt = SpeechToText(this, SttMode.ON_DEVICE)
        fusedLocation = FusedLocation(this).also { it.initialize() }
        navi = NaviHelper(this)
        // initialize() 는 IPC 바인딩이 비동기라 반환 직후 호출하면 실패할 수 있어 잠시 뒤에 조회한다
        mainHandler.postDelayed(::probe, BIND_WAIT_MS)
    }

    private fun probe() {
        try {
            vehicle.getGear().getCurrentGearSelectionState(
                { report("service.gear", it.toString()) },
                { report("service.gear.error", it.toString()) },
            )
            vehicle.getOdometer().getCurrentDistanceDriven(
                { report("service.distanceDriven", it.toString()) },
                { report("service.distanceDriven.error", it.toString()) },
            )
            fusedLocation.getLastKnownLocation(locationListener)
            navi.initialize(
                {
                    navi.addListener(naviListener)
                    navi.getCurrentLocationInfo()
                },
                { report("service.navi.init.error", it.toString()) },
            )
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    tts.addEventListener(ttsListener)
                    tts.setLocale(Locale.KOREA)
                    mainHandler.postDelayed({ tts.speak(TTS_TEXT, true) }, TTS_SPEAK_DELAY_MS)
                }

                override fun onFailure(error: ErrorType) = report("service.tts.init.error", error.toString())
            })
        } catch (e: Exception) {
            report("service.exception", e.toString())
        }
    }

    private fun listen() {
        try {
            stt.initialize(object : SttInitializeListener {
                override fun onSuccess() {
                    stt.addListener(sttListener)
                    stt.setLocale(Locale.KOREA)
                    stt.request()
                }

                override fun onFailure(error: SttErrorType) = report("service.stt.init.error", error.toString())
            })
        } catch (e: Exception) {
            report("service.stt.exception", e.toString())
        }
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(CHANNEL_ID, "운행 감지", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("운행 감지 중")
            .build()
    }

    private fun report(key: String, value: String) {
        Log.i(TAG, "event=\"$key\" value=\"$value\"")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        // 초기화 실패 등으로 등록이 안 된 상태여도 해제는 시도한다
        runCatching { stt.removeListener(sttListener) }
        runCatching { navi.removeListener(naviListener) }
        vehicle.release()
        tts.release()
        stt.release()
        fusedLocation.release()
        navi.release()
        super.onDestroy()
    }

    companion object {
        const val TAG = "PleosProbe"
        private const val CHANNEL_ID = "trip_watch"
        private const val NOTIFICATION_ID = 1
        private const val BIND_WAIT_MS = 1_500L
        private const val TTS_SPEAK_DELAY_MS = 1_000L
        private const val TTS_TEXT = "백그라운드 서비스 음성 테스트입니다."
    }
}
