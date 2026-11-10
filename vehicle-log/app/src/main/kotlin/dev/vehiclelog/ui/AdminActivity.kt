package dev.vehiclelog.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.AdminInfo
import dev.vehiclelog.mail.MailAddress

/**
 * 관리자 등록. 앱을 처음 설치하면 메인 화면이 먼저 이 화면을 연다.
 * 등록한 메일로 운행기록표가 간다. 입력 칸이 있으므로 주행 중에는 시스템이 막는다 (distractionOptimized 아님).
 */
class AdminActivity : Activity() {

    private val firstRun get() = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val current = AdminInfo.load(this)
        val root = Ui.column(this)
        root.addView(Ui.title(this, if (firstRun) "관리자 등록" else "관리자 정보"))
        root.addView(
            Ui.body(
                this,
                if (firstRun) {
                    "처음 한 번만 등록합니다. 운행기록표를 내보내면 이 메일로 엑셀 파일이 갑니다."
                } else {
                    "운행기록표를 받을 메일입니다."
                },
            ),
        )
        val name = Ui.input(this, "관리자 이름", current?.name.orEmpty())
        val email = Ui.input(this, "메일 주소", current?.email.orEmpty()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        root.addView(name)
        root.addView(email)
        root.addView(
            Ui.button(this, if (firstRun) "등록" else "저장") {
                val address = email.text.toString().trim()
                if (name.text.isBlank()) {
                    Toast.makeText(this, "관리자 이름을 입력해 주세요", Toast.LENGTH_SHORT).show()
                    return@button
                }
                if (!MailAddress.isValid(address)) {
                    Toast.makeText(this, "메일 주소를 확인해 주세요", Toast.LENGTH_SHORT).show()
                    return@button
                }
                AdminInfo.save(this, AdminInfo(name.text.toString(), address))
                AppLog.info("admin.saved", "firstRun" to firstRun)
                Toast.makeText(this, "저장했습니다", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            },
        )
        setContentView(Ui.screen(this, root))
    }

    // minSdk 28 이라 뒤로 가기 콜백(OnBackInvokedCallback, 33+)을 쓸 수 없어 이전 방식을 쓴다
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // 처음 등록은 건너뛸 수 없다. 메인 화면으로 돌아가도 다시 열리므로 앱을 닫는다
        if (firstRun && AdminInfo.load(this) == null) finishAffinity() else super.onBackPressed()
    }

    companion object {
        private const val EXTRA_FIRST_RUN = "first_run"

        fun intent(context: Context, firstRun: Boolean = false): Intent =
            Intent(context, AdminActivity::class.java).putExtra(EXTRA_FIRST_RUN, firstRun)
    }
}
