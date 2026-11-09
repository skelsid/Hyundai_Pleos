package dev.vehiclelog.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.vehiclelog.AppLog

/** 앱을 열지 않아도 차량 부팅 뒤 운행 감지를 시작한다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        AppLog.info("boot.received")
        TripService.start(context)
    }
}
