package hikari.autofighterai;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Small client GUI with independent switches. Open using /autofighterai. */
public final class AutoFighterScreen extends Screen {
    private ButtonWidget combatButton;
    private ButtonWidget dodgeButton;
    private ButtonWidget eatButton;

    public AutoFighterScreen() {
        super(Text.literal("Auto Fighter AI"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = Math.max(58, this.height / 2 - 72);
        int buttonWidth = Math.min(270, this.width - 36);
        int left = centerX - buttonWidth / 2;

        combatButton = this.addDrawableChild(ButtonWidget.builder(combatLabel(), button -> {
            AutoFighterAIClient.setCombatEnabled(!AutoFighterAIClient.combatEnabled());
            combatButton.setMessage(combatLabel());
        }).dimensions(left, top, buttonWidth, 22).build());

        dodgeButton = this.addDrawableChild(ButtonWidget.builder(dodgeLabel(), button -> {
            AutoFighterAIClient.setDodgeEnabled(!AutoFighterAIClient.dodgeEnabled());
            dodgeButton.setMessage(dodgeLabel());
        }).dimensions(left, top + 30, buttonWidth, 22).build());

        eatButton = this.addDrawableChild(ButtonWidget.builder(eatLabel(), button -> {
            AutoFighterAIClient.setAutoEatEnabled(!AutoFighterAIClient.autoEatEnabled());
            eatButton.setMessage(eatLabel());
        }).dimensions(left, top + 60, buttonWidth, 22).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Đóng"), button -> this.close())
            .dimensions(centerX - 60, top + 100, 120, 22).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 28, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Bật riêng từng tính năng theo tình huống"), this.width / 2, 42, 0xB8C5D6);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static Text combatLabel() {
        return Text.literal("Chiến đấu tự động: " + state(AutoFighterAIClient.combatEnabled()));
    }

    private static Text dodgeLabel() {
        return Text.literal("Né đòn: " + state(AutoFighterAIClient.dodgeEnabled()));
    }

    private static Text eatLabel() {
        return Text.literal("Tự ăn khi máu thấp: " + state(AutoFighterAIClient.autoEatEnabled()));
    }

    private static String state(boolean enabled) {
        return enabled ? "§aBẬT" : "§cTẮT";
    }
}
