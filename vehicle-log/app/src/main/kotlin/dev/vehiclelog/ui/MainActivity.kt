package dev.vehiclelog.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.TripStore
import dev.vehiclelog.service.Settings
import dev.vehiclelog.service.TripService
import java.util.concurrent.Executors

/**
 * 첫 화면. 설치 직후에는 부팅 이벤트가 없으므로 여기서 운행 감지를 시작한다.
 * 시연용 재생 모드: `adb shell am start -n dev.vehiclelog/.ui.MainActivity --ez replay true`
 */
class MainActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { textSize = STATUS_TEXT_SP; setPadding(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX) }
        setContentView(status)
        applyReplayExtra(intent)
        TripService.start(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyReplayExtra(intent)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun applyReplayExtra(intent: Intent?) {
        if (intent?.hasExtra(EXTRA_REPLAY) != true) return
        val enabled = intent.getBooleanExtra(EXTRA_REPLAY, false)
        if (enabled == Settings.replayMode(this)) return
        Settings.setReplayMode(this, enabled)
        AppLog.info("settings.replay", "enabled" to enabled)
        // 신호 출처는 서비스 생성 시 정하므로 다시 띄운다
        stopService(Intent(this, TripService::class.java))
        TripService.start(this)
    }

    private fun refresh() {
        val replay = Settings.replayMode(this)
        io.execute {
            val count = TripStore(this).use { it.tripCount() }
            runOnUiThread {
                status.text = "운행 감지 중\n신호: ${if (replay) "재생(시연)" else "실차"}\n저장된 운행: ${count}건"
            }
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_REPLAY = "replay"
        private const val STATUS_TEXT_SP = 24f
        private const val PADDING_PX = 48
    }
}
