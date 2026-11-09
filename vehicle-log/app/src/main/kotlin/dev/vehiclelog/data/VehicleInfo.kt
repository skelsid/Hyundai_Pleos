package dev.vehiclelog.data

import android.content.Context

/**
 * 운행기록표 머리에 들어가는 차량 정보. 차대번호는 SDK 에 조회 API 가 없어 직접 입력한다.
 * 빈 칸은 빈 문자열로 둔다.
 */
data class VehicleInfo(
    val corporation: String = "",
    val plate: String = "",
    val model: String = "",
    val vin: String = "",
    val department: String = "",
) {
    companion object {
        private const val PREFS = "vehicle_info"
        private const val KEY_CORPORATION = "corporation"
        private const val KEY_PLATE = "plate"
        private const val KEY_MODEL = "model"
        private const val KEY_VIN = "vin"
        private const val KEY_DEPARTMENT = "department"

        fun load(context: Context): VehicleInfo {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            fun read(key: String) = prefs.getString(key, "").orEmpty()
            return VehicleInfo(read(KEY_CORPORATION), read(KEY_PLATE), read(KEY_MODEL), read(KEY_VIN), read(KEY_DEPARTMENT))
        }

        fun save(context: Context, info: VehicleInfo) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_CORPORATION, info.corporation.trim())
                .putString(KEY_PLATE, info.plate.trim())
                .putString(KEY_MODEL, info.model.trim())
                .putString(KEY_VIN, info.vin.trim())
                .putString(KEY_DEPARTMENT, info.department.trim())
                .apply()
        }
    }
}
