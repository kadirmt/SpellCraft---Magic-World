package com.arcanum.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.arcanum.item.WandItem;
import com.arcanum.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lumos aktifken, asayı tutan oyuncuyu takip eden görünmez bir light bloğu (seviye 15)
 * yerleştirir/taşır — etraf gerçekten aydınlanır. Yalnızca hava bloklarına yazar,
 * oyuncu hareket edince taşır, Lumos kapanınca/asa bırakılınca temizler.
 *
 * Sağlamlık: level, bayat ServerLevel referansı yerine ResourceKey ile tutulur
 * (kapanmış dünyanın chunk kuyruğuna dokunmak SP'de kalıcı donmaya yol açar);
 * oyuncu çıkışında ve sunucu kapanışında ışıklar süpürülür — dünyada yetim
 * ışık bloğu kalmaz.
 *
 * <p>Loader-bağımsız: olay bağlama {@link com.arcanum.ArcanumEvents} üzerinden
 * (tick → {@link #tick}, çıkış → {@link #onPlayerLeave}, kapanış → {@link #onServerStopping},
 * açılış → {@link #onServerStarted}).
 */
public final class LumosLight {
    private static final Map<UUID, ResourceKey<Level>> DIM = new HashMap<>();
    private static final Map<UUID, BlockPos> POS = new HashMap<>();
    private static final BlockState LIGHT = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);

    private LumosLight() {}

    /** Sunucu tick sonu (kök END_SERVER_TICK — tick sırasında İLK). */
    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tick(server, player);
        }
    }

    /** Çıkan oyuncunun ışığını hemen söndür — dünyada yetim blok kalmasın. */
    public static void onPlayerLeave(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        UUID id = player.getUUID();
        removeLight(resolve(server, DIM.remove(id)), POS.remove(id));
    }

    /** Sunucu kapanırken (final save'den önce) tüm ışıkları süpür. */
    public static void onServerStopping(MinecraftServer server) {
        POS.forEach((id, pos) -> removeLight(resolve(server, DIM.get(id)), pos));
        DIM.clear();
        POS.clear();
    }

    /** SP'de aynı JVM içinde yeni dünya açılışına bayat girdi taşınmasın. */
    public static void onServerStarted() {
        DIM.clear();
        POS.clear();
    }

    private static void tick(MinecraftServer server, ServerPlayer player) {
        UUID id = player.getUUID();
        ServerLevel level = player.level();
        ServerLevel oldLevel = resolve(server, DIM.get(id));
        BlockPos oldPos = POS.get(id);

        if (hasLumosWand(player)) {
            BlockPos newPos = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
            if (oldLevel != level || oldPos == null || !oldPos.equals(newPos)) {
                removeLight(oldLevel, oldPos);
                placeLight(level, newPos);
                DIM.put(id, level.dimension());
                POS.put(id, newPos);
            }
        } else if (oldPos != null) {
            removeLight(oldLevel, oldPos);
            DIM.remove(id);
            POS.remove(id);
        }
    }

    /** Boyut anahtarını canlı sunucudan güncel ServerLevel'a çözer (yoksa null). */
    private static ServerLevel resolve(MinecraftServer server, ResourceKey<Level> key) {
        return key == null || server == null ? null : server.getLevel(key);
    }

    private static boolean hasLumosWand(ServerPlayer p) {
        return isLumos(p.getMainHandItem()) || isLumos(p.getOffhandItem());
    }

    private static boolean isLumos(ItemStack s) {
        return s.getItem() instanceof WandItem
                && s.getOrDefault(ModComponents.LUMOS_ACTIVE.get(), false);
    }

    private static void placeLight(ServerLevel level, BlockPos pos) {
        if (level != null && pos != null && level.getBlockState(pos).isAir()) {
            level.setBlock(pos, LIGHT, 2 | 16);
        }
    }

    private static void removeLight(ServerLevel level, BlockPos pos) {
        if (level != null && pos != null && level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
    }
}
