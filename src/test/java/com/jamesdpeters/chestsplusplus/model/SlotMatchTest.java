package com.jamesdpeters.chestsplusplus.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.testing.Tags;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(Tags.UNIT)
class SlotMatchTest {

    @Test
    void recipeActsAsExactWhereTheRecipeOffersNoChoice() {
        assertThat(SlotMatch.RECIPE.effective(true)).isEqualTo(SlotMatch.RECIPE);
        assertThat(SlotMatch.RECIPE.effective(false)).isEqualTo(SlotMatch.EXACT);
        assertThat(SlotMatch.TYPE.effective(false)).isEqualTo(SlotMatch.TYPE);
    }

    @Test
    void cyclesThroughEveryModeOnlyWhereRecipeMeansSomething() {
        assertThat(SlotMatch.RECIPE.next(true)).isEqualTo(SlotMatch.EXACT);
        assertThat(SlotMatch.EXACT.next(true)).isEqualTo(SlotMatch.TYPE);
        assertThat(SlotMatch.TYPE.next(true)).isEqualTo(SlotMatch.RECIPE);

        assertThat(SlotMatch.RECIPE.next(false)).isEqualTo(SlotMatch.TYPE);
        assertThat(SlotMatch.TYPE.next(false)).isEqualTo(SlotMatch.EXACT);
    }
}
