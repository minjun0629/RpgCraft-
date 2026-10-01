package kr.rpgcraft.board;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** v5.10.45 보드게임 공통: 창 안에서 컴퓨터와 1대1. 창을 닫으면 그 판은 기권 (보상 없음) */
public abstract class BoardGameBase extends Gui {
    protected final BoardManager mgr;
    protected final RpgCraft plugin;
    protected final Player p;
    protected final BoardManager.BoardGame game;
    protected BukkitTask task;
    protected boolean over;
    protected int ticks;
    /** 컴퓨터 차례 등 기다리는 동안 클릭 막기 */
    protected boolean busy;

    protected BoardGameBase(BoardManager mgr, Player p, BoardManager.BoardGame game, String title) {
        super(6, title);
        this.mgr = mgr;
        this.plugin = mgr.plugin();
        this.p = p;
        this.game = game;
    }

    protected abstract void setup();

    protected void tick() {
    }

    public void begin() {
        setup();
        open(p);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (over) return;
            if (!p.isOnline() || p.getOpenInventory().getTopInventory().getHolder() != this) { quit(); return; }
            ticks += 2;
            tick();
        }, 10L, 2L);
    }

    private void quit() {
        if (over) return;
        over = true;
        if (task != null) task.cancel();
        if (p.isOnline()) Text.msg(p, "&7" + game.label + " 을(를) 그만뒀습니다. &8(기권 · 보상 없음)");
    }

    /** 몇 틱 뒤에 (게임이 끝났거나 창을 닫았으면 안 함) */
    protected void later(long delay, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!over && p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == this) r.run();
        }, delay);
    }

    protected void finish(boolean win, String why) {
        if (over) return;
        over = true;
        if (task != null) task.cancel();
        mgr.reward(p, game, win);
        if (win) {
            p.sendTitle(Text.c("&a&l승리!"), Text.c("&7" + why), 0, 50, 10);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        } else {
            p.sendTitle(Text.c("&c&l패배"), Text.c("&7" + why), 0, 50, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.9f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == this) mgr.openHub(p);
        }, 70L);
    }

    protected void say(String msg) {
        Text.actionBar(p, msg);
    }
}
