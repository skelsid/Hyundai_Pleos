package dev.vehiclelog.trip

/**
 * 계기판 경고등 점검. 차량이 켜져 있다고 알려 준 경고등 가운데 정비·안전과 관련된 것만 골라 알린다.
 * 주차 브레이크·안전벨트·문 열림·등화·주행 모드 표시는 출발 직전에 켜져 있는 것이 정상이라 뺀다.
 * 이름은 SDK 의 TelltaleType 상수 이름 그대로다.
 */
object WarningCheck {
    /** 알릴 경고등과 읽어 줄 이름. 순서가 안내 순서다 */
    private val LABELS = linkedMapOf(
        "MASTER_WARNING" to "통합 경고",
        "ENGINE" to "엔진",
        "ENGINE_OIL_PRESSURE" to "엔진 오일 압력",
        "ENGINE_TEMPERATURE" to "엔진 과열",
        "EV_SYSTEM" to "전기차 시스템",
        "HEV_SYSTEM" to "하이브리드 시스템",
        "FCEV_SYSTEM" to "수소전기차 시스템",
        "H2_LEAK" to "수소 누출",
        "POWER_DOWN" to "출력 제한",
        "LOW_VOLT_BATTERY" to "배터리 충전",
        "ABS" to "ABS",
        "ESC" to "차체 자세 제어",
        "MDPS" to "전동 조향",
        "AIRBAG" to "에어백",
        "TPMS" to "타이어 공기압",
        "LOW_FUEL" to "연료 부족",
        "LOW_EV_BATTERY" to "구동 배터리 부족",
    )

    /** 타이어 공기압 안내([TireCheck])와 같은 뜻인 경고등 */
    private val TIRE_TYPES = setOf("TPMS")

    /** 연료(배터리) 부족 안내([DepartureCheck])와 같은 뜻인 경고등 */
    private val ENERGY_TYPES = setOf("LOW_FUEL", "LOW_EV_BATTERY")

    /** 켜진 경고등 이름들에서 알릴 것만 안내 순서대로 고른다. */
    fun important(activeTypes: Collection<String>): List<String> {
        val active = activeTypes.toSet()
        return LABELS.filterKeys { it in active }.values.toList()
    }

    /**
     * 음성으로 알릴 경고등. 출발 점검이 같은 내용을 이미 말했으면 뺀다 — 타이어 안내를 했으면 타이어 공기압 경고등,
     * 연료(배터리) 부족 안내를 했으면 연료·구동 배터리 부족 경고등. 화면 요약([important])에는 그대로 둔다.
     */
    fun spoken(activeTypes: Collection<String>, tireAnnounced: Boolean, energyAnnounced: Boolean): List<String> {
        val covered = (if (tireAnnounced) TIRE_TYPES else emptySet()) + (if (energyAnnounced) ENERGY_TYPES else emptySet())
        return important(activeTypes.filterNot { it in covered })
    }

    /** 음성 안내 문구. 알릴 경고등이 없으면 null (안내하지 않음). */
    fun message(labels: List<String>): String? =
        if (labels.isEmpty()) null else "계기판 경고등이 켜져 있습니다. ${labels.joinToString(", ")}. 점검해 주세요."

    /** 화면용 한 줄 요약. labels 가 null 이면 차량에서 값을 받지 못한 것이다. */
    fun summary(labels: List<String>?): String = when {
        labels == null -> "경고등 값 미수신"
        labels.isEmpty() -> "이상 없음"
        else -> labels.joinToString(", ")
    }
}
