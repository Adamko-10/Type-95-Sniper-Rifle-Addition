package dev.type95addition.client.fx;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * A charge's looping sound, following the player: the hum (rising in pitch and volume with the charge), or with
 * {@code fullLayer} the roar that joins it at full charge.
 */
final class ChargeSound extends AbstractTickableSoundInstance {
    private final Player player;
    private final boolean fullLayer;
    private int age;

    ChargeSound(Player player, SoundEvent sound, boolean fullLayer) {
        super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.fullLayer = fullLayer;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
        this.pitch = fullLayer ? 1.0F : 0.6F;
        this.x = player.getX();
        this.y = player.getEyeY();
        this.z = player.getZ();
    }

    @Override
    public void tick() {
        int held = ChargeFx.held(this.player.getId());
        if (held < 0 || this.player.isRemoved()) {
            this.stop();
            return;
        }
        this.age++;
        float c = ChargeFx.charge(held);
        if (this.fullLayer) {
            this.volume = Math.min(1.0F, this.age / 10.0F) * 0.9F;
            this.pitch = 0.95F + 0.05F * (float)Math.sin(this.age * 0.2);
        } else {
            this.volume = Math.min(1.0F, this.age / 6.0F) * (0.3F + 0.6F * c);
            this.pitch = 0.6F + 0.9F * c;
        }
        this.x = this.player.getX();
        this.y = this.player.getEyeY();
        this.z = this.player.getZ();
    }

    void end() {
        this.stop();
    }
}
