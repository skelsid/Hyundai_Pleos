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
    fun test_clean_invalidPlaceholder_returnsNull() {
        // 에뮬레이터 v2.1.0 은 값이 없을 때 따옴표째로 "invalid" 를 돌려준다 (실측)
        for (value in listOf("\"invalid\"", "invalid", " \"INVALID\" ", "\"\"")) assertNull(value, CarIdentity.clean(value))
    }

    @Test
    fun test_clean_quotedValue_stripsQuotes() {
        assertEquals("KMHXX00XXXX000000", CarIdentity.clean("\"KMHXX00XXXX000000\""))
    }

    @Test
    fun test_clean_value_trims() {
        assertEquals("KMHXX00XXXX000000", CarIdentity.clean(" KMHXX00XXXX000000 "))
    }
}
