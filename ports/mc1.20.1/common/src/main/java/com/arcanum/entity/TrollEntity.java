package com.arcanum.entity;

import java.util.EnumSet;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Troll — devasa, sopalı dağ trolü. Kapı kırabilir, gördüğü HER canlıya
 * (oyuncu, hayvan, köylü, demir golem, diğer canavarlar — hatta başka
 * troller dahil) saldırır; aptal ve ayrım gözetmeyen bir vahşi olarak
 * tasarlandı. Ok/donma hasarına dayanıklıdır (kalın deri).
 * Doğal ortam: karanlık ormanlar, dağlar (mağara ağızları) — HP lore'daki
 * "dağ trolü" temasına uygun, gloomwood'a özgü değil.
 */
public class TrollEntity extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.troll.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.troll.walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.troll.attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TrollEntity(EntityType<? extends TrollEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Telegraf'lı saldırı: vanilla MeleeAttackGoal hasarı swing() (animasyon tetikleme)
        // ile AYNI tick'te verir → sopa daha inmeden hasar geliyordu (tester "senkron değil").
        // Özel goal animasyonu başlatır, ~13 tick (sopanın indiği 0.667s karesi) sonra vurur.
        this.goalSelector.addGoal(1, new TrollAttackGoal(this, 1.15));
        // KASITLI: her zorlukta kapı kırabilir (kaynak HogCraft davranışı birebir —
        // vanilla Zombie'nin Hard-only olasılıksal kapı kırmasından FARKLI olarak
        // burada difficulty her zaman true döner, placeholder/unutulmuş kod değil)
        this.goalSelector.addGoal(2, new BreakDoorGoal(this, difficulty -> true));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, false));
        // Player dışındaki TÜM canlılar (hayvan/köylü/demir golem/canavar/diğer troller
        // dahil) — kasıtlı olarak ayrım gözetmeyen, gördüğü her şeye saldıran vahşi bir yaratık.
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, true, false));
        this.getNavigation().setCanFloat(true);
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0f);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // kalın deri: ok ve donma hasarına dayanıklı (kaynak davranışı)
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE) || source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public int getMaxHeadXRot() {
        return 30;
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5,
                state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
        controllers.add(new AnimationController<>(this, "attacking", 0,
                state -> state.setAndContinue(RawAnimation.begin()))
                .triggerableAnim("attack", ATTACK)
                .setSoundKeyframeHandler(state -> {}));
    }

    @Override
    public void swing(net.minecraft.world.InteractionHand hand, boolean updateSelf) {
        super.swing(hand, updateSelf);
        if (!this.level().isClientSide) {
            this.triggerAnim("attacking", "attack");
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /**
     * Telegraf'lı yakın-dövüş saldırısı. Vanilla {@code MeleeAttackGoal} hasarı
     * {@code swing()} (animasyon tetikleme) ile AYNI tick'te uygular; bu, "attack"
     * animasyonunun sopa-savurma karesi ({@code 0.6667s ≈ 13 tick}) daha gelmeden
     * hasar inmesine ve tester'ın bildirdiği görsel senkronsuzluğa yol açıyordu.
     *
     * <p>Bu goal: menzilde + cooldown bitmişken önce {@code swing()} ile animasyonu
     * başlatır, ardından {@link #WINDUP} tick boyunca yerinde durup (ayak dik, sopa
     * savruluyor) hedefi takip eder; windup dolduğunda hedef HÂLÂ menzildeyse
     * {@code doHurtTarget} ile vurur — hedef çekilmişse ıskalar. Yalnızca sürüm-bağımsız
     * kararlı API'ler kullanır (navigation/lookControl/distanceToSqr/doHurtTarget), bu
     * yüzden 1.20.1 ve 1.21.1 ağaçlarında birebir aynı derlenir.
     */
    private static class TrollAttackGoal extends Goal {
        /** Animasyonun sopanın indiği vuruş karesi (~0.667s ≈ 13 tick). */
        private static final int WINDUP = 13;
        /** Vuruş sonrası bir sonraki saldırıya kadar bekleme (anim 25t → ~25t/vuruş). */
        private static final int COOLDOWN = 12;

        private final TrollEntity troll;
        private final double speed;
        private int cooldown;
        private int windup = -1;   // >=0 → saldırı hazırlanıyor
        private int pathTimer;
        private LivingEntity pending;

        TrollAttackGoal(TrollEntity troll, double speed) {
            this.troll = troll;
            this.speed = speed;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = this.troll.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = this.troll.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void start() {
            this.cooldown = 0;
            this.pathTimer = 0;
        }

        @Override
        public void stop() {
            this.windup = -1;
            this.pending = null;
            this.troll.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.troll.getTarget();
            if (target == null) {
                return;
            }
            this.troll.getLookControl().setLookAt(target, 30.0f, 30.0f);
            double reachSqr = this.reachSqr(target);

            // --- hasar telegrafı: swing ile hasar AYNI tick'te DEĞİL; windup sonra iner ---
            if (this.windup >= 0) {
                this.troll.getNavigation().stop(); // sopa savrulurken yerinde dur
                if (--this.windup < 0) {
                    if (this.pending != null && this.pending.isAlive()
                            && this.troll.distanceToSqr(this.pending) <= this.reachSqr(this.pending)) {
                        this.troll.doHurtTarget(this.pending);
                    }
                    this.pending = null;
                    this.cooldown = COOLDOWN;
                }
                return;
            }

            // --- yaklaş (yol throttle'lı, jitter olmasın) ---
            if (--this.pathTimer <= 0) {
                this.pathTimer = 10;
                this.troll.getNavigation().moveTo(target, this.speed);
            }
            if (this.cooldown > 0) {
                this.cooldown--;
            }
            // menzilde + cooldown bitti → animasyonu başlat, hasar WINDUP tick sonra iner
            if (this.troll.distanceToSqr(target) <= reachSqr && this.cooldown <= 0) {
                this.troll.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                this.windup = WINDUP;
                this.pending = target;
            }
        }

        /** Vanilla getAttackReachSqr ile aynı: (genişlik*2)^2 + hedef genişliği. */
        private double reachSqr(LivingEntity t) {
            float w = this.troll.getBbWidth() * 2.0f;
            return w * w + t.getBbWidth();
        }
    }
}
