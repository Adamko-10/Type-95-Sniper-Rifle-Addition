package dev.type95addition.client.mixin;

import dev.type95addition.client.anim.RifleAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third person: while charging the Lightning Formula the player brings the rifle up towards the line of sight
 * (shaking more as the charge grows), and each shot throws the arms, head and body back in recoil. Runs after
 * Type 95's own rifle pose (priority) and moves it rather than replacing it, so the rifle stays where its model
 * expects the hands to be.
 */
@Mixin(value = PlayerModel.class, priority = 1500)
abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void type95addition$aim(AvatarRenderState state, CallbackInfo ci) {
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float aim = RifleAnim.aim(state.id, partial);
        if (aim <= 0.001F) {
            return;
        }
        HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
        boolean right = state.mainArm == HumanoidArm.RIGHT;
        float side = right ? 1.0F : -1.0F;

        float t = RifleAnim.time(partial);
        float c = RifleAnim.tremble(state.id);
        float shake = c * c * 0.06F;
        float jx = (Mth.sin(t * 2.9F) + 0.5F * Mth.sin(t * 7.3F)) * shake;
        float jy = Mth.cos(t * 3.7F) * shake;
        float kick = RifleAnim.recoil(state.id, partial);

        // raise both arms (the rifle comes up to the eye) and follow the head's pitch a little
        float raise = (0.45F + 0.25F * c + model.head.xRot * 0.35F) * aim;
        model.rightArm.xRot -= raise + kick * 0.8F * aim - jx;
        model.leftArm.xRot -= raise + kick * 0.6F * aim - jx;
        model.rightArm.yRot += (-0.15F * side + jy) * aim;
        model.leftArm.yRot += (-0.15F * side + jy) * aim;
        // lean into the shot, rock back with the recoil
        model.body.xRot += (0.08F * c - kick * 0.15F) * aim;
        model.head.xRot -= kick * 0.3F * aim;
    }
}
