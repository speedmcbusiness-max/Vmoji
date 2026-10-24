package com.vemoji;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.BookMeta;

/** Signs, books and join/quit messages. */
public final class ExtraListeners implements Listener {

    private final VEmoji plugin;

    public ExtraListeners(VEmoji plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("display.signs", true) || !player.hasPermission("vemoji.use")) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            Component line = event.line(i);
            Component replaced = plugin.parser().applyIfNeeded(line, player);
            if (replaced != line) {
                event.line(i, replaced);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBook(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("display.books", true) || !player.hasPermission("vemoji.use")) {
            return;
        }
        BookMeta meta = event.getNewBookMeta();
        List<Component> pages = new ArrayList<>();
        boolean changed = false;
        for (Component page : meta.pages()) {
            Component replaced = plugin.parser().applyIfNeeded(page, player);
            changed |= replaced != page;
            pages.add(replaced);
        }
        if (changed) {
            meta.pages(pages);
            event.setNewBookMeta(meta);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getConfig().getBoolean("display.join-quit", true)) {
            event.joinMessage(plugin.parser().applyIfNeeded(event.joinMessage(), null));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getConfig().getBoolean("display.join-quit", true)) {
            event.quitMessage(plugin.parser().applyIfNeeded(event.quitMessage(), null));
        }
    }
}
