# Pleos SDK 프로브

Pleos Connect 에뮬레이터에서 SDK 가 실제로 응답하는지 확인하는 최소 앱. 공모전 본 앱이 아니라 환경 점검용이다.

확인 항목: Vehicle(기어·배터리 SOC 조회와 이벤트), FusedLocation(지원 여부·위치), TextToSpeech(ON_DEVICE 초기화·발화), SpeechToText(ON_DEVICE 초기화·인식 요청), NaviHelper(초기화·현재 위치·경로 상태).
SDK 공개 API 정리는 `../docs/sdk-api/README.md` 에 있다.
결과는 화면과 logcat `PleosProbe` 태그에 `event="..." value="..."` 형식으로 남는다.

## 실행

전제: Android SDK(`%LOCALAPPDATA%\Android\Sdk`), Pleos Connect v2.1.0 AVD, Android Studio 동봉 JDK.

```sh
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew.bat assembleDebug
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.probe.pleos/.MainActivity
adb logcat -s PleosProbe:I
```

`local.properties` 에 `sdk.dir=C:/Users/<사용자>/AppData/Local/Android/Sdk` 가 필요하다(커밋 안 함).

## 알아낸 것 (2026-10-25, 에뮬레이터 v2.1.0)

- 문서에 없는 `pleos.car.permission.TTS_SERVICE` 퍼미션이 없으면 `TextToSpeech.initialize()` 가 SecurityException 으로 앱을 죽인다.
- Vehicle SDK 2.3.2 의 `Gear` API 는 deprecated (AAOS 표준 `GEAR_SELECTION` 속성을 쓰라는 안내).
- Vehicle SDK 에는 현재 차속 조회 API 가 없다(크루즈 목표 속도만 있음).
- `adb exec-out screencap` 은 검은 화면만 나온다. 화면은 `adb emu screenrecord screenshot <절대경로.png>` 로 찍는다.
- 에뮬레이터가 user 빌드라 `cmd car_service inject-vhal-event` 가 막혀 있다. 차량 신호는 Virtual Console(`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088) 로만 바꿀 수 있다.
- 문서에 없는 `pleos.car.permission.STT_SERVICE` 도 필요하다(SpeechToText AAR 바이트코드에서 확인). NaviHelper 는 `NAVI_ROUTE`·`NAVI_ROUTE_SEARCH` 로 조회가 됐다.
- TTS·STT 로케일은 `Locale.KOREA` 로 준다. `Locale.KOREAN` 은 `unsupported locale: ko-` 로 거부된다(STT 는 `ON_DEVICE_ENGINE_ERROR`).
- STT 는 `ready` 까지 온다. 에뮬레이터에 마이크 입력이 없어 인식 텍스트는 확인 못 했다.
- TTS 는 초기화만 성공했다. `speak()` 를 1초 늦춰 불러도 발화·이벤트가 없고, 서비스 로그에 `dev.probe.pleos not found check register` 가 찍힌다. 원인 미확인.
- NaviHelper 현재 위치(성남 수정구)와 FusedLocation 위치(37.42, -122.08 기본값)가 다르다. 위치 소스가 서로 다르다.
- 차량 HAL 일부가 재시작을 반복해 Virtual Console 이 `emulator_inactive` 를 돌려준다. 그래서 기어·차속 변경은 아직 못 해 봤다.
