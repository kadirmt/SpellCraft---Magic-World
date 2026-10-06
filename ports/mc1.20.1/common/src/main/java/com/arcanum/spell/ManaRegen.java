package com.arcanum.spell;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.arcanum.data.ArcanumPlayerData;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.registry.ModMobEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Oyuncu-bazlı MANA yenilenmesi (10. tur — mana asadan oyuncuya taşındı). Her sunucu
 * tick'inde çalışır: oyuncu başına tick'te {@code effRegenMult / BASE_REGEN_DIVISOR}
 * mana-puanı KESİRLİ olarak birikir; birikim 1'i aşınca tam mana verilir.
 *
 * <p>Eski uygulama tamsayı interval ({@code round(15/effMult)}) kullanıyordu — bu,
 * küçük regen artışlarını yuvarlamada YUTUYORDU (ör. 1.21→1.27 tabanı ikisi de
 * interval 12'ye yuvarlanıp %0 fark yaratıyordu; adversarial review bulgusu).
 * Kesirli akümülatörle her çarpan değişimi birebir orana yansır.
 *
 * <p>{@code effRegenMult = skillRegenMult × (Mana Haste ? 2.0 + amplifier : 1.0)}.
 * Mana Haste iksiri (seviye I = amplifier 0) → ×2; komutla yüksek seviye verilince her
 * ek seviye +1× (tick başına birden çok mana bile birikebilir → çok hızlı test).
 */
public final class ManaRegen {
    private ManaRegen() {}

    /** Oyuncu başına kesirli mana birikimi (0..1 arası kalan). Kalıcı değil — RAM yeter. */
    private static final Map<UUID, Double> ACC = new HashMap<>();

    /** Oyuncu başına son görülen mana tavanı — Seçmen Şapka tak/çıkar gibi kapasite
     *  değişimlerinde HUD anında güncellensin diye (regen "doluyken sync yok" yolunda
     *  kapasite değişimi aksi halde bir sonraki cast'e kadar görünmezdi). */
    private static final Map<UUID, Integer> LAST_CAP = new HashMap<>();

    public static void tick(MinecraftServer server) {
        ArcanumPlayerData data = ArcanumPlayerData.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            int cap = data.getManaCap(p);
            int mana = data.getMana(p); // kapasite düştüyse burada kalıcı kırpılır (bkz. getMana)
            Integer lastCap = LAST_CAP.put(p.getUUID(), cap);
            if (lastCap != null && lastCap.intValue() != cap) {
                ArcanumNetwork.syncMagicData(p); // kapasite değişti → HUD'a yeni cap + kırpılmış mana
            }
            if (mana >= cap) {
                ACC.remove(p.getUUID()); // doluyken birikim sıfırlanır (anında patlama olmasın)
                continue;
            }
            double effMult = data.manaRegenMult(p) * com.arcanum.config.ArcanumConfig.get().manaRegenMult; // kullanıcı config
            // Seçmen Şapka kafadayken +%10 mana yenilenmesi (config çarpanıyla bileşik).
            if (com.arcanum.item.SortingHatItem.isWearing(p)) {
                effMult *= 1.10;
            }
            var haste = p.getEffect(ModMobEffects.MANA_HASTE.get());
            if (haste != null) {
                // Mana Haste: iksir (seviye I = amplifier 0) → ×2. Her EK seviye +1× —
                // yani /effect ile yüksek seviye verince gerçekten çok hızlanır (amplifier okunur).
                effMult *= (2.0 + haste.getAmplifier());
            }
            double acc = ACC.getOrDefault(p.getUUID(), 0.0)
                    + effMult / ArcanumPlayerData.BASE_REGEN_DIVISOR;
            if (acc >= 1.0) {
                int whole = (int) acc;
                acc -= whole;
                data.setMana(p, Math.min(cap, mana + whole));
                ArcanumNetwork.syncMagicData(p);
            }
            ACC.put(p.getUUID(), acc);
        }
    }
}
