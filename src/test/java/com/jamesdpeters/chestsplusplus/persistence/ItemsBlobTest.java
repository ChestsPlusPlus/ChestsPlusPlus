package com.jamesdpeters.chestsplusplus.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ItemsBlobTest extends PluginTestBase {

    public record Stash(long id, @Nullable ItemStack @Nullable [] items) {}

    @Test
    void itemsSerialisedOffTheMainThreadRoundTripWithEmptySlotsAsNull() {
        ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD);
        sword.editMeta(meta -> meta.displayName(Component.text("Excalibur")));
        @Nullable ItemStack[] items = {sword, null, ItemStack.of(Material.OAK_LOG, 64)};
        RecordTable<Stash> stashes = new RecordTable<>(Stash.class, "stashes", "id");

        try (Database db = Database.open("jdbc:sqlite::memory:")) {
            db.handle().execute("CREATE TABLE stashes (id INTEGER PRIMARY KEY, items BLOB)");
            CompletableFuture.runAsync(() -> stashes.upsert(db.handle(), List.of(new Stash(1, items), new Stash(2, null)))).join();

            List<Stash> loaded = stashes.all(db.handle());
            assertThat(loaded.getFirst().items()).containsExactly(sword, null, ItemStack.of(Material.OAK_LOG, 64));
            assertThat(loaded.get(1).items()).isNull();
        }
    }
}
