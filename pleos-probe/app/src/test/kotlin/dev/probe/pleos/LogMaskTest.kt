package dev.probe.pleos

import org.junit.Assert.assertEquals
import org.junit.Test

class LogMaskTest {

    @Test
    fun test_maskLocation_coordinatesAndAddress_masked() {
        assertEquals(
            "lat=*** lon=*** address=***",
            maskLocation("lat=37.5172 lon=127.0473 address=서울 강남구 테헤란로 1"),
        )
    }

    @Test
    fun test_maskLocation_coordinatesWithOtherField_keepsOtherField() {
        assertEquals("lat=*** lon=*** heading=90.0", maskLocation("lat=37.5 lon=127.0 heading=90.0"))
    }

    @Test
    fun test_maskLocation_noLocation_unchanged() {
        assertEquals("gear=PARK", maskLocation("gear=PARK"))
    }
}
