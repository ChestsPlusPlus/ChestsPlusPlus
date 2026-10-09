package com.jamesdpeters.chestsplusplus.testing;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.profile.PlayerProfileMock;

/** MockBukkit doesn't implement {@code PlayerProfile#update}; here a name lookup stays pending until the test calls {@link #resolve}. */
@SuppressWarnings("unchecked") // ServerMock#getBanList is a raw override, which -Xlint flags on every subclass
public final class DeferredProfileServer extends ServerMock {

    private final Map<String, CompletableFuture<PlayerProfile>> pending = new HashMap<>();

    @Override
    public PlayerProfileMock createProfile(String name) {
        return new PlayerProfileMock(name, null) {
            @Override
            public CompletableFuture<PlayerProfile> update() {
                return pending.computeIfAbsent(name, _ -> new CompletableFuture<>());
            }
        };
    }

    public void resolve(String name, UUID id) {
        pending.remove(name).complete(new PlayerProfileMock(name, id) {
            @Override
            public boolean isComplete() {
                return true;
            }
        });
    }
}
