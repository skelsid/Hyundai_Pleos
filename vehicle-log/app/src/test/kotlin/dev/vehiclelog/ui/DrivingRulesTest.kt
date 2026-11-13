package dev.vehiclelog.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DrivingRulesTest {

    @Test
    fun test_of_notRequired_returnsParked() {
        // 제한 플래그가 남아 있어도 최적화 요구가 없으면 정차로 본다
        assertEquals(DrivingRules.PARKED, DrivingRules.of(false, DrivingRules.NO_KEYBOARD))
    }

    @Test
    fun test_of_noKeyboardFlag_blocksKeyboard() {
        val rules = DrivingRules.of(true, DrivingRules.NO_KEYBOARD or NO_SETUP)
        assertEquals(DrivingRules(restricted = true, keyboardAllowed = false), rules)
    }

    @Test
    fun test_of_restrictedWithoutKeyboardFlag_keepsKeyboard() {
        // 유럽 등은 주행 중에도 키보드를 허용한다 (driving-restiction-policy)
        assertEquals(DrivingRules(restricted = true, keyboardAllowed = true), DrivingRules.of(true, NO_SETUP))
    }

    private companion object {
        // CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP
        const val NO_SETUP = 64
    }
}
