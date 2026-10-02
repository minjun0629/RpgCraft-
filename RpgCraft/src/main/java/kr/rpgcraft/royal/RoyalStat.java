package kr.rpgcraft.royal;

import kr.rpgcraft.data.PlayerData;
import org.bukkit.Material;

/**
 * v5.11.0 로열 로드 스탯 — 「달빛 조각사」의 가상현실 게임 로열 로드처럼, 포인트를 찍는 게 아니라 <b>행동을 해야</b> 오르는 스탯.
 * 값은 PlayerData 영구 카운터(rr_st_*)에 소수로 쌓이고, 정수가 바뀔 때 "○○ 스탯이 1 상승하였습니다." 알림이 뜬다.
 */
public enum RoyalStat {
    ENDURANCE("인내", "&c", Material.IRON_CHESTPLATE, "몬스터에게 맞으며 버티기 (체력이 적을수록 더 많이)", "최대 체력 · 방어력"),
    ART("예술", "&d", Material.PAINTING, "조각품 만들기 · 남의 조각품 감상하기", "마력 · 경험치 획득량 · 조각품 품질"),
    CHARM("매력", "&e", Material.NAME_TAG, "NPC 와 대화(우클릭)하기 · 명작 이상 조각품 만들기", "NPC 상점 할인"),
    LUCK("행운", "&a", Material.RABBIT_FOOT, "낚시 · 보스 처치 · 아주 가끔 몬스터 처치", "치명타 확률 · 회피 · 조각품 품질"),
    FAITH("신앙", "&f", Material.TOTEM_OF_UNDYING, "하루 한 번 /기도", "체력 · 방어력"),
    LEADERSHIP("통솔력", "&6", Material.GOLDEN_HELMET, "파티 · 생명체와 함께 사냥하기", "생명체 능력치 · 데리고 다닐 수 있는 생명체 수 · 경험치");

    public final String label, color, howTo, effect;
    public final Material icon;

    RoyalStat(String label, String color, Material icon, String howTo, String effect) {
        this.label = label;
        this.color = color;
        this.icon = icon;
        this.howTo = howTo;
        this.effect = effect;
    }

    public String key() {
        return "rr_st_" + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** 소수점까지 쌓인 값 */
    public double raw(PlayerData d) {
        return d.counter(key());
    }

    /** 화면에 보이는 정수 값 */
    public int points(PlayerData d) {
        return (int) Math.floor(raw(d) + 1e-9);
    }
}
