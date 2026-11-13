package dev.vehiclelog.trip

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 화면·안내 문구. 값이 없으면 빈칸 대신 '미상', '미수신'으로 보여 준다. */
object TripText {

    private val WHITESPACE = Regex("""\s+""")

    fun route(startPlace: String?, endPlace: String?): String =
        "${startPlace ?: "출발지 미상"} → ${endPlace ?: "도착지 미상"}"

    /** 손으로 넣은 장소 이름. 앞뒤 공백을 지우고 연속 공백을 하나로 줄인다. 비면 null. */
    fun cleanPlace(text: String): String? =
        text.trim().replace(WHITESPACE, " ").takeIf { it.isNotEmpty() }

    /** 이전 도착지 후보. 지금 칸에 든 값은 빼고 limit 개까지. */
    fun placeChoices(recent: List<String>, current: String?, limit: Int): List<String> =
        recent.filter { it != current }.take(limit)

    fun distance(km: Double?): String =
        km?.let { String.format(Locale.KOREA, "%.1fkm", it) } ?: "거리 미수신"

    fun period(startMillis: Long, endMillis: Long?): String {
        val dayTime = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)
        val start = dayTime.format(Date(startMillis))
        val end = when {
            endMillis == null -> "운행 중"
            // 자정을 넘긴 운행은 끝 날짜도 보여 준다
            sameDay(startMillis, endMillis) -> SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(endMillis))
            else -> dayTime.format(Date(endMillis))
        }
        return "$start – $end"
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val day = SimpleDateFormat("yyyyMMdd", Locale.ROOT)
        return day.format(Date(a)) == day.format(Date(b))
    }

    /**
     * 운행 종료 화면의 기본 사용자. 직전 사용자가 아직 후보에 있으면 그 사람,
     * 아니면 후보가 한 명일 때만 그 사람, 그 밖에는 고르지 않는다.
     */
    fun defaultUserId(userIds: List<Long>, lastUserId: Long?): Long? =
        lastUserId?.takeIf { it in userIds } ?: userIds.singleOrNull()

    /**
     * 같은 도착지를 최근에 어떻게 분류했는지 보고 기본값을 제안한다 (true = 비업무).
     * 많은 쪽을 고르고, 기록이 없거나 반반이면 null(제안 없음 → 업무용이 기본).
     * 자주 가는 거래처는 업무용, 개인 볼일 장소는 비업무로 굳어지므로 한 번 고르면 다음부터 덜 누른다.
     */
    fun suggestNonBusiness(recent: List<Boolean>): Boolean? {
        val nonBusiness = recent.count { it }
        val business = recent.size - nonBusiness
        return when {
            nonBusiness > business -> true
            business > nonBusiness -> false
            else -> null
        }
    }
}
