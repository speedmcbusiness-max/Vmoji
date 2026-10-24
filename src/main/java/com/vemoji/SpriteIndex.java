package com.vemoji;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps shortcodes (":end_crystal:" -> "end_crystal") to atlas sprites.
 *
 * Short names: "item/xxx" first, then "block/xxx". Full sprite paths such as
 * "block/stone" or "item/end_crystal" always work as well.
 */
public final class SpriteIndex {

    public static final SpriteIndex EMPTY = new SpriteIndex(List.of(), Map.of(), Map.of());

    /**
     * Numbered textures: block/respawn_anchor_side3, block/wheat_stage5, item/clock_12 ...
     * base = "respawn_anchor" / "wheat" / "clock", number = the face / stage / frame.
     */
    private static final Pattern NUMBERED =
            Pattern.compile("^(?:block|item)/(.+?)(?:_(?:side|stage))?_?(\\d+)$");
    private static final Pattern TRAILING_NUMBER = Pattern.compile("^(.+?)_?(\\d+)$");

    private final List<SpriteRef> sprites;
    private final Map<String, SpriteRef> names;
    private final Map<String, TreeMap<Integer, SpriteRef>> numbered;

    private SpriteIndex(List<SpriteRef> sprites, Map<String, SpriteRef> names, Map<String, TreeMap<Integer, SpriteRef>> numbered) {
        this.sprites = sprites;
        this.names = names;
        this.numbered = numbered;
    }

    public static SpriteIndex of(List<SpriteRef> entries) {
        List<SpriteRef> sorted = new ArrayList<>(entries);
        // The default "blocks" atlas is the safest one for the client, so it wins ties.
        sorted.sort(Comparator.comparingInt(s -> atlasRank(s.atlas())));

        Map<String, SpriteRef> names = new HashMap<>();
        for (String prefix : new String[] {"item/", "block/"}) {
            for (SpriteRef ref : sorted) {
                String path = SpriteRef.stripNamespace(ref.sprite());
                if (path.startsWith(prefix)) {
                    String rest = path.substring(prefix.length());
                    if (!rest.isEmpty() && rest.indexOf('/') < 0) {
                        names.putIfAbsent(rest, ref);
                    }
                }
            }
        }
        for (SpriteRef ref : sorted) {
            names.putIfAbsent(SpriteRef.stripNamespace(ref.sprite()), ref);
        }

        // Face aliases: X_bottom also answers to X_under / X_down, X_top also to X_up.
        for (Map.Entry<String, SpriteRef> entry : new ArrayList<>(names.entrySet())) {
            String key = entry.getKey();
            if (key.endsWith("_bottom")) {
                String stem = key.substring(0, key.length() - "_bottom".length());
                names.putIfAbsent(stem + "_under", entry.getValue());
                names.putIfAbsent(stem + "_down", entry.getValue());
            } else if (key.endsWith("_top")) {
                names.putIfAbsent(key.substring(0, key.length() - "_top".length()) + "_up", entry.getValue());
            }
        }

        // Numbered textures:
        //   :respawn_anchor0: ... :respawn_anchor4:  -> that exact face
        //   :respawn_anchor5:                        -> too high, shows the highest face (4)
        //   :respawn_anchor:                         -> a random face
        Map<String, TreeMap<Integer, SpriteRef>> numbered = new TreeMap<>();
        for (SpriteRef ref : sorted) {
            Matcher m = NUMBERED.matcher(SpriteRef.stripNamespace(ref.sprite()));
            if (m.matches()) {
                numbered.computeIfAbsent(m.group(1), k -> new TreeMap<>())
                        .putIfAbsent(Integer.parseInt(m.group(2)), ref);
            }
        }
        numbered.values().removeIf(variants -> variants.size() < 2);
        for (Map.Entry<String, TreeMap<Integer, SpriteRef>> entry : numbered.entrySet()) {
            for (Map.Entry<Integer, SpriteRef> variant : entry.getValue().entrySet()) {
                names.putIfAbsent(entry.getKey() + variant.getKey(), variant.getValue());
            }
        }
        return new SpriteIndex(Collections.unmodifiableList(sorted), Collections.unmodifiableMap(names),
                Collections.unmodifiableMap(numbered));
    }

    private static int atlasRank(String atlas) {
        return switch (atlas) {
            case "minecraft:blocks" -> 0;
            case "minecraft:items" -> 1;
            default -> 2;
        };
    }

    public SpriteRef resolve(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        SpriteRef direct = names.get(key);
        if (direct != null) {
            return direct;
        }

        // :respawn_anchor: -> random variant
        TreeMap<Integer, SpriteRef> variants = numbered.get(key);
        if (variants != null) {
            int pick = ThreadLocalRandom.current().nextInt(variants.size());
            return List.copyOf(variants.values()).get(pick);
        }

        // :respawn_anchor_side: -> random side face (when only numbered sides exist)
        if (key.endsWith("_side")) {
            TreeMap<Integer, SpriteRef> sides = numbered.get(key.substring(0, key.length() - "_side".length()));
            if (sides != null) {
                return List.copyOf(sides.values()).get(ThreadLocalRandom.current().nextInt(sides.size()));
            }
        }

        // :respawn_anchor5: (number too high) -> highest variant that exists
        Matcher m = TRAILING_NUMBER.matcher(key);
        if (m.matches()) {
            TreeMap<Integer, SpriteRef> found = numbered.get(m.group(1));
            if (found != null && m.group(2).length() <= 9) {
                int n = Integer.parseInt(m.group(2));
                Map.Entry<Integer, SpriteRef> floor = found.floorEntry(n);
                return floor != null ? floor.getValue() : found.firstEntry().getValue();
            }
        }
        return null;
    }

    public int size() {
        return names.size() + numbered.size();
    }

    public boolean isEmpty() {
        return names.isEmpty() && numbered.isEmpty();
    }

    /** Sorted copy of all valid shortcodes. */
    public Map<String, SpriteRef> sortedNames() {
        Map<String, SpriteRef> all = new TreeMap<>(names);
        for (Map.Entry<String, TreeMap<Integer, SpriteRef>> group : numbered.entrySet()) {
            all.putIfAbsent(group.getKey(), group.getValue().firstEntry().getValue()); // random, shown as first
        }
        return all;
    }

    // ---- cache -----------------------------------------------------------

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        List<String> lines = new ArrayList<>(sprites.size());
        for (SpriteRef ref : sprites) {
            lines.add(ref.atlas() + "\t" + ref.sprite());
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    public static SpriteIndex load(Path file) throws IOException {
        List<SpriteRef> refs = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int tab = line.indexOf('\t');
            if (tab > 0 && tab < line.length() - 1) {
                refs.add(new SpriteRef(line.substring(0, tab), line.substring(tab + 1)));
            }
        }
        return of(refs);
    }
}
