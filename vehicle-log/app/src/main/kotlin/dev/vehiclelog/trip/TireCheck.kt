package dev.vehiclelog.trip

import kotlin.math.abs

enum class Wheel(val label: String) {
    LEFT_FRONT("앞 왼쪽"),
    RIGHT_FRONT("앞 오른쪽"),
    LEFT_REAR("뒤 왼쪽"),
    RIGHT_REAR("뒤 오른쪽"),
}

/** 네 바퀴 공기압과 차량이 주는 저압 기준값. 값을 못 받은 바퀴는 null. 단위는 SDK 가 주는 그대로. */
data class TireReading(val pressure: Map<Wheel, Double?>, val lowLimit: Map<Wheel, Double?>)

data class TireResult(
    /** 차량 저압 기준보다 낮은 바퀴 */
    val low: List<Wheel>,
    /** 다른 바퀴들과 차이가 큰 바퀴 */
    val uneven: List<Wheel>,
    /** 값을 받은 바퀴 수 */
    val measured: Int,
) {
    val ok: Boolean get() = low.isEmpty() && uneven.isEmpty()
}

/**
 * 네 바퀴 공기압 비교. 단위(psi·kPa)를 SDK 문서에서 확인하지 못해 절대값 대신 비율로 비교한다:
 * 네 바퀴 중앙값에서 [UNEVEN_RATIO] 이상 벗어난 바퀴를 '차이 큼'으로 본다.
 * 차량이 주는 저압 기준값이 있으면 그보다 낮은 바퀴는 '낮음'으로 따로 알린다.
 */
object TireCheck {
    /** 가정값: 같은 차의 바퀴끼리 10% 넘게 다르면 점검 대상으로 본다. 실차 자료로 조정 필요 */
    const val UNEVEN_RATIO = 0.10
    private const val MIN_WHEELS_TO_COMPARE = 3

    fun evaluate(reading: TireReading): TireResult {
        val values = Wheel.entries.mapNotNull { w -> reading.pressure[w]?.takeIf { it > 0.0 }?.let { w to it } }
        val low = values.filter { (w, p) -> reading.lowLimit[w]?.let { limit -> limit > 0.0 && p < limit } == true }.map { it.first }
        val uneven = if (values.size < MIN_WHEELS_TO_COMPARE) {
            emptyList()
        } else {
            val median = median(values.map { it.second })
            values.filter { (_, p) -> abs(p - median) / median >= UNEVEN_RATIO }.map { it.first }
        }
        return TireResult(low, uneven, values.size)
    }

    /** 음성 안내 문구. 이상이 없거나 값을 거의 못 받았으면 null (안내하지 않음). */
    fun message(result: TireResult): String? {
        if (result.ok) return null
        val parts = mutableListOf<String>()
        if (result.low.isNotEmpty()) parts += "${names(result.low)} 공기압이 낮습니다"
        val unevenOnly = result.uneven - result.low.toSet()
        if (unevenOnly.isNotEmpty()) parts += "${names(unevenOnly)} 공기압이 다른 바퀴와 차이가 납니다"
        return "타이어를 확인해 주세요. " + parts.joinToString(". ") + "."
    }

    /** 화면용 한 줄 요약. */
    fun summary(result: TireResult): String = when {
        result.measured == 0 -> "공기압 값 미수신"
        result.ok && result.measured < Wheel.entries.size -> "이상 없음 (${result.measured}개 바퀴만 확인)"
        result.ok -> "이상 없음"
        else -> message(result)!!.removePrefix("타이어를 확인해 주세요. ")
    }

    private fun names(wheels: List<Wheel>) = wheels.joinToString(", ") { it.label }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }
}
