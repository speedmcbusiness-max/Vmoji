# VEmoji

Type `:end_crystal:` in chat and the real Minecraft texture appears inline.

Requires **Paper 1.21.9 or newer** (works on 26.x) and players on a 1.21.9+ client.

## Build
```
mvn clean package
```
Needs JDK 21. Jar: `target/VEmoji-1.0.0.jar` -> drop it in `plugins/`.

## Use
- `:end_crystal:`, `:diamond_sword:`, `:golden_apple:`, `:stone:` ...
- `:PlayerName:` (an online player) shows their head, using their current skin (works with SkinsRestorer / cracked skins)
- `:flag_us:`, `:flag_nepal:`, `:flag_germany:` ... 240+ country flags as player heads (`/vemoji flags`). No resource pack needed.
- Full paths work too: `:block/stone:`, `:item/end_crystal:`
- `/vemoji list [filter] [page]` - browse every shortcode (click to copy)
- `/vemoji refresh` - rebuild the list, `/vemoji reload` - reload config
- Custom shortcodes in `config.yml` under `aliases`

First start downloads the Mojang client jar once (set `population.mode: MANUAL`
and drop it at `plugins/VEmoji/client.jar` for offline servers).

Permissions: `vemoji.use` and `vemoji.head` (default: everyone), `vemoji.admin` (default: op).

Flag head textures come from the Minecraft-Heads database mirror (TheLuca98/MinecraftHeads).

## Where it works
Chat, rank prefixes in chat, tab list (names, header, footer), scoreboard (title, team-based lines,
prefixes/suffixes), signs, books, join/quit messages. Toggle each under `display:` in `config.yml`.

Other plugins can convert their own components (titles, boss bars, dialogs, GUIs ...):
```java
Component c = com.vemoji.VEmojiAPI.parse(Component.text("Hi :end_crystal:"));
```

## TAB / PlaceholderAPI
TAB sends its own packets, so VEmoji can't edit TAB's text. Instead VEmoji adds placeholders that output
TAB's sprite/head syntax (needs PlaceholderAPI):
- `%vemoji_end_crystal%`, `%vemoji_respawn_anchor3%`, `%vemoji_flag_us%` - sprites / flags
- `%vemoji_head%` - head of the player it is shown for, `%vemoji_head_Notch%` - head by name
- `%vemoji_parse_TEXT%` - converts every `:shortcode:` inside TEXT, e.g. `%vemoji_parse_{luckperms_prefix}%`

TAB `config.yml` example: `tabprefix: "%vemoji_flag_us% %vemoji_head% "`.
If sprites show as plain text in TAB, set `minimessage-support: false` in TAB's config.
The syntax templates live under `placeholders:` in VEmoji's `config.yml`.

## Dialogs, datapacks, tellraw
VEmoji cannot edit dialogs sent by other plugins or datapacks. Get the JSON text for any shortcode instead:
`/vemoji json end_crystal` prints e.g. `{"type":"object","atlas":"minecraft:blocks","sprite":"minecraft:item/end_crystal"}`
(click to copy). Paste it into a datapack dialog, `/tellraw`, or any JSON text.
Plugin developers can call `VEmojiAPI.parse(component)` on the components they build for dialogs.

## LuckPerms / LPC / chat formatters
VEmoji converts the finished chat line (MONITOR priority) and leaves the raw message alone, so
LuckPerms prefixes/suffixes, LPC, EssentialsXChat and similar formatters work: `:flag_us: [VIP] Steve: hi :end_crystal:`.

## TAB example (heads + icons like big PvP servers)
`TAB-example/TAB-setup-example.yml` has ready-to-copy TAB config for head + icon tab names, header/footer and a sidebar with icons.
Templates use TAB's syntax: `<head:NAME>`, `<head_texture:HASH>`, `<sprite:"atlas":"sprite">`.
