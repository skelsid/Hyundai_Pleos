package dev.vehiclelog.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PleosSignalsTest {

    @Test
    fun test_validOdometer_positive_returnsValue() {
        assertEquals(25042.5, PleosSignals.validOdometer(25042.5f)!!, 1e-3)
    }

    @Test
    fun test_validOdometer_zeroNegativeOrNull_returnsNull() {
        assertNull(PleosSignals.validOdometer(0.0f))
        assertNull(PleosSignals.validOdometer(-1.0f))
        assertNull(PleosSignals.validOdometer(null))
    }

    @Test
    fun test_destinationName_nameGiven_returnsTrimmedName() {
        assertEquals("남양연구소", PleosSignals.destinationName(" 남양연구소 ", "경기 화성시"))
    }

    @Test
    fun test_destinationName_blankName_returnsAddress() {
        assertEquals("경기 화성시", PleosSignals.destinationName("  ", "경기 화성시"))
        assertEquals("경기 화성시", PleosSignals.destinationName(null, "경기 화성시"))
    }

    @Test
    fun test_destinationName_bothBlank_returnsNull() {
        assertNull(PleosSignals.destinationName("", " "))
        assertNull(PleosSignals.destinationName(null, null))
    }

    @Test
    fun test_endPlaceName_destinationSet_prefersDestination() {
        val point = TripPoint(0L, null, null, null, placeName = "경기 성남시 수정구", destinationName = "남양연구소")
        assertEquals("남양연구소", point.endPlaceName)
    }

    @Test
    fun test_endPlaceName_noDestination_returnsCurrentPlace() {
        val point = TripPoint(0L, null, null, null, placeName = "경기 성남시 수정구")
        assertEquals("경기 성남시 수정구", point.endPlaceName)
        assertNull(TripPoint(0L, null, null, null, placeName = null).endPlaceName)
    }
}
