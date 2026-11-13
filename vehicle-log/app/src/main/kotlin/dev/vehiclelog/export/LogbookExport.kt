package dev.vehiclelog.export

import android.content.Context
import android.os.Environment
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.TripStore
import dev.vehiclelog.data.VehicleInfo
import dev.vehiclelog.trip.TripMath
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 한 달치 운행을 기록표 엑셀로 만든다. 메인 스레드가 아닌 곳에서 부른다.
 * 메일에 붙일 때만 앱 캐시 폴더에 잠깐 만들고, 보낸 뒤 [deleteFiles] 로 지운다.
 * 사용자 이름·장소가 든 파일을 기기에 남기지 않는다 (Pleos 개인정보 최소 보관).
 */
object LogbookExport {
    private const val TEMPLATE_ASSET = "logbook_template.xlsx"
    private const val EXPORT_DIR = "export"
    private val FILE_NAME = Regex("""VehicleLog_\d{4}-\d{2}\.xlsx(\.tmp)?""")

    /** 이 앱이 만든 기록표 파일 이름인가. 지울 때 다른 파일을 건드리지 않게 이름으로 거른다. */
    fun isExportFile(name: String): Boolean = FILE_NAME.matches(name)

    /**
     * 만든 기록표를 모두 지운다. 지운 개수를 돌려준다.
     * 예전 버전은 앱 문서 폴더(외부 앱 전용 저장소)·내부 export 폴더에 남겼으므로 그곳도 지운다.
     */
    fun deleteFiles(context: Context): Int {
        val dirs = listOfNotNull(
            File(context.cacheDir, EXPORT_DIR),
            File(context.filesDir, EXPORT_DIR),
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
        )
        var deleted = 0
        for (dir in dirs) {
            val files = dir.listFiles { file -> file.isFile && isExportFile(file.name) } ?: continue
            for (file in files) {
                if (file.delete()) deleted++ else AppLog.warn("export.delete.failed", "file" to file.name)
            }
        }
        return deleted
    }

    fun export(context: Context, year: Int, month: Int, zone: TimeZone = TimeZone.getDefault()): File {
        val (start, end) = TripMath.monthRange(year, month, zone)
        val store = TripStore.get(context)
        val trips = store.endedTripsBetween(start, end)
        val names = store.userNames()
        val info = VehicleInfo.load(context)
        // 일광절약시간이 있는 시간대에서는 (끝-시작)/하루 가 하루 모자랄 수 있어 달력으로 센다
        val lastDay = Calendar.getInstance(zone).apply { timeInMillis = start }.getActualMaximum(Calendar.DAY_OF_MONTH)
        val header = LogHeader(
            corporation = info.corporation,
            plate = info.plate,
            model = info.model,
            vin = info.vin,
            department = info.department,
            period = String.format(Locale.KOREA, "%d-%02d-01 ~ %d-%02d-%02d", year, month, year, month, lastDay),
        )
        val rows = trips.map {
            LogRow(
                startMillis = it.startTime,
                endMillis = it.endTime,
                user = it.userId?.let(names::get),
                startPlace = it.startPlace,
                endPlace = it.endPlace,
                startKm = it.startOdometerKm,
                endKm = it.endOdometerKm,
                nonBusiness = it.nonBusiness,
            )
        }
        val dir = File(context.cacheDir, EXPORT_DIR)
        dir.mkdirs()
        val file = File(dir, String.format(Locale.ROOT, "VehicleLog_%d-%02d.xlsx", year, month))
        // 쓰다가 실패해도 이전 파일이 깨지지 않게 임시 파일에 쓰고 바꾼다
        val temp = File(dir, file.name + ".tmp")
        context.assets.open(TEMPLATE_ASSET).use { template ->
            temp.outputStream().use { LogbookXlsx.write(template, header, rows, zone, it) }
        }
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        AppLog.info("export.done", "rows" to rows.size, "file" to file.name)
        return file
    }
}
