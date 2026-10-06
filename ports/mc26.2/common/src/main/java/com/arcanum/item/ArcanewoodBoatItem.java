package com.arcanum.item;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.BoatItem;

/**
 * Arcanewood bot/sandıklı bot item'ı.
 *
 * <p>1.21.1'de vanilla BoatItem kapalı {@code Boat.Type} enum'una bağlıydı (yeni değer eklenemiyordu),
 * bu yüzden kendi entity tiplerimizi doğrudan yerleştiren basit bir sürüm vardı. 1.21.2+ vanilla
 * {@code BoatItem(EntityType, Properties)} her ağaç türü için AYRI entity tipiyle çalışıyor → eski elle
 * yerleştirme mantığının (bakış ışını, çarpışma kontrolü, yaratıcı modda tüketmeme) vanilla karşılığı
 * kullanılır. Sınıf, kayıt/çağrı noktaları değişmesin diye ince bir alt sınıf olarak durur.
 */
public class ArcanewoodBoatItem extends BoatItem {

    public ArcanewoodBoatItem(EntityType<? extends AbstractBoat> entityType, Properties properties) {
        super(entityType, properties);
    }
}
