package hikari.autofighterai;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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

import java.util.Comparator;
import java.util.Random;

/**
 * Auto Fighter AI V2 for Fabric 1.21.3.
 * Targeting/attacking and movement are client-side automation; direct pathfinding is not included.
 */
public final class AutoFighterAIClient implements ClientModInitializer {
    private static final double TARGET_RANGE_SQUARED = 20.0 * 20.0;
    private static final double ATTACK_RANGE = 3.2;
    private static final double ATTACK_RANGE_SQUARED = ATTACK_RANGE * ATTACK_RANGE;
    private static final double JUMP_ATTACK_CHANCE = 0.30;
    private static final Random RANDOM = new Random();

    private static KeyBinding toggleKey;
    private static boolean enabled;
    private static int ticks;
    private static int eatTicks;
    private static int foodUses;
    private static int previousSlot = -1;
    private static int mobAlertCooldown;
    private static int healthAlertCooldown;
    private static int dodgeCooldown;
    private static int dodgeTicks;
    private static boolean dodgeLeft;
    private static boolean jumpAttackPending;
    private static int jumpAttackTicks;
    private static HostileEntity jumpAttackTarget;

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
            foodUses = 0;
            stopMovement(client);
            if (!enabled) {
                stopEating(client);
                resetJumpAttack();
                dodgeTicks = 0;
            }
            if (client.player != null) {
                client.player.sendMessage(Text.literal(enabled
                    ? "§aAuto Fighter AI V2: ON (G to turn off)"
                    : "§7Auto Fighter AI V2: OFF (G to turn on)"), true);
            }
        }

        if (!enabled || client.player == null || client.world == null || client.interactionManager == null) {
            stopMovement(client);
            return;
        }
        if (client.currentScreen != null) {
            stopMovement(client);
            return;
        }

        ticks++;
        if (mobAlertCooldown > 0) mobAlertCooldown--;
        if (healthAlertCooldown > 0) healthAlertCooldown--;
        if (dodgeCooldown > 0) dodgeCooldown--;

        if (eatTicks > 0) {
            stopMovement(client);
            client.options.useKey.setPressed(true);
            eatTicks--;
            if (eatTicks == 0) stopEating(client);
            return;
        }

        // Emergency food: at or below 10 HP, maximum two eating attempts per enable cycle.
        if (client.player.getHealth() <= 10.0F && foodUses < 2
            && client.player.getHungerManager().getFoodLevel() < 20 && ticks % 10 == 0) {
            int foodSlot = findFoodSlot(client);
            if (foodSlot >= 0) {
                stopMovement(client);
                resetJumpAttack();
                previousSlot = client.player.getInventory().selectedSlot;
                client.player.getInventory().selectedSlot = foodSlot;
                eatTicks = 36;
                foodUses++;
                client.options.useKey.setPressed(true);
                return;
            }
            if (healthAlertCooldown == 0) {
                client.player.sendMessage(Text.literal("§cLow health: no food found in hotbar."), true);
                healthAlertCooldown = 60;
            }
        }

        HostileEntity target = findNearestHostile(client);
        if (target == null) {
            stopMovement(client);
            resetJumpAttack();
            if (healthAlertCooldown == 0 && client.player.getHealth() <= 10.0F) {
                client.player.sendMessage(Text.literal("§c⚠ Health is at or below 10 HP."), true);
                healthAlertCooldown = 100;
            }
            return;
        }

        aimAtTorso(client, target);
        double distanceSquared = client.player.squaredDistanceTo(target);

        // A selected jump attack waits for the downward part of the jump before striking.
        if (jumpAttackPending) {
            if (jumpAttackTarget == null || !jumpAttackTarget.isAlive() || jumpAttackTarget.isRemoved()
                || client.player.squaredDistanceTo(jumpAttackTarget) > TARGET_RANGE_SQUARED) {
                resetJumpAttack();
            } else {
                target = jumpAttackTarget;
                aimAtTorso(client, target);
                distanceSquared = client.player.squaredDistanceTo(target);
                if (distanceSquared > ATTACK_RANGE_SQUARED) {
                    client.options.forwardKey.setPressed(true);
                } else {
                    client.options.forwardKey.setPressed(false);
                }
                if (distanceSquared <= ATTACK_RANGE_SQUARED
                    && !client.player.isOnGround()
                    && client.player.getVelocity().y < 0.0
                    && client.player.fallDistance > 0.0F
                    && client.player.getAttackCooldownProgress(0.5F) >= 1.0F) {
                    attackTarget(client, target);
                    resetJumpAttack();
                    return;
                }
                jumpAttackTicks++;
                if (jumpAttackTicks >= 24 || distanceSquared > TARGET_RANGE_SQUARED
                    || (client.player.isOnGround() && jumpAttackTicks > 3)) {
                    boolean canFallbackAttack = distanceSquared <= ATTACK_RANGE_SQUARED
                        && client.player.getAttackCooldownProgress(0.5F) >= 1.0F;
                    resetJumpAttack();
                    if (canFallbackAttack && target.isAlive()) attackTarget(client, target);
                    return;
                }
                return;
            }
        }

        // Move towards the selected hostile while outside melee distance; stop when close enough.
        client.options.forwardKey.setPressed(distanceSquared > ATTACK_RANGE_SQUARED);
        client.options.sprintKey.setPressed(distanceSquared > 64.0);

        // Occasionally strafe sideways while close to a hostile to make incoming attacks harder to land.
        if (distanceSquared <= 16.0 && dodgeCooldown == 0 && client.player.hurtTime > 0) {
            dodgeLeft = RANDOM.nextBoolean();
            dodgeTicks = 7;
            dodgeCooldown = 32;
        }
        if (dodgeTicks > 0) {
            client.options.leftKey.setPressed(dodgeLeft);
            client.options.rightKey.setPressed(!dodgeLeft);
            dodgeTicks--;
        } else {
            client.options.leftKey.setPressed(false);
            client.options.rightKey.setPressed(false);
        }

        if (distanceSquared <= ATTACK_RANGE_SQUARED
            && client.player.getAttackCooldownProgress(0.5F) >= 1.0F) {
            selectWeapon(client);
            if (client.player.isOnGround() && RANDOM.nextDouble() < JUMP_ATTACK_CHANCE) {
                jumpAttackPending = true;
                jumpAttackTicks = 0;
                jumpAttackTarget = target;
                client.options.forwardKey.setPressed(false);
                client.options.sprintKey.setPressed(false);
                client.options.jumpKey.setPressed(true);
                client.player.jump();
                client.options.jumpKey.setPressed(false);
                return;
            }
            attackTarget(client, target);
        }

        if (mobAlertCooldown == 0 && ticks % 10 == 0) {
            client.player.sendMessage(Text.literal("§c⚠ Hostile mob detected within 20 blocks."), true);
            mobAlertCooldown = 80;
        }
        if (healthAlertCooldown == 0 && client.player.getHealth() <= 10.0F) {
            client.player.sendMessage(Text.literal("§c⚠ Health is at or below 10 HP."), true);
            healthAlertCooldown = 100;
        }
    }

    private static HostileEntity findNearestHostile(MinecraftClient client) {
        return client.world.getEntitiesByClass(
            HostileEntity.class,
            client.player.getBoundingBox().expand(20.0),
            entity -> entity.isAlive() && !entity.isRemoved()
                && client.player.squaredDistanceTo(entity) <= TARGET_RANGE_SQUARED
        ).stream().min(Comparator.comparingDouble(client.player::squaredDistanceTo)).orElse(null);
    }

    private static void attackTarget(MinecraftClient client, HostileEntity target) {
        if (client.player == null || client.interactionManager == null || target == null || !target.isAlive()) return;
        client.options.forwardKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);
    }

    private static void selectWeapon(MinecraftClient client) {
        int bestSwordSlot = -1;
        int firstAxeSlot = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (stack.getItem() instanceof SwordItem) {
                bestSwordSlot = slot;
                break;
            }
            if (firstAxeSlot < 0 && stack.getItem() instanceof AxeItem) firstAxeSlot = slot;
        }
        int chosen = bestSwordSlot >= 0 ? bestSwordSlot : firstAxeSlot;
        if (chosen >= 0) client.player.getInventory().selectedSlot = chosen;
    }

    private static void aimAtTorso(MinecraftClient client, HostileEntity target) {
        var player = client.player;
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double dy = target.getY() + target.getHeight() * 0.55 - player.getEyeY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-MathHelper.atan2(dy, horizontal) * 180.0 / Math.PI);
        player.setYaw(yaw);
        player.setPitch(pitch);
    }

    private static void resetJumpAttack() {
        jumpAttackPending = false;
        jumpAttackTicks = 0;
        jumpAttackTarget = null;
    }

    private static void stopMovement(MinecraftClient client) {
        if (client == null || client.options == null) return;
        client.options.forwardKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
    }

    private static void stopEating(MinecraftClient client) {
        if (client != null && client.options != null) client.options.useKey.setPressed(false);
        if (client != null && client.player != null && previousSlot >= 0 && previousSlot < 9) {
            client.player.getInventory().selectedSlot = previousSlot;
        }
        previousSlot = -1;
        eatTicks = 0;
    }

    private static int findFoodSlot(MinecraftClient client) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.get(DataComponentTypes.FOOD) != null) return slot;
        }
        return -1;
    }
}
