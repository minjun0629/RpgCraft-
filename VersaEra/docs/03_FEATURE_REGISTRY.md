# 03. Feature Registry 요약

> 정본은 `feature_registry.yml` (모든 필드: 출처 · 플레이어 경험 · 의존 · 구현 · 데이터 · UI · 리소스팩 · 성능 위험 · 보안 위험 · 저장 · 테스트 · 상태).
> `RegistryAuditTest` 가 상태와 실제 테스트 파일을 대조합니다. **VERIFIED 는 아직 0개** — 이 개발 환경에서는 Paper 서버를 내려받을 수 없어 실제 서버 테스트를 못 했습니다.

| ID | 기능 | 분류 | 상태 | 자동 테스트 |
|---|---|---|---|---|
| CORE-01 | DB · 트랜잭션 · 마이그레이션 | ORIGINAL | **IMPLEMENTED** | MigratorTest |
| CORE-02 | DB 전용 스레드 · 비동기 흐름 | ORIGINAL | **IMPLEMENTED** | TradeServiceTest |
| CORE-03 | 감사 로그 | ORIGINAL | **IMPLEMENTED** | EconomyServiceTest, TradeServiceTest |
| CONT-01 | 데이터 중심 콘텐츠 로더 | ORIGINAL | **IMPLEMENTED** | ContentIntegrityTest |
| ITM-01 | 고유 아이템 인스턴스 | SOURCE-BASED | **IMPLEMENTED** | ItemServiceTest |
| ITM-02 | 배달함 (안전한 지급) | ORIGINAL | **IMPLEMENTED** | ItemServiceTest |
| ITM-03 | 인벤토리 검증 · 격리 (복제 방지) | ORIGINAL | **PARTIAL** | ItemServiceTest |
| ITM-04 | 내구도 · 수리 | SOURCE-BASED | **IMPLEMENTED** | ItemServiceTest |
| ECO-01 | 돈 · 원장 | ORIGINAL | **IMPLEMENTED** | EconomyServiceTest |
| TRD-01 | 1:1 거래 | ORIGINAL | **IMPLEMENTED** | TradeServiceTest |
| TRD-02 | 묶음 재료 거래 · 경매장 · 개인 상점 | RESEARCH_REQUIRED | **PLANNED** | — |
| SKL-01 | 숙련 (초급 · 중급 · 고급 · 마스터) | SOURCE-BASED | **IMPLEMENTED** | CraftingServiceTest, ContentIntegrityTest, GrowthAndWorldTest |
| SKL-02 | 행동 스탯 (조건 해금) | SOURCE-BASED | **IMPLEMENTED** | GrowthAndWorldTest, ContentIntegrityTest |
| SKL-03 | 행동 스탯의 실제 효과 (체력 · 가격 등) | ORIGINAL | **PARTIAL** | CraftingServiceTest |
| CLS-01 | 직업 체계 | RESEARCH_REQUIRED | **RESEARCH_REQUIRED** | — |
| CRF-01 | 제작 공통 엔진 (대장 · 재봉 · 가죽 · 요리 · 연금 · 조각) | SOURCE-BASED | **IMPLEMENTED** | CraftingServiceTest, ContentIntegrityTest |
| CRF-02 | 조각 (여러 재료 조합 · 실제 아이템) | SOURCE-BASED | **IMPLEMENTED** | CraftingServiceTest |
| GAT-01 | 채집 · 채광 · 벌목 · 낚시 | SOURCE-BASED | **PARTIAL** | ContentIntegrityTest |
| WLD-01 | 지역 · 위험도 · 공간 인덱스 | ORIGINAL | **IMPLEMENTED** | GrowthAndWorldTest |
| WLD-02 | 실제 지형 · 건축 (월드맵 제작) | ORIGINAL | **EXTERNAL_ASSET_REQUIRED** | — |
| EXP-01 | 발견 기록 · 최초 발견자 | SOURCE-BASED | **IMPLEMENTED** | GrowthAndWorldTest |
| MAP-01 | MMORPG 지도 | ORIGINAL | **PARTIAL** | — |
| NPC-01 | NPC 정의 · 관계 | ORIGINAL | **IMPLEMENTED** | GrowthAndWorldTest |
| NPC-02 | NPC 일과 이동 · 상점 · 의뢰 | ORIGINAL | **PLANNED** | — |
| QST-01 | 퀘스트 (탐험 · 생산 · 관계 · 연속 · 선택 · 숨김) | RESEARCH_REQUIRED | **RESEARCH_REQUIRED** | — |
| HID-01 | 히든 콘텐츠 엔진 (봉인 · 행동 조합 · 최초 발견 · 소문) | ORIGINAL | **IMPLEMENTED** | HiddenServiceTest |
| HID-02 | 실제 히든 콘텐츠 목록 | ORIGINAL | **PLANNED** | — |
| CMB-01 | 전투 기본 (무기 · 방어구 · 숙련 · 약점 · 마모) | ORIGINAL | **PARTIAL** | BossAndCombatTest |
| CMB-02 | 스킬 · 콤보 · 회피 · 상태 이상 · 직업 특성 | RESEARCH_REQUIRED | **PLANNED** | — |
| BOS-01 | 거대 보스 규칙 (페이즈 · 패턴 · 예고 · 광폭화 · 약점) | ORIGINAL | **IMPLEMENTED** | BossAndCombatTest |
| BOS-02 | 거대 보스 실행 (모델 · 판정 상자 · 예고 연출 · 보상) | ORIGINAL | **PARTIAL** | — |
| GLD-01 | 길드 · 사회 | RESEARCH_REQUIRED | **RESEARCH_REQUIRED** | — |
| DUN-01 | 던전 (구조 · 퍼즐 · 숨은 공간) | ORIGINAL | **PLANNED** | — |
| EVT-01 | 월드 이벤트 (모래폭풍 · 매몰 도시 입구 등) | ORIGINAL | **PLANNED** | — |
| DTH-01 | 사망 페널티 | SOURCE-BASED | **DESIGNED** | — |
| UI-01 | 전용 MMORPG UI (상자 창 탈피) | ORIGINAL | **PARTIAL** | — |
| RP-01 | 리소스팩 (모델 · 텍스처 · 폰트 · 보스 모델) | ORIGINAL | **EXTERNAL_ASSET_REQUIRED** | — |
| SRV-01 | 실제 Paper 서버 테스트 | ORIGINAL | **BLOCKED** | — |

상태 합계: BLOCKED 1 · DESIGNED 1 · EXTERNAL_ASSET_REQUIRED 2 · IMPLEMENTED 18 · PARTIAL 7 · PLANNED 6 · RESEARCH_REQUIRED 3
