package dev.vehiclelog.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DepartureCheckTest {

    private fun all(level: FluidLevel?) = Fluid.entries.associateWith { level }

    @Test
    fun test_evaluate_allNormalAndFuelEnough_ok() {
        val result = DepartureCheck.evaluate(all(FluidLevel.NORMAL), EnergyLevel(60, battery = false))
        assertTrue(result.ok)
        assertNull(DepartureCheck.message(result))
        assertEquals("이상 없음", DepartureCheck.summary(result))
    }

    @Test
    fun test_evaluate_lowAndCritical_separated() {
        val fluids = mapOf(
            Fluid.ENGINE_OIL to FluidLevel.LOW,
            Fluid.BRAKE_FLUID to FluidLevel.CRITICALLY_LOW,
            Fluid.TRANSMISSION_OIL to FluidLevel.NORMAL,
        )
        val result = DepartureCheck.evaluate(fluids, null)
        assertEquals(listOf(Fluid.ENGINE_OIL), result.lowFluids)
        assertEquals(listOf(Fluid.BRAKE_FLUID), result.criticalFluids)
        assertEquals(
            "브레이크액이 매우 부족합니다. 바로 점검해 주세요. 엔진오일이 부족합니다.",
            DepartureCheck.message(result),
        )
        assertEquals("브레이크액 매우 부족 · 엔진오일 부족", DepartureCheck.summary(result))
    }

    @Test
    fun test_evaluate_lowFuel_announced() {
        val result = DepartureCheck.evaluate(all(FluidLevel.NORMAL), EnergyLevel(14, battery = false))
        assertEquals("연료가 14퍼센트 남았습니다.", DepartureCheck.message(result))
        assertEquals("연료 14%", DepartureCheck.summary(result))
    }

    @Test
    fun test_evaluate_lowBattery_usesBatteryLabel() {
        val result = DepartureCheck.evaluate(all(null), EnergyLevel(DepartureCheck.LOW_ENERGY_PERCENT, battery = true))
        assertEquals("배터리가 15퍼센트 남았습니다.", DepartureCheck.message(result))
    }

    @Test
    fun test_evaluate_noValues_notOkButNothingToSay() {
        // 값을 못 받으면 경고하지 않는다 (에뮬레이터·미지원 차량)
        val result = DepartureCheck.evaluate(all(null), null)
        assertTrue(result.ok)
        assertEquals("소모품 값 미수신", DepartureCheck.summary(result))
    }

    @Test
    fun test_evaluate_errorState_treatedAsUnknown() {
        val result = DepartureCheck.evaluate(all(FluidLevel.ERROR), EnergyLevel(16, battery = false))
        assertTrue(result.ok)
        assertEquals(0, result.measuredFluids)
    }
}
