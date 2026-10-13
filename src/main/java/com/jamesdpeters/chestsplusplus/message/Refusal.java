package com.jamesdpeters.chestsplusplus.message;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Why an action can't go ahead: the message to send and every placeholder it needs. */
public record Refusal(Message message, TagResolver... placeholders) {}
