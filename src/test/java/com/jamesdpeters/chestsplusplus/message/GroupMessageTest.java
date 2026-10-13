package com.jamesdpeters.chestsplusplus.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(Tags.UNIT)
class GroupMessageTest {

    @Test
    void eachPairIsTheSameKeyUnderEachType() {
        for (GroupMessage message : GroupMessage.values()) {
            String chestlink = message.of(GroupType.CHESTLINK).key();
            String autocraft = message.of(GroupType.AUTOCRAFT).key();

            assertThat(chestlink).as(message.name()).startsWith("chestlink.");
            assertThat(autocraft).as(message.name()).isEqualTo("autocraft." + chestlink.substring("chestlink.".length()));
        }
    }
}
