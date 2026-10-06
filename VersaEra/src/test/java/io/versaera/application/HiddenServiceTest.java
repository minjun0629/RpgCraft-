package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.content.ContentLoader;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.hidden.HiddenRule;
import io.versaera.domain.hidden.PlayerFacts;
import io.versaera.security.Sealer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HiddenServiceTest {
    private static String example() throws Exception {
        try (var in = HiddenServiceTest.class.getResourceAsStream("/hidden-example.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void sealedRulesUnlockFromCombinedBehaviourOnlyOnce() throws Exception {
        try (TestWorld w = new TestWorld()) {
            Sealer sealer = new Sealer(Sealer.newKey());
            String sealed = sealer.seal(example());
            assertFalse(sealed.contains("fallen_crater"), "봉인 파일에 조건이 평문으로 보이면 안 됨");
            List<HiddenRule> rules = ContentLoader.hidden(ContentLoader.parse(sealer.open(sealed), "hidden"), "hidden");
            Map<String, String> region = new HashMap<>();
            int[] hour = {12};
            PlayerFacts.class.getName();
            HiddenService h = w.s.attachHidden(rules, uuid -> new PlayerFacts() {
                public long counter(String key) { return w.s.growth.counter(uuid, key); }
                public int mastery(String d) { return w.s.growth.level(uuid, d); }
                public int affinity(String npc) { return w.s.relations.affinity(uuid, npc); }
                public String region() { return region.get(uuid); }
                public boolean discovered(String kind, String ref) { return w.s.exploration.discovered(uuid, kind, ref); }
                public int hour() { return hour[0]; }
            });
            String p = TestWorld.player();
            region.put(p, "fallen_crater");
            w.s.progress.setRelation(p, "yuna_alchemist", 60, 0);
            for (int i = 0; i < 30; i++) w.s.growth.record(p, "gather.herbalism", 1);
            assertTrue(w.eventsOf(GameEvents.HiddenUnlocked.class).isEmpty(), "낮에는 조건이 맞지 않음");
            hour[0] = 22;
            h.checkAll(p);
            assertEquals(1, w.eventsOf(GameEvents.HiddenUnlocked.class).size());
            assertTrue(w.eventsOf(GameEvents.HiddenUnlocked.class).get(0).worldFirst());
            assertTrue(w.s.crafting.knows(p, w.s.crafting.recipe("craft_wind_chime")), "보상: 숨은 제작법 해금");
            h.checkAll(p);
            w.s.growth.record(p, "gather.herbalism", 1);
            assertEquals(1, w.eventsOf(GameEvents.HiddenUnlocked.class).size(), "한 번만 해금");
        }
    }

    @Test
    void tamperedOrForeignSealIsRejected() throws Exception {
        Sealer a = new Sealer(Sealer.newKey()), b = new Sealer(Sealer.newKey());
        String s = a.seal("hidden: {}");
        assertThrows(java.security.GeneralSecurityException.class, () -> b.open(s), "다른 서버 키로는 열 수 없음");
        char[] c = s.toCharArray();
        c[c.length - 3] = c[c.length - 3] == 'A' ? 'B' : 'A';
        assertThrows(Exception.class, () -> a.open(new String(c)), "변조 감지");
        assertThrows(java.security.GeneralSecurityException.class, () -> a.open("hidden: plain"));
    }
}
