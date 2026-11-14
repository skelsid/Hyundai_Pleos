package dev.vehiclelog.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TireCheckTest {

    private fun reading(lf: Double?, rf: Double?, lr: Double?, rr: Double?, low: Double? = null) = TireReading(
        mapOf(Wheel.LEFT_FRONT to lf, Wheel.RIGHT_FRONT to rf, Wheel.LEFT_REAR to lr, Wheel.RIGHT_REAR to rr),
        Wheel.entries.associateWith { low },
    )

    @Test
    fun test_evaluate_evenPressure_ok() {
        val result = TireCheck.evaluate(reading(36.0, 36.0, 35.0, 36.0))
        assertTrue(result.ok)
        assertNull(TireCheck.message(result))
    }

    @Test
    fun test_evaluate_oneWheelTenPercentLower_flagsUneven() {
        val result = TireCheck.evaluate(reading(36.0, 36.0, 30.0, 36.0))
        assertEquals(listOf(Wheel.LEFT_REAR), result.uneven)
        assertEquals("타이어를 확인해 주세요. 뒤 왼쪽 공기압이 다른 바퀴보다 낮습니다.", TireCheck.message(result))
    }

    @Test
    fun test_evaluate_oneWheelHigher_notFlagged() {
        // 기획서: 한 바퀴만 다른 바퀴보다 낮을 때 알린다. 높은 쪽은 알리지 않는다
        val result = TireCheck.evaluate(reading(36.0, 36.0, 42.0, 36.0))
        assertTrue(result.ok)
        assertNull(TireCheck.message(result))
    }

    @Test
    fun test_evaluate_justUnderTenPercentLower_notFlagged() {
        assertTrue(TireCheck.evaluate(reading(36.0, 36.0, 32.5, 36.0)).ok)
    }

    @Test
    fun test_evaluate_belowVehicleLowLimit_flagsLowNotDuplicated() {
        val result = TireCheck.evaluate(reading(36.0, 36.0, 25.0, 36.0, low = 28.0))
        assertEquals(listOf(Wheel.LEFT_REAR), result.low)
        assertEquals("타이어를 확인해 주세요. 뒤 왼쪽 공기압이 낮습니다.", TireCheck.message(result))
    }

    @Test
    fun test_evaluate_twoWheelsMissing_skipsComparison() {
        val result = TireCheck.evaluate(reading(36.0, null, 20.0, null))
        assertTrue(result.uneven.isEmpty())
        assertEquals(2, result.measured)
    }

    @Test
    fun test_evaluate_zeroValues_treatedAsMissing() {
        val result = TireCheck.evaluate(reading(0.0, 0.0, 0.0, 0.0))
        assertEquals(0, result.measured)
        assertEquals("공기압 값 미수신", TireCheck.summary(result))
    }
}
