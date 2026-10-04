package com.jamesdpeters.chestsplusplus.command;

import com.jamesdpeters.chestsplusplus.access.AccessService;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * A group reference: {@code name} for your own group, or {@code owner:name} for one you can access.
 * Parsed as raw text up to the next space (resolution and access checks happen when the command runs); suggestions
 * list the groups the sender can access.
 */
public final class GroupArgument implements CustomArgumentType<String, String> {

    private final GroupType type;
    private final Supplier<@Nullable Services> services;

    public GroupArgument(GroupType type, Supplier<@Nullable Services> services) {
        this.type = type;
        this.services = services;
    }

    @Override
    public String parse(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') reader.skip();
        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    @Override
    public List<String> getExamples() {
        return List.of("ores", "Steve:ores");
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        Services current = services.get();
        if (current == null || !(context.getSource() instanceof CommandSourceStack source) || !(source.getExecutor() instanceof Player player)) {
            return builder.buildFuture();
        }
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        LinkService links = current.get(LinkService.class);
        for (StorageGroup group : links.accessibleGroups(player.getUniqueId(), AccessService.hasBypass(player), type)) {
            String reference = LinkService.reference(player.getUniqueId(), group);
            if (reference.toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(reference);
        }
        return builder.buildFuture();
    }
}
