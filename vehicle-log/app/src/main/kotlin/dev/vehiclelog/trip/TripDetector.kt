package dev.vehiclelog.trip

/** 운행 판단에 필요한 만큼만 줄인 기어 상태. */
enum class Gear { PARK, MOVING, UNKNOWN }

enum class TripEvent { STARTED, ENDED }

/**
 * 기어 변화만 보고 운행 시작·종료를 정한다.
 * 차량 SDK 와 분리해 두어 JVM 단위 테스트로 확인한다.
 *
 * @param tripOpen 부팅 전에 끝나지 않은 운행이 저장소에 남아 있으면 true. 그 운행을 이어 받는다.
 */
class TripDetector(tripOpen: Boolean = false) {

    var tripOpen: Boolean = tripOpen
        private set

    fun onGear(gear: Gear): TripEvent? {
        // 신호가 끊긴 순간(UNKNOWN)에 운행을 닫으면 한 운행이 둘로 쪼개지므로 상태를 유지한다
        if (gear == Gear.UNKNOWN) return null
        if (gear == Gear.MOVING && !tripOpen) {
            tripOpen = true
            return TripEvent.STARTED
        }
        // 후진·중립은 주차 중 조작일 수 있어 P 로 들어갈 때만 끝낸다
        if (gear == Gear.PARK && tripOpen) {
            tripOpen = false
            return TripEvent.ENDED
        }
        return null
    }
}
