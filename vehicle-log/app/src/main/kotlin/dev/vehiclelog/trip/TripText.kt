package dev.vehiclelog.trip

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 화면·안내 문구. 값이 없으면 빈칸 대신 '미상', '미수신'으로 보여 준다. */
object TripText {

    fun route(startPlace: String?, endPlace: String?): String =
        "${startPlace ?: "출발지 미상"} → ${endPlace ?: "도착지 미상"}"

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
}
