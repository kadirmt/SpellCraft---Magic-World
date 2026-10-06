package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * İstemci → sunucu: Büyü Masası'nda "Learn" tıklandığında hedef büyünün id'sini bildirir.
 * Sunucu bu id DIŞINDA istemciden HİÇBİR ŞEYE güvenmez — elindeki kitap, XP seviyesi,
 * envanterdeki malzeme ve kademe tamamlanma durumu tamamen sunucu tarafında yeniden
 * hesaplanır (bkz. SpellGating.attemptLearn). İstemcideki "eligible spells" listesi
 * yalnızca kozmetiktir.
 */
public record LearnSpellPayload(String spellId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LearnSpellPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "learn_spell"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LearnSpellPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, LearnSpellPayload::spellId, LearnSpellPayload::new);

    @Override
    public CustomPacketPayload.Type<LearnSpellPayload> type() {
        return TYPE;
    }
}
