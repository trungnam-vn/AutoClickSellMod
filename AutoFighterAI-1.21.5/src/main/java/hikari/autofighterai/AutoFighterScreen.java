package hikari.autofighterai;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class AutoFighterScreen extends Screen {
    private static final int BUTTON_W = 178;
    private static final int BUTTON_H = 20;
    private int leftX, rightX, topY;

    public AutoFighterScreen() { super(Text.literal("Auto Fighter AI — Control Panel")); }

    @Override
    protected void init() {
        int gap = 8;
        leftX = this.width / 2 - BUTTON_W - gap / 2;
        rightX = this.width / 2 + gap / 2;
        topY = this.height / 2 - 78;
        int y = topY;
        addToggle("Master AI", "ai", () -> AutoFighterAIClient.enabled, leftX, y);
        addToggle("Auto Attack", "attack", () -> AutoFighterAIClient.attackEnabled, rightX, y);
        y += 26;
        addToggle("Approach Target", "approach", () -> AutoFighterAIClient.approachEnabled, leftX, y);
        addToggle("Jump Attack (30%)", "jump", () -> AutoFighterAIClient.jumpAttackEnabled, rightX, y);
        y += 26;
        addToggle("Dodge Sideways", "dodge", () -> AutoFighterAIClient.dodgeEnabled, leftX, y);
        addToggle("Auto Eat at Low HP", "eat", () -> AutoFighterAIClient.autoEatEnabled, rightX, y);
        y += 26;
        addToggle("Mob / Health Alerts", "alerts", () -> AutoFighterAIClient.targetAlertsEnabled, leftX, y);
        addToggle("Target Glow Outline", "glow", () -> AutoFighterAIClient.targetGlowEnabled, rightX, y);
        y += 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
            .dimensions(rightX, y, BUTTON_W, BUTTON_H).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Enable All"), b -> {
            for (String n : new String[]{"ai","attack","approach","jump","dodge","eat","alerts","glow"})
                AutoFighterAIClient.setFeature(n, true);
            clearAndInit();
        }).dimensions(leftX, y, BUTTON_W, BUTTON_H).build());
    }

    private void addToggle(String label, String key, java.util.function.BooleanSupplier getter, int x, int y) {
        addDrawableChild(ButtonWidget.builder(buttonText(label, getter.getAsBoolean()), b -> {
            AutoFighterAIClient.setFeature(key, !getter.getAsBoolean());
            b.setMessage(buttonText(label, getter.getAsBoolean()));
        }).dimensions(x, y, BUTTON_W, BUTTON_H).build());
    }

    private Text buttonText(String label, boolean state) {
        return Text.literal(label + ": " + (state ? "ON" : "OFF"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, topY - 27, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Choose only the features you need; settings are saved."),
            width / 2, topY - 13, 0xB8C7FF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
