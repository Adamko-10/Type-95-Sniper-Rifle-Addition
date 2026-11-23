package dev.type95addition.client.mixin;

import dev.type95addition.client.fx.HomingFx;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Homing Formula crush: how flat the entity is goes into its render state, and it gets the magenta outline. Mobs in
 * the line of a charging Gauss Formula get a pale blue one.
 */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void type95addition$crush(Entity entity, EntityRenderState state, float partial, CallbackInfo ci) {
        state.setData(HomingFx.SQUASH, HomingFx.squash(entity, partial));
        if (HomingFx.outlined(entity)) {
            state.outlineColor = HomingFx.OUTLINE;
        } else if (dev.type95addition.client.fx.GaussFx.targeted(entity)) {
            state.outlineColor = dev.type95addition.client.fx.GaussFx.OUTLINE;
        }
    }
}
