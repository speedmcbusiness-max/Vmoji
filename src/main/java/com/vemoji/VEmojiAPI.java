package com.vemoji;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Public API for other plugins: convert :shortcodes: in any component you send yourself
 * (titles, boss bars, action bars, dialogs, GUIs, holograms ...).
 *
 * <pre>
 *   Component c = VEmojiAPI.parse(Component.text("Hello :end_crystal: :flag_us:"));
 * </pre>
 */
public final class VEmojiAPI {

    private VEmojiAPI() {}

    /** @return the component with shortcodes replaced, or the input unchanged if VEmoji is not running. */
    public static Component parse(Component component) {
        return parse(component, null);
    }

    /** @param context player whose vemoji.head / vemoji.flags permissions apply; null = no restrictions */
    public static Component parse(Component component, Player context) {
        VEmoji plugin = VEmoji.get();
        if (plugin == null || plugin.parser() == null || component == null) {
            return component;
        }
        return plugin.parser().apply(component, context);
    }

    public static Component parse(String text) {
        return parse(Component.text(text), null);
    }
}
