package dev.vehiclelog.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Toast
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.AdminInfo
import dev.vehiclelog.data.AdminPin
import dev.vehiclelog.data.MailAccount
import dev.vehiclelog.data.VehicleInfo
import dev.vehiclelog.mail.MailAddress
import dev.vehiclelog.service.Consent
import dev.vehiclelog.service.TripService
import java.util.concurrent.Executors

/**
 * 관리자 등록. 앱을 처음 설치하면 메인 화면이 먼저 이 화면을 연다.
 * 이름·사업자명·메일과 관리자 비밀번호(숫자 4자리)를 받는다. 그 뒤 앱 관리는 이 비밀번호를 아는 관리자만 한다.
 * 사업자명은 운행기록표 머리의 법인명([VehicleInfo.corporation])과 같은 값이다.
 * 등록한 메일로 운행기록표가 간다. 보내는 Gmail 계정도 여기서 입력해 이 기기에만 저장한다 (APK 에 넣지 않는다).
 * 입력 칸이 있으므로 주행 중에는 시스템이 막는다 (distractionOptimized 아님).
 * 개인정보 수집 안내를 다시 보고, 동의를 철회(모든 기록 삭제)하는 곳도 여기다.
 */
class AdminActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()

    private val firstRun get() = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val current = AdminInfo.load(this)
        val root = Ui.column(this)
        root.addView(Ui.header(this, Ui.title(this, if (firstRun) "관리자 등록" else "관리자 정보")))
        root.addView(
            Ui.body(
                this,
                if (firstRun) {
                    "처음 한 번만 등록합니다. 운행기록표를 내보내면 이 메일로 엑셀 파일이 갑니다.\n" +
                        "앱 관리(내보내기·사용자·차량 정보·관리자 정보)는 여기서 정한 비밀번호를 넣어야 열립니다. " +
                        "비밀번호를 잊으면 되찾을 수 없으니 따로 적어 두세요."
                } else {
                    "운행기록표를 받을 메일입니다."
                },
            ),
        )
        val name = Ui.input(this, "관리자 이름", current?.name.orEmpty())
        val email = Ui.input(this, "메일 주소", current?.email.orEmpty()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val vehicle = VehicleInfo.load(this)
        val corporation = Ui.input(this, "사업자명 (법인명)", vehicle.corporation)
        // 비밀번호가 아직 없으면(처음 등록, 비밀번호가 생기기 전 버전에서 올라온 경우) 꼭 받는다
        val needPin = !AdminPin.isSet(this)
        val pin = PinActivity.input(this, if (needPin) "관리자 비밀번호 (숫자 ${AdminPin.LENGTH}자리)" else "새 관리자 비밀번호 (바꿀 때만)")
        val pinAgain = PinActivity.input(this, if (needPin) "관리자 비밀번호 한 번 더" else "새 관리자 비밀번호 한 번 더")
        root.addView(name)
        root.addView(corporation)
        root.addView(email)
        root.addView(pin)
        root.addView(pinAgain)

        // 보내는 계정은 나중에 입력해도 된다. 없으면 내보내기에서 안내한다
        val account = MailAccount.load(this)
        root.addView(
            Ui.body(
                this,
                "보내는 Gmail 계정 (발신 전용 계정을 쓰세요)\n" +
                    "비밀번호 칸에는 Google 계정 → 보안 → 앱 비밀번호에서 만든 16자리를 넣습니다." +
                    if (account != null) "\n저장된 비밀번호가 있습니다. 바꿀 때만 다시 입력하세요." else "",
            ),
        )
        val senderAddress = Ui.input(this, "보내는 Gmail 주소", account?.address.orEmpty()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        // 저장된 비밀번호는 화면에 다시 보여 주지 않는다
        val senderPassword = Ui.input(this, if (account != null) "앱 비밀번호 (바꿀 때만)" else "앱 비밀번호 16자리", password = true)
        root.addView(senderAddress)
        root.addView(senderPassword)
        root.addView(
            Ui.button(this, if (firstRun) "등록" else "저장", filled = true) {
                val address = email.text.toString().trim()
                if (name.text.isBlank()) {
                    Toast.makeText(this, "관리자 이름을 입력해 주세요", Toast.LENGTH_SHORT).show()
                    return@button
                }
                if (corporation.text.isBlank()) {
                    Toast.makeText(this, "사업자명을 입력해 주세요", Toast.LENGTH_SHORT).show()
                    return@button
                }
                if (!MailAddress.isValid(address)) {
                    Toast.makeText(this, "메일 주소를 확인해 주세요", Toast.LENGTH_SHORT).show()
                    return@button
                }
                val newPin = pin.text.toString()
                if (needPin || newPin.isNotEmpty() || pinAgain.text.isNotEmpty()) {
                    if (!AdminPin.isValidFormat(newPin)) {
                        Toast.makeText(this, "관리자 비밀번호는 숫자 ${AdminPin.LENGTH}자리로 입력해 주세요", Toast.LENGTH_SHORT).show()
                        return@button
                    }
                    if (newPin != pinAgain.text.toString()) {
                        Toast.makeText(this, "관리자 비밀번호 두 칸이 서로 다릅니다", Toast.LENGTH_SHORT).show()
                        return@button
                    }
                }
                val sender = senderAddress.text.toString().trim()
                val typedPassword = MailAccount.normalizePassword(senderPassword.text.toString())
                val password = typedPassword.ifEmpty { account?.appPassword.orEmpty() }
                // 둘 다 비우면 나중에 입력하는 것으로 본다. 하나만 넣었거나 형식이 틀리면 알려 준다
                val senderGiven = sender.isNotEmpty() || typedPassword.isNotEmpty()
                if (senderGiven && !MailAddress.isValid(sender)) {
                    Toast.makeText(this, "보내는 Gmail 주소를 확인해 주세요", Toast.LENGTH_SHORT).show()
                    return@button
                }
                if (senderGiven && password.isEmpty()) {
                    Toast.makeText(this, "앱 비밀번호를 입력해 주세요", Toast.LENGTH_SHORT).show()
                    return@button
                }
                AdminInfo.save(this, AdminInfo(name.text.toString(), address))
                VehicleInfo.save(this, VehicleInfo.load(this).copy(corporation = corporation.text.toString()))
                if (newPin.isNotEmpty()) AdminPin.set(this, newPin)
                // 방금 등록한 사람이 관리자다. 이어서 설정을 마치도록 관리자 메뉴를 열어 둔다
                if (firstRun) AdminSession.unlock()
                if (senderGiven) MailAccount.save(this, sender, password)
                AppLog.info("admin.saved", "firstRun" to firstRun)
                Toast.makeText(this, "저장했습니다", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            },
        )
        if (!firstRun) {
            root.addView(Ui.body(this, "개인정보"))
            root.addView(
                Ui.button(this, "개인정보 수집 안내 보기") {
                    Ui.popup(this, Consent.TITLE, Consent.NOTICE, confirm = "닫기", messageGravity = Gravity.START).show()
                },
            )
            root.addView(Ui.button(this, "동의 철회 · 모든 기록 삭제") { confirmWithdraw() })
        }
        setContentView(Ui.screen(this, root))
    }

    private fun confirmWithdraw() {
        Ui.popup(
            this,
            title = "동의를 철회할까요?",
            message = "저장된 운행 기록, 사용자, 관리자, 메일 계정, 차량 정보가 모두 지워지고 운행 기록을 멈춥니다. " +
                "지운 기록은 되돌릴 수 없습니다. 필요하면 먼저 운행기록표를 보내 두세요.",
            confirm = "철회하고 삭제",
            cancel = "취소",
        ) { withdraw() }.show()
    }

    private fun withdraw() {
        // 서비스를 먼저 멈춰 지우는 동안 새 운행이 쌓이지 않게 한다
        stopService(Intent(this, TripService::class.java))
        AdminSession.lock()
        val app = applicationContext
        io.execute {
            Consent.withdrawAndErase(app)
            runOnUiThread {
                Toast.makeText(app, "동의를 철회하고 모든 기록을 지웠습니다", Toast.LENGTH_SHORT).show()
                // 첫 화면으로 돌아가면 동의를 다시 묻는다
                startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                finish()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 처음 등록은 비밀번호가 생기기 전이라 그냥 연다. 그 뒤로는 관리 화면이다
        if (!firstRun) AdminSession.requireUnlocked(this)
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    // minSdk 28 이라 뒤로 가기 콜백(OnBackInvokedCallback, 33+)을 쓸 수 없어 이전 방식을 쓴다
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // 처음 등록은 건너뛸 수 없다. 메인 화면으로 돌아가도 다시 열리므로 앱을 닫는다
        if (firstRun && (AdminInfo.load(this) == null || !AdminPin.isSet(this))) finishAffinity() else super.onBackPressed()
    }

    companion object {
        private const val EXTRA_FIRST_RUN = "first_run"

        fun intent(context: Context, firstRun: Boolean = false): Intent =
            Intent(context, AdminActivity::class.java).putExtra(EXTRA_FIRST_RUN, firstRun)
    }
}
