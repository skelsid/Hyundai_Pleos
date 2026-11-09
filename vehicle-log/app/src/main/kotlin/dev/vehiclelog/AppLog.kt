package dev.vehiclelog

import android.util.Log

/** logcat 에 key=value 한 줄로 남긴다. `adb logcat -s VehicleLog` 로 추려 본다. */
object AppLog {
    const val TAG = "VehicleLog"

    fun info(event: String, vararg fields: Pair<String, Any?>) = Log.i(TAG, format(event, fields))

    fun warn(event: String, vararg fields: Pair<String, Any?>) = Log.w(TAG, format(event, fields))

    fun error(event: String, error: Throwable, vararg fields: Pair<String, Any?>) =
        Log.e(TAG, format(event, fields), error)

    private fun format(event: String, fields: Array<out Pair<String, Any?>>): String =
        buildString {
            append("event=\"").append(event).append('"')
            for ((key, value) in fields) append(' ').append(key).append("=\"").append(value).append('"')
        }
}
