package com.sharedmods;

import com.sharedmods.network.ModListPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

public class ServerEventHandler {

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Path sharedModsDir = getSharedModsDir();
        ensureDirectoryExists(sharedModsDir);

        List<ModListPayload.ModInfo> modInfos = new ArrayList<>();

        try (Stream<Path> files = Files.list(sharedModsDir)) {
            files
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .forEach(file -> {
                    try {
                        String name = file.getFileName().toString();
                        long size = Files.size(file);
                        String hash = computeSha256(file);
                        modInfos.add(new ModListPayload.ModInfo(name, size, hash));
                        SharedModsMod.LOGGER.info("Sharing mod: {} ({} bytes)", name, size);
                    } catch (IOException | NoSuchAlgorithmException e) {
                        SharedModsMod.LOGGER.error("Failed to process shared mod: {}", file, e);
                    }
                });
        } catch (IOException e) {
            SharedModsMod.LOGGER.error("Failed to list shared_mods directory", e);
            return;
        }

        if (!modInfos.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new ModListPayload(modInfos));
            SharedModsMod.LOGGER.info("Sent mod list ({} mod(s)) to player: {}",
                modInfos.size(), player.getName().getString());
        }
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

    static String computeSha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(Files.readAllBytes(file));
        return HexFormat.of().formatHex(hashBytes);
    }
}
