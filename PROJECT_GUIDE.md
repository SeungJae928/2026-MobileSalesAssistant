# 영업 이동 비서 — 프로젝트 구조 가이드

Oct 28, 2026 · @방승재

## 개요

영업 이동 비서는 차가 주행을 자동 기록하고, 도착 순간 음성 한마디로 방문 내용을 받아 운행기록부와 영업 방문일지를 동시에 만드는 Pleos Connect 앱이다. 운전자는 화면을 거의 만지지 않는다.

- 핵심 사용자: 업무용 차량을 매일 쓰는 영업직, 차량 관련비용이 연 1,500만원을 넘는 개인사업자·법인 대표
- 공모전 MVP: 차량 프로필 기반 내 정보(시트 메모리 자동 불러오기) → 주행 자동 기록 → 정차 시 음성 질문 → 기록 목록 → 운행기록부 CSV 내보내기
- 기획서에만 넣는 확장: 거래처 CRM 구조화, 출장비 정산, 관리자 대시보드, 모바일 QR 본인 인증

개발 환경은 Android Studio + Kotlin, Pleos Connect Emulator(AVD), Android API 28 이상, Gradle 8.0 이상 기준이다.

## 전체 아키텍처

앱의 중심은 트립 상태머신 하나다. SDK 신호는 모두 vehicle 래퍼를 거쳐 들어오고, 상태머신이 도착을 판단하면 voice가 질문하고, 결과는 Room DB 한 곳에 쌓인다.

&#91;embedded content: 레이어 구조 · SDK → 래퍼 → 상태머신 → 저장 → 화면·내보내기\]

화면과 내보내기는 DB만 읽는다. 그래서 SDK가 바뀌어도 화면 코드는 그대로다.

## 모듈·패키지 구조

공모전 기간에는 단일 app 모듈 안에서 패키지로 레이어를 나누고, Pleos SDK 호출은 `vehicle` 패키지 한 곳에만 둔다. SDK 버전이 바뀌거나 에뮬레이터에서 안 되는 API가 있어도 이 패키지만 고치면 된다.

```
app/src/main/java/com/salesdrive/
├── SalesDriveApp.kt          // Hilt Application, SDK 초기화
├── vehicle/                  // Pleos SDK 래퍼 (유일한 SDK 의존 지점)
│   ├── VehicleSignalSource.kt   // Odometer, Brake, 기어/속도 → Flow
│   ├── LocationSource.kt        // FusedLocation SDK
│   ├── NaviSource.kt            // NaviHelper: 현재 목적지
│   ├── DrivingRestriction.kt    // CarUxRestrictions 구독
│   ├── SeatMemory.kt            // 시트 메모리 번호 조회·불러오기
│   └── fake/FakeVehicleSource.kt // 에뮬레이터·테스트용 시나리오 재생
├── voice/
│   ├── VoicePrompter.kt         // TTS 질문
│   ├── SpeechCapture.kt         // STT 결과 수신
│   └── VisitParser.kt           // LLM으로 발화 → 구조화
├── profile/
│   └── SeatRestorer.kt          // 시동 시 시트 메모리 복원 판단
├── trip/
│   ├── TripStateMachine.kt      // 핵심 로직 (아래 상태머신)
│   ├── TripRecorder.kt          // 상태 전이 시 DB 기록
│   └── TripService.kt           // 포그라운드 서비스, 앱이 꺼져도 기록
├── data/
│   ├── db/ (Room: Trip, Visit, DriverProfile, Vehicle 엔티티 + DAO)
│   ├── repo/TripRepository.kt
│   └── RetentionWorker.kt       // 오래된 상세 경로 정리
├── export/
│   └── LogbookExporter.kt       // 국세청 운행기록부 양식 CSV/XLSX
└── ui/ (Jetpack Compose)
    ├── home/       // 오늘 요약, 업무 비율
    ├── purpose/    // 출발 시 업무/출퇴근/개인 선택
    ├── review/     // 정차 시 방문 기록 확인·수정
    ├── logbook/    // 월별 기록 목록 + 내보내기
    └── settings/   // 운전자·차량 등록, 회사 정보
```

| 패키지 | 책임 | 의존 |
| --- | --- | --- |
| vehicle | SDK 신호를 앱 공통 Flow로 변환 | Pleos Connect SDK |
| trip | 주행 시작·종료 판단, 기록 저장 | vehicle, data |
| voice | 질문·인식·구조화 | Gleo AI SDK (TTS, STT, LLM) |
| data | 로컬 저장 | Room |
| export | 기록부 파일 생성 | data |
| ui | 화면, 주행 제한 반영 | trip, data, vehicle.DrivingRestriction |

`fake/FakeVehicleSource`는 시연 영상용으로 중요하다. "출발 → 30km 주행 → 거래처 도착 → 주차" 시나리오를 버튼 하나로 재생하면 에뮬레이터 조작 없이도 매번 같은 데모를 찍을 수 있다.

## 트립 상태머신

신호등 정차를 도착으로 착각하지 않도록, 주행 종료는 PAUSED에서 주차 상태가 유지될 때만 확정한다. 30초·60초는 시작값이고 설정으로 빼둔다.

&#91;embedded content: 트립 상태머신 · 6개 상태\]

| 전이 | 감지 신호 | 기록하는 것 |
| --- | --- | --- |
| IDLE → READY | 시동 상태 (AAOS 차량 속성, 접근 권한 확인 필요) | 내 정보의 시트 메모리 번호 불러오기(P·정차 중에만) |
| READY → DRIVING | 속도 또는 기어 D·R | Odometer 시작값, 출발 위치, 목적, 시트·공조 스냅샷 |
| DRIVING ↔ PAUSED | 속도 0 / 재출발 | 없음 |
| PAUSED → ARRIVED | Brake API 주차브레이크, 기어 P | Odometer 종료값, 도착 위치, NaviHelper 목적지명 |
| ARRIVED → ASKING | 자동 | TTS 질문 시작 |
| ASKING → READY | STT 응답 또는 무응답 2회 | Visit 저장, purpose 갱신 |

상태는 전이할 때마다 DB에 저장해, 앱이 죽었다 살아나도 진행 중인 Trip을 이어서 기록한다. 시동·기어처럼 Pleos Vehicle API 목록에 없는 신호는 AAOS 표준 차량 속성으로 읽어야 하므로, 에뮬레이터에서 먼저 확인한다.

## 운전자 프로필과 시트 프리셋

운전자 구분은 앱 자체 로그인 대신 **차량(AAOS)의 사용자 프로필**을 그대로 쓴다. 차량 화면 오른쪽 위 프로필로 운전자를 바꾸면, 안드로이드가 앱 데이터(Room DB)와 내보낸 CSV(다운로드 폴더)를 프로필마다 따로 보관한다. 그래서 앱 안에는 "운전자 선택"이 없고, 각 프로필의 DB에는 내 정보(DriverProfile) 한 줄만 있다.

- 내 정보: 성명·부서(운행기록부에 그대로 들어감), 연결할 시트 메모리 번호.
- 성명·부서가 비어 있으면 홈에서 입력을 안내한다. 운전 중 키보드 입력이나 비밀번호 인증은 없다.
- 프로필을 지우면 그 사람의 기록도 함께 지워지므로, 내 정보 화면에서 CSV 내보내기를 안내한다.
- 법인차 운행기록부는 원래 차량 1대당 1부지만, 이 구조에서는 사람별로 나온다. 행마다 성명이 있어 회사에서 차량별로 합치기 쉽다.

**시트: 차량 메모리 번호 연결**

에뮬레이터 확인 결과(2026-11-02), Pleos Seat API에는 "위치를 N으로" 같은 절대 위치 지정이 없고 `moveSeatXxx(FORWARD/BACKWARD/STOP)`(누르고 있는 버튼 방식)와 `selectSeatMemory(좌석, 번호)`만 있다. 메모리에 저장하는 API도 없다. 그래서 위치값을 저장했다가 되돌리는 대신, 차량이 이미 저장해 둔 운전자 메모리 번호(1~2)를 프로필에 연결해 불러온다.

- 메모리 번호에 자세를 저장하는 일은 운전자가 차량 기본 기능(차량 설정 > 시트 > 운전자 프리셋)으로 한다.
- 지원 번호 범위는 `checkSeatMemoryCapabilities`로 조회한다. 지원하지 않거나 권한이 없으면 시트는 건너뛴다.
- 에뮬레이터는 시트 위치를 흉내 내지 않아(위치값 항상 0) 실제로 움직이지 않는다. 시연에서는 호출 성공 안내까지만 보인다.
- 위치값 저장·복원(이동 + 위치 감시 후 정지)은 실차에서 검증할 수 있을 때의 확장으로 둔다. 공조·열선 프리셋도 확장이다.

**복원 조건**

- 시동이 켜져 IDLE → READY가 되는 순간, 기어 P·속도 0일 때 한 번만 불러온다. 주행 중에는 어떤 경우에도 시트를 움직이지 않는다.
- 불러오기 전에 TTS로 "운전자 프리셋 N번으로 시트를 맞춥니다"라고 알린다. 메모리 불러오기는 차량이 끝까지 움직이고 멈추는 기능이라, 중단은 차량 기본 동작(시트 스위치 조작)에 맡긴다.

**코드 위치**

- `vehicle/SeatMemory.kt`: 지원 번호 조회, 메모리 불러오기 (Pleos Seat API 래퍼)
- `profile/SeatRestorer.kt`: 시동 시 복원 판단
- `data/repo/ProfileRepository.kt`, `ui/profile/`: 내 정보 저장과 화면

```kotlin
@Entity data class DriverProfile(
  @PrimaryKey val id: Long,
  val name: String, val department: String?,
  val seatMemorySlot: Int?,     // 연결한 차량 시트 메모리 번호
  val seatPreset: String?,      // (확장) 위치값 스냅샷
  val comfortPreset: String?,   // (확장) 공조 온도, 핸들·미러 열선
  val presetCapturedAt: Instant?,
  val locationConsentAt: Instant? // 위치정보 수집 동의 시각
)
```

**기존 메모리 시트와의 차이 (기획서용)**: 차량 프로필 하나로 운전자 구분, 운행기록 분리, 시트 자세가 한 번에 연결된다. 운전자는 프로필만 고르면 되고, 자기 기록만 보게 된다.

## 데이터 모델

엔티티는 Trip(주행 1회)과 Visit(방문 1건) 두 개가 핵심이고, Trip 한 행이 운행기록부 한 줄이 된다.

```kotlin
@Entity data class Trip(
  @PrimaryKey val id: Long,
  val vehicleId: Long, val driverId: Long,
  val startedAt: Instant, val endedAt: Instant?,
  val odoStartKm: Double, val odoEndKm: Double?,   // 계기판 값 그대로
  val startLat: Double, val startLng: Double,
  val endLat: Double?, val endLng: Double?,
  val purpose: Purpose,          // BUSINESS, COMMUTE, PERSONAL
  val purposeSource: Source,     // USER_TAP, VOICE, DEFAULT
  val naviDestination: String?,  // NaviHelper에서 받은 목적지명
  val note: String?              // 기록부 비고란
)

@Entity data class Visit(
  @PrimaryKey val id: Long, val tripId: Long,
  val rawUtterance: String,      // STT 원문은 항상 보존
  val client: String?, val contact: String?,
  val summary: String?, val followUp: String?, val followUpDate: LocalDate?
)
```

| 운행기록부 항목 | 출처 |
| --- | --- |
| 사용일자 | Trip.startedAt |
| 사용자(부서, 성명) | DriverProfile (READY에서 선택) |
| 주행 전 계기판 거리 | Trip.odoStartKm |
| 주행 후 계기판 거리 | Trip.odoEndKm |
| 주행거리 | odoEndKm − odoStartKm |
| 출퇴근용 사용거리 | purpose = COMMUTE 인 주행거리 |
| 일반업무용 사용거리 | purpose = BUSINESS 인 주행거리 |
| 비고 | Trip.note 또는 Visit.summary |

운행기록부 양식 항목은 내보내기 전에 국세청 고시 원문과 한 번 대조할 것. 업무사용비율은 (출퇴근 + 일반업무 거리) ÷ 총주행거리로 계산해 홈 화면에 보여준다.

## 데이터 보관과 리소스

텍스트 기록만 남기면 5년을 써도 10MB 안팎이라 저장 공간은 문제가 되지 않는다. 용량을 키우는 것은 음성 원본과 촘촘한 GPS 경로뿐이므로 둘 다 남기지 않거나 기간을 정해 줄인다.

| 저장 항목 | 1건 크기 (추정) | 1년 누적 (추정) | 정책 |
| --- | --- | --- | --- |
| Trip + Visit 텍스트 | 주행·방문 1쌍 약 1KB | 약 2MB | 요약 기록 5년 보관 |
| GPS 경로, 5초 간격 | 하루 약 2,000점 × 50B | 약 25MB | 3개월 후 출발·도착·거래처 지점만 남김 |
| GPS 경로, 1초 간격 | 하루 약 1만 점 × 50B | 약 130MB | 사용하지 않음 |
| 음성 원본 (10초) | 약 320KB | 약 640MB | 저장하지 않음, STT 텍스트만 |

추정 전제: 하루 주행 8회, 방문 8건, 운전 3시간, 연 250일.

- 보관 기간: 세무 장부는 일반적으로 5년 보관이 필요한 것으로 알려져 있다. 정확한 기간은 세무 확인 후 확정한다.
- 정리 작업: WorkManager로 하루 한 번 DB 크기와 기기 여유 공간을 확인하고, 기간이 지난 상세 경로부터 정리한다.
- 백업: 연말 연간 기록부 파일 내보내기를 기본 백업으로 쓰고, 확장 버전에서 Sync/서버 동기화로 원본을 차 밖에 둔다.

**정보 전달 시 조회 구조**: "지난번 A사 미팅 알려줘"에 기록 전체를 LLM에 넘기면 입력 길이와 응답 속도가 먼저 한계에 걸린다. 질문에서 거래처·날짜를 뽑고 → Room에서 해당 거래처 최근 방문 몇 건만 조회하고 → 그것만 LLM에 넘겨 요약한다. `Visit.client`, `Visit.followUpDate`, `Trip.startedAt`에 인덱스를 건다.

## 화면과 주행 제한 대응

Business 카테고리는 주행 중 예외 허용 대상이 아니므로, 모든 화면은 정차 중에만 조작 가능하고 주행이 시작되면 "기록 중" 최소 화면으로 바뀐다. 입력은 주행 중 음성, 정차 중 터치로 나눈다.

| 화면 | 언제 뜨나 | 주행 중 동작 | 핵심 요소 |
| --- | --- | --- | --- |
| 출발 목적 선택 | 시동 후, 아직 정차 상태 | 주행 시작 시 자동으로 닫힘, 기본값 저장 | 큰 버튼 3개: 업무 / 출퇴근 / 개인 |
| 기록 중 | 주행 중 | 유일하게 표시되는 화면 | 아이콘 + "기록 중" 한 줄, 움직이는 요소 없음 |
| 방문 기록 확인 | 도착 후 정차 + 음성 응답 완료 | 표시 안 함 | 인식된 거래처·요약·후속 일정, 수정 버튼 |
| 홈 | 정차 중 앱 실행 | 기록 중 화면으로 대체 | 오늘 주행 km, 이번 달 업무 비율 |
| 운행기록부 | 정차 중 | 숨김 | 월별 목록, CSV 내보내기 |
| 설정 | 정차 중 | 숨김 | 운전자·차량·회사 정보 |

구현 포인트:

- `DrivingRestriction`이 `CarUxRestrictionsManager`를 구독해 `isRequiresDistractionOptimization` 값을 StateFlow로 내보낸다.
- 최상위 Compose `NavHost`에서 이 값을 보고, true면 어떤 화면이든 "기록 중" 화면으로 덮는다. 화면마다 따로 처리하지 않는다.
- 주행 중 띄우는 화면이 있는 Activity만 매니페스트에 `distractionOptimized` 메타데이터를 선언한다.
- 텍스트 입력은 설정 화면에만 두고, 나머지는 프리셋·최근 항목·음성으로 대신한다.

## 음성·LLM 파이프라인

도착 시 질문 한 번, 답변 한 번으로 끝나야 한다. 실패해도 원문이 남고, 원문이 없어도 주행 기록은 남는다.

1. 정차 확정(아래 상태머신의 ARRIVED) → `VoicePrompter`가 TTS로 질문. 내비 목적지가 있으면 "OO빌딩 도착하셨네요. 방문 내용 말씀해 주세요.", 없으면 "방문 내용 말씀해 주세요."
2. `SpeechCapture`가 STT 결과를 받는다. 8초 무응답이면 한 번만 다시 묻고, 그래도 없으면 "나중에 입력"으로 표시하고 종료.
3. 원문을 `Visit.rawUtterance`에 즉시 저장한다.
4. `VisitParser`가 LLM에 원문 + 목적지명을 보내 JSON으로 받는다: `client`, `contact`, `summary`, `followUp`, `followUpDate`, `purpose`.
5. 파싱 실패나 LLM 미응답이면 원문만 비고란에 넣는다. 정차 중 확인 화면에서 사람이 고칠 수 있다.
6. "출퇴근"·"개인" 같은 답이면 Trip.purpose를 갱신한다. 출발 때 버튼을 안 눌러도 여기서 바로잡힌다.

프롬프트 예시 (VisitParser):

```
너는 영업 방문 기록을 정리한다. 아래 발화를 JSON으로만 답하라.
키: client, contact, summary(20자 이내), followUp, followUpDate(YYYY-MM-DD 또는 null),
purpose(BUSINESS|COMMUTE|PERSONAL). 모르면 null.
오늘 날짜: {today} / 도착지: {naviDestination}
발화: "{utterance}"
```

예: "A상사 김부장 납품 미팅, 견적 다음 주 화요일까지" → client=A상사, contact=김부장, summary=납품 미팅, followUp=견적 회신, followUpDate=다음 주 화요일 날짜, purpose=BUSINESS.

Gleo LLM SDK를 에뮬레이터에서 쓸 수 있는지, 호출 권한 신청이 필요한지는 Pleos Playground에서 먼저 확인한다. 안 되면 규칙 기반 파서(거래처 사전 + 날짜 정규식)로 MVP를 만들고 LLM은 기획서 확장으로 돌린다.

## 개발 일정과 역할

기능 개발은 11/10에 닫고, 마지막 닷새는 시연 영상과 기획서에만 쓴다. 신호 확인이 어긋나면 모든 일정이 밀리므로 첫 사흘이 가장 중요하다.

&#91;embedded content: 개발 일정 · 10/28–11/15\]

역할은 2인 팀 기준이다. 1인이면 음성·LLM을 규칙 기반 파서로 대체해 범위를 줄이고, 3인 이상이면 기획서 담당을 따로 둔다.

| 담당 | 맡는 패키지 | 주요 산출물 |
| --- | --- | --- |
| A (차량·로직) | vehicle, trip, data, export | 상태머신, Fake 시나리오, CSV 내보내기 |
| B (음성·화면) | voice, ui | TTS·STT·LLM 연동, Compose 화면, 주행 제한 처리 |
| 공동 | — | 기획서(타깃·MAU·리텐션), 시연 영상, GitHub README |

## 확장: 모바일 QR 본인 인증 (후순위)

공모전 MVP에서는 차량 프로필로 운전자를 구분하고 별도 인증은 하지 않는다. 회사 계정 연동·기록 동기화가 필요해지면, 본인 인증은 기획서의 확장 로드맵으로 제시한다. 차 화면에서는 키보드 입력 없이, 운전자가 바뀔 때만 자기 폰으로 QR을 스캔해 인증한다. Pleos App Market 로그인과 같은 방식이다.

&#91;embedded content: QR 본인 인증 시퀀스 · 차량 앱, 서버, 모바일 웹\]

- 스캔 시점: 같은 사람이 계속 타면 마지막 운전자를 유지한다. 차량 프로필이 바뀌었거나 사용자가 요청할 때만 QR을 띄운다.
- 보안: QR에는 1회용 세션 ID만 넣고 계정 정보는 넣지 않는다. 사용된 세션과 만료된 세션은 재사용을 거부한다.
- 오프라인: 지하주차장처럼 통신이 없으면 출발을 막지 않는다. "미인증" 상태로 기록을 시작하고, 통신이 되거나 도착 후 확인 화면에서 인증을 붙인다.
- 단계적 구현: 먼저 모바일 웹 + BaaS(예: Firebase) 세션 테이블 하나로 시작하고, 이후 네이티브 앱·회사 관리자 웹·기록 동기화(Sync SDK 검토)로 넓힌다.
- 대안 검토: 차량에 블루투스로 연결된 폰으로 운전자를 추정하면 스캔도 필요 없다. 서드파티 앱의 연결 기기 정보 접근 권한을 먼저 확인한다.
- 위치정보 동의: 법인이 직원 주행 위치를 모으므로, 최초 등록 때 폰에서 개인위치정보 수집 동의를 받고 `DriverProfile.locationConsentAt`에 남긴다. 법적 요건은 별도 확인이 필요하다.

## 리스크와 체크리스트

가장 큰 리스크는 "에뮬레이터에서 특정 신호가 안 나온다"는 것이다. 첫 이틀 안에 신호 확인을 끝내고, 안 되는 신호는 Fake 소스로 대체한다.

| 리스크 | 대응 |
| --- | --- |
| 시동/기어 상태를 앱이 못 읽음 (권한 제한) | 속도 > 0 지속 = 주행 시작, 주차브레이크 ON = 정차로 대체 판단 |
| Odometer 값이 에뮬레이터에서 고정 | Fake 소스로 시나리오 재생, 실차 문의는 설명회 Q&A로 |
| LLM SDK 사용 불가 또는 권한 대기 | 규칙 기반 파서로 MVP, LLM은 기획서 확장 |
| 앱이 백그라운드에서 종료됨 | 포그라운드 서비스 + 마지막 상태를 DB에 저장해 재시작 시 복구 |
| 신호등 정차를 도착으로 오인 | 주차브레이크 또는 P단 + 일정 시간 유지 조건 |
| "폰 앱으로도 되잖아" 질문 | 계기판 실측 거리, 폰 없는 공용차, 정차 순간 질문을 3대 차별점으로 기획서·PT에 고정 |

- [ ] Pleos Playground 가입, 프로젝트 생성, SDK 접근 권한 신청
- [ ] 에뮬레이터에서 Odometer, Brake(주차브레이크), 기어·속도, 위치, NaviHelper 목적지 값 확인, Seat API로 시트가 실제로 움직이는지 확인
- [ ] TTS·STT·LLM 호출이 에뮬레이터에서 되는지 확인
- [ ] 오늘(10/28) 설명회에서 Business 카테고리 주행 중 허용 범위, 시동 상태 접근 권한 질문
- [ ] 운행기록부 양식 항목을 국세청 원문과 대조
- [ ] GitHub 저장소 공개 설정, README에 빌드·실행 방법
- [ ] 시연 영상: Fake 시나리오로 한 사이클 녹화 (2분 이내)
