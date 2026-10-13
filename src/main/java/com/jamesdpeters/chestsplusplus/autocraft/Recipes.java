package com.jamesdpeters.chestsplusplus.autocraft;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import com.jamesdpeters.chestsplusplus.core.PaperApi;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.model.SlotMatch;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Each AutoCraft group's recipe, resolved against the server's recipe list, and what each matrix slot accepts. A change is announced
 * as a group change, which saves and redisplays the group, retries its crafters and redraws open editors.
 */
@Slf4j(topic = ChestsPlusPlus.NAME)
@RequiredArgsConstructor
public final class Recipes {

    private final GroupRegistry groups;
    private final CraftingBackend backend;
    private final Server server;
    /** Each group's recipe choices per slot, cached until its recipe changes. */
    private final Map<Long, List<@Nullable Predicate<ItemStack>>> recipeChoices = new HashMap<>();
    /** What each slot accepts once the group's match modes are applied; what the planner uses. */
    private final Map<Long, List<@Nullable Predicate<ItemStack>>> slotRules = new HashMap<>();
    /** Groups whose stored recipe didn't resolve yet, retried by {@link #resolveDeferred()}. */
    private final Set<Long> unresolved = new HashSet<>();

    /** Re-resolves a loaded group's stored matrix against the live recipe list (results aren't persisted). */
    public void resolveLoaded(AutoCraftGroup group) {
        if (group.matrixIsEmpty() && !rebuildMatrix(group)) return;
        World world = firstWorld();
        CraftingBackend.ResolvedRecipe resolved = world == null ? null : backend.resolve(group.matrix(), world);
        if (resolved == null) leaveUnresolved(group);
        else applyRecipe(group, group.matrix(), resolved);
    }

    /**
     * A v2 import stores only the key of a recipe that wasn't registered yet; rebuilds its matrix once the recipe exists. False while it
     * can't, leaving the group for {@link #resolveDeferred()}.
     */
    private boolean rebuildMatrix(AutoCraftGroup group) {
        NamespacedKey key = group.recipeKey();
        if (key == null) return false;
        @Nullable ItemStack @Nullable [] matrix = RecipeMatrix.of(key);
        if (matrix == null) {
            unresolved.add(group.id());
            return false;
        }
        group.setRecipe(matrix, key, null);
        groups.changed(group);
        return true;
    }

    /** Keeps the stored key, so the next save doesn't lose a recipe that another plugin may only register later. */
    private void leaveUnresolved(AutoCraftGroup group) {
        group.setRecipe(group.matrix(), group.recipeKey(), null);
        forgetRules(group);
        unresolved.add(group.id());
    }

    /** Retries the recipes that didn't resolve at load, once every plugin has had the chance to register its own. */
    public void resolveDeferred() {
        for (long id : List.copyOf(unresolved)) {
            if (groups.byId(id) instanceof AutoCraftGroup group) resolveDeferred(group);
        }
    }

    private void resolveDeferred(AutoCraftGroup group) {
        resolveLoaded(group);
        if (!group.hasRecipe()) {
            log.warn("AutoCraft group {} has no recipe: its stored recipe {} could not be resolved", group.id(), group.recipeKey());
            return;
        }
        groups.changed(group);
    }

    /**
     * Sets a new ghost matrix (from the editor): resolves it once, stores key, matrix and result, and announces the change. Returns the
     * result, or null when the matrix isn't a recipe.
     */
    public @Nullable ItemStack setMatrix(AutoCraftGroup group, @Nullable ItemStack[] matrix, @Nullable Player editor) {
        World world = editor != null ? editor.getWorld() : firstWorld();
        CraftingBackend.ResolvedRecipe resolved = world == null ? null : backend.resolve(matrix, world);
        applyRecipe(group, matrix, resolved);
        groups.changed(group);
        if (editor != null && resolved != null) editor.playSound(PaperApi.location(editor), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.4f);
        return resolved == null ? null : resolved.result();
    }

    /** Sets what one matrix slot accepts, re-applies the rules and announces the change. */
    public void setMatch(AutoCraftGroup group, int index, SlotMatch match) {
        group.setMatch(index, match);
        List<@Nullable Predicate<ItemStack>> choices = recipeChoices.get(group.id());
        if (choices != null) slotRules.put(group.id(), SlotRules.of(group.matrix(), group.matches(), choices));
        groups.changed(group);
    }

    /** Whether the recipe itself accepts more than the ghost item in this matrix slot (e.g. any planks). */
    public boolean hasRecipeChoice(AutoCraftGroup group, int index) {
        List<@Nullable Predicate<ItemStack>> choices = recipeChoices.get(group.id());
        return choices != null && choices.get(index) != null;
    }

    /** What each matrix slot accepts, resolving the group's recipe first if it hasn't been yet; null while it has none. */
    @Nullable
    List<@Nullable Predicate<ItemStack>> slotsFor(AutoCraftGroup group) {
        if (!slotRules.containsKey(group.id())) resolveLoaded(group);
        return slotRules.get(group.id());
    }

    /** The group is gone. */
    void forget(StorageGroup group) {
        forgetRules(group);
        unresolved.remove(group.id());
    }

    private void applyRecipe(AutoCraftGroup group, @Nullable ItemStack[] matrix, CraftingBackend.@Nullable ResolvedRecipe resolved) {
        unresolved.remove(group.id());
        group.setRecipe(matrix, resolved == null ? null : resolved.key(), resolved == null ? null : resolved.result());
        if (resolved == null) {
            forgetRules(group);
            return;
        }
        recipeChoices.put(group.id(), resolved.choices());
        slotRules.put(group.id(), SlotRules.of(group.matrix(), group.matches(), resolved.choices()));
    }

    private void forgetRules(StorageGroup group) {
        recipeChoices.remove(group.id());
        slotRules.remove(group.id());
    }

    private @Nullable World firstWorld() {
        List<World> worlds = server.getWorlds();
        return worlds.isEmpty() ? null : worlds.getFirst();
    }
}
