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

/** 운행 종료 때 고를 수 있는 사용자 후보. 삭제하면 active=0 으로 숨긴다 (지난 운행이 참조하므로). */
data class User(val id: Long, val name: String, val department: String?)

/**
 * 운행·사용자 후보를 차량 내부 SQLite 에 저장한다. 외부 서버로 보내지 않는다.
 * 서비스와 화면이 같은 연결을 쓰도록 [get] 으로 하나만 만든다.
 * 호출은 메인 스레드가 아닌 곳에서 한다.
 */
class TripStore private constructor(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

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

    /** 운행으로 볼 수 없는 짧은 움직임을 지운다. */
    fun deleteTrip(id: Long) {
        writableDatabase.delete("trips", "id = ?", arrayOf(id.toString()))
    }

    fun trip(id: Long): Trip? =
        readableDatabase.query("trips", null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) it.toTrip() else null }

    /** 끝났지만 아직 사용자·업무용 여부를 고르지 않은 운행 중 가장 오래된 것. */
    fun oldestUnclassifiedTrip(): Trip? =
        readableDatabase.query(
            "trips", null, UNCLASSIFIED, null, null, null, "start_time ASC", "1",
        ).use { if (it.moveToFirst()) it.toTrip() else null }

    fun unclassifiedCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM trips WHERE $UNCLASSIFIED", null)
            .use { it.moveToFirst(); it.getInt(0) }

    /** 사용자 후보가 없으면 userId 는 null 로 저장한다. 업무용 여부는 반드시 정한다. */
    fun classify(tripId: Long, userId: Long?, nonBusiness: Boolean) {
        val values = ContentValues().apply {
            put("user_id", userId)
            put("non_business", if (nonBusiness) 1 else 0)
        }
        writableDatabase.update("trips", values, "id = ?", arrayOf(tripId.toString()))
    }

    /** 가장 최근에 분류한 운행의 사용자. 다음 운행 종료 화면의 기본 선택이 된다. */
    fun lastUserId(): Long? =
        readableDatabase.rawQuery(
            "SELECT user_id FROM trips WHERE user_id IS NOT NULL ORDER BY end_time DESC LIMIT 1",
            null,
        ).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }

    fun addUser(name: String, department: String?): Long {
        val values = ContentValues().apply {
            put("name", name)
            put("department", department)
        }
        return writableDatabase.insertOrThrow("users", null, values)
    }

    fun activeUsers(): List<User> =
        readableDatabase.query("users", null, "active = 1", null, null, null, "name ASC").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(User(c.getLong(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("name")), c.stringOrNull("department")))
                }
            }
        }

    fun deactivateUser(id: Long) {
        val values = ContentValues().apply { put("active", 0) }
        writableDatabase.update("users", values, "id = ?", arrayOf(id.toString()))
    }

    /** [startMillis, endMillis) 에 시작해 끝난 운행. 시작 시각 순. */
    fun endedTripsBetween(startMillis: Long, endMillis: Long): List<Trip> =
        readableDatabase.query(
            "trips", null, "end_time IS NOT NULL AND start_time >= ? AND start_time < ?",
            arrayOf(startMillis.toString(), endMillis.toString()), null, null, "start_time ASC",
        ).use { c -> buildList { while (c.moveToNext()) add(c.toTrip()) } }

    /** 끝난 운행의 시작 시각 전부. 내보낼 달 목록을 만들 때 쓴다. */
    fun endedTripStartTimes(): List<Long> =
        readableDatabase.rawQuery("SELECT start_time FROM trips WHERE end_time IS NOT NULL", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }

    /** 빠진 후보까지 포함한 모든 사용자 이름. 지난 운행의 사용자를 보여 줄 때 쓴다. */
    fun userNames(): Map<Long, String> =
        readableDatabase.rawQuery("SELECT id, name FROM users", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getLong(0), c.getString(1)) }
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
        private const val UNCLASSIFIED = "end_time IS NOT NULL AND non_business IS NULL"

        @Volatile
        private var instance: TripStore? = null

        fun get(context: Context): TripStore =
            instance ?: synchronized(this) {
                instance ?: TripStore(context.applicationContext).also { instance = it }
            }
    }
}
