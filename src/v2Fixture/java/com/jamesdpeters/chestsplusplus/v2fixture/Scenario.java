package com.jamesdpeters.chestsplusplus.v2fixture;

import com.jamesdpeters.minecraft.chests.party.PlayerParty;
import com.jamesdpeters.minecraft.chests.party.PlayerPartyStorage;
import com.jamesdpeters.minecraft.chests.serialize.Config;
import com.jamesdpeters.minecraft.chests.sort.SortMethod;
import com.jamesdpeters.minecraft.chests.storage.autocraft.AutoCraftingStorage;
import com.jamesdpeters.minecraft.chests.storage.chestlink.ChestLinkStorage;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.sign.Side;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.jspecify.annotations.Nullable;

/**
 * The v2 upgrade cases (docs/v2-migration-plan.md §8.2), laid out in rows south of spawn with every linked block facing spawn. A standing
 * sign beside each case names it and says what v3 should do with it. ChestLinks are z=0, AutoCrafters z=10, hopper filters z=20.
 */
final class Scenario {

    private static final int CHESTLINKS = 0;
    private static final int AUTOCRAFTERS = 10;
    private static final int FILTERS = 20;
    private static final int FAR_X = 2000;
    /** v2's sign tags (its {@code Values} class isn't in the shaded jar). */
    static final String CHESTLINK_TAG = "[ChestLink]";
    static final String AUTOCRAFT_TAG = "[AutoCraft]";
    /** Where v2 keeps a link sign's owner ({@code Values.playerUUID}). */
    private static final NamespacedKey V2_OWNER_KEY = new NamespacedKey("chestsplusplus", "playeruuid");

    private final OfflinePlayer tester;
    private final OfflinePlayer other;
    private final World world;
    private final int y;

    Scenario(OfflinePlayer tester, OfflinePlayer other) {
        this.tester = tester;
        this.other = other;
        this.world = Bukkit.getWorlds().getFirst();
        this.y = world.getHighestBlockYAt(0, 0) + 1;
    }

    void build() {
        world.setSpawnLocation(0, y, -8);
        chestLinks();
        autoCrafters();
        filters();
        party();
    }

    private void chestLinks() {
        Block storageA = chest(0, CHESTLINKS, BlockFace.NORTH);
        fill(storageA, new ItemStack(Material.COBBLESTONE, 64), enchantedSword(), named(), potion(), book(), shulker());
        ChestLinkStorage storage = chestLink(tester, "Storage", storageA, BlockFace.NORTH);
        storage.setSortMethod(SortMethod.NAME);
        Block storageB = chest(2, CHESTLINKS, BlockFace.EAST);
        storage.addLocation(storageB.getLocation(), linkSign(storageB, BlockFace.EAST, CHESTLINK_TAG, "Storage", tester));
        label(1, CHESTLINKS, "CL-basic", "2 blocks N + E", "items intact", "sort: name");

        Block left = chest(6, CHESTLINKS, BlockFace.NORTH);
        Block right = chest(7, CHESTLINKS, BlockFace.NORTH);
        doubleChest(left, right);
        fill(left, new ItemStack(Material.OAK_LOG, 32));
        chestLink(tester, "Double", left, BlockFace.NORTH);
        label(8, CHESTLINKS, "CL-double", "split into", "two singles", "");

        String[] names = {"Iron Ore", "iron", "IRON", "ThisChestLinkNameIsLongerThan32Chars", "§aGreen"};
        for (int i = 0; i < names.length; i++) chestLink(tester, names[i], chest(10 + i * 2, CHESTLINKS, BlockFace.NORTH), BlockFace.NORTH);
        label(11, CHESTLINKS, "CL-names", "Iron Ore, iron", "IRON_2, cut to", "32, Green");

        chestLink(tester, "Public", chest(22, CHESTLINKS, BlockFace.NORTH), BlockFace.NORTH).setPublic(true);
        chestLink(tester, "Shared", chest(24, CHESTLINKS, BlockFace.NORTH), BlockFace.NORTH).addMember(other);
        chestLink(other, "Gift", chest(26, CHESTLINKS, BlockFace.NORTH), BlockFace.NORTH).addMember(tester);
        label(23, CHESTLINKS, "CL-access", "public; member", "other:Gift has", "you as member");

        hoppers();

        Block stale = chest(34, CHESTLINKS, BlockFace.NORTH);
        chestLink(tester, "Stale", stale, BlockFace.NORTH);
        stale.setType(Material.AIR);
        label(35, CHESTLINKS, "CL-stale", "chest broken", "after linking:", "block unlinked");

        World nether = Objects.requireNonNull(Bukkit.getWorld(world.getName() + "_nether"), "the nether must be enabled");
        nether.getBlockAt(0, 99, 0).setType(Material.NETHERRACK);
        Block netherChest = nether.getBlockAt(0, 100, 0);
        netherChest.setType(Material.CHEST);
        facing(netherChest, BlockFace.NORTH);
        chestLink(tester, "Nether", netherChest, BlockFace.NORTH);
        label(38, CHESTLINKS, "CL-nether", "nether 0 100 0", "blacklisted but", "still imported");

        chestLink(tester, "Distant", chest(FAR_X, CHESTLINKS, BlockFace.NORTH), BlockFace.NORTH);
        label(42, CHESTLINKS, "CL-faraway", "x=" + FAR_X + ": fixed", "when you visit", "or convert-all");
    }

    /** Cobblestone drops through a hopper into the linked chest and out through another into a plain chest. */
    private void hoppers() {
        Block linked = world.getBlockAt(30, y + 1, CHESTLINKS);
        linked.setType(Material.CHEST);
        facing(linked, BlockFace.NORTH);
        world.getBlockAt(30, y, CHESTLINKS).setType(Material.HOPPER);
        facing(world.getBlockAt(30, y, CHESTLINKS), BlockFace.EAST);
        world.getBlockAt(31, y, CHESTLINKS).setType(Material.CHEST);
        Block top = world.getBlockAt(30, y + 2, CHESTLINKS);
        top.setType(Material.HOPPER);
        ((Container) top.getState()).getInventory().addItem(new ItemStack(Material.COBBLESTONE, 64));
        chestLink(tester, "Hoppers", linked, BlockFace.NORTH);
        label(32, CHESTLINKS, "CL-hoppers", "items still", "flow through", "");
    }

    private void autoCrafters() {
        AutoCraftingStorage torches = autoCraft("torches", 0, BlockFace.NORTH, recipe("minecraft:torch"),
                grid(new ItemStack(Material.COAL), null, null,
                        new ItemStack(Material.STICK)));
        Block second = table(2, AUTOCRAFTERS);
        torches.addLocation(second.getLocation(), linkSign(second, BlockFace.EAST, AUTOCRAFT_TAG, "torches", tester));
        label(1, AUTOCRAFTERS, "AC-shaped", "torch; 2nd table", "faces east", "");

        autoCraft("bonemeal", 6, BlockFace.NORTH, recipe("minecraft:bone_meal"), grid(new ItemStack(Material.BONE)));
        label(7, AUTOCRAFTERS, "AC-shapeless", "bone meal", "", "");

        ItemStack worn = damaged(new ItemStack(Material.IRON_PICKAXE));
        autoCraft("repair", 10, BlockFace.NORTH, recipe("minecraft:repair_item"), grid(worn, worn.clone()));
        label(11, AUTOCRAFTERS, "AC-complex", "repair recipe", "kept as items", "");

        autoCraft("gone", 14, BlockFace.NORTH, recipe("v2fixture:gone"), grid(new ItemStack(Material.DIRT), new ItemStack(Material.DIRT)));
        label(15, AUTOCRAFTERS, "AC-missing", "datapack recipe", "removed: no", "recipe in v3");

        autoCraft("empty", 18, BlockFace.NORTH, null, grid());
        label(19, AUTOCRAFTERS, "AC-empty", "no recipe", "", "");
    }

    /** One hopper per frame rotation, one with three frames, and one far away for convert-all. */
    private void filters() {
        List<Rotation> rotations = List.of(Rotation.NONE, Rotation.FLIPPED, Rotation.CLOCKWISE, Rotation.COUNTER_CLOCKWISE);
        List<String> results = List.of("allow exact", "allow similar", "deny exact", "deny similar");
        for (int i = 0; i < rotations.size(); i++) {
            Block hopper = hopper(i * 4, FILTERS);
            frame(hopper, BlockFace.NORTH, new ItemStack(Material.OAK_LOG), rotations.get(i));
            label(i * 4 + 1, FILTERS, "Filter " + rotations.get(i).name().toLowerCase(Locale.ROOT), "-> " + results.get(i), "oak log", "");
        }
        Block multi = hopper(16, FILTERS);
        frame(multi, BlockFace.NORTH, new ItemStack(Material.COAL), Rotation.NONE);
        frame(multi, BlockFace.EAST, new ItemStack(Material.IRON_INGOT), Rotation.NONE);
        frame(multi, BlockFace.WEST, new ItemStack(Material.DIRT), Rotation.CLOCKWISE);
        label(17, FILTERS, "Filter multi", "allow coal +", "iron, deny dirt", "");
        frame(hopper(FAR_X + 4, CHESTLINKS), BlockFace.NORTH, new ItemStack(Material.SAND), Rotation.NONE);
        label(21, FILTERS, "Filter far", "x=" + (FAR_X + 4) + " z=0", "convert-all", "finds it");
    }

    private void party() {
        PlayerPartyStorage storage = new PlayerPartyStorage(tester);
        PlayerParty friends = new PlayerParty(tester, "friends");
        friends.addMember(other);
        storage.getOwnedParties().put("friends", friends);
        Config.getStore().parties.put(tester.getUniqueId().toString(), storage);
        label(-4, CHESTLINKS, "Party", "friends with", Objects.requireNonNullElse(other.getName(), "other"), "-> trusted");
    }

    static ChestLinkStorage chestLink(OfflinePlayer owner, String name, Block block, BlockFace face) {
        Location sign = linkSign(block, face, CHESTLINK_TAG, name, owner);
        ChestLinkStorage storage = new ChestLinkStorage(owner, name, block.getLocation(), sign);
        Config.getStore().chests.computeIfAbsent(owner.getUniqueId().toString(), k -> new HashMap<>()).put(name, storage);
        return storage;
    }

    private AutoCraftingStorage autoCraft(String name, int x, BlockFace face, @Nullable Recipe recipe, @Nullable ItemStack[] items) {
        return autoCraft(tester, name, table(x, AUTOCRAFTERS), face, recipe, items);
    }

    static AutoCraftingStorage autoCraft(OfflinePlayer owner, String name, Block table, BlockFace face, @Nullable Recipe recipe,
            @Nullable ItemStack[] items) {
        AutoCraftingStorage storage = new AutoCraftingStorage(owner, name, table.getLocation(), linkSign(table, face, AUTOCRAFT_TAG, name, owner));
        storage.setRecipe(recipe, items);
        Config.getStore().autocraftingtables.computeIfAbsent(owner.getUniqueId().toString(), k -> new HashMap<>()).put(name, storage);
        return storage;
    }

    private static Recipe recipe(String key) {
        return Objects.requireNonNull(Bukkit.getRecipe(Objects.requireNonNull(NamespacedKey.fromString(key))), "no recipe " + key);
    }

    static @Nullable ItemStack[] grid(@Nullable ItemStack... items) {
        @Nullable ItemStack[] grid = new ItemStack[9];
        System.arraycopy(items, 0, grid, 0, items.length);
        return grid;
    }

    /**
     * The wall sign v2 placed on a linked block's front: the tag, then the group name in brackets. v2 only treats it as a link when it
     * also carries the owner's UUID.
     */
    static Location linkSign(Block block, BlockFace face, String tag, String name, OfflinePlayer owner) {
        Block signBlock = block.getRelative(face);
        signBlock.setType(Material.OAK_WALL_SIGN);
        facing(signBlock, face);
        Sign sign = (Sign) signBlock.getState();
        sign.getSide(Side.FRONT).line(0, Component.text(tag, NamedTextColor.DARK_GREEN));
        sign.getSide(Side.FRONT).line(1, Component.text("[" + name + "]"));
        sign.getPersistentDataContainer().set(V2_OWNER_KEY, PersistentDataType.STRING, owner.getUniqueId().toString());
        sign.update();
        return signBlock.getLocation();
    }

    /** A standing sign naming a case; standing, so the migration can't take it for a v2 link sign. */
    private void label(int x, int z, String... lines) {
        Block block = world.getBlockAt(x, y, z - 2);
        block.setType(Material.OAK_SIGN);
        Rotatable rotation = (Rotatable) block.getBlockData();
        rotation.setRotation(BlockFace.NORTH);
        block.setBlockData(rotation);
        Sign sign = (Sign) block.getState();
        for (int i = 0; i < lines.length && i < 4; i++)
            sign.getSide(Side.FRONT).line(i, Component.text(lines[i], i == 0 ? NamedTextColor.GOLD : NamedTextColor.BLACK));
        sign.update();
    }

    private Block chest(int x, int z, BlockFace face) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.CHEST);
        facing(block, face);
        return block;
    }

    private Block table(int x, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.CRAFTING_TABLE);
        return block;
    }

    private Block hopper(int x, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.HOPPER);
        return block;
    }

    static void facing(Block block, BlockFace face) {
        Directional data = (Directional) block.getBlockData();
        data.setFacing(face);
        block.setBlockData(data);
    }

    /** Facing north, a LEFT half's partner is to its east. */
    private static void doubleChest(Block left, Block right) {
        Chest leftData = (Chest) left.getBlockData();
        leftData.setType(Chest.Type.LEFT);
        left.setBlockData(leftData);
        Chest rightData = (Chest) right.getBlockData();
        rightData.setType(Chest.Type.RIGHT);
        right.setBlockData(rightData);
    }

    private static void fill(Block chest, ItemStack... items) {
        ((Container) chest.getState()).getInventory().addItem(items);
    }

    private void frame(Block hopper, BlockFace side, ItemStack item, Rotation rotation) {
        world.spawn(hopper.getRelative(side).getLocation(), ItemFrame.class, frame -> {
            frame.setFacingDirection(side, true);
            frame.setItem(item);
            frame.setRotation(rotation);
        });
    }

    private static ItemStack enchantedSword() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(Enchantment.SHARPNESS, 5);
        return damaged(sword);
    }

    private static ItemStack named() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        item.editMeta(meta -> meta.displayName(Component.text("Lucky charm", NamedTextColor.LIGHT_PURPLE)));
        return item;
    }

    private static ItemStack potion() {
        ItemStack potion = new ItemStack(Material.POTION);
        potion.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(PotionType.SWIFTNESS));
        return potion;
    }

    private static ItemStack book() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(BookMeta.class, meta -> {
            meta.title(Component.text("Notes"));
            meta.author(Component.text("v2"));
            meta.addPages(Component.text("Written on ChestsPlusPlus v2."));
        });
        return book;
    }

    private static ItemStack shulker() {
        ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
        shulker.editMeta(BlockStateMeta.class, meta -> {
            ShulkerBox box = (ShulkerBox) meta.getBlockState();
            box.getInventory().addItem(new ItemStack(Material.DIAMOND, 32), new ItemStack(Material.EMERALD, 16));
            meta.setBlockState(box);
        });
        return shulker;
    }

    private static ItemStack damaged(ItemStack item) {
        item.editMeta(Damageable.class, meta -> meta.setDamage(100));
        return item;
    }
}
