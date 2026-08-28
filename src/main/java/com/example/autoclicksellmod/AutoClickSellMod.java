package com.example.autoclicksellmod;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafxmod.FXModLanguageProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("autoclicksellmod")
public class AutoClickSellMod {
    public static final String MOD_ID = "autoclicksellmod";
    private static final Logger LOGGER = LogManager.getLogger();
    
    public static boolean ENABLED = false;
    
    public AutoClickSellMod() {
        LOGGER.info("Auto Click Sell Mod initialized!");
    }
}
