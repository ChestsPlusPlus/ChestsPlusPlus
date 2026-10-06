package com.jamesdpeters.chestsplusplus.migration;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * {@code /cpp migrate v2 filters convert-all}: loads every chunk that can hold v2 filters (plus chunks with imported blocks waiting for
 * cleanup), a few at a time, and converts each once its entities have loaded. Resumable, because converted chunks are marked.
 */
@Slf4j(topic = ChestsPlusPlus.NAME)
@RequiredArgsConstructor
public final class V2FilterConversion {

    /** Chunks being loaded at once: enough to finish quickly, few enough not to stall the server. */
    private static final int CONCURRENCY = 8;
    /** Ticks to wait for a chunk's entities before deferring it to the on-load listener. */
    private static final int ENTITY_WAIT_TICKS = 200;
    private static final int REPORT_EVERY_PERCENT = 5;

    private record Target(World world, int x, int z) {}

    private static final class Loading {
        final CompletableFuture<@Nullable Chunk> chunk;
        int waited;

        Loading(CompletableFuture<@Nullable Chunk> chunk) {
            this.chunk = chunk;
        }
    }

    private final class Job {
        final Audience sender;
        final boolean allWorlds;
        final boolean worldsAvailable;
        final Set<UUID> worlds;
        int unfinished;
        final Deque<Target> queue;
        final int total;
        final List<Loading> loading = new ArrayList<>();
        /** The converter's count when the job started; the chunk-load listener converts most chunks as the job loads them. */
        final int convertedBefore;
        int done;
        int processed;
        int nextReport = REPORT_EVERY_PERCENT;

        Job(Audience sender, List<Target> targets, int convertedBefore, boolean allWorlds, boolean worldsAvailable, Set<UUID> worlds) {
            this.sender = sender;
            this.allWorlds = allWorlds;
            this.worldsAvailable = worldsAvailable;
            this.worlds = worlds;
            this.queue = new ArrayDeque<>(targets);
            this.total = targets.size();
            this.convertedBefore = convertedBefore;
        }
    }

    private final Plugin plugin;
    private final Services services;
    private final MigrationState state;
    private final V2FilterMigration filters;
    private final V2Cleanup cleanup;
    private final V2WorldCleanup worldCleanup;
    private final V2PendingLocations pending;
    private @Nullable Job job;
    private boolean scanning;
    /** Bumped by every start and cancel, so a scan cancelled while it ran doesn't start a job when it finishes. */
    private int run;

    public boolean isRunning() {
        return job != null || scanning;
    }

    /** Finds the chunks off the main thread, then starts converting them. */
    public void start(Audience sender, List<World> worlds, boolean allWorlds) {
        if (isRunning()) {
            services.send(sender, Message.MIGRATE_FILTERS_RUNNING);
            return;
        }
        scanning = true;
        int thisRun = ++run;
        state.setFilters(MigrationState.Filters.ON_LOAD);
        Set<V2Cleanup.ChunkRef> waiting = new LinkedHashSet<>(cleanup.chunks());
        for (World world : worlds) {
            for (Chunk chunk : world.getLoadedChunks()) waiting.add(new V2Cleanup.ChunkRef(world.getUID(), chunk.getChunkKey()));
        }
        boolean worldsAvailable = pending.isEmpty()
                && cleanup.chunks().stream().allMatch(ref -> plugin.getServer().getWorld(ref.world()) != null);
        Set<UUID> scannedWorlds = worlds.stream().map(World::getUID).collect(Collectors.toSet());
        Executor mainThread = task -> plugin.getServer().getScheduler().runTask(plugin, task);
        CompletableFuture.supplyAsync(() -> targets(worlds, waiting)).whenCompleteAsync((targets, error) -> {
            if (thisRun != run) return;
            scanning = false;
            if (error != null) {
                log.error("Could not list the chunks to convert", error);
                services.send(sender, Message.MIGRATE_FAILED, Messages.text("error", String.valueOf(error.getMessage())));
                return;
            }
            job = new Job(sender, targets, filters.converted(), allWorlds, worldsAvailable, scannedWorlds);
            report(sender, Message.MIGRATE_FILTERS_STARTED, Messages.text("chunks", targets.size()));
        }, mainThread);
    }

    private static List<Target> targets(List<World> worlds, Set<V2Cleanup.ChunkRef> waiting) {
        Set<Target> targets = new LinkedHashSet<>();
        for (World world : worlds) {
            RegionFiles.chunks(world).forEach(chunk -> targets.add(new Target(world, chunk.x(), chunk.z())));
            for (V2Cleanup.ChunkRef ref : waiting) {
                if (!ref.world().equals(world.getUID())) continue;
                targets.add(new Target(world, ref.x(), ref.z()));
            }
        }
        return new ArrayList<>(targets);
    }

    public void cancel(Audience sender) {
        Job current = job;
        if (scanning) {
            run++;
            scanning = false;
            services.send(sender, Message.MIGRATE_FILTERS_CANCELLED, Messages.text("done", 0), Messages.text("total", "?"));
            return;
        }
        if (current == null) {
            services.send(sender, Message.MIGRATE_FILTERS_NOT_RUNNING);
            return;
        }
        run++;
        job = null;
        current.loading.forEach(loading -> loading.chunk.thenAccept(this::release));
        services.send(sender, Message.MIGRATE_FILTERS_CANCELLED, Messages.text("done", current.done), Messages.text("total", current.total));
    }

    /** Every tick: converts loaded chunks whose entities are in, then starts loading more. */
    public void tick() {
        Job current = job;
        if (current == null) return;
        convertReady(current);
        while (current.loading.size() < CONCURRENCY && !current.queue.isEmpty()) current.loading.add(load(current.queue.poll()));
        progress(current);
        if (current.queue.isEmpty() && current.loading.isEmpty()) finish(current);
    }

    private Loading load(Target target) {
        CompletableFuture<@Nullable Chunk> chunk;
        try {
            chunk = target.world().getChunkAtAsync(target.x(), target.z(), false).thenApply(loaded -> {
                // Keeps the chunk loaded until its entities have loaded and been converted.
                if (loaded != null) loaded.addPluginChunkTicket(plugin);
                return loaded;
            });
        } catch (RuntimeException e) {
            chunk = CompletableFuture.failedFuture(e);
        }
        return new Loading(chunk);
    }

    private void convertReady(Job current) {
        for (Iterator<Loading> it = current.loading.iterator(); it.hasNext();) {
            Loading loading = it.next();
            if (!loading.chunk.isDone()) continue;
            Chunk chunk = loading.chunk.isCompletedExceptionally() ? null : loading.chunk.join();
            if (chunk != null && !chunk.isEntitiesLoaded() && ++loading.waited < ENTITY_WAIT_TICKS) continue;
            try {
                if (chunk == null || !chunk.isEntitiesLoaded()) {
                    current.unfinished++;
                    log.warn("Deferred a v2 filter chunk: {}", chunk == null ? "chunk load failed" : "entities did not load within 200 ticks");
                } else {
                    worldCleanup.finish(chunk);
                    filters.convert(chunk, List.of(chunk.getEntities()));
                    current.done++;
                }
            } catch (RuntimeException e) {
                current.unfinished++;
                log.warn("Could not convert v2 filters in chunk {}; conversion remains on-load", chunk, e);
            } finally {
                release(chunk);
            }
            current.processed++;
            it.remove();
        }
    }

    private void release(@Nullable Chunk chunk) {
        if (chunk != null) chunk.removePluginChunkTicket(plugin);
    }

    private void progress(Job current) {
        if (current.total == 0) return;
        int percent = current.processed * 100 / current.total;
        if (percent < current.nextReport || percent >= 100) return;
        current.nextReport = percent - percent % REPORT_EVERY_PERCENT + REPORT_EVERY_PERCENT;
        report(current.sender, Message.MIGRATE_FILTERS_PROGRESS, Messages.text("done", current.done), Messages.text("total", current.total),
                Messages.text("percent", percent), Messages.text("filters", filters.converted() - current.convertedBefore));
    }

    private void finish(Job current) {
        job = null;
        boolean missingWorlds = pending.size() > 0 || cleanup.chunks().stream().anyMatch(ref -> plugin.getServer().getWorld(ref.world()) == null);
        if (current.allWorlds && current.worldsAvailable && current.unfinished == 0 && !missingWorlds
                && plugin.getServer().getWorlds().stream().allMatch(world -> current.worlds.contains(world.getUID())))
            state.setFilters(MigrationState.Filters.DONE);
        else
            report(current.sender, Message.MIGRATE_FILTERS_DEFERRED, Messages.text("unfinished", current.unfinished));
        report(current.sender, Message.MIGRATE_FILTERS_FINISHED, Messages.text("filters", filters.converted() - current.convertedBefore),
                Messages.text("chunks", current.done));
    }

    /** Progress goes to the console as well, so a long run started in game can be followed in the log. */
    private void report(Audience sender, Message message, TagResolver... placeholders) {
        if (sender != plugin.getServer().getConsoleSender()) services.send(sender, message, placeholders);
        log.info(services.messages().plain(message, placeholders));
    }
}
