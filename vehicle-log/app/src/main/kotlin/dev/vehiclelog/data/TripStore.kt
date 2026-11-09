package dev.vehiclelog.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.vehiclelog.signal.TripPoint

/** 저장된 운행 한 건. 끝나지 않은 운행은 endTime 이 null. 분류 전이면 userId·nonBusiness 가 null. */
data class Trip(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val startOdometerKm: Double?,
    val endOdometerKm: Double?,
    val startPlace: String?,
    val endPlace: String?,
    val userId: Long?,
    val nonBusiness: Boolean?,
)

/**
 * 운행·사용자 후보를 차량 내부 SQLite 에 저장한다. 외부 서버로 보내지 않는다.
 * 호출은 메인 스레드가 아닌 곳에서 한다 (TripService 의 단일 스레드 실행기).
 */
class TripStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE trips (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                start_odometer REAL,
                end_odometer REAL,
                start_lat REAL,
                start_lon REAL,
                end_lat REAL,
                end_lon REAL,
                start_place TEXT,
                end_place TEXT,
                user_id INTEGER REFERENCES users(id),
                non_business INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                department TEXT,
                active INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX trips_start_time ON trips(start_time)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // TODO: 스키마를 바꾸면 여기서 이전한다. 지금은 버전 1 뿐이다
    }

    fun openTrip(): Trip? =
        readableDatabase.query("trips", null, "end_time IS NULL", null, null, null, "start_time DESC", "1")
            .use { if (it.moveToFirst()) it.toTrip() else null }

    fun startTrip(point: TripPoint): Long {
        val values = ContentValues().apply {
            put("start_time", point.timeMillis)
            put("start_odometer", point.odometerKm)
            put("start_lat", point.latitude)
            put("start_lon", point.longitude)
            // 출발지 이름은 직전 운행의 도착지를 이어 받는다 (같은 곳에서 출발하므로)
            put("start_place", point.placeName ?: lastEndPlace())
        }
        return writableDatabase.insertOrThrow("trips", null, values)
    }

    fun endTrip(id: Long, point: TripPoint) {
        val values = ContentValues().apply {
            put("end_time", point.timeMillis)
            put("end_odometer", point.odometerKm)
            put("end_lat", point.latitude)
            put("end_lon", point.longitude)
            put("end_place", point.placeName)
        }
        writableDatabase.update("trips", values, "id = ?", arrayOf(id.toString()))
    }

    fun tripCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM trips", null).use { it.moveToFirst(); it.getInt(0) }

    private fun lastEndPlace(): String? =
        readableDatabase.rawQuery(
            "SELECT end_place FROM trips WHERE end_time IS NOT NULL ORDER BY end_time DESC LIMIT 1",
            null,
        ).use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }

    private fun Cursor.toTrip() = Trip(
        id = getLong(getColumnIndexOrThrow("id")),
        startTime = getLong(getColumnIndexOrThrow("start_time")),
        endTime = longOrNull("end_time"),
        startOdometerKm = doubleOrNull("start_odometer"),
        endOdometerKm = doubleOrNull("end_odometer"),
        startPlace = stringOrNull("start_place"),
        endPlace = stringOrNull("end_place"),
        userId = longOrNull("user_id"),
        nonBusiness = longOrNull("non_business")?.let { it != 0L },
    )

    private fun Cursor.longOrNull(column: String): Long? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getLong(it) }

    private fun Cursor.doubleOrNull(column: String): Double? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getDouble(it) }

    private fun Cursor.stringOrNull(column: String): String? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }

    companion object {
        private const val DB_NAME = "vehicle_log.db"
        private const val DB_VERSION = 1
    }
}
