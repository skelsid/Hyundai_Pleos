package dev.vehiclelog.service

import android.content.Context

/** 앱 설정. 지금은 신호 출처(실차/재생)만 있다. */
object Settings {
    private const val PREFS = "settings"
    private const val KEY_REPLAY = "replay_mode"

    fun replayMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_REPLAY, false)

    fun setReplayMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_REPLAY, enabled).apply()
    }
}
