package com.jamesdpeters.chestsplusplus.core;

import com.jamesdpeters.chestsplusplus.access.AccessService;
import com.jamesdpeters.chestsplusplus.access.TrustService;
import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.core.scheduler.Tickers;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.NodeIndex;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.persistence.PersistenceService;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/** Service container, created on enable and discarded on disable. Settings and messages are swapped on reload. */
public final class Services {

    private final JavaPlugin plugin;
    private final GroupRegistry groups = new GroupRegistry();
    private final NodeIndex nodes = new NodeIndex();
    private final TrustService trust = new TrustService();
    private final AccessService access = new AccessService(trust, groups);
    private final Tickers tickers;
    private volatile Settings settings;
    private volatile Messages messages;
    private @Nullable PersistenceService persistence;
    private final Map<Class<?>, Object> components = new HashMap<>();

    public Services(JavaPlugin plugin, Settings settings, Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.tickers = new Tickers(plugin);
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public void send(Audience audience, Message message, TagResolver... placeholders) {
        messages.send(audience, message, placeholders);
    }

    public void reconfigure(Settings settings, Messages messages) {
        this.settings = settings;
        this.messages = messages;
    }

    public GroupRegistry groups() {
        return groups;
    }

    public NodeIndex nodes() {
        return nodes;
    }

    public TrustService trust() {
        return trust;
    }

    public AccessService access() {
        return access;
    }

    public Tickers tickers() {
        return tickers;
    }

    /** The group {@code block} is linked to, if any. */
    public @Nullable StorageGroup groupAt(Block block) {
        Node node = nodes.at(block);
        return node == null ? null : groups.byId(node.groupId());
    }

    public PersistenceService persistence() {
        if (persistence == null) throw new IllegalStateException("Persistence not started");
        return persistence;
    }

    public void persistence(PersistenceService persistence) {
        this.persistence = persistence;
    }

    public <T> T add(Class<T> type, T component) {
        components.put(type, component);
        return component;
    }

    public <T> T get(Class<T> type) {
        Object component = components.get(type);
        if (component == null) throw new IllegalStateException(type.getSimpleName() + " is not registered");
        return type.cast(component);
    }
}
