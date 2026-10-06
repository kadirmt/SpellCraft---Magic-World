package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Sunucu → istemci: oyuncunun büyü dizilimi (12 slot) + aktif sayfa/slot.
 * Girişte ve her dizilim değişikliğinde (atama / aktif değişimi) gönderilir;
 * istemci sol-alt HUD ile menüyü buna göre çizer.
 *
 * <p>{@code loadout} TAM 12 uzunlukta; her eleman global büyü index'i veya -1 (boş).
 * Sabit-uzunluk yazım: 12 VarInt + activePage(VarInt) + activeSlot(VarInt).
 */
public record SpellLoadoutPayload(int[] loadout, int activePage, int activeSlot)
        implements ArcanumPayload {

    /** Sabit dizilim uzunluğu (3 sayfa × 4 slot). */
    public static final int SIZE = 12;

    public static final ResourceLocation ID = new ResourceLocation(Arcanum.MODID, "spell_loadout");

    @Override
    public void encode(FriendlyByteBuf buf) {
        for (int i = 0; i < SIZE; i++) {
            // null/kısa dizi güvenliği: eksik slotu -1 (boş) yaz — sabit 12 garanti
            int v = (loadout != null && i < loadout.length) ? loadout[i] : -1;
            buf.writeVarInt(v);
        }
        buf.writeVarInt(activePage);
        buf.writeVarInt(activeSlot);
    }

    public static SpellLoadoutPayload decode(FriendlyByteBuf buf) {
        int[] loadout = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            loadout[i] = buf.readVarInt();
        }
        int page = buf.readVarInt();
        int slot = buf.readVarInt();
        return new SpellLoadoutPayload(loadout, page, slot);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
