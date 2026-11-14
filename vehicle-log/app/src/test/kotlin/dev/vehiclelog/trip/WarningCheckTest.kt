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
    fun test_spoken_tireAlreadyAnnounced_dropsTpmsOnly() {
        val active = listOf("TPMS", "ENGINE", "LOW_FUEL")
        assertEquals(listOf("엔진", "연료 부족"), WarningCheck.spoken(active, tireAnnounced = true, energyAnnounced = false))
    }

    @Test
    fun test_spoken_energyAlreadyAnnounced_dropsFuelAndBattery() {
        val active = listOf("LOW_FUEL", "LOW_EV_BATTERY", "TPMS")
        assertEquals(listOf("타이어 공기압"), WarningCheck.spoken(active, tireAnnounced = false, energyAnnounced = true))
        // 남는 것이 없으면 경고등 안내를 하지 않는다
        assertNull(WarningCheck.message(WarningCheck.spoken(listOf("LOW_FUEL"), tireAnnounced = false, energyAnnounced = true)))
    }

    @Test
    fun test_spoken_nothingAnnounced_sameAsImportant() {
        val active = listOf("TPMS", "SEAT_BELT", "LOW_FUEL")
        assertEquals(WarningCheck.important(active), WarningCheck.spoken(active, tireAnnounced = false, energyAnnounced = false))
    }

    @Test
    fun test_summary_notReceived_saysSo() {
        assertEquals("경고등 값 미수신", WarningCheck.summary(null))
    }
}
