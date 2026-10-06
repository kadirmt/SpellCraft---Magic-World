package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sunucu → istemci: oyuncunun büyü dizilimi (12 slot) + aktif sayfa/slot.
 * Girişte ve her dizilim değişikliğinde (atama / aktif değişimi) gönderilir;
 * istemci sol-alt HUD ile menüyü buna göre çizer.
 *
 * <p>{@code loadout} TAM 12 uzunlukta; her eleman global büyü index'i veya -1 (boş).
 * Sabit-uzunluk codec: 12 VarInt + activePage(VarInt) + activeSlot(VarInt).
 */
public record SpellLoadoutPayload(int[] loadout, int activePage, int activeSlot)
        implements CustomPacketPayload {

    /** Sabit dizilim uzunluğu (3 sayfa × 4 slot). */
    public static final int SIZE = 12;

    public static final CustomPacketPayload.Type<SpellLoadoutPayload> TYPE =
            new CustomPacketPayload.Type<>(Arcanum.id("spell_loadout"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpellLoadoutPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public SpellLoadoutPayload decode(RegistryFriendlyByteBuf buf) {
                    int[] loadout = new int[SIZE];
                    for (int i = 0; i < SIZE; i++) {
                        loadout[i] = buf.readVarInt();
                    }
                    int page = buf.readVarInt();
                    int slot = buf.readVarInt();
                    return new SpellLoadoutPayload(loadout, page, slot);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, SpellLoadoutPayload payload) {
                    int[] loadout = payload.loadout();
                    for (int i = 0; i < SIZE; i++) {
                        // null/kısa dizi güvenliği: eksik slotu -1 (boş) yaz — sabit 12 garanti
                        int v = (loadout != null && i < loadout.length) ? loadout[i] : -1;
                        buf.writeVarInt(v);
                    }
                    buf.writeVarInt(payload.activePage());
                    buf.writeVarInt(payload.activeSlot());
                }
            };

    @Override
    public CustomPacketPayload.Type<SpellLoadoutPayload> type() {
        return TYPE;
    }
}
