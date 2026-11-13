package dev.vehiclelog.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormFactorTest {

    @Test
    fun test_of_standardDisplays_returnsS() {
        assertEquals(FormFactor.S, FormFactor.of(2560, 1440))
        assertEquals(FormFactor.S, FormFactor.of(2048, 1152))
        assertEquals(FormFactor.S, FormFactor.of(1920, 1080))
    }

    @Test
    fun test_of_wideStandardAndExtended_returnsW() {
        assertEquals(FormFactor.W, FormFactor.of(3840, 850))
        assertEquals(FormFactor.W, FormFactor.of(3840, 1440))
    }

    @Test
    fun test_of_waterfall_returnsWF() {
        assertEquals(FormFactor.WF, FormFactor.of(3072, 816))
    }

    @Test
    fun test_of_portrait_usesLongSide() {
        assertEquals(FormFactor.W, FormFactor.of(850, 3840))
        assertEquals(FormFactor.S, FormFactor.of(1440, 2560))
    }

    @Test
    fun test_of_unknownOrZero_returnsS() {
        assertEquals(FormFactor.S, FormFactor.of(1280, 720))
        assertEquals(FormFactor.S, FormFactor.of(0, 0))
    }

    @Test
    fun test_dimen_of_picksValuePerFormFactor() {
        val height = PleosTokens.BUTTON_LARGE_HEIGHT
        assertEquals(100, height.of(FormFactor.S))
        assertEquals(84, height.of(FormFactor.W))
        assertEquals(106, height.of(FormFactor.WF))
    }
}
