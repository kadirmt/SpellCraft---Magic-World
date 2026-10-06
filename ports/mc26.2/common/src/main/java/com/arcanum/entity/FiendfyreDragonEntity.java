package com.arcanum.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.arcanum.spell.DiabolicaDragonManager;
import com.arcanum.spell.SpellFx;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

/**
 * MAVİ ALEV EJDERHASI — Protego Diabolica'nın (Grindelwald'ın mavi ateş çemberi)
 * görsel tezahürü. Fantastic Beasts'teki "çemberden yükselen alev yaratığı" anı.
 *
 * <p><b>SALT GÖRSEL.</b> Bu entity HİÇBİR oyun mekaniği taşımaz: hasar VERMEZ
 * (tutuşturma/itme/kalkan zaten {@code Spells.protegoDiabolica} içindedir ve
 * DEĞİŞTİRİLMEMİŞTİR), çarpışmaz, itmez, itilmez, hedeflenemez, hasar almaz,
 * loot düşürmez, XP vermez ve <b>diske YAZILMAZ</b> ({@link #shouldBeSaved()}
 * = false → dünya yeniden yüklendiğinde artık kalamaz).
 *
 * <p><b>Ömür (sunucu-otoriter).</b> {@link DiabolicaDragonManager} kanalın her
 * tick'inde {@code tick()} çağırır; ejderha yoksa doğurur, varsa dokunmaz.
 * Ejderhanın kendi WATCHDOG'u her tick şunu sorar: sahip var mı, canlı mı, AYNI
 * boyutta mı ve kanal hâlâ canlı mı ({@link DiabolicaDragonManager#isChanneling})?
 * Cevap hayırsa {@link #beginDissolve} → {@value #DISSOLVE_TICKS} tick sonra
 * {@code discard()}. Kanal bitişi, mana bitişi, ölüm, boyut değişimi, çıkış ve
 * eşya değişimi hepsi AYNI kapıdan geçer (keepalive sönünce) — WandItem'a ek
 * kanca gerekmez.
 *
 * <p><b>Hareket.</b> Çember yarıçapında ({@value #ORBIT_RADIUS}) sahibin etrafında
 * süzülerek döner; gövde teğet yöne bakar, y sinüs dalgasıyla salınır. Yakma
 * bandında ({@value #BAND_INNER}–{@value #BAND_OUTER}) bir canlı varsa ejderha o
 * hedefin açısına doğru açısal olarak HIZLANIR; üstüne gelince LUNGE (dalış +
 * çene) yapar ve hedefe alev püskürtür. Hedef yoksa sakin devriye.
 *
 * <p><b>GeckoLib sözleşmesi</b> (model/anim ajanı bu isimleri üretir):
 * {@code geometry.diabolica_dragon}; klipler {@code animation.diabolica_dragon.fly}
 * (loop), {@code .lunge} (once ~0.8s), {@code .roar} (once ~1.2s),
 * {@code .dissolve} (once ~0.6s, hold_on_last_frame).
 *
 * <p><b>Klip seçimi TAMAMEN senkron durumdan türer</b> — tetiklenen animasyon (
 * {@code triggerAnim}) hiç kullanılmaz ve TEK controller vardır. Gerekçesi ve tick
 * bütçelerinin neden {@link #BLEND_TICKS} payı taşıdığı {@link #registerControllers}
 * ile ilgili sabitlerin javadoc'undadır; klip süresine dayanan yeni bir sabit
 * eklenecekse o kural okunmalıdır.
 */
public class FiendfyreDragonEntity extends Mob implements GeoEntity {

    // ===================== sabitler =====================

    /** Mavi alev rengi — Spells.protegoDiabolica ile AYNI (0x4FA8FF). */
    public static final int FLAME_COLOR = 0x4FA8FF;

    /** Yörünge yarıçapı — çember FX'iyle birebir aynı olmalı. */
    public static final double ORBIT_RADIUS = 5.5;
    /** Yakma bandı (Spells.protegoDiabolica: radius..radius+1) — hedef arama şeridi. */
    private static final double BAND_INNER = 4.0;
    private static final double BAND_OUTER = 7.5;
    /** Hedefin dikey toleransı (sahibin ayak hizasına göre). */
    private static final double BAND_HEIGHT = 4.0;

    /** Sakin devriye açısal hızı (rad/tick). */
    private static final double BASE_OMEGA = 0.055;
    /** Hedefe koşarken izin verilen azami açısal hız. */
    private static final double MAX_OMEGA = 0.16;
    /** Gövde merkezinin sahibin ayağına göre taban yüksekliği. */
    private static final double BODY_Y = 1.4;
    /** Süzülme dalgalanması genliği (±). */
    private static final double Y_WAVE = 0.5;

    /**
     * Animasyon controller'ının harman (blend) uzunluğu — <b>klibin kendi zaman
     * çizgisi ancak bu kadar tick SONRA başlar.</b> (GeckoLib 4.7.7,
     * {@code AnimationController.process}: {@code TRANSITIONING → RUNNING} geçişi
     * {@code adjustedTick >= transitionLength} olunca yapılır; o ana kadar yalnız
     * eski pozdan yeni klibin 0. karesine lerp edilir.) GeckoLib 5.5.x'te de geçerli:
     * {@code AnimationTimeline.create} her aşamayı [geçiş(transitionTicks)] + [klip] diye kurar
     * (26.x portu, res-geckolib5 §3.5 — kaynak okuması; görsel test ŞART).
     *
     * <p>5.5.2'nin play_once sonuna eklediği "reset" geçişi (BLEND_TICKS uzunluğunda,
     * klip SONU = BLEND_TICKS + klip tick'inde başlar) bu bütçelere DOKUNMAZ (G4 decompile
     * doğrulaması): RISE (3+24−1=26) ve LUNGE (3+16−1=18) klip sonundan (27 / 19) 1 tick ÖNCE
     * fly'a geçer → reset aşamasına hiç girilmez, çıkış yine fly'a harmanla yapılır;
     * DISSOLVE {@code hold_on_last_frame} → {@code LoopType.HOLD_ON_LAST_FRAME}
     * {@code timelineTime}'ı klip sonuna sabitler, reset aşamasında geçiş oranı 0 kalır
     * (son kare = 0.01 ölçek korunur). 4.7.7 bütçeleri AYNEN geçerli.
     *
     * <p><b>KURAL:</b> klip süresine dayanan HER tick bütçesi bu payı içermelidir,
     * yoksa klibin sonu kesilir. Aşağıdaki RISE/DISSOLVE/LUNGE bütçeleri bu yüzden
     * ham klip uzunluğundan türetilir, elle yazılmaz.
     */
    private static final int BLEND_TICKS = 3;

    /** roar klibinin ham uzunluğu (1.2 sn = 24 tick). */
    private static final int ROAR_CLIP_TICKS = 24;
    /**
     * Beliriş evresi = harman + klip. Son 1 tick BİLEREK kırpılır: roar {@code play_once}
     * olduğu için klip bitince controller STOPPED'a düşer ve o kare hiçbir kemik yazmaz
     * (gövde tek karede rest pozuna çakılır). Klip HÂLÂ oynarken fly'a geçmek, çıkışın
     * blend ile yapılmasını garanti eder.
     */
    private static final int RISE_TICKS = BLEND_TICKS + ROAR_CLIP_TICKS - 1;
    /** Yükselişin tamamlandığı tick (roar'ın kalanı havada oynar). */
    private static final int RISE_CLIMB_TICKS = 16;
    /** Çemberin altından başlama derinliği. */
    private static final double RISE_DEPTH = 2.6;

    /** dissolve klibinin ham uzunluğu (0.6 sn = 12 tick). */
    private static final int DISSOLVE_CLIP_TICKS = 12;
    /**
     * Dağılma evresi = harman + klip (tamamı). Burada kırpma YOK: dissolve
     * {@code hold_on_last_frame} olduğu için bitince PAUSED'a geçip son kareyi
     * yazmaya devam eder — gövde 0.01 ölçekte kalır, poz sıçraması olmaz.
     */
    private static final int DISSOLVE_TICKS = BLEND_TICKS + DISSOLVE_CLIP_TICKS;
    /** Kanal kesildikten sonra dağılmanın başlaması için beklenen tick (keepalive toleransı). */
    private static final int ORPHAN_GRACE = 2;
    /**
     * Sert emniyet: sahipsiz kalan ejderha en geç bu kadar tick'te yok olur.
     * <b>DISSOLVE_TICKS'ten TÜRETİLİR</b> — sabit yazılsaydı dağılma bütçesi büyüdüğünde
     * watchdog klibi sessizce kesebilirdi (normal yol {@code ORPHAN_GRACE + DISSOLVE_TICKS}
     * tick'te biter; kalan 7 tick saf emniyet payıdır).
     */
    private static final int ORPHAN_HARD_KILL = ORPHAN_GRACE + DISSOLVE_TICKS + 7;

    /** lunge klibinin ham uzunluğu (0.8 sn = 16 tick) — dalış EĞRİSİ de bu kadar sürer. */
    private static final int LUNGE_TICKS = 16;
    /**
     * Dalış evresinin toplam ömrü = harman + klip − 1. Son tick'in kırpılma gerekçesi
     * {@link #RISE_TICKS} ile aynı (lunge da {@code play_once}).
     */
    private static final int LUNGE_TOTAL_TICKS = BLEND_TICKS + LUNGE_TICKS - 1;
    /**
     * Dalış EĞRİSİNİN aktif tick sayısı: harman payından sonra başlar, evrenin son
     * değerlendirilen tick'inde tam 1.0'a (yani sin = 0'a) ulaşır. Bu sayede eğri
     * iki ucunda da 0'dır ve konumda sıçrama olmaz — {@code LUNGE_TICKS}'in kendisi
     * bölen olarak kullanılsaydı eğri ~0.38'de kesilirdi.
     */
    private static final int LUNGE_CURVE_SPAN = LUNGE_TOTAL_TICKS - BLEND_TICKS - 1;
    /** İki dalış arası asgari bekleme. */
    private static final int LUNGE_COOLDOWN = 50;
    /** Bu açısal yakınlıkta hedefe dalınır. */
    private static final double LUNGE_ANGLE = 0.25;

    /**
     * Gövde yöneliminin azami açısal hızı (derece/tick). Sakin devriye ({@value #BASE_OMEGA}
     * rad/tick ≈ 3.2°/tick) ve azami takip hızı ({@value #MAX_OMEGA} rad/tick ≈ 9.2°/tick)
     * bu tavanın ALTINDA kalır → normal uçuş hiç etkilenmez; yalnız yön tersine
     * döndüğündeki 180°'lik sıçrama ~13 tick'e yayılır.
     */
    private static final float YAW_SLEW = 14.0f;
    /** Yön işareti histerezisi — omega sıfır civarında yalpalarken sign çıtırdamasın. */
    private static final double FACING_FLIP_OMEGA = 0.020;

    /** Hedef yeniden seçim periyodu. */
    private static final int RETARGET_PERIOD = 5;

    /**
     * Modelin "ön" yönü düzeltmesi (derece). Blockbench varsayılanı: burun -Z'ye
     * bakar → 0 doğrudur (türetme: yaw θ bakışı (-sin θ, cos θ); yarıçap açısı a
     * için teğet (-sin a, cos a) → θ = a). Model başka eksene bakacak şekilde
     * çizilirse YALNIZCA bu sabit değiştirilir.
     */
    private static final float MODEL_YAW_OFFSET = 0.0f;

    // ===================== durum (senkron) =====================

    /** Yükseliyor (roar) — çemberin içinden çıkıyor. */
    public static final byte STATE_RISE = 0;
    /** Normal devriye/av (fly). */
    public static final byte STATE_FLY = 1;
    /** Dağılıyor (dissolve) — birkaç tick sonra discard. */
    public static final byte STATE_DISSOLVE = 2;

    /**
     * Sahip UUID'si — 26.x'te {@code OPTIONAL_UUID} serileştiricisi kalktı; vanilla muadili
     * {@code OPTIONAL_LIVING_ENTITY_REFERENCE} (tel üzerinde yine yalnız UUID taşır).
     * Dış API ({@link #getOwnerUUID}/{@link #setOwnerUUID}) UUID olarak AYNEN korunur.
     */
    private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> OWNER =
            SynchedEntityData.defineId(FiendfyreDragonEntity.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    private static final EntityDataAccessor<Byte> STATE =
            SynchedEntityData.defineId(FiendfyreDragonEntity.class, EntityDataSerializers.BYTE);
    /**
     * Dalış klibi oynuyor mu. Eskiden {@code triggerAnim("action","lunge")} ile ayrı bir
     * katmanda tetikleniyordu; artık DURUMDAN sürülüyor — hem tetik paketi kaybolduğunda
     * klip kaybolmuyor, hem de klip fly ile AYNI controller'da olduğu için bitişte
     * blend-out yapılabiliyor (bkz. {@link #registerControllers}).
     */
    private static final EntityDataAccessor<Boolean> LUNGING =
            SynchedEntityData.defineId(FiendfyreDragonEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.diabolica_dragon.fly");
    private static final RawAnimation LUNGE = RawAnimation.begin().thenPlay("animation.diabolica_dragon.lunge");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.diabolica_dragon.roar");
    private static final RawAnimation DISSOLVE = RawAnimation.begin().thenPlay("animation.diabolica_dragon.dissolve");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ===================== durum (sunucu) =====================

    /** Yörünge açısı (radyan, dünya koordinatında). */
    private double orbitAngle;
    /** Yumuşatılmış açısal hız (işaretli — hedef arkadaysa ters yöne kıvrılır). */
    private double omega = BASE_OMEGA;
    /** Gövdenin baktığı yön işareti (+1 ileri, -1 geri) — omega sıfıra yaklaşınca titremesin. */
    private double facingSign = 1.0;
    /**
     * Yumuşatılmış GÖVDE yaw'ı (derece). {@link #facingSign} ayrık (±1) olduğu için
     * hedef yaw yön değişiminde 180° sıçrar; görünen yaw bu alanda {@value #YAW_SLEW}
     * °/tick ile hedefe yaklaştırılır. NaN = "henüz kurulmadı" (ilk tick'te slew yok).
     */
    private float bodyYaw = Float.NaN;
    /** Aktif evredeki tick sayacı (RISE / DISSOLVE). */
    private int stateTicks;
    /** Kalan lunge tick'i (0 = dalış yok). */
    private int lungeTicks;
    /** Dalış bekleme sayacı. */
    private int lungeCooldown;
    /** Sahipsiz/kanalsız geçen tick — sert emniyet sayacı. */
    private int orphanTicks;
    /** Şu anki görsel hedef (salt animasyon amaçlı; hasar bu entity'den GELMEZ). */
    private LivingEntity visualTarget;

    public FiendfyreDragonEntity(EntityType<? extends FiendfyreDragonEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        // 1.21.1: this.noCulling = true (model hitbox'tan çok daha büyük — kırpılmasın).
        // 26.x'te Entity.noCulling alanı YOK; culling renderer'da karar verilir →
        // DiabolicaDragonRenderer#affectedByCulling(entity) false döndürür (istemci tarafı).
        this.setInvulnerable(true);
        this.setNoAi(true);          // goal/navigation boru hattı hiç dönmesin
        this.xpReward = 0;
        // DİKKAT: setPersistenceRequired() KASITLI OLARAK YOK (brief) — bu bir tezahür,
        // kalıcı bir yaratık değil; shouldBeSaved() zaten diske yazılmasını engelliyor.
    }

    /** Mob tabanı MAX_HEALTH ister; gerisi hiç kullanılmaz (AI yok, hareket elle). */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    // ===================== senkron veri =====================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(STATE, STATE_RISE);
        builder.define(LUNGING, false);
    }

    /** Çağıran oyuncunun UUID'si — SENKRON (istemci render/efekt kancaları için de görünür). */
    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER).map(EntityReference::getUUID).orElse(null);
    }

    public void setOwnerUUID(UUID id) {
        this.entityData.set(OWNER, id == null
                ? Optional.empty()
                : Optional.of(EntityReference.<LivingEntity>of(id)));
    }

    /** Sahip oyuncu — YALNIZCA bu ejderhanın bulunduğu boyutta arar (boyut değişimi = null). */
    public Player getOwner() {
        UUID id = getOwnerUUID();
        return id == null ? null : this.level().getPlayerByUUID(id);
    }

    public byte getState() {
        return this.entityData.get(STATE);
    }

    private void setState(byte state) {
        this.entityData.set(STATE, state);
        this.stateTicks = 0;
    }

    public boolean isDissolving() {
        return getState() == STATE_DISSOLVE;
    }

    /** Dalış klibi oynuyor mu — SENKRON, istemcide de doğru (animasyon seçimi buna bakar). */
    public boolean isLunging() {
        return this.entityData.get(LUNGING);
    }

    // ===================== NBT (diske yazılmaz; yine de tutarlı kalsın) =====================

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        UUID id = getOwnerUUID();
        if (id != null) {
            // UUIDUtil.CODEC = eski putUUID ile aynı int[4] biçimi
            output.store("DiabolicaOwner", UUIDUtil.CODEC, id);
        }
        output.putByte("DiabolicaState", getState());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // anahtar yoksa dokunma (eski hasUUID/contains kapıları)
        input.read("DiabolicaOwner", UUIDUtil.CODEC).ifPresent(this::setOwnerUUID);
        this.entityData.set(STATE, input.getByteOr("DiabolicaState", getState()));
    }

    /**
     * Diske ASLA yazılmaz. Brief'in "hiçbir durumda dünyada artık kalmamalı"
     * güvencesinin taşıyıcısı: sunucu çökse/kapatılsa bile chunk'ta iz kalmaz.
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    // ===================== yaşam döngüsü =====================

    @Override
    protected void registerGoals() {
        // Goal YOK — tüm hareket aiStep'te elle sürülür (brief: AI yok).
    }

    @Override
    public void travel(Vec3 input) {
        // Vanilla hareket boru hattı tamamen atlanır — konum her tick elle kurulur.
    }

    @Override
    protected void pushEntities() {
        // Çarpışma yok: ejderha hiçbir varlığı itmez (brief).
    }

    @Override
    public void checkDespawn() {
        // Doğal despawn yok — ömrü yalnızca kanal + watchdog belirler.
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.setDeltaMovement(Vec3.ZERO);
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }

        // --- WATCHDOG (dağılma evresinde de sayar): sahip yok / ölü / başka boyutta /
        //     kanal bitti → dağıl; en geç ORPHAN_HARD_KILL tick'te kesin yok ol. ---
        Player owner = getOwner();
        boolean channelAlive = owner != null && owner.isAlive() && !owner.isRemoved()
                && !owner.isSpectator()
                && owner.level() == this.level()
                && DiabolicaDragonManager.isChanneling(owner);
        if (!channelAlive) {
            this.orphanTicks++;
            if (this.orphanTicks >= ORPHAN_HARD_KILL) {
                discard(); // sert emniyet — dağılma klibi bir şekilde takılsa bile
                return;
            }
        } else {
            this.orphanTicks = 0;
        }

        // --- DAĞILMA EVRESİ: hareket yok, yalnız dağılma partikülü + geri sayım ---
        if (getState() == STATE_DISSOLVE) {
            tickDissolve(server);
            return;
        }
        if (!channelAlive) {
            // 1 tick tolerans (kanal keepalive'ı 3 tick; tek kare boşluk yanlış-alarm yapmasın)
            if (this.orphanTicks >= ORPHAN_GRACE) {
                beginDissolve(server);
            }
            return;
        }

        // --- evre ilerlemesi ---
        this.stateTicks++;
        double riseOffset = 0.0;
        double omegaScale = 1.0;
        if (getState() == STATE_RISE) {
            double climb = Mth.clamp(this.stateTicks / (double) RISE_CLIMB_TICKS, 0.0, 1.0);
            double eased = climb * climb * (3.0 - 2.0 * climb); // smoothstep
            riseOffset = -(1.0 - eased) * RISE_DEPTH;
            omegaScale = 0.45; // beliriş sırasında ağır ağır döner
            if (this.stateTicks >= RISE_TICKS) {
                setState(STATE_FLY);
            }
        }

        // --- hedefleme + açısal sürüş ---
        if (this.lungeCooldown > 0) {
            this.lungeCooldown--;
        }
        if (this.lungeTicks > 0) {
            this.lungeTicks--;
        }
        if (this.tickCount % RETARGET_PERIOD == 0) {
            this.visualTarget = pickBandTarget(server, owner);
        }
        if (this.visualTarget != null && (!this.visualTarget.isAlive() || this.visualTarget.isRemoved())) {
            this.visualTarget = null;
        }

        double desiredOmega = BASE_OMEGA;
        double angleGap = Double.NaN;
        if (this.visualTarget != null) {
            double ta = Math.atan2(this.visualTarget.getZ() - owner.getZ(),
                    this.visualTarget.getX() - owner.getX());
            angleGap = wrapRadians(ta - this.orbitAngle);
            // hedefe doğru açısal hızlanma; üstüne gelince (gap→0) neredeyse durur
            desiredOmega = Mth.clamp(angleGap * 0.10, -MAX_OMEGA, MAX_OMEGA);
        }
        this.omega += (desiredOmega * omegaScale - this.omega) * 0.18; // yumuşak açısal lerp
        this.orbitAngle = wrapRadians(this.orbitAngle + this.omega);
        if (this.omega > FACING_FLIP_OMEGA) {
            this.facingSign = 1.0;
        } else if (this.omega < -FACING_FLIP_OMEGA) {
            this.facingSign = -1.0;
        }

        // --- LUNGE tetikleme: hedefin üstündeyiz + bekleme dolmuş + zaten dalmıyoruz ---
        if (this.visualTarget != null && this.lungeTicks == 0 && this.lungeCooldown == 0
                && getState() == STATE_FLY && Math.abs(angleGap) < LUNGE_ANGLE) {
            startLunge(server);
        }
        // Dalış klibi artık tetik paketiyle değil senkron durumla sürülüyor.
        this.entityData.set(LUNGING, this.lungeTicks > 0);

        // --- konum: yörünge + dalgalanma + dalış eğrisi ---
        long gt = server.getGameTime();
        double lunge = lungeCurve();
        double radius = ORBIT_RADIUS + lunge * 0.85;               // dalışta dışa/aşağı süzülür
        double wave = Math.sin(gt * 0.11 + this.orbitAngle) * Y_WAVE;
        double y = owner.getY() + BODY_Y + wave + riseOffset - lunge * 1.05;
        double x = owner.getX() + Math.cos(this.orbitAngle) * radius;
        double z = owner.getZ() + Math.sin(this.orbitAngle) * radius;
        this.setPos(x, y, z);

        // Gövde teğet yöne bakar (türetme MODEL_YAW_OFFSET javadoc'unda). facingSign AYRIK
        // olduğu için hedef yaw yön dönüşünde 180° sıçrar; bu yüzden görünen yaw hız-sınırlı
        // (slew) yaklaştırılır. Yörünge KONUMU orbitAngle'dan geldiğinden hareket hiç
        // bozulmaz — yalnız gövde yönelimi yumuşar.
        float targetYaw = (float) Math.toDegrees(this.orbitAngle) + MODEL_YAW_OFFSET
                + (this.facingSign < 0 ? 180f : 0f);
        if (Float.isNaN(this.bodyYaw)) {
            this.bodyYaw = targetYaw; // ilk tick: slew yok, doğrudan otur
        }
        float yawDelta = Mth.wrapDegrees(targetYaw - this.bodyYaw);
        this.bodyYaw = Mth.wrapDegrees(this.bodyYaw + Mth.clamp(yawDelta, -YAW_SLEW, YAW_SLEW));
        this.setYRot(this.bodyYaw);
        this.yBodyRot = this.bodyYaw;
        this.yHeadRot = this.bodyYaw;
        this.setXRot((float) (-lunge * 28.0)); // animasyona headPitch verisi (dalış eğimi)

        // --- alev izi + dalış püskürtmesi (tick başına <= 12 partikül) ---
        emitTrail(server, gt, lunge > 0.05);
    }

    // ===================== hedefleme =====================

    /**
     * Yakma bandındaki ({@value #BAND_INNER}–{@value #BAND_OUTER}) en "yakın açılı"
     * canlıyı seçer. Bu seçim YALNIZCA görsel: hasar/tutuşturma hâlâ
     * {@code Spells.protegoDiabolica} döngülerinden gelir, buradan DEĞİL.
     */
    private LivingEntity pickBandTarget(ServerLevel server, Player owner) {
        AABB box = new AABB(owner.position(), owner.position()).inflate(BAND_OUTER, BAND_HEIGHT, BAND_OUTER);
        List<LivingEntity> candidates = server.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != owner && e.isAlive() && !e.isSpectator()
                        && !(e instanceof FiendfyreDragonEntity)); // başka bir tezahürü avlamasın
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : candidates) {
            double dx = e.getX() - owner.getX();
            double dz = e.getZ() - owner.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);
            if (flat < BAND_INNER || flat > BAND_OUTER) {
                continue;
            }
            double gap = Math.abs(wrapRadians(Math.atan2(dz, dx) - this.orbitAngle));
            // açısal yakınlık baskın; bandın tam üstündekiler küçük bir bonus alır
            double score = gap + Math.abs(flat - ORBIT_RADIUS) * 0.12;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    // ===================== lunge =====================

    private void startLunge(ServerLevel server) {
        this.lungeTicks = LUNGE_TOTAL_TICKS;
        this.lungeCooldown = LUNGE_COOLDOWN;
        SpellFx.sound(server, this.position(), SoundEvents.ENDER_DRAGON_FLAP, 0.7f, 1.45f);
        SpellFx.sound(server, this.position(), SoundEvents.BLAZE_SHOOT, 0.5f,
                SpellFx.vary(server, 1.6f));
    }

    /**
     * 0→1→0 dalış eğrisi (ortada tepe) — konum ve partikül yoğunluğunu sürer.
     *
     * <p>İlk {@value #BLEND_TICKS} tick DÜZ 0'dır: klip harmanı bitene kadar gövde
     * hâlâ fly pozundan lunge pozuna geçiyor; dalış hareketi de o ana kadar beklerse
     * hareket ile animasyon senkron kalır.
     */
    private double lungeCurve() {
        if (this.lungeTicks <= 0) {
            return 0.0;
        }
        int elapsed = LUNGE_TOTAL_TICKS - this.lungeTicks; // 0 .. LUNGE_TOTAL_TICKS-1
        double p = (elapsed - BLEND_TICKS) / (double) LUNGE_CURVE_SPAN;
        if (p <= 0.0 || p >= 1.0) {
            return 0.0;
        }
        return Math.sin(Math.PI * p);
    }

    // ===================== partiküller =====================

    /**
     * Gövde boyunca alev izi. Bütçe (brief: tick başına <= 12):
     * normalde 3 alev + (2 tickte 1) koyu bulut + (3 tickte 2) kıvılcım = <= 6;
     * dalışta 3 alev + 6 püskürtme + 3 halka alevi = 12.
     */
    private void emitTrail(ServerLevel server, long gt, boolean lunging) {
        // İz yönü GÖVDEDEN türetilir (yumuşatılmış bodyYaw), ayrık facingSign'dan DEĞİL:
        // aksi halde yön dönüşünde alev izi de tek karede gövdenin öbür tarafına atlardı.
        // +1 = burun ileri, -1 = geri; slew sırasında sürekli olarak 0'dan geçer.
        double dir = Math.cos(Math.toRadians(this.bodyYaw - MODEL_YAW_OFFSET) - this.orbitAngle);
        // 1) gövde/kuyruk boyunca 3 alev noktası (baştan geriye)
        for (int i = 0; i < 3; i++) {
            double a = this.orbitAngle - dir * (0.05 + i * 0.055);
            double r = ORBIT_RADIUS + (i == 2 ? 0.12 : 0.0);
            double px = this.getX() + (Math.cos(a) * r - Math.cos(this.orbitAngle) * ORBIT_RADIUS);
            double pz = this.getZ() + (Math.sin(a) * r - Math.sin(this.orbitAngle) * ORBIT_RADIUS);
            server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    px, this.getY() - i * 0.08, pz, 1, 0.12, 0.12, 0.12, 0.01);
        }
        if (lunging) {
            // 2a) çene alevi: hedefe (yoksa ileri) doğru 6 noktalık püskürtme
            Vec3 from = this.position().add(0, 0.35, 0);
            // hedefsiz yedek yönde dir'in SÜREKLİ değeri sıfıra düşebileceği için burada
            // işaretine indirgenir (püskürtme dönüşün ortasında sıfır boya inmesin)
            double fwd = dir >= 0 ? 1.0 : -1.0;
            Vec3 to = this.visualTarget != null
                    ? this.visualTarget.position().add(0, this.visualTarget.getBbHeight() * 0.5, 0)
                    : from.add(-Math.sin(this.orbitAngle) * fwd * 2.5, -0.4, Math.cos(this.orbitAngle) * fwd * 2.5);
            Vec3 step = to.subtract(from).scale(1.0 / 6.0);
            for (int i = 1; i <= 6; i++) {
                Vec3 p = from.add(step.scale(i));
                server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.02);
            }
            // 2b) halkada o yönde alev yükselmesi (mevcut çember FX'inin üstüne binen katman)
            Player owner = getOwner();
            if (owner != null) {
                for (int i = 0; i < 3; i++) {
                    double a = this.orbitAngle + (i - 1) * 0.16;
                    server.sendParticles(ParticleTypes.FLAME,
                            owner.getX() + Math.cos(a) * ORBIT_RADIUS,
                            owner.getY() + 0.2,
                            owner.getZ() + Math.sin(a) * ORBIT_RADIUS,
                            1, 0.06, 0.5, 0.06, 0.03);
                }
            }
            return;
        }
        // 3) koyu mavi duman bulutu (2 tickte 1)
        if (gt % 2 == 0) {
            server.sendParticles(SpellFx.glow(SpellFx.darken(FLAME_COLOR, 0.45f)),
                    this.getX(), this.getY() + 0.2, this.getZ(), 1, 0.25, 0.2, 0.25, 0.01);
        }
        // 4) kanat kıvılcımı (3 tickte 2)
        if (gt % 3 != 0) {
            server.sendParticles(SpellFx.glow(SpellFx.lighten(FLAME_COLOR, 0.45f)),
                    this.getX(), this.getY() + 0.45, this.getZ(), 2, 0.55, 0.18, 0.55, 0.01);
        }
    }

    // ===================== beliriş / dağılış =====================

    /** Çemberden yükseliş FX'i — doğum anında bir kez (summonFor çağırır). */
    private void spawnFx(ServerLevel server, Vec3 at) {
        SpellFx.nova(server, at, SpellFx.lighten(FLAME_COLOR, 0.35f), 22, 0.42);
        SpellFx.runeRing(server, at, SpellFx.lighten(FLAME_COLOR, 0.3f), 12, 1.4);
        server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.4, at.z,
                30, 0.35, 0.9, 0.35, 0.09);
        server.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.6, at.z,
                10, 0.3, 0.8, 0.3, 0.05);
        SpellFx.sound(server, at, SoundEvents.ENDER_DRAGON_GROWL, 0.55f, 1.5f);
        SpellFx.sound(server, at, SoundEvents.FIRECHARGE_USE, 0.8f, 0.7f);
    }

    /**
     * Dağılma evresine geç: {@code dissolve} klibi oynar, gövde alev alev söner,
     * {@value #DISSOLVE_TICKS} tick sonra {@code discard()}.
     */
    public void beginDissolve(ServerLevel server) {
        if (getState() == STATE_DISSOLVE) {
            return;
        }
        setState(STATE_DISSOLVE);
        this.visualTarget = null;
        this.lungeTicks = 0;
        this.entityData.set(LUNGING, false);
        Vec3 at = this.position();
        server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.3, at.z,
                26, 0.6, 0.5, 0.6, 0.06);
        server.sendParticles(SpellFx.glow(SpellFx.darken(FLAME_COLOR, 0.35f)),
                at.x, at.y + 0.3, at.z, 14, 0.7, 0.5, 0.7, 0.03);
        SpellFx.sound(server, at, SoundEvents.FIRE_EXTINGUISH, 0.7f, 0.85f);
    }

    /** Dağılma geri sayımı — hafif yükselerek söner, sonra kendini siler. */
    private void tickDissolve(ServerLevel server) {
        this.stateTicks++;
        this.setPos(this.getX(), this.getY() + 0.035, this.getZ());
        if (this.stateTicks % 2 == 0) {
            server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX(), this.getY() + 0.3, this.getZ(), 4, 0.45, 0.35, 0.45, 0.03);
        }
        if (this.stateTicks >= DISSOLVE_TICKS) {
            discard();
        }
    }

    /** Ejderha dünyadan çıkarken manager kaydını da temizle (sızıntı önleme). */
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide()) {
            UUID owner = getOwnerUUID();
            if (owner != null) {
                DiabolicaDragonManager.onDragonRemoved(owner, this.getUUID());
            }
        }
        super.remove(reason);
    }

    // ===================== dokunulmazlık / etkisizlik =====================

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // /kill ve void gibi bypass kaynakları ejderhayı sessizce siler; gerisi hiç işlenmez.
        // (26.x: /kill → LivingEntity.kill → hurtServer(genericKill); boşluk → onBelowWorld → hurt)
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            discard();
        }
        return false;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return true;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false; // ok/vuruş/etkileşim içinden geçer
    }

    @Override
    public boolean canBeCollidedWith(net.minecraft.world.entity.Entity other) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(net.minecraft.world.entity.Entity entity) {
        // itmez
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false; // kendisi ateşten yapılma — vanilla alev kaplaması istenmiyor
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null; // ses ritmi FX metotlarından sürülüyor
    }

    @Override
    protected float getSoundVolume() {
        return 0.5f;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        // havada — adım sesi yok
    }

    // ===================== GeckoLib =====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // TEK controller — DÖRT klip de SENKRON DURUMDAN türetilir (tetik paketi yok →
        // paket kaybında klip kaybı da yok): DISSOLVE → dissolve, RISE → roar,
        // dalışta → lunge, aksi halde fly loop'u.
        //
        // lunge'ın AYRI bir "action" katmanında OLMAMASI kasıtlıdır: GeckoLib yalnız
        // controller-İÇİ klip değişiminde harman yapar. Biten bir play_once klip
        // STOPPED'a düşer ve o kare hiçbir kemik yazmaz — üst katmandaki lunge bitince
        // gövde tek karede fly pozuna çakılırdı (blend-OUT yolu yoktur). Aynı
        // controller'da kalınca lunge→fly geçişi BLEND_TICKS tick boyunca lerplenir.
        controllers.add(new AnimationController<>("movement", BLEND_TICKS, state -> {
            byte s = getState();
            if (s == STATE_DISSOLVE) {
                return state.setAndContinue(DISSOLVE);
            }
            if (s == STATE_RISE) {
                return state.setAndContinue(ROAR);
            }
            if (isLunging()) {
                return state.setAndContinue(LUNGE);
            }
            return state.setAndContinue(FLY);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ===================== çağırma =====================

    /**
     * Kanalın İLK tick'inde ejderhayı doğurur (yalnız {@link DiabolicaDragonManager}
     * çağırmalı — "tek caster = tek ejderha" kuralı orada tutulur).
     *
     * @return doğan ejderha, EntityType.create başarısızsa null
     */
    public static FiendfyreDragonEntity summonFor(ServerPlayer owner, ServerLevel level) {
        FiendfyreDragonEntity dragon =
                com.arcanum.registry.ModEntities.DIABOLICA_DRAGON.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (dragon == null) {
            return null;
        }
        dragon.setOwnerUUID(owner.getUUID());
        dragon.entityData.set(STATE, STATE_RISE);
        // sahibin BAKTIĞI yönde belirir: bakış (-sin θ, cos θ) = (cos a, sin a) → a = θ + 90°
        dragon.orbitAngle = wrapRadians(Math.toRadians(owner.getYRot() + 90.0f));
        dragon.omega = BASE_OMEGA * 0.4;
        double x = owner.getX() + Math.cos(dragon.orbitAngle) * ORBIT_RADIUS;
        double z = owner.getZ() + Math.sin(dragon.orbitAngle) * ORBIT_RADIUS;
        double y = owner.getY() + BODY_Y - RISE_DEPTH;
        dragon.snapTo(x, y, z,(float) Math.toDegrees(dragon.orbitAngle) + MODEL_YAW_OFFSET, 0.0f);
        dragon.yBodyRot = dragon.getYRot();
        dragon.yHeadRot = dragon.getYRot();
        dragon.bodyYaw = dragon.getYRot(); // doğum anında slew yaşanmasın
        level.addFreshEntity(dragon);
        dragon.spawnFx(level, new Vec3(x, owner.getY(), z));
        return dragon;
    }

    // ===================== yardımcı =====================

    /** Açıyı (-pi, pi] aralığına sarar. */
    private static double wrapRadians(double angle) {
        double a = angle % (Math.PI * 2.0);
        if (a > Math.PI) {
            a -= Math.PI * 2.0;
        } else if (a <= -Math.PI) {
            a += Math.PI * 2.0;
        }
        return a;
    }
}
