package dev.vehiclelog.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.TripStore
import dev.vehiclelog.data.User
import java.util.concurrent.Executors

/**
 * 사용자 후보 관리. 운행 종료 화면에서 이 목록 중에 고른다.
 * 입력 칸이 있으므로 주행 중에는 시스템이 막는다 (distractionOptimized 아님).
 * 메인 화면의 관리자 메뉴로 들어오면 관리자 비밀번호가 필요하다. 운행 종료 화면의 [사용자 추가]로 들어오면 필요 없다.
 */
class UsersActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.column(this)
        root.addView(Ui.header(this, Ui.title(this, "사용자 관리")))
        val name = Ui.input(this, "이름")
        val department = Ui.input(this, "부서 (선택)")
        root.addView(name)
        root.addView(department)
        root.addView(
            Ui.button(this, "추가") {
                val nameText = name.text.toString().trim()
                if (nameText.isEmpty()) {
                    Toast.makeText(this, "이름을 입력해 주세요", Toast.LENGTH_SHORT).show()
                    return@button
                }
                val departmentText = department.text.toString().trim().ifEmpty { null }
                io.execute {
                    val id = TripStore.get(this).addUser(nameText, departmentText)
                    AppLog.info("user.added", "id" to id)
                    runOnUiThread {
                        name.text.clear()
                        department.text.clear()
                        reload()
                    }
                }
            },
        )
        list = Ui.column(this).apply { setPadding(0, 0, 0, 0) }
        root.addView(list)
        setContentView(Ui.screen(this, root))
        reload()
    }

    private fun reload() {
        // 추가·삭제 결과가 화면을 닫은 뒤에 도착하면 끝난 실행기에 일을 넣지 않는다
        if (isDestroyed) return
        io.execute {
            val users = TripStore.get(this).activeUsers()
            runOnUiThread { render(users) }
        }
    }

    private fun render(users: List<User>) {
        if (isFinishing) return
        list.removeAllViews()
        if (users.isEmpty()) list.addView(Ui.body(this, "등록된 사용자가 없습니다."))
        for (user in users) {
            val label = user.name + (user.department?.let { " ($it)" } ?: "")
            val remove = Ui.button(this, "삭제", medium = true) { confirmRemove(user, label) }
            list.addView(Ui.listItem(this, user.name, user.department, suffix = remove))
        }
    }

    private fun confirmRemove(user: User, label: String) {
        Ui.popup(
            this,
            title = "$label 을(를) 뺄까요?",
            message = "운행 종료 화면의 사용자 후보에서 빠집니다. 지난 운행 기록은 그대로 남습니다.",
            confirm = "빼기",
            cancel = "취소",
        ) {
            io.execute {
                TripStore.get(this).deactivateUser(user.id)
                AppLog.info("user.deactivated", "id" to user.id)
                runOnUiThread { reload() }
            }
        }.show()
    }

    override fun onStart() {
        super.onStart()
        // 관리자 메뉴로 들어왔는데 앱을 떠났다 돌아와 잠겼으면 닫는다
        if (intent.getBooleanExtra(EXTRA_ADMIN_MENU, false)) AdminSession.requireUnlocked(this)
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ADMIN_MENU = "admin_menu"

        /** 메인 화면의 관리자 메뉴에서 여는 경우. */
        fun adminIntent(context: Context): Intent =
            Intent(context, UsersActivity::class.java).putExtra(EXTRA_ADMIN_MENU, true)
    }
}
