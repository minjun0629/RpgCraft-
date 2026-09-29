package kr.rpgcraft.data;

import kr.rpgcraft.passive.Passive;
import kr.rpgcraft.stat.StatSnapshot;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class PlayerData {
    public final UUID uuid;
    public String name;
    public int level = 0;   // 처음 들어오면 Lv.0
    public double exp;
    public int statPoints;
    public int str, dex, adv;
    public long money;
    public boolean blacksmith;
    public final org.bukkit.inventory.ItemStack[] accessories = new org.bukkit.inventory.ItemStack[3];

    /** 도감: 한 번이라도 가져본 아이템 */
    public boolean seen(String id) {
        return counters.containsKey("seen_" + id);
    }

    public boolean markSeen(String id) {
        return id != null && counters.putIfAbsent("seen_" + id, 1.0) == null;
    }
    public String job, subJob, thirdJob, nick;
    public boolean starterGiven;
    /** 기본 지급품을 받은 기본 월드의 UID (맵을 새로 만들면 달라짐 → 다시 지급) */
    public String starterWorld;
    public final Set<String> passives = new LinkedHashSet<>();
    /** 영구 카운터 (단련된 공포 누적치 등) */
    public final Map<String, Double> counters = new HashMap<>();
    /** 회차별 카운터 */
    public int counterRound;
    public final Map<String, Double> roundCounters = new HashMap<>();
    public final Map<String, Integer> potionBag = new LinkedHashMap<>();
    public String selectedPotion;
    public ItemStack[] runes = new ItemStack[3];
    public double hp = -1;
    public String quickSkill;
    public UUID absorbTarget;
    public boolean guildChat;

    // ---- 휘발성 상태
    public transient StatSnapshot stats = StatSnapshot.EMPTY;
    public transient final Map<String, Long> cooldowns = new HashMap<>();
    public transient long lastAttack, lastDamaged, lastCombat, runStart, potionBuffUntil, doldolUntil, invulnUntil;
    public transient int hitsTakenNoAttack, pendingKnock, archerStacks;
    public transient UUID archerTarget;
    public transient long archerLast;
    public transient String ruinId;
    public transient long ruinStart;
    public transient long actionBarLock;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public boolean has(Passive p) {
        return passives.contains(p.name());
    }

    public boolean onCooldown(String key) {
        return cooldowns.getOrDefault(key, 0L) > System.currentTimeMillis();
    }

    public long remaining(String key) {
        return Math.max(0, cooldowns.getOrDefault(key, 0L) - System.currentTimeMillis());
    }

    public void cooldown(String key, long ms) {
        cooldowns.put(key, System.currentTimeMillis() + ms);
    }

    public double counter(String k) {
        return counters.getOrDefault(k, 0.0);
    }

    public void addCounter(String k, double v) {
        counters.put(k, counter(k) + v);
    }

    public double roundCounter(String k, int round) {
        syncRound(round);
        return roundCounters.getOrDefault(k, 0.0);
    }

    public void addRoundCounter(String k, double v, int round) {
        syncRound(round);
        roundCounters.put(k, roundCounters.getOrDefault(k, 0.0) + v);
    }

    private void syncRound(int round) {
        if (counterRound != round) {
            counterRound = round;
            roundCounters.clear();
        }
    }

    public int allocated() {
        return str + dex + adv;
    }
}
