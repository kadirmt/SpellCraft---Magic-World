package com.arcanum.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.level.Level;

/** Arcanewood sandıklı botu — bkz. {@link ArcanewoodBoatEntity} açıklaması. */
public class ArcanewoodChestBoatEntity extends ChestBoat {
    public ArcanewoodChestBoatEntity(EntityType<? extends Boat> type, Level level) {
        super(type, level);
    }
}
