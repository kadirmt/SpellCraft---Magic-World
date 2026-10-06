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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

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
        this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 0.0f); // 1.21.1 DANGER_FIRE (8.0) → FIRE_IN_NEIGHBOR
        this.setPathfindingMalus(PathType.FIRE, 0.0f);             // 1.21.1 DAMAGE_FIRE (16.0) → FIRE
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 26.x: TemptGoal menzili artik Attributes.TEMPT_RANGE'den okunuyor (1.21.1'de TemptGoal icinde sabit 10.0);
        // oznitelik yoksa ilk AI tick'inde IllegalArgumentException -> sunucu cokmesi. Kok davranis: 10.0.
        return Mob.createMobAttributes()
                .add(Attributes.TEMPT_RANGE, 10.0)
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
                                    net.minecraft.world.entity.EntitySpawnReason reason, net.minecraft.core.BlockPos pos, net.minecraft.util.RandomSource random) {
        // Eskiden yalnız KUM üstüne doğuyordu; "her biyomda çıksınlar" isteğiyle spawn artık
        // tüm overworld'e kayıtlı — kum şartı kalsaydı çim/orman biyomlarında phoenix'in
        // kazandığı her spawn hakkı boşa yanardı (adversarial review). Katı zemin + gün ışığı yeter.
        return level.getBlockState(pos.below()).isSolid()
                && level.getMaxLocalRawBrightness(pos) > 8;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, 0);
    }

    /** 0 = klasik turuncu (V1), 1 = alternatif (V2) — spawn egg'e göre sabitlenir. */
    public int getVariant() {
        return this.entityData.get(VARIANT);
    }

    public void setVariant(int variant) {
        this.entityData.set(VARIANT, variant);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Variant", getVariant());
        output.putLong("LastFeatherPluck", this.lastFeatherPluck);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getInt("Variant").ifPresent(this::setVariant);
        input.getLong("LastFeatherPluck").ifPresent(v -> this.lastFeatherPluck = v);
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
                                        net.minecraft.world.DifficultyInstance difficulty,
                                        net.minecraft.world.entity.EntitySpawnReason reason,
                                        net.minecraft.world.entity.SpawnGroupData groupData) {
        setVariant(this.random.nextInt(2));
        return super.finalizeSpawn(level, difficulty, reason, groupData);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.PanicGoal(this, 1.4));
        this.goalSelector.addGoal(2, new TemptGoal(this, 1.2, this::isFood, false));
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
                && !this.level().isClientSide()) {
            long now = this.level().getGameTime();
            if (now - this.lastFeatherPluck < FEATHER_COOLDOWN_TICKS) {
                player.sendOverlayMessage(
                        net.minecraft.network.chat.Component.translatable("arcanum.feather_not_ready"));
                return InteractionResult.CONSUME;
            }
            this.lastFeatherPluck = now;
            this.spawnAtLocation((net.minecraft.server.level.ServerLevel) this.level(), com.arcanum.registry.ModItems.PHOENIX_FEATHER.get());
            stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND
                    ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                    : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
            this.playSound(net.minecraft.sounds.SoundEvents.PARROT_FLY, 1.0f, 1.2f);
            this.level().broadcastEntityEvent(this, (byte) 7);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!stack.isEmpty() && isFood(stack) && !this.level().isClientSide()) {
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
            return InteractionResult.SUCCESS_SERVER;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            return false; // ateşe tam bağışıklık
        }
        return super.hurtServer(level, source, amount);
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
        PhoenixEntity child = com.arcanum.registry.ModEntities.PHOENIX.get().create(level, net.minecraft.world.entity.EntitySpawnReason.BREEDING);
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
        controllers.add(new AnimationController<>("controller", 4,
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
