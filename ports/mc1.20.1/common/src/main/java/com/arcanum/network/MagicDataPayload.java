package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Sunucu → istemci: oyuncunun BÜYÜCÜ verisi (level/xp + skill düğümleri + mana). ManaHud
 * (hep görünür) ve skill ağacı paneli bundan okur. {@code nodes[4]} = cap, regen, cooldown,
 * power düğüm sayıları. {@code maxLevel}/{@code pointsPerLevel} = config'den gelen seviye
 * tavanı + seviye başına skill puanı (istemci "Level X/Y" ve kalan puan gösterimi için).
 */
public record MagicDataPayload(int level, int xp, int xpToNext,
                               int mana, int manaCap, int[] nodes,
                               int maxLevel, int pointsPerLevel)
        implements ArcanumPayload {

    public static final int NODE_COUNT = 4;

    public static final ResourceLocation ID = new ResourceLocation(Arcanum.MODID, "magic_data");

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(level);
        buf.writeVarInt(xp);
        buf.writeVarInt(xpToNext);
        buf.writeVarInt(mana);
        buf.writeVarInt(manaCap);
        for (int i = 0; i < NODE_COUNT; i++) {
            buf.writeVarInt(nodes != null && i < nodes.length ? nodes[i] : 0);
        }
        buf.writeVarInt(maxLevel);
        buf.writeVarInt(pointsPerLevel);
    }

    public static MagicDataPayload decode(FriendlyByteBuf buf) {
        int level = buf.readVarInt();
        int xp = buf.readVarInt();
        int xpN = buf.readVarInt();
        int mana = buf.readVarInt();
        int cap = buf.readVarInt();
        int[] nodes = new int[NODE_COUNT];
        for (int i = 0; i < NODE_COUNT; i++) {
            nodes[i] = buf.readVarInt();
        }
        int maxLevel = buf.readVarInt();
        int ppl = buf.readVarInt();
        return new MagicDataPayload(level, xp, xpN, mana, cap, nodes, maxLevel, ppl);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
