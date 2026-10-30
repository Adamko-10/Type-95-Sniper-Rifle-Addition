package dev.type95addition.client.mixin;

import dev.type95addition.client.fx.StrikeFx;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The camera shake of a nearby strike (the J screen can turn it off). */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract float xRot();

    @Shadow
    public abstract float yRot();

    @Inject(method = "setup", at = @At("TAIL"))
    private void type95addition$shake(Level level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
        float[] turn = StrikeFx.shake(partial);
        if (turn != null) {
            this.setRotation(this.yRot() + turn[0], this.xRot() + turn[1]);
        }
    }
}
