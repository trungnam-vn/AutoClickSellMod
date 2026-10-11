package hikari.autofighterai;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class AutoFighterScreen extends Screen {
    private static final int BUTTON_W = 166;
    private static final int BUTTON_H = 20;
    private int leftX;
    private int rightX;
    private int topY;

    public AutoFighterScreen() {
        super(Text.literal("Auto Fighter AI — Target & Navigation"));
    }

    @Override
    protected void init() {
        int gap = 8;
        leftX = width / 2 - BUTTON_W - gap / 2;
        rightX = width / 2 + gap / 2;
        topY = height / 2 - 38;

        addToggle("Target Tracking", "tracking",
            () -> AutoFighterAIClient.trackingEnabled, leftX, topY);
        addToggle("Target Outline", "glow",
            () -> AutoFighterAIClient.targetGlowEnabled, rightX, topY);

        addToggle("Waypoint Navigation", "navigation",
            () -> AutoFighterAIClient.navigationEnabled, leftX, topY + 25);
        addDrawableChild(ButtonWidget.builder(Text.literal("Set Marker"), button -> {
            boolean ok = AutoFighterAIClient.markWaypointFromGui();
            if (!ok && MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(
                    Text.literal("§cLook at a block first, then click Set Marker."), true
                );
            }
            clearAndInit();
        }).dimensions(rightX, topY + 25, BUTTON_W, BUTTON_H).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Clear Marker"), button -> {
            AutoFighterAIClient.clearWaypointFromGui();
            clearAndInit();
        }).dimensions(leftX, topY + 50, BUTTON_W, BUTTON_H).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
            .dimensions(rightX, topY + 50, BUTTON_W, BUTTON_H).build());
    }

    private void addToggle(String label, String key,
                           java.util.function.BooleanSupplier getter, int x, int y) {
        addDrawableChild(ButtonWidget.builder(buttonText(label, getter.getAsBoolean()), button -> {
            AutoFighterAIClient.setFeature(key, !getter.getAsBoolean());
            button.setMessage(buttonText(label, getter.getAsBoolean()));
        }).dimensions(x, y, BUTTON_W, BUTTON_H).build());
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
            Text.literal("Look at a block to set a marker. G = tracking, H = GUI."),
            width / 2, topY - 11, 0xB8C7FF
        );
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
