package hikari.autofighterai;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.Random;

/** Auto Fighter AI for Fabric 1.21.3. Jump attack chance is 30%; aims at the torso. */
public final class AutoFighterAIClient implements ClientModInitializer {
    private static final double JUMP_ATTACK_CHANCE = 0.30;
    private static final double ATTACK_REACH = 3.35;
    private static final Random RANDOM = new Random();

    private static KeyBinding toggleKey;
    private static boolean enabled;
    private static int ticks;
    private static int eatTicks;
    private static int foodUses;
    private static int previousSlot = -1;
    private static int alertCooldown;

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
            if (!enabled) {
                stopEating(client);
                resetJumpAttack();
            }
            if (client.player != null) {
                client.player.sendMessage(Text.literal(enabled
                    ? "§aAuto Fighter AI: ON (G to turn off)"
                    : "§7Auto Fighter AI: OFF (G to turn on)"), true);
            }
        }
        if (!enabled || client.player == null || client.world == null || client.interactionManager == null) return;

        ticks++;
        if (alertCooldown > 0) alertCooldown--;

        if (eatTicks > 0) {
            client.options.useKey.setPressed(true);
            eatTicks--;
            if (eatTicks == 0) stopEating(client);
            return;
        }

        if (client.player.getHealth() <= 10.0F && foodUses < 2
            && client.player.getHungerManager().getFoodLevel() < 20 && ticks % 10 == 0) {
            int slot = findFoodSlot(client);
            if (slot >= 0) {
                resetJumpAttack();
                previousSlot = client.player.getInventory().selectedSlot;
                client.player.getInventory().selectedSlot = slot;
                eatTicks = 36;
                foodUses++;
                client.options.useKey.setPressed(true);
                return;
            }
        }

        if (jumpAttackPending) {
            HostileEntity pending = jumpAttackTarget;
            if (pending == null || !pending.isAlive() || pending.isRemoved()
                || client.player.squaredDistanceTo(pending) > 400.0) {
                resetJumpAttack();
            } else {
                aimAtTorso(client, pending);
                double d2 = client.player.squaredDistanceTo(pending);
                if (d2 <= ATTACK_REACH * ATTACK_REACH
                    && !client.player.isOnGround()
                    && client.player.getVelocity().y < 0.0
                    && client.player.fallDistance > 0.0F
                    && client.player.getAttackCooldownProgress(0.5F) >= 1.0F) {
                    attackTarget(client, pending);
                    resetJumpAttack();
                    return;
                }

                jumpAttackTicks++;
                if (d2 > ATTACK_REACH * ATTACK_REACH || jumpAttackTicks >= 24
                    || (client.player.isOnGround() && jumpAttackTicks > 3)) {
                    boolean canFallbackAttack = d2 <= ATTACK_REACH * ATTACK_REACH
                        && client.player.getAttackCooldownProgress(0.5F) >= 1.0F;
                    resetJumpAttack();
                    if (canFallbackAttack && pending.isAlive()) attackTarget(client, pending);
                    return;
                }
                return;
            }
        }

        HostileEntity target = client.world.getEntitiesByClass(
            HostileEntity.class,
            client.player.getBoundingBox().expand(20.0),
            entity -> entity.isAlive() && !entity.isRemoved()
                && client.player.squaredDistanceTo(entity) <= 400.0
        ).stream().min(Comparator.comparingDouble(client.player::squaredDistanceTo)).orElse(null);

        if (target == null) return;
        aimAtTorso(client, target);

        double distanceSquared = client.player.squaredDistanceTo(target);
        if (distanceSquared <= ATTACK_REACH * ATTACK_REACH
            && client.player.getAttackCooldownProgress(0.5F) >= 1.0F) {
            if (client.player.isOnGround() && RANDOM.nextDouble() < JUMP_ATTACK_CHANCE) {
                jumpAttackPending = true;
                jumpAttackTicks = 0;
                jumpAttackTarget = target;
                client.player.jump();
                return;
            }
            attackTarget(client, target);
        }

        if (alertCooldown == 0 && ticks % 20 == 0) {
            client.player.sendMessage(Text.literal("§c⚠ Hostile mob detected within 20 blocks."), true);
            alertCooldown = 80;
        }
    }

    private static void attackTarget(MinecraftClient client, HostileEntity target) {
        if (client.player == null || client.interactionManager == null || target == null || !target.isAlive()) return;
        client.interactionManager.attackEntity(client.player, target);
        client.player.swingHand(Hand.MAIN_HAND);
    }

    private static void resetJumpAttack() {
        jumpAttackPending = false;
        jumpAttackTicks = 0;
        jumpAttackTarget = null;
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

    private static void stopEating(MinecraftClient client) {
        if (client != null) client.options.useKey.setPressed(false);
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