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
        boolean resetOnJoin = kr.rpgcraft.data.ResetPending.consume(plugin, e.getPlayer().getUniqueId());
        if (resetOnJoin)   // 나가 있는 동안 초기화된 사람 (freshStart 가 기본 지급품도 줌)
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (e.getPlayer().isOnline()) kr.rpgcraft.data.ResetPending.freshStart(plugin, e.getPlayer()); }, 5L);
        if (nj.nick != null) { e.getPlayer().setDisplayName(nj.nick); e.getPlayer().setPlayerListName(nj.nick); }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {   // 가진 아이템의 설명을 현재 버전으로 새로 고침
            if (!e.getPlayer().isOnline()) return;
            for (ItemStack it : e.getPlayer().getInventory().getContents())
                if (it != null && kr.rpgcraft.item.ItemData.template(it) != null) kr.rpgcraft.item.ItemData.refresh(it);
            if (plugin.potions() != null) {   // 예전 병 포션 → 64개씩 쌓이는 포션 (v5.4.22)
                int n = plugin.potions().convertLegacy(e.getPlayer().getInventory()) + plugin.potions().convertLegacy(e.getPlayer().getEnderChest());
                if (n > 0) Text.msg(e.getPlayer(), "&a포션 " + n + "개를 64개씩 쌓이는 새 포션으로 바꿨습니다. &7(우클릭으로 마시기)");
            }
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
        boolean known = d.name != null;   // 저장된 기록이 있는 사람 (처음 온 사람은 이름 기록이 없음)
        d.name = p.getName();
        // 기본 지급품: 정말 처음 온 사람에게 딱 한 번 (v5.4.28)
        //  - 플레이어 데이터에 "받음" 기록이 있거나, 저장된 기록(이름)이 있거나, 별도 장부(starter.yml: UUID · 이름)에 있으면 주지 않음
        //  - 예전의 "맵(월드 UID)이 바뀌면 다시 지급" 규칙은 서버에 따라 월드 UID 가 바뀌어 접속할 때마다 복제돼서 없앰
        //  - 초기화(/rpg관리 reset)된 사람은 freshStart 가 따로 지급
        boolean ledger = kr.rpgcraft.data.ResetPending.hasStarter(plugin, p);
        boolean firstTime = !resetOnJoin && !d.starterGiven && !known && !ledger;
        if (!firstTime) {
            if (!d.starterGiven || !ledger) { d.starterGiven = true; kr.rpgcraft.data.ResetPending.markStarter(plugin, p); }
        } else {
            d.starterGiven = true;
            kr.rpgcraft.data.ResetPending.markStarter(plugin, p);
            plugin.data().save(d);   // 지급 기록을 바로 저장 (서버가 갑자기 꺼져도 다시 받지 않게)
            plugin.getLogger().info("기본 지급품 지급 (처음 접속): " + p.getName() + " " + p.getUniqueId());
            d.money += plugin.getConfig().getLong("player.starting-money", 0);
            kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&e" + p.getName() + "&f님이 RpgCraft에 처음 오셨습니다!"));
            // 다른 플러그인이 접속 직후 인벤토리를 정리해도 지워지지 않게 조금 뒤에 지급
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                if (kr.rpgcraft.data.ResetPending.giveStarter(plugin, p) > 0) Text.msg(p, "&a기본 지급품을 받았습니다! &7(인벤토리를 확인하세요)");
            }, 10L);
        }
        if (plugin.levels().weekendMult() > 1)   // v5.5.0 주말 경험치 이벤트 안내
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) Text.msg(p, "&6&l주말 이벤트! &e모든 경험치 x" + plugin.levels().weekendMult() + " &7(토 · 일)"); }, 80L);
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
        // v5.10.34 마크에이지 4R 풍 캐릭터 정보창: 왼쪽 장비(갑옷 · 무기) + 실루엣, 오른쪽 능력치 문장 · 장신구 · 룬
        kr.rpgcraft.gui.Gui g = new kr.rpgcraft.gui.Gui(6, "&8" + Text.name(t) + " 님의 정보", "inspect") {
        };
        org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        var hm = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        hm.setOwningPlayer(t);
        hm.setDisplayName(Text.c("&e&l" + Text.name(t) + " &7Lv." + d.level));
        var guild = plugin.guilds().of(t.getUniqueId());
        String title = plugin.content() == null ? "" : plugin.content().title(d);
        hm.setLore(java.util.List.of(Text.c("&7" + plugin.jobs().title(d)), Text.c("&f칭호 &d" + (title.isEmpty() ? "-" : title)),
                Text.c("&f길드 &b" + (guild == null ? "-" : guild.name)), Text.c("&f전투력 &6&l" + Text.num(kr.rpgcraft.stat.Power.of(s)))));
        head.setItemMeta(hm);
        g.set(4, head, null);
        var inv = t.getInventory();
        org.bukkit.inventory.ItemStack[] armor = inv.getArmorContents();
        String[] armorName = {"&7투구 없음", "&7갑옷 없음", "&7바지 없음", "&7신발 없음"};
        int[] armorSlot = {10, 19, 28, 37};
        for (int i = 0; i < 4; i++) {
            if (armor[3 - i] != null && !armor[3 - i].getType().isAir()) g.set(armorSlot[i], armor[3 - i].clone(), null);
            else g.set(armorSlot[i], kr.rpgcraft.gui.Gui.button(Material.GRAY_STAINED_GLASS_PANE, armorName[i]), null);
        }
        g.set(12, inv.getItemInMainHand().getType().isAir() ? kr.rpgcraft.gui.Gui.button(Material.GRAY_STAINED_GLASS_PANE, "&7주 무기 없음") : inv.getItemInMainHand().clone(), null);
        g.set(21, inv.getItemInOffHand().getType().isAir() ? kr.rpgcraft.gui.Gui.button(Material.GRAY_STAINED_GLASS_PANE, "&7보조 손 없음") : inv.getItemInOffHand().clone(), null);
        g.set(30, kr.rpgcraft.gui.Gui.ui(kr.rpgcraft.gui.UiIcon.ACHIEVEMENT, true, "&d&l칭호", "&f" + (title.isEmpty() ? "없음" : title)), null);
        g.set(39, kr.rpgcraft.gui.Gui.ui(kr.rpgcraft.gui.UiIcon.GUILD, guild != null, "&b&l길드", "&f" + (guild == null ? "없음" : guild.name + " &7Lv." + guild.level)), null);
        boolean hj = kr.rpgcraft.world.HiddenJobManager.of(d) != null;
        g.set(14, kr.rpgcraft.gui.Gui.ui(kr.rpgcraft.gui.UiIcon.STAT_STR, true, "&c&l공격 &7(힘 " + (int) s.str + ")",
                "&f공격력 &c" + Text.num(s.attack), "&f마력 &d" + Text.num(s.magic), "&f방어 관통 &b" + String.format("%.1f", s.armorPen) + "%"), null);
        g.set(15, kr.rpgcraft.gui.Gui.ui(kr.rpgcraft.gui.UiIcon.STAT_DEX, true, "&9&l치명 &7(민첩 " + (int) s.dex + ")",
                "&f치명타 &e" + String.format("%.1f", s.crit) + "%", "&f치명타 피해 &e+" + (int) s.critDmg + "%", "&f회피 &b" + String.format("%.1f", s.dodge) + "%"), null);
        g.set(16, kr.rpgcraft.gui.Gui.ui(kr.rpgcraft.gui.UiIcon.STAT_ADV, true, "&a&l생존 &7(모험 " + (int) s.adv + ")",
                "&f체력 &c" + Text.num(s.maxHp), "&f방어력 &7" + String.format("%.1f", s.def) + "%", "&f흡혈 &c" + String.format("%.1f", s.lifesteal) + "%"), null);
        g.set(23, kr.rpgcraft.gui.Gui.ui(hj ? kr.rpgcraft.gui.UiIcon.HIDDEN_JOB : kr.rpgcraft.gui.UiIcon.JOB, true, (hj ? "&5&l" : "&6&l") + plugin.jobs().title(d),
                "&f레벨 &e" + d.level, "&f이동속도 &a" + String.format("%+.1f", s.speed) + "%"), null);
        g.set(24, kr.rpgcraft.gui.Gui.ui(kr.rpgcraft.gui.UiIcon.STAT_POINT, true, "&6&l전투력 &e" + Text.num(kr.rpgcraft.stat.Power.of(s))), null);
        g.set(25, kr.rpgcraft.gui.Gui.ui(kr.rpgcraft.gui.UiIcon.STAT_INFO, true, "&f&l전체 능력치",
                "&f힘 &6" + (int) s.str + " &f민첩 &a" + (int) s.dex + " &f모험 &b" + (int) s.adv,
                "&f공격력 &c" + Text.num(s.attack) + " &f체력 &c" + Text.num(s.maxHp),
                "&f치명타 &e" + String.format("%.1f", s.crit) + "% &f방어 &7" + String.format("%.1f", s.def) + "%"), null);
        String[] accName = {"&7반지 없음", "&7목걸이 없음", "&7귀걸이 없음"};
        for (int i = 0; i < 3; i++) g.set(32 + i, d.accessories[i] != null ? d.accessories[i].clone() : kr.rpgcraft.gui.Gui.button(Material.GRAY_STAINED_GLASS_PANE, accName[i]), null);
        for (int i = 0; i < 3; i++) g.set(41 + i, d.runes[i] != null ? d.runes[i].clone() : kr.rpgcraft.gui.Gui.button(Material.GRAY_STAINED_GLASS_PANE, "&7룬 칸 " + (i + 1)), null);
        g.set(47, kr.rpgcraft.gui.Gui.ui(kr.rpgcraft.gui.UiIcon.SHOP, true, "&a&l거래 신청"), ev -> { p.closeInventory(); plugin.trades().request(p, t.getName()); });
        g.set(49, kr.rpgcraft.gui.Gui.ui(kr.rpgcraft.gui.UiIcon.NAV_CLOSE, true, "&c닫기"), ev -> p.closeInventory());
        g.set(51, kr.rpgcraft.gui.Gui.ui(kr.rpgcraft.gui.UiIcon.GUILD, true, "&b&l파티 초대"), ev -> { p.closeInventory(); p.performCommand("파티 초대 " + t.getName()); });
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
        plugin.health().markDeath(p);
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
        plugin.health().markRespawn(p);   // 체력을 바로 채움 (예전엔 2틱 뒤 → 그 사이 피해로 한 번 더 죽었음)
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
            if (kr.rpgcraft.world.HiddenJobManager.of(d) != null) { Text.msg(p, "&5히든 직업은 초기화할 수 없습니다."); return; }
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

    // ------------------------------------------------------------------ 내구도 (v5.3.7)
    /** 방금 무언가를 때린 시각 (공격 직후 깎이는 내구도 = 공격으로 닳은 것) */
    private final java.util.Map<java.util.UUID, Long> lastAttackTick = new java.util.HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttackMark(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) lastAttackTick.put(p.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * 플러그인 아이템(채집도구 · 망치 등 포함)은 내구도가 닳지 않음 — 곡괭이 모양 채집도구로 몬스터를 때리면 닳던 문제.
     * 일반(바닐라) 곡괭이 · 도끼 · 삽 · 괭이도 몬스터를 때릴 때는 닳지 않음 (블록을 캘 때만 원래대로).
     */
    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent e) {
        ItemStack it = e.getItem();
        if (kr.rpgcraft.item.ItemData.id(it) != null) { e.setCancelled(true); return; }
        String t = it.getType().name();
        boolean tool = t.endsWith("_PICKAXE") || t.endsWith("_AXE") || t.endsWith("_SHOVEL") || t.endsWith("_HOE");
        Long at = lastAttackTick.get(e.getPlayer().getUniqueId());
        if (tool && at != null && System.currentTimeMillis() - at < 40) e.setCancelled(true);   // 같은 틱(공격 직후)
    }

    @EventHandler
    public void onQuitClearAttack(PlayerQuitEvent e) {
        lastAttackTick.remove(e.getPlayer().getUniqueId());
    }
}
