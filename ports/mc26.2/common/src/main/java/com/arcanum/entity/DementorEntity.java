package com.arcanum.entity;

import java.util.EnumSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

/**
 * Ruh Emici (Dementor) — süzülen karanlık varlık. FlyingMoveControl ile uçar,
 * hedefin 1.5-2.5 blok üstünden süzülerek yaklaşır; 3.5 blok içindeki oyunculardan
 * "ruh emer" (Darkness + Slowness + büyü hasarı, kendini iyileştirir).
 * Patronus zayıflığı: dolaylı büyü (indirectMagic) hasarını 1.5x alır.
 * Gün ışığında yanmaz ama gökyüzünü görüyorsa gündüz yavaşlar. Gloomwood'da gece süzülür.
 */
public class DementorEntity extends Monster implements GeoEntity {
    private static final RawAnimation FLOAT = RawAnimation.begin().thenLoop("animation.dementor.float");

    /** Ruh emme aurası yarıçapı (blok). */
    private static final double AURA_RADIUS = 3.5;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Son lanet sesi tick'i — ELDER_GUARDIAN_CURSE 4 sn'de en fazla bir kez. */
    private int lastCurseSoundTick = -80;

    public DementorEntity(EntityType<? extends DementorEntity> type, Level level) {
        super(type, level);
        this.xpReward = 15;
        // uçuş: yerçekimsiz süzülme; canHover=true → hedefsizken havada asılı kalır
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 26.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 28.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new FloatTowardTargetGoal(this));
        this.goalSelector.addGoal(5, new RandomFloatAroundGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        // Ölümyiyenlerin kontrolünden çıkmıştır — birbirlerine düşman.
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, DeathEaterEntity.class, true));
    }

    // ===================== ruh emme aurası =====================

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        // 3.5 blok içindeki oyunculardan her 25 tick'te ruh em
        if (this.tickCount % 25 == 0) {
            boolean drained = false;
            AABB box = getBoundingBox().inflate(AURA_RADIUS);
            for (Player p : server.getEntitiesOfClass(Player.class, box,
                    pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
                p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
                p.hurtServer(server, server.damageSources().indirectMagic(this, this), 1.5f);
                soulDrainFx(server, p);
                drained = true;
            }
            // Ölümyiyenler de aurada zarar görür — Darkness/hız-yavaşlatma oyuncuya
            // özgü olduğundan yalnızca hasar + kendini iyileştirme uygulanır.
            for (DeathEaterEntity de : server.getEntitiesOfClass(DeathEaterEntity.class, box,
                    LivingEntity::isAlive)) {
                de.hurtServer(server, server.damageSources().indirectMagic(this, this), 1.5f);
                drained = true;
            }
            if (drained) {
                heal(2.0f);
                if (this.tickCount - this.lastCurseSoundTick >= 80) {
                    this.lastCurseSoundTick = this.tickCount;
                    server.playSound(null, getX(), getY(), getZ(), SoundEvents.ELDER_GUARDIAN_CURSE,
                            getSoundSource(), 0.3f, 1.1f);
                }
            }
        }
        // gündüz gökyüzü altında halsizleşir (yanmaz — sadece yavaşlık)
        if (this.tickCount % 40 == 0 && server.isBrightOutside() && server.canSeeSky(blockPosition())) {
            addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
        }
    }

    /** Oyuncudan dementor'a akan ruh hattı: 4-5 noktada SMOKE + SOUL. */
    private void soulDrainFx(ServerLevel server, Player p) {
        Vec3 from = p.getEyePosition();
        Vec3 to = getEyePosition();
        int points = 5;
        for (int i = 0; i < points; i++) {
            double t = (i + 0.5) / points;
            Vec3 q = from.lerp(to, t);
            server.sendParticles(ParticleTypes.SMOKE, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0.01);
            server.sendParticles(ParticleTypes.SOUL, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0.02);
        }
    }

    // ===================== zayıflık / bağışıklık =====================

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Patronus zayıflığı: dolaylı büyü hasarı (expecto patronum vb.) 1.5x işler
        // (26.x: Entity.hurt final → override noktası hurtServer; tüm sunucu hasar yolları buradan geçer)
        if (source.is(DamageTypes.INDIRECT_MAGIC)) {
            amount *= 1.5f;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false; // süzülen varlık düşme hasarı almaz
    }

    // ===================== sesler =====================

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        // derin, boğuk hırıltı
        return 0.55f + this.random.nextFloat() * 0.1f;
    }

    // ===================== GeckoLib =====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("controller", 6,
                state -> state.setAndContinue(FLOAT)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ===================== uçuş goal'ları =====================

    /**
     * Hedefe 1.5-2.5 blok yükseklikten süzülerek yaklaşma — doğrudan
     * FlyingMoveControl.setWantedPosition ile (navigasyonsuz, hayalet süzülüşü).
     * Süzülme yüksekliği sinüsle 1.5-2.5 arasında dalgalanır.
     */
    static class FloatTowardTargetGoal extends Goal {
        private final DementorEntity mob;

        FloatTowardTargetGoal(DementorEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) {
                return;
            }
            double hover = 2.0 + Math.sin(mob.tickCount * 0.08) * 0.5; // 1.5 .. 2.5
            mob.getMoveControl().setWantedPosition(t.getX(), t.getY() + hover, t.getZ(), 1.0);
        }
    }

    /**
     * Hedef yokken rastgele süzülme — vanilla Ghast.RandomFloatAroundGoal'dan
     * uyarlandı (daha dar yarıçap: ±8 yatay, ±2 dikey; ağaç seviyesinde kalsın).
     */
    static class RandomFloatAroundGoal extends Goal {
        private final DementorEntity mob;

        RandomFloatAroundGoal(DementorEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            MoveControl mc = mob.getMoveControl();
            if (!mc.hasWanted()) {
                return true;
            }
            // Ghast deseni: hedefe çok yakınsa ya da anlamsızca uzaksa yeni nokta seç
            double dx = mc.getWantedX() - mob.getX();
            double dy = mc.getWantedY() - mob.getY();
            double dz = mc.getWantedZ() - mob.getZ();
            double dSq = dx * dx + dy * dy + dz * dz;
            return dSq < 1.0 || dSq > 3600.0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            RandomSource r = mob.getRandom();
            double x = mob.getX() + (r.nextFloat() * 2.0F - 1.0F) * 8.0F;
            double y = mob.getY() + (r.nextFloat() * 2.0F - 1.0F) * 2.0F;
            double z = mob.getZ() + (r.nextFloat() * 2.0F - 1.0F) * 8.0F;
            mob.getMoveControl().setWantedPosition(x, y, z, 0.9);
        }
    }
}
