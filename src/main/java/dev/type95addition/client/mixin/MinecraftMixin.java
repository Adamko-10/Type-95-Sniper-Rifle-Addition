package dev.type95addition.client.mixin;

import dev.type95addition.client.ChargeClient;
import dev.type95addition.client.Marking;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Right click with the rifle in Lightning mode: the addon's charge starts instead of the rifle's own use, so none of
 * Type 95's charge, scope or shot code runs (they all wait for the player to be using the rifle). While charging
 * with marking on, left click marks the target ({@link Marking}) instead of punching or digging.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void type95addition$lightningFormula(CallbackInfo ci) {
        if (ChargeClient.interceptUse()) {
            ci.cancel();
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void type95addition$mark(CallbackInfoReturnable<Boolean> cir) {
        if (Marking.takesAttack()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void type95addition$markHeld(boolean down, CallbackInfo ci) {
        if (Marking.takesAttack()) {
            ci.cancel();
        }
    }
}
