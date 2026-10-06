package com.arcanum.network;

import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;

/**
 * 1.20.1 PORT: S2C paket alıcılarının İSTEMCİ tarafı köprüsü.
 *
 * <p>S2C receiver kayıtları artık common'da ({@link ArcanumNetwork#registerS2CReceivers()})
 * ama gövdeleri istemci-yalnız sınıflara (ClientSpellData, ClientCastState, ClientLockState,
 * ClientMagicData, ArcanumLevelUpToast, SpellTableScreen) dokunmak zorunda. Common modülü
 * bu sınıflara REFERANS VEREMEZ (loader client kaynak kümesinde yaşıyorlar) — bu yüzden
 * burada yalnızca bir arayüz + statik holder var:
 *
 * <ul>
 *   <li>Loader'ın CLIENT init'i (Fabric: {@code ArcanumFabricClient.onInitializeClient},
 *       Forge: client setup) kendi {@link Handler} implementasyonunu
 *       {@link #setHandler(Handler)} ile kurar, SONRA
 *       {@link ArcanumNetwork#registerS2CReceivers()} çağırır.</li>
 *   <li>Receiver gövdeleri paket alanlarını çözüp ana istemci thread'inde
 *       ({@code ctx.queue}) buradaki handler'a iletir.</li>
 * </ul>
 *
 * <p>Handler kurulmadan paket gelirse (teorik yarış) paket sessizce düşer —
 * hepsi idempotent durum senkronları olduğu için bir sonraki senkron telafi eder.
 */
public final class ArcanumClientHooks {

    /** İstemci tarafının uygulaması gereken S2C paket işleyicileri (ana thread'de çağrılır). */
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
         * {@link SpellBeamPayload} → TEK büyü ışını olayı (ClientSpellBeams.add): yıldırım-tarzı
         * ışın istemcide çizilir, {@code life} tick boyunca solarak iz bırakır.
         */
        void addSpellBeam(double fromX, double fromY, double fromZ,
                          double toX, double toY, double toZ,
                          int color, int life, float width, float spread);

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
         * {@link UmbraFormPayload} → Umbravolo kara duman formu seti (ClientUmbraForms.set):
         * {@code active} true → entity id gizleme setine girer, false → çıkar.
         */
        void setUmbraForm(int entityId, boolean active);
    }

    private static volatile Handler handler;

    private ArcanumClientHooks() {}

    /** Loader CLIENT init'i çağırır — {@link ArcanumNetwork#registerS2CReceivers()}'DAN ÖNCE. */
    public static void setHandler(Handler h) {
        handler = h;
    }

    /** Kurulu handler (yoksa null — çağıran sessizce düşürür). */
    static Handler handler() {
        return handler;
    }
}
