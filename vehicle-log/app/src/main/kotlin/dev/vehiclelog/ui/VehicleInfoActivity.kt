package dev.vehiclelog.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import dev.vehiclelog.AppLog
import dev.vehiclelog.data.VehicleInfo
import dev.vehiclelog.signal.CarIdentity

/**
 * 운행기록표 머리 정보 입력. 차량번호·차대번호가 비어 있으면 차량(Vehicle SDK CarInfo)에서 읽어 채운다.
 * 채운 값도 [저장]을 눌러야 남는다. 차량이 값을 주지 않으면 직접 입력한다.
 * 입력 칸이 있으므로 주행 중에는 시스템이 막는다 (distractionOptimized 아님).
 */
class VehicleInfoActivity : Activity() {

    private var carIdentity: CarIdentity? = null

    override fun onStart() {
        super.onStart()
        // 관리 화면이다. 앱을 떠났다 돌아와 잠겼으면 닫는다
        AdminSession.requireUnlocked(this)
    }

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
        val note = Ui.body(this, "")
        root.addView(note)
        listOf(corporation, plate, model, vin, department).forEach(root::addView)
        if (info.plate.isEmpty() || info.vin.isEmpty()) {
            carIdentity = CarIdentity(this).also { reader ->
                reader.read { car ->
                    // 그 사이 사람이 적은 칸은 건드리지 않는다
                    val filled = listOfNotNull(
                        car.registration?.takeIf { plate.text.isEmpty() }?.let { plate.setText(it); "차량번호" },
                        car.vin?.takeIf { vin.text.isEmpty() }?.let { vin.setText(it); "차대번호" },
                    )
                    if (filled.isNotEmpty()) note.text = "${filled.joinToString("·")}를 차량에서 읽어 채웠습니다. 확인한 뒤 저장해 주세요."
                }
            }
        }
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

    override fun onDestroy() {
        carIdentity?.release()
        super.onDestroy()
    }
}
