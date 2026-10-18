package com.jamesdpeters.chestsplusplus.migration;

import static com.jamesdpeters.chestsplusplus.migration.V2Fixtures.ALICE;
import static com.jamesdpeters.chestsplusplus.migration.V2Fixtures.BOB;
import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.testing.PluginTestBase;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Needs a server for item deserialisation, so it runs on MockBukkit. */
class V2StorageTest extends PluginTestBase {

    private static V2Data.Group group(V2Data data, GroupType type, String name) {
        return data.groups().stream().filter(group -> group.type() == type && group.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void readsGroupsRecipesAndParties() {
        V2Data data = V2Storage.parse(V2Fixtures.storage());

        V2Data.Group ore = group(data, GroupType.CHESTLINK, "Iron Ore");
        assertThat(ore.owner()).isEqualTo(ALICE);
        assertThat(ore.isPublic()).isTrue();
        assertThat(ore.sortMode()).isEqualTo(SortMode.AMOUNT_DESC);
        assertThat(ore.members()).containsExactly(BOB);
        assertThat(ore.items()).containsExactly(ItemStack.of(Material.DIAMOND, 5), null);
        assertThat(ore.locations()).containsExactly(new V2Data.Location("world", 0, 64, 0), new V2Data.Location("lost_world", 5, 64, 5));
        assertThat(ore.source()).isEqualTo("chestlink:" + ALICE + ":Iron Ore");

        assertThat(group(data, GroupType.CHESTLINK, "IRON").sortMode()).isEqualTo(SortMode.NAME);
        assertThat(group(data, GroupType.CHESTLINK, "iron").members()).isEmpty();

        V2Data.Group torches = group(data, GroupType.AUTOCRAFT, "torches");
        assertThat(torches.items()).isNull();
        assertThat(torches.recipe()).isEqualTo(new V2Data.Recipe(NamespacedKey.minecraft("torch"), null));
        assertThat(torches.locations()).containsExactly(new V2Data.Location("world", 0, 64, 4));

        assertThat(data.parties()).containsExactly(new V2Data.Party(ALICE, "friends", List.of(BOB, ALICE)));
    }

    @Test
    void readsTheOldestLocationFormAndSkipsBrokenEntries() {
        V2Data data = V2Storage.parse(V2Fixtures.storage());

        assertThat(group(data, GroupType.CHESTLINK, "Old").locations()).containsExactly(new V2Data.Location("world", 6, 64, 0));
        assertThat(data.groups()).noneMatch(group -> group.name().equals("Broken"));
        assertThat(data.problems()).singleElement().asString().contains("Broken");
    }

    @Test
    void readsWorldKeysAndReportsLocationsWithNoWorld() {
        V2Data data = V2Storage.parse(V2Fixtures.keyedStorage("minecraft:overworld", "minecraft:schneckenhaus"));

        assertThat(group(data, GroupType.CHESTLINK, "sorter-in").locations()).containsExactly(new V2Data.Location("minecraft:overworld", 0, 64, 0),
                new V2Data.Location("minecraft:schneckenhaus", 2, 28, 5));
        assertThat(data.problems()).singleElement().asString().contains("sorter-in", "could not be read");
    }

    @Test
    void readsTheOldRecipeTypeName() {
        String legacy = V2Fixtures.storage().replace("==: C++Recipe", "==: Recipe");

        V2Data data = V2Storage.parse(legacy);

        assertThat(data.groups()).hasSize(6);
        assertThat(group(data, GroupType.AUTOCRAFT, "torches").recipe()).isNotNull();
    }

    /** The storage.yml v2 itself wrote for the upgrade test bed on Paper 1.21.7 (./gradlew v2UpgradeFixture copies it here). */
    @Test
    void readsTheFileV2WroteForTheTestBed() throws Exception {
        V2Data data = readFixture("fixture-storage.yml");

        assertTestBedCases(data);
        assertThat(group(data, GroupType.CHESTLINK, "Storage").locations()).containsExactly(new V2Data.Location("world", 0, -60, 0),
                new V2Data.Location("world", 2, -60, 0));
        assertThat(group(data, GroupType.CHESTLINK, "Nether").locations()).containsExactly(new V2Data.Location("world_nether", 0, 100, 0));
    }

    /**
     * The same cases written by v2 on Paper 26.3 (-Pchestsplusplus.v2Paper=26.3). Locations name their world by key; items are
     * serialised as on 1.21.7 apart from the data version. v2 left running on a server upgraded from 1.21.7 writes the same file.
     */
    @Test
    void readsTheFileV2WroteOnPaper26() throws Exception {
        V2Data data = readFixture("fixture-storage-26.3.yml");

        assertTestBedCases(data);
        assertThat(group(data, GroupType.CHESTLINK, "Storage").locations()).containsExactly(new V2Data.Location("minecraft:overworld", 0, -60, 0),
                new V2Data.Location("minecraft:overworld", 2, -60, 0));
        assertThat(group(data, GroupType.CHESTLINK, "Nether").locations())
                .containsExactly(new V2Data.Location("minecraft:the_nether", 0, 100, 0));
    }

    private V2Data readFixture(String name) throws Exception {
        URL resource = getClass().getResource("/v2/" + name);
        Assumptions.assumeTrue(resource != null, "run ./gradlew v2UpgradeFixture to create the v2-written fixture");
        return V2Storage.read(Path.of(resource.toURI()));
    }

    /**
     * MockBukkit can't read item components yet (Bukkit then returns null, which is reported as a problem), so only plain items are
     * checked here. The real server reads every item; the upgrade test bed checks that (docs/testing.md#v2-upgrade, CL-basic).
     */
    private static void assertTestBedCases(V2Data data) {
        assertThat(data.problems()).allSatisfy(problem -> assertThat(problem).startsWith("A saved org.bukkit.inventory.ItemStack could not be read"));
        assertThat(data.groups()).extracting(V2Data.Group::name).contains("Storage", "Double", "Iron Ore", "iron", "IRON", "Public", "Shared", "Gift",
                "Hoppers", "Stale", "Nether", "Distant", "torches", "bonemeal", "repair", "gone", "empty");
        assertThat(data.groups()).allSatisfy(group -> assertThat(group.locations()).isNotEmpty());
        assertThat(group(data, GroupType.CHESTLINK, "Storage").sortMode()).isEqualTo(SortMode.NAME);
        assertThat(group(data, GroupType.CHESTLINK, "Storage").items()).contains(ItemStack.of(Material.COBBLESTONE, 64));
        assertThat(group(data, GroupType.CHESTLINK, "Double").items()).contains(ItemStack.of(Material.OAK_LOG, 32));
        assertThat(group(data, GroupType.CHESTLINK, "Shared").members()).containsExactly(BOB);
        assertThat(group(data, GroupType.CHESTLINK, "Gift").owner()).isEqualTo(BOB);
        assertThat(group(data, GroupType.AUTOCRAFT, "torches").recipe().key()).isEqualTo(NamespacedKey.minecraft("torch"));
        assertThat(group(data, GroupType.AUTOCRAFT, "bonemeal").recipe()).isNotNull();
        assertThat(group(data, GroupType.AUTOCRAFT, "repair").recipe().items()).isNotNull();
        assertThat(group(data, GroupType.AUTOCRAFT, "gone").recipe().key()).isEqualTo(new NamespacedKey("v2fixture", "gone"));
        assertThat(group(data, GroupType.AUTOCRAFT, "empty").recipe()).isNull();
        assertThat(data.parties()).containsExactly(new V2Data.Party(ALICE, "friends", List.of(BOB)));
    }

    @Test
    void reportsAnItemBukkitCannotRead() {
        V2Data data = V2Storage.parse(V2Fixtures.storage().replace("type: DIAMOND", "type: NOT_A_MATERIAL"));

        assertThat(group(data, GroupType.CHESTLINK, "Iron Ore").items()).containsOnlyNulls();
        assertThat(data.problems()).anySatisfy(problem -> assertThat(problem).contains("ItemStack could not be read", "NOT_A_MATERIAL"));
    }

    @Test
    void anEmptyOrForeignFileHasNothingToImport() {
        assertThat(V2Storage.parse("").groups()).isEmpty();
        assertThat(V2Storage.parse("something: else").groups()).isEmpty();
    }
}
