package hikari.autofighterai;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;

/** Auto Fighter AI Safe Helper for Fabric 1.21.3. Alert-only: no aiming, jumping, or attacking. */
public final class AutoFighterAIClient implements ClientModInitializer {
    private static KeyBinding toggleKey;
    private static boolean enabled;
    private static int tickCounter;
    private static int mobAlertCooldown;
    private static int healthAlertCooldown;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.autofighterai.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G,
            "category.autofighterai"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(AutoFighterAIClient::onTick);
    }

    private static void onTick(MinecraftClient client) {
        while (toggleKey.wasPressed()) {
            enabled = !enabled;
            if (client.player != null) {
                client.player.sendMessage(Text.literal(enabled
                    ? "§aAuto Fighter AI Safe Helper: ON (G to turn off)"
                    : "§7Auto Fighter AI Safe Helper: OFF (G to turn on)"), true);
            }
        }
        if (!enabled || client.player == null || client.world == null) return;

        tickCounter++;
        if (mobAlertCooldown > 0) mobAlertCooldown--;
        if (healthAlertCooldown > 0) healthAlertCooldown--;

        if (client.player.getHealth() <= 10.0F && healthAlertCooldown == 0) {
            client.player.sendMessage(Text.literal("§c⚠ Health is at or below 10 HP."), true);
            healthAlertCooldown = 100;
        }

        if (mobAlertCooldown == 0 && tickCounter % 10 == 0) {
            HostileEntity nearest = client.world.getEntitiesByClass(
                HostileEntity.class,
                client.player.getBoundingBox().expand(20.0),
                entity -> entity.isAlive() && !entity.isRemoved()
            ).stream().min(Comparator.comparingDouble(client.player::squaredDistanceTo)).orElse(null);

            if (nearest != null) {
                client.player.sendMessage(Text.literal("§c⚠ Hostile mob detected within 20 blocks."), true);
                mobAlertCooldown = 100;
            }
        }
    }
}
