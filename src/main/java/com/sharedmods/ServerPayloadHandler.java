package com.sharedmods;

import com.sharedmods.network.ModChunkPayload;
import com.sharedmods.network.ModRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerPayloadHandler {

    private static final int CHUNK_SIZE = 256 * 1024;     // 256 KB per chunk
    private static final int CHUNKS_PER_BATCH = 8;        // 2 MB per batch
    private static final long BATCH_DELAY_MS = 20;        // pause between batches

    // Fixed pool prevents thread exhaustion when many players join simultaneously.
    // Daemon threads allow clean JVM exit if shutdown() is never called.
    static final ExecutorService TRANSFER_EXECUTOR = Executors.newFixedThreadPool(
        3,
        r -> {
            Thread t = new Thread(r, "sharedmods-transfer");
            t.setDaemon(true);
            return t;
        }
    );

    public static void handleModRequest(ModRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        List<String> requested = payload.requestedMods();
        SharedModsMod.LOGGER.info("Player {} requested {} mod(s)",
            player.getName().getString(), requested.size());

        Path sharedModsDir = ServerEventHandler.getSharedModsDir();

        TRANSFER_EXECUTOR.submit(() -> {
            for (String modName : requested) {
                if (player.isRemoved()) {
                    SharedModsMod.LOGGER.info("Player {} disconnected, aborting transfer",
                        player.getName().getString());
                    break;
                }

                if (!isValidModName(modName)) {
                    SharedModsMod.LOGGER.warn("Rejected invalid mod name from {}: {}",
                        player.getName().getString(), modName);
                    continue;
                }

                Path modFile = sharedModsDir.resolve(modName);

                // Guard against path traversal (e.g. "../../server.properties")
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

    public static void shutdownExecutor() {
        TRANSFER_EXECUTOR.shutdown();
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
            long fileSize = Files.size(modFile);
            if (fileSize == 0) {
                SharedModsMod.LOGGER.warn("Skipping empty mod file: {}", modName);
                return;
            }

            int totalChunks = (int) ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE);

            SharedModsMod.LOGGER.info("Sending {} to {} ({} bytes, {} chunk(s))",
                modName, player.getName().getString(), fileSize, totalChunks);

            // Stream the file chunk-by-chunk instead of Files.readAllBytes to
            // avoid loading potentially hundreds of MB into heap all at once.
            byte[] buffer = new byte[CHUNK_SIZE];
            int chunkIndex = 0;

            try (FileInputStream fis = new FileInputStream(modFile.toFile())) {
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    if (player.isRemoved()) {
                        SharedModsMod.LOGGER.info("Player {} disconnected mid-transfer of {}",
                            player.getName().getString(), modName);
                        return;
                    }

                    // Copy only the bytes that were actually read (last chunk may be smaller).
                    byte[] chunkData = (bytesRead == buffer.length)
                        ? buffer.clone()
                        : Arrays.copyOf(buffer, bytesRead);

                    PacketDistributor.sendToPlayer(player,
                        new ModChunkPayload(modName, chunkIndex, totalChunks, chunkData));

                    chunkIndex++;

                    // Rate-limit to avoid overwhelming Netty's write buffer.
                    // Without this, sending 400+ packets back-to-back causes disconnects.
                    if (chunkIndex % CHUNKS_PER_BATCH == 0) {
                        Thread.sleep(BATCH_DELAY_MS);
                    }
                }
            }

            SharedModsMod.LOGGER.info("Finished sending {} to {}", modName, player.getName().getString());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            SharedModsMod.LOGGER.warn("Transfer of {} was interrupted", modName);
        } catch (IOException e) {
            SharedModsMod.LOGGER.error("Failed to send mod {} to {}",
                modName, player.getName().getString(), e);
        }
    }
}
