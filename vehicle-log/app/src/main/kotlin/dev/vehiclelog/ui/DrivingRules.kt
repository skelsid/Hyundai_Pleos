package dev.vehiclelog.ui

/**
 * 주행 중 제한(CarUxRestrictions)에서 이 앱이 끌 기능.
 * 차량 라이브러리 없이 정수만으로 정해서 JVM 테스트로 확인한다.
 *
 * - restricted: 주행 중 제한이 걸렸다. distractionOptimized 가 아닌 화면(설정·입력 화면)은 시스템이 막으므로 그 화면으로 가는 버튼을 끈다.
 * - keyboardAllowed: 키보드 입력을 받아도 된다 (지역에 따라 주행 중에도 허용될 수 있다).
 *
 * Pleos 문서(driving-restriction-development-guide): distractionOptimized 를 선언한 앱도 ActiveRestrictions 를 보고 기능별 제한을 스스로 적용해야 한다.
 */
data class DrivingRules(val restricted: Boolean, val keyboardAllowed: Boolean) {

    companion object {
        /** android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD 와 같은 값 */
        const val NO_KEYBOARD = 8

        val PARKED = DrivingRules(restricted = false, keyboardAllowed = true)

        fun of(requiresDistractionOptimization: Boolean, activeRestrictions: Int): DrivingRules =
            if (!requiresDistractionOptimization) {
                PARKED
            } else {
                DrivingRules(restricted = true, keyboardAllowed = activeRestrictions and NO_KEYBOARD == 0)
            }
    }
}
