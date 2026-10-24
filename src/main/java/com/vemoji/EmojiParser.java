package com.vemoji;

import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Replaces :shortcodes: inside a component:
 *  - :PlayerName:  -> that online player's head (uses their current skin, incl. SkinsRestorer)
 *  - :end_crystal: -> Minecraft atlas sprite
 */
public final class EmojiParser {

    private static final Pattern SHORTCODE = Pattern.compile(":([A-Za-z0-9_./-]{1,64}):");
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final VEmoji plugin;
    private volatile Map<String, String> aliases = Map.of();
    private volatile int maxPerMessage = 10;
    private volatile boolean headsEnabled = true;
    private volatile boolean headHat = true;
    private volatile boolean headUseSkinProperties = true;
    private volatile boolean headHover = true;
    private volatile boolean flagsEnabled = true;
    private volatile boolean flagHat = true;

    public EmojiParser(VEmoji plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        maxPerMessage = Math.max(1, plugin.getConfig().getInt("chat.max-per-message", 10));
        headsEnabled = plugin.getConfig().getBoolean("heads.enabled", true);
        headHat = plugin.getConfig().getBoolean("heads.show-hat-layer", true);
        headUseSkinProperties = plugin.getConfig().getBoolean("heads.use-skin-properties", true);
        headHover = plugin.getConfig().getBoolean("heads.hover-name", true);
        flagsEnabled = plugin.getConfig().getBoolean("flags.enabled", true);
        flagHat = plugin.getConfig().getBoolean("flags.show-hat-layer", true);

        Map<String, String> map = new HashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("aliases");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null && !value.isBlank()) {
                    map.put(key.toLowerCase(Locale.ROOT), value.trim());
                }
            }
        }
        aliases = Map.copyOf(map);
    }

    public SpriteRef resolve(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        String alias = aliases.get(lower);
        if (alias != null) {
            SpriteRef explicit = SpriteRef.parseExplicit(alias);
            if (explicit != null) {
                return explicit;
            }
            SpriteRef viaAlias = plugin.index().resolve(alias);
            if (viaAlias != null) {
                return viaAlias;
            }
        }
        return plugin.index().resolve(lower);
    }

    private Player findPlayer(String name) {
        if (!PLAYER_NAME.matcher(name).matches()) {
            return null;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) {
                return online;
            }
        }
        return null;
    }

    private Component headOf(Player target) {
        PlayerHeadObjectContents.Builder builder = ObjectContents.playerHead()
                .name(target.getName())
                .id(target.getUniqueId())
                .hat(headHat);

        if (headUseSkinProperties) {
            // The live profile holds the skin currently applied (SkinsRestorer writes its skin here),
            // so cracked players with a restored skin show correctly.
            for (ProfileProperty property : target.getPlayerProfile().getProperties()) {
                if ("textures".equals(property.getName())) {
                    builder.profileProperty(PlayerHeadObjectContents.property(
                            property.getName(), property.getValue(), property.getSignature()));
                }
            }
        }

        Component head = Component.object(builder.build());
        if (headHover) {
            head = head.hoverEvent(HoverEvent.showText(Component.text(target.getName())));
        }
        return head;
    }

    /**
     * One shortcode name (no colons) -> its component without hover text, or null if unknown.
     * Handy for copying into datapack dialogs, tellraw and other JSON text.
     */
    public Component single(String name, Player chatter) {
        Component result = null;
        boolean allowHeads = headsEnabled && (chatter == null || chatter.hasPermission("vemoji.head"));
        boolean allowFlags = flagsEnabled && (chatter == null || chatter.hasPermission("vemoji.flags"));

        if (allowHeads) {
            Player target = findPlayer(name);
            if (target != null) {
                result = headOf(target);
            }
        }
        if (result == null && allowFlags && name.regionMatches(true, 0, "flag_", 0, 5)) {
            result = plugin.flags().component(name, flagHat);
        }
        if (result == null) {
            SpriteRef ref = resolve(name);
            if (ref != null) {
                result = ref.toComponent();
            }
        }
        return result == null ? null : result.hoverEvent(null);
    }

    /** Cheap pre-check so we don't rebuild components that contain no :shortcode: at all. */
    public boolean mayContainShortcode(Component component) {
        return component != null && SHORTCODE.matcher(PlainTextComponentSerializer.plainText().serialize(component)).find();
    }

    /** Like apply(), but returns the very same instance when nothing changed (null-safe). */
    public Component applyIfNeeded(Component component, Player chatter) {
        if (!mayContainShortcode(component)) {
            return component;
        }
        Component result = apply(component, chatter);
        return result.equals(component) ? component : result;
    }

    /** @param chatter the sender (used for the vemoji.head permission); may be null */
    public Component apply(Component message, Player chatter) {
        AtomicInteger count = new AtomicInteger();
        int max = maxPerMessage;
        boolean allowHeads = headsEnabled && (chatter == null || chatter.hasPermission("vemoji.head"));
        boolean allowFlags = flagsEnabled && (chatter == null || chatter.hasPermission("vemoji.flags"));

        TextReplacementConfig config = TextReplacementConfig.builder()
                .match(SHORTCODE)
                .replacement((MatchResult match, TextComponent.Builder builder) -> {
                    if (count.get() >= max) {
                        return builder;
                    }
                    String name = match.group(1);

                    if (allowHeads) {
                        Player target = findPlayer(name);
                        if (target != null) {
                            count.incrementAndGet();
                            return headOf(target);
                        }
                    }

                    if (allowFlags && name.regionMatches(true, 0, "flag_", 0, 5)) {
                        Component flag = plugin.flags().component(name, flagHat);
                        if (flag != null) {
                            count.incrementAndGet();
                            return flag;
                        }
                    }

                    SpriteRef ref = resolve(name);
                    if (ref == null) {
                        return builder; // unknown shortcode: leave the text untouched
                    }
                    count.incrementAndGet();
                    ComponentLike sprite = ref.toComponent()
                            .hoverEvent(HoverEvent.showText(Component.text(":" + name.toLowerCase(Locale.ROOT) + ":")));
                    return sprite;
                })
                .build();

        return message.replaceText(config);
    }
}
