package com.jamesdpeters.minecraft.chests;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * This class serves as a basis for creating a simple and easy to use Config using the Spigot/Bukkit api.
 * Before any values are accessed the config for a @{@link Plugin} must be loaded using the {@link #load(Plugin)} method.
 * To add values to the config they can either be add in this config in the same way the default values are.
 * Or added in a separate class, any initialisation of a @{@link Value} or @{@link ListValue} automatically gets added
 * to the config.
 *
 * @author James Peters - JamesPeters98
 */
public class PluginConfig {

    private static FileConfiguration configuration;
    public static HashMap<String, List<AbstractValue<?>>> values = new HashMap<>();

    /* ***************
     * Config Values
     * ***************/
    public final static Value<Boolean> IS_UPDATE_CHECKER_ENABLED = new Value<>("update-checker", Boolean.class, true);
    public final static Value<Integer> UPDATE_CHECKER_PERIOD = new Value<>("update-checker-period", Integer.class, 60 * 60);
    public final static Value<Boolean> CHESTLINKS_ENABLED = new Value<>("chestlinks-enabled", Boolean.class, true);
    public final static Value<Boolean> AUTOCRAFTERS_ENABLED = new Value<>("autocrafters-enabled", Boolean.class, true);
    public final static Value<Boolean> HOPPER_FILTERS_ENABLED = new Value<>("hopper-filters-enabled", Boolean.class, true);
    public final static Value<Boolean> SHOULD_LIMIT_CHESTS = new Value<>("limit-chests", Boolean.class, false);
    public final static Value<Integer> LIMIT_CHESTS_AMOUNT = new Value<>("limit-chestlinks-amount", Integer.class, 0);
    public final static Value<Boolean> SHOULD_ANIMATE_ALL_CHESTS = new Value<>("should-animate-all-chests", Boolean.class, true);
    public final static Value<Boolean> DISPLAY_CHESTLINK_ARMOUR_STAND = new Value<>("display_chestlink_armour_stand", Boolean.class, true);
    public final static Value<Boolean> DISPLAY_AUTOCRAFT_ARMOUR_STAND = new Value<>("display_autocraft_armour_stands", Boolean.class, true);
    public final static Value<Boolean> INVISIBLE_FILTER_ITEM_FRAMES = new Value<>("set-filter-itemframe-invisible", Boolean.class, false);
    public final static ListValue<String> WORLD_BLACKLIST = new ListValue<>("world-blacklist", String.class, Collections.singletonList(""));
    public final static Value<String> LANG_FILE = new Value<>("language-file", String.class, "default");

    /**
     * Loads this @{@link Plugin}'s Config and adds default values if they don't exist.
     *
     * @param plugin - the plugin of the @{@link org.bukkit.configuration.Configuration} to be loaded.
     */
    public static void load(Plugin plugin) {
        //Read in config
        configuration = plugin.getConfig();

        //Add default values
        values.forEach((configSection, values) -> {
            if (configSection != null) {
                ConfigurationSection section = Optional.ofNullable(configuration.getConfigurationSection(configSection)).orElse(configuration.createSection(configSection));
                values.forEach(value -> section.addDefault(value.getPath(), value.getDefaultValue()));
            } else {
                values.forEach((value) -> configuration.addDefault(value.getPath(), value.getDefaultValue()));
            }
        });
        configuration.options().copyDefaults(true);

        //Save config and default values
        plugin.saveConfig();

        //Read config in and set values
        configuration = plugin.getConfig();
        values.forEach((configSection, values) -> {
            if (configSection != null) {
                ConfigurationSection section = Optional.ofNullable(configuration.getConfigurationSection(configSection)).orElse(configuration.createSection(configSection));
                values.forEach(value -> value.setValue(section.get(value.getPath())));
            } else {
                values.forEach(value -> value.setValue(configuration.get(value.getPath())));
            }
        });
    }

    private abstract static class AbstractValue<T> {
        protected final String path, configurationSection;
        protected final T defaultValue;
        protected T value;

        AbstractValue(String configurationSection, String path, T defaultValue) {
            this.path = path;
            this.value = defaultValue;
            this.defaultValue = defaultValue;
            this.configurationSection = configurationSection;

            List<AbstractValue<?>> valueList = values.computeIfAbsent(configurationSection, k -> new ArrayList<>());
            if (valueList.stream().anyMatch(value -> value.getPath().equals(path))) {
                throw new IllegalArgumentException("Two values with the same path have been added to the Config: '" + path + "'");
            }
            valueList.add(this);
        }


        public T get() {
            return value != null ? value : defaultValue;
        }

        public void set(T value) {
            this.value = value;
        }

        public T getDefaultValue() {
            return defaultValue;
        }

        public String getPath() {
            return path;
        }

        public abstract void setValue(Object object);
    }

    /**
     * A class that represents a singular value in a @{@link org.bukkit.configuration.Configuration}
     *
     * @param <T> the type of the variable to be stored/loaded.
     */
    public static class Value<T> extends AbstractValue<T> {
        protected final Class<T> type;

        /**
         * @param configSection the @{@link ConfigurationSection} this value belongs to.
         * @param path          the key that represents this value.
         * @param type          the @{@link Class} that represents this value
         * @param defaultValue  the default value to be inserted into the @{@link org.bukkit.configuration.Configuration}
         */
        public Value(String configSection, String path, Class<T> type, T defaultValue) {
            super(configSection, path, defaultValue);
            this.type = type;
        }

        /**
         * @param path         the key that represents this value.
         * @param type         the @{@link Class} that represents this value
         * @param defaultValue the default value to be inserted into the @{@link org.bukkit.configuration.Configuration}
         */
        public Value(String path, Class<T> type, T defaultValue) {
            this(null, path, type, defaultValue);
        }

        public void setValue(Object value) {
            this.value = type.isInstance(value) ? type.cast(value) : defaultValue;
        }

    }

    /**
     * A class that represents a list of values in a @{@link org.bukkit.configuration.Configuration}
     *
     * @param <T> the type of the variable to be stored/loaded.
     */
    public static class ListValue<T> extends AbstractValue<List<T>> {

        protected final Class<T> type;

        /**
         * @param configSection the @{@link ConfigurationSection} this value belongs to.
         * @param path          the key that represents this value.
         * @param type          the @{@link Class} that represents a value in the list
         * @param defaultValue  the default value to be inserted into the @{@link org.bukkit.configuration.Configuration}
         */
        public ListValue(String configSection, String path, Class<T> type, List<T> defaultValue) {
            super(configSection, path, defaultValue);
            this.type = type;
        }

        /**
         * @param path         the key that represents this value.
         * @param type         the @{@link Class} that represents a value in the list
         * @param defaultValue the default value to be inserted into the @{@link org.bukkit.configuration.Configuration}
         */
        public ListValue(String path, Class<T> type, List<T> defaultValue) {
            this(null, path, type, defaultValue);
        }

        public void setValue(Object value) {
            List<?> list = (value instanceof List) ? (List<?>) value : defaultValue;
            this.value = new ArrayList<T>();

            for (Object o : list) {
                if (type.isInstance(o)) {
                    this.value.add(type.cast(o));
                }
            }
        }
    }
}
