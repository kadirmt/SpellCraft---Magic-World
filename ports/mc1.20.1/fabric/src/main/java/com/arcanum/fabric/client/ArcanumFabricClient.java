package com.arcanum.fabric.client;

import java.util.List;
import java.util.UUID;

import com.arcanum.fabric.client.particle.BeamParticle;
import com.arcanum.fabric.client.particle.GlowParticle;
import com.arcanum.fabric.client.particle.LeafMoteParticle;
import com.arcanum.fabric.client.particle.RuneParticle;
import com.arcanum.fabric.client.particle.SpellSparkParticle;
import com.arcanum.fabric.client.particle.TrailParticle;
import com.arcanum.fabric.client.render.SpellLockRenderer;
import com.arcanum.item.WandItem;
import com.arcanum.network.ArcanumClientHooks;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.network.SetActivePayload;
import com.arcanum.registry.ModBlocks;
import com.arcanum.registry.ModComponents;
import com.arcanum.registry.ModEntities;
import com.arcanum.registry.ModMenus;
import com.arcanum.registry.ModParticles;
import com.arcanum.spell.SpellFx;
import com.arcanum.util.ArcanumColorParticleOption;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric istemci giriş noktası.
 * - Büyü partikül provider'ları (glow/trail/rune/spark/leaf)
 * - Mana HUD + seçili büyü göstergesi
 * - G tuşu radyal büyü menüsü
 * - Bilinen büyü senkronu (S2C) ve Lumos asa ucu ışıltısı
 */
public final class ArcanumFabricClient implements ClientModInitializer {
    public static KeyMapping spellWheelKey;
    public static KeyMapping pagePrevKey;
    public static KeyMapping pageNextKey;
    /** Doğrudan slot seçimi (aktif sayfanın 1..4 slotu) — varsayılan ATANMAMIŞ (unbound);
     *  binek üzerindeyken tekerlek sorunu yaşayanlar kendi tuşunu bağlar. */
    public static final KeyMapping[] spellSlotKeys = new KeyMapping[4];
    /** Tekerlek kısayol değiştiricisi — "Shift"+scroll'daki Shift artık bu keybind'in
     *  bağlı tuşudur (varsayılan SOL SHIFT; oyuncu R'ye bağlarsa R+scroll çalışır). */
    public static KeyMapping scrollModifierKey;

    @Override
    public void onInitializeClient() {
        // İSTEMCİ-TARAFI CONFIG ISITMASI: ArcanumConfig tembel yüklenir ve dosyayı
        // cwd'ye göreli config/arcanum.json'dan okur — istemci JVM'inde bu .minecraft
        // klasörüdür, yani adanmış sunucuya bağlıyken de OYUNCUNUN KENDİ dosyası okunur
        // (mana barı ölçeği/saydamlığı salt görsel olduğu için senkron gerekmez).
        // Burada bir kez çağırmak dosyayı oyun açılışında oluşturur/okur; aksi hâlde ilk
        // okuma HUD çiziminde, yani render thread'inde disk I/O yapardı.
        com.arcanum.config.ArcanumConfig.get();

        // İstemci-yerel config reload komutu (/arcanumclient reloadconfig) — adanmış
        // sunucuda sunucu komutu istemcinin örneğini tazelemediği için gerekli.
        ArcanumClientConfigCommand.register();

        ParticleFactoryRegistry.getInstance().register(
                ModParticles.SPELL_SPARK.get(), SpellSparkParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(
                ModParticles.SPELL_GLOW.get(), GlowParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(
                ModParticles.SPELL_TRAIL.get(), TrailParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(
                ModParticles.MAGIC_RUNE.get(), RuneParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(
                ModParticles.LEAF_MOTE.get(), LeafMoteParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(
                ModParticles.SPELL_BEAM.get(), BeamParticle.Provider::new);

        // Sol-alt HUD: mana barı + büyü dizilim slotları
        HudRenderCallback.EVENT.register(ManaHud::render);
        HudRenderCallback.EVENT.register(SpellSlotsHud::render);
        // İmleç altı minik cast göstergesi
        HudRenderCallback.EVENT.register(CastBarHud::render);
        // Asa kenetlenmesi tug-of-war HUD'u (crosshair üstü denge çubuğu)
        HudRenderCallback.EVENT.register(LockTugHud::render);
        // Asa kenetlenmesi ışın+düğüm dünya render'ı (WorldRenderEvents, mixin'siz)
        SpellLockRenderer.register();

        // 1.20.1 PORT: S2C receiver'lar common'a taşındı (ArcanumNetwork.registerS2CReceivers).
        // İstemci tarafı işler ArcanumClientHooks.Handler üzerinden buraya geri bağlanır —
        // handler ZATEN ana istemci thread'inde çağrılır (ctx.queue), ekstra execute gerekmez.
        // SIRA ÖNEMLİ: önce setHandler, SONRA registerS2CReceivers (f3-network-contract.md).
        ArcanumClientHooks.setHandler(new ArcanumClientHooks.Handler() {
            // Bilinen büyü listesi senkronu
            @Override
            public void setKnownSpells(List<String> spellIds) {
                ClientSpellData.set(spellIds);
            }

            // Büyü dizilimi senkronu (12 slot + aktif sayfa/slot)
            @Override
            public void setLoadout(int[] loadout, int activePage, int activeSlot) {
                ClientSpellData.setLoadout(loadout, activePage, activeSlot);
            }

            // Cast durumu senkronu (S2C): cast başladı (spellIndex>=0) / bitti-iptal (spellIndex<0).
            // ClientCastState.set spellIndex<0 / totalTicks<=0 gelince kendi içinde clear() yapar.
            @Override
            public void setCastState(int spellIndex, int totalTicks) {
                ClientCastState.set(spellIndex, totalTicks);
            }

            // Asa kenetlenmesi durumu senkronu (S2C): ışın/düğüm/HUD buna göre çizilir/temizlenir.
            @Override
            public void setLockState(UUID a, UUID b, float node, int colorA, int colorB, int phase) {
                ClientLockState.set(a, b, node, colorA, colorB, phase);
            }

            // Büyü ışını olayı (S2C): yıldırım-tarzı ışın istemcide çizilir, ~0.5 sn iz bırakır.
            @Override
            public void addSpellBeam(double fromX, double fromY, double fromZ,
                                     double toX, double toY, double toZ,
                                     int color, int life, float width, float spread) {
                com.arcanum.client.beam.ClientSpellBeams.add(
                        new Vec3(fromX, fromY, fromZ), new Vec3(toX, toY, toZ),
                        color, life, width, spread);
            }

            // Büyücü verisi (level/xp/skill/mana) senkron — ManaHud + skill paneli buradan okur.
            @Override
            public void setMagicData(int level, int xp, int xpToNext, int mana, int manaCap, int[] nodes,
                                     int maxLevel, int pointsPerLevel) {
                ClientMagicData.set(level, xp, xpToNext, mana, manaCap, nodes, maxLevel, pointsPerLevel);
            }

            // Seviye atlama olayı → sağ-üstte cilalı toast bildirimi göster.
            @Override
            public void showLevelUp(int newLevel) {
                Minecraft.getInstance().getToasts().addToast(new ArcanumLevelUpToast(newLevel));
            }

            // Büyü Masası GUI'si açıkken vanilla actionbar/chat görünmez — sonucu
            // doğrudan açık ekrana ilet.
            @Override
            public void showSpellTableFeedback(boolean success, Component message) {
                if (Minecraft.getInstance().screen instanceof SpellTableScreen screen) {
                    screen.showFeedback(message, success);
                }
            }

            // Umbravolo kara duman formu seti (S2C) → zırh/eldeki-eşya/elytra/kafa
            // render mixin'lerinin baktığı gizleme seti.
            @Override
            public void setUmbraForm(int entityId, boolean active) {
                ClientUmbraForms.set(entityId, active);
            }
        });
        ArcanumNetwork.registerS2CReceivers();

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientSpellData.clear();
            ClientCastState.clear();
            ClientLockState.clear();
            ClientMagicData.clear();
            ClientUmbraForms.clear();
            com.arcanum.client.beam.ClientSpellBeams.clear();
        });

        // Arcanewood yaprak/fidan saydamlığı
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ARCANEWOOD_LEAVES.get(), RenderType.cutoutMipped());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ARCANEWOOD_SAPLING.get(), RenderType.cutout());

        // Yaratık renderer'ları (GeckoLib)
        EntityRendererRegistry.register(ModEntities.DEATH_EATER.get(), DeathEaterRenderer::new);
        EntityRendererRegistry.register(ModEntities.DEMENTOR.get(), DementorRenderer::new);
        EntityRendererRegistry.register(ModEntities.BOWTRUCKLE.get(), BowtruckleRenderer::new);
        EntityRendererRegistry.register(ModEntities.MOONCALF.get(), MooncalfRenderer::new);
        EntityRendererRegistry.register(ModEntities.BROOM.get(), BroomRenderer::new);
        EntityRendererRegistry.register(ModEntities.TROLL.get(), TrollRenderer::new);
        EntityRendererRegistry.register(ModEntities.PHOENIX.get(), PhoenixRenderer::new);
        EntityRendererRegistry.register(ModEntities.THUNDERBIRD.get(), ThunderbirdRenderer::new);
        EntityRendererRegistry.register(ModEntities.SNOWY_OWL.get(), SnowyOwlRenderer::new);
        EntityRendererRegistry.register(ModEntities.THESTRAL.get(), ThestralRenderer::new);
        EntityRendererRegistry.register(ModEntities.UNICORN.get(), UnicornRenderer::new);
        EntityRendererRegistry.register(ModEntities.ARCANEWOOD_BOAT.get(), ArcanewoodBoatRenderer::new);
        EntityRendererRegistry.register(ModEntities.ARCANEWOOD_CHEST_BOAT.get(), ArcanewoodChestBoatRenderer::new);
        EntityRendererRegistry.register(ModEntities.HIPPOGRIFF.get(), HippogriffRenderer::new);
        EntityRendererRegistry.register(ModEntities.ACROMANTULA.get(), AcromantulaRenderer::new);
        EntityRendererRegistry.register(ModEntities.WEREWOLF.get(), WerewolfRenderer::new);
        EntityRendererRegistry.register(ModEntities.GRINDYLOW.get(), GrindylowRenderer::new);
        EntityRendererRegistry.register(ModEntities.KNEAZLE.get(), KneazleRenderer::new);
        EntityRendererRegistry.register(ModEntities.BASILISK.get(), BasiliskRenderer::new);
        EntityRendererRegistry.register(ModEntities.PATRONUS.get(), PatronusRenderer::new);
        EntityRendererRegistry.register(ModEntities.WIZARD_TRADER.get(), WizardTraderRenderer::new);
        // Protego Diabolica alev ejderhası (translucent-emissive gövde + additive glowmask)
        EntityRendererRegistry.register(ModEntities.DIABOLICA_DRAGON.get(), DiabolicaDragonRenderer::new);

        spellWheelKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.arcanum.spell_wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.category.arcanum"));

        // Dizilim sayfası gezinme (numpad -/+); Shift+tekerlek slot gezinmesini MouseHandlerMixin yapar
        pagePrevKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.arcanum.page_prev", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_SUBTRACT, "key.category.arcanum"));
        pageNextKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.arcanum.page_next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_ADD, "key.category.arcanum"));

        // Doğrudan slot seçimi (1..4) — varsayılan ATANMAMIŞ; Shift+tekerleğin yaptığı slot
        // değişiminin tuşlu hali (aynı SetActivePayload / sunucu yolu).
        for (int i = 0; i < spellSlotKeys.length; i++) {
            spellSlotKeys[i] = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    "key.arcanum.slot_" + (i + 1), InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN, "key.category.arcanum"));
        }
        // Tekerlek kısayol değiştiricisi (varsayılan SOL SHIFT) — MouseHandlerMixin
        // isScrollModifierDown üzerinden okur; fare tuşuna da bağlanabilir.
        scrollModifierKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.arcanum.scroll_modifier", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_SHIFT, "key.category.arcanum"));

        // Büyü Masası container GUI'si
        MenuScreens.register(ModMenus.SPELL_TABLE_MENU.get(), SpellTableScreen::new);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
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
            while (spellWheelKey.consumeClick()) {
                LocalPlayer p = mc.player;
                if (p != null) {
                    mc.setScreen(new SpellMenuScreen());
                }
            }
            // +/-: aktif dizilim sayfasını gezin (ekran kapalı + asa elde)
            while (pageNextKey.consumeClick()) {
                changeActivePage(mc, +1);
            }
            while (pagePrevKey.consumeClick()) {
                changeActivePage(mc, -1);
            }
            // Slot 1..4: aktif sayfanın o slotunu doğrudan seç (ekran kapalı + asa elde)
            for (int i = 0; i < spellSlotKeys.length; i++) {
                while (spellSlotKeys[i].consumeClick()) {
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
        });
    }

    /** +/- ile aktif dizilim sayfasını döngüsel değiştirir (ekran kapalı + asa elde). */
    private static void changeActivePage(net.minecraft.client.Minecraft mc, int delta) {
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
    private static void selectSlot(net.minecraft.client.Minecraft mc, int slot) {
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
     * Tekerlek kısayol değiştiricisi ŞU AN basılı mı — MouseHandlerMixin buradan okur.
     * BİLEREK KeyMapping.isDown DEĞİL, bağlı tuşun GLFW anlık durumu sorgulanır:
     * varsayılan SOL SHIFT vanilla "Eğil" ile aynı tuştur ve vanilla KeyMapping tuş
     * haritası tuş-başına TEK mapping tuttuğundan çakışan mapping'lerde isDown güvenilmez
     * kalır. GLFW poll'u hem bu çakışmadan etkilenmez hem de fare tuşlarında da çalışır.
     */
    public static boolean isScrollModifierDown(net.minecraft.client.Minecraft mc) {
        if (scrollModifierKey == null || scrollModifierKey.isUnbound()) {
            return false;
        }
        InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(scrollModifierKey);
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
