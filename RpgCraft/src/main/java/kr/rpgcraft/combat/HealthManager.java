package kr.rpgcraft.combat;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.mob.MobManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * 가상 체력 시스템. 대규모 RPG처럼 수천~수만 단위 체력을 쓰기 위해
 * 실제 체력은 데이터로 관리하고 바닐라 하트는 비율만 표시한다.
 */
public class HealthManager {
    private final RpgCraft plugin;

    public HealthManager(RpgCraft plugin) {
        this.plugin = plugin;
    }

    public double max(LivingEntity e) {
        if (e instanceof Player p) return plugin.data().get(p).stats.maxHp;
        return plugin.mobs().state(e).maxHp;
    }

    public double cur(LivingEntity e) {
        if (e instanceof Player p) {
            PlayerData d = plugin.data().get(p);
            if (d.hp < 0) d.hp = d.stats.maxHp;
            return d.hp;
        }
        return plugin.mobs().state(e).hp;
    }

    public void set(LivingEntity e, double v) {
        v = Math.max(0, Math.min(max(e), v));
        if (e instanceof Player p) plugin.data().get(p).hp = v;
        else {
            MobManager.MobState s = plugin.mobs().state(e);
            if (v < s.hp) s.shownUntil = System.currentTimeMillis() + 6000;
            s.hp = v;
            plugin.mobs().updateName(e, s);
        }
        sync(e);
    }

    public void heal(LivingEntity e, double amount) {
        if (amount <= 0 || e.isDead()) return;
        set(e, cur(e) + amount);
    }

    public void healPercent(LivingEntity e, double pct) {
        heal(e, max(e) * pct / 100.0);
    }

    /** @return 치명상이면 true */
    public boolean damage(LivingEntity e, double amount, Player source) {
        if (plugin.dummies() != null && plugin.dummies().isDummy(e)) {   // 허수아비: 기록만 하고 죽지 않음
            plugin.dummies().record(e, amount);
            return false;
        }
        double before = cur(e);
        double after = before - amount;
        if (!(e instanceof Player)) {
            MobManager.MobState s = plugin.mobs().state(e);
            if (source != null) s.contrib.merge(source.getUniqueId(), Math.min(amount, before), Double::sum);
            if (after <= 0 && s.bossId != null && plugin.bosses().tryAwaken(e, s)) return false;
        }
        if (after <= 0 && e instanceof Player p && plugin.legendary() != null && plugin.legendary().tryImmortal(p)) {
            set(e, max(e) * 0.3);
            return false;
        }
        if (after <= 0 && e instanceof Player wp && plugin.events() != null && plugin.events().tryWaveRevive(wp)) {
            set(e, max(e) * 0.5);   // 필드 웨이브: 1회 부활
            return false;
        }
        if (after <= 0) {
            set(e, 0);
            return true;
        }
        set(e, after);
        return false;
    }

    public void sync(LivingEntity e) {
        if (e.isDead() || !e.isValid() && !(e instanceof Player)) return;
        AttributeInstance a = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (a == null) return;
        if (e instanceof Player && a.getBaseValue() != 20) a.setBaseValue(20);
        double vmax = a.getValue();
        double ratio = cur(e) / Math.max(1, max(e));
        double vh = Math.max(0.5, Math.min(vmax, vmax * ratio));
        if (Math.abs(e.getHealth() - vh) > 0.01) e.setHealth(vh);
    }
}
