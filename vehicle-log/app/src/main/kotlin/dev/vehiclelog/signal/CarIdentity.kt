package dev.vehiclelog.signal

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.vehiclelog.AppLog

/**
 * 차량이 알려 주는 차대번호(VIN)와 차량 등록번호(CRN). 차량 정보 화면의 빈 칸을 채우는 데만 쓴다.
 * 못 받은 값은 null 이다 (추정하지 않는다). 콜백은 메인 스레드에서 한 번만 부른다.
 */
class CarIdentity(context: Context) {

    data class Result(val vin: String?, val registration: String?)

    private val handler = Handler(Looper.getMainLooper())
    private val vehicle = Vehicle(context.applicationContext)
    private var released = false

    fun read(callback: (Result) -> Unit) {
        var vin: String? = null
        var registration: String? = null
        var answered = 0
        var delivered = false
        val finish = {
            if (!delivered && !released) {
                delivered = true
                AppLog.info("carinfo.read", "hasVin" to (vin != null), "hasRegistration" to (registration != null))
                callback(Result(vin, registration))
            }
        }
        fun answer(store: () -> Unit) = handler.post {
            store()
            answered++
            if (answered == ITEMS) finish()
        }
        runCatching {
            vehicle.initialize()
            val info = vehicle.getCarInfo()
            info.getVINInfo(
                { v -> answer { vin = clean(v) } },
                { e -> AppLog.warn("carinfo.vin.error", "error" to e); answer {} },
            )
            info.getCRNInfo(
                { v -> answer { registration = clean(v) } },
                { e -> AppLog.warn("carinfo.crn.error", "error" to e); answer {} },
            )
        }.onFailure {
            AppLog.error("carinfo.exception", it)
            handler.post(finish)
        }
        handler.postDelayed(finish, READ_TIMEOUT_MS)
    }

    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        runCatching { vehicle.release() }
    }

    companion object {
        private const val ITEMS = 2
        private const val READ_TIMEOUT_MS = 3_000L

        /** 차량이 값이 없을 때 돌려주는 자리 채움 값 (에뮬레이터 v2.1.0 실측: 따옴표째로 "invalid") */
        private const val NO_VALUE = "invalid"

        /** 앞뒤 공백·따옴표를 떼고, 빈 값과 자리 채움 값은 못 받은 것으로 본다. */
        fun clean(value: String?): String? = value?.trim()?.trim('"')?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals(NO_VALUE, ignoreCase = true) }
    }
}
