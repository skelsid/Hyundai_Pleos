package dev.vehiclelog.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class TripTextTest {

    @Test
    fun test_defaultUserId_lastUserStillActive_returnsLastUser() {
        assertEquals(2L, TripText.defaultUserId(listOf(1L, 2L, 3L), lastUserId = 2L))
    }

    @Test
    fun test_defaultUserId_lastUserRemovedWithSeveralCandidates_returnsNull() {
        assertNull(TripText.defaultUserId(listOf(1L, 3L), lastUserId = 2L))
    }

    @Test
    fun test_defaultUserId_singleCandidate_returnsIt() {
        assertEquals(5L, TripText.defaultUserId(listOf(5L), lastUserId = null))
    }

    @Test
    fun test_defaultUserId_noCandidates_returnsNull() {
        assertNull(TripText.defaultUserId(emptyList(), lastUserId = 2L))
    }

    @Test
    fun test_suggestNonBusiness_noHistory_returnsNull() {
        assertNull(TripText.suggestNonBusiness(emptyList()))
    }

    @Test
    fun test_suggestNonBusiness_mostlyNonBusiness_returnsTrue() {
        assertEquals(true, TripText.suggestNonBusiness(listOf(true)))
        assertEquals(true, TripText.suggestNonBusiness(listOf(true, false, true)))
    }

    @Test
    fun test_suggestNonBusiness_mostlyBusiness_returnsFalse() {
        assertEquals(false, TripText.suggestNonBusiness(listOf(false, false, true)))
    }

    @Test
    fun test_suggestNonBusiness_tie_returnsNull() {
        assertNull(TripText.suggestNonBusiness(listOf(true, false)))
    }

    @Test
    fun test_distance_null_returnsMissingLabel() {
        assertEquals("거리 미수신", TripText.distance(null))
        assertEquals("42.3km", TripText.distance(42.3))
    }

    @Test
    fun test_route_missingPlaces_returnsUnknownLabels() {
        assertEquals("출발지 미상 → 식당", TripText.route(null, "식당"))
    }
    private fun inSeoul(block: () -> Unit) {
        val saved = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
        try {
            block()
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    // 2026-11-09 09:00 KST
    private val nineAm = 1_794_182_400_000L
    private val hour = 3_600_000L

    @Test
    fun test_period_sameDay_showsEndTimeOnly() = inSeoul {
        assertEquals("11월 9일 09:00 – 10:30", TripText.period(nineAm, nineAm + hour * 3 / 2))
    }

    @Test
    fun test_period_crossesMidnight_showsEndDate() = inSeoul {
        assertEquals("11월 9일 09:00 – 11월 10일 01:00", TripText.period(nineAm, nineAm + hour * 16))
    }

    @Test
    fun test_period_open_showsDriving() = inSeoul {
        assertEquals("11월 9일 09:00 – 운행 중", TripText.period(nineAm, null))
    }

    @Test
    fun test_cleanPlace_extraSpaces_collapsesAndTrims() {
        assertEquals("남양 연구소", TripText.cleanPlace("  남양 \t 연구소\n"))
    }

    @Test
    fun test_cleanPlace_blank_returnsNull() {
        assertNull(TripText.cleanPlace(""))
        assertNull(TripText.cleanPlace("   "))
    }

    @Test
    fun test_placeChoices_currentInList_excludesCurrentAndLimits() {
        val recent = listOf("본사", "남양연구소", "물류센터", "고객사")
        assertEquals(listOf("본사", "물류센터"), TripText.placeChoices(recent, "남양연구소", 2))
    }

    @Test
    fun test_placeChoices_noCurrent_returnsFirstLimit() {
        assertEquals(listOf("본사"), TripText.placeChoices(listOf("본사", "고객사"), null, 1))
        assertEquals(emptyList<String>(), TripText.placeChoices(emptyList(), null, 6))
    }
}
