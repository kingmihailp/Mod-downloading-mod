package com.sharedmods;

import com.sharedmods.network.ModChunkPayload;
import com.sharedmods.network.ModRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ServerPayloadHandler {

    private static final int CHUNK_SIZE = 256 * 1024; // 256 KB

    public static void handleModRequest(ModRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        List<String> requested = payload.requestedMods();
        SharedModsMod.LOGGER.info("Player {} requested {} mod(s)",
            player.getName().getString(), requested.size());

        Path sharedModsDir = ServerEventHandler.getSharedModsDir();

        // Stream files from a background thread to avoid blocking the server tick
        CompletableFuture.runAsync(() -> {
            for (String modName : requested) {
                if (!isValidModName(modName)) {
                    SharedModsMod.LOGGER.warn("Rejected invalid mod name from {}: {}",
                        player.getName().getString(), modName);
                    continue;
                }

                Path modFile = sharedModsDir.resolve(modName);

                // Guard against path traversal
                if (!modFile.normalize().startsWith(sharedModsDir.normalize())) {
                    SharedModsMod.LOGGER.warn("Path traversal attempt blocked: {}", modName);
                    continue;
                }

                if (!Files.exists(modFile)) {
                    SharedModsMod.LOGGER.warn("Requested mod not found: {}", modName);
                    continue;
                }

                sendModInChunks(player, modName, modFile);
            }
        });
    }

    private static boolean isValidModName(String name) {
        return name != null
            && name.endsWith(".jar")
            && !name.contains("/")
            && !name.contains("\\")
            && !name.contains("..")
            && !name.isBlank();
    }

    private static void sendModInChunks(ServerPlayer player, String modName, Path modFile) {
        try {
            byte[] fileData = Files.readAllBytes(modFile);
            int totalChunks = Math.max(1, (int) Math.ceil((double) fileData.length / CHUNK_SIZE));

            SharedModsMod.LOGGER.info("Sending {} to {} ({} bytes, {} chunk(s))",
                modName, player.getName().getString(), fileData.length, totalChunks);

            for (int i = 0; i < totalChunks; i++) {
                int start = i * CHUNK_SIZE;
                int end = Math.min(start + CHUNK_SIZE, fileData.length);
                byte[] chunkData = new byte[end - start];
                System.arraycopy(fileData, start, chunkData, 0, chunkData.length);

                PacketDistributor.sendToPlayer(player, new ModChunkPayload(modName, i, totalChunks, chunkData));
            }

            SharedModsMod.LOGGER.info("Finished sending {} to {}", modName, player.getName().getString());
        } catch (IOException e) {
            SharedModsMod.LOGGER.error("Failed to send mod {} to {}",
                modName, player.getName().getString(), e);
        }
    }
}
