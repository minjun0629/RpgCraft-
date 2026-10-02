package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 길라잡이: 이 서버의 시스템을 주제별로 알려 주는 안내서 (/길라잡이).
 * 아이콘에 마우스를 올리면 설명, 클릭하면 채팅에 자세히.
 * 처음 들어온 사람에게는 한 번 창을 열어 준다.
 */
public class GuideManager implements CommandExecutor, Listener {
    private record Topic(Material icon, String title, String[] lines) {}

    private static final Topic[] TOPICS = {
            new Topic(Material.COMPASS, "조작법", new String[]{
                    "쉬프트+F : 메인 메뉴", "F : 물약 가방에서 골라 둔 포션 마시기 (메뉴 > 물약 가방 좌클릭으로 선택)", "좌클릭 : 참격 (지팡이는 마력탄, 활은 사격)",
                    "우클릭 : 무기 강공격 스킬", "쉬프트+좌/우클릭 : 희귀 이상 무기 추가 스킬", "쉬프트 두 번 : 레전드 이상 무기 궁극기",
                    "쉬프트+Q (또는 /퀵키) : 퀵 스킬", "쉬프트+우클릭(플레이어) : 상대 정보"}),
            new Topic(Material.EXPERIENCE_BOTTLE, "레벨 · 스탯", new String[]{
                    "Lv.0 부터 시작해 최대 Lv.300", "레벨업마다 스탯 포인트 → 메뉴 > 스탯 에서 분배",
                    "힘: 공격력·마력 / 민첩: 치명타·이동속도 / 모험: 체력·방어력", "스탯 50마다 추가 효과",
                    "죽으면 경험치 일부를 잃음", "Lv.300 에서 /환생 (영구 강화)"}),
            new Topic(Material.IRON_SWORD, "무기 · 스킬", new String[]{
                    "검·단검·도끼·방패·창·몽둥이·활·지팡이", "무기마다 고유 스킬 (아이템 설명의 ✦ 무기 스킬)",
                    "무기는 레벨이 아니라 스탯 조건만 필요", "3번 때릴 때마다 평타 스킬", "상점 무기고 I·II·III 에서 구매", "/분해 : 안 쓰는 장비를 재료로"}),
            new Topic(Material.ANVIL, "강화 · 룬 · 장신구", new String[]{
                    "강화: 최대 +15 (결정·돈 필요, +8 부터 파괴 위험)", "룬 3칸: 무작위 옵션 (메뉴 > 룬)",
                    "장신구 3칸: 반지·목걸이·귀걸이 (메뉴 > 장신구)", "룬 변경권으로 옵션 재설정"}),
            new Topic(Material.PLAYER_HEAD, "직업", new String[]{
                    "Lv.10: 기초 직업 4종 중 선택", "Lv.40: 주 스탯에 따라 2차 직업 12종", "Lv.100: 3차 전직 (돈 + 마력 핵 10개)", "직업 초기화권으로 다시 선택"}),
            new Topic(Material.ZOMBIE_HEAD, "몬스터 · 보스", new String[]{
                    "스폰에서 멀수록 몬스터 레벨이 높음", "정예(금색) · 중간 보스(붉은색)는 더 강하고 보상이 큼",
                    "낮엔 인간형 몬스터, 밤엔 몬스터가 많아짐 (핏빛 달 주의)", "빨간 고리가 뜨면 곧 범위 기술 → 피하기",
                    "보스는 월드·던전에서 등장"}),
            new Topic(Material.RED_BANNER, "웨이브 · 던전", new String[]{
                    "필드 웨이브: 가끔 생기는 깃발 우클릭 → 3단계 방어", "웨이브 중 한 번은 부활, 두 번째엔 탈락",
                    "던전: 맵의 우는 흑요석 입구 우클릭 (파티 가능)", "던전 보상은 하루 한 번"}),
            new Topic(Material.GOLD_INGOT, "돈 · 상점", new String[]{
                    "몬스터 처치·의뢰·전리품 판매로 돈 벌기", "상점: 왕국 시장의 상인 NPC (잡화·무기고·장신구·전당포 등)",
                    "전당포 NPC: 모든 전리품 매입", "상점 [전체 판매] 버튼", "/거래 <이름> : 플레이어 거래"}),
            new Topic(Material.WRITABLE_BOOK, "의뢰 · 업적", new String[]{
                    "맵 곳곳의 [의뢰] NPC: 매일 바뀌는 의뢰 3개", "업적 달성 → 칭호 (메뉴 > 업적)", "매일 출석 보상 (30일)",
                    "어딘가에 숨은 ??? NPC 도 있다고…"}),
            new Topic(Material.FISHING_ROD, "채집 · 낚시", new String[]{
                    "채집도구로 흙·나무·돌 → 재료 (초급 3분 / 중급 2분30초 / 상급 2분)", "낚시: ◆ 가 초록 구간일 때 우클릭 2번",
                    "비·밤엔 희귀 물고기"}),
            new Topic(Material.MAP, "탐험", new String[]{
                    "보물 지도: 우클릭 해독 → 나침반 ✚ 방향", "현상수배범: 나침반 ⚔ 방향", "구조물·유적 상자: 모험 스탯 조건, 개인 전리품",
                    "전설의 대장장이 렉스: 3시간마다 스폰 근처 (명작 제작)", "수상한 상인: 가끔 어딘가에"}),
            new Topic(Material.SHIELD, "파티 · 길드 · PvP", new String[]{
                    "/파티 : 최대 5명, 기여도만큼 경험치 분배", "/길드 : 길드 · 공성전", "파티원·길드원끼리는 공격 불가",
                    "/호출 <내용> : 관리자 부르기"})};

    private final RpgCraft plugin;

    public GuideManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** (예전 호환) 사이드바 한 줄 — 이제 표시하지 않음 */
    public String line(PlayerData d) {
        return null;
    }

    public void open(Player p) {
        Gui g = new Gui(3, "&8길라잡이") {
        };
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < TOPICS.length && i < slots.length; i++) {
            Topic t = TOPICS[i];
            String[] lore = new String[t.lines.length];
            for (int j = 0; j < lore.length; j++) lore[j] = "&7· " + t.lines[j];
            g.set(slots[i], Gui.button(t.icon, "&e&l" + t.title, lore), e -> {
                p.closeInventory();
                Text.msg(p, "&6&l[" + t.title + "]");
                for (String l : t.lines) p.sendMessage(Text.c(" &7· &f" + l));
            });
        }
        g.fill(0, 26);
        g.open(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        PlayerData d = plugin.data().get(e.getPlayer());
        if (d.counter("guide_seen") > 0) return;
        d.counters.put("guide_seen", 1.0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (e.getPlayer().isOnline()) open(e.getPlayer()); }, 60L);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player p) open(p);
        return true;
    }
}
