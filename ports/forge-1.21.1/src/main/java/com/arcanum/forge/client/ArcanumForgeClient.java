package com.arcanum.forge.client;

import java.util.List;
import java.util.UUID;

import com.arcanum.client.beam.ClientSpellBeams;
import com.arcanum.forge.client.state.ClientCastState;
import com.arcanum.forge.client.state.ClientLockState;
import com.arcanum.forge.client.state.ClientMagicData;
import com.arcanum.forge.client.state.ClientSpellData;
import com.arcanum.forge.client.state.ClientUmbraForms;
import com.arcanum.forge.client.ui.ArcanumLevelUpToast;
import com.arcanum.forge.client.ui.SpellTableScreen;
import com.arcanum.network.ArcanumClientHooks;
import com.arcanum.registry.ModMenus;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/**
 * FORGE 1.21.1 istemci MOD-bus kayıtları — kök fabric
 * {@code ArcanumFabricClient.onInitializeClient}'ın UI/input/durum yarısı.
 * (Partikül provider'ları, entity renderer'lar ve dünya-render'ı BAŞKA ajanda:
 * {@code com.arcanum.forge.client.render}.)
 *
 * <ul>
 *   <li>{@link RegisterKeyMappingsEvent} → 3 keybinding (G menüsü, numpad -/+ sayfa) —
 *       kök {@code KeyBindingHelper.registerKeyBinding} karşılığı.</li>
 *   <li>4 HUD katmanı ARTIK BURADA DEĞİL: {@code AddGuiOverlayLayersEvent} yalnız geç
 *       52.x build'lerinde var (erken 52.0.x'te NoClassDefFoundError) → HUD çizimi
 *       sürüm-bağımsız {@code mixin.GuiMixin}'e taşındı (vanilla {@code Gui.render}
 *       TAIL'i = kök fabric {@code HudRenderCallback} noktası; sıra aynı:
 *       Mana → SpellSlots → CastBar → LockTug).</li>
 *   <li>{@link FMLClientSetupEvent} → {@code MenuScreens.register} (Büyü Masası GUI'si,
 *       thread-safety için enqueueWork) + {@link ArcanumClientHooks#setHandler} (S2C
 *       paket alıcılarının istemci köprüsü — f6-network-contract T5 maddesi 1).</li>
 * </ul>
 *
 * FORGE-bus tarafı (tick, fare, disconnect) {@code input.ArcanumClientEvents}'te.
 */
@Mod.EventBusSubscriber(modid = "arcanum", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArcanumForgeClient {

    // Kök ArcanumFabricClient'taki public statik keybinding'ler — adlar/kategori/tuşlar aynı.
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

    /**
     * Tekerlek kısayol değiştiricisi ŞU AN basılı mı — ArcanumClientEvents.onMouseScroll
     * buradan okur. BİLEREK KeyMapping.isDown DEĞİL, bağlı tuşun GLFW anlık durumu
     * sorgulanır: varsayılan SOL SHIFT vanilla "Eğil" ile aynı tuştur ve vanilla
     * KeyMapping tuş haritası tuş-başına TEK mapping tuttuğundan çakışan mapping'lerde
     * isDown güvenilmez kalır. GLFW poll'u hem bu çakışmadan etkilenmez hem de fare
     * tuşlarında da çalışır. (Forge: fabric KeyBindingHelper.getBoundKeyOf yerine
     * Forge'un public getKey()'i.)
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

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        spellWheelKey = new KeyMapping(
                "key.arcanum.spell_wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.category.arcanum");
        // Dizilim sayfası gezinme (numpad -/+); Shift+tekerlek slot gezinmesini
        // input.ArcanumClientEvents (InputEvent.MouseScrollingEvent) yapar.
        pagePrevKey = new KeyMapping(
                "key.arcanum.page_prev", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_SUBTRACT, "key.category.arcanum");
        pageNextKey = new KeyMapping(
                "key.arcanum.page_next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_ADD, "key.category.arcanum");
        event.register(spellWheelKey);
        event.register(pagePrevKey);
        event.register(pageNextKey);
        // Doğrudan slot seçimi (1..4) — varsayılan ATANMAMIŞ (GLFW_KEY_UNKNOWN);
        // Shift+tekerleğin yaptığı slot değişiminin tuşlu hali (aynı SetActivePayload yolu).
        for (int i = 0; i < spellSlotKeys.length; i++) {
            spellSlotKeys[i] = new KeyMapping(
                    "key.arcanum.slot_" + (i + 1), InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN, "key.category.arcanum");
            event.register(spellSlotKeys[i]);
        }
        // Tekerlek kısayol değiştiricisi (varsayılan SOL SHIFT) — ArcanumClientEvents
        // isScrollModifierDown üzerinden okur; fare tuşuna da bağlanabilir.
        scrollModifierKey = new KeyMapping(
                "key.arcanum.scroll_modifier", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_SHIFT, "key.category.arcanum");
        event.register(scrollModifierKey);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // İSTEMCİ-TARAFI CONFIG ISITMASI: ArcanumConfig tembel yüklenir ve dosyayı
        // cwd'ye göreli config/arcanum.json'dan okur — istemci JVM'inde bu .minecraft
        // klasörüdür, yani adanmış sunucuya bağlıyken de OYUNCUNUN KENDİ dosyası okunur
        // (mana barı ölçeği/saydamlığı salt görsel olduğu için senkron gerekmez).
        // Burada bir kez çağırmak dosyayı oyun açılışında oluşturur/okur; aksi hâlde ilk
        // okuma HUD çiziminde, yani render thread'inde disk I/O yapardı.
        com.arcanum.config.ArcanumConfig.get();

        // DEV-ONLY workaround (-Darcanum.dev.startBusEarly=true ile açılır; production'da
        // etkisiz): Forge 52.1.15'te --quickPlaySingleplayer dünyayı
        // Minecraft#onResourceLoadFinished içinde açar, MinecraftForge.EVENT_BUS.start()
        // ise HEMEN SONRAKİ satırdaki ClientModLoader.completeModLoading()'de çağrılır
        // (Minecraft.java patch satır 615-616). Bus henüz kapalıyken post edilen
        // AddReloadListenerEvent düşer → ForgeInternalHandler.LootModifierManager null
        // kalır → ilk loot çekiminde "Can not retrieve LootModifierManager" ile entegre
        // sunucu çöker. Bus'ı burada (quickplay'den önce) başlatmak yarışı kapatır;
        // start() idempotenttir, completeModLoading'in ikinci çağrısı zararsızdır.
        if (Boolean.getBoolean("arcanum.dev.startBusEarly")) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.start();
        }

        // S2C alıcılarının istemci köprüsü — bir sunucuya bağlanmadan önce kurulmuş olmalı.
        // Handler zaten ana istemci thread'inde çağrılır (consumerMainThread) — içeride
        // ekstra execute GEREKMEZ (bkz. f6-network-contract).
        ArcanumClientHooks.setHandler(new ArcanumClientHooks.Handler() {
            @Override
            public void setKnownSpells(List<String> spellIds) {
                ClientSpellData.set(spellIds);
            }

            @Override
            public void setLoadout(int[] loadout, int activePage, int activeSlot) {
                ClientSpellData.setLoadout(loadout, activePage, activeSlot);
            }

            @Override
            public void setCastState(int spellIndex, int totalTicks) {
                ClientCastState.set(spellIndex, totalTicks);
            }

            @Override
            public void setLockState(UUID a, UUID b, float node, int colorA, int colorB, int phase) {
                ClientLockState.set(a, b, node, colorA, colorB, phase);
            }

            @Override
            public void addSpellBeam(Vec3 from, Vec3 to, int color, int life, float width, float spread) {
                // Büyü ışını olayı → yıldırım-tarzı ışın istemcide çizilir, ~0.5 sn iz bırakır.
                ClientSpellBeams.add(from, to, color, life, width, spread);
            }

            @Override
            public void setMagicData(int level, int xp, int xpToNext, int mana, int manaCap, int[] nodes,
                                     int maxLevel, int pointsPerLevel) {
                ClientMagicData.set(level, xp, xpToNext, mana, manaCap, nodes, maxLevel, pointsPerLevel);
            }

            @Override
            public void showLevelUp(int newLevel) {
                // Seviye atlama olayı → sağ-üstte cilalı toast bildirimi göster.
                Minecraft.getInstance().getToasts().addToast(new ArcanumLevelUpToast(newLevel));
            }

            @Override
            public void showSpellTableFeedback(boolean success, Component message) {
                // Büyü Masası GUI'si açıkken vanilla actionbar/chat görünmez —
                // sonucu doğrudan açık ekrana ilet (açık değilse yok say).
                if (Minecraft.getInstance().screen instanceof SpellTableScreen screen) {
                    screen.showFeedback(message, success);
                }
            }

            @Override
            public void setUmbraForm(int entityId, boolean active) {
                // Umbravolo kara duman formu seti — render mixin'leri bu sete bakar.
                ClientUmbraForms.set(entityId, active);
            }
        });

        // Büyü Masası container GUI'si — kök MenuScreens.register karşılığı
        // (Forge 52'de RegisterMenuScreensEvent yok; enqueueWork = thread-safety).
        event.enqueueWork(() ->
                MenuScreens.register(ModMenus.SPELL_TABLE_MENU.get(), SpellTableScreen::new));
    }
}
