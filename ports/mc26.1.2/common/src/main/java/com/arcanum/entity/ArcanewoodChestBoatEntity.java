package com.arcanum.entity;

import com.arcanum.registry.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.level.Level;

/** Arcanewood sandıklı botu — bkz. {@link ArcanewoodBoatEntity} açıklaması. */
public class ArcanewoodChestBoatEntity extends ChestBoat {
    public ArcanewoodChestBoatEntity(EntityType<? extends ChestBoat> type, Level level) {
        super(type, level, () -> ModItems.ARCANEWOOD_CHEST_BOAT.get());
    }
}
