package com.sharedmods;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@Mod(SharedModsMod.MOD_ID)
public class SharedModsMod {

    public static final String MOD_ID = "sharedmods";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SharedModsMod(IEventBus modEventBus) {
        modEventBus.addListener(NetworkHandler::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener(ServerEventHandler::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(SharedModsMod::onServerStopping);
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        ServerPayloadHandler.shutdownExecutor();
    }
}
