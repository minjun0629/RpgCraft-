package io.versaera.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 히든 콘텐츠 조건 봉인 (AES-256-GCM). 서버마다 다른 키 → 운영 폴더를 열어 봐도 조건이 평문으로 보이지 않는다.
 * 키 파일은 서버 데이터 폴더에만 두고 배포 jar 에 넣지 않는다.
 * (서버 관리자는 키를 가지므로 완전한 비밀은 아님 — 목적은 "쉽게 노출되지 않게" 와 변조 감지.)
 */
public final class Sealer {
    private static final int IV = 12, TAG_BITS = 128;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public Sealer(byte[] keyBytes) {
        if (keyBytes.length != 32) throw new IllegalArgumentException("키는 32바이트여야 합니다");
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public static byte[] newKey() {
        try {
            KeyGenerator g = KeyGenerator.getInstance("AES");
            g.init(256);
            return g.generateKey().getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String seal(String plain) {
        try {
            byte[] iv = new byte[IV];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV + ct.length];
            System.arraycopy(iv, 0, out, 0, IV);
            System.arraycopy(ct, 0, out, IV, ct.length);
            return "VSEAL1:" + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("봉인 실패", e);
        }
    }

    /** 키가 다르거나 내용이 변조되면 예외 */
    public String open(String sealed) throws GeneralSecurityException {
        if (sealed == null || !sealed.startsWith("VSEAL1:")) throw new GeneralSecurityException("봉인 형식이 아닙니다");
        byte[] all = Base64.getDecoder().decode(sealed.substring(7).strip());
        if (all.length <= IV) throw new GeneralSecurityException("봉인 데이터가 너무 짧습니다");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV));
        return new String(c.doFinal(all, IV, all.length - IV), StandardCharsets.UTF_8);
    }
}
