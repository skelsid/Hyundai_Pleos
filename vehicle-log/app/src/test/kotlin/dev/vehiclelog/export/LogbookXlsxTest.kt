package dev.vehiclelog.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.TimeZone
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class LogbookXlsxTest {

    private val seoul = TimeZone.getTimeZone("Asia/Seoul")
    private val template = File("src/main/assets/logbook_template.xlsx")
    private val header = LogHeader("테스트 법인", "12가3456", "아이오닉 5", "KMHTEST0000000001", "총무팀", "2026-11-01 ~ 2026-11-30")

    // 2026-11-09 09:00 KST
    private val nineAm = 1_794_182_400_000L

    private fun row(i: Int, nonBusiness: Boolean? = false, startKm: Double? = 25_000.0 + i * 10, endKm: Double? = 25_000.0 + i * 10 + 7.5) =
        LogRow(nineAm + i * 3_600_000L, nineAm + i * 3_600_000L + 1_800_000L, "사용자$i", "본사", "거래처 <A&B>", startKm, endKm, nonBusiness)

    private fun export(rows: List<LogRow>): Map<String, String> {
        val out = ByteArrayOutputStream()
        template.inputStream().use { LogbookXlsx.write(it, header, rows, seoul, out) }
        val parts = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val bytes = zip.readBytes()
                if (e.name.endsWith(".xml") || e.name.endsWith(".rels")) parts[e.name] = String(bytes, Charsets.UTF_8)
                else parts[e.name] = ""
            }
        }
        return parts
    }

    private fun cell(xml: String, ref: String): String =
        Regex("<c r=\"$ref\"[^>]*?(/>|>.*?</c>)", RegexOption.DOT_MATCHES_ALL).find(xml)!!.value

    private fun assertWellFormed(parts: Map<String, String>) {
        val builder = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        for ((name, text) in parts) {
            if (name.endsWith(".xml") || name.endsWith(".rels")) builder.parse(ByteArrayInputStream(text.toByteArray()))
        }
    }

    @Test
    fun test_excelSerial_seoulMorning_matchesExcel() {
        assertEquals(46_335.375, LogbookXlsx.excelSerial(nineAm, seoul), 1e-9)
    }

    @Test
    fun test_write_threeRows_fillsHeaderRowsAndCheckbox() {
        val parts = export(listOf(row(0), row(1, nonBusiness = true), row(2, nonBusiness = null, endKm = null)))
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")
        assertWellFormed(parts)
        assertTrue(cell(sheet, "C5").contains("테스트 법인"))
        assertTrue(cell(sheet, "C7").contains("KMHTEST0000000001"))
        assertTrue(cell(sheet, "B11").contains("<v>46335</v>"))
        assertTrue(cell(sheet, "D11").contains("<v>46335.375</v>"))
        assertTrue(cell(sheet, "G11").contains("거래처 &lt;A&amp;B&gt;"))
        assertTrue(cell(sheet, "I11").contains("<v>25000</v>"))
        assertTrue(cell(sheet, "K11").contains("<v>7.5</v>"))
        // 업무용은 체크 없음, 비업무만 체크
        assertTrue(cell(sheet, "L11").contains("<v>0</v>"))
        assertTrue(cell(sheet, "L12").contains("<v>1</v>"))
        // 미분류·거리 없음은 비고에 적고 체크하지 않는다
        assertTrue(cell(sheet, "L13").contains("<v>0</v>"))
        assertTrue(cell(sheet, "N13").contains("미분류, 계기판 값 미수신"))
        assertTrue(cell(sheet, "K31").contains("<v>15</v>"))
        // 남은 행은 비어 있다
        assertFalse(cell(sheet, "C14").contains("<is>"))
        assertFalse(parts.containsKey("xl/worksheets/sheet2.xml"))
    }

    @Test
    fun test_write_twentyFiveRows_addsSecondSheet() {
        val parts = export((0 until 25).map { row(it) })
        assertWellFormed(parts)
        val sheet2 = parts.getValue("xl/worksheets/sheet2.xml")
        assertTrue(cell(sheet2, "A11").contains("<v>21</v>"))
        assertTrue(cell(sheet2, "C15").contains("사용자24"))
        assertFalse(cell(sheet2, "C16").contains("<is>"))
        assertFalse(sheet2.contains("tabSelected"))
        val workbook = parts.getValue("xl/workbook.xml")
        assertEquals(2, Regex("<sheet ").findAll(workbook).count())
        assertEquals(2, Regex("_xlnm.Print_Area").findAll(workbook).count())
        assertTrue(parts.getValue("xl/_rels/workbook.xml.rels").contains("worksheets/sheet2.xml"))
        assertTrue(parts.getValue("[Content_Types].xml").contains("/xl/worksheets/sheet2.xml"))
        assertTrue(parts.getValue("xl/worksheets/_rels/sheet2.xml.rels").contains("drawing2.xml"))
        assertTrue(parts.containsKey("xl/drawings/drawing2.xml"))
        assertTrue(parts.containsKey("xl/printerSettings/printerSettings2.bin"))
    }

    @Test
    fun test_write_controlCharInName_removedAndXmlStaysValid() {
        val parts = export(listOf(row(0).copy(user = "홍\u0001길\u0008동")))
        assertWellFormed(parts)
        assertTrue(cell(parts.getValue("xl/worksheets/sheet1.xml"), "C11").contains(">홍길동<"))
    }

    @Test
    fun test_write_noRows_keepsSingleEmptySheet() {
        val parts = export(emptyList())
        assertWellFormed(parts)
        assertFalse(parts.containsKey("xl/worksheets/sheet2.xml"))
        assertFalse(cell(parts.getValue("xl/worksheets/sheet1.xml"), "C11").contains("<is>"))
    }

    /** 사람이 엑셀로 열어 확인할 견본을 build/ 아래에 남긴다 (저장소에는 들어가지 않는다). */
    @Test
    fun test_write_sampleFile_isWritten() {
        val sample = File("build/sample/VehicleLog_sample.xlsx").apply { parentFile!!.mkdirs() }
        sample.outputStream().use { out ->
            template.inputStream().use {
                LogbookXlsx.write(it, header, (0 until 23).map { i -> row(i, nonBusiness = i % 5 == 4) }, seoul, out)
            }
        }
        assertTrue(sample.length() > 0)
    }

    @Test
    fun test_template_hasNoPersonalMetadata() {
        val parts = export(emptyList())
        val all = parts.values.joinToString("")
        assertFalse(all.contains("lastModifiedBy"))
        assertFalse(all.contains("dc:creator"))
        assertFalse(all.contains("absPath"))
        assertFalse(parts.containsKey("xl/calcChain.xml"))
        assertTrue(parts.getValue("xl/workbook.xml").contains("fullCalcOnLoad=\"1\""))
    }

    @Test
    fun test_energyNote_formats() {
        val fuel = { p: Int -> dev.vehiclelog.trip.EnergyLevel(p, battery = false) }
        org.junit.Assert.assertEquals("연료 72→65%", LogbookXlsx.energyNote(fuel(72), fuel(65)))
        org.junit.Assert.assertEquals("연료 출발 72%", LogbookXlsx.energyNote(fuel(72), null))
        org.junit.Assert.assertEquals("배터리 도착 40%", LogbookXlsx.energyNote(null, dev.vehiclelog.trip.EnergyLevel(40, battery = true)))
        org.junit.Assert.assertNull(LogbookXlsx.energyNote(null, null))
    }
}
