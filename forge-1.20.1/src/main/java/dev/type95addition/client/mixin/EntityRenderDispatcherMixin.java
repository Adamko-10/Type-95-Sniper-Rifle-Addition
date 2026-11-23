package dev.type95addition.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.type95addition.client.fx.HomingFx;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws a crushed entity squashed about its feet (any entity: this is before its own renderer, so the dragon too). */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void type95addition$squash(Entity entity, double x, double y, double z, float yRot, float partial, PoseStack pose,
                                       MultiBufferSource buffers, int light, CallbackInfo ci) {
        HomingFx.squash(pose, HomingFx.squash(entity, partial), entity.tickCount + partial);
    }
}
