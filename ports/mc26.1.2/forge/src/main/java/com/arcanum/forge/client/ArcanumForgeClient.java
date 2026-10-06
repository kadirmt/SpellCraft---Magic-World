package com.arcanum.forge.client;

import com.arcanum.client.ArcanumClient;
import com.arcanum.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;

/**
 * Forge istemci girişi — YALNIZ {@code ArcanumForge} ctor'undan, {@code FMLEnvironment.dist == CLIENT} iken çağrılır
 * (dedicated sunucuda bu sınıf hiç yüklenmez). İnce katman: tüm istemci kayıtları ortak {@link ArcanumClient#init()}'te
 * (backend kuyruğa alır); burada yalnız Forge'a özgü olay köprüleri + istemci komutu.
 *
 * <p>Olaylar (Forge 64.1.3 kaynak): {@code TickEvent.ClientTickEvent.Post.BUS},
 * {@code ClientPlayerNetworkEvent.LoggingOut.BUS} (Minecraft#disconnect içinden; tekil oyuncu dünyası
 * kurulurken de ateşlenebilir — temizlik idempotent), {@code RegisterClientCommandsEvent.BUS} — hepsi DEFAULT grup.
 */
public final class ArcanumForgeClient {
    private ArcanumForgeClient() {}

    public static void init(BusGroup modBusGroup) {
        Platform.initClient(new ForgePlatformClient(modBusGroup));
        ArcanumClient.init();

        // İstemci-yerel config reload komutu (/arcanumclient reloadconfig)
        ArcanumClientConfigCommand.register();

        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(ArcanumForgeClient::onLoggingOut);
        TickEvent.ClientTickEvent.Post.BUS.addListener(ArcanumForgeClient::onClientTickPost);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ArcanumClient.onClientDisconnect();
    }

    private static void onClientTickPost(TickEvent.ClientTickEvent.Post event) {
        ArcanumClient.onClientTickEnd(Minecraft.getInstance());
    }
}
