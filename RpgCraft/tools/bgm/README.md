# 상황별 배경음악 (BGM) 넣는 법

1. 곡을 **.ogg (Ogg Vorbis)** 로 바꿔서 아래 폴더에 넣는다. (파일 이름은 아무거나, 영어 권장)
   - mp3 → ogg 변환: Audacity (파일 → 내보내기 → OGG) 또는
     `ffmpeg -i 곡.mp3 -c:a libvorbis -q:a 3 곡.ogg` (3분짜리 약 2~3MB)
2. `python3 tools/build_resourcepack.py` 로 팩을 다시 만들고, 플러그인 jar 를 다시 빌드한다.
3. 서버가 팩에 적힌 곡 길이를 읽어서 곡이 끝나면 같은 상황의 다른 곡을 튼다.
   (필드 곡이 하나라도 있으면 게임 기본 필드 음악은 자동으로 꺼짐)

| 폴더 | 상황 | 추천 곡 |
|---|---|---|
| `town/` | 마을 · 로비 (스폰 150칸 안) | Kevin MacLeod - Carefree, Fiddles McGinty · DOVA-SYNDROME - 冒険の始まり |
| `field/` | 필드 · 탐험 | Kevin MacLeod - Master of the Feast · Alexander Nakarada - Adventure · Bryan Teoh - A Journey Awaits |
| `battle/` | 일반 전투 (몬스터 2마리 이상과 싸울 때 · 웨이브 · PvP) | Kevin MacLeod - Volatile Reaction · Alexander Nakarada - Chase · DOVA-SYNDROME - Cross Counter |
| `boss/` | 보스전 (보스 40칸 안) | Kevin MacLeod - Clash Defiant, Decisions · Alexander Nakarada - Boss Fight · Ross Bugden - Raid |
| `dungeon/` | 던전 · 신전 | Kevin MacLeod - Darkest Child, The Pyre · Purple Planet - Labyrinth · Alexander Nakarada - Cryptic |

## 라이선스 주의 (꼭 확인)
- **Kevin MacLeod (incompetech)**, **Alexander Nakarada** : CC BY 4.0 — 서버 공지/웹사이트 등에 출처 표기 필요
  예) `Music: "Carefree" Kevin MacLeod (incompetech.com), Licensed under CC BY 4.0`
- **Bryan Teoh (FreePD)** : 퍼블릭 도메인 (표기 불필요)
- **Ross Bugden**, **Purple Planet** : 무료 사용 가능하지만 출처 표기 필요 — 각 사이트의 약관 확인
- **DOVA-SYNDROME** : 게임 사용은 허용되지만 곡마다 작곡가 조건이 다르고 음원 파일 자체의 재배포는 금지 —
  리소스팩은 유저에게 파일이 그대로 배포되므로, 사용 전 각 곡 페이지의 약관을 확인할 것
