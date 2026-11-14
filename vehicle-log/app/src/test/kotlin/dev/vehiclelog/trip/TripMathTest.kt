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

    @Test
    fun test_isTrivialKm_parkingShuffle_true() {
        // 거리 0, 1분: 주차 자리 옮기기
        assertEquals(true, TripMath.isTrivialKm(TripMath.distanceKm(25000.0, 25000.0), 60_000L))
        // 거리를 모르고 30초
        assertEquals(true, TripMath.isTrivialKm(TripMath.distanceKm(null, null), 30_000L))
    }

    @Test
    fun test_isTrivialKm_realTrips_false() {
        assertEquals(false, TripMath.isTrivialKm(TripMath.distanceKm(25000.0, 25042.3), 8_000L))
        // 계기판 눈금이 거칠어 거리가 0 으로 보여도 3분 넘게 달렸으면 남긴다
        assertEquals(false, TripMath.isTrivialKm(TripMath.distanceKm(25000.0, 25000.0), 180_000L))
        // 거리를 모르면 1분부터 남긴다
        assertEquals(false, TripMath.isTrivialKm(TripMath.distanceKm(null, 25000.0), 60_000L))
        // 계기판이 줄어든 경우(값 오류)는 거리를 모르는 것으로 본다
        assertEquals(false, TripMath.isTrivialKm(TripMath.distanceKm(25000.0, 24000.0), 120_000L))
    }

    @Test
    fun test_tripDistanceKm_prefersOdometer() {
        assertEquals(42.0, TripMath.tripDistanceKm(25000.0, 25042.0, 1.0, 3.0)!!, delta)
    }

    @Test
    fun test_tripDistanceKm_noOdometer_usesTripMeter() {
        assertEquals(12.5, TripMath.tripDistanceKm(null, null, 3.0, 15.5)!!, delta)
    }

    @Test
    fun test_tripDistanceKm_tripMeterReset_returnsNull() {
        // 시동을 다시 걸어 트립 미터가 0 으로 돌아가면 거리를 모른다
        assertNull(TripMath.tripDistanceKm(null, null, 15.0, 2.0))
    }

    @Test
    fun test_isTrivialKm_sameRuleAsOdometer() {
        assertEquals(true, TripMath.isTrivialKm(0.05, 60_000L))
        assertEquals(false, TripMath.isTrivialKm(5.0, 60_000L))
        assertEquals(true, TripMath.isTrivialKm(null, 30_000L))
    }
}
