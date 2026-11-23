# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Type 95 Addition**: handoff notes for whoever (human or Claude) works on this next. Written 2026-10-23 (1.0.0), updated
2026-10-24 (1.1.0, 1.2.0), 2026-10-30 (Forge 1.20.1 port, GitHub, Homing Formula) and 2026-11-23 (Gauss Formula).
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

## 1.3.0 (2026-10-30): the Homing Formula

The user asked for a third ammo: 10 homing bullets, the hit one crushed flat and killed ("crushed by a homing
bullet"), red tint + magenta outline + vertical streaks (a Paper Mario screenshot), Infinite's (Sonic Forces) Phantom
Ruby look, fliers grounded with special animations for bosses.

- R now cycles Explosion -> Lightning -> Homing (`ClientConfig.ammo`, file key `ammo`; old `lightning_ammo` read once).
  Right click fires at once (`ChargePayload.HOMING`), 30-tick cooldown, no J-screen settings.
- `world/HomingShot`: targets = living things in a 35 degree cone in sight, nearest the crosshair first (no creative
  players, no allies); bullets simulated on the server and, from `HomingPayload`, on each client (`launch`/`steer`/
  `reaches` are shared). Hit -> `CrushPayload`: fliers slammed to the ground (12 ticks, bosses 30), held still
  (setPos every tick, no NoAI: a mob unloaded mid-crush would be saved with it), pressed (30 / 50 ticks), flat at
  `FLATTEN`, then `hurt` with damage type `homing_formula` (fallback `kill`, except a dragon already in DYING).
  Boss = wither, dragon or 150+ max health.
- `client/fx/HomingFx`: bullet trails with ruby cubes, streaks, the pressing cube, boss rings/chains, `t95_homing.fsh`
  (red tint from the nearest crush only, halo, streaks, glitch bands). Squash: Fabric `EntityRendererMixin` (render
  state data key + `outlineColor`) and `EntityRenderDispatcherMixin` (scale before the renderer's submit, so the
  dragon too); Forge `EntityRenderDispatcherMixin` + `EntityMixin.getTeamColor` + `MinecraftMixin.shouldEntityAppearGlowing`.
- Harness: `-Dt95.clienttest.homing=true` (pigs, a wither and a dragon in the cone; slimes are removed first).

### The Gauss Formula (2026-11-23, still 1.3.0: nothing of 1.3.0 is released yet)

The user's spec (a railgun round; named Gauss so it isn't confused with The Shooting Star's "Railgun" skill). They said
the heat bar, ferrous-slug ammo, storm-charging, overcharge stage, bullet cam, science note and the heat/damage/block
config are **theirs to do later: don't build them**.

- R cycles Explosion -> Lightning -> Homing -> Gauss (`ClientConfig.GAUSS`, file key `gauss`). Hold right click:
  3 stages of 20 ticks (`GaussShot.STAGE_TICKS`, full at 40); a tap under 3 ticks fizzles, any longer release fires
  at the stage reached. 20-tick cooldown (bar under the ammo label only, no chat-line message).
- Server: `ChargePayload.GAUSS_START` starts a charge in `LightningCasting`'s `CHARGING` map with the id in its
  `GAUSS` set; `RELEASE` fires `GaussShot.fire`. Hitscan from the eye: `GaussShot.trace` (shared with the client)
  walks `BlockGetter.traverseBlocks`; blocks softer than stone (explosion resistance < stone's, breakable) are gone
  through, up to 12, if `LightningTuning.gaussBlocks` (J screen "Gauss: through blocks", server key `gauss_blocks`);
  glass-sounding blocks and leaves are broken (`destroyBlock`, mobGriefing only). Mobs on the segment, nearest first,
  2/4/6 by stage; the slug stops in the last. Damage 10-40 by charge, x0.85 per mob; 40% as `gauss_formula_pierce`
  (in `#minecraft:bypasses_armor`), the rest as `gauss_formula`.
- `GaussPayload` (muzzle, end, charge, block exits, hit points) goes to everyone within 192 of the muzzle, the middle
  or the end. `ChargeSyncPayload` has a `gauss` flag so others see the right charge.
- `client/fx/GaussFx`: coils (5, lit one by one per stage: blue, bright blue, white), whine (`ChargeSound` with
  `gauss`), stage chime, aim line + rings at block exits + mobs in line outlined (`OUTLINE`; Fabric
  `EntityRendererMixin`, Forge `EntityMixin`/`MinecraftMixin`), the shot (line of light 22 ticks, ionised spiral,
  muzzle shock cone, sparks at exits/hits), crack at the muzzle, boom from the nearest point of the line delayed by
  distance (`StrikeFx.later`). Sounds: `dev/art/make_gauss_sounds.py` (uses make_sounds.py's helpers).
- **The coils are drawn with the rifle, not as world particles.** First person: the hand is drawn over the world,
  so world particles on the barrel are hidden; `ItemInHandRendererMixin` calls `GaussFx.handCoils` (view space, the
  same barrel line as `ChargeFx.gun`). Third person: `ItemInHandLayerMixin` calls `GaussFx.heldCoils` in the held
  item's space. Type 95 draws its rifle with code (a special model, no geometry file), so `HELD_MUZZLE`/`HELD_AXIS`
  were measured: the harness's stand-in (a client-side `RemotePlayer` holding the rifle, charged) was shot from its
  side, front and back with the held pose matrix logged, and a line was fitted so it projects onto the barrel in all
  three. If Type 95 changes its model, redo that (log `pose.last().pose()` in `heldCoils`, fit offline).
- HUD: the Gauss charge bar and label sit left of the crosshair (the aimed rifle's muzzle is just below it). The J
  screen (now titled "Type 95 Addition") got the Gauss toggle as a 9th row; its rows shrink from 24 to 21 px so it
  fits a 240-px-high GUI.
- Harness: `-Dt95.clienttest.gauss=true`: stand-in shots (`t95_gauss0_standin_*`), then a glass wall, planks, wool,
  leaves, 4 pigs, a diamond-armoured zombie, stone and a pig behind it in one line; a stage-1 tap (2 pigs die), then
  a full shot (2 pigs + the zombie hit, it stops at the stone), a side view of the line, and the J screen. Logs who
  is left and which blocks are gone.

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
  then a bolt every 2 ticks. Scorch does 500 columns/tick, nearest first, only with mobGriefing on.
- `client/`: `ChargeClient` (local state, keys), `AdditionHud`, `LightningScreen`, `ClientConfig`, `Palette`;
  `client/fx/`: `ArcParticle` (full-bright dot, the only particle type: `type95_addition:arc`), `Arcs` (jagged
  arcs/rings out of dots), `ChargeFx` (the 4 stages + sounds), `ChargeSound` (hum), `StrikeFx` (beam, sky bolts in
  the palette colour over the vanilla ones, shock ring, ground arcs, flash, shake), `MarkFx`, `StormFx` (Rimulib
  post-processing). Fabric mixins: `MinecraftMixin` (right/left click), `AbstractClientPlayerMixin` (zoom),
  `CameraMixin` (shake), `MouseHandlerMixin` (scope scroll), `ItemInHandRendererMixin`, `PlayerModelMixin`.
- Sounds: the addon's own 18 oggs (`AddonSounds`, `sounds.json`) plus some vanilla ones.

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

## Repo and releases

- GitHub `Adamko-10/Type-95-Sniper-Rifle-Addition`, branch `main` (both builds in one branch). Commit as the user's own
  git identity. Push and release only when the user asks.
- Releases: one per loader, tag `v<ver>` (Fabric 1.21.11) and `v<ver>+forge-1.20.1`, titled
  `Type 95 Addition <ver> [<Loader> <MC>]`, the jar attached, a short changelog for players. Local test jars live in
  `releases/<ver> (to test)/` (git-ignored). `gh release create` treats `[...]` in a file name as a glob, and the
  Windows `gh.exe` can't read `/c/...` paths: upload a copy with a plain name from a relative path, label it with
  `file#Proper name`.
- `.gitignore` must keep `libs/**/*.jar` and `forge-1.20.1/libs/**/*.jar` (Type 95's and Rimulib's jars are All Rights
  Reserved; `libs/*.jar` alone missed `libs/dev/`). Check `git diff --cached --name-only | grep jar` before committing.

## Pitfalls already paid for

- Never ask for a key's name (`KeyMapping.getTranslatedKeyMessage()`) during mod init: it calls GLFW before the game
  starts it, and the game crashes at launch ("GLFW error before init").
- Git Bash on Windows: long heredocs with quotes fail to parse; write files with the editor instead (or a .py file).
- The real mouse over the test window turns the view: the harness re-locks the aim every tick (`holdAim`).
- Shader light terms need squared falloff and modest gains: `lightFalloff` has a long tail, and 2-4x gains washed
  the whole screen white. Rimulib's bloom only picks up what a pass *adds* (plus a `synthetic` knee), keep
  `synthetic` low (0.15-0.25) or the daytime sky blooms.
- In first person the rifle is drawn after the world with the depth cleared: anything meant to sit *on* the rifle
  must be drawn in the hand pass (`ItemInHandRendererMixin`), not as particles. In third person draw it in the held
  item's space (`ItemInHandLayerMixin`); `ChargeFx.gun`'s third-person muzzle is only a rough guess.
- Darkening Minecraft's own clouds to near-black makes ugly black blocks: tint them instead.
