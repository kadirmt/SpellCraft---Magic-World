package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Sunucu → istemci: cast (büyü yapma) DURUMU. Cast artık server-authoritative
 * {@link com.arcanum.item.CastManager}'da işlendiği için, istemci cast göstergesini
 * (imleç altı minik çubuk, bkz. CastBarHud) yalnızca bu pakete göre çizer.
 *
 * <p>{@code spellIndex} = cast BAŞLADI → ilgili {@link com.arcanum.spell.ModSpells}
 * global index'i; {@code totalTicks} = {@link com.arcanum.spell.SpellCastTime#ticks}.
 * {@code spellIndex = -1} → cast BİTTİ / YOK (istemci göstergeyi temizler).
 */
public record CastStatePayload(int spellIndex, int totalTicks) implements ArcanumPayload {

    public static final ResourceLocation ID = new ResourceLocation(Arcanum.MODID, "cast_state");

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(spellIndex);
        buf.writeVarInt(totalTicks);
    }

    public static CastStatePayload decode(FriendlyByteBuf buf) {
        return new CastStatePayload(buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
