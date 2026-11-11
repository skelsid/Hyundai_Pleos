package dev.vehiclelog.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.AdminInfo
import dev.vehiclelog.data.TripStore
import dev.vehiclelog.service.ScreenAccess
import dev.vehiclelog.service.Settings
import dev.vehiclelog.service.TireStatus
import dev.vehiclelog.service.TripService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 첫 화면. 설치 직후에는 부팅 이벤트가 없으므로 여기서 운행 감지를 시작한다.
 * 처음 실행하면 관리자 등록 → 종료 화면 자동 표시 허용 순서로 안내한다.
 * 시연용 재생 모드: `adb shell am start --user 10 -n dev.vehiclelog/.ui.MainActivity --ez replay true` 뒤 확인 창에서 [바꾸기]
 */
class MainActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var classifyButton: Button
    private lateinit var screenAccessButton: Button
    private lateinit var replayButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.column(this)
        status = Ui.body(this, "")
        classifyButton = Ui.button(this, "미분류 운행 분류") { startActivity(TripEndActivity.intent(this)) }
        root.addView(Ui.title(this, "차량운행기록"))
        root.addView(status)
        root.addView(classifyButton)
        root.addView(Ui.button(this, "월별 운행기록") { startActivity(Intent(this, MonthlyActivity::class.java)) })
        root.addView(Ui.button(this, "사용자 관리") { startActivity(Intent(this, UsersActivity::class.java)) })
        root.addView(Ui.button(this, "차량 정보") { startActivity(Intent(this, VehicleInfoActivity::class.java)) })
        root.addView(Ui.button(this, "관리자 정보") { startActivity(AdminActivity.intent(this)) })
        screenAccessButton = Ui.button(this, "운행 종료 화면 자동 표시 켜기") { askScreenAccess() }
        root.addView(screenAccessButton)
        // 에뮬레이터는 기어·주행거리를 바꿀 수 없다. 심사·시연 때 adb 없이 켤 수 있게 화면에 둔다
        replayButton = Ui.button(this, "") { confirmReplay(!Settings.replayMode(this)) }
        root.addView(replayButton)
        setContentView(Ui.screen(this, root))
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
        when {
            AdminInfo.load(this) == null -> startActivity(AdminActivity.intent(this, firstRun = true))
            !Settings.screenAccessAsked(this) -> {
                Settings.setScreenAccessAsked(this)
                askScreenAccess()
            }
        }
    }

    /**
     * 앱이 뒤에 있어도 운행 종료 화면이 뜨도록 허용을 받는다.
     * 1) 알림 허용(안드로이드 13+) → 2) 다른 앱 위에 표시 (없으면 전체 화면 알림 허용).
     */
    private fun askScreenAccess() {
        // 안드로이드 12 이하는 notificationsAllowed 가 늘 true 라 13 이상에서만 묻는다
        if (!ScreenAccess.notificationsAllowed(this)) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            return
        }
        askOverlay()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATIONS) return
        AppLog.info("permission.notifications", "granted" to ScreenAccess.notificationsAllowed(this))
        askOverlay()
    }

    private fun askOverlay() {
        refresh()
        if (isFinishing || ScreenAccess.canDrawOverApps(this)) return
        val settings = ScreenAccess.overlaySettings(this)
            ?: ScreenAccess.fullScreenSettings(this)?.takeIf { !ScreenAccess.fullScreenAllowed(this) }
        val dialog = AlertDialog.Builder(this)
        if (settings == null) {
            dialog.setMessage(
                if (ScreenAccess.canShowTripEnd(this)) {
                    "운행이 끝나면 알림으로 종료 화면을 띄웁니다."
                } else {
                    "이 차량에서는 허용 설정 화면을 열 수 없습니다. 운행이 끝나면 알림으로 알려 드립니다."
                },
            ).setPositiveButton("확인", null)
        } else {
            dialog.setMessage("다른 앱을 보고 있어도 운행이 끝나면 종료 화면을 띄우려면, 다음 설정 화면에서 이 앱을 허용해 주세요.")
                .setPositiveButton("설정 열기") { _, _ -> startActivity(settings) }
                .setNegativeButton("나중에", null)
        }
        dialog.show()
    }

    /**
     * 화면이 외부에 열려 있어(exported) 다른 앱도 이 extra 를 보낼 수 있다.
     * 몰래 가짜 신호로 바뀌지 않도록 사람이 확인 창에서 눌러야만 바꾼다.
     */
    private fun applyReplayExtra(intent: Intent?) {
        if (intent?.hasExtra(EXTRA_REPLAY) != true) return
        val enabled = intent.getBooleanExtra(EXTRA_REPLAY, false)
        // 화면을 다시 만들 때 같은 요청으로 또 묻지 않게 지운다
        intent.removeExtra(EXTRA_REPLAY)
        if (enabled == Settings.replayMode(this)) return
        confirmReplay(enabled)
    }

    private fun confirmReplay(enabled: Boolean) {
        val message = if (enabled) {
            "시연 모드(재생 신호)로 바꿀까요?\n실차 신호 대신 시연용 가짜 운행이 기록에 추가됩니다."
        } else {
            "실차 신호로 바꿀까요?"
        }
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("바꾸기") { _, _ -> setReplay(enabled) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun setReplay(enabled: Boolean) {
        Settings.setReplayMode(this, enabled)
        AppLog.info("settings.replay", "enabled" to enabled)
        // 신호 출처는 서비스 생성 시 정하므로 다시 띄운다
        stopService(Intent(this, TripService::class.java))
        TripService.start(this)
        refresh()
    }

    private fun refresh() {
        val replay = Settings.replayMode(this)
        io.execute {
            val store = TripStore.get(this)
            val count = store.tripCount()
            val unclassified = store.unclassifiedCount()
            val tire = TireStatus.load(this)
            runOnUiThread {
                val tireText = tire?.let { "${it.summary} (${TIME.format(Date(it.timeMillis))} 출발 시)" } ?: "아직 점검 전"
                val screen = when {
                    ScreenAccess.canDrawOverApps(this) -> "켜짐"
                    ScreenAccess.canShowTripEnd(this) -> "켜짐(알림)"
                    else -> "꺼짐"
                }
                status.text = "운행 감지 중 · 신호: ${if (replay) "재생(시연)" else "실차"}\n저장된 운행: ${count}건\n" +
                    "타이어 공기압: $tireText\n종료 화면 자동 표시: $screen"
                // 다른 앱 위에 표시까지 받으면 더 물을 게 없으므로 버튼을 숨긴다
                screenAccessButton.visibility = if (ScreenAccess.canDrawOverApps(this)) android.view.View.GONE else android.view.View.VISIBLE
                replayButton.text = if (replay) "시연 모드 끄기 (실차 신호로)" else "시연 모드 켜기 (가짜 운행 재생)"
                classifyButton.text = "미분류 운행 분류 (${unclassified}건)"
                classifyButton.isEnabled = unclassified > 0
            }
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private val TIME = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)
        private const val EXTRA_REPLAY = "replay"
        private const val REQUEST_NOTIFICATIONS = 1
    }
}
