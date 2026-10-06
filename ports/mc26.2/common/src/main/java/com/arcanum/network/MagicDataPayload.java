package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sunucu → istemci: oyuncunun BÜYÜCÜ verisi (level/xp + skill düğümleri + mana). ManaHud
 * (hep görünür) ve skill ağacı paneli bundan okur. {@code nodes[4]} = cap, regen, cooldown,
 * power düğüm sayıları. {@code maxLevel}/{@code pointsPerLevel} = config'den gelen seviye
 * tavanı + seviye başına skill puanı (istemci "Level X/Y" ve kalan puan gösterimi için).
 * Elle-codec (8+ alan, composite 6 sınırını aşar).
 */
public record MagicDataPayload(int level, int xp, int xpToNext,
                               int mana, int manaCap, int[] nodes,
                               int maxLevel, int pointsPerLevel)
        implements CustomPacketPayload {

    public static final int NODE_COUNT = 4;

    public static final CustomPacketPayload.Type<MagicDataPayload> TYPE =
            new CustomPacketPayload.Type<>(Arcanum.id("magic_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MagicDataPayload> CODEC = new StreamCodec<>() {
        @Override
        public MagicDataPayload decode(RegistryFriendlyByteBuf buf) {
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
        public void encode(RegistryFriendlyByteBuf buf, MagicDataPayload p) {
            buf.writeVarInt(p.level());
            buf.writeVarInt(p.xp());
            buf.writeVarInt(p.xpToNext());
            buf.writeVarInt(p.mana());
            buf.writeVarInt(p.manaCap());
            int[] n = p.nodes();
            for (int i = 0; i < NODE_COUNT; i++) {
                buf.writeVarInt(n != null && i < n.length ? n[i] : 0);
            }
            buf.writeVarInt(p.maxLevel());
            buf.writeVarInt(p.pointsPerLevel());
        }
    };

    @Override
    public CustomPacketPayload.Type<MagicDataPayload> type() {
        return TYPE;
    }
}
