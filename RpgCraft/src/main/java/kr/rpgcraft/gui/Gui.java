package kr.rpgcraft.gui;

import kr.rpgcraft.util.ItemBuilder;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** 모든 GUI 의 기반. 버튼 슬롯은 클릭이 취소되고, editable 슬롯만 아이템을 넣고 뺄 수 있다. */
public abstract class Gui implements InventoryHolder {
    protected Inventory inv;
    private final int rows;
    private final String plainTitle, background;
    private String shownTitle;
    private final Map<Integer, Consumer<InventoryClickEvent>> buttons = new HashMap<>();

    protected Gui(int rows, String title) {
        this(rows, title, null);
    }

    /** @param background 리소스팩 GUI 배경 키 (null = 줄 수에 맞는 기본 배경, "main" = 메인 메뉴) */
    protected Gui(int rows, String title, String background) {
        this.rows = rows;
        this.plainTitle = Text.c(title);
        this.background = background;
        this.shownTitle = plainTitle;
        inv = Bukkit.createInventory(this, rows * 9, plainTitle);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    /**
     * 여는 플레이어가 리소스팩을 적용했다면 전용 배경 글리프가 들어간 제목으로 인벤토리를 다시 만든다
     * (팩이 없는 플레이어에게는 원래 제목 그대로 → 네모 글자 없음)
     */
    /** 이 창을 열기 직전에 보고 있던 창 (뒤로가기 대상) */
    private Gui parent;

    public void open(Player p) {
        // 다른 메뉴 창에서 들어왔다면 왼쪽 아래에 [뒤로가기] 버튼 (메인 메뉴 → 하위 창 → 그 하위 창 … 모두)
        Inventory cur = p.getOpenInventory().getTopInventory();
        if (cur.getHolder() instanceof Gui prev && prev != this && parent == null)
            parent = prev.getClass() == getClass() ? prev.parent : prev;   // 같은 창을 새로 그린 경우엔 이전 창의 부모로
        if (parent != null) {
            int slot = rows * 9 - 9;
            ItemStack there = inv.getItem(slot);
            if (!editable(slot) && (there == null || isFiller(there) || !buttons.containsKey(slot))) {
                Gui back = parent;
                set(slot, button(Material.ARROW, "&e← 뒤로가기", "&7이전 창으로 돌아갑니다"), e -> back.open(p));
            }
        }
        String want = kr.rpgcraft.pack.PackManager.decorateFor(p, rows, background, plainTitle);
        if (!want.equals(shownTitle)) {
            Inventory n = Bukkit.createInventory(this, rows * 9, want);
            n.setContents(inv.getContents());
            inv = n;
            shownTitle = want;
        }
        p.openInventory(inv);
    }

    public void set(int slot, ItemStack it, Consumer<InventoryClickEvent> action) {
        inv.setItem(slot, it);
        if (action == null) buttons.remove(slot);
        else buttons.put(slot, action);
    }

    public void set(int slot, ItemStack it) {
        set(slot, it, null);
    }

    public void clearButtons() {
        buttons.clear();
    }

    public void fill(int from, int to) {
        ItemStack pane = kr.rpgcraft.pack.PackManager.filler(Material.GRAY_STAINED_GLASS_PANE);
        // 아이템을 넣고 빼는 칸(editable)은 비워둔다 → 닫을 때 돌려주는 아이템에 판유리가 섞이지 않음
        for (int i = from; i <= to && i < inv.getSize(); i++) if (inv.getItem(i) == null && !editable(i)) inv.setItem(i, pane);
    }

    /** 메뉴 칸 채우기용 판유리인지 (이전 버전에서 새어나온 것 포함) */
    public static boolean isFiller(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        var m = it.getItemMeta();
        if (m.getPersistentDataContainer().has(kr.rpgcraft.Keys.FILLER, org.bukkit.persistence.PersistentDataType.BYTE)) return true;
        String type = it.getType().name();
        return (type.equals("GRAY_STAINED_GLASS_PANE") || type.equals("BLACK_STAINED_GLASS_PANE"))
                && m.hasDisplayName() && m.getDisplayName().trim().isEmpty() && m.hasCustomModelData() && m.getCustomModelData() == 1;
    }

    /** 플레이어 인벤토리에서 판유리 제거, 제거한 개수 반환 */
    public static int purge(Player p) {
        int n = 0;
        var pi = p.getInventory();
        for (int i = 0; i < pi.getSize(); i++) {
            ItemStack it = pi.getItem(i);
            if (isFiller(it)) {
                n += it.getAmount();
                pi.setItem(i, null);
            }
        }
        if (isFiller(p.getItemOnCursor())) p.setItemOnCursor(null);
        return n;
    }

    public static ItemStack button(Material m, String name, String... lore) {
        UiIcon ui = iconFor(m, name);   // v5.10.44 메뉴의 바닐라 아이콘은 모두 4R 풍 그림으로
        if (ui != null) return ui(ui, true, name, lore);
        return new ItemBuilder(m).name(name).lore(lore).hideAll().build();
    }

    /** v5.10.44 메뉴 단추에 쓰던 바닐라 재질 → 디자인한 아이콘 (없으면 null = 바닐라 그대로) */
    public static UiIcon iconFor(Material m, String name) {
        if (m == null) return null;
        String n = name == null ? "" : name;
        return switch (m.name()) {
            case "ARROW", "SPECTRAL_ARROW" -> n.contains("다음") || n.contains("▶") || n.contains("→") ? UiIcon.NAV_NEXT : UiIcon.NAV_BACK;
            case "BARRIER", "RED_WOOL", "RED_CONCRETE" -> UiIcon.NAV_CLOSE;
            case "GRAY_DYE", "BLACK_DYE", "IRON_BARS" -> UiIcon.LOCKED;
            case "LIME_DYE", "LIME_WOOL", "LIME_CONCRETE", "GREEN_WOOL" -> UiIcon.CHECK;
            case "BOOK", "KNOWLEDGE_BOOK", "BOOKSHELF" -> UiIcon.BOOK;
            case "NETHER_STAR" -> UiIcon.STAR;
            case "PAPER", "FILLED_MAP" -> UiIcon.NOTE;
            case "NAME_TAG" -> UiIcon.TAG;
            case "GOLD_INGOT", "GOLD_NUGGET", "GOLD_BLOCK", "SUNFLOWER", "EMERALD", "RAW_GOLD" -> UiIcon.COIN;
            case "ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL", "SMITHING_TABLE" -> UiIcon.ANVIL;
            case "CHEST", "BARREL", "CHEST_MINECART", "ENDER_CHEST", "TRAPPED_CHEST", "SHULKER_BOX" -> UiIcon.CHEST;
            case "HOPPER" -> UiIcon.FUNNEL;
            case "CLOCK", "COMPASS", "RECOVERY_COMPASS" -> UiIcon.CLOCK;
            case "SADDLE" -> UiIcon.MOUNT;
            case "BEACON", "SEA_LANTERN" -> UiIcon.BEACON;
            case "SKELETON_SKULL" -> UiIcon.SKULL;
            case "ZOMBIE_HEAD", "CREEPER_HEAD", "SPAWNER" -> m == Material.SPAWNER ? UiIcon.SPAWNER : UiIcon.MOB;
            case "WITHER_SKELETON_SKULL", "DRAGON_HEAD", "PIGLIN_HEAD" -> UiIcon.BOSS;
            case "NETHERITE_UPGRADE_SMITHING_TEMPLATE", "EXPERIENCE_BOTTLE" -> m == Material.EXPERIENCE_BOTTLE ? UiIcon.EXP : UiIcon.UPGRADE;
            case "WRITABLE_BOOK", "WRITTEN_BOOK", "FEATHER" -> m == Material.FEATHER ? UiIcon.FEATHER : UiIcon.QUILL;
            case "MAP" -> UiIcon.MAP;
            case "EGG", "TURTLE_EGG", "SNIFFER_EGG" -> UiIcon.EGG;
            case "DRAGON_EGG" -> UiIcon.DRAGON_EGG;
            case "ENCHANTED_BOOK" -> UiIcon.SPELLBOOK;
            case "COOKIE" -> UiIcon.COOKIE;
            case "LEATHER_BOOTS", "IRON_BOOTS" -> UiIcon.BOOTS;
            case "SOUL_LANTERN", "LANTERN", "SOUL_TORCH", "TORCH" -> UiIcon.LANTERN;
            case "OAK_SIGN", "SPRUCE_SIGN", "DARK_OAK_SIGN" -> UiIcon.SIGN;
            case "CAMPFIRE", "SOUL_CAMPFIRE", "BLAZE_POWDER", "LAVA_BUCKET", "FIRE_CHARGE", "MAGMA_CREAM" -> UiIcon.FIRE;
            case "TNT" -> UiIcon.BOMB;
            case "WOODEN_PICKAXE", "STONE_PICKAXE", "IRON_PICKAXE", "DIAMOND_PICKAXE" -> UiIcon.PICKAXE;
            case "AMETHYST_CLUSTER", "AMETHYST_SHARD", "END_CRYSTAL", "HEART_OF_THE_SEA", "PRISMARINE_CRYSTALS" -> UiIcon.CRYSTAL;
            case "MOSSY_STONE_BRICKS", "CRACKED_STONE_BRICKS" -> UiIcon.RUINS;
            case "ENCHANTING_TABLE", "CRAFTING_TABLE", "BREWING_STAND" -> UiIcon.TABLE;
            case "TRIPWIRE_HOOK" -> UiIcon.KEY;
            case "BONE", "BONE_MEAL" -> UiIcon.BONE;
            case "TOTEM_OF_UNDYING" -> UiIcon.TOTEM;
            case "IRON_INGOT", "RAW_IRON", "COPPER_INGOT" -> UiIcon.INGOT;
            case "LIGHTNING_ROD" -> UiIcon.LIGHTNING;
            case "GOLDEN_HELMET" -> UiIcon.CROWN;
            case "POTION", "SPLASH_POTION", "DRAGON_BREATH", "GLASS_BOTTLE", "HONEY_BOTTLE" -> UiIcon.POTION_BAG;
            case "IRON_SWORD", "DIAMOND_SWORD", "STONE_SWORD", "WOODEN_SWORD", "GOLDEN_SWORD" -> UiIcon.WPN_SWORD;
            case "SHEARS" -> UiIcon.WPN_DAGGER;
            case "IRON_AXE", "DIAMOND_AXE" -> UiIcon.WPN_AXE;
            case "SHIELD" -> UiIcon.WPN_SHIELD;
            case "BOW", "CROSSBOW" -> UiIcon.WPN_BOW;
            case "BLAZE_ROD" -> UiIcon.WPN_STAFF;
            case "TRIDENT" -> UiIcon.WPN_SPEAR;
            case "IRON_CHESTPLATE", "ARMOR_STAND", "DIAMOND_CHESTPLATE" -> UiIcon.SHOP_ARMOR_WARRIOR;
            case "STONE_STAIRS", "PURPUR_STAIRS" -> UiIcon.TOWER;
            case "BLUE_BANNER", "WHITE_BANNER", "RED_BANNER" -> UiIcon.GUILD;
            case "COD", "SALMON", "TROPICAL_FISH", "FISHING_ROD" -> UiIcon.SHOP_FISH;
            default -> null;
        };
    }

    /** v5.10.34 4R 풍 아이콘 단추 (PAPER + UiIcon 그림, on=false 면 회색) */
    public static ItemStack ui(UiIcon icon, boolean on, String name, String... lore) {
        ItemStack it = new ItemBuilder(Material.PAPER).name(name).lore(lore).hideAll().build();
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        m.setCustomModelData(icon.cmd(on));
        it.setItemMeta(m);
        return it;
    }

    /** 아이템을 넣고 뺄 수 있는 슬롯인지 */
    public boolean editable(int rawSlot) {
        return false;
    }

    /** 플레이어 인벤토리에서 쉬프트 클릭으로 GUI 에 아이템을 넣는 것을 허용할지 */
    public boolean allowShiftIn() {
        return false;
    }

    public void handleClick(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        if (raw < 0) return;
        if (raw >= inv.getSize()) {
            if (e.isShiftClick() && !allowShiftIn()) e.setCancelled(true);
            if (e.getClick().name().contains("DOUBLE")) e.setCancelled(true);
            onBottomClick(e);
            return;
        }
        if (editable(raw)) {
            if (isFiller(e.getCurrentItem()) || isFiller(e.getCursor())) {
                e.setCancelled(true);
                inv.setItem(raw, null);
                return;
            }
            onEditableClick(e);
            return;
        }
        e.setCancelled(true);
        // v5.10.34 더블클릭은 첫 클릭과 함께 한 번 더 들어와서 켜고 바로 끄는 문제 (설정이 자꾸 꺼짐) → 무시
        if (e.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) return;
        Consumer<InventoryClickEvent> c = buttons.get(raw);
        // 클릭 이벤트 도중 인벤토리를 열고 닫으면 불안정하므로 다음 틱에 실행
        if (c != null && e.getWhoClicked() instanceof org.bukkit.entity.Player pl
                && kr.rpgcraft.data.Setting.SOUND.get(kr.rpgcraft.RpgCraft.get().data().get(pl)))
            pl.playSound(pl.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.4f, 1.3f);
        if (c != null) Bukkit.getScheduler().runTask(kr.rpgcraft.RpgCraft.get(), () -> c.accept(e));
    }

    public void handleDrag(InventoryDragEvent e) {
        for (int raw : e.getRawSlots()) {
            if (raw < inv.getSize() && !editable(raw)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    protected void onBottomClick(InventoryClickEvent e) {}

    protected void onEditableClick(InventoryClickEvent e) {}

    public void onClose(InventoryCloseEvent e) {}

    /** 편집 슬롯에 남은 아이템을 플레이어에게 돌려준다 */
    protected void giveBack(Player p, int... slots) {
        for (int s : slots) {
            ItemStack it = inv.getItem(s);
            if (it == null || it.getType().isAir()) continue;
            inv.setItem(s, null);
            if (isFiller(it)) continue;
            for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }
}
