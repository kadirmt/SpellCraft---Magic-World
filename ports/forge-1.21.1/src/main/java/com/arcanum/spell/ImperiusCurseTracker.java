package com.arcanum.spell;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.arcanum.registry.ModMobEffects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Imperio'nun (İtaat Laneti) "evcil köpek gibi davran" davranışı için
 * loader-bağımsız takip kümesi. Eski sürüm HER tick TÜM dünyalardaki TÜM
 * entity'leri tarıyordu (Reviewer Finding 9) — bunun yerine yalnızca
 * {@link Spells#imperio} tarafından fiilen lanetlenen hedefler burada iz
 * sürülür (LumosLight.java'nın "küçük kümeyi tut" desenine benzer). Kayıt,
 * etki süresi dolunca veya entity kaybolunca kendiliğinden düşer.
 *
 * <p><b>Yaratıklar</b> için: lanet süresince HER tick, mob'un hedefi caster'ın
 * son saldırganına (varsa) KALICI olarak zorlanır — eski "hurt timestamp
 * değişti" tek-tick kapısı, mob'un kendi AI'sinin aradaki tick'lerde tekrar
 * caster'ı hedeflemesine izin verdiğinden savunma neredeyse hiç tetiklenmiyordu.
 * END_SERVER_TICK sonunda çalıştığı için bu setTarget, mob'un kendi hedefleme
 * goal'lerini EZER.
 *
 * <p><b>Oyuncular</b> için (Imperio bir oyuncuya atılınca): kukla davranışı
 * sunucu-yetkili teleport ile uygulanır. Kurban caster'ın saldırganına doğru
 * sürüklenir + saldırır; saldırgan yoksa {@code anchor}'a kilitlenir ("bastığın
 * tuşlar işe yaramaz"). {@code connection.teleport} istemci konumunu EZER, bu da
 * girdi kilidinin kaynağıdır.
 */
public final class ImperiusCurseTracker {
    private static final Map<UUID, TrackedCurse> TRACKED = new HashMap<>();

    private ImperiusCurseTracker() {}

    /**
     * Tek bir lanetli varlık için mutable takip verisi. {@code anchorX/Y/Z} ve
     * {@code attackCd} yalnızca oyuncu kuklaları için anlamlıdır (mob'larda
     * kullanılmaz, zararsız).
     */
    private static final class TrackedCurse {
        final ResourceKey<Level> levelKey;
        final UUID casterId;
        double anchorX;
        double anchorY;
        double anchorZ;
        int attackCd;
        /**
         * Caster'ın "şunu hallet" komutuyla işaret ettiği hedef (caster bir
         * varlığa vurunca {@link #commandAttack} ile atanır). recentFoe'dan
         * ÖNCELİKLİDİR. {@code commandExpiry} tick sayacı 0'a düşünce veya hedef
         * ölünce/kaybolunca temizlenir.
         */
        UUID commandedTargetId;
        int commandExpiry;

        TrackedCurse(ResourceKey<Level> levelKey, UUID casterId) {
            this.levelKey = levelKey;
            this.casterId = casterId;
        }
    }

    /** Komut verilen hedefin geçerli kalacağı süre (tick). ~10 sn. */
    private static final int COMMAND_TICKS = 200;

    /** Imperio hedefe uygulandığı anda çağrılır. */
    public static void track(ServerLevel level, Entity entity, LivingEntity caster) {
        TrackedCurse curse = new TrackedCurse(level.dimension(), caster.getUUID());
        // Oyuncu kuklaları için anchor'ı O ANKI konumla doldur (saldırgan yokken
        // kurbanın kilitleneceği nokta). Mob'larda kullanılmaz.
        curse.anchorX = entity.getX();
        curse.anchorY = entity.getY();
        curse.anchorZ = entity.getZ();
        TRACKED.put(entity.getUUID(), curse);
    }

    /**
     * "Şunu hallet" komutu: {@code caster} bir varlığa vurduğunda çağrılır
     * (server-side). Caster'a ait TÜM aktif Imperio lanetlerinin komut hedefini
     * {@code target}'a ayarlar ve süreyi tazeler; böylece o lanetli mob(lar)
     * caster'ın vurduğu hedefe saldırır. Caster'ın aktif laneti yoksa no-op.
     *
     * <p>Şu durumlarda atlanır (güvenli no-op): {@code target} LivingEntity
     * değilse, {@code target == caster} ise, ya da {@code target} o lanetin
     * kurbanının (cursed mob'un) KENDİSİyse (kendine saldırmasın).
     */
    public static void commandAttack(Player caster, Entity target) {
        if (caster == null || target == null || TRACKED.isEmpty()) {
            return;
        }
        if (!(target instanceof LivingEntity) || target == caster) {
            return;
        }
        UUID casterId = caster.getUUID();
        UUID targetId = target.getUUID();
        for (Map.Entry<UUID, TrackedCurse> e : TRACKED.entrySet()) {
            TrackedCurse curse = e.getValue();
            if (!casterId.equals(curse.casterId)) {
                continue;
            }
            // Lanetli mob'un kendisine saldırmasını komutlama.
            if (e.getKey().equals(targetId)) {
                continue;
            }
            curse.commandedTargetId = targetId;
            curse.commandExpiry = COMMAND_TICKS;
        }
    }

    public static void clear() {
        TRACKED.clear();
    }

    /**
     * Caster'ın "yakın zamanda" saldırganını döndürür — mob'un/oyuncu kuklasının
     * savunacağı düşman. {@code self} dışlanır (kurbanın kendisi caster'a
     * vurduysa kendine yönelmesin), caster de dışlanır. 100 tick (5 sn) pencere.
     */
    private static LivingEntity recentFoe(ServerPlayer caster, Entity self) {
        if (caster == null) {
            return null;
        }
        LivingEntity a = caster.getLastHurtByMob();
        int ts = caster.getLastHurtByMobTimestamp();
        if (a != null && a != self && a != caster && a.isAlive()
                && caster.tickCount - ts <= 100) {
            return a;
        }
        return null;
    }

    /**
     * Caster'ın "şunu hallet" komutuyla işaret ettiği geçerli hedefi döndürür
     * (canlı + süresi dolmamış); yoksa {@code null}. Yan etki: hedef geçersizse
     * (expired/ölü/kayıp/kendisi) komut kaydını temizler. Bu foe recentFoe'dan
     * ÖNCELİKLİDİR.
     */
    private static LivingEntity commandedFoe(ServerLevel level, TrackedCurse curse, Entity self) {
        if (curse.commandedTargetId == null) {
            return null;
        }
        if (curse.commandExpiry <= 0) {
            curse.commandedTargetId = null;
            return null;
        }
        curse.commandExpiry--;
        Entity target = level.getEntity(curse.commandedTargetId);
        if (!(target instanceof LivingEntity commanded)
                || !commanded.isAlive() || target == self) {
            curse.commandedTargetId = null;
            curse.commandExpiry = 0;
            return null;
        }
        return commanded;
    }

    public static void tick(MinecraftServer server) {
        if (TRACKED.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, TrackedCurse>> it = TRACKED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TrackedCurse> e = it.next();
            TrackedCurse tracked = e.getValue();
            ServerLevel level = server.getLevel(tracked.levelKey);
            Entity entity = level != null ? level.getEntity(e.getKey()) : null;
            if (!(entity instanceof LivingEntity victim)
                    || !victim.hasEffect(ModMobEffects.holderOf(ModMobEffects.IMPERIUS_CURSE))) {
                it.remove();
                continue;
            }

            ServerPlayer caster = server.getPlayerList().getPlayer(tracked.casterId);
            // "Şunu hallet" komut hedefi ÖNCELİKLİ; yoksa caster'a son saldırana düş.
            LivingEntity foe = commandedFoe(level, tracked, victim);
            if (foe == null) {
                foe = recentFoe(caster, victim);
            }

            if (victim instanceof Mob mob) {
                // Yaratık dalı: hedefi caster'ın saldırganına KALICI zorla; yoksa
                // pasif kal (foe null -> setTarget(null)).
                mob.setTarget(foe);
                if (foe != null) {
                    // Warden ve bazı moblar GoalSelector değil Brain kullanıyor;
                    // setTarget'i sessizce yok sayıyorlar. Brain hafızasına da
                    // ATTACK_TARGET yaz (brain'de bu modül yoksa vanilla sessizce
                    // yok sayar — güvenli). Süre olarak takip penceresine (~200 tick)
                    // yakın bir değer; her tick yeniden yazıldığı için taze kalır.
                    mob.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, foe, 200L);
                    // Warden özel API'siyle de tetikle (Brain'i doğru şekilde besler) +
                    // kendi öfke sistemine foe'ya karşı öfke yükle ki AI'si onu avlasın.
                    if (mob instanceof Warden warden) {
                        warden.setAttackTarget(foe);
                        warden.increaseAngerAt(foe);
                    }
                    // Wither: bespoke boss AI setTarget'ı ezer; 3 kafayı da foe'ya kilitle —
                    // orta kafa getTarget(=foe), yan iki kafa alternatif hedefi kullanır.
                    if (mob instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither) {
                        wither.setAlternativeTarget(1, foe.getId());
                        wither.setAlternativeTarget(2, foe.getId());
                    }
                }
            } else if (victim instanceof ServerPlayer puppet) {
                // Oyuncu kuklası dalı: teleport-tabanlı sürükleme + saldırı.
                if (foe != null) {
                    double dx = foe.getX() - puppet.getX();
                    double dz = foe.getZ() - puppet.getZ();
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                    if (dist > 2.2 && dist > 1.0e-4) {
                        double step = Math.min(0.30, dist);
                        double nx = puppet.getX() + dx / dist * step;
                        double nz = puppet.getZ() + dz / dist * step;
                        puppet.connection.teleport(nx, puppet.getY(), nz, yaw, puppet.getXRot());
                    } else {
                        puppet.connection.teleport(puppet.getX(), puppet.getY(), puppet.getZ(),
                                yaw, puppet.getXRot());
                        if (tracked.attackCd <= 0) {
                            foe.hurt(puppet.damageSources().playerAttack(puppet), 5.0f);
                            puppet.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                            tracked.attackCd = 12;
                        }
                    }
                    tracked.attackCd = Math.max(0, tracked.attackCd - 1);
                    // kuklalarken anchor'ı güncelle ki foe kaybolunca olduğu yerde dursun
                    tracked.anchorX = puppet.getX();
                    tracked.anchorY = puppet.getY();
                    tracked.anchorZ = puppet.getZ();
                } else {
                    // saldırgan yok -> anchor'a kilitle ("tuşlar işe yaramaz")
                    double mdx = puppet.getX() - tracked.anchorX;
                    double mdz = puppet.getZ() - tracked.anchorZ;
                    if (mdx * mdx + mdz * mdz > 0.02 * 0.02) {
                        puppet.connection.teleport(tracked.anchorX, tracked.anchorY, tracked.anchorZ,
                                puppet.getYRot(), puppet.getXRot());
                    }
                }
            }
        }
    }
}
