package dev.vehiclelog.export

import org.junit.Assert.assertEquals
import org.junit.Test

/** 기록표의 도착지·방문처 칸에 무엇이 들어가는지 본다. */
class EndColumnsTest {

    @Test
    fun test_endColumns_addressAndDifferentName_addressThenName() {
        assertEquals("경기 성남시 분당구 판교역로" to "○○상사", LogbookExport.endColumns("○○상사", "경기 성남시 분당구 판교역로"))
    }

    @Test
    fun test_endColumns_nameSameAsAddress_visitEmpty() {
        assertEquals("경기 화성시" to null, LogbookExport.endColumns("경기 화성시", "경기 화성시"))
        // 공백만 다른 것은 같은 값으로 본다
        assertEquals("경기 화성시" to null, LogbookExport.endColumns(" 경기  화성시 ", "경기 화성시"))
    }

    @Test
    fun test_endColumns_noAddress_nameGoesToDestination() {
        assertEquals("○○상사" to null, LogbookExport.endColumns("○○상사", null))
        assertEquals("○○상사" to null, LogbookExport.endColumns("○○상사", "  "))
    }

    @Test
    fun test_endColumns_nameClearedByUser_addressOnly() {
        assertEquals("경기 화성시" to null, LogbookExport.endColumns(null, "경기 화성시"))
    }

    @Test
    fun test_endColumns_nothing_bothEmpty() {
        assertEquals(null to null, LogbookExport.endColumns(null, null))
    }
}
