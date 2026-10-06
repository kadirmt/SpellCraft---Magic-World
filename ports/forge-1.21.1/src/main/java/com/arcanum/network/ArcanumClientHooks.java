package com.arcanum.network;

import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * FORGE 1.21.1 PORTU: S2C paket alıcılarının İSTEMCİ tarafı köprüsü.
 *
 * <p>S2C alıcı gövdeleri {@link ArcanumNetwork#register()} içinde (SimpleChannel
 * consumer'ları olarak) kayıtlı, ama asıl işleri istemci-yalnız sınıflara
 * (ClientSpellData, ClientCastState, ClientLockState, ClientMagicData,
 * ArcanumLevelUpToast, SpellTableScreen) dokunmak zorunda. {@code ArcanumNetwork}
 * dedicated server'da da yüklenir — bu sınıflara doğrudan REFERANS VEREMEZ
 * (class-load'da {@code ClassNotFoundException}: istemci sınıfları server jar'ında yok).
 * Bu yüzden burada yalnızca bir arayüz + statik holder var:
 *
 * <ul>
 *   <li>İstemci kurulumunda (T5: {@code FMLClientSetupEvent} veya client mod-bus init —
 *       {@code com.arcanum.forge.client.*}) kendi {@link Handler} implementasyonu
 *       {@link #setHandler(Handler)} ile kurulur. Bir sunucuya bağlanmadan ÖNCE
 *       kurulmuş olması yeterli.</li>
 *   <li>Alıcı gövdeleri {@code consumerMainThread} sayesinde zaten ana istemci
 *       thread'inde çalışır ve paket alanlarını buradaki handler'a iletir —
 *       handler içinde ekstra {@code execute} GEREKMEZ.</li>
 * </ul>
 *
 * <p>Handler kurulmadan paket gelirse (teorik yarış) paket sessizce düşer —
 * hepsi idempotent durum senkronları olduğu için bir sonraki senkron telafi eder.
 * Dedicated server'da handler hiç kurulmaz (S2C consumer orada zaten çalışmaz).
 */
public final class ArcanumClientHooks {

    /** İstemci tarafının uygulaması gereken S2C paket işleyicileri (ana istemci thread'inde çağrılır). */
    public interface Handler {

        /** {@link KnownSpellsPayload} → bilinen büyü listesi (ClientSpellData.set). */
        void setKnownSpells(List<String> spellIds);

        /** {@link SpellLoadoutPayload} → 12 slot dizilim + aktif sayfa/slot (ClientSpellData.setLoadout). */
        void setLoadout(int[] loadout, int activePage, int activeSlot);

        /**
         * {@link CastStatePayload} → cast göstergesi (ClientCastState.set;
         * {@code spellIndex<0} / {@code totalTicks<=0} gelince kendi içinde clear yapar).
         */
        void setCastState(int spellIndex, int totalTicks);

        /** {@link LockStatePayload} → asa kenetlenmesi ışın/düğüm/HUD durumu (ClientLockState.set). */
        void setLockState(UUID a, UUID b, float node, int colorA, int colorB, int phase);

        /**
         * {@link SpellBeamPayload} → yıldırım-tarzı büyü ışını olayı (ClientSpellBeams.add;
         * ışın istemcide çizilir, {@code life} tick boyunca solarak iz bırakır).
         */
        void addSpellBeam(Vec3 from, Vec3 to, int color, int life, float width, float spread);

        /**
         * {@link MagicDataPayload} → büyücü verisi: level/xp/skill/mana + config'den seviye
         * tavanı ve seviye başına puan (ClientMagicData.set).
         */
        void setMagicData(int level, int xp, int xpToNext, int mana, int manaCap, int[] nodes,
                          int maxLevel, int pointsPerLevel);

        /** {@link LevelUpPayload} → sağ-üstte "Seviye Atladın" toast bildirimi. */
        void showLevelUp(int newLevel);

        /**
         * {@link SpellTableFeedbackPayload} → Büyü Masası GUI'si AÇIKSA sonucu ekrana ilet
         * (açık değilse yok say — GUI açıkken vanilla actionbar/chat görünmüyordu).
         */
        void showSpellTableFeedback(boolean success, Component message);

        /**
         * {@link UmbraFormPayload} → Umbravolo kara duman formu seti (ClientUmbraForms.set;
         * zırh/eldeki-eşya/elytra/kafa render mixin'leri bu sete bakarak formdaki
         * oyuncuyu tamamen gizler).
         */
        void setUmbraForm(int entityId, boolean active);
    }

    private static volatile Handler handler;

    private ArcanumClientHooks() {}

    /** İstemci kurulumu (T5) çağırır — bir sunucuya bağlanmadan önce kurulmuş olmalı. */
    public static void setHandler(Handler h) {
        handler = h;
    }

    /** Kurulu handler (yoksa null — çağıran sessizce düşürür). */
    static Handler handler() {
        return handler;
    }
}
