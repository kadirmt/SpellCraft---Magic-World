package com.arcanum.spell;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.arcanum.network.ArcanumNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * UMBRAVOLO — kara duman formu yöneticisi (Ölüm Yiyen uçuşu, filmlerdeki gibi).
 *
 * <p>1.20.1 PORT — 1.21.1 root'tan davranış BİREBİR taşındı; yalnız API farkları:
 * <ul>
 *   <li>{@code ColorParticleOption} 1.20.1'de YOK ve {@code ENTITY_EFFECT} renk almaz —
 *       yumuşak mor-siyah bulut vanilla {@link DustParticleOptions} (joml Vector3f renk)
 *       ile üretilir (SPELL_GLOW full-bright translucent olduğundan koyu renkte görünmezdi).</li>
 *   <li>{@code SoundEvents.SOUL_ESCAPE} 1.20.1'de düz {@code SoundEvent} — {@code .value()} YOK.</li>
 *   <li>Form senkron paketi ({@code UmbraFormPayload}) Architectury NetworkManager /
 *       FriendlyByteBuf hattından gider (bkz. {@link ArcanumNetwork#sendUmbraForm}).</li>
 * </ul>
 *
 * <p>{@link Spells#umbravolo} forma SOKAR; formdayken asayla tekrar sağ tık
 * ({@code WandItem.use} başındaki form kesmesi) ANINDA çıkarır — cast time yok,
 * hangi büyü seçili olursa olsun. Formdayken BAŞKA büyü castlenemez (aynı kesme
 * cast boru hattına hiç girmeden döner).
 *
 * <p>Sunucu-yetkili durum (CastManager + ManaRegen melezi deseni):
 * <ul>
 *   <li>Uçuş: creative-tarzı serbest uçuş — abilities.mayfly+flying + flyingSpeed
 *       {@value #FLY_SPEED} (vanilla 0.05f'in 2 katı; sprint-fly süpürge sprintini geçer).
 *       Çıkışta önceki mayfly/hız GERİ YÜKLENİR (creative/spectator uçuşu bozulmaz).</li>
 *   <li>Mana: saniyede {@value #MANA_PER_SECOND} akar (Protego Maxima 30/sn'nin ~%30 azı).
 *       21/20 tick tam bölünmediği için ManaRegen'in KESİRLİ akümülatör idiomu ters yönde
 *       kullanılır. Mana yetmezse form ANINDA kapanır. Creative bedava (Protego paritesi).</li>
 *   <li>Görsel: oyuncu modeli vanilla INVISIBILITY (her tick sessizce tazelenir) +
 *       zırh/eldeki-eşya/elytra/kafa istemci mixin'leriyle gizlenir (ClientUmbraForms
 *       senkronu); yerine her tick yoğun kara duman bulutu + hareketle arkada ~2 sn
 *       süzülen siyah iz (SPELL_BEAM partikülü asılı kalır).</li>
 *   <li>Güvenlik: ölüm/kopma/boyut/gamemode tick süpürmesi + logout {@link #forget} +
 *       JOIN {@link #sanitizeOnJoin} (NBT'ye sızmış 2× flySpeed onarımı) + kapanışta
 *       {@link #exitAll} + çıkışta havadaysa 3.5 sn Slow Falling + fallDistance sıfırlama.</li>
 * </ul>
 */
public final class UmbraFormManager {
    private UmbraFormManager() {}

    /** Formdayken saniyede akan mana (Protego Maxima 30/sn'nin %30 azı). */
    public static final int MANA_PER_SECOND = 21;
    /** Duman formu uçuş hızı — vanilla creative 0.05f'in 2 katı (süpürgeden hızlı). */
    private static final float FLY_SPEED = 0.1f;
    /** Vanilla varsayılan uçuş hızı (Abilities.flyingSpeed) — JOIN sanitize hedefi. */
    private static final float VANILLA_FLY_SPEED = 0.05f;
    /** İstemci form seti periyodik tazeleme aralığı (geç katılan izleyiciler paketi kaçırmasın). */
    private static final int RESYNC_INTERVAL = 100;

    /** Yumuşak mor-siyah bulut rengi (1.20.1: DustParticleOptions, 0..1 RGB). */
    private static final DustParticleOptions DARK_CLOUD =
            new DustParticleOptions(new Vector3f(0.082f, 0.063f, 0.118f), 1.8f); // ~0x15101E
    /** Giriş/çıkış patlamasındaki mor-siyah bulut rengi (~0x1A1226). */
    private static final DustParticleOptions DARK_BURST =
            new DustParticleOptions(new Vector3f(0.102f, 0.071f, 0.149f), 2.2f);

    /** Girişte kaydedilen "önceki hal" — çıkışta abilities düzgün restore edilsin diye.
     *  {@code gm}: girişteki oyun modu — formdayken /gamemode değişirse prevMayfly bayatlar,
     *  tick süpürmesi formu kapatır ve restore'da bayat mayfly UYGULANMAZ. */
    private record FormState(ResourceKey<Level> dim, GameType gm, boolean prevMayfly, float prevFlySpeed) {}

    private static final Map<UUID, FormState> ACTIVE = new ConcurrentHashMap<>();
    /** 21/20 = 1.05 mana/tick kesirli drenaj akümülatörü (ManaRegen.ACC ters yönü). */
    private static final Map<UUID, Double> DRAIN_ACC = new HashMap<>();
    /** Bir önceki tick'in konumu — arkada kalıcı siyah iz çizgisi için. */
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();

    /** Oyuncu şu an kara duman formunda mı? (WandItem.use form kesmesi buna bakar) */
    public static boolean isActive(Player p) {
        return ACTIVE.containsKey(p.getUUID());
    }

    /** Forma GİR — {@link Spells#umbravolo} çağırır (mana/cooldown cast boru hattında düştü). */
    public static void enter(ServerLevel level, ServerPlayer p) {
        if (ACTIVE.containsKey(p.getUUID())) {
            return; // zaten formda (normalde WandItem kesmesi buraya düşürmez)
        }
        p.stopRiding(); // duman bir şeye binemez (süpürge/at vb. bırakılır)
        var ab = p.getAbilities();
        ACTIVE.put(p.getUUID(), new FormState(level.dimension(),
                p.gameMode.getGameModeForPlayer(), ab.mayfly, ab.getFlyingSpeed()));
        LAST_POS.put(p.getUUID(), p.position().add(0, p.getBbHeight() * 0.5, 0));
        ab.mayfly = true;
        ab.flying = true; // anında havalanma — film gibi yükselerek dağılır
        ab.setFlyingSpeed(FLY_SPEED);
        p.onUpdateAbilities(); // ServerPlayer → ClientboundPlayerAbilitiesPacket (abilities senkron)
        ArcanumNetwork.sendUmbraForm(level.getServer(), p.getId(), true);
        transformFx(level, p, 0.7f);
    }

    /** Formdan ÇIK — asayla tekrar sağ tık ({@code WandItem.use}) ya da ölüm/boyut değişimi. */
    public static void exit(ServerPlayer p) {
        FormState st = ACTIVE.remove(p.getUUID());
        if (st == null) {
            return;
        }
        DRAIN_ACC.remove(p.getUUID());
        LAST_POS.remove(p.getUUID());
        deactivate(p, st);
    }

    /** Oyuncu çıkışında sunucu-taraf haritalarını temizle (CastManager.forget kardeşi). */
    public static void forget(UUID id) {
        ACTIVE.remove(id);
        DRAIN_ACC.remove(id);
        LAST_POS.remove(id);
    }

    /**
     * JOIN sanitize — formdayken logout/sunucu kapanışı/crash olursa NBT'ye
     * {@code flySpeed=0.1} yazılmış olabilir (vanilla login mayfly/flying'i gamemode'a
     * göre sıfırlar ama flySpeed'e HİÇ dokunmaz → kalıcı 2× creative/spectator uçuşu).
     * Girişte formda OLMAYAN oyuncunun hızı tam {@link #FLY_SPEED} ise vanilla
     * {@link #VANILLA_FLY_SPEED}'e çekilir.
     */
    public static void sanitizeOnJoin(ServerPlayer p) {
        if (isActive(p)) {
            return;
        }
        var ab = p.getAbilities();
        if (ab.getFlyingSpeed() == FLY_SPEED) {
            ab.setFlyingSpeed(VANILLA_FLY_SPEED);
            p.onUpdateAbilities();
        }
    }

    /**
     * SERVER_STOPPING — tüm aktif formları TEMİZ kapat (abilities restore) ki sunucunun
     * son {@code saveAll}'u oyuncu NBT'sine bozuk flySpeed/mayfly yazmasın. Çevrimdışı
     * kalmış girdiler yalnızca unutulur (verileri zaten kaydedilmiş olabilir — onları
     * bir SONRAKİ girişte {@link #sanitizeOnJoin} toparlar).
     */
    public static void exitAll(MinecraftServer server) {
        for (UUID id : java.util.List.copyOf(ACTIVE.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                exit(p);
            } else {
                forget(id);
            }
        }
    }

    /**
     * Şu an formda olan ÇEVRİMİÇİ oyuncuların entity id'leri — JOIN handler'ı geç
     * katılan izleyiciye mevcut form setini anında göndermek için kullanır
     * (periyodik {@code RESYNC_INTERVAL} tazelemesini 5 sn beklemeden).
     */
    public static java.util.List<Integer> activeEntityIds(MinecraftServer server) {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (UUID id : ACTIVE.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                ids.add(p.getId());
            }
        }
        return ids;
    }

    /**
     * Her sunucu tick'i (END server tick): mana drenajı, görünmezlik tazeleme,
     * duman bulutu + iz FX, ölüm/kopma/boyut/gamemode süpürmesi.
     */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        var data = com.arcanum.data.ArcanumPlayerData.get(server);
        Iterator<Map.Entry<UUID, FormState>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, FormState> e = it.next();
            UUID id = e.getKey();
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            // oyuncu yok / ölü / kaldırılmış → formu sök (ölüm temizliği ayrıca ölüm event'inde)
            if (p == null || !p.isAlive() || p.isRemoved()) {
                it.remove();
                DRAIN_ACC.remove(id);
                LAST_POS.remove(id);
                if (p != null) {
                    deactivate(p, e.getValue());
                }
                continue;
            }
            // boyut değişimi → form TEMİZ kapanır (film dumanı portaldan geçmez)
            if (p.level().dimension() != e.getValue().dim()) {
                it.remove();
                DRAIN_ACC.remove(id);
                LAST_POS.remove(id);
                deactivate(p, e.getValue());
                continue;
            }
            // gamemode değişimi de form-bozan olaydır (boyut değişimi deseni): spectator
            // eşya kullanamadığı için asayla çıkamaz + drenaj sürerdi; survival'a düşen
            // vanilla updatePlayerAbilities yüzünden uçamadan formda kalırdı. Temiz kapat.
            if (p.gameMode.getGameModeForPlayer() != e.getValue().gm()) {
                it.remove();
                DRAIN_ACC.remove(id);
                LAST_POS.remove(id);
                deactivate(p, e.getValue());
                continue;
            }
            // duman bir şeye binemez — girişteki stopRiding tek seferlikti; formdayken
            // at/bot/süpürgeye binilirse her tick zorla indirilir (görünmez binici olmaz).
            if (p.isPassenger()) {
                p.stopRiding();
            }
            // ---- mana drenajı: 21/sn kesirli akümülatörle (creative bedava, Protego paritesi) ----
            if (!p.isCreative()) {
                double acc = DRAIN_ACC.getOrDefault(id, 0.0) + MANA_PER_SECOND / 20.0;
                int whole = (int) acc;
                DRAIN_ACC.put(id, acc - whole);
                if (whole > 0) {
                    if (data.getMana(p) < whole) {
                        // MANA BİTTİ → anında normale dön
                        it.remove();
                        DRAIN_ACC.remove(id);
                        LAST_POS.remove(id);
                        deactivate(p, e.getValue());
                        p.displayClientMessage(Component.translatable("arcanum.no_mana")
                                .withStyle(ChatFormatting.RED), true);
                        continue;
                    }
                    data.spendMana(p, whole);
                    ArcanumNetwork.syncMagicData(p); // ManaHud güncel kalsın (burnMana idiomu)
                }
            }
            ServerLevel level = p.serverLevel();
            long gt = level.getGameTime();
            // ---- görünmezlik bayrağı her tick tazelenir (pelerin idiomu: sessiz, ikonsuz) ----
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 10, 0, false, false, false));
            // ---- ÇOK YOĞUN kara duman bulutu (server-side sendParticles → tüm izleyiciler görür) ----
            Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
            level.sendParticles(ParticleTypes.SQUID_INK,
                    c.x, c.y, c.z, 10, 0.30, 0.45, 0.30, 0.02); // yoğun siyah çekirdek
            level.sendParticles(DARK_CLOUD,
                    c.x, c.y, c.z, 12, 0.45, 0.55, 0.45, 0.0); // yumuşak mor-siyah bulut (1.20.1: dust)
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    c.x, c.y, c.z, 4, 0.35, 0.45, 0.35, 0.01); // kenar dumanı
            // ---- hareket ederken ARKADA KALICI SİYAH İZ (SPELL_BEAM ~2 sn asılı kalır) ----
            Vec3 last = LAST_POS.getOrDefault(id, c);
            if (last.distanceToSqr(c) > 0.0025) {
                SpellFx.line(level, last, c, SpellFx.beamOpt(0x241233), 0.6);
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        last.x, last.y, last.z, 1, 0.15, 0.15, 0.15, 0.005);
            }
            LAST_POS.put(id, c);
            // ---- uçarken periyodik hafif whoosh (BroomEntity PHANTOM_FLAP kalıbı) ----
            if (gt % 12L == 0L && p.getDeltaMovement().lengthSqr() > 0.04) {
                SpellFx.sound(level, c, SoundEvents.PHANTOM_FLAP, 0.25f, SpellFx.vary(level, 0.6f));
            }
            // ---- geç katılan izleyiciler için periyodik form-seti tazeleme ----
            if (gt % RESYNC_INTERVAL == 0L) {
                ArcanumNetwork.sendUmbraForm(server, p.getId(), true);
            }
        }
    }

    /**
     * Formu SÖK: abilities restore (creative/spectator mayfly'ı bozulmaz), havadaysa
     * 3.5 sn Slow Falling (film gibi yere süzülme), fallDistance sıfırla, istemci
     * form-seti + çıkış FX senkronu. Haritalardan silme ÇAĞIRANIN işidir.
     */
    private static void deactivate(ServerPlayer p, FormState st) {
        var ab = p.getAbilities();
        boolean gamemodeFly = p.isCreative() || p.isSpectator();
        // prevMayfly anlık görüntüsü YALNIZCA gamemode değişmediyse uygulanır: creative'de
        // girip formdayken survival'a düşen oyuncuya bayat mayfly=true sızmasın (kalıcı
        // creative-tarzı uçuş bug'ı). Gamemode değiştiyse mayfly'a tek otorite gamemode'dur.
        boolean sameGm = p.gameMode.getGameModeForPlayer() == st.gm();
        ab.mayfly = gamemodeFly || (sameGm && st.prevMayfly());
        if (!ab.mayfly) {
            ab.flying = false;
        }
        ab.setFlyingSpeed(st.prevFlySpeed());
        p.onUpdateAbilities();
        p.fallDistance = 0.0f;
        if (!ab.flying && !p.onGround() && p.isAlive()) {
            // havada form bitti → yere yumuşak süzülme (görünür partiküllü olsun ki anlaşılsın)
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 70, 0, false, false, true));
        }
        ArcanumNetwork.sendUmbraForm(p.getServer(), p.getId(), false);
        if (p.level() instanceof ServerLevel level && p.isAlive()) {
            transformFx(level, p, 1.0f);
        }
    }

    /** Giriş/çıkış patlama-tarzı duman bursts + SOUL_ESCAPE (1.20.1'de düz SoundEvent — .value() YOK). */
    private static void transformFx(ServerLevel level, ServerPlayer p, float pitch) {
        Vec3 c = p.position().add(0, 0.9, 0);
        level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 40, 0.5, 0.8, 0.5, 0.12);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 25, 0.5, 0.8, 0.5, 0.06);
        level.sendParticles(DARK_BURST, c.x, c.y, c.z, 30, 0.6, 0.9, 0.6, 0.0);
        SpellFx.sound(level, c, SoundEvents.SOUL_ESCAPE, 1.2f, SpellFx.vary(level, pitch));
        SpellFx.sound(level, c, SoundEvents.PHANTOM_FLAP, 0.7f, SpellFx.vary(level, 0.55f));
    }
}
