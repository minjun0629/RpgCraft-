package io.versaera.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 아이템 속성(String → String)을 한 줄 텍스트로 저장. 형식: key=value;key=value (%, =, ; 는 %XX 로 이스케이프).
 * JSON 라이브러리에 기대지 않고, 잘못된 값이 들어와도 깨지지 않게 단순하게.
 */
final class Props {
    private Props() {
    }

    static String encode(Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(esc(e.getKey())).append('=').append(esc(e.getValue() == null ? "" : e.getValue()));
        }
        return sb.toString();
    }

    static Map<String, String> decode(String s) {
        Map<String, String> m = new LinkedHashMap<>();
        if (s == null || s.isEmpty() || s.equals("{}")) return m;
        for (String part : s.split(";")) {
            int i = part.indexOf('=');
            if (i <= 0) continue;
            m.put(unesc(part.substring(0, i)), unesc(part.substring(i + 1)));
        }
        return m;
    }

    private static String esc(String s) {
        return s.replace("%", "%25").replace("=", "%3D").replace(";", "%3B");
    }

    private static String unesc(String s) {
        return s.replace("%3B", ";").replace("%3D", "=").replace("%25", "%");
    }
}
