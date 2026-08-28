package com.example.autoclicksellmod.client;

import com.example.autoclicksellmod.AutoClickSellMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = AutoClickSellMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {
    
    private static int clickDelay = 0;
    private static boolean wasPressed = false;
    
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        
        if (mc.screen == null) return;
        
        // Toggle mod with V key
        boolean vKeyPressed = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_V) == GLFW.GLFW_PRESS;
        
        if (vKeyPressed && !wasPressed) {
            AutoClickSellMod.ENABLED = !AutoClickSellMod.ENABLED;
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                        "§aAuto Click: " + (AutoClickSellMod.ENABLED ? "§a✓ ON" : "§c✗ OFF")
                    ), true
                );
            }
            wasPressed = true;
        } else if (!vKeyPressed) {
            wasPressed = false;
        }
        
        if (!AutoClickSellMod.ENABLED || mc.screen == null) return;
        
        String screenTitle = mc.screen.getTitle().getString().toLowerCase();
        
        // Auto click SELL button
        if ((screenTitle.contains("sell") || screenTitle.contains("shop") || screenTitle.contains("trade")) 
            && mc.screen instanceof AbstractContainerScreen) {
            
            clickDelay++;
            if (clickDelay > 5) {
                // Try to find and click sell button
                if (mc.screen instanceof AbstractContainerScreen<?> screen) {
                    screen.children().stream()
                        .filter(widget -> widget instanceof net.minecraft.client.gui.components.Button)
                        .map(widget -> (net.minecraft.client.gui.components.Button) widget)
                        .forEach(btn -> {
                            String label = btn.getMessage().getString().toLowerCase();
                            if (label.contains("sell") || label.contains("confirm") || label.contains("accept")) {
                                btn.onPress();
                            }
                        });
                }
                clickDelay = 0;
            }
        }
    }
}
