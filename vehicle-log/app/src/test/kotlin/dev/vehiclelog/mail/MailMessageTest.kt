package dev.vehiclelog.mail

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.Date

class MailMessageTest {

    private fun message(attachments: List<MailAttachment> = emptyList(), subject: String = "운행기록표") = MailMessage(
        fromAddress = "sender@example.com",
        fromName = "Hyundai Vehicle Log",
        to = "admin@example.com",
        subject = subject,
        body = "안녕하세요.\n두 번째 줄",
        attachments = attachments,
    )

    @Test
    fun test_toMime_koreanSubject_encodedAsUtf8Word() {
        val mime = message().toMime(Date(0), "B")
        val encoded = "=?UTF-8?B?" + Base64.getEncoder().encodeToString("운행기록표".toByteArray()) + "?="
        assertTrue(mime.contains("Subject: $encoded\r\n"))
        assertTrue(mime.contains("From: Hyundai Vehicle Log <sender@example.com>\r\n"))
        assertTrue(mime.contains("To: <admin@example.com>\r\n"))
    }

    @Test
    fun test_toMime_allLinesCrlfAndShort() {
        val big = ByteArray(5000) { it.toByte() }
        val mime = message(listOf(MailAttachment("VehicleLog_2026-11.xlsx", "application/x", big))).toMime(Date(0), "B")
        assertFalse(mime.replace("\r\n", "").contains('\n'))
        assertTrue(mime.split("\r\n").all { it.length <= 998 })
        assertTrue(mime.endsWith("--B--\r\n"))
    }

    @Test
    fun test_toMime_attachmentRoundTrips() {
        val bytes = ByteArray(300) { (it * 7).toByte() }
        val mime = message(listOf(MailAttachment("a.xlsx", "application/x", bytes))).toMime(Date(0), "B")
        val part = mime.substringAfter("filename=\"a.xlsx\"\r\nContent-Transfer-Encoding: base64\r\n\r\n").substringBefore("--B")
        assertArrayEquals(bytes, Base64.getMimeDecoder().decode(part))
    }

    @Test
    fun test_toMime_bodyDecodesToUtf8WithCrlf() {
        val mime = message().toMime(Date(0), "B")
        val part = mime.substringAfter("charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n").substringBefore("--B")
        assertEquals("안녕하세요.\r\n두 번째 줄", String(Base64.getMimeDecoder().decode(part), Charsets.UTF_8))
    }

    @Test
    fun test_toMime_newlineInSubject_encodedNotInjected() {
        val mime = message(subject = "a\r\nBcc: x@example.com").toMime(Date(0), "B")
        assertFalse(mime.contains("\r\nBcc:"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun test_toMime_newlineInRecipient_rejected() {
        MailMessage("s@example.com", "n", "a@example.com\r\nBcc: x@example.com", "s", "b").toMime(Date(0), "B")
    }

    @Test
    fun test_encodeWord_asciiUnchanged() {
        assertEquals("Hyundai Vehicle Log", MailMessage.encodeWord("Hyundai Vehicle Log"))
    }

    /** encodeWord 결과를 받는 쪽처럼 풀어 본다: 덩어리마다 base64 를 풀어 이어 붙인다. */
    private fun decodeWords(encoded: String): String =
        encoded.split(" ").joinToString("") { word ->
            assertTrue(word, word.startsWith("=?UTF-8?B?") && word.endsWith("?="))
            String(Base64.getDecoder().decode(word.removePrefix("=?UTF-8?B?").removeSuffix("?=")), Charsets.UTF_8)
        }

    @Test
    fun test_encodeWord_longKoreanSubject_splitsIntoWordsOf75OrLess() {
        val subject = "[차량운행기록] 12가3456 운행기록표 2025년 11월, 12월, 2026년 1월, 2월, 3월, 4월, 5월, 6월, 7월, 8월, 9월, 10월"
        val encoded = MailMessage.encodeWord(subject)
        val words = encoded.split(" ")
        assertTrue(words.size > 1)
        assertTrue(words.all { it.length <= 75 })
        assertEquals(subject, decodeWords(encoded))
    }

    @Test
    fun test_encodeWord_surrogatePairAtBoundary_notSplit() {
        // 38바이트 뒤에 4바이트 글자가 오면 그 글자는 통째로 다음 덩어리로 간다
        val text = "가".repeat(12) + "ab" + "🚗" + "끝"
        val encoded = MailMessage.encodeWord(text)
        assertEquals(2, encoded.split(" ").size)
        assertEquals(text, decodeWords(encoded))
    }

    @Test
    fun test_toMime_longKoreanSubject_foldedIntoLinesOf76OrLess() {
        val subject = "[차량운행기록] 12가3456 운행기록표 2025년 11월, 12월, 2026년 1월, 2월, 3월"
        val mime = message(subject = subject).toMime(Date(0), "B")
        val headers = mime.substringBefore("\r\n\r\n").split("\r\n")
        assertTrue(headers.all { it.length <= 76 })
        // 접힌 줄(공백으로 시작)을 앞줄에 이어 붙이면 제목이 그대로 나온다
        val start = headers.indexOfFirst { it.startsWith("Subject: ") }
        val folded = headers.drop(start + 1).takeWhile { it.startsWith(" ") }
        assertTrue(folded.isNotEmpty())
        val unfolded = headers[start].removePrefix("Subject: ") + folded.joinToString("")
        assertEquals(subject, decodeWords(unfolded))
    }

    @Test
    fun test_encodeWord_shortKorean_singleWord() {
        assertEquals("운행기록표", decodeWords(MailMessage.encodeWord("운행기록표")))
        assertFalse(MailMessage.encodeWord("운행기록표").contains(" "))
    }

    @Test
    fun test_isValid_addresses() {
        assertTrue(MailAddress.isValid("admin@example.com"))
        assertTrue(MailAddress.isValid(" a.b+c@sub.example.co.kr "))
        assertFalse(MailAddress.isValid("admin@example"))
        assertFalse(MailAddress.isValid("admin example@x.com"))
        assertFalse(MailAddress.isValid("<a@x.com>"))
        assertFalse(MailAddress.isValid("a@x.com\r\nBcc: b@y.com"))
        assertFalse(MailAddress.isValid(""))
    }
}
