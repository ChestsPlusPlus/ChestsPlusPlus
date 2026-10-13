package com.jamesdpeters.chestsplusplus.ui;

import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.GroupActions;
import com.jamesdpeters.chestsplusplus.link.Handlers;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.ui.menu.MenuListener;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

/** What every screen builds dialogs from: single-use buttons and links that run on the main thread, group icons and messages. */
@RequiredArgsConstructor
final class Dialogs {

    static final int BUTTON_WIDTH = 200;
    static final int TOOLBAR_BUTTON_WIDTH = 130;
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(10))
            .build();
    /** Defined on the member and trust dialogs' input and read back from their {@link DialogResponseView}. */
    private static final String PLAYER_INPUT = "player";

    private final Services services;
    private final Handlers handlers;
    private final GroupActions actions;
    private final MenuListener menus;
    /** Set when a dialog is shown, so a click that shows nothing new can close the dialog it came from. Main thread only. */
    private boolean dialogShown;

    ActionButton button(Component label, BiConsumer<DialogResponseView, Player> onClick) {
        return button(label, BUTTON_WIDTH, onClick);
    }

    /** A single-use, expiring dialog button whose callback runs on the main thread. */
    ActionButton button(Component label, int width, BiConsumer<DialogResponseView, Player> onClick) {
        return ActionButton.builder(label)
                .width(width)
                .action(DialogAction.customClick((view, audience) -> onMainThread(audience, p -> onClick.accept(view, p)), CALLBACK_OPTIONS))
                .build();
    }

    ActionButton toolbarButton(Component label, BiConsumer<DialogResponseView, Player> onClick) {
        return button(label, TOOLBAR_BUTTON_WIDTH, onClick);
    }

    /** Clickable text: dialog bodies run click events, so a link in a group row acts like a button that re-fetches its group. */
    Component link(Message label, StorageGroup group, BiConsumer<Player, StorageGroup> action) {
        long id = group.id();
        ClickEvent<?> click = ClickEvent.callback(audience -> onMainThread(audience, p -> withGroup(p, id, g -> action.accept(p, g))),
                CALLBACK_OPTIONS);
        return text(label).clickEvent(click);
    }

    Dialog multiAction(DialogBase base, List<ActionButton> buttons, int columns) {
        // An action-less button would leave the dialog open, since dialogs keep themselves open after clicks.
        ActionButton close = button(text(Message.MENU_CLOSE), (view, p) -> p.closeDialog());
        return Dialog.create(factory -> factory.empty()
                .base(base)
                .type(DialogType.multiAction(buttons).columns(columns).exitAction(close).build()));
    }

    /** A player name box above {@code buttons}, as the member and trust dialogs use. */
    Dialog playerListDialog(Component title, List<ActionButton> buttons) {
        DialogBase base = baseBuilder(title)
                .inputs(List.of(DialogInput.text(PLAYER_INPUT, text(Message.MENU_MEMBERS_PLAYER)).maxLength(16).build()))
                .build();
        return multiAction(base, buttons, 1);
    }

    static String playerInput(DialogResponseView view) {
        String value = view.getText(PLAYER_INPUT);
        return value == null ? "" : value.trim();
    }

    /**
     * Every dialog keeps itself open after a click ({@link DialogAfterAction#NONE}) so the next one replaces it without flickering back to the
     * world. Vanilla rejects that for dialogs that pause the game.
     */
    static DialogBase.Builder baseBuilder(Component title) {
        return DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogAfterAction.NONE);
    }

    void show(Player player, Dialog dialog) {
        dialogShown = true;
        player.showDialog(dialog);
    }

    /**
     * Runs a click for an online player on the main thread. Paper doesn't document the callback thread, so this re-dispatches if needed.
     * A click that neither shows a dialog nor opens an inventory (a refusal, or an async player lookup) closes the dialog: it stays open
     * after clicks, and its buttons are single-use.
     */
    private void onMainThread(Audience audience, Consumer<Player> action) {
        if (!(audience instanceof Player player)) return;
        Runnable run = () -> {
            if (!player.isOnline() || !services.plugin().isEnabled()) return;
            dialogShown = false;
            action.accept(player);
            if (!dialogShown && player.getOpenInventory().getType() == InventoryType.CRAFTING) player.closeDialog();
        };
        services.mainThread().run(run);
    }

    /** A click handler that re-fetches the group by id first (it may have been removed or renamed since the dialog was shown). */
    BiConsumer<DialogResponseView, Player> onGroup(StorageGroup group, BiConsumer<Player, StorageGroup> action) {
        long id = group.id();
        return (view, player) -> withGroup(player, id, g -> action.accept(player, g));
    }

    void withGroup(Player player, long id, Consumer<StorageGroup> action) {
        StorageGroup group = services.groups().byId(id);
        if (group == null) services.send(player, Message.ERROR_UNKNOWN_GROUP, Messages.text("group", "#" + id));
        else action.accept(group);
    }

    /** Opens the group itself (its inventory or recipe); when the player closes it, {@code back} reopens the menu they came from. */
    void openRemote(Player player, StorageGroup group, Runnable back) {
        if (actions.openRemote(player, group)) menus.returnTo(player, back);
    }

    Component text(Message message, TagResolver... placeholders) {
        return services.messages().get(message, placeholders);
    }

    String plain(Message message) {
        return services.messages().plain(message);
    }

    ItemStack icon(StorageGroup group) {
        return handlers.of(group).icon(group);
    }

    /** The group's icon named after the group, with {@code lore} as its tooltip. */
    ItemStack namedIcon(StorageGroup group, Message lore, TagResolver... placeholders) {
        ItemStack icon = icon(group).clone();
        icon.setData(DataComponentTypes.ITEM_NAME, Component.text(group.name()));
        icon.setData(DataComponentTypes.LORE, ItemLore.lore(services.messages().lines(lore, placeholders)));
        return icon;
    }

    String summary(StorageGroup group) {
        return handlers.of(group).summary(group);
    }
}
