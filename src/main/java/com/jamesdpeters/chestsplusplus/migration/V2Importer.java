package com.jamesdpeters.chestsplusplus.migration;

import com.jamesdpeters.chestsplusplus.access.TrustService;
import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.core.PlayerNames;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupNames;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.NodeIndex;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.persistence.GroupStore;
import com.jamesdpeters.chestsplusplus.persistence.GroupStore.GroupRow;
import com.jamesdpeters.chestsplusplus.persistence.GroupStore.GroupSnapshot;
import com.jamesdpeters.chestsplusplus.persistence.GroupStore.MemberRow;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Imports {@link V2Data} into the live model. Groups go through {@link GroupStore#adopt}, exactly as if they had been loaded, and are
 * tagged with their v2 source, and every block they link is recorded, so a repeated import adds only blocks it skipped before and never
 * imports items twice. Imported blocks
 * face north until {@link V2WorldCleanup} sees their chunk. Limits and the world blacklist don't apply: this is an admin operation.
 */
@RequiredArgsConstructor
public final class V2Importer {

    private record OwnerType(UUID owner, GroupType type) {}

    private final GroupRegistry groups;
    private final NodeIndex nodes;
    private final TrustService trust;
    private final GroupStore groupStore;
    private final V2Cleanup cleanup;
    private final WorldUids worlds;
    private final Supplier<Settings> settings;
    /** Called for each node added, so its display can spawn if the chunk is loaded. */
    private final Consumer<Node> nodeAdded;

    /** Plans the import and, when {@code apply} is true, carries it out. */
    public ImportReport run(V2Data data, boolean apply) {
        return new Run(data, apply).execute();
    }

    private final class Run {

        private final V2Data data;
        private final boolean apply;
        private final ImportReport report;
        private final Map<String, StorageGroup> earlier = new HashMap<>();
        private final Set<BlockPos> claimed = new HashSet<>();
        private final Set<String> newNames = new HashSet<>();
        private final Map<OwnerType, Integer> newGroups = new HashMap<>();

        Run(V2Data data, boolean apply) {
            this.data = data;
            this.apply = apply;
            this.report = new ImportReport(apply, data.problems());
            groups.all().stream().filter(group -> group.v2Source() != null).forEach(group -> earlier.put(group.v2Source(), group));
        }

        ImportReport execute() {
            data.groups().forEach(this::importGroup);
            data.parties().forEach(this::importParty);
            newGroups.keySet().forEach(this::checkLimit);
            return report;
        }

        private void importGroup(V2Data.Group group) {
            String label = PlayerNames.of(group.owner()) + ":" + group.name();
            StorageGroup existing = earlier.get(group.source());
            List<BlockPos> positions = positions(group, existing, label);
            if (existing != null) {
                report.alreadyImported(positions.size());
                if (apply && !positions.isEmpty()) {
                    positions.forEach(pos -> addNode(new Node(pos, BlockFace.NORTH, existing.id())));
                    groupStore.markDirty(existing);
                }
                return;
            }
            String name = uniqueName(group);
            if (!name.equals(group.name())) report.renamed(PlayerNames.of(group.owner()), group.type(), group.name(), name);
            @Nullable ItemStack @Nullable [] items = items(group, label);
            report.imported(group.type(), positions.size(), stacks(group));
            newGroups.merge(new OwnerType(group.owner(), group.type()), 1, Integer::sum);
            if (apply) adopt(group, name, items, positions);
        }

        private void adopt(V2Data.Group group, String name, @Nullable ItemStack @Nullable [] items, List<BlockPos> positions) {
            long id = groups.nextId();
            V2Data.Recipe recipe = group.recipe();
            GroupRow row = new GroupRow(id, group.type(), group.owner(), name, group.isPublic(),
                    group.type() == GroupType.CHESTLINK ? group.sortMode() : null,
                    System.currentTimeMillis(), items, recipe == null ? null : recipe.key().asString(), null, group.source());
            List<MemberRow> members = group.members().stream().filter(member -> !member.equals(group.owner()))
                    .map(member -> new MemberRow(id, member)).toList();
            groupStore.adopt(new GroupSnapshot(row, members, positions.stream().map(pos -> GroupStore.row(id, pos, BlockFace.NORTH)).toList()));
            for (Node node : nodes.nodesOf(id)) {
                cleanup.add(node.pos());
                nodeAdded.accept(node);
            }
        }

        /** The group's blocks that can be linked: in a known world, inside it, and not linked to anything else. */
        private List<BlockPos> positions(V2Data.Group group, @Nullable StorageGroup existing, String label) {
            List<BlockPos> out = new ArrayList<>();
            for (V2Data.Location location : group.locations()) {
                UUID world = worlds.resolve(location.world());
                if (world == null) {
                    report.unresolvedWorld(location.world());
                    continue;
                }
                BlockPos pos;
                try {
                    pos = new BlockPos(world, location.x(), location.y(), location.z());
                } catch (IllegalArgumentException e) {
                    report.outOfRange(label);
                    continue;
                }
                // Imported before: still linked, or unlinked by a player since, and either way not to be linked again.
                if (cleanup.wasImported(pos)) continue;
                Node linked = nodes.get(pos);
                if (linked != null || !claimed.add(pos)) report.taken(pos, label);
                else out.add(pos);
            }
            return out;
        }

        private @Nullable ItemStack @Nullable [] items(V2Data.Group group, String label) {
            if (group.type() == GroupType.CHESTLINK) return group.items() == null ? null : Arrays.copyOf(group.items(), ChestLinkGroup.SIZE);
            V2Data.Recipe recipe = group.recipe();
            if (recipe == null) return null;
            @Nullable ItemStack[] matrix = V2Recipes.matrix(recipe);
            if (matrix == null) report.missingRecipe(label, recipe.key().asString());
            return matrix;
        }

        private static int stacks(V2Data.Group group) {
            return group.items() == null ? 0 : (int) Arrays.stream(group.items()).filter(Objects::nonNull).count();
        }

        private String uniqueName(V2Data.Group group) {
            String base = cleanName(group.name());
            String name = base;
            for (int n = 2; isTaken(group, name); n++) {
                String suffix = "_" + n;
                name = base.substring(0, Math.min(base.length(), GroupNames.MAX_LENGTH - suffix.length())) + suffix;
            }
            newNames.add(nameKey(group, name));
            return name;
        }

        private boolean isTaken(V2Data.Group group, String name) {
            return groups.find(group.type(), group.owner(), name) != null || newNames.contains(nameKey(group, name));
        }

        private static String nameKey(V2Data.Group group, String name) {
            return group.type() + ":" + group.owner() + ":" + GroupNames.normalise(name);
        }

        private void importParty(V2Data.Party party) {
            for (UUID member : party.members()) {
                if (member.equals(party.owner()) || trust.isTrusted(party.owner(), member)) continue;
                report.addTrusted();
                if (apply) trust.trust(party.owner(), member);
            }
        }

        private void checkLimit(OwnerType key) {
            Settings.Limits limits = settings.get().limits();
            int limit = key.type().pick(limits.chestlinkDefault(), limits.autocraftDefault());
            int count = groups.ownedBy(key.owner(), key.type()).size() + (apply ? 0 : newGroups.get(key));
            if (limit >= 0 && count > limit) report.overLimit(PlayerNames.of(key.owner()), key.type(), count, limit);
        }

        private void addNode(Node node) {
            nodes.put(node);
            cleanup.add(node.pos());
            nodeAdded.accept(node);
        }
    }

    /** Fits a free-text v2 name to v3's rules: colour codes dropped, other characters replaced with {@code _}, at most 32 long. */
    static String cleanName(String name) {
        String cleaned = name.replaceAll("§.", "").trim().replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
        if (cleaned.length() > GroupNames.MAX_LENGTH) cleaned = cleaned.substring(0, GroupNames.MAX_LENGTH);
        return cleaned.isEmpty() ? "group" : cleaned;
    }
}
