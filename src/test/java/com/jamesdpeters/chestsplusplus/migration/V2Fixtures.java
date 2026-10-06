package com.jamesdpeters.chestsplusplus.migration;

import java.util.UUID;

/** v2 {@code storage.yml} content in the shape v2 wrote it, with the test's player UUIDs filled in. */
final class V2Fixtures {

    static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private V2Fixtures() {}

    static String location(String world, int x, int y, int z) {
        return """
                {==: org.bukkit.Location, world: %s, x: %d.0, y: %d.0, z: %d.0, pitch: 0.0, yaw: 0.0}""".formatted(world, x, y, z);
    }

    /**
     * Alice: "Iron Ore" (public, sorted, Bob a member, a diamond stack, one block in an unknown world), "iron" and "IRON" (names that clash in
     * v3), an AutoCrafter "torches" and one whose recipe is gone, and a party with Bob. Bob: "Old" in v2's oldest {@code locations} form.
     */
    static String storage() {
        return """
                chests++:
                  ==: ConfigStorage
                  chests:
                    %1$s:
                      Iron Ore:
                        ==: ChestLinkStorage
                        inventory:
                        - ==: org.bukkit.inventory.ItemStack
                          v: 3955
                          type: DIAMOND
                          amount: 5
                        - null
                        locationInfo:
                        - ==: LocationInfo
                          Location: %3$s
                        - ==: LocationInfo
                          Location: %4$s
                        playerUUID: %1$s
                        members:
                        - %2$s
                        isPublic: true
                        inventoryName: Iron Ore
                        sortMethod: AMOUNT_DESC
                      iron:
                        ==: ChestLinkStorage
                        inventory: []
                        locationInfo:
                        - ==: LocationInfo
                          Location: %5$s
                        playerUUID: %1$s
                        members: null
                        isPublic: false
                        inventoryName: iron
                        sortMethod: OFF
                      IRON:
                        ==: ChestLinkStorage
                        inventory: []
                        locationInfo:
                        - ==: LocationInfo
                          Location: %6$s
                        playerUUID: %1$s
                        isPublic: false
                        inventoryName: IRON
                        sortMethod: NAME
                    %2$s:
                      Old:
                        ==: ChestLinkStorage
                        inventory: []
                        locations:
                        - %7$s
                        playerUUID: %2$s
                        inventoryName: Old
                      Broken:
                        ==: ChestLinkStorage
                        inventory: []
                        locationInfo: []
                        playerUUID: not-a-uuid
                  autocraftingtables:
                    %1$s:
                      torches:
                        ==: AutoCraftingStorage
                        locationInfo:
                        - ==: LocationInfo
                          Location: %8$s
                        playerUUID: %1$s
                        members: null
                        isPublic: false
                        recipe:
                          ==: C++Recipe
                          namespace: minecraft
                          key: torch
                        identifier: torches
                      gone:
                        ==: AutoCraftingStorage
                        locationInfo: []
                        playerUUID: %1$s
                        isPublic: false
                        recipe:
                          ==: C++Recipe
                          namespace: removedpack
                          key: thing
                        identifier: gone
                  parties:
                    %1$s:
                      ==: PlayerPartyStorage
                      owner: %1$s
                      ownedParties:
                        friends:
                          ==: PlayerParty
                          owner: %1$s
                          partyName: friends
                          members:
                          - %2$s
                          - %1$s
                """.formatted(ALICE, BOB, location("world", 0, 64, 0), location("lost_world", 5, 64, 5), location("world", 2, 64, 0),
                location("world", 4, 64, 0), location("world", 6, 64, 0), location("world", 0, 64, 4));
    }
}
