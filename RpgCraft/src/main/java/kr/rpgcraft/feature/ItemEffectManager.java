package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.item.ItemLore;
import kr.rpgcraft.item.ItemTemplate;
import kr.rpgcraft.util.Fx;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 아이템 특수 효과 (신화 유물 전용) + 강화 완성(+10) 무기의 타격 연출.
 * CombatService 의 afterHit / defend 에서 호출된다.
 */
public class ItemEffectManager {
    public enum Effect {
        JUDGEMENT("단죄", "크리티컬 적중 시 공격력 40%의 추가 피해 (방어 무시)"),
        ABYSS("심연의 송곳니", "적중 시 20% 확률로 3초간 출혈 (초당 공격력 15%)"),
        ECLIPSE("일식", "5번째 근접 적중마다 주변 4칸의 적에게 공격력 150% 원형 참격"),
        OATH("불굴의 서약", "피격 시 15% 확률로 받는 피해 50% 감소, 공격자에게 30% 반사");

        public final String label, desc;

        Effect(String label, String desc) {
            this.label = label;
            this.desc = desc;
        }

        public static Effect find(String s) {
            try {
                return s == null ? null : valueOf(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private final RpgCraft plugin;
    private final Map<UUID, Integer> eclipseHits = new HashMap<>();

    public ItemEffectManager(RpgCraft plugin) {
        this.plugin = plugin;
    }

    private Effect effectOf(ItemStack it) {
        ItemTemplate t = ItemData.template(it);
        return t == null ? null : Effect.find(t.effect);
    }

    /** 공격자 적중 후 */
    public void onHit(Player p, LivingEntity victim, double dealt, boolean crit, boolean melee) {
        if (dealt <= 0 || !plugin.data().get(p).stats.weaponOk) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        double atk = plugin.data().get(p).stats.attack;
        if (melee && ItemData.enh(hand) >= ItemLore.STAGE_PERFECT) {
            Color c = Color.fromRGB(ItemData.grade(hand).rgb);
            Fx.circle(victim.getLocation().add(0, victim.getHeight() * 0.5, 0), 0.7, 8, c, 1.0f);
        }
        Effect fx = effectOf(hand);
        if (fx == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (fx) {
            case JUDGEMENT -> {
                if (!crit || !melee) return;
                double extra = atk * 0.4;
                plugin.combat().applyDamage(victim, extra, p, p, true);
                Location l = victim.getLocation();
                for (double y = 0; y < 3.2; y += 0.4) Fx.dust(l.clone().add(0, y, 0), Color.fromRGB(0xCFE6FF), 1.4f);
                victim.getWorld().spawnParticle(Particle.END_ROD, l.add(0, 1, 0), 8, 0.2, 0.6, 0.2, 0.05);
                victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.35f, 1.8f);
                Text.actionBar(p, "&b&l단죄! &f+" + Text.num(extra));
            }
            case ABYSS -> {
                if (r.nextDouble() >= 0.2) return;
                bleed(victim, p, atk * 0.15, 3);
                Text.actionBar(p, "&3심연의 송곳니 &7- 출혈");
            }
            case ECLIPSE -> {
                if (!melee) return;
                int n = eclipseHits.merge(p.getUniqueId(), 1, Integer::sum);
                if (n < 5) return;
                eclipseHits.put(p.getUniqueId(), 0);
                Location c = p.getLocation();
                Fx.shockwave(plugin, c, 4, Color.fromRGB(0xFFB300));
                Fx.circle(c.clone().add(0, 1, 0), 3.5, 28, Color.fromRGB(0x1A1A1A), 1.6f);
                p.getWorld().playSound(c, Sound.ENTITY_WITHER_SHOOT, 0.5f, 0.6f);
                for (Entity e : p.getNearbyEntities(4, 2.5, 4)) {
                    if (e instanceof LivingEntity le && plugin.combat().isEnemy(p, le)) plugin.combat().dealSkillDamage(p, le, atk * 1.5, false);
                }
                Text.actionBar(p, "&6&l일식!");
            }
            default -> { }
        }
    }

    /** 피격 대미지 보정 (방패 효과) */
    public double onDefend(Player vp, Entity source, double amount) {
        Effect fx = effectOf(vp.getInventory().getItemInMainHand());
        if (fx != Effect.OATH || !plugin.data().get(vp).stats.weaponOk || ThreadLocalRandom.current().nextDouble() >= 0.15) return amount;
        double reduced = amount * 0.5;
        if (source instanceof LivingEntity le && !le.equals(vp)) {
            double reflect = amount * 0.3;
            Bukkit.getScheduler().runTask(plugin, () -> plugin.combat().applyDamage(le, reflect, vp, vp, false));
        }
        Fx.sphere(vp.getLocation().add(0, 1, 0), 1.2, 30, Color.fromRGB(0xFFD46B), 1.0f);
        vp.getWorld().playSound(vp.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.5f);
        Text.actionBar(vp, "&e&l불굴의 서약! &7피해 반감 · 반사");
        return reduced;
    }

    private void bleed(LivingEntity victim, Player src, double perSec, int seconds) {
        new BukkitRunnable() {
            int n = 0;

            @Override
            public void run() {
                if (n++ >= seconds || victim.isDead() || !victim.isValid()) {
                    cancel();
                    return;
                }
                Location l = victim.getLocation().add(0, victim.getHeight() * 0.6, 0);
                for (int i = 0; i < 5; i++)
                    Fx.dust(l.clone().add(ThreadLocalRandom.current().nextDouble(-0.3, 0.3), ThreadLocalRandom.current().nextDouble(-0.4, 0.4),
                            ThreadLocalRandom.current().nextDouble(-0.3, 0.3)), i % 2 == 0 ? Color.fromRGB(0x8B0000) : Color.fromRGB(0x53FFD8), 1.0f);
                plugin.combat().applyDamage(victim, perSec, src, src, false);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void forget(UUID id) {
        eclipseHits.remove(id);
    }
}
