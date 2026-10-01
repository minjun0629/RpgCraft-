package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * v5.10.35 대미지 스킨 (메이플스토리 느낌). 시즌 패스에서 스킨 아이템을 얻어 우클릭하면 등록 · 장착,
 * /데미지스킨 에서 가진 스킨으로 바꿈. 내가 준 대미지 숫자를 리소스팩을 적용한 모든 플레이어가 그 스킨으로 본다
 * (v5.10.42 팩 필수 모드가 아니어도 적용 — 팩이 없는 사람에게는 기본 숫자를 따로 보여 줌).
 * 글리프 번호는 tools/dmg_skin.py 와 같아야 함.
 */
public class DamageSkinManager implements Listener, CommandExecutor {
    public enum Skin {
        BASIC("기본", "&c", "빨간 기본 숫자"),
        GOLD("황금", "&6", "번쩍이는 황금 숫자"),
        ICE("얼음", "&b", "얼음 결정이 박힌 숫자"),
        FIRE("불꽃", "&c", "타오르는 불꽃 숫자"),
        ARCANE("마력", "&d", "보랏빛 마력이 흐르는 숫자"),
        CANDY("사탕", "&d", "분홍 줄무늬 사탕 숫자"),
        TOXIC("맹독", "&a", "독이 끓어오르는 숫자"),
        RAINBOW("무지개", "&e", "한 글자마다 다른 색의 무지개 숫자");

        public final String label, color, desc;

        Skin(String label, String color, String desc) {
            this.label = label;
            this.color = color;
            this.desc = desc;
        }

        public String itemId() {
            return "dmg_skin_" + name().toLowerCase();
        }

        /** 이 스킨 글리프 시작 번호 (BASIC 은 없음) */
        int base() {
            return 0xE500 + (ordinal() - 1) * 32;
        }
    }

    private final RpgCraft plugin;

    public DamageSkinManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean owns(PlayerData d, Skin s) {
        return s == Skin.BASIC || d.counter("dskin_own_" + s.name()) > 0;
    }

    public Skin selected(PlayerData d) {
        int i = (int) d.counter("dskin_sel");
        return i > 0 && i < Skin.values().length && owns(d, Skin.values()[i]) ? Skin.values()[i] : Skin.BASIC;
    }

    /** 이 플레이어 화면에 스킨 글자를 보여도 되는지 (리소스팩을 적용한 사람만 — 없으면 네모로 보이므로) */
    public boolean canSee(Player viewer) {
        if (plugin.pack() == null) return false;
        String mode = plugin.getConfig().getString("resourcepack.gui-overlay", "auto");
        if ("false".equalsIgnoreCase(mode)) return false;
        return "true".equalsIgnoreCase(mode) || plugin.pack().overlay() || plugin.pack().hasPack(viewer);
    }

    /** 대미지 숫자 문자열 (공격한 플레이어의 스킨). null 이면 기본 글자로 */
    public String render(Player attacker, double amount, boolean crit) {
        if (plugin.pack() == null) return null;
        if ("false".equalsIgnoreCase(plugin.getConfig().getString("resourcepack.gui-overlay", "auto"))) return null;
        Skin s = attacker == null ? Skin.BASIC : selected(plugin.data().get(attacker));
        String num = Text.num(amount);
        if (s == Skin.BASIC) {   // v5.10.49 기본도 마크에이지 4R 풍 숫자 (tools/num4r.py DMG_N · DMG_C)
            if (!plugin.getConfig().getBoolean("combat.damage-4r-font", true)) return null;
            StringBuilder b = new StringBuilder("§f");
            int base = crit ? 0xE490 : 0xE480;
            for (char c : num.toCharArray()) {
                if (c >= '0' && c <= '9') b.append((char) (base + (c - '0')));
                else if (c == ',') b.append((char) (base + 10));
            }
            return b.toString();
        }
        StringBuilder sb = new StringBuilder("§f");
        int base = s.base() + (crit ? 16 : 0);
        if (crit) sb.append((char) (s.base() + 27));
        for (char c : num.toCharArray()) {
            if (c >= '0' && c <= '9') sb.append((char) (base + (c - '0')));
            else if (c == ',') sb.append((char) (base + 10));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 스킨 아이템 우클릭 → 등록 · 장착
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        ItemStack it = e.getItem();
        String id = ItemData.id(it);
        if (id == null || !id.startsWith("dmg_skin_")) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        Skin s;
        try { s = Skin.valueOf(id.substring(9).toUpperCase()); } catch (IllegalArgumentException ex) { return; }
        if (d.onCooldown("dskin_use")) return;
        d.cooldown("dskin_use", 500);
        boolean had = owns(d, s);
        if (!had) {
            it.setAmount(it.getAmount() - 1);
            d.counters.put("dskin_own_" + s.name(), 1.0);
        }
        d.counters.put("dskin_sel", (double) s.ordinal());
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.6f);
        Text.msg(p, (had ? "&7이미 가진 스킨입니다. " : "&a새 대미지 스킨 등록! ") + s.color + "&l" + s.label + " &f스킨을 장착했습니다. &7(/데미지스킨)");
    }

    // ------------------------------------------------------------------ 창
    @Override
    public boolean onCommand(CommandSender sender, Command c, String l, String[] a) {
        if (sender instanceof Player p) open(p);
        return true;
    }

    public void open(Player p) {
        PlayerData d = plugin.data().get(p);
        Skin cur = selected(d);
        Gui g = new Gui(4, "&8대미지 스킨") {
        };
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 22};
        for (Skin s : Skin.values()) {
            boolean own = owns(d, s);
            List<String> lore = new ArrayList<>();
            lore.add("&7" + s.desc);
            lore.add("");
            if (!own) lore.add("&8미보유 — 시즌 패스 보상으로 얻을 수 있습니다");
            else if (s == cur) lore.add("&a● 장착 중");
            else lore.add("&e▶ 클릭하여 장착");
            ItemStack icon;
            if (s == Skin.BASIC) icon = Gui.button(Material.RED_DYE, (s == cur ? "&a&l" : "&f&l") + s.label + " 스킨", lore.toArray(new String[0]));
            else {
                icon = Gui.button(own ? Material.PAPER : Material.GRAY_DYE, (own ? s.color + "&l" : "&8") + s.label + " 스킨", lore.toArray(new String[0]));
                if (own) {
                    ItemMeta m = icon.getItemMeta();
                    m.setCustomModelData(12800 + s.ordinal());
                    icon.setItemMeta(m);
                }
            }
            g.set(slots[s.ordinal()], icon, e -> {
                if (!owns(plugin.data().get(p), s)) return;
                plugin.data().get(p).counters.put("dskin_sel", (double) s.ordinal());
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.4f);
                open(p);
            });
        }
        g.set(4, Gui.button(Material.NAME_TAG, "&e&l대미지 스킨", "&7내가 준 대미지 숫자의 모양", "&7모든 플레이어에게 이 스킨으로 보입니다",
                "&7스킨 아이템은 시즌 패스 보상", "", "&f지금: " + cur.color + "&l" + cur.label), null);
        g.fill(0, 35);
        g.open(p);
    }
}
