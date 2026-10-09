package com.jamesdpeters.chestsplusplus.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.Ignore;
import de.exlll.configlib.NameFormatters;
import de.exlll.configlib.PostProcess;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurationStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * {@code config.yml}, mapped by ConfigLib: the field initialisers are the defaults and the {@code @Comment}s are written into the file.
 * Nothing changes an instance after loading; {@code /cpp reload} swaps in a new one.
 */
@Configuration
@Getter
public final class Settings {

    private static final YamlConfigurationStore<Settings> STORE = new YamlConfigurationStore<>(Settings.class,
            YamlConfigurationProperties.newBuilder()
                    .header("ChestsPlusPlus v3 configuration. Reload with /cpp reload.")
                    .setNameFormatter(NameFormatters.LOWER_KEBAB_CASE)
                    .build());

    private Features features = new Features();
    private Linking linking = new Linking();
    private ChestLink chestlink = new ChestLink();
    private AutoCraft autocraft = new AutoCraft();
    private Filters filters = new Filters();
    @Comment("-1 = unlimited. Overridden per player by chestsplusplus.limit.chestlink.<n> / chestsplusplus.limit.autocraft.<n>.") private Limits limits = new Limits();
    private Worlds worlds = new Worlds();
    private Storage storage = new Storage();
    private Toggle updateChecker = new Toggle();
    private Toggle metrics = new Toggle();

    /** Loads {@code file}, or creates it, and writes it back so keys added since it was last saved appear with their defaults. */
    public static Settings update(Path file) throws IOException {
        // ConfigLib refuses a file without a single key, where Bukkit read it as all defaults.
        if (Files.isRegularFile(file) && hasNoKeys(file)) Files.delete(file);
        return STORE.update(file);
    }

    public static Settings parse(String yaml) {
        return STORE.read(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    public static Settings defaults() {
        return new Settings();
    }

    private static boolean hasNoKeys(Path file) throws IOException {
        return Files.readAllLines(file).stream().map(String::strip).allMatch(line -> line.isEmpty() || line.startsWith("#"));
    }

    public boolean isBlacklisted(String worldName) {
        return worlds.lowerCased.contains(worldName.toLowerCase(Locale.ROOT));
    }

    @Configuration
    @Getter
    public static final class Features {
        @Comment({"Turning chestlinks or autocraft off keeps every group and its items, ready for when it is turned back on. Until then their",
                "linked blocks can't be opened, linked or broken, their commands and menus refuse, hoppers, droppers, crafters and golems",
                "don't move items through ChestLinks, and AutoCrafters stop crafting."}) private boolean chestlinks = true;
        private boolean autocraft = true;
        private boolean hopperFilters = true;
        @Comment("Copper golems take items from linked copper chests and deliver into linked chests (only while chestlinks is on).") private boolean copperGolems = true;
    }

    @Configuration
    @Getter
    public static final class Linking {
        @Comment("Use up one name tag each time a block is linked by right-clicking it with a named name tag (never in creative).") private boolean consumeNameTags = true;
        @Comment("Use up the sign when a block is linked with a [ChestLink] or [AutoCraft] sign. When false, the sign goes back to the player.") private boolean consumeSigns = false;
    }

    @Configuration
    @Getter
    public static final class Display {
        private boolean enabled = true;
        private boolean label = true;
        @Comment("Multiplier on the client's entity view distance (1.0 = 64 blocks at default settings).") private float viewRange = 0.5f;

        @PostProcess
        private void clamp() {
            viewRange = Math.clamp(viewRange, 0.05f, 16.0f);
        }
    }

    @Configuration
    @Getter
    public static final class ChestLink {
        @Comment("Play the lid animation on every node of a group, not just the one that was opened.") private boolean animateAllNodes = true;
        private Display display = new Display();
    }

    @Configuration
    @Getter
    public static final class AutoCraft {
        private Display display = new Display();
        @Comment("Ticks between crafting attempts (20 = once a second).") private int tickInterval = 20;

        @PostProcess
        private void clamp() {
            tickInterval = Math.clamp(tickInterval, 1, 1200);
        }
    }

    @Configuration
    @Getter
    public static final class Filters {
        @Comment("Show each hopper's filter entries as small items on its sides (look at them to see allow/deny details).") private boolean displays = true;
    }

    /** {@code -1} means unlimited. */
    @Configuration
    @Getter
    public static final class Limits {
        private int chestlinkDefault = -1;
        private int autocraftDefault = -1;

        @PostProcess
        private void clamp() {
            chestlinkDefault = Math.max(-1, chestlinkDefault);
            autocraftDefault = Math.max(-1, autocraftDefault);
        }
    }

    @Configuration
    @Getter
    public static final class Worlds {
        @Comment("Worlds where ChestLinks and AutoCrafters cannot be created, linked or opened.") private List<String> blacklist = List.of();
        /** Kept apart from {@link #blacklist} so the file keeps the names as the admin wrote them. */
        @Ignore
        @Getter(AccessLevel.NONE) private Set<String> lowerCased = Set.of();

        @PostProcess
        private void normalise() {
            lowerCased = blacklist.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        }
    }

    @Configuration
    @Getter
    public static final class Storage {
        private int flushIntervalSeconds = 30;

        @PostProcess
        private void clamp() {
            flushIntervalSeconds = Math.clamp(flushIntervalSeconds, 1, 3600);
        }
    }

    @Configuration
    @Getter
    public static final class Toggle {
        private boolean enabled = true;
    }
}
