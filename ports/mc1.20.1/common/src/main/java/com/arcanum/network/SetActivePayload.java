package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * İstemci → sunucu: aktif dizilim slotunu (sayfa+slot) değiştirir
 * (+/- ile sayfa, Shift+tekerlek ile slot gezinme). Sunucu clamp'ler (0..2 / 0..3).
 */
public record SetActivePayload(int page, int slot) implements ArcanumPayload {

    public static final ResourceLocation ID = new ResourceLocation(Arcanum.MODID, "set_active");

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(page);
        buf.writeVarInt(slot);
    }

    public static SetActivePayload decode(FriendlyByteBuf buf) {
        return new SetActivePayload(buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
