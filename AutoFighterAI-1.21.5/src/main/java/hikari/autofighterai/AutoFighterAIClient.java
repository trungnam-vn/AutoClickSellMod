package hikari.autofighterai;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client-side target highlighter and waypoint navigation helper.
 * It intentionally does not attack entities or select/use weapons.
 */
public final class AutoFighterAIClient implements ClientModInitializer {
    public static boolean trackingEnabled = true;
    public static boolean targetGlowEnabled = true;
    public static boolean navigationEnabled = false;
    public static boolean waypointSet = false;
    public static double waypointX;
    public static double waypointY;
    public static double waypointZ;

    private static final double RANGE_SQUARED = 20.0 * 20.0;
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir()
        .resolve("autofighterai.properties");

    private static KeyBinding toggleKey;
    private static KeyBinding guiKey;
    private static boolean guiOpenRequested;
    private static HostileEntity cachedTarget;
    private static int targetScanCooldown;
    private static String waypointDimension = "";
    private static boolean waypointReached;
    private static boolean aiForward;
    private static boolean aiSprint;
    private static boolean originalForwardPressed;
    private static boolean originalSprintPressed;

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
                    context.getSource().sendFeedback(Text.literal("Target tracking: ON"));
                    return 1;
                }))
                .then(literal("off").executes(context -> {
                    setTrackingEnabled(false);
                    context.getSource().sendFeedback(Text.literal("Target tracking: OFF"));
                    return 1;
                })));

            dispatcher.register(literal("afai")
                .executes(context -> { openGui(); return 1; })
                .then(literal("gui").executes(context -> { openGui(); return 1; }))
                .then(literal("mark").executes(context -> {
                    boolean result = setWaypointFromCrosshair(MinecraftClient.getInstance());
                    context.getSource().sendFeedback(Text.literal(result
                        ? "Waypoint saved at the block you're looking at."
                        : "Look at a block, then run /afai mark."));
                    return result ? 1 : 0;
                }))
                .then(literal("clear").executes(context -> {
                    clearWaypoint();
                    context.getSource().sendFeedback(Text.literal("Waypoint cleared."));
                    return 1;
                }))
                .then(literal("nav").executes(context -> {
                    setNavigationEnabled(!navigationEnabled);
                    context.getSource().sendFeedback(Text.literal("Waypoint navigation: "
                        + (navigationEnabled ? "ON" : "OFF")));
                    return 1;
                })));

            dispatcher.register(literal("autofightergui").executes(context -> { openGui(); return 1; }));
        });

        ClientTickEvents.END_CLIENT_TICK.register(AutoFighterAIClient::onTick);

        // Draw a bright, camera-relative outline around the locked target. This avoids
        // relying on the server-synchronized glowing entity flag, which can be reset.
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!trackingEnabled || !targetGlowEnabled || cachedTarget == null
                || context.world() == null || context.matrixStack() == null
                || context.consumers() == null) {
                return;
            }

            HostileEntity target = cachedTarget;
            if (target.isRemoved() || !target.isAlive() || target.getWorld() != context.world()) {
                return;
            }

            Vec3d camera = context.camera().getPos();
            Box localBox = target.getBoundingBox()
                .offset(-target.getX(), -target.getY(), -target.getZ())
                .expand(0.045);

            var matrices = context.matrixStack();
            matrices.push();
            matrices.translate(
                target.getX() - camera.x,
                target.getY() - camera.y,
                target.getZ() - camera.z
            );
            VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
            VertexRendering.drawBox(matrices, lines, localBox, 0.12F, 0.95F, 1.0F, 1.0F);
            matrices.pop();
        });
    }

    public static void setFeature(String name, boolean state) {
        switch (name.toLowerCase()) {
            case "tracking" -> setTrackingEnabled(state);
            case "glow" -> {
                targetGlowEnabled = state;
                saveSettings();
            }
            case "navigation", "nav" -> setNavigationEnabled(state);
            default -> { }
        }
    }

    private static void setTrackingEnabled(boolean state) {
        trackingEnabled = state;
        if (!state) {
            cachedTarget = null;
            targetScanCooldown = 0;
        }
        saveSettings();
    }

    private static void setNavigationEnabled(boolean state) {
        navigationEnabled = state && waypointSet;
        if (!navigationEnabled) {
            stopMovement(MinecraftClient.getInstance());
        } else {
            waypointReached = false;
        }
        saveSettings();
    }

    public static void saveSettings() {
        Properties p = new Properties();
        p.setProperty("tracking", Boolean.toString(trackingEnabled));
        p.setProperty("targetGlow", Boolean.toString(targetGlowEnabled));
        p.setProperty("navigation", Boolean.toString(navigationEnabled));
        p.setProperty("waypointSet", Boolean.toString(waypointSet));
        p.setProperty("waypointX", Double.toString(waypointX));
        p.setProperty("waypointY", Double.toString(waypointY));
        p.setProperty("waypointZ", Double.toString(waypointZ));
        p.setProperty("waypointDimension", waypointDimension);
        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG)) {
                p.store(out, "Auto Fighter AI target display and waypoint navigation");
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
            navigationEnabled = Boolean.parseBoolean(p.getProperty("navigation", "false"));
            waypointSet = Boolean.parseBoolean(p.getProperty("waypointSet", "false"));
            waypointX = Double.parseDouble(p.getProperty("waypointX", "0"));
            waypointY = Double.parseDouble(p.getProperty("waypointY", "0"));
            waypointZ = Double.parseDouble(p.getProperty("waypointZ", "0"));
            waypointDimension = p.getProperty("waypointDimension", "");
            if (!waypointSet) navigationEnabled = false;
        } catch (Exception ignored) {
            waypointSet = false;
            navigationEnabled = false;
        }
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
                    ? "§aTarget tracking: ON" : "§7Target tracking: OFF"), true);
            }
        }

        if (client.player == null || client.world == null) {
            cachedTarget = null;
            targetScanCooldown = 0;
            stopMovement(client);
            return;
        }

        if (client.currentScreen != null) {
            stopMovement(client);
            return;
        }

        if (trackingEnabled) {
            cachedTarget = getCachedTarget(client);
        } else {
            cachedTarget = null;
            targetScanCooldown = 0;
        }

        if (navigationEnabled) {
            navigateToWaypoint(client);
        } else {
            stopMovement(client);
        }
    }

    // Keep the selected hostile until it dies, is removed, changes world, or leaves 20 blocks.
    private static HostileEntity getCachedTarget(MinecraftClient client) {
        if (cachedTarget != null && (!cachedTarget.isAlive() || cachedTarget.isRemoved()
            || cachedTarget.getWorld() != client.world
            || client.player.squaredDistanceTo(cachedTarget) > RANGE_SQUARED)) {
            cachedTarget = null;
            targetScanCooldown = 0;
        }

        if (cachedTarget == null && targetScanCooldown <= 0) {
            cachedTarget = nearestVisibleHostile(client);
            targetScanCooldown = 4;
        } else if (cachedTarget == null) {
            targetScanCooldown--;
        }
        return cachedTarget;
    }

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

    public static boolean markWaypointFromGui() {
        return setWaypointFromCrosshair(MinecraftClient.getInstance());
    }

    public static void clearWaypointFromGui() {
        clearWaypoint();
    }

    private static boolean setWaypointFromCrosshair(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null
            || !(client.crosshairTarget instanceof BlockHitResult hit)) {
            return false;
        }

        var pos = hit.getBlockPos();
        waypointX = pos.getX() + 0.5;
        waypointY = pos.getY() + 1.0;
        waypointZ = pos.getZ() + 0.5;
        waypointDimension = client.world.getRegistryKey().getValue().toString();
        waypointSet = true;
        waypointReached = false;
        saveSettings();
        client.player.sendMessage(Text.literal(String.format(
            "§bWaypoint set: %.1f, %.1f, %.1f", waypointX, waypointY, waypointZ
        )), true);
        return true;
    }

    private static void clearWaypoint() {
        waypointSet = false;
        navigationEnabled = false;
        waypointDimension = "";
        waypointReached = false;
        stopMovement(MinecraftClient.getInstance());
        saveSettings();
    }

    private static void navigateToWaypoint(MinecraftClient client) {
        if (!waypointSet || !waypointDimension.equals(client.world.getRegistryKey().getValue().toString())) {
            stopMovement(client);
            return;
        }

        var player = client.player;
        double dx = waypointX - player.getX();
        double dz = waypointZ - player.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDistance <= 1.2 && Math.abs(waypointY - player.getY()) <= 2.5) {
            stopMovement(client);
            if (!waypointReached) {
                player.sendMessage(Text.literal("§aReached waypoint."), true);
                waypointReached = true;
            }
            return;
        }
        waypointReached = false;

        float desiredYaw = (float)(MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float yawDelta = MathHelper.wrapDegrees(desiredYaw - player.getYaw());
        player.setYaw(player.getYaw() + MathHelper.clamp(yawDelta, -6.0F, 6.0F));

        setAIForward(client, true);
        setAISprint(client, horizontalDistance > 6.0);

        // Basic obstacle assist: jump when walking into a block while grounded.
        if (player.horizontalCollision && player.isOnGround()) {
            player.jump();
        }
    }

    private static void openGui() {
        guiOpenRequested = true;
    }

    private static void setAIForward(MinecraftClient client, boolean pressed) {
        if (client == null || client.options == null) return;
        if (pressed && !aiForward) {
            originalForwardPressed = client.options.forwardKey.isPressed();
            client.options.forwardKey.setPressed(true);
            aiForward = true;
        } else if (!pressed && aiForward) {
            client.options.forwardKey.setPressed(originalForwardPressed);
            aiForward = false;
        }
    }

    private static void setAISprint(MinecraftClient client, boolean pressed) {
        if (client == null || client.options == null) return;
        if (pressed && !aiSprint) {
            originalSprintPressed = client.options.sprintKey.isPressed();
            client.options.sprintKey.setPressed(true);
            aiSprint = true;
        } else if (!pressed && aiSprint) {
            client.options.sprintKey.setPressed(originalSprintPressed);
            aiSprint = false;
        }
    }

    private static void stopMovement(MinecraftClient client) {
        setAIForward(client, false);
        setAISprint(client, false);
    }
}
