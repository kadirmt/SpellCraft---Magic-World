package com.arcanum.fabric.platform;

import com.arcanum.platform.PlatformNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Yalnız fiziksel istemcide yüklenir. */
final class FabricClientNet {
    private FabricClientNet() {}

    static <T extends CustomPacketPayload> void registerReceiver(CustomPacketPayload.Type<T> type,
                                                                 PlatformNet.ClientReceiver<T> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                context.client().execute(() -> receiver.handle(payload)));
    }

    static void send(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    static boolean canSend(CustomPacketPayload.Type<?> type) {
        return ClientPlayNetworking.canSend(type);
    }
}
