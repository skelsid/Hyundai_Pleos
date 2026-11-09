# 차량운행기록 (Vehicle Log)

업무용 차량의 **운행기록부를 자동으로 작성**하고, 출발할 때 **네 바퀴 타이어 공기압을 비교**해 알려 주는 Pleos Connect(AAOS) 앱이다.

법인 차량을 업무에 쓰면 운행기록부를 남겨야 하지만, 운전자가 매번 출발·도착 시각과 계기판 거리를 손으로 적기는 어렵다. 이 앱은 기어 변화로 운행을 감지해 시각·계기판 거리·위치·도착지를 자동으로 저장하고, 운전자는 운행이 끝났을 때 **사용자와 업무용/비업무만 고르면** 된다.

## 기능

| 기능 | 내용 |
|---|---|
| 운행 자동 감지 | 기어가 P 에서 벗어나면 운행 시작, 다시 P 에 들어가면 종료. 앱을 열지 않아도 부팅 뒤 포그라운드 서비스로 동작 |
| 자동 기록 | 시작·종료 시각, 계기판 누적거리, 위치(FusedLocation), 내비 도착지 이름(NaviHelper) |
| 운행 종료 화면 | 음성 안내 + 종료 화면(앱이 뒤에 있으면 전체 화면 알림). 등록된 사용자 중 고르고 [업무용]/[비업무] 한 번 누르면 끝. 마지막 사용자를 기본 선택 |
| 월별 운행기록 | 달별 운행 목록, 총 주행거리·업무용 거리·업무사용비율. 운행을 눌러 분류 수정 |
| 엑셀 내보내기 | 운행기록표 양식(xlsx)에 그대로 채워 저장. 20건마다 시트 추가, 비업무만 체크 |
| 타이어 공기압 비교 | 출발할 때 네 바퀴 값을 읽어 다른 바퀴와 10% 이상 차이 나거나 차량 저압 기준보다 낮으면 음성으로 알림 |
| 사용자·차량 정보 | 사용자(이름·부서) 사전 등록, 법인명·차량번호·차종·차대번호·부서 입력(엑셀 머리글에 들어감) |

- 출퇴근은 업무용으로 분류한다.
- 주행 중에는 입력 화면(사용자 관리·차량 정보)이 열리지 않는다. 종료·월별·메인 화면만 `distractionOptimized` 로 열어 둔다.
- 값을 받지 못한 항목은 **추정하지 않고 비워 둔다**(엑셀 비고에 "계기판 값 미수신" 등). 실차에서 값이 오면 코드 변경 없이 채워진다.

## 사용하는 Pleos SDK·퍼미션

| SDK | 버전 | 용도 | 퍼미션 |
|---|---|---|---|
| Vehicle | 2.3.2 | 기어, 계기판 누적거리, 타이어 공기압·저압 기준값 | `CAR_DRIVE`, `CAR_SAFETY`, `PRIVATE_CAR_SAFETY` |
| FusedLocation | 1.1.3 | 출발·도착 위치 | `FUSED_LOCATION` |
| NaviHelper | 3.0.5 | 도착지 이름 | `NAVI_ROUTE` |
| TextToSpeech | 3.3.1 | 운행 종료·타이어 음성 안내 (기기 내 엔진) | `TTS_SERVICE` |

조회용 퍼미션만 쓰고, 차량 제어(`CONTROL_*`) 퍼미션은 쓰지 않는다.

## 구조

```
vehicle-log/app/src/main/kotlin/dev/vehiclelog/
  signal/   차량 신호 계층 — PleosSignals(실차), ReplaySignals(시연용 재생)
  trip/     운행 감지·거리·월 합계·타이어 비교 (안드로이드 의존 없음, JVM 단위 테스트)
  data/     SQLite 운행 저장소, 차량 정보
  service/  포그라운드 서비스, 부팅 수신, 음성 안내
  export/   운행기록표 엑셀 생성 (zip 안 XML 직접 편집, POI 미사용)
  ui/       화면 (레이아웃 XML 없이 코드로, 큰 글씨·큰 버튼)
```

## 빌드

요구: Android Studio(JDK 17 포함), Android SDK 36. Pleos SDK 는 공개 Maven 저장소에서 받는다(`settings.gradle.kts`).

```sh
cd vehicle-log
./gradlew testDebugUnitTest   # 단위 테스트
./gradlew assembleDebug       # 디버그 APK
./gradlew assembleRelease     # 릴리스 APK
```

릴리스 서명은 `vehicle-log/keystore.properties`(커밋하지 않음)에서 읽는다. 파일이 없으면 서명 없는 릴리스가 나온다.

```properties
storeFile=release.keystore
storePassword=...
keyAlias=vehiclelog
keyPassword=...
```

제출용 APK: `apk/1등하면채용해주세요_차량운행기록(VehicleLog)_release.apk`

## 에뮬레이터에서 시연하기

Pleos Connect 에뮬레이터는 기어·주행거리를 바꿀 수 없어 **재생 모드**로 시연한다. 재생 모드는 `assets/replay_default.json` 의 시나리오(본사 → ○○상사 → 식당, 두 번째 출발에 뒤 왼쪽 타이어 저압)를 시간 순서대로 흘려보낸다.

```sh
adb install -r --user 10 apk/1등하면채용해주세요_차량운행기록\(VehicleLog\)_release.apk
# 재생 모드 켜기 (서비스가 다시 시작되며 시나리오가 처음부터 돈다)
adb shell am start --user 10 -n dev.vehiclelog/.ui.MainActivity --ez replay true
# 재생 모드 끄기 (실차 신호)
adb shell am start --user 10 -n dev.vehiclelog/.ui.MainActivity --ez replay false --activity-clear-top
```

- 명령을 보내면 앱에 확인 창이 뜬다. [바꾸기]를 눌러야 바뀐다(다른 앱이 몰래 가짜 신호로 바꾸지 못하게).
- 재생 모드의 운행도 실제 기록과 같은 저장소에 쌓인다. 실차에서는 켜지 않는다.
- 에뮬레이터의 사용자 번호는 10 이다(`adb shell am get-current-user`). 실차에서는 `--user` 를 빼거나 해당 번호를 쓴다.
- 앱 로그: `adb logcat -s VehicleLog`

## 알려진 한계 (실차 확인 필요)

- 계기판 누적거리(`getCurrentDistanceDriven`)의 단위를 km 로 가정했다. 에뮬레이터는 0 을 주므로 0 이하는 값 없음으로 처리한다.
- 타이어 공기압 단위(psi·kPa)를 확인하지 못해 절대값 대신 바퀴끼리의 비율과 차량이 주는 저압 기준값으로만 비교한다. 10% 기준은 가정값이다.
- `PRIVATE_CAR_SAFETY` 퍼미션이 App Market 에서 허용되는지 확인하지 못했다.
- 앱이 뒤에 있을 때 전체 화면 알림으로 종료 화면이 열리는지는 실차·에뮬레이터에서 확인하지 못했다.
- 엑셀 파일은 앱 전용 문서 폴더(`Android/data/dev.vehiclelog/files/Documents/`)에 저장된다. 차량에서 꺼내는 방법(USB·공유)은 실차 환경에 맞춰 정해야 한다.
- 시작 직후 3초 안에 끝나는 아주 짧은 운행은 시작·종료 조회 순서가 뒤집힐 수 있다.
