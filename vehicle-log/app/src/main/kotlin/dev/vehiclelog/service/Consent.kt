package dev.vehiclelog.service

import android.app.NotificationManager
import android.content.Context
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.AdminInfo
import dev.vehiclelog.data.AdminPin
import dev.vehiclelog.data.MailAccount
import dev.vehiclelog.data.TripStore
import dev.vehiclelog.data.VehicleInfo
import dev.vehiclelog.export.LogbookExport

/**
 * 개인정보 수집·이용 동의. 동의하기 전에는 운행을 기록하지 않는다.
 * Pleos 심사 기준(review-user-privacy-security): 수집 항목·목적·보관 기간·거부할 권리를 앱 안에서 알리고,
 * 동의 버튼을 눌러야만 동의로 본다(뒤로 가기·바깥 터치는 동의 아님). 철회하면 모은 정보를 바로 지운다.
 */
object Consent {
    private const val PREFS = "consent"
    private const val KEY_VERSION = "agreed_version"

    /** 알리는 내용(수집 항목 등)이 바뀌면 올린다. 그러면 다시 동의를 받는다. */
    private const val NOTICE_VERSION = 2

    const val TITLE = "개인정보 수집·이용 동의"

    // 수집 항목은 실제로 저장하는 것과 맞춘다: TripStore(운행·사용자), AdminInfo, MailAccount, VehicleInfo, DepartureStatus, WarningStatus
    val NOTICE = """
        차량운행기록은 업무용 차량 운행기록표를 만들려고 아래 정보를 이 차량 안에만 저장합니다.

        [수집 항목]
        · 운행: 시작·종료 시각, 계기판 누적 거리·트립 거리, 연료(배터리) 잔량, 출발·도착 위치(좌표·주소), 내비 목적지, 업무용 여부
        · 사람: 운전자 이름·부서, 관리자 이름·메일, 보내는 메일 계정(비밀번호는 암호화)
        · 차량: 법인명, 차량번호, 차종, 차대번호, 부서, 출발 점검 결과(타이어 공기압, 엔진오일·브레이크액·변속기오일, 계기판 경고등)

        [이용 목적] 월별 운행기록표(엑셀) 작성, 관리자 메일 발송
        [보관 기간] 동의를 철회하거나 앱을 지울 때까지. 철회하면 바로 모두 지웁니다.
        [외부 전송] 관리자가 [보내기]를 누를 때만, 등록한 관리자 메일로 운행기록표를 보냅니다. 보낸 파일은 차량에 남기지 않습니다.

        동의하지 않을 수 있습니다. 그때는 운행을 기록하지 않습니다.
        동의는 [관리자 정보] 화면에서 언제든 철회할 수 있습니다.
    """.trimIndent()

    fun agreed(context: Context): Boolean = prefs(context).getInt(KEY_VERSION, 0) >= NOTICE_VERSION

    fun agree(context: Context) {
        prefs(context).edit().putInt(KEY_VERSION, NOTICE_VERSION).apply()
        AppLog.info("consent.agreed", "version" to NOTICE_VERSION)
    }

    /**
     * 동의를 거두고 모은 정보를 모두 지운다. 디스크를 쓰므로 메인 스레드에서 부르지 않는다.
     * 운행 감지 서비스는 부르는 쪽에서 먼저 멈춘다. 앱 설정(시연 모드 등)도 처음 상태로 되돌린다:
     * 남겨 두면 다시 동의하자마자 시연용 가짜 운행이 기록에 쌓인다.
     */
    fun withdrawAndErase(context: Context) {
        // 먼저 동의를 거둬, 아직 끝나지 않은 서비스 작업이 더 기록하지 않게 한다
        if (!prefs(context).edit().clear().commit()) AppLog.warn("consent.clear.failed")
        TripStore.get(context).eraseAll()
        val cleared = listOf(
            AdminInfo.clear(context), AdminPin.clear(context), VehicleInfo.clear(context),
            DepartureStatus.clear(context), WarningStatus.clear(context), Settings.clear(context),
        )
        if (!cleared.all { it }) AppLog.warn("consent.prefs.clear.failed")
        MailAccount.clear(context)
        val files = LogbookExport.deleteFiles(context)
        context.getSystemService(NotificationManager::class.java)?.cancelAll()
        AppLog.info("consent.withdrawn", "deletedFiles" to files)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
