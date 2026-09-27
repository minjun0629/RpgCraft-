package kr.rpgcraft.world;

import kr.rpgcraft.item.Grade;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * 낚시 어종 표 (v5.2.0). tools/fish_data.py 와 같은 순서 · 같은 모델 번호 (1600 + 순번).
 * 서식지: OCEAN 바다·해변 / DEEP 깊은 바다 / COLD 추운 곳 / WARM 따뜻한 곳 / RIVER 강 / SWAMP 늪 / FRESH 민물 (비우면 어디서나)
 * 조건: NIGHT 밤에만 / RAIN 비 올 때만
 */
public final class FishSpecies {
    private FishSpecies() {}

    public record Species(String id, String name, Material material, long price, Grade grade, String habitat, String cond, double size) {
        public boolean anywhere() { return habitat.isEmpty(); }

        public boolean fits(String biome, boolean night, boolean rain) {
            if (cond.contains("NIGHT") && !night) return false;
            if (cond.contains("RAIN") && !rain) return false;
            if (habitat.isEmpty()) return true;
            for (String h : habitat.split(",")) if (matches(h, biome)) return true;
            return false;
        }
    }

    public static boolean matches(String habitat, String biome) {
        boolean sea = biome.contains("OCEAN") || biome.contains("BEACH");
        return switch (habitat) {
            case "OCEAN" -> sea;
            case "DEEP" -> biome.contains("DEEP");
            case "COLD" -> biome.contains("FROZEN") || biome.contains("SNOW") || biome.contains("ICE") || biome.contains("COLD") || biome.contains("GROVE") || biome.contains("TAIGA");
            case "WARM" -> biome.contains("WARM") || biome.contains("JUNGLE") || biome.contains("MANGROVE") || biome.contains("DESERT") || biome.contains("SAVANNA") || biome.contains("BADLANDS");
            case "RIVER" -> biome.contains("RIVER");
            case "SWAMP" -> biome.contains("SWAMP");
            case "FRESH" -> !sea;
            default -> false;
        };
    }

    /** 새로 추가된 어종 (아이템 등록 대상, 모델 1600 + 순번) */
    public static final List<Species> NEW = List.of(
            new Species("fish_mackerel", "고등어", Material.COD, 250L, Grade.NORMAL, "OCEAN", "", 0.95),
            new Species("fish_anchovy", "멸치", Material.COD, 120L, Grade.NORMAL, "OCEAN", "", 0.7),
            new Species("fish_herring", "청어", Material.COD, 220L, Grade.NORMAL, "OCEAN,COLD", "", 0.9),
            new Species("fish_sardine", "정어리", Material.COD, 160L, Grade.NORMAL, "OCEAN", "", 0.75),
            new Species("fish_catfish", "메기", Material.COD, 350L, Grade.NORMAL, "FRESH", "", 1.05),
            new Species("fish_loach", "미꾸라지", Material.COD, 180L, Grade.NORMAL, "SWAMP,RIVER", "", 0.8),
            new Species("fish_perch", "쏘가리", Material.COD, 400L, Grade.NORMAL, "FRESH", "", 0.95),
            new Species("fish_bluegill", "블루길", Material.COD, 200L, Grade.NORMAL, "FRESH", "", 0.85),
            new Species("fish_trout", "송어", Material.SALMON, 450L, Grade.NORMAL, "RIVER,COLD", "", 1.0),
            new Species("fish_squid", "한치", Material.COD, 380L, Grade.NORMAL, "OCEAN", "", 0.9),
            new Species("fish_shrimp", "보리새우", Material.COD, 300L, Grade.NORMAL, "OCEAN,SWAMP", "", 0.85),
            new Species("fish_crab", "꽃게", Material.COD, 420L, Grade.NORMAL, "OCEAN", "", 0.9),
            new Species("fish_flounder", "가자미", Material.COD, 330L, Grade.NORMAL, "OCEAN", "", 0.85),
            new Species("fish_goby", "망둥어", Material.COD, 150L, Grade.NORMAL, "SWAMP,OCEAN", "", 0.8),
            new Species("fish_eel", "민물 장어", Material.COD, 1600L, Grade.RARE, "FRESH", "", 1.1),
            new Species("fish_bass", "큰입 배스", Material.COD, 1200L, Grade.RARE, "FRESH", "", 1.05),
            new Species("fish_redsnapper", "참돔", Material.COD, 2200L, Grade.RARE, "OCEAN", "", 1.05),
            new Species("fish_yellowtail", "방어", Material.COD, 2000L, Grade.RARE, "OCEAN,COLD", "", 1.1),
            new Species("fish_octopus", "대왕 문어", Material.COD, 2600L, Grade.RARE, "OCEAN", "", 1.05),
            new Species("fish_blowfish", "자주복", Material.PUFFERFISH, 1800L, Grade.RARE, "OCEAN,WARM", "", 1.0),
            new Species("fish_icefish", "빙어", Material.COD, 1300L, Grade.RARE, "COLD", "", 0.75),
            new Species("fish_piranha", "피라냐", Material.COD, 1700L, Grade.RARE, "WARM", "", 0.9),
            new Species("fish_lobster", "바닷가재", Material.COD, 2800L, Grade.RARE, "OCEAN", "", 1.1),
            new Species("fish_jelly", "보름달 해파리", Material.COD, 2400L, Grade.RARE, "OCEAN", "NIGHT", 1.0),
            new Species("fish_ray", "노랑가오리", Material.COD, 2300L, Grade.RARE, "OCEAN,WARM", "", 1.05),
            new Species("fish_char", "곤들매기", Material.SALMON, 1500L, Grade.RARE, "COLD,RIVER", "", 1.0),
            new Species("fish_gar", "늪 가아", Material.COD, 1900L, Grade.RARE, "SWAMP", "", 1.1),
            new Species("fish_tuna", "참다랑어", Material.COD, 7000L, Grade.UNIQUE, "DEEP,OCEAN", "", 1.2),
            new Species("fish_swordfish", "황새치", Material.COD, 7500L, Grade.UNIQUE, "OCEAN,WARM", "", 1.15),
            new Species("fish_shark", "청상아리", Material.COD, 8500L, Grade.UNIQUE, "OCEAN", "", 1.15),
            new Species("fish_arowana", "은룡어", Material.TROPICAL_FISH, 8000L, Grade.UNIQUE, "WARM,RIVER", "", 1.15),
            new Species("fish_moonfish", "달빛 개복치", Material.PUFFERFISH, 6500L, Grade.UNIQUE, "OCEAN", "NIGHT", 1.2),
            new Species("fish_ghostcray", "유령 가재", Material.COD, 6000L, Grade.UNIQUE, "SWAMP", "NIGHT", 1.0),
            new Species("fish_frostsalmon", "서리비늘 연어", Material.SALMON, 6800L, Grade.UNIQUE, "COLD", "", 1.1),
            new Species("fish_stormeel", "뇌전 뱀장어", Material.COD, 7200L, Grade.UNIQUE, "", "RAIN", 1.15),
            new Species("fish_coelacanth", "실러캔스", Material.COD, 9000L, Grade.UNIQUE, "DEEP", "", 1.15),
            new Species("fish_rainbow", "무지개 송어", Material.SALMON, 6200L, Grade.UNIQUE, "RIVER,FRESH", "RAIN", 1.05),
            new Species("fish_leviathan", "심연의 리바이어던", Material.COD, 55000L, Grade.LEGEND, "DEEP", "NIGHT", 1.25),
            new Species("fish_phoenixkoi", "불사조 비단잉어", Material.TROPICAL_FISH, 40000L, Grade.LEGEND, "WARM,RIVER", "", 1.15),
            new Species("fish_kraken", "새끼 크라켄", Material.COD, 45000L, Grade.LEGEND, "OCEAN", "RAIN", 1.2),
            new Species("fish_starwhale", "별고래", Material.COD, 50000L, Grade.LEGEND, "COLD,OCEAN", "NIGHT", 1.25),
            new Species("fish_jadekoi", "비취 잉어왕", Material.TROPICAL_FISH, 35000L, Grade.LEGEND, "FRESH", "", 1.15),
            new Species("fish_megalodon", "메갈로돈", Material.COD, 60000L, Grade.LEGEND, "DEEP", "", 1.3)
    );

    /** 기존 어종 (아이템은 원래 자리에서 등록) */
    public static final List<Species> OLD = List.of(
            new Species("fish_small", null, null, 0L, Grade.NORMAL, "", "", 0.8),
            new Species("fish_carp", null, null, 0L, Grade.NORMAL, "", "", 1.0),
            new Species("fish_salmon", null, null, 0L, Grade.NORMAL, "", "", 1.05),
            new Species("fish_deep", null, null, 0L, Grade.RARE, "DEEP,OCEAN", "", 1.0),
            new Species("fish_gold", null, null, 0L, Grade.UNIQUE, "", "", 1.05),
            new Species("fish_legend", null, null, 0L, Grade.LEGEND, "", "", 1.15)
    );

    public static List<Species> all() {
        List<Species> out = new ArrayList<>(OLD);
        out.addAll(NEW);
        return out;
    }

    public static String habitatKo(Species s) {
        if (s.habitat().isEmpty()) return "어디서나";
        StringBuilder b = new StringBuilder();
        for (String h : s.habitat().split(",")) {
            if (b.length() > 0) b.append(" · ");
            b.append(switch (h) {
                case "OCEAN" -> "바다"; case "DEEP" -> "깊은 바다"; case "COLD" -> "추운 물가"; case "WARM" -> "따뜻한 물가";
                case "RIVER" -> "강"; case "SWAMP" -> "늪"; case "FRESH" -> "민물"; default -> h;
            });
        }
        return b.toString();
    }
}
