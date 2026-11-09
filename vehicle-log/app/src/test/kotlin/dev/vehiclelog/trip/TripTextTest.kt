package dev.vehiclelog.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
