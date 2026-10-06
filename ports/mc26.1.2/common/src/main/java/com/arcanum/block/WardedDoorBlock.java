package com.arcanum.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Mühürlü Kapı — yalnız Alohomora büyüsüyle açılan büyülü kapı.
 *
 * <p>Tasarım:
 * <ul>
 *   <li>{@link BlockSetType#IRON} kullanır → {@code canOpenByHand()=false}, elle tıklayınca AÇILMAZ
 *       (açılış/kapanış sesleri de demir kapı sesleridir).</li>
 *   <li>Elle tıklamada kilit "tıngırtısı" ({@link SoundEvents#CHEST_LOCKED}) + aksiyon çubuğunda
 *       mühür mesajı gösterilir; vanilla demir kapı reddiyle tutarlı olarak {@code PASS} döner.</li>
 *   <li>Redstone tamamen yok sayılır — büyülü mühür kırmızıtaşla delinemez.</li>
 *   <li>{@link #unlock(Level, BlockPos, BlockState)} Alohomora'nın giriş noktasıdır.</li>
 * </ul>
 */
public class WardedDoorBlock extends DoorBlock {

    public WardedDoorBlock(BlockBehaviour.Properties properties) {
        super(BlockSetType.IRON, properties);
    }

    /**
     * Elle tıklama: IRON set tipi zaten açılmayı engeller; biz ek olarak kapalıysa kilit sesi
     * çalar ve oyuncuya mühür mesajını aksiyon çubuğunda gösteririz.
     * Vanilla demir kapının reddi gibi {@link InteractionResult#PASS} döner
     * (eldeki eşya kullanımı — ör. blok yerleştirme — engellenmez).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && !state.getValue(OPEN)) {
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS,
                    0.8f, level.getRandom().nextFloat() * 0.1f + 0.9f);
            player.sendOverlayMessage(Component.translatable("arcanum.warded_door.locked"));
        }
        return InteractionResult.PASS;
    }

    /**
     * Redstone sinyali TAMAMEN yok sayılır: super çağrılmaz, {@code POWERED} asla değişmez.
     * Büyülü mühür yalnız Alohomora'ya boyun eğer.
     * <p>26.x imzası: 5. parametre komşu konumu değil {@code @Nullable Orientation} (1.21.2+). {@code @Override}
     * imzayı derleyiciye doğrulatır — tutmasaydı vanilla DoorBlock#neighborChanged çalışır ve redstone
     * kapıyı AÇARDI.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighborBlock, Orientation orientation, boolean movedByPiston) {
        // bilerek boş — büyülü mühür redstone tanımaz
    }

    /**
     * Alohomora büyüsünün çağırdığı kilit açma. Hangi yarım verilirse verilsin çalışır:
     * {@link DoorBlock#setOpen} tıklanan yarımı günceller, diğer yarım vanilla
     * {@code updateShape} senkronuyla otomatik açılır. IRON set tipi sayesinde
     * {@link SoundEvents#IRON_DOOR_OPEN} sesini setOpen kendisi çalar; üstüne büyü çanı eklenir.
     *
     * @param level sunucu tarafı seviye
     * @param pos   kapının (herhangi bir yarımının) konumu
     * @param state {@code pos}'taki mevcut blockstate
     */
    public void unlock(Level level, BlockPos pos, BlockState state) {
        if (!state.is(this) || state.getValue(OPEN)) {
            return; // yanlış blok ya da zaten açık
        }
        setOpen(null, level, state, pos, true);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.4f);
    }
}
