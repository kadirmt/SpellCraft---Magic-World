package com.arcanum.entity;

import com.arcanum.registry.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.level.Level;

/**
 * Arcanewood botu — vanilla Boat.Type kapalı bir enum olduğu için (yeni değer
 * eklenemiyor), ayrı bir EntityType olarak tanımlanıyor (BiomesOPlenty'nin de
 * kendi ahşap türleri için kullandığı yöntem). Görsel/doku tamamen client
 * tarafındaki ArcanewoodBoatRenderer'da sabitlenir — Boat.getVariant() burada
 * kullanılmıyor.
 *
 * <p>26.x: {@code Boat.Type} enum'u kalktı, her ahşap ayrı EntityType ve kurucu
 * kırılınca düşecek item'ı bir {@code Supplier<Item>} olarak ister. Supplier
 * TEMBEL — {@code ModItems.ARCANEWOOD_BOAT.get()} yalnız bot kırıldığında /
 * pick-result istendiğinde çağrılır (Forge kayıt sırası kuralı: ctor'da .get() yok).
 *
 * <p>BİLİNÇLİ SAPMA (parite denetimi): 1.21.1'de getDropItem override edilmediği ve
 * varyant OAK kaldığı için bu bot kırılınca / orta-tıkta MEŞE botu veriyordu (gizli hata).
 * 26.x'te kendi item'ı düşer. Birebir eski davranış istenirse tedarikçi
 * {@code () -> Items.OAK_BOAT} yapılır (sandıklıda {@code Items.OAK_CHEST_BOAT}).
 */
public class ArcanewoodBoatEntity extends Boat {
    public ArcanewoodBoatEntity(EntityType<? extends ArcanewoodBoatEntity> type, Level level) {
        super(type, level, () -> ModItems.ARCANEWOOD_BOAT.get());
    }
}
