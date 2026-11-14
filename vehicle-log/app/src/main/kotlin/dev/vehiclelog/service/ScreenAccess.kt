package dev.vehiclelog.service

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * 앱이 뒤에 있을 때 운행 종료 화면을 앞으로 띄우는 데 필요한 허용 상태.
 *
 * 안드로이드 10 이후로 뒤에 있는 앱은 화면을 마음대로 띄울 수 없다. 길은 둘이다.
 * 1. 다른 앱 위에 표시 허용 — 이게 있으면 화면을 바로 띄울 수 있다 (가장 확실).
 * 2. 전체 화면 알림 — 알림 허용(안드로이드 13+)과 전체 화면 알림 허용(14+)이 모두 있어야 한다.
 *    차량(AAOS) 시스템 UI 는 전체 화면 알림을 상단 알림으로만 보여 줄 수 있어 1 을 먼저 쓴다.
 */
object ScreenAccess {

    fun canDrawOverApps(context: Context): Boolean = android.provider.Settings.canDrawOverlays(context)

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun fullScreenAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /** 종료 화면이 뒤에서도 뜰 수 있는 상태인가. */
    fun canShowTripEnd(context: Context): Boolean =
        canDrawOverApps(context) || (notificationsAllowed(context) && fullScreenAllowed(context))

    /**
     * 종료 화면 자동 표시를 위해 더 받을 것이 남았는가 — 알림 허용을 아직 못 받았거나, 열 수 있는 허용 설정 화면이 있다.
     * 없으면 메인 화면의 [켜기] 버튼을 숨긴다 (눌러도 바뀌는 것이 없다).
     */
    fun canAskMore(context: Context): Boolean {
        if (canDrawOverApps(context)) return false
        return !notificationsAllowed(context) ||
            overlaySettings(context) != null ||
            (!fullScreenAllowed(context) && fullScreenSettings(context) != null)
    }

    /** "다른 앱 위에 표시" 설정 화면. 차량 설정 앱에 이 화면이 없으면 null. */
    fun overlaySettings(context: Context): Intent? =
        Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
            .takeIf { it.resolveActivity(context.packageManager) != null }

    /** "전체 화면 알림" 설정 화면 (안드로이드 14+). 없으면 null. */
    fun fullScreenSettings(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName))
            .takeIf { it.resolveActivity(context.packageManager) != null }
    }
}
