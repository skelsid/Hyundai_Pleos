package dev.vehiclelog.mail

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import javax.net.ssl.SSLSocketFactory

/** 메일을 보내지 못한 이유. 화면에 보여 줄 문장으로 바꿀 수 있게 종류를 나눈다. */
class MailException(val kind: Kind, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Kind { NOT_CONFIGURED, NETWORK, LOGIN, REJECTED }
}

/**
 * 최소한의 SMTP 클라이언트. Gmail(smtp.gmail.com:465, 처음부터 TLS)에 앱 비밀번호로 로그인해 보낸다.
 * 메일 라이브러리 없이 소켓으로 직접 말한다. 메인 스레드가 아닌 곳에서 부른다.
 *
 * @param connect 테스트에서 가짜 서버로 바꿔 끼우는 연결 함수
 */
class SmtpSender(
    private val host: String = GMAIL_HOST,
    private val port: Int = GMAIL_PORT,
    private val connect: (String, Int) -> Socket = ::tlsSocket,
) {
    fun send(user: String, password: String, message: MailMessage) {
        if (user.isBlank() || password.isBlank()) {
            throw MailException(MailException.Kind.NOT_CONFIGURED, "발신 계정이 설정되지 않았다")
        }
        if (!MailAddress.isValid(message.fromAddress)) {
            throw MailException(MailException.Kind.NOT_CONFIGURED, "발신 주소가 올바르지 않다")
        }
        // 주소는 SMTP 명령에 그대로 들어가므로 줄바꿈 등이 섞이면 연결 전에 막는다
        if (!MailAddress.isValid(message.to)) {
            throw MailException(MailException.Kind.REJECTED, "받는 주소가 올바르지 않다")
        }
        val socket = try {
            connect(host, port)
        } catch (e: Exception) {
            throw MailException(MailException.Kind.NETWORK, "메일 서버에 연결하지 못했다", e)
        }
        socket.use {
            try {
                it.soTimeout = TIMEOUT_MS
                val reader = BufferedReader(InputStreamReader(it.getInputStream(), Charsets.US_ASCII))
                val out = it.getOutputStream()
                Session(reader, out).run(user, password, message)
            } catch (e: MailException) {
                throw e
            } catch (e: Exception) {
                throw MailException(MailException.Kind.NETWORK, "메일 서버와 통신이 끊겼다", e)
            }
        }
    }

    private class Session(private val reader: BufferedReader, private val out: OutputStream) {

        fun run(user: String, password: String, message: MailMessage) {
            expect(read(), 220)
            command("EHLO vehiclelog.local", 250)
            val token = Base64.getEncoder().encodeToString("\u0000$user\u0000$password".toByteArray(Charsets.UTF_8))
            val auth = commandRaw("AUTH PLAIN $token")
            if (auth.code != 235) {
                throw MailException(MailException.Kind.LOGIN, "로그인 거부: ${auth.code}")
            }
            command("MAIL FROM:<${message.fromAddress}>", 250)
            val rcpt = commandRaw("RCPT TO:<${message.to}>")
            if (rcpt.code != 250 && rcpt.code != 251) {
                throw MailException(MailException.Kind.REJECTED, "받는 주소 거부: ${rcpt.code}")
            }
            command("DATA", 354)
            write(dotStuff(message.toMime()))
            write(".\r\n")
            expect(read(), 250)
            // QUIT 응답은 기다리지 않아도 이미 접수됐다
            runCatching { write("QUIT\r\n") }
        }

        private fun command(line: String, code: Int) = expect(commandRaw(line), code)

        private fun commandRaw(line: String): Reply {
            write(line + "\r\n")
            return read()
        }

        private fun expect(reply: Reply, code: Int) {
            if (reply.code != code) throw MailException(MailException.Kind.REJECTED, "예상 $code, 받은 ${reply.code}")
        }

        private fun write(text: String) {
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
        }

        /** 여러 줄 응답("250-...")은 마지막 줄("250 ...")까지 읽는다. */
        private fun read(): Reply {
            while (true) {
                val line = reader.readLine() ?: throw MailException(MailException.Kind.NETWORK, "서버가 연결을 끊었다")
                val code = line.take(3).toIntOrNull() ?: throw MailException(MailException.Kind.NETWORK, "알 수 없는 응답")
                if (line.length < 4 || line[3] != '-') return Reply(code)
            }
        }
    }

    private data class Reply(val code: Int)

    companion object {
        const val GMAIL_HOST = "smtp.gmail.com"
        const val GMAIL_PORT = 465
        private const val TIMEOUT_MS = 30_000

        /** 줄 맨 앞의 '.' 은 메시지 끝 표시와 헷갈리지 않게 하나 더 붙인다 (RFC 5321 4.5.2). */
        fun dotStuff(mime: String): String =
            (if (mime.startsWith(".")) ".$mime" else mime).replace("\r\n.", "\r\n..")

        private fun tlsSocket(host: String, port: Int): Socket {
            val socket = SSLSocketFactory.getDefault().createSocket() as javax.net.ssl.SSLSocket
            socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
            // 인증서 이름 확인을 켠다 (기본 소켓 팩토리는 호스트 이름을 확인하지 않는다)
            socket.sslParameters = socket.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            socket.startHandshake()
            return socket
        }
    }
}
