package com.jamesdpeters.chestsplusplus.migration;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * The v2 → v3 migration: the automatic import on the first v3 start, and everything {@code /cpp migrate v2} does. The v2 file is renamed
 * only once the import has been written, so a crash before then simply imports again (and the v2 source tags make that harmless).
 */
@Slf4j(topic = ChestsPlusPlus.NAME)
@RequiredArgsConstructor
public final class V2Migration implements Listener {

    static final String MIGRATED_SUFFIX = ".v2-migrated";
    /** Detail lines sent to a player; the console and the log file get them all. */
    private static final int CHAT_DETAIL_LINES = 15;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private final JavaPlugin plugin;
    private final Services services;
    private final V2Importer importer;
    private final V2Cleanup cleanup;
    private final V2WorldCleanup worldCleanup;
    private final MigrationState state;
    private final V2FilterMigration filters;
    private final V2FilterConversion conversion;

    /** Imports {@code data/storage.yml} if a v2 install left one, then reminds the console about filters. Never throws. */
    public void importOnStartup() {
        try {
            worldCleanup.finishLoaded();
        } catch (RuntimeException e) {
            log.error("Finishing imported v2 blocks in loaded chunks failed; they will be finished when their chunks next load", e);
        }
        Path file = candidates().stream().filter(path -> !path.toString().endsWith(MIGRATED_SUFFIX)).filter(Files::isRegularFile).findFirst()
                .orElse(null);
        if (file != null) {
            log.info("Found ChestsPlusPlus v2 data in {}; importing it", file);
            run(plugin.getServer().getConsoleSender(), file, true, true);
        }
        if (state.filters() == MigrationState.Filters.PENDING) log.warn(services.messages().plain(Message.MIGRATE_FILTERS_PENDING_CONSOLE));
    }

    /** {@code /cpp migrate v2 [confirm] [file <name>]}: a preview, or the import itself. */
    public void importFile(Audience sender, boolean apply, @Nullable String name) {
        Path file = name == null ? candidates().stream().filter(Files::isRegularFile).findFirst().orElse(null) : named(name);
        if (file == null || !Files.isRegularFile(file)) {
            List<Path> looked = name == null ? candidates() : file == null ? List.of() : List.of(file);
            services.send(sender, Message.MIGRATE_NO_FILE, Messages.text("files", String.join(", ", looked.stream().map(Path::toString).toList())));
            return;
        }
        run(sender, file, apply, !file.toString().endsWith(MIGRATED_SUFFIX));
    }

    private void run(Audience sender, Path file, boolean apply, boolean renameAfter) {
        try {
            V2Data data = V2Storage.read(file);
            if (apply) backup();
            ImportReport report = importer.run(data, apply);
            if (apply) afterImport(report);
            tell(sender, report);
            if (apply) writeLog(file, report);
            if (apply && renameAfter) services.persistence().flush().thenRun(() -> rename(file));
        } catch (IOException | RuntimeException e) {
            log.error("Importing ChestsPlusPlus v2 data from {} failed", file, e);
            services.send(sender, Message.MIGRATE_FAILED, Messages.text("error", String.valueOf(e.getMessage())));
        }
    }

    private void afterImport(ImportReport report) {
        if (!report.changedAnything()) return;
        worldCleanup.finishLoaded();
        if (report.groups() > 0 && state.filters() == MigrationState.Filters.NONE && services.settings().features().hopperFilters()) {
            state.setFilters(MigrationState.Filters.PENDING);
        }
    }

    private void tell(Audience sender, ImportReport report) {
        if (report.applied() && !report.changedAnything() && report.lines().size() <= 1) {
            services.send(sender, Message.MIGRATE_NOTHING);
            return;
        }
        services.send(sender, Message.MIGRATE_SUMMARY, Messages.text("mode", report.applied() ? "finished" : "preview"),
                Messages.text("chestlinks", report.chestLinks()), Messages.text("autocrafters", report.autoCrafters()),
                Messages.text("nodes", report.nodes()),
                Messages.text("items", report.itemStacks()), Messages.text("trusted", report.trusted()));
        boolean console = sender == plugin.getServer().getConsoleSender();
        List<String> lines = report.lines();
        int shown = console ? lines.size() : Math.min(lines.size(), CHAT_DETAIL_LINES);
        for (String line : lines.subList(0, shown)) services.send(sender, Message.MIGRATE_DETAIL, Messages.text("line", line));
        if (shown < lines.size()) {
            services.send(sender, Message.MIGRATE_DETAIL,
                    Messages.text("line", (lines.size() - shown) + " more line(s) in the console and the migration log"));
        }
        if (!console) lines.forEach(line -> log.info("v2 import: {}", line));
        if (!report.applied()) services.send(sender, Message.MIGRATE_PREVIEW_HINT);
    }

    private void writeLog(Path file, ImportReport report) throws IOException {
        List<String> out = new ArrayList<>();
        out.add("ChestsPlusPlus v2 import from " + file + " at " + LocalDateTime.now());
        out.add(report.chestLinks() + " ChestLink(s), " + report.autoCrafters() + " AutoCrafter(s), " + report.nodes() + " linked block(s), "
                + report.itemStacks() + " item stack(s), " + report.trusted() + " trusted player(s)");
        out.addAll(report.lines());
        Files.write(dataFolder().resolve("v2-migration-" + stamp() + ".log"), out, StandardCharsets.UTF_8);
    }

    /** Copies the data folder (except v3's database and earlier backups) so a server can always go back to v2. */
    private void backup() throws IOException {
        Path folder = dataFolder();
        Path backup = folder.resolve("v2-backup-" + stamp());
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Path relative = folder.relativize(path);
                String top = relative.getName(0).toString();
                if (top.startsWith("data.db") || top.startsWith("v2-backup-") || top.startsWith("v2-migration-")) continue;
                Path target = backup.resolve(relative);
                Files.createDirectories(target.getParent());
                Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        log.info("Backed up the ChestsPlusPlus data folder to {}", backup);
    }

    private void rename(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + MIGRATED_SUFFIX), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Imported {} but could not rename it; the next start will check it again (nothing is imported twice)", file, e);
        }
    }

    public void status(Audience sender) {
        String filterState = state.filters().name().toLowerCase(Locale.ROOT).replace('_', '-') + (conversion.isRunning() ? " (converting now)" : "");
        services.send(sender, Message.MIGRATE_STATUS, Messages.text("pending", cleanup.size()), Messages.text("filters", filterState));
    }

    /** Loads the chunks within {@code radius} chunks of the player that still have imported blocks waiting. */
    public void cleanupNear(Player player, int radius) {
        World world = player.getWorld();
        Chunk center = player.getLocation().getChunk();
        List<V2Cleanup.ChunkRef> chunks = cleanup.chunks().stream().filter(ref -> ref.world().equals(world.getUID()))
                .filter(ref -> Math.abs(ref.x() - center.getX()) <= radius && Math.abs(ref.z() - center.getZ()) <= radius).toList();
        services.send(player, Message.MIGRATE_CLEANUP_STARTED, Messages.text("chunks", chunks.size()));
        chunks.forEach(ref -> world.getChunkAtAsync(ref.x(), ref.z()).thenAccept(worldCleanup::finish));
    }

    /** Converts filters as chunks load from now on, starting with the chunks already loaded. */
    public void filtersOnLoad(Audience sender) {
        state.setFilters(MigrationState.Filters.ON_LOAD);
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) if (chunk.isEntitiesLoaded()) filters.convert(chunk, List.of(chunk.getEntities()));
        }
        services.send(sender, Message.MIGRATE_FILTERS_ON_LOAD);
    }

    public void filtersDismiss(Audience sender) {
        if (conversion.isRunning()) conversion.cancel(sender);
        state.setFilters(MigrationState.Filters.DONE);
        services.send(sender, Message.MIGRATE_FILTERS_DISMISSED);
    }

    /** Converts every chunk of {@code worldName}, or of every world when it is null. */
    public void convertAll(Audience sender, @Nullable String worldName) {
        if (worldName == null) {
            conversion.start(sender, plugin.getServer().getWorlds());
            return;
        }
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) services.send(sender, Message.MIGRATE_UNKNOWN_WORLD, Messages.text("world", worldName));
        else conversion.start(sender, List.of(world));
    }

    public void cancelConversion(Audience sender) {
        conversion.cancel(sender);
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (state.filters() == MigrationState.Filters.PENDING && player.hasPermission(Permissions.ADMIN_MIGRATE)) {
            services.send(player, Message.MIGRATE_FILTERS_PENDING);
        }
    }

    /** Where v2 kept its data, then the copy an earlier import renamed. */
    private List<Path> candidates() {
        Path storage = dataFolder().resolve("data").resolve("storage.yml");
        return List.of(storage, storage.resolveSibling("storage.yml" + MIGRATED_SUFFIX));
    }

    /** A file named relative to the data folder; null if the name would leave it. */
    private @Nullable Path named(String name) {
        Path folder = dataFolder().toAbsolutePath().normalize();
        Path file = folder.resolve(name).normalize();
        return file.startsWith(folder) ? file : null;
    }

    private Path dataFolder() {
        return plugin.getDataFolder().toPath();
    }

    private static String stamp() {
        return LocalDateTime.now().format(STAMP);
    }
}
