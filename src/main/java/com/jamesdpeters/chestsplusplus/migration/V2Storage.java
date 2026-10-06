package com.jamesdpeters.chestsplusplus.migration;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Reads a v2 {@code storage.yml} without v2's classes. The file is parsed as plain YAML; v2's own types ({@code ChestLinkStorage},
 * {@code C++Recipe}, ...) and {@code Location} stay maps, because {@code Location.deserialize} throws for worlds that aren't loaded. Every
 * other serialised object (items, item meta) goes through Bukkit, which upgrades items saved by older Minecraft versions.
 */
public final class V2Storage {

    private static final String TYPE_KEY = ConfigurationSerialization.SERIALIZED_TYPE_KEY;
    private static final Set<String> KEPT_AS_MAPS = Set.of("ConfigStorage", "ChestLinkStorage", "AutoCraftingStorage", "LocationInfo", "C++Recipe",
            "PlayerPartyStorage", "PlayerParty", "Location", "org.bukkit.Location", "Material");

    private final List<String> problems = new ArrayList<>();

    private V2Storage() {}

    public static V2Data read(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static V2Data parse(String yaml) {
        return new V2Storage().data(recipeAlias(yaml));
    }

    /** v2 renames its recipe type on every start ({@code Config.configConverter}), so a file it last saved may still use the old name. */
    private static String recipeAlias(String yaml) {
        return yaml.replaceAll("==: Recipe\\b", "==: C++Recipe");
    }

    private V2Data data(String yaml) {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(Integer.MAX_VALUE);
        options.setMaxAliasesForCollections(Integer.MAX_VALUE);
        Object root = new Yaml(new SafeConstructor(options)).load(yaml);
        Map<?, ?> store = root instanceof Map<?, ?> map && map.get("chests++") instanceof Map<?, ?> inner ? inner : Map.of();
        List<V2Data.Group> groups = new ArrayList<>();
        readGroups(store.get("chests"), GroupType.CHESTLINK, groups);
        readGroups(store.get("autocraftingtables"), GroupType.AUTOCRAFT, groups);
        return new V2Data(groups, parties(store.get("parties")), List.copyOf(problems));
    }

    private void readGroups(@Nullable Object byOwner, GroupType type, List<V2Data.Group> out) {
        if (!(byOwner instanceof Map<?, ?> owners)) return;
        owners.forEach((owner, byName) -> {
            if (!(byName instanceof Map<?, ?> named)) return;
            named.forEach((name, storage) -> {
                try {
                    if (storage instanceof Map<?, ?> map) out.add(group(type, String.valueOf(name), map));
                } catch (RuntimeException e) {
                    problems.add(type.displayName() + " " + owner + ":" + name + " could not be read (" + e.getMessage() + ")");
                }
            });
        });
    }

    private V2Data.Group group(GroupType type, String name, Map<?, ?> raw) {
        Map<?, ?> map = (Map<?, ?>) walk(raw);
        UUID owner = UUID.fromString(String.valueOf(map.get("playerUUID")));
        boolean isPublic = Boolean.TRUE.equals(map.get("isPublic"));
        return new V2Data.Group(type, owner, name, isPublic, uuids(map.get("members")), sortMode(map.get("sortMethod")),
                type == GroupType.CHESTLINK ? items(map.get("inventory")) : null, type == GroupType.AUTOCRAFT ? recipe(map.get("recipe")) : null,
                locations(map));
    }

    private static SortMode sortMode(@Nullable Object value) {
        if (value == null) return SortMode.OFF;
        try {
            return SortMode.valueOf(value.toString().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SortMode.OFF;
        }
    }

    /** v2 kept either {@code locationInfo: [LocationInfo{Location}]} or, in older files, {@code locations: [Location]}. */
    private static List<V2Data.Location> locations(Map<?, ?> map) {
        List<V2Data.Location> out = new ArrayList<>();
        Object list = map.containsKey("locationInfo") ? map.get("locationInfo") : map.get("locations");
        if (!(list instanceof List<?> entries)) return out;
        for (Object entry : entries) {
            Object location = entry instanceof Map<?, ?> info && info.containsKey("Location") ? info.get("Location") : entry;
            V2Data.Location parsed = location(location);
            if (parsed != null && !out.contains(parsed)) out.add(parsed);
        }
        return out;
    }

    private static V2Data.@Nullable Location location(@Nullable Object value) {
        if (!(value instanceof Map<?, ?> map) || !(map.get("world") instanceof String world)) return null;
        if (!(map.get("x") instanceof Number x) || !(map.get("y") instanceof Number y) || !(map.get("z") instanceof Number z)) return null;
        return new V2Data.Location(world, (int) Math.floor(x.doubleValue()), (int) Math.floor(y.doubleValue()), (int) Math.floor(z.doubleValue()));
    }

    private static V2Data.@Nullable Recipe recipe(@Nullable Object value) {
        if (!(value instanceof Map<?, ?> map) || !(map.get("namespace") instanceof String namespace) || !(map.get("key") instanceof String key)) {
            return null;
        }
        NamespacedKey parsed = NamespacedKey.fromString(namespace + ":" + key);
        return parsed == null ? null : new V2Data.Recipe(parsed, map.containsKey("items") ? items(map.get("items")) : null);
    }

    private static @Nullable ItemStack @Nullable [] items(@Nullable Object value) {
        if (!(value instanceof List<?> list)) return null;
        @Nullable ItemStack[] items = new ItemStack[list.size()];
        for (int i = 0; i < items.length; i++) items[i] = list.get(i) instanceof ItemStack item && !item.isEmpty() ? item : null;
        return items;
    }

    private static List<UUID> uuids(@Nullable Object value) {
        List<UUID> out = new ArrayList<>();
        if (!(value instanceof List<?> list)) return out;
        for (Object entry : list) {
            try {
                UUID uuid = UUID.fromString(String.valueOf(entry));
                if (!out.contains(uuid)) out.add(uuid);
            } catch (IllegalArgumentException ignored) {
                // not a UUID: v2 never wrote one, so skip it
            }
        }
        return out;
    }

    private List<V2Data.Party> parties(@Nullable Object value) {
        List<V2Data.Party> out = new ArrayList<>();
        if (!(value instanceof Map<?, ?> byOwner)) return out;
        for (Object storage : byOwner.values()) {
            if (!(storage instanceof Map<?, ?> map) || !(map.get("ownedParties") instanceof Map<?, ?> owned)) continue;
            for (Object party : owned.values()) {
                if (!(party instanceof Map<?, ?> partyMap)) continue;
                try {
                    UUID owner = UUID.fromString(String.valueOf(partyMap.get("owner")));
                    out.add(new V2Data.Party(owner, String.valueOf(partyMap.get("partyName")), uuids(partyMap.get("members"))));
                } catch (IllegalArgumentException e) {
                    problems.add("A party could not be read (" + e.getMessage() + ")");
                }
            }
        }
        return out;
    }

    /** Deserialises Bukkit objects from the inside out, as Bukkit's own YAML loader does, leaving v2's types as maps. */
    private @Nullable Object walk(@Nullable Object value) {
        if (value instanceof List<?> list) {
            List<@Nullable Object> out = new ArrayList<>(list.size());
            for (Object entry : list) out.add(walk(entry));
            return out;
        }
        if (!(value instanceof Map<?, ?> map)) return value;
        Map<String, @Nullable Object> out = new LinkedHashMap<>();
        map.forEach((key, entry) -> out.put(String.valueOf(key), walk(entry)));
        Object type = out.get(TYPE_KEY);
        if (type == null || KEPT_AS_MAPS.contains(type.toString())) return out;
        try {
            return ConfigurationSerialization.deserializeObject(out);
        } catch (RuntimeException e) {
            problems.add("A saved " + type + " could not be read and was skipped (" + e.getMessage() + ")");
            return null;
        }
    }
}
