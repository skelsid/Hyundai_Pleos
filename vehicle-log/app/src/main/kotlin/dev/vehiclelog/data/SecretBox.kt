package dev.vehiclelog.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 비밀값(메일 앱 비밀번호)을 Android Keystore 의 AES 키로 암호화한다.
 * 키는 Keystore 밖으로 나오지 않으므로 저장 파일만 빼내서는 비밀번호를 읽을 수 없다
 * (Pleos 보안 지침: 토큰·자격 증명은 Keystore 로 보호).
 * 저장 형식: "v1:" + base64(IV) + ":" + base64(암호문+태그)
 */
object SecretBox {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "mail_app_password"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val PREFIX = "v1:"
    private const val SEPARATOR = ':'

    /** 이 형식으로 암호화된 값인가. 아니면 예전 버전이 평문으로 저장한 값이다. */
    fun isSealed(stored: String): Boolean = stored.startsWith(PREFIX)

    fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return format(cipher.iv, cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    /** 풀 수 없으면(형식 오류·키 없음·변조) null. */
    fun open(stored: String): String? {
        val (iv, sealed) = parse(stored) ?: return null
        val key = existingKey() ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            }
            String(cipher.doFinal(sealed), Charsets.UTF_8)
        }.getOrNull()
    }

    /** 키를 지운다. 이 키로 암호화한 값은 다시 풀 수 없다. */
    fun deleteKey() {
        keyStore().takeIf { it.containsAlias(ALIAS) }?.deleteEntry(ALIAS)
    }

    fun format(iv: ByteArray, sealed: ByteArray): String {
        val encoder = Base64.getEncoder()
        return PREFIX + encoder.encodeToString(iv) + SEPARATOR + encoder.encodeToString(sealed)
    }

    fun parse(stored: String): Pair<ByteArray, ByteArray>? {
        if (!isSealed(stored)) return null
        val parts = stored.removePrefix(PREFIX).split(SEPARATOR)
        if (parts.size != 2 || parts.any { it.isEmpty() }) return null
        val decoder = Base64.getDecoder()
        return runCatching { decoder.decode(parts[0]) to decoder.decode(parts[1]) }.getOrNull()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(ALIAS, null) as? SecretKey

    private fun key(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        generateKey()
    }
}
