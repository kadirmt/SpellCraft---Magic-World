package com.arcanum.network;

import com.arcanum.Arcanum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * İstemci → sunucu: Büyü Masası'nda "Learn" tıklandığında hedef büyünün id'sini bildirir.
 * Sunucu bu id DIŞINDA istemciden HİÇBİR ŞEYE güvenmez — elindeki kitap, XP seviyesi,
 * envanterdeki malzeme ve kademe tamamlanma durumu tamamen sunucu tarafında yeniden
 * hesaplanır (bkz. SpellGating.attemptLearn). İstemcideki "eligible spells" listesi
 * yalnızca kozmetiktir.
 */
public record LearnSpellPayload(String spellId) implements ArcanumPayload {

    public static final ResourceLocation ID = new ResourceLocation(Arcanum.MODID, "learn_spell");

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(spellId);
    }

    public static LearnSpellPayload decode(FriendlyByteBuf buf) {
        return new LearnSpellPayload(buf.readUtf());
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
