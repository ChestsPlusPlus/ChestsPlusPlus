package com.jamesdpeters.chestsplusplus.model;

/** What an AutoCraft recipe slot accepts in place of its ghost item. Right-clicking a ghost in the recipe editor cycles these. */
public enum SlotMatch {
    /** Whatever the recipe accepts there (e.g. any planks). Special recipes accept nothing generic, so this acts as {@link #EXACT}. */
    RECIPE,
    /** Only items identical to the ghost, components included. */
    EXACT,
    /** Any item of the ghost's type, whatever its components. */
    TYPE;

    /** How this mode behaves in a slot; {@code recipeChoice} says whether the recipe offers a generic choice there. */
    public SlotMatch effective(boolean recipeChoice) {
        return this == RECIPE && !recipeChoice ? EXACT : this;
    }

    /** The mode after a right-click, skipping {@link #RECIPE} where it would only repeat {@link #EXACT}. */
    public SlotMatch next(boolean recipeChoice) {
        return switch (effective(recipeChoice)) {
            case RECIPE -> EXACT;
            case EXACT -> TYPE;
            case TYPE -> recipeChoice ? RECIPE : EXACT;
        };
    }
}
