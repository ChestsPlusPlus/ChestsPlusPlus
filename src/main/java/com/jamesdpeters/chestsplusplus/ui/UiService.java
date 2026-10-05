package com.jamesdpeters.chestsplusplus.ui;

import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.access.AccessService;
import com.jamesdpeters.chestsplusplus.core.PlayerNames;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.GroupActions;
import com.jamesdpeters.chestsplusplus.link.GroupTypeHandler;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.ui.menu.MenuListener;
import com.jamesdpeters.chestsplusplus.ui.menu.PaginatedMenu;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Menus: the Dialog hub (a row per group with its icon, then search, paging and a toolbar), the group, members, trust and confirm dialogs, and the icon grid. Every
 * action goes through {@link GroupActions}, which re-checks permissions.
 */
@RequiredArgsConstructor
public final class UiService {

    /** Each group is a body row about 36px tall, so six fit on screen with the controls before the dialog starts scrolling. */
    static final int GROUPS_PER_PAGE = 6;
    private static final int BUTTON_WIDTH = 200;
    private static final int TOOLBAR_BUTTON_WIDTH = 130;
    /** Three toolbar columns plus the client's 2px gaps, so the header, group rows and search box line up with the toolbar. */
    private static final int HUB_WIDTH = 3 * TOOLBAR_BUTTON_WIDTH + 4;
    /**
     * Giving a group row's description a width fixes its box size, so rows line up. The client centres text in that box, so keeping it
     * narrow keeps the text beside its icon. Longer names wrap onto a third line.
     */
    private static final int GROUP_ROW_TEXT_WIDTH = 220;
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(10))
            .build();
    /** Dialog input keys: each is defined on a {@link DialogInput} and read back from the {@link DialogResponseView}. */
    private static final String SEARCH_INPUT = "search";
    private static final String NAME_INPUT = "name";
    private static final String PUBLIC_INPUT = "public";
    private static final String SORT_INPUT = "sort";
    private static final String PLAYER_INPUT = "player";

    private final Services services;
    private final LinkService links;
    private final GroupActions actions;
    private final MenuListener menus;
    /** Set when a dialog is shown, so a click that shows nothing new can close the dialog it came from. Main thread only. */
    private boolean dialogShown;

    /** Groups shown in the hub for {@code search} (case-insensitive substring of name or owner). */
    public List<StorageGroup> hubGroups(Player player, GroupType type, String search) {
        String needle = search.trim().toLowerCase(Locale.ROOT);
        return services.access().accessibleGroups(player.getUniqueId(), AccessService.hasBypass(player), type).stream()
                .filter(g -> needle.isEmpty() || contains(g.name(), needle) || contains(PlayerNames.of(g.owner()), needle))
                .toList();
    }

    private static boolean contains(String text, String lowerCaseNeedle) {
        return text.toLowerCase(Locale.ROOT).contains(lowerCaseNeedle);
    }

    public void openHub(Player player, GroupType type, String search, int page) {
        if (!player.hasPermission(Permissions.menu(type))) {
            services.send(player, Message.ERROR_NO_PERMISSION);
            return;
        }
        List<StorageGroup> groups = hubGroups(player, type, search);
        int pages = Math.max(1, (groups.size() + GROUPS_PER_PAGE - 1) / GROUPS_PER_PAGE);
        int current = Math.clamp(page, 0, pages - 1);
        int from = current * GROUPS_PER_PAGE;

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(hubHeader(groups.size(), current, pages), HUB_WIDTH));
        List<StorageGroup> shown = groups.subList(from, Math.min(groups.size(), from + GROUPS_PER_PAGE));
        shown.forEach(group -> body.add(hubGroupRow(group, search, current)));
        for (int i = shown.size(); i < GROUPS_PER_PAGE; i++) body.add(blankGroupRow());
        DialogInput searchInput = DialogInput.text(SEARCH_INPUT, text(Message.MENU_HUB_SEARCH))
                .initial(search)
                .maxLength(32)
                .width(HUB_WIDTH)
                .build();
        DialogBase base = baseBuilder(text(Message.MENU_HUB_TITLE, Messages.text("type", type.displayName())))
                .body(body)
                .inputs(List.of(searchInput))
                .build();
        show(player, multiAction(base, hubToolbar(player, type, current, pages), 3));
    }

    private Component hubHeader(int groups, int current, int pages) {
        if (groups == 0) return text(Message.MENU_HUB_EMPTY);
        return text(Message.MENU_HUB_HEADER, Messages.text("count", groups), Messages.text("page", current + 1), Messages.text("pages", pages));
    }

    /** The group's icon (hover for details) beside its name, owner and Open / Manage links. Closing the opened group returns to this page. */
    private DialogBody hubGroupRow(StorageGroup group, String search, int page) {
        GroupType type = group.type();
        BiConsumer<Player, StorageGroup> open = (p, g) -> openRemote(p, g, () -> openHub(p, type, search, page));
        Component description = text(Message.MENU_HUB_GROUP,
                Messages.group(group),
                Messages.text("items", summary(group)),
                Messages.component("owner_head", Component.object(ObjectContents.playerHead(group.owner()))),
                Messages.text("owner", PlayerNames.of(group.owner())),
                Messages.component("open", link(type.pick(Message.MENU_HUB_GROUP_OPEN, Message.MENU_HUB_GROUP_RECIPE), group, open)),
                Messages.component("manage", link(Message.MENU_HUB_GROUP_MANAGE, group, this::openGroup)));
        ItemStack icon = namedIcon(group, Message.MENU_HUB_GROUP_LORE,
                Messages.text("owner", PlayerNames.of(group.owner())),
                Messages.text("items", summary(group)),
                Messages.component("public", text(group.isPublic() ? Message.STATE_PUBLIC : Message.STATE_PRIVATE)),
                Messages.text("members", memberNames(group)));
        return DialogBody.item(icon)
                .description(DialogBody.plainMessage(description, GROUP_ROW_TEXT_WIDTH))
                .showDecorations(false)
                .build();
    }

    /**
     * Pads a short page so the search box and toolbar stay put. The client sizes both this and a group row as two lines of text plus 4px
     * padding (26px), and the 16px icon fits inside that.
     */
    private static DialogBody blankGroupRow() {
        return DialogBody.plainMessage(Component.text(" \n "), HUB_WIDTH);
    }

    /**
     * Paging around Search on the first row, then grid and trust. The client centres a short last row, so the layout holds with any
     * subset of buttons. Paging greys out rather than disappearing, so Search never moves.
     */
    private List<ActionButton> hubToolbar(Player player, GroupType type, int current, int pages) {
        List<ActionButton> buttons = new ArrayList<>();
        if (pages > 1) buttons.add(pageButton(Message.MENU_HUB_PREVIOUS, type, current, current - 1, current > 0));
        buttons.add(toolbarButton(text(Message.MENU_HUB_SEARCH_BUTTON), (view, p) -> openHub(p, type, search(view), 0)));
        if (pages > 1) buttons.add(pageButton(Message.MENU_HUB_NEXT, type, current, current + 1, current < pages - 1));
        buttons.add(toolbarButton(text(Message.MENU_HUB_GRID_BUTTON), (view, p) -> openGrid(p, type)));
        if (player.hasPermission(Permissions.TRUST)) buttons.add(toolbarButton(text(Message.MENU_HUB_TRUST_BUTTON), (view, p) -> openTrust(p, type)));
        return buttons;
    }

    /** A disabled paging button only re-shows the page: a button with no action would close the dialog instead. */
    private ActionButton pageButton(Message label, GroupType type, int current, int target, boolean enabled) {
        Component shown = enabled ? text(label) : Component.text(services.messages().plain(label), NamedTextColor.DARK_GRAY);
        int to = enabled ? target : current;
        return toolbarButton(shown, (view, p) -> openHub(p, type, search(view), to));
    }

    private ActionButton toolbarButton(Component label, BiConsumer<DialogResponseView, Player> onClick) {
        return button(label, TOOLBAR_BUTTON_WIDTH, onClick);
    }

    /** Clickable text: dialog bodies run click events, so a link in a group row acts like a button that re-fetches its group. */
    private Component link(Message label, StorageGroup group, BiConsumer<Player, StorageGroup> action) {
        long id = group.id();
        ClickEvent<?> click = ClickEvent.callback(audience -> onMainThread(audience, p -> withGroup(p, id, g -> action.accept(p, g))),
                CALLBACK_OPTIONS);
        return text(label).clickEvent(click);
    }

    public void openGroup(Player player, StorageGroup group) {
        if (!actions.canUse(player, group)) return;
        boolean manage = services.access().canManage(player.getUniqueId(), player, group);
        String description = PlayerNames.of(group.owner()) + " · " + summary(group) + " · " + services.nodes().count(group.id()) + " block(s)";
        DialogBase base = baseBuilder(text(Message.MENU_GROUP_TITLE, Messages.group(group)))
                .body(List.of(DialogBody.item(icon(group)).description(DialogBody.plainMessage(Component.text(description))).build()))
                .inputs(manage ? groupInputs(player, group) : List.of())
                .build();
        show(player, multiAction(base, groupButtons(player, group, manage), 2));
    }

    private List<DialogInput> groupInputs(Player player, StorageGroup group) {
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.text(NAME_INPUT, text(Message.MENU_GROUP_NEW_NAME)).initial(group.name()).maxLength(32).build());
        inputs.add(DialogInput.bool(PUBLIC_INPUT, text(Message.MENU_GROUP_PUBLIC)).initial(group.isPublic()).build());
        if (group instanceof ChestLinkGroup chest && player.hasPermission(Permissions.CHESTLINK_SORT)) {
            List<SingleOptionDialogInput.OptionEntry> modes = new ArrayList<>();
            for (SortMode mode : SortMode.values()) {
                Component label = Component.text(mode.name().toLowerCase(Locale.ROOT));
                modes.add(SingleOptionDialogInput.OptionEntry.create(mode.name(), label, mode == chest.sortMode()));
            }
            inputs.add(DialogInput.singleOption(SORT_INPUT, text(Message.MENU_GROUP_SORT_MODE), modes).build());
        }
        return inputs;
    }

    private List<ActionButton> groupButtons(Player player, StorageGroup group, boolean manage) {
        GroupType type = group.type();
        List<ActionButton> buttons = new ArrayList<>();
        BiConsumer<Player, StorageGroup> open = (p, g) -> openRemote(p, g, () -> withGroup(p, g.id(), latest -> openGroup(p, latest)));
        buttons.add(button(text(type.pick(Message.MENU_GROUP_OPEN, Message.MENU_GROUP_RECIPE)), onGroup(group, open)));
        if (manage) {
            buttons.add(button(text(Message.MENU_GROUP_SAVE), (view, p) -> withGroup(p, group.id(), g -> {
                save(p, g, view);
                openGroup(p, g);
            })));
            if (player.hasPermission(Permissions.members(type)))
                buttons.add(button(text(Message.MENU_GROUP_MEMBERS), onGroup(group, this::openMembers)));
            if (player.hasPermission(Permissions.remove(type)))
                buttons.add(button(text(Message.MENU_GROUP_REMOVE), onGroup(group, this::confirmRemove)));
        }
        buttons.add(button(text(Message.MENU_BACK), (view, p) -> openHub(p, type, "", 0)));
        return buttons;
    }

    /** Applies the group dialog's inputs: only what changed, each through {@link GroupActions}. */
    void save(Player player, StorageGroup group, DialogResponseView view) {
        String name = view.getText(NAME_INPUT);
        if (name != null && !name.isBlank() && !name.equals(group.name())) actions.rename(player, group, name.trim());
        Boolean isPublic = view.getBoolean(PUBLIC_INPUT);
        if (isPublic != null && isPublic != group.isPublic()) actions.setPublic(player, group, isPublic);
        SortMode mode = parseSortMode(view.getText(SORT_INPUT));
        if (mode != null && group instanceof ChestLinkGroup chest && mode != chest.sortMode()) actions.sort(player, chest, mode);
    }

    private static @Nullable SortMode parseSortMode(@Nullable String option) {
        if (option == null) return null;
        try {
            return SortMode.valueOf(option);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void confirmRemove(Player player, StorageGroup group) {
        DialogBase base = baseBuilder(text(Message.MENU_GROUP_REMOVE))
                .body(List.of(DialogBody.plainMessage(text(Message.MENU_CONFIRM_REMOVE, Messages.group(group)))))
                .build();
        ActionButton yes = button(text(Message.MENU_CONFIRM_YES), onGroup(group, actions::remove));
        ActionButton no = button(text(Message.MENU_CONFIRM_NO), onGroup(group, this::openGroup));
        show(player, Dialog.create(factory -> factory.empty().base(base).type(DialogType.confirmation(yes, no))));
    }

    public void openMembers(Player player, StorageGroup group) {
        if (!actions.canManage(player, group)) return;
        long id = group.id();
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(text(Message.MENU_MEMBERS_ADD), (view, p) -> withGroup(p, id, g -> {
            String name = playerInput(view);
            if (!name.isEmpty()) actions.addMember(p, g, name, () -> openMembers(p, g));
        })));
        for (UUID member : List.copyOf(group.members())) {
            String name = PlayerNames.of(member);
            buttons.add(button(text(Message.MENU_MEMBERS_REMOVE, Messages.player(name)),
                    (view, p) -> withGroup(p, id, g -> actions.removeMember(p, g, name, () -> openMembers(p, g)))));
        }
        buttons.add(button(text(Message.MENU_BACK), onGroup(group, this::openGroup)));
        show(player, playerListDialog(text(Message.MENU_MEMBERS_TITLE, Messages.group(group)), buttons));
    }

    public void openTrust(Player player, GroupType backTo) {
        if (!player.hasPermission(Permissions.TRUST)) {
            services.send(player, Message.ERROR_NO_PERMISSION);
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(text(Message.MENU_TRUST_ADD), (view, p) -> {
            String name = playerInput(view);
            if (!name.isEmpty()) actions.trust(p, name, () -> openTrust(p, backTo));
        }));
        for (UUID trusted : List.copyOf(services.trust().trustedBy(player.getUniqueId()))) {
            String name = PlayerNames.of(trusted);
            buttons.add(button(text(Message.MENU_TRUST_REMOVE, Messages.player(name)),
                    (view, p) -> actions.untrust(p, name, () -> openTrust(p, backTo))));
        }
        buttons.add(button(text(Message.MENU_BACK), (view, p) -> openHub(p, backTo, "", 0)));
        show(player, playerListDialog(text(Message.MENU_TRUST_TITLE), buttons));
    }

    private Dialog playerListDialog(Component title, List<ActionButton> buttons) {
        DialogBase base = baseBuilder(title)
                .inputs(List.of(DialogInput.text(PLAYER_INPUT, text(Message.MENU_MEMBERS_PLAYER)).maxLength(16).build()))
                .build();
        return multiAction(base, buttons, 1);
    }

    public PaginatedMenu openGrid(Player player, GroupType type) {
        List<PaginatedMenu.Entry> entries = new ArrayList<>();
        for (StorageGroup group : hubGroups(player, type, "")) {
            ItemStack icon = namedIcon(group, Message.MENU_GRID_ENTRY_LORE,
                    Messages.text("owner", PlayerNames.of(group.owner())),
                    Messages.text("items", summary(group)));
            entries.add(new PaginatedMenu.Entry(icon, (p, click) -> withGroup(p, group.id(), g -> onGridClick(p, g, click))));
        }
        PaginatedMenu menu = new PaginatedMenu(6, text(Message.MENU_GRID_TITLE), entries, text(Message.MENU_GRID_PREVIOUS),
                text(Message.MENU_GRID_NEXT));
        menu.open(player);
        return menu;
    }

    /** Right-click opens the group dialog; left-click opens the group itself and comes back to the grid afterwards. */
    private void onGridClick(Player player, StorageGroup group, ClickType click) {
        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            player.closeInventory();
            openGroup(player, group);
        } else {
            openRemote(player, group, () -> openGrid(player, group.type()));
        }
    }

    /** Opens the group itself (its inventory or recipe); when the player closes it, {@code back} reopens the menu they came from. */
    private void openRemote(Player player, StorageGroup group, Runnable back) {
        if (actions.openRemote(player, group)) menus.returnTo(player, back);
    }

    private Dialog multiAction(DialogBase base, List<ActionButton> buttons, int columns) {
        // An action-less button would leave the dialog open, since dialogs keep themselves open after clicks.
        ActionButton close = button(text(Message.MENU_CLOSE), (view, p) -> p.closeDialog());
        return Dialog.create(factory -> factory.empty()
                .base(base)
                .type(DialogType.multiAction(buttons).columns(columns).exitAction(close).build()));
    }

    /** A click handler that re-fetches the group by id first (it may have been removed or renamed since the dialog was shown). */
    private BiConsumer<DialogResponseView, Player> onGroup(StorageGroup group, BiConsumer<Player, StorageGroup> action) {
        long id = group.id();
        return (view, player) -> withGroup(player, id, g -> action.accept(player, g));
    }

    private void withGroup(Player player, long id, Consumer<StorageGroup> action) {
        StorageGroup group = services.groups().byId(id);
        if (group == null) services.send(player, Message.ERROR_UNKNOWN_GROUP, Messages.text("group", "#" + id));
        else action.accept(group);
    }

    private ActionButton button(Component label, BiConsumer<DialogResponseView, Player> onClick) {
        return button(label, BUTTON_WIDTH, onClick);
    }

    /** A single-use, expiring dialog button whose callback runs on the main thread. */
    private ActionButton button(Component label, int width, BiConsumer<DialogResponseView, Player> onClick) {
        return ActionButton.builder(label)
                .width(width)
                .action(DialogAction.customClick((view, audience) -> onMainThread(audience, p -> onClick.accept(view, p)), CALLBACK_OPTIONS))
                .build();
    }

    /**
     * Every dialog keeps itself open after a click ({@link DialogAfterAction#NONE}) so the next one replaces it without flickering back to the
     * world. Vanilla rejects that for dialogs that pause the game.
     */
    private static DialogBase.Builder baseBuilder(Component title) {
        return DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogAfterAction.NONE);
    }

    private void show(Player player, Dialog dialog) {
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
        if (Bukkit.isPrimaryThread()) run.run();
        else Bukkit.getScheduler().runTask(services.plugin(), run);
    }

    private Component text(Message message, TagResolver... placeholders) {
        return services.messages().get(message, placeholders);
    }

    private ItemStack icon(StorageGroup group) {
        GroupTypeHandler handler = links.handler(group.type());
        return handler == null ? ItemStack.of(Material.CHEST) : handler.icon(group);
    }

    /** The group's icon named after the group, with {@code lore} as its tooltip. */
    private ItemStack namedIcon(StorageGroup group, Message lore, TagResolver... placeholders) {
        ItemStack icon = icon(group).clone();
        icon.setData(DataComponentTypes.ITEM_NAME, Component.text(group.name()));
        icon.setData(DataComponentTypes.LORE, ItemLore.lore(services.messages().lines(lore, placeholders)));
        return icon;
    }

    private String summary(StorageGroup group) {
        GroupTypeHandler handler = links.handler(group.type());
        return handler == null ? "" : handler.summary(group);
    }

    private static String memberNames(StorageGroup group) {
        return group.members().isEmpty() ? "-" : PlayerNames.join(group.members());
    }

    private static String search(DialogResponseView view) {
        String value = view.getText(SEARCH_INPUT);
        return value == null ? "" : value;
    }

    private static String playerInput(DialogResponseView view) {
        String value = view.getText(PLAYER_INPUT);
        return value == null ? "" : value.trim();
    }
}
