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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Menus: the Dialog hub (search, paging, one button per group), the group, members, trust and confirm dialogs, and the icon grid. Every
 * action goes through {@link GroupActions}, which re-checks permissions.
 */
public final class UiService {

    static final int GROUPS_PER_PAGE = 10;
    private static final Duration CALLBACK_LIFETIME = Duration.ofMinutes(10);
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

    public UiService(Services services, LinkService links, GroupActions actions, MenuListener menus) {
        this.services = services;
        this.links = links;
        this.actions = actions;
        this.menus = menus;
    }

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

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(text(Message.MENU_HUB_SEARCH_BUTTON), (view, p) -> openHub(p, type, search(view), 0)));
        buttons.add(button(text(Message.MENU_HUB_GRID_BUTTON), (view, p) -> openGrid(p, type)));
        if (player.hasPermission(Permissions.TRUST)) buttons.add(button(text(Message.MENU_HUB_TRUST_BUTTON), (view, p) -> openTrust(p, type)));
        int from = current * GROUPS_PER_PAGE;
        for (StorageGroup group : groups.subList(from, Math.min(groups.size(), from + GROUPS_PER_PAGE))) buttons.add(hubGroupButton(group));
        if (current > 0) buttons.add(button(text(Message.MENU_HUB_PREVIOUS), (view, p) -> openHub(p, type, search(view), current - 1)));
        if (current < pages - 1) buttons.add(button(text(Message.MENU_HUB_NEXT), (view, p) -> openHub(p, type, search(view), current + 1)));

        DialogInput searchInput = DialogInput.text(SEARCH_INPUT, text(Message.MENU_HUB_SEARCH)).initial(search).maxLength(32).build();
        DialogBase base = DialogBase.builder(text(Message.MENU_HUB_TITLE))
                .canCloseWithEscape(true)
                .body(groups.isEmpty() ? List.of(DialogBody.plainMessage(text(Message.MENU_HUB_EMPTY))) : List.of())
                .inputs(List.of(searchInput))
                .build();
        player.showDialog(multiAction(base, buttons, 1));
    }

    private ActionButton hubGroupButton(StorageGroup group) {
        String owner = PlayerNames.of(group.owner());
        Component label = text(Message.MENU_HUB_GROUP_BUTTON,
                Messages.group(group),
                Messages.text("owner", owner),
                Messages.text("items", summary(group)));
        Component tooltip = text(Message.MENU_HUB_GROUP_TOOLTIP,
                Messages.text("owner", owner),
                Messages.component("public", text(group.isPublic() ? Message.STATE_PUBLIC : Message.STATE_PRIVATE)),
                Messages.text("members", memberNames(group)));
        return button(label, tooltip, onGroup(group, this::openGroup));
    }

    public void openGroup(Player player, StorageGroup group) {
        if (!actions.canUse(player, group)) return;
        boolean manage = services.access().canManage(player.getUniqueId(), player, group);
        String description = PlayerNames.of(group.owner()) + " · " + summary(group) + " · " + services.nodes().count(group.id()) + " block(s)";
        DialogBase base = DialogBase.builder(text(Message.MENU_GROUP_TITLE, Messages.group(group)))
                .canCloseWithEscape(true)
                .body(List.of(DialogBody.item(icon(group)).description(DialogBody.plainMessage(Component.text(description))).build()))
                .inputs(manage ? groupInputs(player, group) : List.of())
                .build();
        player.showDialog(multiAction(base, groupButtons(player, group, manage), 2));
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
        buttons.add(button(text(type.pick(Message.MENU_GROUP_OPEN, Message.MENU_GROUP_RECIPE)), onGroup(group, actions::openRemote)));
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
        DialogBase base = DialogBase.builder(text(Message.MENU_GROUP_REMOVE))
                .body(List.of(DialogBody.plainMessage(text(Message.MENU_CONFIRM_REMOVE, Messages.group(group)))))
                .build();
        ActionButton yes = button(text(Message.MENU_CONFIRM_YES), onGroup(group, actions::remove));
        ActionButton no = button(text(Message.MENU_CONFIRM_NO), onGroup(group, this::openGroup));
        player.showDialog(Dialog.create(factory -> factory.empty().base(base).type(DialogType.confirmation(yes, no))));
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
        player.showDialog(playerListDialog(text(Message.MENU_MEMBERS_TITLE, Messages.group(group)), buttons));
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
        player.showDialog(playerListDialog(text(Message.MENU_TRUST_TITLE), buttons));
    }

    private Dialog playerListDialog(Component title, List<ActionButton> buttons) {
        DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .inputs(List.of(DialogInput.text(PLAYER_INPUT, text(Message.MENU_MEMBERS_PLAYER)).maxLength(16).build()))
                .build();
        return multiAction(base, buttons, 1);
    }

    public PaginatedMenu openGrid(Player player, GroupType type) {
        List<PaginatedMenu.Entry> entries = new ArrayList<>();
        for (StorageGroup group : hubGroups(player, type, "")) {
            ItemStack icon = icon(group).clone();
            icon.setData(DataComponentTypes.ITEM_NAME, Component.text(group.name()));
            icon.setData(DataComponentTypes.LORE, ItemLore.lore(services.messages().lines(Message.MENU_GRID_ENTRY_LORE,
                    Messages.text("owner", PlayerNames.of(group.owner())),
                    Messages.text("items", summary(group)))));
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
        } else if (actions.openRemote(player, group)) {
            menus.returnTo(player, () -> openGrid(player, group.type()));
        }
    }

    private Dialog multiAction(DialogBase base, List<ActionButton> buttons, int columns) {
        ActionButton close = ActionButton.builder(text(Message.MENU_CLOSE)).build();
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
        return button(label, null, onClick);
    }

    /**
     * A single-use, expiring dialog button whose callback always runs on the main thread for an online player. Paper doesn't document the
     * callback thread, so this re-dispatches if needed.
     */
    private ActionButton button(Component label, @Nullable Component tooltip, BiConsumer<DialogResponseView, Player> onClick) {
        ActionButton.Builder builder = ActionButton.builder(label).width(250);
        if (tooltip != null) builder.tooltip(tooltip);
        ClickCallback.Options options = ClickCallback.Options.builder().uses(1).lifetime(CALLBACK_LIFETIME).build();
        return builder.action(DialogAction.customClick((view, audience) -> {
            if (!(audience instanceof Player player)) return;
            Runnable run = () -> {
                if (player.isOnline() && services.plugin().isEnabled()) onClick.accept(view, player);
            };
            if (Bukkit.isPrimaryThread()) run.run();
            else Bukkit.getScheduler().runTask(services.plugin(), run);
        }, options)).build();
    }

    private Component text(Message message, TagResolver... placeholders) {
        return services.messages().get(message, placeholders);
    }

    private ItemStack icon(StorageGroup group) {
        GroupTypeHandler handler = links.handler(group.type());
        return handler == null ? ItemStack.of(Material.CHEST) : handler.icon(group);
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
