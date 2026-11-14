package dev.vehiclelog.signal

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.vehiclelog.AppLog
import dev.vehiclelog.trip.EnergyLevel
import dev.vehiclelog.trip.Fluid
import dev.vehiclelog.trip.FluidLevel
import dev.vehiclelog.trip.Gear
import dev.vehiclelog.trip.TireReading
import dev.vehiclelog.trip.Wheel
import org.json.JSONArray

/** 재생 시나리오의 한 단계. 앞 단계에서 delayMs 뒤에 이 상태가 된다. */
data class ReplayStep(
    val delayMs: Long,
    val gear: Gear,
    val odometerKm: Double?,
    val latitude: Double?,
    val longitude: Double?,
    val placeName: String?,
    /** 앞왼·앞오·뒤왼·뒤오 순서. 없으면 공기압 미수신으로 본다 */
    val tires: List<Double?> = emptyList(),
    /** 연료 잔량(%). 없으면 미수신 */
    val fuelPercent: Int? = null,
    /** 부족한 소모품만 적는다. 적지 않은 것은 정상으로 본다 */
    val fluids: Map<Fluid, FluidLevel> = emptyMap(),
    /** 켜져 있는 경고등의 TelltaleType 이름들. 없으면 경고등 없음 */
    val warnings: List<String> = emptyList(),
)

/**
 * 시연용 재생 신호. assets 의 JSON 시나리오를 시간 순서대로 흘려보낸다.
 * 에뮬레이터는 기어·주행거리를 바꿀 수 없어 이 모드로 시연한다.
 */
class ReplaySignals(private val steps: List<ReplayStep>) : VehicleSignals {

    override val name = "replay"

    private val handler = Handler(Looper.getMainLooper())
    private var current: ReplayStep? = null

    override fun start(onGear: (Gear) -> Unit) {
        var at = 0L
        for (step in steps) {
            at += step.delayMs
            handler.postDelayed({
                current = step
                AppLog.info("signal.replay.step", "gear" to step.gear, "odometerKm" to step.odometerKm, "place" to step.placeName)
                onGear(step.gear)
            }, at)
        }
    }

    override fun readPoint(callback: (TripPoint) -> Unit) {
        val step = current
        callback(
            TripPoint(
                System.currentTimeMillis(), step?.odometerKm, step?.latitude, step?.longitude, step?.placeName,
                energy = step?.fuelPercent?.let { EnergyLevel(it, battery = false) },
            ),
        )
    }

    override fun readTires(callback: (TireReading) -> Unit) {
        val tires = current?.tires.orEmpty()
        val pressure = Wheel.entries.withIndex().associate { (i, w) -> w to tires.getOrNull(i) }
        // 재생 시나리오에는 저압 기준값이 없다(상대 비교만 시연)
        callback(TireReading(pressure, Wheel.entries.associateWith { null }))
    }

    override fun readFluids(callback: (Map<Fluid, FluidLevel?>) -> Unit) {
        val listed = current?.fluids.orEmpty()
        callback(Fluid.entries.associateWith { listed[it] ?: FluidLevel.NORMAL })
    }

    override fun readWarnings(callback: (List<String>?) -> Unit) {
        callback(current?.warnings.orEmpty())
    }

    override fun onTripStarted() = Unit

    override fun stop() {
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        const val DEFAULT_ASSET = "replay_default.json"

        fun fromAsset(context: Context, asset: String = DEFAULT_ASSET): ReplaySignals {
            val text = context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() }
            return ReplaySignals(parse(text))
        }

        fun parse(json: String): List<ReplayStep> {
            val array = JSONArray(json)
            return List(array.length()) { i ->
                val o = array.getJSONObject(i)
                ReplayStep(
                    delayMs = o.getLong("delayMs"),
                    gear = Gear.valueOf(o.getString("gear")),
                    odometerKm = o.optDouble("odometerKm").takeUnless { it.isNaN() },
                    latitude = o.optDouble("lat").takeUnless { it.isNaN() },
                    longitude = o.optDouble("lon").takeUnless { it.isNaN() },
                    placeName = o.optString("place").takeIf { it.isNotBlank() },
                    tires = o.optJSONArray("tires")?.let { t ->
                        List(t.length()) { j -> t.optDouble(j).takeUnless { it.isNaN() } }
                    }.orEmpty(),
                    fuelPercent = o.optInt("fuel", -1).takeIf { it in 0..100 },
                    fluids = o.optJSONObject("fluids")?.let { f ->
                        Fluid.entries.mapNotNull { fluid ->
                            f.optString(fluid.name).takeIf { it.isNotBlank() }?.let { fluid to FluidLevel.valueOf(it) }
                        }.toMap()
                    }.orEmpty(),
                    warnings = o.optJSONArray("warnings")?.let { w -> List(w.length()) { j -> w.getString(j) } }.orEmpty(),
                )
            }
        }
    }
}
