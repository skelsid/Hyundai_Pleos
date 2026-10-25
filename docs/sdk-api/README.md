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
| Vehicle | `pleos.car.permission.PRIVATE_CAR_*` | 용도 미확인 |
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

### NaviHelper
- `initialize(onOk, onErr)`, `addListener`.
- 경로: `requestRoute`, `requestRouteWithWaypoints`, `cancelRoute`, `addWaypoint`, `removeWaypoint`, `changeRouteOption`, `requestReRoute`.
- 조회: `getCurrentLocationInfo`, `getRouteStateInfo`, `getDestinationInfo`, `getWaypointInfo`, `getTBTInfo`, `getBookmarkInfo`, `getRecentDestinationInfo`, `getChargerOperatorInfo`.
- 화면: `requestNavigationScreen`, `requestTrip`.
- 리스너에 `onCurrentRoadSpeedLimit(int)`, `onCameraAlert` 등 주행 이벤트가 있다.
- 실측: 초기화, 현재 위치(성남 수정구 주소), 경로 상태 조회가 응답했다.

### FusedLocation
- `isSupported`, `getLastKnownLocation`, `registerFusedLocationCallback`.
- 실측: 응답했지만 좌표가 37.42, -122.08(에뮬레이터 기본값)이다. **NaviHelper 위치와 소스가 다르다.**

### TextToSpeech
- 모드: `HYBRID` / `SERVER` / `ON_DEVICE`. 서버 모드는 `registerApp(clientId, clientSecret)` 이 필요하다(Playground 계정 발급).
- 실측: ON_DEVICE `initialize()` 는 성공했다. **`speak()` 는 발화가 확인되지 않았다.** start/done/error 이벤트가 오지 않았고 서비스 로그에도 호출이 남지 않았다. 서비스 쪽에 `dev.probe.pleos not found check register` 가 찍힌다. 원인 미확인.

### SpeechToText
- `request()`, `stop()`, `sendAudio(byte[])`, `completeAudioSend()`, `setLocale`, `registerApp`. `getCallApi`, `getAmbientAi` 도 있다(용도 미확인).
- 실측: ON_DEVICE 초기화 후 `ready` 까지 왔다. 마이크 입력이 없어 인식 텍스트는 확인하지 못했다.

## 문서와 다른 점, 주의

- 로케일은 `Locale.KOREA`(ko-KR)로 줘야 한다. `Locale.KOREAN`(ko)은 엔진이 `unsupported locale: ko-` 로 거부한다.
- 에뮬레이터의 차량 HAL 일부가 계속 재시작해서 Virtual Console 로 기어·차속을 바꿀 수 없었다(`emulator_inactive`). 주최 측 문의 대상.
