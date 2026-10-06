package com.arcanum.forge;

import com.arcanum.item.WandItem;
import com.arcanum.registry.ModMobEffects;
import com.arcanum.spell.ImperiusCurseTracker;
import com.arcanum.spell.UmbraFormManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Imperio (İtaat Laneti) kurbanının KENDİ girişini (sol/sağ tık) iptal eder.
 *
 * <p>Kanon davranış: Imperius altındaki kişi kendi iradesiyle hareket edemez —
 * yalnızca lanet sahibinin (tracker) zorladığı aksiyonları yapar. Kökteki 5
 * Fabric player event'inin Forge karşılıkları (hepsi FORGE bus, cancelable):
 * AttackEntityCallback → {@link AttackEntityEvent},
 * AttackBlockCallback → {@link PlayerInteractEvent.LeftClickBlock},
 * UseItemCallback → {@link PlayerInteractEvent.RightClickItem},
 * UseBlockCallback → {@link PlayerInteractEvent.RightClickBlock},
 * PlayerBlockBreakEvents.BEFORE → {@link BlockEvent.BreakEvent}.
 * Efekt taşımayan herkes için event'e dokunulmaz (davranış değişmez).
 * Kuklanın ZORLA saldırısı {@link ImperiusCurseTracker} içinde server-side
 * teleport + hurt ile uygulanır ve bu bloklamadan ETKİLENMEZ (o yol
 * event'lerden geçmez).
 */
public final class ImperiusInputBlocker {
    private ImperiusInputBlocker() {}

    /** IMPERIUS_CURSE efekti taşıyan oyuncular için true. */
    private static boolean isCursed(Player player) {
        return player != null
                && player.hasEffect(ModMobEffects.holderOf(ModMobEffects.IMPERIUS_CURSE));
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

    /** Sol tık — varlığa saldırı (iki logical side'da da tetiklenir, kök callback gibi). */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        // Cursed VEYA duman formundaki oyuncu: kendi saldırısını iptal et.
        if (isCursed(player) || isSmoke(player)) {
            event.setCanceled(true);
            return;
        }
        // Cursed OLMAYAN caster server-side'da bir varlığa vurunca: kendi
        // Imperio mob'larına "şunu hallet" komutu kaydet, ama saldırıyı İPTAL
        // ETME (normal saldırı devam eder).
        if (!player.level().isClientSide && player instanceof ServerPlayer) {
            ImperiusCurseTracker.commandAttack(player, event.getTarget());
        }
    }

    /** Sol tık — blok kırmayı başlatma. */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isCursed(event.getEntity()) || isSmoke(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Sağ tık — item kullan (kök UseItemCallback 'fail' → cancel + FAIL sonucu). */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isCursed(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        // KRİTİK: duman formunda ASA MUAF — asa sağ-tıkı formdan çıkışın TEK gönüllü
        // yolu (WandItem.use form kesmesi); asayı da yutarsak oyuncu formda kilitlenir.
        // Asa dışı her şey (yay/inci/iksir...) formdayken kesilir.
        if (isSmoke(event.getEntity())
                && !(event.getItemStack().getItem() instanceof WandItem)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Sağ tık — bloğa etkileşim. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isCursed(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        // Duman formunda asa yine MUAF: iptal edilirse istemci useItem'a hiç geçmez
        // ve bloğa bakarken asayla formdan çıkılamazdı.
        if (isSmoke(event.getEntity())
                && !(event.getItemStack().getItem() instanceof WandItem)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Blok kırmayı tamamen engelle (kök PlayerBlockBreakEvents.BEFORE → false). */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (isCursed(event.getPlayer()) || isSmoke(event.getPlayer())) {
            event.setCanceled(true);
        }
    }
}
