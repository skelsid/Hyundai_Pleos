package dev.vehiclelog.export

import dev.vehiclelog.data.VehicleInfo
import dev.vehiclelog.trip.TripMath
import dev.vehiclelog.trip.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LogbookMailTest {

    @Test
    fun test_monthList_sameYear_groupsYear() {
        assertEquals("2026년 10월, 11월", LogbookMail.monthList(listOf(YearMonth(2026, 11), YearMonth(2026, 10))))
    }

    @Test
    fun test_monthList_acrossYears_repeatsYear() {
        assertEquals("2025년 12월, 2026년 1월", LogbookMail.monthList(listOf(YearMonth(2026, 1), YearMonth(2025, 12), YearMonth(2026, 1))))
    }

    @Test
    fun test_subject_withPlate() {
        assertEquals("[차량운행기록] 12가3456 운행기록표 2026년 11월", LogbookMail.subject(" 12가3456 ", listOf(YearMonth(2026, 11))))
        assertEquals("[차량운행기록] 운행기록표 2026년 11월", LogbookMail.subject("", listOf(YearMonth(2026, 11))))
    }

    @Test
    fun test_body_namesAdminAndPeriod() {
        val body = LogbookMail.body("홍길동", VehicleInfo(corporation = "OO상사", plate = "12가3456"), listOf(YearMonth(2026, 10), YearMonth(2026, 11)))
        assertTrue(body.startsWith("홍길동 님, 안녕하세요."))
        assertTrue(body.contains("2개월치"))
        assertTrue(body.contains("차량: OO상사 · 12가3456"))
        assertTrue(body.contains("기간: 2026년 10월, 11월"))
    }

    @Test
    fun test_monthCounts_groupsByLocalMonth_newestFirst() {
        val zone = TimeZone.getTimeZone("Asia/Seoul")
        fun at(year: Int, month: Int, day: Int, hour: Int) =
            Calendar.getInstance(zone).apply { clear(); set(year, month - 1, day, hour, 0) }.timeInMillis
        // 11월 1일 0시 30분(서울)은 UTC 로는 10월이지만 11월로 센다
        val times = listOf(at(2026, 10, 5, 9), at(2026, 11, 1, 0) + 30 * 60_000, at(2026, 11, 20, 18), at(2025, 12, 31, 23))
        assertEquals(
            listOf(YearMonth(2026, 11) to 2, YearMonth(2026, 10) to 1, YearMonth(2025, 12) to 1),
            TripMath.monthCounts(times, zone),
        )
    }
}
