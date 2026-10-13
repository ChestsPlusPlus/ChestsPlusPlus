package com.jamesdpeters.chestsplusplus.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jamesdpeters.chestsplusplus.model.AutoCraftGroup;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(Tags.UNIT)
class HandlersTest {

    private final Handlers handlers = new Handlers();
    private final StubHandler chestLinks = new StubHandler(GroupType.CHESTLINK);
    private final StubHandler autoCraft = new StubHandler(GroupType.AUTOCRAFT);

    @Test
    void startupCheckFailsWhenATypeHasNoHandler() {
        handlers.register(chestLinks);

        assertThatThrownBy(handlers::requireAll).isInstanceOf(IllegalStateException.class).hasMessageContaining("AUTOCRAFT");
    }

    @Test
    void returnsTheHandlerRegisteredForATypeOrGroup() {
        handlers.register(chestLinks);
        handlers.register(autoCraft);
        handlers.requireAll();

        assertThat(handlers.of(GroupType.CHESTLINK)).isSameAs(chestLinks);
        assertThat(handlers.of(new AutoCraftGroup(1, UUID.randomUUID(), "crafter", 0))).isSameAs(autoCraft);
    }

    private static final class StubHandler extends GroupTypeHandler<ChestLinkGroup> {

        StubHandler(GroupType type) {
            super(type, ChestLinkGroup.class);
        }

        @Override
        public boolean isValidBlock(Block block) {
            return false;
        }

        @Override
        public ChestLinkGroup create(long id, UUID owner, String name) {
            return new ChestLinkGroup(id, owner, name, 0);
        }

        @Override
        public void closeAllViews() {}

        @Override
        protected int linked(ChestLinkGroup group, Block block) {
            return 0;
        }

        @Override
        protected void removed(ChestLinkGroup group, Location dropAt) {}

        @Override
        protected void renamed(ChestLinkGroup group) {}

        @Override
        protected void revokedViews(ChestLinkGroup group) {}

        @Override
        protected void openRemotely(Player player, ChestLinkGroup group) {}

        @Override
        protected ItemStack iconOf(ChestLinkGroup group) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected String summaryOf(ChestLinkGroup group) {
            return "";
        }
    }
}
