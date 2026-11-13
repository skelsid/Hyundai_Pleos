package dev.vehiclelog.ui

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.vehiclelog.AppLog

/**
 * 차량의 주행 중 제한 상태를 화면에 알려 준다. 콜백은 메인 스레드에서 온다.
 * 차량 서비스에 연결하지 못하면 제한이 없는 것으로 둔다 (distractionOptimized 가 아닌 화면은 어차피 시스템이 막는다).
 */
class DrivingState(private val context: Context, private val onChange: (DrivingRules) -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    /** 화면을 만들 때 부른다. 연결은 기다리지 않고, 연결되면 현재 상태부터 알려 준다. */
    fun start() {
        // android.car 라이브러리가 없는 기기에서는 클래스가 없어 Error 가 난다. 화면이 죽지 않게 모두 잡는다
        car = runCatching {
            Car.createCar(context, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (ready) subscribe(connected) else AppLog.warn("uxr.car.disconnected")
            }
        }.onFailure { AppLog.warn("uxr.car.unavailable", "error" to it) }.getOrNull()
    }

    private fun subscribe(connected: Car) {
        runCatching {
            val uxr = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            manager = uxr
            uxr.registerListener { restrictions -> handler.post { deliver(restrictions) } }
            deliver(uxr.currentCarUxRestrictions)
        }.onFailure { AppLog.warn("uxr.subscribe.failed", "error" to it) }
    }

    private fun deliver(restrictions: CarUxRestrictions?) {
        val rules = if (restrictions == null) {
            DrivingRules.PARKED
        } else {
            DrivingRules.of(restrictions.isRequiresDistractionOptimization, restrictions.activeRestrictions)
        }
        AppLog.info("uxr.changed", "restricted" to rules.restricted, "keyboard" to rules.keyboardAllowed)
        onChange(rules)
    }

    /** 화면을 없앨 때 부른다. */
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }
}
