package dev.type95addition.client.mixin;

import dev.type95addition.client.ChargeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The zoom while charging the Lightning Formula (the J screen can turn it off). */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void type95addition$chargeZoom(boolean firstPerson, float partial, CallbackInfoReturnable<Float> cir) {
        if ((Object)this == Minecraft.getInstance().player) {
            float zoom = ChargeClient.zoom(partial);
            if (zoom != 1.0F) {
                cir.setReturnValue(cir.getReturnValueF() * zoom);
            }
        }
    }
}
