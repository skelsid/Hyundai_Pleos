package dev.vehiclelog.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WarningCheckTest {

    @Test
    fun test_important_normalIndicatorsOnly_empty() {
        val labels = WarningCheck.important(listOf("PARKING_BRAKE", "SEAT_BELT", "DOOR_OPEN", "HEADLAMP_LOW"))
        assertEquals(emptyList<String>(), labels)
        assertNull(WarningCheck.message(labels))
        assertEquals("이상 없음", WarningCheck.summary(labels))
    }

    @Test
    fun test_important_mixed_keepsOnlyWarningsInFixedOrder() {
        val labels = WarningCheck.important(listOf("TPMS", "SEAT_BELT", "ENGINE", "TPMS"))
        assertEquals(listOf("엔진", "타이어 공기압"), labels)
        assertEquals("계기판 경고등이 켜져 있습니다. 엔진, 타이어 공기압. 점검해 주세요.", WarningCheck.message(labels))
        assertEquals("엔진, 타이어 공기압", WarningCheck.summary(labels))
    }

    @Test
    fun test_important_unknownType_ignored() {
        assertEquals(emptyList<String>(), WarningCheck.important(listOf("SOMETHING_NEW")))
    }

    @Test
    fun test_summary_notReceived_saysSo() {
        assertEquals("경고등 값 미수신", WarningCheck.summary(null))
    }
}
