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
}
