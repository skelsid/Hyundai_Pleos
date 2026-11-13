package dev.vehiclelog.data

import android.content.Context
import dev.vehiclelog.AppLog

/**
 * 운행기록표를 보내는 Gmail 발신 계정. 관리자가 앱에서 입력하고 이 기기에만 저장한다.
 * APK 에 넣지 않는다 — APK 는 공개 저장소에 올라가므로 누구나 꺼낼 수 있다.
 * 앱 비밀번호는 Android Keystore 키로 암호화해 앱 전용 저장소(다른 앱이 못 읽음, 백업 제외)에 둔다 ([SecretBox]).
 * 그래도 발신 전용 계정만 쓴다.
 */
data class MailAccount(val address: String, val appPassword: String, val fromName: String = DEFAULT_FROM_NAME) {

    // 로그·예외 메시지에 비밀번호가 찍히지 않게 한다
    override fun toString(): String = "MailAccount(address=***, appPassword=***, fromName=$fromName)"

    companion object {
        const val DEFAULT_FROM_NAME = "Hyundai Vehicle Log"
        private const val PREFS = "mail_account"
        private const val KEY_ADDRESS = "address"
        private const val KEY_PASSWORD = "app_password"

        /** 입력 전이거나 비밀번호를 풀 수 없으면(키가 지워짐 등) null. */
        fun load(context: Context): MailAccount? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val address = prefs.getString(KEY_ADDRESS, null)?.takeIf { it.isNotBlank() } ?: return null
            val stored = prefs.getString(KEY_PASSWORD, null)?.takeIf { it.isNotBlank() } ?: return null
            if (!SecretBox.isSealed(stored)) {
                // 예전 버전이 평문으로 저장한 값: 암호화해 다시 저장한다
                save(context, address, stored)
                AppLog.info("mail.account.migrated")
                return MailAccount(address, stored)
            }
            val password = SecretBox.open(stored)
            if (password == null) {
                AppLog.warn("mail.account.unreadable")
                return null
            }
            return MailAccount(address, password)
        }

        fun save(context: Context, address: String, appPassword: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_ADDRESS, address.trim())
                .putString(KEY_PASSWORD, SecretBox.seal(normalizePassword(appPassword)))
                .apply()
        }

        /** 저장한 계정과 암호화 키를 지운다 (개인정보 동의 철회). */
        fun clear(context: Context) {
            if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()) {
                AppLog.warn("mail.account.clear.failed")
            }
            SecretBox.deleteKey()
        }

        /** Gmail 은 앱 비밀번호를 4자리씩 띄어 보여 준다. 그대로 옮겨 적어도 되게 공백을 지운다. */
        fun normalizePassword(text: String): String = text.filterNot { it.isWhitespace() }
    }
}
