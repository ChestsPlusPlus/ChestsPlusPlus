package com.jamesdpeters.chestsplusplus.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * Checks Modrinth for a newer ChestsPlusPlus. Release builds only look at releases; pre-release builds also see betas and alphas.
 * The check runs on the async scheduler with {@code java.net.http}; only players with {@code chestsplusplus.admin.update} are told,
 * when they join.
 */
@Slf4j(topic = ChestsPlusPlus.NAME)
@RequiredArgsConstructor
public final class UpdateChecker implements Listener {

    static final String PROJECT_ID = "DXfVvKr8";
    static final String VERSIONS_URL = "https://api.modrinth.com/v2/project/" + PROJECT_ID + "/version?loaders=%5B%22paper%22%5D";
    static final String PROJECT_URL = "https://modrinth.com/plugin/" + PROJECT_ID;
    private static final long RECHECK_TICKS = 20L * 60 * 60 * 12;

    private final Services services;
    private final String currentVersion;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile @Nullable String latest;
    private @Nullable BukkitTask task;

    public void start() {
        task = services.plugin().getServer().getScheduler().runTaskTimerAsynchronously(services.plugin(), this::check, 20L * 10, RECHECK_TICKS);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    /** The newer version found, if any. */
    public @Nullable String latest() {
        return latest;
    }

    void check() {
        if (!services.settings().updateChecker().enabled()) return;
        try {
            HttpRequest request = HttpRequest.newBuilder(versionsUri(currentVersion))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "ChestsPlusPlus/" + currentVersion + " (github.com/ChestsPlusPlus/ChestsPlusPlus)")
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return;
            String newest = newest(response.body()).orElse(null);
            if (newest == null || !Versions.isNewer(newest, currentVersion)) return;
            if (latest == null)
                log.info("ChestsPlusPlus {} is available (running {}): {}", newest, currentVersion, downloadUrl(newest));
            latest = newest;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("Update check failed", e);
        }
    }

    static URI versionsUri(String currentVersion) {
        return URI.create(Versions.isPreRelease(currentVersion) ? VERSIONS_URL : VERSIONS_URL + "&version_type=release");
    }

    /** The highest {@code version_number} in Modrinth's version list, without relying on its ordering. */
    static Optional<String> newest(String json) {
        return JsonParser.parseString(json)
                .getAsJsonArray()
                .asList()
                .stream()
                .map(JsonElement::getAsJsonObject)
                .map(version -> version.get("version_number").getAsString())
                .max(Versions::compare);
    }

    static String downloadUrl(String version) {
        return PROJECT_URL + "/version/" + version;
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        String version = latest;
        Player player = event.getPlayer();
        if (version == null || !services.settings().updateChecker().enabled() || !player.hasPermission(Permissions.ADMIN_UPDATE)) return;
        services.send(player, Message.UPDATE_AVAILABLE, Messages.text("version", version), Messages.text("url", downloadUrl(version)));
    }
}
