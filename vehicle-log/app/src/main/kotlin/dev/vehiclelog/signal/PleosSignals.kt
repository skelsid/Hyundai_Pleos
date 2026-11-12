package dev.vehiclelog.signal

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.constant.area.WheelValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.vehiclelog.AppLog
import dev.vehiclelog.trip.Gear
import dev.vehiclelog.trip.TireReading
import dev.vehiclelog.trip.Wheel

/** 실차 신호. Pleos Vehicle·FusedLocation·NaviHelper 를 쓴다. */
class PleosSignals(context: Context) : VehicleSignals {

    override val name = "pleos"

    private val handler = Handler(Looper.getMainLooper())
    private val vehicle = Vehicle(context)
    private val location = FusedLocation(context)
    private val navi = NaviHelper(context)

    private var onGear: ((Gear) -> Unit)? = null
    private var gearAttempts = 0

    // 내비에 찍은 목적지(안내 시작·목적지 변경·도착 이벤트로 받는다). 운행 종료 시 도착지로 쓴다 (메인 스레드에서만 만진다)
    private var destination: String? = null
    // 경로 안내 중인지. 안내 없이 출발한 운행에 지난 운행의 목적지가 남지 않게 운행 시작 때 본다
    private var routing = false
    private var naviReady = false

    // 내비에 현재 위치 주소를 물어보고 답을 기다리는 조회들. 답은 리스너로 온다 (메인 스레드에서만 만진다)
    private val waitingAddress = mutableListOf<PointReading>()

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            handler.post { onGear?.invoke(toGear(gear)) }
        }

        override fun onFailed(e: Exception) {
            AppLog.warn("signal.gear.error", "error" to e)
        }
    }

    // 장소 이름은 위치 정보라 로그에는 받았는지만 남긴다. 리스너는 바인더 스레드에서 올 수 있어 메인 스레드로 넘긴다
    private val naviListener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) {
            val name = destinationName(info.name, null)
            handler.post {
                routing = true
                if (name != null) destination = name
                AppLog.info("signal.navi.route.started", "hasName" to (name != null))
            }
        }

        override fun onDestinationChanged() {
            // 이벤트에 새 목적지가 없어 다시 묻는다. 답은 onDestinationInfo 로 온다
            requestDestination()
        }

        // onRouteStateInfo 의 isRouting 은 쓰지 않는다. 에뮬레이터에서 안내 중에도 false 가 왔다 (2026-11-12 실측)

        override fun onDestinationInfo(info: DestinationInfo) {
            // 안내 중이면 목적지가 오고, 안내를 끈 뒤에는 빈 값이 왔다 (2026-11-12 실측). 다른 앱이 물은 답도 여기로 온다
            val name = destinationName(info.name, info.address)
            handler.post {
                routing = name != null
                // 빈 값이면 도착해서 안내가 끝난 경우일 수 있어 목적지는 지우지 않는다
                if (name != null) destination = name
                AppLog.info("signal.navi.destination", "hasName" to (name != null))
            }
        }

        override fun onRouteCancelled() {
            handler.post {
                routing = false
                destination = null
                AppLog.info("signal.navi.route.cancelled")
            }
        }

        override fun onRouteEnded() {
            // 도착해서 끝난 경우가 많으므로 목적지는 남긴다 (틀리면 운행 종료 화면에서 고친다)
            handler.post { routing = false }
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            val name = destinationName(info.name, info.address)
            handler.post {
                routing = false
                if (name != null) destination = name
                AppLog.info("signal.navi.arrived", "hasName" to (name != null))
            }
        }

        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            val address = info.address.takeIf { it.isNotBlank() }
            handler.post {
                // 주소는 위치 정보라 로그에 남기지 않는다
                AppLog.info("signal.navi.address", "hasAddress" to (address != null), "waiting" to waitingAddress.size)
                val readings = waitingAddress.toList()
                waitingAddress.clear()
                readings.forEach { it.address(address) }
            }
        }

        override fun onError(code: NaviErrorCode) {
            AppLog.warn("signal.navi.error", "code" to code)
        }
    }

    override fun start(onGear: (Gear) -> Unit) {
        this.onGear = onGear
        // 퍼미션이 없거나 서비스가 없으면 예외가 난다. 서비스가 죽지 않게 잡는다
        runCatching { vehicle.initialize() }.onFailure { AppLog.error("signal.vehicle.init.exception", it) }
        runCatching { location.initialize() }.onFailure { AppLog.error("signal.location.init.exception", it) }
        // initialize() 는 IPC 바인딩이 비동기라 반환 직후 호출하면 실패할 수 있다 (pleos-probe 실측)
        handler.postDelayed(::subscribe, BIND_WAIT_MS)
    }

    private fun subscribe() {
        subscribeGear()
        // 내비가 없어도 운행 기록은 돼야 하므로 기어와 따로 다룬다 (도착지 이름만 빠진다)
        runCatching {
            navi.initialize(
                {
                    navi.addListener(naviListener)
                    handler.post { naviReady = true }
                    // 앱보다 먼저 찍어 둔 목적지도 받도록 한 번 묻는다. 답은 onDestinationInfo 로 온다
                    requestDestination()
                },
                { AppLog.warn("signal.navi.init.error", "error" to it) },
            )
        }.onFailure { AppLog.error("signal.navi.init.exception", it) }
    }

    private fun requestDestination() {
        runCatching { navi.getDestinationInfo() }.onFailure { AppLog.error("signal.navi.destination.exception", it) }
    }

    /** 부팅 직후엔 차량 서비스 연결이 늦어 예외가 날 수 있다. 서비스가 죽지 않게 잡고 몇 번 다시 시도한다. */
    private fun subscribeGear() {
        runCatching {
            val gear = vehicle.getGear()
            gear.getCurrentGearSelectionState(
                { state -> handler.post { onGear?.invoke(toGear(state)) } },
                { AppLog.warn("signal.gear.read.error", "error" to it) },
            )
            gear.registerGearSelection(gearListener)
        }.onSuccess {
            AppLog.info("signal.gear.subscribed", "attempt" to gearAttempts + 1)
        }.onFailure { e ->
            gearAttempts++
            if (gearAttempts < MAX_SUBSCRIBE_ATTEMPTS) {
                AppLog.warn("signal.gear.subscribe.retry", "attempt" to gearAttempts, "error" to e)
                handler.postDelayed(::subscribeGear, BIND_WAIT_MS)
            } else {
                // 기어를 못 들으면 운행을 감지할 수 없다. 로그로 남기고 화면 동작은 유지한다
                AppLog.error("signal.gear.subscribe.failed", e, "attempts" to gearAttempts)
            }
        }
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
        // 내비 목적지 없이 다닌 운행도 장소가 남도록 현재 위치의 주소를 내비에 묻는다. 답은 onCurrentLocationInfo 로 온다
        if (naviReady) {
            waitingAddress += reading
            runCatching { navi.getCurrentLocationInfo() }.onFailure {
                AppLog.error("signal.navi.address.exception", it)
                waitingAddress -= reading
                reading.address(null)
            }
        } else {
            reading.address(null)
        }
        // 한쪽이 응답하지 않아도 운행은 기록해야 하므로 시간이 지나면 받은 값만으로 넘긴다
        handler.postDelayed(reading::finish, READ_TIMEOUT_MS)
    }

    override fun readTires(callback: (TireReading) -> Unit) {
        // TODO: 공기압 단위(psi·kPa)를 실차에서 확인. 비교는 비율·차량 기준값으로만 해서 단위와 무관하다
        val pressure = mutableMapOf<Wheel, Double?>()
        val low = mutableMapOf<Wheel, Double?>()
        var delivered = false
        val finish = {
            if (!delivered) {
                delivered = true
                callback(TireReading(pressure.toMap(), low.toMap()))
            }
        }
        val expected = Wheel.entries.size * 2
        fun put(target: MutableMap<Wheel, Double?>, wheel: Wheel, value: Float?) {
            target[wheel] = value?.toDouble()?.takeIf { it > 0.0 }
            if (pressure.size + low.size == expected) finish()
        }
        val tire = runCatching { vehicle.getTire() }.getOrElse {
            AppLog.error("signal.tire.exception", it)
            finish()
            return
        }
        for (wheel in Wheel.entries) {
            val area = WheelArea(toWheelValue(wheel))
            runCatching {
                tire.getTirePressure(
                    area,
                    { v -> handler.post { put(pressure, wheel, v) } },
                    { e -> handler.post { AppLog.warn("signal.tire.error", "wheel" to wheel, "error" to e); put(pressure, wheel, null) } },
                )
                tire.getLowTirePressure(
                    area,
                    { v -> handler.post { put(low, wheel, v) } },
                    { e -> handler.post { AppLog.warn("signal.tire.low.error", "wheel" to wheel, "error" to e); put(low, wheel, null) } },
                )
            }.onFailure {
                AppLog.error("signal.tire.exception", it)
                pressure.putIfAbsent(wheel, null)
                low.putIfAbsent(wheel, null)
            }
        }
        if (pressure.size + low.size == expected) finish()
        handler.postDelayed(finish, READ_TIMEOUT_MS)
    }

    override fun onTripStarted() {
        // 출발 전에 찍은 목적지는 남기고, 안내 없이 출발하면 지난 운행의 목적지를 지운다
        if (!routing) destination = null
    }

    override fun stop() {
        onGear = null
        handler.removeCallbacksAndMessages(null)
        waitingAddress.clear()
        runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
        runCatching { navi.removeListener(naviListener) }
        // 서비스 종료 중 예외로 앱이 죽지 않게 각각 잡는다
        runCatching { vehicle.release() }
        runCatching { location.release() }
        runCatching { navi.release() }
    }

    /** 거리·위치·주소 조회가 모두 끝나거나 시간이 다 되면 한 번만 콜백한다. */
    private inner class PointReading(private val callback: (TripPoint) -> Unit) {
        private var odometerDone = false
        private var locationDone = false
        private var addressDone = false
        private var delivered = false
        private var address: String? = null
        private var odometerKm: Double? = null
        private var latitude: Double? = null
        private var longitude: Double? = null
        private val timeMillis = System.currentTimeMillis()

        fun odometer(km: Double?) {
            odometerKm = km
            odometerDone = true
            finishIfDone()
        }

        fun location(lat: Double?, lon: Double?) {
            latitude = lat
            longitude = lon
            locationDone = true
            finishIfDone()
        }

        fun address(text: String?) {
            address = text
            addressDone = true
            finishIfDone()
        }

        private fun finishIfDone() {
            if (odometerDone && locationDone && addressDone) finish()
        }

        fun finish() {
            if (delivered) return
            delivered = true
            waitingAddress -= this
            // 도착지는 내비 목적지(상호 등)가 있으면 그걸, 없으면 주소를 쓴다 (TripPoint.endPlaceName)
            callback(TripPoint(timeMillis, odometerKm, latitude, longitude, address, destination))
        }
    }

    companion object {
        private const val BIND_WAIT_MS = 1_500L
        // 1.5초 간격 10번 = 부팅 뒤 약 15초까지 기다린다
        private const val MAX_SUBSCRIBE_ATTEMPTS = 10
        private const val READ_TIMEOUT_MS = 3_000L

        /**
         * 계기판 누적거리는 0 이하일 수 없다. 에뮬레이터는 값이 없을 때 0.0 을 주므로 '값 없음'으로 본다.
         * 실차에서 값이 오면 코드 변경 없이 그대로 채워진다.
         */
        fun validOdometer(km: Float?): Double? = km?.toDouble()?.takeIf { it > 0.0 }

        /** 내비가 준 목적지 이름. 상호가 비어 있으면 주소를 쓰고, 둘 다 비면 null. */
        fun destinationName(name: String?, address: String?): String? =
            name?.trim()?.takeIf { it.isNotEmpty() } ?: address?.trim()?.takeIf { it.isNotEmpty() }

        fun toWheelValue(wheel: Wheel): WheelValue = when (wheel) {
            Wheel.LEFT_FRONT -> WheelValue.LEFT_FRONT
            Wheel.RIGHT_FRONT -> WheelValue.RIGHT_FRONT
            Wheel.LEFT_REAR -> WheelValue.LEFT_REAR
            Wheel.RIGHT_REAR -> WheelValue.RIGHT_REAR
        }

        fun toGear(gear: VehicleGear?): Gear = when (gear) {
            null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
            VehicleGear.GEAR_PARK -> Gear.PARK
            else -> Gear.MOVING
        }
    }
}
