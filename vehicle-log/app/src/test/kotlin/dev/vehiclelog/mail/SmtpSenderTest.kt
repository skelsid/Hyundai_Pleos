package dev.vehiclelog.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import kotlin.concurrent.thread

/** 가짜 SMTP 서버와 대화해 보낸 명령 순서와 오류 처리를 확인한다. */
class SmtpSenderTest {

    private val message = MailMessage("sender@example.com", "Hyundai Vehicle Log", "admin@example.com", "제목", "본문")

    /** [authCode] 로 로그인 응답을 주는 서버를 띄우고, 받은 줄들을 돌려준다. */
    private fun runServer(authCode: Int = 235, rcptCode: Int = 250, block: (SmtpSender) -> Unit): List<String> {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val received = mutableListOf<String>()
        val worker = thread {
            server.accept().use { s ->
                val input = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val out = s.getOutputStream()
                fun reply(text: String) = out.write((text + "\r\n").toByteArray()).also { out.flush() }
                reply("220 fake ESMTP")
                var inData = false
                while (true) {
                    val line = input.readLine() ?: break
                    received += line
                    if (inData) {
                        if (line == ".") {
                            inData = false
                            reply("250 2.0.0 OK queued")
                        }
                        continue
                    }
                    when {
                        line.startsWith("EHLO") -> { reply("250-fake"); reply("250-AUTH PLAIN LOGIN"); reply("250 SIZE 35882577") }
                        line.startsWith("AUTH") -> reply(if (authCode == 235) "235 2.7.0 Accepted" else "$authCode 5.7.8 Bad credentials")
                        line.startsWith("MAIL") -> reply("250 OK")
                        line.startsWith("RCPT") -> reply("$rcptCode recipient")
                        line == "DATA" -> { inData = true; reply("354 Go ahead") }
                        line == "QUIT" -> { reply("221 bye"); break }
                    }
                }
            }
        }
        try {
            block(SmtpSender("localhost", server.localPort) { host, port -> Socket(host, port) })
        } finally {
            worker.join(5000)
            server.close()
        }
        return received
    }

    @Test
    fun test_send_success_followsSmtpOrder() {
        val lines = runServer { it.send("sender@example.com", "pw", message) }
        val commands = lines.filter { it.matches(Regex("^(EHLO|AUTH|MAIL|RCPT|DATA|QUIT).*")) }.map { it.substringBefore(' ') }
        assertEquals(listOf("EHLO", "AUTH", "MAIL", "RCPT", "DATA", "QUIT"), commands)
        val token = lines.first { it.startsWith("AUTH PLAIN ") }.removePrefix("AUTH PLAIN ")
        assertEquals("\u0000sender@example.com\u0000pw", String(Base64.getDecoder().decode(token)))
        assertTrue(lines.contains("RCPT TO:<admin@example.com>"))
        assertTrue(lines.contains("."))
    }

    @Test
    fun test_send_badPassword_loginError() {
        runServer(authCode = 535) {
            try {
                it.send("sender@example.com", "wrong", message)
                fail()
            } catch (e: MailException) {
                assertEquals(MailException.Kind.LOGIN, e.kind)
            }
        }
    }

    @Test
    fun test_send_recipientRejected_rejectedError() {
        runServer(rcptCode = 550) {
            try {
                it.send("sender@example.com", "pw", message)
                fail()
            } catch (e: MailException) {
                assertEquals(MailException.Kind.REJECTED, e.kind)
            }
        }
    }

    @Test
    fun test_send_noAccount_notConfigured() {
        try {
            SmtpSender("localhost", 1) { _, _ -> error("연결하면 안 된다") }.send("", "", message)
            fail()
        } catch (e: MailException) {
            assertEquals(MailException.Kind.NOT_CONFIGURED, e.kind)
        }
    }

    @Test
    fun test_send_cannotConnect_networkError() {
        try {
            SmtpSender("localhost", 1) { _, _ -> throw java.io.IOException("refused") }.send("a@b.com", "pw", message)
            fail()
        } catch (e: MailException) {
            assertEquals(MailException.Kind.NETWORK, e.kind)
        }
    }

    @Test
    fun test_send_newlineInRecipient_rejectedBeforeConnecting() {
        val injected = MailMessage("sender@example.com", "n", "admin@example.com\r\nRCPT TO:<x@evil.example>", "s", "b")
        try {
            SmtpSender("localhost", 1) { _, _ -> error("연결하면 안 된다") }.send("sender@example.com", "pw", injected)
            fail()
        } catch (e: MailException) {
            assertEquals(MailException.Kind.REJECTED, e.kind)
        }
    }

    @Test
    fun test_dotStuff_leadingDotDoubled() {
        assertEquals("a\r\n..b\r\nc.", SmtpSender.dotStuff("a\r\n.b\r\nc."))
    }
}
