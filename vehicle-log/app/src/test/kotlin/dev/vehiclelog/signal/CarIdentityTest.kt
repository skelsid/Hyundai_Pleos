package dev.vehiclelog.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarIdentityTest {

    @Test
    fun test_clean_blankOrNull_returnsNull() {
        for (value in listOf(null, "", "   ", "\n")) assertNull(CarIdentity.clean(value))
    }

    @Test
    fun test_clean_value_trims() {
        assertEquals("KMHXX00XXXX000000", CarIdentity.clean(" KMHXX00XXXX000000 "))
    }
}
