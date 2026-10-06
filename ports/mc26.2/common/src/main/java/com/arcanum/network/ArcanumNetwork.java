package com.arcanum.network;

import java.util.ArrayList;
import java.util.List;

import com.arcanum.data.ArcanumPlayerData;
import com.arcanum.item.WandItem;
import com.arcanum.menu.SpellTableMenu;
import com.arcanum.platform.Platform;
import com.arcanum.platform.PlatformNet;
import com.arcanum.registry.ModComponents;
import com.arcanum.spell.DuelLock;
import com.arcanum.spell.ModSpells;
import com.arcanum.spell.Spell;
import com.arcanum.spell.SpellFx;
import com.arcanum.spell.SpellGating;
import com.arcanum.spell.WandLockManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Loader-bağımsız paket kaydı + gönderimi. Payload tip/codec kayıtları ve C2S alıcıları
 * {@link #registerPayloads()} ile {@code Platform.net()} üzerinden yapılır (Fabric:
 * PayloadTypeRegistry + ServerPlayNetworking; Forge: SimpleChannel "arcanum:main").
 * Gönderim {@code Platform.net().sendToPlayer/sendToAll/sendToServer} ile yapılır.
 */
public final class ArcanumNetwork {
    private ArcanumNetwork() {}

    /**
     * TÜM payload'ları bildirir + C2S sunucu alıcılarını bağlar. {@code Arcanum.init()} içinden,
     * iki fiziksel tarafta da çağrılır. <b>SIRA SÖZLEŞMEDİR</b> (Forge discriminator'ı çağrı
     * sırasından türer) — kökteki ArcanumFabric PayloadTypeRegistry sırasıyla birebir aynı.
     * Alıcılar {@link PlatformNet.ServerReceiver} sözleşmesi gereği zaten SUNUCU ANA THREAD'inde
     * çalışır (kökteki {@code player.getServer().execute(...)} sıçraması platform katmanında).
     */
    public static void registerPayloads() {
        PlatformNet net = Platform.net();

        // büyü seçim C2S paketi + bilinen büyü S2C senkronu
        net.registerC2S(SelectSpellPayload.TYPE, SelectSpellPayload.CODEC, (payload, player) -> {
            int idx = payload.index();
            int clamped = Math.max(0, Math.min(idx, ModSpells.SPELLS.size() - 1));
            ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
            ItemStack wand = main.getItem() instanceof WandItem ? main
                    : (off.getItem() instanceof WandItem ? off : null);
            if (wand != null) {
                wand.set(ModComponents.SELECTED_SPELL.get(), clamped);
            }
        });
        // Büyü Masası öğrenme isteği: TÜM doğrulama sunucu tarafında SpellGating'de
        // yapılır — alıcı ana sunucu thread'inde çalışır (paket network thread'inde
        // gelir, envanter/XP mutasyonu asla oradan yapılmamalı; hop platform katmanında).
        net.registerC2S(LearnSpellPayload.TYPE, LearnSpellPayload.CODEC,
                (payload, player) -> handleLearnSpell(player, payload.spellId()));
        net.registerS2C(KnownSpellsPayload.TYPE, KnownSpellsPayload.CODEC);
        net.registerS2C(SpellTableFeedbackPayload.TYPE, SpellTableFeedbackPayload.CODEC);

        // büyü dizilimi (loadout): S2C senkron + C2S atama/aktif-değiştir
        net.registerS2C(SpellLoadoutPayload.TYPE, SpellLoadoutPayload.CODEC);
        // Dizilim: slota büyü ata/temizle (C2S). Sunucu-yetkili — spellIndex>=0 ise büyü
        // gerçekten bilinmeli (SpellGating.knows), yoksa atama YAPMA.
        net.registerC2S(AssignSlotPayload.TYPE, AssignSlotPayload.CODEC, (payload, player) -> {
            int page = payload.page();
            int slot = payload.slot();
            int spellIndex = payload.spellIndex();
            if (page < 0 || page >= ArcanumPlayerData.PAGES
                    || slot < 0 || slot >= ArcanumPlayerData.SLOTS) {
                return; // geçersiz konum — sessizce yok say
            }
            var server = player.level().getServer();
            ArcanumPlayerData data = ArcanumPlayerData.get(server);
            if (spellIndex < 0) {
                data.setSlot(player, page, slot, -1); // temizle
            } else if (spellIndex < ModSpells.SPELLS.size()) {
                Spell spell = ModSpells.SPELLS.get(spellIndex);
                if (!SpellGating.knows(server, player, spell)) {
                    return; // bilinmeyen büyü — atama yok, resync gereksiz
                }
                data.setSlot(player, page, slot, spellIndex);
            } else {
                return; // aralık dışı index
            }
            syncLoadout(player);
        });
        // Dizilim: aktif sayfa/slot değiştir (C2S). +/- sayfa, Shift+tekerlek slot.
        net.registerC2S(SetActivePayload.TYPE, SetActivePayload.CODEC, (payload, player) -> {
            ArcanumPlayerData.get(player.level().getServer()).setActive(player, payload.page(), payload.slot());
            syncLoadout(player);
        });

        // cast durumu S2C (imleç altı minik cast göstergesi — CastBarHud buna göre çizer)
        net.registerS2C(CastStatePayload.TYPE, CastStatePayload.CODEC);
        net.registerS2C(SpellBeamPayload.TYPE, SpellBeamPayload.CODEC);

        // asa kenetlenmesi (Priori Incantatem): S2C durum + C2S tık ("asayı it")
        net.registerS2C(LockStatePayload.TYPE, LockStatePayload.CODEC);
        // Asa kenetlenmesi: sol-tık "asayı it" sinyali (C2S). Tık SAYIMI ve CPS TAVANI (18)
        // otoritesi sunucuda (WandLockManager); kilit yoksa sessizce yok sayılır.
        net.registerC2S(LockPushPayload.TYPE, LockPushPayload.CODEC, (payload, player) ->
                WandLockManager.registerClick(player.getUUID(), player.level().getGameTime()));

        // büyücü level/skill/mana: S2C veri senkron + C2S puan harca/respec
        net.registerS2C(MagicDataPayload.TYPE, MagicDataPayload.CODEC);
        // Skill ağacı: puan harca (server-authoritative doğrulama) + respec (ücretsiz).
        net.registerC2S(SpendPointPayload.TYPE, SpendPointPayload.CODEC, (payload, player) -> {
            int track = payload.track();
            if (track < 0 || track > 3) {
                return;
            }
            // Redde de sync gönder: bayat istemci (ör. runtime'da düşürülen config)
            // "satın alınabilir" görünümünde takılı kalmasın — sunucu-otoriter, zararsız.
            ArcanumPlayerData.get(player.level().getServer()).trySpendPoint(player, track);
            syncMagicData(player);
        });
        net.registerC2S(RespecPayload.TYPE, RespecPayload.CODEC, (payload, player) -> {
            ArcanumPlayerData.get(player.level().getServer()).respec(player);
            syncMagicData(player);
        });

        // seviye atlama olayı S2C → istemci "Seviye Atladın" toast bildirimi çizer
        net.registerS2C(LevelUpPayload.TYPE, LevelUpPayload.CODEC);
        // Umbravolo kara duman formu S2C (entityId+aktif) → istemci zırh/eldeki-eşya gizleme seti
        net.registerS2C(UmbraFormPayload.TYPE, UmbraFormPayload.CODEC);
    }

    /**
     * Yalnız istemci: bir C2S payload'ı sunucuya gönderir (kökteki
     * {@code ClientPlayNetworking.send(payload)} çağrılarının yerine geçer).
     */
    public static void sendToServer(CustomPacketPayload payload) {
        Platform.net().sendToServer(payload);
    }

    /** Oyuncunun bildiği büyü listesini istemcisine gönderir (giriş + öğrenme sonrası). */
    public static void syncKnownSpells(ServerPlayer player) {
        List<String> ids = new ArrayList<>(
                ArcanumPlayerData.get(player.level().getServer()).knownOf(player));
        Platform.net().sendToPlayer(player, new KnownSpellsPayload(ids));
    }

    /**
     * Oyuncunun büyü dizilimini (12 slot + aktif sayfa/slot) istemcisine gönderir —
     * giriş, slot atama ve aktif slot değişiminden sonra çağrılır. HUD + menü buna
     * göre çizilir.
     */
    public static void syncLoadout(ServerPlayer player) {
        ArcanumPlayerData data = ArcanumPlayerData.get(player.level().getServer());
        Platform.net().sendToPlayer(player, new SpellLoadoutPayload(
                data.getLoadout(player), data.getActivePage(player), data.getActiveSlot(player)));
    }

    /**
     * Cast (büyü yapma) durumunu istemciye gönderir — cast START'ında
     * ({@code spellIndex}=büyü global index'i, {@code totalTicks}=cast süresi) ve
     * cast ATEŞ/İPTAL'inde ({@code spellIndex}=-1, {@code totalTicks}=0). İstemci
     * imleç altı minik cast göstergesini yalnızca bu pakete göre çizer/temizler.
     */
    public static void syncCastState(ServerPlayer player, int spellIndex, int totalTicks) {
        Platform.net().sendToPlayer(player, new CastStatePayload(spellIndex, totalTicks));
    }

    /**
     * ASA KENETLENMESİ durumunu tek bir oyuncuya gönderir (kenetlenmenin HER iki tarafına
     * ayrı ayrı çağrılır). {@code phase}: 0 = başladı/güncel · 1 = bitti, kazanan A ·
     * 2 = bitti, kazanan B · 3 = iptal. İstemci ışını/düğümü/HUD'u buna göre çizer/temizler.
     */
    public static void sendLock(ServerPlayer player, DuelLock l, int phase) {
        Platform.net().sendToPlayer(player,
                new LockStatePayload(l.a, l.b, l.node, l.colorA, l.colorB, phase));
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
        SpellBeamPayload pkt =
                new SpellBeamPayload(from.x, from.y, from.z, to.x, to.y, to.z, color, life, width, spread);
        double r2 = 128.0 * 128.0;
        for (ServerPlayer p : level.players()) {
            double d2 = Math.min(p.distanceToSqr(from.x, from.y, from.z),
                    p.distanceToSqr(to.x, to.y, to.z));
            if (d2 <= r2) {
                Platform.net().sendToPlayer(p, pkt);
            }
        }
    }

    /**
     * Oyuncunun BÜYÜCÜ verisini (level/xp + skill düğümleri + mana) istemcisine gönderir.
     * ManaHud (hep görünür) + skill ağacı paneli bunu okur. XP kazanımı, puan harcama, mana
     * değişimi (regen/cast) sonrası çağrılır.
     */
    public static void syncMagicData(ServerPlayer player) {
        ArcanumPlayerData d = ArcanumPlayerData.get(player.level().getServer());
        int[] nodes = {d.nodes(player, 0), d.nodes(player, 1), d.nodes(player, 2), d.nodes(player, 3)};
        Platform.net().sendToPlayer(player, new MagicDataPayload(
                d.getLevel(player), d.getXp(player), ArcanumPlayerData.xpToNext(d.getLevel(player)),
                d.getMana(player), d.getManaCap(player), nodes,
                ArcanumPlayerData.maxLevel(), ArcanumPlayerData.pointsPerLevel()));
    }

    /**
     * UMBRAVOLO kara duman formu durumunu TÜM çevrimiçi oyunculara yayınlar (form
     * girişi/çıkışı + periyodik tazeleme). Entity id sunucu genelinde benzersizdir;
     * izleyen her istemci ClientUmbraForms setini buna göre günceller (zırh/eldeki
     * eşya mixin gizlemesi çok-oyunculuda da doğru çalışsın diye herkese gider).
     */
    public static void sendUmbraForm(net.minecraft.server.MinecraftServer server,
                                     int entityId, boolean active) {
        Platform.net().sendToAll(server, new UmbraFormPayload(entityId, active));
    }

    /**
     * UMBRAVOLO form durumunu TEK izleyiciye gönderir (unicast) — JOIN handler'ı geç
     * katılan oyuncuya o an aktif formların setini periyodik tazelemeyi beklemeden
     * iletir (aksi halde 5 sn'ye kadar "havada süzülen zırh+asa" görünürdü).
     */
    public static void sendUmbraFormTo(ServerPlayer viewer, int entityId, boolean active) {
        Platform.net().sendToPlayer(viewer, new UmbraFormPayload(entityId, active));
    }

    /**
     * Oyuncu YENİ bir büyücü seviyesine ulaştığında (yalnızca gerçek seviye atlamada)
     * istemciye gönderilir → istemci sağ-üstte "Seviye Atladın" toast bildirimi çizer.
     */
    public static void sendLevelUp(ServerPlayer player, int newLevel) {
        Platform.net().sendToPlayer(player, new LevelUpPayload(newLevel));
    }

    /**
     * Büyü Masası'ndan gelen {@link LearnSpellPayload} C2S isteğini işler — TAM
     * doğrulama {@link SpellGating#attemptLearn} içinde yapılır (server-authoritative,
     * istemci yalnızca hedef spellId önerir). Kitap/reagent artık oyuncunun açık
     * {@link SpellTableMenu}'sünün GERÇEK slot stack'lerinden okunur — menü yoksa
     * (bayat/sahte paket) sessizce yok sayılır, sunucu ASLA crash olmaz. Başarılıysa
     * öğrenme VFX'i (SpellBookItem ile aynı görsel dil) + known-spells resync;
     * başarısızsa ret mesajı + "denied" sesi. Bu metot çağrıldığında zaten sunucu ana
     * thread'inde olunmalıdır — {@link #registerPayloads()} içindeki C2S alıcısı bunu
     * garanti eder (PlatformNet.ServerReceiver sözleşmesi).
     */
    public static void handleLearnSpell(ServerPlayer player, String spellId) {
        if (!(player.containerMenu instanceof SpellTableMenu menu)) {
            return;
        }
        applyLearnResult(player, SpellGating.attemptLearn(player.level().getServer(), player, spellId,
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
        Platform.net().sendToPlayer(player,
                new SpellTableFeedbackPayload(result.success(), result.message()));

        if (result.success()) {
            syncKnownSpells(player);
            int color = result.spell() != null ? result.spell().color() : 0xFFFFFF;
            var server = player.level();
            SpellFx.helix(server, player, color, 2.6, 30);
            SpellFx.runeRing(server, player.position(), color, 10, 1.4);
            SpellFx.nova(server, player.position(), color, 20, 0.2);
            SpellFx.soundAt(server, player, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
            SpellFx.soundAt(server, player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.6f);
            // kök: displayClientMessage(msg, false) → 26.1.2: sendSystemMessage(msg) (sohbet)
            player.sendSystemMessage(result.message().copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else {
            // kök: displayClientMessage(msg, true) → 26.1.2: sendOverlayMessage(msg) (actionbar)
            player.sendOverlayMessage(result.message().copy().withStyle(ChatFormatting.RED));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 0.8f);
        }
    }
}
