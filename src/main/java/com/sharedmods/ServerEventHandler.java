package com.sharedmods;

import com.sharedmods.network.ModListPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class ServerEventHandler {

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Path sharedModsDir = getSharedModsDir();
        ensureDirectoryExists(sharedModsDir);

        MinecraftServer server = player.getServer();

        // Scan shared_mods on a background thread to avoid blocking the server tick.
        // SHA-256 was removed: the client only checks file existence, so hashing
        // every mod on login (reading gigabytes on the main thread) was pure waste.
        CompletableFuture
            .supplyAsync(() -> buildModList(sharedModsDir))
            .thenAcceptAsync(modInfos -> {
                if (modInfos.isEmpty() || player.isRemoved()) return;
                PacketDistributor.sendToPlayer(player, new ModListPayload(modInfos));
                SharedModsMod.LOGGER.info("Sent mod list ({} mod(s)) to {}",
                    modInfos.size(), player.getName().getString());
            }, server);  // re-enter the server thread for the packet send
    }

    private static List<ModListPayload.ModInfo> buildModList(Path sharedModsDir) {
        List<ModListPayload.ModInfo> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(sharedModsDir)) {
            files
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .forEach(file -> {
                    try {
                        String name = file.getFileName().toString();
                        long size = Files.size(file);
                        result.add(new ModListPayload.ModInfo(name, size, ""));
                        SharedModsMod.LOGGER.info("Sharing mod: {} ({} bytes)", name, size);
                    } catch (IOException e) {
                        SharedModsMod.LOGGER.error("Failed to read mod file info: {}", file, e);
                    }
                });
        } catch (IOException e) {
            SharedModsMod.LOGGER.error("Failed to list shared_mods directory", e);
        }
        return result;
    }

    static Path getSharedModsDir() {
        return FMLPaths.GAMEDIR.get().resolve("shared_mods");
    }

    private static void ensureDirectoryExists(Path dir) {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                SharedModsMod.LOGGER.info("Created shared_mods directory: {}", dir.toAbsolutePath());
            } catch (IOException e) {
                SharedModsMod.LOGGER.error("Failed to create shared_mods directory", e);
            }
        }
    }
}
