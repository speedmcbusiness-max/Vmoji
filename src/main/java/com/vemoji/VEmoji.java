package com.vemoji;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * VEmoji - turns :end_crystal: in chat into the real Minecraft texture,
 * using sprite object components (Minecraft 1.21.9+).
 */
public final class VEmoji extends JavaPlugin {

    private static final Pattern VERSION = Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?.*");

    private static volatile VEmoji instance;

    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile SpriteIndex index = SpriteIndex.EMPTY;
    private EmojiParser parser;
    private volatile FlagRegistry flags = FlagRegistry.empty();
    private String mcVersion;

    @Override
    public void onEnable() {
        instance = this;
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
        } catch (ClassNotFoundException ex) {
            getLogger().severe("VEmoji needs Paper (or a Paper fork like Purpur/Pufferfish). Spigot/CraftBukkit are not supported. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        mcVersion = Bukkit.getMinecraftVersion();
        getLogger().info("Detected Minecraft " + mcVersion);
        if (!isSupported(mcVersion)) {
            getLogger().severe("VEmoji requires Minecraft 1.21.9 or newer; detected " + mcVersion + ". Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        loadFlags();
        parser = new EmojiParser(this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ExtraListeners(this), this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                PapiHook.register(this);
                getLogger().info("PlaceholderAPI found: %vemoji_...% placeholders registered (for TAB etc.).");
            } catch (Throwable ex) {
                getLogger().warning("Could not register PlaceholderAPI expansion: " + ex);
            }
        }

        long interval = Math.max(5L, getConfig().getLong("display.update-ticks", 20L));
        Bukkit.getScheduler().runTaskTimer(this, new DisplayUpdater(this), 40L, interval);

        PluginCommand command = getCommand("vemoji");
        if (command != null) {
            VEmojiCommand handler = new VEmojiCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        Path cache = cacheFile();
        if (Files.exists(cache)) {
            try {
                index = SpriteIndex.load(cache);
                getLogger().info("Loaded " + index.size() + " emoji from cache.");
            } catch (IOException ex) {
                getLogger().warning("Could not read sprite cache: " + ex.getMessage());
            }
        }
        if (index.isEmpty()) {
            refreshIndex(null);
        }
    }

    @Override
    public void onDisable() {
        instance = null;
    }

    /** The running plugin instance, or null when VEmoji is not enabled. */
    public static VEmoji get() {
        return instance;
    }

    public SpriteIndex index() {
        return index;
    }

    public EmojiParser parser() {
        return parser;
    }

    public FlagRegistry flags() {
        return flags;
    }

    /** Loads the flag shortcodes (custom player-head textures, no resource pack). */
    private void loadFlags() {
        try (java.io.InputStream in = getResource("flags.tsv")) {
            if (in != null) {
                flags = FlagRegistry.load(in);
                getLogger().info("Loaded " + flags.size() + " flag shortcodes.");
            }
        } catch (Exception ex) {
            getLogger().warning("Could not load flags: " + ex.getMessage());
        }
    }

    private Path cacheFile() {
        return getDataFolder().toPath().resolve("sprite-index-" + mcVersion + ".tsv");
    }

    /** Rebuilds the sprite list asynchronously. initiator may be null (console log only). */
    public void refreshIndex(CommandSender initiator) {
        if (!refreshing.compareAndSet(false, true)) {
            notify(initiator, "A refresh is already running.", NamedTextColor.YELLOW);
            return;
        }
        notify(initiator, "Building emoji list from the Minecraft client jar...", NamedTextColor.GRAY);

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                boolean manual = "MANUAL".equalsIgnoreCase(getConfig().getString("population.mode", "AUTOMATIC"));
                Path jar = getDataFolder().toPath().resolve("client.jar");
                boolean temporary = false;

                if (manual) {
                    if (!Files.exists(jar)) {
                        throw new IOException("MANUAL mode: place the " + mcVersion + " client jar at " + jar);
                    }
                } else {
                    jar = getDataFolder().toPath().resolve("client-" + mcVersion + ".jar.tmp");
                    SpriteIndexBuilder.downloadClientJar(mcVersion, jar);
                    temporary = true;
                }

                List<SpriteRef> sprites;
                try {
                    sprites = SpriteIndexBuilder.fromClientJar(jar);
                } finally {
                    if (temporary) {
                        Files.deleteIfExists(jar);
                    }
                }

                SpriteIndex built = SpriteIndex.of(sprites);
                if (built.isEmpty()) {
                    throw new IOException("No sprites found in client jar.");
                }
                built.save(cacheFile());
                index = built;
                getLogger().info("Emoji list ready: " + built.size() + " shortcodes.");
                notify(initiator, "Emoji list ready: " + built.size() + " shortcodes.", NamedTextColor.GREEN);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                getLogger().log(java.util.logging.Level.SEVERE, "Failed to build emoji list", ex);
                notify(initiator, "Failed to build emoji list: " + ex.getMessage(), NamedTextColor.RED);
            } finally {
                refreshing.set(false);
            }
        });
    }

    private void notify(CommandSender sender, String message, NamedTextColor color) {
        if (sender != null) {
            sender.sendMessage(Component.text(message, color));
        }
    }

    static boolean isSupported(String version) {
        if (version == null) {
            return false;
        }
        Matcher m = VERSION.matcher(version.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) {
            return false;
        }
        int major = part(m.group(1));
        int minor = part(m.group(2));
        int patch = part(m.group(3));
        if (major != 1) {
            return major > 1;
        }
        if (minor != 21) {
            return minor > 21;
        }
        return patch >= 9;
    }

    private static int part(String s) {
        return s == null ? 0 : Integer.parseInt(s);
    }
}
