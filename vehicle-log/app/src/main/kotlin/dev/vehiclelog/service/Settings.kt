package dev.vehiclelog.service

import android.content.Context

/** 앱 설정. 신호 출처(실차/재생)와 처음 실행 때 물어본 것들. */
object Settings {
    private const val PREFS = "settings"
    private const val KEY_REPLAY = "replay_mode"
    private const val KEY_SCREEN_ACCESS_ASKED = "screen_access_asked"

    fun replayMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_REPLAY, false)

    fun setReplayMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_REPLAY, enabled).apply()
    }

    /** 종료 화면 자동 표시 허용을 처음 한 번 스스로 물어봤는가. 그 뒤로는 메인 화면 버튼으로만 묻는다. */
    fun screenAccessAsked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SCREEN_ACCESS_ASKED, false)

    /** 설정을 처음 설치한 상태로 되돌린다 (신호는 실차, 허용은 다시 묻는다). 지우는 데 성공하면 true. */
    fun clear(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()

    fun setScreenAccessAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SCREEN_ACCESS_ASKED, true).apply()
    }
}
