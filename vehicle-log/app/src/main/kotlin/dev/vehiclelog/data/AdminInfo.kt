package dev.vehiclelog.data

import android.content.Context

/**
 * 운행기록표를 메일로 받는 관리자. 앱을 처음 설치하면 먼저 등록한다.
 * 기기 밖으로 나가지 않는 앱 전용 저장소에 둔다 (백업 규칙에서 제외됨).
 */
data class AdminInfo(val name: String, val email: String) {
    companion object {
        private const val PREFS = "admin"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"

        /** 등록 전이면 null. */
        fun load(context: Context): AdminInfo? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val email = prefs.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() } ?: return null
            return AdminInfo(prefs.getString(KEY_NAME, "").orEmpty(), email)
        }

        fun save(context: Context, info: AdminInfo) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_NAME, info.name.trim())
                .putString(KEY_EMAIL, info.email.trim())
                .apply()
        }
    }
}
