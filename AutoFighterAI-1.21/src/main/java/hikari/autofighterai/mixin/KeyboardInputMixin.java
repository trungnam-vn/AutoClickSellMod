package hikari.autofighterai.mixin;

import hikari.autofighterai.AutoFighterAIClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores the AI-requested movement input while the vanilla inventory is open.
 * Minecraft 1.21 uses the older Input movement fields, not the later PlayerInput record.
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

        Input input = (Input) (Object) this;
        boolean forward = AutoFighterAIClient.aiForwardIntent();
        boolean left = AutoFighterAIClient.aiLeftIntent();
        boolean right = AutoFighterAIClient.aiRightIntent();

        input.pressingForward = forward;
        input.pressingBack = false;
        input.pressingLeft = left;
        input.pressingRight = right;
        input.movementForward = forward ? 1.0F : 0.0F;
        input.movementSideways = (right ? 1.0F : 0.0F) - (left ? 1.0F : 0.0F);
    }
}
