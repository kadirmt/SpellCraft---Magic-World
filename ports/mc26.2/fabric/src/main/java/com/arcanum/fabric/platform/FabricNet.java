package com.arcanum.fabric.platform;

import com.arcanum.platform.PlatformNet;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric ağ backend'i. İstemciye özgü çağrılar {@link FabricClientNet}'te — bu sınıfın gövdesindeki referanslar
 * yalnız ilgili metod ÇALIŞINCA çözülür, dedicated sunucuda hiç çalışmaz.
 */
final class FabricNet implements PlatformNet {

    @Override
    public <T extends CustomPacketPayload> void registerC2S(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                            ServerReceiver<T> receiver) {
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
        // Sözleşme: alıcı sunucu ana thread'inde çalışır → execute ile garanti altına alınır.
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                context.server().execute(() -> receiver.handle(payload, context.player())));
    }

    @Override
    public <T extends CustomPacketPayload> void registerS2C(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        PayloadTypeRegistry.clientboundPlay().register(type, codec);
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientReceiver(CustomPacketPayload.Type<T> type,
                                                                       ClientReceiver<T> receiver) {
        FabricClientNet.registerReceiver(type, receiver);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        FabricClientNet.send(payload);
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        return FabricClientNet.canSend(type);
    }
}
