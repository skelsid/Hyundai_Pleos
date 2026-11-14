package dev.vehiclelog.data

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 관리자 비밀번호(숫자 4자리). 앱 관리 화면은 이 비밀번호를 넣어야 열린다.
 * 비밀번호 자체는 저장하지 않고 소금을 섞은 해시만 둔다. 4자리라 해시만으로는 약하므로
 * 여러 번 틀리면 잠시 잠가 하나씩 넣어 보는 것을 막는다.
 * 잊어버리면 복구할 방법이 없다 (앱 데이터를 지우고 다시 등록).
 */
object AdminPin {
    const val LENGTH = 4
    const val MAX_FAILS = 5
    const val LOCK_MS = 30_000L

    private const val PREFS = "admin_pin"
    private const val KEY_SALT = "salt"
    private const val KEY_HASH = "hash"
    private const val KEY_FAILS = "fails"
    private const val KEY_LOCKED_UNTIL = "locked_until"
    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256
    private const val ITERATIONS = 10_000

    /** 틀린 횟수와 잠금이 풀리는 시각. */
    data class State(val fails: Int = 0, val lockedUntil: Long = 0L)

    sealed interface Result {
        data object Ok : Result

        /** [left]: 잠기기 전까지 남은 횟수 */
        data class Wrong(val left: Int) : Result

        data class Locked(val untilMillis: Long) : Result
    }

    fun isValidFormat(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, HASH_BITS))
            .encoded

    /** 한 번 넣어 본 결과와 다음 상태. 잠긴 동안에는 맞는 번호도 받지 않는다. */
    fun attempt(state: State, matches: Boolean, now: Long): Pair<State, Result> {
        if (now < state.lockedUntil) return state to Result.Locked(state.lockedUntil)
        if (matches) return State() to Result.Ok
        val fails = state.fails + 1
        if (fails >= MAX_FAILS) {
            val until = now + LOCK_MS
            return State(0, until) to Result.Locked(until)
        }
        return State(fails, 0L) to Result.Wrong(MAX_FAILS - fails)
    }

    fun isSet(context: Context): Boolean = prefs(context).contains(KEY_HASH)

    fun set(context: Context, pin: String) {
        require(isValidFormat(pin)) { "비밀번호는 숫자 $LENGTH 자리" }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val encoder = Base64.getEncoder()
        prefs(context).edit()
            .putString(KEY_SALT, encoder.encodeToString(salt))
            .putString(KEY_HASH, encoder.encodeToString(hash(pin, salt)))
            .remove(KEY_FAILS)
            .remove(KEY_LOCKED_UNTIL)
            .apply()
    }

    fun check(context: Context, pin: String, now: Long = System.currentTimeMillis()): Result {
        val prefs = prefs(context)
        val decoder = Base64.getDecoder()
        val salt = prefs.getString(KEY_SALT, null)?.let(decoder::decode)
        val stored = prefs.getString(KEY_HASH, null)?.let(decoder::decode)
        val matches = salt != null && stored != null && isValidFormat(pin) && MessageDigest.isEqual(stored, hash(pin, salt))
        val state = State(prefs.getInt(KEY_FAILS, 0), prefs.getLong(KEY_LOCKED_UNTIL, 0L))
        val (next, result) = attempt(state, matches, now)
        prefs.edit().putInt(KEY_FAILS, next.fails).putLong(KEY_LOCKED_UNTIL, next.lockedUntil).apply()
        return result
    }

    /** 지운다 (개인정보 동의 철회). 디스크에 바로 쓰고 성공 여부를 돌려준다. */
    fun clear(context: Context): Boolean = prefs(context).edit().clear().commit()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
