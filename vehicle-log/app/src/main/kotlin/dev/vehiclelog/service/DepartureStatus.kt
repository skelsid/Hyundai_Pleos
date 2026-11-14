package dev.vehiclelog.service

import android.content.Context

/** 마지막 출발 점검 결과(타이어 공기압, 소모품·연료). 화면 표시용 한 줄과 시각. */
object DepartureStatus {
    private const val PREFS = "tire_status"
    private const val KEY_SUMMARY = "summary"
    private const val KEY_TIME = "time"
    private const val KEY_DEPARTURE = "departure_summary"

    data class Last(val summary: String, val timeMillis: Long)

    fun save(context: Context, summary: String, timeMillis: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SUMMARY, summary)
            .putLong(KEY_TIME, timeMillis)
            .apply()
    }

    /** 소모품·연료 점검 한 줄. 타이어와 같은 출발 시각에 남긴다. */
    fun saveDeparture(context: Context, summary: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_DEPARTURE, summary).apply()
    }

    fun loadDeparture(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DEPARTURE, null)

    fun load(context: Context): Last? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val summary = prefs.getString(KEY_SUMMARY, null) ?: return null
        return Last(summary, prefs.getLong(KEY_TIME, 0L))
    }

    /** 지운다 (개인정보 동의 철회). 디스크에 바로 쓰고 성공 여부를 돌려준다. */
    fun clear(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
}
