package com.arcanum.entity;

import com.arcanum.Arcanum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Hogsmeade büyücü tüccarı — sabit zümrüt ekonomili gezgin satıcı NPC.
 * <p>
 * Vanilla {@code AbstractVillager} yerine {@link Merchant} arayüzü elle uygulanır:
 * böylece villager beyni/meslek sistemi olmadan basit ve sağlam bir tüccar elde edilir.
 * Teklif listesi SABİTTİR (ilk açılışta bir kez kurulur, rastgelelik yok);
 * her teklif 12 kullanımlıktır ve yenilenmez (restock yok).
 * <p>
 * VARYANT sistemi: {@code 0} = malzeme/kitap tüccarı (varsayılan),
 * {@code 1} = İKSİRCİ (modun tüm özel iksirlerini zümrüt + temalı malzeme karşılığı satar).
 * Varyant senkronize byte olarak tutulur ve NBT'ye kaydedilir; teklifler tembel
 * kurulduğundan varyant İLK {@link #getOffers()} çağrısından önce set edilmelidir
 * ({@link #setVariant(int)} yine de güvence olarak teklif önbelleğini sıfırlar).
 * <p>
 * Meslek edinme: köylülerin meslek edinmesi gibi — imbik (brewing stand) yanına
 * koyulan işsiz (varyant 0) tüccar kendiliğinden İKSİRCİ (varyant 1) olur; dönüşüm
 * tek yönlüdür, geri alınmaz. Yapı tüccarları setVariant ile açıkça atanır (değişmedi).
 * <p>
 * Savunma: oyuncularla ASLA savaşmaz ({@link #setTarget} düşman-olmayanı yok sayar);
 * hostile (Enemy) moblara karşı Ölümyiyen gibi SALDIRI büyüleri (Stupefy/
 * Expelliarmus/Bombarda) artı dondurma/itme KONTROL büyüleriyle savaşır;
 * ayrıca kendine bakar: yanınca Aguamenti ile söndürür, canı azalınca Episkey.
 * Oyuncu vurarsa (düşman-olmayan saldırgan) eski davranış: panikleyip kaçar.
 * Yapı (Hogsmeade/Azkaban) içine yerleştirildiği için uzaklıkta despawn OLMAZ.
 * <p>
 * 1.20.1 PORT NOTLARI: {@code ItemCost} sınıfı yok — {@link MerchantOffer}
 * {@code (ItemStack, ItemStack, int, int, float)} (+ çift maliyetli
 * {@code (ItemStack, ItemStack, ItemStack, int, int, float)}) constructor'ları
 * kullanılır; iksir stack'i {@code PotionContents} yerine
 * {@code PotionUtils.setPotion} ile kurulur; {@code defineSynchedData()}
 * parametresiz + {@code entityData.define}; {@code ResourceLocation} new ile
 * kurulur; GeckoLib importları {@code core.*}.
 * {@code Merchant.openTradingScreen(Player, Component, int)} 1.20.1'de de mevcut
 * (javap ile doğrulandı). Savunma/meslek eklemeleri için 1.20.1'de javap ile
 * doğrulananlar: {@code PanicGoal.shouldPanic()}, 6-arg
 * {@code NearestAttackableTargetGoal}, {@code SoundEvents.PLAYER_HURT_FREEZE},
 * {@code ParticleTypes.SNOWFLAKE}, {@code Entity.isFreezing()} — hepsi aynı imzayla var.
 * GeckoLib animasyon sözleşmesi: {@code animation.wizard_trader.idle} / {@code animation.wizard_trader.walk}.
 */
public class WizardTraderEntity extends PathfinderMob implements GeoEntity, Merchant {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.wizard_trader.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.wizard_trader.walk");

    /** Varyant 0: malzeme/kitap tüccarı (varsayılan davranış). */
    public static final int VARIANT_REAGENT = 0;
    /** Varyant 1: İKSİRCİ — özel iksirleri satar, iksir hammaddesi alır. */
    public static final int VARIANT_POTION = 1;

    /** Senkronize varyant baytı — istemci renderer'ı doku seçiminde okur. */
    private static final EntityDataAccessor<Byte> DATA_VARIANT =
            SynchedEntityData.defineId(WizardTraderEntity.class, EntityDataSerializers.BYTE);

    /** Her teklifin maksimum kullanım sayısı. */
    private static final int MAX_USES = 12;
    /** Ticaret başına oyuncuya verilen XP küresi miktarı. */
    private static final int TRADE_XP = 3;

    /** Meslek taraması aralığı (tick) — köylü POI taraması gibi seyrek, ucuz. */
    private static final int JOB_SCAN_INTERVAL = 100;
    /** İmbik arama yarıçapı (blok) — tüccarın konumundan küp tarama. */
    private static final int JOB_SCAN_RADIUS = 3;

    // ---- savaş/bakım büyüsü tema renkleri (ModSpells paletiyle uyumlu) ----
    /** Dondurma (glacius-vari) — buz mavisi. */
    private static final int COLOR_FREEZE = 0x9FE2FF;
    /** İtme (depulso-vari) — açık mavi. */
    private static final int COLOR_REPEL = 0x9FD8FF;
    /** Stupefy (sersemlet) — kızıl (Ölümyiyen paletiyle aynı). */
    private static final int COLOR_STUPEFY = 0xE03A1C;
    /** Expelliarmus — gül kızılı (Ölümyiyen paletiyle aynı). */
    private static final int COLOR_EXPELLIARMUS = 0xE8344E;
    /** Bombarda — patlama turuncusu. */
    private static final int COLOR_BOMBARDA = 0xFF8C1A;
    /** Aguamenti (kendine) — su mavisi. */
    private static final int COLOR_AGUAMENTI = 0x4AA3E0;
    /** Episkey (kendine) — altın şifa (Ölümyiyen episkey rengiyle aynı). */
    private static final int COLOR_EPISKEY = 0xFFD37A;

    /** Aguamenti (kendine) bekleme süresi (tick) — yangın sürerse yeniden söndürebilir. */
    private static final int AGUAMENTI_COOLDOWN = 60;
    /** Episkey (kendine) bekleme süresi (tick) — 8 sn'de en fazla bir şifa (Ölümyiyen'le aynı). */
    private static final int EPISKEY_COOLDOWN = 160;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Son kendine-Aguamenti kastının tick'i — kalıcı DEĞİL (NBT'ye yazılmaz). */
    private int lastAguamentiTick = -AGUAMENTI_COOLDOWN;
    /** Son kendine-Episkey kastının tick'i — kalıcı DEĞİL (NBT'ye yazılmaz). */
    private int lastEpiskeyTick = -EPISKEY_COOLDOWN;

    /** Şu an ticaret ekranı açık olan oyuncu (yoksa null). Sadece sunucu tarafında anlamlı. */
    private Player tradingPlayer;
    /** Sabit teklif listesi — ilk {@link #getOffers()} çağrısında kurulur. */
    private MerchantOffers offers;

    public WizardTraderEntity(EntityType<? extends WizardTraderEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    // ---- Varyant ----

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_VARIANT, (byte) VARIANT_REAGENT);
    }

    /**
     * Varyantı set eder ve teklif önbelleğini sıfırlar — böylece doğurma kodu
     * varyantı ilk etkileşimden önce herhangi bir anda set edebilir; teklifler
     * bir sonraki {@link #getOffers()} çağrısında doğru varyantla kurulur.
     */
    public void setVariant(int variant) {
        this.entityData.set(DATA_VARIANT, (byte) variant);
        this.offers = null;
    }

    public int getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Variant", (byte) this.getVariant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Eski kayıtlarda etiket yok → getByte 0 döner = VARIANT_REAGENT (geriye uyumlu).
        this.setVariant(tag.getByte("Variant"));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Savaş büyüsü öncelik 1 (stroll'dan önce): hostile hedef varken saldırı + kontrol büyüleri kastlar.
        this.goalSelector.addGoal(1, new TraderSpellGoal(this));
        // Panik KOŞULLU: yalnız düşman-olmayan (ör. oyuncu) vurduğunda kaçar; canavara karşı savaşır.
        this.goalSelector.addGoal(2, new TraderPanicGoal(this, 1.35));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        // setTarget override'ı Enemy-dışını null'a çevirdiği için HurtByTargetGoal oyuncuya
        // dönemez; ticaret sırasında hedef edinmesin diye canUse ek koşulla sarılır.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return WizardTraderEntity.this.getTradingPlayer() == null && super.canUse();
            }
        });
        // Yalnız hostile (Enemy) moblar hedeflenir — oyuncu/hayvan/köylü asla.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                e -> e instanceof Enemy && e.isAlive() && this.getTradingPlayer() == null));
    }

    /**
     * Tüccar ASLA oyunculara (ya da düşman olmayan herhangi bir canlıya) saldırmaz:
     * Enemy olmayan her hedef null'a zorlanır — "sadece hostile moblara saldırsınlar".
     * HurtByTargetGoal bu sayede güvenlidir: oyuncu vursa bile hedef alınmaz.
     */
    @Override
    public void setTarget(LivingEntity target) {
        super.setTarget(target instanceof Enemy ? target : null);
    }

    @Override
    protected void customServerAiStep() {
        // Kendine bakım (Aguamenti/Episkey) — kast ritminden ve hedeften BAĞIMSIZ:
        // ticaret sırasında ya da hedef yokken bile çalışır (tezgahta alev alev beklemesin).
        this.tickSelfCare();
        // Ticaret sırasında yerinde dur ve müşteriye bak (vanilla TradeWithPlayerGoal muadili).
        if (this.tradingPlayer != null && this.tradingPlayer.isAlive()) {
            this.getNavigation().stop();
            this.getLookControl().setLookAt(this.tradingPlayer, 30.0F, 30.0F);
        } else if (this.tickCount % JOB_SCAN_INTERVAL == 0) {
            // Köylülerin meslek edinmesi gibi — imbik yanına koyulan işsiz tüccar İKSİRCİ olur.
            // Seyrek tarama (~5 sn'de bir), yalnız ticaret yokken; dönüşüm tek yönlü.
            this.tryAcquirePotionJob();
        }
        super.customServerAiStep();
    }

    /**
     * Varyant 0 (işsiz/reagent) tüccar {@value #JOB_SCAN_RADIUS} blok içinde imbik
     * (brewing stand) bulursa İKSİRCİ'ye (varyant 1) terfi eder — küçük kutlamayla.
     * Yapıyla doğan tüccarlar setVariant ile açıkça atandığı için etkilenmez;
     * varyant 1 asla geri döndürülmez.
     */
    private void tryAcquirePotionJob() {
        if (this.getVariant() != VARIANT_REAGENT) {
            return;
        }
        BlockPos center = this.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-JOB_SCAN_RADIUS, -JOB_SCAN_RADIUS, -JOB_SCAN_RADIUS),
                center.offset(JOB_SCAN_RADIUS, JOB_SCAN_RADIUS, JOB_SCAN_RADIUS))) {
            if (this.level().getBlockState(pos).is(Blocks.BREWING_STAND)) {
                this.setVariant(VARIANT_POTION);
                // Küçük kutlama: köylü seviye atlaması havası — mutlu partikül + kısık sesler.
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            this.getX(), this.getY() + 1.2, this.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
                }
                this.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
                this.playSound(SoundEvents.PLAYER_LEVELUP, 0.4F, 1.4F);
                return;
            }
        }
    }

    // ---- Savaş büyüleri ----

    /**
     * Savaş büyüsü kastlar — kullanıcı isteği: "sadece kontrol büyüleri değil;
     * Expelliarmus, Stupefy, Bombarda gibi SALDIRI büyüleri de atsın (Ölümyiyen
     * gibi), kontrol büyüleri buna EK". Ağırlıklı seçim: %25 Stupefy + %15
     * Expelliarmus + %15 Bombarda (saldırı), %25 dondurma + %20 itme (kontrol).
     * Işın motoru ve hasar kaynağı Ölümyiyen'le aynı desendir.
     */
    void castCombatSpell(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 from = this.getEyePosition();
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
        int roll = this.random.nextInt(100);
        if (roll < 25) {
            castStupefy(server, target, from, to);
        } else if (roll < 40) {
            castExpelliarmus(server, target, from, to);
        } else if (roll < 55) {
            castBombarda(server, target, from, to);
        } else if (roll < 80) {
            castFreeze(server, target, from, to);
        } else {
            castRepel(server, target, from, to);
        }
    }

    /** Stupefy — kızıl ışın: 4.5 hasar + kısa ağır sersemletme (20t Slowness VII). */
    private void castStupefy(ServerLevel server, LivingEntity t, Vec3 from, Vec3 to) {
        com.arcanum.network.ArcanumNetwork.sendBeam(server, from, to, COLOR_STUPEFY, 8, 0.05f, 0f);
        t.hurt(mobMagic(server), 4.5f);
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 6));
        server.playSound(null, to.x, to.y, to.z, SoundEvents.FIRECHARGE_USE,
                this.getSoundSource(), 0.8f, 1.0f);
    }

    /** Expelliarmus — gül kızılı ışın: 4 hasar + küçük geri savurma (tüccar disarm yapmaz). */
    private void castExpelliarmus(ServerLevel server, LivingEntity t, Vec3 from, Vec3 to) {
        com.arcanum.network.ArcanumNetwork.sendBeam(server, from, to, COLOR_EXPELLIARMUS, 8, 0.05f, 0f);
        t.hurt(mobMagic(server), 4.0f);
        Vec3 dir = t.position().subtract(this.position());
        Vec3 push = new Vec3(dir.x, 0, dir.z).normalize().scale(0.8);
        t.setDeltaMovement(push.x, 0.2, push.z);
        t.hurtMarked = true; // istemci tahminini ez — savurma görünür olsun (Spells.depulso deseni)
        server.playSound(null, to.x, to.y, to.z, SoundEvents.PLAYER_ATTACK_SWEEP,
                this.getSoundSource(), 0.8f, 1.4f);
    }

    /**
     * Bombarda — turuncu ışın + hedefte MANUEL mini patlama (blok hasarı YOK,
     * level.explode kullanılmaz): hedefe 5.5 hasar, hedefin 3 blok çevresindeki
     * DİĞER düşmanlara (Enemy) 2.5 sıçrama hasarı, hepsine hafif itiş.
     */
    private void castBombarda(ServerLevel server, LivingEntity t, Vec3 from, Vec3 to) {
        com.arcanum.network.ArcanumNetwork.sendBeam(server, from, to, COLOR_BOMBARDA, 8, 0.05f, 0f);
        t.hurt(mobMagic(server), 5.5f);
        blastPush(to, t, 0.5);
        // sıçrama yalnız DİĞER düşmanlara — oyuncu/hayvan/köylü asla zarar görmez
        for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class,
                t.getBoundingBox().inflate(3.0), e -> e != t && e instanceof Enemy && e.isAlive())) {
            near.hurt(mobMagic(server), 2.5f);
            blastPush(to, near, 0.35);
        }
        server.sendParticles(ParticleTypes.EXPLOSION, to.x, to.y, to.z, 5, 0.6, 0.5, 0.6, 0.0);
        // 1.20.1: GENERIC_EXPLODE düz SoundEvent (1.21.1'deki gibi Holder değil) — .value() yok
        server.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE,
                this.getSoundSource(), 0.7f, 1.2f);
    }

    /** Patlama noktasından uzağa hafif yatay itiş (Bombarda sıçraması). */
    private static void blastPush(Vec3 center, LivingEntity e, double strength) {
        Vec3 dir = new Vec3(e.getX() - center.x, 0, e.getZ() - center.z);
        if (dir.lengthSqr() < 1.0E-4) {
            dir = new Vec3(0, 0, 1); // tam merkezde: sıfır vektörü normalize edilmez
        }
        Vec3 push = dir.normalize().scale(strength);
        e.push(push.x, 0.2, push.z);
        e.hurtMarked = true;
    }

    /** Dondurma (glacius-vari) — buz mavisi ışın: 3 hasar + Slowness III (80t) + kar taneleri. */
    private void castFreeze(ServerLevel server, LivingEntity t, Vec3 from, Vec3 to) {
        com.arcanum.network.ArcanumNetwork.sendBeam(server, from, to, COLOR_FREEZE, 8, 0.05f, 0f);
        t.hurt(mobMagic(server), 3.0f);
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
        server.sendParticles(ParticleTypes.SNOWFLAKE, to.x, to.y, to.z, 14, 0.3, 0.3, 0.3, 0.04);
        server.playSound(null, to.x, to.y, to.z, SoundEvents.PLAYER_HURT_FREEZE,
                this.getSoundSource(), 0.8f, 1.0f);
    }

    /** İtme (depulso-vari) — açık mavi ışın: 2 hasar + tüccardan uzağa güçlü savurma. */
    private void castRepel(ServerLevel server, LivingEntity t, Vec3 from, Vec3 to) {
        com.arcanum.network.ArcanumNetwork.sendBeam(server, from, to, COLOR_REPEL, 8, 0.05f, 0f);
        t.hurt(mobMagic(server), 2.0f);
        Vec3 dir = t.position().subtract(this.position());
        Vec3 push = new Vec3(dir.x, 0, dir.z).normalize().scale(1.8);
        t.setDeltaMovement(push.x, 0.3, push.z);
        t.hurtMarked = true; // istemci tahminini ez — itiş görünür olsun (Spells.depulso deseni)
        server.playSound(null, to.x, to.y, to.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                this.getSoundSource(), 0.8f, 1.0f);
    }

    /** Ölümyiyen'le aynı hasar kaynağı: dolaylı büyü (indirectMagic). */
    private DamageSource mobMagic(ServerLevel server) {
        return server.damageSources().indirectMagic(this, this);
    }

    // ---- Kendine bakım büyüleri (Aguamenti / Episkey) ----

    /**
     * Kendine bakım — kast ritminden ve hedeften BAĞIMSIZ kendi bekleme süreleri:
     * yanınca Aguamenti ile kendini söndürür, canı yarının altına düşünce Episkey
     * ile iyileşir. {@link #customServerAiStep()} her tick çağırır (yalnız sunucu);
     * ticaret sırasında bile çalışır — tüccar tezgahta alev alev beklemesin.
     */
    private void tickSelfCare() {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        // AGUAMENTI (kendine): yanınca kendini söndürür.
        if (this.isOnFire() && this.tickCount - this.lastAguamentiTick >= AGUAMENTI_COOLDOWN) {
            this.lastAguamentiTick = this.tickCount;
            this.clearFire();
            // asasını kendine doğrultur: gözden ayağa minik su mavisi ışın
            com.arcanum.network.ArcanumNetwork.sendBeam(server, this.getEyePosition(),
                    this.position(), COLOR_AGUAMENTI, 5, 0.05f, 0f);
            server.sendParticles(ParticleTypes.SPLASH,
                    this.getX(), this.getY() + 1.0, this.getZ(), 15, 0.4, 0.6, 0.4, 0.1);
            server.sendParticles(ParticleTypes.FALLING_WATER,
                    this.getX(), this.getY() + 1.8, this.getZ(), 15, 0.3, 0.2, 0.3, 0.0);
            this.playSound(SoundEvents.FIRE_EXTINGUISH, 0.7f, 1.0f);
            this.playSound(SoundEvents.GENERIC_SPLASH, 0.7f, 1.0f);
        }
        // EPISKEY (kendine): canı %50 altına düşünce kendini iyileştirir.
        if (this.getHealth() < this.getMaxHealth() * 0.5f
                && this.tickCount - this.lastEpiskeyTick >= EPISKEY_COOLDOWN) {
            this.lastEpiskeyTick = this.tickCount;
            this.heal(6.0f);
            // altın parıltı: gözden ayağa minik ışın + kalp + mutlu partikül
            // (meslek kutlamasıyla aynı HAPPY_VILLAGER idiomu)
            com.arcanum.network.ArcanumNetwork.sendBeam(server, this.getEyePosition(),
                    this.position(), COLOR_EPISKEY, 5, 0.05f, 0f);
            server.sendParticles(ParticleTypes.HEART,
                    this.getX(), this.getY() + 1.4, this.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    this.getX(), this.getY() + 1.2, this.getZ(), 8, 0.4, 0.6, 0.4, 0.02);
            this.playSound(SoundEvents.PLAYER_LEVELUP, 0.5f, 1.6f);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof SpawnEggItem) && this.isAlive() && !this.isTrading()) {
            if (!this.level().isClientSide && !this.getOffers().isEmpty()) {
                this.setTradingPlayer(player);
                this.openTradingScreen(player, this.getDisplayName(), 1);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    /** Yapı NPC'si: oyuncu uzaklaşınca despawn olmasın. */
    @Override
    public boolean removeWhenFarAway(double distSq) {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.stopTrading();
    }

    /** Açık ticaret ekranını kapatır ve müşteriyi bırakır. */
    private void stopTrading() {
        if (this.tradingPlayer != null) {
            this.tradingPlayer.closeContainer();
            this.setTradingPlayer(null);
        }
    }

    private boolean isTrading() {
        return this.tradingPlayer != null;
    }

    // ---- Merchant arayüzü ----

    @Override
    public void setTradingPlayer(Player player) {
        this.tradingPlayer = player;
    }

    @Override
    public Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        if (this.offers == null) {
            this.offers = this.getVariant() == VARIANT_POTION ? buildPotionOffers() : buildReagentOffers();
        }
        return this.offers;
    }

    @Override
    public void overrideOffers(MerchantOffers offers) {
        // Sabit ekonomi — dışarıdan teklif ezmeye izin verilmez (vanilla Villager da no-op).
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        this.ambientSoundTime = -this.getAmbientSoundInterval();
        this.playSound(this.getNotifyTradeSound());
        if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            ExperienceOrb.award(serverLevel, this.position().add(0.0, 0.5, 0.0), TRADE_XP);
        }
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        // Slot her değiştiğinde ses spamlamamak için vanilla'nın ambient-zamanlayıcı koruması.
        if (!this.level().isClientSide && this.ambientSoundTime > -this.getAmbientSoundInterval() + 20) {
            this.ambientSoundTime = -this.getAmbientSoundInterval();
            this.playSound(stack.isEmpty() ? SoundEvents.VILLAGER_NO : SoundEvents.VILLAGER_YES);
        }
    }

    @Override
    public int getVillagerXp() {
        return 0; // İlerleme çubuğu yok — seviye sistemi kullanılmıyor.
    }

    @Override
    public void overrideXp(int xp) {
        // Seviye sistemi yok.
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean canRestock() {
        return false;
    }

    @Override
    public boolean isClientSide() {
        return this.level().isClientSide;
    }

    // ---- Teklifler ----

    /**
     * Varyant 0 — sabit zümrüt ekonomisi. Arcanum item'ları kayıt kimliğiyle (id)
     * çözülür; böylece bu sınıf item kayıt sırasına derleme bağımlılığı taşımaz.
     * Henüz kayıtlı olmayan (AIR'e düşen) item içeren teklifler sessizce atlanır.
     */
    private static MerchantOffers buildReagentOffers() {
        MerchantOffers list = new MerchantOffers();
        // Satış: zümrüt → malzeme
        addOffer(list, Items.EMERALD, 3, arcanumItem("practice_chalk"), 2);
        addOffer(list, Items.EMERALD, 4, arcanumItem("spell_diagram"), 2);
        addOffer(list, Items.EMERALD, 6, arcanumItem("crushed_amethyst"), 2);
        addOffer(list, Items.EMERALD, 8, arcanumItem("storm_vial"), 1);
        addOffer(list, Items.EMERALD, 8, arcanumItem("enchanted_honeycomb"), 1);
        addOffer(list, Items.EMERALD, 12, arcanumItem("owl_charm"), 1);
        addOffer(list, Items.EMERALD, 14, arcanumItem("phoenix_quill"), 1);
        addOffer(list, Items.EMERALD, 10, arcanumItem("starter_spell_book"), 1);
        // Alım: yaratık ganimeti → zümrüt
        addOffer(list, arcanumItem("troll_hide"), 1, Items.EMERALD, 2);
        addOffer(list, arcanumItem("mooncalf_dust"), 2, Items.EMERALD, 3);
        addOffer(list, arcanumItem("thunderbird_feather"), 1, Items.EMERALD, 4);
        return list;
    }

    /**
     * Varyant 1 — İKSİRCİ. Modun TÜM içilebilir özel iksirleri (brewing ara
     * aşamaları hariç) zümrüt + iksirin tarifine uygun temalı ikinci malzeme
     * karşılığı satılır; karşılığında iksir hammaddesi (cam şişe, cehennem siğili)
     * alınır. İksirler de item'lar gibi kayıt kimliğiyle çözülür.
     */
    private static MerchantOffers buildPotionOffers() {
        MerchantOffers list = new MerchantOffers();
        // Satış: zümrüt + temalı malzeme → iksir
        addPotionOffer(list, 8, arcanumItem("crushed_amethyst"), 1, "mana_haste_potion");
        addPotionOffer(list, 10, arcanumItem("phoenix_quill"), 1, "exstimulo_potion");
        addPotionOffer(list, 8, arcanumItem("troll_hide"), 1, "girding_potion");
        // Alım: iksir hammaddesi → zümrüt
        addOffer(list, Items.GLASS_BOTTLE, 4, Items.EMERALD, 1);
        addOffer(list, Items.NETHER_WART, 3, Items.EMERALD, 1);
        return list;
    }

    /** Arcanum item'ını id ile çözer; kayıtlı değilse AIR döner (teklif atlanır). */
    private static Item arcanumItem(String id) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(Arcanum.MODID, id));
    }

    /** Maliyet ya da sonuç AIR ise (item henüz kayıtlı değil) teklifi eklemez. */
    private static void addOffer(MerchantOffers list, Item cost, int costCount, Item result, int resultCount) {
        if (cost == Items.AIR || result == Items.AIR) {
            return;
        }
        // 1.20.1: ItemCost yok — maliyet doğrudan ItemStack olarak verilir.
        list.add(new MerchantOffer(
                new ItemStack(cost, costCount),
                new ItemStack(result, resultCount),
                MAX_USES, TRADE_XP, 0.05F));
    }

    /**
     * Çift maliyetli iksir satış teklifi: zümrüt (A) + temalı malzeme (B) → iksir.
     * Potion kayıt kimliğiyle çözülür; iksir ya da B-malzemesi kayıtlı değilse
     * teklif sessizce atlanır. 1.20.1: iksir stack'i {@code PotionUtils.setPotion}
     * ile kurulur, çift maliyet {@code MerchantOffer(ItemStack, ItemStack,
     * ItemStack, int, int, float)} constructor'ına verilir.
     */
    private static void addPotionOffer(MerchantOffers list, int emeraldCount,
                                       Item costB, int costBCount, String potionId) {
        Optional<Potion> potion = BuiltInRegistries.POTION.getOptional(
                new ResourceLocation(Arcanum.MODID, potionId));
        if (costB == Items.AIR || potion.isEmpty()) {
            return;
        }
        list.add(new MerchantOffer(
                new ItemStack(Items.EMERALD, emeraldCount),
                new ItemStack(costB, costBCount),
                PotionUtils.setPotion(new ItemStack(Items.POTION), potion.get()),
                MAX_USES, TRADE_XP, 0.05F));
    }

    // ---- Sesler ----

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isTrading() ? SoundEvents.WANDERING_TRADER_TRADE : SoundEvents.WANDERING_TRADER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WANDERING_TRADER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WANDERING_TRADER_DEATH;
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5,
                state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ---- Goal'lar ----

    /**
     * Koşullu panik: yalnız düşman-OLMAYAN bir saldırgan (ör. oyuncu) vurduğunda
     * kaçar — canavar vurursa kaçmak yerine savaşır (TraderSpellGoal devralır).
     * Ateş/donma paniği vanilla'daki gibi korunur (ama düşmanla savaşırken değil).
     */
    static class TraderPanicGoal extends PanicGoal {
        TraderPanicGoal(WizardTraderEntity mob, double speed) {
            super(mob, speed);
        }

        @Override
        protected boolean shouldPanic() {
            LivingEntity attacker = this.mob.getLastHurtByMob();
            if (attacker instanceof Enemy) {
                return false; // canavara karşı panik yok — savaş
            }
            return attacker != null || this.mob.isFreezing() || this.mob.isOnFire();
        }
    }

    /**
     * Savaş büyü Goal'u — Ölümyiyen SpellCastGoal'unun uyarlaması: hedef varken
     * 6-10 blok bandını korumaya çalışır (RangedBowAttackGoal strafe deseni),
     * her 45-55 tick'te bir savaş büyüsü (saldırı + kontrol) kastlar. Melee
     * fallback OLMADIĞI için mesafe bandı aktivasyon koşulu DEĞİLDİR — hedef
     * yaklaşsa da kast sürer, geri adımla mesafe yeniden açılır. Ticaret
     * sırasında asla çalışmaz.
     */
    static class TraderSpellGoal extends Goal {
        /** Tercih edilen menzil bandı: 6-10 blok (menzilli büyücü mesafeyi korur). */
        private static final double MIN_RANGE_SQ = 6.0 * 6.0;
        private static final double MAX_RANGE_SQ = 10.0 * 10.0;

        private final WizardTraderEntity mob;
        private int castTimer;
        private int seeTime;
        private int strafingTime = -1;
        private boolean strafingClockwise;
        private boolean strafingBackwards;

        TraderSpellGoal(WizardTraderEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            // Müşteri her şeyden önce gelir: ticaret sırasında kast YOK.
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && mob.getTradingPlayer() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            // ilk kast hafif gecikmeli — hedefi görür görmez ateşlemesin
            this.castTimer = 15 + mob.getRandom().nextInt(16);
        }

        @Override
        public void stop() {
            this.seeTime = 0;
            this.strafingTime = -1;
            mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            double distSq = mob.distanceToSqr(target);
            boolean canSee = mob.getSensing().hasLineOfSight(target);
            this.seeTime = canSee ? this.seeTime + 1 : 0;

            // RangedBowAttackGoal strafe deseni: banttaysa dur + yan adım, uzaktaysa yaklaş
            if (distSq <= MAX_RANGE_SQ && this.seeTime >= 20) {
                mob.getNavigation().stop();
                this.strafingTime++;
            } else {
                mob.getNavigation().moveTo(target, 1.0);
                this.strafingTime = -1;
            }
            if (this.strafingTime >= 20) {
                if (mob.getRandom().nextFloat() < 0.3f) {
                    this.strafingClockwise = !this.strafingClockwise;
                }
                if (mob.getRandom().nextFloat() < 0.3f) {
                    this.strafingBackwards = !this.strafingBackwards;
                }
                this.strafingTime = 0;
            }
            if (this.strafingTime > -1) {
                // 6-10 bandını koru: 10'un sınırına yaklaşınca ileri, 6'nın altına inince geri
                if (distSq > MAX_RANGE_SQ * 0.75) {
                    this.strafingBackwards = false;
                } else if (distSq < MIN_RANGE_SQ) {
                    this.strafingBackwards = true;
                }
                mob.getMoveControl().strafe(this.strafingBackwards ? -0.5f : 0.5f,
                        this.strafingClockwise ? 0.5f : -0.5f);
                mob.lookAt(target, 30.0f, 30.0f);
            } else {
                mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
            }

            // 45-55 tick'te bir savaş büyüsü (yalnız görüş varken)
            if (--this.castTimer <= 0) {
                if (!canSee) {
                    return;
                }
                this.castTimer = 45 + mob.getRandom().nextInt(11);
                mob.castCombatSpell(target);
            }
        }
    }
}
