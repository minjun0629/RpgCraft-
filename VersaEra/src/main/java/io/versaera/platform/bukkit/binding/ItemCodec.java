package io.versaera.platform.bukkit.binding;

import io.versaera.application.ItemTypeRegistry;
import io.versaera.domain.item.ItemInstance;
import io.versaera.domain.item.ItemType;
import io.versaera.domain.item.Quality;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 아이템 ↔ ItemStack. PDC 에는 식별용 값만 넣는다 (instance id · 종류 · 묶음 품질).
 * <b>PDC 는 보안 근거가 아니다</b> — 고유 아이템은 InventoryGuard 가 DB 와 대조하고, 묶음 품질은 50 단위로만 쓰며 서버가 다시 확인한다.
 */
public final class ItemCodec {
    private final NamespacedKey idKey, typeKey, qualityKey;
    private final ItemTypeRegistry types;

    public ItemCodec(Plugin plugin, ItemTypeRegistry types) {
        this.idKey = new NamespacedKey(plugin, "item_id");
        this.typeKey = new NamespacedKey(plugin, "type");
        this.qualityKey = new NamespacedKey(plugin, "quality");
        this.types = types;
    }

    private static Material material(ItemType t) {
        Material m = Material.matchMaterial(t.material());
        return m == null ? Material.PAPER : m;
    }

    public ItemStack unique(ItemInstance it) {
        ItemType t = types.get(it.typeId());
        ItemStack s = new ItemStack(material(t));
        ItemMeta m = s.getItemMeta();
        m.setDisplayName(Ui.c(Ui.gradeColor(it.quality()) + (it.props().containsKey("title") ? it.props().get("title") : t.name())));
        List<String> lore = new ArrayList<>();
        lore.add(Ui.c("&7" + Quality.gradeName(it.quality()) + " · " + it.quality() / 10));
        for (var e : t.stats().entrySet())
            lore.add(Ui.c("&f" + statName(e.getKey()) + " " + Math.round(e.getValue() * Quality.statMultiplier(it.quality()))));
        if (it.maxDurability() > 0) lore.add(Ui.c("&7내구 " + it.durability() + "/" + it.maxDurability()));
        if (it.creatorName() != null) lore.add(Ui.c("&8" + it.creatorName()));
        m.setLore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        m.setUnbreakable(true);   // 내구도는 서버가 관리 (바닐라 내구도로 부서지지 않게)
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(idKey, PersistentDataType.STRING, it.id());
        pdc.set(typeKey, PersistentDataType.STRING, it.typeId());
        s.setItemMeta(m);
        return s;
    }

    public static int bucket(int quality) {
        return Math.max(0, Math.min(1000, Math.round(quality / 50f) * 50));
    }

    public ItemStack bulk(String typeId, int quality, int amount) {
        ItemType t = types.get(typeId);
        int q = bucket(quality);
        ItemStack s = new ItemStack(material(t), Math.max(1, Math.min(64, amount)));
        ItemMeta m = s.getItemMeta();
        m.setDisplayName(Ui.c(Ui.gradeColor(q) + t.name()));
        m.setLore(List.of(Ui.c("&7" + Quality.gradeName(q) + " · " + q / 10)));
        m.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, typeId);
        m.getPersistentDataContainer().set(qualityKey, PersistentDataType.INTEGER, q);
        s.setItemMeta(m);
        return s;
    }

    private static String statName(String k) {
        return switch (k) {
            case "attack" -> "공격력";
            case "defense" -> "방어력";
            default -> k;
        };
    }

    public String instanceId(ItemStack s) {
        if (s == null || !s.hasItemMeta()) return null;
        return s.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public String typeId(ItemStack s) {
        if (s == null || !s.hasItemMeta()) return null;
        String t = s.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return t != null && types.has(t) ? t : null;
    }

    /** 묶음 품질 (없거나 범위 밖이면 0 — 위조된 값은 가장 낮게 본다) */
    public int bulkQuality(ItemStack s) {
        if (s == null || !s.hasItemMeta()) return 0;
        Integer q = s.getItemMeta().getPersistentDataContainer().get(qualityKey, PersistentDataType.INTEGER);
        return q == null || q < 0 || q > 1000 || q % 50 != 0 ? 0 : q;
    }

    public ItemTypeRegistry types() {
        return types;
    }
}
