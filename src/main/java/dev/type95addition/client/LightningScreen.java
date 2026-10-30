package dev.type95addition.client;

import dev.type95addition.LightningTuning;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The Lightning Formula's settings (J): power, damage and bolts like Type 95's own sliders, the fire / terrain /
 * blast switches (greyed out when the server doesn't allow them), the colour, and the client-only effects.
 * Saved and sent to the server when the screen closes.
 */
public final class LightningScreen extends Screen {
    private static final int ROW = 24;
    private static final int WIDTH = 304;
    private static final int HALF = 150;

    public LightningScreen() {
        super(Component.translatable("gui.type95_addition.title"));
    }

    @Override
    protected void init() {
        int left = this.width / 2 - WIDTH / 2;
        int right = left + WIDTH - HALF;
        int y = Math.max(28, this.height / 2 - 112);

        this.addRenderableWidget(new Slider(left, y, WIDTH, powerToSlider(ClientConfig.power), v -> ClientConfig.power = sliderToPower(v),
            () -> Component.translatable("gui.type95_addition.power", String.format(Locale.ROOT, "%.1fx", ClientConfig.power))));
        y += ROW;
        this.addRenderableWidget(new Slider(left, y, WIDTH, damageToSlider(ClientConfig.damage), v -> ClientConfig.damage = sliderToDamage(v),
            () -> ClientConfig.damage == LightningTuning.INFINITE
                ? Component.translatable("gui.type95_addition.damage.infinite").withStyle(ChatFormatting.LIGHT_PURPLE)
                : Component.translatable("gui.type95_addition.damage", Math.round(ClientConfig.damage))));
        y += ROW;
        this.addRenderableWidget(new Slider(left, y, WIDTH, (ClientConfig.bolts - 1) / 15.0,
            v -> ClientConfig.bolts = 1 + (int)Math.round(v * 15.0),
            () -> Component.translatable("gui.type95_addition.bolts", ClientConfig.bolts)));
        y += ROW;

        this.toggle(left, y, "fire", ClientConfig.fire, ChargeClient.serverFire, v -> ClientConfig.fire = v);
        this.toggle(right, y, "terrain", ClientConfig.terrain, ChargeClient.serverTerrain, v -> ClientConfig.terrain = v);
        y += ROW;
        this.toggle(left, y, "blast", ClientConfig.blast, ChargeClient.serverBlast, v -> ClientConfig.blast = v);
        this.addRenderableWidget(CycleButton.builder(Palette::label, ClientConfig.palette)
            .withValues(List.of(Palette.values()))
            .create(right, y, HALF, 20, Component.translatable("gui.type95_addition.palette"), (button, value) -> ClientConfig.palette = value));
        y += ROW;
        this.toggle(left, y, "flash", ClientConfig.flash, true, v -> ClientConfig.flash = v);
        this.toggle(right, y, "shake", ClientConfig.shake, true, v -> ClientConfig.shake = v);
        y += ROW;
        this.toggle(left, y, "zoom", ClientConfig.zoom, true, v -> ClientConfig.zoom = v);
        this.toggle(right, y, "scope", ClientConfig.scope, true, v -> ClientConfig.scope = v);
        y += ROW;
        this.toggle(left, y, "marking", ClientConfig.marking, true, v -> ClientConfig.marking = v);
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).bounds(right, y, HALF, 20).build());
    }

    private void toggle(int x, int y, String key, boolean value, boolean allowed, Consumer<Boolean> set) {
        CycleButton<Boolean> button = CycleButton.onOffBuilder(value)
            .create(x, y, HALF, 20, Component.translatable("gui.type95_addition." + key), (b, v) -> set.accept(v));
        button.active = allowed;
        this.addRenderableWidget(button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int top = Math.max(28, this.height / 2 - 112);
        g.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), this.width / 2, top - 16, 0xFF000000 | ClientConfig.palette.glow);
        int y = top + ROW * 8 + 2;
        double scale = LightningTuning.scale(1.0F, ClientConfig.power);
        g.drawCenteredString(this.font, Component.translatable("gui.type95_addition.radius", Math.round(LightningTuning.radius(scale)),
            ClientConfig.blast ? String.valueOf(Math.round(LightningTuning.craterRadius(scale, 420.0))) : "-",
            Math.round(LightningTuning.shockRadius(scale))), this.width / 2, y, 0xFFD0D0D0);
        y += 11;
        String power = String.format(Locale.ROOT, "%.1fx", ChargeClient.maxPower);
        String damage = ChargeClient.maxDamage <= 0.0F ? "∞" : String.valueOf(Math.round(ChargeClient.maxDamage));
        g.drawCenteredString(this.font, Component.translatable("gui.type95_addition.limits", power, damage), this.width / 2, y, 0xFF909090);
        if (ClientConfig.power > 5.0F) {
            y += 11;
            g.drawCenteredString(this.font, Component.translatable("gui.type95_addition.warn"), this.width / 2, y, 0xFFFF7060);
        }
    }

    @Override
    public void removed() {
        ClientConfig.save();
        ChargeClient.sendTuning();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Power 0.1-10 and damage 2-1000 on log scales (like Type 95's sliders); the damage slider's far end is ∞.
    private static double powerToSlider(float power) {
        return Mth.clamp(Math.log(power / LightningTuning.MIN_POWER) / Math.log(200.0), 0.0, 1.0);
    }

    private static float sliderToPower(double v) {
        double p = LightningTuning.MIN_POWER * Math.pow(200.0, v);
        return (float)(p < 1.0 ? Math.round(p * 20.0) / 20.0 : Math.round(p * 10.0) / 10.0);
    }

    private static double damageToSlider(float damage) {
        if (damage == LightningTuning.INFINITE) {
            return 1.0;
        }
        return Mth.clamp(Math.log(damage / LightningTuning.MIN_DAMAGE) / Math.log(500.0) * 0.97, 0.0, 0.97);
    }

    private static float sliderToDamage(double v) {
        if (v > 0.985) {
            return LightningTuning.INFINITE;
        }
        return Math.round(LightningTuning.MIN_DAMAGE * Math.pow(500.0, Math.min(1.0, v / 0.97)));
    }

    private static final class Slider extends AbstractSliderButton {
        private final java.util.function.DoubleConsumer set;
        private final java.util.function.Supplier<Component> label;

        Slider(int x, int y, int width, double value, java.util.function.DoubleConsumer set, java.util.function.Supplier<Component> label) {
            super(x, y, width, 20, CommonComponents.EMPTY, value);
            this.set = set;
            this.label = label;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(this.label.get());
        }

        @Override
        protected void applyValue() {
            this.set.accept(this.value);
        }
    }
}
