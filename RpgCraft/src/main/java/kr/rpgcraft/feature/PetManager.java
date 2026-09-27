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

    /** 꺼내 둔 펫의 능력치 (StatCalculator 에서 더함) */
    public StatMap bonus(PlayerData d) {
        Pet p = active(d);
        return p == null || !owns(d, p) ? new StatMap() : p.stats;
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
        if (owns(d, pet)) {   // 중복: 뽑기 비용 일부 환급
            long back = (long) (plugin.getConfig().getLong("pets.draw-cost", 1500000) * plugin.getConfig().getDouble("pets.duplicate-refund", 0.2));
            plugin.economy().give(p, back);
            Text.msg(p, "&7이미 가진 펫이라 &e" + Text.money(back) + "&7을(를) 돌려받았습니다.");
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
        if (grade == 3) Text.announce(Text.PREFIX + Text.c("&6&l" + p.getName() + "&f님이 전설 펫 &6" + got.label + "&f을(를) 뽑았습니다!"));
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
            if (own) lore.addAll(statLines(pet));
            else lore.add("&8능력치: ???");   // 아직 얻지 못한 펫은 능력치를 가림
            lore.add("");
            if (!own) lore.add("&8미보유 — 뽑기로 얻을 수 있습니다");
            else if (pet == act) lore.add("&a● 함께하는 중 &7(클릭: 넣기)");
            else lore.add("&e▶ 클릭: 꺼내기");
            ItemStack icon;
            if (own) {
                icon = Gui.button(Material.PAPER, GRADE[pet.grade].substring(0, 2) + "&l" + pet.label, lore.toArray(new String[0]));
                ItemMeta m = icon.getItemMeta();
                m.setCustomModelData(pet.cmd());
                icon.setItemMeta(m);
            } else icon = Gui.button(Material.GRAY_DYE, "&8??? " + GRADE[pet.grade].substring(0, 2) + "(" + pet.label + ")", lore.toArray(new String[0]));
            g.set(slot, icon, e -> {
                if (!own) return;
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
        lore.add("&7중복 펫은 비용의 " + (int) (plugin.getConfig().getDouble("pets.duplicate-refund", 0.2) * 100) + "% 환급");
        lore.add("&e▶ 클릭하여 뽑기");
        g.set(49, Gui.button(Material.EGG, "&6&l펫 뽑기", lore.toArray(new String[0])), e -> draw(p));
        g.set(45, Gui.button(Material.BARRIER, "&c펫 넣기"), e -> { setActive(p, null); open(p); });
        int owned = 0;
        for (Pet pet : Pet.values()) if (owns(d, pet)) owned++;
        g.set(4, Gui.button(Material.BOOK, "&e&l펫 도감 &f" + owned + " / " + Pet.values().length,
                "&7모은 펫: " + (owned * 100 / Pet.values().length) + "%", "&7얻지 못한 펫의 능력치는 가려집니다"));
        if (plugin.mounts() != null)
            g.set(53, Gui.button(Material.SADDLE, "&6탈것 도감 보기", "&e▶ 클릭"), e -> plugin.mounts().openCollection(p));
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
            } else d.teleport(want);
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
        double bob = Math.sin((tick + p.getEntityId() * 7) / 9.0) * 0.12;
        Location out = l.clone().add(x, 1.25 + bob, z);
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
            float sc = (float) plugin.getConfig().getDouble("pets.scale", 0.6);
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
