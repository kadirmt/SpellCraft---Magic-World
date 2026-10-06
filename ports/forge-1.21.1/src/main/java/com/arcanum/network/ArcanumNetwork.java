package com.arcanum.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.arcanum.Arcanum;
import com.arcanum.data.ArcanumPlayerData;
import com.arcanum.item.WandItem;
import com.arcanum.menu.SpellTableMenu;
import com.arcanum.registry.ModComponents;
import com.arcanum.spell.DuelLock;
import com.arcanum.spell.ModSpells;
import com.arcanum.spell.Spell;
import com.arcanum.spell.SpellFx;
import com.arcanum.spell.SpellGating;
import com.arcanum.spell.WandLockManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * Ağ katmanı (FORGE 1.21.1 PORTU — {@link ChannelBuilder} + {@link SimpleChannel}).
 *
 * <p>Kökteki (Fabric 1.21.1) hat: vanilla {@code CustomPacketPayload} tip kaydı
 * {@code PayloadTypeRegistry} ile fabric modülünde, gönderim common'dan ham
 * {@code ClientboundCustomPayloadPacket} ile. MinecraftForge 1.21.1'de vanilla payload
 * tip haritasına mod payload'u sokan bir API yok — bunun yerine Forge'un TEK kanallı
 * ({@code arcanum:main}) SimpleChannel'ı kullanılır; her mesaj int discriminator ile
 * ayrışır ve mevcut {@code StreamCodec<RegistryFriendlyByteBuf, T>} CODEC'leri
 * {@code MessageBuilder.codec(...)} üzerinden AYNEN yeniden kullanılır (javap ile
 * doğrulandı: Forge, {@code RegistryFriendlyByteBuf.wrap}'i covariant override'layarak
 * play fazında payload buffer'ının registry-friendly kalmasını garanti eder —
 * {@code ComponentSerialization.STREAM_CODEC} dahil çalışır).
 *
 * <ul>
 *   <li><b>Kayıt:</b> mod constructor'ı {@link #register()} çağırır (handshake'ten önce
 *       olmalı). Alıcı gövdeleri de burada kaydedilir — C2S gövdeler sunucu mantığını
 *       doğrudan, S2C gövdeler istemci işini {@link ArcanumClientHooks} köprüsünden
 *       çağırır (bu sınıf dedicated server'da da yüklenir; client-only sınıf referansı
 *       YOKTUR).</li>
 *   <li><b>Sürüm:</b> {@code networkProtocolVersion(1)} + varsayılan kabul testi =
 *       {@code VersionTest.exact(1)} her iki yanda → kanalı taşımayan (modsuz/vanilla)
 *       veya farklı protokol sürümlü istemci REDDEDİLİR. Kökteki "istemcide mod şart"
 *       davranışının birebir karşılığı; {@code optional()} BİLEREK çağrılmıyor.</li>
 *   <li><b>Thread modeli:</b> {@code consumerMainThread} = decode network thread'inde,
 *       gövde {@code enqueueWork} ile mantıksal tarafın ana thread'inde — kökteki
 *       {@code player.getServer().execute(...)} / {@code client.execute(...)}
 *       kalıplarının birebir karşılığı.</li>
 * </ul>
 *
 * <p>Sunucu tarafı gönderim metodlarının ({@code sync*}, {@code send*}, handleLearnSpell) imzaları kökle
 * AYNEN aynı — çağıran common dosyaları değişmez. İstemci kodu (ekranlar, HUD,
 * MouseHandler mixin'i) C2S için {@link #sendToServer(CustomPacketPayload)} kullanır
 * (kökteki {@code ClientPlayNetworking.send(payload)} çağrılarının yerine geçer).
 */
public final class ArcanumNetwork {
    private ArcanumNetwork() {}

    /** Kanal protokol sürümü — payload şeması değişirse artır (eski istemci reddedilir). */
    public static final int PROTOCOL_VERSION = 5; // 5: disc. 16/17 kaldirildi (mesaj sayisi 16)

    private static SimpleChannel channel;

    private static SimpleChannel channel() {
        return Objects.requireNonNull(channel,
                "ArcanumNetwork.register() cagrilmadan paket gonderildi (mod ctor kaydi eksik)");
    }

    // ------------------------------------------------------------------
    // Kanal kurulumu — mod constructor'ından BİR KEZ çağrılır
    // ------------------------------------------------------------------

    /**
     * Kanalı kurar ve TÜM mesajları (7 C2S + 9 S2C) kaydeder. Mod constructor'ından
     * (her iki fiziksel tarafta) BİR KEZ çağrılır — kanal listesi login handshake'inde
     * karşı tarafa bildirildiği için kayıt oyuncu bağlanmadan önce bitmiş olmalıdır.
     * İkinci çağrı sessizce yok sayılır (idempotent).
     *
     * <p>Discriminator'lar SABİT (0-6 C2S, 7-15 S2C) —
     * sıra iki taraf arasında wire
     * sözleşmesidir, mevcut bir mesajın numarasını asla değiştirme; yeni mesaj daima
     * sona eklenir + {@link #PROTOCOL_VERSION} artırılır.
     */
    public static void register() {
        if (channel != null) {
            return;
        }

        SimpleChannel ch = ChannelBuilder
                .named(ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "main"))
                .networkProtocolVersion(PROTOCOL_VERSION)
                .simpleChannel();

        // ---------------- C2S (istemci → sunucu) ----------------
        // Radyal menü: seçilen büyü index'i → eldeki asanın SELECTED_SPELL bileşeni (doğrulayarak).
        ch.messageBuilder(SelectSpellPayload.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .codec(SelectSpellPayload.CODEC)
                .consumerMainThread(ArcanumNetwork::handleSelectSpell)
                .add();
        // Büyü Masası öğrenme isteği: TÜM doğrulama sunucu tarafında SpellGating'de
        // yapılır — envanter/XP mutasyonu asla network thread'inden yapılmaz
        // (consumerMainThread ana sunucu thread'ine geçişi garanti eder).
        ch.messageBuilder(LearnSpellPayload.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .codec(LearnSpellPayload.CODEC)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.getSender();
                    if (player != null) {
                        handleLearnSpell(player, msg.spellId());
                    }
                })
                .add();
        // Dizilim: slota büyü ata/temizle. Sunucu-yetkili — spellIndex>=0 ise büyü
        // gerçekten bilinmeli (SpellGating.knows), yoksa atama YAPMA.
        ch.messageBuilder(AssignSlotPayload.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .codec(AssignSlotPayload.CODEC)
                .consumerMainThread(ArcanumNetwork::handleAssignSlot)
                .add();
        // Dizilim: aktif sayfa/slot değiştir (+/- sayfa, Shift+tekerlek slot). Sunucu clamp'ler.
        ch.messageBuilder(SetActivePayload.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .codec(SetActivePayload.CODEC)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.getSender();
                    if (player != null) {
                        ArcanumPlayerData.get(player.getServer()).setActive(player, msg.page(), msg.slot());
                        syncLoadout(player);
                    }
                })
                .add();
        // Asa kenetlenmesi: sol-tık "asayı it" sinyali. Tık SAYIMI ve CPS TAVANI (18)
        // otoritesi sunucuda (WandLockManager); kilit yoksa sessizce yok sayılır.
        ch.messageBuilder(LockPushPayload.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .codec(LockPushPayload.CODEC)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.getSender();
                    if (player != null) {
                        WandLockManager.registerClick(player.getUUID(), player.level().getGameTime());
                    }
                })
                .add();
        // Skill ağacı: puan harca (server-authoritative doğrulama) + respec (ücretsiz).
        ch.messageBuilder(SpendPointPayload.class, 5, NetworkDirection.PLAY_TO_SERVER)
                .codec(SpendPointPayload.CODEC)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.getSender();
                    int track = msg.track();
                    if (player == null || track < 0 || track > 3) {
                        return;
                    }
                    // Redde de sync gönder: bayat istemci (ör. runtime'da düşürülen config)
                    // "satın alınabilir" görünümünde takılı kalmasın — sunucu-otoriter, zararsız.
                    ArcanumPlayerData.get(player.getServer()).trySpendPoint(player, track);
                    syncMagicData(player);
                })
                .add();
        ch.messageBuilder(RespecPayload.class, 6, NetworkDirection.PLAY_TO_SERVER)
                .codec(RespecPayload.CODEC)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer player = ctx.getSender();
                    if (player != null) {
                        ArcanumPlayerData.get(player.getServer()).respec(player);
                        syncMagicData(player);
                    }
                })
                .add();

        // ---------------- S2C (sunucu → istemci) ----------------
        // Gövdeler ArcanumClientHooks köprüsünden geçer: bu sınıf dedicated server'da da
        // yüklendiği için client-only sınıflara (ClientSpellData vb.) referans YASAK.
        // Handler kurulmadan paket gelirse (teorik yarış) paket sessizce düşer — hepsi
        // idempotent durum senkronu, bir sonraki senkron telafi eder.
        // Bilinen büyü listesi senkronu
        ch.messageBuilder(KnownSpellsPayload.class, 7, NetworkDirection.PLAY_TO_CLIENT)
                .codec(KnownSpellsPayload.CODEC)
                .consumerMainThread((msg, ctx) -> {
                    ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                    if (h != null) {
                        h.setKnownSpells(msg.spellIds());
                    }
                })
                .add();
        // Büyü Masası GUI'si açıkken vanilla actionbar/chat görünmez — sonucu doğrudan
        // açık ekrana ilet (ekran kontrolünü client handler yapar).
        ch.messageBuilder(SpellTableFeedbackPayload.class, 8, NetworkDirection.PLAY_TO_CLIENT)
                .codec(SpellTableFeedbackPayload.CODEC)
                .consumerMainThread((msg, ctx) -> {
                    ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                    if (h != null) {
                        h.showSpellTableFeedback(msg.success(), msg.message());
                    }
                })
                .add();
        // Büyü dizilimi senkronu (12 slot + aktif sayfa/slot)
        ch.messageBuilder(SpellLoadoutPayload.class, 9, NetworkDirection.PLAY_TO_CLIENT)
                .codec(SpellLoadoutPayload.CODEC)
                .consumerMainThread((msg, ctx) -> {
                    ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                    if (h != null) {
                        h.setLoadout(msg.loadout(), msg.activePage(), msg.activeSlot());
                    }
                })
                .add();
        // Cast durumu senkronu: cast başladı (spellIndex>=0) / bitti-iptal (spellIndex<0).
        // İstemci implementasyonu spellIndex<0 / totalTicks<=0 gelince kendi içinde clear() yapar.
        ch.messageBuilder(CastStatePayload.class, 10, NetworkDirection.PLAY_TO_CLIENT)
                .codec(CastStatePayload.CODEC)
                .consumerMainThread((msg, ctx) -> {
                    ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                    if (h != null) {
                        h.setCastState(msg.spellIndex(), msg.totalTicks());
                    }
                })
                .add();
        // Asa kenetlenmesi durumu senkronu: ışın/düğüm/HUD buna göre çizilir/temizlenir.
        ch.messageBuilder(LockStatePayload.class, 11, NetworkDirection.PLAY_TO_CLIENT)
                .codec(LockStatePayload.CODEC)
                .consumerMainThread((msg, ctx) -> {
                    ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                    if (h != null) {
                        h.setLockState(msg.a(), msg.b(), msg.node(), msg.colorA(), msg.colorB(), msg.phase());
                    }
                })
                .add();
        // Büyücü verisi (level/xp/skill/mana) senkron — ManaHud + skill paneli buradan okur.
        ch.messageBuilder(MagicDataPayload.class, 12, NetworkDirection.PLAY_TO_CLIENT)
                .codec(MagicDataPayload.CODEC)
                .consumerMainThread((msg, ctx) -> {
                    ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                    if (h != null) {
                        h.setMagicData(msg.level(), msg.xp(), msg.xpToNext(),
                                msg.mana(), msg.manaCap(), msg.nodes(),
                                msg.maxLevel(), msg.pointsPerLevel());
                    }
                })
                .add();
        // Seviye atlama olayı → sağ-üstte cilalı toast bildirimi göster.
        ch.messageBuilder(LevelUpPayload.class, 13, NetworkDirection.PLAY_TO_CLIENT)
                .codec(LevelUpPayload.CODEC)
                .consumerMainThread((msg, ctx) -> {
                    ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                    if (h != null) {
                        h.showLevelUp(msg.level());
                    }
                })
                .add();
        // Büyü ışını olayı: yıldırım-tarzı ışın istemcide çizilir, ~0.5 sn iz bırakır
        // (ClientSpellBeams.add — client-only sınıfa köprüden geçilir).
        ch.messageBuilder(SpellBeamPayload.class, 14, NetworkDirection.PLAY_TO_CLIENT)
                .codec(SpellBeamPayload.CODEC)
                .consumerMainThread((msg, ctx) -> {
                    ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                    if (h != null) {
                        h.addSpellBeam(
                                new net.minecraft.world.phys.Vec3(msg.fromX(), msg.fromY(), msg.fromZ()),
                                new net.minecraft.world.phys.Vec3(msg.toX(), msg.toY(), msg.toZ()),
                                msg.color(), msg.life(), msg.width(), msg.spread());
                    }
                })
                .add();

        // Umbravolo kara duman formu durumu: formdaki oyuncunun entity id'si + aktif/pasif.
        // İstemci ClientUmbraForms setine işler; zırh/eldeki-eşya/elytra/kafa render
        // mixin'leri o sete bakarak formdaki oyuncuyu tamamen gizler.
        ch.messageBuilder(UmbraFormPayload.class, 15, NetworkDirection.PLAY_TO_CLIENT)
                .codec(UmbraFormPayload.CODEC)
                .consumerMainThread((msg, ctx) -> {
                    ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                    if (h != null) {
                        h.setUmbraForm(msg.entityId(), msg.active());
                    }
                })
                .add();

        channel = ch.build();
    }

    // ------------------------------------------------------------------
    // C2S alıcı gövdeleri (kökteki ServerPlayNetworking kayıtlarının taşınmış hali)
    // ------------------------------------------------------------------

    /** Radyal menü seçimi: index'i clamp'le, eldeki asanın SELECTED_SPELL bileşenine yaz. */
    private static void handleSelectSpell(SelectSpellPayload msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(msg.index(), ModSpells.SPELLS.size() - 1));
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        ItemStack wand = main.getItem() instanceof WandItem ? main
                : (off.getItem() instanceof WandItem ? off : null);
        if (wand != null) {
            wand.set(ModComponents.SELECTED_SPELL.get(), clamped);
        }
    }

    /** Dizilim: slota büyü ata/temizle — sunucu-yetkili doğrulama (aralık + büyü biliniyor mu). */
    private static void handleAssignSlot(AssignSlotPayload msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) {
            return;
        }
        int page = msg.page();
        int slot = msg.slot();
        int spellIndex = msg.spellIndex();
        if (page < 0 || page >= ArcanumPlayerData.PAGES
                || slot < 0 || slot >= ArcanumPlayerData.SLOTS) {
            return; // geçersiz konum — sessizce yok say
        }
        ArcanumPlayerData data = ArcanumPlayerData.get(player.getServer());
        if (spellIndex < 0) {
            data.setSlot(player, page, slot, -1); // temizle
        } else if (spellIndex < ModSpells.SPELLS.size()) {
            Spell spell = ModSpells.SPELLS.get(spellIndex);
            if (!SpellGating.knows(player.getServer(), player, spell)) {
                return; // bilinmeyen büyü — atama yok, resync gereksiz
            }
            data.setSlot(player, page, slot, spellIndex);
        } else {
            return; // aralık dışı index
        }
        syncLoadout(player);
    }

    // ------------------------------------------------------------------
    // Gönderim yardımcıları
    // ------------------------------------------------------------------

    /** Sunucu → tek oyuncu (S2C) gönderim. */
    private static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        channel().send(payload, PacketDistributor.PLAYER.with(player));
    }

    /**
     * İstemci → sunucu (C2S) gönderim — YALNIZCA istemci tarafından çağrılır
     * (ekranlar, HUD tuşları, MouseHandler mixin'i). Kökteki
     * {@code ClientPlayNetworking.send(payload)} çağrılarının yerine geçer.
     */
    public static void sendToServer(CustomPacketPayload payload) {
        channel().send(payload, PacketDistributor.SERVER.noArg());
    }

    // ------------------------------------------------------------------
    // Sunucu tarafı gönderimler — İMZALAR KÖKLE AYNI
    // ------------------------------------------------------------------

    /** Oyuncunun bildiği büyü listesini istemcisine gönderir (giriş + öğrenme sonrası). */
    public static void syncKnownSpells(ServerPlayer player) {
        List<String> ids = new ArrayList<>(
                ArcanumPlayerData.get(player.serverLevel().getServer()).knownOf(player));
        sendToPlayer(player, new KnownSpellsPayload(ids));
    }

    /**
     * Oyuncunun büyü dizilimini (12 slot + aktif sayfa/slot) istemcisine gönderir —
     * giriş, slot atama ve aktif slot değişiminden sonra çağrılır. HUD + menü buna
     * göre çizilir.
     */
    public static void syncLoadout(ServerPlayer player) {
        ArcanumPlayerData data = ArcanumPlayerData.get(player.serverLevel().getServer());
        sendToPlayer(player, new SpellLoadoutPayload(
                data.getLoadout(player), data.getActivePage(player), data.getActiveSlot(player)));
    }

    /**
     * Cast (büyü yapma) durumunu istemciye gönderir — cast START'ında
     * ({@code spellIndex}=büyü global index'i, {@code totalTicks}=cast süresi) ve
     * cast ATEŞ/İPTAL'inde ({@code spellIndex}=-1, {@code totalTicks}=0). İstemci
     * imleç altı minik cast göstergesini yalnızca bu pakete göre çizer/temizler.
     */
    public static void syncCastState(ServerPlayer player, int spellIndex, int totalTicks) {
        sendToPlayer(player, new CastStatePayload(spellIndex, totalTicks));
    }

    /**
     * ASA KENETLENMESİ durumunu tek bir oyuncuya gönderir (kenetlenmenin HER iki tarafına
     * ayrı ayrı çağrılır). {@code phase}: 0 = başladı/güncel · 1 = bitti, kazanan A ·
     * 2 = bitti, kazanan B · 3 = iptal. İstemci ışını/düğümü/HUD'u buna göre çizer/temizler.
     */
    public static void sendLock(ServerPlayer player, DuelLock l, int phase) {
        sendToPlayer(player, new LockStatePayload(l.a, l.b, l.node, l.colorA, l.colorB, phase));
    }

    /**
     * Bir büyü ışını olayını (yıldırım-tarzı, istemcide çizilir) iki ucun 128 blok
     * çevresindeki TÜM oyunculara yayınlar. Kast başına tek paket — eski adım-başına
     * sendParticles seline kıyasla hem ağ hem görsel olarak çok daha iyi.
     * {@code spread<=0} → istemci mesafeye göre otomatik seçer.
     */
    public static void sendBeam(net.minecraft.server.level.ServerLevel level,
                                net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to,
                                int color, int life, float width, float spread) {
        SpellBeamPayload payload =
                new SpellBeamPayload(from.x, from.y, from.z, to.x, to.y, to.z, color, life, width, spread);
        double r2 = 128.0 * 128.0;
        for (ServerPlayer p : level.players()) {
            double d2 = Math.min(p.distanceToSqr(from.x, from.y, from.z),
                    p.distanceToSqr(to.x, to.y, to.z));
            if (d2 <= r2) {
                sendToPlayer(p, payload);
            }
        }
    }

    /**
     * UMBRAVOLO kara duman formu durumunu TÜM çevrimiçi oyunculara yayınlar (form
     * girişi/çıkışı + periyodik tazeleme). Entity id sunucu genelinde benzersizdir;
     * izleyen her istemci ClientUmbraForms setini buna göre günceller (zırh/eldeki
     * eşya mixin gizlemesi çok-oyunculuda da doğru çalışsın diye herkese gider).
     * İmza kökle aynı ({@code server} parametresi Forge'da PacketDistributor.ALL
     * ile gerekmez ama çağıran common kodu — UmbraFormManager — değişmesin diye korunur).
     */
    public static void sendUmbraForm(net.minecraft.server.MinecraftServer server,
                                     int entityId, boolean active) {
        channel().send(new UmbraFormPayload(entityId, active), PacketDistributor.ALL.noArg());
    }

    /**
     * UMBRAVOLO form durumunu TEK izleyiciye gönderir (unicast) — JOIN handler'ı geç
     * katılan izleyiciye o an formda olan oyuncuların setini herkese yeniden yayın
     * yapmadan iletmek için kullanır.
     */
    public static void sendUmbraFormTo(ServerPlayer viewer, int entityId, boolean active) {
        sendToPlayer(viewer, new UmbraFormPayload(entityId, active));
    }

    /**
     * Oyuncunun BÜYÜCÜ verisini (level/xp + skill düğümleri + mana) istemcisine gönderir.
     * ManaHud (hep görünür) + skill ağacı paneli bunu okur. XP kazanımı, puan harcama, mana
     * değişimi (regen/cast) sonrası çağrılır.
     */
    public static void syncMagicData(ServerPlayer player) {
        ArcanumPlayerData d = ArcanumPlayerData.get(player.serverLevel().getServer());
        int[] nodes = {d.nodes(player, 0), d.nodes(player, 1), d.nodes(player, 2), d.nodes(player, 3)};
        sendToPlayer(player, new MagicDataPayload(
                d.getLevel(player), d.getXp(player), ArcanumPlayerData.xpToNext(d.getLevel(player)),
                d.getMana(player), d.getManaCap(player), nodes,
                ArcanumPlayerData.maxLevel(), ArcanumPlayerData.pointsPerLevel()));
    }

    /**
     * Oyuncu YENİ bir büyücü seviyesine ulaştığında (yalnızca gerçek seviye atlamada)
     * istemciye gönderilir → istemci sağ-üstte "Seviye Atladın" toast bildirimi çizer.
     */
    public static void sendLevelUp(ServerPlayer player, int newLevel) {
        sendToPlayer(player, new LevelUpPayload(newLevel));
    }

    /**
     * Büyü Masası'ndan gelen {@link LearnSpellPayload} C2S isteğini işler — TAM
     * doğrulama {@link SpellGating#attemptLearn} içinde yapılır (server-authoritative,
     * istemci yalnızca hedef spellId önerir). Kitap/reagent oyuncunun açık
     * {@link SpellTableMenu}'sünün GERÇEK slot stack'lerinden okunur — menü yoksa
     * (bayat/sahte paket) sessizce yok sayılır, sunucu ASLA crash olmaz. Başarılıysa
     * öğrenme VFX'i (SpellBookItem ile aynı görsel dil) + known-spells resync;
     * başarısızsa ret mesajı + "denied" sesi. Bu metot çağrıldığında zaten sunucu ana
     * thread'inde olunmalıdır — {@link #register()} içindeki alıcı bunu
     * {@code consumerMainThread} ile garanti eder.
     */
    public static void handleLearnSpell(ServerPlayer player, String spellId) {
        if (!(player.containerMenu instanceof SpellTableMenu menu)) {
            return;
        }
        applyLearnResult(player, SpellGating.attemptLearn(player.getServer(), player, spellId,
                menu.getBookStack(), menu.getReagentContainer()));
    }

    /** Bir öğrenme denemesinin sonucunu VFX/mesaj/ses geri bildirimine çevirir. */
    private static void applyLearnResult(ServerPlayer player, SpellGating.LearnResult result) {
        if (result.message() == null) {
            // bozuk/bayat istemci paketi — sessizce yok say (spesifikasyon §7, madde 1)
            return;
        }

        // GUI açıkken vanilla chat/actionbar görünmez (ekranın opak arka planı
        // üstüne çiziyor) — bu yüzden SpellTableScreen'in kendi içinde
        // gösterebileceği ayrı bir bildirim paketi de gönderilir.
        sendToPlayer(player, new SpellTableFeedbackPayload(result.success(), result.message()));

        if (result.success()) {
            syncKnownSpells(player);
            int color = result.spell() != null ? result.spell().color() : 0xFFFFFF;
            var server = player.serverLevel();
            SpellFx.helix(server, player, color, 2.6, 30);
            SpellFx.runeRing(server, player.position(), color, 10, 1.4);
            SpellFx.nova(server, player.position(), color, 20, 0.2);
            SpellFx.soundAt(server, player, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
            SpellFx.soundAt(server, player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.6f);
            player.displayClientMessage(result.message().copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        } else {
            player.displayClientMessage(result.message().copy().withStyle(ChatFormatting.RED), true);
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 0.8f);
        }
    }
}
