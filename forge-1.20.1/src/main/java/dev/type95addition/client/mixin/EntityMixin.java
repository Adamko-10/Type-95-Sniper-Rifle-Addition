package dev.type95addition.client.mixin;

import dev.type95addition.client.fx.HomingFx;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A crushed entity's outline is the Homing Formula's magenta, a mob in a charging Gauss Formula's line pale blue (they
 * glow: see {@link MinecraftMixin}).
 */
@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void type95addition$crushOutline(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity)(Object)this;
        if (self.level().isClientSide && HomingFx.outlined(self)) {
            cir.setReturnValue(HomingFx.OUTLINE);
        } else if (self.level().isClientSide && dev.type95addition.client.fx.GaussFx.targeted(self)) {
            cir.setReturnValue(dev.type95addition.client.fx.GaussFx.OUTLINE);
        }
    }
}
