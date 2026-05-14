package com.sharedmods;

import com.sharedmods.network.ModChunkPayload;
import com.sharedmods.network.ModListPayload;
import com.sharedmods.network.ModRequestPayload;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientPayloadHandler {

    // Accumulates in-flight chunks: modName -> chunkIndex -> data
    private static final Map<String, Map<Integer, byte[]>> pendingChunks = new ConcurrentHashMap<>();
    private static final Map<String, Integer> expectedTotalChunks = new ConcurrentHashMap<>();

    public static void handleModList(ModListPayload payload, IPayloadContext context) {
        // Clear any leftover state from a previous session
        pendingChunks.clear();
        expectedTotalChunks.clear();

        context.enqueueWork(() -> {
            Path modsDir = FMLPaths.MODSDIR.get();
            List<String> needed = new ArrayList<>();

            for (ModListPayload.ModInfo info : payload.mods()) {
                Path modFile = modsDir.resolve(info.name());
                if (!Files.exists(modFile)) {
                    needed.add(info.name());
                    SharedModsMod.LOGGER.info("Queued for download: {}", info.name());
                } else {
                    SharedModsMod.LOGGER.info("Already present, skipping: {}", info.name());
                }
            }

            if (needed.isEmpty()) {
                SharedModsMod.LOGGER.info("All shared mods already present — nothing to download.");
                return;
            }

            context.player().displayClientMessage(
                Component.literal("[SharedMods] Downloading " + needed.size() + " mod(s) from server..."),
                false
            );

            PacketDistributor.sendToServer(new ModRequestPayload(needed));
        });
    }

    public static void handleModChunk(ModChunkPayload payload, IPayloadContext context) {
        String modName = payload.modName();
        int chunkIndex = payload.chunkIndex();
        int totalChunks = payload.totalChunks();

        Map<Integer, byte[]> chunks = pendingChunks.computeIfAbsent(modName, k -> new ConcurrentHashMap<>());
        chunks.put(chunkIndex, payload.data());
        expectedTotalChunks.put(modName, totalChunks);

        if (chunks.size() == totalChunks) {
            context.enqueueWork(() -> {
                assembleAndSave(modName, chunks, totalChunks, context);
                pendingChunks.remove(modName);
                expectedTotalChunks.remove(modName);
            });
        }
    }

    private static void assembleAndSave(String modName, Map<Integer, byte[]> chunks,
                                        int totalChunks, IPayloadContext context) {
        try {
            int totalSize = 0;
            for (int i = 0; i < totalChunks; i++) totalSize += chunks.get(i).length;

            byte[] fileData = new byte[totalSize];
            int offset = 0;
            for (int i = 0; i < totalChunks; i++) {
                byte[] chunk = chunks.get(i);
                System.arraycopy(chunk, 0, fileData, offset, chunk.length);
                offset += chunk.length;
            }

            Path outputFile = FMLPaths.MODSDIR.get().resolve(modName);
            Files.write(outputFile, fileData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            SharedModsMod.LOGGER.info("Saved downloaded mod: {} ({} bytes)", modName, fileData.length);

            context.player().displayClientMessage(
                Component.literal("[SharedMods] Downloaded: " + modName
                    + " — restart Minecraft to load it!"),
                false
            );
        } catch (IOException e) {
            SharedModsMod.LOGGER.error("Failed to save downloaded mod: {}", modName, e);
            context.player().displayClientMessage(
                Component.literal("[SharedMods] ERROR: could not save " + modName),
                false
            );
        }
    }
}
