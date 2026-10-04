package com.jamesdpeters.chestsplusplus.ui.menu;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** A menu whose top rows show a page of entries, with previous/next buttons on the bottom row. */
public class PaginatedMenu extends Menu {

    /** One entry: its icon and click handler. */
    public record Entry(ItemStack icon, ClickHandler handler) {}

    private final List<Entry> entries;
    private final Component previous;
    private final Component next;
    private final int perPage;
    private int page;

    @SuppressWarnings("this-escape")
    public PaginatedMenu(int rows, Component title, List<Entry> entries, Component previous, Component next) {
        super(rows, title);
        this.entries = List.copyOf(entries);
        this.previous = previous;
        this.next = next;
        this.perPage = (rows - 1) * 9;
        render();
    }

    public int page() {
        return page;
    }

    public int pages() {
        return Math.max(1, (entries.size() + perPage - 1) / perPage);
    }

    public void showPage(int newPage) {
        page = Math.clamp(newPage, 0, pages() - 1);
        render();
    }

    private void render() {
        clear();
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < entries.size(); i++) {
            Entry entry = entries.get(start + i);
            set(i, entry.icon(), entry.handler());
        }
        int bottom = size() - 9;
        if (page > 0) set(bottom, named(Material.ARROW, previous), (player, click) -> showPage(page - 1));
        if (page < pages() - 1) set(bottom + 8, named(Material.ARROW, next), (player, click) -> showPage(page + 1));
    }

    static ItemStack named(Material material, Component name) {
        ItemStack item = ItemStack.of(material);
        item.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_NAME, name);
        return item;
    }
}
