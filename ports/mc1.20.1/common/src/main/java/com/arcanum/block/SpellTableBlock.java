package com.arcanum.block;

import com.arcanum.menu.SpellTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Büyü Masası — HogCraft'ın "diagon alley merchant" masasından portlanan blok
 * modeli. 4 yöne bakabilen basit bir HorizontalDirectionalBlock — köy mekaniği
 * için bir POI (Wizard mesleği) iş istasyonu olarak da kullanılır.
 *
 * <p>Etkileşim gerçek bir container GUI'si açar (vanilla Crafting Table tarzı):
 * sağ tıkta {@link SpellTableMenu} açılır, oyuncu kitabı ve malzemeyi kendi
 * slotlarına koyup GUI'deki listeden hangi büyüyü öğrenmek istediğini seçer.
 * TÜM doğrulama (kitap/kademe/XP/malzeme) sunucu tarafında
 * {@link com.arcanum.spell.SpellGating#attemptLearn} içinde yapılır.
 */
public class SpellTableBlock extends HorizontalDirectionalBlock {
    // 1.20.1 portu: blok codec() sistemi (MapCodec + simpleCodec) 1.20.3+'ta geldi —
    // 1.20.1'de yoktur, CODEC alanı ve codec() override'ı kaldırıldı.

    public SpellTableBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // 1.20.1: useWithoutItem yerine use(state, level, pos, player, hand, hit) — javap ile doğrulandı.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inv, p) -> new SpellTableMenu(containerId, inv, ContainerLevelAccess.create(level, pos)),
                    Component.translatable("block.arcanum.spell_table")));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }
}
