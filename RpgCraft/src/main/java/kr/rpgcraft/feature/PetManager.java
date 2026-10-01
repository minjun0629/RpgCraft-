package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 펫: /펫 에서 뽑기 → 펫 알(아이템)을 우클릭하면 도감에 등록 → 도감에서 하나를 꺼내면 어깨 옆을 따라다니며 능력치를 올려 준다.
 * 모델은 리소스팩의 전용 3D 모델 (PAPER, CustomModelData 9000 + BossModelManager.ORDER 순번).
 * 등급 확률: 일반 64% · 희귀 27% · 영웅 8% · 전설 1% (config pets.odds)
 */
public class PetManager implements Listener, CommandExecutor {
    public enum Pet {
        SLIME("pet_slime", "말랑 슬라임", 0, StatMap.of(Stat.HP_PCT, 3)),
        CHICK("pet_chick", "아기 병아리", 0, StatMap.of(Stat.EXP_PCT, 6)),
        BUNNY("pet_bunny", "솜뭉치 토끼", 0, StatMap.of(Stat.DODGE, 1, Stat.HP_PCT, 1)),
        FOX("pet_fox", "불여우 새끼", 1, StatMap.of(Stat.CRIT, 2, Stat.CRIT_DMG, 5)),
        PENGUIN("pet_penguin", "꼬마 펭귄", 1, StatMap.of(Stat.DEF, 2, Stat.HP_PCT, 3)),
        OWL("pet_owl", "지혜의 부엉이", 1, StatMap.of(Stat.EXP_PCT, 10, Stat.CRIT, 1)),
        GOLEM("pet_golem", "수호 골렘", 2, StatMap.of(Stat.DEF, 4, Stat.HP_PCT, 6)),
        FAIRY("pet_fairy", "숲의 요정", 2, StatMap.of(Stat.LIFESTEAL, 2, Stat.HP_PCT, 4, Stat.EXP_PCT, 5)),
        GHOST("pet_ghost", "장난꾸러기 유령", 2, StatMap.of(Stat.DODGE, 3, Stat.CRIT, 3)),
        PHOENIX("pet_phoenix", "불사조", 3, StatMap.of(Stat.LIFESTEAL, 3, Stat.HP_PCT, 8, Stat.CRIT_DMG, 12)),
        DRAGON("pet_dragon", "아기 용", 3, StatMap.of(Stat.STR_PCT, 5, Stat.DEX_PCT, 5, Stat.ADV_PCT, 5, Stat.CRIT, 3)),
        STAR("pet_star", "별의 정령", 3, StatMap.of(Stat.EXP_PCT, 20, Stat.CRIT, 4, Stat.DODGE, 3));

        public final String model, label;
        public final int grade;
        public final StatMap stats;

        Pet(String model, String label, int grade, StatMap stats) {
            this.model = model;
            this.label = label;
            this.grade = grade;
            this.stats = stats;
        }

        public int cmd() {
            return 9000 + kr.rpgcraft.boss.BossModelManager.ORDER.indexOf(model);
        }
    }

    private static final String[] GRADE = {"&f일반", "&9희귀", "&5영웅", "&6전설"};
    private static final double[] ODDS = {0.64, 0.27, 0.08, 0.01};
    private static final Color[] TRAIL = {Color.fromRGB(0xFFFFFF), Color.fromRGB(0x5AA0FF), Color.fromRGB(0xC060FF), Color.fromRGB(0xFFC030)};

    private final RpgCraft plugin;
    private final NamespacedKey KEY, ENTITY;
    private final Map<UUID, UUID> shown = new HashMap<>();   // 플레이어 → 펫 모델
    private long tick;

    public PetManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "pet");
        this.ENTITY = new NamespacedKey(plugin, "pet_entity");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        for (World w : Bukkit.getWorlds())   // 이전 실행에서 남은 펫 모델 정리
            for (Entity e : w.getEntities()) if (e.getPersistentDataContainer().has(ENTITY, PersistentDataType.STRING)) e.remove();
    }

    private double odds(int grade) {
        List<Double> l = plugin.getConfig().getDoubleList("pets.odds");
        return grade < l.size() ? l.get(grade) : ODDS[grade];
    }

    // ------------------------------------------------------------------ 보유 · 장착
    public int ownedCount(PlayerData d) {
        int n = 0;
        for (Pet p : Pet.values()) if (owns(d, p)) n++;
        return n;
    }

    public boolean owns(PlayerData d, Pet p) {
        return d.counter("pet_own_" + p.name()) > 0;
    }

    public Pet active(PlayerData d) {
        int i = (int) d.counter("pet_active") - 1;
        return i >= 0 && i < Pet.values().length ? Pet.values()[i] : null;
    }

    /** 꺼내 둔 펫의 능력치 (StatCalculator 에서 더함) — 레벨 · 진화 · 별에 따라 커짐 (v5.10.30) */
    public StatMap bonus(PlayerData d) {
        Pet p = active(d);
        return p == null || !owns(d, p) ? new StatMap() : p.stats.scaled(mult(d, p));
    }

    // ------------------------------------------------------------------ v5.10.30 성장 · 진화 · 합성
    private static final String[] STAGE = {"아기", "성장", "각성"};
    private static final double[] STAGE_MULT = {1.0, 1.35, 1.8};
    private static final int[] STAGE_CAP = {10, 20, 30};
    public static final int MAX_STARS = 5;

    public int level(PlayerData d, Pet p) { return Math.max(1, (int) d.counter("pet_lv_" + p.name())); }

    public int stage(PlayerData d, Pet p) { return Math.min(2, (int) d.counter("pet_stage_" + p.name())); }

    public int stars(PlayerData d, Pet p) { return Math.min(MAX_STARS, (int) d.counter("pet_star_" + p.name())); }

    public int dups(PlayerData d, Pet p) { return (int) d.counter("pet_dup_" + p.name()); }

    private int cap(PlayerData d, Pet p) { return STAGE_CAP[stage(d, p)]; }

    private static double need(int lv) { return 40 + lv * 30.0; }

    public double mult(PlayerData d, Pet p) {
        return (1 + 0.03 * (level(d, p) - 1)) * STAGE_MULT[stage(d, p)] * (1 + 0.08 * stars(d, p));
    }

    private String title(PlayerData d, Pet p) {
        int st = stars(d, p);
        return (stage(d, p) > 0 ? STAGE[stage(d, p)] + "한 " : "") + p.label + " &7Lv." + level(d, p) + (st > 0 ? " &e" + "★".repeat(st) : "");
    }

    /** 꺼내 둔 펫에게 경험치 */
    public void addExp(Player pl, double n) {
        PlayerData d = plugin.data().get(pl);
        Pet p = active(d);
        if (p == null || !owns(d, p) || n <= 0) return;
        int lv = level(d, p), cap = cap(d, p);
        if (lv >= cap) return;
        double xp = d.counter("pet_xp_" + p.name()) + n * plugin.getConfig().getDouble("pets.exp-mult", 1.0);
        boolean up = false;
        while (lv < cap && xp >= need(lv)) { xp -= need(lv); lv++; up = true; }
        d.counters.put("pet_lv_" + p.name(), (double) lv);
        d.counters.put("pet_xp_" + p.name(), lv >= cap ? 0 : xp);
        if (up) {
            pl.playSound(pl.getLocation(), Sound.ENTITY_ALLAY_ITEM_GIVEN, 1f, 1.4f);
            Text.msg(pl, "&a♥ " + p.label + "&f이(가) &eLv." + lv + "&f이(가) 되었습니다!" + (lv >= cap && stage(d, p) < 2 ? " &d진화할 수 있습니다! &7(/펫 → 우클릭)" : ""));
            plugin.stats().refresh(pl);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onKill(org.bukkit.event.entity.EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null || e.getEntity() instanceof Player) return;
        boolean boss = e.getEntity().getPersistentDataContainer().has(kr.rpgcraft.Keys.BOSS, PersistentDataType.STRING);
        var t = plugin.tiers() != null ? plugin.tiers().tier(e.getEntity()) : kr.rpgcraft.mob.MonsterTierManager.Tier.NORMAL;
        addExp(k, boss ? 40 : t == kr.rpgcraft.mob.MonsterTierManager.Tier.MINIBOSS ? 10 : t == kr.rpgcraft.mob.MonsterTierManager.Tier.ELITE ? 4 : 1);
    }

    /** 먹이: 펫 간식 +60 · 요리 음식 +40 (shift: 가진 만큼) */
    private void feed(Player pl, Pet p, boolean all) {
        PlayerData d = plugin.data().get(pl);
        if (active(d) != p) { Text.actionBar(pl, "&c꺼내 둔 펫에게만 먹이를 줄 수 있습니다"); return; }
        int fed = 0;
        for (ItemStack it : pl.getInventory().getStorageContents()) {
            String id = kr.rpgcraft.item.ItemData.id(it);
            if (id == null || !(id.equals("pet_snack") || id.startsWith("food_"))) continue;
            while (it.getAmount() > 0 && level(d, p) < cap(d, p)) {
                it.setAmount(it.getAmount() - 1);
                addExp(pl, id.equals("pet_snack") ? 60 : 40);
                fed++;
                if (!all) break;
            }
            if (!all && fed > 0 || level(d, p) >= cap(d, p)) break;
        }
        if (fed == 0) { Text.actionBar(pl, level(d, p) >= cap(d, p) ? "&e지금 단계의 최고 레벨입니다 — 진화하세요" : "&c펫 간식이나 요리 음식이 없습니다 (요리 상점 · /요리)"); return; }
        pl.playSound(pl.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1.4f);
        Text.actionBar(pl, "&a♥ " + p.label + "에게 먹이 " + fed + "개를 주었습니다");
    }

    private long evolveCost(PlayerData d, Pet p) {
        return plugin.getConfig().getLong("pets.evolve-cost", 2_000_000) * (p.grade + 1) * (stage(d, p) + 1);
    }

    private void evolve(Player pl, Pet p) {
        PlayerData d = plugin.data().get(pl);
        int st = stage(d, p);
        if (st >= 2) { Text.actionBar(pl, "&7이미 마지막 단계입니다"); return; }
        if (level(d, p) < cap(d, p)) { Text.actionBar(pl, "&cLv." + cap(d, p) + "이 되어야 진화할 수 있습니다"); return; }
        long cost = evolveCost(d, p);
        if (!plugin.economy().take(pl, cost)) { Text.actionBar(pl, "&c돈이 부족합니다 (" + Text.money(cost) + ")"); return; }
        d.counters.put("pet_stage_" + p.name(), st + 1.0);
        hide(pl);
        plugin.stats().refresh(pl);
        pl.playSound(pl.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        pl.getWorld().spawnParticle(Particle.TOTEM, pl.getLocation().add(0, 1.5, 0), 60, 0.6, 0.6, 0.6, 0.3);
        pl.sendTitle(Text.c("&d&l진화!"), Text.c("&f" + p.label + " → &d" + STAGE[st + 1] + " 단계"), 5, 50, 10);
        if (st + 1 == 2 && p.grade >= 2) Text.announce(Text.PREFIX + Text.c("&d" + Text.name(pl) + "&f님의 " + GRADE[p.grade] + " " + p.label + "&f이(가) &d각성&f했습니다!"));
    }

    /** 별 합성: 같은 펫(중복으로 얻은 것) 1마리 → 별 +1 (능력치 +8%) */
    private void star(Player pl, Pet p) {
        PlayerData d = plugin.data().get(pl);
        if (dups(d, p) <= 0) { Text.actionBar(pl, "&c합성할 같은 펫이 없습니다 (중복으로 뽑으면 쌓임)"); return; }
        if (stars(d, p) >= MAX_STARS) { Text.actionBar(pl, "&7별이 가득 찼습니다"); return; }
        d.counters.put("pet_dup_" + p.name(), dups(d, p) - 1.0);
        d.counters.put("pet_star_" + p.name(), stars(d, p) + 1.0);
        plugin.stats().refresh(pl);
        pl.playSound(pl.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.6f);
        Text.msg(pl, "&e★ " + p.label + " 별 합성 성공! &f" + "★".repeat(stars(d, p)));
    }

    /** 등급 합성: 같은 등급의 남는 펫 5마리 (v5.10.35 3 → 5, config pets.fuse-count) → 한 등급 위 펫 알 1개 */
    private int fuseCount() {
        return Math.max(2, plugin.getConfig().getInt("pets.fuse-count", 5));
    }

    private void gradeFuse(Player pl, int grade) {
        PlayerData d = plugin.data().get(pl);
        if (grade >= 3) return;
        int have = 0;
        for (Pet m : Pet.values()) if (m.grade == grade) have += dups(d, m);
        int need = fuseCount();
        if (have < need) { Text.actionBar(pl, "&c" + Text.strip(Text.c(GRADE[grade])) + " 남는 펫이 " + need + "마리 필요합니다 (" + have + "/" + need + ")"); return; }
        int left = need;
        for (Pet m : Pet.values()) {
            if (m.grade != grade) continue;
            int take = Math.min(left, dups(d, m));
            d.counters.put("pet_dup_" + m.name(), dups(d, m) - (double) take);
            left -= take;
            if (left == 0) break;
        }
        List<Pet> pool = new ArrayList<>();
        for (Pet m : Pet.values()) if (m.grade == grade + 1) pool.add(m);
        Pet got = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        for (ItemStack l : pl.getInventory().addItem(egg(got)).values()) pl.getWorld().dropItemNaturally(pl.getLocation(), l);
        pl.playSound(pl.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        pl.sendTitle(Text.c("&d등급 합성!"), Text.c(GRADE[got.grade] + " &f" + got.label), 5, 40, 10);
        if (got.grade == 3) Text.announce(Text.PREFIX + Text.c("&6&l" + Text.name(pl) + "&f님이 합성으로 전설 펫 &6" + got.label + "&f을(를) 얻었습니다!"));
    }

    /** 펫 하나의 성장 창 */
    public void openPet(Player pl, Pet p) {
        PlayerData d = plugin.data().get(pl);
        if (!owns(d, p)) return;
        Gui g = new Gui(4, "&8펫 - " + p.label) {
        };
        int lv = level(d, p), cap = cap(d, p), st = stage(d, p);
        double xp = d.counter("pet_xp_" + p.name());
        List<String> info = new ArrayList<>();
        info.add(GRADE[p.grade] + " &7펫 · &d" + STAGE[st] + " 단계");
        info.add("&f레벨 &e" + lv + " &7/ " + cap + (st < 2 ? " &8(진화하면 " + STAGE_CAP[st + 1] + "까지)" : ""));
        if (lv < cap) { info.add(Text.bar(xp / need(lv), 20, "&a", "&8")); info.add("&7다음 레벨까지 &f" + (int) xp + " / " + (int) need(lv)); }
        info.add("&f별 &e" + (stars(d, p) > 0 ? "★".repeat(stars(d, p)) : "없음") + " &7(" + stars(d, p) + "/" + MAX_STARS + ")");
        info.add("");
        info.add("&f능력치 &7(×" + String.format("%.2f", mult(d, p)) + ")");
        StatMap now = p.stats.scaled(mult(d, p));
        for (Stat s : Stat.values()) if (now.get(s) != 0) info.add("&a" + s.label + " " + Text.signed(Math.round(now.get(s) * 10) / 10.0, s.pct));
        info.add("");
        info.add("&7레벨 +3%/Lv · 진화 ×1.35 / ×1.8 · 별 +8%");
        ItemStack head = Gui.button(Material.PAPER, GRADE[p.grade].substring(0, 2) + "&l" + title(d, p), info.toArray(new String[0]));
        ItemMeta hm = head.getItemMeta();
        hm.setCustomModelData(p.cmd());
        head.setItemMeta(hm);
        g.set(4, head, null);
        g.set(19, Gui.button(Material.COOKIE, "&a&l먹이 주기", "&7펫 간식 +60 · 요리 음식 +40 경험치", "&7사냥하면 꺼내 둔 펫도 경험치를 얻음",
                "&8(일반 1 · 정예 4 · 중간 보스 10 · 보스 40)", "", "&e▶ 클릭: 하나 · 쉬프트: 가진 만큼"), e -> { feed(pl, p, e.isShiftClick()); openPet(pl, p); });
        g.set(21, Gui.button(st >= 2 ? Material.NETHER_STAR : lv >= cap ? Material.DRAGON_BREATH : Material.GLASS_BOTTLE,
                st >= 2 ? "&d&l각성 완료" : "&d&l진화 → " + STAGE[st + 1],
                st >= 2 ? "&7마지막 단계입니다" : "&7조건: Lv." + cap, st >= 2 ? "" : "&7비용: &e" + Text.money(evolveCost(d, p)),
                st >= 2 ? "" : "&7능력치 ×" + STAGE_MULT[st + 1] + " · 최대 레벨 " + STAGE_CAP[st + 1] + " · 더 커짐",
                st >= 2 ? "" : lv >= cap ? "&e▶ 클릭하여 진화" : "&8레벨이 부족합니다"), e -> { evolve(pl, p); openPet(pl, p); });
        g.set(23, Gui.button(Material.NETHER_STAR, "&e&l별 합성", "&7중복으로 얻은 같은 펫 1마리 → 별 +1", "&7별 하나마다 능력치 +8% (최대 " + MAX_STARS + ")",
                "", "&f남는 같은 펫: &e" + dups(d, p) + "마리", "&e▶ 클릭"), e -> { star(pl, p); openPet(pl, p); });
        boolean act = active(d) == p;
        g.set(25, Gui.button(act ? Material.LIME_DYE : Material.GRAY_DYE, act ? "&a함께하는 중 &7(클릭: 넣기)" : "&e▶ 꺼내기"), e -> {
            setActive(pl, active(plugin.data().get(pl)) == p ? null : p);
            openPet(pl, p);
        });
        g.set(31, Gui.button(Material.ARROW, "&f◀ 펫 도감"), e -> open(pl));
        g.fill(0, 35);
        g.open(pl);
    }

    private void setActive(Player pl, Pet p) {
        PlayerData d = plugin.data().get(pl);
        if (p == null) d.counters.remove("pet_active");
        else d.counters.put("pet_active", p.ordinal() + 1.0);
        hide(pl);
        plugin.stats().refresh(pl);
    }

    // ------------------------------------------------------------------ 펫 알 (뽑기 결과 아이템)
    public ItemStack egg(Pet p) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Text.c(GRADE[p.grade].substring(0, 2) + "&l" + p.label + " &7(펫)"));
        List<String> lore = new ArrayList<>();
        lore.add(Text.c(GRADE[p.grade] + " &7펫"));
        lore.add("");
        for (String s : statLines(p)) lore.add(Text.c(s));
        lore.add("");
        lore.add(Text.c("&e▶ 우클릭: 펫 도감에 등록 (/펫)"));
        meta.setLore(lore);
        meta.setCustomModelData(p.cmd());
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, p.name());
        it.setItemMeta(meta);
        return it;
    }

    private List<String> statLines(Pet p) {
        List<String> out = new ArrayList<>();
        for (Stat s : Stat.values()) {
            double v = p.stats.get(s);
            if (v != 0) out.add("&a" + s.label + " " + Text.signed(v, s.pct));
        }
        return out;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta()) return;
        String id = it.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        if (d.onCooldown("pet_egg")) return;
        d.cooldown("pet_egg", 500);
        Pet pet;
        try { pet = Pet.valueOf(id); } catch (IllegalArgumentException ex) { return; }
        it.setAmount(it.getAmount() - 1);
        if (owns(d, pet)) {   // 중복: 합성 재료로 보관 (v5.10.30 — 별 합성 · 등급 합성). 별이 가득 차고 재료도 넉넉하면 환급
            if (stars(d, pet) >= MAX_STARS && dups(d, pet) >= fuseCount()) {
                long back = (long) (plugin.getConfig().getLong("pets.draw-cost", 1500000) * plugin.getConfig().getDouble("pets.duplicate-refund", 0.2));
                plugin.economy().give(p, back);
                Text.msg(p, "&7별이 가득 찬 펫이라 &e" + Text.money(back) + "&7을(를) 돌려받았습니다.");
                return;
            }
            d.counters.put("pet_dup_" + pet.name(), dups(d, pet) + 1.0);
            Text.msg(p, "&e" + pet.label + "&f을(를) 합성 재료로 보관했습니다 &7(남는 펫 " + dups(d, pet) + "마리 · /펫 → 우클릭: 별 합성)");
            return;
        }
        d.counters.put("pet_own_" + pet.name(), 1.0);
        p.playSound(p.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1f, 1.2f);
        Text.msg(p, "&a펫 도감에 " + GRADE[pet.grade] + " &f" + pet.label + "&a을(를) 등록했습니다! &7(/펫 에서 꺼내기)");
        if (active(d) == null) setActive(p, pet);
    }

    // ------------------------------------------------------------------ 뽑기
    private void draw(Player p) {
        long cost = plugin.getConfig().getLong("pets.draw-cost", 1500000);
        if (!plugin.economy().take(p, cost)) { Text.actionBar(p, "&c돈이 부족합니다 (" + Text.money(cost) + ")"); return; }
        double r = ThreadLocalRandom.current().nextDouble(), acc = 0;
        int grade = 0;
        for (int i = 0; i < 4; i++) { acc += odds(i); if (r < acc) { grade = i; break; } }
        List<Pet> pool = new ArrayList<>();
        for (Pet m : Pet.values()) if (m.grade == grade) pool.add(m);
        Pet got = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        for (ItemStack l : p.getInventory().addItem(egg(got)).values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
        p.playSound(p.getLocation(), grade >= 2 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        p.sendTitle(Text.c(GRADE[grade]), Text.c("&f" + got.label), 5, 40, 10);
        if (grade == 3) Text.announce(Text.PREFIX + Text.c("&6&l" + Text.name(p) + "&f님이 전설 펫 &6" + got.label + "&f을(를) 뽑았습니다!"));
        open(p);
    }

    // ------------------------------------------------------------------ 창
    public void open(Player p) {
        PlayerData d = plugin.data().get(p);
        Gui g = new Gui(6, "&8펫 도감") {
        };
        Pet act = active(d);
        for (Pet pet : Pet.values()) {
            int slot = 10 + pet.grade * 9 + (pet.ordinal() % 3) * 2;
            boolean own = owns(d, pet);
            List<String> lore = new ArrayList<>();
            lore.add(GRADE[pet.grade] + " &7펫");
            lore.add("");
            if (own) {
                lore.add("&d" + STAGE[stage(d, pet)] + " 단계 &7· Lv." + level(d, pet) + (stars(d, pet) > 0 ? " &e" + "★".repeat(stars(d, pet)) : "")
                        + (dups(d, pet) > 0 ? " &7· 남는 펫 " + dups(d, pet) : ""));
                StatMap now = pet.stats.scaled(mult(d, pet));
                for (Stat s : Stat.values()) if (now.get(s) != 0) lore.add("&a" + s.label + " " + Text.signed(Math.round(now.get(s) * 10) / 10.0, s.pct));
            } else lore.add("&8능력치: ???");   // 아직 얻지 못한 펫은 능력치를 가림
            lore.add("");
            if (!own) lore.add("&8미보유 — 뽑기로 얻을 수 있습니다");
            else {
                lore.add(pet == act ? "&a● 함께하는 중 &7(좌클릭: 넣기)" : "&e▶ 좌클릭: 꺼내기");
                lore.add("&d▶ 우클릭: 성장 · 진화 · 합성");
            }
            ItemStack icon;
            if (own) {
                icon = Gui.button(Material.PAPER, GRADE[pet.grade].substring(0, 2) + "&l" + title(d, pet), lore.toArray(new String[0]));
                ItemMeta m = icon.getItemMeta();
                m.setCustomModelData(pet.cmd());
                icon.setItemMeta(m);
            } else icon = Gui.button(Material.GRAY_DYE, "&8??? " + GRADE[pet.grade].substring(0, 2) + "(" + pet.label + ")", lore.toArray(new String[0]));
            g.set(slot, icon, e -> {
                if (!own) return;
                if (e.isRightClick()) { openPet(p, pet); return; }
                if (pet == active(plugin.data().get(p))) { setActive(p, null); Text.actionBar(p, "&7펫을 넣었습니다"); }
                else { setActive(p, pet); Text.actionBar(p, "&a" + pet.label + "&f와(과) 함께합니다!"); p.playSound(p.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 1f, 1.2f); }
                open(p);
            });
        }
        long cost = plugin.getConfig().getLong("pets.draw-cost", 1500000);
        List<String> lore = new ArrayList<>();
        lore.add("&7비용 " + Text.money(cost));
        lore.add("");
        for (int i = 0; i < 4; i++) {
            StringBuilder names = new StringBuilder();
            for (Pet m : Pet.values()) if (m.grade == i) names.append(names.length() > 0 ? ", " : "").append(m.label);
            lore.add(GRADE[i] + " &7" + String.format(odds(i) < 0.1 ? "%.1f" : "%.0f", odds(i) * 100) + "% &8(" + names + ")");
        }
        lore.add("");
        lore.add("&7중복 펫은 합성 재료로 보관 (별 합성 · 등급 합성)");
        lore.add("&e▶ 클릭하여 뽑기");
        g.set(49, Gui.button(Material.EGG, "&6&l펫 뽑기", lore.toArray(new String[0])), e -> draw(p));
        g.set(45, Gui.button(Material.BARRIER, "&c펫 넣기"), e -> { setActive(p, null); open(p); });
        for (int gr = 0; gr < 3; gr++) {   // 등급 합성 (v5.10.30)
            int have = 0, fg = gr;
            for (Pet m : Pet.values()) if (m.grade == gr) have += dups(d, m);
            int need = fuseCount();
            g.set(46 + gr, Gui.button(have >= need ? Material.ENCHANTING_TABLE : Material.CRAFTING_TABLE, "&d&l등급 합성 " + GRADE[gr] + " &7→ " + GRADE[gr + 1],
                    "&7같은 등급의 남는 " + Text.strip(Text.c(GRADE[gr])) + " 펫 " + need + "마리 → " + Text.strip(Text.c(GRADE[gr + 1])) + " 펫 알 1개", "", "&f가진 재료: &e" + have + " / " + need,
                    have >= need ? "&e▶ 클릭" : "&8재료 부족"), e -> { gradeFuse(p, fg); open(p); });
        }
        g.set(50, Gui.button(Material.COOKIE, "&a&l펫 간식 사기", "&7요리 재료 상점에서 팝니다", "&e▶ 클릭"), e -> {
            if (plugin.shops().get("cook") != null) plugin.shops().open(p, "cook", 0);
        });
        int owned = 0;
        for (Pet pet : Pet.values()) if (owns(d, pet)) owned++;
        g.set(4, Gui.button(Material.BOOK, "&e&l펫 도감 &f" + owned + " / " + Pet.values().length,
                "&7모은 펫: " + (owned * 100 / Pet.values().length) + "%", "&7얻지 못한 펫의 능력치는 가려집니다"));
        g.set(53, Gui.button(Material.ARROW, "&f◀ 도감"), e -> plugin.menu().openCodex(p));
        g.fill(0, 53);
        g.open(p);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player p) open(p);
        return true;
    }

    // ------------------------------------------------------------------ 따라다니기
    private void tick() {
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Pet pet = active(plugin.data().get(p));
            if (pet == null || !owns(plugin.data().get(p), pet) || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) { hide(p); continue; }
            Entity d = shown.containsKey(p.getUniqueId()) ? Bukkit.getEntity(shown.get(p.getUniqueId())) : null;
            Location want = spot(p);
            if (d == null || !d.isValid() || !d.getWorld().equals(p.getWorld()) || d.getLocation().distanceSquared(want) > 64) {
                if (d != null) d.remove();
                d = spawn(p, pet, want);
            } else {
                // 렉 줄이기 (v5.4.29): 주인이 움직이거나 돌았을 때만 순간이동, 둥실거림은 0.5초마다 부드럽게 보간
                Location cl = d.getLocation();
                if (cl.distanceSquared(want) > 0.0004 || Math.abs(cl.getYaw() - want.getYaw()) > 0.5) d.teleport(want);
                if (tick % 10 == 0 && d instanceof ItemDisplay idp) {
                    float sc = (float) (plugin.getConfig().getDouble("pets.scale", 0.6) * (1 + 0.15 * stage(plugin.data().get(p), pet)));
                    float bob = (float) (Math.sin((tick + 10 + p.getEntityId() * 7) / 9.0) * 0.12);
                    idp.setInterpolationDelay(0);
                    idp.setInterpolationDuration(10);
                    idp.setTransformation(new Transformation(new Vector3f(0, bob, 0), new AxisAngle4f((float) Math.PI, 0, 1, 0), new Vector3f(sc), new AxisAngle4f()));
                }
            }
            if (stage(plugin.data().get(p), pet) >= 2 && tick % 5 == 0)   // 각성한 펫: 반짝이
                p.getWorld().spawnParticle(Particle.END_ROD, want.clone().add(0, 0.3, 0), 1, 0.2, 0.2, 0.2, 0.005);
            if (pet.grade >= 2 && tick % 8 == 0)
                p.getWorld().spawnParticle(Particle.REDSTONE, want.clone().add(0, 0.1, 0), 2, 0.15, 0.1, 0.15, 0, new Particle.DustOptions(TRAIL[pet.grade], 0.8f));
            if (pet == Pet.PHOENIX && tick % 6 == 0) p.getWorld().spawnParticle(Particle.FLAME, want, 1, 0.1, 0.05, 0.1, 0.005);
        }
    }

    /** 오른쪽 어깨 뒤, 둥실둥실 */
    private Location spot(Player p) {
        Location l = p.getLocation();
        double yaw = Math.toRadians(l.getYaw());
        double side = 0.85, back = -0.45;
        double x = -Math.cos(yaw) * side - Math.sin(yaw) * back, z = -Math.sin(yaw) * side + Math.cos(yaw) * back;   // 오른쪽 · 뒤
        Location out = l.clone().add(x, 1.25, z);   // 둥실거림은 모델 변형(보간)으로 (v5.4.29)
        out.setPitch(0);
        return out;
    }

    private Entity spawn(Player p, Pet pet, Location at) {
        ItemDisplay d = at.getWorld().spawn(at, ItemDisplay.class, x -> {
            ItemStack model = new ItemStack(Material.PAPER);
            ItemMeta mm = model.getItemMeta();
            mm.setCustomModelData(pet.cmd());
            model.setItemMeta(mm);
            x.setItemStack(model);
            x.setPersistent(false);
            float sc = (float) (plugin.getConfig().getDouble("pets.scale", 0.6) * (1 + 0.15 * stage(plugin.data().get(p), pet)));
            x.setTransformation(new Transformation(new Vector3f(0, 0, 0), new AxisAngle4f((float) Math.PI, 0, 1, 0), new Vector3f(sc), new AxisAngle4f()));
            x.getPersistentDataContainer().set(ENTITY, PersistentDataType.STRING, p.getUniqueId().toString());
        });
        shown.put(p.getUniqueId(), d.getUniqueId());
        return d;
    }

    private void hide(Player p) {
        UUID u = shown.remove(p.getUniqueId());
        Entity e = u == null ? null : Bukkit.getEntity(u);
        if (e != null) e.remove();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hide(e.getPlayer());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        hide(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        hide(e.getPlayer());
    }

    public void cleanup() {
        for (Player p : Bukkit.getOnlinePlayers()) hide(p);
    }
}
