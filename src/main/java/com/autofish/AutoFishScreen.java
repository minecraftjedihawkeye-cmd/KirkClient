package com.autofish;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class AutoFishScreen extends Screen {

    private final Screen parent;

    public AutoFishScreen(Screen parent) {
        super(Text.literal("AutoFish Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = 40;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 26;

        // Enable / Disable toggle
        this.addDrawableChild(ButtonWidget.builder(
                getToggleText("AutoFish", AutoFishConfig.enabled),
                button -> {
                    AutoFishConfig.enabled = !AutoFishConfig.enabled;
                    button.setMessage(getToggleText("AutoFish", AutoFishConfig.enabled));
                    AutoFishConfig.save();
                }
        ).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());
        y += spacing;

        // Reel delay slider (0 - 500 ms)
        this.addDrawableChild(new ValueSlider(
                centerX - buttonWidth / 2, y, buttonWidth, buttonHeight,
                "Reel Delay", "ms", AutoFishConfig.reelDelay, 0, 500,
                value -> { AutoFishConfig.reelDelay = value; AutoFishConfig.save(); }
        ));
        y += spacing;

        // Recast delay slider (0 - 500 ms)
        this.addDrawableChild(new ValueSlider(
                centerX - buttonWidth / 2, y, buttonWidth, buttonHeight,
                "Recast Delay", "ms", AutoFishConfig.recastDelay, 0, 500,
                value -> { AutoFishConfig.recastDelay = value; AutoFishConfig.save(); }
        ));
        y += spacing;

        // Only activate when holding fishing rod
        this.addDrawableChild(ButtonWidget.builder(
                getToggleText("Only With Rod", AutoFishConfig.onlyWithRod),
                button -> {
                    AutoFishConfig.onlyWithRod = !AutoFishConfig.onlyWithRod;
                    button.setMessage(getToggleText("Only With Rod", AutoFishConfig.onlyWithRod));
                    AutoFishConfig.save();
                }
        ).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());
        y += spacing;

        // Randomize delays toggle
        this.addDrawableChild(ButtonWidget.builder(
                getToggleText("Randomize Delays", AutoFishConfig.randomizeDelays),
                button -> {
                    AutoFishConfig.randomizeDelays = !AutoFishConfig.randomizeDelays;
                    button.setMessage(getToggleText("Randomize Delays", AutoFishConfig.randomizeDelays));
                    AutoFishConfig.save();
                }
        ).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());
        y += spacing;

        // --- Safety section ---

        // Safe Mode toggle
        this.addDrawableChild(ButtonWidget.builder(
                getToggleText("Safe Mode", AutoFishConfig.safeMode),
                button -> {
                    AutoFishConfig.safeMode = !AutoFishConfig.safeMode;
                    button.setMessage(getToggleText("Safe Mode", AutoFishConfig.safeMode));
                    AutoFishConfig.save();
                }
        ).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());
        y += spacing;

        // Miss Chance slider (0 - 20%)
        this.addDrawableChild(new ValueSlider(
                centerX - buttonWidth / 2, y, buttonWidth, buttonHeight,
                "Miss Chance", "%", AutoFishConfig.missChance, 0, 20,
                value -> { AutoFishConfig.missChance = value; AutoFishConfig.save(); }
        ));
        y += spacing;

        // Auto Break slider (0 - 60 min)
        this.addDrawableChild(new AutoBreakSlider(
                centerX - buttonWidth / 2, y, buttonWidth, buttonHeight,
                AutoFishConfig.autoBreakMinutes, 0, 60,
                value -> { AutoFishConfig.autoBreakMinutes = value; AutoFishConfig.save(); }
        ));
        y += spacing + 10;

        // Done button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Done"),
                button -> close()
        ).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(
                this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private static Text getToggleText(String label, boolean value) {
        return Text.literal(label + ": " + (value ? "ON" : "OFF"));
    }

    // --- Generic value slider ---

    private static class ValueSlider extends SliderWidget {
        private final String label;
        private final String suffix;
        private final int minValue;
        private final int maxValue;
        private final IntConsumer onChanged;

        public ValueSlider(int x, int y, int width, int height,
                           String label, String suffix, int currentValue, int minValue, int maxValue,
                           IntConsumer onChanged) {
            super(x, y, width, height,
                    Text.literal(label + ": " + currentValue + " " + suffix),
                    clampNormalize(currentValue, minValue, maxValue));
            this.label = label;
            this.suffix = suffix;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.onChanged = onChanged;
        }

        private static double clampNormalize(int value, int min, int max) {
            if (max <= min) return 0;
            return (double) (value - min) / (max - min);
        }

        private int getDenormalizedValue() {
            return minValue + (int) (this.value * (maxValue - minValue));
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal(label + ": " + getDenormalizedValue() + " " + suffix));
        }

        @Override
        protected void applyValue() {
            onChanged.accept(getDenormalizedValue());
        }

        @FunctionalInterface
        interface IntConsumer {
            void accept(int value);
        }
    }

    // --- Auto break slider that shows "OFF" at 0 ---

    private static class AutoBreakSlider extends SliderWidget {
        private final int minValue;
        private final int maxValue;
        private final ValueSlider.IntConsumer onChanged;

        public AutoBreakSlider(int x, int y, int width, int height,
                               int currentValue, int minValue, int maxValue,
                               ValueSlider.IntConsumer onChanged) {
            super(x, y, width, height,
                    Text.literal(formatAutoBreak(currentValue)),
                    ValueSlider.clampNormalize(currentValue, minValue, maxValue));
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.onChanged = onChanged;
        }

        private int getDenormalizedValue() {
            return minValue + (int) (this.value * (maxValue - minValue));
        }

        private static String formatAutoBreak(int value) {
            if (value == 0) return "Auto Break: OFF";
            return "Auto Break: " + value + " min";
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal(formatAutoBreak(getDenormalizedValue())));
        }

        @Override
        protected void applyValue() {
            onChanged.accept(getDenormalizedValue());
        }
    }
}
