package com.jamesdpeters.chestsplusplus.v2fixture;

import com.jamesdpeters.minecraft.chests.serialize.Config;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Runs on the v2 server only and sets worlds up with v2's own classes: {@code /v2fixture build <tester> <other>} lays out the upgrade cases,
 * {@code /v2fixture benchmark <chestlinks> <autocrafters> <filters>} the benchmark cells.
 */
public final class V2FixturePlugin extends JavaPlugin {

    static final String BENCHMARK_OWNER = "Bench";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 3 && args[0].equals("build")) {
            new Scenario(offline(args[1]), offline(args[2])).build();
            Config.save();
            sender.sendMessage("v2 fixture built for " + args[1] + " and " + args[2]);
            return true;
        }
        if (args.length == 4 && args[0].equals("benchmark")) {
            String area = new BenchmarkScenario(offline(BENCHMARK_OWNER)).build(Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                    Integer.parseInt(args[3]));
            Config.save();
            sender.sendMessage("v2 benchmark built in " + area);
            return true;
        }
        return false;
    }

    /** The server runs in offline mode, where a player's UUID comes from their name alone. */
    private OfflinePlayer offline(String name) {
        return getServer().getOfflinePlayer(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
    }
}
