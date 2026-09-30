package kr.rpgcraft.minigame;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.scheduler.BukkitTask;

/** 미니게임 공통: 창 안에서 돌아가는 게임. 창을 닫으면 그만둠 */
public abstract class MiniGame extends Gui {
    protected final RpgCraft plugin;
    protected final MiniGameManager mgr;
    protected final Player p;
    protected final Diff diff;
    protected final Game game;
    protected BukkitTask task;
    protected boolean over;
    protected int ticks;

    protected MiniGame(MiniGameManager mgr, Player p, Game game, Diff diff, String title, String bg) {
        super(6, title, bg);
        this.mgr = mgr;
        this.plugin = mgr.plugin();
        this.p = p;
        this.game = game;
        this.diff = diff;
    }

    /** 첫 화면 그리기 */
    protected abstract void setup();

    /** period 틱마다 */
    protected abstract void tick();

    protected int period() {
        return 2;
    }

    public void begin() {
        setup();
        open(p);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (over) return;
            if (!p.isOnline() || !(p.getOpenInventory().getTopInventory().getHolder() == this)) { quit(); return; }
            ticks += period();
            tick();
        }, 10L, period());
    }

    private void quit() {
        if (over) return;
        over = true;
        if (task != null) task.cancel();
        if (p.isOnline()) Text.msg(p, "&7" + game.label + " 을(를) 그만뒀습니다.");
    }

    /** 끝: 이기면 코인 */
    protected void finish(boolean win, String why) {
        if (over) return;
        over = true;
        if (task != null) task.cancel();
        if (win) {
            long got = mgr.reward(p, game, diff);
            p.sendTitle(Text.c("&a&l성공!"), Text.c(game.coin.color + game.coin.label + " +" + got), 0, 40, 10);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        } else {
            p.sendTitle(Text.c("&c&l실패"), Text.c("&7" + why), 0, 40, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.9f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {   // 결과를 잠깐 보여준 뒤 난이도 선택으로
            if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == this) mgr.openDifficulty(p, game);
        }, 50L);
    }

    @Override
    public void onClose(InventoryCloseEvent e) {
        // 다음 틱 검사에서 그만둠 처리 (다른 창으로 넘어가는 경우 포함)
    }

    protected String timeLeft(int limitTicks) {
        int s = Math.max(0, (limitTicks - ticks + 19) / 20);
        return s + "초";
    }
}
