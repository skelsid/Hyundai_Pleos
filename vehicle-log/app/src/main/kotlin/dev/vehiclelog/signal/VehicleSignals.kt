package dev.vehiclelog.signal

import dev.vehiclelog.trip.Gear

/** 운행 시작·종료 순간에 남길 값. 읽지 못한 값은 null. */
data class TripPoint(
    val timeMillis: Long,
    val odometerKm: Double?,
    val latitude: Double?,
    val longitude: Double?,
    val placeName: String?,
)

/**
 * 차량 신호 출처. 실차(Pleos SDK)와 시연용 재생을 같은 형태로 다룬다.
 * 에뮬레이터는 기어를 바꿀 수 없어 시연은 재생으로만 가능하다.
 * 모든 콜백은 메인 스레드에서 부른다.
 */
interface VehicleSignals {
    val name: String

    fun start(onGear: (Gear) -> Unit)

    /** 지금 시점의 계기판 거리·위치·도착지 이름. 조회가 비동기라 콜백으로 준다. */
    fun readPoint(callback: (TripPoint) -> Unit)

    /** 새 운행이 시작되면 직전 운행의 도착지 이름을 지운다. */
    fun onTripStarted()

    fun stop()
}
