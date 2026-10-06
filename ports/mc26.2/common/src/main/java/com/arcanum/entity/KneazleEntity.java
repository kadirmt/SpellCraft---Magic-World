package com.arcanum.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.feline.CatSoundVariants;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.arcanum.util.TriggerReturningController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

/**
 * Kneazle — kedimsi, akıllı, sahibine sadık evcil yaratık. Çiğ balıkla
 * evcilleştirilir (kaynak: kanon'da balık/et sever); evcilleştikten sonra
 * sahibini takip eder, çökme emri verilebilir (shift+sağ tık). Lore'a uygun
 * "güvenilmez kişileri/tehlikeyi sezme" özelliği, yakınında canavar (Monster)
 * belirdiğinde tetiklenen kozmetik bir "tıslama" tepkisiyle temsil edilir.
 */
public class KneazleEntity extends TamableAnimal implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.kneazle.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.kneazle.walk");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.kneazle.sit");
    private static final RawAnimation HISS = RawAnimation.begin().thenPlay("animation.kneazle.hiss");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Bir sonraki tehlike-sezme kontrolüne kalan tick (sunucu tarafı, kozmetik). */
    private int senseCooldown = 40;

    public KneazleEntity(EntityType<? extends KneazleEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 26.x: TemptGoal menzili artik Attributes.TEMPT_RANGE'den okunuyor (1.21.1'de TemptGoal icinde sabit 10.0);
        // oznitelik yoksa ilk AI tick'inde IllegalArgumentException -> sunucu cokmesi. Kok davranis: 10.0.
        return Mob.createMobAttributes()
                .add(Attributes.TEMPT_RANGE, 10.0)
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    public static boolean canSpawn(EntityType<KneazleEntity> type, ServerLevelAccessor level,
                                    EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        // Işık kısıtı gevşetildi: karanlık Gloomwood biyomunda da doğabilsin diye
        // '> 8' -> '>= 0' (yalnızca altındaki blok katı olmalı). '>= 0' her zaman
        // doğru olduğundan ışık artık kısıtlamıyor ama koşul, ileride bir
        // değişiklik gerekirse görünür kalsın diye açıkça bırakıldı.
        return level.getBlockState(pos.below()).isSolid() && level.getRawBrightness(pos, 0) >= 0;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.6));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.2, this::isFood, false));
        this.goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.1, 8.0F, 2.0F));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.COD) || stack.is(Items.SALMON);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!this.level().isClientSide()) {
            if (!this.isTame() && !stack.isEmpty() && isFood(stack)) {
                if (this.random.nextInt(3) == 0) {
                    this.tame(player);
                    this.navigation.stop();
                    this.setTarget(null);
                    this.setOrderedToSit(true);
                    this.level().broadcastEntityEvent(this, (byte) 7);
                } else {
                    this.level().broadcastEntityEvent(this, (byte) 6);
                }
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                // 1.21.1'de sunucu-tarafı SUCCESS kolu sallatıyordu → 26.x muadili SUCCESS_SERVER
                return InteractionResult.SUCCESS_SERVER;
            }
            if (this.isTame() && this.isOwnedBy(player)) {
                if (!stack.isEmpty() && isFood(stack)) {
                    this.heal(3.0f);
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                } else if (!player.isSecondaryUseActive()) {
                    this.setOrderedToSit(!this.isOrderedToSit());
                }
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null; // üreme desteklenmiyor
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("SenseCooldown", this.senseCooldown);
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getInt("SenseCooldown").ifPresent(v -> this.senseCooldown = v);
    }

    /**
     * Lore: Kneazle'lar tehlikeyi/güvenilmez kişileri sezer. Yakında (8 blok)
     * bir Monster varsa periyodik olarak tısla — tamamen kozmetik, hedefleme
     * veya hasar yok.
     */
    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel server) || !this.isAlive() || this.isOrderedToSit()) {
            return;
        }
        if (--this.senseCooldown <= 0) {
            this.senseCooldown = 60 + this.random.nextInt(60);
            // 1.21.2+: getNearestEntity(TargetingConditions, …) yalnız ServerEntityGetter'da (ServerLevel)
            Monster nearby = server.getNearestEntity(Monster.class,
                    net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().range(8.0),
                    this, this.getX(), this.getY(), this.getZ(), this.getBoundingBox().inflate(8.0));
            if (nearby != null) {
                hiss();
            }
        }
    }

    private void hiss() {
        triggerAnim("controller", "hiss");
        if (this.level() instanceof ServerLevel) {
            // SoundEvents.CAT_HISS kalktı (kedi sesleri varyant setine taşındı); aynı ses olayı
            // "entity.cat.hiss" = CLASSIC setin yetişkin tıslaması.
            this.playSound(SoundEvents.CAT_SOUNDS.get(CatSoundVariants.SoundSet.CLASSIC)
                            .adultSounds().hissSound().value(),
                    1.0F, 1.0F + (this.random.nextFloat() - 0.5F) * 0.2F);
        }
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new TriggerReturningController<>("controller", 4,
                state -> {
                    if (this.isOrderedToSit()) {
                        return state.setAndContinue(SIT);
                    }
                    return state.setAndContinue(state.isMoving() ? WALK : IDLE);
                }).triggerableAnim("hiss", HISS));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
