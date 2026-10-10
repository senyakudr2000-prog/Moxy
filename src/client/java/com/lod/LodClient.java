package com.wissper.lod;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LodClient implements ClientModInitializer {
    public static final String MOD_ID = "mobile-lod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Mobile LOD] stage 1 skeleton loaded");
    }
}
