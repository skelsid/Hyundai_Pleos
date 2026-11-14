package dev.vehiclelog.mail

import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID

/** 첨부 한 개. 파일 이름은 ASCII 로 둔다 (메일 앱마다 한글 파일 이름 처리 방식이 달라 깨질 수 있다). */
class MailAttachment(val fileName: String, val contentType: String, val bytes: ByteArray)

/** 보낼 메일 한 통. 본문은 일반 텍스트. */
class MailMessage(
    val fromAddress: String,
    val fromName: String,
    val to: String,
    val subject: String,
    val body: String,
    val attachments: List<MailAttachment> = emptyList(),
) {
    /**
     * SMTP DATA 로 보낼 RFC 5322 메시지. 줄 끝은 CRLF, 한글 머리글은 RFC 2047 로 인코딩하고
     * 본문·첨부는 base64 76자 줄로 나눈다 (Gmail 은 한 줄 998자를 넘으면 거부한다).
     */
    fun toMime(date: Date = Date(), boundary: String = "=_vehiclelog_" + UUID.randomUUID().toString().replace("-", "")): String =
        buildString {
            header("From", "${encodeWord(fromName)} <$fromAddress>")
            header("To", "<$to>")
            header("Subject", encodeWord(subject))
            header("Date", SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(date))
            header("MIME-Version", "1.0")
            header("Content-Type", "multipart/mixed; boundary=\"$boundary\"")
            append(CRLF)

            append("--").append(boundary).append(CRLF)
            header("Content-Type", "text/plain; charset=UTF-8")
            header("Content-Transfer-Encoding", "base64")
            append(CRLF)
            appendBase64(body.replace("\r\n", "\n").replace("\n", CRLF).toByteArray(Charsets.UTF_8))

            for (file in attachments) {
                require(file.fileName.all { it.code in 0x20..0x7E && it != '"' }) { "첨부 이름은 ASCII 여야 한다: ${file.fileName}" }
                append("--").append(boundary).append(CRLF)
                header("Content-Type", "${file.contentType}; name=\"${file.fileName}\"")
                header("Content-Disposition", "attachment; filename=\"${file.fileName}\"")
                header("Content-Transfer-Encoding", "base64")
                append(CRLF)
                appendBase64(file.bytes)
            }
            append("--").append(boundary).append("--").append(CRLF)
        }

    private fun StringBuilder.header(name: String, value: String) {
        // 머리글에 줄바꿈이 끼면 다른 머리글을 끼워 넣을 수 있다 (사용자 입력이 들어가는 곳)
        require('\r' !in value && '\n' !in value) { "머리글에 줄바꿈이 있다: $name" }
        append(name).append(": ").append(value).append(CRLF)
    }

    private fun StringBuilder.appendBase64(bytes: ByteArray) {
        val encoded = Base64.getEncoder().encodeToString(bytes)
        var i = 0
        while (i < encoded.length) {
            append(encoded, i, minOf(i + LINE, encoded.length)).append(CRLF)
            i += LINE
        }
    }

    companion object {
        const val CRLF = "\r\n"
        private const val LINE = 76

        // 한 덩어리에 담는 UTF-8 바이트 수. base64 60자 + "=?UTF-8?B?"·"?=" 12자 = 72자로 75자 상한 안이다
        private const val WORD_BYTES = 45

        /**
         * ASCII 만 있으면 그대로, 아니면 =?UTF-8?B?...?= 로 감싼다.
         * 한 덩어리(encoded-word)는 75자를 넘지 못하므로(RFC 2047 2절) 글자 경계에서 나눠 공백으로 잇는다.
         * 받는 쪽은 이어진 덩어리 사이의 공백을 버리고 붙여 읽는다.
         */
        fun encodeWord(text: String): String {
            if (text.all { it.code in 0x20..0x7E }) return text
            val words = mutableListOf<String>()
            var chunk = ByteArray(0)
            var i = 0
            while (i < text.length) {
                val next = text.offsetByCodePoints(i, 1)
                val bytes = text.substring(i, next).toByteArray(Charsets.UTF_8)
                if (chunk.size + bytes.size > WORD_BYTES) {
                    words += word(chunk)
                    chunk = ByteArray(0)
                }
                chunk += bytes
                i = next
            }
            words += word(chunk)
            return words.joinToString(" ")
        }

        private fun word(bytes: ByteArray): String = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(bytes) + "?="
    }
}

object MailAddress {
    // 실용적인 범위만 본다: 로컬@도메인.최상위, 공백·꺾쇠·따옴표·줄바꿈 없음
    private val PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")

    fun isValid(address: String): Boolean = PATTERN.matches(address.trim())
}
