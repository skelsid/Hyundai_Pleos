# 차량운행기록 (Vehicle Log)

업무용 차량의 **운행기록부를 자동으로 작성**하고, 출발할 때 **네 바퀴 타이어 공기압을 비교**해 알려 주는 Pleos Connect(AAOS) 앱이다.

법인 차량을 업무에 쓰면 운행기록부를 남겨야 하지만, 운전자가 매번 출발·도착 시각과 계기판 거리를 손으로 적기는 어렵다. 이 앱은 기어 변화로 운행을 감지해 시각·계기판 거리·위치·도착지를 자동으로 저장하고, 운전자는 운행이 끝났을 때 **사용자와 업무용/비업무만 고르면** 된다.

## 기능

| 기능 | 내용 |
|---|---|
| 운행 자동 감지 | 기어가 P 에서 벗어나면 운행 시작, 다시 P 에 들어가면 종료. 주차 자리 옮기기 같은 짧은 움직임(100m 미만·3분 미만)은 기록하지 않는다. 앱을 열지 않아도 부팅 뒤 포그라운드 서비스로 동작 |
| 자동 기록 | 시작·종료 시각, 계기판 누적거리, 위치(FusedLocation), 도착지(NaviHelper: 내비에 찍은 목적지 — 안내 시작·목적지 변경·도착 이벤트로 받는다. 내비 없이 다니면 현재 위치 주소) |
| 운행 종료 화면 | 음성 안내 + 종료 화면. 앱이 뒤에 있어도 "다른 앱 위에 표시" 허용이 있으면 바로 뜨고, 없으면 전체 화면 알림으로 연다. 자동으로 채운 도착지를 고치거나 이전 도착지 버튼으로 바꾸고, 등록된 사용자 중 고르고 [업무용]/[비업무] 한 번 누르면 끝. 마지막 사용자를 기본 선택 |
| 월별 운행기록 | 달별 운행 목록, 총 주행거리·업무용 거리·업무사용비율. 운행을 눌러 분류 수정 |
| 내보내기 (관리자 메일) | 운행이 있는 달 중 보낼 달을 여러 개 고르면, 달마다 운행기록표 양식(xlsx)을 채워 관리자 메일로 한 통에 보낸다. 20건마다 시트 추가, 비업무만 체크 |
| 관리자 등록 | 앱을 처음 열면 관리자 이름·메일을 먼저 등록한다. 메인 화면 [관리자 정보]에서 바꾼다 |
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
| NaviHelper | 3.0.5 | 도착지 이름, 현재 위치 주소 | `NAVI_ROUTE` |
| TextToSpeech | 3.3.1 | 운행 종료·타이어 음성 안내 (기기 내 엔진) | `TTS_SERVICE` |

조회용 퍼미션만 쓰고, 차량 제어(`CONTROL_*`) 퍼미션은 쓰지 않는다.

## 구조

```
vehicle-log/app/src/main/kotlin/dev/vehiclelog/
  signal/   차량 신호 계층 — PleosSignals(실차), ReplaySignals(시연용 재생)
  trip/     운행 감지·거리·월 합계·타이어 비교 (안드로이드 의존 없음, JVM 단위 테스트)
  data/     SQLite 운행 저장소, 차량 정보
  service/  포그라운드 서비스, 부팅 수신, 음성 안내
  export/   운행기록표 엑셀 생성 (zip 안 XML 직접 편집, POI 미사용), 관리자 메일 보내기
  mail/     Gmail SMTP 발송 (메일 라이브러리 없이 소켓으로, JVM 단위 테스트)
  ui/       화면 (레이아웃 XML 없이 코드로, 큰 글씨·큰 버튼)
```

화면 모양은 [Pleos Connect UI 디자인 가이드](https://document.pleos.ai/en/docs/connect/guide/docs-design/)를 따른다.
색·글자·크기는 가이드가 배포하는 디자인 자산(`Pleos_Connect_design_assets_215.fig`)의 변수와 컴포넌트 값을 그대로 쓴다.
크기는 화면 종류별 값(`connect-s` / `connect-w` / `connect-waterfall`)을 `ui/PleosTokens.kt` 에 옮겨 두고, 디스플레이 물리 해상도로 고른다(`ui/FormFactor.kt`: 긴 변 3840 = Wide, 3072 = Waterfall, 그 외 16:9 Standard). 레이아웃 문서에 따라 기존 Connect-L 은 Standard 로 본다.
색은 Light/Dark 값이고 낮/밤은 시스템 설정을 따른다(`ui/Ui.kt`, `res/values(-night)`).
컴포넌트는 상단바(`ivi_title` Main/Depth), 버튼(`ivi_basic_button` Large/Medium), 토글 버튼(`ivi_toggle_button` Large: 운행 종료 화면의 사용자, 보내기 화면의 달 고르기), 입력칸(`ivi_text_field` Medium), 목록 항목(`List/item`), 확인 창(`ivi_system_popup` Medium)을 옮겼다. 확인 창은 플랫폼 AlertDialog 대신 직접 그린다(AlertDialog 는 밤에도 밝게 그려진다).
글꼴은 가이드가 쓰는 Asta Sans(SIL OFL 1.1)를 앱에 넣었다. 라이선스 전문은 `app/src/main/assets/licenses/AstaSans-OFL.txt`.

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

### 메일 발신 계정

운행기록표는 발신 전용 Gmail 계정으로 보낸다(보낸 사람 이름: Hyundai Vehicle Log). 계정은 **APK 에 넣지 않고** 관리자가 앱의 [관리자 정보] 화면에서 입력해 그 기기에만 저장한다. APK 는 이 저장소에 올라가 공개되므로, 빌드에 넣으면 누구나 비밀번호를 꺼낼 수 있다.

1. 발신용 Gmail 계정을 만들고 2단계 인증을 켠다.
2. https://myaccount.google.com/apppasswords 에서 앱 비밀번호(16자리)를 만든다. 계정 비밀번호가 아니다.
3. 앱 [관리자 정보] → "보내는 Gmail 주소"와 "앱 비밀번호"에 입력한다. 띄어쓰기는 있어도 된다.

- 입력하지 않으면 내보내기에서 "보내는 메일 계정이 없습니다"로 안내한다.
- 비밀번호는 앱 전용 저장소에 평문으로 둔다(다른 앱은 못 읽고 백업에서 빠진다). 기기를 루팅하면 읽을 수 있으므로 **발신 전용 계정**만 쓴다.
- smtp.gmail.com:465(TLS)로 보낸다. 차량이 인터넷에 연결돼 있어야 한다.

## 에뮬레이터에서 시연하기

Pleos Connect 에뮬레이터는 기어·주행거리를 바꿀 수 없어 **재생 모드**로 시연한다. 앱 메인 화면 맨 아래 **[시연 모드 켜기]** 버튼으로 켠다(아래 adb 명령으로도 된다). 재생 모드는 `assets/replay_default.json` 의 시나리오(본사 → ○○상사 → 식당, 두 번째 출발에 뒤 왼쪽 타이어 저압)를 시간 순서대로 흘려보낸다.

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

### 팀원 확인 항목 (에뮬레이터)

1. **처음 실행** — 설치 후 앱을 열면 관리자 등록 화면이 먼저 뜨는지. 뒤로 가기로 건너뛸 수 없어야 한다.
2. **허용 안내** — 등록 뒤 알림 허용 → "다른 앱 위에 표시" 설정 안내가 차례로 뜨는지. 차량 설정 앱에 그 화면이 없으면 adb 로 준다.
   ```sh
   adb shell appops set --user 10 dev.vehiclelog SYSTEM_ALERT_WINDOW allow
   ```
   메인 화면 "종료 화면 자동 표시: 켜짐" 으로 바뀌는지 본다.
3. **뒤에서 종료 화면** — 재생 모드를 켜고 홈 화면이나 내비로 나간 뒤, 운행이 끝날 때 종료 화면이 앞으로 뜨는지. 로그 `event="trip.end.screen"` 에 허용 상태가 남는다. 안 뜨면 `adb logcat | grep -i "background activity"` 로 시스템이 막았는지 본다.
4. **메일 보내기** — [관리자 정보]에 보내는 Gmail 계정을 입력한 뒤, 월별 화면 [내보내기] → 달 여러 개 선택 → [관리자에게 보내기]. 관리자 메일함에 달마다 `VehicleLog_YYYY-MM.xlsx` 가 붙은 메일 한 통이 오는지.

## 알려진 한계 (실차 확인 필요)

- 계기판 누적거리(`getCurrentDistanceDriven`)의 단위를 km 로 가정했다. 에뮬레이터는 0 을 주므로 0 이하는 값 없음으로 처리한다.
- 타이어 공기압 단위(psi·kPa)를 확인하지 못해 절대값 대신 바퀴끼리의 비율과 차량이 주는 저압 기준값으로만 비교한다. 10% 기준은 가정값이다.
- `PRIVATE_CAR_SAFETY` 퍼미션이 App Market 에서 허용되는지 확인하지 못했다.
- 앱이 뒤에 있을 때 종료 화면이 뜨는지는 아직 에뮬레이터에서 확인하지 못했다(위 팀원 확인 항목 3). 차량 설정 앱에 "다른 앱 위에 표시" 화면이 있는지도 미확인이다.
- 메일 발송은 가짜 SMTP 서버로만 시험했다. 실제 Gmail 발송은 앱에 발신 계정을 입력해 확인해야 한다.
- 현재 위치 주소(`getCurrentLocationInfo`)는 에뮬레이터에서 내비 자체 위치(성남 수정구)를 준다. 실차에서 실제 위치 주소가 오는지는 미확인이다.
- 짧은 움직임을 버리는 기준(100m·3분, 거리 미수신이면 1분)은 가정값이다.
- 화면은 16:9(2560x1440, 160dpi) 에뮬레이터에서만 보았다. Wide·Waterfall 값은 화면 종류를 강제로 바꾼 빌드로 크기만 확인했고, 실제 그 화면과 실차의 화면 밀도는 확인하지 못했다(.fig 의 px 를 dp 로 본다).
