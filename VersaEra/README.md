# VersaEra — 베르사 새벽기

『달빛조각사』의 **주요 이야기가 끝나고 수백 년이 흐른 뒤**의 베르사 대륙을 무대로 하는 독립형 Minecraft MMORPG 서버 플러그인입니다.

- **비공식 팬 프로젝트**입니다. 원작의 사건 · 인물은 역사 · 전설로만 남고, 플레이어는 새 시대(개척력 37년)를 삽니다.
- **RpgCraft 와 완전히 분리**되어 있습니다. 코드 · 패키지 · DB · 아이템 id · 명령어 · 리소스팩을 하나도 공유하지 않습니다.
- 원작의 히든 직업 · 스킬 · 조건은 쓰지 않습니다. 이 게임의 히든 콘텐츠는 서버마다 봉인된 새 조건으로만 열립니다.

| 문서 | 내용 |
|---|---|
| [docs/01_RESEARCH.md](docs/01_RESEARCH.md) | 원작 조사 · 교차 검증 · 분류(CANON / SOURCE-BASED / ORIGINAL / RESEARCH_REQUIRED) |
| [docs/02_WORLD.md](docs/02_WORLD.md) | 후대 시대 세계 · 연표 · 지역 · 세력 · 교통 · 자원 · 보스 지역 |
| [docs/03_FEATURE_REGISTRY.md](docs/03_FEATURE_REGISTRY.md) | 기능 목록 요약 · 상태 (정본: `docs/feature_registry.yml`) |
| [docs/04_ARCHITECTURE.md](docs/04_ARCHITECTURE.md) | 계층 · 스레드 규칙 · 아이템 보관 모델 · 거래 · 히든 엔진 · DB 스키마 · 마이그레이션 |

## 환경 · 의존성

- Paper 1.20.1 (Spigot 1.20.1 도 동작하도록 Bukkit API 만 사용) · Java 21
- 실행 의존성은 없습니다. SQLite 드라이버와 SnakeYAML 은 서버에 들어 있는 것을 씁니다.
- 빌드: Gradle 8.5 이상
  - 컴파일: Paper API (`compileOnly`)
  - 테스트: JUnit 5 · sqlite-jdbc · SnakeYAML

## 빌드

```bash
gradle build                         # Paper 저장소에서 API 를 받아 빌드 + 자동 테스트
gradle build -PapiJar=<api.jar>      # 저장소에 접속할 수 없을 때, 가지고 있는 API jar 로 빌드
```

결과: `build/libs/VersaEra-0.1.0.jar` (미리 빌드한 jar 는 `dist/` 에 있습니다)

## 실행

1. jar 를 `plugins/` 에 넣고 서버를 켭니다.
2. 처음 켜면 `plugins/VersaEra/` 에 다음 파일이 생깁니다.

| 파일 | 내용 |
|---|---|
| `versaera.db` | SQLite DB (WAL) |
| `content/*.yml` | 콘텐츠 정의 — 여기서 수정 |
| `config.yml` | 설정 |
| `secret.key` | 히든 콘텐츠 봉인 키 — **백업하고, 공유하지 마세요** |

3. 지역 좌표(`content/regions.yml`)는 12000 × 12000 월드 기준입니다. 실제 지형 · 건축은 아직 없습니다 (아래 알려진 제한).

## 설정

| 항목 | 기본값 | 설명 |
|---|---|---|
| `timezone` | `Asia/Seoul` | 하루 단위 규칙(NPC 하루 첫 대화 등)의 날짜 기준 |

콘텐츠는 모두 `content/` YAML 입니다:

- `items.yml` 아이템
- `disciplines.yml` 숙련 분야
- `action_stats.yml` 행동 스탯
- `recipes.yml` 제작법
- `resources.yml` 자원
- `regions.yml` 지역
- `npcs.yml` NPC
- `bosses.yml` 보스

새 콘텐츠는 데이터만 추가하면 됩니다. 잘못된 항목이 있으면 **어느 파일의 어느 id 인지** 알려 주고 시작을 멈춥니다.

## DB

- 스키마: `src/main/resources/db/migration/V1__init.sql`
- 마이그레이션 규칙:
  - 업데이트는 `V2__*.sql` 처럼 새 파일을 만들고 `migrations.txt` 에 한 줄 추가합니다.
  - 이미 적용한 파일을 고치면 서버가 시작을 거부합니다.
- 백업: 서버를 끈 뒤 `versaera.db*` 파일을 복사합니다 (WAL 모드).

## 플레이 방식 (명령어 최소)

| 하는 일 | 방법 |
|---|---|
| 캐릭터 (숙련 · 행동 스탯 · 돈) | `/versa` (`/베르사`) |
| 지도 (발견한 지역만) | `/versa 지도` |
| 거래 | `/거래 <이름>` → 상대가 `/거래 수락` |
| 제작 | 월드의 블록 우클릭 (아래 표) |
| 수리 | 대장장이 작업대 우클릭 (장비를 손에 들고) |
| 채집 · 채광 · 벌목 · 낚시 | 자원 블록 캐기 · 낚시 (도구 필요) |
| NPC | 우클릭 대화 (하루 첫 대화 · 선물로 관계 상승) |
| 탐험 | 지역에 들어가면 자동 기록 · 서버 최초 발견자 알림 |

제작대 (블록 우클릭):

| 블록 | 분야 |
|---|---|
| 모루 | 대장 |
| 베틀 | 재봉 |
| 제작대 | 가죽 |
| 훈연기 | 요리 |
| 양조기 | 연금 |
| 석재 절단기 | 조각 |
| 대장장이 작업대 | 수리 |

## 관리자 명령어 (`/versaadmin`, `/va`)

권한: `versaera.admin` (기본 OP)

| 명령 | 설명 |
|---|---|
| `/va inspect <이름>` | 돈 · 숙련 · 스탯 · 기록 · 보유 아이템 수 |
| `/va item` | 손에 든 고유 아이템의 DB 기록 · 내력 |
| `/va audit [행동]` | 최근 감사 로그 (예: `TRADE_COMPLETED`, `ITEM_QUARANTINED`) |
| `/va give <이름> <아이템> [품질] [수량]` | 지급 (배달함 경유 · 감사 로그) |
| `/va money <이름> <+/-금액>` | 돈 지급 · 회수 |
| `/va npc spawn <id>` | 서 있는 자리에 NPC |
| `/va boss spawn <id>` · `/va boss stop` | 거대 보스 소환 · 제거 |
| `/va seal` | `hidden-src/*.yml` 을 `hidden.sealed` 로 봉인 (재시작 후 적용, 원본 폴더는 치울 것) |
| `/va perf` | 콘텐츠 수 · 메모리 |

## 히든 콘텐츠 작성

1. `plugins/VersaEra/hidden-src/` 에 YAML 을 씁니다 (형식: `src/test/resources/hidden-example.yml`).
2. 조건은 아래를 조합합니다. **확률 조건은 없습니다.**
   - `counter` 행동 기록
   - `mastery` 숙련
   - `affinity` NPC 호감
   - `region` 지역
   - `hours` 게임 시각
   - `discovered` 발견 기록
3. `/va seal` → 재시작.
4. 서버에서 `hidden-src` 폴더를 치웁니다.

원작의 히든 조건은 쓰지 마세요. 저장소에는 실제 규칙을 올리지 않습니다 (공략 독점 방지).

## 개발 구조

```text
io.versaera
├─ domain          순수 규칙 (Bukkit 없음) — 아이템 · 거래 · 숙련 · 제작 · 채집 · 지역 · NPC · 히든 · 전투 · 보스
├─ application     서비스 · 저장소 인터페이스(port) · GameServices(조립)
├─ persistence     SQLite · 트랜잭션 · 마이그레이션 · DB 전용 스레드
├─ content         YAML 로더 (SafeConstructor)
├─ security        Sealer (AES-GCM)
└─ platform.bukkit 플러그인 · 리스너 · 명령 · 임시 메뉴 · ItemStack 바인딩 · 보스 실행부
```

**규칙**:

- DB 는 `versa-db` 스레드에서만 다룹니다.
- 월드 · 인벤토리는 메인 스레드에서만 다룹니다.
- 메인 스레드는 DB 를 기다리지 않습니다.

## 테스트

```bash
gradle test
```

46개 테스트가 메모리 DB + 실제 마이그레이션 + 실제 콘텐츠로 돕니다:

- **경제 · 아이템:** 원장, 중복 지급 방지, 배달 1회 확정
- **거래 · 악용 방지:** 거래 롤백, 위조 id, 확인 후 제안 바꾸기, 동시 확인 40회, 재시작 복구
- **제작:** 생산 전 실패만 재료 환불, 제작자 위조 속성 거부
- **성장:** 숙련 곡선, 행동 스탯 해금
- **월드 · NPC:** 지역 인덱스, 최초 발견자, NPC 하루 첫 대화
- **히든 · 보스 · 전투:** 봉인 · 변조 감지 · 해금 1회, 보스 판정 · 페이즈 · 광폭화 · 약점, 피해 계산
- **콘텐츠 · 레지스트리:** 참조 무결성, 위험한 YAML 거부, Feature Registry 와 실제 테스트 대조

## Feature Registry

`docs/feature_registry.yml` (38개 기능, 모든 필드)

| 상태 | 수 |
|---|---|
| IMPLEMENTED | 18 |
| PARTIAL | 7 |
| PLANNED | 6 |
| RESEARCH_REQUIRED | 3 |
| EXTERNAL_ASSET_REQUIRED | 2 |
| DESIGNED | 1 |
| BLOCKED | 1 |

**VERIFIED 0** — 실제 서버 테스트를 아직 하지 못했습니다.

## 알려진 제한 (정직한 현황)

- **실제 서버 테스트를 하지 않았습니다.** 개발 환경에서 Paper · Mojang 서버 다운로드가 막혀 있었습니다.
  - 자동 테스트는 서버 없이 도는 계층만 검증합니다.
  - Bukkit 연결 코드는 컴파일 검사와 API 시그니처 대조까지만 했습니다 → 레지스트리에서 `PARTIAL`.
- 월드 지형 · 건축, 리소스팩(모델 · UI 폰트 · 보스 모델)이 없습니다 → `EXTERNAL_ASSET_REQUIRED`.
  - 지금 UI 는 짧은 문구 규칙만 지킨 임시 상자 창입니다.
  - 보스는 임시 블록 모델입니다.
- 아직 없는 기능 → `PLANNED` / `RESEARCH_REQUIRED`:
  - 직업 체계
  - 스킬 · 콤보 · 회피
  - 퀘스트
  - 길드
  - 던전
  - 월드 이벤트
  - 경매장
  - NPC 일과 이동
  - 사망 페널티
- 원작 자료 본문(나무위키 등)을 개발 환경에서 직접 열 수 없어, 검색 요약으로만 교차 검증했습니다. 불확실한 것은 `RESEARCH_REQUIRED` 로 표시했습니다.
- SQLite 는 쓰기가 한 줄로만 처리됩니다. 대규모 서버는 PostgreSQL 이전이 필요합니다 (저장소 계층은 분리되어 있음).

## 실제 서버 테스트 계획 (SRV-01)

1. **접속 · 저장:** 접속 → 퇴장 → 재접속 → 서버 재시작 → 기록 유지 확인
2. **배달 · 장비:**
   - `/va give` → 배달 → 인벤토리 확인
   - 가방이 가득 찼을 때의 재시도
   - 수리 (초급 · 중급)
3. **거래:**
   - 정상 거래
   - 취소 · 창 닫기 · 접속 끊기
   - 거래 중 `/stop` → 재시작 후 원래 주인 배달함 확인
   - 확인 버튼 연타
   - 같은 아이템을 두 창에서 올리기
4. **복제 방지:** 위조 PDC 아이템 · 같은 id 두 개 → 회수 + `/va audit ITEM_QUARANTINED`
5. **제작 · 채집:**
   - 각 제작대 · 재료 부족 · 도구 없음 · 실패 시 재료 환불
   - 자원 블록 캐기 → 재생 → `/stop` 시 복구
6. **탐험 · NPC:** 지역 진입 · 최초 발견 알림, NPC 대화 하루 1회 상승
7. **보스:**
   - 패턴 예고 · 판정
   - 페이즈 · 광폭화
   - TPS 측정 (`/va perf`, `/mspt`)
8. **히든:** 봉인 → 재시작 → 조건 충족 시 1회 해금 · 키가 바뀐 파일 거부
