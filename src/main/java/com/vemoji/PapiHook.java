package com.vemoji;

/** Isolated so PlaceholderAPI classes are only loaded when PlaceholderAPI is installed. */
final class PapiHook {

    private PapiHook() {}

    static void register(VEmoji plugin) {
        new VEmojiExpansion(plugin).register();
    }
}
