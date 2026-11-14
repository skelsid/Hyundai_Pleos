package dev.vehiclelog.ui

import android.app.Activity
import android.app.Dialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.Trip
import dev.vehiclelog.data.TripStore
import dev.vehiclelog.data.User
import dev.vehiclelog.service.TripService
import dev.vehiclelog.trip.TripText
import java.util.concurrent.Executors

/**
 * 운행 종료 화면. 도착지를 확인하고 사용자와 업무용/비업무를 고른다. 출퇴근은 업무용이다.
 * 도착지는 자동으로 채운 값(내비 목적지, 없으면 주소)을 고치거나 이전 도착지 버튼으로 바꾼다.
 * 사용자는 버튼을 누르면 뜨는 목록에서 고른다. 후보가 없어도 목록은 뜨고 맨 위에 [사용자 추가]가 있다.
 * 정차(P) 상태에서 뜨지만, 주행 중 다시 열려도 막히지 않게 distractionOptimized 로 둔다.
 * distractionOptimized 화면은 주행 중 제한을 스스로 지켜야 한다(Pleos driving-restriction-development-guide):
 * 키보드 제한(NO_KEYBOARD)이면 도착지 칸을 Read Only 로 두고, 주행 중에는 설정 화면(사용자 등록)으로 가는 버튼을 끈다.
 * 이전 도착지 버튼과 분류 버튼은 주행 중에도 쓸 수 있다.
 * 같은 도착지를 전에 분류했으면 많이 고른 쪽을 강조 버튼으로 둔다 (버튼 자리는 그대로).
 * TODO: Pleos 실차에서 주행 중 제한 값이 들어오는지 확인 (실차 검증 보류)
 */
class TripEndActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private var trip: Trip? = null
    private var selectedUserId: Long? = null
    private var placeInput: EditText? = null
    private var userButton: Button? = null
    private var userPopup: Dialog? = null
    private var users: List<User> = emptyList()

    // 사용자 추가 화면으로 가기 전의 후보. 돌아왔을 때 새로 생긴 사람을 바로 골라 주려고 기억한다
    private var idsBeforeAdding: Set<Long>? = null
    private var rules = DrivingRules.PARKED
    private lateinit var drivingState: DrivingState

    // 사용자 관리에서 돌아오거나 화면을 다시 만들면 다시 그리므로, 입력하던 도착지·고른 사용자를 잠시 둔다
    private var kept: Kept? = null

    private data class Kept(val tripId: Long, val place: String, val userId: Long?)

    // 월별 기록표에서 이미 분류한 운행을 고치러 들어온 경우
    private var editing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = Ui.column(this)
        setContentView(Ui.screen(this, root))
        getSystemService(NotificationManager::class.java).cancel(TripService.TRIP_END_NOTIFICATION_ID)
        editing = intent.getBooleanExtra(EXTRA_EDIT, false)
        kept = savedInstanceState?.takeIf { it.containsKey(STATE_TRIP_ID) }?.let {
            Kept(
                tripId = it.getLong(STATE_TRIP_ID),
                place = it.getString(STATE_PLACE).orEmpty(),
                userId = it.getLong(STATE_USER_ID, NO_USER).takeIf { id -> id != NO_USER },
            )
        }
        drivingState = DrivingState(this) { applyRules(it) }
        drivingState.start()
        // 화면을 다시 만들었으면 보던 운행을 다시 연다
        load(kept?.tripId ?: intent.getLongExtra(EXTRA_TRIP_ID, NO_TRIP))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentInput()?.let {
            outState.putLong(STATE_TRIP_ID, it.tripId)
            outState.putString(STATE_PLACE, it.place)
            outState.putLong(STATE_USER_ID, it.userId ?: NO_USER)
        }
    }

    private fun currentInput(): Kept? {
        val target = trip ?: return null
        return Kept(target.id, placeInput?.text?.toString().orEmpty(), selectedUserId)
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
        kept = currentInput()
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
            // 지금 운행의 도착지가 목록에 끼면 빼야 하므로 하나 더 읽는다
            val recentPlaces = store.recentEndPlaces(RECENT_PLACE_LIMIT + 1)
            val history = target?.let { t -> t.endPlace?.let { store.recentClassifications(it, t.id, HISTORY_LIMIT) } }.orEmpty()
            runOnUiThread { render(target, users, lastUserId, remaining, recentPlaces, history) }
        }
    }

    private fun render(
        target: Trip?,
        users: List<User>,
        lastUserId: Long?,
        remaining: Int,
        recentPlaces: List<String>,
        history: List<Boolean>,
    ) {
        if (isFinishing) return
        if (target == null) {
            Toast.makeText(this, "분류할 운행이 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val keep = kept?.takeIf { it.tripId == target.id }
        kept = null
        val placeText = keep?.place ?: target.endPlace.orEmpty()
        trip = target
        this.users = users
        val userIds = users.map { it.id }
        val added = idsBeforeAdding?.let { before -> userIds.filter { it !in before } }.orEmpty()
        idsBeforeAdding = null
        // 방금 추가한 사람이 한 명이면 그 사람을 고른 것으로 본다
        selectedUserId = added.singleOrNull() ?: keep?.userId?.takeIf { it in userIds } ?: if (editing) {
            target.userId?.takeIf { it in userIds }
        } else {
            TripText.defaultUserId(userIds, lastUserId)
        }
        userPopup?.dismiss()
        root.removeAllViews()

        val title = when {
            editing -> "운행 분류 수정"
            remaining > 1 -> "운행이 끝났습니다 (미분류 ${remaining}건)"
            else -> "운행이 끝났습니다"
        }
        root.addView(Ui.header(this, Ui.title(this, title)))
        val km = target.distanceKm
        root.addView(
            Ui.body(
                this,
                "${TripText.period(target.startTime, target.endTime)}\n출발 ${target.startPlace ?: "출발지 미상"} · ${TripText.distance(km)}",
            ),
        )

        root.addView(Ui.body(this, "도착지"))
        val place = Ui.input(this, PLACE_HINT, placeText).apply {
            filters = arrayOf(InputFilter.LengthFilter(MAX_PLACE_LENGTH))
        }
        placeInput = place
        root.addView(place)
        val choices = TripText.placeChoices(recentPlaces, target.endPlace, RECENT_PLACE_LIMIT)
        if (choices.isNotEmpty()) {
            root.addView(Ui.body(this, "이전 도착지"))
            for (pair in choices.chunked(PLACES_PER_ROW)) {
                val buttons = pair.map { name ->
                    Ui.button(this, name) {
                        place.setText(name)
                        place.setSelection(place.text.length)
                    }
                }
                root.addView(Ui.row(this, *buttons.toTypedArray()))
            }
        }

        root.addView(Ui.body(this, "사용자"))
        // 후보가 없어도 같은 버튼을 두고, 누르면 맨 위에 [사용자 추가]가 있는 목록이 뜬다
        userButton = Ui.button(this, "") { showUserList() }.also(root::addView)
        refreshUserButton()

        // 고치러 들어왔으면 지금 분류를, 아니면 같은 도착지의 이전 분류를 강조한다. 둘 다 없으면 업무용
        val suggested = if (editing) target.nonBusiness else TripText.suggestNonBusiness(history)
        if (!editing && suggested != null) {
            root.addView(Ui.body(this, if (suggested) "이 도착지는 주로 비업무로 분류했습니다" else "이 도착지는 주로 업무용으로 분류했습니다"))
        }
        val nonBusinessFirst = suggested == true
        root.addView(
            Ui.row(
                this,
                Ui.button(this, "업무용", filled = !nonBusinessFirst) { classify(nonBusiness = false) },
                Ui.button(this, "비업무", filled = nonBusinessFirst) { classify(nonBusiness = true) },
            ),
        )
        root.addView(Ui.button(this, "나중에") { finish() })
        applyRules(rules)
    }

    private fun applyRules(next: DrivingRules) {
        rules = next
        placeInput?.let { Ui.setTypingAllowed(it, next.keyboardAllowed, PLACE_HINT, PLACE_BLOCKED_HINT) }
        // 목록 안의 [사용자 추가]는 열 때의 주행 상태로 그린다. 상태가 바뀌면 닫아서 다시 열게 한다
        if (userPopup?.isShowing == true) userPopup?.dismiss()
    }

    private fun label(user: User) = user.name + (user.department?.let { " ($it)" } ?: "")

    private fun refreshUserButton() {
        val selected = users.firstOrNull { it.id == selectedUserId }
        userButton?.text = when {
            selected != null -> "${label(selected)}  ▾"
            users.isEmpty() -> "등록된 사용자 없음  ▾"
            else -> "사용자를 골라 주세요  ▾"
        }
    }

    /** 사용자 목록. 맨 위는 항상 [사용자 추가]이고, 후보가 없어도 뜬다. */
    private fun showUserList() {
        if (isFinishing) return
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val add = Ui.button(this, if (rules.restricted) "＋ 사용자 추가 (주행 중에는 쓸 수 없음)" else "＋ 사용자 추가", filled = true) {
            userPopup?.dismiss()
            idsBeforeAdding = users.map { it.id }.toSet()
            startActivity(Intent(this, UsersActivity::class.java))
        }
        // 사용자 관리는 distractionOptimized 가 아니라 주행 중 시스템이 막는다. 눌러도 막힌 화면만 보이므로 끈다
        add.isEnabled = !rules.restricted
        list.addView(add)
        if (users.isEmpty()) list.addView(Ui.body(this, "등록된 사용자가 없습니다. 사용자 없이 저장할 수도 있습니다."))
        for (user in users) {
            val item = Ui.toggle(this, label(user)) {
                selectedUserId = user.id
                refreshUserButton()
                userPopup?.dismiss()
            }
            item.isSelected = user.id == selectedUserId
            list.addView(item)
        }
        userPopup?.dismiss()
        userPopup = Ui.listPopup(this, "사용자", list, close = "닫기").also { it.show() }
    }

    private fun classify(nonBusiness: Boolean) {
        val target = trip ?: return
        if (users.isNotEmpty() && selectedUserId == null) {
            Toast.makeText(this, "사용자를 골라 주세요", Toast.LENGTH_SHORT).show()
            return
        }
        val userId = selectedUserId
        val endPlace = TripText.cleanPlace(placeInput?.text?.toString().orEmpty())
        io.execute {
            TripStore.get(this).classify(target.id, userId, nonBusiness, endPlace)
            // 도착지는 위치 정보라 로그에는 있는지만 남긴다
            AppLog.info(
                "trip.classified",
                "id" to target.id, "userId" to userId, "nonBusiness" to nonBusiness, "hasPlace" to (endPlace != null),
            )
            runOnUiThread {
                Toast.makeText(this, "저장했습니다", Toast.LENGTH_SHORT).show()
                if (editing) finish() else load(NO_TRIP)
            }
        }
    }

    override fun onDestroy() {
        userPopup?.dismiss()
        drivingState.stop()
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TRIP_ID = "trip_id"
        private const val EXTRA_EDIT = "edit"
        private const val NO_TRIP = -1L
        private const val NO_USER = -1L
        private const val STATE_TRIP_ID = "trip_id"
        private const val STATE_PLACE = "place"
        private const val STATE_USER_ID = "user_id"
        private const val PLACE_HINT = "도착지를 입력하거나 아래에서 고르세요"
        private const val PLACE_BLOCKED_HINT = "주행 중에는 입력할 수 없습니다. 아래에서 고르세요"
        private const val RECENT_PLACE_LIMIT = 6
        private const val PLACES_PER_ROW = 2

        // 업무용 여부 제안에 볼 같은 도착지의 최근 운행 수
        private const val HISTORY_LIMIT = 5

        // 엑셀 칸에 들어가는 장소 이름 길이 상한
        private const val MAX_PLACE_LENGTH = 60

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
