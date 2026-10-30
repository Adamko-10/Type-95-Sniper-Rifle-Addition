package dev.type95addition.world;

import cyou.rimuru.rimulib.mc1201.terrain.Carving;
import dev.type95addition.Type95Addition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The crater a charged strike blasts (stage 3 and up, "Thunderclap crater" on): a bowl with a ragged rim, its floor
 * fused by the lightning - blackstone and basalt with magma veins, and fulgurite glass streaks running out from the
 * centre like frozen lightning. Carved by Rimulib's streamed {@link Carving} (what Type 95's own crater uses), so even
 * a 400-block crater doesn't stall the server: the chunks are cut a few per tick as the shock front reaches them.
 *
 * <p>The floor and surface functions run on Rimulib's worker threads: they only do arithmetic.
 */
final class Crater {
    private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
    private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
    private static final BlockState TINTED = Blocks.TINTED_GLASS.defaultBlockState();
    private static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState BASALT = Blocks.SMOOTH_BASALT.defaultBlockState();
    private static final BlockState OBSIDIAN = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    private static final BlockState COARSE = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();

    private final Carving carving;
    private final double radius;

    Crater(ServerLevel level, Vec3 target, double radius, long seed) {
        this.radius = radius;
        double cx = target.x;
        double cz = target.z;
        int ground = (int)Math.floor(target.y + 0.01);
        int lowest = level.getMinBuildHeight() + 2;
        double depth = Math.max(2.0, radius * 0.32);
        double phase = (seed & 0xFFFF) / 65535.0 * Math.PI * 2.0;
        int streaks = 9 + (int)((seed >>> 16) & 7);

        Carving.Floor floor = (x, z) -> {
            double dx = x + 0.5 - cx;
            double dz = z + 0.5 - cz;
            double r = Math.sqrt(dx * dx + dz * dz);
            double edge = rim(radius, Math.atan2(dz, dx), phase);
            if (r >= edge) {
                return Carving.UNTOUCHED;
            }
            double t = r / edge;
            return Math.max(lowest, ground - 1 - (int)Math.round(depth * (1.0 - t * t)));
        };
        Carving.Surface surface = (x, y, z, was) -> {
            if (was.isAir() || !was.getFluidState().isEmpty()) {
                return null;
            }
            double dx = x + 0.5 - cx;
            double dz = z + 0.5 - cz;
            double r = Math.sqrt(dx * dx + dz * dz);
            double angle = Math.atan2(dz, dx);
            double t = r / rim(radius, angle, phase);
            long h = hash(x, z, seed);
            if (t >= 0.86) {
                return (h & 3) == 0 ? BLACKSTONE : (h & 3) == 1 ? GRAVEL : COARSE;
            }
            if (t < 0.08) {
                return (h & 1) == 0 ? OBSIDIAN : MAGMA;
            }
            // Fulgurite streaks: pale lines of fused rock and glass running out from the centre, wandering a little
            double wobble = 0.12 * Math.sin(r * 0.09 + phase) + 0.05 * Math.sin(r * 0.31 + phase * 2.0);
            double cell = (angle / (Math.PI * 2.0) + 1.0) * streaks + wobble;
            double off = Math.abs(cell - Math.floor(cell) - 0.5) * (Math.PI * 2.0 * r / streaks);
            if (off < 0.9 + radius * 0.006) {
                return (h & 7) < 5 ? CALCITE : GLASS;
            }
            int roll = (int)(h % 100);
            return roll < 14 ? MAGMA : roll < 58 ? BLACKSTONE : roll < 90 ? BASALT : TINTED;
        };

        this.carving = Carving.flatten(level, new Vec3(cx, ground, cz), radius * 1.12, floor, surface, null)
            .perTick(40)
            .named("Lightning Formula crater")
            .streaming(new Carving.Tickets() {
                @Override
                public void hold(ChunkPos pos) {
                    level.getChunkSource().addRegionTicket(Type95Addition.CRATER_TICKET, pos, 0, pos);
                }

                @Override
                public void release(ChunkPos pos) {
                    level.getChunkSource().removeRegionTicket(Type95Addition.CRATER_TICKET, pos, 0, pos);
                }
            });
    }

    /** The rim's distance from the centre at this angle: the radius, give or take a ragged 12%. */
    private static double rim(double radius, double angle, double phase) {
        return radius * (1.0 + 0.06 * Math.sin(angle * 5.0 + phase) + 0.04 * Math.sin(angle * 11.0 + phase * 3.0)
            + 0.02 * Math.sin(angle * 23.0 + phase * 7.0));
    }

    private static long hash(int x, int z, long seed) {
        long h = x * -7046029254386353131L ^ z * -4417276706812531889L ^ seed;
        h ^= h >>> 29;
        h *= -4658895280553007687L;
        h ^= h >>> 32;
        return h & Long.MAX_VALUE;
    }

    /** Cuts up to where the blast front has got to (blocks from the centre). */
    void tick(double front) {
        if (!this.carving.done()) {
            this.carving.tick(Math.max(4.0, front));
        }
    }

    boolean done() {
        return this.carving.done();
    }

    double radius() {
        return this.radius;
    }

    /** The strike is over: Rimulib finishes the rest from its own tick. */
    void hand() {
        if (!this.carving.done()) {
            Carving.linger(this.carving);
        }
    }

    void stop() {
        this.carving.stop();
    }
}
