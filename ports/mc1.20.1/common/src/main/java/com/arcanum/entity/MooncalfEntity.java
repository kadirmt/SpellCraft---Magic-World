package com.arcanum.entity;

import com.arcanum.registry.ModEntities;
import com.arcanum.registry.ModParticles;
import com.arcanum.util.ArcanumColorParticleOption;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Mooncalf — kocaman gözlü, ürkek ay danası. Gece ay ışığında dans eder;
 * buğdayla beslenir/ürer. Gloomwood ve koruların pasif sakinlerinden.
 */
public class MooncalfEntity extends Animal implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mooncalf.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.mooncalf.walk");
    private static final RawAnimation DANCE = RawAnimation.begin().thenPlay("animation.mooncalf.dance");

    /** Ay dansı partikül rengi — gümüşi ay ışığı (ARGB). */
    private static final int MOONLIGHT_ARGB = 0xFFDDE8F5;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Bir sonraki ay dansına kalan tick (sunucu tarafı, kozmetik). */
    private int danceCooldown = 100;

    public MooncalfEntity(EntityType<? extends MooncalfEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 14.0);
    }

    /**
     * Gevşetilmiş spawn koşulu (Kneazle deseniyle aynı): yalnızca altındaki blok
     * katı olsun — IŞIK ŞARTI YOK. Vanilla {@code Animal::checkAnimalSpawnRules}
     * ışık>8 istediğinden karanlık Gloomwood biyomunda Mooncalf hiç doğamıyordu.
     * Pasif hayvan olduğu için gece/gündüz fark etmeksizin doğabilmeli.
     */
    public static boolean canSpawn(EntityType<MooncalfEntity> type, ServerLevelAccessor level,
                                    MobSpawnType reason, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).isSolid();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.5));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.2, Ingredient.of(Items.WHEAT), false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.WHEAT);
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return ModEntities.MOONCALF.get().create(level);
    }

    /**
     * Gece ritmi: açık gökyüzü altında, gece, yerdeyken ve boştayken 400-700 tick'te
     * bir ay dansı yapar. Tamamen kozmetik — ödül yok, sadece atmosfer.
     */
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !isAlive()) {
            return;
        }
        Level lvl = level();
        boolean moonlit = lvl.isNight() && lvl.canSeeSky(blockPosition());
        if (moonlit && onGround() && getNavigation().isDone() && !isBaby()) {
            if (--this.danceCooldown <= 0) {
                this.danceCooldown = 400 + this.random.nextInt(301); // 400-700 tick arası
                dance();
            }
        }
    }

    /** Ay dansı: animasyon tetikler + gümüş ışıma partikülleri + kristal çıngırtı. */
    public void dance() {
        triggerAnim("controller", "dance");
        if (level() instanceof ServerLevel server) {
            ArcanumColorParticleOption moonGlow =
                    ArcanumColorParticleOption.create(ModParticles.SPELL_GLOW.get(), MOONLIGHT_ARGB);
            int count = 6 + this.random.nextInt(3); // 6-8 adet
            server.sendParticles(moonGlow, getX(), getY() + 0.7, getZ(), count, 0.5, 0.4, 0.5, 0.015);
            server.playSound(null, this, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL,
                    0.9F, 1.1F + this.random.nextFloat() * 0.2F);
        }
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 4,
                state -> state.setAndContinue(state.isMoving() ? WALK : IDLE))
                .triggerableAnim("dance", DANCE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
