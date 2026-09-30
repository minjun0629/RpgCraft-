package kr.rpgcraft.minigame;

import org.bukkit.Material;

/** 미니게임 종류 */
public enum Game {
    MOLE("두더지 잡기", Coin.EVENT, Icons.MOLE, "튀어나온 두더지를 클릭! 황금 두더지 +3, 폭탄은 -3", "제한 시간 30초 안에 목표 점수 넘기기"),
    BREAKOUT("벽돌깨기", Coin.EVENT, Icons.BRICK, "맨 아래 줄을 클릭하면 받침대가 그 자리로", "공을 떨어뜨리지 않고 벽돌을 모두 깨기"),
    MINES("지뢰찾기", Coin.EVENT, Icons.FLAG, "좌클릭: 열기 · 우클릭: 깃발", "지뢰를 피해 안전한 칸을 모두 열기"),
    MEMORY("같은 그림 찾기", Coin.EVENT, Icons.CARD, "카드 두 장을 뒤집어 같은 그림 맞추기", "제한 시간 안에 모든 짝 맞추기");

    public final String label, how, goal;
    public final Coin coin;
    public final int icon;

    Game(String label, Coin coin, int icon, String how, String goal) {
        this.label = label;
        this.coin = coin;
        this.icon = icon;
        this.how = how;
        this.goal = goal;
    }

    public String key() {
        return name().toLowerCase();
    }
}
