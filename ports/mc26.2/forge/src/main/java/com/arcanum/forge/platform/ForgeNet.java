package com.arcanum.forge.platform;

import com.arcanum.Arcanum;
import com.arcanum.platform.PlatformNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forge ağ backend'i: TEK {@link SimpleChannel} {@code arcanum:main}.
 *
 * <p><b>Neden iki "zarf" mesajı:</b> SimpleChannel mesajları SINIF ile eşler ({@code messageBuilder(Class, disc, dir)}),
 * SPI ise yalnız {@code CustomPacketPayload.Type} + {@code StreamCodec} verir — Class yok. Bu yüzden kanala iki sabit
 * mesaj kaydedilir: {@link ToServer} (disc 0, PLAY_TO_SERVER) ve {@link ToClient} (disc 1, PLAY_TO_CLIENT). Her zarfın
 * gövdesi {@code [VarInt payloadIndex][payload codec'i]}; {@code payloadIndex} = {@code registerC2S/registerS2C}
 * ÇAĞRI SIRASI (sözleşmedeki "çağrı sırası = discriminator"). Yön doğrulaması iki katmanda: Forge (zarf yönü) + biz
 * (index'in kayıtlı yönü).
 *
 * <p>Tampon: PLAY fazında Forge {@code RegistryFriendlyByteBuf.wrap}'i override eder → decode tamponu registry-friendly
 * kalır (ItemStack/Component codec'leri çalışır). İstemciye özgü çağrılar {@link ForgeClientNet}'te.
 */
public final class ForgeNet implements PlatformNet {
    /** 1.21.1 Forge portunda 5 idi; 26.x zarf biçimi farklı → 6. Farklı sürüm = bağlantı reddi (VersionTest.exact). */
    public static final int PROTOCOL_VERSION = 6;

    private enum Dir { C2S, S2C }

    private record Entry(int index, Identifier id, Dir dir,
                         StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> codec,
                         ServerReceiver<CustomPacketPayload> serverReceiver) {}

    /** Zarflar (yalnız bu sınıf içinde; SimpleChannel sınıfla eşler). */
    record ToServer(CustomPacketPayload payload) {}
    record ToClient(CustomPacketPayload payload) {}

    private final List<Entry> byIndex = new ArrayList<>();
    private final Map<Identifier, Entry> byId = new HashMap<>();
    private final Map<Identifier, ClientReceiver<CustomPacketPayload>> clientReceivers = new ConcurrentHashMap<>();
    private final SimpleChannel channel;

    /**
     * Mod ctor'unda (her iki fiziksel tarafta) — kanal login el sıkışmasından ÖNCE kayıtlı olmalı.
     *
     * <p>26.2 / Forge 65.1.3: {@code SimpleChannel} kaynağı 64.1.3 ile BİREBİR aynı; {@code messageBuilder(Class, int,
     * NetworkDirection)} yalnız javadoc {@code @deprecated} (forRemoval YOK, 64'te de öyleydi) → tel biçimi/discriminator
     * sırası (0 = ToServer, 1 = ToClient) ve PROTOCOL_VERSION korunmak için {@code protocol(...)} API'sine GEÇİLMEDİ.
     */
    @SuppressWarnings("deprecation")
    ForgeNet() {
        this.channel = ChannelBuilder.named(Arcanum.id("main"))
                .networkProtocolVersion(PROTOCOL_VERSION)
                .simpleChannel();
        channel.messageBuilder(ToServer.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((msg, buf) -> encode(buf, msg.payload(), Dir.C2S))
                .decoder(buf -> new ToServer(decode(buf, Dir.C2S)))
                .consumerMainThread(this::handleToServer)
                .add();
        channel.messageBuilder(ToClient.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> encode(buf, msg.payload(), Dir.S2C))
                .decoder(buf -> new ToClient(decode(buf, Dir.S2C)))
                .consumerMainThread(this::handleToClient)
                .add();
        channel.build();
    }

    // ---------------------------------------------------------------- kayıt

    @Override
    public <T extends CustomPacketPayload> void registerC2S(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                            ServerReceiver<T> receiver) {
        add(type, codec, Dir.C2S, receiver);
    }

    @Override
    public <T extends CustomPacketPayload> void registerS2C(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        add(type, codec, Dir.S2C, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends CustomPacketPayload> void registerClientReceiver(CustomPacketPayload.Type<T> type,
                                                                       ClientReceiver<T> receiver) {
        Entry e = byId.get(type.id());
        if (e == null || e.dir() != Dir.S2C) {
            throw new IllegalStateException("registerClientReceiver: " + type.id() + " S2C olarak kayitli degil (once registerS2C)");
        }
        if (clientReceivers.putIfAbsent(type.id(), (ClientReceiver<CustomPacketPayload>) receiver) != null) {
            throw new IllegalStateException("registerClientReceiver iki kez: " + type.id());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private synchronized void add(CustomPacketPayload.Type<?> type, StreamCodec<? super RegistryFriendlyByteBuf, ?> codec,
                                  Dir dir, ServerReceiver<?> receiver) {
        if (byId.containsKey(type.id())) {
            throw new IllegalStateException("Payload iki kez kaydedildi: " + type.id());
        }
        Entry e = new Entry(byIndex.size(), type.id(), dir,
                (StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload>) (StreamCodec) codec,
                (ServerReceiver<CustomPacketPayload>) receiver);
        byIndex.add(e);
        byId.put(type.id(), e);
    }

    // ---------------------------------------------------------------- tel biçimi

    private void encode(RegistryFriendlyByteBuf buf, CustomPacketPayload payload, Dir expected) {
        Entry e = byId.get(payload.type().id());
        if (e == null) throw new IllegalArgumentException("Kayitsiz payload gonderildi: " + payload.type().id());
        if (e.dir() != expected) {
            throw new IllegalArgumentException("Yanlis yonde payload: " + e.id() + " kayitli=" + e.dir() + " istenen=" + expected);
        }
        buf.writeVarInt(e.index());
        e.codec().encode(buf, payload);
    }

    private CustomPacketPayload decode(RegistryFriendlyByteBuf buf, Dir expected) {
        int index = buf.readVarInt();
        if (index < 0 || index >= byIndex.size()) {
            throw new IllegalArgumentException("Gecersiz arcanum:main payload index'i " + index + " (kayitli " + byIndex.size() + ")");
        }
        Entry e = byIndex.get(index);
        if (e.dir() != expected) {
            throw new IllegalArgumentException("Yanlis yonde payload alindi: " + e.id() + " kayitli=" + e.dir());
        }
        return e.codec().decode(buf);
    }

    // ---------------------------------------------------------------- alıcılar (ana thread — consumerMainThread)

    private void handleToServer(ToServer msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        Entry e = byId.get(msg.payload().type().id());
        if (e != null && e.serverReceiver() != null) {
            e.serverReceiver().handle(msg.payload(), player);
        }
    }

    private void handleToClient(ToClient msg, CustomPayloadEvent.Context ctx) {
        ClientReceiver<CustomPacketPayload> r = clientReceivers.get(msg.payload().type().id());
        if (r != null) {
            r.handle(msg.payload());
        } else {
            Arcanum.LOGGER.warn("[Arcanum/Forge] S2C {} icin istemci alicisi yok", msg.payload().type().id());
        }
    }

    // ---------------------------------------------------------------- gönderim

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        channel.send(new ToClient(payload), PacketDistributor.PLAYER.with(player));
    }

    @Override
    public void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            channel.send(new ToClient(payload), PacketDistributor.PLAYER.with(p));
        }
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        channel.send(new ToServer(payload), PacketDistributor.SERVER.noArg());
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        Entry e = byId.get(type.id());
        return e != null && e.dir() == Dir.C2S && ForgeClientNet.isRemotePresent(channel);
    }
}
