package com.arcanum.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;

/**
 * Arcanewood botu — vanilla Boat.Type kapalı bir enum olduğu için (yeni değer
 * eklenemiyor), ayrı bir EntityType olarak tanımlanıyor (BiomesOPlenty'nin de
 * kendi ahşap türleri için kullandığı yöntem). Görsel/doku tamamen client
 * tarafındaki ArcanewoodBoatRenderer'da sabitlenir — Boat.getVariant() burada
 * kullanılmıyor.
 */
public class ArcanewoodBoatEntity extends Boat {
    public ArcanewoodBoatEntity(EntityType<? extends ArcanewoodBoatEntity> type, Level level) {
        super(type, level);
    }
}
