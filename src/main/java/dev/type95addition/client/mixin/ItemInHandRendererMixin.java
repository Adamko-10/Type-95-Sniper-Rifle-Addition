package dev.type95addition.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.type95addition.Type95Addition;
import dev.type95addition.client.anim.RifleAnim;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First person: while charging, the rifle comes in towards the centre of the view and starts to shake (harder as
 * the charge grows, pulsing at full charge); each shot kicks it back into the shoulder and up, springing back.
 */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
    @Unique
    private boolean type95addition$moved;

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void type95addition$animate(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing,
                                         ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light,
                                         CallbackInfo ci) {
        this.type95addition$moved = false;
        if (hand != InteractionHand.MAIN_HAND || !Type95Addition.isRifle(stack)) {
            return;
        }
        int id = player.getId();
        float aim = RifleAnim.aim(id, partial);
        float kick = RifleAnim.recoil(id, partial);
        if (aim <= 0.001F && kick == 0.0F) {
            return;
        }
        this.type95addition$moved = true;
        pose.pushPose();
        float t = RifleAnim.time(partial);
        float c = RifleAnim.tremble(id);
        float shake = (0.0015F + 0.008F * c * c) * aim;
        pose.translate(
            -0.07F * aim + (Mth.sin(t * 3.1F) + 0.5F * Mth.sin(t * 9.7F)) * shake,
            0.035F * aim + (Mth.cos(t * 2.6F) + 0.5F * Mth.sin(t * 8.3F)) * shake,
            0.04F * aim + kick * 0.22F);
        pose.mulPose(Axis.XP.rotationDegrees(kick * 16.0F + c * 2.0F * aim));
        pose.mulPose(Axis.ZP.rotationDegrees(-kick * 4.0F + Mth.sin(t * 5.3F) * 1.4F * c * c * aim));
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void type95addition$restore(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing,
                                         ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light,
                                         CallbackInfo ci) {
        if (this.type95addition$moved) {
            this.type95addition$moved = false;
            pose.popPose();
        }
    }
}
