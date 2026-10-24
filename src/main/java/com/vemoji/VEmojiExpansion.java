package com.vemoji;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI expansion "vemoji". Output is the text syntax understood by TAB
 * (and other MiniMessage-style plugins) for sprites and heads.
 *
 *   %vemoji_end_crystal%         sprite
 *   %vemoji_respawn_anchor%      random face, %vemoji_respawn_anchor3% exact face
 *   %vemoji_flag_us%             country flag head
 *   %vemoji_head%                head of the player the placeholder is shown for
 *   %vemoji_head_Notch%          head by name (client shows the skin of that player, SkinsRestorer skins included)
 *   %vemoji_parse_TEXT%          converts every :shortcode: inside TEXT
 *                                (nesting works: %vemoji_parse_{luckperms_prefix}%)
 */
public final class VEmojiExpansion extends PlaceholderExpansion {

    private static final Pattern SHORTCODE = Pattern.compile(":([A-Za-z0-9_./-]{1,64}):");

    private final VEmoji plugin;

    public VEmojiExpansion(VEmoji plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "vemoji";
    }

    @Override
    public String getAuthor() {
        return "VEmoji";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        String lower = params.toLowerCase(Locale.ROOT);

        if (lower.startsWith("parse_")) {
            return convertText(params.substring("parse_".length()));
        }
        if (lower.equals("head")) {
            String name = player == null ? null : player.getName();
            return name == null ? "" : headName(name);
        }
        if (lower.startsWith("head_")) {
            return headName(params.substring("head_".length()));
        }
        return convertOne(params); // null for unknown -> placeholder stays visible, easy to debug
    }

    /** One shortcode name (without colons) -> TAB syntax, or null if unknown. */
    private String convertOne(String name) {
        if (name.regionMatches(true, 0, "flag_", 0, 5)) {
            FlagRegistry.Flag flag = plugin.flags().get(name);
            if (flag != null) {
                return template("head-texture", "<head_texture:{hash}>").replace("{hash}", flag.hash());
            }
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) {
                return headName(online.getName());
            }
        }
        SpriteRef ref = plugin.parser().resolve(name);
        if (ref != null) {
            return template("sprite", "<sprite:\"{atlas}\":\"{sprite}\">")
                    .replace("{atlas}", ref.atlas())
                    .replace("{sprite}", ref.sprite());
        }
        return null;
    }

    private String convertText(String text) {
        Matcher matcher = SHORTCODE.matcher(text);
        return matcher.replaceAll(result -> {
            String replacement = convertOne(result.group(1));
            return Matcher.quoteReplacement(replacement == null ? result.group() : replacement);
        });
    }

    private String headName(String name) {
        return template("head-name", "<head:{name}>").replace("{name}", name);
    }

    private String template(String key, String fallback) {
        String value = plugin.getConfig().getString("placeholders." + key, fallback);
        // Older configs used syntax TAB does not understand; fall back to the correct one.
        if (value == null || value.contains("head:name:") || value.contains("{texture}")) {
            return fallback;
        }
        return value;
    }
}
