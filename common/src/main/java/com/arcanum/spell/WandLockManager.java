package com.arcanum.spell;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.arcanum.item.CastManager;
import com.arcanum.item.WandItem;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.data.ArcanumPlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * "Asa Kenetlenmesi" (Priori Incantatem) sunucu-tarafı yöneticisi — {@link CastManager}
 * kardeşi. Her sunucu tick'inde ({@code ServerTickEvents.END_SERVER_TICK}) çalışır.
 *
 * <p>Bir oyuncu SALDIRI büyüsü ATEŞLERKEN, karşısında ona bakan + o da saldırı büyüsü
 * cast eden/az önce atmış başka bir oyuncu varsa, mermiler uçmaz — bir {@link DuelLock}
 * kurulur. İki asa ucu arasında bir ışın olur; ortadaki çarpışma düğümü her tick
 * <b>asa gücü farkı + tıklama hızı (CPS, tavan {@value #CPS_CAP}) + kalan mana</b>'ya
 * göre KAYBEDENE doğru kayar. Düğüm bir asaya değince o oyuncu KAZANANIN büyüsüne maruz
 * kalır (AK → ölüm, Expelliarmus → asası uçar). Yalnızca oyuncu-oyuncu.
 */
public final class WandLockManager {
    private WandLockManager() {}

    // ---- denge sabitleri (bkz. blueprint Bölüm 5) ----
    /** Tıklama hızı tavanı — üstü sayılmaz (autoclicker'ı etkisiz kılar). */
    public static final int CPS_CAP = 18;
    /** CPS farkının düğüme etkisi (tick başına). Maks baskı: 0.0015×18 = 0.027/tick. */
    private static final float K1_CPS = 0.0015f;
    /** Asa gücü farkının düğüme etkisi. Maks baskı: 0.009×1.15 ≈ 0.010/tick (skill > asa). */
    private static final float K2_POWER = 0.009f;
    /** Kenetlenme KURULUM menzili (blok) — savaş büyülerinin gerçek hitscan erişimiyle
     *  hizalı: taban 16 × RANGE_MULT(1.50) × config menzil çarpanı ≈ 24 blok.
     *  (Eski sabit 11.0 idi; büyü menzili ×1.5 buff'ından sonra 11 bloktan uzak her
     *  düelloda büyü isabet edip kilit hiç kurulamıyordu — S1 kök nedeni.) */
    private static double range() {
        return 16.0 * 1.50 * com.arcanum.config.ArcanumConfig.get().spellRangeMult;
    }
    /** SÜRDÜRME menzil payı (blok) — histerezis: aimedPlayer bounding-box YÜZEYİNE kadar
     *  kilit kurar, tick ise MERKEZ-MERKEZ mesafe ölçer; pay olmadan tam-menzil kurulan
     *  kilit doğduğu tick "menzil dışı" sayılıp grace sonunda sessizce kopuyordu (S2 #1). */
    private static final double RANGE_KEEP_PAD = 2.0;
    /** Karşılıklı bakış koni eşiği — KURULUM için (~35°). */
    private static final double FACING_DOT = 0.82;
    /** Bakış koni eşiği — SÜRDÜRME için (~60°): kurulmuş kilit, düğümü/asayı içgüdüyle
     *  takip eden kameranın küçük kaçmalarıyla kopmasın (S2 #2 histerezisi). */
    private static final double FACING_DOT_KEEP = 0.5;
    /** Görüş/menzil bozulunca kenetlenmeyi bitirmeden önceki toparlanma payı (tick).
     *  12 (0.6 sn) çok dardı — teleport-onayı sırasında bayatlayan rotasyon paketleri
     *  bile aşıyordu; 30 tick (1.5 sn) sahte kopmaları örter, gerçek kaçışı yine bitirir. */
    private static final int GRACE_TICKS = 30;
    /** Bu tick'ten sonra düğüm hızlanır (nefes kesen berabereleri çözer). */
    private static final int SUDDEN_DEATH = 160;
    /** Sonuç sonrası iki tarafa uygulanan asa cooldown'u (tick). */
    private static final int RESULT_COOLDOWN = 60;
    /** "Az önce ateşledi" penceresi (tick) — aynı-tick fire yarışını kapatır. */
    private static final int RECENT_WINDOW = 12;
    /** İzleyicilerin ışını görebileceği menzil (blok²). */
    private static final double SPECTATE_R2 = 48.0 * 48.0;
    /** İzleyicilere durum gönderme aralığı (tick) — katılımcılar her tick, izleyiciler kısılmış. */
    private static final int SPECTATOR_INTERVAL = 3;

    /** Aktif kenetlenmeler; HER oyuncu UUID'si kendi kenetlenmesine işaret eder (iki anahtar/kilit). */
    private static final ConcurrentHashMap<UUID, DuelLock> BY_PLAYER = new ConcurrentHashMap<>();

    /** Kenetlenmeye giren SALDIRI büyüleri. Bombarda/Bombarda Maxima AoE olsa da kullanıcı
     *  isteğiyle düelloya dahil edildi (kazananın büyüsü kaybedende patlar — bkz. applyClashLoss). */
    private static final Set<String> ATTACK = Set.of(
            "expelliarmus", "stupefy", "petrificus_totalus", "incendio", "glacius",
            "sectumsempra", "crucio", "imperio", "avada_kedavra", "flipendo", "diffindo",
            "depulso", "everte_statum", "duro", "reducto", // rictusempra pasifleştirildi — düellodan çıkarıldı
            "bombarda", "bombarda_maxima");

    public static boolean isAttackSpell(Spell s) {
        return s != null && ATTACK.contains(s.id());
    }

    public static boolean isAttackIdx(int idx) {
        return idx >= 0 && idx < ModSpells.SPELLS.size() && isAttackSpell(ModSpells.SPELLS.get(idx));
    }

    public static boolean isLocked(UUID id) {
        return BY_PLAYER.containsKey(id);
    }

    /** C2S {@code lock_push} alıcısından (ana thread) çağrılır — bir tık kaydeder. */
    public static void registerClick(UUID id, long gameTime) {
        DuelLock l = BY_PLAYER.get(id);
        if (l != null) {
            l.addClick(id, gameTime);
        }
    }

    /**
     * {@link CastManager} fire kancasından çağrılır: {@code caster} bir saldırı büyüsü
     * ateşlemek üzereyken karşısında uygun bir rakip varsa kenetlenme başlatır ve
     * {@code true} döner (bu durumda normal ateşleme YAPILMAZ). Aksi halde {@code false}.
     */
    public static boolean tryStart(ServerLevel level, ServerPlayer caster, int spellIndex) {
        if (isLocked(caster.getUUID()) || wandOf(caster) == null) {
            return false;
        }
        if (manaOf(caster) < 1) {
            return false; // 0-mana ateşleyen (creative bypass) kilide girmesin — normal executeCast'e düşer
        }
        // A GERÇEKTEN bir OYUNCUYA mı nişan alıyor? (mob/blok/boşluk ise kilit yok → büyü
        // normal ateşlenir, mermi yutulmaz.) Bu, "bystander'ı düelloya sürükleme" hatasını kapatır.
        ServerPlayer target = aimedPlayer(level, caster);
        if (target == null || target == caster || !target.isAlive() || target.isRemoved()
                || isLocked(target.getUUID())) {
            return false;
        }
        ItemStack tw = wandOf(target);
        if (tw == null || manaOf(target) < 1) {
            return false; // asasız / 0-mana rakip kilide alınmaz (anında ölmesin)
        }
        if (!hasLineOfSight(level, caster, target)) {
            return false;
        }
        long gt = level.getGameTime();

        // Rakip A'ya SALDIRIYOR mu? (şu an saldırı cast ediyor VEYA az önce GERÇEKTEN
        // A'yı hedefleyerek ateşledi.)
        int targetSpell = -1;
        boolean targetCasting = false;
        int casting = CastManager.castingSpellIndex(target.getUUID());
        if (isAttackIdx(casting)) {
            targetSpell = casting;
            targetCasting = true;
        } else {
            CastManager.RecentFire rf = CastManager.recentFire(target.getUUID());
            if (rf != null && gt - rf.gameTime() <= RECENT_WINDOW && isAttackIdx(rf.spellIndex())
                    && caster.getUUID().equals(rf.targetId())) {
                targetSpell = rf.spellIndex();
            }
        }
        if (targetSpell < 0) {
            return false;
        }
        // BAKIŞ KAPISI: ateşleyen her zaman koni içinde bakmalı (aimedPlayer bunu fiilen
        // garanti eder ama koni de doğrulanır). Hedef ZATEN saldırı cast'i halindeyse
        // (telegraph'lı yavaş büyü — ör. Avada Kedavra) dar kurulum konisi yerine GEVŞEK
        // koni (FACING_DOT_KEEP ~60°) aranır: cast'teki oyuncunun anlık kamera oynaması
        // kenetlenmeyi düşürmesin (S1 gevşetme) AMA sırtı dönük PvE cast'i yapan oyuncu
        // zorla kilide çekilemesin (mob'a büyü atan oyuncuya force-lock griefing engeli).
        if (lookDot(caster, target) < FACING_DOT
                || lookDot(target, caster) < (targetCasting ? FACING_DOT_KEEP : FACING_DOT)) {
            return false;
        }
        CastManager.cancel(target); // rakibin devam eden cast'ini iptal et (ayrıca ateşlemesin)
        Spell sA = ModSpells.SPELLS.get(spellIndex);
        Spell sB = ModSpells.SPELLS.get(targetSpell);
        DuelLock l = new DuelLock(caster.getUUID(), target.getUUID(), spellIndex, targetSpell, sA.color(), sB.color());
        // ERKEN ATIŞIN ÖDÜLÜ: ateşlemeyi ilk bitiren taraf (caster=a) düğüme hafif avantajla
        // başlar (0.5→0.55, düğüm 1.0'a varırsa A kazanır). Hızlı büyü tek taraflı anında
        // öldürmek yerine düelloda küçük bir öncelik getirir — denge formülü değişmez.
        l.node = 0.55f;
        l.posA = caster.position(); // kenetlenme boyunca buraya sabitlenecek (WASD işlevsiz)
        l.posB = target.position();
        put(l);
        // ÇARPIŞMA ANI — iki asa ucunun ortasında parlak patlama + şok halkası
        Vec3 clashMid = SpellFx.wandTip(caster).lerp(SpellFx.wandTip(target), 0.5);
        SpellFx.burst(level, clashMid, 0xFFF4CE, 20, 0.3, 0.14);
        SpellFx.nova(level, clashMid, blend(sA.color(), sB.color(), 0.5f), 20, 0.25);
        SpellFx.soundAt(level, caster, SoundEvents.TRIDENT_RETURN, 1.0f, 0.7f);
        SpellFx.soundAt(level, target, SoundEvents.TRIDENT_RETURN, 1.0f, 0.7f);
        ArcanumNetwork.sendLock(caster, l, 0);
        ArcanumNetwork.sendLock(target, l, 0);
        return true;
    }

    /**
     * {@code caster}'ın bakış ışınında GERÇEKTEN nişan aldığı ilk oyuncunun UUID'si; nişan
     * alınan ilk şey mob/blok ise ya da kimse yoksa {@code null}. {@link CastManager#noteFire}
     * "az önce kimi hedefledi" penceresi için kullanır. ({@link com.arcanum.spell.Spells}'in
     * {@code ray} mantığıyla aynı: blok arkasındaki oyuncu sayılmaz.)
     */
    public static UUID aimedPlayerId(ServerLevel level, ServerPlayer caster) {
        ServerPlayer p = aimedPlayer(level, caster);
        return p == null ? null : p.getUUID();
    }

    private static ServerPlayer aimedPlayer(ServerLevel level, ServerPlayer caster) {
        double range = range();
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult bh = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double limit2 = bh.getType() == HitResult.Type.BLOCK ? eye.distanceToSqr(bh.getLocation()) : range * range;
        AABB box = caster.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(caster, eye, end, box,
                e -> e != caster && e.isPickable() && !e.isSpectator(), limit2);
        // İlk isabet eden canlı bir OYUNCU değilse (mob) veya blok daha yakınsa → nişan oyuncuda değil.
        if (eh != null && eh.getEntity() instanceof ServerPlayer sp
                && eye.distanceToSqr(eh.getLocation()) <= limit2) {
            return sp;
        }
        return null;
    }

    /** Her sunucu tick'i — düğümü kaydır, mana yak, koşulları denetle, çöz. */
    public static void tick(MinecraftServer server) {
        if (BY_PLAYER.isEmpty()) {
            return;
        }
        Set<DuelLock> locks = new HashSet<>(BY_PLAYER.values());
        for (DuelLock l : locks) {
            if (l.ended) continue;
            ServerPlayer pa = server.getPlayerList().getPlayer(l.a);
            ServerPlayer pb = server.getPlayerList().getPlayer(l.b);
            // İPTAL: biri yok / ölü / kaldırılmış
            if (pa == null || pb == null || !pa.isAlive() || !pb.isAlive() || pa.isRemoved() || pb.isRemoved()) {
                cancel(l, pa, pb, "arcanum.duel.cancel.gone");
                continue;
            }
            ServerLevel level = pa.serverLevel();
            if (pb.level() != level) { // farklı boyuta geçti
                cancel(l, pa, pb, "arcanum.duel.cancel.gone");
                continue;
            }
            ItemStack wa = wandOf(pa);
            ItemStack wb = wandOf(pb);
            if (wa == null || wb == null) { // asa elden çıktı (uçtu/değiştirildi)
                cancel(l, pa, pb, "arcanum.duel.cancel.wand");
                continue;
            }

            // HAREKET KİLİDİ — iki oyuncu da başlangıç konumuna sabitlenir (WASD işlevsiz,
            // kesinlikle yerinde kalırlar; bakış serbest — birbirlerini görüp asayı iterler).
            if (l.posA != null) {
                freeze(pa, l.posA);
            }
            if (l.posB != null) {
                freeze(pb, l.posB);
            }

            // MENZİL + KARŞILIKLI BAKIŞ + GÖRÜŞ (grace payıyla). HİSTEREZİS: sürdürme
            // menzili kurulum menzilinden RANGE_KEEP_PAD geniş (aimedPlayer box-yüzeyi,
            // burası merkez-merkez ölçer) ve bakış konisi gevşek (FACING_DOT_KEEP ~60°) —
            // oyuncular donuk olduğundan mesafe meşru yolla değişemez, pay denge bozmaz.
            double keep = range() + RANGE_KEEP_PAD;
            boolean ok = pa.distanceToSqr(pb) <= keep * keep
                    && facingEachOtherKeep(pa, pb) && hasLineOfSight(level, pa, pb);
            if (!ok) {
                if (++l.outOfSightTicks > GRACE_TICKS) {
                    cancel(l, pa, pb, "arcanum.duel.cancel.sight");
                    continue;
                }
            } else {
                l.outOfSightTicks = 0;
            }

            long gt = level.getGameTime();

            // MANA YAKMA + anında-kayıp
            int burn = l.ticks >= SUDDEN_DEATH ? 2 : 1;
            burnMana(pa, burn);
            burnMana(pb, burn);
            int ma = manaOf(pa);
            int mb = manaOf(pb);
            if (ma <= 0 && mb <= 0) {
                resolve(level, l, l.node > 0.5f, pa, pb); // dezavantajlı (düğüme yakın) kaybeder
                continue;
            } else if (ma <= 0) {
                resolve(level, l, false, pa, pb); // A mana bitti → kazanan B
                continue;
            } else if (mb <= 0) {
                resolve(level, l, true, pa, pb); // B mana bitti → kazanan A
                continue;
            }

            // DÜĞÜM FORMÜLÜ
            int cpsA = Math.min(CPS_CAP, l.cps(l.a, gt));
            int cpsB = Math.min(CPS_CAP, l.cps(l.b, gt));
            float delta = K1_CPS * (cpsA - cpsB) + K2_POWER * (powerOf(pa) - powerOf(pb));
            if (l.ticks >= SUDDEN_DEATH) {
                delta *= 1f + (l.ticks - SUDDEN_DEATH) / 160f;
            }
            l.node = clamp01(l.node + delta);
            l.ticks++;

            if (l.node <= 0.001f) {
                resolve(level, l, false, pa, pb); // A ucuna değdi → kazanan B
                continue;
            }
            if (l.node >= 0.999f) {
                resolve(level, l, true, pa, pb); // B ucuna değdi → kazanan A
                continue;
            }

            // IŞIN ARTIK TAMAMEN CLIENT-SIDE çizilir (SpellLockRenderer.spawnClientBeam +
            // world-render). Sunucu per-tick partikül YOLLAMAZ — o "paket seli" uzaktaki
            // oyuncunun bağlantısını tıkayıp (head-of-line blocking) düello bitişini 1-2 sn
            // geciktiriyordu. Artık ağdan yalnızca minik durum paketi (node + renkler) geçer.

            // S2C — KATILIMCILARA her tick (duyarlı HUD), yakın İZLEYİCİLERE kısılmış hızda
            // (client interpolasyonu pürüzsüzleştirir → az paketle akıcı ışın).
            ArcanumNetwork.sendLock(pa, l, 0);
            ArcanumNetwork.sendLock(pb, l, 0);
            if (l.ticks % SPECTATOR_INTERVAL == 0) {
                broadcastSpectators(level, l, 0, pa, pb);
            }
        }
    }

    // ---- sonuç / iptal ----

    private static void resolve(ServerLevel level, DuelLock l, boolean winnerIsA, ServerPlayer pa, ServerPlayer pb) {
        drop(l);
        ServerPlayer winner = winnerIsA ? pa : pb;
        ServerPlayer loser = winnerIsA ? pb : pa;
        int winnerSpell = winnerIsA ? l.spellA : l.spellB;
        int winnerColor = winnerIsA ? l.colorA : l.colorB;
        int phase = winnerIsA ? 1 : 2;

        ItemStack ww = wandOf(winner);
        Spells.applyClashLoss(level, winner, loser, ModSpells.SPELLS.get(winnerSpell), ww);
        if (ww != null) {
            winner.getCooldowns().addCooldown(ww.getItem(), RESULT_COOLDOWN);
        }
        ItemStack lw = wandOf(loser);
        if (lw != null) {
            loser.getCooldowns().addCooldown(lw.getItem(), RESULT_COOLDOWN);
        }
        Vec3 loserTip = SpellFx.wandTip(loser);
        SpellFx.burst(level, loserTip, winnerColor, 22, 0.35, 0.16);
        SpellFx.nova(level, loserTip, winnerColor, 24, 0.3);
        SpellFx.soundAt(level, winner, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.9f, 1.2f);
        SpellFx.sound(level, loserTip, SoundEvents.WITHER_HURT, 0.7f, 0.7f);

        // bitiş: katılımcılar + yakın izleyiciler (hepsinin ışını temizlensin)
        notifyNearby(level, l, phase);
    }

    private static void cancel(DuelLock l, ServerPlayer pa, ServerPlayer pb, String reasonKey) {
        drop(l);
        // Katılımcılar FARKLI boyutlarda olabilir (boyut-değişim iptal dalı) — notifyNearby
        // yalnız tek boyutu gezdiğinden diğer boyuttaki tarafı kaçırır. Her ikisine DOĞRUDAN
        // yolla (phase-3 idempotent, olası çift gönderim zararsız), sonra izleyicileri bilgilendir.
        // GÖRÜNÜRLÜK (S2 düzeltme 5): kalan meşru iptaller artık asla "sebepsiz" algılanmasın —
        // iki katılımcıya action-bar sebep mesajı.
        if (pa != null) {
            pa.displayClientMessage(Component.translatable(reasonKey).withStyle(ChatFormatting.GRAY), true);
            ArcanumNetwork.sendLock(pa, l, 3);
        }
        if (pb != null) {
            pb.displayClientMessage(Component.translatable(reasonKey).withStyle(ChatFormatting.GRAY), true);
            ArcanumNetwork.sendLock(pb, l, 3);
        }
        ServerLevel level = pa != null ? pa.serverLevel() : (pb != null ? pb.serverLevel() : null);
        if (level != null) {
            notifyNearby(level, l, 3);
        }
    }

    /** Kilit midpoint'i (donmuş başlangıç konumlarından). */
    private static Vec3 midOf(DuelLock l) {
        if (l.posA != null && l.posB != null) {
            return l.posA.lerp(l.posB, 0.5);
        }
        return l.posA != null ? l.posA : l.posB;
    }

    /** Durumu KATILIMCILAR + yakın İZLEYİCİLERE (48 blok) yollar — bitiş/iptalde ışın herkeste temizlensin. */
    private static void notifyNearby(ServerLevel level, DuelLock l, int phase) {
        Vec3 mid = midOf(l);
        for (ServerPlayer sp : level.players()) {
            if (l.involves(sp.getUUID())
                    || (mid != null && sp.distanceToSqr(mid.x, mid.y, mid.z) <= SPECTATE_R2)) {
                ArcanumNetwork.sendLock(sp, l, phase);
            }
        }
    }

    /** Yalnız yakın İZLEYİCİLERE (katılımcılar hariç) — tick sırasında kısılmış hızda güncel durum. */
    private static void broadcastSpectators(ServerLevel level, DuelLock l, int phase, ServerPlayer pa, ServerPlayer pb) {
        Vec3 mid = midOf(l);
        if (mid == null) {
            return;
        }
        for (ServerPlayer sp : level.players()) {
            if (sp == pa || sp == pb) {
                continue;
            }
            if (sp.distanceToSqr(mid.x, mid.y, mid.z) <= SPECTATE_R2) {
                ArcanumNetwork.sendLock(sp, l, phase);
            }
        }
    }

    private static void put(DuelLock l) {
        BY_PLAYER.put(l.a, l);
        BY_PLAYER.put(l.b, l);
    }

    private static void drop(DuelLock l) {
        BY_PLAYER.remove(l.a);
        BY_PLAYER.remove(l.b);
        l.ended = true;
    }

    // ---- yardımcılar ----

    static ItemStack wandOf(ServerPlayer p) {
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(h);
            if (s.getItem() instanceof WandItem) {
                return s;
            }
        }
        return null;
    }

    static float powerOf(ServerPlayer p) {
        ItemStack w = wandOf(p);
        float wand = (w != null && w.getItem() instanceof WandItem wi) ? wi.tier().powerMult() : 1.0f;
        // 10. tur: Güç skill'i düelloda da etkili (aynı asa + aynı CPS → güç yüksek olan kazanır).
        return wand * (1.0f + ArcanumLeveling.powerBonus(p));
    }

    /**
     * Oyuncuyu {@code anchor} konumuna sabitler — hız sıfırlanır, görünmez güçlü yavaşlık
     * (yürüme tahminini kırar), ve anchor'dan kaydıysa geri ışınlanır. BAKIŞA DOKUNMAZ
     * (kendi yaw/xRot'u geri verilir) — oyuncular birbirini görüp asayı iter, ama YER
     * DEĞİŞTİREMEZ (WASD işlevsiz). ImperiusCurseTracker'daki anchor deseniyle aynı.
     */
    private static void freeze(ServerPlayer p, Vec3 anchor) {
        p.setDeltaMovement(0, 0, 0);
        p.fallDistance = 0;
        p.hurtMarked = false;
        // görünmez güçlü yavaşlık — yalnızca tükenmek üzereyken tazele (her tick paket yollama).
        MobEffectInstance slow = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slow == null || slow.getDuration() < 4 || slow.getAmplifier() < 250) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 250, false, false));
        }
        double dx = p.getX() - anchor.x;
        double dy = p.getY() - anchor.y;
        double dz = p.getZ() - anchor.z;
        // Eşik 0.3 blok (0.09 = 0.3²): eski 0.02-blok eşiği zıplama/itilme mikro-kaymalarında
        // her tick teleport yağdırıyor, bekleyen teleport onayı sırasında vanilla sunucu
        // istemcinin hareket+ROTASYON paketlerini yok saydığından bakış açısı bayatlayıp
        // sahte facing-fail üretiyordu (S2 #2c). Yavaşlık+hız-sıfırlama zaten yer değiştirmeyi
        // engelliyor; teleport yalnız gerçek sürüklenmede devreye girer.
        if (dx * dx + dy * dy + dz * dz > 0.09) { // >0.3 blok kaydıysa geri zorla
            p.connection.teleport(anchor.x, anchor.y, anchor.z, p.getYRot(), p.getXRot());
        }
    }

    private static int manaOf(ServerPlayer p) {
        return ArcanumPlayerData.get(p.getServer()).getMana(p);
    }

    private static void burnMana(ServerPlayer p, int amt) {
        ArcanumPlayerData d = ArcanumPlayerData.get(p.getServer());
        d.setMana(p, Math.max(0, d.getMana(p) - amt));
        ArcanumNetwork.syncMagicData(p);
    }

    /** {@code a}'nın bakışının {@code b}'ye yönelme derecesi (dot; 1.0 = tam üstüne). */
    private static double lookDot(ServerPlayer a, ServerPlayer b) {
        Vec3 aToB = b.getEyePosition().subtract(a.getEyePosition()).normalize();
        return a.getLookAngle().dot(aToB);
    }

    /** SÜRDÜRME bakış kontrolü — kurulumdan (FACING_DOT) daha gevşek koni (histerezis). */
    private static boolean facingEachOtherKeep(ServerPlayer a, ServerPlayer b) {
        return lookDot(a, b) >= FACING_DOT_KEEP && lookDot(b, a) >= FACING_DOT_KEEP;
    }

    private static boolean hasLineOfSight(ServerLevel level, ServerPlayer a, ServerPlayer b) {
        Vec3 eyeA = a.getEyePosition();
        Vec3 eyeB = b.getEyePosition();
        return level.clip(new ClipContext(eyeA, eyeB, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, a))
                .getType() == HitResult.Type.MISS;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** İki RGB rengi t (0..1) oranında karıştırır. */
    private static int blend(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = Math.round(r1 + (r2 - r1) * t);
        int g = Math.round(g1 + (g2 - g1) * t);
        int b = Math.round(b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }
}
