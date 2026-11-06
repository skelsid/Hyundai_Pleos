# Pleos SDK 공개 API 정리

공식 문서 일부가 404 이거나 불완전해서, Maven 저장소의 AAR 을 받아 `javap` 로 공개 API 를 뽑았다. 같은 폴더의 `*.txt` 가 원본이고 이 문서는 사람이 읽는 요약이다.

- 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/` (인증 불필요)
- 확인 시점: 2026-10-25, 에뮬레이터 Pleos Connect v2.1.0 (API 34)

| SDK | 좌표 | 클래스 수 | 원본 |
|---|---|---|---|
| Vehicle | `ai.pleos.playground:Vehicle:2.3.2` | 176 | `vehicle.txt` |
| NaviHelper | `ai.pleos.playground:NaviHelper:3.0.5` | 53 | `navihelper.txt` |
| FusedLocation | `ai.pleos.playground:FusedLocation:1.1.3` | 9 | `fusedlocation.txt` |
| TextToSpeech | `ai.pleos.playground:TextToSpeech:3.3.1` | 17 | `texttospeech.txt` |
| SpeechToText | `ai.pleos.playground:SpeechToText:3.3.1` | 29 | `speechtotext.txt` |

딸려 오는 의존: vehicle-data 1.1.11, vehicle-aidl 1.1.1, navi-data 3.0.4, navi-aidl 3.0.1.

## 퍼미션

AAR 매니페스트와 바이트코드 안의 퍼미션 문자열에서 찾았다. **굵게** 표시한 것은 공식 문서에 없다.

| SDK | 퍼미션 | 비고 |
|---|---|---|
| Vehicle | `pleos.car.permission.CAR_*` | 조회 |
| Vehicle | `pleos.car.permission.CONTROL_CAR_*` | 제어. App Market 사전 승인 대상 |
| Vehicle | `pleos.car.permission.PRIVATE_CAR_*` | `getTirePressure` 가 `PRIVATE_CAR_SAFETY` 를 요구한다. 에뮬레이터에서 `protectionLevel:normal` 로 부여됨(실측). App Market 허용 여부는 미확인 |
| FusedLocation | `pleos.car.permission.FUSED_LOCATION` | |
| TextToSpeech | **`pleos.car.permission.TTS_SERVICE`** | 없으면 `initialize()` 가 SecurityException (실측) |
| SpeechToText | **`pleos.car.permission.STT_SERVICE`** | + `android.permission.RECORD_AUDIO` |
| NaviHelper | `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH` | 조회·경로 요청 |
| NaviHelper | **`NAVI_CUSTOM_ETC`, `NAVI_CUSTOM_MAP`, `NAVI_CUSTOM_ROUTE`** | 용도 미확인 |

## SDK 별 핵심과 실측 결과

### Vehicle
- `Vehicle(context).initialize()` 는 void 이고 IPC 바인딩이 비동기다. 반환 직후 조회하면 실패할 수 있다.
- 기어·EV 배터리 SOC 조회와 리스너 등록이 에뮬레이터에서 응답했다.
- **현재 차속 조회 API 가 없다.** 크루즈 목표 속도만 있다.
- `Gear` API 는 deprecated 다. AAOS 표준 `GEAR_SELECTION` 속성을 쓰라는 안내가 있다.
- 주행 거리(2026-11-06 실측): `getCurrentDistanceDriven` 은 응답하고 값은 `0.0` 이다. 이름상 누적 거리로 보이나 의미와 단위는 미확인이다. `getTripCurrentDrivingDistance`, `getTripCumulativeDrivingDistance` 는 `ERROR_NOT_SUPPORTED_PROPERTY`.
- 타이어(2026-11-06 실측): `getTirePressure` 는 에뮬레이터가 지원하지 않는다(`property ID is not in carPropertyConfig list, and so it is not supported: TIRE_PRESSURE`). 실차 응답은 미확인이다. `getLowTirePressure` 는 네 바퀴 모두 `137.0` 을 준다(단위 미확인).
- 경고등(2026-11-06 실측): `getTelltalesInfo` 는 응답하고 항목은 0건이다.

### NaviHelper
- `initialize(onOk, onErr)`, `addListener`.
- 경로: `requestRoute`, `requestRouteWithWaypoints`, `cancelRoute`, `addWaypoint`, `removeWaypoint`, `changeRouteOption`, `requestReRoute`.
- 조회: `getCurrentLocationInfo`, `getRouteStateInfo`, `getDestinationInfo`, `getWaypointInfo`, `getTBTInfo`, `getBookmarkInfo`, `getRecentDestinationInfo`, `getChargerOperatorInfo`.
- 화면: `requestNavigationScreen`, `requestTrip`.
- 리스너에 `onCurrentRoadSpeedLimit(int)`, `onCameraAlert` 등 주행 이벤트가 있다.
- 실측: 초기화, 현재 위치(성남 수정구 주소), 경로 상태 조회가 응답했다.

### FusedLocation
- `isSupported`, `getLastKnownLocation`, `registerFusedLocationCallback`.
- 실측: 에뮬레이터 GPS 를 따른다(기본값 37.42, -122.08). `adb emu geo fix <경도> <위도>` 로 바꾸면 바로 반영된다.
- **NaviHelper 는 이 GPS 를 따르지 않는다.** 내비 엔진이 자체 위치(성남 수정구 37.4127, 127.0947)를 쓴다. 둘을 맞추려면 `adb emu geo fix 127.094681 37.4127212`.

### TextToSpeech
- 모드: `HYBRID` / `SERVER` / `ON_DEVICE`. 서버 모드는 `registerApp(clientId, clientSecret)` 이 필요하다(Playground 계정 발급).
- **`speak(text, isFinal)` 의 두 번째 인자는 스트리밍의 마지막 조각 여부다.** `false` 면 텍스트를 쌓아만 두고 읽지 않는다. 한 문장을 바로 읽히려면 `true`. 서비스는 앱별로 쌓아 두므로 `false` 로 보낸 문장은 다음 `true` 때 한꺼번에 읽힌다.
- **STT 가 인식 중이면 TTS 는 서비스에 전달은 되지만 재생되지 않는다.** STT 를 끝낸 뒤 말하게 해야 한다.
- 실측: 위 두 가지를 지키면 ON_DEVICE 로 재생되고 `onStart` → `onDone` 이 온다.

### SpeechToText
- `request()`, `stop()`, `sendAudio(byte[])`, `completeAudioSend()`, `setLocale`, `registerApp`. `getCallApi`, `getAmbientAi` 도 있다(용도 미확인).
- 실측: ON_DEVICE 초기화 후 `ready` 까지 왔고 서비스가 에뮬레이터 마이크로 녹음·인식을 돌린다. 말소리를 넣어 본 적은 없어 인식 텍스트는 확인하지 못했다.

## 앱을 열지 않은 상태의 동작 (2026-11-06 실측)

앱을 한 번 실행한 뒤 재부팅해 `BOOT_COMPLETED` 수신기와 `dataSync` 포그라운드 서비스로 확인했다. 드라이버 사용자(u10)에서 자동 시작됐다.

| 기능 | 결과 |
|---|---|
| 기어, `getCurrentDistanceDriven` | 응답 |
| FusedLocation, NaviHelper 위치 | 응답 |
| TTS | `start` → `done` |
| STT | `ready` 까지. 마이크 app op 가 `mode=1` 이고, 앱 화면에서 실행하면 `mode=0` 이다 |

- 시스템이 `Foreground service started from background can not have location/camera/microphone access` 경고를 낸다. FusedLocation 은 그래도 응답한다.
- STT 는 `mode=1` 에서도 Pleos 음성 서비스가 녹음을 시작했다. 실제 말소리가 인식되는지는 미확인이다.
- 실차에서 시동을 끄고 켜는 것이 재부팅인지 절전 복귀인지는 미확인이다. `BOOT_COMPLETED` 에만 기대면 안 된다.
- Android 15 이상을 대상으로 하면 `dataSync` 는 부팅 직후 시작이 제한된다. 현재 대상은 API 34 다.

## 문서와 다른 점, 주의

- 로케일은 `Locale.KOREA`(ko-KR)로 줘야 한다. `Locale.KOREAN`(ko)은 엔진이 `unsupported locale: ko-` 로 거부한다.
- Virtual Console 로 기어·차속을 바꿀 수 없다(`emulator_inactive`, `bridge_ready=false`). 로그상 SELinux 가 `icvi-service` 실행을 막아(`avc: denied { execute } ... icvi_exec`) 재시작이 반복되고, 차량 HAL 은 `SecureStorageError: Connection refused` 를 낸다. user 빌드라 `-selinux permissive` 도 무시된다(실측). 이미지 결함으로 보이며 주최 측 문의 대상.
