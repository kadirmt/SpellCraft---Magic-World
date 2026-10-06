package com.arcanum.server;

import com.arcanum.item.WandItem;
import com.arcanum.registry.ModMobEffects;
import com.arcanum.spell.ImperiusCurseTracker;
import com.arcanum.spell.UmbraFormManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Imperio (İtaat Laneti) kurbanının KENDİ girişini (sol/sağ tık) iptal eder.
 * <p>
 * Kanon davranış: Imperius altındaki kişi kendi iradesiyle hareket edemez —
 * yalnızca lanet sahibinin (tracker) zorladığı aksiyonları yapar. Kökteki 5
 * Fabric interaction callback'inin karar mantığı burada, loader-bağımsız
 * yüklemler olarak durur ({@code true} = girdiyi ENGELLE); loader yalnız olayı
 * yakalar ve sonucu kendi iptal biçimine çevirir:
 * <ul>
 *   <li>Fabric: AttackEntity/AttackBlock/UseItem/UseBlock → {@code InteractionResult.FAIL} (değilse PASS),
 *       {@code PlayerBlockBreakEvents.BEFORE} → {@code false}.</li>
 *   <li>Forge (EventBus 7): AttackEntityEvent / LeftClickBlock → dinleyici {@code true} döner (iptal);
 *       RightClickItem / RightClickBlock → {@code setCancellationResult(FAIL)} + iptal;
 *       {@code BlockEvent.BreakEvent} → {@code setResult(Result.DENY)} (yalnız iptal kırmayı ENGELLEMEZ).</li>
 * </ul>
 * IMPERIUS_CURSE efekti taşıyan oyuncunun saldırı/kullanma girdileri kesilir;
 * efekt taşımayan herkes için davranış değişmez. Kuklanın ZORLA saldırısı
 * {@link ImperiusCurseTracker} içinde server-side teleport + hurt ile uygulanır
 * ve bu bloklamadan ETKİLENMEZ (o yol event'lerden geçmez).
 */
public final class ImperiusInputBlocker {
    private ImperiusInputBlocker() {}

    /** IMPERIUS_CURSE efekti taşıyan oyuncular için true. */
    private static boolean isCursed(Player player) {
        return player != null
                && player.hasEffect(ModMobEffects.IMPERIUS_CURSE.holder());
    }

    /**
     * Umbravolo kara duman formundaki oyuncular için true — duman fiziksel dünyaya
     * dokunamaz: melee/yay/inci/iksir/blok girişleri kesilir ("görünmez uçan okçu"
     * PvP boşluğu). Sunucu-otoriter harita; istemci tarafında (dedicated) boş kalabilir,
     * asıl iptal her durumda sunucuda gerçekleşir.
     */
    private static boolean isSmoke(Player player) {
        return player != null && UmbraFormManager.isActive(player);
    }

    /** Sol tık — varlığa saldırı (iki mantıksal tarafta da çağrılır, kök callback gibi). */
    public static boolean shouldBlockAttackEntity(Player player, Level level, Entity target) {
        // Cursed VEYA duman formundaki oyuncu: kendi saldırısını iptal et.
        if (isCursed(player) || isSmoke(player)) {
            return true;
        }
        // Cursed OLMAYAN caster server-side'da bir varlığa vurunca: kendi
        // Imperio mob'larına "şunu hallet" komutu kaydet, ama saldırıyı İPTAL
        // ETME (PASS -> normal saldırı devam eder).
        if (!level.isClientSide() && player instanceof ServerPlayer) {
            ImperiusCurseTracker.commandAttack(player, target);
        }
        return false;
    }

    /** Sol tık — blok kırmayı başlatma. */
    public static boolean shouldBlockAttackBlock(Player player) {
        return isCursed(player) || isSmoke(player);
    }

    /**
     * Sağ tık — item kullan. KRİTİK: duman formunda ASA MUAF — asa sağ-tıkı formdan
     * çıkışın TEK gönüllü yolu (WandItem.use form kesmesi); asayı da yutarsak oyuncu
     * formda kilitlenir. Asa dışı her şey (yay/inci/iksir...) formdayken kesilir.
     */
    public static boolean shouldBlockUseItem(Player player, InteractionHand hand) {
        if (isCursed(player)) {
            return true;
        }
        return isSmoke(player) && !(player.getItemInHand(hand).getItem() instanceof WandItem);
    }

    /**
     * Sağ tık — bloğa etkileşim. Duman formunda asa yine MUAF: engellenirse istemci
     * useItem'a hiç geçmez ve bloğa bakarken asayla formdan çıkılamazdı.
     */
    public static boolean shouldBlockUseBlock(Player player, InteractionHand hand) {
        if (isCursed(player)) {
            return true;
        }
        return isSmoke(player) && !(player.getItemInHand(hand).getItem() instanceof WandItem);
    }

    /** Blok kırmayı tamamen engelle (kök PlayerBlockBreakEvents.BEFORE → false = iptal). */
    public static boolean shouldBlockBlockBreak(Player player) {
        return isCursed(player) || isSmoke(player);
    }
}
