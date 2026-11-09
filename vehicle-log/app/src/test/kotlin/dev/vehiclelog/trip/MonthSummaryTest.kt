package dev.vehiclelog.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class MonthSummaryTest {

    private val delta = 1e-9
    private val seoul = TimeZone.getTimeZone("Asia/Seoul")

    @Test
    fun test_monthSummary_unclassifiedTrip_countedInTotalButNotInRatio() {
        val summary = TripMath.monthSummary(
            listOf(
                MonthTrip(30.0, nonBusiness = false),
                MonthTrip(10.0, nonBusiness = true),
                MonthTrip(60.0, nonBusiness = null),
            ),
        )
        assertEquals(3, summary.tripCount)
        assertEquals(100.0, summary.totalKm, delta)
        assertEquals(30.0, summary.businessKm, delta)
        assertEquals(0.75, summary.businessRatio!!, delta)
        assertEquals(1, summary.unclassified)
        assertEquals(0, summary.unmeasured)
    }

    @Test
    fun test_monthSummary_missingDistance_countedAsUnmeasured() {
        val summary = TripMath.monthSummary(listOf(MonthTrip(null, nonBusiness = false)))
        assertEquals(1, summary.unmeasured)
        assertEquals(0.0, summary.totalKm, delta)
        assertNull(summary.businessRatio)
    }

    @Test
    fun test_monthSummary_empty_returnsZeros() {
        val summary = TripMath.monthSummary(emptyList())
        assertEquals(0, summary.tripCount)
        assertNull(summary.businessRatio)
    }

    @Test
    fun test_monthRange_november_startsAtLocalMidnight() {
        val (start, end) = TripMath.monthRange(2026, 11, seoul)
        // 2026-11-01T00:00+09:00 = 2026-10-31T15:00Z
        assertEquals(1_793_458_800_000L, start)
        // 30일
        assertEquals(30L * 24 * 60 * 60 * 1000, end - start)
    }

    @Test
    fun test_monthRange_december_rollsOverToNextYear() {
        val (_, end) = TripMath.monthRange(2026, 12, seoul)
        assertEquals(TripMath.monthRange(2027, 1, seoul).first, end)
    }

    @Test(expected = IllegalArgumentException::class)
    fun test_monthRange_invalidMonth_throws() {
        TripMath.monthRange(2026, 13, seoul)
    }
}
