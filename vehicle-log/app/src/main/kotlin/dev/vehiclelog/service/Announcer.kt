package dev.vehiclelog.service

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.vehiclelog.AppLog
import java.util.Locale

/**
 * 음성 안내. 차 안에서 처리하는 ON_DEVICE 모드라 키·네트워크가 필요 없다.
 * 초기화 전에 들어온 안내는 마지막 하나만 보관했다가 초기화 직후 말한다.
 */
class Announcer(context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context, Mode.ON_DEVICE)
    private var ready = false
    private var pending: String? = null

    fun start() {
        runCatching {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    handler.post {
                        tts.setLocale(Locale.KOREA)
                        ready = true
                        pending?.let(::speakNow)
                        pending = null
                    }
                }

                override fun onFailure(error: ErrorType) {
                    AppLog.warn("tts.init.error", "error" to error)
                }
            })
        }.onFailure { AppLog.error("tts.init.exception", it) }
    }

    /** 메인 스레드에서 부른다. */
    fun speak(text: String) {
        if (ready) speakNow(text) else pending = text
    }

    private fun speakNow(text: String) {
        runCatching { tts.speak(text, true) }
            .onSuccess { AppLog.info("tts.speak", "text" to text) }
            .onFailure { AppLog.error("tts.speak.exception", it) }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        runCatching { tts.release() }
    }
}
