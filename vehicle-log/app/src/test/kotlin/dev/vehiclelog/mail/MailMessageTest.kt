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
