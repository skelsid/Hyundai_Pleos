package dev.vehiclelog.signal

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.vehiclelog.AppLog
import dev.vehiclelog.trip.Gear
import org.json.JSONArray

/** 재생 시나리오의 한 단계. 앞 단계에서 delayMs 뒤에 이 상태가 된다. */
data class ReplayStep(
    val delayMs: Long,
    val gear: Gear,
    val odometerKm: Double?,
    val latitude: Double?,
    val longitude: Double?,
    val placeName: String?,
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
        callback(TripPoint(System.currentTimeMillis(), step?.odometerKm, step?.latitude, step?.longitude, step?.placeName))
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
                )
            }
        }
    }
}
