package io.versaera;

import io.versaera.content.ContentLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Feature Registry 와 실제 코드 대조 (요구 §89): 상태가 사실과 맞는지 자동으로 검사한다. */
class RegistryAuditTest {
    private static final Set<String> CLASS = Set.of("CANON", "SOURCE-BASED", "ORIGINAL", "RESEARCH_REQUIRED");
    private static final Set<String> STATUS = Set.of("PLANNED", "DESIGNED", "IMPLEMENTING", "IMPLEMENTED", "PARTIAL", "TESTING", "VERIFIED",
            "BLOCKED", "RESEARCH_REQUIRED", "PAPER_LIMITATION", "EXTERNAL_ASSET_REQUIRED");
    private static final List<String> FIELDS = List.of("id", "feature", "category", "source", "source_type", "classification", "description",
            "player_experience", "dependencies", "implementation", "data_model", "ui", "resource_pack", "performance_risk", "security_risk",
            "persistence", "tests", "status");

    @SuppressWarnings("unchecked")
    @Test
    void registryMatchesReality() throws Exception {
        Map<String, Object> root = ContentLoader.parse(Files.readString(Path.of("docs/feature_registry.yml")), "feature_registry.yml");
        List<Map<String, Object>> features = (List<Map<String, Object>>) root.get("features");
        assertNotNull(features);
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> f : features) {
            String id = String.valueOf(f.get("id"));
            assertTrue(ids.add(id), "id 중복: " + id);
            for (String k : FIELDS) assertTrue(f.containsKey(k), id + ": 필드 없음 " + k);
            assertTrue(CLASS.contains(String.valueOf(f.get("classification"))), id + ": 분류 " + f.get("classification"));
            String status = String.valueOf(f.get("status"));
            assertTrue(STATUS.contains(status), id + ": 상태 " + status);
            List<String> tests = (List<String>) f.get("tests");
            if (status.equals("IMPLEMENTED") || status.equals("TESTING") || status.equals("VERIFIED")) {
                assertFalse(tests.isEmpty(), id + ": 테스트 없이 " + status + " 로 표시할 수 없음");
                for (String t : tests) {
                    try (var walk = Files.walk(Path.of("src/test/java"))) {
                        assertTrue(walk.anyMatch(p -> p.getFileName().toString().equals(t + ".java")), id + ": 테스트 클래스 없음 " + t);
                    }
                }
            }
            if (status.equals("VERIFIED")) assertEquals("PASSED", String.valueOf(f.get("server_test")), id + ": 서버 테스트 없이 VERIFIED 불가");
        }
        for (Map<String, Object> f : features)
            for (String dep : (List<String>) f.get("dependencies")) assertTrue(ids.contains(dep), f.get("id") + ": 없는 의존 " + dep);
    }
}
