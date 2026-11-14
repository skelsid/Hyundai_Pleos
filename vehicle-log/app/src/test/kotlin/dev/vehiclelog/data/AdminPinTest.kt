package dev.vehiclelog.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminPinTest {

    @Test
    fun test_isValidFormat_onlyFourDigits_returnsTrue() {
        assertTrue(AdminPin.isValidFormat("0000"))
        assertTrue(AdminPin.isValidFormat("1234"))
        for (bad in listOf("", "123", "12345", "12a4", "12 4", "１２３４")) assertFalse(bad, AdminPin.isValidFormat(bad))
    }

    @Test
    fun test_hash_sameSalt_sameResult_differentSalt_differentResult() {
        val salt = ByteArray(16) { it.toByte() }
        assertArrayEquals(AdminPin.hash("1234", salt), AdminPin.hash("1234", salt))
        assertFalse(AdminPin.hash("1234", salt).contentEquals(AdminPin.hash("1235", salt)))
        assertFalse(AdminPin.hash("1234", salt).contentEquals(AdminPin.hash("1234", ByteArray(16))))
    }

    @Test
    fun test_attempt_match_resetsFails() {
        val (next, result) = AdminPin.attempt(AdminPin.State(fails = 3), matches = true, now = 1_000L)
        assertEquals(AdminPin.Result.Ok, result)
        assertEquals(AdminPin.State(), next)
    }

    @Test
    fun test_attempt_wrong_countsDownThenLocks() {
        var state = AdminPin.State()
        for (left in AdminPin.MAX_FAILS - 1 downTo 1) {
            val (next, result) = AdminPin.attempt(state, matches = false, now = 1_000L)
            assertEquals(AdminPin.Result.Wrong(left), result)
            state = next
        }
        val (locked, result) = AdminPin.attempt(state, matches = false, now = 1_000L)
        assertEquals(AdminPin.Result.Locked(1_000L + AdminPin.LOCK_MS), result)
        assertEquals(AdminPin.State(0, 1_000L + AdminPin.LOCK_MS), locked)
    }

    @Test
    fun test_attempt_whileLocked_rejectsEvenCorrectPin_thenAcceptsAfter() {
        val state = AdminPin.State(0, 5_000L)
        assertEquals(AdminPin.Result.Locked(5_000L), AdminPin.attempt(state, matches = true, now = 4_999L).second)
        assertEquals(AdminPin.Result.Ok, AdminPin.attempt(state, matches = true, now = 5_000L).second)
    }
}
