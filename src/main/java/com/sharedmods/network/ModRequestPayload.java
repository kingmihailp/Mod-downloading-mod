package com.sharedmods.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record ModRequestPayload(List<String> requestedMods) implements CustomPacketPayload {

    public static final Type<ModRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("sharedmods", "mod_request")
    );

    public static final StreamCodec<FriendlyByteBuf, ModRequestPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.requestedMods().size());
            for (String name : payload.requestedMods()) {
                buf.writeUtf(name);
            }
        },
        buf -> {
            int count = buf.readVarInt();
            List<String> mods = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                mods.add(buf.readUtf());
            }
            return new ModRequestPayload(mods);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
