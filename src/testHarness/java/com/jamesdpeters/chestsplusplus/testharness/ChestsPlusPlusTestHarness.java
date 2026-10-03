package com.jamesdpeters.chestsplusplus.testharness;

import com.jamesdpeters.chestsplusplus.ChestsPlusPlus;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * E2E test harness. Never shipped: built from {@code src/testHarness} into ChestsPlusPlus-TestHarness.jar and only
 * installed on the E2E server. Exposes console-only, read-only {@code /cpptest} inspection commands with plain,
 * parseable output (driven over RCON).
 */
public final class ChestsPlusPlusTestHarness extends JavaPlugin {

    @Override
    public void onEnable() {
        getLifecycleManager()
                .registerEventHandler(
                        LifecycleEvents.COMMANDS,
                        event -> event.registrar()
                                .register(
                                        Commands.literal("cpptest")
                                                .requires(source -> source.getSender() instanceof ConsoleCommandSender)
                                                .then(Commands.literal("ping")
                                                        .executes(context -> reply(context, "cpptest pong")))
                                                .then(Commands.literal("plugin").executes(this::pluginState))
                                                .build(),
                                        "ChestsPlusPlus E2E test harness (console only)"));
    }

    /** Reads the plugin through the joined classpath, proving the harness can see ChestsPlusPlus internals. */
    private int pluginState(CommandContext<CommandSourceStack> context) {
        ChestsPlusPlus plugin = JavaPlugin.getPlugin(ChestsPlusPlus.class);
        return reply(
                context,
                "cpptest plugin enabled=" + plugin.isEnabled() + " version="
                        + plugin.getPluginMeta().getVersion());
    }

    private static int reply(CommandContext<CommandSourceStack> context, String line) {
        context.getSource().getSender().sendPlainMessage(line);
        return Command.SINGLE_SUCCESS;
    }
}
