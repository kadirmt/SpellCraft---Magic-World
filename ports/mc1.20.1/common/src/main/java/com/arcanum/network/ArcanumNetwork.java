package com.arcanum.network;

import java.util.ArrayList;
import java.util.List;

import com.arcanum.data.ArcanumPlayerData;
import com.arcanum.menu.SpellTableMenu;
import com.arcanum.item.WandItem;
import com.arcanum.spell.DuelLock;
import com.arcanum.spell.ModSpells;
import com.arcanum.spell.Spell;
import com.arcanum.spell.SpellFx;
import com.arcanum.spell.SpellGating;
import com.arcanum.spell.WandLockManager;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Loader-bağımsız ağ katmanı (1.20.1 PORTU — Architectury {@code NetworkManager} 9.2.14,
 * FriendlyByteBuf tabanlı).
 *
 * <p>1.21.1'deki {@code CustomPacketPayload}+{@code StreamCodec}+{@code PayloadTypeRegistry}
 * hattı 1.20.1'de yok; onların yerine her payload elle {@code encode}/{@code decode}
 * ({@link ArcanumPayload}) kazandı ve TÜM kayıt/gönderim burada, common'da toplandı.
 * Fabric ve Forge modülleri SIFIR ek ağ kodu yazar; yalnızca:
 *
 * <ul>
 *   <li>ORTAK init'ten (her iki fiziksel taraf): {@link #registerC2SReceivers()};</li>
 *   <li>CLIENT init'ten (yalnızca istemci): önce {@link ArcanumClientHooks#setHandler},
 *       sonra {@link #registerS2CReceivers()} çağırır.</li>
 * </ul>
 *
 * <p>Gönderim metodlarının (send* / sync*) imzaları 1.21.1 ile AYNEN aynı — çağıran
 * sunucu tarafı dosyalar değişmez. İstemci → sunucu gönderimi için ekranlar/mixin'ler
 * {@link #sendToServer(ArcanumPayload)} kullanır (eski {@code ClientPlayNetworking.send}
 * çağrılarının birebir karşılığı).
 */
public final class ArcanumNetwork {
    private ArcanumNetwork() {}

    // ------------------------------------------------------------------
    // Gönderim yardımcıları (Architectury NetworkManager, FriendlyByteBuf)
    // ------------------------------------------------------------------

    /** Payload'u yeni bir buffer'a yazar (NetworkManager gönderimde sahipliği devralır). */
    private static FriendlyByteBuf toBuf(ArcanumPayload payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        payload.encode(buf);
        return buf;
    }

    /** Sunucu → tek oyuncu (S2C) gönderim. */
    private static void sendToPlayer(ServerPlayer player, ArcanumPayload payload) {
        NetworkManager.sendToPlayer(player, payload.id(), toBuf(payload));
    }

    /**
     * İstemci → sunucu (C2S) gönderim — YALNIZCA istemci tarafından çağrılır
     * (ekranlar, HUD tuşları, MouseHandler mixin'i). 1.21.1'deki
     * {@code ClientPlayNetworking.send(payload)} çağrılarının yerine geçer.
     */
    public static void sendToServer(ArcanumPayload payload) {
        NetworkManager.sendToServer(payload.id(), toBuf(payload));
    }

    // ------------------------------------------------------------------
    // Sunucu tarafı gönderimler — İMZALAR 1.21.1 İLE AYNI
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
     *
     * <p>1.20.1 PORT: her alıcı için {@code sendToPlayer} taze bir buffer yazar
     * (NetworkManager gönderimde buffer sahipliğini devraldığından paylaşım güvensiz).
     */
    public static void sendBeam(net.minecraft.server.level.ServerLevel level,
                                net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to,
                                int color, int life, float width, float spread) {
        SpellBeamPayload payload = new SpellBeamPayload(
                from.x, from.y, from.z, to.x, to.y, to.z, color, life, width, spread);
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
     * UMBRAVOLO kara duman formu durumunu TÜM çevrimiçi oyunculara yayınlar —
     * izleyen her istemci ClientUmbraForms setini buna göre günceller (zırh/eldeki
     * eşya mixin gizlemesi çok-oyunculuda da doğru çalışsın diye herkese gider).
     * 1.20.1 PORT: her alıcıya taze buffer yazılır ({@code sendToPlayer} zaten öyle
     * yapar — NetworkManager gönderimde buffer sahipliğini devralır).
     */
    public static void sendUmbraForm(net.minecraft.server.MinecraftServer server,
                                     int entityId, boolean active) {
        UmbraFormPayload payload = new UmbraFormPayload(entityId, active);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sendToPlayer(p, payload);
        }
    }

    /**
     * UMBRAVOLO form durumunu TEK izleyiciye gönderir (unicast) — JOIN handler'ı geç
     * katılan oyuncuya o an aktif formların setini periyodik tazelemeyi beklemeden
     * iletir (aksi halde 5 sn'ye kadar "havada süzülen zırh+asa" görünürdü).
     */
    public static void sendUmbraFormTo(ServerPlayer viewer, int entityId, boolean active) {
        sendToPlayer(viewer, new UmbraFormPayload(entityId, active));
    }

    /**
     * Büyü Masası'ndan gelen {@link LearnSpellPayload} C2S isteğini işler — TAM
     * doğrulama {@link SpellGating#attemptLearn} içinde yapılır (server-authoritative,
     * istemci yalnızca hedef spellId önerir). Kitap/reagent artık oyuncunun açık
     * {@link SpellTableMenu}'sünün GERÇEK slot stack'lerinden okunur — menü yoksa
     * (bayat/sahte paket) sessizce yok sayılır, sunucu ASLA crash olmaz. Başarılıysa
     * öğrenme VFX'i (SpellBookItem ile aynı görsel dil) + known-spells resync;
     * başarısızsa ret mesajı + "denied" sesi. Bu metot çağrıldığında zaten sunucu ana
     * thread'inde olunmalıdır — {@link #registerC2SReceivers()} içindeki alıcı bunu
     * {@code ctx.queue(...)} ile garanti eder.
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

    // ------------------------------------------------------------------
    // C2S alıcı kayıtları — ORTAK init'ten çağrılır (fabric + forge)
    // ------------------------------------------------------------------

    /**
     * TÜM istemci → sunucu paket alıcılarını kaydeder. Loader'ların ANA init'inden
     * (Fabric: {@code ArcanumFabric.onInitialize}, Forge: mod constructor/commonSetup)
     * {@code Arcanum.init()} sonrasında BİR KEZ çağrılır — her iki fiziksel tarafta da
     * güvenlidir (Architectury C2S kaydını uygun tarafa bağlar).
     *
     * <p>Gövdeler 1.21.1'de fabric modülündeki {@code ServerPlayNetworking} kayıtlarının
     * BİREBİR taşınmış halidir. Paketler network thread'inde gelir; alanlar orada çözülür
     * (buffer alıcı dönünce serbest bırakılır), oyun mutasyonu {@code ctx.queue(...)} ile
     * ana sunucu thread'ine aktarılır (eski {@code player.getServer().execute} kalıbının
     * Architectury karşılığı).
     */
    public static void registerC2SReceivers() {
        // Radyal menü: seçilen büyü index'i → eldeki asanın NBT'sine (doğrulayarak).
        // 1.20.1 portu: DataComponent SELECTED_SPELL yerine ModComponents facade'ı —
        // tek kanonik NBT anahtarı ModComponents.KEY_SELECTED_SPELL (f3-core-contracts §1);
        // ham anahtar yazma, facade ile çakışır.
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, SelectSpellPayload.ID, (buf, ctx) -> {
            SelectSpellPayload payload = SelectSpellPayload.decode(buf);
            ctx.queue(() -> {
                ServerPlayer player = (ServerPlayer) ctx.getPlayer();
                int clamped = Math.max(0, Math.min(payload.index(), ModSpells.SPELLS.size() - 1));
                ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
                ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
                ItemStack wand = main.getItem() instanceof WandItem ? main
                        : (off.getItem() instanceof WandItem ? off : null);
                if (wand != null) {
                    com.arcanum.registry.ModComponents.setSelectedSpell(wand, clamped);
                }
            });
        });

        // Büyü Masası öğrenme isteği: TÜM doğrulama sunucu tarafında SpellGating'de
        // yapılır — burada yalnızca ana sunucu thread'ine geçiş var (paket network
        // thread'inde gelir, envanter/XP mutasyonu asla oradan yapılmamalı).
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, LearnSpellPayload.ID, (buf, ctx) -> {
            LearnSpellPayload payload = LearnSpellPayload.decode(buf);
            ctx.queue(() -> handleLearnSpell((ServerPlayer) ctx.getPlayer(), payload.spellId()));
        });

        // Dizilim: slota büyü ata/temizle (C2S). Sunucu-yetkili — spellIndex>=0 ise büyü
        // gerçekten bilinmeli (SpellGating.knows), yoksa atama YAPMA. Ana thread'e geç.
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, AssignSlotPayload.ID, (buf, ctx) -> {
            AssignSlotPayload payload = AssignSlotPayload.decode(buf);
            ctx.queue(() -> {
                ServerPlayer player = (ServerPlayer) ctx.getPlayer();
                int page = payload.page();
                int slot = payload.slot();
                int spellIndex = payload.spellIndex();
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
            });
        });

        // Dizilim: aktif sayfa/slot değiştir (C2S). +/- sayfa, Shift+tekerlek slot.
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, SetActivePayload.ID, (buf, ctx) -> {
            SetActivePayload payload = SetActivePayload.decode(buf);
            ctx.queue(() -> {
                ServerPlayer player = (ServerPlayer) ctx.getPlayer();
                ArcanumPlayerData.get(player.getServer()).setActive(player, payload.page(), payload.slot());
                syncLoadout(player);
            });
        });

        // Asa kenetlenmesi: sol-tık "asayı it" sinyali (C2S). Tık SAYIMI ve CPS TAVANI (18)
        // otoritesi sunucuda (WandLockManager); ana thread'e geç, kilit yoksa sessizce yok say.
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, LockPushPayload.ID, (buf, ctx) ->
                ctx.queue(() -> {
                    ServerPlayer player = (ServerPlayer) ctx.getPlayer();
                    WandLockManager.registerClick(player.getUUID(), player.level().getGameTime());
                }));

        // Skill ağacı: puan harca (server-authoritative doğrulama) + respec (ücretsiz). Ana thread hop.
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, SpendPointPayload.ID, (buf, ctx) -> {
            SpendPointPayload payload = SpendPointPayload.decode(buf);
            ctx.queue(() -> {
                ServerPlayer player = (ServerPlayer) ctx.getPlayer();
                int track = payload.track();
                if (track < 0 || track > 3) {
                    return;
                }
                // Redde de sync gönder: bayat istemci (ör. runtime'da düşürülen config)
                // "satın alınabilir" görünümünde takılı kalmasın — sunucu-otoriter, zararsız.
                ArcanumPlayerData.get(player.getServer()).trySpendPoint(player, track);
                syncMagicData(player);
            });
        });
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, RespecPayload.ID, (buf, ctx) ->
                ctx.queue(() -> {
                    ServerPlayer player = (ServerPlayer) ctx.getPlayer();
                    ArcanumPlayerData.get(player.getServer()).respec(player);
                    syncMagicData(player);
                }));
    }

    // ------------------------------------------------------------------
    // S2C alıcı kayıtları — YALNIZCA CLIENT init'ten çağrılır
    // ------------------------------------------------------------------

    /**
     * TÜM sunucu → istemci paket alıcılarını kaydeder. YALNIZCA istemci giriş
     * noktasından (Fabric: {@code ArcanumFabricClient.onInitializeClient}, Forge:
     * client setup) çağrılır — dedicated server'da S2C kaydı Architectury'de
     * yasaktır. Çağrıdan ÖNCE {@link ArcanumClientHooks#setHandler} kurulmuş olmalı.
     *
     * <p>Gövdeler 1.21.1'de fabric client'taki {@code ClientPlayNetworking} kayıtlarının
     * taşınmış hali: alanlar network thread'inde çözülür, istemci durumu
     * {@code ctx.queue(...)} ile ana istemci thread'inde {@link ArcanumClientHooks}
     * üzerinden güncellenir (common → client-only sınıf referansı YOK).
     */
    public static void registerS2CReceivers() {
        // Bilinen büyü listesi senkronu
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, KnownSpellsPayload.ID, (buf, ctx) -> {
            KnownSpellsPayload payload = KnownSpellsPayload.decode(buf);
            ctx.queue(() -> {
                ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                if (h != null) {
                    h.setKnownSpells(payload.spellIds());
                }
            });
        });
        // Büyü dizilimi senkronu (12 slot + aktif sayfa/slot)
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, SpellLoadoutPayload.ID, (buf, ctx) -> {
            SpellLoadoutPayload payload = SpellLoadoutPayload.decode(buf);
            ctx.queue(() -> {
                ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                if (h != null) {
                    h.setLoadout(payload.loadout(), payload.activePage(), payload.activeSlot());
                }
            });
        });
        // Cast durumu senkronu (S2C): cast başladı (spellIndex>=0) / bitti-iptal (spellIndex<0).
        // İstemci implementasyonu spellIndex<0 / totalTicks<=0 gelince kendi içinde clear() yapar.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, CastStatePayload.ID, (buf, ctx) -> {
            CastStatePayload payload = CastStatePayload.decode(buf);
            ctx.queue(() -> {
                ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                if (h != null) {
                    h.setCastState(payload.spellIndex(), payload.totalTicks());
                }
            });
        });
        // Asa kenetlenmesi durumu senkronu (S2C): ışın/düğüm/HUD buna göre çizilir/temizlenir.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, LockStatePayload.ID, (buf, ctx) -> {
            LockStatePayload payload = LockStatePayload.decode(buf);
            ctx.queue(() -> {
                ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                if (h != null) {
                    h.setLockState(payload.a(), payload.b(), payload.node(),
                            payload.colorA(), payload.colorB(), payload.phase());
                }
            });
        });
        // Büyü ışını olayı (S2C): yıldırım-tarzı ışın istemcide çizilir, ~0.5 sn iz bırakır.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, SpellBeamPayload.ID, (buf, ctx) -> {
            SpellBeamPayload payload = SpellBeamPayload.decode(buf);
            ctx.queue(() -> {
                ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                if (h != null) {
                    h.addSpellBeam(payload.fromX(), payload.fromY(), payload.fromZ(),
                            payload.toX(), payload.toY(), payload.toZ(),
                            payload.color(), payload.life(), payload.width(), payload.spread());
                }
            });
        });
        // Büyücü verisi (level/xp/skill/mana) senkron — ManaHud + skill paneli buradan okur.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, MagicDataPayload.ID, (buf, ctx) -> {
            MagicDataPayload payload = MagicDataPayload.decode(buf);
            ctx.queue(() -> {
                ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                if (h != null) {
                    h.setMagicData(payload.level(), payload.xp(), payload.xpToNext(),
                            payload.mana(), payload.manaCap(), payload.nodes(),
                            payload.maxLevel(), payload.pointsPerLevel());
                }
            });
        });
        // Seviye atlama olayı → sağ-üstte cilalı toast bildirimi göster.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, LevelUpPayload.ID, (buf, ctx) -> {
            LevelUpPayload payload = LevelUpPayload.decode(buf);
            ctx.queue(() -> {
                ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                if (h != null) {
                    h.showLevelUp(payload.level());
                }
            });
        });
        // Umbravolo kara duman formu (S2C): entityId + aktif/pasif → istemci gizleme seti
        // (ClientUmbraForms). Zırh/eldeki-eşya/elytra/kafa render mixin'leri bu sete bakar.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, UmbraFormPayload.ID, (buf, ctx) -> {
            UmbraFormPayload payload = UmbraFormPayload.decode(buf);
            ctx.queue(() -> {
                ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                if (h != null) {
                    h.setUmbraForm(payload.entityId(), payload.active());
                }
            });
        });
        // Büyü Masası GUI'si açıkken vanilla actionbar/chat görünmez — sonucu
        // doğrudan açık ekrana ilet (ekran kontrolünü client handler yapar).
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, SpellTableFeedbackPayload.ID, (buf, ctx) -> {
            SpellTableFeedbackPayload payload = SpellTableFeedbackPayload.decode(buf);
            ctx.queue(() -> {
                ArcanumClientHooks.Handler h = ArcanumClientHooks.handler();
                if (h != null) {
                    h.showSpellTableFeedback(payload.success(), payload.message());
                }
            });
        });
    }
}
