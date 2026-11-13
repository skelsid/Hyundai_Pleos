package dev.vehiclelog.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.AdminInfo
import dev.vehiclelog.data.TripStore
import dev.vehiclelog.export.LogbookMail
import dev.vehiclelog.mail.MailException
import dev.vehiclelog.trip.TripMath
import dev.vehiclelog.trip.YearMonth
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * 내보내기. 운행이 있는 달 중 보낼 달을 고르면 달마다 운행기록표 엑셀을 만들어 관리자 메일로 보낸다.
 * 입력 칸 없이 버튼만 있어 distractionOptimized 로 둔다.
 */
class SendActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private val zone: TimeZone = TimeZone.getDefault()
    private lateinit var adminText: TextView
    private lateinit var monthList: LinearLayout
    private lateinit var status: TextView
    private lateinit var sendButton: Button

    private val monthButtons = mutableMapOf<YearMonth, Pair<Button, Int>>()
    private val selected = mutableSetOf<YearMonth>()
    private var sending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 화면을 다시 만들 때는 고르던 상태를, 처음에는 월별 화면에서 보던 달을 고른 채로 연다
        val initial = savedInstanceState?.getIntArray(STATE_SELECTED) ?: intent.getIntArrayExtra(EXTRA_MONTHS)
        initial?.let { selected += decode(it) }

        val root = Ui.column(this)
        root.addView(Ui.header(this, Ui.title(this, "운행기록표 내보내기")))
        adminText = Ui.body(this, "")
        root.addView(adminText)
        root.addView(Ui.button(this, "받는 사람·보내는 계정 바꾸기") { startActivity(AdminActivity.intent(this)) })
        root.addView(Ui.body(this, "보낼 달을 고르세요"))
        monthList = Ui.column(this).apply { setPadding(0, 0, 0, 0) }
        root.addView(monthList)
        status = Ui.body(this, "")
        root.addView(status)
        sendButton = Ui.button(this, "", filled = true) { send() }
        root.addView(sendButton)
        setContentView(Ui.screen(this, root))
    }

    override fun onResume() {
        super.onResume()
        val admin = AdminInfo.load(this)
        adminText.text = admin?.let { "받는 사람: ${it.name} <${it.email}>" } ?: "받는 사람이 없습니다. 관리자를 먼저 등록해 주세요."
        load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(STATE_SELECTED, encode(selected))
    }

    private fun load() {
        io.execute {
            val counts = TripMath.monthCounts(TripStore.get(this).endedTripStartTimes(), zone)
            runOnUiThread { renderMonths(counts) }
        }
    }

    private fun renderMonths(counts: List<Pair<YearMonth, Int>>) {
        if (isFinishing) return
        monthButtons.clear()
        monthList.removeAllViews()
        // 운행이 없어진 달(예: 기록 정리)은 선택에서 뺀다
        selected.retainAll(counts.map { it.first }.toSet())
        if (counts.isEmpty()) monthList.addView(Ui.body(this, "보낼 운행이 없습니다."))
        for ((month, count) in counts) {
            val button = Ui.toggle(this, "") { toggle(month) }
            monthButtons[month] = button to count
            monthList.addView(button)
        }
        refresh()
    }

    private fun toggle(month: YearMonth) {
        if (sending) return
        if (!selected.remove(month)) selected += month
        refresh()
    }

    private fun refresh() {
        for ((month, entry) in monthButtons) {
            val (button, count) = entry
            button.isSelected = month in selected
            button.text = "${month.year}년 ${month.month}월 · 운행 ${count}건"
            button.isEnabled = !sending
        }
        sendButton.text = if (sending) "보내는 중…" else "관리자에게 보내기 (${selected.size}개월)"
        sendButton.isEnabled = !sending && selected.isNotEmpty()
    }

    private fun send() {
        val admin = AdminInfo.load(this)
        if (admin == null) {
            startActivity(AdminActivity.intent(this))
            return
        }
        val months = selected.toList()
        if (months.isEmpty() || sending) return
        sending = true
        status.text = ""
        refresh()
        io.execute {
            val message = runCatching { LogbookMail.send(this, months, admin, zone) }.fold(
                onSuccess = { "${admin.email} 로 ${months.size}개월치를 보냈습니다." },
                onFailure = {
                    AppLog.error("mail.failed", it, "kind" to (it as? MailException)?.kind)
                    failureText(it)
                },
            )
            runOnUiThread {
                sending = false
                status.text = message
                refresh()
            }
        }
    }

    private fun failureText(error: Throwable): String = when ((error as? MailException)?.kind) {
        MailException.Kind.NOT_CONFIGURED -> "보내는 메일 계정이 없습니다. [받는 사람·보내는 계정 바꾸기]에서 보내는 Gmail 계정을 입력해 주세요."
        MailException.Kind.NETWORK -> "인터넷에 연결되지 않아 보내지 못했습니다. 연결을 확인한 뒤 다시 보내 주세요."
        MailException.Kind.LOGIN -> "보내는 Gmail 계정에 로그인하지 못했습니다. [받는 사람·보내는 계정 바꾸기]에서 주소와 앱 비밀번호를 확인해 주세요."
        MailException.Kind.REJECTED -> "메일 서버가 받지 않았습니다. 받는 사람 메일 주소를 확인해 주세요."
        null -> "엑셀 파일을 만들지 못해 보내지 못했습니다."
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_MONTHS = "months"
        private const val STATE_SELECTED = "selected"

        /** [preselect] 달을 미리 골라 둔 채 연다. */
        fun intent(context: Context, preselect: YearMonth? = null): Intent =
            Intent(context, SendActivity::class.java).apply {
                if (preselect != null) putExtra(EXTRA_MONTHS, encode(listOf(preselect)))
            }

        private fun encode(months: Collection<YearMonth>): IntArray = months.map { it.year * 100 + it.month }.toIntArray()

        private fun decode(values: IntArray): List<YearMonth> = values.map { YearMonth(it / 100, it % 100) }
    }
}
