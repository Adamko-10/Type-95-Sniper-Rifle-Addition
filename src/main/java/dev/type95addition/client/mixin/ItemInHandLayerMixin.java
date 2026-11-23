package dev.type95addition.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.type95addition.Type95Addition;
import dev.type95addition.client.fx.GaussFx;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Third person: the Gauss Formula's coils are drawn with the rifle in the player's hand (any player, seen by anyone). */
@Mixin(ItemInHandLayer.class)
abstract class ItemInHandLayerMixin {
    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void type95addition$coils(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm,
                                      PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar && arm == avatar.mainArm && Type95Addition.isRifle(stack)) {
            GaussFx.heldCoils(avatar.id, pose, collector);
        }
    }
}
