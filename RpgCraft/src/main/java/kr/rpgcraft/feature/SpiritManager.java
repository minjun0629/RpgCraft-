package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.guild.Guild;
import kr.rpgcraft.item.*;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 사신수 / 사흉수 시스템.
 * 기운 파편 5개 → 기운 뽑기 결정 → 우클릭 30% 확률로 기운 → 기운 5개 + 유니크 무기 = 사신수 무기.
 * 사신수 무기는 우클릭 전용 스킬을 가진다 (스킬 수치는 자체 설계).
 */
public class SpiritManager implements Listener {
    private static class Field {
        Location center;
        UUID owner;
        long until;
        double radius;
    }

    private static final String[][] BEASTS = {
            // 원소, 기운 이름, 사신수 id, 무기 종류
            {"wind", "바람", "qinglong", "SWORD"}, {"earth", "땅", "baihu", "AXE"},
            {"fire", "불", "zhuque", "DAGGER"}, {"nature", "자연", "xuanwu", "SPEAR"}};
    private static final String[] FIENDS = {"hundun", "taotie", "taowu", "qiongqi"};

    private final RpgCraft plugin;
    private final List<Field> fields = new ArrayList<>();

    public SpiritManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::fieldTick, 20L, 20L);
    }

    public String skillLabel(String skill) {
        return switch (skill) {
            case "QINGLONG" -> "청룡승천 - 전방 8칸 돌진, 경로의 적에게 공격력 300% (15초)";
            case "BAIHU" -> "백호격 - 도약 후 내려찍어 5칸 내 적에게 공격력 400% (12초)";
            case "ZHUQUE" -> "주작염 - 6칸 불꽃 폭발 공격력 250% + 4초 화상 (15초)";
            case "XUANWU" -> "현무진 - 8초간 결계: 아군 받는 피해 40% 감소, 적 초당 공격력 50% (25초)";
            default -> skill;
        };
    }

    public boolean inXuanwuField(Player p) {
        long now = System.currentTimeMillis();
        Guild g = plugin.guilds().of(p.getUniqueId());
        for (Field f : fields) {
            if (f.until < now || !f.center.getWorld().equals(p.getWorld()) || f.center.distanceSquared(p.getLocation()) > f.radius * f.radius) continue;
            if (f.owner.equals(p.getUniqueId())) return true;
            if (g != null && g.members.contains(f.owner)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 우클릭
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack it = p.getInventory().getItemInMainHand();
        ItemTemplate t = ItemData.template(it);
        if (t == null) return;
        if (t.category == Category.CRYSTAL) {
            e.setCancelled(true);
            openCrystal(p, it, t);
            return;
        }
        if (t.skill != null && t.category == Category.WEAPON && p.isSneaking()) {   // 사신수 전용 기술은 쉬프트+우클릭 (우클릭은 절기)
            e.setCancelled(true);
            useSkill(p, t.skill);
        }
    }

    private void openCrystal(Player p, ItemStack it, ItemTemplate t) {
        String el = t.id.substring("crystal_".length());
        it.setAmount(it.getAmount() - 1);
        double chance = plugin.getConfig().getDouble("spirit.crystal-chance", 0.3);
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            ItemStack ess = plugin.items().create("essence_" + el, 1);
            give(p, ess.clone());
            plugin.visuals().obtain(p, ess);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            p.getWorld().spawnParticle(Particle.TOTEM, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.3);
            kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&d" + Text.name(p) + "&f님이 &d" + plugin.items().get("essence_" + el).name + "&f을(를) 획득했습니다!"));
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 0.6f);
            Text.msg(p, "&7결정이 부서졌지만 아무것도 얻지 못했습니다...");
        }
    }

    public void useSkill(Player p, String skill) {
        PlayerData d = plugin.data().get(p);
        if (!d.stats.weaponOk) {
            Text.actionBar(p, "&c무기 요구 조건 미충족: " + d.stats.weaponProblem);
            return;
        }
        String key = "skill_" + skill;
        if (d.onCooldown(key)) {
            Text.actionBar(p, "&c스킬 쿨타임 " + (d.remaining(key) / 1000 + 1) + "초");
            return;
        }
        double atk = d.stats.attack;
        World w = p.getWorld();
        switch (skill) {
            case "QINGLONG" -> {
                d.cooldown(key, 15_000);
                Vector dir = p.getLocation().getDirection().setY(0).normalize();
                Location start = p.getLocation().clone();
                p.setVelocity(dir.clone().multiply(2.2).setY(0.2));
                w.playSound(start, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 1.4f);
                Set<UUID> hit = new HashSet<>();
                new BukkitRunnable() {
                    int n = 0;

                    @Override
                    public void run() {
                        if (n++ > 8 || !p.isOnline()) {
                            cancel();
                            return;
                        }
                        w.spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 3, 0.5, 0.3, 0.5);
                        w.spawnParticle(Particle.CLOUD, p.getLocation(), 6, 0.3, 0.1, 0.3, 0.02);
                        kr.rpgcraft.util.Fx.circle(p.getLocation().add(0, 1, 0), 1.2, 12, Color.fromRGB(0x3FA9FF), 1.4f);
                        for (Entity en : p.getNearbyEntities(2, 2, 2)) {
                            if (en instanceof LivingEntity le && hit.add(en.getUniqueId()) && plugin.combat().isEnemy(p, le))
                                plugin.combat().dealSkillDamage(p, le, atk * 3, true);
                        }
                    }
                }.runTaskTimer(plugin, 0L, 2L);
            }
            case "BAIHU" -> {
                d.cooldown(key, 12_000);
                p.setVelocity(p.getLocation().getDirection().setY(0).normalize().multiply(0.6).setY(1.1));
                w.playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 1.2f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    p.setVelocity(new Vector(0, -2.5, 0));
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Location c = p.getLocation();
                        w.spawnParticle(Particle.EXPLOSION_LARGE, c, 6, 2, 0.2, 2);
                        w.spawnParticle(Particle.BLOCK_CRACK, c, 80, 2.5, 0.2, 2.5, Material.STONE.createBlockData());
                        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
                        kr.rpgcraft.util.Fx.shockwave(plugin, c, 5, Color.fromRGB(0xEDEDED));
                        for (Entity en : p.getNearbyEntities(5, 3, 5)) {
                            if (en instanceof LivingEntity le && plugin.combat().isEnemy(p, le)) {
                                plugin.combat().dealSkillDamage(p, le, atk * 4, true);
                                le.setVelocity(new Vector(0, 0.7, 0));
                            }
                        }
                    }, 6L);
                }, 12L);
            }
            case "ZHUQUE" -> {
                d.cooldown(key, 15_000);
                Location c = p.getLocation();
                w.playSound(c, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.7f);
                kr.rpgcraft.util.Fx.shockwave(plugin, c, 6, Color.fromRGB(0xFF5A1F));
                for (int r = 1; r <= 6; r++) {
                    int rr = r;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        for (int a = 0; a < 360; a += 12) {
                            double rad = Math.toRadians(a);
                            w.spawnParticle(Particle.FLAME, c.clone().add(Math.cos(rad) * rr, 0.3, Math.sin(rad) * rr), 1, 0, 0.1, 0, 0.01);
                        }
                    }, r);
                }
                for (Entity en : p.getNearbyEntities(6, 3, 6)) {
                    if (en instanceof LivingEntity le && plugin.combat().isEnemy(p, le)) {
                        plugin.combat().dealSkillDamage(p, le, atk * 2.5, true);
                        plugin.combat().burn(le, p, atk * 0.2, 4);
                    }
                }
            }
            case "XUANWU" -> {
                d.cooldown(key, 25_000);
                Field f = new Field();
                f.center = p.getLocation().clone();
                f.owner = p.getUniqueId();
                f.until = System.currentTimeMillis() + 8_000;
                f.radius = 6;
                fields.add(f);
                w.playSound(f.center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.8f);
                Text.msg(p, "&2현무진 전개! 8초간 아군 피해 감소");
            }
            default -> Text.actionBar(p, "&c알 수 없는 스킬");
        }
    }

    private void fieldTick() {
        long now = System.currentTimeMillis();
        fields.removeIf(f -> f.until < now);
        for (Field f : fields) {
            Player owner = Bukkit.getPlayer(f.owner);
            World w = f.center.getWorld();
            for (int a = 0; a < 360; a += 10) {
                double rad = Math.toRadians(a);
                kr.rpgcraft.util.Fx.dust(f.center.clone().add(Math.cos(rad) * f.radius, 0.2, Math.sin(rad) * f.radius), Color.fromRGB(0x2FBF71), 1.5f);
            }
            if (owner == null) continue;
            double atk = plugin.data().get(owner).stats.attack;
            for (Entity en : w.getNearbyEntities(f.center, f.radius, 3, f.radius)) {
                if (en instanceof LivingEntity le && plugin.combat().isEnemy(owner, le)) plugin.combat().dealSkillDamage(owner, le, atk * 0.5, false);
            }
        }
    }

    // ------------------------------------------------------------------ 조합 GUI
    public void open(Player p) {
        new EssenceGui(p).open(p);
    }

    private void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    /** 인벤토리에서 조건에 맞는 유니크 장비를 찾는다 */
    private ItemStack findUnique(Player p, WeaponClass wc, ArmorSlot slot) {
        for (ItemStack it : p.getInventory().getStorageContents()) {
            ItemTemplate t = ItemData.template(it);
            if (t == null || !t.id.startsWith("trans_")) continue;   // 재료: 초월 장비
            if (wc != null && t.category == Category.WEAPON && (wc == WeaponClass.SPEAR || ItemData.weaponClass(it) == wc)) return it;
            if (slot != null && t.category == Category.ARMOR && ItemData.armorSlot(it) == slot) return it;
        }
        return null;
    }

    /** v5.10.53 기운 조합 창: 바닐라 재질 대신 실제 아이템 (전용 3D 모델) 그대로 보여 줌 */
    private ItemStack show(String id, String name, String... lore) {
        ItemStack it = plugin.items().create(id, 1);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.setDisplayName(Text.c(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(Text.c(s));
        m.setLore(l);
        m.addItemFlags(ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    /** v5.10.68 좌우 대칭: 사신수 무기는 자기 방어구 줄 위쪽 (왼쪽 무리: 0·2 번, 오른쪽 무리: 3·1 번) */
    private static final int[] WEAPON_SLOT = {10, 16, 11, 15};

    private class EssenceGui extends Gui {
        EssenceGui(Player p) {
            super(6, "&d기운 조합 (사신수 / 사흉수)", "spirit");
            EnhanceManager em = plugin.enhance();
            int need = plugin.getConfig().getInt("spirit.shards-per-crystal", 5);
            // 1행: 파편 → 결정
            String[] els = {"fire", "wind", "dark", "nature", "earth"};
            for (int i = 0; i < els.length; i++) {
                String el = els[i];
                ItemTemplate crystal = plugin.items().get("crystal_" + el);
                set(2 + i, show("crystal_" + el, "&d" + crystal.name + " 교환",
                        "&f" + plugin.items().get("shard_" + el).name + " " + em.count(p, "shard_" + el) + "/" + need, "&e클릭하여 교환"), e -> {
                    if (!em.take(p, "shard_" + el, need)) {
                        Text.msg(p, "&c기운 파편이 부족합니다.");
                        return;
                    }
                    give(p, plugin.items().create("crystal_" + el, 1));
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
                    new EssenceGui(p).open(p);
                });
            }
            // 2~3행: 사신수 무기 / 방어구
            int essW = plugin.getConfig().getInt("spirit.essence-per-weapon", 5);
            int essA = plugin.getConfig().getInt("spirit.essence-per-armor", 3);
            for (int b = 0; b < 4; b++) {
                String[] be = BEASTS[b];
                String essence = "essence_" + be[0];
                WeaponClass wc = WeaponClass.valueOf(be[3]);
                ItemTemplate wt = plugin.items().get("spirit_" + be[2]);
                set(WEAPON_SLOT[b], show(wt.id, "&c" + wt.name, "&f" + plugin.items().get(essence).name + " " + em.count(p, essence) + "/" + essW,
                        "&f+ 초월 " + (wc == WeaponClass.SPEAR ? "무기(종류 무관)" : wc.label) + " 1개 " + (findUnique(p, wc, null) != null ? "&a✔" : "&c✘"),
                        "", "&d" + skillLabel(wt.skill), "&e클릭하여 조합"), e -> {
                    ItemStack base = findUnique(p, wc, null);
                    if (base == null || em.count(p, essence) < essW) {
                        Text.msg(p, "&c재료가 부족합니다.");
                        return;
                    }
                    em.take(p, essence, essW);
                    base.setAmount(0);
                    ItemStack out = plugin.items().create(wt.id, 1);
                    give(p, out.clone());
                    plugin.visuals().obtain(p, out);
                    kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&c&l" + Text.name(p) + "&f님이 사신수 무기 &c" + wt.name + "&f을(를) 탄생시켰습니다!"));
                    for (Player o : Bukkit.getOnlinePlayers()) o.playSound(o.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
                    p.closeInventory();
                });
                for (int s = 0; s < 4; s++) {
                    ArmorSlot slot = ArmorSlot.values()[s];
                    ItemTemplate at = plugin.items().get("spirit_" + be[2] + "_a" + s);
                    set((b < 2 ? 18 : 27) + (b % 2 == 0 ? 0 : 5) + s, show(at.id, "&6" + at.name,
                            "&f" + plugin.items().get(essence).name + " " + em.count(p, essence) + "/" + essA,
                            "&f+ 초월 " + slot.label + " 1개 " + (findUnique(p, null, slot) != null ? "&a✔" : "&c✘"), "&e클릭하여 조합"), e -> {
                        ItemStack base = findUnique(p, null, slot);
                        if (base == null || em.count(p, essence) < essA) {
                            Text.msg(p, "&c재료가 부족합니다.");
                            return;
                        }
                        em.take(p, essence, essA);
                        base.setAmount(0);
                        ItemStack out = plugin.items().create(at.id, 1);
                        give(p, out.clone());
                        plugin.visuals().obtain(p, out);
                        Text.msg(p, "&6" + at.name + " 조합 완료!");
                        p.closeInventory();
                    });
                }
            }
            // 사흉수 갑주 (어둠의 기운)
            for (int s = 0; s < 4; s++) {
                ArmorSlot slot = ArmorSlot.values()[s];
                ItemTemplate ft = plugin.items().get("fiend_" + FIENDS[s]);
                set(s < 2 ? 38 + s : 39 + s, show(ft.id, "&8&l" + ft.name, "&f어둠(흑룡)의 기운 " + em.count(p, "essence_dark") + "/" + essA,
                        "&f+ 초월 " + slot.label + " 1개 " + (findUnique(p, null, slot) != null ? "&a✔" : "&c✘"), "&e클릭하여 조합"), e -> {
                    ItemStack base = findUnique(p, null, slot);
                    if (base == null || em.count(p, "essence_dark") < essA) {
                        Text.msg(p, "&c재료가 부족합니다.");
                        return;
                    }
                    em.take(p, "essence_dark", essA);
                    base.setAmount(0);
                    ItemStack out = plugin.items().create(ft.id, 1);
                    give(p, out.clone());
                    plugin.visuals().obtain(p, out);
                    kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&8&l" + Text.name(p) + "&7님이 사흉수 갑주 " + ft.name + "을(를) 손에 넣었습니다..."));
                    p.closeInventory();
                });
            }
            set(40, show("essence_dark", "&8&l사흉수 갑주", "&7어둠(흑룡)의 기운으로 조합", "&7양옆 4부위 (투구 · 갑옷 · 각반 · 신발)"), e -> {});
            set(49, button(Material.BOOK, "&e기운 안내", "&7월드보스(사막의 악몽/시포니아/카인)와 채집에서 기운 파편 획득",
                    "&7파편 " + need + "개 → 기운 뽑기 결정 → 우클릭 시 확률적으로 기운 획득",
                    "&71행: 결정 교환 | 2행: 사신수 무기 | 3~4행: 사신수 방어구 | 5행: 사흉수 갑주"));
            fill(0, 53);
        }
    }
}
