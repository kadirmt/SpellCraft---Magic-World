package com.arcanum.entity;

import java.util.EnumSet;

import com.arcanum.spell.SpellFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Ölümyiyen — kara büyücü. SpellCastGoal ile 5-14 blok bandında strafe atarak
 * büyü kastlar (stupefy/incendio/expelliarmus/sectumsempra; canı %40 altına
 * düşünce kendine episkey). Menzil bandı dışına (yakına) girilirse melee'ye döner.
 * Monster olduğu için gün ışığında YANMAZ (zombi/iskelet davranışı yok).
 * Kamp yapılarında 2-3 kişilik gruplar halinde bulunur; gloomwood'da gece spawn olur.
 */
public class DeathEaterEntity extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.death_eater.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.death_eater.walk");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.death_eater.cast");

    // ---- büyü tema renkleri (ModSpells paletiyle uyumlu) ----
    private static final int COLOR_EPISKEY = 0xFFD37A;
    private static final int COLOR_STUPEFY = 0xE03A1C;
    private static final int COLOR_INCENDIO = 0xFF6A00;
    private static final int COLOR_EXPELLIARMUS = 0xE8344E;
    private static final int COLOR_SECTUMSEMPRA = 0xC0392B;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Son episkey kastının tick'i — şifa (ve PLAYER_LEVELUP sesi) 8 sn'de en fazla bir kez. */
    private int lastEpiskeyTick = -160;

    public DeathEaterEntity(EntityType<? extends DeathEaterEntity> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Büyü kast goal'u öncelik 2: 5-14 blok bandındayken MOVE bayrağını tutar
        // ve melee (öncelik 3) çalışamaz; hedef yakına sokulursa melee devralır.
        this.goalSelector.addGoal(2, new SpellCastGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Villager.class, true));
        // Ruh Emiciler Ölümyiyenlerin de kontrolünden çıkmıştır — birbirlerine düşman.
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, DementorEntity.class, true));
    }

    // ===================== büyü kastı =====================

    /** Rastgele saldırı büyüsü ya da (can düşükse) kendine şifa kastlar. */
    void castSpell(LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        // kast koreografisi: kol savurma animasyonu + evoker vızıltısı
        triggerAnim("controller", "cast");
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL,
                getSoundSource(), 1.0f, 0.9f + this.random.nextFloat() * 0.2f);

        // canı %40 altındaysa önce kendini topla (8 sn'de en fazla bir kez)
        if (getHealth() < getMaxHealth() * 0.4f && this.tickCount - this.lastEpiskeyTick >= 160) {
            this.lastEpiskeyTick = this.tickCount;
            heal(6.0f);
            SpellFx.helix(server, this, COLOR_EPISKEY, 2.2, 20);
            SpellFx.runeRing(server, position(), COLOR_EPISKEY, 6, 0.7);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_LEVELUP,
                    getSoundSource(), 0.8f, 1.4f);
            return;
        }

        Vec3 from = getEyePosition();
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
        int roll = this.random.nextInt(100);
        if (roll < 35) {
            castStupefy(server, target, from, to);
        } else if (roll < 60) {
            castIncendio(server, target, from, to);
        } else if (roll < 80) {
            castExpelliarmus(server, target, from, to);
        } else {
            castSectumsempra(server, target, from, to);
        }
    }

    /** Stupefy — kızıl titrek şimşek: 4.2 hasar + Slowness IV (60t). */
    private void castStupefy(ServerLevel server, LivingEntity t, Vec3 from, Vec3 to) {
        jitterLine(server, from, to, COLOR_STUPEFY);
        t.hurt(mobMagic(server), 4.2f);
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3));
        SpellFx.burst(server, to, COLOR_STUPEFY, 14, 0.30, 0.12);
        SpellFx.runeRing(server, position(), COLOR_STUPEFY, 8, 1.0);
        server.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                getSoundSource(), 0.5f, 1.7f);
    }

    /** Incendio — turuncu alev ışını: tutuşturur (80t) + 4.2 hasar. */
    private void castIncendio(ServerLevel server, LivingEntity t, Vec3 from, Vec3 to) {
        beamLine(server, from, to, COLOR_INCENDIO);
        SpellFx.line(server, from, to, ParticleTypes.FLAME, 0.5);
        t.setRemainingFireTicks(80);
        t.hurt(mobMagic(server), 4.2f);
        server.sendParticles(ParticleTypes.FLAME, to.x, to.y, to.z, 12, 0.25, 0.25, 0.25, 0.05);
        SpellFx.burst(server, to, COLOR_INCENDIO, 10, 0.25, 0.10);
        SpellFx.runeRing(server, position(), COLOR_INCENDIO, 8, 1.0);
        server.playSound(null, to.x, to.y, to.z, SoundEvents.FIRECHARGE_USE,
                getSoundSource(), 0.7f, 1.0f);
    }

    /**
     * Expelliarmus — silahsızlandırma: %60 şansla hedefin elindeki eşya savrulur
     * (Spells.expelliarmus deseni: slot boşaltılır + ItemEntity fırlatılır).
     */
    private void castExpelliarmus(ServerLevel server, LivingEntity t, Vec3 from, Vec3 to) {
        beamLine(server, from, to, COLOR_EXPELLIARMUS);
        if (this.random.nextFloat() < 0.6f) {
            ItemStack held = t.getMainHandItem();
            if (!held.isEmpty()) {
                t.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                ItemEntity drop = new ItemEntity(server, t.getX(), t.getEyeY(), t.getZ(), held.copy());
                Vec3 fling = position().subtract(t.position()).normalize().scale(0.7);
                drop.setDeltaMovement(fling.x, 0.3, fling.z);
                drop.setPickUpDelay(15);
                server.addFreshEntity(drop);
                // savrulan eşyanın izlediği yay
                SpellFx.line(server, t.getEyePosition(), position().add(0, 1.0, 0),
                        SpellFx.trail(SpellFx.lighten(COLOR_EXPELLIARMUS, 0.3f)), 0.5);
            }
        }
        SpellFx.burst(server, to, COLOR_EXPELLIARMUS, 12, 0.25, 0.10);
        SpellFx.runeRing(server, position(), COLOR_EXPELLIARMUS, 8, 1.0);
        server.playSound(null, to.x, to.y, to.z, SoundEvents.TRIDENT_RETURN,
                getSoundSource(), 0.8f, 1.3f);
    }

    /** Sectumsempra — koyu kızıl kesik: 7 hasar + Wither (100t) + X yara izi. */
    private void castSectumsempra(ServerLevel server, LivingEntity t, Vec3 from, Vec3 to) {
        int dark = SpellFx.darken(COLOR_SECTUMSEMPRA, 0.2f);
        beamLine(server, from, to, dark);
        t.hurt(mobMagic(server), 7.0f);
        t.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
        // X biçimli çapraz kesik izleri
        double s = 0.7;
        SpellFx.line(server, to.add(-s, s, -s * 0.3), to.add(s, -s, s * 0.3), SpellFx.trail(dark), 0.12);
        SpellFx.line(server, to.add(s, s, s * 0.3), to.add(-s, -s, -s * 0.3), SpellFx.trail(dark), 0.12);
        server.sendParticles(ParticleTypes.DAMAGE_INDICATOR, to.x, to.y, to.z, 8, 0.3, 0.3, 0.3, 0.12);
        SpellFx.runeRing(server, position(), COLOR_SECTUMSEMPRA, 8, 1.0);
        server.playSound(null, to.x, to.y, to.z, SoundEvents.PLAYER_ATTACK_SWEEP,
                getSoundSource(), 0.9f, 0.8f);
    }

    /** Düz ışın: göz hizasından hedefe — oyuncu büyüleriyle AYNI yıldırım motoru (istemcide çizilir). */
    private void beamLine(ServerLevel server, Vec3 from, Vec3 to, int color) {
        com.arcanum.network.ArcanumNetwork.sendBeam(server, from, to, color, 10, 0.05f, 0f);
        SpellFx.burst(server, from, SpellFx.lighten(color, 0.3f), 4, 0.10, 0.03);
    }

    /** Titrek/vahşi ışın (lanetler) — aynı motor, geniş kırılma genliği. */
    private void jitterLine(ServerLevel server, Vec3 from, Vec3 to, int color) {
        com.arcanum.network.ArcanumNetwork.sendBeam(server, from, to, color, 10, 0.06f, 0.6f);
    }

    private DamageSource mobMagic(ServerLevel server) {
        return server.damageSources().indirectMagic(this, this);
    }

    // ===================== sesler =====================

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PILLAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        // pitch ~0.8: boğuk, maske ardından gelen ses
        return 0.75f + this.random.nextFloat() * 0.1f;
    }

    // ===================== GeckoLib =====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5,
                state -> state.setAndContinue(state.isMoving() ? WALK : IDLE))
                .triggerableAnim("cast", CAST));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ===================== SpellCastGoal =====================

    /**
     * Büyü kast Goal'u — hedef 5-14 blok bandında ve görüşteyken vanilla
     * RangedBowAttackGoal'un strafe desenini uygular; her 50-80 tick'te bir
     * büyü kastlar. MOVE bayrağını tuttuğu için melee goal bastırılır.
     */
    static class SpellCastGoal extends Goal {
        private static final double MIN_RANGE_SQ = 5.0 * 5.0;
        private static final double MAX_RANGE_SQ = 14.0 * 14.0;
        /** Sürdürme bandı biraz geniş — sınırda titremesin (hysteresis). */
        private static final double CONT_MIN_SQ = 4.0 * 4.0;
        private static final double CONT_MAX_SQ = 15.0 * 15.0;

        private final DeathEaterEntity mob;
        private int castTimer;
        private int seeTime;
        private int strafingTime = -1;
        private boolean strafingClockwise;
        private boolean strafingBackwards;

        SpellCastGoal(DeathEaterEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            if (t == null || !t.isAlive()) {
                return false;
            }
            double d = mob.distanceToSqr(t);
            return d >= MIN_RANGE_SQ && d <= MAX_RANGE_SQ;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = mob.getTarget();
            if (t == null || !t.isAlive()) {
                return false;
            }
            double d = mob.distanceToSqr(t);
            return d >= CONT_MIN_SQ && d <= CONT_MAX_SQ;
        }

        @Override
        public void start() {
            // ilk kast hafif gecikmeli — spawn kamplarında senkron yaylım olmasın
            this.castTimer = 20 + mob.getRandom().nextInt(20);
        }

        @Override
        public void stop() {
            this.seeTime = 0;
            this.strafingTime = -1;
            mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            double distSq = mob.distanceToSqr(target);
            boolean canSee = mob.getSensing().hasLineOfSight(target);
            this.seeTime = canSee ? this.seeTime + 1 : 0;

            // RangedBowAttackGoal strafe deseni: görüşteyse dur + yan adım, değilse yaklaş
            if (distSq <= MAX_RANGE_SQ && this.seeTime >= 20) {
                mob.getNavigation().stop();
                this.strafingTime++;
            } else {
                mob.getNavigation().moveTo(target, 1.0);
                this.strafingTime = -1;
            }
            if (this.strafingTime >= 20) {
                if (mob.getRandom().nextFloat() < 0.3f) {
                    this.strafingClockwise = !this.strafingClockwise;
                }
                if (mob.getRandom().nextFloat() < 0.3f) {
                    this.strafingBackwards = !this.strafingBackwards;
                }
                this.strafingTime = 0;
            }
            if (this.strafingTime > -1) {
                if (distSq > MAX_RANGE_SQ * 0.75) {
                    this.strafingBackwards = false;
                } else if (distSq < MAX_RANGE_SQ * 0.25) {
                    this.strafingBackwards = true;
                }
                mob.getMoveControl().strafe(this.strafingBackwards ? -0.5f : 0.5f,
                        this.strafingClockwise ? 0.5f : -0.5f);
                mob.lookAt(target, 30.0f, 30.0f);
            } else {
                mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
            }

            // 50-80 tick'te bir büyü (yalnız görüş varken)
            if (--this.castTimer <= 0) {
                if (!canSee) {
                    return;
                }
                this.castTimer = 50 + mob.getRandom().nextInt(31);
                mob.castSpell(target);
            }
        }
    }
}
