package kr.rpgcraft.listener;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.item.Category;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class PlayerListener implements Listener {
    private final RpgCraft plugin;

    public PlayerListener(RpgCraft plugin) {
        this.plugin = plugin;
    }

    /** 주문서 남은 시간을 보관 (접속 종료·서버 종료 시) */
    public static void pauseBuffs(PlayerData bd) {
        long now = System.currentTimeMillis();
        for (String k : new String[]{"buff_atk", "buff_def", "buff_speed", "buff_exp"}) {
            long left = (long) bd.counter(k) - now;
            if (left > 0) { bd.counters.put("left_" + k, (double) left); bd.counters.remove(k); }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        PlayerData nj = plugin.data().get(e.getPlayer());
        if (nj.nick != null) { e.getPlayer().setDisplayName(nj.nick); e.getPlayer().setPlayerListName(nj.nick); }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {   // 가진 아이템의 설명을 현재 버전으로 새로 고침
            if (!e.getPlayer().isOnline()) return;
            for (ItemStack it : e.getPlayer().getInventory().getContents())
                if (it != null && kr.rpgcraft.item.ItemData.template(it) != null) kr.rpgcraft.item.ItemData.refresh(it);
        }, 20L);
        PlayerData bj = plugin.data().get(e.getPlayer());
        for (String k : new String[]{"buff_atk", "buff_def", "buff_speed", "buff_exp"}) {   // 나가 있던 동안 멈춰 있던 주문서 시간 복원
            Double left = bj.counters.remove("left_" + k);
            if (left != null && left > 0) bj.counters.put(k, (double) (System.currentTimeMillis() + left.longValue()));
        }
        if (plugin.playerCommands() != null) Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.playerCommands().deliverCalls(e.getPlayer()), 40L);
        if (plugin.auction() != null) Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.auction().onJoinNotice(e.getPlayer()), 60L);
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        d.name = p.getName();
        if (!d.starterGiven) {
            d.starterGiven = true;
            d.money += plugin.getConfig().getLong("player.starting-money", 0);
            for (String s : plugin.getConfig().getStringList("player.starter-kit")) {
                String[] kv = s.split(":");
                ItemStack it = plugin.items().create(kv[0], kv.length > 1 ? Text.parseInt(kv[1], 1) : 1);
                if (it != null) p.getInventory().addItem(it);
            }
            kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&e" + p.getName() + "&f님이 RpgCraft에 처음 오셨습니다!"));
        }
        plugin.stats().refresh(p);
        plugin.hud().setup(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) plugin.health().sync(p);
        }, 5L);
    }

    /** 다른 플레이어를 쉬프트+우클릭하면 그 사람의 정보 */
    @EventHandler(ignoreCancelled = true)
    public void onInspect(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !e.getPlayer().isSneaking() || !(e.getRightClicked() instanceof Player t)) return;
        if (plugin.combat().isNpc(t)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(t);
        var s = d.stats;
        kr.rpgcraft.gui.Gui g = new kr.rpgcraft.gui.Gui(6, "&8" + Text.name(t) + " 님의 정보") {
        };
        org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        var hm = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        hm.setOwningPlayer(t);
        hm.setDisplayName(Text.c("&e&l" + Text.name(t)));
        var guild = plugin.guilds().of(t.getUniqueId());
        String title = plugin.content() == null ? "" : plugin.content().title(d);
        hm.setLore(java.util.List.of(Text.c("&fLv." + d.level + " &7" + plugin.jobs().title(d)), Text.c("&f칭호 &d" + (title.isEmpty() ? "-" : title)),
                Text.c("&f길드 &b" + (guild == null ? "-" : guild.name)), Text.c("&f전투력 &e" + Text.num(kr.rpgcraft.stat.Power.of(s)))));
        head.setItemMeta(hm);
        g.set(4, head, null);
        var inv = t.getInventory();
        org.bukkit.inventory.ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < 4; i++) if (armor[3 - i] != null) g.set(19 + i, armor[3 - i].clone(), null);
        if (!inv.getItemInMainHand().getType().isAir()) g.set(24, inv.getItemInMainHand().clone(), null);
        if (!inv.getItemInOffHand().getType().isAir()) g.set(25, inv.getItemInOffHand().clone(), null);
        g.set(31, kr.rpgcraft.gui.Gui.button(Material.PAPER, "&f능력치",
                "&f힘 &6" + (int) s.str + " &f민첩 &a" + (int) s.dex + " &f모험 &b" + (int) s.adv,
                "&f공격력 &c" + Text.num(s.attack) + " &f마력 &d" + Text.num(s.magic),
                "&f체력 &c" + Text.num(s.maxHp) + " &f방어력 &7" + String.format("%.1f", s.def),
                "&f치명타 &e" + String.format("%.1f", s.crit) + "% &f치명타 피해 &e" + (int) s.critDmg + "%"), null);
        for (int i = 0; i < 3; i++) if (d.accessories[i] != null) g.set(37 + i, d.accessories[i].clone(), null);
        for (int i = 0; i < 3; i++) if (d.runes[i] != null) g.set(41 + i, d.runes[i].clone(), null);
        g.set(47, kr.rpgcraft.gui.Gui.button(Material.EMERALD, "&a거래 신청"), ev -> { p.closeInventory(); plugin.trades().request(p, t.getName()); });
        g.set(51, kr.rpgcraft.gui.Gui.button(Material.CAKE, "&b파티 초대"), ev -> { p.closeInventory(); p.performCommand("파티 초대 " + t.getName()); });
        g.fill(0, 53);
        g.open(p);
    }

    /** 단검(가위 재질)으로 양털 등을 깎아 무한히 얻는 것을 막는다 */
    @EventHandler(ignoreCancelled = true)
    public void onShear(org.bukkit.event.player.PlayerShearEntityEvent e) {
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuitTrade(PlayerQuitEvent e) {
        pauseBuffs(plugin.data().get(e.getPlayer()));
        plugin.trades().abort(e.getPlayer(), Text.name(e.getPlayer()) + "님이 나가서 거래가 취소되었습니다.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeathExp(org.bukkit.event.entity.PlayerDeathEvent e) {
        PlayerData d = plugin.data().get(e.getEntity());
        double loss = plugin.levels().need(d.level) * plugin.getConfig().getDouble("death.exp-loss", 0.10);
        if (loss <= 0 || d.exp <= 0) return;
        d.exp = Math.max(0, d.exp - loss);
        Bukkit.getScheduler().runTaskLater(plugin, () -> Text.msg(e.getEntity(), "&c경험치를 잃었습니다. &7(-" + Text.num(loss) + ")"), 20L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeathTrade(org.bukkit.event.entity.PlayerDeathEvent e) {
        plugin.trades().abort(e.getEntity(), "거래 도중 사망하여 거래가 취소되었습니다.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        PlayerData d = plugin.data().get(e.getPlayer());
        d.ruinId = null;
        plugin.data().unload(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        PlayerData d = plugin.data().get(p);
        if (plugin.getConfig().getBoolean("death.keep-inventory", true)) {
            e.setKeepInventory(true);
            e.getDrops().clear();
        }
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        long lossPct = plugin.getConfig().getLong("death.money-loss-percent", 0);
        if (lossPct > 0) {
            long loss = d.money * lossPct / 100;
            d.money -= loss;
            if (loss > 0) Text.msg(p, "&c사망하여 소지금 " + Text.money(loss) + "을 잃었습니다.");
        }
        d.ruinId = null;
        d.pendingKnock = 0;
        Player killer = p.getKiller();
        plugin.passives().onDeath(p, killer);
        if (killer != null && !killer.equals(p))
            e.setDeathMessage(Text.c("&c☠ &f" + Text.name(p) + " &7님이 &f" + Text.name(killer) + " &7님에게 처치당했습니다."));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            PlayerData d = plugin.data().get(p);
            plugin.stats().refresh(p);
            d.hp = d.stats.maxHp;
            plugin.health().sync(p);
        }, 2L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (!plugin.getConfig().getBoolean("player.lock-food", true)) return;
        e.setCancelled(true);
        e.getEntity().setFoodLevel(20);
        e.getEntity().setSaturation(5f);
    }

    @EventHandler
    public void onExp(PlayerExpChangeEvent e) {
        e.setAmount(0);
    }

    /** 커스텀 아이템 우클릭 사용 (수표, 토템 뽑기권, 스탯 초기화권) */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack it = p.getInventory().getItemInMainHand();
        Category c = ItemData.category(it);
        if (c == null) return;
        String id = ItemData.id(it);
        PlayerData d = plugin.data().get(p);
        if (c == Category.CHECK) {
            e.setCancelled(true);
            long v = (long) ItemData.value(it);
            it.setAmount(it.getAmount() - 1);
            plugin.economy().give(p, v);
            Text.msg(p, "&a수표 입금: " + Text.money(v));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        } else if ("ticket_totem".equals(id)) {
            e.setCancelled(true);
            it.setAmount(it.getAmount() - 1);
            String t = plugin.guilds().randomTotem();
            for (ItemStack left : p.getInventory().addItem(plugin.guilds().totemItem(t)).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            Text.msg(p, "&6토템 획득: &f" + plugin.guilds().totemLabel(t));
            p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
        } else if (id != null && id.startsWith("scroll_")) {
            e.setCancelled(true);
            useScroll(p, d, it, id);
        } else if ("ticket_job_reset".equals(id)) {
            e.setCancelled(true);
            if (d.job == null) { Text.msg(p, "&c직업이 없습니다."); return; }
            it.setAmount(it.getAmount() - 1);
            plugin.jobs().reset(d);
            plugin.stats().refresh(p);
            Text.msg(p, "&a직업이 초기화되었습니다. /직업 으로 다시 선택하세요.");
        } else if ("ticket_stat_reset".equals(id)) {
            e.setCancelled(true);
            if (d.allocated() == 0) {
                Text.msg(p, "&c투자한 스탯이 없습니다.");
                return;
            }
            it.setAmount(it.getAmount() - 1);
            d.statPoints += d.allocated();
            d.str = d.dex = d.adv = 0;
            plugin.stats().refresh(p);
            Text.msg(p, "&a스탯이 초기화되었습니다. 스탯 포인트: " + d.statPoints);
        } else if (c == Category.TICKET || c == Category.MATERIAL || c == Category.SHARD || c == Category.ESSENCE
                || c == Category.RUNE || c == Category.TOTEM || c == Category.HAMMER) {
            e.setCancelled(true); // 먹기·던지기·설치 방지
        }
    }

    private void useScroll(Player p, PlayerData d, ItemStack it, String id) {
        long now = System.currentTimeMillis();
        switch (id) {
            case "scroll_return" -> {
                if (System.currentTimeMillis() - d.lastCombat < 5000) { Text.msg(p, "&c전투 중에는 사용할 수 없습니다."); return; }
                it.setAmount(it.getAmount() - 1);
                org.bukkit.Location start = p.getLocation();
                Text.msg(p, "&b5초 뒤 스폰으로 귀환합니다. 움직이면 취소됩니다.");
                new org.bukkit.scheduler.BukkitRunnable() {
                    int t = 0;

                    @Override
                    public void run() {
                        if (!p.isOnline() || p.getLocation().distanceSquared(start) > 0.5) { Text.actionBar(p, "&c귀환 취소"); cancel(); return; }
                        p.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, p.getLocation().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0.3);
                        if (++t >= 10) {
                            cancel();
                            p.teleport(p.getWorld().getSpawnLocation());
                            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                        }
                    }
                }.runTaskTimer(plugin, 10L, 10L);
                return;
            }
            case "scroll_atk" -> d.counters.put("buff_atk", (double) (now + 600_000));
            case "scroll_def" -> d.counters.put("buff_def", (double) (now + 600_000));
            case "scroll_speed" -> d.counters.put("buff_speed", (double) (now + 600_000));
            case "scroll_exp" -> d.counters.put("buff_exp", (double) (now + 1_800_000));
            default -> { return; }
        }
        it.setAmount(it.getAmount() - 1);
        plugin.stats().refresh(p);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.4f);
        Text.actionBar(p, "&a" + plugin.items().get(id).name + " 사용!");
    }

    /** 커스텀 아이템은 블록으로 설치 불가 */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        ItemStack it = e.getItemInHand();
        if (ItemData.id(it) != null) e.setCancelled(true);
    }

    /** 커스텀 아이템을 바닐라 조합/모루/대장장이 작업대 재료로 쓰지 못하게 */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (ItemData.id(it) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        for (ItemStack it : e.getInventory().getContents()) {
            if (ItemData.id(it) != null) {
                e.setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onSmith(PrepareSmithingEvent e) {
        for (ItemStack it : e.getInventory().getContents()) {
            if (ItemData.id(it) != null) {
                e.setResult(null);
                return;
            }
        }
    }

    /** 장비를 바꾸면 즉시 스탯 갱신 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!e.getPlayer().isOnline()) return;
            ItemStack held = e.getPlayer().getInventory().getItemInMainHand();   // 업데이트로 바뀐 스킬 설명 등을 최신으로
            if (kr.rpgcraft.item.ItemData.template(held) != null) kr.rpgcraft.item.ItemData.refresh(held);
            plugin.stats().refresh(e.getPlayer());
        });
    }
}
