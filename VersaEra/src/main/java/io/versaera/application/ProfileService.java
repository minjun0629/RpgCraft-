package io.versaera.application;

import io.versaera.application.port.ProfileRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.GameClock;

/** 접속 기록 · 캐릭터 기본 정보 */
public final class ProfileService {
    private final TxRunner tx;
    private final ProfileRepository profiles;
    private final GameClock clock;

    public ProfileService(TxRunner tx, ProfileRepository profiles, GameClock clock) {
        this.tx = tx;
        this.profiles = profiles;
        this.clock = clock;
    }

    /** @return 처음 접속이면 true */
    public boolean join(String uuid, String name) {
        long now = clock.nowMillis();
        return tx.inTx(() -> {
            var p = profiles.find(uuid);
            if (p.isEmpty()) {
                profiles.upsert(new ProfileRepository.Profile(uuid, name, now, now, 1, 0));
                return true;
            }
            var o = p.get();
            profiles.upsert(new ProfileRepository.Profile(uuid, name, o.firstSeen(), now, o.level(), o.exp()));
            return false;
        });
    }

    public void quit(String uuid) {
        long now = clock.nowMillis();
        tx.inTx(() -> {
            profiles.find(uuid).ifPresent(o -> profiles.upsert(new ProfileRepository.Profile(uuid, o.name(), o.firstSeen(), now, o.level(), o.exp())));
            return null;
        });
    }

    public java.util.Optional<ProfileRepository.Profile> find(String uuid) {
        return profiles.find(uuid);
    }
}
