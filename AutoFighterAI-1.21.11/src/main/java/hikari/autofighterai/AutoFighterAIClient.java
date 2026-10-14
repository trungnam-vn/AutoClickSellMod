package hikari.autofighterai;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Survival utilities for Minecraft 1.21.11.
 * This port focuses on auto-eating and waypoint movement, with persistent settings.
 */
public final class AutoFighterAIClient implements ClientModInitializer {
    public static boolean masterEnabled = true;
    public static boolean movementEnabled = true;
    public static boolean autoEatEnabled = true;
    public static boolean navigationEnabled = false;
    public static boolean waypointSet = false;
    public static double waypointX;
    public static double waypointY;
    public static double waypointZ;

    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir()
        .resolve("autofighterai.properties");

    private static KeyBinding toggleKey;
    private static KeyBinding guiKey;
    private static boolean guiOpenRequested;
    private static String waypointDimension = "";
    private static boolean waypointReached;
    private static boolean aiForward;
    private static boolean aiSprint;
    private static boolean aiLeft;
    private static boolean aiRight;
    private static boolean originalForwardPressed;
    private static boolean originalSprintPressed;
    private static boolean originalLeftPressed;
    private static boolean originalRightPressed;
    private static int eatTicks;
    private static int previousSlot = -1;
    private static int foodUses;
    private static int ticks;

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
                .then(literal("gui").executes(context -> { openGui(); return 1; })));

            dispatcher.register(literal("afai")
                .executes(context -> { openGui(); return 1; })
                .then(literal("gui").executes(context -> { openGui(); return 1; }))
                .then(literal("eat").executes(context -> {
                    setFeature("eat", !autoEatEnabled);
                    context.getSource().sendFeedback(Text.literal("Auto Eat: "
                        + (autoEatEnabled ? "ON" : "OFF")));
                    return 1;
                }))
                .then(literal("move").executes(context -> {
                    setFeature("movement", !movementEnabled);
                    context.getSource().sendFeedback(Text.literal("Movement: "
                        + (movementEnabled ? "ON" : "OFF")));
                    return 1;
                }))
                .then(literal("mark").executes(context -> {
                    boolean result = setWaypointFromCrosshair(MinecraftClient.getInstance());
                    context.getSource().sendFeedback(Text.literal(result
                        ? "Waypoint set at the block you're looking at."
                        : "Look at a block first, then run /afai mark."));
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
        });

        ClientTickEvents.END_CLIENT_TICK.register(AutoFighterAIClient::onTick);
    }

    public static boolean shouldApplyInventoryAiInput() {
        return masterEnabled && movementEnabled && navigationEnabled && aiForward;
    }

    public static boolean aiForwardIntent() { return aiForward; }
    public static boolean aiLeftIntent() { return aiLeft; }
    public static boolean aiRightIntent() { return aiRight; }
    public static boolean aiSprintIntent() { return aiSprint; }

    public static void setFeature(String name, boolean state) {
        switch (name.toLowerCase()) {
            case "movement" -> movementEnabled = state;
            case "eat" -> autoEatEnabled = state;
            case "navigation", "nav" -> {
                setNavigationEnabled(state);
                return;
            }
            default -> { return; }
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (!state && (name.equalsIgnoreCase("movement")
            || name.equalsIgnoreCase("approach"))) {
            stopMovement(client);
        }
        if (!state && name.equalsIgnoreCase("eat")) stopEating(client);
        saveSettings();
    }

    public static void setMasterEnabled(boolean state) {
        masterEnabled = state;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!state) {
            stopMovement(client);
            stopEating(client);
        }
        saveSettings();
    }

    private static void setNavigationEnabled(boolean state) {
        navigationEnabled = state && waypointSet;
        waypointReached = false;
        if (!navigationEnabled) stopMovement(MinecraftClient.getInstance());
        saveSettings();
    }

    public static void saveSettings() {
        Properties p = new Properties();
        p.setProperty("master", Boolean.toString(masterEnabled));
        p.setProperty("movement", Boolean.toString(movementEnabled));
        p.setProperty("eat", Boolean.toString(autoEatEnabled));
        p.setProperty("navigation", Boolean.toString(navigationEnabled));
        p.setProperty("waypointSet", Boolean.toString(waypointSet));
        p.setProperty("waypointX", Double.toString(waypointX));
        p.setProperty("waypointY", Double.toString(waypointY));
        p.setProperty("waypointZ", Double.toString(waypointZ));
        p.setProperty("waypointDimension", waypointDimension);

        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG)) {
                p.store(out, "Auto Fighter AI 1.21.11 survival settings");
            }
        } catch (Exception ignored) { }
    }

    private static void loadSettings() {
        if (!Files.exists(CONFIG)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG)) {
            p.load(in);
            masterEnabled = Boolean.parseBoolean(p.getProperty("master", "true"));
            movementEnabled = Boolean.parseBoolean(p.getProperty("movement", "true"));
            autoEatEnabled = Boolean.parseBoolean(p.getProperty("eat", "true"));
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
        while (guiKey.wasPressed()) client.setScreen(new AutoFighterScreen());
        while (toggleKey.wasPressed()) {
            setMasterEnabled(!masterEnabled);
            if (client.player != null) {
                client.player.sendMessage(Text.literal(masterEnabled
                    ? "§aAuto Fighter AI: ON" : "§7Auto Fighter AI: OFF"), true);
            }
        }

        if (client.player == null || client.world == null) {
            stopMovement(client);
            stopEating(client);
            return;
        }

        // Keep the utility active while the vanilla inventory (E) is open.
        if (client.currentScreen != null && !(client.currentScreen instanceof InventoryScreen)) {
            stopMovement(client);
            if (eatTicks > 0) stopEating(client);
            return;
        }
        if (client.interactionManager == null) {
            stopMovement(client);
            return;
        }
        if (!masterEnabled) {
            stopMovement(client);
            if (eatTicks > 0) stopEating(client);
            return;
        }

        ticks++;

        if (eatTicks > 0 && autoEatEnabled) {
            stopMovement(client);
            client.options.useKey.setPressed(true);
            if (--eatTicks <= 0) stopEating(client);
            return;
        } else if (eatTicks > 0) {
            stopEating(client);
        }

        if (autoEatEnabled && client.player.getHealth() <= 10.0F && foodUses < 2
            && client.player.getHungerManager().getFoodLevel() < 20 && ticks % 8 == 0) {
            int foodSlot = findFoodSlot(client);
            if (foodSlot >= 0) {
                previousSlot = client.player.getInventory().selectedSlot;
                client.player.getInventory().selectedSlot = foodSlot;
                eatTicks = 36;
                foodUses++;
                client.options.useKey.setPressed(true);
                stopMovement(client);

                // The usual use-key is suppressed by the inventory screen, so start the
                // item interaction directly when E is open.
                if (client.currentScreen instanceof InventoryScreen
                    && client.interactionManager != null) {
                    client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                }
                return;
            }
        }

        if (client.player.getHealth() > 16.0F) foodUses = 0;

        if (masterEnabled && movementEnabled && navigationEnabled && waypointSet) {
            navigateToWaypoint(client);
        } else {
            stopMovement(client);
        }
        tryJumpOverObstacle(client);
    }

    private static int findFoodSlot(MinecraftClient client) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.get(DataComponentTypes.FOOD) != null) return i;
        }
        return -1;
    }

    private static void stopEating(MinecraftClient client) {
        if (client != null && client.options != null) client.options.useKey.setPressed(false);
        if (client != null && client.player != null && previousSlot >= 0 && previousSlot < 9) {
            client.player.getInventory().selectedSlot = previousSlot;
        }
        previousSlot = -1;
        eatTicks = 0;
    }

    public static boolean markWaypointFromGui() {
        return setWaypointFromCrosshair(MinecraftClient.getInstance());
    }

    public static void clearWaypointFromGui() {
        clearWaypoint();
    }

    private static boolean setWaypointFromCrosshair(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null
            || !(client.crosshairTarget instanceof BlockHitResult hit)) return false;

        BlockPos pos = hit.getBlockPos();
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
        if (!waypointSet || !waypointDimension.equals(
            client.world.getRegistryKey().getValue().toString())) {
            stopMovement(client);
            return;
        }

        var player = client.player;
        double dx = waypointX - player.getX();
        double dz = waypointZ - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance <= 1.2 && Math.abs(waypointY - player.getY()) <= 2.5) {
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
        setAISprint(client, distance > 6.0);
        if (player.horizontalCollision && player.isOnGround()) player.jump();
    }

    private static void tryJumpOverObstacle(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null
            || !aiForward || !client.player.isOnGround()) return;

        var player = client.player;
        double yawRadians = Math.toRadians(player.getYaw());
        double forwardX = -Math.sin(yawRadians);
        double forwardZ = Math.cos(yawRadians);
        BlockPos front = BlockPos.ofFloored(
            player.getX() + forwardX * 0.75,
            player.getY() + 0.05,
            player.getZ() + forwardZ * 0.75
        );
        BlockPos head = front.up();
        BlockPos aboveHead = head.up();

        boolean obstacleAtFeet = !client.world.getBlockState(front)
            .getCollisionShape(client.world, front).isEmpty();
        boolean headSpaceClear = client.world.getBlockState(head)
            .getCollisionShape(client.world, head).isEmpty();
        boolean aboveHeadClear = client.world.getBlockState(aboveHead)
            .getCollisionShape(client.world, aboveHead).isEmpty();

        if (obstacleAtFeet && headSpaceClear && aboveHeadClear) player.jump();
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

    private static void setAILeft(MinecraftClient client, boolean pressed) {
        if (client == null || client.options == null) return;
        if (pressed && !aiLeft) {
            originalLeftPressed = client.options.leftKey.isPressed();
            client.options.leftKey.setPressed(true);
            aiLeft = true;
        } else if (!pressed && aiLeft) {
            client.options.leftKey.setPressed(originalLeftPressed);
            aiLeft = false;
        }
    }

    private static void setAIRight(MinecraftClient client, boolean pressed) {
        if (client == null || client.options == null) return;
        if (pressed && !aiRight) {
            originalRightPressed = client.options.rightKey.isPressed();
            client.options.rightKey.setPressed(true);
            aiRight = true;
        } else if (!pressed && aiRight) {
            client.options.rightKey.setPressed(originalRightPressed);
            aiRight = false;
        }
    }

    private static void stopMovement(MinecraftClient client) {
        setAIForward(client, false);
        setAISprint(client, false);
        setAILeft(client, false);
        setAIRight(client, false);
    }
}
