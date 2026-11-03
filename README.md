# 영업 이동 비서

차가 주행을 자동으로 기록하고, 도착하는 순간 음성 한마디로 방문 내용을 받아
**국세청 업무용승용차 운행기록부**와 **영업 방문일지**를 함께 만드는 Pleos Connect 앱입니다.
운전자는 화면을 거의 만지지 않습니다.

## 주요 기능

| 기능 | 설명 |
| --- | --- |
| 주행 자동 기록 | 시동·기어·속도·주차브레이크 신호로 출발과 도착을 판단하고, 계기판 주행거리(Odometer)를 그대로 기록 |
| 신호등 정차 구분 | P단·주차브레이크가 일정 시간 유지돼야 도착으로 확정. 휴게소 정차는 내비 목적지를 보고 경유지로 기록 |
| 도착 시 음성 질문 | "OO 도착하셨네요. 방문 기록을 남길까요?" → 음성 답변 원문 저장 → 거래처·담당자·요약·후속 일정으로 정리 |
| 운행기록부 CSV | 월별로 국세청 양식 순서의 CSV를 다운로드 폴더에 저장 |
| 주행 중 화면 제한 | 주행 중에는 어떤 화면이든 "기록 중" 화면만 표시 |
| 차량 프로필 | 운전자는 차량 사용자 프로필로 구분(별도 로그인 없음). 시동 시 내 시트 메모리 번호를 불러옴 |
| 위치 | 출발·도착·경유 지점 좌표만 기록(촘촘한 경로는 저장하지 않음) |

## 빌드 환경

- Android Studio, JDK 17, Gradle 8.14 / AGP 8.10, Kotlin 2.1
- compileSdk 35, minSdk 28
- Pleos Connect 에뮬레이터(Android Automotive API 34). 설치 방법은 Pleos 개발자 문서의 Connect SDK 에뮬레이터 가이드 참고

### Pleos 앱 등록 정보 (선택)

음성 인식·LLM을 서버 모드로 쓰려면 `local.properties`에 Pleos Playground에서 받은 값을 넣습니다.
이 파일은 git에 올라가지 않습니다. 값이 없으면 On-device 모드로 동작하고, LLM이 응답하지 않으면 규칙 기반 파서로 방문 내용을 정리합니다.

```properties
pleos.clientId=<Pleos Playground 앱 Client ID>
pleos.clientSecret=<Pleos Playground 앱 Client Secret>
```

## 실행

```bash
./gradlew :app:installDebug        # 에뮬레이터에 설치
./gradlew :app:testDebugUnitTest   # 단위 테스트
```

처음 실행하면 속도·위치 권한을 묻습니다. 거부해도 속도는 기어로, 위치는 빈칸으로 두고 기록합니다.
그다음 **내 정보** 탭에서 성명·부서·차량번호를 입력하세요.

## 시연하기

- **버튼 하나로**: 신호 확인 탭 → "시연 주행 재생". 출발 → 주행 → 거래처 도착 → 주차 → 음성 질문까지 매번 같은 순서로 재생합니다.
- **Virtual Console로**: `tools/vc-drive.sh [시작 km] [주행 km]`. 에뮬레이터의 차량 신호를 바꿔 실제 신호 경로로 한 사이클을 돕니다.
  주행거리는 줄어들 수 없으므로 시작 km는 현재 값 이상으로 줍니다. 에뮬레이터를 다시 켜면 Virtual Console 주행거리는 0으로 돌아갑니다.
- **운행기록부 가져오기**: 운행기록부 탭에서 CSV 내보내기 → `tools/pull-logbook.sh` 로 바탕화면에 복사합니다.

### 에뮬레이터 시간대

Pleos 에뮬레이터는 기본 시간대가 미국 서부라 기록 시각이 한국 시각과 다르게 보일 수 있습니다.
에뮬레이터 설정의 날짜 및 시간에서 시간대를 서울로 바꾸세요. 앱은 기기 시간대를 그대로 쓰므로 한국 차량에서는 따로 할 일이 없습니다.
현재 시간대 확인: `adb shell getprop persist.sys.timezone`

## 구조

```
app/src/main/java/com/salesdrive/
├── vehicle/   Pleos SDK·AAOS 차량 속성·위치를 앱 공통 신호(VehicleSnapshot)로 변환 — SDK 의존은 여기만
├── trip/      트립 상태머신, 상태 전이를 DB에 기록, 포그라운드 서비스
├── navi/      NaviHelper 목적지·도착 이벤트, 휴게소 정차 판단
├── voice/     TTS 질문, STT 응답, LLM/규칙 기반 방문 파서
├── profile/   시동 시 시트 메모리 불러오기
├── data/      Room DB(Trip, TripStop, Visit, DriverProfile, Vehicle)와 저장소
├── export/    운행기록부 CSV
└── ui/        Jetpack Compose 화면(홈, 운행기록부, 내 정보, 신호 확인, 방문 확인, 기록 중)
```

```
IDLE ─시동─▶ READY ─출발─▶ DRIVING ◀─재출발/정지─▶ PAUSED ─주차 유지─▶ ARRIVED ─▶ ASKING ─응답─▶ READY
```

## 한계

- 에뮬레이터는 시트가 실제로 움직이지 않습니다(시트 메모리 호출은 차량 로그로만 확인).
- Virtual Console의 전원 On/Off는 시동 신호를 바꾸지 않으므로, 시동 시 동작은 앱을 다시 실행해 확인합니다.
- 운행기록부 양식 항목은 제출 전에 국세청 고시 원문과 대조가 필요합니다.
