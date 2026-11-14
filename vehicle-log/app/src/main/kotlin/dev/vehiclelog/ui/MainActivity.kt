package dev.vehiclelog.ui

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.AdminInfo
import dev.vehiclelog.data.AdminPin
import dev.vehiclelog.data.TripStore
import dev.vehiclelog.service.Consent
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
 * 처음 실행하면 개인정보 수집 동의 → 관리자 등록 → 종료 화면 자동 표시 허용 순서로 안내한다.
 * 동의하지 않으면 운행을 기록하지 않고, 동의 버튼만 남긴다.
 * 운전자는 운행 분류와 월별 기록 보기·분류 수정만 한다. 나머지(내보내기·사용자·차량·관리자 정보·설정)는 관리자 메뉴이고,
 * 관리자 비밀번호([PinActivity])를 넣은 뒤 앱이 화면에 떠 있는 동안만 보인다 ([AdminSession]).
 * 주행 중 제한이 걸리면 설정·관리 화면(distractionOptimized 아님)으로 가는 버튼을 끄고, 첫 실행 안내도 정차 뒤로 미룬다.
 * 시연용 재생 모드: `adb shell am start --user 10 -n dev.vehiclelog/.ui.MainActivity --ez replay true` 뒤 확인 창에서 [바꾸기]
 */
class MainActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var classifyButton: Button
    private lateinit var screenAccessButton: Button
    private lateinit var replayButton: Button
    private lateinit var drivingNote: TextView
    private lateinit var consentButton: Button
    private lateinit var unlockButton: Button
    private var consentPopup: Dialog? = null

    // 이 화면에서 동의를 거절했으면 다시 묻지 않는다 (동의 버튼으로만 다시 연다)
    private var consentDeclined = false
    private lateinit var drivingState: DrivingState
    private var rules = DrivingRules.PARKED

    // 주행 중에는 시스템이 막는 화면으로 가는 버튼
    private val parkedOnly = mutableListOf<Button>()

    // 관리자 비밀번호를 넣어야 보이는 버튼
    private val adminOnly = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdminSession.watch(application)
        val root = Ui.column(this)
        status = Ui.body(this, "")
        classifyButton = Ui.button(this, "미분류 운행 분류") { startActivity(TripEndActivity.intent(this)) }
        root.addView(Ui.header(this, Ui.title(this, "차량운행기록"), main = true))
        root.addView(status)
        consentButton = Ui.button(this, Consent.TITLE, filled = true) { showConsent() }
        root.addView(consentButton)
        root.addView(classifyButton)
        drivingNote = Ui.body(this, "주행 중에는 운행 분류만 쓸 수 있습니다. 나머지는 정차 후 이용해 주세요.").apply {
            visibility = View.GONE
        }
        root.addView(drivingNote)
        // 기록을 보고 분류를 고치는 것은 운전자도 한다 (내보내기는 그 화면 안에서 관리자만)
        addParkedOnly(root, Ui.button(this, "월별 운행기록 · 분류 수정") { startActivity(Intent(this, MonthlyActivity::class.java)) })
        // 비밀번호 화면은 입력 칸이 있어 주행 중에는 시스템이 막는다
        unlockButton = Ui.button(this, "관리자 메뉴 열기") { startActivity(Intent(this, PinActivity::class.java)) }
        addParkedOnly(root, unlockButton)
        addAdminOnly(root, Ui.button(this, "사용자 관리") { startActivity(UsersActivity.adminIntent(this)) })
        addAdminOnly(root, Ui.button(this, "차량 정보") { startActivity(Intent(this, VehicleInfoActivity::class.java)) })
        addAdminOnly(root, Ui.button(this, "관리자 정보") { startActivity(AdminActivity.intent(this)) })
        // 다른 앱 위에 표시까지 받으면 더 물을 게 없어 숨긴다 (updateAccess). 그래서 adminOnly 에 넣지 않는다
        screenAccessButton = Ui.button(this, "운행 종료 화면 자동 표시 켜기") { askScreenAccess() }
        addParkedOnly(root, screenAccessButton)
        // 에뮬레이터는 기어·주행거리를 바꿀 수 없다. 심사·시연 때 adb 없이 켤 수 있게 화면에 둔다
        replayButton = Ui.button(this, "") { confirmReplay(!Settings.replayMode(this)) }
        addAdminOnly(root, replayButton)
        // 잠그기는 주행 중에도 된다
        val lockButton = Ui.button(this, "관리자 메뉴 잠그기") {
            AdminSession.lock()
            updateAccess()
        }
        adminOnly += lockButton
        root.addView(lockButton)
        setContentView(Ui.screen(this, root))
        drivingState = DrivingState(this) { applyRules(it) }
        drivingState.start()
        applyReplayExtra(intent)
        // 동의 전이면 시작하지 않는다 (start 가 확인한다)
        TripService.start(this)
    }

    private fun addParkedOnly(root: LinearLayout, button: Button) {
        parkedOnly += button
        root.addView(button)
    }

    private fun addAdminOnly(root: LinearLayout, button: Button) {
        adminOnly += button
        addParkedOnly(root, button)
    }

    private fun applyRules(next: DrivingRules) {
        val parked = rules.restricted && !next.restricted
        rules = next
        updateAccess()
        // 주행 중이라 미룬 첫 실행 안내를 정차하면 이어 한다
        if (parked && hasWindowFocus()) firstRunSteps()
    }

    /**
     * 주행 중이거나 동의 전이면 관리·설정 버튼을 끈다. 동의 안내(긴 글)도 주행 중에는 열지 않는다.
     * 관리 버튼은 관리자 비밀번호를 넣은 동안만 보인다.
     */
    private fun updateAccess() {
        val agreed = Consent.agreed(this)
        val unlocked = AdminSession.unlocked
        parkedOnly.forEach { it.isEnabled = agreed && !rules.restricted }
        adminOnly.forEach { it.visibility = if (unlocked) View.VISIBLE else View.GONE }
        // 비밀번호를 정하기 전(처음 등록 전)에는 열 것이 없다
        unlockButton.visibility = if (agreed && !unlocked && AdminPin.isSet(this)) View.VISIBLE else View.GONE
        screenAccessButton.visibility = if (unlocked && !ScreenAccess.canDrawOverApps(this)) View.VISIBLE else View.GONE
        consentButton.visibility = if (agreed) View.GONE else View.VISIBLE
        consentButton.isEnabled = !rules.restricted
        drivingNote.visibility = if (rules.restricted) View.VISIBLE else View.GONE
    }

    private fun showConsent() {
        if (consentPopup?.isShowing == true || isFinishing) return
        val popup = Ui.popup(
            this,
            title = Consent.TITLE,
            message = Consent.NOTICE,
            confirm = "동의",
            cancel = "동의 안 함",
            messageGravity = Gravity.START,
        ) {
            Consent.agree(this)
            consentDeclined = false
            TripService.start(this)
            refresh()
            firstRunSteps()
        }
        // [동의] 를 누른 경우만 동의로 본다. 뒤로 가기·바깥 터치·[동의 안 함] 은 거절
        popup.setOnDismissListener {
            if (!Consent.agreed(this)) {
                consentDeclined = true
                AppLog.info("consent.declined")
                refresh()
            }
        }
        consentPopup = popup
        popup.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyReplayExtra(intent)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        firstRunSteps()
    }

    /** 처음 실행 안내. 관리자 등록·설정 화면은 주행 중 막히므로 정차했을 때만 연다. */
    private fun firstRunSteps() {
        if (rules.restricted) return
        when {
            !Consent.agreed(this) -> if (!consentDeclined) showConsent()
            // 비밀번호가 생기기 전 버전에서 관리자만 등록한 경우에도 비밀번호를 받는다
            AdminInfo.load(this) == null || !AdminPin.isSet(this) -> startActivity(AdminActivity.intent(this, firstRun = true))
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
        val popup = if (settings == null) {
            Ui.popup(
                this,
                title = "종료 화면 알림",
                message = if (ScreenAccess.canShowTripEnd(this)) {
                    "운행이 끝나면 알림으로 종료 화면을 띄웁니다."
                } else {
                    "이 차량에서는 허용 설정 화면을 열 수 없습니다. 운행이 끝나면 알림으로 알려 드립니다."
                },
                confirm = "확인",
            )
        } else {
            Ui.popup(
                this,
                title = "다른 앱 위에 표시",
                message = "다른 앱을 보고 있어도 운행이 끝나면 종료 화면을 띄우려면, 다음 설정 화면에서 이 앱을 허용해 주세요.",
                confirm = "설정 열기",
                cancel = "나중에",
            ) { startActivity(settings) }
        }
        popup.show()
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
        val title = if (enabled) "시연 모드로 바꿀까요?" else "실차 신호로 바꿀까요?"
        val message = if (enabled) {
            "실차 신호 대신 시연용 가짜 운행이 기록에 추가됩니다."
        } else {
            "시연용 재생을 멈추고 차량 신호로 운행을 기록합니다."
        }
        Ui.popup(this, title, message, confirm = "바꾸기", cancel = "취소") { setReplay(enabled) }.show()
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
            val departure = TireStatus.loadDeparture(this)
            runOnUiThread {
                val tireText = tire?.let { "${it.summary} (${TIME.format(Date(it.timeMillis))} 출발 시)" } ?: "아직 점검 전"
                val screen = when {
                    ScreenAccess.canDrawOverApps(this) -> "켜짐"
                    ScreenAccess.canShowTripEnd(this) -> "켜짐(알림)"
                    else -> "꺼짐"
                }
                status.text = if (!Consent.agreed(this)) {
                    "개인정보 수집·이용에 동의하지 않아 운행을 기록하지 않습니다."
                } else {
                    "운행 감지 중 · 신호: ${if (replay) "재생(시연)" else "실차"}\n저장된 운행: ${count}건\n" +
                        "타이어 공기압: $tireText\n" +
                        "소모품·연료: ${departure ?: "아직 점검 전"}\n종료 화면 자동 표시: $screen"
                }
                updateAccess()
                replayButton.text = if (replay) "시연 모드 끄기 (실차 신호로)" else "시연 모드 켜기 (가짜 운행 재생)"
                classifyButton.text = "미분류 운행 분류 (${unclassified}건)"
                // 동의 전에는 남아 있던 기록도 다루지 않는다
                classifyButton.isEnabled = unclassified > 0 && Consent.agreed(this)
            }
        }
    }

    override fun onDestroy() {
        consentPopup?.dismiss()
        drivingState.stop()
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private val TIME = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)
        private const val EXTRA_REPLAY = "replay"
        private const val REQUEST_NOTIFICATIONS = 1
    }
}
