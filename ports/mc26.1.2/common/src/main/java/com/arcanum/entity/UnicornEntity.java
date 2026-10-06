package com.arcanum.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

/**
 * Unicorn — evcilleştirilebilir, eyerlenip binilebilen at-benzeri yaratık.
 * Altın elmayla evcilleştirilir (%33 şans, kaynak: HogCraft). Kaynak, eyer
 * takılı/takılı-değil hâlini İKİ AYRI entity ile (entity-swap hack'i) çözüyordu
 * — burada tek sınıf + senkronize SADDLED bool ile (çok daha temiz, vanilla
 * at mantığına yakın). 7 rastgele doku varyantı (kaynaktaki "*6.0 → yalnızca
 * 0-5 seçilebiliyor, 7. doku hiç çıkmıyor" hatası düzeltilmiş: nextInt(7)).
 * Doğal ortam: plains/sunflower_plains/flower_forest (kaynak sadece
 * sunflower_plains kullanıyordu, HP lore'daki "Yasak Orman/çayır" temasına
 * uygun şekilde genişletildi).
 */
public class UnicornEntity extends TamableAnimal implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.unicorn.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.unicorn.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.unicorn.run");

    private static final EntityDataAccessor<Boolean> SADDLED =
            SynchedEntityData.defineId(UnicornEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TEXTURE_VARIANT =
            SynchedEntityData.defineId(UnicornEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public UnicornEntity(EntityType<? extends UnicornEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 26.x: TemptGoal menzili artik Attributes.TEMPT_RANGE'den okunuyor (1.21.1'de TemptGoal icinde sabit 10.0);
        // oznitelik yoksa ilk AI tick'inde IllegalArgumentException -> sunucu cokmesi. Kok davranis: 10.0.
        return Mob.createMobAttributes()
                .add(Attributes.TEMPT_RANGE, 10.0)
                .add(Attributes.MAX_HEALTH, 18.0)
                .add(Attributes.MOVEMENT_SPEED, 0.225)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SADDLED, false);
        builder.define(TEXTURE_VARIANT, 0);
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
                                        net.minecraft.world.DifficultyInstance difficulty,
                                        net.minecraft.world.entity.EntitySpawnReason reason,
                                        net.minecraft.world.entity.SpawnGroupData groupData) {
        setTextureVariant(this.random.nextInt(7));
        return super.finalizeSpawn(level, difficulty, reason, groupData);
    }

    /**
     * Eyer durumu — senkron {@link #SADDLED} bool'u (eyer item'ı SADDLE slotuna KONMAZ).
     *
     * <p>BİLİNÇLİ ÇAKIŞMA ÇÖZÜMÜ (26.x): 1.21.5'te vanilla {@code Mob#isSaddled()} eklendi
     * ("SADDLE ekipman slotunda geçerli eyer var mı"). Bu metot artık onu OVERRIDE ediyor ve
     * kasıtlı olarak bizim senkron bool'umuzu döndürüyor — böylece 1.21.1 davranışı (eyer tak/çıkar,
     * binme kapısı, kontrol eden yolcu, renderer'ın "saddle" kemiği) aynen korunur. Vanilla'da
     * isSaddled'ı çağıran yerler yalnız kendi alt sınıfları (at/domuz/strider/deve/nautilus);
     * slot tabanlı mantık (makasla eyer sökme, ölümde ekipman düşürme, {@code can_equip_saddle})
     * slotun kendisine baktığından ve Unicorn o etikette olmadığından hiçbiri tetiklenmez.
     * Ad KORUNDU (UnicornModel/renderer bu adla çağırıyor); NBT anahtarı "Saddled" aynı.
     */
    @Override
    public boolean isSaddled() {
        return this.entityData.get(SADDLED);
    }

    public int getTextureVariant() {
        return this.entityData.get(TEXTURE_VARIANT);
    }

    public void setTextureVariant(int variant) {
        this.entityData.set(TEXTURE_VARIANT, variant);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Saddled", isSaddled());
        output.putInt("TextureVariant", getTextureVariant());
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(SADDLED, input.getBooleanOr("Saddled", false));
        input.getInt("TextureVariant").ifPresent(this::setTextureVariant);
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
        return stack.is(Items.GOLDEN_APPLE);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // eyer çıkar: elde boşken shift+sağ-tık (sadece sahibi, binilmiyorken —
        // aksi halde biniciyi kontrolsüz/senkronsuz bir "vagon" durumunda bırakır)
        if (stack.isEmpty() && player.isShiftKeyDown() && isSaddled() && this.isOwnedBy(player) && !this.isVehicle()) {
            if (!this.level().isClientSide()) {
                this.entityData.set(SADDLED, false);
                this.spawnAtLocation((net.minecraft.server.level.ServerLevel) this.level(), Items.SADDLE);
                this.playSound(net.minecraft.sounds.SoundEvents.HORSE_SADDLE.value(), 1.0f, 1.0f);
            }
            // 1.21.1 sidedSuccess(client) = istemci SUCCESS / sunucu CONSUME → 26.x: SUCCESS (yalnız istemci sallar)
            return InteractionResult.SUCCESS;
        }

        // eyer tak: tame + sahibi + eyer item'ı elinde + henüz eyerli değil
        if (!stack.isEmpty() && stack.is(Items.SADDLE) && this.isTame() && this.isOwnedBy(player) && !isSaddled()) {
            if (!this.level().isClientSide()) {
                this.entityData.set(SADDLED, true);
                this.playSound(net.minecraft.sounds.SoundEvents.HORSE_SADDLE.value(), 1.0f, 1.0f);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }

        // besleme / evcilleştirme (altın elma)
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

        // binme: tame + sahibi + eyerli + henüz binilmiyor
        if (this.isTame() && this.isOwnedBy(player) && isSaddled() && !this.isVehicle() && stack.isEmpty()
                && !this.level().isClientSide()) {
            player.startRiding(this);
            return InteractionResult.SUCCESS_SERVER;
        }

        return super.mobInteract(player, hand);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return isSaddled() && this.getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        this.setYRot(player.getYRot());
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return new Vec3(player.xxa * 0.5f, 0.0, player.zza);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        float base = (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return base * (player.isSprinting() ? 2.2f : 1.3f);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0, 1.05 * scale, -0.05 * scale)
                .yRot(-this.getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    public net.minecraft.world.entity.AgeableMob getBreedOffspring(net.minecraft.server.level.ServerLevel level,
                                                                     net.minecraft.world.entity.AgeableMob partner) {
        return null; // üreme desteklenmiyor (kaynakta da yok)
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("controller", 5,
                state -> {
                    if (!state.isMoving()) {
                        return state.setAndContinue(IDLE);
                    }
                    return state.setAndContinue((this.isVehicle() && this.getControllingPassenger() != null
                            && ((Player) this.getControllingPassenger()).isSprinting()) ? RUN : WALK);
                }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
