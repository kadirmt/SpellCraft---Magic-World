package com.arcanum.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Thestral — kanatlı, uçabilen at-benzeri yaratık. Elma/havuç/altın havuç/altın
 * elma/ekmekle evcilleştirilir (kaynak: HogCraft, %33 şans), eyer GEREKMEZ —
 * evcilleştirilince direkt binilir. Yerdeyken normal yürür; biniciliyken uçuş
 * fiziği (BroomEntity ile aynı desen: bakış pitch'i yükselt/alçalt). Doğal
 * ortam: gloomwood — kaynağın dark_forest tercihiyle uyumlu, ölüm/karanlık
 * temasına lore olarak uygun (kullanıcı onayı: bazı yaratıklar gloomwood'da
 * kalabilir, hepsi tek biyoma sıkışmıyor).
 */
public class ThestralEntity extends TamableAnimal implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.thestral.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.thestral.walk");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.thestral.fly");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ThestralEntity(EntityType<? extends ThestralEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 18.0)
                .add(Attributes.MOVEMENT_SPEED, 0.225)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    /**
     * Gevşetilmiş spawn koşulu (Kneazle deseniyle aynı): yalnızca altındaki blok
     * katı olsun — IŞIK ŞARTI YOK. Vanilla {@code Animal::checkAnimalSpawnRules}
     * ışık>8 istediğinden karanlık Gloomwood biyomunda Thestral hiç doğamıyordu.
     * Karanlık/ölüm temalı bir yaratık olduğu için gece/gündüz fark etmeksizin
     * doğabilmeli.
     */
    public static boolean canSpawn(EntityType<ThestralEntity> type, ServerLevelAccessor level,
                                    MobSpawnType reason, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).isSolid();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.2));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.2, this::isFood, false));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(7, new FloatGoal(this));
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.APPLE) || stack.is(Items.CARROT) || stack.is(Items.GOLDEN_CARROT)
                || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.BREAD);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.isEmpty() && isFood(stack) && !this.level().isClientSide) {
            if (!this.isTame()) {
                if (this.random.nextInt(3) == 0) {
                    this.tame(player);
                    this.navigation.stop();
                    this.setTarget(null);
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
        if (this.isTame() && this.isOwnedBy(player) && !this.isVehicle() && !this.level().isClientSide) {
            player.startRiding(this);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player p ? p : null;
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
        return base * (player.isSprinting() ? 2.4f : 1.4f);
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
                this.move(MoverType.SELF, this.getDeltaMovement());
            }
            this.calculateEntityAnimation(false);
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.isVehicle()) {
            Vec3 v = this.getDeltaMovement();
            if (v.y < -0.08) {
                this.setDeltaMovement(v.x, -0.08, v.z);
            }
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0, 0.9 * scale, -0.05 * scale)
                .yRot(-this.getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public net.minecraft.world.entity.AgeableMob getBreedOffspring(net.minecraft.server.level.ServerLevel level,
                                                                     net.minecraft.world.entity.AgeableMob partner) {
        return null; // üreme desteklenmiyor (kaynakta da yok)
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5,
                state -> {
                    if (!this.onGround()) {
                        return state.setAndContinue(FLY);
                    }
                    return state.setAndContinue(state.isMoving() ? WALK : IDLE);
                }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
