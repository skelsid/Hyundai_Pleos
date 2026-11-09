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
}
