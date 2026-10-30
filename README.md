# Type 95 Addition

An unofficial addon for rimuru.dev's **[Type 95 Sniper Rifle](https://www.curseforge.com/minecraft/mc-mods/type-95-sniper-rifle)**.
It gives the rifle a second ammo type: the **Lightning Formula**.

- **R** (rifle in the main hand) switches between the rifle's own Explosion Formula and the Lightning Formula.
- **Hold right click**: lightning gathers round the gun in four stages, from static to a crown of storm rings.
- **Left click while charging** marks where the strike lands: a mob (followed until the shot), a block or a spot in
  the sky.
- **Let go**: a beam leaves the muzzle and the storm comes down on the target. You get the main bolt, a volley around
  it, a thunderclap shock wave, a fused crater and Lichtenberg scars burnt into the ground, and a storm that keeps
  striking for a few seconds.
- **J** opens the settings: power 0.1-20x, damage 2-1000 or True Erasure, bolts 1-16, fire / terrain / blast,
  colour (6 palettes), scope, marking, screen flash, camera shake and zoom.

> ⚠️ It permanently changes your world (craters, scorched land, fires). Back up any world you care about.
> Photosensitivity warning: bright flashes.

## Downloads

Get the jar for your loader from [Releases](https://github.com/Adamko-10/Type-95-Sniper-Rifle-Addition/releases).

| Loader | Minecraft | Jar |
|---|---|---|
| Fabric | 1.21.11 | `Type 95 Addition <version> [Fabric 1.21.11].jar` |
| Forge or NeoForge | 1.20.1 | `Type 95 Addition <version> [Forge 1.20.1].jar` (the same jar for both) |

Works with Type 95 1.0.1 and 1.0.2.

## Requirements

- **Type 95 Sniper Rifle** 1.0.1 or newer
- **[Rimulib](https://www.curseforge.com/minecraft/mc-mods/rimulib)** 1.1.1 or newer (Type 95 needs it too)
- Fabric: Fabric Loader 0.17+ and **Fabric API**
- Forge 47.1+ or NeoForge 47.1+ (1.20.1)
- Multiplayer: install it on the server **and** every client.

## Config

- `config/type95_addition-client.json`: your own settings (the J screen writes them).
- `config/type95_addition-server.json`: the server's limits (charge time, cooldowns, range, max power / damage /
  bolts / crater size, which switches players may use, survival on or off). Each option has a `_help_` line. It is
  read again whenever a world or server starts.

## Building

- Fabric 1.21.11 (the root project): `./gradlew build` with JDK 25 to run Gradle (the game itself runs on Java 21).
- Forge 1.20.1 (`forge-1.20.1/`): `./gradlew build` with JDK 21.

Type 95 and Rimulib are not included. For dev runs, put their jars in `libs/dev/` (Fabric) or `forge-1.20.1/libs/`
(Forge); see `CLAUDE.md`.

## Credits

Type 95 Sniper Rifle and Rimulib are by [rimuru.dev](https://www.tiktok.com/@rimuru.dev), all rights reserved. This
addon is not made or endorsed by them and contains none of their code or assets: it only uses the rifle's item id and
Rimulib's public API.
