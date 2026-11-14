package dev.vehiclelog.trip

/** 출발 때 확인하는 차량 소모품. 차량이 수준 상태만 준다 (Vehicle SDK Safety). */
enum class Fluid(val label: String) {
    ENGINE_OIL("엔진오일"),
    BRAKE_FLUID("브레이크액"),
    TRANSMISSION_OIL("변속기오일"),
}

/** 소모품 수준. SDK 의 OilLevelState 와 같은 값. */
enum class FluidLevel { NORMAL, HIGH, LOW, CRITICALLY_LOW, ERROR }

/** 연료 또는 배터리 잔량(%). battery 면 전기차 배터리. */
data class EnergyLevel(val percent: Int, val battery: Boolean) {
    val label: String get() = if (battery) "배터리" else "연료"
}

data class DepartureResult(
    /** 부족한 소모품. 매우 부족한 것이 앞에 온다 */
    val lowFluids: List<Fluid>,
    val criticalFluids: List<Fluid>,
    /** 잔량이 [DepartureCheck.LOW_ENERGY_PERCENT] 이하일 때만 */
    val lowEnergy: EnergyLevel?,
    /** 값을 받은 소모품 수 */
    val measuredFluids: Int,
) {
    val ok: Boolean get() = lowFluids.isEmpty() && criticalFluids.isEmpty() && lowEnergy == null
}

/**
 * 출발 점검. 엔진오일·브레이크액·변속기오일 부족과 연료(배터리) 부족을 본다.
 * 타이어 공기압([TireCheck])과 함께 출발 순간 한 번 음성으로 알린다.
 */
object DepartureCheck {
    /** 가정값: 남은 연료·배터리가 이 이하이면 알린다. 차량 자체 경고등보다 조금 일찍 알리려는 값 */
    const val LOW_ENERGY_PERCENT = 15

    fun evaluate(fluids: Map<Fluid, FluidLevel?>, energy: EnergyLevel?): DepartureResult {
        val measured = Fluid.entries.filter { fluids[it] != null && fluids[it] != FluidLevel.ERROR }
        return DepartureResult(
            lowFluids = measured.filter { fluids[it] == FluidLevel.LOW },
            criticalFluids = measured.filter { fluids[it] == FluidLevel.CRITICALLY_LOW },
            lowEnergy = energy?.takeIf { it.percent in 0..LOW_ENERGY_PERCENT },
            measuredFluids = measured.size,
        )
    }

    /** 음성 안내 문구. 이상이 없으면 null. */
    fun message(result: DepartureResult): String? {
        if (result.ok) return null
        val parts = mutableListOf<String>()
        if (result.criticalFluids.isNotEmpty()) parts += "${names(result.criticalFluids)}이 매우 부족합니다. 바로 점검해 주세요"
        if (result.lowFluids.isNotEmpty()) parts += "${names(result.lowFluids)}이 부족합니다"
        result.lowEnergy?.let { parts += "${it.label}가 ${it.percent}퍼센트 남았습니다" }
        return parts.joinToString(". ") + "."
    }

    /** 화면용 한 줄 요약. */
    fun summary(result: DepartureResult): String {
        val parts = mutableListOf<String>()
        if (result.criticalFluids.isNotEmpty()) parts += "${names(result.criticalFluids)} 매우 부족"
        if (result.lowFluids.isNotEmpty()) parts += "${names(result.lowFluids)} 부족"
        result.lowEnergy?.let { parts += "${it.label} ${it.percent}%" }
        return when {
            parts.isNotEmpty() -> parts.joinToString(" · ")
            result.measuredFluids == 0 -> "소모품 값 미수신"
            else -> "이상 없음"
        }
    }

    private fun names(fluids: List<Fluid>) = fluids.joinToString(", ") { it.label }
}
