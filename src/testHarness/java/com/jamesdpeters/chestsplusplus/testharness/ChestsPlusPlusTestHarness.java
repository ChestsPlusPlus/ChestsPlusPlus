package com.jamesdpeters.chestsplusplus.testharness;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.display.DisplayService;
import com.jamesdpeters.chestsplusplus.link.GroupTypeHandler;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Display;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * E2E test harness. Never shipped: built from {@code src/testHarness} into ChestsPlusPlus-TestHarness.jar and only
 * installed on the E2E server. Console/RCON-only {@code /cpptest} commands with plain, parseable output. Everything is
 * read-only except the explicit fixture commands ({@code link}, {@code reset}).
 */
public final class ChestsPlusPlusTestHarness extends JavaPlugin {

    @Override
    public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(Commands.literal("cpptest").requires(ChestsPlusPlusTestHarness::isConsole)
                        .then(Commands.literal("ping").executes(c -> reply(c, "cpptest pong")))
                        .then(Commands.literal("plugin").executes(this::pluginState)).then(withGroupArgs(Commands.literal("group"), this::group))
                        .then(Commands.literal("displays").executes(this::displays)).then(Commands.literal("reset").executes(this::reset))
                        .then(Commands.literal("recipe")
                                .then(Commands.argument("owner", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .then(Commands.argument("matrix", StringArgumentType.greedyString()).executes(this::recipe)))))
                        .then(Commands
                                .literal(
                                        "filter")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                                .then(Commands.argument("material", StringArgumentType.greedyString())
                                                                        .executes(this::filter)))))))
                        .then(Commands.literal("link")
                                .then(Commands.argument("type", StringArgumentType.word()).then(Commands.argument("owner", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                                        .then(Commands.argument("y", IntegerArgumentType.integer()).then(
                                                                Commands.argument("z", IntegerArgumentType.integer()).executes(this::link))))))))
                        .build(), "ChestsPlusPlus E2E test harness (console only)"));
    }

    /** {@code <type> <owner> <name>} arguments, executing {@code command} at the end if given. */
    private static ArgumentBuilder<CommandSourceStack, ?> withGroupArgs(ArgumentBuilder<CommandSourceStack, ?> root,
            @Nullable Command<CommandSourceStack> command) {
        var name = Commands.argument("name", StringArgumentType.word());
        if (command != null) name.executes(command);
        return root.then(Commands.argument("type", StringArgumentType.word()).then(Commands.argument("owner", StringArgumentType.word()).then(name)));
    }

    private Services services() {
        ChestsPlusPlus plugin = JavaPlugin.getPlugin(ChestsPlusPlus.class);
        Services services = plugin.services();
        if (services == null) throw new IllegalStateException("ChestsPlusPlus is not enabled");
        return services;
    }

    private int pluginState(CommandContext<CommandSourceStack> context) {
        ChestsPlusPlus plugin = JavaPlugin.getPlugin(ChestsPlusPlus.class);
        return reply(context, "cpptest plugin enabled=" + plugin.isEnabled() + " version=" + plugin.getPluginMeta().getVersion());
    }

    private static GroupType type(CommandContext<CommandSourceStack> context) {
        return GroupType.valueOf(StringArgumentType.getString(context, "type").toUpperCase(Locale.ROOT));
    }

    private static UUID owner(CommandContext<CommandSourceStack> context) {
        return Bukkit.getOfflinePlayer(StringArgumentType.getString(context, "owner")).getUniqueId();
    }

    /** {@code cpptest group <type> <owner> <name>} → {@code cpptest group <name> nodes=<n> items={MATERIAL=n, ...}}. */
    private int group(CommandContext<CommandSourceStack> context) {
        Services services = services();
        String name = StringArgumentType.getString(context, "name");
        StorageGroup group = services.groups().find(type(context), owner(context), name);
        if (group == null) return reply(context, "cpptest group " + name + " missing");
        String items = "-";
        if (group instanceof ChestLinkGroup chest) {
            Map<String, Integer> totals = new TreeMap<>();
            Arrays.stream(chest.inventory().getContents()).filter(Objects::nonNull)
                    .forEach(item -> totals.merge(item.getType().name(), item.getAmount(), Integer::sum));
            items = totals.toString();
        }
        return reply(context, "cpptest group " + group.name() + " nodes=" + services.nodes().count(group.id()) + " public=" + group.isPublic()
                + " dirty=" + services.groupStore().isDirty(group) + " items=" + items);
    }

    /** {@code cpptest displays} → counts of our display entities (by PDC marker) vs tracked displays. */
    private int displays(CommandContext<CommandSourceStack> context) {
        Services services = services();
        DisplayService displays = services.get(DisplayService.class);
        int entities = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Display display : world.getEntitiesByClass(Display.class)) if (displays.isOurs(display)) entities++;
        }
        return reply(context, "cpptest displays tracked=" + displays.count() + " entities=" + entities);
    }

    /** Fixture: {@code cpptest link <type> <owner> <name> <x> <y> <z>} links a block in the first world. */
    private int link(CommandContext<CommandSourceStack> context) {
        Services services = services();
        GroupType type = type(context);
        UUID owner = owner(context);
        String name = StringArgumentType.getString(context, "name");
        World world = Bukkit.getWorlds().getFirst();
        Block block = world.getBlockAt(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"),
                IntegerArgumentType.getInteger(context, "z"));
        LinkService links = services.get(LinkService.class);
        GroupTypeHandler handler = links.handler(type);
        if (handler == null || !handler.isValidBlock(block)) {
            return reply(context, "cpptest link failed: " + block.getType() + " is not valid for " + type);
        }
        StorageGroup group = services.groups().find(type, owner, name);
        if (group == null) {
            group = handler.create(services.groups().nextId(), owner, name);
            services.groups().add(group);
        }
        links.addNode(group, block, BlockFace.NORTH);
        handler.onLinked(group, block);
        return reply(context, "cpptest link ok " + group.name() + " nodes=" + services.nodes().count(group.id()));
    }

    /**
     * Fixture: {@code cpptest recipe <owner> <name> <m1,...,m9>} ({@code -} for empty) sets an AutoCraft group's
     * matrix through the same path as the editor and reports the resolved result.
     */
    private int recipe(CommandContext<CommandSourceStack> context) {
        Services services = services();
        StorageGroup group = services.groups().find(GroupType.AUTOCRAFT, owner(context), StringArgumentType.getString(context, "name"));
        if (!(group instanceof com.jamesdpeters.chestsplusplus.model.AutoCraftGroup craft)) {
            return reply(context, "cpptest recipe failed: no such AutoCraft group");
        }
        String[] parts = StringArgumentType.getString(context, "matrix").split(",");
        org.bukkit.inventory.ItemStack[] matrix = new org.bukkit.inventory.ItemStack[9];
        for (int i = 0; i < 9 && i < parts.length; i++) {
            org.bukkit.Material material = org.bukkit.Material.matchMaterial(parts[i].trim());
            matrix[i] = material == null ? null : org.bukkit.inventory.ItemStack.of(material);
        }
        var result = services.get(com.jamesdpeters.chestsplusplus.autocraft.AutoCraftService.class).setMatrix(craft, matrix, null);
        return reply(context, "cpptest recipe result=" + (result == null ? "none" : result.getType() + "x" + result.getAmount()));
    }

    /** Fixture: {@code cpptest filter <x> <y> <z> <allow|deny> <material>} sets one TYPE filter on a hopper. */
    private int filter(CommandContext<CommandSourceStack> context) {
        Services services = services();
        Block block = Bukkit.getWorlds().getFirst().getBlockAt(IntegerArgumentType.getInteger(context, "x"),
                IntegerArgumentType.getInteger(context, "y"), IntegerArgumentType.getInteger(context, "z"));
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(StringArgumentType.getString(context, "material"));
        if (material == null) return reply(context, "cpptest filter failed: unknown material");
        var mode = com.jamesdpeters.chestsplusplus.filter.HopperFilter.Mode
                .valueOf(StringArgumentType.getString(context, "mode").toUpperCase(Locale.ROOT));
        var filters = services.get(com.jamesdpeters.chestsplusplus.filter.FilterService.class);
        filters.write(block, List.of(new com.jamesdpeters.chestsplusplus.filter.HopperFilter(org.bukkit.inventory.ItemStack.of(material), mode,
                com.jamesdpeters.chestsplusplus.filter.HopperFilter.Match.TYPE)));
        return reply(context, "cpptest filter ok indexed=" + (filters.get(block) != null));
    }

    /** Fixture: removes every group (contents are discarded, not dropped). */
    private int reset(CommandContext<CommandSourceStack> context) {
        Services services = services();
        LinkService links = services.get(LinkService.class);
        List<StorageGroup> all = new ArrayList<>(services.groups().all());
        for (StorageGroup group : all) {
            if (group instanceof ChestLinkGroup chest) chest.inventory().clear();
            links.removeGroup(group, new org.bukkit.Location(Bukkit.getWorlds().getFirst(), 0, -100, 0));
        }
        return reply(context, "cpptest reset removed=" + all.size());
    }

    /** Server console or RCON (how the E2E suite drives it); never players or command blocks. */
    private static boolean isConsole(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        return sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender;
    }

    private static int reply(CommandContext<CommandSourceStack> context, String line) {
        context.getSource().getSender().sendPlainMessage(line);
        return Command.SINGLE_SUCCESS;
    }
}
