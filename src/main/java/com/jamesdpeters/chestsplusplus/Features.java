package com.jamesdpeters.chestsplusplus;

import com.jamesdpeters.chestsplusplus.autocraft.AutoCraftService;
import com.jamesdpeters.chestsplusplus.autocraft.CraftingBackend;
import com.jamesdpeters.chestsplusplus.chestlink.ChestLinkService;
import com.jamesdpeters.chestsplusplus.chestlink.LidAnimator;
import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.display.DisplayLayout.Surface;
import com.jamesdpeters.chestsplusplus.display.DisplayService;
import com.jamesdpeters.chestsplusplus.filter.FilterCodec;
import com.jamesdpeters.chestsplusplus.filter.FilterService;
import com.jamesdpeters.chestsplusplus.filter.ItemGrouping;
import com.jamesdpeters.chestsplusplus.link.Checks;
import com.jamesdpeters.chestsplusplus.link.GroupActions;
import com.jamesdpeters.chestsplusplus.link.GroupLimits;
import com.jamesdpeters.chestsplusplus.link.Handlers;
import com.jamesdpeters.chestsplusplus.link.LinkItem;
import com.jamesdpeters.chestsplusplus.link.LinkService;
import com.jamesdpeters.chestsplusplus.link.ProtectionCheck;
import com.jamesdpeters.chestsplusplus.ui.UiService;
import com.jamesdpeters.chestsplusplus.ui.menu.MenuListener;
import org.bukkit.block.Chest;
import org.bukkit.plugin.java.JavaPlugin;

/** The feature services, built once on enable before the database opens. */
public record Features(Handlers handlers, DisplayService displays, Checks checks, ProtectionCheck protection, LinkService links, LinkItem linkItem,
        AutoCraftService autoCraft,
        ChestLinkService chestLinks, GroupActions actions, MenuListener menus, UiService ui, FilterService filters) {

    public static Features create(Services services) {
        JavaPlugin plugin = services.plugin();
        Handlers handlers = new Handlers();
        DisplayService displays = new DisplayService(plugin, services.groups(), services.nodes(), services::settings, handlers::of);
        services.shutdown().add("displays", displays::despawnAll);
        displays.surfaces(block -> block.getState(false) instanceof Chest ? Surface.CHEST : Surface.FULL_BLOCK);
        Checks checks = new Checks(services);
        ProtectionCheck protection = new ProtectionCheck();
        LinkService links = new LinkService(services, displays, handlers, checks, new GroupLimits(services.groups(), services::settings), protection);
        LinkItem linkItem = new LinkItem(plugin);

        AutoCraftService autoCraft = new AutoCraftService(services, displays, checks, CraftingBackend.bukkit());
        ChestLinkService chestLinks = new ChestLinkService(services, autoCraft::inventoryChanged,
                new LidAnimator(services.nodes(), services::settings));
        handlers.register(chestLinks);
        handlers.register(autoCraft);
        handlers.requireAll();

        GroupActions actions = new GroupActions(services, links, handlers, checks, chestLinks::setSortMode);
        MenuListener menus = new MenuListener(services.mainThread());
        UiService ui = new UiService(services, handlers, checks, actions, menus);
        FilterService filters = new FilterService(plugin, new FilterCodec(plugin), ItemGrouping.fromServerTags(), services::settings);
        services.shutdown().add("filter displays", filters::despawnAll);
        services.groups().listen(displays::requestUpdate);
        return new Features(handlers, displays, checks, protection, links, linkItem, autoCraft, chestLinks, actions, menus, ui, filters);
    }
}
