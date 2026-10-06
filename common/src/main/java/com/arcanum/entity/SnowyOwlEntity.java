package com.arcanum.entity;

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
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * Kar Baykuşu (Hedwig teması) — evcilleştirilebilir uçan yaratık, çiğ tavukla
 * beslenip evcilleştirilir (kaynak: HogCraft, %33 şans). Gözleri karanlıkta
 * parlar (renderer'daki emissive katman). Doğal ortam: snowy_plains/snowy_taiga
 * — kaynaktaki kar-zemin + aydınlık şartı korunuyor.
 */
public class SnowyOwlEntity extends TamableAnimal implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.snowy_owl.idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.snowy_owl.fly");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SnowyOwlEntity(EntityType<? extends SnowyOwlEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FLYING_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    /**
     * Kar zeminli, aydınlık yerlerde doğar (kaynak: snowy_plains/snowy_taiga şartı).
     * NOT: Animal.checkAnimalSpawnRules KASITLI OLARAK kullanılmıyor — o metod
     * altındaki bloğun BlockTags.ANIMALS_SPAWNABLE_ON (yalnız çim) olmasını da
     * şart koşar ve kar bloğuyla asla aynı anda sağlanamaz (imkansız AND —
     * eklenmiş olsaydı doğal spawn hiç gerçekleşmezdi).
     *
     * KRİTİK: SNOWY_PLAINS/SNOWY_TAIGA düz arazisinde yüzey = grass_block + üstünde
     * TEK KATMAN kar layer'ı. Kar layer'ının çarpışma kutusu BOŞ olduğu için mob
     * grass_block üstünde (pos) durur, {@code pos.below()} = grass_block olur. Eskiden
     * yalnız {@code pos.below()}'un BlockTags.SNOW olması aranıyordu → düz arazide koşul
     * ASLA sağlanmıyordu (kar baykuşu pratikte hiç doğmuyordu). Artık: ayak hizasındaki
     * kar layer'ı (pos) VEYA snow_block yüzeyli biyomlar (pos.below()) VEYA karlı biyomun
     * çim tabanı kabul ediliyor (vanilla Rabbit.checkRabbitSpawnRules ile aynı mantık).
     */
    public static boolean canSpawn(EntityType<SnowyOwlEntity> type, net.minecraft.world.level.ServerLevelAccessor level,
                                    net.minecraft.world.entity.MobSpawnType reason, net.minecraft.core.BlockPos pos, net.minecraft.util.RandomSource random) {
        return (level.getBlockState(pos).is(net.minecraft.tags.BlockTags.SNOW)
                        || level.getBlockState(pos.below()).is(net.minecraft.tags.BlockTags.SNOW)
                        || level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK))
                && level.getMaxLocalRawBrightness(pos) > 8;
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
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("LastFeatherPluck", this.lastFeatherPluck);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("LastFeatherPluck")) {
            this.lastFeatherPluck = tag.getLong("LastFeatherPluck");
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.SHEARS) && this.isTame() && this.isOwnedBy(player) && !this.level().isClientSide) {
            long now = this.level().getGameTime();
            if (now - this.lastFeatherPluck < FEATHER_COOLDOWN_TICKS) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("arcanum.feather_not_ready"), true);
                return InteractionResult.CONSUME;
            }
            this.lastFeatherPluck = now;
            this.spawnAtLocation(com.arcanum.registry.ModItems.SNOWY_OWL_FEATHER.get());
            stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND
                    ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                    : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
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
        controllers.add(new AnimationController<>(this, "controller", 4,
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
