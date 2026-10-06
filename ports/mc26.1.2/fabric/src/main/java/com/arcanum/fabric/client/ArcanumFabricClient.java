package com.arcanum.fabric.client;

import com.arcanum.client.ArcanumClient;
import com.arcanum.platform.Platform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Fabric istemci girişi (main entrypoint'ten SONRA çalışır → ortak kayıtlar bağlı).
 * İnce katman: tüm istemci kayıtları ortak {@link ArcanumClient#init()}'te; burada yalnız
 * Fabric'e özgü olay köprüleri + istemci komutu.
 */
public final class ArcanumFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Platform.initClient(new FabricPlatformClient());
        ArcanumClient.init();

        // İstemci-yerel config reload komutu (/arcanumclient reloadconfig) — adanmış
        // sunucuda sunucu komutu istemcinin örneğini tazelemediği için gerekli.
        ArcanumClientConfigCommand.register();

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ArcanumClient.onClientDisconnect());
        ClientTickEvents.END_CLIENT_TICK.register(ArcanumClient::onClientTickEnd);
    }
}
