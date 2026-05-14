package com.sharedmods.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ModChunkPayload(String modName, int chunkIndex, int totalChunks, byte[] data)
        implements CustomPacketPayload {

    public static final Type<ModChunkPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("sharedmods", "mod_chunk")
    );

    // Must be >= server-side CHUNK_SIZE (256 KB)
    private static final int MAX_CHUNK_BYTES = 512 * 1024;

    public static final StreamCodec<FriendlyByteBuf, ModChunkPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.modName());
            buf.writeVarInt(payload.chunkIndex());
            buf.writeVarInt(payload.totalChunks());
            buf.writeByteArray(payload.data());
        },
        buf -> new ModChunkPayload(
            buf.readUtf(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readByteArray(MAX_CHUNK_BYTES)
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
