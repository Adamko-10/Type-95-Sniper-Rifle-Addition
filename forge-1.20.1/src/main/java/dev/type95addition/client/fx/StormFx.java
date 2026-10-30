package dev.type95addition.client.fx;

import cyou.rimuru.rimulib.mc1201.client.render.PostFx;
import cyou.rimuru.rimulib.mc1201.client.render.PostFxRenderer;
import cyou.rimuru.rimulib.mc1201.client.render.PostPass;
import cyou.rimuru.rimulib.mc1201.client.render.ScreenFx;
import cyou.rimuru.rimulib.mc1201.client.render.ShaderWarmup;
import dev.type95addition.Type95Addition;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The Lightning Formula's screen effects, drawn by Rimulib's post-processing (the same renderer Type 95's own
 * effects use): every frame the charges ({@link ChargeFx#passes}) and strikes ({@link StrikeFx#passes}) add their
 * shader passes (assets/type95_addition/shaders/fx/) and their screen effects (bloom, flash, colour split, zoom
 * blur, ripple, vignette), and the renderer runs them over the frame, ending with our composite pass.
 */
public final class StormFx {
    private static final PostFxRenderer RENDERER = new PostFxRenderer(Type95Addition.MOD_ID);
    private static final ScreenFx SCREEN = new ScreenFx();
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;

    private StormFx() {
    }

    public static void register() {
        PostFx.register(Type95Addition.MOD_ID, StormFx::capture, StormFx::render);
        PostFx.wardAware(Type95Addition.MOD_ID);
        ShaderWarmup.register(Type95Addition.MOD_ID, RENDERER::warm);
    }

    private static void capture(Matrix4f view, Matrix4f projection) {
        VIEW.set(view);
        PROJECTION.set(projection);
    }

    private static void render(Camera cam, float partial) {
        camera = cam.getPosition();
        VIEW_PROJECTION.set(PROJECTION).mul(VIEW);
        List<PostPass> passes = new ArrayList<>();
        SCREEN.reset();
        ChargeFx.passes(camera, partial, passes, SCREEN);
        StrikeFx.passes(camera, partial, passes, SCREEN);
        MarkFx.passes(camera, partial, passes, SCREEN);
        if (passes.isEmpty() && SCREEN.idle()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float time = ((mc.level == null ? 0L : mc.level.getGameTime() % 24000L) + partial) / 20.0F;
        RENDERER.render(passes, SCREEN, PROJECTION, VIEW, time);
    }

    /** Where a world point is on the screen (0-1 each way, y up), or null when it's behind the camera. */
    static float[] onScreen(Vec3 world) {
        Vec3 rel = world.subtract(camera);
        Vector4f clip = VIEW_PROJECTION.transform(new Vector4f((float)rel.x, (float)rel.y, (float)rel.z, 1.0F));
        if (clip.w <= 0.05F) {
            return null;
        }
        return new float[] {clip.x / clip.w * 0.5F + 0.5F, clip.y / clip.w * 0.5F + 0.5F};
    }

    static Vec3 camera() {
        return camera;
    }
}
