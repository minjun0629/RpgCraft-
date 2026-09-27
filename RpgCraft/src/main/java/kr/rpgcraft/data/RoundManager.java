package kr.rpgcraft.data;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

/** 회차(라운드). 회차당 제한(단련된 공포, 유적, 떠돌이 상인 재고, 전쟁 선포 횟수)이 여기에 묶인다. */
public class RoundManager {
    private final RpgCraft plugin;
    private final File file;
    private final YamlConfiguration state;

    public RoundManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "state.yml");
        this.state = YamlConfiguration.loadConfiguration(file);
    }

    public int round() {
        return state.getInt("round", 1);
    }

    public YamlConfiguration state() {
        return state;
    }

    public void next() {
        state.set("round", round() + 1);
        state.set("shop-stock", null);
        state.set("war-declares", null);
        save();
        kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&e&l" + round() + "회차&f가 시작되었습니다! 회차 제한이 초기화됩니다."));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(Text.c("&6&l" + round() + "회차"), Text.c("&fRpgCraft"), 10, 50, 10);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    public void save() {
        try {
            state.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("state.yml 저장 실패: " + e.getMessage());
        }
    }
}
