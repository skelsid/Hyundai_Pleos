package dev.vehiclelog.ui

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import dev.vehiclelog.data.Trip
import dev.vehiclelog.data.TripStore
import dev.vehiclelog.trip.MonthTrip
import dev.vehiclelog.trip.TripMath
import dev.vehiclelog.trip.TripText
import dev.vehiclelog.trip.YearMonth
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * 월별 운행기록표. 합계·업무사용비율과 운행 목록을 보여 주고, 운행을 누르면 분류를 고칠 수 있다.
 * 읽기 위주라 주행 중에도 열 수 있게 distractionOptimized 로 둔다.
 */
class MonthlyActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private val zone: TimeZone = TimeZone.getDefault()
    private lateinit var monthLabel: TextView
    private lateinit var summary: TextView
    private lateinit var list: LinearLayout
    private var year = 0
    private var month = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val now = Calendar.getInstance(zone)
        year = savedInstanceState?.getInt(STATE_YEAR) ?: now.get(Calendar.YEAR)
        month = savedInstanceState?.getInt(STATE_MONTH) ?: (now.get(Calendar.MONTH) + 1)

        val root = Ui.column(this)
        monthLabel = Ui.title(this, "")
        summary = Ui.body(this, "")
        list = Ui.column(this).apply { setPadding(0, 0, 0, 0) }
        root.addView(Ui.header(this, monthLabel))
        root.addView(Ui.row(this, Ui.button(this, "이전 달") { moveMonth(-1) }, Ui.button(this, "다음 달") { moveMonth(1) }))
        root.addView(summary)
        root.addView(Ui.button(this, "내보내기 (관리자 메일)") { startActivity(SendActivity.intent(this, YearMonth(year, month))) })
        root.addView(list)
        setContentView(Ui.screen(this, root))
    }

    override fun onResume() {
        super.onResume()
        // 분류를 고치고 돌아오면 다시 읽는다
        load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_YEAR, year)
        outState.putInt(STATE_MONTH, month)
    }

    private fun moveMonth(delta: Int) {
        val index = year * MONTHS + (month - 1) + delta
        year = index / MONTHS
        month = index % MONTHS + 1
        load()
    }

    private fun load() {
        val (start, end) = TripMath.monthRange(year, month, zone)
        val shownYear = year
        val shownMonth = month
        io.execute {
            val store = TripStore.get(this)
            val trips = store.endedTripsBetween(start, end)
            val names = store.userNames()
            runOnUiThread {
                // 빠르게 달을 넘기면 늦게 온 결과는 버린다
                if (shownYear == year && shownMonth == month) render(trips, names)
            }
        }
    }

    private fun render(trips: List<Trip>, names: Map<Long, String>) {
        if (isFinishing) return
        monthLabel.text = String.format(Locale.KOREA, "%d년 %d월 운행기록", year, month)
        val distances = trips.associate { it.id to TripMath.distanceKm(it.startOdometerKm, it.endOdometerKm) }
        val total = TripMath.monthSummary(trips.map { MonthTrip(distances[it.id], it.nonBusiness) })
        summary.text = buildString {
            append("운행 ${total.tripCount}건 · 주행 ${TripText.distance(total.totalKm)} · 업무용 ${TripText.distance(total.businessKm)}\n")
            append("업무사용비율 ")
            append(total.businessRatio?.let { String.format(Locale.KOREA, "%.1f%%", it * PERCENT) } ?: "—")
            if (total.unclassified > 0) append(" · 미분류 ${total.unclassified}건(비율에서 제외)")
            if (total.unmeasured > 0) append(" · 거리 미수신 ${total.unmeasured}건")
        }

        list.removeAllViews()
        if (trips.isEmpty()) list.addView(Ui.body(this, "이 달의 운행이 없습니다."))
        for (trip in trips) {
            val user = trip.userId?.let { names[it] } ?: "사용자 없음"
            val kind = when (trip.nonBusiness) {
                null -> "미분류"
                true -> "비업무"
                false -> "업무용"
            }
            val title = "${TripText.period(trip.startTime, trip.endTime)}  ${TripText.distance(distances[trip.id])}"
            val subtitle = "${TripText.route(trip.startPlace, trip.endPlace)}\n$user · $kind"
            list.addView(Ui.listItem(this, title, subtitle) { startActivity(TripEndActivity.editIntent(this, trip.id)) })
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val STATE_YEAR = "year"
        private const val STATE_MONTH = "month"
        private const val MONTHS = 12
        private const val PERCENT = 100.0
    }
}
