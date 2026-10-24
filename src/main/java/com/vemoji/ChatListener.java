package com.vemoji;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Converts :shortcodes: in the FINISHED chat line.
 *
 * The message itself is left untouched, so LuckPerms / LPC / EssentialsXChat / other chat
 * formatters see plain text and cannot flatten the textures away. We wrap whatever renderer
 * is set when all other plugins are done (MONITOR priority), so prefixes, suffixes, nicknames
 * and the message are all converted. Permissions of the sender are respected.
 */
public final class ChatListener implements Listener {

    private final VEmoji plugin;

    public ChatListener(VEmoji plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!event.getPlayer().hasPermission("vemoji.use")) {
            return;
        }
        ChatRenderer previous = event.renderer();
        event.renderer((source, sourceDisplayName, message, viewer) ->
                plugin.parser().applyIfNeeded(previous.render(source, sourceDisplayName, message, viewer), source));
    }
}
