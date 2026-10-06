package com.arcanum.network;

import java.util.ArrayList;
import java.util.List;

import com.arcanum.data.ArcanumPlayerData;
import com.arcanum.menu.SpellTableMenu;
import com.arcanum.spell.DuelLock;
import com.arcanum.spell.SpellFx;
import com.arcanum.spell.SpellGating;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Loader-bağımsız paket gönderimi. Payload tip kayıtları loader tarafında
 * (Fabric: PayloadTypeRegistry) yapılır; gönderim vanilla paket ile yapılır.
 */
public final class ArcanumNetwork {
    private ArcanumNetwork() {}

    /** Oyuncunun bildiği büyü listesini istemcisine gönderir (giriş + öğrenme sonrası). */
    public static void syncKnownSpells(ServerPlayer player) {
        List<String> ids = new ArrayList<>(
                ArcanumPlayerData.get(player.serverLevel().getServer()).knownOf(player));
        player.connection.send(new ClientboundCustomPayloadPacket(new KnownSpellsPayload(ids)));
    }

    /**
     * Oyuncunun büyü dizilimini (12 slot + aktif sayfa/slot) istemcisine gönderir —
     * giriş, slot atama ve aktif slot değişiminden sonra çağrılır. HUD + menü buna
     * göre çizilir.
     */
    public static void syncLoadout(ServerPlayer player) {
        ArcanumPlayerData data = ArcanumPlayerData.get(player.serverLevel().getServer());
        player.connection.send(new ClientboundCustomPayloadPacket(new SpellLoadoutPayload(
                data.getLoadout(player), data.getActivePage(player), data.getActiveSlot(player))));
    }

    /**
     * Cast (büyü yapma) durumunu istemciye gönderir — cast START'ında
     * ({@code spellIndex}=büyü global index'i, {@code totalTicks}=cast süresi) ve
     * cast ATEŞ/İPTAL'inde ({@code spellIndex}=-1, {@code totalTicks}=0). İstemci
     * imleç altı minik cast göstergesini yalnızca bu pakete göre çizer/temizler.
     */
    public static void syncCastState(ServerPlayer player, int spellIndex, int totalTicks) {
        player.connection.send(new ClientboundCustomPayloadPacket(
                new CastStatePayload(spellIndex, totalTicks)));
    }

    /**
     * ASA KENETLENMESİ durumunu tek bir oyuncuya gönderir (kenetlenmenin HER iki tarafına
     * ayrı ayrı çağrılır). {@code phase}: 0 = başladı/güncel · 1 = bitti, kazanan A ·
     * 2 = bitti, kazanan B · 3 = iptal. İstemci ışını/düğümü/HUD'u buna göre çizer/temizler.
     */
    public static void sendLock(ServerPlayer player, DuelLock l, int phase) {
        player.connection.send(new ClientboundCustomPayloadPacket(
                new LockStatePayload(l.a, l.b, l.node, l.colorA, l.colorB, phase)));
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
        ClientboundCustomPayloadPacket pkt = new ClientboundCustomPayloadPacket(
                new SpellBeamPayload(from.x, from.y, from.z, to.x, to.y, to.z, color, life, width, spread));
        double r2 = 128.0 * 128.0;
        for (ServerPlayer p : level.players()) {
            double d2 = Math.min(p.distanceToSqr(from.x, from.y, from.z),
                    p.distanceToSqr(to.x, to.y, to.z));
            if (d2 <= r2) {
                p.connection.send(pkt);
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
        player.connection.send(new ClientboundCustomPayloadPacket(new MagicDataPayload(
                d.getLevel(player), d.getXp(player), ArcanumPlayerData.xpToNext(d.getLevel(player)),
                d.getMana(player), d.getManaCap(player), nodes,
                ArcanumPlayerData.maxLevel(), ArcanumPlayerData.pointsPerLevel())));
    }

    /**
     * UMBRAVOLO kara duman formu durumunu TÜM çevrimiçi oyunculara yayınlar (form
     * girişi/çıkışı + periyodik tazeleme). Entity id sunucu genelinde benzersizdir;
     * izleyen her istemci ClientUmbraForms setini buna göre günceller (zırh/eldeki
     * eşya mixin gizlemesi çok-oyunculuda da doğru çalışsın diye herkese gider).
     */
    public static void sendUmbraForm(net.minecraft.server.MinecraftServer server,
                                     int entityId, boolean active) {
        ClientboundCustomPayloadPacket pkt = new ClientboundCustomPayloadPacket(
                new UmbraFormPayload(entityId, active));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(pkt);
        }
    }

    /**
     * UMBRAVOLO form durumunu TEK izleyiciye gönderir (unicast) — JOIN handler'ı geç
     * katılan oyuncuya o an aktif formların setini periyodik tazelemeyi beklemeden
     * iletir (aksi halde 5 sn'ye kadar "havada süzülen zırh+asa" görünürdü).
     */
    public static void sendUmbraFormTo(ServerPlayer viewer, int entityId, boolean active) {
        viewer.connection.send(new ClientboundCustomPayloadPacket(
                new UmbraFormPayload(entityId, active)));
    }

    /**
     * Oyuncu YENİ bir büyücü seviyesine ulaştığında (yalnızca gerçek seviye atlamada)
     * istemciye gönderilir → istemci sağ-üstte "Seviye Atladın" toast bildirimi çizer.
     */
    public static void sendLevelUp(ServerPlayer player, int newLevel) {
        player.connection.send(new ClientboundCustomPayloadPacket(new LevelUpPayload(newLevel)));
    }

    /**
     * Büyü Masası'ndan gelen {@link LearnSpellPayload} C2S isteğini işler — TAM
     * doğrulama {@link SpellGating#attemptLearn} içinde yapılır (server-authoritative,
     * istemci yalnızca hedef spellId önerir). Kitap/reagent artık oyuncunun açık
     * {@link SpellTableMenu}'sünün GERÇEK slot stack'lerinden okunur — menü yoksa
     * (bayat/sahte paket) sessizce yok sayılır, sunucu ASLA crash olmaz. Başarılıysa
     * öğrenme VFX'i (SpellBookItem ile aynı görsel dil) + known-spells resync;
     * başarısızsa ret mesajı + "denied" sesi. Bu metot çağrıldığında zaten sunucu ana
     * thread'inde (server.execute) olunmalıdır — ServerPlayNetworking receiver'ı bunu
     * garanti eder (bkz. ArcanumFabric kaydı).
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
        player.connection.send(new ClientboundCustomPayloadPacket(
                new SpellTableFeedbackPayload(result.success(), result.message())));

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
