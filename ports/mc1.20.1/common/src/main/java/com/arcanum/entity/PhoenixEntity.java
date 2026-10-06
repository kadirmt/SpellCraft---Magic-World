package com.arcanum.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Phoenix (anka kuşu) — evcilleştirilebilir uçan yaratık, altın havuçla beslenip
 * evcilleştirilir/üretilir/iyileştirilir, ateşe tamamen bağışıklıdır (kaynak: HogCraft).
 * İki görsel varyant (V1/V2, kaynakta ayrı entity idi — burada tek sınıf + senkronize
 * VARIANT alanı, iki ayrı spawn egg aynı entity'yi farklı varyantla üretir).
 * Doğal ortam: badlands/desert (kaynakla birebir — kum zemin + gün ışığı şartı).
 */
public class PhoenixEntity extends TamableAnimal implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.phoenix.idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.phoenix.fly");

    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(PhoenixEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public PhoenixEntity(EntityType<? extends PhoenixEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, true);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 0.0f);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, 0.0f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    /**
     * Kum zeminli, aydınlık yerlerde doğar (kaynak: badlands/desert şartı).
     * NOT: Animal.checkAnimalSpawnRules KASITLI OLARAK kullanılmıyor — o metod
     * altındaki bloğun BlockTags.ANIMALS_SPAWNABLE_ON (yalnız çim) olmasını da
     * şart koşar ve kum bloğuyla asla aynı anda sağlanamaz (imkansız AND —
     * eklenmiş olsaydı doğal spawn hiç gerçekleşmezdi). Işık kontrolü zaten
     * burada elle yapılıyor, ek bir kontrole gerek yok.
     */
    public static boolean canSpawn(EntityType<PhoenixEntity> type, net.minecraft.world.level.ServerLevelAccessor level,
                                    net.minecraft.world.entity.MobSpawnType reason, net.minecraft.core.BlockPos pos, net.minecraft.util.RandomSource random) {
        // Eskiden yalnız KUM üstüne doğuyordu; "her biyomda çıksınlar" isteğiyle spawn artık
        // tüm overworld'e kayıtlı — kum şartı kalsaydı çim/orman biyomlarında phoenix'in
        // kazandığı her spawn hakkı boşa yanardı (adversarial review). Katı zemin + gün ışığı yeter.
        return level.getBlockState(pos.below()).isSolid()
                && level.getMaxLocalRawBrightness(pos) > 8;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(VARIANT, 0);
    }

    /** 0 = klasik turuncu (V1), 1 = alternatif (V2) — spawn egg'e göre sabitlenir. */
    public int getVariant() {
        return this.entityData.get(VARIANT);
    }

    public void setVariant(int variant) {
        this.entityData.set(VARIANT, variant);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", getVariant());
        tag.putLong("LastFeatherPluck", this.lastFeatherPluck);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Variant")) {
            setVariant(tag.getInt("Variant"));
        }
        if (tag.contains("LastFeatherPluck")) {
            this.lastFeatherPluck = tag.getLong("LastFeatherPluck");
        }
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
                                        net.minecraft.world.DifficultyInstance difficulty,
                                        net.minecraft.world.entity.MobSpawnType reason,
                                        net.minecraft.world.entity.SpawnGroupData groupData,
                                        net.minecraft.nbt.CompoundTag dataTag) {
        setVariant(this.random.nextInt(2));
        return super.finalizeSpawn(level, difficulty, reason, groupData, dataTag);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.PanicGoal(this, 1.4));
        this.goalSelector.addGoal(2, new TemptGoal(this, 1.2, Ingredient.of(Items.GOLDEN_CARROT), false));
        this.goalSelector.addGoal(3, new FlyRandomStrollGoal(this, 1.0, 16));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(5, new FloatGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.GOLDEN_CARROT);
    }

    /** İki tüy koparma denemesi arası minimum süre (tick) — 5 dk. */
    private static final long FEATHER_COOLDOWN_TICKS = 6000L;

    /** Son tüy koparma anındaki dünya zamanı (getGameTime) — kalıcı, bkz. addAdditionalSaveData. */
    private long lastFeatherPluck = Long.MIN_VALUE;

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(net.minecraft.world.item.Items.SHEARS) && this.isTame() && this.isOwnedBy(player)
                && !this.level().isClientSide) {
            long now = this.level().getGameTime();
            if (now - this.lastFeatherPluck < FEATHER_COOLDOWN_TICKS) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("arcanum.feather_not_ready"), true);
                return InteractionResult.CONSUME;
            }
            this.lastFeatherPluck = now;
            this.spawnAtLocation(com.arcanum.registry.ModItems.PHOENIX_FEATHER.get());
            // 1.20.1: hurtAndBreak Consumer alır — kırılma animasyonu aynı ele oynatılır
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            this.playSound(net.minecraft.sounds.SoundEvents.PARROT_FLY, 1.0f, 1.2f);
            this.level().broadcastEntityEvent(this, (byte) 7);
            return InteractionResult.SUCCESS;
        }
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
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            return false; // ateşe tam bağışıklık
        }
        return super.hurt(source, amount);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        PhoenixEntity child = com.arcanum.registry.ModEntities.PHOENIX.get().create(level);
        if (child != null) {
            child.setVariant(this.random.nextInt(2));
        }
        return child;
    }

    @Override
    public boolean canFallInLove() {
        return this.isTame() && super.canFallInLove();
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 4,
                state -> state.setAndContinue(this.onGround() && !state.isMoving() ? IDLE : FLY)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** 3D rastgele dolaşma — yer-tabanlı stroll yerine uçan yaratığa uygun. */
    private static class FlyRandomStrollGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private final PathfinderMob mob;
        private final double speed;
        private final int range;

        FlyRandomStrollGoal(PathfinderMob mob, double speed, int range) {
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
