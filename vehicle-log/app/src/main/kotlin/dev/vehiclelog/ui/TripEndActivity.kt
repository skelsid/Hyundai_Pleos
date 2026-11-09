package dev.vehiclelog.ui

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.Trip
import dev.vehiclelog.data.TripStore
import dev.vehiclelog.data.User
import dev.vehiclelog.service.TripService
import dev.vehiclelog.trip.TripText
import dev.vehiclelog.trip.TripMath
import java.util.concurrent.Executors

/**
 * 운행 종료 화면. 사용자와 업무용/비업무만 고른다. 출퇴근은 업무용이다.
 * 정차(P) 상태에서 뜨지만, 주행 중 다시 열려도 막히지 않게 distractionOptimized 로 둔다 (입력 칸 없음).
 */
class TripEndActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private var trip: Trip? = null
    private var selectedUserId: Long? = null

    // 월별 기록표에서 이미 분류한 운행을 고치러 들어온 경우
    private var editing = false
    private val userButtons = mutableMapOf<Long, Pair<Button, User>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = Ui.column(this)
        setContentView(Ui.screen(this, root))
        getSystemService(NotificationManager::class.java).cancel(TripService.TRIP_END_NOTIFICATION_ID)
        editing = intent.getBooleanExtra(EXTRA_EDIT, false)
        load(intent.getLongExtra(EXTRA_TRIP_ID, NO_TRIP))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        editing = intent.getBooleanExtra(EXTRA_EDIT, false)
        load(intent.getLongExtra(EXTRA_TRIP_ID, NO_TRIP))
    }

    override fun onRestart() {
        super.onRestart()
        // 사용자 관리 화면에서 돌아오면 후보 목록을 다시 읽는다
        load(trip?.id ?: NO_TRIP)
    }

    private fun load(tripId: Long) {
        io.execute {
            val store = TripStore.get(this)
            val target = if (editing) {
                store.trip(tripId)?.takeIf { it.endTime != null }
            } else {
                // 이미 분류된 운행을 다시 열면 남은 미분류 운행으로 넘어간다
                store.trip(tripId)?.takeIf { it.endTime != null && it.nonBusiness == null }
                    ?: store.oldestUnclassifiedTrip()
            }
            val users = store.activeUsers()
            val lastUserId = store.lastUserId()
            val remaining = store.unclassifiedCount()
            runOnUiThread { render(target, users, lastUserId, remaining) }
        }
    }

    private fun render(target: Trip?, users: List<User>, lastUserId: Long?, remaining: Int) {
        if (isFinishing) return
        if (target == null) {
            Toast.makeText(this, "분류할 운행이 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        trip = target
        val userIds = users.map { it.id }
        selectedUserId = if (editing) {
            target.userId?.takeIf { it in userIds }
        } else {
            TripText.defaultUserId(userIds, lastUserId)
        }
        userButtons.clear()
        root.removeAllViews()

        val title = when {
            editing -> "운행 분류 수정"
            remaining > 1 -> "운행이 끝났습니다 (미분류 ${remaining}건)"
            else -> "운행이 끝났습니다"
        }
        root.addView(Ui.title(this, title))
        val km = TripMath.distanceKm(target.startOdometerKm, target.endOdometerKm)
        root.addView(
            Ui.body(
                this,
                "${TripText.period(target.startTime, target.endTime)}\n${TripText.route(target.startPlace, target.endPlace)} · ${TripText.distance(km)}",
            ),
        )

        root.addView(Ui.body(this, "사용자"))
        if (users.isEmpty()) {
            root.addView(Ui.body(this, "등록된 사용자가 없습니다. 사용자 없이 저장하거나 먼저 등록해 주세요."))
            root.addView(Ui.button(this, "사용자 등록") { startActivity(Intent(this, UsersActivity::class.java)) })
        } else {
            for (user in users) {
                val button = Ui.button(this, "") { selectUser(user.id) }
                userButtons[user.id] = button to user
                root.addView(button)
            }
            refreshUserButtons()
        }

        root.addView(
            Ui.row(
                this,
                Ui.button(this, "업무용") { classify(nonBusiness = false) },
                Ui.button(this, "비업무") { classify(nonBusiness = true) },
            ),
        )
        root.addView(Ui.button(this, "나중에") { finish() })
    }

    private fun selectUser(userId: Long) {
        selectedUserId = userId
        refreshUserButtons()
    }

    private fun refreshUserButtons() {
        for ((id, entry) in userButtons) {
            val (button, user) = entry
            val mark = if (id == selectedUserId) "● " else "○ "
            button.text = mark + user.name + (user.department?.let { " ($it)" } ?: "")
        }
    }

    private fun classify(nonBusiness: Boolean) {
        val target = trip ?: return
        if (userButtons.isNotEmpty() && selectedUserId == null) {
            Toast.makeText(this, "사용자를 골라 주세요", Toast.LENGTH_SHORT).show()
            return
        }
        val userId = selectedUserId
        io.execute {
            TripStore.get(this).classify(target.id, userId, nonBusiness)
            AppLog.info("trip.classified", "id" to target.id, "userId" to userId, "nonBusiness" to nonBusiness)
            runOnUiThread {
                Toast.makeText(this, "저장했습니다", Toast.LENGTH_SHORT).show()
                if (editing) finish() else load(NO_TRIP)
            }
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TRIP_ID = "trip_id"
        private const val EXTRA_EDIT = "edit"
        private const val NO_TRIP = -1L

        /** 이미 분류한 운행을 고친다. 저장하면 화면을 닫는다. */
        fun editIntent(context: Context, tripId: Long): Intent =
            Intent(context, TripEndActivity::class.java)
                .putExtra(EXTRA_TRIP_ID, tripId)
                .putExtra(EXTRA_EDIT, true)

        fun intent(context: Context, tripId: Long = NO_TRIP): Intent =
            Intent(context, TripEndActivity::class.java)
                .putExtra(EXTRA_TRIP_ID, tripId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
