package dev.vehiclelog.export

import android.content.Context
import android.os.Environment
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.TripStore
import dev.vehiclelog.data.VehicleInfo
import dev.vehiclelog.trip.TripMath
import java.io.File
import java.util.Locale
import java.util.TimeZone

/**
 * 한 달치 운행을 기록표 엑셀로 저장한다. 메인 스레드가 아닌 곳에서 부른다.
 * 저장 위치는 앱 전용 문서 폴더(권한 불필요). 차에서 꺼내는 방법은 TODO: 실차 내보내기 경로 확인(USB·공유).
 */
object LogbookExport {
    private const val TEMPLATE_ASSET = "logbook_template.xlsx"

    fun export(context: Context, year: Int, month: Int, zone: TimeZone = TimeZone.getDefault()): File {
        val (start, end) = TripMath.monthRange(year, month, zone)
        val store = TripStore.get(context)
        val trips = store.endedTripsBetween(start, end)
        val names = store.userNames()
        val info = VehicleInfo.load(context)
        val lastDay = (end - start) / DAY_MILLIS
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
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: File(context.filesDir, "export")
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

    private const val DAY_MILLIS = 86_400_000L
}
