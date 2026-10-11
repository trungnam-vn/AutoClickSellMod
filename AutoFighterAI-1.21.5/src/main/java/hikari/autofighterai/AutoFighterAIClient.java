package hikari.autofighterai;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Visual-only target display. It does not attack, aim, move, eat, or switch items.
 */
public final class AutoFighterAIClient implements ClientModInitializer {
    public static boolean trackingEnabled = true;
    public static boolean targetGlowEnabled = true;

    private static final double RANGE_SQUARED = 20.0 * 20.0;
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir()
        .resolve("autofighterai.properties");

    private static KeyBinding toggleKey;
    private static KeyBinding guiKey;
    private static boolean guiOpenRequested;
    private static HostileEntity cachedTarget;
    private static HostileEntity glowingTarget;
    private static boolean previousTargetGlowState;
    private static int targetScanCooldown;

    @Override
    public void onInitializeClient() {
        loadSettings();
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.autofighterai.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G,
            "category.autofighterai"
        ));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.autofighterai.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H,
            "category.autofighterai"
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("autofighterai")
                .executes(context -> { openGui(); return 1; })
                .then(literal("gui").executes(context -> { openGui(); return 1; }))
                .then(literal("on").executes(context -> {
                    setTrackingEnabled(true);
                    context.getSource().sendFeedback(Text.literal("Target Display: ON"));
                    return 1;
                }))
                .then(literal("off").executes(context -> {
                    setTrackingEnabled(false);
                    context.getSource().sendFeedback(Text.literal("Target Display: OFF"));
                    return 1;
                })));
            dispatcher.register(literal("afai").executes(context -> { openGui(); return 1; }));
            dispatcher.register(literal("autofightergui").executes(context -> { openGui(); return 1; }));
        });

        ClientTickEvents.END_CLIENT_TICK.register(AutoFighterAIClient::onTick);
    }

    public static void setFeature(String name, boolean state) {
        switch (name.toLowerCase()) {
            case "tracking" -> setTrackingEnabled(state);
            case "glow" -> {
                targetGlowEnabled = state;
                if (!targetGlowEnabled) updateTargetGlow(null);
                saveSettings();
            }
            default -> { }
        }
    }

    private static void setTrackingEnabled(boolean state) {
        trackingEnabled = state;
        if (!trackingEnabled) {
            cachedTarget = null;
            targetScanCooldown = 0;
            updateTargetGlow(null);
        }
        saveSettings();
    }

    public static void saveSettings() {
        Properties p = new Properties();
        p.setProperty("tracking", Boolean.toString(trackingEnabled));
        p.setProperty("targetGlow", Boolean.toString(targetGlowEnabled));
        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG)) {
                p.store(out, "Auto Fighter AI visual target display settings");
            }
        } catch (Exception ignored) { }
    }

    private static void loadSettings() {
        if (!Files.exists(CONFIG)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG)) {
            p.load(in);
            trackingEnabled = Boolean.parseBoolean(p.getProperty("tracking", "true"));
            targetGlowEnabled = Boolean.parseBoolean(p.getProperty("targetGlow", "true"));
        } catch (Exception ignored) { }
    }

    private static void onTick(MinecraftClient client) {
        if (guiOpenRequested) {
            guiOpenRequested = false;
            client.setScreen(new AutoFighterScreen());
        }
        while (guiKey.wasPressed()) {
            client.setScreen(new AutoFighterScreen());
        }
        while (toggleKey.wasPressed()) {
            setTrackingEnabled(!trackingEnabled);
            if (client.player != null) {
                client.player.sendMessage(Text.literal(trackingEnabled
                    ? "§aTarget Display: ON" : "§7Target Display: OFF"), true);
            }
        }

        if (client.player == null || client.world == null || !trackingEnabled) {
            cachedTarget = null;
            targetScanCooldown = 0;
            updateTargetGlow(null);
            return;
        }

        HostileEntity target = getCachedTarget(client);
        updateTargetGlow(targetGlowEnabled ? target : null);
        if (target != null && client.player.age % 20 == 0) {
            double distance = Math.sqrt(client.player.squaredDistanceTo(target));
            client.player.sendMessage(Text.literal(String.format(
                "§bTarget: §f%s §7(%.1f blocks)", target.getName().getString(), distance
            )), true);
        }
    }

    // Hold the selected target until it dies, is removed, changes world, or leaves 20 blocks.
    private static HostileEntity getCachedTarget(MinecraftClient client) {
        if (cachedTarget != null && (!cachedTarget.isAlive() || cachedTarget.isRemoved()
            || cachedTarget.getWorld() != client.world
            || client.player.squaredDistanceTo(cachedTarget) > RANGE_SQUARED)) {
            cachedTarget = null;
            targetScanCooldown = 0;
        }

        if (cachedTarget == null && targetScanCooldown <= 0) {
            cachedTarget = nearestVisibleHostile(client);
            targetScanCooldown = 5;
        } else if (cachedTarget == null) {
            targetScanCooldown--;
        }
        return cachedTarget;
    }

    // Only consider hostiles the player can currently see; this is not an x-ray feature.
    private static HostileEntity nearestVisibleHostile(MinecraftClient client) {
        return client.world.getEntitiesByClass(
                HostileEntity.class,
                client.player.getBoundingBox().expand(20.0),
                e -> e.isAlive()
                    && !e.isRemoved()
                    && client.player.squaredDistanceTo(e) <= RANGE_SQUARED
                    && client.player.canSee(e)
            )
            .stream()
            .min(Comparator.comparingDouble(client.player::squaredDistanceTo))
            .orElse(null);
    }

    // Preserve an entity's previous glow state when the selected target changes.
    private static void updateTargetGlow(HostileEntity target) {
        if (glowingTarget != target) {
            if (glowingTarget != null && !glowingTarget.isRemoved()) {
                glowingTarget.setGlowing(previousTargetGlowState);
            }
            glowingTarget = target;
            if (glowingTarget != null && !glowingTarget.isRemoved()) {
                previousTargetGlowState = glowingTarget.isGlowing();
                glowingTarget.setGlowing(true);
            }
        } else if (glowingTarget != null && !glowingTarget.isRemoved()) {
            glowingTarget.setGlowing(true);
        }
    }

    private static void openGui() {
        guiOpenRequested = true;
    }
}
