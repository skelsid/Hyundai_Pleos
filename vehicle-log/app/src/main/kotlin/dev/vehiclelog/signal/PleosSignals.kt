package dev.vehiclelog.signal

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.vehiclelog.AppLog
import dev.vehiclelog.trip.Gear

/** 실차 신호. Pleos Vehicle·FusedLocation·NaviHelper 를 쓴다. */
class PleosSignals(context: Context) : VehicleSignals {

    override val name = "pleos"

    private val handler = Handler(Looper.getMainLooper())
    private val vehicle = Vehicle(context)
    private val location = FusedLocation(context)
    private val navi = NaviHelper(context)

    private var onGear: ((Gear) -> Unit)? = null

    // 내비 도착 이벤트로 받은 이름. 운행 종료 시 도착지로 쓴다
    private var arrivedName: String? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            handler.post { onGear?.invoke(toGear(gear)) }
        }

        override fun onFailed(e: Exception) {
            AppLog.warn("signal.gear.error", "error" to e)
        }
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            arrivedName = info.name.takeIf { it.isNotBlank() }
            AppLog.info("signal.navi.arrived", "name" to arrivedName)
        }

        override fun onError(code: NaviErrorCode) {
            AppLog.warn("signal.navi.error", "code" to code)
        }
    }

    override fun start(onGear: (Gear) -> Unit) {
        this.onGear = onGear
        vehicle.initialize()
        location.initialize()
        // initialize() 는 IPC 바인딩이 비동기라 반환 직후 호출하면 실패할 수 있다 (pleos-probe 실측)
        handler.postDelayed(::subscribe, BIND_WAIT_MS)
    }

    private fun subscribe() {
        val gear = vehicle.getGear()
        gear.getCurrentGearSelectionState(
            { state -> handler.post { onGear?.invoke(toGear(state)) } },
            { AppLog.warn("signal.gear.read.error", "error" to it) },
        )
        gear.registerGearSelection(gearListener)
        navi.initialize(
            { navi.addListener(naviListener) },
            { AppLog.warn("signal.navi.init.error", "error" to it) },
        )
    }

    override fun readPoint(callback: (TripPoint) -> Unit) {
        val reading = PointReading(callback)
        // TODO: getCurrentDistanceDriven 의 단위·의미(누적 거리인지)를 실차에서 확인. 지금은 km 누적값으로 가정
        runCatching {
            vehicle.getOdometer().getCurrentDistanceDriven(
                { km -> handler.post { reading.odometer(validOdometer(km)) } },
                { e -> handler.post { AppLog.warn("signal.odometer.error", "error" to e); reading.odometer(null) } },
            )
        }.onFailure { AppLog.error("signal.odometer.exception", it); reading.odometer(null) }
        runCatching {
            location.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    handler.post { reading.location(location?.latitude, location?.longitude) }
                }

                override fun onError(e: Exception) {
                    handler.post { AppLog.warn("signal.location.error", "error" to e); reading.location(null, null) }
                }
            })
        }.onFailure { AppLog.error("signal.location.exception", it); reading.location(null, null) }
        // 한쪽이 응답하지 않아도 운행은 기록해야 하므로 시간이 지나면 받은 값만으로 넘긴다
        handler.postDelayed(reading::finish, READ_TIMEOUT_MS)
    }

    override fun onTripStarted() {
        arrivedName = null
    }

    override fun stop() {
        onGear = null
        handler.removeCallbacksAndMessages(null)
        runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
        runCatching { navi.removeListener(naviListener) }
        vehicle.release()
        location.release()
        navi.release()
    }

    /** 거리·위치 두 조회가 모두 끝나거나 시간이 다 되면 한 번만 콜백한다. */
    private inner class PointReading(private val callback: (TripPoint) -> Unit) {
        private var odometerDone = false
        private var locationDone = false
        private var delivered = false
        private var odometerKm: Double? = null
        private var latitude: Double? = null
        private var longitude: Double? = null
        private val timeMillis = System.currentTimeMillis()

        fun odometer(km: Double?) {
            odometerKm = km
            odometerDone = true
            if (locationDone) finish()
        }

        fun location(lat: Double?, lon: Double?) {
            latitude = lat
            longitude = lon
            locationDone = true
            if (odometerDone) finish()
        }

        fun finish() {
            if (delivered) return
            delivered = true
            callback(TripPoint(timeMillis, odometerKm, latitude, longitude, arrivedName))
        }
    }

    companion object {
        private const val BIND_WAIT_MS = 1_500L
        private const val READ_TIMEOUT_MS = 3_000L

        /**
         * 계기판 누적거리는 0 이하일 수 없다. 에뮬레이터는 값이 없을 때 0.0 을 주므로 '값 없음'으로 본다.
         * 실차에서 값이 오면 코드 변경 없이 그대로 채워진다.
         */
        fun validOdometer(km: Float?): Double? = km?.toDouble()?.takeIf { it > 0.0 }

        fun toGear(gear: VehicleGear?): Gear = when (gear) {
            null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
            VehicleGear.GEAR_PARK -> Gear.PARK
            else -> Gear.MOVING
        }
    }
}
