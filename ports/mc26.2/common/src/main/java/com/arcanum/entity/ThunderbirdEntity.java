package com.arcanum.entity;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * Thunderbird (Gökgürültü Kuşu) — evcilleştirilebilir uçan yaratık, çiğ tavukla
 * beslenip evcilleştirilir (kaynak: HogCraft, %33 şans). Kaynakta doğal spawn'ı
 * biome kısıtı olmadan yalnızca fırtınalı havada gerçekleşiyordu — bu davranış
 * {@link com.arcanum.registry.ModEntities#registerSpawnPlacements()} içindeki
 * özel yerleşim kuralında korunuyor. Doğal ortam: açık/fırtınalı biyomlar
 * (windswept hills, savanna, desert) — tek bir biyoma sıkıştırılmadı.
 */
public class ThunderbirdEntity extends TamableAnimal implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.thunderbird.idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.thunderbird.fly");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ThunderbirdEntity(EntityType<? extends ThunderbirdEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 26.x: TemptGoal menzili artik Attributes.TEMPT_RANGE'den okunuyor (1.21.1'de TemptGoal icinde sabit 10.0);
        // oznitelik yoksa ilk AI tick'inde IllegalArgumentException -> sunucu cokmesi. Kok davranis: 10.0.
        return Mob.createMobAttributes()
                .add(Attributes.TEMPT_RANGE, 10.0)
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.45)
                .add(Attributes.FLYING_SPEED, 0.9)
                .add(Attributes.ARMOR, 3.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    /**
     * Gündüz, katı zeminde doğar (eskiden yalnızca {@code isThundering()} idi — fırtına
     * çok nadir olduğundan pratikte HİÇ doğal spawn olmuyordu). Animal.checkAnimalSpawnRules
     * KASITLI kullanılmıyor: çim zemin şartı desert/gravelly biyomlarında (kum/çakıl) spawn'ı
     * imkansız kılardı. Bunun yerine "altındaki blok katı + ışık>8 (gündüz)" — fırtına şartı
     * kaldırıldı, böylece kendi biyomlarında (windswept/savanna/desert) güvenilir doğar.
     */
    public static boolean canSpawn(EntityType<ThunderbirdEntity> type, net.minecraft.world.level.ServerLevelAccessor level,
                                    net.minecraft.world.entity.EntitySpawnReason reason, net.minecraft.core.BlockPos pos, net.minecraft.util.RandomSource random) {
        return level.getBlockState(pos.below()).isSolid() && level.getRawBrightness(pos, 0) > 8;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.4));
        this.goalSelector.addGoal(2, new TemptGoal(this, 1.2, this::isFood, false));
        this.goalSelector.addGoal(3, new FlyWanderGoal(this, 1.0, 16));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(5, new FloatGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.CHICKEN);
    }

    /** İki tüy koparma denemesi arası minimum süre (tick) — 5 dk. */
    private static final long FEATHER_COOLDOWN_TICKS = 6000L;

    /** Son tüy koparma anındaki dünya zamanı (getGameTime) — kalıcı, bkz. addAdditionalSaveData. */
    private long lastFeatherPluck = Long.MIN_VALUE;

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putLong("LastFeatherPluck", this.lastFeatherPluck);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getLong("LastFeatherPluck").ifPresent(v -> this.lastFeatherPluck = v);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.SHEARS) && this.isTame() && this.isOwnedBy(player) && !this.level().isClientSide()) {
            long now = this.level().getGameTime();
            if (now - this.lastFeatherPluck < FEATHER_COOLDOWN_TICKS) {
                player.sendOverlayMessage(
                        net.minecraft.network.chat.Component.translatable("arcanum.feather_not_ready"));
                return InteractionResult.CONSUME;
            }
            this.lastFeatherPluck = now;
            this.spawnAtLocation((net.minecraft.server.level.ServerLevel) this.level(), com.arcanum.registry.ModItems.THUNDERBIRD_FEATHER.get());
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
    public net.minecraft.world.entity.AgeableMob getBreedOffspring(net.minecraft.server.level.ServerLevel level,
                                                                     net.minecraft.world.entity.AgeableMob partner) {
        return null; // üreme desteklenmiyor (kaynakta da yok)
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
        controllers.add(new AnimationController<>("controller", 4,
                state -> state.setAndContinue(this.onGround() && !state.isMoving() ? IDLE : FLY)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** 3D rastgele dolaşma — yer-tabanlı stroll yerine uçan yaratığa uygun. */
    private static class FlyWanderGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private final PathfinderMob mob;
        private final double speed;
        private final int range;

        FlyWanderGoal(PathfinderMob mob, double speed, int range) {
            this.mob = mob;
            this.speed = speed;
            this.range = range;
            this.setFlags(EnumSet.of(Flag.MOVE));
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
