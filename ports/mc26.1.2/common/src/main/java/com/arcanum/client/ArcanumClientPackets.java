package com.arcanum.client;

import com.arcanum.client.beam.ClientSpellBeams;
import com.arcanum.network.CastStatePayload;
import com.arcanum.network.KnownSpellsPayload;
import com.arcanum.network.LevelUpPayload;
import com.arcanum.network.LockStatePayload;
import com.arcanum.network.MagicDataPayload;
import com.arcanum.network.SpellBeamPayload;
import com.arcanum.network.SpellLoadoutPayload;
import com.arcanum.network.SpellTableFeedbackPayload;
import com.arcanum.network.UmbraFormPayload;
import com.arcanum.platform.Platform;
import com.arcanum.platform.PlatformNet;
import net.minecraft.world.phys.Vec3;

/**
 * Tüm S2C istemci alıcıları (kökteki ArcanumFabricClient'ın
 * {@code ClientPlayNetworking.registerGlobalReceiver} gövdeleri, birebir). Yalnız
 * {@code ArcanumClient.init()} içinden çağrılır. {@link PlatformNet.ClientReceiver}
 * sözleşmesi gereği alıcılar zaten İSTEMCİ ANA THREAD'inde çalışır (kökteki
 * {@code context.client().execute(...)} sıçraması platform katmanında).
 */
public final class ArcanumClientPackets {
    private ArcanumClientPackets() {}

    public static void register() {
        PlatformNet net = Platform.net();

        // Bilinen büyü listesi senkronu
        net.registerClientReceiver(KnownSpellsPayload.TYPE, payload ->
                ClientSpellData.set(payload.spellIds()));
        // Büyü dizilimi senkronu (12 slot + aktif sayfa/slot)
        net.registerClientReceiver(SpellLoadoutPayload.TYPE, payload ->
                ClientSpellData.setLoadout(payload.loadout(), payload.activePage(), payload.activeSlot()));
        // Cast durumu senkronu (S2C): cast başladı (spellIndex>=0) / bitti-iptal (spellIndex<0).
        // ClientCastState.set spellIndex<0 / totalTicks<=0 gelince kendi içinde clear() yapar.
        net.registerClientReceiver(CastStatePayload.TYPE, payload ->
                ClientCastState.set(payload.spellIndex(), payload.totalTicks()));
        // Asa kenetlenmesi durumu senkronu (S2C): ışın/düğüm/HUD buna göre çizilir/temizlenir.
        net.registerClientReceiver(LockStatePayload.TYPE, payload -> ClientLockState.set(
                payload.a(), payload.b(), payload.node(),
                payload.colorA(), payload.colorB(), payload.phase()));
        // Büyü ışını olayı (S2C): yıldırım-tarzı ışın istemcide çizilir, ~0.5 sn iz bırakır.
        net.registerClientReceiver(SpellBeamPayload.TYPE, payload -> ClientSpellBeams.add(
                new Vec3(payload.fromX(), payload.fromY(), payload.fromZ()),
                new Vec3(payload.toX(), payload.toY(), payload.toZ()),
                payload.color(), payload.life(), payload.width(), payload.spread()));
        // Büyücü verisi (level/xp/skill/mana) senkron — ManaHud + skill paneli buradan okur.
        net.registerClientReceiver(MagicDataPayload.TYPE, payload -> ClientMagicData.set(
                payload.level(), payload.xp(), payload.xpToNext(),
                payload.mana(), payload.manaCap(), payload.nodes(),
                payload.maxLevel(), payload.pointsPerLevel()));
        // Seviye atlama olayı → sağ-üstte cilalı toast bildirimi göster.
        net.registerClientReceiver(LevelUpPayload.TYPE, payload ->
                ClientCompat.toasts().addToast(new ArcanumLevelUpToast(payload.level())));
        // Umbravolo kara duman formu senkronu (S2C): formdaki oyuncuların entity id seti —
        // zırh/eldeki-eşya gizleme mixin'leri + görünmezlik bütünlüğü buradan okur.
        net.registerClientReceiver(UmbraFormPayload.TYPE, payload ->
                ClientUmbraForms.set(payload.entityId(), payload.active()));

        // Büyü Masası GUI'si açıkken vanilla actionbar/chat görünmez — sonucu
        // doğrudan açık ekrana ilet.
        net.registerClientReceiver(SpellTableFeedbackPayload.TYPE, payload -> {
            if (ClientCompat.screen() instanceof SpellTableScreen screen) {
                screen.showFeedback(payload.message(), payload.success());
            }
        });
    }
}
