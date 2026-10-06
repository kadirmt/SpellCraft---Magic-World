package com.arcanum.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Hippogriff — kartal başlı/kanatlı, at gövdeli gururlu yaratık. HP lore'una
 * uygun "eğilme ritüeli": önce shift+sağ-tık ile eğilmeden (empty hand)
 * yaklaşıp beslemeye/evcilleştirmeye çalışırsan reddedip düşmanlaşır; eğilip
 * saygı gösterirsen (100 tick pencere) ardından çiğ tavşan/tavukla
 * evcilleştirilebilir. Evcilleştirilince eyerle (vanilla Saddle) binilip
 * uçulabilir (Unicorn/Thestral ile aynı desen).
 */
public class HippogriffEntity extends TamableAnimal implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.hippogriff.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.hippogriff.walk");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.hippogriff.fly");
    private static final RawAnimation BOW = RawAnimation.begin().thenPlay("animation.hippogriff.bow");

    private static final EntityDataAccessor<Boolean> SADDLED =
            SynchedEntityData.defineId(HippogriffEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Oyuncu UUID'sine göre "az önce eğildi" penceresi (tick). */
    private final java.util.Map<java.util.UUID, Integer> bowedRecently = new java.util.HashMap<>();

    /** Saygısız besleme sonrası kızgınlık süresi (tick) — sıfırlanınca hedef otomatik temizlenir. */
    private int angryTicks = 0;

    public HippogriffEntity(EntityType<? extends HippogriffEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 22.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SADDLED, false);
    }

    public boolean isSaddled() {
        return this.entityData.get(SADDLED);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Saddled", isSaddled());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(SADDLED, tag.getBoolean("Saddled"));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.3));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
        this.goalSelector.addGoal(4, new FlyWanderGoal(this, 1.0, 18));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(6, new FloatGoal(this));
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.RABBIT) || stack.is(Items.CHICKEN);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }
        if (!bowedRecently.isEmpty()) {
            bowedRecently.replaceAll((id, ticks) -> ticks - 1);
            bowedRecently.values().removeIf(ticks -> ticks <= 0);
        }
        if (this.angryTicks > 0 && --this.angryTicks == 0) {
            this.setTarget(null);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // eğilme ritüeli: elde eşya yokken shift+sağ-tık
        if (stack.isEmpty() && player.isShiftKeyDown() && !this.isTame()) {
            if (!this.level().isClientSide) {
                bowedRecently.put(player.getUUID(), 100);
                this.triggerAnim("controller", "bow");
                this.playSound(SoundEvents.PARROT_IMITATE_PHANTOM, 1.0f, 1.4f);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // eyer çıkar (yalnızca binilmiyorken, sahibi, tame)
        if (stack.isEmpty() && player.isShiftKeyDown() && isSaddled() && this.isTame()
                && this.isOwnedBy(player) && !this.isVehicle()) {
            if (!this.level().isClientSide) {
                this.entityData.set(SADDLED, false);
                this.spawnAtLocation(Items.SADDLE);
                this.playSound(SoundEvents.HORSE_SADDLE, 1.0f, 1.0f);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // eyer tak
        if (!stack.isEmpty() && stack.is(Items.SADDLE) && this.isTame() && this.isOwnedBy(player) && !isSaddled()) {
            if (!this.level().isClientSide) {
                this.entityData.set(SADDLED, true);
                this.playSound(SoundEvents.HORSE_SADDLE, 1.0f, 1.0f);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // besleme / evcilleştirme — YALNIZCA az önce eğilinmişse
        if (!stack.isEmpty() && isFood(stack) && !this.level().isClientSide) {
            boolean bowed = bowedRecently.getOrDefault(player.getUUID(), 0) > 0;
            if (!this.isTame()) {
                if (!bowed) {
                    // saygısızlık: reddeder, hafif düşmanlaşır (geçici — angryTicks sıfırlanınca hedef temizlenir)
                    this.level().broadcastEntityEvent(this, (byte) 6);
                    this.setTarget(player);
                    this.angryTicks = 300;
                    return InteractionResult.SUCCESS;
                }
                if (this.random.nextInt(3) == 0) {
                    this.tame(player);
                    this.navigation.stop();
                    this.setTarget(null);
                    bowedRecently.remove(player.getUUID());
                    this.level().broadcastEntityEvent(this, (byte) 7);
                } else {
                    this.level().broadcastEntityEvent(this, (byte) 6);
                }
            } else if (this.isOwnedBy(player)) {
                this.heal(4.0f);
                this.level().broadcastEntityEvent(this, (byte) 7);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }

        // binme
        if (this.isTame() && this.isOwnedBy(player) && isSaddled() && !this.isVehicle() && stack.isEmpty()
                && !this.level().isClientSide) {
            player.startRiding(this);
            return InteractionResult.SUCCESS;
        }

        return super.mobInteract(player, hand);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return isSaddled() && this.getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    public boolean isNoGravity() {
        return this.isVehicle() || super.isNoGravity();
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        this.setYRot(player.getYRot());
        this.setXRot(player.getXRot() * 0.6f);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        float forward = player.zza;
        float strafe = player.xxa * 0.4f;
        if (forward < 0.0f) {
            forward *= 0.5f;
        }
        double vertical = 0.0;
        if (forward != 0.0f) {
            vertical = -Math.sin(Math.toRadians(player.getXRot())) * forward;
        }
        return new Vec3(strafe, vertical, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        float base = (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return base * (player.isSprinting() ? 2.6f : 1.6f);
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isVehicle() && this.getControllingPassenger() instanceof Player) {
            if (this.isControlledByLocalInstance()) {
                float yaw = this.getYRot() * Mth.DEG_TO_RAD;
                float sin = Mth.sin(yaw);
                float cos = Mth.cos(yaw);
                Vec3 target = new Vec3(
                        travelVector.x * cos - travelVector.z * sin,
                        travelVector.y,
                        travelVector.z * cos + travelVector.x * sin);
                if (target.lengthSqr() > 1.0) {
                    target = target.normalize();
                }
                target = target.scale(this.getSpeed());
                Vec3 velocity = this.getDeltaMovement().scale(0.8).add(target.scale(0.2));
                this.setDeltaMovement(velocity);
                this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
            }
            this.calculateEntityAnimation(false);
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        // 1.20.1 notu: 1.21.1'deki getPassengerAttachmentPoint (0, 1.35, -0.1)
        // karşılığı — oyuncunun 1.21.1 vehicle-attachment ofseti (0.6) düşülerek
        // dünya konumuna çevrildi; binici görsel olarak birebir aynı yerde oturur.
        if (!this.hasPassenger(passenger)) {
            return;
        }
        Vec3 off = new Vec3(0.0, 1.35 - 0.6, -0.1)
                .yRot(-this.getYRot() * Mth.DEG_TO_RAD);
        callback.accept(passenger, this.getX() + off.x, this.getY() + off.y, this.getZ() + off.z);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5,
                state -> {
                    if (this.isVehicle() || !this.onGround()) {
                        return state.setAndContinue(FLY);
                    }
                    return state.setAndContinue(state.isMoving() ? WALK : IDLE);
                }).triggerableAnim("bow", BOW));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** 3D rastgele dolaşma — yer-tabanlı stroll yerine uçan yaratığa uygun. */
    private static class FlyWanderGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private final net.minecraft.world.entity.PathfinderMob mob;
        private final double speed;
        private final int range;

        FlyWanderGoal(net.minecraft.world.entity.PathfinderMob mob, double speed, int range) {
            this.mob = mob;
            this.speed = speed;
            this.range = range;
            this.setFlags(java.util.EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.mob.getNavigation().isDone() && this.mob.getRandom().nextInt(30) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            net.minecraft.core.BlockPos base = this.mob.blockPosition();
            double x = base.getX() + (this.mob.getRandom().nextDouble() - 0.5) * 2 * range;
            double y = base.getY() + (this.mob.getRandom().nextDouble() - 0.5) * 2 * (range / 2.0);
            double z = base.getZ() + (this.mob.getRandom().nextDouble() - 0.5) * 2 * range;
            this.mob.getNavigation().moveTo(x, y, z, this.speed);
        }
    }
}
