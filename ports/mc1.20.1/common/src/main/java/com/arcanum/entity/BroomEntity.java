package com.arcanum.entity;

import com.arcanum.registry.ModItems;
import com.arcanum.spell.SpellFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Uçan süpürge — tekne benzeri basit bir araç (canlı bir varlık gibi
 * davranmaz). Sağ-tık: bin. Binilmiyorken herhangi bir saldırı/hasar onu
 * anında kırıp item olarak düşürür (vanilla tekne mantığı).
 *
 * <p><b>Kademeler:</b> tek EntityType, senkronize {@link #TIER}
 * byte'ı üç süpürgeyi ayırır — 0=Oakshaft 79 (yavaş/sağlam), 1=Comet 260 (orta),
 * 2=Cleansweep (en hızlı; eski "broom" item'ı). Kademe; azami hızı, ivmeyi,
 * kırılınca düşen item'ı ve istemcide geo+doku seçimini (BroomModel) belirler.
 *
 * <p><b>Uçuş modeli (momentum + drift):</b> binicinin bakışı rotayı belirir ama
 * hız artık anlık lerp değil — hız BÜYÜKLÜĞÜ hedefe kademenin ivme oranıyla
 * yavaşça yaklaşır (atalet), gaz kesilince düşük sürtünmeyle uzun süzülme.
 * Hız VEKTÖRÜNÜN YÖNÜ ile süpürgenin burnu ayrı yaşar: yön, burna normalde
 * ~0.25/tick tutuş (grip) oranında hizalanır; SPACE basılıyken (drift,
 * {@link BroomInput} istemci köprüsü) grip ~0.06'ya düşer — burun döner ama
 * süpürge eski yönünde kaymaya devam eder (viraj kayması). Drift bırakılınca
 * yüksek grip hızla toparlar. Binici varken yerçekimi kapalı; boşken süpürge
 * tüy gibi süzülerek iner (dikey hız -0.08'e sınırlanır). Üstündeyken asa
 * kullanımı serbesttir.
 *
 * <p>1.20.1 PORT notları: defineSynchedData parametresiz + entityData.define;
 * GeckoLib core.* paketleri; oturma noktası getPassengerAttachmentPoint yerine
 * positionRider override'ı (0.6 vehicle-attachment ofseti düşülür).
 */
public class BroomEntity extends Mob implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.broom.idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.broom.fly");

    /** Kuyruk izinin buz mavisi parıltısı (SpellFx trail rengi). */
    private static final int TRAIL_COLOR = 0x9FD8FF;

    // ---- kademe sabitleri ----

    /** Kademe sabitleri — {@link #TIER} synched byte değeri. */
    public static final byte TIER_OAKSHAFT = 0;
    public static final byte TIER_COMET = 1;
    public static final byte TIER_CLEANSWEEP = 2;

    private static final EntityDataAccessor<Byte> TIER =
            SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.BYTE);

    /** Kademe → tam gaz azami hız (blok/tick; sprint bunu ×{@link #SPRINT_MULT} katlar). */
    private static final float[] MAX_SPEED = {0.55f, 0.75f, 0.95f};

    /** Kademe → ivme oranı (hız büyüklüğünün hedefe tick başına yaklaşma payı). */
    private static final double[] ACCEL = {0.03, 0.045, 0.06};

    /** Gaz kesikken süzülme sürtünmesi — 1'e yakın = uzun süzülme. */
    private static final double DRAG = 0.985;

    /** Normal tutuş: hız yönünün burun yönüne tick başına hizalanma payı. */
    private static final double GRIP_NORMAL = 0.25;

    /** Drift tutuşu (SPACE basılı): burun döner, hız eski yönünü korur. */
    private static final double GRIP_DRIFT = 0.06;

    /** Sprint hız çarpanı (eski davranışla aynı). */
    private static final float SPRINT_MULT = 2.2f;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BroomEntity(EntityType<? extends BroomEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.9)
                .add(Attributes.FLYING_SPEED, 1.2);
    }

    // ---- kademe ----

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        // varsayılan Cleansweep: eski dünyalardaki mevcut süpürgeler en hızlı
        // kademe olarak yüklenir (eski "broom" item'ının davranışı değişmez)
        this.entityData.define(TIER, TIER_CLEANSWEEP);
    }

    /** 0=Oakshaft 79, 1=Comet 260, 2=Cleansweep. */
    public byte getTier() {
        byte t = this.entityData.get(TIER);
        return (t < 0 || t > TIER_CLEANSWEEP) ? TIER_CLEANSWEEP : t;
    }

    public void setTier(byte tier) {
        this.entityData.set(TIER, (tier < 0 || tier > TIER_CLEANSWEEP) ? TIER_CLEANSWEEP : tier);
    }

    /** Kırılınca / geri alınınca düşecek item — kademeye göre doğru süpürge. */
    private Item getDropItem() {
        return switch (this.getTier()) {
            case TIER_OAKSHAFT -> ModItems.OAKSHAFT_BROOM.get();
            case TIER_COMET -> ModItems.COMET_BROOM.get();
            default -> ModItems.BROOM.get();
        };
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Tier", this.getTier());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Tier")) {
            this.setTier(tag.getByte("Tier"));
        }
    }

    // ---- etkileşim ----

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.isVehicle()) {
            if (!this.level().isClientSide) {
                player.startRiding(this);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // tekne mantığı: canlı bir varlık gibi can kaybedip ölmez — binilmiyorken
        // herhangi bir hasar onu anında kırıp item olarak düşürür (isVehicle()
        // iken zaten isInvulnerableTo tüm hasarı en baştan engelliyor)
        if (this.level().isClientSide || this.isVehicle() || !this.isAlive()) {
            return false;
        }
        this.playSound(SoundEvents.WOOD_BREAK, 1.0f, 1.0f);
        this.spawnAtLocation(this.getDropItem());
        this.discard();
        return true;
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player p ? p : null;
    }

    // ---- uçuş fiziği ----

    @Override
    public boolean isNoGravity() {
        // binici varken tam uçuş; boşken yerçekimi normal akar ama aiStep'te
        // düşüş hızı sınırlanır (süzülerek iniş)
        return this.isVehicle() || super.isNoGravity();
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        // rotasyon biniciden: yaw birebir, pitch yumuşatılmış (görsel eğim)
        this.setYRot(player.getYRot());
        this.setXRot(player.getXRot() * 0.6f);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        float forward = player.zza;
        float strafe = player.xxa * 0.4f;
        if (forward < 0.0f) {
            forward *= 0.5f; // geri vites yarım hız
        }
        // dikey kontrol bakış pitch'i ile: ileri giderken yukarı bak → yüksel
        double vertical = 0.0;
        if (forward != 0.0f) {
            vertical = -Math.sin(Math.toRadians(player.getXRot())) * forward;
        }
        return new Vec3(strafe, vertical, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        // kademe tablosundan azami hız (attribute yerine) — travel'daki hedef
        // hız büyüklüğü buradan gelir (travelRidden setSpeed(getRiddenSpeed) çağırır)
        float base = MAX_SPEED[this.getTier()];
        return base * (player.isSprinting() ? SPRINT_MULT : 1.0f);
    }

    @Override
    public void travel(Vec3 travelVector) {
        // travelRidden (private) getRiddenInput çıktısını buraya iletir —
        // en az riskli override noktası: binici varken kendi uçuş modelimiz
        if (this.isVehicle() && this.getControllingPassenger() instanceof Player) {
            if (this.isControlledByLocalInstance()) {
                // yerel input (strafe, dikey, ileri) → dünya uzayı (vanilla getInputVector düzeni)
                float yaw = this.getYRot() * Mth.DEG_TO_RAD;
                float sin = Mth.sin(yaw);
                float cos = Mth.cos(yaw);
                Vec3 wish = new Vec3(
                        travelVector.x * cos - travelVector.z * sin,
                        travelVector.y,
                        travelVector.z * cos + travelVector.x * sin);
                if (wish.lengthSqr() > 1.0) {
                    wish = wish.normalize();
                }
                Vec3 target = wish.scale(this.getSpeed()); // travelRidden setSpeed(getRiddenSpeed) çağırdı

                Vec3 v = this.getDeltaMovement();
                if (target.lengthSqr() > 1.0e-6) {
                    // BroomInput yalnız bu (yerel istemci) dalda okunur — sunucuda
                    // kimse yazmadığı için dedicated'da daima false (güvenli köprü)
                    boolean drifting = BroomInput.drifting;

                    // 1) İVME (atalet): hız BÜYÜKLÜĞÜ hedefe kademenin accel
                    //    oranıyla yaklaşır — tam gaza ulaşmak saniyeler sürer,
                    //    gazı azaltınca da aynı yumuşaklıkla yavaşlar
                    double curSpeed = v.length();
                    double targetSpeed = target.length();
                    double newSpeed = curSpeed + (targetSpeed - curSpeed) * ACCEL[this.getTier()];

                    // 2) TUTUŞ (grip): hız vektörünün YÖNÜ burnun gösterdiği yöne
                    //    grip oranında döner. Driftte grip düşer — burun çevrilir
                    //    ama süpürge eski yönünde kaymaya devam eder; drift
                    //    bırakılınca yüksek grip hızla toparlar
                    Vec3 targetDir = target.scale(1.0 / targetSpeed);
                    Vec3 curDir = curSpeed > 1.0e-4 ? v.scale(1.0 / curSpeed) : targetDir;
                    double grip = drifting ? GRIP_DRIFT : GRIP_NORMAL;
                    Vec3 dir = curDir.scale(1.0 - grip).add(targetDir.scale(grip));
                    double dirLen = dir.length();
                    dir = dirLen > 1.0e-4 ? dir.scale(1.0 / dirLen) : targetDir;

                    if (drifting) {
                        // viraj kayması hissi: drift sırasında hız büyüklüğü
                        // neredeyse korunur (hafif hız koruması)
                        newSpeed = Math.max(newSpeed, curSpeed * 0.995);
                    }
                    v = dir.scale(newSpeed);
                } else {
                    // gaz kesildi: düşük sürtünmeyle uzun süzülme
                    v = v.scale(DRAG);
                }
                this.setDeltaMovement(v);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.spawnRiderTrail(v); // yalnız süren görür (client-side partikül)
            }
            this.calculateEntityAnimation(false);
        } else {
            super.travel(travelVector);
        }
    }

    /**
     * Yüksek hızda süpürgenin arkasına CLOUD + seyrek END_ROD izi — yalnızca
     * kontrol eden istemcinin travel dalından çağrılır ({@code addParticle}
     * istemci-yerel, dolayısıyla yalnız süren görür; sunucu tarafındaki herkese
     * görünen buz mavisi iz {@link #aiStep} içinde ayrıca yaşamaya devam eder).
     */
    private void spawnRiderTrail(Vec3 velocity) {
        double speed = velocity.length();
        if (speed < 0.5 || this.tickCount % 2 != 0) {
            return;
        }
        double inv = 1.0 / speed;
        double bx = this.getX() - velocity.x * inv * 1.2;
        double by = this.getY() + 0.3 - velocity.y * inv * 1.2;
        double bz = this.getZ() - velocity.z * inv * 1.2;
        this.level().addParticle(ParticleTypes.CLOUD, bx, by, bz,
                -velocity.x * 0.1, -velocity.y * 0.1, -velocity.z * 0.1);
        if (this.random.nextInt(4) == 0) {
            this.level().addParticle(ParticleTypes.END_ROD, bx, by, bz,
                    (this.random.nextDouble() - 0.5) * 0.02,
                    (this.random.nextDouble() - 0.5) * 0.02,
                    (this.random.nextDouble() - 0.5) * 0.02);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.isVehicle()) {
            // binicisiz: süpürge tüy gibi süzülerek iner (terminal hız -0.08)
            Vec3 v = this.getDeltaMovement();
            if (v.y < -0.08) {
                this.setDeltaMovement(v.x, -0.08, v.z);
            }
            return;
        }
        // sunucu tarafı uçuş efektleri — hız, paket-senkronlu pozisyon deltasından
        // ölçülür (binici kontrolündeyken deltaMovement sunucuda sıfırdır)
        if (this.level() instanceof ServerLevel server) {
            double dx = this.getX() - this.xo;
            double dy = this.getY() - this.yo;
            double dz = this.getZ() - this.zo;
            double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (speed > 0.3 && this.tickCount % 2 == 0) {
                // hızlı uçuşta kuyruktan geriye buz mavisi parıltı izi
                double inv = 1.0 / speed;
                double bx = this.getX() - dx * inv * 1.1;
                double by = this.getY() + 0.3 - dy * inv * 1.1;
                double bz = this.getZ() - dz * inv * 1.1;
                server.sendParticles(SpellFx.trail(TRAIL_COLOR), bx, by, bz, 2, 0.10, 0.08, 0.10, 0.0);
            }
            if (speed > 0.4 && this.tickCount % 20 == 0) {
                // hafif rüzgar vınlaması
                server.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 0.2f, 1.6f);
            }
        }
    }

    // ---- oturma & hasar ----

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        // binici sapın ortasında, süpürge otunun hemen önünde oturur.
        // 1.20.1 notu: 1.21.1'deki getPassengerAttachmentPoint (0, 0.45, -0.15)
        // karşılığı — oyuncunun 1.21.1 vehicle-attachment ofseti (0.6) düşülerek
        // dünya konumuna çevrildi; binici görsel olarak birebir aynı yerde oturur.
        if (!this.hasPassenger(passenger)) {
            return;
        }
        Vec3 off = new Vec3(0.0, 0.45 - 0.6, -0.15)
                .yRot(-this.getYRot() * Mth.DEG_TO_RAD);
        callback.accept(passenger, this.getX() + off.x, this.getY() + off.y, this.getZ() + off.z);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false; // süpürge düşüş hasarı almaz/aktarmaz
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // binicili uçuş sırasında ateş/lav/ok/yaratık saldırısıyla ölüp
        // biniciyi havada bırakmasın diye — yerdeyken (binicisiz) normal kırılabilir
        return this.isVehicle() || super.isInvulnerableTo(source);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
        // uçan araç — düşüş takibi yok
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // hareket halinde (binicili) süpürge otu titrer; boşta yumuşak hover
        controllers.add(new AnimationController<>(this, "controller", 5,
                state -> state.setAndContinue(state.isMoving() && this.isVehicle() ? FLY : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
