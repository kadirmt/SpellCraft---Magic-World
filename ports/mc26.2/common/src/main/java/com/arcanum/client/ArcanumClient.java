package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.client.beam.ClientSpellBeams;
import com.arcanum.client.particle.BeamParticle;
import com.arcanum.client.particle.GlowParticle;
import com.arcanum.client.particle.LeafMoteParticle;
import com.arcanum.client.particle.RuneParticle;
import com.arcanum.client.particle.SpellSparkParticle;
import com.arcanum.client.particle.TrailParticle;
import com.arcanum.config.ArcanumConfig;
import com.arcanum.entity.BroomEntity;
import com.arcanum.entity.BroomInput;
import com.arcanum.item.WandItem;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.network.SetActivePayload;
import com.arcanum.platform.Platform;
import com.arcanum.platform.client.PlatformClient;
import com.arcanum.registry.ModComponents;
import com.arcanum.registry.ModEntities;
import com.arcanum.registry.ModMenus;
import com.arcanum.registry.ModParticles;
import com.arcanum.spell.SpellFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Ortak İSTEMCİ girişi (kökteki {@code ArcanumFabricClient#onInitializeClient} davranışı, loader'dan bağımsız).
 * Yalnız loader istemci giriş noktasından çağrılır (dedicated sunucuda bu sınıf YÜKLENMEZ).
 * Sıra: {@code Platform.initClient(clientBackend)} → {@link #init()}. Forge: mod ctor'unda (dist=CLIENT) çağrılır ki
 * backend kuyrukları ilgili olaylardan ÖNCE dolsun — bu yüzden burada kayıt nesnesi {@code .get()} YASAK
 * (yalnız Supplier geçilir; {@code .get()} tick/olay anında).
 *
 * <ul>
 *   <li>Büyü partikül provider'ları (spark/glow/trail/rune/leaf/beam)</li>
 *   <li>Mana HUD + büyü dizilim slotları + cast göstergesi + kenetlenme HUD'u</li>
 *   <li>G tuşu büyü dizilim menüsü, sayfa/slot tuşları ({@link ArcanumKeys})</li>
 *   <li>S2C alıcıları ({@link ArcanumClientPackets}) ve Lumos asa ucu ışıltısı</li>
 * </ul>
 * Loader'a özgü kalanlar (istemci komutu {@code /arcanumclient reloadconfig}, tick/bağlantı-kopması olayları)
 * loader istemci katmanındadır ve buradaki {@link #onClientTickEnd}, {@link #onClientDisconnect},
 * {@link #reloadClientConfig} köprülerini çağırır.
 */
public final class ArcanumClient {
    private ArcanumClient() {}

    public static void init() {
        PlatformClient client = Platform.client();

        // İSTEMCİ-TARAFI CONFIG ISITMASI: ArcanumConfig tembel yüklenir ve dosyayı
        // cwd'ye göreli config/arcanum.json'dan okur — istemci JVM'inde bu .minecraft
        // klasörüdür, yani adanmış sunucuya bağlıyken de OYUNCUNUN KENDİ dosyası okunur
        // (mana barı ölçeği/saydamlığı salt görsel olduğu için senkron gerekmez).
        // Burada bir kez çağırmak dosyayı oyun açılışında oluşturur/okur; aksi hâlde ilk
        // okuma HUD çiziminde, yani render thread'inde disk I/O yapardı.
        ArcanumConfig.get();

        // İstemci-yerel config reload komutu (/arcanumclient reloadconfig) — adanmış
        // sunucuda sunucu komutu istemcinin örneğini tazelemediği için gerekli.
        // Komut ağacı loader'a özgü (Fabric ClientCommands / Forge RegisterClientCommandsEvent):
        // loader istemci katmanı kaydeder, gövde reloadClientConfig()'i çağırır.

        client.registerParticleProvider(ModParticles.SPELL_SPARK, SpellSparkParticle.Provider::new);
        client.registerParticleProvider(ModParticles.SPELL_GLOW, GlowParticle.Provider::new);
        client.registerParticleProvider(ModParticles.SPELL_TRAIL, TrailParticle.Provider::new);
        client.registerParticleProvider(ModParticles.MAGIC_RUNE, RuneParticle.Provider::new);
        client.registerParticleProvider(ModParticles.LEAF_MOTE, LeafMoteParticle.Provider::new);
        client.registerParticleProvider(ModParticles.SPELL_BEAM, BeamParticle.Provider::new);

        // Sol-alt HUD: mana barı + büyü dizilim slotları
        client.registerHudElement(Arcanum.id("mana_hud"), ManaHud::render);
        client.registerHudElement(Arcanum.id("spell_slots_hud"), SpellSlotsHud::render);
        // İmleç altı minik cast göstergesi
        client.registerHudElement(Arcanum.id("cast_bar_hud"), CastBarHud::render);
        // Asa kenetlenmesi tug-of-war HUD'u (crosshair üstü denge çubuğu)
        client.registerHudElement(Arcanum.id("lock_tug_hud"), LockTugHud::render);
        // Asa kenetlenmesi ışın+düğüm + serbest büyü ışınları dünya render'ı: kökteki
        // SpellLockRenderer.register() (WorldRenderEvents) YERİNE ortak LevelRendererMixin
        // (LevelRenderer#submitEntities TAIL) → client.render.ArcanumWorldRender. Burada kayıt YOK.

        // Tüm S2C alıcıları (bilinen büyüler, dizilim, cast, kenetlenme, ışın, büyücü verisi,
        // level-up toast, Umbravolo formu, Büyü Masası geri bildirimi)
        ArcanumClientPackets.register();

        // Arcanewood yaprak/fidan saydamlığı: 26.x'te blok render katmanı doku alfasından
        // otomatik türetilir — kökteki BlockRenderLayerMap çağrılarının karşılığı YOK.

        // Arcanewood botları: kökteki gibi vanilla meşe bot katmanı (ModelLayers.OAK_BOAT / OAK_CHEST_BOAT)
        // + kendi dokusu (ArcanewoodBoatRenderer) — ayrı model katmanı kaydı GEREKMEZ.

        // Yaratık renderer'ları (GeckoLib)
        client.registerEntityRenderer(ModEntities.DEATH_EATER, DeathEaterRenderer::new);
        client.registerEntityRenderer(ModEntities.DEMENTOR, DementorRenderer::new);
        client.registerEntityRenderer(ModEntities.BOWTRUCKLE, BowtruckleRenderer::new);
        client.registerEntityRenderer(ModEntities.MOONCALF, MooncalfRenderer::new);
        client.registerEntityRenderer(ModEntities.BROOM, BroomRenderer::new);
        client.registerEntityRenderer(ModEntities.TROLL, TrollRenderer::new);
        client.registerEntityRenderer(ModEntities.PHOENIX, PhoenixRenderer::new);
        client.registerEntityRenderer(ModEntities.THUNDERBIRD, ThunderbirdRenderer::new);
        client.registerEntityRenderer(ModEntities.SNOWY_OWL, SnowyOwlRenderer::new);
        client.registerEntityRenderer(ModEntities.THESTRAL, ThestralRenderer::new);
        client.registerEntityRenderer(ModEntities.UNICORN, UnicornRenderer::new);
        client.registerEntityRenderer(ModEntities.ARCANEWOOD_BOAT, ArcanewoodBoatRenderer::new);
        client.registerEntityRenderer(ModEntities.ARCANEWOOD_CHEST_BOAT, ArcanewoodChestBoatRenderer::new);
        client.registerEntityRenderer(ModEntities.HIPPOGRIFF, HippogriffRenderer::new);
        client.registerEntityRenderer(ModEntities.ACROMANTULA, AcromantulaRenderer::new);
        client.registerEntityRenderer(ModEntities.WEREWOLF, WerewolfRenderer::new);
        client.registerEntityRenderer(ModEntities.GRINDYLOW, GrindylowRenderer::new);
        client.registerEntityRenderer(ModEntities.KNEAZLE, KneazleRenderer::new);
        client.registerEntityRenderer(ModEntities.BASILISK, BasiliskRenderer::new);
        client.registerEntityRenderer(ModEntities.PATRONUS, PatronusRenderer::new);
        client.registerEntityRenderer(ModEntities.WIZARD_TRADER, WizardTraderRenderer::new);
        client.registerEntityRenderer(ModEntities.DIABOLICA_DRAGON, DiabolicaDragonRenderer::new);

        // Tuşlar: G (dizilim menüsü), numpad -/+ (sayfa), slot 1..4 (atanmamış), tekerlek değiştiricisi
        ArcanumKeys.register();

        // Büyü Masası container GUI'si
        client.registerMenuScreen(ModMenus.SPELL_TABLE_MENU, SpellTableScreen::new);
    }

    // ---- loader olay köprüleri (istemci tarafı) ----

    /** Her istemci tick sonu (Fabric ClientTickEvents.END_CLIENT_TICK / Forge TickEvent.ClientTickEvent.Post). */
    public static void onClientTickEnd(Minecraft mc) {
        // Süpürge drift köprüsü: SPACE (zıplama tuşu) basılı + süpürgedeyken
        // BroomEntity.travel yerel-istemci dalı bu bayrağı okur (BroomInput).
        BroomInput.drifting = mc.player != null
                && mc.player.getVehicle() instanceof BroomEntity
                && mc.options.keyJump.isDown();
        // Asa kenetlenmesi düğümünü pürüzsüzce ilerlet; ışınlar artık partikül değil,
        // SpellLockRenderer'da yıldırım-geometri olarak çizilir (dünya render kancası).
        ClientLockState.tickInterp();
        // Serbest büyü ışını izleri: yaşlandır + süresi dolanları at.
        ClientSpellBeams.tick();
        // G: sürükle-bırak büyü dizilim menüsü + skill ağacı. ASA ŞARTI YOK —
        // skill ağacı/mana asadan bağımsız olduğu için elde asa olmasa da açılır.
        while (ArcanumKeys.spellWheelKey.consumeClick()) {
            LocalPlayer p = mc.player;
            if (p != null) {
                ClientCompat.setScreen(new SpellMenuScreen());
            }
        }
        // +/-: aktif dizilim sayfasını gezin (ekran kapalı + asa elde)
        while (ArcanumKeys.pageNextKey.consumeClick()) {
            changeActivePage(mc, +1);
        }
        while (ArcanumKeys.pagePrevKey.consumeClick()) {
            changeActivePage(mc, -1);
        }
        // Slot 1..4: aktif sayfanın o slotunu doğrudan seç (ekran kapalı + asa elde)
        for (int i = 0; i < ArcanumKeys.spellSlotKeys.length; i++) {
            while (ArcanumKeys.spellSlotKeys[i].consumeClick()) {
                selectSlot(mc, i);
            }
        }
        // Lumos: aktif asa taşıyan görünür oyuncuların asa ucunda ışıltı
        ClientLevel level = mc.level;
        if (level != null && level.getGameTime() % 3L == 0L) {
            // 26.x: Level.random protected → getRandom() (aynı RandomSource örneği)
            RandomSource random = level.getRandom();
            for (Player p : level.players()) {
                if (hasLumos(p.getMainHandItem()) || hasLumos(p.getOffhandItem())) {
                    Vec3 tip = SpellFx.wandTip(p);
                    level.addParticle(
                            ColorParticleOption.create(ModParticles.SPELL_GLOW.get(), 0xFFEAF4FF),
                            tip.x + (random.nextDouble() - 0.5) * 0.08,
                            tip.y + (random.nextDouble() - 0.5) * 0.08,
                            tip.z + (random.nextDouble() - 0.5) * 0.08,
                            0.0, 0.005, 0.0);
                }
            }
        }
    }

    /**
     * İstemci oyun bağlantısı koptu (Fabric ClientPlayConnectionEvents.DISCONNECT /
     * Forge ClientPlayerNetworkEvent.LoggingOut): sunucudan senkronlanan tüm istemci durumunu sıfırla.
     */
    public static void onClientDisconnect() {
        ClientSpellData.clear();
        ClientCastState.clear();
        ClientLockState.clear();
        ClientMagicData.clear();
        ClientUmbraForms.clear();
        ClientSpellBeams.clear();
    }

    /**
     * {@code /arcanumclient reloadconfig} gövdesi (iki loader'ın istemci komutu çağırır): İSTEMCİ JVM'indeki
     * {@code config/arcanum.json} örneğini yeniden okur ve oyuncuya gösterilecek geri bildirimi döndürür.
     * Disk I/O render thread'inde DEĞİL, komut yürütülürken olur. Sunucu-yetkili alanlar ETKİLENMEZ.
     */
    public static Component reloadClientConfig() {
        ArcanumConfig.reload();
        return Component.literal("[Arcanum] İstemci config yeniden okundu "
                + "(mana barı ölçek/saydamlık/asa şartı).");
    }

    /** +/- ile aktif dizilim sayfasını döngüsel değiştirir (ekran kapalı + asa elde). */
    private static void changeActivePage(Minecraft mc, int delta) {
        LocalPlayer p = mc.player;
        if (ClientCompat.screen() != null || p == null || !holdsWand(p)) {
            return;
        }
        int newPage = Math.floorMod(ClientSpellData.activePage() + delta, ClientSpellData.PAGES);
        ArcanumNetwork.sendToServer(new SetActivePayload(newPage, ClientSpellData.activeSlot()));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    /** Slot keybind'i (1..4): aktif SAYFANIN o slotunu seçer — Shift+tekerlek slot
     *  değişiminin doğrudan hali, aynı C2S paketi/sunucu yolu (SetActivePayload). */
    private static void selectSlot(Minecraft mc, int slot) {
        LocalPlayer p = mc.player;
        if (ClientCompat.screen() != null || p == null || !holdsWand(p)) {
            return;
        }
        if (slot == ClientSpellData.activeSlot()) {
            return; // zaten seçili — paket/ses üretme
        }
        ArcanumNetwork.sendToServer(new SetActivePayload(ClientSpellData.activePage(), slot));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
    }

    private static boolean hasLumos(ItemStack s) {
        return s.getItem() instanceof WandItem
                && s.getOrDefault(ModComponents.LUMOS_ACTIVE.get(), false);
    }

    private static boolean holdsWand(LocalPlayer p) {
        return p.getMainHandItem().getItem() instanceof WandItem
                || p.getOffhandItem().getItem() instanceof WandItem;
    }
}
