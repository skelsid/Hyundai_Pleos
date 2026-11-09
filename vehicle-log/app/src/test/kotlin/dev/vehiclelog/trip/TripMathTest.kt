package dev.vehiclelog.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripMathTest {

    private val delta = 1e-9

    @Test
    fun test_distanceKm_normal_returnsDifference() {
        assertEquals(42.0, TripMath.distanceKm(25000.0, 25042.0)!!, delta)
    }

    @Test
    fun test_distanceKm_sameValue_returnsZero() {
        assertEquals(0.0, TripMath.distanceKm(100.0, 100.0)!!, delta)
    }

    @Test
    fun test_distanceKm_missingOrDecreasing_returnsNull() {
        assertNull(TripMath.distanceKm(null, 10.0))
        assertNull(TripMath.distanceKm(10.0, null))
        assertNull(TripMath.distanceKm(10.0, 9.0))
    }

    @Test
    fun test_businessRatio_mixedTrips_excludesNonBusiness() {
        val trips = listOf(
            TripDistance(30.0, nonBusiness = false),
            TripDistance(10.0, nonBusiness = true),
            TripDistance(null, nonBusiness = false),
        )
        assertEquals(0.75, TripMath.businessRatio(trips)!!, delta)
    }

    @Test
    fun test_businessRatio_noDistance_returnsNull() {
        assertNull(TripMath.businessRatio(emptyList()))
        assertNull(TripMath.businessRatio(listOf(TripDistance(0.0, nonBusiness = false))))
    }
}
