package dev.vehiclelog.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 저장 형식만 본다. Keystore 암복호화는 기기에서만 돌아 에뮬레이터로 확인한다. */
class SecretBoxTest {

    @Test
    fun test_format_thenParse_returnsSameBytes() {
        val iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val sealed = byteArrayOf(-1, 0, 127, -128, 42)
        val parsed = SecretBox.parse(SecretBox.format(iv, sealed))
        assertNotNull(parsed)
        assertArrayEquals(iv, parsed!!.first)
        assertArrayEquals(sealed, parsed.second)
    }

    @Test
    fun test_isSealed_legacyPlaintext_returnsFalse() {
        // 예전 버전은 16자리 앱 비밀번호를 그대로 저장했다
        assertFalse(SecretBox.isSealed("abcdabcdabcdabcd"))
        assertTrue(SecretBox.isSealed(SecretBox.format(byteArrayOf(1), byteArrayOf(2))))
    }

    @Test
    fun test_parse_brokenValues_returnsNull() {
        assertNull(SecretBox.parse("abcdabcdabcdabcd"))
        assertNull(SecretBox.parse("v1:"))
        assertNull(SecretBox.parse("v1:AQ=="))
        assertNull(SecretBox.parse("v1::AQ=="))
        assertNull(SecretBox.parse("v1:AQ==:AQ==:AQ=="))
        assertNull(SecretBox.parse("v1:@@@:AQ=="))
    }

    @Test
    fun test_format_usesVersionPrefix() {
        assertEquals("v1:AQ==:Ag==", SecretBox.format(byteArrayOf(1), byteArrayOf(2)))
    }
}
