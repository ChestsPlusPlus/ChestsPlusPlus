package com.jamesdpeters.chestsplusplus.ui;

import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.access.AccessService;
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
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Menus (plan §5.7): the Dialog hub (search, paging, one button per group), the group, members, trust and confirm
 * dialogs, and the icon grid. Every action goes through {@link GroupActions}, which re-checks permissions.
 */
public final class UiService {

    static final int GROUPS_PER_PAGE = 10;
    private static final Duration CALLBACK_LIFETIME = Duration.ofMinutes(10);

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

    private Component text(Message message, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... args) {
        return services.messages().get(message, args);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Hub
    // ---------------------------------------------------------------------------------------------------------------

    /** Groups shown in the hub for {@code search} (case-insensitive substring of name or owner). */
    public List<StorageGroup> hubGroups(Player player, GroupType type, String search) {
        String needle = search.trim().toLowerCase(Locale.ROOT);
        return links.accessibleGroups(player.getUniqueId(), AccessService.hasBypass(player), type).stream().filter(g -> needle.isEmpty()
                || g.name().toLowerCase(Locale.ROOT).contains(needle) || LinkService.ownerName(g.owner()).toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    public void openHub(Player player, GroupType type, String search, int page) {
        if (!player.hasPermission(Permissions.menu(type))) {
            services.messages().send(player, Message.ERROR_NO_PERMISSION);
            return;
        }
        List<StorageGroup> groups = hubGroups(player, type, search);
        int pages = Math.max(1, (groups.size() + GROUPS_PER_PAGE - 1) / GROUPS_PER_PAGE);
        int current = Math.clamp(page, 0, pages - 1);
        GroupTypeHandler handler = links.handler(type);

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(text(Message.MENU_HUB_SEARCH_BUTTON), null, (view, p) -> openHub(p, type, nonNull(view.getText("search")), 0)));
        buttons.add(button(text(Message.MENU_HUB_GRID_BUTTON), null, (view, p) -> openGrid(p, type)));
        if (player.hasPermission(Permissions.TRUST)) {
            buttons.add(button(text(Message.MENU_HUB_TRUST_BUTTON), null, (view, p) -> openTrust(p, type)));
        }
        for (StorageGroup group : groups.subList(current * GROUPS_PER_PAGE, Math.min(groups.size(), (current + 1) * GROUPS_PER_PAGE))) {
            long id = group.id();
            buttons.add(button(
                    text(Message.MENU_HUB_GROUP_BUTTON, Messages.text("group", group.name()),
                            Messages.text("owner", LinkService.ownerName(group.owner())),
                            Messages.text("items", handler == null ? "" : handler.summary(group))),
                    text(Message.MENU_HUB_GROUP_TOOLTIP, Messages.text("owner", LinkService.ownerName(group.owner())),
                            Messages.component("public", text(group.isPublic() ? Message.STATE_PUBLIC : Message.STATE_PRIVATE)),
                            Messages.text("members", memberNames(group))),
                    (view, p) -> withGroup(p, id, g -> openGroup(p, g))));
        }
        if (current > 0) {
            buttons.add(button(text(Message.MENU_HUB_PREVIOUS), null, (view, p) -> openHub(p, type, nonNull(view.getText("search")), current - 1)));
        }
        if (current < pages - 1) {
            buttons.add(button(text(Message.MENU_HUB_NEXT), null, (view, p) -> openHub(p, type, nonNull(view.getText("search")), current + 1)));
        }

        List<DialogBody> body = new ArrayList<>();
        if (groups.isEmpty()) body.add(DialogBody.plainMessage(text(Message.MENU_HUB_EMPTY)));
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(text(Message.MENU_HUB_TITLE)).canCloseWithEscape(true).body(body)
                        .inputs(List.of(DialogInput.text("search", text(Message.MENU_HUB_SEARCH)).initial(search).maxLength(32).build())).build())
                .type(DialogType.multiAction(buttons).columns(1).exitAction(ActionButton.builder(text(Message.MENU_CLOSE)).build()).build()));
        player.showDialog(dialog);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Group
    // ---------------------------------------------------------------------------------------------------------------

    public void openGroup(Player player, StorageGroup group) {
        if (!actions.canUse(player, group)) return;
        GroupTypeHandler handler = links.handler(group.type());
        boolean manage = services.access().canManage(player.getUniqueId(), player, group);
        long id = group.id();
        GroupType type = group.type();

        List<DialogInput> inputs = new ArrayList<>();
        if (manage) {
            inputs.add(DialogInput.text("name", text(Message.MENU_GROUP_NEW_NAME)).initial(group.name()).maxLength(32).build());
            inputs.add(DialogInput.bool("public", text(Message.MENU_GROUP_PUBLIC)).initial(group.isPublic()).build());
            if (group instanceof ChestLinkGroup chest && player.hasPermission(Permissions.CHESTLINK_SORT)) {
                List<SingleOptionDialogInput.OptionEntry> modes = new ArrayList<>();
                for (SortMode mode : SortMode.values()) {
                    modes.add(SingleOptionDialogInput.OptionEntry.create(mode.name(), Component.text(mode.name().toLowerCase(Locale.ROOT)),
                            mode == chest.sortMode()));
                }
                inputs.add(DialogInput.singleOption("sort", text(Message.MENU_GROUP_SORT_MODE), modes).build());
            }
        }

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(text(type == GroupType.AUTOCRAFT ? Message.MENU_GROUP_RECIPE : Message.MENU_GROUP_OPEN), null,
                (view, p) -> withGroup(p, id, g -> actions.openRemote(p, g))));
        if (manage) {
            buttons.add(button(text(Message.MENU_GROUP_SAVE), null, (view, p) -> withGroup(p, id, g -> {
                save(p, g, view);
                openGroup(p, g);
            })));
            if (player.hasPermission(Permissions.members(type))) {
                buttons.add(button(text(Message.MENU_GROUP_MEMBERS), null, (view, p) -> withGroup(p, id, g -> openMembers(p, g))));
            }
            if (player.hasPermission(Permissions.remove(type))) {
                buttons.add(button(text(Message.MENU_GROUP_REMOVE), null, (view, p) -> withGroup(p, id, g -> confirmRemove(p, g))));
            }
        }
        buttons.add(button(text(Message.MENU_BACK), null, (view, p) -> openHub(p, type, "", 0)));

        ItemStack icon = handler == null ? ItemStack.of(Material.CHEST) : handler.icon(group);
        Dialog dialog = Dialog.create(factory -> factory
                .empty().base(
                        DialogBase
                                .builder(text(Message.MENU_GROUP_TITLE, Messages.text("group", group.name()))).canCloseWithEscape(
                                        true)
                                .body(List.of(DialogBody.item(icon)
                                        .description(DialogBody.plainMessage(Component
                                                .text(LinkService.ownerName(group.owner()) + " · " + (handler == null ? "" : handler.summary(group))
                                                        + " · " + services.nodes().count(group.id()) + " block(s)")))
                                        .build()))
                                .inputs(inputs).build())
                .type(DialogType.multiAction(buttons).columns(2).exitAction(ActionButton.builder(text(Message.MENU_CLOSE)).build()).build()));
        player.showDialog(dialog);
    }

    /** Applies the group dialog's inputs: only what changed, each through {@link GroupActions}. */
    void save(Player player, StorageGroup group, DialogResponseView view) {
        String name = view.getText("name");
        if (name != null && !name.isBlank() && !name.equals(group.name())) actions.rename(player, group, name.trim());
        Boolean isPublic = view.getBoolean("public");
        if (isPublic != null && isPublic != group.isPublic()) actions.setPublic(player, group, isPublic);
        String sort = view.getText("sort");
        if (sort != null && group instanceof ChestLinkGroup chest) {
            try {
                SortMode mode = SortMode.valueOf(sort);
                if (mode != chest.sortMode()) actions.sort(player, chest, mode);
            } catch (IllegalArgumentException ignored) {
                // unknown option id; ignore
            }
        }
    }

    public void confirmRemove(Player player, StorageGroup group) {
        long id = group.id();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(text(Message.MENU_GROUP_REMOVE))
                        .body(List.of(DialogBody.plainMessage(text(Message.MENU_CONFIRM_REMOVE, Messages.text("group", group.name()))))).build())
                .type(DialogType.confirmation(button(text(Message.MENU_CONFIRM_YES), null, (view, p) -> withGroup(p, id, g -> actions.remove(p, g))),
                        button(text(Message.MENU_CONFIRM_NO), null, (view, p) -> withGroup(p, id, g -> openGroup(p, g))))));
        player.showDialog(dialog);
    }

    public void openMembers(Player player, StorageGroup group) {
        if (!actions.canManage(player, group)) return;
        long id = group.id();
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(text(Message.MENU_MEMBERS_ADD), null, (view, p) -> withGroup(p, id, g -> {
            String name = nonNull(view.getText("player")).trim();
            if (!name.isEmpty()) actions.addMember(p, g, name, () -> openMembers(p, g));
        })));
        for (UUID member : List.copyOf(group.members())) {
            String name = LinkService.ownerName(member);
            buttons.add(button(text(Message.MENU_MEMBERS_REMOVE, Messages.text("player", name)), null,
                    (view, p) -> withGroup(p, id, g -> actions.removeMember(p, g, name, () -> openMembers(p, g)))));
        }
        buttons.add(button(text(Message.MENU_BACK), null, (view, p) -> withGroup(p, id, g -> openGroup(p, g))));
        player.showDialog(playerListDialog(text(Message.MENU_MEMBERS_TITLE, Messages.text("group", group.name())), buttons));
    }

    public void openTrust(Player player, GroupType backTo) {
        if (!player.hasPermission(Permissions.TRUST)) {
            services.messages().send(player, Message.ERROR_NO_PERMISSION);
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(text(Message.MENU_TRUST_ADD), null, (view, p) -> {
            String name = nonNull(view.getText("player")).trim();
            if (!name.isEmpty()) actions.trust(p, name, () -> openTrust(p, backTo));
        }));
        for (UUID trusted : List.copyOf(services.trust().trustedBy(player.getUniqueId()))) {
            String name = LinkService.ownerName(trusted);
            buttons.add(button(text(Message.MENU_TRUST_REMOVE, Messages.text("player", name)), null,
                    (view, p) -> actions.untrust(p, name, () -> openTrust(p, backTo))));
        }
        buttons.add(button(text(Message.MENU_BACK), null, (view, p) -> openHub(p, backTo, "", 0)));
        player.showDialog(playerListDialog(text(Message.MENU_TRUST_TITLE), buttons));
    }

    private Dialog playerListDialog(Component title, List<ActionButton> buttons) {
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title).canCloseWithEscape(true)
                        .inputs(List.of(DialogInput.text("player", text(Message.MENU_MEMBERS_PLAYER)).maxLength(16).build())).build())
                .type(DialogType.multiAction(buttons).columns(1).exitAction(ActionButton.builder(text(Message.MENU_CLOSE)).build()).build()));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Icon grid
    // ---------------------------------------------------------------------------------------------------------------

    public PaginatedMenu openGrid(Player player, GroupType type) {
        List<PaginatedMenu.Entry> entries = new ArrayList<>();
        GroupTypeHandler handler = links.handler(type);
        for (StorageGroup group : hubGroups(player, type, "")) {
            long id = group.id();
            ItemStack icon = (handler == null ? ItemStack.of(Material.CHEST) : handler.icon(group)).clone();
            icon.setData(DataComponentTypes.ITEM_NAME, Component.text(group.name()));
            icon.setData(DataComponentTypes.LORE,
                    ItemLore.lore(
                            services.messages().lines(Message.MENU_GRID_ENTRY_LORE, Messages.text("owner", LinkService.ownerName(group.owner())),
                                    Messages.text("items", handler == null ? "" : handler.summary(group)))));
            entries.add(new PaginatedMenu.Entry(icon, (p, click) -> withGroup(p, id, g -> {
                if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                    p.closeInventory();
                    openGroup(p, g);
                } else if (actions.openRemote(p, g)) {
                    menus.returnTo(p, () -> openGrid(p, type));
                }
            })));
        }
        PaginatedMenu menu = new PaginatedMenu(6, text(Message.MENU_GRID_TITLE), entries, text(Message.MENU_GRID_PREVIOUS),
                text(Message.MENU_GRID_NEXT));
        menu.open(player);
        return menu;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------------

    /** Re-fetches the group by id (it may have been removed or renamed since the dialog was shown). */
    private void withGroup(Player player, long id, java.util.function.Consumer<StorageGroup> action) {
        StorageGroup group = services.groups().byId(id);
        if (group == null) {
            services.messages().send(player, Message.ERROR_UNKNOWN_GROUP, Messages.text("group", "#" + id));
            return;
        }
        action.accept(group);
    }

    /**
     * A dialog button whose callback always runs on the main thread for an online player (spike S3: the callback
     * thread isn't confirmed yet, so this re-dispatches if needed). Single use, with an expiry (plan §5.7).
     */
    private ActionButton button(Component label, @Nullable Component tooltip, BiConsumer<DialogResponseView, Player> onClick) {
        ActionButton.Builder builder = ActionButton.builder(label).width(250);
        if (tooltip != null) builder.tooltip(tooltip);
        return builder.action(DialogAction.customClick((view, audience) -> {
            if (!(audience instanceof Player player)) return;
            Runnable run = () -> {
                if (player.isOnline() && services.plugin().isEnabled()) onClick.accept(view, player);
            };
            if (Bukkit.isPrimaryThread()) run.run();
            else Bukkit.getScheduler().runTask(services.plugin(), run);
        }, ClickCallback.Options.builder().uses(1).lifetime(CALLBACK_LIFETIME).build())).build();
    }

    private String memberNames(StorageGroup group) {
        return group.members().isEmpty() ? "-" : group.members().stream().map(LinkService::ownerName).collect(Collectors.joining(", "));
    }

    private static String nonNull(@Nullable String value) {
        return value == null ? "" : value;
    }
}
