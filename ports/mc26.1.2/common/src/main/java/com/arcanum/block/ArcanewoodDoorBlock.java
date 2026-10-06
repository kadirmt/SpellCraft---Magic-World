package com.arcanum.block;

import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** Arcanewood kapısı — DoorBlock'un constructor'ı protected olduğu için ince bir alt sınıf gerekiyor. */
public class ArcanewoodDoorBlock extends DoorBlock {
    public ArcanewoodDoorBlock(BlockBehaviour.Properties properties) {
        super(BlockSetType.OAK, properties);
    }
}
