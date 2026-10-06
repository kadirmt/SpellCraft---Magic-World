package com.arcanum.fabric;

import com.arcanum.item.WandItem;
import com.arcanum.registry.ModMobEffects;
import com.arcanum.spell.ImperiusCurseTracker;
import com.arcanum.spell.UmbraFormManager;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Imperio (İtaat Laneti) kurbanının KENDİ girişini (sol/sağ tık) iptal eder.
 * <p>
 * Kanon davranış: Imperius altındaki kişi kendi iradesiyle hareket edemez —
 * yalnızca lanet sahibinin (tracker) zorladığı aksiyonları yapar. Buradaki
 * Fabric interaction event'leri, IMPERIUS_CURSE efekti taşıyan oyuncunun
 * saldırı/kullanma girdilerini FAIL'e çevirir; efekt taşımayan herkes için
 * PASS döner (davranış değişmez). Kuklanın ZORLA saldırısı
 * {@link com.arcanum.spell.ImperiusCurseTracker} içinde server-side teleport +
 * hurt ile uygulanır ve bu bloklamadan ETKİLENMEZ (o yol event'lerden geçmez).
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

    public static void register() {
        // Sol tık — varlığa saldırı
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            // Cursed VEYA duman formundaki oyuncu: kendi saldırısını iptal et.
            if (isCursed(player) || isSmoke(player)) {
                return InteractionResult.FAIL;
            }
            // Cursed OLMAYAN caster server-side'da bir varlığa vurunca: kendi
            // Imperio mob'larına "şunu hallet" komutu kaydet, ama saldırıyı İPTAL
            // ETME (PASS -> normal saldırı devam eder).
            if (!world.isClientSide && player instanceof ServerPlayer) {
                ImperiusCurseTracker.commandAttack(player, entity);
            }
            return InteractionResult.PASS;
        });

        // Sol tık — blok kırmayı başlatma
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                (isCursed(player) || isSmoke(player)) ? InteractionResult.FAIL : InteractionResult.PASS);

        // Sağ tık — item kullan. KRİTİK: duman formunda ASA MUAF — asa sağ-tıkı formdan
        // çıkışın TEK gönüllü yolu (WandItem.use form kesmesi); asayı da yutarsak oyuncu
        // formda kilitlenir. Asa dışı her şey (yay/inci/iksir...) formdayken kesilir.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (isCursed(player)) {
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }
            if (isSmoke(player) && !(player.getItemInHand(hand).getItem() instanceof WandItem)) {
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }
            return InteractionResultHolder.pass(ItemStack.EMPTY);
        });

        // Sağ tık — bloğa etkileşim. Duman formunda asa yine MUAF: FAIL dönülürse istemci
        // useItem'a hiç geçmez ve bloğa bakarken asayla formdan çıkılamazdı.
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (isCursed(player)) {
                return InteractionResult.FAIL;
            }
            if (isSmoke(player) && !(player.getItemInHand(hand).getItem() instanceof WandItem)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // Blok kırmayı tamamen engelle (false = iptal)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                !(isCursed(player) || isSmoke(player)));
    }
}
