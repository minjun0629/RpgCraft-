package kr.rpgcraft.world;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.mob.CustomMobManager;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

/**
 * 맵 곳곳의 의뢰 NPC.
 * /rpg관리 questnpc scatter <수> 로 스폰에서 떨어진 여러 장소에 흩어 배치한다 (또는 here <유형>).
 * NPC 유형: 사냥꾼(처치), 수집가(전리품 납품), 목동(야생 동물), 탐험가(정예·중간 보스)
 * 우클릭 → 오늘의 의뢰 3개 (NPC·날짜마다 다름). 창을 연 뒤부터 진행도가 올라가고, 완료하면 클릭해 보상.
 * 모두 혼자서 완료할 수 있는 PvE 의뢰만 있다.
 */
public class QuestNpcManager implements Listener {
    public enum Type {
        HUNTER("사냥꾼", Villager.Profession.WEAPONSMITH), COLLECTOR("수집가", Villager.Profession.LIBRARIAN),
        HERDER("목동", Villager.Profession.SHEPHERD), EXPLORER("탐험가", Villager.Profession.CARTOGRAPHER),
        FISHER("어부", Villager.Profession.FISHERMAN), MINER("광부", Villager.Profession.TOOLSMITH),
        ALCHEMIST("연금술사", Villager.Profession.CLERIC), GUARD("경비대장", Villager.Profession.ARMORER),
        MERCHANT("행상인", Villager.Profession.NITWIT), HUNTMASTER("사냥 길드장", Villager.Profession.LEATHERWORKER);

        final String label;
        final Villager.Profession prof;

        Type(String label, Villager.Profession prof) {
            this.label = label;
            this.prof = prof;
        }
    }

    private record Quest(String kind, String target, int amount, String desc) {}

    private static final String[] FIRST = {"한스", "마리", "도윤", "세린", "바람", "로안", "하늘", "에단", "루나", "태오", "미라", "카일",
            "브론", "엘라", "고든", "시아", "다린", "노아", "피오", "레아", "오스카", "유나", "바엘", "케이트", "모건", "리오", "사샤", "토르", "이안", "헤라"};
    private final RpgCraft plugin;
    private final NamespacedKey KEY;
    private final Map<String, Type> npcs = new LinkedHashMap<>();
    private final File file;
    private final Random rnd = new Random();

    public QuestNpcManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "quest_npc");
        this.file = new File(plugin.getDataFolder(), "quest_npcs.yml");
        Bukkit.getScheduler().runTaskLater(plugin, this::autoFill, 20L * 30);
        Bukkit.getScheduler().runTaskTimer(plugin, this::groundNpcs, 20L * 20, 20L * 30);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String id : y.getKeys(false)) {
            try {
                npcs.put(id, Type.valueOf(y.getString(id)));
            } catch (Exception ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        npcs.forEach((k, v) -> y.set(k, v.name()));
        try {
            y.save(file);
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------ 배치
    public Villager spawn(Location l, Type t) {
        String id = "q" + System.currentTimeMillis() % 100000000 + rnd.nextInt(100);
        String name = FIRST[rnd.nextInt(FIRST.length)];
        Villager v = l.getWorld().spawn(l, Villager.class, x -> {
            x.setAI(false);
            x.setInvulnerable(true);
            x.setSilent(true);
            x.setPersistent(true);
            x.setRemoveWhenFarAway(false);
            x.setProfession(t.prof);
            x.setCustomName(Text.c("&e&l[의뢰] &f" + t.label + " " + name));
            x.setCustomNameVisible(true);
            x.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
            x.getPersistentDataContainer().set(Keys.INDICATOR, PersistentDataType.BYTE, (byte) 0);
        });
        npcs.put(id, t);
        save();
        // 작은 쉼터
        Block b = l.getBlock();
        b.getRelative(1, 0, 1).setType(Material.LANTERN, false);
        return v;
    }

    public int scatter(World w, int count) {
        int made = 0;
        Location spawn = w.getSpawnLocation();
        Type[] types = Type.values();
        for (int i = 0; i < count * 10 && made < count; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 80 + rnd.nextDouble() * 1400;
            Block top = kr.rpgcraft.util.Locs.surface(w, spawn.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
            if (top.isLiquid() || top.getType().name().contains("LEAVES")) continue;
            if (KingdomBuilder.inKingdom(plugin, top.getLocation())) continue;   // v5.10.56 왕국 안은 정해진 자리에만
            spawn(top.getLocation().add(0.5, 1, 0.5), types[made % types.length]);
            made++;
        }
        return made;
    }

    // ------------------------------------------------------------------ 의뢰 (NPC·날짜로 결정되는 3개)
    private static long day() {
        return LocalDate.now().toEpochDay();
    }

    private Quest quest(String npc, Type t, int i) {
        Random r = new Random(npc.hashCode() * 31L + day() * 7L + i);
        List<CustomMobManager.MobDef> defs = new ArrayList<>(plugin.customMobs().defs());
        return switch (t) {
            case HUNTER -> {
                if (!defs.isEmpty() && r.nextBoolean()) {
                    CustomMobManager.MobDef d = defs.get(r.nextInt(defs.size()));
                    int n = 3 + r.nextInt(4);
                    yield new Quest("KILL_ID", d.id, n, d.name + " " + n + "마리 처치 &8(Lv." + d.minLevel + "~" + d.maxLevel + ")");
                }
                int n = 15 + r.nextInt(16);
                yield new Quest("KILL_ANY", "", n, "아무 몬스터 " + n + "마리 처치");
            }
            case COLLECTOR -> {
                String[] ids = {"loot_bone", "loot_slime", "loot_fang", "loot_venom", "loot_dust", "loot_hide", "loot_feather", "loot_ink", "loot_bandage"};
                String id = ids[r.nextInt(ids.length)];
                int n = 5 + r.nextInt(8);
                yield new Quest("COLLECT", id, n, plugin.items().get(id).name + " " + n + "개 납품");
            }
            case HERDER -> {
                int n = 6 + r.nextInt(8);
                yield new Quest("ANIMAL", "", n, "사나운 야생 동물 " + n + "마리 처치");
            }
            case FISHER -> {
                int n = 3 + r.nextInt(5);
                yield new Quest("FISH", "", n, "물고기 " + n + "마리 낚기");
            }
            case MINER -> {
                int n = 2 + r.nextInt(4);
                yield new Quest("GATHER", "", n, "채집 " + n + "회");
            }
            case ALCHEMIST -> {
                String[] ids = {"loot_venom", "loot_slime", "loot_ink", "loot_dust", "loot_feather"};
                String id = ids[r.nextInt(ids.length)];
                int n = 4 + r.nextInt(6);
                yield new Quest("COLLECT", id, n, plugin.items().get(id).name + " " + n + "개 납품");
            }
            case GUARD -> {
                int n = 25 + r.nextInt(20);
                yield new Quest("KILL_ANY", "", n, "몬스터 " + n + "마리 소탕");
            }
            case MERCHANT -> {
                String[] ids = {"loot_hide", "loot_fang", "loot_bone", "loot_bandage", "loot_totem"};
                String id = ids[r.nextInt(ids.length)];
                int n = 3 + r.nextInt(8);
                yield new Quest("COLLECT", id, n, plugin.items().get(id).name + " " + n + "개 구해 오기");
            }
            case HUNTMASTER -> {
                if (!defs.isEmpty()) {
                    CustomMobManager.MobDef d = defs.get(r.nextInt(defs.size()));
                    int n = 5 + r.nextInt(6);
                    yield new Quest("KILL_ID", d.id, n, d.name + " " + n + "마리 사냥");
                }
                yield new Quest("ELITE", "", 3, "정예 몬스터 3마리 처치");
            }
            case EXPLORER -> {
                int n = 2 + r.nextInt(3);
                yield i == 2 ? new Quest("MINIBOSS", "", 1, "중간 보스 1마리 처치") : new Quest("ELITE", "", n, "정예 몬스터 " + n + "마리 처치");
            }
        };
    }

    private String key(String npc, int i, String what) {
        return "qn_" + npc + "_" + day() + "_" + i + "_" + what;
    }

    // ------------------------------------------------------------------ 상호작용
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        String id = e.getRightClicked().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        Type t = npcs.get(id);
        if (t == null) return;
        new QuestGui(e.getPlayer(), id, t, e.getRightClicked().getCustomName()).open(e.getPlayer());
    }

    private class QuestGui extends Gui {
        QuestGui(Player p, String npc, Type t, String title) {
            super(3, "&8" + (title == null ? "의뢰" : Text.strip(title)));
            PlayerData d = plugin.data().get(p);
            for (int i = 0; i < 3; i++) {
                Quest q = quest(npc, t, i);
                d.counters.putIfAbsent(key(npc, i, "acc"), 1.0);
                int prog = (int) d.counter(key(npc, i, "p"));
                boolean done = d.counter(key(npc, i, "done")) > 0;
                boolean ready = q.kind().equals("COLLECT") ? count(p, q.target()) >= q.amount() : prog >= q.amount();
                long money = reward(d, i);
                int slot = 11 + i * 2;
                int idx = i;
                set(slot, button(done ? Material.LIME_DYE : ready ? Material.WRITABLE_BOOK : Material.PAPER,
                        (done ? "&a✔ " : ready ? "&e" : "&f") + "의뢰 " + (i + 1),
                        "&7" + q.desc(),
                        q.kind().equals("COLLECT") ? "&f보유: " + Math.min(q.amount(), count(p, q.target())) + " / " + q.amount() : "&f진행: " + Math.min(prog, q.amount()) + " / " + q.amount(),
                        "", "&6보상: &f" + Text.money(money) + " &7+ 경험치",
                        done ? "&a완료한 의뢰입니다" : ready ? "&e▶ 클릭해서 보상 받기" : "&8매일 새로운 의뢰로 바뀝니다"), e -> {
                    if (done) return;
                    if (q.kind().equals("COLLECT")) {
                        if (count(p, q.target()) < q.amount()) return;
                        take(p, q.target(), q.amount());
                    } else if (d.counter(key(npc, idx, "p")) < q.amount()) return;
                    d.counters.put(key(npc, idx, "done"), 1.0);
                    d.counters.merge("ach_quest", 1.0, Double::sum);
                    plugin.economy().give(p, money);
                    plugin.levels().addExp(p, plugin.levels().need(d.level) * plugin.getConfig().getDouble("quests.exp-npc", 0.02));   // v5.6.3: 8% → 2%
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1.1f);
                    Text.msg(p, "&a의뢰 완료! &f" + Text.money(money) + " &7+ 경험치");
                    p.closeInventory();
                });
            }
            fill(0, 26);
        }
    }

    private long reward(PlayerData d, int i) {
        return (long) (3000 * (1 + d.level * 0.1) * (1 + i * 0.3) * plugin.getConfig().getDouble("economy.quest-money-mult", 1.0));
    }

    private int count(Player p, String id) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (id.equals(ItemData.id(it))) n += it.getAmount();
        return n;
    }

    private void take(Player p, String id, int amount) {
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (amount <= 0) break;
            if (!id.equals(ItemData.id(it))) continue;
            int t = Math.min(amount, it.getAmount());
            it.setAmount(it.getAmount() - t);
            amount -= t;
        }
    }

    // ------------------------------------------------------------------ 진행도
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null) {
            var st = plugin.mobs().peek(e.getEntity());
            if (st != null && st.lastHitBy != null && System.currentTimeMillis() - st.lastHitAt < 15_000) k = Bukkit.getPlayer(st.lastHitBy);
        }
        if (k == null || npcs.isEmpty()) return;
        LivingEntity ent = e.getEntity();
        PlayerData d = plugin.data().get(k);
        CustomMobManager.MobDef def = plugin.customMobs().of(ent);
        var tier = plugin.tiers().tier(ent);
        for (Map.Entry<String, Type> en : npcs.entrySet()) {
            for (int i = 0; i < 3; i++) {
                if (d.counter(key(en.getKey(), i, "acc")) <= 0 || d.counter(key(en.getKey(), i, "done")) > 0) continue;
                Quest q = quest(en.getKey(), en.getValue(), i);
                boolean match = switch (q.kind()) {
                    case "KILL_ANY" -> ent instanceof Enemy;
                    case "KILL_ID" -> def != null && def.id.equals(q.target());
                    case "ANIMAL" -> ent instanceof Animals;
                    case "ELITE" -> tier != kr.rpgcraft.mob.MonsterTierManager.Tier.NORMAL;
                    case "MINIBOSS" -> tier == kr.rpgcraft.mob.MonsterTierManager.Tier.MINIBOSS;
                    default -> false;
                };
                if (!match) continue;
                double v = d.counters.merge(key(en.getKey(), i, "p"), 1.0, Double::sum);
                if (v == q.amount()) Text.actionBar(k, "&e의뢰 달성: &f" + q.desc() + " &7(의뢰인에게 돌아가세요)");
            }
        }
    }

    private void progress(Player k, String kind) {
        if (npcs.isEmpty()) return;
        PlayerData d = plugin.data().get(k);
        for (Map.Entry<String, Type> en : npcs.entrySet()) {
            for (int i = 0; i < 3; i++) {
                if (d.counter(key(en.getKey(), i, "acc")) <= 0 || d.counter(key(en.getKey(), i, "done")) > 0) continue;
                Quest q = quest(en.getKey(), en.getValue(), i);
                if (!q.kind().equals(kind)) continue;
                double v = d.counters.merge(key(en.getKey(), i, "p"), 1.0, Double::sum);
                if (v == q.amount()) Text.actionBar(k, "&e의뢰 달성: &f" + q.desc());
            }
        }
    }

    /** AI 가 꺼진 NPC 는 중력이 없어 공중에 남으므로, 발밑이 비어 있으면 땅(나뭇잎 제외)으로 옮김 */
    private void groundNpcs() {
        // (수정) 이전 버전은 건물 안 상점 NPC 까지 지붕 위로 옮겨 "사라진" 것처럼 보였음 → 의뢰 NPC 만, 아래로만 내림
        for (World w : Bukkit.getWorlds())
            for (Villager v : w.getEntitiesByClass(Villager.class)) {
                if (v.hasAI() || !v.getPersistentDataContainer().has(KEY, PersistentDataType.STRING)) continue;
                Location l = v.getLocation();
                if (!l.clone().add(0, -0.2, 0).getBlock().isPassable()) continue;
                Location to = l.clone();
                for (int i2 = 0; i2 < 40 && to.getBlockY() > w.getMinHeight(); i2++) {
                    if (!to.clone().add(0, -1, 0).getBlock().isPassable()) break;
                    to.add(0, -1, 0);
                }
                to.setY(to.getBlockY());
                if (to.getY() < l.getY() - 0.5) v.teleport(to);
            }
    }

    public void onGather(Player p) { progress(p, "GATHER"); }

    public void onFish(Player p) { progress(p, "FISH"); }

    /** 서버를 켤 때 의뢰 NPC 가 목표 수보다 적으면 맵 곳곳에 채워 넣음 */
    public void autoFill() {
        int target = plugin.getConfig().getInt("quest-npcs.target", 60);
        if (npcs.size() >= target) return;
        int made = scatter(Bukkit.getWorlds().get(0), target - npcs.size());
        plugin.getLogger().info("[QuestNPC] placed " + made);
    }

    /** 지난 날짜의 의뢰 진행 기록 정리 (데이터 파일이 커지지 않게) */
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        String today = "_" + day() + "_";
        plugin.data().get(e.getPlayer()).counters.keySet().removeIf(k -> k.startsWith("qn_") && !k.contains(today));
    }

    public int count() {
        return npcs.size();
    }
}
