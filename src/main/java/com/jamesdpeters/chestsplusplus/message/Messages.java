package com.jamesdpeters.chestsplusplus.message;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * The message catalogue: MiniMessage templates keyed by {@link Message}. English defaults ship in the jar; a
 * {@code messages.yml} in the data folder overrides individual keys. Localisation can later plug in through
 * Adventure's {@code MiniMessageTranslationStore} without changing call sites (plan §5.11).
 */
public final class Messages {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final Map<Message, String> templates;
    private final TagResolver prefix;

    private Messages(Map<Message, String> templates) {
        this.templates = templates;
        this.prefix = Placeholder.parsed("prefix", templates.get(Message.PREFIX));
    }

    /** Loads the bundled defaults, then applies overrides from {@code override} if it exists. */
    public static Messages load(InputStream defaults, @Nullable File override) throws IOException {
        YamlConfiguration bundled = new YamlConfiguration();
        try (var reader = new InputStreamReader(defaults, StandardCharsets.UTF_8)) {
            bundled.load(reader);
        } catch (InvalidConfigurationException e) {
            throw new IOException("Bundled messages.yml is invalid", e);
        }
        YamlConfiguration user = new YamlConfiguration();
        if (override != null && override.isFile()) {
            try {
                user.load(override);
            } catch (InvalidConfigurationException e) {
                throw new IOException(override + " is invalid: " + e.getMessage(), e);
            }
        }
        Map<Message, String> templates = new EnumMap<>(Message.class);
        for (Message message : Message.values()) {
            String template = user.getString(message.key(), bundled.getString(message.key()));
            if (template == null) throw new IOException("Missing bundled message " + message.key());
            templates.put(message, template);
        }
        return new Messages(templates);
    }

    public Component get(Message message, TagResolver... placeholders) {
        return MINI_MESSAGE.deserialize(templates.get(message), TagResolver.resolver(prefix, TagResolver.resolver(placeholders)));
    }

    /** The message split into lines on {@code <newline>} (item lore can't contain line breaks), non-italic. */
    public java.util.List<Component> lines(Message message, TagResolver... placeholders) {
        TagResolver resolver = TagResolver.resolver(prefix, TagResolver.resolver(placeholders));
        java.util.List<Component> lines = new java.util.ArrayList<>();
        for (String line : templates.get(message).split("<newline>|<br>")) {
            lines.add(MINI_MESSAGE.deserialize(line, resolver).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        return lines;
    }

    public String plain(Message message, TagResolver... placeholders) {
        return PlainTextComponentSerializer.plainText().serialize(get(message, placeholders));
    }

    public void send(Audience audience, Message message, TagResolver... placeholders) {
        audience.sendMessage(get(message, placeholders));
    }

    /** An unparsed (escaped) text placeholder, e.g. for group and player names. */
    public static TagResolver text(String key, String value) {
        return Placeholder.unparsed(key, value);
    }

    public static TagResolver component(String key, Component value) {
        return Placeholder.component(key, value);
    }
}
