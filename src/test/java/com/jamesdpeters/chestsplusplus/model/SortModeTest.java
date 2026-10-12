package com.jamesdpeters.chestsplusplus.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag(Tags.UNIT)
class SortModeTest {

    @Test
    void parsesIgnoringCase() {
        assertThat(SortMode.parse("amount_desc")).isEqualTo(SortMode.AMOUNT_DESC);
        assertThat(SortMode.parse("AMOUNT_ASC")).isEqualTo(SortMode.AMOUNT_ASC);
        assertThat(SortMode.parse("Name")).isEqualTo(SortMode.NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "sideways", "amount", " name"})
    void unknownTextParsesToNull(String text) {
        assertThat(SortMode.parse(text)).isNull();
    }

    @ParameterizedTest
    @EnumSource(SortMode.class)
    void keyIsTheLowerCaseNameAndParsesBack(SortMode mode) {
        assertThat(mode.key()).isEqualTo(mode.name().toLowerCase(Locale.ROOT));
        assertThat(SortMode.parse(mode.key())).isEqualTo(mode);
    }
}
