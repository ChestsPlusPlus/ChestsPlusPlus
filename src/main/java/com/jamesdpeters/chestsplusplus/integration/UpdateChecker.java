package com.jamesdpeters.chestsplusplus.integration;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * Checks GitHub Releases for a newer ChestsPlusPlus. The check
 * runs on the async scheduler with {@code java.net.http}; only players with {@code chestsplusplus.admin.update} are told,
 * when they join.
 */
@Slf4j(topic = ChestsPlusPlus.NAME)
public final class UpdateChecker implements Listener {

    static final String RELEASES_URL = "https://api.github.com/repos/ChestsPlusPlus/ChestsPlusPlus/releases/latest";
    static final String DOWNLOAD_URL = "https://github.com/ChestsPlusPlus/ChestsPlusPlus/releases/latest";
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final long RECHECK_TICKS = 20L * 60 * 60 * 12;

    private final Services services;
    private final String currentVersion;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile @Nullable String latest;
    private @Nullable BukkitTask task;

    public UpdateChecker(Services services, String currentVersion) {
        this.services = services;
        this.currentVersion = currentVersion;
    }

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
        if (!services.settings().updateChecker()) return;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "ChestsPlusPlus/" + currentVersion)
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return;
            String tag = parseTag(response.body());
            if (tag == null || !Versions.isNewer(tag, currentVersion)) return;
            if (latest == null)
                log.info("ChestsPlusPlus {} is available (running {}): {}", tag, currentVersion, DOWNLOAD_URL);
            latest = tag;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("Update check failed", e);
        }
    }

    static @Nullable String parseTag(String json) {
        Matcher matcher = TAG.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        String version = latest;
        Player player = event.getPlayer();
        if (version == null || !services.settings().updateChecker() || !player.hasPermission(Permissions.ADMIN_UPDATE)) return;
        services.messages().send(player, Message.UPDATE_AVAILABLE, Messages.text("version", version), Messages.text("url", DOWNLOAD_URL));
    }
}
