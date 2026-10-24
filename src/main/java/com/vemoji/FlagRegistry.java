package com.vemoji;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;

/**
 * Country flags, shown as custom player heads (no resource pack needed).
 * Each flag is a skin texture hosted on textures.minecraft.net.
 */
public final class FlagRegistry {

    public record Flag(String name, String hash, boolean primary, UUID id, String textureValue) {}

    private final Map<String, Flag> byName;

    private FlagRegistry(Map<String, Flag> byName) {
        this.byName = Collections.unmodifiableMap(byName);
    }

    public static FlagRegistry empty() {
        return new FlagRegistry(Map.of());
    }

    /** Reads lines of "shortcode TAB texture-hash TAB primary(1/0)". */
    public static FlagRegistry load(InputStream in) throws IOException {
        Map<String, Flag> map = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length < 2) {
                    continue;
                }
                String name = parts[0].trim();
                String hash = parts[1].trim();
                boolean primary = parts.length > 2 && parts[2].trim().equals("1");
                String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
                String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
                UUID id = UUID.nameUUIDFromBytes(("vemoji-flag-" + hash).getBytes(StandardCharsets.UTF_8));
                map.put(name, new Flag(name, hash, primary, id, value));
            }
        }
        return new FlagRegistry(map);
    }

    public Flag get(String name) {
        return byName.get(name.toLowerCase(Locale.ROOT));
    }

    public Component component(Flag flag, boolean hat) {
        PlayerHeadObjectContents contents = ObjectContents.playerHead()
                .id(flag.id())
                .hat(hat)
                .profileProperty(PlayerHeadObjectContents.property("textures", flag.textureValue()))
                .build();
        return Component.object(contents)
                .hoverEvent(HoverEvent.showText(Component.text(":" + flag.name() + ":")));
    }

    public Component component(String name, boolean hat) {
        Flag flag = get(name);
        return flag == null ? null : component(flag, hat);
    }

    public Map<String, Flag> sorted() {
        return new TreeMap<>(byName);
    }

    public int size() {
        return byName.size();
    }
}
