package dev.vehiclelog.data

import android.content.Context

/**
 * 운행기록표를 보내는 Gmail 발신 계정. 관리자가 앱에서 입력하고 이 기기에만 저장한다.
 * APK 에 넣지 않는다 — APK 는 공개 저장소에 올라가므로 누구나 꺼낼 수 있다.
 * 앱 전용 저장소(다른 앱이 못 읽음, 백업 제외)에 평문으로 둔다. 기기를 루팅하면 읽을 수 있으므로 발신 전용 계정만 쓴다.
 */
data class MailAccount(val address: String, val appPassword: String, val fromName: String = DEFAULT_FROM_NAME) {
    companion object {
        const val DEFAULT_FROM_NAME = "Hyundai Vehicle Log"
        private const val PREFS = "mail_account"
        private const val KEY_ADDRESS = "address"
        private const val KEY_PASSWORD = "app_password"

        /** 입력 전이면 null. */
        fun load(context: Context): MailAccount? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val address = prefs.getString(KEY_ADDRESS, null)?.takeIf { it.isNotBlank() } ?: return null
            val password = prefs.getString(KEY_PASSWORD, null)?.takeIf { it.isNotBlank() } ?: return null
            return MailAccount(address, password)
        }

        fun save(context: Context, address: String, appPassword: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_ADDRESS, address.trim())
                .putString(KEY_PASSWORD, normalizePassword(appPassword))
                .apply()
        }

        /** Gmail 은 앱 비밀번호를 4자리씩 띄어 보여 준다. 그대로 옮겨 적어도 되게 공백을 지운다. */
        fun normalizePassword(text: String): String = text.filterNot { it.isWhitespace() }
    }
}
