package dev.probe.pleos

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RouteStateInfo
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
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.EventListener as TtsEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import java.util.Locale

/**
 * Pleos Connect SDK 가 에뮬레이터에서 실제로 응답하는지 확인하는 프로브 화면.
 * 결과는 화면과 logcat(tag=PleosProbe) 양쪽에 key=value 로 남긴다.
 */
class MainActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val results = linkedMapOf<String, String>()
    private lateinit var output: TextView

    private lateinit var vehicle: Vehicle
    private lateinit var fusedLocation: FusedLocation
    private lateinit var tts: TextToSpeech
    private lateinit var stt: SpeechToText
    private lateinit var navi: NaviHelper

    private val naviListener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) =
            report("navi.location", "lat=${info.latitude} lon=${info.longitude} address=${info.address}")

        override fun onRouteStateInfo(info: RouteStateInfo) =
            report("navi.routeState", "mode=${info.naviMode} option=${info.routeOption} terms=${info.termsAgreement}")

        override fun onError(code: NaviErrorCode) = report("navi.error", code.toString())
    }

    private val ttsListener = object : TtsEventListener {
        override fun onStart() = report("tts.state", "start")
        override fun onReady() = Unit
        override fun onDone() = report("tts.state", "done")
        override fun onError(error: ErrorType) = report("tts.error", error.toString())
        override fun onStop() = report("tts.state", "stop")
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private val sttListener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) = report("stt.text", "final=$isFinal text=$text")
        override fun onUpdatedEpdData(start: Long, end: Long) = Unit
        override fun onStartedRecognition() = report("stt.state", "started")
        override fun onEndedRecognition() = report("stt.state", "ended")
        override fun onError(error: SttErrorType) = report("stt.error", error.toString())
        override fun onReady() = report("stt.state", "ready")
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = report("vehicle.gear.event", gear.toString())
        override fun onFailed(e: Exception) = report("vehicle.gear.event.error", e.toString())
    }

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(soc: Int?) = report("vehicle.soc.event", soc.toString())
        override fun onFailed(e: Exception) = report("vehicle.soc.event.error", e.toString())
    }

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            // 문서: 위치 데이터가 아직 없으면 null 이 올 수 있다
            if (location == null) return report("location.event", "null")
            report("location.event", "lat=${location.latitude} lon=${location.longitude} heading=${location.heading}")
        }

        override fun onError(e: Exception) = report("location.error", e.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply {
            textSize = OUTPUT_TEXT_SP
            setPadding(OUTPUT_PADDING_PX, OUTPUT_PADDING_PX, OUTPUT_PADDING_PX, OUTPUT_PADDING_PX)
        }
        setContentView(output)

        vehicle = Vehicle(this).also { it.initialize() }
        fusedLocation = FusedLocation(this).also { it.initialize() }
        tts = TextToSpeech(this, Mode.ON_DEVICE)
        stt = SpeechToText(this, SttMode.ON_DEVICE)
        navi = NaviHelper(this)

        // initialize() 는 IPC 바인딩이 비동기라 반환 직후 호출하면 실패할 수 있어 잠시 뒤에 조회한다
        mainHandler.postDelayed(::probeAll, BIND_WAIT_MS)
    }

    private fun probeAll() {
        // SDK 가 퍼미션 부족 등으로 동기 예외를 던지므로, 하나가 실패해도 나머지는 확인하도록 따로 감싼다
        // 실험용: adb shell am start ... --es skip stt,navi 로 일부 프로브를 뺀다(서로 간섭하는지 확인)
        val skip = intent.getStringExtra(EXTRA_SKIP)?.split(',')?.toSet().orEmpty()
        listOf(
            "vehicle" to ::probeVehicle,
            "location" to ::probeLocation,
            "tts" to ::probeTts,
            "stt" to ::probeStt,
            "navi" to ::probeNavi,
        ).filterNot { it.first in skip }.forEach { (name, probe) -> runProbe(name, probe) }
    }

    private fun probeStt() {
        stt.initialize(object : SttInitializeListener {
            override fun onSuccess() {
                report("stt.init", "ok")
                stt.addListener(sttListener)
                // 실험용: adb shell am start ... --es stt_locale none|ko-KR|en-US
                when (val tag = intent.getStringExtra(EXTRA_STT_LOCALE)) {
                    null -> stt.setLocale(SPEECH_LOCALE)
                    "none" -> Unit
                    else -> stt.setLocale(Locale.forLanguageTag(tag))
                }
                // 에뮬레이터 마이크 입력이 없으면 ready 뒤 error/ended 로 끝난다. 호출 경로 확인이 목적
                stt.request()
            }

            override fun onFailure(error: SttErrorType) = report("stt.init.error", error.toString())
        })
    }

    private fun probeNavi() {
        navi.initialize(
            {
                report("navi.init", "ok")
                navi.addListener(naviListener)
                navi.getCurrentLocationInfo()
                navi.getRouteStateInfo()
            },
            { report("navi.init.error", it.toString()) },
        )
    }

    private fun runProbe(name: String, probe: () -> Unit) {
        try {
            probe()
        } catch (e: Exception) {
            report("$name.exception", e.toString())
        }
    }

    private fun probeVehicle() {
        val gear = vehicle.getGear()
        gear.getCurrentGearSelectionState(
            { report("vehicle.gear", it.toString()) },
            { report("vehicle.gear.error", it.toString()) },
        )
        gear.registerGearSelection(gearListener)

        val battery = vehicle.getEvBattery()
        battery.getEvBatteryStateOfCharge(
            { report("vehicle.soc", it.toString()) },
            { report("vehicle.soc.error", it.toString()) },
        )
        battery.registerEvBatteryStateOfCharge(socListener)
    }

    private fun probeLocation() {
        val supported = fusedLocation.isSupported()
        report("location.supported", supported.toString())
        if (!supported) return
        fusedLocation.getLastKnownLocation(locationListener)
        fusedLocation.registerFusedLocationCallback(locationListener)
    }

    private fun probeTts() {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                report("tts.init", "ok")
                tts.addEventListener(ttsListener)
                tts.setLocale(SPEECH_LOCALE)
                // 두 번째 인자는 스트리밍의 마지막 조각 여부다. false 면 SDK 가 텍스트를 쌓아만 두고 서비스로 보내지 않는다
                // (OnDeviceStreamTts.doSpeak 바이트코드). 한 문장을 바로 읽히려면 true
                mainHandler.postDelayed({ tts.speak(TTS_TEST_TEXT, true) }, TTS_SPEAK_DELAY_MS)
            }

            override fun onFailure(error: ErrorType) = report("tts.init.error", error.toString())
        })
    }

    private fun report(key: String, value: String) {
        Log.i(TAG, "event=\"$key\" value=\"$value\"")
        // SDK 콜백이 어느 스레드로 올지 문서에 없어 화면 갱신은 메인 스레드로 넘긴다
        mainHandler.post {
            results[key] = value
            output.text = results.entries.joinToString("\n") { "${it.key} = ${it.value}" }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        // 초기화 실패 등으로 등록이 안 된 상태여도 해제는 시도한다
        runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
        runCatching { vehicle.getEvBattery().unregisterEvBatteryStateOfCharge(socListener) }
        runCatching { fusedLocation.unregisterFusedLocationCallback(locationListener) }
        runCatching { stt.removeListener(sttListener) }
        runCatching { navi.removeListener(naviListener) }
        vehicle.release()
        fusedLocation.release()
        tts.release()
        stt.release()
        navi.release()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "PleosProbe"
        const val BIND_WAIT_MS = 1_500L
        const val OUTPUT_TEXT_SP = 22f
        const val OUTPUT_PADDING_PX = 48
        const val TTS_TEST_TEXT = "플레오스 음성 합성 테스트입니다."

        // Locale.KOREAN("ko")은 엔진이 "unsupported locale: ko-" 로 거부한다. 국가까지 지정해야 한다
        val SPEECH_LOCALE: Locale = Locale.KOREA
        const val EXTRA_STT_LOCALE = "stt_locale"
        const val EXTRA_SKIP = "skip"
        const val TTS_SPEAK_DELAY_MS = 1_000L
    }
}
