package com.arcanum.entity;

import com.arcanum.spell.SpellFx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.arcanum.util.TriggerReturningController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

/**
 * Basilisk — Salazar Slytherin'in canavarı: devasa, kadim yılan. Bir BOSS
 * yaratığı olarak tasarlandı: mor boss bar, çok fazlı saldırılar, modern
 * büyü VFX (SpellFx) ile zehir tükürme ışını, taşlaştırma bakışı ve
 * (faz 2) kuyruk savurma AoE'si. Zehre bağışık (kendi zehri onu etkilemez),
 * boğulma ve düşme hasarına bağışık. Yalnızca zindan feature'ı tarafından
 * addFreshEntity ile doğar — DOĞAL SPAWN YOK.
 *
 * NOT: GeckoLib geo/animation/texture dosyaları AYRI bir ajan tarafından
 * üretiliyor; burada yalnızca kilitli animasyon adları referans alınır
 * (animation.basilisk.idle/move/strike/spit/tail, controller adı "controller").
 */
public class BasiliskEntity extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.basilisk.idle");
    private static final RawAnimation MOVE = RawAnimation.begin().thenLoop("animation.basilisk.move");
    private static final RawAnimation STRIKE = RawAnimation.begin().thenPlay("animation.basilisk.strike");
    private static final RawAnimation SPIT = RawAnimation.begin().thenPlay("animation.basilisk.spit");
    private static final RawAnimation TAIL = RawAnimation.begin().thenPlay("animation.basilisk.tail");

    // Basilisk kadim yeşilinin renk paleti (SpellFx RGB alır, alpha içeride).
    private static final int COLOR_VENOM = 0x3FA34D;   // zehir yeşili
    private static final int COLOR_GAZE = 0x59C36A;     // taşlaştırma halesi
    private static final int COLOR_TAIL = 0x2E7D46;     // kuyruk şok halkası

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // 26.x: ServerBossEvent UUID'yi dışarıdan ister (vanilla WitherBoss deseni; 1.21.1
    // kurucusu içeride Mth.createInsecureUUID() üretiyordu — davranış aynı).
    private final ServerBossEvent bossBar = new ServerBossEvent(
            net.minecraft.util.Mth.createInsecureUUID(this.random),
            this.getDisplayName(),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.NOTCHED_10);

    // Saldırı cooldown sayaçları (sunucu tick'i).
    private int spitCooldown = 40;
    private int gazeCooldown = 100;
    private int tailCooldown = 90;
    // Faz 2 (<%50 can) bir kez tetiklenir.
    private boolean phaseTwo = false;

    public BasiliskEntity(EntityType<? extends BasiliskEntity> type, Level level) {
        super(type, level);
        this.xpReward = 120;
        this.bossBar.setDarkenScreen(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 320.0)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ===================== bağışıklıklar =====================

    /** Yılan kendi zehrine bağışık — POISON efektini reddet. */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.is(MobEffects.POISON)) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    /** Boğulma ve düşme hasarına bağışık (devasa kadim yaratık). */
    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FALL)) {
            return true;
        }
        return super.isInvulnerableTo(level, source);
    }

    // ===================== melee =====================

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
            // 26.x: doHurtTarget artık ServerLevel alıyor → eski instanceof kapısı gereksiz
            Vec3 to = living.position().add(0, living.getBbHeight() * 0.6, 0);
            // ısırık VFX: hedefte kadim yeşil patlama (asa ışını yok — melee)
            SpellFx.burst(level, to, COLOR_VENOM, 12, 0.30, 0.10);
            triggerAnim("controller", "strike");
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR,
                    getSoundSource(), 1.2f, 0.7f);
        }
        return hit;
    }

    // ===================== boss bar =====================

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        // Boss bar tracking ile EKLENMEZ: Basilisk yeraltında/uzakta (tracking
        // menzili ~128 blok) olsa da bar çıkmasın. Bar ekleme/çıkarma artık
        // customServerAiStep() içindeki MESAFE KAPISI (40 blok) ile yapılır.
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossBar.removePlayer(player);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.bossBar.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public void die(DamageSource source) {
        // ölüm anı: kadim yeşil patlama koreografisi
        if (this.level() instanceof ServerLevel server) {
            Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
            SpellFx.nova(server, position(), COLOR_VENOM, 48, 0.55);
            SpellFx.burst(server, c, COLOR_GAZE, 40, 1.2, 0.25);
            SpellFx.runeRing(server, position(), COLOR_TAIL, 32, 3.2);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_DEATH,
                    getSoundSource(), 1.4f, 0.6f);
        }
        this.bossBar.removeAllPlayers();
        super.die(source);
    }

    // ===================== fazlar + saldırılar =====================

    @Override
    protected void customServerAiStep(ServerLevel server) {
        super.customServerAiStep(server);

        // Boss bar "üstte takılı kalma" güvenliği: ölü/kaldırılmış (unload dahil)
        // durumda barı KESİN temizle; aksi halde ilerlemeyi güncelle ve canlıyken göster.
        if (!this.isAlive() || this.isRemoved()) {
            this.bossBar.setVisible(false);
            this.bossBar.removeAllPlayers();
            return;
        }
        this.bossBar.setVisible(true);
        this.bossBar.setProgress(getHealth() / getMaxHealth());

        // (26.x: seviye parametre olarak geliyor — eski "ServerLevel değilse çık" kapısı gereksiz)

        // MESAFE KAPISI: boss bar yalnızca Basilisk'e gerçekten yaklaşan (40 blok)
        // oyuncularda görünür. Uzaktaki/yeraltındaki tracking'e giren oyunculara
        // bar takılmaz; menzilden çıkanlardan bar kaldırılır.
        for (ServerPlayer p : server.players()) {
            if (this.distanceToSqr(p) <= 40.0 * 40.0) {
                this.bossBar.addPlayer(p);
            } else {
                this.bossBar.removePlayer(p);
            }
        }

        // Faz 2 geçişi (<%50 can): bir kez — VFX patlaması + hafif self-buff.
        if (!this.phaseTwo && getHealth() < getMaxHealth() * 0.5f) {
            this.phaseTwo = true;
            enterPhaseTwo(server);
        }

        LivingEntity target = getTarget();

        if (this.spitCooldown > 0) {
            this.spitCooldown--;
        }
        if (this.gazeCooldown > 0) {
            this.gazeCooldown--;
        }
        if (this.tailCooldown > 0) {
            this.tailCooldown--;
        }

        // ZEHIR TÜKÜRME: hedef 6-20 blok bandındaysa, ~60 tick'te bir hitscan ışın.
        if (target != null && target.isAlive() && this.spitCooldown <= 0) {
            double dist = distanceTo(target);
            if (dist >= 6.0 && dist <= 20.0 && hasLineOfSight(target)) {
                this.spitCooldown = 60;
                spitVenom(server, target);
            }
        }

        // TAŞLAŞTIRMA BAKIŞI: yalnızca AGGRO olunca (hedef varken) — pasif boss'un
        // yanından geçen oyuncuyu körleştirmesin (inceleme bulgusu). 12 blok içindeki
        // oyunculara, ~140 tick'te bir.
        if (target != null && target.isAlive() && this.gazeCooldown <= 0) {
            this.gazeCooldown = 140;
            petrifyingGaze(server);
        }

        // KUYRUK SAVURMA (faz 2): yalnızca aggro olunca. 4 blok içindeki tüm canlılara
        // AoE, ~90 tick'te bir.
        if (this.phaseTwo && target != null && target.isAlive() && this.tailCooldown <= 0) {
            this.tailCooldown = 90;
            tailSweep(server);
        }
    }

    /** Faz 2 girişi: patlama + kısa süreli hız/hasar self-buff + zehir sisi. */
    private void enterPhaseTwo(ServerLevel server) {
        Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
        SpellFx.nova(server, position(), COLOR_VENOM, 40, 0.5);
        SpellFx.burst(server, c, COLOR_GAZE, 30, 1.0, 0.2);
        SpellFx.runeRing(server, position(), COLOR_TAIL, 28, 2.8);
        // öfke: hız + hasar artışı (uzun süreli self-buff)
        addEffect(new MobEffectInstance(MobEffects.SPEED, 6000, 0));
        addEffect(new MobEffectInstance(MobEffects.STRENGTH, 6000, 0));
        // çevreye zehir sisi partikülü
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.SNEEZE,
                getX(), getY() + getBbHeight() * 0.5, getZ(), 60, 2.5, 1.0, 2.5, 0.02);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR,
                getSoundSource(), 1.5f, 0.5f);
    }

    /** Zehir tükürme — hitscan yeşil ışın + küçük büyü hasarı + POISON. */
    private void spitVenom(ServerLevel server, LivingEntity target) {
        triggerAnim("controller", "spit");
        Vec3 from = getEyePosition();
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
        // modern ışın VFX (DeathEater beamLine deseni): açık ton kalıcı çekirdek + iz + kaynakta patlama
        SpellFx.line(server, from, to, SpellFx.beamOpt(SpellFx.lighten(COLOR_VENOM, 0.35f)), 0.4);
        SpellFx.line(server, from, to, SpellFx.trail(COLOR_VENOM), 0.5);
        SpellFx.burst(server, from, SpellFx.lighten(COLOR_VENOM, 0.3f), 5, 0.12, 0.03);
        SpellFx.burst(server, to, COLOR_VENOM, 10, 0.30, 0.10);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.SNEEZE,
                to.x, to.y, to.z, 14, 0.3, 0.3, 0.3, 0.05);
        // hitscan hasar + zehir. mobAttack KULLAN (indirectMagic #bypasses_armor tag'inde →
        // netherite set bile hiç işe yaramıyordu, tester "tek atıyor" dedi). mobAttack zırha
        // saygı duyar; taban hasar 5→7 (zırhsız yine ciddi, zırhlı yaşayabilir).
        target.hurtServer(server, server.damageSources().mobAttack(this), 7.0f);
        target.addEffect(new MobEffectInstance(MobEffects.POISON, 140, 1));
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.LLAMA_SPIT,
                getSoundSource(), 1.3f, 0.6f);
        SpellFx.sound(server, to, SoundEvents.GENERIC_SPLASH, 0.7f, 0.5f);
    }

    /**
     * Taşlaştırma bakışı — 12 blok içindeki oyunculara ağır yavaşlık + körlük +
     * karanlık; her birine yeşil hale (shroud) VFX.
     */
    private void petrifyingGaze(ServerLevel server) {
        boolean any = false;
        for (ServerPlayer player : server.getPlayers(p ->
                p.isAlive() && !p.isSpectator() && !p.isCreative() && distanceToSqr(p) <= 12.0 * 12.0)) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
            SpellFx.shroud(server, player, COLOR_GAZE, 18);
            any = true;
        }
        if (any) {
            SpellFx.runeRing(server, position(), COLOR_GAZE, 24, 2.0);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM,
                    getSoundSource(), 1.0f, 0.7f);
        }
    }

    /**
     * Kuyruk savurma (faz 2) — 4 blok içindeki tüm LivingEntity'lere (kendisi
     * hariç) hasar + güçlü knockback + şok halkası VFX.
     */
    private void tailSweep(ServerLevel server) {
        triggerAnim("controller", "tail");
        java.util.List<LivingEntity> victims = server.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(4.0),
                e -> e != this && e.isAlive() && !(e instanceof BasiliskEntity));
        Vec3 origin = position();
        for (LivingEntity victim : victims) {
            victim.hurtServer(server, server.damageSources().mobAttack(this), 9.0f);
            Vec3 push = victim.position().subtract(origin);
            if (push.lengthSqr() < 1.0e-4) {
                push = new Vec3(this.random.nextDouble() - 0.5, 0, this.random.nextDouble() - 0.5);
            }
            push = push.normalize();
            applyKnockback(victim, 1.6, -push.x, -push.z);
            victim.push(push.x * 0.6, 0.45, push.z * 0.6);
        }
        SpellFx.nova(server, origin, COLOR_TAIL, 36, 0.6);
        SpellFx.burst(server, origin.add(0, 0.4, 0), COLOR_TAIL, 24, 0.8, 0.15);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ATTACK,
                getSoundSource(), 1.4f, 0.6f);
    }

    /**
     * Knockback TEK çağrı noktası (26.2 ileri-uyum): 26.1.2'de
     * {@code knockback(double, double, double)}; 26.2'de imza
     * {@code knockback(double, double, double, DamageSource, float)} oluyor —
     * portta YALNIZ bu yardımcı değişir.
     */
    private static void applyKnockback(LivingEntity victim, double strength, double x, double z) {
        victim.knockback(strength, x, z);
    }

    // ===================== GeckoLib =====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new TriggerReturningController<>("controller", 5,
                state -> state.setAndContinue(state.isMoving() ? MOVE : IDLE))
                .triggerableAnim("strike", STRIKE)
                .triggerableAnim("spit", SPIT)
                .triggerableAnim("tail", TAIL));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
