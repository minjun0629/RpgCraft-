# 04. 아키텍처 · 데이터 모델 · DB

> STEP 6 ~ 8 결과입니다. RpgCraft 와 코드 · 패키지 · 데이터 · 명령어를 **하나도 공유하지 않습니다.**
> 루트 패키지: `io.versaera` · 플러그인 이름: `VersaEra` · 관리자 명령어: `/versaadmin` (`/va`)

## 1. 계층

```text
platform.bukkit  (Listener · Command · UI · ItemStack 바인딩 · 스케줄러)   ← Bukkit API 는 여기에만
      │ 호출
application      (서비스: ItemService · EconomyService · TradeService · CraftingService …)
      │                  ↑ DomainEvent 발행 (EventBus)
domain           (순수 Java 규칙: 품질 계산 · 거래 상태 기계 · 숙련 곡선 · 보스 페이즈 …)
      │
repository 인터페이스 (application.port)
      │ 구현
persistence      (JDBC · SQLite · 마이그레이션 · 트랜잭션)
content          (YAML → 도메인 정의: 아이템 종류 · 레시피 · 지역 · NPC · 보스)
```

- `domain` · `application` · `persistence` · `content` 는 **Bukkit 을 import 하지 않는다** → JUnit 으로 서버 없이 테스트.
- 새 콘텐츠(아이템 · 레시피 · 지역 · NPC · 몬스터 · 보스)는 YAML 만 추가하면 된다.

## 2. 패키지

```text
io.versaera
├─ domain
│  ├─ common      Ids · Money · Clock · DomainException
│  ├─ item        ItemType · ItemInstance · Quality · Durability · ItemHistory
│  ├─ economy     Wallet 규칙 · LedgerEntry
│  ├─ trade       TradeSession (상태 기계) · TradeOffer
│  ├─ skill       Discipline · Mastery(초급/중급/고급/마스터) · ActionStat
│  ├─ crafting    Recipe · MaterialSlot · QualityCalculator
│  ├─ gathering   ResourceNode · GatherRule
│  ├─ world       Region · DangerTier · Discovery
│  ├─ npc         NpcDefinition · Relation
│  ├─ hidden      HiddenRule · Condition (봉인된 히든 콘텐츠)
│  ├─ combat      DamageCalculator · StatusEffect
│  ├─ boss        BossDefinition · BossFight (페이즈 · 패턴 · 예고 · 광폭화)
│  └─ event       DomainEvent · EventBus
├─ application    *Service · port(Repository 인터페이스)
├─ persistence    Database · Migrator · Jdbc*Repository
├─ content        ContentLoader (YAML)
├─ security       Sealer (AES-GCM) · AuditLog
└─ platform.bukkit VersaEraPlugin · listener · command · ui · binding
```

## 3. 스레드 규칙

| 작업 | 스레드 |
|---|---|
| DB 읽기 · 쓰기 | 전용 단일 스레드 `versa-db` (SQLite 는 쓰기 1개가 안전) |
| 월드 · 엔티티 · 인벤토리 | Bukkit 메인 스레드만 |
| 흐름 | 메인 → `db.submit(...)` → 결과를 `runTask` 로 메인에 돌려줌 |

- 메인 스레드에서 DB 를 기다리지 않는다 (접속 시 데이터 로드도 비동기 → 로드 전에는 행동 제한).
- 서버 종료 시에는 예외로 메인 스레드에서 남은 작업을 끝까지 기다린다 (데이터 손실 방지).

## 4. 아이템 — 서버가 진짜 주인

- 모든 중요한 아이템은 DB 의 `item_instance` 한 줄입니다. `ItemStack` 은 그 아이템을 **보여 주는 그림**일 뿐입니다.
- `ItemStack` 의 PDC 에는 `instance_id` 만 들어갑니다. **PDC 를 믿지 않습니다**:
  - 접속 · 인벤토리 열기 · 아이템 사용 때 DB 와 대조합니다.
  - DB 에 없는 아이디, 파괴된 아이템, 다른 사람 소유는 **격리**합니다.
  - 같은 아이디가 두 곳에 보이면 둘 다 **격리**하고, 감사 로그를 남깁니다.
- 아이템의 위치(`custody`): `PLAYER(uuid)` · `ESCROW(trade_id)` · `DELIVERY(uuid)` · `DESTROYED`.
- 실제 인벤토리에 넣는 것은 **배달함(delivery)** 을 통해서만:
  1. DB 트랜잭션 안에서 custody 를 `DELIVERY(uuid)` 로 바꿉니다.
  2. 메인 스레드가 인벤토리에 넣습니다.
  3. custody 를 `PLAYER` 로 확정합니다.
- 서버가 중간에 꺼지면 다음 접속 때 배달함에서 다시 줍니다 → 아이템이 사라지거나 두 개가 되지 않습니다.

## 5. 돈 · 거래

- 돈은 DB 에만 존재합니다 (`wallet`, `ledger`). 음수 불가 · 오버플로 검사 · `idempotency_key` 로 같은 요청 두 번 처리 금지.
- 거래 순서:
  1. 상태 기계: `OPEN → (양쪽 LOCK) → (양쪽 CONFIRM) → COMMITTED / CANCELLED`
  2. 아이템을 올리거나 바꾸면 양쪽 확인이 풀립니다.
  3. 올린 아이템은 곧바로 `ESCROW` 로 옮겨집니다 (인벤토리에서 빠짐).
  4. 확정은 **DB 트랜잭션 하나**로 처리합니다: 돈 이동 + 아이템 소유 이동 + 원장 + 감사 로그. 중간에 실패하면 전체를 되돌립니다.
  5. 취소 · 서버 종료 · 접속 끊김이면 `ESCROW` 아이템을 원래 주인의 배달함으로 돌려보냅니다.

## 6. 히든 콘텐츠 엔진

- 조건 데이터는 운영 폴더에 평문으로 두지 않습니다.
  - 제작자는 `hidden-src/*.yml` 을 쓰고, 관리자 명령으로 `hidden.sealed` 로 **AES-GCM 봉인**합니다.
  - 키는 서버마다 처음 실행할 때 따로 생성됩니다.
- 조건은 행동 기록(카운터) · 지역 체류 · NPC 관계 · 숙련 · 시간대를 **조합**하고, 확률은 쓰지 않습니다.
- 발견하면 서버에 **최초 발견자 기록**이 남고, 다른 사람에게는 **소문**(일부 힌트)만 퍼집니다.

## 7. 이벤트

`ItemCreatedEvent` · `ItemTransferredEvent` · `MoneyChangedEvent` · `TradeCompletedEvent` · `TradeCancelledEvent` · `PlayerCraftedEvent` · `MasteryTierReachedEvent` · `PlayerDiscoveredEvent` · `NpcRelationChangedEvent` · `HiddenUnlockedEvent` · `BossDefeatedEvent`
— 도메인 이벤트(순수 Java)이며, Bukkit 쪽이 받아서 화면 · 소리 · 공지로 바꿉니다.

## 8. DB 스키마 (V1)

`src/main/resources/db/migration/V1__init.sql` 이 정본입니다. 요약:

| 테이블 | 내용 |
|---|---|
| `schema_version` | 적용된 마이그레이션 버전 |
| `player_profile` | uuid · 이름 · 최초 접속 · 마지막 접속 · 레벨 · 경험치 · 버전(낙관적 잠금) |
| `wallet` | uuid · 잔액 (CHECK ≥ 0) |
| `ledger` | 돈 이동 원장 (from · to · 금액 · 사유 · idempotency_key UNIQUE) |
| `item_instance` | 아이템 하나하나 (id · type · quality · durability · max_durability · weight · creator · method · props(JSON) · custody_kind · custody_ref · version) |
| `item_history` | 아이템 내력 (생성 · 수리 · 거래 · 파괴 …) |
| `mastery` | uuid · discipline · xp |
| `action_counter` | uuid · key · value (행동 기록 — 성장 · 히든 판정의 바탕) |
| `discovery` | uuid · kind · ref · 시각 |
| `world_first` | kind · ref · 최초 발견자 · 시각 |
| `npc_relation` | uuid · npc · 호감 · 마지막 대화 |
| `trade` · `trade_offer` | 거래 세션 · 올린 물건 (복구용) |
| `hidden_unlock` | uuid · rule_id · 시각 |
| `audit_log` | 감사 로그 (ITEM_CREATED · MONEY_ADDED · TRADE_COMPLETED …) |

## 9. 마이그레이션

- `db/migration/V{n}__{설명}.sql` 을 순서대로, 한 버전씩 **트랜잭션 안에서** 적용합니다.
- `schema_version` 에 기록합니다. 이미 적용한 파일의 해시가 바뀌면 시작을 멈춥니다 (덮어쓰기 금지).
- PostgreSQL 이전을 고려해 표준 SQL 위주로 쓰고, SQLite 전용 문법은 `persistence.Dialect` 에 모읍니다.

## 10. 성능 예산

| 시스템 | 원칙 |
|---|---|
| 지역 판정 | 청크(16×16) 격자 인덱스 → 이동 이벤트에서 **블록이 바뀔 때만** O(1) 조회 |
| NPC | 근처에 플레이어가 있을 때만 행동 (거리 기반 활성화) |
| 보스 | 몸체 엔티티 1개 + 모델 디스플레이 소수 + 판정은 수학(원 · 부채꼴 · 선)으로 계산 |
| 저장 | 변경분만 모아 비동기 배치 저장 |
| 파티클 | 예고 범위 외곽선만, 거리 컬링 |
