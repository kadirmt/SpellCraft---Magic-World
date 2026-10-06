package com.arcanum.entity;

import com.arcanum.registry.ModEntities;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

/**
 * Bowtruckle — avuç içi kadar, dal görünümlü utangaç ağaç bekçisi.
 * Arcanewood ağaçlarının dibinde yaşar; tatlı meyveyle kandırılır, ürkektir.
 */
public class BowtruckleEntity extends Animal implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.bowtruckle.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.bowtruckle.walk");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BowtruckleEntity(EntityType<? extends BowtruckleEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 26.x: TemptGoal menzili artik Attributes.TEMPT_RANGE'den okunuyor (1.21.1'de TemptGoal icinde sabit 10.0);
        // oznitelik yoksa ilk AI tick'inde IllegalArgumentException -> sunucu cokmesi. Kok davranis: 10.0.
        return Mob.createMobAttributes()
                .add(Attributes.TEMPT_RANGE, 10.0)
                .add(Attributes.MAX_HEALTH, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 12.0);
    }

    /**
     * Gevşetilmiş spawn koşulu (Kneazle deseniyle aynı): yalnızca altındaki blok
     * katı olsun — IŞIK ŞARTI YOK. Vanilla {@code Animal::checkAnimalSpawnRules}
     * ışık>8 istediğinden karanlık Gloomwood biyomunda Bowtruckle hiç doğamıyordu.
     * Pasif hayvan olduğu için gece/gündüz fark etmeksizin doğabilmeli.
     */
    public static boolean canSpawn(EntityType<BowtruckleEntity> type, ServerLevelAccessor level,
                                    EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).isSolid();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.8));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.2, this::isFood, false));
        this.goalSelector.addGoal(4, new StayNearTreesGoal(this, 1.0));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 5.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.SWEET_BERRIES);
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return ModEntities.BOWTRUCKLE.get().create(level, EntitySpawnReason.BREEDING);
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("controller", 4,
                state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ---- Özel hedefler ----

    /**
     * Ağaç sevgisi: belirli aralıklarla (≈200 tick) 12 blok içindeki en yakın
     * kütüğü (LOGS tag'i) bulur ve dibine doğru yürür. Bowtruckle böylece
     * korulukta gezinir ama hep ağaçların çevresinde kalır.
     */
    private static class StayNearTreesGoal extends Goal {
        private static final int SCAN_RADIUS = 12;
        private static final int SCAN_HEIGHT = 4;

        private final PathfinderMob mob;
        private final double speed;
        private int cooldown = 40; // ilk taramadan önce kısa bekleme
        private BlockPos target;

        StayNearTreesGoal(PathfinderMob mob, double speed) {
            this.mob = mob;
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (--this.cooldown > 0) {
                return false;
            }
            this.cooldown = 200 + this.mob.getRandom().nextInt(60);
            this.target = findNearestLog();
            // Zaten bir ağacın dibindeyse yürümeye gerek yok
            return this.target != null && this.target.distSqr(this.mob.blockPosition()) > 9.0;
        }

        @Override
        public void start() {
            if (this.target != null) {
                this.mob.getNavigation().moveTo(
                        this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, this.speed);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return !this.mob.getNavigation().isDone();
        }

        @Override
        public void stop() {
            this.target = null;
            this.mob.getNavigation().stop();
        }

        /** 12 blok yarıçapında (±4 dikey) en yakın kütük bloğunu döndürür. */
        private BlockPos findNearestLog() {
            BlockPos origin = this.mob.blockPosition();
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(
                    origin.offset(-SCAN_RADIUS, -SCAN_HEIGHT, -SCAN_RADIUS),
                    origin.offset(SCAN_RADIUS, SCAN_HEIGHT, SCAN_RADIUS))) {
                if (!this.mob.level().getBlockState(pos).is(BlockTags.LOGS)) {
                    continue;
                }
                double dist = pos.distSqr(origin);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = pos.immutable();
                }
            }
            return best;
        }
    }
}
