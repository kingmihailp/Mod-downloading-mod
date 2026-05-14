package com.sharedmods.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record ModListPayload(List<ModInfo> mods) implements CustomPacketPayload {

    public static final Type<ModListPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("sharedmods", "mod_list")
    );

    public static final StreamCodec<FriendlyByteBuf, ModListPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.mods().size());
            for (ModInfo mod : payload.mods()) {
                buf.writeUtf(mod.name());
                buf.writeLong(mod.size());
                buf.writeUtf(mod.hash());
            }
        },
        buf -> {
            int count = buf.readVarInt();
            List<ModInfo> mods = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                mods.add(new ModInfo(buf.readUtf(), buf.readLong(), buf.readUtf()));
            }
            return new ModListPayload(mods);
        }
    );

    public record ModInfo(String name, long size, String hash) {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
