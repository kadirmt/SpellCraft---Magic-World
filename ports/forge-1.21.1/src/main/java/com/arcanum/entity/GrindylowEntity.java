package com.arcanum.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Grindylow — su altında yaşayan, uzun parmaklı, saldırgan küçük deniz
 * yaratığı. Yalnızca suda hareket eder/saldırır (Guardian'a benzer su-sınırlı
 * navigasyon); ısırığı kısa bir "boğulma" (Nausea + solunum düşürme benzeri)
 * etkisi bırakır. Karaya çıkmaz.
 */
public class GrindylowEntity extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.grindylow.idle");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.grindylow.swim");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.grindylow.attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Kaç tick'tir sudan çıkmış durumda — kısa bir tolerans penceresi sağlar. */
    private int outOfWaterTicks = 0;

    public GrindylowEntity(EntityType<? extends GrindylowEntity> type, Level level) {
        super(type, level);
        this.moveControl = new MoveControl(this);
        this.setPathfindingMalus(PathType.WATER, 0.0f);
        this.setPathfindingMalus(PathType.WATER_BORDER, 8.0f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    /**
     * Doğal doğumda ek seyreklik zarı (1/N). Ağırlık zaten en düşük (1) olduğundan
     * "bayağı seyrek" hedefi ancak bununla tutuyor — ölçüm: g9-grindylow/26.1.2-fabric/RAPOR.md.
     */
    private static final int NATURAL_RARITY = 3;

    /**
     * Yalnız derin suda (pos ve üstü su) ve karanlıkta doğar; barışçılda doğmaz.
     * Işık kuralı vanilla Drowned ile aynı (trial spawner ışığı yok sayar).
     */
    public static boolean canSpawn(EntityType<GrindylowEntity> type, ServerLevelAccessor level,
                                    MobSpawnType reason, BlockPos pos, RandomSource random) {
        boolean natural = reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
        return (!natural || random.nextInt(NATURAL_RARITY) == 0)
                && level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
                && level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)
                && level.getFluidState(pos.above()).is(net.minecraft.tags.FluidTags.WATER)
                && (MobSpawnType.ignoresLightRequirements(reason)
                        || Monster.isDarkEnoughToSpawn(level, pos, random));
    }

    // Taban Mob#checkSpawnObstruction doğum kutusunda sıvı istemez → su şartıyla hiç doğamazdı.
    // Kullanıcı kararı (2026-09-24): okyanusta yalnız karanlıkta, seyrek doğar (ağırlık bkz. add_spawn_grindylow.json).
    @Override
    public boolean checkSpawnObstruction(net.minecraft.world.level.LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.4, true));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, true));
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    public void travel(net.minecraft.world.phys.Vec3 travelVector) {
        if (this.isInWater()) {
            this.moveRelative(this.getSpeed(), travelVector);
            this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }
        if (this.isInWaterOrBubble()) {
            this.outOfWaterTicks = 0;
            return;
        }
        this.outOfWaterTicks++;
        // 5 saniyelik tolerans penceresinden sonra saniyede 1 can zarar görür
        // (kıyıda avlanırken anında ölmemesi için — drowned'ın aksine kısa bir şans tanır).
        if (this.outOfWaterTicks > 100 && this.outOfWaterTicks % 20 == 0) {
            this.hurt(this.damageSources().drown(), 1.0f);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
        }
        return hit;
    }

    @Override
    public void swing(net.minecraft.world.InteractionHand hand, boolean updateSelf) {
        super.swing(hand, updateSelf);
        if (!this.level().isClientSide) {
            this.triggerAnim("controller", "attack");
        }
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 4,
                state -> state.setAndContinue(state.isMoving() ? SWIM : IDLE))
                .triggerableAnim("attack", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
