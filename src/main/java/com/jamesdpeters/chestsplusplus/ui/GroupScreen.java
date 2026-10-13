package com.jamesdpeters.chestsplusplus.ui;

import com.jamesdpeters.chestsplusplus.GroupPermission;
import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.core.PlayerNames;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.link.Checks;
import com.jamesdpeters.chestsplusplus.link.GroupActions;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.ChestLinkGroup;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import com.jamesdpeters.chestsplusplus.model.SortMode;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** One group's dialog: open it, and for managers rename, visibility, sort mode, members and remove. */
@RequiredArgsConstructor
final class GroupScreen {

    /** Dialog input keys: each is defined on a {@link DialogInput} and read back from the {@link DialogResponseView}. */
    static final String NAME_INPUT = "name";
    static final String PUBLIC_INPUT = "public";
    static final String SORT_INPUT = "sort";

    private final Services services;
    private final Checks checks;
    private final GroupActions actions;
    private final Dialogs dialogs;
    private final Navigator navigator;

    void open(Player player, StorageGroup group) {
        if (checks.refused(player, checks.enabled(group.type()), checks.use(player, group))) return;
        boolean manage = services.access().canManage(player.getUniqueId(), player, group);
        String description = PlayerNames.of(group.owner()) + " · " + dialogs.summary(group) + " · " + services.nodes().count(group.id())
                + " block(s)";
        DialogBase base = Dialogs.baseBuilder(dialogs.text(Message.MENU_GROUP_TITLE, Messages.group(group)))
                .body(List.of(DialogBody.item(dialogs.icon(group)).description(DialogBody.plainMessage(Component.text(description))).build()))
                .inputs(manage ? inputs(player, group) : List.of())
                .build();
        dialogs.show(player, dialogs.multiAction(base, buttons(player, group, manage), 2));
    }

    private List<DialogInput> inputs(Player player, StorageGroup group) {
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.text(NAME_INPUT, dialogs.text(Message.MENU_GROUP_NEW_NAME)).initial(group.name()).maxLength(32).build());
        inputs.add(DialogInput.bool(PUBLIC_INPUT, dialogs.text(Message.MENU_GROUP_PUBLIC)).initial(group.isPublic()).build());
        if (group instanceof ChestLinkGroup chest && player.hasPermission(Permissions.CHESTLINK_SORT)) {
            List<SingleOptionDialogInput.OptionEntry> modes = new ArrayList<>();
            for (SortMode mode : SortMode.values()) {
                Component label = Component.text(mode.key());
                modes.add(SingleOptionDialogInput.OptionEntry.create(mode.name(), label, mode == chest.sortMode()));
            }
            inputs.add(DialogInput.singleOption(SORT_INPUT, dialogs.text(Message.MENU_GROUP_SORT_MODE), modes).build());
        }
        return inputs;
    }

    private List<ActionButton> buttons(Player player, StorageGroup group, boolean manage) {
        GroupType type = group.type();
        List<ActionButton> buttons = new ArrayList<>();
        BiConsumer<Player, StorageGroup> open = (p, g) -> dialogs.openRemote(p, g, () -> dialogs.withGroup(p, g.id(), latest -> open(p, latest)));
        buttons.add(dialogs.button(dialogs.text(type.pick(Message.MENU_GROUP_OPEN, Message.MENU_GROUP_RECIPE)), dialogs.onGroup(group, open)));
        if (manage) {
            buttons.add(dialogs.button(dialogs.text(Message.MENU_GROUP_SAVE), (view, p) -> dialogs.withGroup(p, group.id(), g -> {
                save(p, g, view);
                open(p, g);
            })));
            if (player.hasPermission(Permissions.of(type, GroupPermission.MEMBERS)))
                buttons.add(dialogs.button(dialogs.text(Message.MENU_GROUP_MEMBERS), dialogs.onGroup(group, navigator::openMembers)));
            if (player.hasPermission(Permissions.of(type, GroupPermission.REMOVE)))
                buttons.add(dialogs.button(dialogs.text(Message.MENU_GROUP_REMOVE), dialogs.onGroup(group, this::confirmRemove)));
        }
        buttons.add(dialogs.button(dialogs.text(Message.MENU_BACK), (view, p) -> navigator.openHub(p, type, "", 0)));
        return buttons;
    }

    /** Applies the group dialog's inputs: only what changed, each through {@link GroupActions}. */
    void save(Player player, StorageGroup group, DialogResponseView view) {
        String name = view.getText(NAME_INPUT);
        if (name != null && !name.isBlank() && !name.equals(group.name())) actions.rename(player, group, name.trim());
        Boolean isPublic = view.getBoolean(PUBLIC_INPUT);
        if (isPublic != null && isPublic != group.isPublic()) actions.setPublic(player, group, isPublic);
        String sort = view.getText(SORT_INPUT);
        SortMode mode = sort == null ? null : SortMode.parse(sort);
        if (mode != null && group instanceof ChestLinkGroup chest && mode != chest.sortMode()) actions.sort(player, chest, mode);
    }

    private void confirmRemove(Player player, StorageGroup group) {
        DialogBase base = Dialogs.baseBuilder(dialogs.text(Message.MENU_GROUP_REMOVE))
                .body(List.of(DialogBody.plainMessage(dialogs.text(Message.MENU_CONFIRM_REMOVE, Messages.group(group)))))
                .build();
        ActionButton yes = dialogs.button(dialogs.text(Message.MENU_CONFIRM_YES), dialogs.onGroup(group, actions::remove));
        ActionButton no = dialogs.button(dialogs.text(Message.MENU_CONFIRM_NO), dialogs.onGroup(group, this::open));
        dialogs.show(player, Dialog.create(factory -> factory.empty().base(base).type(DialogType.confirmation(yes, no))));
    }
}
