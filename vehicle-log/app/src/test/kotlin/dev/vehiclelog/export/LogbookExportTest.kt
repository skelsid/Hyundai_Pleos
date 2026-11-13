package dev.vehiclelog.export

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** 지울 때 이 앱이 만든 기록표만 고르는지 본다. */
@RunWith(Parameterized::class)
class LogbookExportTest(private val name: String, private val expected: Boolean) {

    @Test
    fun test_isExportFile_name_matchesOnlyLogbook() {
        assertEquals(expected, LogbookExport.isExportFile(name))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf("VehicleLog_2026-11.xlsx", true),
            arrayOf("VehicleLog_2026-11.xlsx.tmp", true),
            arrayOf("VehicleLog_2026-1.xlsx", false),
            arrayOf("VehicleLog_2026-11.xlsx.bak", false),
            arrayOf("my_VehicleLog_2026-11.xlsx", false),
            arrayOf("report.xlsx", false),
            arrayOf("", false),
        )
    }
}
