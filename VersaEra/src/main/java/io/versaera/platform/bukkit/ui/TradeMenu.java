package io.versaera.platform.bukkit.ui;

import io.versaera.application.GameServices;
import io.versaera.domain.item.ItemInstance;
import io.versaera.domain.trade.TradeSession;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 거래 창. 왼쪽 = 내 제안, 오른쪽 = 상대 제안. 내 인벤토리의 고유 아이템을 클릭하면 올리고(서버가 ESCROW 로 옮긴 뒤에만 인벤토리에서 지움),
 * 올린 아이템을 클릭하면 내린다(배달함으로 돌아옴). 돈 · 고정 · 확인 버튼. 제안이 바뀌면 양쪽 고정 · 확인이 풀린다.
 * 묶음 재료 거래는 아직 지원하지 않는다 (TRD-02 PLANNED).
 */
public final class TradeMenu extends Menu {
    private static final int[] MINE = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21}, THEIRS = {5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26};
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final Player me;
    private final String tradeId;
    private final java.util.Map<String, ItemInstance> cache = new java.util.HashMap<>();
    private final java.util.function.Consumer<Player> deliver;

    /** @param deliver 배달함을 인벤토리로 (거래가 끝나거나 아이템을 내리면 부름) */
    public TradeMenu(GameServices s, Async async, ItemCodec codec, Player me, String tradeId, java.util.function.Consumer<Player> deliver) {
        super(4, "&8거래");
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.me = me;
        this.tradeId = tradeId;
        this.deliver = deliver;
    }

    private String uid() {
        return me.getUniqueId().toString();
    }

    /** DB 스레드에서 상태를 읽어 메인 스레드에서 그린다 */
    public void refresh() {
        async.run("trade-view", () -> {
            TradeSession t = s.trades.of(uid()).filter(x -> x.id().equals(tradeId)).orElse(null);
            if (t == null) return null;
            List<ItemInstance> all = new ArrayList<>();
            for (String id : t.items(t.a())) s.items.find(id).ifPresent(all::add);
            for (String id : t.items(t.b())) s.items.find(id).ifPresent(all::add);
            return new Object[]{t, all};
        }, r -> {
            if (r == null) {
                if (me.getOpenInventory().getTopInventory().getHolder() == this) me.closeInventory();
                return;
            }
            TradeSession t = (TradeSession) r[0];
            for (Object o : (List<?>) r[1]) cache.put(((ItemInstance) o).id(), (ItemInstance) o);
            draw(t);
        }, me);
    }

    private void draw(TradeSession t) {
        for (int i = 0; i < 36; i++) set(i, null, null);
        String other = t.other(uid());
        int i = 0;
        for (String id : t.items(uid())) {
            ItemInstance it = cache.get(id);
            if (it != null && i < MINE.length) set(MINE[i++], codec.unique(it), e -> withdraw(id));
        }
        i = 0;
        for (String id : t.items(other)) {
            ItemInstance it = cache.get(id);
            if (it != null && i < THEIRS.length) set(THEIRS[i++], codec.unique(it), null);
        }
        for (int r = 0; r < 3; r++) set(4 + r * 9, Menu.icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        set(27, Menu.icon(Material.GOLD_NUGGET, "&e" + t.money(uid()), List.of("&7좌 +100 · 우 -100 · 쉬프트 ×10")), this::money);
        set(35, Menu.icon(Material.GOLD_NUGGET, "&e" + t.money(other), List.of()), null);
        boolean myLock = t.locked(uid()), theirLock = t.locked(other);
        set(30, Menu.icon(myLock ? Material.IRON_BARS : Material.OAK_FENCE, myLock ? "&a고정됨" : "&f고정", List.of()), e -> act("lock"));
        set(32, Menu.icon(t.confirmed(uid()) ? Material.LIME_DYE : Material.GRAY_DYE, "&f확인",
                List.of(theirLock ? "&a상대 고정" : "&7상대 대기", t.confirmed(other) ? "&a상대 확인" : "&7")), e -> act("confirm"));
        set(31, Menu.icon(Material.BARRIER, "&c취소", List.of()), e -> act("cancel"));
        stamp = t.offerVersion();
    }

    private int stamp;

    @Override
    public void click(InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getClickedInventory() == me.getInventory()) {   // 내 인벤토리 → 올리기
            ItemStack it = e.getCurrentItem();
            String id = codec.instanceId(it);
            if (id == null) {
                if (it != null) me.sendMessage(Ui.error("지금은 고유 장비 · 예술품만 거래할 수 있습니다"));
                return;
            }
            int slot = e.getSlot();
            async.run("trade-offer", () -> {
                s.trades.offerItem(tradeId, uid(), id);
                return true;
            }, ok -> {
                ItemStack now = me.getInventory().getItem(slot);
                if (id.equals(codec.instanceId(now))) me.getInventory().setItem(slot, null);   // 서버가 보관을 확정한 뒤에만 지움
                updateBoth();
            }, me);
            return;
        }
        super.click(e);
    }

    private void withdraw(String itemId) {
        async.run("trade-withdraw", () -> {
            s.trades.withdrawItem(tradeId, uid(), itemId);
            return true;
        }, ok -> {
            updateBoth();
            deliver.accept(me);   // 내린 아이템은 배달함 → 인벤토리
        }, me);
    }

    private void money(InventoryClickEvent e) {
        long step = (e.isShiftClick() ? 1000 : 100) * (e.isRightClick() ? -1 : 1);
        async.run("trade-money", () -> {
            TradeSession t = s.trades.of(uid()).orElseThrow();
            s.trades.setMoney(tradeId, uid(), Math.max(0, t.money(uid()) + step));
            return true;
        }, ok -> updateBoth(), me);
    }

    private void act(String what) {
        int seen = stamp;
        async.run("trade-" + what, () -> switch (what) {
            case "lock" -> { s.trades.lock(tradeId, uid()); yield false; }
            case "confirm" -> s.trades.confirm(tradeId, uid(), seen);
            default -> { s.trades.cancel(tradeId, "cancelled by " + uid()); yield false; }
        }, done -> {
            if (done) Bukkit.getOnlinePlayers().forEach(p -> {
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof TradeMenu tm && tm.tradeId.equals(tradeId)) {
                    p.closeInventory();
                    p.sendMessage(Ui.info("거래 완료"));
                }
            });
            updateBoth();
            for (Player p : participants()) deliver.accept(p);
        }, me);
    }

    private void updateBoth() {
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers())
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof TradeMenu tm && tm.tradeId.equals(tradeId)) tm.refresh();
    }

    private List<Player> participants() {
        List<Player> out = new ArrayList<>(List.of(me));
        for (Player o : Bukkit.getOnlinePlayers())
            if (!o.equals(me) && o.getOpenInventory().getTopInventory().getHolder() instanceof TradeMenu tm && tm.tradeId.equals(tradeId)) out.add(o);
        return out;
    }

    /** 창을 닫으면 거래 취소 → 올렸던 아이템은 각자의 배달함으로 */
    @Override
    public void closed(Player p) {
        List<Player> both = participants();
        String id = p.getUniqueId().toString();
        async.run("trade-close", () -> {
            s.trades.of(id).filter(t -> t.id().equals(tradeId)).ifPresent(t -> s.trades.cancel(tradeId, "window closed"));
            return true;
        }, ok -> {
            updateBoth();
            for (Player x : both) if (x.isOnline()) deliver.accept(x);
        }, null);
    }
}
