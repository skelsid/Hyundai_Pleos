package dev.probe.pleos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** 앱을 열지 않아도 부팅 뒤 자동 실행되는지 확인한다. 운행 자동 기록의 전제다. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i(TripWatchService.TAG, "event=\"boot.received\" value=\"ok\"")
        context.startForegroundService(Intent(context, TripWatchService::class.java))
    }
}
