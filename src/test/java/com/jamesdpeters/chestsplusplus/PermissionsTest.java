package com.jamesdpeters.chestsplusplus;

import static org.assertj.core.api.Assertions.assertThat;

import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Checks {@link Permissions} against the nodes {@code build.gradle.kts} declares in the generated paper-plugin.yml. Paper makes an
 * undeclared node op-only, and the MockBukkit tests grant nodes directly, so nothing else would notice one missing.
 */
@Tag(Tags.UNIT)
class PermissionsTest {

    private static ConfigurationSection declared;

    @BeforeAll
    static void loadPluginYaml() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        // Node names contain dots, which Bukkit would otherwise read as nesting.
        yaml.options().pathSeparator('/');
        try (InputStream in = Objects.requireNonNull(PermissionsTest.class.getResourceAsStream("/paper-plugin.yml"))) {
            yaml.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertThat(yaml.getString("main")).isEqualTo(ChestsPlusPlus.class.getName());
        declared = Objects.requireNonNull(yaml.getConfigurationSection("permissions"));
    }

    @Test
    void everyNodeTheCodeChecksIsDeclared() throws Exception {
        assertThat(declared.getKeys(false)).containsAll(used());
    }

    @Test
    void everyDeclaredNodeIsUsed() throws Exception {
        Set<String> declaredNodes = declared.getKeys(false).stream()
                .filter(node -> Arrays.stream(GroupType.values()).noneMatch(type -> node.startsWith(Permissions.limitPrefix(type))))
                .collect(Collectors.toSet());

        assertThat(used()).containsAll(declaredNodes);
    }

    @Test
    void eachActionHasTheSameDefaultForEveryType() {
        for (GroupPermission action : GroupPermission.values()) {
            String chestlink = declared.getString(Permissions.of(GroupType.CHESTLINK, action) + "/default");
            String autocraft = declared.getString(Permissions.of(GroupType.AUTOCRAFT, action) + "/default");

            assertThat(autocraft).as(action.name()).isEqualTo(chestlink);
        }
    }

    private static Set<String> used() throws IllegalAccessException {
        Set<String> nodes = new HashSet<>();
        for (Field field : Permissions.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()) && field.getType() == String.class)
                nodes.add((String) field.get(null));
        }
        for (GroupType type : GroupType.values()) {
            for (GroupPermission action : GroupPermission.values()) nodes.add(Permissions.of(type, action));
        }
        return nodes;
    }
}
