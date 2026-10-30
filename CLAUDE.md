# CLAUDE.md

**Type 95 Addition**: handoff notes for whoever (human or Claude) works on this next. Written 2026-10-23 (1.0.0), updated 2026-10-24 (1.1.0: the VFX/power/sound/animation overhaul).
If the code disagrees with this file, the code wins.

## What this is

An unofficial addon for rimuru.dev's **Type 95 Sniper Rifle** (mod id `tanya_demo`, CurseForge project 1759670,
All Rights Reserved; needs **Rimulib**, CurseForge 1753907). It adds a second ammo type, the **Lightning Formula**:

- **R** (with the rifle in the main hand) switches between the rifle's own Explosion Formula and the Lightning Formula.
- In Lightning mode, hold right click: lightning gathers round the gun in 4 stages (static -> crawling arcs -> ball
  lightning + ground arcs -> storm crown rings), the view zooms in. Let go: a beam leaves the muzzle and the strike
  comes down on the crosshair (main bolt + up to 16 more around it, damage/knock-back over the radius, thunderclap
  blast from stage 3, Lichtenberg scorch scars of glass/magma/blackstone, fires).
- **J** opens the settings: power 0.1-20x, damage 2-1000 or True Erasure, bolts 1-16, fire / terrain / blast,
  colour (6 palettes), screen flash, camera shake, zoom. Client file `config/type95_addition-client.json`.
- Server limits: `config/type95_addition-server.json` (charge time, cooldowns, range, max power/damage/bolts, which
  switches players may use, survival on/off). Re-read when a world/server starts.

Mod id `type95_addition`, package `dev.type95addition`. Two builds: **Fabric 1.21.11** (the root project) and
**Forge 1.20.1** (`forge-1.20.1/`, since 2026-10-30; the same jar runs on NeoForge 1.20.1). Those are all the
loaders/versions Type 95 has: its two releases (1.0.1 CurseForge file 9289577, 1.0.2 file 9295725) are each one jar
for Fabric 1.21.11 + Forge/NeoForge 1.20.1. Both builds were run (harness) against both Type 95 versions.

## 1.1.0 (2026-10-24): what changed and why

The user compared 1.0.0 with Type 95's own Explosion Formula and found ours weak ("max on ours is useless") and plain.
Measured (harness `-Dt95.clienttest.og=true` fires the original by reflection, dev only): at 1x full charge the
original kills within 110 blocks, carves a 46-block crater, and covers the screen with shader effects (bloom, ground
formula circle, rune dome, dust cloud). So 1.1.0:

- **Power** 0.1-**20x** (server `max_power` default 20; 1.0.0 configs with 10 are migrated via `config_version`).
  `LightningTuning`: radius 15+110*scale (1x: 125, 10x: 1115, 20x: 2215; original 110/1100), shock wave 3.5x that,
  crater min(6+40*scale, `max_crater_radius` 420) (original caps 256), volley bolts scale with sqrt(scale), a storm
  of follow-up bolts (up to 12 s, max 5/s, half of them chain to living things). The shooter is spared
  (`hurt_shooter`).
- **Crater** (`world/Crater`): Rimulib's streamed `Carving` (what the original uses), chunk tickets
  `type95_addition:crater`; fused floor (blackstone/basalt/magma, calcite+glass fulgurite streaks, crying obsidian core).
  `Scorch` scars now start at the crater rim and run up to 240 blocks.
- **Shaders** through Rimulib's `PostFxRenderer` (`client/fx/StormFx`): `shaders/fx/t95_charge.fsh` (muzzle orb,
  its light on the ground, world dimming, storm gathering overhead), `t95_strike.fsh` (storm clouds over the target lit
  by the bolts, bolt light columns, terrain lighting, flash, plasma dome, shock ring + wall + refraction, crater
  cracks glowing, the beam), `composite.fsh` (ours: Rimulib needs one per namespace), helpers in `t95.glsl`.
  Positions are camera-relative. ScreenFx (bloom, flash, aberration, zoom blur, ripple, vignette) set in
  `ChargeFx.passes`/`StrikeFx.passes`. About 1 ms GPU per frame on the user's PC.
- **Sounds**: 14 synthesised oggs (`dev/art/make_sounds.py`, numpy + ffmpeg), `AddonSounds`. Thunder is delayed
  by distance (17 blocks/tick).
- **Animations** (`client/anim/RifleAnim`): first person (`ItemInHandRendererMixin`: rifle comes in, shakes with
  the charge, recoil kick), third person (`PlayerModelMixin`, priority 1500: *nudges* Type 95's own rifle pose -
  replacing it made the rifle point straight up), view kick on firing.
- First-person particle placement follows the aim animation (`ChargeFx.gun`).

## 1.2.0 (2026-10-24): scope, marking, fixes

- **Scope** (J screen, `scope`, off by default - the user likes charging without it): first person while charging,
  `client/Scope` (1.5-12x, scroll via `MouseHandlerMixin`, hand hidden and mouse slowed through Rimulib's
  `ClientHooks.hideHand/limitTurn`), overlay in `AdditionHud.scope` (vanilla crosshair hidden while it's up).
- **Marking** (`marking`, on by default; "hold right + left click" is the feature the user really wanted):
  `client/Marking` (left click / hold while charging; `MinecraftMixin` blocks attacking meanwhile), `MarkPayload`,
  `LightningCasting.mark` (the strike lands on the mark: a mob (followed), block, or under a sky point; no mark =
  the crosshair as before). Looks: `fx/MarkFx` + `shaders/fx/t95_mark.fsh` (ground circle, the strike's reach ring
  growing with the charge, beacon) and a HUD marker.
- Fixed "effects following you": the beam's start is frozen at the shot (`StrikeFx.Strike.muzzle`), the crater-crack
  noise is in target-relative coordinates (it was camera-relative and slid as you walked), the charge's storm is
  anchored where the charge began (`ChargeFx.ANCHORS`).
- A bit cooler: a formula circle in the sky over the strike and one burned into the ground (`Sigil` uniform), embers.
- Harness: `-Dt95.clienttest.scope=true`, `-Dt95.clienttest.mark=true`.

## Forge 1.20.1 build (`forge-1.20.1/`, 2026-10-30)

A separate Gradle project (ModDevGradle legacy, Forge 47.4.10, Java 17, parchment), set up like The Shooting Star
Addition's forge-1.20.1 branch. Its Java is a ported copy of `src/main/java` (**every change must be made in both**);
the assets are shared (`srcDir '../src/main/resources'`), plus its own `pack.mcmeta`, mixin config (JAVA_17, refmap)
and `shaders/fx/depth_copy.fsh` (Rimulib's 1.20.1 renderer wants one per namespace). Differences from Fabric:

- `Type95Addition` is the `@Mod`; registers sounds/particle in `RegisterEvent` (plain fields, no RegistryObjects),
  `TicketType.create` + `addRegionTicket(T, pos, 0, pos)`, and calls **`Rimulib.register(MOD_ID, Type95Addition.class)`**:
  without it Rimulib looks for our shaders from its own module (Forge mods are separate modules) and finds nothing
  ("missing shader rimulib:t95_charge.fsh", then its GPU-incompatible screen).
- `net/AddonNetwork`: one `SimpleChannel` (`acceptMissingOr`); payloads are records with `write`/`read`;
  server-to-client packets go to `AddonNetwork.client` (set by `AdditionClient`); `client/ClientNet` sends to the server.
- `client/AdditionClient.init(modBus)`: zoom, camera shake and scope scroll are Forge events (`ViewportEvent.ComputeFov`,
  `ComputeCameraAngles`, `InputEvent.MouseScrollingEvent`), HUD is a gui overlay, crosshair hidden via
  `RenderGuiOverlayEvent.Pre`. Mixins left: `MinecraftMixin`, `ItemInHandRendererMixin`, `PlayerModelMixin` (1.20.1
  `setupAnim(LivingEntity,FFFFF)`; re-copies sleeves/jacket/hat from arms/body/head at the end).
- 1.20.1's flash particle can't be coloured: `StrikeFx` draws a big `ArcParticle` dot instead.
- Build: `JAVA_HOME` = JDK 21 (`C:\Program Files\Java\jdk-21.0.11`), `./gradlew --no-daemon build` ->
  `build/libs/type95_addition-<ver>.jar` (SRG names, `MixinConfigs` in the manifest). `forge-1.20.1/libs/` holds
  Type 95's jar and `rimulib-*-forge.jar` (never committed); the build copies them to `build/dev-mods` with empty
  refmaps for the dev game (their mixins use SRG names without refmaps). `./gradlew runClientTest` as on Fabric (no
  `og` mode); `-Pneoforge` runs on NeoForge 1.20.1 (47.1.106). Rimulib's own logs: `run/clienttest/*.log`.

## The one design rule

**Never touch Type 95's classes** (Rimulib's are fine: readable API, a hard dependency since 1.1.0). They are obfuscated (`cyou.rimuru.tanya.a/b/c...`) and will be renamed in later
releases. The addon only knows the rifle's item id `tanya_demo:type95_rifle`. In Lightning mode
`client/mixin/MinecraftMixin` cancels `Minecraft.startUseItem` (right click), so the rifle is never "used" and none of
Type 95's charge/scope/shot code runs (all of it waits for `isUsingItem() && useItem is the rifle`). The addon's own
charge (`client/ChargeClient` + `world/LightningCasting`) and strike (`world/LightningStrike`, `world/Scorch`) replace
it. Type 95's HUD still shows its own bits while the rifle is held; that is fine.

## Code map

- `Type95Addition` (main), `LightningTuning` (a player's settings + all the strike's numbers: charge, stage, scale,
  radius, bolt count, damage, blast), `ServerConfig` (JSON, `_help_*` keys explain each option).
- `net/`: `ChargePayload` (C2S start/release/cancel), `TunePayload` (C2S settings), `SettingsPayload` (S2C limits on
  join), `ChargeSyncPayload` (S2C who is charging, for other players' effects), `StrikePayload` (S2C beam),
  `BoltPayload` (S2C each bolt), `CooldownPayload`.
- `world/`: server. Strike timeline: tick 0 beam, tick 3 (`MAIN`) main bolt + area damage + blast + scorch start,
  then a bolt every 2 ticks. Scorch does 240 columns/tick, nearest first, only with mobGriefing on.
- `client/`: `ChargeClient` (local state, keys), `AdditionHud`, `LightningScreen`, `ClientConfig`, `Palette`;
  `client/fx/`: `ArcParticle` (full-bright dot, the only particle type: `type95_addition:arc`), `Arcs` (jagged
  arcs/rings out of dots), `ChargeFx` (the 4 stages + sounds), `ChargeSound` (hum), `StrikeFx` (beam, sky bolts in
  the palette colour over the vanilla ones, shock ring, ground arcs, flash, shake). Mixins: `MinecraftMixin`,
  `AbstractClientPlayerMixin` (zoom), `CameraMixin` (shake).
- Sounds are all vanilla (beacon hum, lightning impact/thunder, trident thunder); no custom audio yet.

## Building and testing

- `./gradlew --no-daemon build` with `JAVA_HOME` = JDK 25 (`C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot`;
  Loom 1.18.4 needs it). Jar: `build/libs/type95_addition-<ver>.jar`.
- The game itself must run on Java 21 (the build's toolchain): LWJGL in 1.21.11 can't init GLFW on 25.
- Dev runs load `libs/dev/*.jar`: Type 95's jar and Rimulib's 1.21.11 inner jar
  (`unzip -j rimulib-*-fabric.jar 'META-INF/jars/*1.21.11*' -d libs/dev`). `libs/*.jar` is never committed or shipped.
- `./gradlew runClientTest` (from PowerShell; options `-Dt95.clienttest.power=3 -Dt95.clienttest.palette=violet
  -Dt95.clienttest.bolts=12`): `src/clienttest` makes a flat world, charges through all stages, fires at 8 pigs,
  screenshots to `run/clienttest/screenshots/t95_*.png`, logs `[T95TEST]` lines (pigs left, blocks scorched) and quits.
  `run/clienttest/options.txt` needs `onboardAccessibility:false` or it stalls on the first-launch screen.

## Pitfalls already paid for

- Never ask for a key's name (`KeyMapping.getTranslatedKeyMessage()`) during mod init: it calls GLFW before the game
  starts it, and the game crashes at launch ("GLFW error before init").
- Git Bash on Windows: long heredocs with quotes fail to parse; write files with the editor instead (or a .py file).
- The real mouse over the test window turns the view: the harness re-locks the aim every tick (`holdAim`).
- Shader light terms need squared falloff and modest gains: `lightFalloff` has a long tail, and 2-4x gains washed
  the whole screen white. Rimulib's bloom only picks up what a pass *adds* (plus a `synthetic` knee), keep
  `synthetic` low (0.15-0.25) or the daytime sky blooms.
- Darkening Minecraft's own clouds to near-black makes ugly black blocks: tint them instead.
