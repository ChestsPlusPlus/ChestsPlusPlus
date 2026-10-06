package com.jamesdpeters.chestsplusplus.migration;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/** What an import did (or, for a preview, would do): counts for the summary and one line per notable event for the details. */
public final class ImportReport {

    @Getter private final boolean applied;
    @Getter private int chestLinks;
    @Getter private int autoCrafters;
    @Getter private int nodes;
    @Getter private int itemStacks;
    @Getter private int trusted;
    private int alreadyImported;
    private int nodesAddedToEarlier;
    private final List<String> details = new ArrayList<>();
    private final Map<String, Integer> unresolvedWorlds = new LinkedHashMap<>();

    ImportReport(boolean applied, List<String> problems) {
        this.applied = applied;
        details.addAll(problems);
    }

    public int groups() {
        return chestLinks + autoCrafters;
    }

    public boolean changedAnything() {
        return groups() + nodesAddedToEarlier + trusted > 0;
    }

    void imported(GroupType type, int nodeCount, int stacks) {
        if (type == GroupType.CHESTLINK) chestLinks++;
        else autoCrafters++;
        nodes += nodeCount;
        itemStacks += stacks;
    }

    void alreadyImported(int newNodes) {
        alreadyImported++;
        nodesAddedToEarlier += newNodes;
        nodes += newNodes;
    }

    void addTrusted() {
        trusted++;
    }

    void renamed(String owner, GroupType type, String from, String to) {
        details.add("Renamed " + owner + "'s " + type.displayName() + " \"" + from + "\" to " + to
                + " (v3 names allow up to 32 letters, numbers, - and _)");
    }

    void taken(BlockPos pos, String group) {
        details.add("Skipped the block at " + pos.x() + " " + pos.y() + " " + pos.z() + " for " + group + ": it is already linked to another group");
    }

    void unresolvedWorld(String world) {
        unresolvedWorlds.merge(world, 1, Integer::sum);
    }

    void outOfRange(String group) {
        details.add("Skipped a block of " + group + ": its position is outside the world");
    }

    void missingRecipe(String group, String key) {
        details.add(group + "'s recipe " + key + " no longer exists; it was imported without one");
    }

    void overLimit(String owner, GroupType type, int count, int limit) {
        details.add(owner + " now has " + count + " " + type.displayName() + "s, more than the default limit of " + limit);
    }

    /** Every detail line, worlds that couldn't be found last. v2 names may hold colour codes, shown as {@code &} so chat leaves them be. */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        if (alreadyImported > 0) {
            lines.add(alreadyImported + " group(s) had been imported before" + (nodesAddedToEarlier > 0
                    ? "; added " + nodesAddedToEarlier
                            + " block(s) to them that were skipped last time"
                    : "; nothing new to add") + ". Their items were not imported again.");
        }
        lines.addAll(details);
        unresolvedWorlds.forEach((world, count) -> lines.add("World '" + world + "' wasn't found, so " + count
                + " block(s) in it were skipped. Run /cpp migrate v2 confirm again once it is loaded."));
        return lines.stream().map(line -> line.replace('§', '&')).toList();
    }
}
