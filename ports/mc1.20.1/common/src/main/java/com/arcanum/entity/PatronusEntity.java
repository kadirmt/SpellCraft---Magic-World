package com.arcanum.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import com.arcanum.config.ArcanumConfig;
import com.arcanum.registry.ModParticles;
import com.arcanum.spell.SpellFx;
import com.arcanum.util.ArcanumColorParticleOption;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Cisimleşmiş Patronus — Expecto Patronum'un çağırdığı gümüş-mavi ruhani yoldaş.
 *
 * <p>Üç görsel varyant tek sınıfta (senkronize {@link #VARIANT} byte alanı,
 * {@link PhoenixEntity}'deki desenle aynı): 0=Baykuş (uçar), 1=Kurt, 2=Geyik.
 * Varyant SEÇİM mantığı burada DEĞİL — çağıran ({@link #summonFor}) büyücü
 * seviyesine göre karar verir, entity yalnızca değeri saklar/uygular.
 *
 * <p>Sahibini (çağıran oyuncu) takip eder; her ~10 tick'te hedef seçer:
 * önce 16 blok içindeki en yakın {@link DementorEntity}, yoksa sahibin son
 * dövüştüğü düşman ({@link Enemy}). Ruh Emicilere 3x hasar vurur + sert geri
 * teper + hedefini sildirir (kaçış). Ruhani varlık: ateş/boğulma/düşme hasarı
 * almaz, itilmez, tasmalanamaz, doğal despawn olmaz — ömrünü {@link #lifeTicks}
 * sayacı sınırlar; süre dolunca ışıma bulutuyla dağılır.
 *
 * <p>1.20.1 PORT NOTLARI: {@code defineSynchedData()} parametresiz +
 * {@code entityData.define}; {@code canBeLeashed(Player)} imzalı;
 * {@code finalizeSpawn} 5 parametreli; renkli partikül vanilla
 * {@code ColorParticleOption} yerine {@link ArcanumColorParticleOption};
 * GeckoLib importları {@code core.*} paketlerinden.
 *
 * <p>GeckoLib animasyon sözleşmesi (3 varyant için AYNI isimler — asset
 * ajanları bu isimleri takip eder): {@code animation.patronus.idle} /
 * {@code .walk} / {@code .attack}.
 */
public class PatronusEntity extends PathfinderMob implements GeoEntity {
    /** Varyant sabitleri — {@link #VARIANT} synched byte değeri. */
    public static final byte VARIANT_OWL = 0;
    public static final byte VARIANT_WOLF = 1;
    public static final byte VARIANT_STAG = 2;

    /** Patronus ışıma rengi (RGB) — gümüş-mavi. */
    public static final int GLOW_COLOR = 0xBFE8FF;

    private static final EntityDataAccessor<Byte> VARIANT =
            SynchedEntityData.defineId(PatronusEntity.class, EntityDataSerializers.BYTE);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.patronus.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.patronus.walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.patronus.attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Çağıran oyuncunun UUID'si — yalnızca sunucu tarafı + NBT (senkron gerekmez). */
    private UUID ownerUUID;

    /** Kalan ömür (tick) — {@link #summonFor} config'den ayarlar, 0'a inince dağılır. */
    private int lifeTicks = 600;

    public PatronusEntity(EntityType<? extends PatronusEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 28.0)
                .add(Attributes.MOVEMENT_SPEED, 0.42)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                // FLYING_SPEED şart: baykuş varyantının FlyingMoveControl'u bu attribute'u okur
                .add(Attributes.FLYING_SPEED, 0.6);
    }

    // ===================== varyant =====================

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(VARIANT, VARIANT_OWL);
    }

    /** 0=Baykuş, 1=Kurt, 2=Geyik. */
    public byte getVariant() {
        return this.entityData.get(VARIANT);
    }

    public void setVariant(byte variant) {
        this.entityData.set(VARIANT, variant);
        applyVariantMovement();
    }

    /**
     * Varyanta uygun hareket kontrolü/navigasyonu uygular — yalnız sunucuda
     * (nav/moveControl sunucu-tarafı; yerçekimsizlik bayrağı zaten synched).
     * Baykuş: {@link FlyingMoveControl} + {@link FlyingPathNavigation} + yerçekimsiz
     * süzülme ({@link DementorEntity} deseni). Kurt/Geyik: standart yer navigasyonu.
     */
    private void applyVariantMovement() {
        if (this.level().isClientSide) {
            return;
        }
        if (getVariant() == VARIANT_OWL) {
            this.moveControl = new FlyingMoveControl(this, 10, true);
            FlyingPathNavigation nav = new FlyingPathNavigation(this, this.level());
            nav.setCanOpenDoors(false);
            nav.setCanFloat(true);
            this.navigation = nav;
            this.setNoGravity(true);
        } else {
            this.moveControl = new MoveControl(this);
            this.navigation = new GroundPathNavigation(this, this.level());
            this.setNoGravity(false);
        }
    }

    // ===================== sahip & ömür =====================

    public void setOwner(ServerPlayer owner) {
        this.ownerUUID = owner.getUUID();
    }

    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    /** Sahip oyuncu (aynı boyutta ve çevrimiçiyse), yoksa null. */
    public Player getOwner() {
        return this.ownerUUID == null ? null : this.level().getPlayerByUUID(this.ownerUUID);
    }

    public void setLifeTicks(int ticks) {
        this.lifeTicks = ticks;
    }

    // ===================== NBT =====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Variant", getVariant());
        tag.putInt("LifeTicks", this.lifeTicks);
        if (this.ownerUUID != null) {
            tag.putUUID("Owner", this.ownerUUID);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getByte("Variant"));
        if (tag.contains("LifeTicks")) {
            this.lifeTicks = tag.getInt("LifeTicks");
        }
        if (tag.hasUUID("Owner")) {
            this.ownerUUID = tag.getUUID("Owner");
        }
    }

    // ===================== AI =====================

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.addGoal(3, new FollowSummonerGoal(this, 1.15));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        // targetSelector kasıtlı boş — hedef seçimi aiStep'te (Dementor önceliği
        // + sahibin dövüştüğü düşman) her ~10 tick'te elle yapılır.
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        // --- ömür sayacı: dolunca ışıma bulutuyla dağıl ---
        if (--this.lifeTicks <= 0) {
            expire(server);
            return;
        }
        // --- hafif ruhani iz (ucuz: 5 tick'te 1 partikül) ---
        if (this.tickCount % 5 == 0) {
            server.sendParticles(glowOption(),
                    getX(), getY(0.6), getZ(), 1,
                    getBbWidth() * 0.3, getBbHeight() * 0.3, getBbWidth() * 0.3, 0.005);
        }
        // --- hedef seçimi (~10 tick'te bir) ---
        if (this.tickCount % 10 == 0) {
            updateTarget(server);
        }
    }

    /** Süre doldu: END_ROD + gümüş-mavi ışıma "puf"u ile görünür şekilde dağıl. */
    private void expire(ServerLevel server) {
        double cx = getX();
        double cy = getY(0.5);
        double cz = getZ();
        server.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 24,
                getBbWidth() * 0.5, getBbHeight() * 0.4, getBbWidth() * 0.5, 0.03);
        server.sendParticles(glowOption(), cx, cy, cz, 18,
                getBbWidth() * 0.4, getBbHeight() * 0.4, getBbWidth() * 0.4, 0.02);
        server.playSound(null, cx, cy, cz, SoundEvents.AMETHYST_BLOCK_CHIME,
                getSoundSource(), 0.8f, 0.7f);
        discard();
    }

    /**
     * Hedef seçimi — ÖNCELİK 1: 16 blok içindeki en yakın Ruh Emici;
     * ÖNCELİK 2: sahibin son saldıranı / son saldırdığı, düşmansa ({@link Enemy}).
     * Mevcut hedef geçersizleştiyse (öldü/koptu) bırakılır.
     */
    private void updateTarget(ServerLevel server) {
        // mevcut hedef geçersizse temizle
        LivingEntity current = getTarget();
        if (current != null && (!current.isAlive() || distanceToSqr(current) > 24.0 * 24.0)) {
            setTarget(null);
            current = null;
        }
        // 1) en yakın Dementor (16 blok)
        DementorEntity nearest = null;
        double best = Double.MAX_VALUE;
        List<DementorEntity> dementors = server.getEntitiesOfClass(DementorEntity.class,
                getBoundingBox().inflate(16.0), LivingEntity::isAlive);
        for (DementorEntity d : dementors) {
            double dist = distanceToSqr(d);
            if (dist < best) {
                best = dist;
                nearest = d;
            }
        }
        if (nearest != null) {
            setTarget(nearest);
            return;
        }
        // 2) sahibin dövüştüğü düşman
        Player owner = getOwner();
        if (owner == null) {
            return;
        }
        LivingEntity threat = validThreat(owner.getLastHurtByMob());
        if (threat == null) {
            threat = validThreat(owner.getLastHurtMob());
        }
        if (threat != null) {
            setTarget(threat);
            return;
        }
        // 3) yakindaki HERHANGI bir dusman (Enemy) — kiskirtilmasa da saldirir
        //    (troll, Olum Yiyen, zombi...): patronus aktifken cevreyi temizler (tester istegi).
        LivingEntity nearestEnemy = null;
        double bestEnemy = Double.MAX_VALUE;
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(12.0), en -> en.isAlive() && en instanceof Enemy)) {
            double d = distanceToSqr(e);
            if (d < bestEnemy) {
                bestEnemy = d;
                nearestEnemy = e;
            }
        }
        if (nearestEnemy != null) {
            setTarget(nearestEnemy);
        }
    }

    /** Aday hedef geçerli mi: canlı + düşman ({@link Enemy}) + 16 blok içinde. */
    private LivingEntity validThreat(LivingEntity candidate) {
        if (candidate == null || !candidate.isAlive() || !(candidate instanceof Enemy)) {
            return null;
        }
        return distanceToSqr(candidate) <= 16.0 * 16.0 ? candidate : null;
    }

    // ===================== dövüş =====================

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof DementorEntity dementor && this.level() instanceof ServerLevel server) {
            // Ruh Emici zayıflığı: taban hasarın 2 katı EK büyü hasarı (toplam ~3x)
            float base = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
            dementor.hurt(server.damageSources().indirectMagic(this, this), base * 2.0f);
            // sert geri tepme + hedefi bıraktır (ışıktan KAÇSIN)
            dementor.knockback(2.5, getX() - dementor.getX(), getZ() - dementor.getZ());
            dementor.setTarget(null);
            SpellFx.burst(server, dementor.position().add(0, 1.0, 0),
                    SpellFx.lighten(GLOW_COLOR, 0.3f), 10, 0.4, 0.15);
        }
        return hit;
    }

    @Override
    public void swing(InteractionHand hand, boolean updateSelf) {
        super.swing(hand, updateSelf);
        if (!this.level().isClientSide) {
            this.triggerAnim("attacking", "attack");
        }
    }

    // ===================== ruhani bağışıklıklar =====================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // ruhani varlık: ateş/boğulma/düşme işlemez; sahibi de ona zarar veremez
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        if (this.ownerUUID != null && source.getEntity() instanceof Player p
                && this.ownerUUID.equals(p.getUUID())) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false; // doğal despawn yok — ömrü lifeTicks sınırlar
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    // ===================== sesler (sessize yakın, ruhani) =====================

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4f;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // ruhani — adım sesi yok
    }

    // ===================== GeckoLib =====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5,
                state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
        controllers.add(new AnimationController<>(this, "attacking", 0,
                state -> state.setAndContinue(RawAnimation.begin()))
                .triggerableAnim("attack", ATTACK)
                .setSoundKeyframeHandler(state -> {}));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ===================== çağırma =====================

    /**
     * Sahip için Patronus çağırır. Aynı sahibin 64 blok içindeki eski Patronus'u
     * sessizce kaldırılır (tek Patronus kuralı). Varyant büyücü seviyesinden:
     * 1-7 Baykuş, 8-11 Kurt, 12+ Geyik. Ömür {@link ArcanumConfig#patronusDurationTicks}.
     *
     * @return doğan Patronus (EntityType.create başarısızsa null)
     */
    public static PatronusEntity summonFor(ServerPlayer owner, int wizardLevel, ServerLevel level) {
        // aynı sahibin yaşayan eski Patronus'unu kaldır
        for (PatronusEntity old : level.getEntitiesOfClass(PatronusEntity.class,
                owner.getBoundingBox().inflate(64.0),
                p -> p.isAlive() && owner.getUUID().equals(p.getOwnerUUID()))) {
            old.discard();
        }
        PatronusEntity patronus = com.arcanum.registry.ModEntities.PATRONUS.get().create(level);
        if (patronus == null) {
            return null;
        }
        // sahibin bakış yönünde ~1.5 blok ileriye doğar
        Vec3 look = owner.getLookAngle();
        Vec3 pos = owner.position().add(look.x * 1.5, 0.1, look.z * 1.5);
        patronus.moveTo(pos.x, pos.y, pos.z, owner.getYRot(), 0.0f);
        patronus.setOwner(owner);
        patronus.setVariant(variantForLevel(wizardLevel));
        patronus.setLifeTicks(ArcanumConfig.get().patronusDurationTicks);
        // 1.20.1: finalizeSpawn 5 parametreli (SpawnGroupData + CompoundTag)
        patronus.finalizeSpawn(level, level.getCurrentDifficultyAt(patronus.blockPosition()),
                MobSpawnType.MOB_SUMMONED, null, null);
        level.addFreshEntity(patronus);
        // parıltı novası + ruhani çağırma sesi
        SpellFx.nova(level, pos, GLOW_COLOR, 20, 0.5);
        SpellFx.nova(level, pos.add(0, 0.6, 0), SpellFx.lighten(GLOW_COLOR, 0.4f), 14, 0.35);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z,
                20, 0.5, 0.7, 0.5, 0.03);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE,
                owner.getSoundSource(), 0.9f, 1.5f);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ALLAY_AMBIENT_WITH_ITEM,
                owner.getSoundSource(), 1.0f, 1.2f);
        return patronus;
    }

    /** Seviye eşiği → varyant: 1-7 Baykuş, 8-11 Kurt, 12+ Geyik. */
    private static byte variantForLevel(int wizardLevel) {
        if (wizardLevel >= 12) {
            return VARIANT_STAG;
        }
        if (wizardLevel >= 8) {
            return VARIANT_WOLF;
        }
        return VARIANT_OWL;
    }

    /** Gümüş-mavi ışıma partikül seçeneği ({@link ModParticles#SPELL_GLOW}) — 1.20.1: ArcanumColorParticleOption. */
    private static ArcanumColorParticleOption glowOption() {
        return ArcanumColorParticleOption.create(ModParticles.SPELL_GLOW.get(), 0xFF000000 | GLOW_COLOR);
    }

    // ===================== sahibi takip goal'ü =====================

    /**
     * Sahibi takip — vanilla {@code FollowOwnerGoal} mantığının el yapımı kopyası
     * (TamableAnimal olmadığımız için): 10 bloktan uzaksa yürü/uç, 24 bloktan
     * uzaksa yanına ışınlan, ~5 bloğa yaklaşınca dur.
     */
    private static class FollowSummonerGoal extends Goal {
        private static final double START_DIST_SQR = 10.0 * 10.0;
        private static final double STOP_DIST_SQR = 5.0 * 5.0;
        private static final double TELEPORT_DIST_SQR = 24.0 * 24.0;

        private final PatronusEntity patronus;
        private final double speed;
        private int pathTimer;

        FollowSummonerGoal(PatronusEntity patronus, double speed) {
            this.patronus = patronus;
            this.speed = speed;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Player owner = this.patronus.getOwner();
            return owner != null && owner.isAlive() && !owner.isSpectator()
                    && this.patronus.getTarget() == null
                    && this.patronus.distanceToSqr(owner) > START_DIST_SQR;
        }

        @Override
        public boolean canContinueToUse() {
            Player owner = this.patronus.getOwner();
            return owner != null && owner.isAlive()
                    && this.patronus.getTarget() == null
                    && this.patronus.distanceToSqr(owner) > STOP_DIST_SQR;
        }

        @Override
        public void start() {
            this.pathTimer = 0;
        }

        @Override
        public void stop() {
            this.patronus.getNavigation().stop();
        }

        @Override
        public void tick() {
            Player owner = this.patronus.getOwner();
            if (owner == null) {
                return;
            }
            this.patronus.getLookControl().setLookAt(owner, 10.0f, 30.0f);
            if (this.patronus.distanceToSqr(owner) > TELEPORT_DIST_SQR) {
                teleportNear(owner);
                return;
            }
            if (--this.pathTimer <= 0) {
                this.pathTimer = 10;
                this.patronus.getNavigation().moveTo(owner, this.speed);
            }
        }

        /** Sahibin ±3 blok çevresinde çarpışmasız bir noktaya ışınlan (10 deneme). */
        private void teleportNear(Player owner) {
            for (int attempt = 0; attempt < 10; attempt++) {
                double x = owner.getX() + (this.patronus.getRandom().nextDouble() - 0.5) * 6.0;
                double y = owner.getY() + this.patronus.getRandom().nextInt(3) - 1;
                double z = owner.getZ() + (this.patronus.getRandom().nextDouble() - 0.5) * 6.0;
                if (this.patronus.level().noCollision(this.patronus,
                        this.patronus.getBoundingBox().move(
                                x - this.patronus.getX(), y - this.patronus.getY(), z - this.patronus.getZ()))) {
                    this.patronus.moveTo(x, y, z, this.patronus.getYRot(), this.patronus.getXRot());
                    this.patronus.getNavigation().stop();
                    return;
                }
            }
            // hiçbiri uymadıysa doğrudan sahibin konumuna
            this.patronus.moveTo(owner.getX(), owner.getY(), owner.getZ(),
                    this.patronus.getYRot(), this.patronus.getXRot());
            this.patronus.getNavigation().stop();
        }
    }
}
