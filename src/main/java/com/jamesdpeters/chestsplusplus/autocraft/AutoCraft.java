package com.jamesdpeters.chestsplusplus.autocraft;

import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.Checks;
import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;

/**
 * AutoCraft's parts. A recipe change reaches the crafters and the open editors as a group change, so {@link Recipes} needs neither of
 * them.
 */
public record AutoCraft(AutoCraftHandler handler, Recipes recipes, RecipeEditor editor, Crafter crafter) {

    public static AutoCraft create(Services services, Checks checks, CraftingBackend backend) {
        Recipes recipes = new Recipes(services.groups(), backend, services.plugin().getServer());
        RecipeEditor editor = new RecipeEditor(services, checks, recipes);
        Crafter crafter = new Crafter(services, recipes, backend);
        AutoCraft autoCraft = new AutoCraft(new AutoCraftHandler(services, recipes, editor, crafter), recipes, editor, crafter);
        services.groups().listen(autoCraft::changed);
        return autoCraft;
    }

    /** Any change to a group, not just its recipe, retries its crafters at once; the extra wake-ups (a rename, say) are harmless. */
    private void changed(StorageGroup group) {
        if (!(group instanceof AutoCraftGroup craft)) return;
        crafter.craftSoon(craft);
        editor.render(craft);
    }
}
