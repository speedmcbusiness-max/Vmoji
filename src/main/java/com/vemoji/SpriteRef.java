package com.vemoji;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;

/**
 * A single sprite inside a texture atlas, e.g. atlas "minecraft:blocks",
 * sprite "minecraft:item/end_crystal".
 */
public record SpriteRef(String atlas, String sprite) {

    /** The 1.21.9+ object component that makes the client draw this sprite inline. */
    public Component toComponent() {
        return Component.object(ObjectContents.sprite(Key.key(atlas), Key.key(sprite)));
    }

    /** Parses "atlas@sprite". Returns null when malformed. */
    public static SpriteRef parseExplicit(String text) {
        int at = text.indexOf('@');
        if (at <= 0 || at == text.length() - 1) {
            return null;
        }
        try {
            String atlas = withNamespace(text.substring(0, at));
            String sprite = withNamespace(text.substring(at + 1));
            Key.key(atlas);
            Key.key(sprite);
            return new SpriteRef(atlas, sprite);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static String withNamespace(String id) {
        String trimmed = id.trim();
        return trimmed.indexOf(':') >= 0 ? trimmed : "minecraft:" + trimmed;
    }

    public static String stripNamespace(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}
