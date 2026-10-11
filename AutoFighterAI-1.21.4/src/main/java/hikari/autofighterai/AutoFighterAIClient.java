package hikari.autofighterai;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.Random;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class AutoFighterAIClient implements ClientModInitializer {
    public static boolean enabled = true;
    public static boolean attackEnabled = true;
    public static boolean approachEnabled = true;
    public static boolean jumpAttackEnabled = true;
    public static boolean dodgeEnabled = true;
    public static boolean autoEatEnabled = true;
    public static boolean targetAlertsEnabled = true;

    private static final double RANGE_SQUARED = 400.0;
    private static final double ATTACK_RANGE_SQUARED = 3.2 * 3.2;
    // Hysteresis prevents forward movement from rapidly toggling near the attack boundary.
    private static final double APPROACH_STOP_SQUARED = 2.25 * 2.25;
    private static final double APPROACH_RESUME_SQUARED = 3.0 * 3.0;
    private static final Random RANDOM = new Random();
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("autofighterai.properties");
    private static KeyBinding toggleKey, guiKey;
    private static boolean guiOpenRequested;
    private static int ticks, eatTicks, foodUses, previousSlot = -1;
    private static int mobCooldown, healthCooldown, dodgeCooldown, dodgeTicks, jumpTicks;
    private static boolean dodgeLeft, jumpPending;
    // Track only key states this mod itself pressed so normal player movement is not blocked.
    private static boolean aiForward, aiSprint, aiLeft, aiRight;
    private static boolean approachMoving;
    private static HostileEntity cachedTarget, jumpTarget;
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
                .executes(context -> {
                    openGui();
                    return 1;
                })
                .then(literal("gui").executes(context -> { openGui(); return 1; }))
                .then(literal("on").executes(context -> {
                    enabled = true; saveSettings();
                    context.getSource().sendFeedback(Text.literal("Auto Fighter AI: ON"));
                    return 1;
                }))
                .then(literal("off").executes(context -> {
                    enabled = false; saveSettings();
                    context.getSource().sendFeedback(Text.literal("Auto Fighter AI: OFF"));
                    return 1;
                }))
                .then(literal("feature")
                    .then(argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .then(argument("state", BoolArgumentType.bool()).executes(context -> {
                            String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name");
                            boolean state = BoolArgumentType.getBool(context, "state");
                            setFeature(name, state);
                            context.getSource().sendFeedback(Text.literal("Auto Fighter AI feature '" + name + "' = " + state));
                            return 1;
                        })))));
            dispatcher.register(literal("afai").executes(context -> { openGui(); return 1; }));
            dispatcher.register(literal("autofightergui").executes(context -> { openGui(); return 1; }));
        });
        ClientTickEvents.END_CLIENT_TICK.register(AutoFighterAIClient::onTick);
    }

    public static void setFeature(String name, boolean state) {
        switch (name.toLowerCase()) {
            case "attack" -> attackEnabled = state;
            case "approach" -> approachEnabled = state;
            case "jump" -> jumpAttackEnabled = state;
            case "dodge" -> dodgeEnabled = state;
            case "eat" -> autoEatEnabled = state;
            case "alerts" -> targetAlertsEnabled = state;
            case "ai" -> enabled = state;
            default -> { return; }
        }
        saveSettings();
    }

    public static void saveSettings() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("attack", Boolean.toString(attackEnabled));
        p.setProperty("approach", Boolean.toString(approachEnabled));
        p.setProperty("jump", Boolean.toString(jumpAttackEnabled));
        p.setProperty("dodge", Boolean.toString(dodgeEnabled));
        p.setProperty("eat", Boolean.toString(autoEatEnabled));
        p.setProperty("alerts", Boolean.toString(targetAlertsEnabled));
        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG)) { p.store(out, "Auto Fighter AI settings"); }
        } catch (Exception ignored) { }
    }

    private static void loadSettings() {
        if (!Files.exists(CONFIG)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG)) {
            p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            attackEnabled = Boolean.parseBoolean(p.getProperty("attack", "true"));
            approachEnabled = Boolean.parseBoolean(p.getProperty("approach", "true"));
            jumpAttackEnabled = Boolean.parseBoolean(p.getProperty("jump", "true"));
            dodgeEnabled = Boolean.parseBoolean(p.getProperty("dodge", "true"));
            autoEatEnabled = Boolean.parseBoolean(p.getProperty("eat", "true"));
            targetAlertsEnabled = Boolean.parseBoolean(p.getProperty("alerts", "true"));
        } catch (Exception ignored) { }
    }

    private static void onTick(MinecraftClient client) {
        // Commands are invoked while ChatScreen is still handling Enter. Defer screen
        // opening until the tick ends so ChatScreen cannot immediately close/overwrite it.
        if (guiOpenRequested) {
            guiOpenRequested = false;
            if (client != null) client.setScreen(new AutoFighterScreen());
        }
        while (guiKey.wasPressed()) {
            if (client != null) client.setScreen(new AutoFighterScreen());
        }
        while (toggleKey.wasPressed()) {
            enabled = !enabled;
            if (!enabled) {
                stopMovement(client);
                stopEating(client);
                resetJump();
            }
            saveSettings();
            if (client.player != null) client.player.sendMessage(Text.literal(enabled
                ? "§aAuto Fighter AI: ON" : "§7Auto Fighter AI: OFF"), true);
        }
        if (!enabled || client.player == null || client.world == null || client.interactionManager == null
            || client.currentScreen != null) {
            if (!enabled || client.currentScreen != null) stopMovement(client);
            return;
        }
        ticks++;
        if (mobCooldown > 0) mobCooldown--;
        if (healthCooldown > 0) healthCooldown--;
        if (dodgeCooldown > 0) dodgeCooldown--;

        if (eatTicks > 0) {
            stopMovement(client);
            client.options.useKey.setPressed(true);
            if (--eatTicks == 0) stopEating(client);
            return;
        }
        if (autoEatEnabled && client.player.getHealth() <= 10.0F && foodUses < 2
            && client.player.getHungerManager().getFoodLevel() < 20 && ticks % 10 == 0) {
            int slot = findFoodSlot(client);
            if (slot >= 0) {
                stopMovement(client);
                previousSlot = client.player.getInventory().selectedSlot;
                client.player.getInventory().selectedSlot = slot;
                eatTicks = 36;
                foodUses++;
                client.options.useKey.setPressed(true);
                return;
            }
        }

        HostileEntity target = getCachedTarget(client);
        if (target == null) {
            stopMovement(client);
            resetJump();
            if (targetAlertsEnabled && mobCooldown == 0 && ticks % 10 == 0) {
                // Only warn when low health; avoid noisy chat when no target exists.
                if (client.player.getHealth() <= 10.0F && healthCooldown == 0) {
                    client.player.sendMessage(Text.literal("§c⚠ Health is at or below 10 HP."), true);
                    healthCooldown = 100;
                }
            }
            return;
        }
        if (targetAlertsEnabled && mobCooldown == 0) {
            client.player.sendMessage(Text.literal("§c⚠ Hostile mob detected within 20 blocks."), true);
            mobCooldown = 100;
        }

        double dist = client.player.squaredDistanceTo(target);
        if (attackEnabled || approachEnabled || jumpAttackEnabled) aimAtTorso(client, target);

        if (jumpPending) {
            if (jumpTarget == null || !jumpTarget.isAlive() || jumpTarget.isRemoved()
                || client.player.squaredDistanceTo(jumpTarget) > RANGE_SQUARED) {
                resetJump();
            } else {
                target = jumpTarget;
                dist = client.player.squaredDistanceTo(target);
                if (approachEnabled) updateApproachMovement(client, dist);
                else {
                    approachMoving = false;
                    setAIForward(client, false);
                    setAISprint(client, false);
                }
                if (attackEnabled && dist <= ATTACK_RANGE_SQUARED && !client.player.isOnGround()
                    && client.player.getVelocity().y < 0 && client.player.fallDistance > 0
                    && client.player.getAttackCooldownProgress(0.5F) >= 1.0F) {
                    attack(client, target); resetJump(); return;
                }
                if (++jumpTicks >= 24 || (client.player.isOnGround() && jumpTicks > 3)) {
                    resetJump();
                } else return;
            }
        }

        if (approachEnabled) {
            updateApproachMovement(client, dist);
        } else {
            approachMoving = false;
            setAIForward(client, false);
            setAISprint(client, false);
        }

        if (dodgeEnabled && dist <= 16.0 && dodgeCooldown == 0 && client.player.hurtTime > 0) {
            dodgeLeft = RANDOM.nextBoolean();
            dodgeTicks = 7;
            dodgeCooldown = 32;
        }
        if (dodgeEnabled && dodgeTicks > 0) {
            setAILeft(client, dodgeLeft);
            setAIRight(client, !dodgeLeft);
            dodgeTicks--;
        } else {
            setAILeft(client, false);
            setAIRight(client, false);
        }

        if (attackEnabled && dist <= ATTACK_RANGE_SQUARED
            && client.player.getAttackCooldownProgress(0.5F) >= 1.0F) {
            selectWeapon(client);
            if (jumpAttackEnabled && client.player.isOnGround() && RANDOM.nextDouble() < 0.30) {
                jumpPending = true; jumpTicks = 0; jumpTarget = target;
                setAIForward(client, false);
                setAISprint(client, false);
                client.player.jump();
                return;
            }
            attack(client, target);
        }
    }

    // Refresh target selection every 4 ticks instead of scanning nearby entities on every tick.
    // Invalid/out-of-range targets are discarded immediately to keep response time predictable.
    private static HostileEntity getCachedTarget(MinecraftClient client) {
        if (cachedTarget != null && (!cachedTarget.isAlive() || cachedTarget.isRemoved()
            || client.player.squaredDistanceTo(cachedTarget) > RANGE_SQUARED)) {
            cachedTarget = null;
            targetScanCooldown = 0;
        }
        if (targetScanCooldown <= 0) {
            cachedTarget = nearestHostile(client);
            targetScanCooldown = 4;
        } else {
            targetScanCooldown--;
        }
        return cachedTarget;
    }

    private static HostileEntity nearestHostile(MinecraftClient client) {
        return client.world.getEntitiesByClass(HostileEntity.class, client.player.getBoundingBox().expand(20),
            e -> e.isAlive() && !e.isRemoved() && client.player.squaredDistanceTo(e) <= RANGE_SQUARED)
            .stream().min(Comparator.comparingDouble(client.player::squaredDistanceTo)).orElse(null);
    }

    private static void attack(MinecraftClient client, HostileEntity target) {
        // Do not clear the forward key on every hit: that produced a stop-go stutter
        // while the attack cooldown was refilling. Movement is controlled by the
        // hysteresis-based approach logic above.
        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);
    }

    private static void selectWeapon(MinecraftClient client) {
        int sword = -1, axe = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (s.getItem() instanceof SwordItem) { sword = i; break; }
            if (axe < 0 && s.getItem() instanceof AxeItem) axe = i;
        }
        int slot = sword >= 0 ? sword : axe;
        if (slot >= 0) client.player.getInventory().selectedSlot = slot;
    }

    private static void aimAtTorso(MinecraftClient client, HostileEntity target) {
        var p = client.player;
        double dx = target.getX() - p.getX(), dz = target.getZ() - p.getZ();
        double dy = target.getY() + target.getHeight() * 0.55 - p.getEyeY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float desiredYaw = (float)(MathHelper.atan2(dz, dx) * 180 / Math.PI) - 90;
        float yawDelta = MathHelper.wrapDegrees(desiredYaw - p.getYaw());
        p.setYaw(p.getYaw() + MathHelper.clamp(yawDelta, -9.0F, 9.0F));
        float desiredPitch = (float)(-MathHelper.atan2(dy, horizontal) * 180 / Math.PI);
        p.setPitch(p.getPitch() + MathHelper.clamp(desiredPitch - p.getPitch(), -6.0F, 6.0F));
    }

    private static int findFoodSlot(MinecraftClient client) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.get(DataComponentTypes.FOOD) != null) return i;
        }
        return -1;
    }

    private static void openGui() {
        guiOpenRequested = true;
    }

    private static void setAIForward(MinecraftClient client, boolean pressed) {
        if (client == null || client.options == null || aiForward == pressed) return;
        client.options.forwardKey.setPressed(pressed);
        aiForward = pressed;
    }

    private static void setAISprint(MinecraftClient client, boolean pressed) {
        if (client == null || client.options == null || aiSprint == pressed) return;
        client.options.sprintKey.setPressed(pressed);
        aiSprint = pressed;
    }

    private static void setAILeft(MinecraftClient client, boolean pressed) {
        if (client == null || client.options == null || aiLeft == pressed) return;
        client.options.leftKey.setPressed(pressed);
        aiLeft = pressed;
    }

    private static void setAIRight(MinecraftClient client, boolean pressed) {
        if (client == null || client.options == null || aiRight == pressed) return;
        client.options.rightKey.setPressed(pressed);
        aiRight = pressed;
    }

    private static void updateApproachMovement(MinecraftClient client, double dist) {
        if (dist >= APPROACH_RESUME_SQUARED) approachMoving = true;
        else if (dist <= APPROACH_STOP_SQUARED) approachMoving = false;
        setAIForward(client, approachMoving);
        setAISprint(client, approachMoving && dist > 64.0);
    }

    private static void stopMovement(MinecraftClient client) {
        approachMoving = false;
        setAIForward(client, false);
        setAISprint(client, false);
        setAILeft(client, false);
        setAIRight(client, false);
    }

    private static void stopEating(MinecraftClient client) {
        if (client != null && client.options != null) client.options.useKey.setPressed(false);
        if (client != null && client.player != null && previousSlot >= 0 && previousSlot < 9)
            client.player.getInventory().selectedSlot = previousSlot;
        previousSlot = -1; eatTicks = 0;
    }

    private static void resetJump() { jumpPending = false; jumpTicks = 0; jumpTarget = null; }
}
