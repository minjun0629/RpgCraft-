package kr.rpgcraft.passive;

import kr.rpgcraft.stat.StatMap;

import static kr.rpgcraft.stat.Stat.*;

/**
 * RpgCraft 특수 스킬.
 * HIDDEN = 히든 패시브, DUNGEON = 던전 클리어 보상, FIRST = 던전 최초 클리어 보상, SECRET = ??? 패시브
 * cooldown > 0 이면 액티브 스킬 (/스킬 GUI 또는 쉬프트+F 퀵슬롯으로 사용)
 */
public enum Passive {
    SWORD_SAINT(Type.SECRET, "검성의 깨달음", "힘 +15%, 치명타 +10%, 치명타 피해 +40%", 0, StatMap.of(STR_PCT, 15, CRIT, 10, CRIT_DMG, 40)),
    SAGE_WISDOM(Type.SECRET, "현자의 지혜", "민첩 +15%, 마력 +400, 경험치 +30%", 0, StatMap.of(DEX_PCT, 15, MAGIC, 400, EXP_PCT, 30)),
    KINGS_MAJESTY(Type.SECRET, "몰락한 왕의 위엄", "모험 +15%, 체력 +20%, 방어력 +6", 0, StatMap.of(ADV_PCT, 15, HP_PCT, 20, DEF, 6)),
    FISH_MASTER(Type.HIDDEN, "낚시 명인", "경험치 +5%, 행운 +", 0, StatMap.of(EXP_PCT, 5)),
    GATHER_MASTER(Type.HIDDEN, "채집 장인", "모험 +5%", 0, StatMap.of(ADV_PCT, 5)),
    TREASURE_HUNTER(Type.HIDDEN, "보물 사냥꾼", "회피 +3%, 이동속도 +5%", 0, StatMap.of(DODGE, 3, SPEED, 5)),
    BOSS_SLAYER(Type.HIDDEN, "보스 사냥꾼", "치명타 피해 +30%", 0, StatMap.of(CRIT_DMG, 30)),
    WAVE_DEFENDER(Type.HIDDEN, "방어의 달인", "방어력 +5", 0, StatMap.of(DEF, 5)),
    DUNGEON_DIVER(Type.HIDDEN, "던전 탐험가", "체력 +8%", 0, StatMap.of(HP_PCT, 8)),
    ELITE_HUNTER(Type.HIDDEN, "정예 사냥꾼", "치명타 +5%", 0, StatMap.of(CRIT, 5)),
    QUEST_DEVOTEE(Type.HIDDEN, "의뢰 해결사", "경험치 +10%", 0, StatMap.of(EXP_PCT, 10)),
    HP_BOOST_1(Type.HIDDEN, "체력 증진 1", "체력 +3%", 0, StatMap.of(HP_PCT, 3)),
    HP_BOOST_2(Type.HIDDEN, "체력 증진 2", "체력 +6%", 0, StatMap.of(HP_PCT, 6)),
    HP_BOOST_3(Type.HIDDEN, "체력 증진 3", "체력 +10%", 0, StatMap.of(HP_PCT, 10)),
    HP_BOOST_4(Type.HIDDEN, "체력 증진 4", "체력 +15%", 0, StatMap.of(HP_PCT, 15)),
    HP_BOOST_5(Type.HIDDEN, "체력 증진 5", "체력 +25%", 0, StatMap.of(HP_PCT, 25)),
    DELICATE_1(Type.HIDDEN, "섬세한 공격 1", "공격력 +10", 0, StatMap.of(ATK, 10)),
    DELICATE_2(Type.HIDDEN, "섬세한 공격 2", "공격력 +20", 0, StatMap.of(ATK, 20)),
    DELICATE_3(Type.HIDDEN, "섬세한 공격 3", "공격력 +30", 0, StatMap.of(ATK, 30)),
    DELICATE_4(Type.HIDDEN, "섬세한 공격 4", "공격력 +50", 0, StatMap.of(ATK, 50)),
    DELICATE_5(Type.HIDDEN, "섬세한 공격 5", "공격력 +100", 0, StatMap.of(ATK, 100)),
    DELICATE_6(Type.HIDDEN, "섬세한 공격 6", "공격력 +500", 0, StatMap.of(ATK, 500)),
    DELICATE_7(Type.HIDDEN, "섬세한 공격 7", "공격력 +1000", 0, StatMap.of(ATK, 1000)),
    PUSH(Type.HIDDEN, "밀치기", "사용 후 다음 타격 시 상대를 10칸 밀친다", 180, null),
    JACKPOT(Type.HIDDEN, "잭팟", "사망 시 0.1% 확률로 소지금 10배", 0, null),
    ENHANCE_GOD(Type.HIDDEN, "강화의 신", "강화 확률 +1%", 0, StatMap.of(ENHANCE_RATE, 1)),
    RUNNER(Type.HIDDEN, "달리기 선수", "멈추지 않고 달릴수록 5초당 공격력 +5 (최대 +100)", 0, null),
    AIR_COMBAT(Type.HIDDEN, "공중전투", "점프 공격 시 공격력 +50", 0, null),
    CROUCH_ATTACK(Type.HIDDEN, "방어는 최선의 공격", "웅크린 상태로 공격 시 공격력 +50", 0, null),
    CURL(Type.HIDDEN, "몸말기", "웅크린 상태로 피격 시 대미지 3% 감소", 0, null),
    BACK_GUARD(Type.HIDDEN, "뒤통수 강화", "뒤에서 피격 시 대미지 50% 감소", 0, null),
    AIR_MASTER(Type.HIDDEN, "공중전투 달인", "점프 상태로 피격 시 5% 확률로 대미지 반사", 0, null),
    PARALYZE_ARROW(Type.HIDDEN, "마비화살", "원거리 공격 시 3% 확률로 2초 이동 불가", 0, null),
    ENDURANCE(Type.HIDDEN, "맷집", "공격하지 않고 10회 연속 피격 시 체력 500 회복", 0, null),
    POTION_SIDE(Type.HIDDEN, "물약 부작용", "물약 사용 시 25% 확률로 10초간 공격력 +25", 0, null),
    VITAL(Type.HIDDEN, "급소", "크리티컬 성공 시 체력 3% 회복 (쿨타임 3초)", 0, null),
    SNIPER(Type.HIDDEN, "스나이퍼", "30칸 이상 떨어진 적을 원거리 타격 시 대미지 100% 증가", 0, null),
    ARCHER(Type.HIDDEN, "노련한 궁수", "활로 같은 적 연속 타격 시 공격력 +20씩 (최대 400, 유예 10초)", 0, null),
    BULLY(Type.HIDDEN, "약자 멸시", "적이 나보다 레벨이 낮으면 공격력 +100", 0, null),
    UNDERDOG(Type.HIDDEN, "강자 멸시", "적이 나보다 레벨이 높으면 공격력 +100", 0, null),
    TRUCE(Type.HIDDEN, "휴전", "뒤로 13칸 돌진", 60, null),

    MERMAID(Type.DUNGEON, "인어의 용사", "영구적으로 공격력 +20", 0, StatMap.of(ATK, 20)),
    ORC(Type.DUNGEON, "오크들의 축복", "경험치 획득량 +10%", 0, StatMap.of(EXP_PCT, 10)),
    FAIRY(Type.DUNGEON, "선녀의 갑옷", "영구적으로 체력 +1,000", 0, StatMap.of(HP, 1000)),
    PIRATE(Type.DUNGEON, "해적의 생명력", "영구적으로 체력 +1,000", 0, StatMap.of(HP, 1000)),
    STONE(Type.DUNGEON, "돌의 단단함", "영구적으로 체력 +1,000", 0, StatMap.of(HP, 1000)),
    SPIDER(Type.DUNGEON, "거미의 빠름", "영구적으로 이동속도 +2", 0, StatMap.of(SPEED, 2)),
    BANDIT(Type.DUNGEON, "산적의 강인함", "영구적으로 공격력 +30", 0, StatMap.of(ATK, 30)),
    HARPY_WING(Type.DUNGEON, "하피의 날개짓", "사용 후 다음 타격이 상대를 5칸 넉백", 60, null),
    GOBLIN_HEADBUTT(Type.DUNGEON, "고블린 박치기", "앞으로 8칸 돌진", 60, null),
    RED_TRANSCEND(Type.DUNGEON, "붉은 초월체의 힘", "영구적으로 공격력 +50", 0, StatMap.of(ATK, 50)),
    SEA_GOLEM(Type.DUNGEON, "바다골렘의 단단함", "사용 후 2초간 무적", 600, null),
    MUD_SORROW(Type.DUNGEON, "흙흙이의 슬픔", "4칸 이내 적에게 들고 있는 무기 대미지", 60, null),
    DOLDOL_GIANT(Type.DUNGEON, "돌돌이의 거대함", "5분간 최대 체력 +50%", 900, null),
    BURNING_HAND(Type.DUNGEON, "불타는 손", "타격 시 30% 확률로 체력 500 회복", 0, null),
    PYRAMID_CURSE(Type.DUNGEON, "피라미드의 저주", "바라보는 10칸 이내 상대 2초 기절", 120, null),
    BLUE_TRANSCEND(Type.DUNGEON, "푸른 초월체의 힘", "100레벨 이상 체력 +1,000 / 105레벨 이상 체력 +1,000", 0, null),
    UNDERGROUND(Type.DUNGEON, "지하의 기운", "최대 체력 +30%", 0, StatMap.of(HP_PCT, 30)),

    FC_SPIDER(Type.FIRST, "거미 퀘스트 최초 보상", "영구적으로 공격력 +250", 0, StatMap.of(ATK, 250)),
    FC_BANDIT(Type.FIRST, "산적 퀘스트 최초 보상", "영구적으로 체력 +1,500", 0, StatMap.of(HP, 1500)),
    FC_HARPY(Type.FIRST, "하피 퀘스트 최초 보상", "전방으로 날아오른다", 30, null),
    FC_GOBLIN(Type.FIRST, "고블린 퀘스트 최초 보상", "사망 시 10% 확률로 영구 스탯 +3", 0, null),
    FC_DOLDOL(Type.FIRST, "돌돌이 최초 클리어 보상", "웅크릴 시 80% 확률로 대미지 30% 경감", 0, null),
    FC_SEA_GOLEM(Type.FIRST, "바다골렘 퀘스트 최초 보상", "물에 들어가면 체력 100% 회복 (쿨타임 10분)", 0, null),
    FC_RED(Type.FIRST, "붉은 초월체 퀘스트 최초 보상", "평타 60% 확률로 적을 불태운다", 0, null),
    FC_DESERT(Type.FIRST, "불타는 사막 퀘스트 최초 보상", "영구적으로 공격력 +250, 체력 +2,000", 0, StatMap.of(ATK, 250, HP, 2000)),
    FC_PYRAMID(Type.FIRST, "피라미드 퀘스트 최초 보상", "영구적으로 공격력 +300", 0, StatMap.of(ATK, 300)),
    FC_BLUE(Type.FIRST, "푸른 초월체 퀘스트 최초 보상", "영구적으로 체력 +5,000", 0, StatMap.of(HP, 5000)),

    TRAINED_FEAR(Type.SECRET, "단련된 공포", "플레이어 처치 시 공격력 영구 +5 (최대 +500, 회차당 +50)", 0, null),
    FAMILIAR_FEAR(Type.SECRET, "익숙한 공포", "사망 시 공격력 영구 +15 (최대 +500)", 0, null),
    HIDDEN_POWER(Type.SECRET, "숨겨둔 힘", "미사용 스탯 포인트 1당 공격력 +3, 체력 +8", 0, null),
    WEALTH(Type.SECRET, "넘치는 재력", "소지금 1천만/3천만/1억 이상 시 공격력·체력 증가", 0, null),
    ABSORB(Type.SECRET, "흡수", "/흡수 <대상> 지정 후 처치 시 스탯 3~10 강탈 (쿨 10분, 지정 쿨 60분)", 0, null),
    STRONGEST(Type.SECRET, "최강자", "낮은 레벨에게 받는 대미지 50% 감소, 최고 레벨일 때 공격력 +500 체력 +2,000", 0, null),
    MOON_FISHER(Type.SECRET, "전설의 달빛 낚시꾼", "낚시 성공 시 체력 영구 +40 (최대 +8,000)", 0, null),
    MOON_GATHERER(Type.SECRET, "전설의 달빛 채집가", "채집 시 체력 영구 +100 (최대 +8,000)", 0, null),
    GOLDEN_HAND(Type.SECRET, "황금의 손", "대장장이 무기 제작 시 +8강, 방어구 +3강으로 제작", 0, null),
    GAMBLER(Type.SECRET, "도박사", "레벨업 시 스탯 30 지급. 사망 시 레벨과 스탯 초기화 (해제 불가)", 0, null);

    public enum Type {
        HIDDEN("히든 패시브"), DUNGEON("던전 클리어 보상"), FIRST("던전 최초 클리어 보상"), SECRET("??? 패시브");
        public final String label;

        Type(String l) {
            label = l;
        }
    }

    public final Type type;
    public final String label, desc;
    public final int cooldown;
    public final StatMap flat;

    Passive(Type type, String label, String desc, int cooldown, StatMap flat) {
        this.type = type;
        this.label = label;
        this.desc = desc;
        this.cooldown = cooldown;
        this.flat = flat;
    }

    public boolean isActive() {
        return cooldown > 0;
    }

    /** 표시용 종류 이름: 쿨타임이 있는 것은 "히든 액티브" 처럼 (v5.4.26: 공지에 "히든 패시브 휴전"으로 나왔음) */
    public String kindLabel() {
        return isActive() ? type.label.replace("패시브", "액티브") : type.label;
    }

    public static Passive find(String s) {
        for (Passive p : values()) if (p.name().equalsIgnoreCase(s) || p.label.replace(" ", "").equals(s.replace(" ", ""))) return p;
        return null;
    }
}
