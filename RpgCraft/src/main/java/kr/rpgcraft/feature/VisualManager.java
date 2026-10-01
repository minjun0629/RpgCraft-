package kr.rpgcraft.feature;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.data.Setting;
import kr.rpgcraft.item.Category;
import kr.rpgcraft.item.Grade;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.item.ItemLore;
import kr.rpgcraft.util.Fx;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 아이템 등급 연출 전담.
 * - 바닥 드롭: 등급 색 발광(팀 색) + 이름표, 유니크 이상 빛기둥, 신화는 도는 광점 (근처에 플레이어가 있을 때만, 최대 개수 제한)
 * - 획득 연출: 등급별 5단계 차등 (신화도 1~2초 안에 끝남)
 * - 손에 든 +7/+10 무기와 신화 무기의 은은한 오라 (1초 간격, 소량)
 * - TextDisplay 대미지 숫자, 레벨업 연출
 */
public class VisualManager implements Listener {
    private static final int MAX_TRACKED = 80;
    private static final Map<Grade, ChatColor> TEAM_COLOR = Map.of(Grade.RARE, ChatColor.GREEN, Grade.UNIQUE, ChatColor.YELLOW,
            Grade.LEGEND, ChatColor.RED, Grade.MYTHIC, ChatColor.LIGHT_PURPLE);

    private final RpgCraft plugin;
    private final Map<UUID, Grade> drops = new LinkedHashMap<>();
    private final Map<UUID, Long> expire = new HashMap<>();
    private int tick;

    public VisualManager(RpgCraft plugin) {
        this.plugin = plugin;
        registerTeams(Bukkit.getScoreboardManager().getMainScoreboard());
        Bukkit.getScheduler().runTaskTimer(plugin, this::dropTick, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::auraTick, 20L, 20L);
    }

    // ------------------------------------------------------------------ 등급 발광 팀
    private static String teamName(Grade g) {
        return "rpgcraft_g_" + g.name().toLowerCase(Locale.ROOT);
    }

    public void registerTeams(Scoreboard sb) {
        for (Map.Entry<Grade, ChatColor> en : TEAM_COLOR.entrySet()) {
            Team t = sb.getTeam(teamName(en.getKey()));
            if (t == null) t = sb.registerNewTeam(teamName(en.getKey()));
            t.setColor(en.getValue());
        }
        for (Map.Entry<UUID, Grade> d : drops.entrySet()) {
            Team t = sb.getTeam(teamName(d.getValue()));
            if (t != null) t.addEntry(d.getKey().toString());
        }
    }

    private List<Scoreboard> boards() {
        List<Scoreboard> list = new ArrayList<>(plugin.hud() == null ? List.of() : plugin.hud().boards());
        list.add(Bukkit.getScoreboardManager().getMainScoreboard());
        return list;
    }

    private void glow(Item item, Grade g, boolean on) {
        for (Scoreboard sb : boards()) {
            Team t = sb.getTeam(teamName(g));
            if (t == null) continue;
            if (on) t.addEntry(item.getUniqueId().toString());
            else t.removeEntry(item.getUniqueId().toString());
        }
    }

    private void untrack(UUID id) {
        Grade g = drops.remove(id);
        expire.remove(id);
        if (g == null) return;
        for (Scoreboard sb : boards()) {
            Team t = sb.getTeam(teamName(g));
            if (t != null) t.removeEntry(id.toString());
        }
    }

    // ------------------------------------------------------------------ 대미지 숫자
    public void damageNumber(LivingEntity victim, double amount, boolean crit, boolean heal) {
        damageNumber(victim, amount, crit, heal, null);
    }

    /** attacker: 대미지를 준 플레이어 (v5.10.35 그 사람의 대미지 스킨으로) */
    public void damageNumber(LivingEntity victim, double amount, boolean crit, boolean heal, org.bukkit.entity.Player attacker) {
        if (!plugin.getConfig().getBoolean("combat.damage-indicator", true) || amount <= 0) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location l = victim.getLocation().add(r.nextDouble(-0.6, 0.6), victim.getHeight() + 0.1, r.nextDouble(-0.6, 0.6));
        String skinned = heal || plugin.damageSkins() == null ? null : plugin.damageSkins().render(attacker, amount, crit);
        String plain = heal ? "&a+" + Text.num(amount)
                : crit ? "&6&l✦ " + Text.num(amount) + " ✦"
                : amount >= 10000 ? "&c&l" + Text.num(amount) : "&c" + Text.num(amount);
        float scale = crit ? 1.6f : heal ? 0.9f : 1.1f;
        // v5.10.42 스킨 숫자는 리소스팩을 적용한 사람에게만, 나머지에게는 기본 숫자 (팩 필수 모드가 아니어도 스킨이 보이도록)
        TextDisplay main = spawnNumber(l, skinned != null ? skinned : plain, scale);
        TextDisplay alt = skinned != null ? spawnNumber(l, plain, scale) : null;
        for (Player p : victim.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(l) > 48 * 48) continue;
            if (!Setting.INDICATOR.get(plugin.data().get(p))) {
                p.hideEntity(plugin, main);
                if (alt != null) p.hideEntity(plugin, alt);
            } else if (alt != null) {
                if (plugin.damageSkins().canSee(p)) p.hideEntity(plugin, alt);
                else p.hideEntity(plugin, main);
            }
        }
        animateNumber(main, scale);
        if (alt != null) animateNumber(alt, scale);
        if (crit) {
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, victim.getHeight() * 0.6, 0), 18, 0.3, 0.4, 0.3, 0.35);
            Fx.circle(victim.getLocation().add(0, victim.getHeight() * 0.6, 0), 0.8, 14, Color.fromRGB(0xFFC83D), 1.1f);
        }
    }

    private TextDisplay spawnNumber(Location l, String text, float scale) {
        return l.getWorld().spawn(l, TextDisplay.class, d -> {
            d.setText(Text.c(text));
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setSeeThrough(true);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale * 0.3f), new AxisAngle4f()));
            d.getPersistentDataContainer().set(Keys.INDICATOR, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void animateNumber(TextDisplay td, float scale) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!td.isValid()) return;
            td.setInterpolationDelay(0);
            td.setInterpolationDuration(4);
            td.setTransformation(new Transformation(new Vector3f(0, 0.25f, 0), new AxisAngle4f(), new Vector3f(scale), new AxisAngle4f()));
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!td.isValid()) return;
            td.setInterpolationDelay(0);
            td.setInterpolationDuration(14);
            td.setTransformation(new Transformation(new Vector3f(0, 1.1f, 0), new AxisAngle4f(), new Vector3f(scale * 0.4f), new AxisAngle4f()));
        }, 6L);
        Bukkit.getScheduler().runTaskLater(plugin, td::remove, 22L);
    }

    // ------------------------------------------------------------------ 드롭
    private Grade gradeOf(ItemStack it) {
        if (ItemData.id(it) == null) return null;
        if (ItemData.category(it) == Category.ESSENCE) return Grade.LEGEND;
        return ItemData.grade(it);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        Item item = e.getEntity();
        ItemStack it = item.getItemStack();
        Grade g = gradeOf(it);
        if (g == null || g == Grade.NORMAL) return;
        String name = it.hasItemMeta() && it.getItemMeta().hasDisplayName() ? it.getItemMeta().getDisplayName() : it.getType().name();
        item.setCustomName(Text.c(g.color + g.icon + " ") + name + (it.getAmount() > 1 ? Text.c(" &7x" + it.getAmount()) : ""));
        item.setCustomNameVisible(true);
        if (drops.size() >= MAX_TRACKED) return; // 발광/빛기둥은 개수 제한 (이름표는 유지)
        if (plugin.getConfig().getBoolean("visual.item-glow", false)) item.setGlowing(true);
        drops.put(item.getUniqueId(), g);
        expire.put(item.getUniqueId(), System.currentTimeMillis() + 120_000);
        glow(item, g, true);
        World w = item.getWorld();
        Location l = item.getLocation();
        switch (g) {
            case UNIQUE -> w.playSound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
            case LEGEND -> {
                w.playSound(l, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
                Fx.shockwave(plugin, l, 2.5, Color.fromRGB(g.rgb));
            }
            case MYTHIC -> {
                w.playSound(l, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.4f);
                w.playSound(l, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 0.8f);
                Fx.shockwave(plugin, l, 3.5, Color.fromRGB(g.rgb));
            }
            default -> { }
        }
    }

    /** 0.5초마다: 근처에 플레이어가 있는 드롭만 연출 */
    private void dropTick() {
        tick++;
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(drops.keySet())) {
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof Item item) || !item.isValid() || expire.getOrDefault(id, 0L) < now) {
                untrack(id);
                continue;
            }
            Grade g = drops.get(id);
            if (g.ordinal() < Grade.UNIQUE.ordinal() || !playerNear(item.getLocation(), 40)) continue;
            Location base = item.getLocation();
            Color c = Color.fromRGB(g.rgb);
            int height = g == Grade.UNIQUE ? 3 : g == Grade.LEGEND ? 5 : 7;
            if (g == Grade.UNIQUE && tick % 2 == 1) continue;
            for (double y = 0.4; y < height; y += 0.45) Fx.dust(base.clone().add(0, y, 0), c, 0.9f);
            if (g.atLeast(Grade.LEGEND) && tick % 2 == 0) Fx.circle(base.clone().add(0, 0.1, 0), 0.7, 10, c, 1.1f);
            if (g == Grade.MYTHIC) {
                double a = tick * 0.6;
                for (int k = 0; k < 3; k++) {
                    double ang = a + k * Math.PI * 2 / 3;
                    Fx.dust(base.clone().add(Math.cos(ang) * 0.9, 0.6 + 0.3 * Math.sin(a + k), Math.sin(ang) * 0.9), Color.WHITE, 1.2f);
                }
            }
        }
    }

    private static boolean playerNear(Location l, double r) {
        for (Player p : l.getWorld().getPlayers()) if (p.getLocation().distanceSquared(l) <= r * r) return true;
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent e) {
        untrack(e.getEntity().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMerge(ItemMergeEvent e) {
        untrack(e.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        untrack(e.getItem().getUniqueId());
        if (e.getEntity() instanceof Player p) obtain(p, e.getItem().getItemStack());
    }

    // ------------------------------------------------------------------ 획득 연출
    /** 보스 보상/제작/조합/줍기 등 모든 획득 지점에서 호출 */
    public void obtain(Player p, ItemStack it) {
        plugin.data().get(p).markSeen(ItemData.id(it));
        Grade g = gradeOf(it);
        if (g == null || g == Grade.NORMAL) return;
        PlayerData d = plugin.data().get(p);
        boolean notice = Setting.LOOT_NOTICE.get(d);
        String name = it.hasItemMeta() && it.getItemMeta().hasDisplayName() ? it.getItemMeta().getDisplayName() : it.getType().name();
        Location l = p.getLocation();
        World w = p.getWorld();
        Color c = Color.fromRGB(g.rgb);
        switch (g) {
            case RARE -> {
                p.playSound(l, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.7f);
                if (notice) {
                    Text.actionBar(p, g.color + g.icon + " " + Text.strip(name) + " &7획득");
                    d.actionBarLock = System.currentTimeMillis() + 1500;
                }
            }
            case UNIQUE -> {
                p.playSound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
                p.playSound(l, Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.9f);
                Fx.circle(l.clone().add(0, 0.2, 0), 1.0, 16, c, 1.2f);
                if (notice) p.sendTitle("", Text.c("&e✦ " + Text.strip(name) + " ✦"), 3, 25, 8);
            }
            case LEGEND -> {
                p.playSound(l, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                p.playSound(l, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.5f);
                Fx.shockwave(plugin, l, 3, c);
                w.spawnParticle(Particle.TOTEM, l.clone().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.25);
                if (notice) p.sendTitle(Text.c("&c&l❖ 레전드 획득 ❖"), Text.c(name), 3, 30, 10);
                Text.announce(Text.PREFIX + Text.c("&c&l" + Text.name(p) + "&f님이 레전드 " + name + "&f을(를) 손에 넣었습니다!"));
            }
            case MYTHIC -> {
                p.playSound(l, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
                p.playSound(l, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.6f);
                p.playSound(l, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
                Fx.helix(plugin, p, 2.6, 1.0, 24, c, Color.WHITE);
                Fx.shockwave(plugin, l, 4.5, c);
                Fx.sphere(l.clone().add(0, 1, 0), 1.6, 50, c, 1.2f);
                if (notice) p.sendTitle(Text.c("&d&l✧ 신화 ✧"), Text.c(name), 3, 35, 12);
                Text.announce(Text.PREFIX + Text.c("&d&l✧ " + Text.name(p) + "&f님이 신화 유물 " + name + "&f을(를) 깨웠습니다! ✧"));
                for (Player o : Bukkit.getOnlinePlayers())
                    if (!o.equals(p) && Setting.ANNOUNCE.get(plugin.data().get(o))) o.playSound(o.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 0.8f);
            }
            default -> { }
        }
    }

    /** 강화 +10 달성 */
    public void perfected(Player p, ItemStack it) {
        Color c = Color.fromRGB(ItemData.grade(it).rgb);
        Fx.helix(plugin, p, 2.2, 0.9, 20, Color.fromRGB(0xFFD23F), c);
        Fx.shockwave(plugin, p.getLocation(), 3.5, Color.fromRGB(0xFFD23F));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.5f);
        p.sendTitle(Text.c("&6&l『완성』"), Text.c(it.getItemMeta().getDisplayName()), 3, 35, 10);
    }

    // ------------------------------------------------------------------ 손에 든 무기 오라
    private void auraTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || p.isInvisible()) continue;
            spiritAura(p);
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (ItemData.id(hand) == null || !ItemData.category(hand).isEquipment()) continue;
            int e = ItemData.enh(hand);
            Grade g = ItemData.grade(hand);
            if (e < ItemLore.STAGE_ENGRAVED && g != Grade.MYTHIC) continue;
            Location hl = handLocation(p);
            Color c = Color.fromRGB(g.rgb);
            if (g == Grade.MYTHIC) {
                double a = (System.currentTimeMillis() / 150.0) % (Math.PI * 2);
                for (int k = 0; k < 2; k++)
                    Fx.dust(hl.clone().add(Math.cos(a + k * Math.PI) * 0.35, 0.2, Math.sin(a + k * Math.PI) * 0.35), c, 0.9f);
            }
            if (e >= ItemLore.STAGE_PERFECT) {
                Fx.dust(hl, Color.fromRGB(0xFFD23F), 1.1f);
                p.getWorld().spawnParticle(Particle.END_ROD, hl, 1, 0.1, 0.2, 0.1, 0.005);
            } else if (e >= ItemLore.STAGE_ENGRAVED) {
                Fx.dust(hl, c, 0.8f);
            }
        }
    }

    private static final Map<String, Color> ELEMENT = Map.of("qinglong", Color.fromRGB(0x6BD8FF), "baihu", Color.fromRGB(0xF4F4F4),
            "zhuque", Color.fromRGB(0xFF7A1F), "xuanwu", Color.fromRGB(0x2FBF71), "fiend", Color.fromRGB(0xB01030));

    /** 사신수 무기를 들면 속성 입자, 사신수·사흉수 4부위를 모두 입으면 발밑에 속성 문양 */
    private void spiritAura(Player p) {
        String hid = ItemData.id(p.getInventory().getItemInMainHand());
        if (hid != null && hid.matches("spirit_(qinglong|baihu|zhuque|xuanwu)")) {
            String el = hid.substring(7);
            Location hl = handLocation(p);
            Color c = ELEMENT.get(el);
            double a = (System.currentTimeMillis() / 120.0) % (Math.PI * 2);
            for (int k = 0; k < 3; k++)
                Fx.dust(hl.clone().add(Math.cos(a + k * 2.1) * 0.4, 0.25 + k * 0.12, Math.sin(a + k * 2.1) * 0.4), c, 0.9f);
            switch (el) {
                case "zhuque" -> p.getWorld().spawnParticle(Particle.FLAME, hl, 2, 0.1, 0.15, 0.1, 0.01);
                case "qinglong" -> p.getWorld().spawnParticle(Particle.CLOUD, hl, 1, 0.1, 0.1, 0.1, 0.01);
                case "xuanwu" -> p.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, hl, 1, 0.15, 0.15, 0.15, 0);
                default -> p.getWorld().spawnParticle(Particle.END_ROD, hl, 1, 0.1, 0.1, 0.1, 0.005);
            }
        }
        Map<String, Integer> sets = new HashMap<>();
        for (ItemStack a : p.getInventory().getArmorContents()) {
            String id = ItemData.id(a);
            if (id == null) continue;
            if (id.startsWith("fiend_")) sets.merge("fiend", 1, Integer::sum);
            else if (id.startsWith("spirit_") && id.contains("_a")) sets.merge(id.substring(7, id.indexOf("_a")), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> en : sets.entrySet()) {
            if (en.getValue() < 4 || !ELEMENT.containsKey(en.getKey())) continue;
            Color c = ELEMENT.get(en.getKey());
            Location feet = p.getLocation().add(0, 0.1, 0);
            double rot = (System.currentTimeMillis() / 400.0) % (Math.PI * 2);
            for (int i = 0; i < 6; i++) {
                double ang = rot + i * Math.PI / 3;
                Fx.dust(feet.clone().add(Math.cos(ang) * 0.9, 0, Math.sin(ang) * 0.9), c, 1.1f);
            }
            if (en.getKey().equals("fiend")) p.getWorld().spawnParticle(Particle.SMOKE_NORMAL, feet.add(0, 1, 0), 3, 0.3, 0.5, 0.3, 0.01);
        }
    }

    private static Location handLocation(Player p) {
        Location l = p.getLocation();
        double yaw = Math.toRadians(l.getYaw());
        return l.add(-Math.cos(yaw) * 0.45 - Math.sin(yaw) * 0.25, 1.0, -Math.sin(yaw) * 0.45 + Math.cos(yaw) * 0.25);
    }

    // ------------------------------------------------------------------ 레벨업
    public void levelUp(Player p) { // 전투를 방해하지 않도록 발밑에 짧게
        Fx.circle(p.getLocation().add(0, 0.1, 0), 0.9, 16, Color.fromRGB(0xFFD23F), 1.1f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
    }
}
