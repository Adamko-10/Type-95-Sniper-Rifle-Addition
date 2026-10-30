package dev.type95addition.clienttest;

import dev.type95addition.Type95Addition;
import dev.type95addition.client.ChargeClient;
import dev.type95addition.client.ClientConfig;
import dev.type95addition.client.Palette;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;

/**
 * Dev-only: makes a flat world, gives the player the rifle, charges the Lightning Formula through its four stages
 * (screenshots of each, first person, then from the front and behind), fires it at a herd of pigs 30 blocks away,
 * screenshots the strike and the ground it leaves, logs what it hit, and quits. Lines start with [T95TEST].
 */
public final class Harness implements ClientModInitializer {
    private static final String LEVEL = "t95_clienttest";
    private static final int TARGET_DIST = 30;

    private static int step;
    private static int idle;
    private static int wait;
    private static int held;
    private static int after;
    private static int ground;
    private static int burntBefore;

    @Override
    public void onInitializeClient() {
        if (System.getProperty("t95.clienttest") == null) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(Harness::tick);
        log("armed");
    }

    private static void tick(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.options.tutorialStep = TutorialSteps.NONE;
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        switch (step) {
            case 0 -> {
                if (++idle % 200 == 0) {
                    log("waiting for the title screen, on: " + (mc.screen == null ? "none" : mc.screen.getClass().getName()));
                }
                if (mc.screen instanceof TitleScreen && ++wait > 20) {
                    createWorld(mc);
                    step = 1;
                    wait = 0;
                }
            }
            case 1 -> {
                if (mc.player != null && mc.getSingleplayerServer() != null && mc.screen == null && ++wait > 60) {
                    setUp(mc);
                    step = 2;
                    wait = 0;
                }
            }
            case 2 -> {
                if (++wait > 40) {
                    log("charging; charge time " + ChargeClient.chargeTicks + " ticks");
                    step = OG ? 10 : 3;
                    wait = 0;
                }
            }
            case 3 -> charge(mc);
            case 10 -> ogCharge(mc);
            case 11 -> ogAfter(mc);
            case 4 -> afterShot(mc);
            default -> {
            }
        }
    }

    private static void createWorld(Minecraft mc) {
        try {
            Path save = mc.gameDirectory.toPath().resolve("saves").resolve(LEVEL);
            if (Files.exists(save)) {
                try (Stream<Path> walk = Files.walk(save)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        } catch (IOException e) {
            log("couldn't delete the old save: " + e);
        }
        LevelSettings settings = new LevelSettings(LEVEL, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
            new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
        log("creating world");
        mc.createWorldOpenFlows().createFreshLevel(LEVEL, settings, WorldOptions.defaultWithRandomSeed(),
            access -> access.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
            new TitleScreen());
    }

    private static void setUp(Minecraft mc) {
        ClientConfig.lightning = !OG;
        String palette = System.getProperty("t95.clienttest.palette");
        if (palette != null) {
            ClientConfig.palette = Palette.byKey(palette);
        }
        String power = System.getProperty("t95.clienttest.power");
        if (power != null) {
            ClientConfig.power = Float.parseFloat(power);
        }
        String bolts = System.getProperty("t95.clienttest.bolts");
        if (bolts != null) {
            ClientConfig.bolts = Integer.parseInt(bolts);
        }
        ClientConfig.scope = SCOPE;
        ClientConfig.marking = true;
        ChargeClient.sendTuning();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            level.setDayTime(6000);
            ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            float pitch = (float)Math.toDegrees(Math.atan2(1.62, TARGET_DIST));
            player.teleportTo(level, 0.5, ground, 0.5, Set.of(), 0.0F, pitch, false);
            var rifle = BuiltInRegistries.ITEM.getValue(Type95Addition.RIFLE);
            player.getInventory().setItem(0, new ItemStack(rifle));
            log("rifle item " + BuiltInRegistries.ITEM.getKey(rifle) + ", ground y " + ground);
            for (int i = 0; i < 8; i++) {
                EntityType.PIG.spawn(level, new BlockPos(-4 + i, ground, TARGET_DIST + (i % 3) - 1), EntitySpawnReason.COMMAND);
            }
            burntBefore = countBurnt(level);
            if (MARK) {
                EntityType.COW.spawn(level, new BlockPos((int)Math.floor(COW_X), ground, TARGET_DIST), EntitySpawnReason.COMMAND);
            }
        });
    }

    /** Keeps the aim on the pigs (the real mouse over the test window would otherwise turn the view). */
    private static final boolean SCOPE = Boolean.getBoolean("t95.clienttest.scope");
    private static final boolean MARK = Boolean.getBoolean("t95.clienttest.mark");
    /** Mark mode: a cow off to the side, marked mid-charge while the view stays on the pigs. */
    private static final double COW_X = 18.5;

    private static void holdAim(Minecraft mc) {
        int h = ChargeClient.charging() ? ChargeClient.held() : 0;
        if (MARK && h >= 30 && h < 46) {
            // look at the cow and left click it
            double dx = COW_X - 0.5;
            double dz = TARGET_DIST + 0.5 - 0.5;
            mc.player.setYRot((float)-Math.toDegrees(Math.atan2(dx, dz)));
            mc.player.setXRot((float)Math.toDegrees(Math.atan2(1.62 - 0.6, Math.sqrt(dx * dx + dz * dz))));
            mc.options.keyAttack.setDown(h >= 34 && h < 40);
            return;
        }
        mc.options.keyAttack.setDown(false);
        mc.player.setYRot(0.0F);
        mc.player.setXRot((float)Math.toDegrees(Math.atan2(1.62, TARGET_DIST)));
    }

    private static void charge(Minecraft mc) {
        holdAim(mc);
        mc.options.keyUse.setDown(true);
        if (!ChargeClient.charging()) {
            if (++wait == 20) {
                log("FAIL: holding right click didn't start a charge through Minecraft.startUseItem; starting it directly");
                ChargeClient.interceptUse();
            }
            return;
        }
        int full = ChargeClient.chargeTicks;
        held = ChargeClient.held();
        if (held == 1) {
            log("ok: right click started the charge (startUseItem mixin)");
        }
        if (held == 41) {
            var mark = dev.type95addition.client.Marking.current();
            log(mark == null ? "FAIL: left click while charging made no mark" : "ok: marked " + mark);
        }
        if (held == 60) {
            shot(mc, "marked_first_person");
        }
        int[] shots = {(int)(full * 0.15), (int)(full * 0.4), (int)(full * 0.65), (int)(full * 0.9)};
        for (int i = 0; i < shots.length; i++) {
            if (held == shots[i]) {
                shot(mc, "stage" + (i + 1) + "_first_person");
            }
        }
        if (held == full + 2) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        if (held == full + 8) {
            shot(mc, "full_front");
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (held == full + 14) {
            shot(mc, "full_back");
            mc.options.keyUse.setDown(false);
            log("released after " + held + " ticks");
            step = 4;
            after = 0;
        }
    }

    private static void afterShot(Minecraft mc) {
        mc.options.keyUse.setDown(false);
        after++;
        for (int t : new int[] {1, 3, 6, 12, 25, 45, 70, 100, 140}) {
            if (after == t) {
                shot(mc, "after_" + t);
            }
        }
        if (after == 160) {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerLevel level = server.overworld();
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                int pigs = level.getEntitiesOfClass(Animal.class, new AABB(-40, ground - 10, -10, 40, ground + 30, 70)).size();
                log("pigs alive after the strike: " + pigs + " of 8");
                log("scorched ground blocks: before " + burntBefore + ", after " + countBurnt(level));
                java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (int x = -25; x <= 25; x++) {
                    for (int z = TARGET_DIST - 25; z <= TARGET_DIST + 25; z++) {
                        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                        kinds.merge(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.set(x, y, z)).getBlock()).getPath(), 1, Integer::sum);
                    }
                }
                log("surface blocks round the target: " + kinds);
                for (Animal pig : level.getEntitiesOfClass(Animal.class, new AABB(-40, ground - 10, -10, 40, ground + 30, 70))) {
                    log("survivor at " + pig.position() + ", " + pig.getHealth() + " hp, " + pig.position().distanceTo(
                        new net.minecraft.world.phys.Vec3(0.5, ground, TARGET_DIST + 0.5)) + " blocks from the target");
                }
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                double crater = dev.type95addition.LightningTuning.craterRadius(ClientConfig.power, 420.0);
                player.teleportTo(level, 0.5, Math.min(ground + 110 + crater * 1.4, 140), TARGET_DIST + 0.5, Set.of(), 0.0F, 90.0F, false);
            });
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = true;
        }
        if (after == 260) {
            shot(mc, "aftermath_aerial");
        }
        if (after == 270) {
            log("done");
            mc.stop();
        }
    }

    // ---- The original Explosion Formula, for comparison (-Dt95.clienttest.og=true) ---------------------------------

    private static final boolean OG = Boolean.getBoolean("t95.clienttest.og");
    private static int ogHeld;

    /** Holds right click with the Explosion Formula loaded: Type 95's own charge. */
    private static void ogCharge(Minecraft mc) {
        holdAim(mc);
        mc.options.keyUse.setDown(true);
        if (mc.player.isUsingItem()) {
            ogHeld++;
        } else if (++wait == 40) {
            log("FAIL: the rifle's own charge didn't start");
        }
        int[] shots = {40, 100, 160, 205};
        for (int i = 0; i < shots.length; i++) {
            if (ogHeld == shots[i]) {
                shot(mc, "og_charge" + (i + 1) + "_first_person");
            }
        }
        if (ogHeld == 207) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        if (ogHeld == 214) {
            shot(mc, "og_full_front");
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.options.keyUse.setDown(false);
            IntegratedServer server = mc.getSingleplayerServer();
            float power = ClientConfig.power;
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                try {
                    Class<?> shot = Class.forName("cyou.rimuru.tanya.shot.e");
                    for (java.lang.reflect.Method m : shot.getDeclaredMethods()) {
                        if (m.getName().equals("a") && m.getParameterCount() == 5 && m.getParameterTypes()[0] == ServerPlayer.class) {
                            m.setAccessible(true);
                            m.invoke(null, player, 200, new cyou.rimuru.rimulib.skill.Tuning(power, 24.0F, 1.0F),
                                new net.minecraft.world.phys.Vec3(0.5, ground, TARGET_DIST + 0.5), false);
                            log("fired the original Explosion Formula, power " + power);
                        }
                    }
                } catch (ReflectiveOperationException e) {
                    log("FAIL: couldn't fire the original: " + e);
                }
            });
            step = 11;
            after = 0;
        }
    }

    private static void ogAfter(Minecraft mc) {
        after++;
        for (int t : new int[] {3, 12, 25, 45, 70, 100, 140, 200}) {
            if (after == t) {
                shot(mc, "og_after_" + t);
            }
        }
        if (after == 260) {
            log("done");
            mc.stop();
        }
    }

    private static int countBurnt(ServerLevel level) {
        int n = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -40; x <= 40; x++) {
            for (int z = TARGET_DIST - 40; z <= TARGET_DIST + 40; z++) {
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                var state = level.getBlockState(pos.set(x, y, z));
                if (!state.is(Blocks.GRASS_BLOCK)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static void shot(Minecraft mc, String name) {
        String file = "t95_" + name + ".png";
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), 1,
            component -> log("shot " + new File(new File(mc.gameDirectory, "screenshots"), file)));
    }

    static void log(String message) {
        System.out.println("[T95TEST] " + message);
    }
}
