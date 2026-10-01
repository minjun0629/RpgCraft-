package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
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
import java.util.List;
import java.util.Random;

/**
 * (숨김) 맵 먼 곳의 이름 없는 NPC 6명. 설명은 하지 않는다.
 */
public class HiddenJobManager implements Listener {
    public record Tier(String line, int tier, String label, String[] need, StatMap bonus) {}

    public static final List<Tier> TIERS = List.of(
            // v5.3.0: 히든 직업은 원래 직업을 "대신" 하므로, 잃는 직업 보너스만큼 모든 스탯 % 를 더 준다
            new Tier("A", 1, "망령 사냥꾼", new String[]{"#lv:50", "spirit_summon:1", "loot_dust:50"}, StatMap.of(Stat.LIFESTEAL, 3, Stat.MAGIC, 300, Stat.STR_PCT, 12, Stat.DEX_PCT, 12, Stat.ADV_PCT, 12, Stat.HP_PCT, 8)),
            new Tier("A", 2, "영혼 수확자", new String[]{"#lv:120", "#ach_boss:10", "loot_eye:10"}, StatMap.of(Stat.LIFESTEAL, 6, Stat.MAGIC, 900, Stat.HP_PCT, 20, Stat.STR_PCT, 20, Stat.DEX_PCT, 20, Stat.ADV_PCT, 20)),
            new Tier("A", 3, "명계의 군주", new String[]{"#lv:200", "#ach_boss:30", "loot_core:30"}, StatMap.of(Stat.LIFESTEAL, 10, Stat.MAGIC, 2000, Stat.HP_PCT, 35, Stat.STR_PCT, 30, Stat.DEX_PCT, 30, Stat.ADV_PCT, 30)),
            new Tier("B", 1, "별빛 방랑자", new String[]{"#lv:50", "#ach_treasure:10", "loot_frost:30"}, StatMap.of(Stat.CRIT, 8, Stat.SPEED, 8, Stat.STR_PCT, 12, Stat.DEX_PCT, 12, Stat.ADV_PCT, 12, Stat.HP_PCT, 8)),
            new Tier("B", 2, "성운 기사", new String[]{"#lv:120", "#ach_elite:300", "loot_scale:30"}, StatMap.of(Stat.CRIT, 12, Stat.CRIT_DMG, 40, Stat.DODGE, 6, Stat.STR_PCT, 20, Stat.DEX_PCT, 20, Stat.ADV_PCT, 20, Stat.HP_PCT, 12)),
            new Tier("B", 3, "천구의 주재자", new String[]{"#lv:200", "#ach_boss:30", "loot_crown:10"}, StatMap.of(Stat.CRIT, 18, Stat.CRIT_DMG, 90, Stat.DODGE, 10, Stat.STR_PCT, 30, Stat.DEX_PCT, 30, Stat.ADV_PCT, 30, Stat.HP_PCT, 20)),
            // v5.10.9 네크로맨서: 혼자서는 약하고(능력치 보너스 낮음) 영혼으로 일으킨 군단으로 싸움. 조건도 가장 어려움 (NecromancyManager)
            new Tier("C", 1, "네크로맨서", new String[]{"#lv:120", "#ach_boss:30", "#ach_elite:500", "loot_core:40"}, StatMap.of(Stat.MAGIC, 200, Stat.STR_PCT, 6, Stat.DEX_PCT, 6, Stat.ADV_PCT, 6, Stat.HP_PCT, 6)),
            new Tier("C", 2, "해골 군단장", new String[]{"#lv:200", "#ach_boss:80", "loot_crown:20", "loot_eye:60"}, StatMap.of(Stat.MAGIC, 600, Stat.STR_PCT, 10, Stat.DEX_PCT, 10, Stat.ADV_PCT, 10, Stat.HP_PCT, 10)),
            new Tier("C", 3, "죽음의 대군주", new String[]{"#lv:270", "#ach_boss:150", "loot_crown:50", "loot_core:100"}, StatMap.of(Stat.MAGIC, 1400, Stat.STR_PCT, 16, Stat.DEX_PCT, 16, Stat.ADV_PCT, 16, Stat.HP_PCT, 16)),
            // v5.10.18 시간술사: 되감기 · 시간 가속 · 시간 역행 (ChronoManager)
            new Tier("D", 1, "시간 방랑자", new String[]{"#lv:80", "#ach_treasure:30", "#ach_elite:200", "loot_eye:30"}, StatMap.of(Stat.SPEED, 10, Stat.DODGE, 5, Stat.CRIT, 5, Stat.STR_PCT, 12, Stat.DEX_PCT, 12, Stat.ADV_PCT, 12, Stat.HP_PCT, 8)),
            new Tier("D", 2, "시간술사", new String[]{"#lv:150", "#ach_boss:40", "loot_core:40", "loot_frost:40"}, StatMap.of(Stat.SPEED, 15, Stat.DODGE, 8, Stat.CRIT, 8, Stat.CRIT_DMG, 30, Stat.STR_PCT, 20, Stat.DEX_PCT, 20, Stat.ADV_PCT, 20, Stat.HP_PCT, 14)),
            new Tier("D", 3, "시간의 지배자", new String[]{"#lv:230", "#ach_boss:100", "loot_crown:30", "loot_core:60"}, StatMap.of(Stat.SPEED, 20, Stat.DODGE, 12, Stat.CRIT, 12, Stat.CRIT_DMG, 60, Stat.STR_PCT, 30, Stat.DEX_PCT, 30, Stat.ADV_PCT, 30, Stat.HP_PCT, 20)));

    /** 히든 전직서 아이템 ID (단계마다 하나) */
    public static String scrollId(Tier t) {
        return "hj_scroll_" + t.line() + t.tier();
    }

    private static final NamespacedKey OWNER = new NamespacedKey("rpgcraft", "hj_owner");

    private final RpgCraft plugin;
    private final NamespacedKey KEY;
    private final File file;
    private final YamlConfiguration data;

    public HiddenJobManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "hj_npc");
        this.file = new File(plugin.getDataFolder(), "hidden_jobs.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Bukkit.getScheduler().runTaskLater(plugin, this::ensurePlaced, 260L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> respawnMissing(null), 460L);   // 사라진 NPC 는 원래 자리에 다시
    }

    public static Tier of(PlayerData d) {
        String line = d.counters.containsKey("hj_line_A") ? "A" : d.counters.containsKey("hj_line_B") ? "B" : d.counters.containsKey("hj_line_C") ? "C" : d.counters.containsKey("hj_line_D") ? "D" : null;
        if (line == null) return null;
        int t = (int) d.counter("hj_tier");
        for (Tier x : TIERS) if (x.line().equals(line) && x.tier() == t) return x;
        return null;
    }

    public static StatMap bonus(PlayerData d) {
        Tier t = of(d);
        return t == null ? new StatMap() : t.bonus();
    }

    private void ensurePlaced() {
        World w = Bukkit.getWorlds().get(0);
        Random r = new Random();
        for (Tier t : TIERS) {
            String id = t.line() + t.tier();
            if (data.contains("placed." + id)) continue;
            for (int i = 0; i < 30; i++) {
                double a = r.nextDouble() * Math.PI * 2, dd = t.line().equals("C") ? 3500 + r.nextDouble() * 2500 : t.line().equals("D") ? 2500 + r.nextDouble() * 2500 : 1800 + r.nextDouble() * 2600;   // 네크로맨서는 더 먼 곳
                Location l = w.getSpawnLocation().clone().add(Math.cos(a) * dd, 0, Math.sin(a) * dd);
                w.getChunkAt(l).load(true);
                Block top = kr.rpgcraft.util.Locs.surface(w, l);
                if (top.isLiquid()) continue;
                spawnNpc(w, t, top);
                data.set("placed." + id, top.getX() + "," + top.getZ());
                save();
                break;
            }
        }
    }

    private void spawnNpc(World w, Tier t, Block top) {
        String id = t.line() + t.tier();
        w.spawn(top.getLocation().add(0.5, 1, 0.5), Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setProfession(t.line().equals("A") ? Villager.Profession.CLERIC : t.line().equals("C") ? Villager.Profession.NITWIT : t.line().equals("D") ? Villager.Profession.LIBRARIAN : Villager.Profession.CARTOGRAPHER);
            v.setVillagerLevel(5);
            v.setCustomName(Text.c("&8…"));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
        });
    }

    /** 배치 기록은 있는데 NPC 가 없어졌으면 같은 자리에 다시 세운다 */
    public void respawnMissing(org.bukkit.command.CommandSender who) {
        World w = Bukkit.getWorlds().get(0);
        for (Tier t : TIERS) {
            String id = t.line() + t.tier();
            String xz = data.getString("placed." + id);
            if (xz == null) continue;
            String[] v = xz.split(",");
            int x = Integer.parseInt(v[0].trim()), z = Integer.parseInt(v[1].trim());
            kr.rpgcraft.util.NpcRespawn.ensure(plugin, w, x, z, KEY, id, top -> spawnNpc(w, t, top), again -> {
                if (!again) return;
                String msg = "숨은 직업 NPC " + id + " 다시 배치: " + x + ", " + z;
                if (who != null) who.sendMessage(Text.c("&5" + msg));
                else plugin.getLogger().info(msg);
            });
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ignored) {
        }
    }

    public List<String> locations() {
        List<String> out = new java.util.ArrayList<>();
        var sec = data.getConfigurationSection("placed");
        if (sec != null) for (String k : sec.getKeys(false)) out.add(k + " " + sec.getString(k));
        return out;
    }

    private int count(Player p, String id) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (id.equals(ItemData.id(it))) n += it.getAmount();
        return n;
    }

    private boolean meets(Player p, PlayerData d, String need) {
        String[] kv = need.split(":");
        int n = Integer.parseInt(kv[1]);
        if (kv[0].equals("#lv")) return d.level >= n;
        if (kv[0].startsWith("#")) return d.counter(kv[0].substring(1)) >= n;
        return count(p, kv[0]) >= n;
    }

    // ------------------------------------------------------------------ v5.10.17 히든 직업 전용 직업창
    private static final String[] LINE_NAME = {"망령의 길", "별의 길", "죽음의 길", "시간의 길"};
    private static final String[][] PERK = {
            {"처치 시 흡혈", "처치 시 체력 5% 회복", "처치 시 체력 회복 + 영혼 폭발 (주변 적에게 피해)"},
            {"치명타 · 이동 속도", "치명타 피해 · 회피", "치명타가 터지면 별똥별이 떨어짐"},
            {"영혼으로 군단원 3기", "군단원 5기 · 군단 능력치 +20%", "군단원 8기 · 군단 능력치 +45%"},
            {"처치 시 모든 재사용 대기 -10%", "처치 시 대기 -15% · 되감기 자리에 시간 균열", "처치 시 대기 -20% · 도착 지점 시간 정지 · 체력 20% 아래면 시간 역행"}};
    private static final String[][] SKILL = {{"영혼 수확", "영혼 수확", "명계 강림"}, {"유성 낙하", "유성 낙하", "천구 붕괴"}, {"시체 폭발", "시체 폭발", "죽음의 행진"}, {"되감기", "되감기 · 시간 균열", "되감기 · 시간 정지"}};

    private static int lineIdx(String line) {
        return line.equals("A") ? 0 : line.equals("B") ? 1 : line.equals("C") ? 2 : 3;
    }

    private static String bonusLine(StatMap m) {
        StringBuilder sb = new StringBuilder();
        for (var en : m.entries()) {
            if (sb.length() > 0) sb.append(" &8· ");
            sb.append("&f").append(en.getKey().label).append(" &a+").append(Text.num(en.getValue())).append(en.getKey().pct ? "%" : "");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ v5.10.20 히든 직업 전용 무기
    private static final String[] WEAPON_NAME = {"명계의 낫", "성운검 스텔라", "망자의 홀", "시간의 바늘"};

    /** 들고 있는 전용 무기의 계열 (A~D), 아니면 null */
    public String heldWeaponLine(Player p) {
        String id = ItemData.id(p.getInventory().getItemInMainHand());
        if (id == null || !id.startsWith("hjw_")) return null;
        Tier t = of(plugin.data().get(p));
        String line = id.substring(4, 5).toUpperCase(java.util.Locale.ROOT);
        return t != null && t.line().equals(line) ? line : null;
    }

    /** 근접 공격이 맞았을 때 (CombatService.afterHit) */
    public void weaponHit(Player p, LivingEntity v, double dealt, boolean crit) {
        String line = heldWeaponLine(p);
        if (line == null) return;
        PlayerData d = plugin.data().get(p);
        int tr = of(d).tier();
        var r = java.util.concurrent.ThreadLocalRandom.current();
        double atk = Math.max(d.stats.attack, d.stats.magic);
        switch (line) {
            case "A" -> {   // 영혼 베기
                if (r.nextDouble() >= 0.15 || !v.isValid() || v.isDead()) return;
                kr.rpgcraft.util.Vfx.slash(v.getLocation().add(0, 1, 0), p.getLocation().getDirection().setY(0), 3, 30, Color.fromRGB(0x7FE8FF));
                v.getWorld().playSound(v.getLocation(), Sound.ENTITY_VEX_CHARGE, 1f, 0.6f);
                plugin.combat().dealSkillDamage(p, v, atk * (0.8 + 0.4 * tr), false);
                plugin.health().healPercent(p, 3);
            }
            case "B" -> {   // 별빛 일격
                if (!crit || r.nextDouble() >= 0.25 || !v.isValid()) return;
                Location at = v.getLocation();
                kr.rpgcraft.util.Vfx.beam(at.clone().add(0, 9, 0), at.clone().add(0, 0.5, 0), 0.9, Color.fromRGB(0xFFE9A0));
                at.getWorld().playSound(at, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1.4f);
                for (var en : at.getWorld().getNearbyEntities(at, 3, 3, 3))
                    if (en instanceof LivingEntity le && plugin.combat().isEnemy(p, le))
                        plugin.combat().dealSkillDamage(p, le, atk * (1 + 0.5 * tr) * (le.equals(v) ? 1 : 0.5), false);
            }
            case "C" -> {   // 군단 회복 (공격력 증가는 NecromancyManager 가 확인)
                if (r.nextDouble() < 0.2 && plugin.necro() != null) plugin.necro().heal(p, 0.05);
            }
            case "D" -> {   // 초침: 되감기 대기 감소
                long until = d.cooldowns.getOrDefault("job_skill", 0L);
                if (until > System.currentTimeMillis()) d.cooldowns.put("job_skill", until - 250L * tr);
            }
            default -> { }
        }
    }

    /** 히든 직업창에서 전용 무기 만들기 */
    private void craftWeapon(Player p, Tier cur) {
        long money = plugin.getConfig().getLong("hidden-weapon.money", 30_000_000);
        int cores = plugin.getConfig().getInt("hidden-weapon.cores", 20), crystals = plugin.getConfig().getInt("hidden-weapon.crystals", 10);
        if (count(p, "loot_core") < cores || count(p, "crystal_high") < crystals) { Text.msg(p, "&c재료가 부족합니다. &7(마력 핵 " + cores + " · 상급 결정 " + crystals + ")"); return; }
        if (!plugin.economy().take(p, money)) { Text.msg(p, "&c돈이 부족합니다. &7(" + Text.money(money) + ")"); return; }
        takeItem(p, "loot_core", cores);
        takeItem(p, "crystal_high", crystals);
        ItemStack it = plugin.items().create("hjw_" + cur.line().toLowerCase(java.util.Locale.ROOT), 1);
        if (it != null) for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        p.getWorld().strikeLightningEffect(p.getLocation());
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 0.6f);
        Text.announce(Text.PREFIX + Text.c("&5" + Text.name(p) + "&f님이 히든 직업 전용 무기 &5&l" + WEAPON_NAME[lineIdx(cur.line())] + "&f을(를) 손에 넣었습니다!"));
    }

    private void takeItem(Player p, String id, int n) {
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (n <= 0) return;
            if (!id.equals(ItemData.id(it))) continue;
            int t = Math.min(n, it.getAmount());
            it.setAmount(it.getAmount() - t);
            n -= t;
        }
    }

    /** 히든 직업이면 일반 직업창 대신 이 창 (1 · 2 · 3단계 · 조건 · 효과 · 전용 기능) */
    public void openJobWindow(Player p) {
        PlayerData d = plugin.data().get(p);
        Tier cur = of(d);
        if (cur == null) return;
        int li = lineIdx(cur.line());
        Gui g = new Gui(4, "&5&l✦ 히든 직업 ✦") {
        };
        Material icon = li == 0 ? Material.WITHER_SKELETON_SKULL : li == 1 ? Material.NETHER_STAR : li == 2 ? Material.SKELETON_SKULL : Material.CLOCK;
        List<String> head = new java.util.ArrayList<>();
        head.add("&8" + LINE_NAME[li] + " · " + cur.tier() + "단계");
        head.add("");
        head.add("&d능력치 보너스");
        head.add(" " + bonusLine(cur.bonus()));
        head.add("");
        head.add("&d고유 효과");
        head.add(" &f" + PERK[li][cur.tier() - 1]);
        head.add("");
        head.add("&d직업 스킬 (Q)");
        head.add(" &f" + SKILL[li][cur.tier() - 1] + (li == 2 ? " &7+ 군단 체력 25% 회복" : ""));
        head.add("");
        head.add("&8원래 직업(1 · 2 · 3차)의 능력은 쓰지 않는다.");
        g.set(4, Gui.button(icon, "&5&l" + cur.label(), head.toArray(new String[0])), null);
        int[] slots = {11, 13, 15};
        for (Tier t : TIERS) {
            if (!t.line().equals(cur.line())) continue;
            List<String> lore = new java.util.ArrayList<>();
            boolean done = t.tier() <= cur.tier(), next = t.tier() == cur.tier() + 1;
            lore.add("&8" + t.tier() + "단계");
            lore.add("");
            lore.add("&7보너스: " + bonusLine(t.bonus()));
            lore.add("&7효과: &f" + PERK[li][t.tier() - 1]);
            lore.add("");
            Material m;
            String name;
            if (t.tier() == cur.tier()) { m = Material.ENCHANTED_BOOK; name = "&d&l" + t.label() + " &a(현재)"; lore.add("&a▶ 지금 이 길을 걷고 있다"); }
            else if (done) { m = Material.BOOK; name = "&7" + t.label() + " &8(지나온 길)"; lore.add("&8이미 지나온 단계"); }
            else if (next) {
                m = Material.WRITABLE_BOOK;
                name = "&5&l" + t.label() + " &e(다음)";
                lore.add("&d승급 조건");
                for (String n : t.need()) lore.add((meets(p, d, n) ? " &a✔ " : " &c✘ ") + label(n));
                lore.add("");
                if (d.counter("hj_issued_" + t.line() + t.tier()) > 0) lore.add("&e히든 전직서를 들고 우클릭하면 승급");
                else {
                    lore.add("&8조건을 갖추고 이 단계의 이름 없는 자를 찾아가라…");
                    boolean all = true;
                    for (String n : t.need()) all &= meets(p, d, n);
                    lore.addAll(hint(p, t, all));
                }
            } else { m = Material.BLACK_STAINED_GLASS_PANE; name = "&8???"; lore.clear(); lore.add("&8아직 보이지 않는 길"); }
            g.set(slots[t.tier() - 1], Gui.button(m, name, lore.toArray(new String[0])), null);
        }
        if (li == 2 && plugin.necro() != null)
            g.set(31, Gui.button(Material.SOUL_LANTERN, "&5&l☠ 사령 군단", "&7영혼으로 일으킨 군단원 관리 · 소환", "&e▶ 클릭 (/군단)"), e -> plugin.necro().open(p));
        {   // v5.10.20 전용 무기
            var wt = plugin.items().get("hjw_" + cur.line().toLowerCase(java.util.Locale.ROOT));
            long money = plugin.getConfig().getLong("hidden-weapon.money", 30_000_000);
            int cores = plugin.getConfig().getInt("hidden-weapon.cores", 20), crystals = plugin.getConfig().getInt("hidden-weapon.crystals", 10);
            List<String> wl = new java.util.ArrayList<>();
            wl.add("&8이 길을 걷는 자만 쥘 수 있다");
            if (wt != null) for (String l : wt.desc) wl.add("&7" + l);
            wl.add("");
            wl.add("&d제작 재료");
            wl.add((count(p, "loot_core") >= cores ? " &a✔ " : " &c✘ ") + "&f마력 핵 " + count(p, "loot_core") + "/" + cores);
            wl.add((count(p, "crystal_high") >= crystals ? " &a✔ " : " &c✘ ") + "&f상급 결정 " + count(p, "crystal_high") + "/" + crystals);
            wl.add(" &f" + Text.money(money));
            wl.add("");
            wl.add("&e▶ 쉬프트 클릭하여 제작");
            g.set(22, Gui.button(wt == null ? Material.NETHERITE_SWORD : wt.material, "&5&l전용 무기: " + WEAPON_NAME[li], wl.toArray(new String[0])), e -> {
                if (!e.isShiftClick()) return;
                p.closeInventory();
                craftWeapon(p, cur);
            });
        }
        g.set(27, Gui.button(Material.PAPER, "&7히든 직업 안내", "&7히든 직업은 다른 직업으로 바꾸거나 초기화할 수 없다.",
                "&7다음 단계는 직업창이 아니라 맵 먼 곳의", "&7이름 없는 자에게서 받은 전직서로 오른다."), null);
        g.fill(0, 35);
        g.open(p);
    }

    /**
     * v5.10.19 다음 단계 NPC 의 좌표 힌트. 정확한 자리 대신 사람마다 다르게 흔든 대략의 좌표 (±150칸),
     * 조건을 모두 갖추면 더 가까운 힌트 (±40칸) + 스폰에서의 방향 · 거리
     */
    private List<String> hint(Player p, Tier t, boolean close) {
        List<String> out = new java.util.ArrayList<>();
        String xz = data.getString("placed." + t.line() + t.tier());
        if (xz == null) { out.add("&8(아직 이 자는 세상에 나타나지 않았다)"); return out; }
        String[] v = xz.split(",");
        int x, z;
        try { x = Integer.parseInt(v[0].trim()); z = Integer.parseInt(v[1].trim()); } catch (Exception ex) { return out; }
        int err = close ? 40 : 150;
        Random r = new Random((p.getUniqueId().toString() + t.line() + t.tier() + close).hashCode());
        int hx = x + r.nextInt(-err, err + 1), hz = z + r.nextInt(-err, err + 1);
        int step = close ? 10 : 50;
        hx = Math.round(hx / (float) step) * step;
        hz = Math.round(hz / (float) step) * step;
        Location sp = Bukkit.getWorlds().get(0).getSpawnLocation();
        double dx = x - sp.getX(), dz = z - sp.getZ();
        String[] dirs = {"남", "남서", "서", "북서", "북", "북동", "동", "남동"};   // 마인크래프트: +Z 남쪽, +X 동쪽
        int oct = (int) Math.round(Math.toDegrees(Math.atan2(-dx, dz)) / 45.0);
        String dir = dirs[Math.floorMod(oct, 8)];
        int dist = (int) Math.round(Math.hypot(dx, dz) / 100.0) * 100;
        out.add("");
        out.add("&d좌표 힌트 " + (close ? "&a(가까운 힌트)" : "&7(대략)"));
        out.add(" &fX ≈ " + String.format("%,d", hx) + " &7· &fZ ≈ " + String.format("%,d", hz) + " &8(±" + err + "칸)");
        out.add(" &7스폰에서 &f" + dir + "쪽 &7약 &f" + String.format("%,d", dist) + "칸");
        if (!close) out.add(" &8조건을 모두 갖추면 더 정확한 힌트가 보인다");
        return out;
    }

    private String label(String need) {
        String[] kv = need.split(":");
        return switch (kv[0]) {
            case "#lv" -> "Lv." + kv[1];
            case "#ach_boss" -> "보스 " + kv[1];
            case "#ach_elite" -> "정예 " + kv[1];
            case "#ach_treasure" -> "보물 " + kv[1];
            default -> plugin.items().get(kv[0]).name + " " + kv[1];
        };
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        String id = e.getRightClicked().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        Tier t = TIERS.stream().filter(x -> (x.line() + x.tier()).equals(id)).findFirst().orElse(null);
        if (t == null) return;
        Tier cur = of(d);
        if (cur != null && cur.line().equals(t.line()) && cur.tier() >= t.tier()) return;   // 이미 깬 히든 NPC 는 말을 걸 수 없음 (v5.3.6)
        // 순서: 같은 줄기의 바로 앞 단계여야 함 (1단계는 아무 숨은 길도 걷지 않은 사람만)
        boolean ready = t.tier() == 1 ? cur == null : cur != null && cur.line().equals(t.line()) && cur.tier() == t.tier() - 1;
        Gui g = new Gui(3, "&8…") {
        };
        if (ready && d.counter("hj_issued_" + t.line() + t.tier()) > 0) {   // 이미 퀘스트를 깼음: 전직서를 잃어버렸으면 다시 줌
            boolean holding = false;
            for (ItemStack it : p.getInventory().getContents()) if (scrollId(t).equals(ItemData.id(it))) { holding = true; break; }
            if (!holding) giveScroll(p, t);
            Text.msg(p, "&d히든 전직서를 들고 우클릭하면 &5&l" + t.label() + "&d(으)로 전직합니다.");
            return;
        }
        if (!ready) {
            g.set(13, Gui.button(Material.GRAY_DYE, "&8…"), null);
            g.fill(0, 26);
            g.open(p);
            return;
        }
        List<String> lore = new java.util.ArrayList<>();
        boolean all = true;
        for (String n : t.need()) {
            boolean ok = meets(p, d, n);
            all &= ok;
            lore.add((ok ? "&a✔ " : "&c✘ ") + label(n));
        }
        boolean fAll = all;
        if (all) lore.add("&e▶");
        g.set(13, Gui.button(Material.NETHER_STAR, "&5???", lore.toArray(new String[0])), ev -> {
            if (!fAll) return;
            for (String n : t.need()) {
                String[] kv = n.split(":");
                if (kv[0].startsWith("#")) continue;
                int left = Integer.parseInt(kv[1]);
                for (ItemStack it : p.getInventory().getStorageContents()) {
                    if (left <= 0) break;
                    if (!kv[0].equals(ItemData.id(it))) continue;
                    int take = Math.min(left, it.getAmount());
                    it.setAmount(it.getAmount() - take);
                    left -= take;
                }
            }
            d.counters.put("hj_issued_" + t.line() + t.tier(), 1.0);
            p.closeInventory();
            giveScroll(p, t);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.6f);
            Text.msg(p, "&d✦ 히든 전직 퀘스트 완료! &f「히든 전직서: " + t.label() + "」&d를 받았습니다. &7(들고 우클릭하면 전직)");
        });
        g.fill(0, 26);
        g.open(p);
    }

    // ------------------------------------------------------------------ 히든 전직서 (v5.3.0)
    private void giveScroll(Player p, Tier t) {
        ItemStack it = plugin.items().create(scrollId(t), 1);
        if (it == null) return;
        var m = it.getItemMeta();
        m.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, p.getUniqueId().toString());
        List<String> lore = m.hasLore() ? new java.util.ArrayList<>(m.getLore()) : new java.util.ArrayList<>();
        lore.add(Text.c("&8주인: " + p.getName() + " (다른 사람은 사용 불가)"));
        m.setLore(lore);
        it.setItemMeta(m);
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    private Tier scrollTier(ItemStack it) {
        String id = ItemData.id(it);
        if (id == null || !id.startsWith("hj_scroll_")) return null;
        for (Tier t : TIERS) if (scrollId(t).equals(id)) return t;
        return null;
    }

    /** 전직서 우클릭 → 확인 창 → 원래 직업 대신 히든 직업으로 */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUseScroll(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        var a = e.getAction();
        if (a != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && a != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        Tier t = scrollTier(hand);
        if (t == null) return;
        e.setCancelled(true);
        String owner = hand.getItemMeta().getPersistentDataContainer().get(OWNER, PersistentDataType.STRING);
        if (owner != null && !owner.equals(p.getUniqueId().toString())) { Text.msg(p, "&c다른 사람의 전직서는 사용할 수 없습니다."); return; }
        PlayerData d = plugin.data().get(p);
        Tier cur = of(d);
        boolean ready = t.tier() == 1 ? cur == null : cur != null && cur.line().equals(t.line()) && cur.tier() == t.tier() - 1;
        if (!ready) { Text.msg(p, cur != null && cur.line().equals(t.line()) && cur.tier() >= t.tier() ? "&7이미 이 단계 이상의 히든 직업입니다." : "&c아직 이 전직서를 쓸 수 없습니다."); return; }
        String now = plugin.jobs().title(d);
        Gui g = new Gui(3, "&8히든 전직") {
        };
        g.set(11, Gui.button(Material.BARRIER, "&7취소"), ev -> p.closeInventory());
        g.set(15, Gui.button(Material.NETHER_STAR, "&5&l" + t.label() + " &d(으)로 전직",
                "&7현재 직업: &f" + now,
                t.tier() == 1 ? "&c원래 직업의 능력치 · 효과 · 직업 스킬은 사라지고" : "&7히든 직업이 한 단계 올라갑니다.",
                t.tier() == 1 ? "&c히든 직업으로 바뀝니다." : "",
                "", "&e▶ 클릭하여 전직"), ev -> {
            p.closeInventory();
            ItemStack h2 = p.getInventory().getItemInMainHand();
            if (scrollTier(h2) != t) return;
            Tier c2 = of(d);
            boolean ok = t.tier() == 1 ? c2 == null : c2 != null && c2.line().equals(t.line()) && c2.tier() == t.tier() - 1;
            if (!ok) return;
            h2.setAmount(h2.getAmount() - 1);
            d.counters.remove("hj_issued_" + t.line() + t.tier());
            promote(p, d, t);
        });
        g.fill(0, 26);
        g.open(p);
    }

    private void promote(Player p, PlayerData d, Tier t) {
        if (t.tier() == 1 && (d.job != null || d.subJob != null || d.thirdJob != null)) {   // 원래 직업은 기록만 남겨 둠 (관리자가 되돌릴 수 있게)
            d.counters.put("hj_prev_set", 1.0);
            plugin.getLogger().info("[히든 직업] " + p.getName() + " 원래 직업 " + d.job + "/" + d.subJob + "/" + d.thirdJob + " → " + t.label());
        }
        d.counters.remove("hj_line_A");
        d.counters.remove("hj_line_B");
        d.counters.remove("hj_line_C");
        d.counters.remove("hj_line_D");
        d.counters.put("hj_line_" + t.line(), 1.0);
        d.counters.put("hj_tier", (double) t.tier());
        plugin.stats().refresh(p);
        p.sendTitle(Text.c("&5&l" + t.label()), Text.c(t.tier() == 1 ? "&7히든 직업으로 전직했습니다" : "&7히든 직업 승급"), 10, 70, 20);
        if (plugin.content() != null) plugin.content().onHiddenJob(p, t.label());   // 히든 직업 칭호 · 공지
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.4f);
        p.getWorld().strikeLightningEffect(p.getLocation());
        if (t.line().equals("C")) Text.msg(p, "&5☠ &f/군단 &7— 쓰러뜨린 몬스터의 영혼으로 군단을 일으킬 수 있다.");
        if (t.line().equals("D")) Text.msg(p, "&b⟲ &f직업 스킬(Q) &b되감기 &7— 3초 전의 자리와 체력으로 돌아간다.");
    }

    /** 숨은 직업 전용 효과: 망령(A) 처치 시 회복 · 3단계는 영혼 폭발 / 별(B) 3단계는 치명타 때 별똥별 */
    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null) return;
        Tier t = of(plugin.data().get(k));
        if (t == null || !t.line().equals("A")) return;
        if (t.tier() >= 2) plugin.health().healPercent(k, 5);
        if (t.tier() >= 3) {
            Location at = e.getEntity().getLocation().add(0, 1, 0);
            kr.rpgcraft.util.Vfx.burst(at, 3.5, Color.fromRGB(0x7FE8FF));
            kr.rpgcraft.util.Vfx.ring(at.clone().add(0, -1, 0), 4, Color.fromRGB(0xC060FF));
            for (var en : at.getWorld().getNearbyEntities(at, 4, 3, 4))
                if (en instanceof LivingEntity le && !le.equals(e.getEntity()) && plugin.combat().isEnemy(k, le))
                    plugin.combat().dealSkillDamage(k, le, plugin.data().get(k).stats.magic * 0.5 + plugin.data().get(k).stats.attack * 0.3, false);
        }
    }

    public static boolean starfall(PlayerData d) {
        Tier t = of(d);
        return t != null && t.line().equals("B") && t.tier() >= 3;
    }
}
