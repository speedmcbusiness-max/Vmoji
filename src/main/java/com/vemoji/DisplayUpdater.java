package com.vemoji;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Periodically rewrites :shortcodes: in the tab list (names, header, footer) and in
 * scoreboards (objective titles, team prefixes/suffixes = sidebar lines, tab ranks and nametags).
 * Runs on the main thread.
 */
public final class DisplayUpdater implements Runnable {

    private final VEmoji plugin;

    public DisplayUpdater(VEmoji plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        boolean tabNames = plugin.getConfig().getBoolean("display.tab-list", true);
        boolean tabHeader = plugin.getConfig().getBoolean("display.tab-header-footer", true);
        boolean boards = plugin.getConfig().getBoolean("display.scoreboard", true);
        if (!tabNames && !tabHeader && !boards) {
            return;
        }

        EmojiParser parser = plugin.parser();
        Set<Scoreboard> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (tabNames) {
                    Component name = player.playerListName();
                    Component replaced = parser.applyIfNeeded(name, null);
                    if (replaced != name) {
                        player.playerListName(replaced);
                    }
                }
                if (tabHeader) {
                    Component header = player.playerListHeader();
                    Component footer = player.playerListFooter();
                    Component newHeader = parser.applyIfNeeded(header, null);
                    Component newFooter = parser.applyIfNeeded(footer, null);
                    if (newHeader != header || newFooter != footer) {
                        player.sendPlayerListHeaderAndFooter(
                                newHeader == null ? Component.empty() : newHeader,
                                newFooter == null ? Component.empty() : newFooter);
                    }
                }
                if (boards) {
                    seen.add(player.getScoreboard());
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().fine("Display update failed for " + player.getName() + ": " + ex.getMessage());
            }
        }

        for (Scoreboard board : seen) {
            updateBoard(board, parser);
        }
    }

    private void updateBoard(Scoreboard board, EmojiParser parser) {
        for (Objective objective : board.getObjectives()) {
            try {
                Component title = objective.displayName();
                Component replaced = parser.applyIfNeeded(title, null);
                if (replaced != title) {
                    objective.displayName(replaced);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().fine("Objective update failed: " + ex.getMessage());
            }
        }
        for (Team team : board.getTeams()) {
            try {
                Component prefix = team.prefix();
                Component newPrefix = parser.applyIfNeeded(prefix, null);
                if (newPrefix != prefix) {
                    team.prefix(newPrefix);
                }
                Component suffix = team.suffix();
                Component newSuffix = parser.applyIfNeeded(suffix, null);
                if (newSuffix != suffix) {
                    team.suffix(newSuffix);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().fine("Team update failed: " + ex.getMessage());
            }
        }
    }
}
