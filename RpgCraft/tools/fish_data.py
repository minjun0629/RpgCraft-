"""
낚시 어종 (v5.2.0). ItemRegistry / FishingManager 의 FishSpecies.java 와 같은 순서 · 같은 번호 (CustomModelData 1600 + 순번).
(id, 이름, 재료, 판매가, 등급, 서식지, 조건, 모양, 몸 색, 배 색, 크기)
서식지: OCEAN 바다·해변 / DEEP 깊은 바다 / COLD 추운 곳 / WARM 따뜻한 곳 / RIVER 강 / SWAMP 늪 / FRESH 바다가 아닌 민물 (비우면 어디서나)
조건: NIGHT 밤에만 / RAIN 비 올 때만
모양: fish 물고기 / eel 장어 / ray 가오리·가자미 / puffer 복어 / shark 상어 / sword 황새치 / squid 오징어·문어 / crab 게 / shrimp 새우·가재 / jelly 해파리
"""
FISH_SPECIES = [
    # ---- 노말
    ("fish_mackerel", "고등어", "COD", 250, "NORMAL", "OCEAN", "", "fish", "2a5a8a", "c8d8e8", 0.95),
    ("fish_anchovy", "멸치", "COD", 120, "NORMAL", "OCEAN", "", "fish", "8aa0b8", "e8eef4", 0.7),
    ("fish_herring", "청어", "COD", 220, "NORMAL", "OCEAN,COLD", "", "fish", "4a6a8a", "d0dce8", 0.9),
    ("fish_sardine", "정어리", "COD", 160, "NORMAL", "OCEAN", "", "fish", "5a7aa0", "e0e8f0", 0.75),
    ("fish_catfish", "메기", "COD", 350, "NORMAL", "FRESH", "", "catfish", "5a5040", "a89880", 1.05),
    ("fish_loach", "미꾸라지", "COD", 180, "NORMAL", "SWAMP,RIVER", "", "eel", "7a6a40", "c8b880", 0.8),
    ("fish_perch", "쏘가리", "COD", 400, "NORMAL", "FRESH", "", "fish", "8a7a3a", "e8d890", 0.95),
    ("fish_bluegill", "블루길", "COD", 200, "NORMAL", "FRESH", "", "fish", "3a5a7a", "f0a040", 0.85),
    ("fish_trout", "송어", "SALMON", 450, "NORMAL", "RIVER,COLD", "", "fish", "6a8a5a", "f0c0b0", 1.0),
    ("fish_squid", "한치", "COD", 380, "NORMAL", "OCEAN", "", "squid", "f0c8c0", "fff0ec", 0.9),
    ("fish_shrimp", "보리새우", "COD", 300, "NORMAL", "OCEAN,SWAMP", "", "shrimp", "e07050", "f8c0a0", 0.85),
    ("fish_crab", "꽃게", "COD", 420, "NORMAL", "OCEAN", "", "crab", "b04a3a", "f09070", 0.9),
    ("fish_flounder", "가자미", "COD", 330, "NORMAL", "OCEAN", "", "ray", "8a7a5a", "d8c8a0", 0.85),
    ("fish_goby", "망둥어", "COD", 150, "NORMAL", "SWAMP,OCEAN", "", "fish", "7a6a5a", "c8b8a0", 0.8),
    # ---- 레어
    ("fish_eel", "민물 장어", "COD", 1600, "RARE", "FRESH", "", "eel", "3a3a2a", "8a8a5a", 1.1),
    ("fish_bass", "큰입 배스", "COD", 1200, "RARE", "FRESH", "", "fish", "4a6a2a", "c8d890", 1.05),
    ("fish_redsnapper", "참돔", "COD", 2200, "RARE", "OCEAN", "", "fish", "d05060", "f8c0c0", 1.05),
    ("fish_yellowtail", "방어", "COD", 2000, "RARE", "OCEAN,COLD", "", "fish", "3a5a8a", "f0e060", 1.1),
    ("fish_octopus", "대왕 문어", "COD", 2600, "RARE", "OCEAN", "", "octopus", "a03a4a", "e08a90", 1.05),
    ("fish_blowfish", "자주복", "PUFFERFISH", 1800, "RARE", "OCEAN,WARM", "", "puffer", "c8a860", "f8f0c8", 1.0),
    ("fish_icefish", "빙어", "COD", 1300, "RARE", "COLD", "", "fish", "b8e0f8", "ffffff", 0.75),
    ("fish_piranha", "피라냐", "COD", 1700, "RARE", "WARM", "", "piranha", "5a5a6a", "e04040", 0.9),
    ("fish_lobster", "바닷가재", "COD", 2800, "RARE", "OCEAN", "", "shrimp", "b02020", "e86040", 1.1),
    ("fish_jelly", "보름달 해파리", "COD", 2400, "RARE", "OCEAN", "NIGHT", "jelly", "b8a0ff", "ffffff", 1.0),
    ("fish_ray", "노랑가오리", "COD", 2300, "RARE", "OCEAN,WARM", "", "ray", "8a7a4a", "f0e0a0", 1.05),
    ("fish_char", "곤들매기", "SALMON", 1500, "RARE", "COLD,RIVER", "", "fish", "3a4a5a", "f07050", 1.0),
    ("fish_gar", "늪 가아", "COD", 1900, "RARE", "SWAMP", "", "sword", "5a6a3a", "b0c080", 1.1),
    # ---- 유니크
    ("fish_tuna", "참다랑어", "COD", 7000, "UNIQUE", "DEEP,OCEAN", "", "fish", "1a2a5a", "c0c8d8", 1.2),
    ("fish_swordfish", "황새치", "COD", 7500, "UNIQUE", "OCEAN,WARM", "", "sword", "2a3a6a", "b0c0e0", 1.15),
    ("fish_shark", "청상아리", "COD", 8500, "UNIQUE", "OCEAN", "", "shark", "4a5a7a", "e0e8f0", 1.15),
    ("fish_arowana", "은룡어", "TROPICAL_FISH", 8000, "UNIQUE", "WARM,RIVER", "", "fish", "c0c8d0", "ffffff", 1.15),
    ("fish_moonfish", "달빛 개복치", "PUFFERFISH", 6500, "UNIQUE", "OCEAN", "NIGHT", "puffer", "c8d4f0", "ffffff", 1.2),
    ("fish_ghostcray", "유령 가재", "COD", 6000, "UNIQUE", "SWAMP", "NIGHT", "shrimp", "9ab0c8", "e8f4ff", 1.0),
    ("fish_frostsalmon", "서리비늘 연어", "SALMON", 6800, "UNIQUE", "COLD", "", "fish", "5ab0e0", "ffffff", 1.1),
    ("fish_stormeel", "뇌전 뱀장어", "COD", 7200, "UNIQUE", "", "RAIN", "eel", "2a2a7a", "ffe060", 1.15),
    ("fish_coelacanth", "실러캔스", "COD", 9000, "UNIQUE", "DEEP", "", "fish", "3a4a6a", "7a8aa0", 1.15),
    ("fish_rainbow", "무지개 송어", "SALMON", 6200, "UNIQUE", "RIVER,FRESH", "RAIN", "rainbow", "ff7070", "70c0ff", 1.05),
    # ---- 전설
    ("fish_leviathan", "심연의 리바이어던", "COD", 55000, "LEGEND", "DEEP", "NIGHT", "eel", "1a1a3a", "7fe8ff", 1.25),
    ("fish_phoenixkoi", "불사조 비단잉어", "TROPICAL_FISH", 40000, "LEGEND", "WARM,RIVER", "", "fish", "ff5020", "ffd040", 1.15),
    ("fish_kraken", "새끼 크라켄", "COD", 45000, "LEGEND", "OCEAN", "RAIN", "octopus", "5a2a6a", "c080ff", 1.2),
    ("fish_starwhale", "별고래", "COD", 50000, "LEGEND", "COLD,OCEAN", "NIGHT", "whale", "1a2a5a", "aee8ff", 1.25),
    ("fish_jadekoi", "비취 잉어왕", "TROPICAL_FISH", 35000, "LEGEND", "FRESH", "", "fish", "2a9a6a", "c0ffe0", 1.15),
    ("fish_megalodon", "메갈로돈", "COD", 60000, "LEGEND", "DEEP", "", "shark", "4a5060", "c0c8d0", 1.3),
]
