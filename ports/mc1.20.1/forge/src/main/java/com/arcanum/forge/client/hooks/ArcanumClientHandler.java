package com.arcanum.forge.client.hooks;

import java.util.List;
import java.util.UUID;

import com.arcanum.client.beam.ClientSpellBeams;
import com.arcanum.forge.client.state.ClientCastState;
import com.arcanum.forge.client.state.ClientLockState;
import com.arcanum.forge.client.state.ClientMagicData;
import com.arcanum.forge.client.state.ClientSpellData;
import com.arcanum.forge.client.ui.ArcanumLevelUpToast;
import com.arcanum.forge.client.ui.SpellTableScreen;
import com.arcanum.network.ArcanumClientHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * {@link ArcanumClientHooks.Handler} Forge implementasyonu — fabric'teki anonim handler'ın
 * birebir karşılığı. S2C receiver'lar common'da ({@code ArcanumNetwork.registerS2CReceivers});
 * istemci tarafı işler bu sınıf üzerinden client-yalnız durum tutuculara bağlanır.
 * Handler ZATEN ana istemci thread'inde çağrılır (ctx.queue), ekstra execute gerekmez.
 * SIRA ÖNEMLİ: önce {@code setHandler(new ArcanumClientHandler())}, SONRA
 * {@code registerS2CReceivers()} (f3-network-contract.md).
 */
public final class ArcanumClientHandler implements ArcanumClientHooks.Handler {

    // Bilinen büyü listesi senkronu
    @Override
    public void setKnownSpells(List<String> spellIds) {
        ClientSpellData.set(spellIds);
    }

    // Büyü dizilimi senkronu (12 slot + aktif sayfa/slot)
    @Override
    public void setLoadout(int[] loadout, int activePage, int activeSlot) {
        ClientSpellData.setLoadout(loadout, activePage, activeSlot);
    }

    // Cast durumu senkronu (S2C): cast başladı (spellIndex>=0) / bitti-iptal (spellIndex<0).
    // ClientCastState.set spellIndex<0 / totalTicks<=0 gelince kendi içinde clear() yapar.
    @Override
    public void setCastState(int spellIndex, int totalTicks) {
        ClientCastState.set(spellIndex, totalTicks);
    }

    // Asa kenetlenmesi durumu senkronu (S2C): ışın/düğüm/HUD buna göre çizilir/temizlenir.
    @Override
    public void setLockState(UUID a, UUID b, float node, int colorA, int colorB, int phase) {
        ClientLockState.set(a, b, node, colorA, colorB, phase);
    }

    // Büyü ışını olayı (S2C): yıldırım-tarzı ışın istemcide çizilir, ~0.5 sn iz bırakır.
    @Override
    public void addSpellBeam(double fromX, double fromY, double fromZ,
                             double toX, double toY, double toZ,
                             int color, int life, float width, float spread) {
        ClientSpellBeams.add(new Vec3(fromX, fromY, fromZ), new Vec3(toX, toY, toZ),
                color, life, width, spread);
    }

    // Büyücü verisi (level/xp/skill/mana) senkron — ManaHud + skill paneli buradan okur.
    @Override
    public void setMagicData(int level, int xp, int xpToNext, int mana, int manaCap, int[] nodes,
                             int maxLevel, int pointsPerLevel) {
        ClientMagicData.set(level, xp, xpToNext, mana, manaCap, nodes, maxLevel, pointsPerLevel);
    }

    // Seviye atlama olayı → sağ-üstte cilalı toast bildirimi göster.
    @Override
    public void showLevelUp(int newLevel) {
        Minecraft.getInstance().getToasts().addToast(new ArcanumLevelUpToast(newLevel));
    }

    // Büyü Masası GUI'si açıkken vanilla actionbar/chat görünmez — sonucu
    // doğrudan açık ekrana ilet.
    @Override
    public void showSpellTableFeedback(boolean success, Component message) {
        if (Minecraft.getInstance().screen instanceof SpellTableScreen screen) {
            screen.showFeedback(message, success);
        }
    }

    // Umbravolo kara duman formu seti (S2C) → zırh/eldeki-eşya/elytra/kafa
    // render mixin'lerinin baktığı gizleme seti.
    @Override
    public void setUmbraForm(int entityId, boolean active) {
        com.arcanum.forge.client.state.ClientUmbraForms.set(entityId, active);
    }

}
