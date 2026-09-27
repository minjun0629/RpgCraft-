package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 행운의 룰렛 (게임 머니 전용, 현금 결제와 무관).
 * 창 테두리 24칸이 룰렛 판 — 불빛(▼)이 빠르게 돌다가 점점 느려지며 멈춘 칸의 배율을 받는다.
 * 실제 확률: ×0 45% · ×0.5 20% · ×1.5 20% · ×2 10% · ×5 4.5% · ×10 0.5% → 기대값 약 0.88 (조금씩 잃는 구조)
 * 1회 최대 베팅 · 하루 베팅 한도로 서버 경제를 보호한다.
 */
public class CasinoManager {
    private static final double[] MULT = {0, 0.5, 1.5, 2, 5, 10};
    private static final double[] PROB = {0.45, 0.20, 0.20, 0.10, 0.045, 0.005};
    private static final Material[] ICON = {Material.COAL, Material.IRON_NUGGET, Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.NETHER_STAR};
    private static final String[] COLOR = {"&8", "&7", "&f", "&e", "&b", "&d&l"};
    /** 테두리 24칸 (시계 방향) */
    private static final int[] RING = {0, 1, 2, 3, 4, 5, 6, 7, 8, 17, 26, 35, 44, 43, 42, 41, 40, 39, 38, 37, 36, 27, 18, 9};
    /** 판 위 배율 배치 (칸 수가 곧 보이는 비율, 실제 확률은 PROB) */
    private static final int[] BOARD = {0, 2, 0, 1, 0, 3, 0, 2, 1, 0, 5, 0, 2, 0, 1, 0, 3, 0, 2, 1, 0, 4, 0, 2};

    private final RpgCraft plugin;

    public CasinoManager(RpgCraft plugin) {
        this.plugin = plugin;
    }

    private long dailyLimit() {
        return plugin.getConfig().getLong("casino.daily-limit", 500_000);
    }

    private long spentToday(PlayerData d) {
        double day = LocalDate.now().toEpochDay();
        if (d.counter("casino_day") != day) {
            d.counters.put("casino_day", day);
            d.counters.put("casino_spent", 0.0);
        }
        return (long) d.counter("casino_spent");
    }

    public void open(Player p) {
        new RouletteGui(p).open(p);
    }

    private static String multText(int i) {
        double m = MULT[i];
        return "×" + (m == (int) m ? String.valueOf((int) m) : String.valueOf(m));
    }

    private class RouletteGui extends Gui {
        private final Player p;
        private boolean spinning;
        private int pointer = -1;

        RouletteGui(Player p) {
            super(5, "&8행운의 룰렛");
            this.p = p;
            draw(-1, false);
        }

        void draw(int lit, boolean flash) {
            for (int i = 0; i < RING.length; i++) {
                int b = BOARD[i];
                boolean on = i == lit;
                ItemStack it = button(on ? (flash ? Material.GLOWSTONE : Material.LIME_STAINED_GLASS) : ICON[b],
                        (on ? "&a&l▶ " : "") + COLOR[b] + multText(b), "&7당첨 확률 " + String.format("%.1f", PROB[b] * 100) + "%");
                set(RING[i], it);
            }
            PlayerData d = plugin.data().get(p);
            long[] bets = {1_000, 10_000, 50_000, plugin.getConfig().getLong("casino.max-bet", 100_000)};
            int[] slots = {20, 21, 23, 24};
            for (int i = 0; i < 4; i++) {
                long bet = bets[i];
                set(slots[i], button(spinning ? Material.GRAY_DYE : Material.GOLD_NUGGET, (spinning ? "&7" : "&e") + Text.money(bet) + " 걸고 돌리기",
                        "&7소지금 " + Text.money(d.money), "&7오늘 사용 " + Text.money(spentToday(d)) + " / " + Text.money(dailyLimit())), e -> spin(bet));
            }
            set(22, button(spinning ? Material.CLOCK : Material.SUNFLOWER, spinning ? "&e&l룰렛이 돌아가는 중..." : "&6&l행운의 룰렛",
                    "&7금액을 고르면 판이 돌아갑니다", "&8평균적으로는 조금 잃는 게임입니다. 적당히!"));
            set(31, button(Material.PAPER, "&f배율표", "&8×0 &7(45%)  &7×0.5 (20%)  &f×1.5 (20%)", "&e×2 (10%)  &b×5 (4.5%)  &d&l×10 &7(0.5%)"));
            fill(10, 34);
        }

        void spin(long bet) {
            if (spinning) return;
            PlayerData d = plugin.data().get(p);
            if (spentToday(d) + bet > dailyLimit()) { Text.msg(p, "&c오늘의 베팅 한도를 넘었습니다. (" + Text.money(dailyLimit()) + ")"); return; }
            if (!plugin.economy().take(p, bet)) { Text.msg(p, "&c소지금이 부족합니다."); return; }
            d.counters.merge("casino_spent", (double) bet, Double::sum);
            // 결과는 확률표로 먼저 정하고, 그 배율이 적힌 칸 중 하나에 멈추도록 연출
            double r = ThreadLocalRandom.current().nextDouble(), acc = 0;
            int result = 0;
            for (int i = 0; i < PROB.length; i++) { acc += PROB[i]; if (r < acc) { result = i; break; } }
            List<Integer> cells = new ArrayList<>();
            for (int i = 0; i < BOARD.length; i++) if (BOARD[i] == result) cells.add(i);
            int target = cells.get(ThreadLocalRandom.current().nextInt(cells.size()));
            if (pointer < 0) pointer = 0;
            int start = pointer;
            int steps = RING.length * 3 + ((target - start + RING.length) % RING.length);
            spinning = true;
            int res = result;
            new BukkitRunnable() {
                int done = 0, wait = 0;

                @Override
                public void run() {
                    if (!p.isOnline()) { cancel(); return; }
                    if (wait-- > 0) return;
                    pointer = (pointer + 1) % RING.length;
                    done++;
                    draw(pointer, false);
                    float pitch = 0.6f + 1.2f * done / steps;
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.9f, pitch);
                    int left = steps - done;
                    wait = left > 18 ? 0 : left > 10 ? 1 : left > 5 ? 2 : left > 2 ? 4 : 6;   // 점점 느려짐
                    if (done >= steps) {
                        cancel();
                        finish(bet, res);
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        void finish(long bet, int res) {
            long win = Math.round(bet * MULT[res]);
            if (win > 0) plugin.economy().give(p, win);
            new BukkitRunnable() {  // 멈춘 칸 깜빡임
                int n = 0;

                @Override
                public void run() {
                    if (!p.isOnline() || n++ >= 6) {
                        cancel();
                        spinning = false;
                        if (p.isOnline()) draw(pointer, false);
                        return;
                    }
                    draw(pointer, n % 2 == 1);
                }
            }.runTaskTimer(plugin, 0L, 4L);
            if (MULT[res] >= 1.5) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, MULT[res] >= 5 ? 0.8f : 1.4f);
            else p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            p.sendTitle("", Text.c(win > bet ? "&a&l" + multText(res) + " 당첨! &f+" + Text.money(win) : win > 0 ? "&e" + multText(res) + " &7(" + Text.money(win) + ")" : "&7꽝..."), 0, 30, 10);
            if (MULT[res] >= 5) {
                plugin.data().get(p).counters.merge("ach_jackpot", 1.0, Double::sum);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                Text.announce(Text.PREFIX + Text.c("&e" + Text.name(p) + "&f님이 행운의 룰렛에서 &6" + multText(res) + "&f 대박! &7(+" + Text.money(win) + ")"));
            }
        }
    }
}
