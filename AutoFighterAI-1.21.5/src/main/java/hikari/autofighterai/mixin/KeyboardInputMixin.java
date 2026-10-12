package hikari.autofighterai.mixin;

import hikari.autofighterai.AutoFighterAIClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores only the AI-requested movement input while the vanilla player inventory is open.
 * This is needed because vanilla keyboard input normally suppresses movement on screens.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void autofighterai$allowMovementInInventory(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof InventoryScreen)
            || !AutoFighterAIClient.shouldApplyInventoryAiInput()) {
            return;
        }

        KeyboardInput input = (KeyboardInput) (Object) this;
        input.playerInput = new PlayerInput(
            AutoFighterAIClient.aiForwardIntent(),
            false,
            AutoFighterAIClient.aiLeftIntent(),
            AutoFighterAIClient.aiRightIntent(),
            false,
            false,
            AutoFighterAIClient.aiSprintIntent()
        );
    }
}
