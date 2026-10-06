package com.arcanum.forge.client;

import com.arcanum.forge.client.hooks.ArcanumClientHandler;
import com.arcanum.forge.client.input.MouseInputHandler;
import com.arcanum.forge.client.state.ClientCastState;
import com.arcanum.forge.client.state.ClientLockState;
import com.arcanum.forge.client.state.ClientMagicData;
import com.arcanum.forge.client.state.ClientSpellData;
import com.arcanum.forge.client.ui.CastBarHud;
import com.arcanum.forge.client.ui.LockTugHud;
import com.arcanum.forge.client.ui.ManaHud;
import com.arcanum.forge.client.ui.SpellMenuScreen;
import com.arcanum.forge.client.ui.SpellSlotsHud;
import com.arcanum.forge.client.ui.SpellTableScreen;
import com.arcanum.item.WandItem;
import com.arcanum.network.ArcanumClientHooks;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.network.SetActivePayload;
import com.arcanum.registry.ModBlocks;
import com.arcanum.registry.ModComponents;
import com.arcanum.registry.ModMenus;
import com.arcanum.registry.ModParticles;
import com.arcanum.spell.SpellFx;
import com.arcanum.util.ArcanumColorParticleOption;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Forge istemci giriş noktası — fabric'teki {@code ArcanumFabricClient.onInitializeClient()}
 * UI/input/durum kalemlerinin birebir karşılığı. Render kalemleri (entity renderer +
 * partikül provider) {@code render.ArcanumForgeRenderers}'ta; mixin'ler
 * {@code arcanum.mixins.json}'da — bu sınıf onlara dokunmaz.
 *
 * <p>YALNIZCA fiziksel istemcide, {@code ArcanumForge} ctor'undan
 * {@code FMLEnvironment.dist.isClient()} korumasıyla çağrılır (dedicated server bu
 * sınıfı hiç yüklemez).
 *
 * <ul>
 *   <li><b>FMLClientSetupEvent:</b> önce {@code ArcanumClientHooks.setHandler(...)},
 *       SONRA {@code ArcanumNetwork.registerS2CReceivers()} (f3-network-contract sırası);
 *       + enqueueWork içinde {@code MenuScreens.register} ve yaprak/fidan render layer'ları
 *       (fabric {@code BlockRenderLayerMap} karşılığı).</li>
 *   <li><b>RegisterKeyMappingsEvent:</b> 3 tuş (G büyü menüsü, numpad -/+ sayfa) —
 *       fabric {@code KeyBindingHelper} karşılığı.</li>
 *   <li><b>RegisterGuiOverlaysEvent:</b> 4 HUD, fabric {@code HudRenderCallback} kayıt
 *       sırasıyla (kayıt sırası = çizim sırası): Mana → Slotlar → CastBar → LockTug.</li>
 *   <li><b>FORGE bus:</b> client tick görevleri, bağlantı kesilme temizliği,
 *       {@link MouseInputHandler} (Shift+tekerlek slot, kenetlenme sol-tık, Crucio iptal).</li>
 * </ul>
 */
public final class ArcanumForgeClient {
    /** SpellMenuScreen/SpellWheelScreen "G ile kapat" için null-güvenli okunur. */
    public static KeyMapping spellWheelKey;
    public static KeyMapping pagePrevKey;
    public static KeyMapping pageNextKey;
    /** Doğrudan slot seçimi (aktif sayfanın 1..4 slotu) — varsayılan ATANMAMIŞ (unbound);
     *  binek üzerindeyken tekerlek sorunu yaşayanlar kendi tuşunu bağlar. */
    public static final KeyMapping[] spellSlotKeys = new KeyMapping[4];
    /** Tekerlek kısayol değiştiricisi — "Shift"+scroll'daki Shift artık bu keybind'in
     *  bağlı tuşudur (varsayılan SOL SHIFT; oyuncu R'ye bağlarsa R+scroll çalışır). */
    public static KeyMapping scrollModifierKey;

    private ArcanumForgeClient() {}

    /** {@code ArcanumForge} ctor'undan (yalnız fiziksel istemci) çağrılır. */
    public static void init(IEventBus modBus) {
        // ---- MOD bus (lifecycle + kayıt event'leri) ----
        modBus.addListener(ArcanumForgeClient::onClientSetup);
        modBus.addListener(ArcanumForgeClient::onRegisterKeyMappings);
        modBus.addListener(ArcanumForgeClient::onRegisterGuiOverlays);

        // ---- FORGE bus (oyun-içi client event'leri) ----
        MinecraftForge.EVENT_BUS.addListener(ArcanumForgeClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ArcanumForgeClient::onLoggingOut);
        // Fabric MouseHandlerMixin karşılığı: Shift+tekerlek slot gezinme +
        // kenetlenme sol-tık "asayı it" + Crucio fare iptali (cancellable event'ler).
        MinecraftForge.EVENT_BUS.addListener(MouseInputHandler::onMouseScroll);
        MinecraftForge.EVENT_BUS.addListener(MouseInputHandler::onMousePress);

        // NOT: Büyü ışını + asa kenetlenmesi DÜNYA render kaydı (fabric'te
        // SpellLockRenderer.register() → WorldRenderEvents) render sahibinin işi —
        // Forge'da RenderLevelStageEvent kaydını SpellLockRenderer kendisi yapar
        // (@Mod.EventBusSubscriber). Buradan yalnız tick-yanı ClientSpellBeams.tick()
        // çağrılır (aşağıda).
    }

    /**
     * S2C alıcı kayıtları + thread-hassas vanilla kayıtları.
     * SIRA ÖNEMLİ: önce setHandler, SONRA registerS2CReceivers (f3-network-contract.md).
     */
    private static void onClientSetup(FMLClientSetupEvent event) {
        // İSTEMCİ-TARAFI CONFIG ISITMASI: ArcanumConfig tembel yüklenir ve dosyayı
        // cwd'ye göreli config/arcanum.json'dan okur — istemci JVM'inde bu .minecraft
        // klasörüdür, yani adanmış sunucuya bağlıyken de OYUNCUNUN KENDİ dosyası okunur
        // (mana barı ölçeği/saydamlığı salt görsel olduğu için senkron gerekmez).
        // Burada bir kez çağırmak dosyayı oyun açılışında oluşturur/okur; aksi hâlde ilk
        // okuma HUD çiziminde, yani render thread'inde disk I/O yapardı.
        com.arcanum.config.ArcanumConfig.get();

        ArcanumClientHooks.setHandler(new ArcanumClientHandler());
        ArcanumNetwork.registerS2CReceivers();

        event.enqueueWork(() -> {
            // Büyü Masası container GUI'si (fabric: MenuScreens.register init içinde;
            // Forge'da ana thread'e enqueueWork ile alınır).
            MenuScreens.register(ModMenus.SPELL_TABLE_MENU.get(), SpellTableScreen::new);

            // Arcanewood yaprak/fidan saydamlığı — fabric BlockRenderLayerMap karşılığı
            // (1.20.1'de hâlâ desteklenen istemci-yanı kayıt).
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.ARCANEWOOD_LEAVES.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.ARCANEWOOD_SAPLING.get(), RenderType.cutout());
        });
    }

    /** 3 keybinding — fabric ile aynı tuşlar/çeviri anahtarları/kategori. */
    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        spellWheelKey = new KeyMapping(
                "key.arcanum.spell_wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.category.arcanum");
        // Dizilim sayfası gezinme (numpad -/+); Shift+tekerlek slot gezinmesini MouseInputHandler yapar
        pagePrevKey = new KeyMapping(
                "key.arcanum.page_prev", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_SUBTRACT, "key.category.arcanum");
        pageNextKey = new KeyMapping(
                "key.arcanum.page_next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_ADD, "key.category.arcanum");
        event.register(spellWheelKey);
        event.register(pagePrevKey);
        event.register(pageNextKey);
        // Doğrudan slot seçimi (1..4) — varsayılan ATANMAMIŞ (InputConstants.UNKNOWN);
        // Shift+tekerleğin yaptığı slot değişiminin tuşlu hali (aynı SetActivePayload yolu).
        for (int i = 0; i < spellSlotKeys.length; i++) {
            spellSlotKeys[i] = new KeyMapping(
                    "key.arcanum.slot_" + (i + 1), InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN, "key.category.arcanum");
            event.register(spellSlotKeys[i]);
        }
        // Tekerlek kısayol değiştiricisi (varsayılan SOL SHIFT) — MouseInputHandler
        // isScrollModifierDown üzerinden okur; fare tuşuna da bağlanabilir.
        scrollModifierKey = new KeyMapping(
                "key.arcanum.scroll_modifier", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_SHIFT, "key.category.arcanum");
        event.register(scrollModifierKey);
    }

    /**
     * 4 HUD — fabric {@code HudRenderCallback} kayıt sırası korunur (sonra kaydedilen
     * üstte çizilir): mana barı → büyü slotları → cast çubuğu → kenetlenme tug HUD'u.
     * Çizim kodları vanilla {@code GuiGraphics} — fabric imzası {@code (GuiGraphics,float)}
     * IGuiOverlay lambda'sıyla sarılır.
     */
    private static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("mana_hud", (gui, g, partialTick, w, h) -> ManaHud.render(g, partialTick));
        event.registerAboveAll("spell_slots_hud", (gui, g, partialTick, w, h) -> SpellSlotsHud.render(g, partialTick));
        event.registerAboveAll("cast_bar_hud", (gui, g, partialTick, w, h) -> CastBarHud.render(g, partialTick));
        event.registerAboveAll("lock_tug_hud", (gui, g, partialTick, w, h) -> LockTugHud.render(g, partialTick));
    }

    /** Bağlantı kesilince istemci durumu temizliği — fabric DISCONNECT karşılığı. */
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSpellData.clear();
        ClientCastState.clear();
        ClientLockState.clear();
        ClientMagicData.clear();
        com.arcanum.forge.client.state.ClientUmbraForms.clear();
        com.arcanum.client.beam.ClientSpellBeams.clear();
    }

    /** Fabric {@code ClientTickEvents.END_CLIENT_TICK} karşılığı (Phase.END filtreli). */
    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // Süpürge drift köprüsü: SPACE (zıplama tuşu) basılı + süpürgedeyken
        // BroomEntity.travel yerel-istemci dalı bu bayrağı okur (BroomInput).
        com.arcanum.entity.BroomInput.drifting = mc.player != null
                && mc.player.getVehicle() instanceof com.arcanum.entity.BroomEntity
                && mc.options.keyJump.isDown();
        // Asa kenetlenmesi düğümünü pürüzsüzce ilerlet; ışınlar artık partikül değil,
        // SpellLockRenderer'da yıldırım-geometri olarak çizilir (dünya render kancası).
        ClientLockState.tickInterp();
        // Serbest büyü ışını izleri: yaşlandır + süresi dolanları at.
        com.arcanum.client.beam.ClientSpellBeams.tick();
        // G: sürükle-bırak büyü dizilim menüsü + skill ağacı. ASA ŞARTI YOK —
        // skill ağacı/mana asadan bağımsız olduğu için elde asa olmasa da açılır.
        while (spellWheelKey != null && spellWheelKey.consumeClick()) {
            LocalPlayer p = mc.player;
            if (p != null) {
                mc.setScreen(new SpellMenuScreen());
            }
        }
        // +/-: aktif dizilim sayfasını gezin (ekran kapalı + asa elde)
        while (pageNextKey != null && pageNextKey.consumeClick()) {
            changeActivePage(mc, +1);
        }
        while (pagePrevKey != null && pagePrevKey.consumeClick()) {
            changeActivePage(mc, -1);
        }
        // Slot 1..4: aktif sayfanın o slotunu doğrudan seç (ekran kapalı + asa elde)
        for (int i = 0; i < spellSlotKeys.length; i++) {
            while (spellSlotKeys[i] != null && spellSlotKeys[i].consumeClick()) {
                selectSlot(mc, i);
            }
        }
        // Lumos: aktif asa taşıyan görünür oyuncuların asa ucunda ışıltı
        if (mc.level != null && mc.level.getGameTime() % 3L == 0L) {
            for (Player p : mc.level.players()) {
                if (hasLumos(p.getMainHandItem()) || hasLumos(p.getOffhandItem())) {
                    Vec3 tip = SpellFx.wandTip(p);
                    mc.level.addParticle(
                            ArcanumColorParticleOption.create(ModParticles.SPELL_GLOW.get(), 0xFFEAF4FF),
                            tip.x + (mc.level.random.nextDouble() - 0.5) * 0.08,
                            tip.y + (mc.level.random.nextDouble() - 0.5) * 0.08,
                            tip.z + (mc.level.random.nextDouble() - 0.5) * 0.08,
                            0.0, 0.005, 0.0);
                }
            }
        }
    }

    /** +/- ile aktif dizilim sayfasını döngüsel değiştirir (ekran kapalı + asa elde). */
    private static void changeActivePage(Minecraft mc, int delta) {
        LocalPlayer p = mc.player;
        if (mc.screen != null || p == null || !holdsWand(p)) {
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
        if (mc.screen != null || p == null || !holdsWand(p)) {
            return;
        }
        if (slot == ClientSpellData.activeSlot()) {
            return; // zaten seçili — paket/ses üretme
        }
        ArcanumNetwork.sendToServer(new SetActivePayload(ClientSpellData.activePage(), slot));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
    }

    /**
     * Tekerlek kısayol değiştiricisi ŞU AN basılı mı — MouseInputHandler buradan okur.
     * BİLEREK KeyMapping.isDown DEĞİL, bağlı tuşun GLFW anlık durumu sorgulanır:
     * varsayılan SOL SHIFT vanilla "Eğil" ile aynı tuştur ve vanilla KeyMapping tuş
     * haritası tuş-başına TEK mapping tuttuğundan çakışan mapping'lerde isDown güvenilmez
     * kalır. GLFW poll'u hem bu çakışmadan etkilenmez hem de fare tuşlarında da çalışır.
     * (Forge: fabric KeyBindingHelper.getBoundKeyOf yerine Forge'un public getKey()'i.)
     */
    public static boolean isScrollModifierDown(Minecraft mc) {
        if (scrollModifierKey == null || scrollModifierKey.isUnbound()) {
            return false;
        }
        InputConstants.Key key = scrollModifierKey.getKey();
        if (key == null || key.getValue() == GLFW.GLFW_KEY_UNKNOWN) {
            return false;
        }
        if (key.getType() == InputConstants.Type.SCANCODE) {
            // SCANCODE bağlaması (GLFW keycode karşılığı olmayan nadir/uluslararası tuş):
            // GLFW'de scancode→keycode ters eşleme YOK, glfwGetKey'e scancode verilemez
            // (yanlış tuş ya da GLFW_INVALID_ENUM). Bu tipte vanilla KeyMapping.isDown
            // güvenlidir — SOL SHIFT/Eğil çakışması KEYSYM'e özgüdür, SCANCODE'da geçersiz.
            return scrollModifierKey.isDown();
        }
        long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }

    private static boolean hasLumos(ItemStack s) {
        // 1.20.1 PORT: component yerine NBT facade — mutasyonsuz okuma (getTag null-check),
        // istemcide getOrCreateTag YASAK (item-değişti animasyonu tetikler).
        return s.getItem() instanceof WandItem
                && ModComponents.isLumosActive(s);
    }

    private static boolean holdsWand(LocalPlayer p) {
        return p.getMainHandItem().getItem() instanceof WandItem
                || p.getOffhandItem().getItem() instanceof WandItem;
    }
}
