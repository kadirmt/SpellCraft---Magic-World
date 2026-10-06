package com.arcanum.fabric;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.arcanum.item.WandItem;
import com.arcanum.registry.ModComponents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
 */
public final class LumosLight {
    private static final Map<UUID, ResourceKey<Level>> DIM = new HashMap<>();
    private static final Map<UUID, BlockPos> POS = new HashMap<>();
    private static final BlockState LIGHT = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);

    private LumosLight() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tick(server, player);
            }
        });

        // Çıkan oyuncunun ışığını hemen söndür — dünyada yetim blok kalmasın
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUUID();
            removeLight(resolve(server, DIM.remove(id)), POS.remove(id));
        });

        // Sunucu kapanırken (final save'den önce) tüm ışıkları süpür
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            POS.forEach((id, pos) -> removeLight(resolve(server, DIM.get(id)), pos));
            DIM.clear();
            POS.clear();
        });

        // SP'de aynı JVM içinde yeni dünya açılışına bayat girdi taşınmasın
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            DIM.clear();
            POS.clear();
        });
    }

    private static void tick(MinecraftServer server, ServerPlayer player) {
        UUID id = player.getUUID();
        ServerLevel level = player.serverLevel();
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
        return key == null ? null : server.getLevel(key);
    }

    private static boolean hasLumosWand(ServerPlayer p) {
        return isLumos(p.getMainHandItem()) || isLumos(p.getOffhandItem());
    }

    private static boolean isLumos(ItemStack s) {
        // 1.20.1 PORT: component yerine NBT facade (mutasyonsuz okuma).
        return s.getItem() instanceof WandItem
                && ModComponents.isLumosActive(s);
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
