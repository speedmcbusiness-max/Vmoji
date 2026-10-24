package com.vemoji;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class VEmojiCommand implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 10;

    private final VEmoji plugin;

    public VEmojiCommand(VEmoji plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list", "search" -> list(sender, args);
            case "flags" -> listFlags(sender, args);
            case "json" -> json(sender, args);
            case "refresh" -> {
                if (!sender.hasPermission("vemoji.admin")) {
                    sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                } else {
                    plugin.refreshIndex(sender);
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("vemoji.admin")) {
                    sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                } else {
                    plugin.reloadConfig();
                    plugin.parser().reload();
                    sender.sendMessage(Component.text("VEmoji config reloaded.", NamedTextColor.GREEN));
                }
            }
            default -> {
                sender.sendMessage(Component.text("VEmoji", NamedTextColor.GOLD)
                        .append(Component.text(" - type :name: in chat, e.g. :end_crystal:", NamedTextColor.GRAY)));
                sender.sendMessage(Component.text("/vemoji list [filter] [page]  /vemoji flags [filter] [page]", NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("/vemoji json <name>  (text for dialogs / datapacks / tellraw)", NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("/vemoji refresh  /vemoji reload", NamedTextColor.YELLOW));
            }
        }
        return true;
    }

    private void list(CommandSender sender, String[] args) {
        SpriteIndex index = plugin.index();
        if (index.isEmpty()) {
            sender.sendMessage(Component.text("Sprite list is empty. Use /vemoji refresh.", NamedTextColor.RED));
            return;
        }

        String filter = "";
        int page = 1;
        for (int i = 1; i < args.length; i++) {
            if (i == args.length - 1 && args[i].matches("\\d+")) {
                page = Math.max(1, Integer.parseInt(args[i]));
            } else {
                filter = args[i].toLowerCase(Locale.ROOT);
            }
        }

        List<Map.Entry<String, SpriteRef>> matches = new ArrayList<>();
        for (Map.Entry<String, SpriteRef> entry : index.sortedNames().entrySet()) {
            if (filter.isEmpty() || entry.getKey().contains(filter)) {
                matches.add(entry);
            }
        }
        if (matches.isEmpty()) {
            sender.sendMessage(Component.text("No emoji match '" + filter + "'.", NamedTextColor.RED));
            return;
        }

        int pages = (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.min(page, pages);
        sender.sendMessage(Component.text("Emoji (" + matches.size() + ") page " + page + "/" + pages, NamedTextColor.GOLD));
        for (int i = (page - 1) * PAGE_SIZE; i < Math.min(matches.size(), page * PAGE_SIZE); i++) {
            Map.Entry<String, SpriteRef> entry = matches.get(i);
            String code = ":" + entry.getKey() + ":";
            sender.sendMessage(entry.getValue().toComponent()
                    .append(Component.text(" " + code, NamedTextColor.YELLOW))
                    .clickEvent(ClickEvent.copyToClipboard(code))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy " + code))));
        }
    }

    /** Prints the JSON text component of a shortcode, click to copy. */
    private void json(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /vemoji json <name>   e.g. /vemoji json end_crystal", NamedTextColor.YELLOW));
            return;
        }
        String name = args[1].replace(":", "");
        Player context = sender instanceof Player p ? p : null;
        Component component = plugin.parser().single(name, context);
        if (component == null) {
            sender.sendMessage(Component.text("Unknown emoji '" + name + "'.", NamedTextColor.RED));
            return;
        }
        String json = GsonComponentSerializer.gson().serialize(component);
        sender.sendMessage(Component.text("JSON for :" + name + ": (click to copy)", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(json, NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.copyToClipboard(json))
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy"))));
    }

    private void listFlags(CommandSender sender, String[] args) {
        String filter = "";
        int page = 1;
        for (int i = 1; i < args.length; i++) {
            if (i == args.length - 1 && args[i].matches("\\d+")) {
                page = Math.max(1, Integer.parseInt(args[i]));
            } else {
                filter = args[i].toLowerCase(Locale.ROOT);
            }
        }
        List<FlagRegistry.Flag> matches = new ArrayList<>();
        for (FlagRegistry.Flag flag : plugin.flags().sorted().values()) {
            boolean show = filter.isEmpty() ? flag.primary() : flag.name().contains(filter);
            if (show) {
                matches.add(flag);
            }
        }
        if (matches.isEmpty()) {
            sender.sendMessage(Component.text("No flags match '" + filter + "'.", NamedTextColor.RED));
            return;
        }
        int pages = (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.min(page, pages);
        sender.sendMessage(Component.text("Flags (" + matches.size() + ") page " + page + "/" + pages
                + "", NamedTextColor.GOLD));
        for (int i = (page - 1) * PAGE_SIZE; i < Math.min(matches.size(), page * PAGE_SIZE); i++) {
            FlagRegistry.Flag flag = matches.get(i);
            String code = ":" + flag.name() + ":";
            sender.sendMessage(plugin.flags().component(flag, true)
                    .append(Component.text(" " + code, NamedTextColor.YELLOW))
                    .clickEvent(ClickEvent.copyToClipboard(code)));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("list", "flags", "json"));
            if (sender.hasPermission("vemoji.admin")) {
                options.add("refresh");
                options.add("reload");
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            options.removeIf(s -> !s.startsWith(prefix));
            return options;
        }
        return List.of();
    }
}
