package dev.type95addition.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The ground a strike leaves: a molten spot at the centre and branching Lichtenberg scars running out from it
 * (sand turns to glass, the rest to magma, blackstone and burnt earth), plants and leaves burnt off. Done a few
 * hundred columns per tick, nearest first, so big strikes don't stall the server.
 */
final class Scorch {
    private static final int PER_TICK = 500;
    private static final int MAX_LENGTH = 240;

    private final ServerLevel level;
    private final boolean fire;
    private final RandomSource random;
    private final List<long[]> columns = new ArrayList<>();
    private int next;

    /** @param crater the crater's radius (0: none): the scars start at its rim, the crater paints its own floor */
    Scorch(ServerLevel level, Vec3 centre, double radius, int stage, boolean fire, long seed, double crater) {
        this.level = level;
        this.fire = fire;
        this.random = RandomSource.create(seed ^ 0x5DEECE66DL);
        Map<Long, Float> heat = new HashMap<>();
        int cx = (int)Math.floor(centre.x);
        int cz = (int)Math.floor(centre.z);

        // The molten centre (when there is no crater)
        double core = crater > 0.0 ? 0.0 : Math.min(radius * 0.22, 14.0);
        int ci = (int)Math.ceil(core);
        for (int dx = -ci; dx <= ci; dx++) {
            for (int dz = -ci; dz <= ci; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= core) {
                    put(heat, cx + dx, cz + dz, (float)(1.0 - 0.3 * d / Math.max(1.0, core)));
                }
            }
        }

        // The scars: random walks out from the centre that fork now and then
        double length = Math.min(radius * 0.95, MAX_LENGTH);
        int branches = 4 + stage + this.random.nextInt(3);
        double turn = this.random.nextDouble() * Math.PI * 2.0;
        for (int b = 0; b < branches; b++) {
            double angle = turn + b * Math.PI * 2.0 / branches + (this.random.nextDouble() - 0.5) * 0.5;
            double startR = crater * 0.95;
            this.walk(heat, centre.x + Math.cos(angle) * startR, centre.z + Math.sin(angle) * startR, angle,
                length * (0.7 + 0.3 * this.random.nextDouble()), 0.95F, 0);
        }

        // Scattered burns inside half the radius
        double scatter = Math.min(radius * 0.5, crater + 40.0);
        int si = (int)Math.ceil(scatter);
        for (int dx = -si; dx <= si; dx++) {
            for (int dz = -si; dz <= si; dz++) {
                double t = Math.sqrt(dx * dx + dz * dz) / Math.max(1.0, scatter);
                if (t < 1.0 && this.random.nextDouble() < 0.3 * (1.0 - t) * (1.0 - t)) {
                    put(heat, cx + dx, cz + dz, (float)(0.45 * (1.0 - t)));
                }
            }
        }

        for (Map.Entry<Long, Float> entry : heat.entrySet()) {
            long key = entry.getKey();
            int x = (int)(key >> 32);
            int z = (int)key;
            long dist = (long)((x - cx) * (long)(x - cx) + (z - cz) * (long)(z - cz));
            if (dist < crater * crater * 0.96) {
                continue;
            }
            this.columns.add(new long[] {key, Float.floatToIntBits(entry.getValue()), dist});
        }
        this.columns.sort((a, b) -> Long.compare(a[2], b[2]));
    }

    private void walk(Map<Long, Float> heat, double x, double z, double angle, double length, float start, int depth) {
        double travelled = 0.0;
        while (travelled < length) {
            float h = start * (float)(1.0 - 0.6 * travelled / length);
            int bx = (int)Math.floor(x);
            int bz = (int)Math.floor(z);
            put(heat, bx, bz, h);
            if (h > 0.55F) {
                put(heat, bx + 1, bz, h * 0.7F);
                put(heat, bx, bz + 1, h * 0.7F);
            }
            angle += (this.random.nextDouble() - 0.5) * 0.7;
            x += Math.cos(angle);
            z += Math.sin(angle);
            travelled += 1.0;
            if (depth < 2 && travelled > 3.0 && this.random.nextDouble() < 0.07) {
                double side = this.random.nextBoolean() ? 1.0 : -1.0;
                this.walk(heat, x, z, angle + side * (0.5 + 0.4 * this.random.nextDouble()),
                    (length - travelled) * (0.4 + 0.4 * this.random.nextDouble()), h * 0.85F, depth + 1);
            }
        }
    }

    private static void put(Map<Long, Float> heat, int x, int z, float value) {
        heat.merge(((long)x << 32) | (z & 0xFFFFFFFFL), value, Math::max);
    }

    boolean done() {
        return this.next >= this.columns.size();
    }

    void tick() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int n = 0; n < PER_TICK && this.next < this.columns.size(); n++) {
            long[] column = this.columns.get(this.next++);
            int x = (int)(column[0] >> 32);
            int z = (int)column[0];
            this.burn(pos, x, z, Float.intBitsToFloat((int)column[1]));
        }
    }

    private void burn(BlockPos.MutableBlockPos pos, int x, int z, float heat) {
        if (this.level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)) == null) {
            return;
        }
        int y = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        // Burn off what grows on top (plants, snow, leaves), a few blocks deep at most
        for (int i = 0; i < 6 && y > this.level.getMinY(); i++) {
            BlockState state = this.level.getBlockState(pos.set(x, y, z));
            boolean burnable = state.is(BlockTags.LEAVES) || state.getFluidState().isEmpty() && state.canBeReplaced();
            if (!burnable || state.hasBlockEntity()) {
                break;
            }
            if (state.is(BlockTags.LEAVES) && this.random.nextFloat() > heat + 0.3F) {
                break;
            }
            this.level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            y--;
        }
        BlockState ground = this.level.getBlockState(pos.set(x, y, z));
        if (ground.isAir() || !ground.getFluidState().isEmpty() || ground.hasBlockEntity() || ground.getDestroySpeed(this.level, pos) < 0.0F) {
            return;
        }
        BlockState burnt = this.burnt(ground, heat);
        if (burnt != null && burnt != ground) {
            this.level.setBlock(pos, burnt, Block.UPDATE_ALL);
        }
        if (this.fire && this.random.nextFloat() < 0.1F * heat) {
            BlockPos above = pos.above();
            if (this.level.getBlockState(above).isAir()) {
                BlockState flame = BaseFireBlock.getState(this.level, above);
                if (flame.canSurvive(this.level, above)) {
                    this.level.setBlock(above, flame, Block.UPDATE_ALL);
                }
            }
        }
    }

    private BlockState burnt(BlockState ground, float heat) {
        float roll = this.random.nextFloat();
        if (ground.is(BlockTags.SAND)) {
            // Fulgurite: lightning fuses sand into glass
            return heat > 0.25F ? (roll < 0.3F ? Blocks.TINTED_GLASS : Blocks.GLASS).defaultBlockState() : null;
        }
        if (ground.is(Blocks.SNOW_BLOCK) || ground.is(BlockTags.ICE)) {
            return heat > 0.4F ? Blocks.AIR.defaultBlockState() : null;
        }
        if (heat > 0.8F) {
            return (roll < 0.3F ? Blocks.MAGMA_BLOCK : roll < 0.75F ? Blocks.BLACKSTONE : Blocks.BASALT).defaultBlockState();
        }
        if (heat > 0.55F) {
            if (ground.is(BlockTags.DIRT) || ground.is(Blocks.GRAVEL)) {
                return (roll < 0.5F ? Blocks.COARSE_DIRT : Blocks.BLACKSTONE).defaultBlockState();
            }
            if (ground.is(BlockTags.BASE_STONE_OVERWORLD)) {
                return (roll < 0.2F ? Blocks.MAGMA_BLOCK : Blocks.BLACKSTONE).defaultBlockState();
            }
            return null;
        }
        if (heat > 0.2F && ground.is(BlockTags.DIRT) && !ground.is(Blocks.COARSE_DIRT)) {
            return (roll < 0.5F ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState();
        }
        return null;
    }
}
