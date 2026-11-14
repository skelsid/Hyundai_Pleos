package dev.vehiclelog.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.vehiclelog.signal.TripPoint
import dev.vehiclelog.trip.EnergyLevel
import dev.vehiclelog.trip.TripMath

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
    val startTripMeterKm: Double? = null,
    val endTripMeterKm: Double? = null,
    val startEnergy: EnergyLevel? = null,
    val endEnergy: EnergyLevel? = null,
) {
    /** 주행거리. 계기판 누적거리 차이를 먼저 쓰고, 없으면 트립 미터 차이를 쓴다. */
    val distanceKm: Double? get() = TripMath.tripDistanceKm(startOdometerKm, endOdometerKm, startTripMeterKm, endTripMeterKm)

    /** 계기판 값이 없어 트립 미터로 구한 거리인가. */
    val distanceFromTripMeter: Boolean get() = TripMath.distanceKm(startOdometerKm, endOdometerKm) == null && distanceKm != null
}

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
                non_business INTEGER,
                $V2_COLUMNS
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
        // 2: 트립 미터·연료(배터리) 잔량 칸 추가. 이전 운행은 빈 값으로 남는다
        if (oldVersion < 2) {
            for (column in V2_COLUMNS.split(",")) db.execSQL("ALTER TABLE trips ADD COLUMN ${column.trim()}")
        }
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
            put("start_trip_km", point.tripMeterKm)
            put("start_energy", point.energy?.percent)
            put("energy_battery", point.energy?.battery?.let { if (it) 1 else 0 })
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
            put("end_trip_km", point.tripMeterKm)
            put("end_energy", point.energy?.percent)
            // 출발 때 못 받았으면 도착 때 받은 종류를 남긴다
            point.energy?.let { put("energy_battery", if (it.battery) 1 else 0) }
            put("end_place", point.endPlaceName)
        }
        writableDatabase.update("trips", values, "id = ?", arrayOf(id.toString()))
    }

    /** 운행으로 볼 수 없는 짧은 움직임을 지운다. */
    fun deleteTrip(id: Long) {
        writableDatabase.delete("trips", "id = ?", arrayOf(id.toString()))
    }

    /**
     * 운행·사용자를 모두 지운다 (개인정보 동의 철회).
     * 지운 행이 DB 파일의 빈 공간에 남지 않게 VACUUM 으로 다시 쓰고, WAL 파일도 비운다.
     */
    fun eraseAll() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("trips", null, null)
            db.delete("users", null, null)
            // AUTOINCREMENT 번호도 처음부터
            db.delete("sqlite_sequence", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        db.execSQL("VACUUM")
        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
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

    /**
     * 사용자 후보가 없으면 userId 는 null 로 저장한다. 업무용 여부는 반드시 정한다.
     * 도착지는 운행 종료 화면에서 고친 값으로 덮는다 (비우면 null).
     */
    fun classify(tripId: Long, userId: Long?, nonBusiness: Boolean, endPlace: String?) {
        val values = ContentValues().apply {
            put("user_id", userId)
            put("non_business", if (nonBusiness) 1 else 0)
            put("end_place", endPlace)
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

    /** 같은 도착지로 끝난 다른 운행들의 최근 분류(true = 비업무). 미분류는 뺀다. */
    fun recentClassifications(endPlace: String, excludeTripId: Long, limit: Int): List<Boolean> =
        readableDatabase.rawQuery(
            "SELECT non_business FROM trips WHERE end_place = ? AND non_business IS NOT NULL AND id != ? " +
                "ORDER BY end_time DESC LIMIT $limit",
            arrayOf(endPlace, excludeTripId.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0) != 0L) } }

    /** 최근 도착지. 같은 이름은 한 번만, 마지막으로 간 순서대로. 운행 종료 화면에서 골라 쓴다. */
    fun recentEndPlaces(limit: Int): List<String> =
        readableDatabase.rawQuery(
            "SELECT end_place FROM trips WHERE end_place IS NOT NULL AND TRIM(end_place) != '' " +
                "GROUP BY end_place ORDER BY MAX(end_time) DESC LIMIT $limit",
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    private fun lastEndPlace(): String? =
        readableDatabase.rawQuery(
            "SELECT end_place FROM trips WHERE end_time IS NOT NULL ORDER BY end_time DESC LIMIT 1",
            null,
        ).use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }

    private fun Cursor.toTrip(): Trip {
        val battery = longOrNull("energy_battery")?.let { it != 0L } ?: false
        fun energy(column: String) = longOrNull(column)?.let { EnergyLevel(it.toInt(), battery) }
        return Trip(
            id = getLong(getColumnIndexOrThrow("id")),
            startTime = getLong(getColumnIndexOrThrow("start_time")),
            endTime = longOrNull("end_time"),
            startOdometerKm = doubleOrNull("start_odometer"),
            endOdometerKm = doubleOrNull("end_odometer"),
            startPlace = stringOrNull("start_place"),
            endPlace = stringOrNull("end_place"),
            userId = longOrNull("user_id"),
            nonBusiness = longOrNull("non_business")?.let { it != 0L },
            startTripMeterKm = doubleOrNull("start_trip_km"),
            endTripMeterKm = doubleOrNull("end_trip_km"),
            startEnergy = energy("start_energy"),
            endEnergy = energy("end_energy"),
        )
    }

    private fun Cursor.longOrNull(column: String): Long? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getLong(it) }

    private fun Cursor.doubleOrNull(column: String): Double? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getDouble(it) }

    private fun Cursor.stringOrNull(column: String): String? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }

    companion object {
        private const val DB_NAME = "vehicle_log.db"
        private const val DB_VERSION = 2
        private const val V2_COLUMNS = "start_trip_km REAL, end_trip_km REAL, start_energy INTEGER, end_energy INTEGER, energy_battery INTEGER"
        private const val UNCLASSIFIED = "end_time IS NOT NULL AND non_business IS NULL"

        @Volatile
        private var instance: TripStore? = null

        fun get(context: Context): TripStore =
            instance ?: synchronized(this) {
                instance ?: TripStore(context.applicationContext).also { instance = it }
            }
    }
}
