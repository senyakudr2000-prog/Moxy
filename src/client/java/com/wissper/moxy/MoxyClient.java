package com.wissper.moxy;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoxyClient implements ClientModInitializer {
    public static final String MOD_ID = "moxy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Moxy] stage 1 skeleton loaded");
    }
}
