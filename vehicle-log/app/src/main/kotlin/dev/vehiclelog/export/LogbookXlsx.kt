package dev.vehiclelog.export

import dev.vehiclelog.trip.TripMath
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 기록표 머리 정보. 빈 값은 빈 칸으로 둔다. */
data class LogHeader(
    val corporation: String,
    val plate: String,
    val model: String,
    val vin: String,
    val department: String,
    val period: String,
)

/** 기록표 한 줄. 값이 없으면 그 칸은 비우고 비고에 이유를 적는다. */
data class LogRow(
    val startMillis: Long,
    val endMillis: Long?,
    val user: String?,
    val startPlace: String?,
    val endPlace: String?,
    val startKm: Double?,
    val endKm: Double?,
    /** null 이면 아직 분류하지 않은 운행 */
    val nonBusiness: Boolean?,
)

/**
 * 운행기록표 양식(xlsx)을 채운다. Apache POI 대신 zip 안의 XML 을 직접 고친다:
 * 안드로이드에서 가볍고, Microsoft 365 체크박스 서식(styles 의 featurePropertyBag)을 그대로 보존한다.
 *
 * 양식 전제: 시트 하나(xl/worksheets/sheet1.xml), 데이터 11~30행(20줄), K열 수식, L열 체크박스,
 * calcChain 없음·fullCalcOnLoad 설정(엑셀이 열 때 다시 계산). 20줄을 넘으면 시트를 복제한다.
 */
object LogbookXlsx {
    const val ROWS_PER_SHEET = 20
    private const val FIRST_ROW = 11
    private const val TOTAL_ROW = 31
    private const val SHEET1 = "xl/worksheets/sheet1.xml"
    private const val SHEET1_RELS = "xl/worksheets/_rels/sheet1.xml.rels"
    private const val DRAWING1 = "xl/drawings/drawing1.xml"
    private const val DRAWING1_RELS = "xl/drawings/_rels/drawing1.xml.rels"
    private const val PRINTER1 = "xl/printerSettings/printerSettings1.bin"
    private const val WORKBOOK = "xl/workbook.xml"
    private const val WORKBOOK_RELS = "xl/_rels/workbook.xml.rels"
    private const val CONTENT_TYPES = "[Content_Types].xml"
    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val EXCEL_EPOCH_OFFSET_DAYS = 25_569.0 // 1899-12-30 ~ 1970-01-01

    fun write(template: InputStream, header: LogHeader, rows: List<LogRow>, zone: TimeZone, out: OutputStream) {
        val parts = readZip(template)
        val sheetXml = parts.text(SHEET1)
        val sheetName = Regex("<sheet name=\"([^\"]+)\"").find(parts.text(WORKBOOK))?.groupValues?.get(1)
            ?: error("양식에 시트 이름이 없다")
        val pages = rows.chunked(ROWS_PER_SHEET).ifEmpty { listOf(emptyList()) }

        pages.forEachIndexed { index, page ->
            val number = index + 1
            var filled = fillSheet(sheetXml, header, page, index * ROWS_PER_SHEET, zone)
            if (number == 1) {
                parts[SHEET1] = filled.toByteArray()
                return@forEachIndexed
            }
            filled = filled.replace(" tabSelected=\"1\"", "")
            parts["xl/worksheets/sheet$number.xml"] = filled.toByteArray()
            parts["xl/worksheets/_rels/sheet$number.xml.rels"] = parts.text(SHEET1_RELS)
                .replace("drawing1.xml", "drawing$number.xml")
                .replace("printerSettings1.bin", "printerSettings$number.bin")
                .toByteArray()
            parts["xl/drawings/drawing$number.xml"] = parts.getValue(DRAWING1)
            parts["xl/drawings/_rels/drawing$number.xml.rels"] = parts.getValue(DRAWING1_RELS)
            parts["xl/printerSettings/printerSettings$number.bin"] = parts.getValue(PRINTER1)
        }
        if (pages.size > 1) registerSheets(parts, sheetName, pages.size)
        writeZip(parts, out)
    }

    /** 엑셀 날짜 일련번호(1900 체계). 시간대 보정을 한 현지 시각 기준. */
    fun excelSerial(millis: Long, zone: TimeZone): Double =
        (millis + zone.getOffset(millis)) / MILLIS_PER_DAY + EXCEL_EPOCH_OFFSET_DAYS

    private fun fillSheet(template: String, header: LogHeader, rows: List<LogRow>, offset: Int, zone: TimeZone): String {
        var xml = template
        xml = setText(xml, "C5", header.corporation)
        xml = setText(xml, "I5", header.period)
        xml = setText(xml, "C6", header.plate)
        xml = setText(xml, "G6", header.model)
        xml = setText(xml, "K6", header.department)
        xml = setText(xml, "C7", header.vin)
        var sheetTotal = 0.0
        var anyDistance = false
        for (i in 0 until ROWS_PER_SHEET) {
            val r = FIRST_ROW + i
            xml = setNumber(xml, "A$r", (offset + i + 1).toDouble())
            val row = rows.getOrNull(i) ?: continue
            xml = setNumber(xml, "B$r", Math.floor(excelSerial(row.startMillis, zone)))
            xml = setText(xml, "C$r", row.user)
            xml = setNumber(xml, "D$r", excelSerial(row.startMillis, zone))
            xml = setNumber(xml, "E$r", row.endMillis?.let { excelSerial(it, zone) })
            xml = setText(xml, "F$r", row.startPlace)
            xml = setText(xml, "G$r", row.endPlace)
            xml = setNumber(xml, "I$r", row.startKm)
            xml = setNumber(xml, "J$r", row.endKm)
            xml = setBoolean(xml, "L$r", row.nonBusiness == true)
            xml = setText(xml, "N$r", note(row))
            val km = TripMath.distanceKm(row.startKm, row.endKm)
            xml = setFormulaCache(xml, "K$r", km, invalid = row.startKm != null && row.endKm != null && km == null)
            if (km != null) {
                sheetTotal += km
                anyDistance = true
            }
        }
        // 엑셀은 열 때 다시 계산하지만, 미리보기 앱은 캐시값만 보여 주므로 합계도 채워 둔다
        return setFormulaCache(xml, "K$TOTAL_ROW", sheetTotal.takeIf { anyDistance }, invalid = false)
    }

    private fun note(row: LogRow): String? =
        listOfNotNull(
            "미분류".takeIf { row.nonBusiness == null },
            "계기판 값 미수신".takeIf { row.startKm == null || row.endKm == null },
        ).joinToString(", ").ifEmpty { null }

    // ── 셀 편집 ────────────────────────────────────────────────

    private fun cellRegex(ref: String) = Regex("<c r=\"$ref\"([^>]*?)(/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL)

    private fun styleOf(attrs: String): String = Regex(" s=\"\\d+\"").find(attrs)?.value.orEmpty()

    private fun replaceCell(xml: String, ref: String, build: (style: String, inner: String) -> String): String {
        val match = cellRegex(ref).find(xml) ?: error("양식에 $ref 셀이 없다")
        val replacement = build(styleOf(match.groupValues[1]), match.groupValues[3])
        return xml.replaceRange(match.range, replacement)
    }

    private fun setText(xml: String, ref: String, value: String?): String {
        if (value.isNullOrBlank()) return xml
        return replaceCell(xml, ref) { style, _ ->
            "<c r=\"$ref\"$style t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(value)}</t></is></c>"
        }
    }

    private fun setNumber(xml: String, ref: String, value: Double?): String {
        if (value == null) return xml
        return replaceCell(xml, ref) { style, _ -> "<c r=\"$ref\"$style><v>${plain(value)}</v></c>" }
    }

    private fun setBoolean(xml: String, ref: String, value: Boolean): String =
        replaceCell(xml, ref) { style, _ -> "<c r=\"$ref\"$style t=\"b\"><v>${if (value) 1 else 0}</v></c>" }

    /** 수식은 그대로 두고 캐시값만 바꾼다. 양식 수식과 같은 규칙: 거리 없으면 "", 줄어들면 "확인". */
    private fun setFormulaCache(xml: String, ref: String, value: Double?, invalid: Boolean): String =
        replaceCell(xml, ref) { style, inner ->
            val formula = Regex("<f[^>]*/>|<f[^>]*>.*?</f>", RegexOption.DOT_MATCHES_ALL).find(inner)?.value
                ?: error("$ref 에 수식이 없다")
            when {
                invalid -> "<c r=\"$ref\"$style t=\"str\">$formula<v>확인</v></c>"
                value == null -> "<c r=\"$ref\"$style t=\"str\">$formula<v></v></c>"
                else -> "<c r=\"$ref\"$style>$formula<v>${plain(value)}</v></c>"
            }
        }

    private fun plain(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // ── 시트 복제 등록 ─────────────────────────────────────────

    private fun registerSheets(parts: MutableMap<String, ByteArray>, baseName: String, count: Int) {
        val sheets = StringBuilder("<sheets>")
        val printAreas = StringBuilder()
        val rels = StringBuilder()
        val types = StringBuilder()
        for (n in 1..count) {
            val name = if (n == 1) baseName else "$baseName ($n)"
            val relId = if (n == 1) "rId1" else "rIdLogSheet$n"
            sheets.append("<sheet name=\"$name\" sheetId=\"$n\" r:id=\"$relId\"/>")
            printAreas.append("<definedName name=\"_xlnm.Print_Area\" localSheetId=\"${n - 1}\">'$name'!\$A\$1:\$N\$37</definedName>")
            if (n == 1) continue
            rels.append(
                "<Relationship Id=\"$relId\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" " +
                    "Target=\"worksheets/sheet$n.xml\"/>",
            )
            types.append(
                "<Override PartName=\"/xl/worksheets/sheet$n.xml\" " +
                    "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                    "<Override PartName=\"/xl/drawings/drawing$n.xml\" " +
                    "ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>",
            )
        }
        sheets.append("</sheets>")

        var workbook = parts.text(WORKBOOK)
        // 바꿀 글에 '$A$1' 이 있어 문자열 치환을 쓰면 그룹 참조로 읽힌다. 람다로 그대로 넣는다
        workbook = workbook.replace(Regex("<sheets>.*?</sheets>", RegexOption.DOT_MATCHES_ALL)) { sheets.toString() }
        workbook = workbook.replace(Regex("<definedNames>.*?</definedNames>", RegexOption.DOT_MATCHES_ALL)) {
            "<definedNames>$printAreas</definedNames>"
        }
        parts[WORKBOOK] = workbook.toByteArray()
        parts[WORKBOOK_RELS] = parts.text(WORKBOOK_RELS).replace("</Relationships>", "$rels</Relationships>").toByteArray()
        parts[CONTENT_TYPES] = parts.text(CONTENT_TYPES).replace("</Types>", "$types</Types>").toByteArray()
    }

    // ── zip ────────────────────────────────────────────────────

    private fun readZip(input: InputStream): LinkedHashMap<String, ByteArray> {
        val parts = LinkedHashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val buffer = ByteArrayOutputStream()
                    zip.copyTo(buffer)
                    parts[entry.name] = buffer.toByteArray()
                }
            }
        }
        require(SHEET1 in parts && WORKBOOK in parts) { "운행기록표 양식이 아니다" }
        return parts
    }

    private fun writeZip(parts: Map<String, ByteArray>, out: OutputStream) {
        val zip = ZipOutputStream(out)
        // [Content_Types].xml 을 맨 앞에 둔다 (일부 뷰어가 순서를 본다)
        val ordered = parts.entries.sortedBy { if (it.key == CONTENT_TYPES) 0 else 1 }
        for ((name, data) in ordered) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(data)
            zip.closeEntry()
        }
        zip.finish()
    }

    private fun Map<String, ByteArray>.text(name: String): String =
        String(get(name) ?: error("양식에 $name 이 없다"), Charsets.UTF_8)
}
