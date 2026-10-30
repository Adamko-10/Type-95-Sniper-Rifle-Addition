package dev.type95addition.client.mixin;

import dev.type95addition.client.Scope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scrolling while looking through the scope zooms it instead of changing the hotbar slot. */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void type95addition$scopeZoom(long window, double dx, double dy, CallbackInfo ci) {
        if (window == this.minecraft.getWindow().handle() && this.minecraft.screen == null && Scope.scroll(dy)) {
            ci.cancel();
        }
    }
}
