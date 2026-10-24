package com.vemoji;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Builds the sprite list from the vanilla client jar by reading
 * assets/minecraft/atlases/*.json and the textures they point to.
 */
public final class SpriteIndexBuilder {

    private static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String ATLAS_PREFIX = "assets/minecraft/atlases/";
    private static final String TEXTURE_PREFIX = "assets/minecraft/textures/";

    private SpriteIndexBuilder() {}

    /** Downloads the client jar for the given version into target. */
    public static void downloadClientJar(String mcVersion, Path target) throws IOException, InterruptedException {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        JsonObject manifest = fetchJson(http, MANIFEST_URL);
        String versionUrl = null;
        for (JsonElement el : manifest.getAsJsonArray("versions")) {
            JsonObject entry = el.getAsJsonObject();
            if (mcVersion.equals(entry.get("id").getAsString())) {
                versionUrl = entry.get("url").getAsString();
                break;
            }
        }
        if (versionUrl == null) {
            throw new IOException("Minecraft version " + mcVersion + " not found in Mojang version manifest.");
        }

        JsonObject versionJson = fetchJson(http, versionUrl);
        JsonObject client = versionJson.getAsJsonObject("downloads").getAsJsonObject("client");
        String jarUrl = client.get("url").getAsString();

        Files.createDirectories(target.getParent());
        HttpRequest request = HttpRequest.newBuilder(URI.create(jarUrl))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() / 100 != 2) {
            Files.deleteIfExists(target);
            throw new IOException("Client jar download failed with HTTP " + response.statusCode());
        }
    }

    public static List<SpriteRef> fromClientJar(Path jar) throws IOException {
        Set<String> textures = new HashSet<>();
        List<ZipEntry> atlasEntries = new ArrayList<>();

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }
                if (name.startsWith(TEXTURE_PREFIX) && name.endsWith(".png")) {
                    textures.add(name.substring(TEXTURE_PREFIX.length(), name.length() - 4));
                } else if (name.startsWith(ATLAS_PREFIX) && name.endsWith(".json")) {
                    atlasEntries.add(entry);
                }
            }

            Set<SpriteRef> result = new LinkedHashSet<>();
            for (ZipEntry atlasEntry : atlasEntries) {
                String file = atlasEntry.getName().substring(ATLAS_PREFIX.length());
                String atlasId = "minecraft:" + file.substring(0, file.length() - ".json".length());
                JsonObject atlas;
                try (Reader reader = new InputStreamReader(zip.getInputStream(atlasEntry), StandardCharsets.UTF_8)) {
                    atlas = JsonParser.parseReader(reader).getAsJsonObject();
                }
                collectAtlas(atlasId, atlas, textures, result);
            }
            return new ArrayList<>(result);
        }
    }

    private static void collectAtlas(String atlasId, JsonObject atlas, Set<String> textures, Set<SpriteRef> out) {
        JsonArray sources = atlas.getAsJsonArray("sources");
        if (sources == null) {
            return;
        }
        for (JsonElement el : sources) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject source = el.getAsJsonObject();
            if (!source.has("type")) {
                continue;
            }
            String type = SpriteRef.stripNamespace(source.get("type").getAsString());

            if (type.equals("directory") && source.has("source")) {
                String dir = SpriteRef.stripNamespace(source.get("source").getAsString());
                String prefix = source.has("prefix") ? source.get("prefix").getAsString() : "";
                String dirPrefix = dir + "/";
                for (String texture : textures) {
                    if (texture.startsWith(dirPrefix)) {
                        String sprite = prefix + texture.substring(dirPrefix.length());
                        out.add(new SpriteRef(atlasId, "minecraft:" + sprite));
                    }
                }
            } else if (type.equals("single") && source.has("resource")) {
                String resource = SpriteRef.stripNamespace(source.get("resource").getAsString());
                if (textures.contains(resource)) {
                    String sprite = source.has("sprite")
                            ? SpriteRef.stripNamespace(source.get("sprite").getAsString())
                            : resource;
                    out.add(new SpriteRef(atlasId, "minecraft:" + sprite));
                }
            }
            // filter / unstitch / paletted_permutations are ignored on purpose.
        }
    }

    private static JsonObject fetchJson(HttpClient http, String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
