package com.jamesdpeters.chestsplusplus.core;

import com.jamesdpeters.chestsplusplus.access.AccessService;
import com.jamesdpeters.chestsplusplus.access.TrustService;
import com.jamesdpeters.chestsplusplus.config.Settings;
import com.jamesdpeters.chestsplusplus.core.scheduler.MainThread;
import com.jamesdpeters.chestsplusplus.core.scheduler.Tickers;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.message.Refusal;
import com.jamesdpeters.chestsplusplus.model.GroupRegistry;
import com.jamesdpeters.chestsplusplus.model.Node;
import com.jamesdpeters.chestsplusplus.model.NodeIndex;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import lombok.Getter;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/** The shared kernel every feature builds on, created on enable and discarded on disable. Settings and messages are swapped on reload. */
public final class Services {

    @Getter private final JavaPlugin plugin;
    @Getter private final GroupRegistry groups = new GroupRegistry();
    @Getter private final NodeIndex nodes = new NodeIndex();
    @Getter private final TrustService trust = new TrustService();
    @Getter private final AccessService access = new AccessService(trust, groups);
    @Getter private final Tickers tickers;
    @Getter private final MainThread mainThread;
    @Getter private final Shutdown shutdown = new Shutdown();
    @Getter private volatile Settings settings;
    @Getter private volatile Messages messages;

    public Services(JavaPlugin plugin, Settings settings, Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.tickers = new Tickers(plugin);
        this.mainThread = new MainThread(plugin);
    }

    public void send(Audience audience, Message message, TagResolver... placeholders) {
        messages.send(audience, message, placeholders);
    }

    public void send(Audience audience, Refusal refusal) {
        send(audience, refusal.message(), refusal.placeholders());
    }

    public void reconfigure(Settings settings, Messages messages) {
        this.settings = settings;
        this.messages = messages;
    }

    /** The group {@code block} is linked to, if any. */
    public @Nullable StorageGroup groupAt(Block block) {
        Node node = nodes.at(block);
        return node == null ? null : groups.byId(node.groupId());
    }
}
