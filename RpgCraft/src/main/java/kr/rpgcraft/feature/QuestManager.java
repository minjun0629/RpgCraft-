package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 일일 의뢰: 매일 3개가 무작위로 배정되며 기존 행동 카운터(처치/채집/강화 등)의 증가량으로 진행된다.
 * 3개 모두 완료하면 추가 보상. 진행 상황은 PlayerData.counters 에 저장된다.
 */
public class QuestManager {
    public record Quest(String counter, String label, int amount, double moneyMult, Material icon) {}

    public static final List<Quest> POOL = List.of(
            new Quest("mob_kills", "몬스터 %d마리 처치", 60, 1.0, Material.IRON_SWORD),
            new Quest("mob_kills", "몬스터 %d마리 처치", 150, 2.0, Material.DIAMOND_SWORD),
            new Quest("gathers", "채집 %d회", 8, 1.2, Material.WOODEN_PICKAXE),
            new Quest("enhance_attempts", "장비 강화 %d회 시도", 5, 1.3, Material.ANVIL),
            new Quest("potions", "포션 %d개 사용", 15, 0.8, Material.POTION),
            new Quest("crit_hits", "크리티컬 %d회", 120, 1.0, Material.GOLDEN_SWORD),
            new Quest("arrows_hit", "화살 %d회 명중", 60, 1.0, Material.BOW),
            new Quest("fish", "낚시 %d회 성공", 10, 1.0, Material.FISHING_ROD),
            new Quest("sprint_seconds", "%d초 동안 달리기", 600, 0.8, Material.LEATHER_BOOTS),
            new Quest("hits_taken", "공격 %d회 버텨내기", 200, 0.9, Material.SHIELD));

    private final RpgCraft plugin;

    public QuestManager(RpgCraft plugin) {
        this.plugin = plugin;
    }

    private static long today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul")).toEpochDay();
    }

    /** 날짜가 바뀌었으면 새 의뢰 배정 */
    public void ensure(PlayerData d) {
        if (d.counter("dq_day") == today()) return;
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < POOL.size(); i++) idx.add(i);
        Collections.shuffle(idx, ThreadLocalRandom.current());
        List<String> used = new ArrayList<>();
        int slot = 0;
        for (int i : idx) {
            if (slot >= 3) break;
            Quest q = POOL.get(i);
            if (used.contains(q.counter())) continue;
            used.add(q.counter());
            d.counters.put("dq_" + slot + "_idx", (double) i);
            d.counters.put("dq_" + slot + "_base", d.counter(q.counter()));
            d.counters.put("dq_" + slot + "_claimed", 0.0);
            slot++;
        }
        d.counters.put("dq_bonus", 0.0);
        d.counters.put("dq_day", (double) today());
    }

    public Quest quest(PlayerData d, int slot) {
        ensure(d);
        int i = (int) d.counter("dq_" + slot + "_idx");
        return i >= 0 && i < POOL.size() ? POOL.get(i) : POOL.get(0);
    }

    public int progress(PlayerData d, int slot) {
        Quest q = quest(d, slot);
        return (int) Math.min(q.amount(), d.counter(q.counter()) - d.counter("dq_" + slot + "_base"));
    }

    public boolean done(PlayerData d, int slot) {
        return progress(d, slot) >= quest(d, slot).amount();
    }

    public boolean claimed(PlayerData d, int slot) {
        return d.counter("dq_" + slot + "_claimed") > 0;
    }

    public int completedCount(PlayerData d) {
        int n = 0;
        for (int i = 0; i < 3; i++) if (done(d, i)) n++;
        return n;
    }

    public long moneyReward(PlayerData d, int slot) {
        return (long) (4000 * (1 + d.level * 0.12) * quest(d, slot).moneyMult() * plugin.getConfig().getDouble("economy.quest-money-mult", 1.0));
    }

    public double expReward(PlayerData d) {
        return plugin.levels().need(d.level) * 0.12;
    }

    /** PassiveManager.track 에서 호출: 방금 완료된 의뢰가 있으면 알림 */
    public void onProgress(Player p, String counter, double before) {
        PlayerData d = plugin.data().get(p);
        if (d.counter("dq_day") != today()) return;
        for (int s = 0; s < 3; s++) {
            Quest q = quest(d, s);
            if (!q.counter().equals(counter) || claimed(d, s)) continue;
            double base = d.counter("dq_" + s + "_base");
            if (before - base < q.amount() && d.counter(counter) - base >= q.amount()) {
                p.sendTitle(Text.c("&a&l의뢰 완료!"), Text.c("&f" + String.format(q.label(), q.amount()) + " &7- /메뉴 에서 보상 수령"), 5, 40, 10);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.4f);
            }
        }
    }

    public void claim(Player p, int slot) {
        PlayerData d = plugin.data().get(p);
        if (!done(d, slot)) { Text.msg(p, "&c아직 완료하지 않은 의뢰입니다."); return; }
        if (claimed(d, slot)) { Text.msg(p, "&7이미 보상을 받았습니다."); return; }
        d.counters.put("dq_" + slot + "_claimed", 1.0);
        long money = moneyReward(d, slot);
        plugin.economy().give(p, money);
        plugin.levels().addExp(p, expReward(d));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        Text.msg(p, "&a의뢰 보상: " + Text.money(money) + " + 경험치 " + Text.num(expReward(d)));
        if (d.counter("dq_bonus") == 0 && claimed(d, 0) && claimed(d, 1) && claimed(d, 2)) {
            d.counters.put("dq_bonus", 1.0);
            String id = d.level >= 90 ? "crystal_top" : d.level >= 60 ? "crystal_high" : d.level >= 30 ? "crystal_mid" : "crystal_low";
            ItemStack bonus = plugin.items().create(id, 3);
            for (ItemStack left : p.getInventory().addItem(bonus).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            if (ThreadLocalRandom.current().nextDouble() < 0.1)
                for (ItemStack left : p.getInventory().addItem(plugin.items().create("ticket_rate10", 1)).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            p.sendTitle(Text.c("&6&l일일 의뢰 올클리어!"), Text.c("&f추가 보상: " + plugin.items().get(id).name + " x3"), 5, 50, 10);
        }
    }
}
