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
        super(Text.literal("Auto Fighter AI — Utilities"));
    }

    @Override
    protected void init() {
        int gap = 8;
        leftX = width / 2 - BUTTON_W - gap / 2;
        rightX = width / 2 + gap / 2;
        topY = height / 2 - 100;

        addToggle("AI Master (G)", "master", () -> AutoFighterAIClient.masterEnabled, leftX, topY);
        addToggle("Auto Eat (Low HP)", "eat", () -> AutoFighterAIClient.autoEatEnabled, rightX, topY);
        addToggle("Waypoint Movement", "movement", () -> AutoFighterAIClient.movementEnabled, leftX, topY + 25);
        addToggle("Waypoint Navigation", "navigation", () -> AutoFighterAIClient.navigationEnabled, rightX, topY + 25);

        addDrawableChild(ButtonWidget.builder(Text.literal("Arrival −0.5"), button -> {
            AutoFighterAIClient.adjustWaypointStopRadius(-0.5);
            clearAndInit();
        }).dimensions(leftX, topY + 60, BUTTON_W, BUTTON_H).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Arrival +0.5"), button -> {
            AutoFighterAIClient.adjustWaypointStopRadius(0.5);
            clearAndInit();
        }).dimensions(rightX, topY + 60, BUTTON_W, BUTTON_H).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Set Marker"), button -> {
            boolean ok = AutoFighterAIClient.markWaypointFromGui();
            if (!ok && MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(
                    Text.literal("§cLook at a block first, then click Set Marker."), true
                );
            }
            clearAndInit();
        }).dimensions(leftX, topY + 95, BUTTON_W, BUTTON_H).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Clear Marker"), button -> {
            AutoFighterAIClient.clearWaypointFromGui();
            clearAndInit();
        }).dimensions(rightX, topY + 95, BUTTON_W, BUTTON_H).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
            .dimensions(width / 2 - BUTTON_W / 2, topY + 130, BUTTON_W, BUTTON_H).build());
    }

    private void addToggle(String label, String key,
                           java.util.function.BooleanSupplier getter, int x, int y) {
        addDrawableChild(ButtonWidget.builder(buttonText(label, getter.getAsBoolean()), button -> {
            boolean nextState = !getter.getAsBoolean();
            if ("master".equals(key)) {
                AutoFighterAIClient.setMasterEnabled(nextState);
            } else {
                AutoFighterAIClient.setFeature(key, nextState);
            }
            clearAndInit();
        }).dimensions(x, y, BUTTON_W, BUTTON_H).build());
    }

    private Text buttonText(String label, boolean state) {
        return Text.literal(label + ": " + (state ? "ON" : "OFF"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, topY - 24, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("G = toggle all AI • H = open settings"),
            width / 2, topY - 10, 0xB8C7FF);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Waypoint arrival radius: " + String.format(java.util.Locale.ROOT, "%.1f", AutoFighterAIClient.waypointStopRadius)),
            width / 2, topY + 82, 0xFFD98A);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
