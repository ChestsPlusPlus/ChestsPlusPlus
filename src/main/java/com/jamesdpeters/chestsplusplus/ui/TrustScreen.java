package com.jamesdpeters.chestsplusplus.ui;

import com.jamesdpeters.chestsplusplus.Permissions;
import com.jamesdpeters.chestsplusplus.access.TrustService;
import com.jamesdpeters.chestsplusplus.core.PlayerNames;
import com.jamesdpeters.chestsplusplus.link.Checks;
import com.jamesdpeters.chestsplusplus.link.GroupActions;
import com.jamesdpeters.chestsplusplus.message.Message;
import com.jamesdpeters.chestsplusplus.message.Messages;
import com.jamesdpeters.chestsplusplus.model.GroupType;
import io.papermc.paper.registry.data.dialog.ActionButton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;

/** The players a player trusts with all their groups: add by name, or remove one. */
@RequiredArgsConstructor
final class TrustScreen {

    private final TrustService trust;
    private final Checks checks;
    private final GroupActions actions;
    private final Dialogs dialogs;
    private final Navigator navigator;

    void open(Player player, GroupType backTo) {
        if (checks.refused(player, checks.permission(player, Permissions.TRUST))) return;
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(dialogs.button(dialogs.text(Message.MENU_TRUST_ADD), (view, p) -> {
            String name = Dialogs.playerInput(view);
            if (!name.isEmpty()) actions.trust(p, name, () -> open(p, backTo));
        }));
        for (UUID trusted : List.copyOf(trust.trustedBy(player.getUniqueId()))) {
            String name = PlayerNames.of(trusted);
            buttons.add(dialogs.button(dialogs.text(Message.MENU_TRUST_REMOVE, Messages.player(name)),
                    (view, p) -> actions.untrust(p, name, () -> open(p, backTo))));
        }
        buttons.add(dialogs.button(dialogs.text(Message.MENU_BACK), (view, p) -> navigator.openHub(p, backTo, "", 0)));
        dialogs.show(player, dialogs.playerListDialog(dialogs.text(Message.MENU_TRUST_TITLE), buttons));
    }
}
