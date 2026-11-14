package dev.vehiclelog.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * 관리자 비밀번호를 넣은 뒤 관리 화면이 열려 있는 동안. 앱이 화면에서 사라지면 다시 잠근다 —
 * 관리자가 열어 둔 채 내려도 다음 사람이 관리 화면을 쓰지 못하게.
 * UI 스레드에서만 쓴다.
 */
object AdminSession {
    var unlocked = false
        private set

    private var watching = false
    private val started = HashSet<Activity>()

    fun unlock() {
        unlocked = true
    }

    fun lock() {
        unlocked = false
    }

    /** 관리 화면의 onStart 에서 부른다. 잠겨 있으면 그 화면을 닫는다. */
    fun requireUnlocked(activity: Activity) {
        if (!unlocked) activity.finish()
    }

    /** 앱의 모든 화면이 멈추면 잠그도록 지켜본다. 여러 번 불러도 한 번만 건다. */
    fun watch(application: Application) {
        if (watching) return
        watching = true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started += activity
            }

            override fun onActivityStopped(activity: Activity) {
                // 지켜보기 전에 떠 있던 화면(서비스가 띄운 종료 화면)이 멈춘 것은 세지 않는다
                if (!started.remove(activity)) return
                // 밤/낮 전환처럼 화면을 다시 만드는 중이면 잠그지 않는다
                if (started.isEmpty() && !activity.isChangingConfigurations) lock()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
