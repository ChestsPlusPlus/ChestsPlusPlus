package com.jamesdpeters.chestsplusplus.command;

import com.jamesdpeters.chestsplusplus.access.AccessService;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * A group reference: {@code name} for your own group, or {@code owner:name} for one you can access. Quote it when the name
 * has a space ({@code "Iron Ore"}, {@code "Steve:Iron Ore"}); unquoted it runs to the next space, so {@code owner:name} needs
 * no quotes. Resolution and access checks happen when the command runs; suggestions list the groups the sender can access.
 */
@RequiredArgsConstructor
public final class GroupArgument implements CustomArgumentType<String, String> {

    private final GroupType type;
    private final Supplier<@Nullable Services> services;

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && StringReader.isQuotedStringStart(reader.peek())) return reader.readQuotedString();
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') reader.skip();
        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.string();
    }

    @Override
    public List<String> getExamples() {
        return List.of("ores", "Steve:ores", "\"Iron Ore\"");
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        Services current = services.get();
        if (current == null || !(context.getSource() instanceof CommandSourceStack source) || !(source.getExecutor() instanceof Player player)) {
            return builder.buildFuture();
        }
        String typed = builder.getRemaining().replace("\"", "").toLowerCase(Locale.ROOT);
        for (StorageGroup group : current.access().accessibleGroups(player.getUniqueId(), AccessService.hasBypass(player), type)) {
            String reference = group.referenceFor(player.getUniqueId());
            if (reference.toLowerCase(Locale.ROOT).startsWith(typed)) builder.suggest(StringArgumentType.escapeIfRequired(reference));
        }
        return builder.buildFuture();
    }
}
