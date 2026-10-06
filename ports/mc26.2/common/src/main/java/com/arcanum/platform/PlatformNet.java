package com.arcanum.platform;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ağ dikişi. Payload sınıfları ortak (vanilla {@link CustomPacketPayload} + {@link StreamCodec}).
 *
 * <p><b>KAYIT SIRASI SÖZLEŞMEDİR:</b> {@code registerC2S}/{@code registerS2C} çağrıları {@code Arcanum.init()} içinde,
 * iki fiziksel tarafta da AYNI sırayla yapılır (Forge SimpleChannel discriminator'ı çağrı sırasından türetir).
 * {@code registerClientReceiver} yalnız {@code ArcanumClient.init()} içinden çağrılır; sunucuda hiç çağrılmaz.
 */
public interface PlatformNet {

    /** İstemci→sunucu payload'ı bildirir + sunucu alıcısını bağlar. Alıcı SUNUCU ANA THREAD'inde çalışır. */
    <T extends CustomPacketPayload> void registerC2S(CustomPacketPayload.Type<T> type,
                                                     StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                     ServerReceiver<T> receiver);

    /** Sunucu→istemci payload'ı bildirir (iki tarafta da çağrılır; alıcı ayrı bağlanır). */
    <T extends CustomPacketPayload> void registerS2C(CustomPacketPayload.Type<T> type,
                                                     StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

    /** S2C alıcısı. Yalnız istemci init'inden. Alıcı İSTEMCİ ANA THREAD'inde çalışır. */
    <T extends CustomPacketPayload> void registerClientReceiver(CustomPacketPayload.Type<T> type,
                                                                ClientReceiver<T> receiver);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToAll(MinecraftServer server, CustomPacketPayload payload);

    /** Yalnız istemci. */
    void sendToServer(CustomPacketPayload payload);

    /** Yalnız istemci: bağlı sunucu bu payload'ı kabul ediyor mu (vanilla sunucuya bağlanınca false). */
    boolean canSendToServer(CustomPacketPayload.Type<?> type);

    @FunctionalInterface
    interface ServerReceiver<T extends CustomPacketPayload> {
        void handle(T payload, ServerPlayer player);
    }

    @FunctionalInterface
    interface ClientReceiver<T extends CustomPacketPayload> {
        void handle(T payload);
    }
}
