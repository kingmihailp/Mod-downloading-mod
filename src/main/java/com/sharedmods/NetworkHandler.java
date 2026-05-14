package com.sharedmods;

import com.sharedmods.network.ModChunkPayload;
import com.sharedmods.network.ModListPayload;
import com.sharedmods.network.ModRequestPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
            ModListPayload.TYPE,
            ModListPayload.STREAM_CODEC,
            ClientPayloadHandler::handleModList
        );

        registrar.playToServer(
            ModRequestPayload.TYPE,
            ModRequestPayload.STREAM_CODEC,
            ServerPayloadHandler::handleModRequest
        );

        registrar.playToClient(
            ModChunkPayload.TYPE,
            ModChunkPayload.STREAM_CODEC,
            ClientPayloadHandler::handleModChunk
        );
    }
}
