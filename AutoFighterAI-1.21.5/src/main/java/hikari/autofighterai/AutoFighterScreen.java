package hikari.autofighterai;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class AutoFighterScreen extends Screen {
    private static final int BUTTON_W = 220;
    private static final int BUTTON_H = 20;
    private int topY;

    public AutoFighterScreen() {
        super(Text.literal("Auto Fighter AI — Target Display"));
    }

    @Override
    protected void init() {
        topY = height / 2 - 32;
        int x = width / 2 - BUTTON_W / 2;

        addDrawableChild(ButtonWidget.builder(
            buttonText("Target Tracking", AutoFighterAIClient.trackingEnabled), button -> {
                AutoFighterAIClient.setFeature("tracking", !AutoFighterAIClient.trackingEnabled);
                button.setMessage(buttonText("Target Tracking", AutoFighterAIClient.trackingEnabled));
            }).dimensions(x, topY, BUTTON_W, BUTTON_H).build());

        addDrawableChild(ButtonWidget.builder(
            buttonText("Target Glow Outline", AutoFighterAIClient.targetGlowEnabled), button -> {
                AutoFighterAIClient.setFeature("glow", !AutoFighterAIClient.targetGlowEnabled);
                button.setMessage(buttonText("Target Glow Outline", AutoFighterAIClient.targetGlowEnabled));
            }).dimensions(x, topY + 27, BUTTON_W, BUTTON_H).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
            .dimensions(x, topY + 54, BUTTON_W, BUTTON_H).build());
    }

    private Text buttonText(String label, boolean state) {
        return Text.literal(label + ": " + (state ? "ON" : "OFF"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, topY - 25, 0xFFFFFF);
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal("Visual-only • 20-block range • no auto-combat"),
            width / 2, topY - 10, 0xB8C7FF
        );
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
