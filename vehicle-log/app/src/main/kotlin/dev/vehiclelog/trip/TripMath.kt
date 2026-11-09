package dev.vehiclelog.trip

/** 운행기록표 계산. 엑셀 양식의 수식과 같은 규칙을 쓴다. */
object TripMath {

    /** 계기판 거리 차이. 값이 없거나 줄어들면(계기판 오류·교체) 계산하지 않는다. */
    fun distanceKm(startOdometerKm: Double?, endOdometerKm: Double?): Double? {
        if (startOdometerKm == null || endOdometerKm == null) return null
        if (endOdometerKm < startOdometerKm) return null
        return endOdometerKm - startOdometerKm
    }

    /**
     * 업무사용비율 = 업무용 거리 / 전체 거리 (국세청 고시 서식 ⑬).
     * 출·퇴근은 업무용에 포함한다. 거리 계산이 안 된 운행은 양쪽에서 모두 뺀다.
     *
     * @return 0.0~1.0, 전체 거리가 0 이면 null
     */
    fun businessRatio(trips: List<TripDistance>): Double? {
        val measured = trips.filter { it.distanceKm != null }
        val total = measured.sumOf { it.distanceKm!! }
        if (total <= 0.0) return null
        val business = measured.filter { !it.nonBusiness }.sumOf { it.distanceKm!! }
        return business / total
    }
}

data class TripDistance(val distanceKm: Double?, val nonBusiness: Boolean)
