package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * İstemci → sunucu: büyü menüsünden (G) bir slota büyü atar/temizler.
 * {@code spellIndex} -1 → slotu temizle; ≥0 → o global büyü index'ini ata.
 * Sunucu TÜM doğrulamayı kendi yapar: page/slot aralığı + (atama ise) büyünün
 * gerçekten bilindiği ({@code SpellGating.knows}). İstemciden yalnızca istek gelir.
 */
public record AssignSlotPayload(int page, int slot, int spellIndex) implements ArcanumPayload {

    public static final ResourceLocation ID = new ResourceLocation(Arcanum.MODID, "assign_slot");

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(page);
        buf.writeVarInt(slot);
        buf.writeVarInt(spellIndex);
    }

    public static AssignSlotPayload decode(FriendlyByteBuf buf) {
        return new AssignSlotPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
