package dev.vehiclelog.trip

import java.util.Calendar
import java.util.TimeZone

/** 운행기록표 계산. 엑셀 양식의 수식과 같은 규칙을 쓴다. */
object TripMath {

    /** 계기판 거리 차이. 값이 없거나 줄어들면(계기판 오류·교체) 계산하지 않는다. */
    fun distanceKm(startOdometerKm: Double?, endOdometerKm: Double?): Double? {
        if (startOdometerKm == null || endOdometerKm == null) return null
        if (endOdometerKm < startOdometerKm) return null
        return endOdometerKm - startOdometerKm
    }

    /**
     * 업무사용비율 = 업무용 거리 / 전체 거리 (국세청 고시 서식 ⑬).
     * 출·퇴근은 업무용에 포함한다. 거리 계산이 안 된 운행은 양쪽에서 모두 뺀다.
     *
     * @return 0.0~1.0, 전체 거리가 0 이면 null
     */
    fun businessRatio(trips: List<TripDistance>): Double? {
        val measured = trips.filter { it.distanceKm != null }
        val total = measured.sumOf { it.distanceKm!! }
        if (total <= 0.0) return null
        val business = measured.filter { !it.nonBusiness }.sumOf { it.distanceKm!! }
        return business / total
    }

    /**
     * 월별 기록표 합계. 아직 분류하지 않은 운행(nonBusiness == null)은 전체 거리에는 넣고
     * 업무사용비율 계산에서는 뺀다 — 분류 전에 비율이 확정된 것처럼 보이지 않게.
     */
    fun monthSummary(trips: List<MonthTrip>): MonthSummary {
        val classified = trips.mapNotNull { t -> t.nonBusiness?.let { TripDistance(t.distanceKm, it) } }
        return MonthSummary(
            tripCount = trips.size,
            totalKm = trips.sumOf { it.distanceKm ?: 0.0 },
            businessKm = classified.filter { !it.nonBusiness }.sumOf { it.distanceKm ?: 0.0 },
            businessRatio = businessRatio(classified),
            unmeasured = trips.count { it.distanceKm == null },
            unclassified = trips.count { it.nonBusiness == null },
        )
    }

    /** 시각들이 속한 (년, 월)별 개수. 최근 달이 먼저. 내보낼 달 목록에 쓴다. */
    fun monthCounts(times: List<Long>, zone: TimeZone): List<Pair<YearMonth, Int>> {
        val calendar = Calendar.getInstance(zone)
        return times.groupingBy {
            calendar.timeInMillis = it
            YearMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
        }.eachCount().toList().sortedByDescending { it.first }
    }

    /** 그 달 1일 0시부터 다음 달 1일 0시 직전까지의 [시작, 끝) 밀리초. month 는 1~12. */
    fun monthRange(year: Int, month: Int, zone: TimeZone): Pair<Long, Long> {
        require(month in 1..12) { "month 는 1~12: $month" }
        val calendar = Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, 1)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 1)
        return start to calendar.timeInMillis
    }
}

/** month 는 1~12. */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    override fun compareTo(other: YearMonth): Int = compareValuesBy(this, other, { it.year }, { it.month })
}

data class TripDistance(val distanceKm: Double?, val nonBusiness: Boolean)

/** 월별 합계 입력. nonBusiness 가 null 이면 미분류. */
data class MonthTrip(val distanceKm: Double?, val nonBusiness: Boolean?)

data class MonthSummary(
    val tripCount: Int,
    val totalKm: Double,
    val businessKm: Double,
    /** 0.0~1.0. 분류된 운행의 거리가 0 이면 null */
    val businessRatio: Double?,
    /** 계기판 값이 없어 거리를 못 구한 운행 수 */
    val unmeasured: Int,
    val unclassified: Int,
)
