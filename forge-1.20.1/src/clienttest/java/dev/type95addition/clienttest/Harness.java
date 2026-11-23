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
import java.util.stream.Stream;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dev-only: makes a flat world, gives the player the rifle, charges the Lightning Formula through its four stages
 * (screenshots of each, first person, then from the front and behind), fires it at a herd of pigs 30 blocks away,
 * screenshots the strike and the ground it leaves, logs what it hit, and quits. Lines start with [T95TEST].
 * (The Fabric build's og mode, firing the original Explosion Formula for comparison, is Fabric only.)
 */
@Mod("t95_clienttest")
public final class Harness {
    private static final String LEVEL = "t95_clienttest";
    private static final int TARGET_DIST = 30;

    private static int step;
    private static int idle;
    private static int wait;
    private static int held;
    private static int after;
    private static int ground;
    private static int burntBefore;

    public Harness() {
        if (System.getProperty("t95.clienttest") == null) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                tick(Minecraft.getInstance());
            }
        });
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
                    step = HOMING ? 20 : GAUSS ? 30 : 3;
                    wait = 0;
                }
            }
            case 3 -> charge(mc);
            case 4 -> afterShot(mc);
            case 20 -> homing(mc);
            case 30 -> gauss(mc);
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
            new GameRules(), WorldDataConfiguration.DEFAULT);
        log("creating world");
        mc.createWorldOpenFlows().createFreshLevel(LEVEL, settings, WorldOptions.defaultWithRandomSeed(),
            access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
    }

    private static void setUp(Minecraft mc) {
        ClientConfig.ammo = HOMING ? ClientConfig.HOMING : GAUSS ? ClientConfig.GAUSS : ClientConfig.LIGHTNING;
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
            player.teleportTo(level, 0.5, ground, 0.5, 0.0F, pitch);
            var rifle = BuiltInRegistries.ITEM.get(Type95Addition.RIFLE);
            player.getInventory().setItem(0, new ItemStack(rifle));
            log("rifle item " + BuiltInRegistries.ITEM.getKey(rifle) + ", ground y " + ground);
            if (GAUSS) {
                gaussRange(server, level);
                return;
            }
            for (int i = 0; i < 8; i++) {
                var pig = EntityType.PIG.spawn(level, new BlockPos(-4 + i, ground, TARGET_DIST + (i % 3) - 1), MobSpawnType.COMMAND);
                if (i == 0 && pig != null) {
                    pig.setCustomName(net.minecraft.network.chat.Component.literal("Bob"));
                }
            }
            if (HOMING) {
                server.setDifficulty(Difficulty.NORMAL, true);
                EntityType.WITHER.spawn(level, new BlockPos(6, ground + 10, 34), MobSpawnType.COMMAND);
                EntityType.ENDER_DRAGON.spawn(level, new BlockPos(0, ground + 20, 44), MobSpawnType.COMMAND);
            }
            burntBefore = countBurnt(level);
            if (MARK) {
                EntityType.COW.spawn(level, new BlockPos((int)Math.floor(COW_X), ground, TARGET_DIST), MobSpawnType.COMMAND);
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
                player.teleportTo(level, 0.5, Math.min(ground + 110 + crater * 1.4, 140), TARGET_DIST + 0.5, 0.0F, 90.0F);
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

    // ---- The Homing Formula (-Dt95.clienttest.homing=true): one shot at the pigs, a wither and a dragon --------------

    private static final boolean HOMING = Boolean.getBoolean("t95.clienttest.homing");

    private static void homing(Minecraft mc) {
        holdAim(mc);
        after++;
        if (after == 1) {
            // superflat's slimes would take bullets meant for the bosses
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> server.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class,
                new AABB(-200, -100, -200, 200, 100, 300)).forEach(net.minecraft.world.entity.Entity::discard));
        }
        mc.options.keyUse.setDown(after >= 3 && after <= 4);
        for (int t : new int[] {4, 10, 18, 26, 36, 48, 60, 75, 90, 100, 115, 140}) {
            if (after == t) {
                shot(mc, "homing_" + t);
            }
        }
        if (after == 60) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (after == 160) {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerLevel level = server.overworld();
                AABB box = new AABB(-60, ground - 10, -10, 60, ground + 60, 90);
                log("pigs alive after the homing shot: " + level.getEntitiesOfClass(Animal.class, box).size() + " of 8");
                log("withers alive: " + level.getEntitiesOfClass(net.minecraft.world.entity.boss.wither.WitherBoss.class, box).size());
                for (var dragon : level.getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EnderDragon.class, box.inflate(100))) {
                    log("dragon: " + dragon.getPhaseManager().getCurrentPhase().getPhase() + " at " + dragon.position() + ", " + dragon.getHealth() + " hp");
                }
            });
        }
        if (after == 170) {
            log("done");
            mc.stop();
        }
    }

    // ---- The Gauss Formula (-Dt95.clienttest.gauss=true): a tap shot, then a full one, down a line of targets ---------

    private static final boolean GAUSS = Boolean.getBoolean("t95.clienttest.gauss");
    /** The line's slope: from eye height at the player down through the pigs' middles 20-30 blocks off. */
    private static final double GAUSS_SLOPE = 0.039;
    private static final int[] GAUSS_PIGS = {20, 23, 26, 29};
    private static final int GAUSS_ZOMBIE = 32;
    private static final int GAUSS_STONE = 35;
    private static final int GAUSS_BEHIND = 38;
    private static int gaussShot;

    private static void gaussRange(IntegratedServer server, ServerLevel level) {
        server.setDifficulty(Difficulty.NORMAL, true);
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y < 3; y++) {
                level.setBlockAndUpdate(new BlockPos(x, ground + y, 12), Blocks.GLASS.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(new BlockPos(0, ground + 1, 14), Blocks.OAK_PLANKS.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(0, ground + 1, 16), Blocks.WHITE_WOOL.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(0, ground, 18), Blocks.OAK_LEAVES.defaultBlockState()
            .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        level.setBlockAndUpdate(new BlockPos(0, ground, GAUSS_STONE), Blocks.STONE.defaultBlockState());
        for (int z : GAUSS_PIGS) {
            gaussTarget(level, EntityType.PIG, z);
        }
        var zombie = gaussTarget(level, EntityType.ZOMBIE, GAUSS_ZOMBIE);
        if (zombie != null) {
            zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
            zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE));
            zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(net.minecraft.world.item.Items.DIAMOND_LEGGINGS));
            zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS));
            log("zombie armour " + zombie.getArmorValue());
        }
        gaussTarget(level, EntityType.PIG, GAUSS_BEHIND);
    }

    private static net.minecraft.world.entity.Mob gaussTarget(ServerLevel level, EntityType<? extends net.minecraft.world.entity.Mob> type, int z) {
        var mob = type.spawn(level, new BlockPos(0, ground, z), MobSpawnType.COMMAND);
        if (mob != null) {
            mob.setNoAi(true);
            mob.setCustomName(net.minecraft.network.chat.Component.literal(type.getDescription().getString() + " @" + z));
            mob.setCustomNameVisible(true);
        }
        return mob;
    }

    private static int standIn;
    private static net.minecraft.client.player.RemotePlayer fake;

    /**
     * Before the shots: a client-side stand-in player holding the rifle, charged, 3.5 blocks in front, photographed
     * from its right side, its front and its back (how other players see the coils).
     */
    private static void standIn(Minecraft mc) {
        standIn++;
        mc.player.setYRot(0.0F);
        mc.player.setXRot(10.0F);
        mc.options.hideGui = true;
        if (standIn == 1) {
            fake = new net.minecraft.client.player.RemotePlayer(mc.level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "StandIn"));
            fake.setPos(0.5, ground, 4.0);
            fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(Type95Addition.RIFLE)));
            mc.level.addPlayer(fake.getId(), fake);
            dev.type95addition.client.fx.GaussFx.sync(fake.getId(), true, 60);
        }
        float yaw = standIn < 7 ? 90.0F : standIn < 12 ? 180.0F : 0.0F;
        fake.setYRot(yaw);
        fake.setYHeadRot(yaw);
        fake.yBodyRot = fake.yBodyRotO = fake.yHeadRotO = yaw;
        fake.setXRot(0.0F);
        if (standIn == 6) {
            shot(mc, "gauss0_standin_side");
        }
        if (standIn == 11) {
            shot(mc, "gauss0_standin_front");
        }
        if (standIn == 16) {
            shot(mc, "gauss0_standin_back");
        }
        if (standIn == 17) {
            dev.type95addition.client.fx.GaussFx.sync(fake.getId(), false, 0);
            mc.level.removeEntity(fake.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            mc.options.hideGui = false;
        }
    }

    private static void gauss(Minecraft mc) {
        if (standIn < 17) {
            standIn(mc);
            return;
        }
        after++;
        if (gaussShot == 0 || after < 80 + 46 + 2) {
            mc.player.setYRot(0.0F);
            mc.player.setXRot((float)Math.toDegrees(Math.atan(GAUSS_SLOPE)));
        } else if (after == 80 + 46 + 2) {
            // the line from the side, as someone standing next to it sees it
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = true;
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> server.getPlayerList().getPlayers().get(0).teleportTo(server.overworld(), 12.5, ground, 16.0,
                75.0F, 8.0F));
        }
        if (after == 1) {
            // superflat's slimes would wander into the line
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> server.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class,
                new AABB(-200, -100, -200, 200, 100, 300)).forEach(net.minecraft.world.entity.Entity::discard));
        }
        // shot 1: a tap (stage 1: through 2 mobs); shot 2 (from tick 80): full charge (through 6)
        int start = gaussShot == 0 ? 5 : 80;
        int release = gaussShot == 0 ? start + 10 : start + 46;
        mc.options.keyUse.setDown(after >= start && after < release);
        int held = ChargeClient.gaussHeld();
        if (held == 1) {
            log("ok: right click started a Gauss charge (shot " + (gaussShot + 1) + ")");
        }
        if (gaussShot == 0 && held == 6) {
            shot(mc, "gauss1_stage1");
            log("mobs outlined in line (stage 1): " + dev.type95addition.client.fx.GaussFx.targets());
        }
        if (gaussShot == 1) {
            if (held == 28) {
                shot(mc, "gauss2_stage2");
            }
            if (held == 42) {
                shot(mc, "gauss2_full_first_person");
                log("mobs outlined in line (full): " + dev.type95addition.client.fx.GaussFx.targets());
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            if (held == 44) {
                shot(mc, "gauss2_full_front");
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        }
        if (after == release) {
            log("released Gauss shot " + (gaussShot + 1));
        }
        for (int t : new int[] {1, 3, 8, 16, 26}) {
            if (after == release + t) {
                shot(mc, "gauss" + (gaussShot + 1) + "_after_" + t);
            }
        }
        if (after == release + 30) {
            gaussReport(mc, "after shot " + (gaussShot + 1));
            if (gaussShot == 0) {
                gaussShot = 1;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
        }
        if (gaussShot == 1 && after == 80 + 46 + 40) {
            mc.options.hideGui = false;
            mc.setScreen(new dev.type95addition.client.LightningScreen());
        }
        if (gaussShot == 1 && after == 80 + 46 + 50) {
            shot(mc, "gauss_settings_screen");
        }
        if (gaussShot == 1 && after == 80 + 46 + 56) {
            mc.setScreen(null);
            log("done");
            mc.stop();
        }
    }

    private static void gaussReport(Minecraft mc, String when) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            StringBuilder alive = new StringBuilder();
            for (var mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(-5, ground - 2, 0, 5, ground + 5, 60))) {
                alive.append(" [").append(mob.getCustomName() == null ? mob.getType().toShortString() : mob.getCustomName().getString())
                    .append(" ").append(mob.getHealth()).append(" hp]");
            }
            log(when + ": alive" + alive);
            log(when + ": glass at the line " + level.getBlockState(new BlockPos(0, ground + 1, 12)).getBlock()
                + ", planks " + level.getBlockState(new BlockPos(0, ground + 1, 14)).getBlock()
                + ", wool " + level.getBlockState(new BlockPos(0, ground + 1, 16)).getBlock()
                + ", leaves " + level.getBlockState(new BlockPos(0, ground, 18)).getBlock()
                + ", stone " + level.getBlockState(new BlockPos(0, ground, GAUSS_STONE)).getBlock());
        });
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
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(),
            component -> log("shot " + new File(new File(mc.gameDirectory, "screenshots"), file)));
    }

    static void log(String message) {
        System.out.println("[T95TEST] " + message);
    }
}
