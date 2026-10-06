package io.versaera.application.port;

import java.util.Optional;

public interface ProfileRepository {
    record Profile(String uuid, String name, long firstSeen, long lastSeen, int level, long exp) {}

    Optional<Profile> find(String uuid);

    void upsert(Profile p);
}
