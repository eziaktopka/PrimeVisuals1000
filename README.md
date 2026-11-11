# Prime Visuals - Textures Manager (Fabric 1.21.4)

Client-side mod. Press **Right Shift** in game to open the manager.

Built-in: 12 colour variants (Red, Orange, Yellow, Green, Cyan, Blue, Purple, Pink, Black & White,
Dark, Neon, Inverted) are generated from the vanilla textures and show up as "Prime - <name>"
even with no packs installed.

1. (Optional) Put extra texture packs (.zip or folders) into `.minecraft/resourcepacks/`.
2. Open the GUI, pick a category on the left (Totem, Crystals, Anchor, Gapple, Sword...).
3. Click a pack's thumbnail to use its texture for that category. Click again to go back to Vanilla.
4. Close the GUI - the mod builds `resourcepacks/PrimeVisuals_Output`, enables it and reloads resources.

LOOK button (top right): 6 styles (Prime, Minecraft, Classic, Modern, Glass, Flat), accent colour,
window opacity, optional background image (`config/primevisuals_bg.png`).
SETTINGS button: grid size, rescan packs.

EFFECTS button: auras (Christmas, Halloween, Colorful, Fire, Ice, Galaxy, Love, Sakura),
hit effects and kill effects (particles, client-side only), ground ring around you and around
the player you fight (7 colours) and a Shockwave kill effect (expanding rings + lightning sparks). Aura is shown in third person (F5).

Hit sound: EXTRAS tab -> "Hit Sound" category (TikTok tick from the video, Blip, Bump); plays when your hit lands.
Kill sound: EXTRAS tab -> "Kill Sound" category (click a sound to select + preview it; also in the EFFECTS window): TikTok (chiptune dyad, default), Coin, 1-Up or Stomp (Mario-style). Plays whenever a kill is detected.
Files: assets/primevisuals/sounds/kill*.ogg - replace them with your own mono OGG if you like.

Training bot: type /bot spawn to create a dummy where you look (/bot clear removes them, there is also a
Spawn bot button in EFFECTS). It only exists on your client, stands still, has 20 HP and dies in a few hits;
hitting it sends nothing to the server, so you can use it anywhere to test your hit/kill effects and sounds.

Silent lightning bolt after a kill (EFFECTS -> KILL LIGHTNING BOLT): strikes down from the sky, branches, flicker,
ground flash and a faint screen flash; 5 colours or Off.
Box around target: thin glowing hitbox around the player you fight (toggle in EFFECTS).
Ring style Swoosh (default): wide crescent swooshes tapering at both ends, circling the body. Also Ground circle, Comets,
Ground + swoosh.
Rings are drawn as glowing ribbons (ground circles + orbiting comets).

F over a thumbnail = favorite. Presets tab saves/loads your whole selection.
Config: `config/primevisuals.json`.

## Build
Push to GitHub - the workflow in `.github/workflows/build.yml` builds the jar
(Actions -> latest run -> Artifacts -> prime-visuals-mod). Or locally: `gradle build` (Java 21).
