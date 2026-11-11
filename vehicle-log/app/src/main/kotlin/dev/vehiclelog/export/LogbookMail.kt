package dev.vehiclelog.export

import android.content.Context
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.AdminInfo
import dev.vehiclelog.data.MailAccount
import dev.vehiclelog.data.VehicleInfo
import dev.vehiclelog.mail.MailAttachment
import dev.vehiclelog.mail.MailException
import dev.vehiclelog.mail.MailMessage
import dev.vehiclelog.mail.SmtpSender
import dev.vehiclelog.trip.YearMonth
import java.util.TimeZone

/**
 * 고른 달들의 운행기록표를 만들어 관리자 메일로 한 통에 보낸다. 메인 스레드가 아닌 곳에서 부른다.
 * 엑셀은 보내기 직전에 다시 만든다 — 그 사이 분류를 고쳤어도 최신 내용이 가게.
 */
object LogbookMail {
    private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun send(context: Context, months: List<YearMonth>, admin: AdminInfo, zone: TimeZone = TimeZone.getDefault()) {
        require(months.isNotEmpty()) { "보낼 달이 없다" }
        val account = MailAccount.load(context)
            ?: throw MailException(MailException.Kind.NOT_CONFIGURED, "발신 계정이 입력되지 않았다")
        val sorted = months.distinct().sorted()
        val files = sorted.map { LogbookExport.export(context, it.year, it.month, zone) }
        val info = VehicleInfo.load(context)
        val message = MailMessage(
            fromAddress = account.address,
            fromName = account.fromName,
            to = admin.email,
            subject = subject(info.plate, sorted),
            body = body(admin.name, info, sorted),
            attachments = files.map { MailAttachment(it.name, XLSX, it.readBytes()) },
        )
        SmtpSender().send(account.address, account.appPassword, message)
        AppLog.info("mail.sent", "months" to sorted.size)
    }

    /** 예: "[차량운행기록] 12가3456 운행기록표 2026년 10월, 11월" */
    fun subject(plate: String, months: List<YearMonth>): String = buildString {
        append("[차량운행기록] ")
        if (plate.isNotBlank()) append(plate.trim()).append(' ')
        append("운행기록표 ").append(monthList(months))
    }

    fun body(adminName: String, info: VehicleInfo, months: List<YearMonth>): String = buildString {
        append(if (adminName.isNotBlank()) "${adminName.trim()} 님, 안녕하세요.\n\n" else "안녕하세요.\n\n")
        append("차량운행기록 앱에서 운행기록표 ${months.size}개월치를 보냅니다.\n\n")
        val car = listOf(info.corporation, info.plate, info.model).filter { it.isNotBlank() }.joinToString(" · ")
        if (car.isNotEmpty()) append("차량: ").append(car).append('\n')
        append("기간: ").append(monthList(months)).append("\n\n")
        append("이 메일은 차량에서 자동으로 보낸 메일입니다.\n")
    }

    /** 같은 해는 묶어 쓴다: "2026년 10월, 11월", 해가 바뀌면 "2025년 12월, 2026년 1월". */
    fun monthList(months: List<YearMonth>): String {
        val sorted = months.distinct().sorted()
        val parts = mutableListOf<String>()
        var lastYear: Int? = null
        for (m in sorted) {
            parts += if (m.year == lastYear) "${m.month}월" else "${m.year}년 ${m.month}월"
            lastYear = m.year
        }
        return parts.joinToString(", ")
    }
}
