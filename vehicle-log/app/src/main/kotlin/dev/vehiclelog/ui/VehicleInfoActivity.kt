package dev.vehiclelog.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.VehicleInfo

/**
 * 운행기록표 머리 정보 입력. 차대번호는 SDK 조회 API 가 없어 직접 입력한다.
 * 입력 칸이 있으므로 주행 중에는 시스템이 막는다 (distractionOptimized 아님).
 */
class VehicleInfoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val info = VehicleInfo.load(this)
        val root = Ui.column(this)
        root.addView(Ui.header(this, Ui.title(this, "차량 정보")))
        val corporation = Ui.input(this, "법인명", info.corporation)
        val plate = Ui.input(this, "차량번호", info.plate)
        val model = Ui.input(this, "차종", info.model)
        val vin = Ui.input(this, "차대번호", info.vin, caps = true)
        val department = Ui.input(this, "관리 부서", info.department)
        listOf(corporation, plate, model, vin, department).forEach(root::addView)
        root.addView(
            Ui.button(this, "저장", filled = true) {
                VehicleInfo.save(
                    this,
                    VehicleInfo(
                        corporation.text.toString(),
                        plate.text.toString(),
                        model.text.toString(),
                        vin.text.toString(),
                        department.text.toString(),
                    ),
                )
                AppLog.info("vehicle.saved")
                Toast.makeText(this, "저장했습니다", Toast.LENGTH_SHORT).show()
                finish()
            },
        )
        setContentView(Ui.screen(this, root))
    }
}
