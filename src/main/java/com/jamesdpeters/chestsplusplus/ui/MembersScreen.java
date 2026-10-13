package com.jamesdpeters.chestsplusplus.ui;

import com.jamesdpeters.chestsplusplus.core.PlayerNames;
import com.jamesdpeters.chestsplusplus.link.Checks;
import com.jamesdpeters.chestsplusplus.link.GroupActions;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import io.papermc.paper.registry.data.dialog.ActionButton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;

/** A group's members: add by name, or remove one. */
@RequiredArgsConstructor
final class MembersScreen {

    private final Checks checks;
    private final GroupActions actions;
    private final Dialogs dialogs;
    private final Navigator navigator;

    void open(Player player, StorageGroup group) {
        if (checks.refused(player, checks.manage(player, group))) return;
        long id = group.id();
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(dialogs.button(dialogs.text(Message.MENU_MEMBERS_ADD), (view, p) -> dialogs.withGroup(p, id, g -> {
            String name = Dialogs.playerInput(view);
            if (!name.isEmpty()) actions.addMember(p, g, name, () -> open(p, g));
        })));
        for (UUID member : List.copyOf(group.members())) {
            String name = PlayerNames.of(member);
            buttons.add(dialogs.button(dialogs.text(Message.MENU_MEMBERS_REMOVE, Messages.player(name)),
                    (view, p) -> dialogs.withGroup(p, id, g -> actions.removeMember(p, g, name, () -> open(p, g)))));
        }
        buttons.add(dialogs.button(dialogs.text(Message.MENU_BACK), dialogs.onGroup(group, navigator::openGroup)));
        dialogs.show(player, dialogs.playerListDialog(dialogs.text(Message.MENU_MEMBERS_TITLE, Messages.group(group)), buttons));
    }
}
