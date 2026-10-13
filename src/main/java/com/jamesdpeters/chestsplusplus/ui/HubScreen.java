package com.jamesdpeters.chestsplusplus.ui;

import com.jamesdpeters.chestsplusplus.GroupPermission;
import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.access.AccessService;
import com.jamesdpeters.chestsplusplus.access.AccessibleGroups;
import com.jamesdpeters.chestsplusplus.core.PlayerNames;
import com.jamesdpeters.chestsplusplus.link.Checks;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** The Dialog hub: a row per group with its icon, then search, paging and a toolbar. */
@RequiredArgsConstructor
final class HubScreen {

    /** Each group is a body row about 36px tall, so six fit on screen with the controls before the dialog starts scrolling. */
    static final int GROUPS_PER_PAGE = 6;
    /** Three toolbar columns plus the client's 2px gaps, so the header, group rows and search box line up with the toolbar. */
    private static final int HUB_WIDTH = 3 * Dialogs.TOOLBAR_BUTTON_WIDTH + 4;
    /**
     * Giving a group row's description a width fixes its box size, so rows line up. The client centres text in that box, so keeping it
     * narrow keeps the text beside its icon. Longer names wrap onto a third line.
     */
    private static final int GROUP_ROW_TEXT_WIDTH = 220;
    /** Defined on the search box and read back from the {@link DialogResponseView}. */
    private static final String SEARCH_INPUT = "search";

    private final AccessService access;
    private final Checks checks;
    private final Dialogs dialogs;
    private final Navigator navigator;

    /** Groups shown in the hub for {@code search} (case-insensitive substring of name or owner). */
    AccessibleGroups groups(Player player, GroupType type, String search) {
        String needle = search.trim().toLowerCase(Locale.ROOT);
        AccessibleGroups accessible = access.accessibleGroups(player.getUniqueId(), AccessService.hasBypass(player), type);
        return accessible.filter(g -> needle.isEmpty() || contains(g.name(), needle) || contains(accessible.ownerName(g), needle));
    }

    private static boolean contains(String text, String lowerCaseNeedle) {
        return text.toLowerCase(Locale.ROOT).contains(lowerCaseNeedle);
    }

    void open(Player player, GroupType type, String search, int page) {
        if (checks.refused(player, checks.permission(player, Permissions.of(type, GroupPermission.MENU)), checks.enabled(type))) return;
        AccessibleGroups accessible = groups(player, type, search);
        List<StorageGroup> groups = accessible.groups();
        int pages = Math.max(1, (groups.size() + GROUPS_PER_PAGE - 1) / GROUPS_PER_PAGE);
        int current = Math.clamp(page, 0, pages - 1);
        int from = current * GROUPS_PER_PAGE;

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(header(groups.size(), current, pages), HUB_WIDTH));
        List<StorageGroup> shown = groups.subList(from, Math.min(groups.size(), from + GROUPS_PER_PAGE));
        shown.forEach(group -> body.add(groupRow(group, accessible.ownerName(group), search, current)));
        for (int i = shown.size(); i < GROUPS_PER_PAGE; i++) body.add(blankGroupRow());
        DialogInput searchInput = DialogInput.text(SEARCH_INPUT, dialogs.text(Message.MENU_HUB_SEARCH))
                .initial(search)
                .maxLength(32)
                .width(HUB_WIDTH)
                .build();
        DialogBase base = Dialogs.baseBuilder(dialogs.text(Message.MENU_HUB_TITLE, Messages.text("type", type.displayName())))
                .body(body)
                .inputs(List.of(searchInput))
                .build();
        dialogs.show(player, dialogs.multiAction(base, toolbar(player, type, current, pages), 3));
    }

    private Component header(int groups, int current, int pages) {
        if (groups == 0) return dialogs.text(Message.MENU_HUB_EMPTY);
        return dialogs.text(Message.MENU_HUB_HEADER, Messages.text("count", groups), Messages.text("page", current + 1),
                Messages.text("pages", pages));
    }

    /** The group's icon (hover for details) beside its name, owner and Open / Manage links. Closing the opened group returns to this page. */
    private DialogBody groupRow(StorageGroup group, String ownerName, String search, int page) {
        GroupType type = group.type();
        BiConsumer<Player, StorageGroup> open = (p, g) -> dialogs.openRemote(p, g, () -> open(p, type, search, page));
        Component description = dialogs.text(Message.MENU_HUB_GROUP,
                Messages.group(group),
                Messages.text("items", dialogs.summary(group)),
                Messages.component("owner_head", Component.object(ObjectContents.playerHead(group.owner()))),
                Messages.text("owner", ownerName),
                Messages.component("open", dialogs.link(type.pick(Message.MENU_HUB_GROUP_OPEN, Message.MENU_HUB_GROUP_RECIPE), group, open)),
                Messages.component("manage", dialogs.link(Message.MENU_HUB_GROUP_MANAGE, group, navigator::openGroup)));
        ItemStack icon = dialogs.namedIcon(group, Message.MENU_HUB_GROUP_LORE,
                Messages.text("owner", ownerName),
                Messages.text("items", dialogs.summary(group)),
                Messages.component("public", dialogs.text(group.isPublic() ? Message.STATE_PUBLIC : Message.STATE_PRIVATE)),
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
    private List<ActionButton> toolbar(Player player, GroupType type, int current, int pages) {
        List<ActionButton> buttons = new ArrayList<>();
        if (pages > 1) buttons.add(pageButton(Message.MENU_HUB_PREVIOUS, type, current, current - 1, current > 0));
        buttons.add(dialogs.toolbarButton(dialogs.text(Message.MENU_HUB_SEARCH_BUTTON), (view, p) -> open(p, type, search(view), 0)));
        if (pages > 1) buttons.add(pageButton(Message.MENU_HUB_NEXT, type, current, current + 1, current < pages - 1));
        buttons.add(dialogs.toolbarButton(dialogs.text(Message.MENU_HUB_GRID_BUTTON), (view, p) -> navigator.openGrid(p, type)));
        if (player.hasPermission(Permissions.TRUST)) {
            buttons.add(dialogs.toolbarButton(dialogs.text(Message.MENU_HUB_TRUST_BUTTON), (view, p) -> navigator.openTrust(p, type)));
        }
        return buttons;
    }

    /** A disabled paging button only re-shows the page: a button with no action would close the dialog instead. */
    private ActionButton pageButton(Message label, GroupType type, int current, int target, boolean enabled) {
        Component shown = enabled ? dialogs.text(label) : Component.text(dialogs.plain(label), NamedTextColor.DARK_GRAY);
        int to = enabled ? target : current;
        return dialogs.toolbarButton(shown, (view, p) -> open(p, type, search(view), to));
    }

    private static String memberNames(StorageGroup group) {
        return group.members().isEmpty() ? "-" : PlayerNames.join(group.members());
    }

    private static String search(DialogResponseView view) {
        String value = view.getText(SEARCH_INPUT);
        return value == null ? "" : value;
    }
}
