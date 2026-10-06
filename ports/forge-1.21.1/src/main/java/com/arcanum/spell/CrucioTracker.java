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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Crucio (Cruciatus Laneti) — MONOTONİK can drenajı + kıvranma. {@link ImperiusCurseTracker}
 * deseni: yalnızca fiilen lanetlenen kurbanlar iz sürülür, END_SERVER_TICK'te işlenir.
 *
 * <p>{@value #DURATION} tick (10 sn) boyunca kurbanın canı, başlangıçtan maks canının
 * %{@code 10}'una ({@code 1 - }{@value #TOTAL_FRAC}) DOĞRUSAL olarak indirilir — yani toplam
 * ~%90 can gider. Drenaj her tick {@code setHealth} ile ZORLA uygulanır: bu Affedilmez laneti
 * <b>zırh / Koruma büyüsü / Direnç / doğal REJENERASYON</b> tarafından hafifletilemez (eski
 * düz {@code hurt()} sürümü tok bir oyuncuda regen'e yenilip "1 kalp" veriyordu — çöp). Ama
 * ASLA öldürmez: %10 canda durur ("öldürmeyen acı" — lore). Süre boyunca Yavaşlık III +
 * Madenci Yorgunluğu III + CRUCIO işareti tazelenir; işaret üzerindeyken kurban asa
 * castleyemez ve sağ/sol tıklayamaz (WandItem.use / MouseHandlerMixin).
 */
public final class CrucioTracker {
    private CrucioTracker() {}

    /** Toplam süre (tick) — 10 sn. */
    public static final int DURATION = 200;
    /** Süre sonunda giden toplam can oranı (maks canın). Kalan = %10. */
    private static final float TOTAL_FRAC = 0.9f;
    /** Görsel/ses geri bildirim aralığı (tick). */
    private static final int FEEDBACK_INTERVAL = 10;

    private static final class Cursed {
        final ResourceKey<Level> levelKey;
        final UUID casterId;
        final float startHealth; // afflict anındaki can
        final float endHealth;   // maks canın %10'u — altına inmez (öldürmez)
        int ticksLeft;

        Cursed(ResourceKey<Level> levelKey, UUID casterId, float startHealth, float endHealth, int ticksLeft) {
            this.levelKey = levelKey;
            this.casterId = casterId;
            this.startHealth = startHealth;
            this.endHealth = endHealth;
            this.ticksLeft = ticksLeft;
        }
    }

    private static final Map<UUID, Cursed> CURSED = new HashMap<>();

    /** Crucio hedefe uygulandığında çağrılır — monotonik can drenajı + debuff'lar başlar. */
    public static void afflict(ServerLevel level, LivingEntity victim, Player caster) {
        float start = victim.getHealth();
        float end = victim.getMaxHealth() * (1.0f - TOTAL_FRAC);
        CURSED.put(victim.getUUID(), new Cursed(level.dimension(), caster.getUUID(), start, end, DURATION));
        applyMarks(victim);
    }

    public static boolean isAfflicted(LivingEntity e) {
        return CURSED.containsKey(e.getUUID());
    }

    /** İşaret + debuff'ları (kısa süreli, her tick tazelenir) uygula. */
    private static void applyMarks(LivingEntity victim) {
        victim.addEffect(new MobEffectInstance(ModMobEffects.holderOf(ModMobEffects.CRUCIO), 40, 0, false, true));
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false)); // Yavaşlık III
        victim.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 2, false, false));       // Madenci Yorgunluğu III
    }

    public static void tick(MinecraftServer server) {
        if (CURSED.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Cursed>> it = CURSED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Cursed> e = it.next();
            Cursed c = e.getValue();
            // Kurbanı BOYUTTAN BAĞIMSIZ çöz (portal/tp ile laneti iptal edememeli): önce oyuncu
            // listesi, yoksa tüm yüklü boyutlarda ara.
            Entity entity = server.getPlayerList().getPlayer(e.getKey());
            if (entity == null) {
                for (ServerLevel l : server.getAllLevels()) {
                    entity = l.getEntity(e.getKey());
                    if (entity != null) {
                        break;
                    }
                }
            }
            if (entity instanceof LivingEntity dead && (!dead.isAlive() || dead.isRemoved())) {
                it.remove();
                continue;
            }
            if (!(entity instanceof LivingEntity victim)) {
                // geçici bulunamadı (chunk unload vb.) — SİLME, sadece sayacı azalt.
                c.ticksLeft--;
                if (c.ticksLeft <= 0) {
                    it.remove();
                }
                continue;
            }
            ServerLevel level = (ServerLevel) victim.level(); // kurbanın GERÇEK boyutu
            applyMarks(victim); // debuff'lar asla lapse etmesin

            // MONOTONİK hedef-can: elapsed oranına göre start→end doğrusal düşer. Can hedefin
            // ÜSTÜNDEYSE (regen dahil) hedefe zorla indirilir → regen/zırh/koruma ETKİSİZ.
            int elapsed = DURATION - c.ticksLeft;                 // 0..DURATION
            float frac = Math.min(1.0f, elapsed / (float) DURATION);
            float target = c.startHealth - (c.startHealth - c.endHealth) * frac;
            if (target < c.endHealth) {
                target = c.endHealth;                            // asla %10 altına inme
            }
            float cur = victim.getHealth();
            if (cur > target) {
                boolean feedback = c.ticksLeft % FEEDBACK_INTERVAL == 0;
                if (feedback) {
                    // kırmızı flaş + hurt sesi/animasyonu + son-vuran atıfı (küçük, güvenli —
                    // hemen ardından setHealth hedefe sabitler, hurt öldüremez).
                    ServerPlayer caster = server.getPlayerList().getPlayer(c.casterId);
                    DamageSource src = caster != null
                            ? level.damageSources().indirectMagic(caster, caster)
                            : level.damageSources().magic();
                    victim.hurt(src, Math.min(1.0f, cur - target));
                }
                victim.setHealth(target);                        // ASIL drenaj (mitigation-bypass)
                if (feedback) {
                    SpellFx.shroud(level, victim, 0xFF2E2E, 10);
                }
            }

            c.ticksLeft--;
            if (c.ticksLeft <= 0) {
                it.remove(); // debuff'lar son tazelemeden ~2 sn sonra kendiliğinden söner
            }
        }
    }
}
