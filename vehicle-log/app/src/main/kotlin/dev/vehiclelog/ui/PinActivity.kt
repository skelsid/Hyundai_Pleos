package dev.vehiclelog.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.AdminPin

/**
 * 관리자 비밀번호 확인. 맞으면 관리 메뉴를 연다 ([AdminSession]).
 * 입력 칸이 있으므로 주행 중에는 시스템이 막는다 (distractionOptimized 아님).
 */
class PinActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.column(this)
        root.addView(Ui.header(this, Ui.title(this, "관리자 비밀번호")))
        root.addView(Ui.body(this, "관리 메뉴는 관리자만 쓸 수 있습니다. 등록할 때 정한 숫자 ${AdminPin.LENGTH}자리를 넣어 주세요."))
        val pin = input(this, "비밀번호 ${AdminPin.LENGTH}자리")
        val message = Ui.body(this, "")
        root.addView(pin)
        root.addView(message)
        root.addView(Ui.button(this, "확인", filled = true) { check(pin, message) })
        setContentView(Ui.screen(this, root))
    }

    private fun check(pin: TextView, message: TextView) {
        when (val result = AdminPin.check(this, pin.text.toString())) {
            AdminPin.Result.Ok -> {
                AppLog.info("admin.unlocked")
                AdminSession.unlock()
                finish()
            }
            is AdminPin.Result.Wrong -> {
                AppLog.warn("admin.pin.wrong", "left" to result.left)
                pin.text = ""
                message.text = "비밀번호가 맞지 않습니다. ${result.left}번 더 틀리면 잠시 잠깁니다."
            }
            is AdminPin.Result.Locked -> {
                AppLog.warn("admin.pin.locked")
                pin.text = ""
                val seconds = ((result.untilMillis - System.currentTimeMillis()) / MILLIS_PER_SECOND).coerceAtLeast(1)
                message.text = "여러 번 틀려 잠겼습니다. ${seconds}초 뒤에 다시 넣어 주세요."
            }
        }
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1_000L

        /** 숫자 4자리만 들어가는 비밀번호 칸. */
        fun input(context: Context, hint: String): EditText = Ui.input(context, hint, password = true).apply {
            // 비밀번호 입력 방식으로 바꾸면 글꼴이 고정폭으로 바뀌므로 되돌린다
            val font = typeface
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            typeface = font
            filters = arrayOf(InputFilter.LengthFilter(AdminPin.LENGTH))
        }
    }
}
