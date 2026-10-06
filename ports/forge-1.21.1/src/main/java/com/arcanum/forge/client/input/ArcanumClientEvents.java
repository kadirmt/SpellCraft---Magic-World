package com.arcanum.forge.client.input;

import com.arcanum.client.beam.ClientSpellBeams;
import com.arcanum.entity.BroomEntity;
import com.arcanum.entity.BroomInput;
import com.arcanum.forge.client.ArcanumForgeClient;
import com.arcanum.forge.client.state.ClientCastState;
import com.arcanum.forge.client.state.ClientLockState;
import com.arcanum.forge.client.state.ClientMagicData;
import com.arcanum.forge.client.state.ClientSpellData;
import com.arcanum.forge.client.state.ClientUmbraForms;
import com.arcanum.forge.client.ui.SpellMenuScreen;
import com.arcanum.item.WandItem;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.network.LockPushPayload;
import com.arcanum.network.SetActivePayload;
import com.arcanum.registry.ModComponents;
import com.arcanum.registry.ModMobEffects;
import com.arcanum.registry.ModParticles;
import com.arcanum.spell.SpellFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * FORGE 1.21.1 istemci FORGE-bus olayları — kök fabric istemcisinin
 * {@code ClientTickEvents.END_CLIENT_TICK} + {@code ClientPlayConnectionEvents.DISCONNECT}
 * kayıtlarının ve {@code MouseHandlerMixin}'in birebir karşılığı (Forge'da mixin YERİNE
 * cancellable input event'leri kullanılır — davranış aynı).
 *
 * <ul>
 *   <li>{@link TickEvent.ClientTickEvent.Post} → kenetlenme interp + serbest büyü ışını
 *       izlerinin yaşlandırılması ({@code ClientSpellBeams.tick} — ışınlar artık partikül
 *       değil, {@code SpellLockRenderer}'da yıldırım-geometri olarak çizilir), G menüsü,
 *       numpad -/+ sayfa gezinme, Lumos asa-ucu ışıltısı.</li>
 *   <li>{@link InputEvent.MouseScrollingEvent} → kök {@code MouseHandlerMixin#arcanum$loadoutScroll}:
 *       ekran kapalı + Shift + asa elde → tekerlek slot 0..3 gezinir, vanilla hotbar
 *       kaydırması iptal. (Forge hook'u tam mixin'in kestiği yerde: screen==null &&
 *       player!=null dalında, hotbar kaydırmasından ÖNCE.)</li>
 *   <li>{@link InputEvent.MouseButton.Pre} → kök {@code MouseHandlerMixin#arcanum$lockClick}:
 *       (a) CRUCIO altında ekran-kapalı tüm fare basışları iptal; (b) düellodayken
 *       SOL-TIK → {@code LockPushPayload} + HUD elmas pulse, saldırı iptal.</li>
 *   <li>{@link ClientPlayerNetworkEvent.LoggingOut} → 4 istemci durumunun temizliği
 *       (kök DISCONNECT karşılığı — f6-network-contract T5 maddesi 3).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "arcanum", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ArcanumClientEvents {

    private ArcanumClientEvents() {}

    // ------------------------------------------------------------- client tick (END)

    @SubscribeEvent
    public static void onClientTickEnd(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
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
        while (ArcanumForgeClient.spellWheelKey.consumeClick()) {
            LocalPlayer p = mc.player;
            if (p != null) {
                mc.setScreen(new SpellMenuScreen());
            }
        }
        // +/-: aktif dizilim sayfasını gezin (ekran kapalı + asa elde)
        while (ArcanumForgeClient.pageNextKey.consumeClick()) {
            changeActivePage(mc, +1);
        }
        while (ArcanumForgeClient.pagePrevKey.consumeClick()) {
            changeActivePage(mc, -1);
        }
        // Slot 1..4: aktif sayfanın o slotunu doğrudan seç (ekran kapalı + asa elde)
        for (int i = 0; i < ArcanumForgeClient.spellSlotKeys.length; i++) {
            while (ArcanumForgeClient.spellSlotKeys[i] != null
                    && ArcanumForgeClient.spellSlotKeys[i].consumeClick()) {
                selectSlot(mc, i);
            }
        }
        // Lumos: aktif asa taşıyan görünür oyuncuların asa ucunda ışıltı
        if (mc.level != null && mc.level.getGameTime() % 3L == 0L) {
            for (Player p : mc.level.players()) {
                if (hasLumos(p.getMainHandItem()) || hasLumos(p.getOffhandItem())) {
                    Vec3 tip = SpellFx.wandTip(p);
                    mc.level.addParticle(
                            ColorParticleOption.create(ModParticles.SPELL_GLOW.get(), 0xFFEAF4FF),
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

    // ------------------------------------------------------------- fare tekerleği

    /**
     * Dünyada (hiçbir ekran açık değilken) asa elde iken Shift + fare tekerleği ile
     * aktif büyü slotunu (0..3) gezinir ve vanilla hotbar kaydırmasını iptal eder.
     * Yukarı kaydırma (deltaY > 0) önceki slota, aşağı sonraki slota geçer;
     * sunucuya {@link SetActivePayload} gönderilir. (Kök mixin koşulları birebir.)
     */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        double yOffset = event.getDeltaY();
        if (mc.screen != null || yOffset == 0.0) {
            return;
        }
        // ASA KENETLENMESİ sırasında TÜM tekerlek iptal (S2 düzeltme 4): kazara Shift'siz
        // scroll vanilla hotbar'ı değiştirip asayı elden düşürüyor → kilit anında, mesajsız
        // kopuyordu. 1-9 ile bilinçli asa değişimi hâlâ mümkün (denge korunur).
        if (ClientLockState.myView() != null) {
            event.setCanceled(true);
            return;
        }
        LocalPlayer p = mc.player;
        // "Shift" artık yapılandırılabilir: scroll_modifier keybind'inin bağlı tuşu
        // (varsayılan SOL SHIFT; oyuncu R'ye bağlarsa R+scroll). GLFW-poll'lu kontrol
        // için bkz. ArcanumForgeClient.isScrollModifierDown.
        if (p == null || !ArcanumForgeClient.isScrollModifierDown(mc) || !holdsWand(p)) {
            return;
        }
        int ns = Math.floorMod(ClientSpellData.activeSlot() + (yOffset > 0 ? -1 : 1), ClientSpellData.SLOTS);
        ArcanumNetwork.sendToServer(new SetActivePayload(ClientSpellData.activePage(), ns));
        event.setCanceled(true); // vanilla hotbar kaydırmasını engelle
    }

    // ------------------------------------------------------------- fare tuşu (Pre)

    /**
     * ASA KENETLENMESİ sırasında SOL-TIK "asayı it" girdisine dönüşür: her fiziksel basış
     * bir kez sunucuya {@link LockPushPayload} yollar (tık SAYIMI + CPS TAVANI 18 otoritesi
     * sunucuda) ve normal saldırı/blok-kırma iptal edilir. Kenetlenme yokken hiçbir şey
     * yapmaz (vanilla davranış). CRUCIO: kıvranırken sağ/sol tık İŞE YARAMAZ — ekran
     * kapalıyken tüm basışları iptal et. (Kök mixin koşulları birebir.)
     */
    @SubscribeEvent
    public static void onMousePress(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        int button = event.getButton();
        int action = event.getAction();
        // CRUCIO: kıvranırken sağ/sol tık İŞE YARAMAZ — ekran kapalıyken tüm basışları iptal et.
        if (mc.screen == null && action == GLFW.GLFW_PRESS && mc.player != null
                && mc.player.hasEffect(ModMobEffects.holderOf(ModMobEffects.CRUCIO))) {
            event.setCanceled(true);
            return;
        }
        // Yalnız YEREL oyuncu bir düelloda ise (izleyici DEĞİL) sol-tık "asayı it"e döner.
        if (mc.screen != null || ClientLockState.myView() == null) {
            return;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || action != GLFW.GLFW_PRESS) {
            return;
        }
        ClientLockState.noteLocalClick();                     // HUD elmas pulse'u
        ArcanumNetwork.sendToServer(new LockPushPayload());   // ham tık → sunucu sayar
        event.setCanceled(true);                              // normal saldırı/blok-kırma iptal
    }

    // ------------------------------------------------------------- disconnect temizliği

    /** Bağlantı kopunca istemci durum önbellekleri varsayılana döner (kök DISCONNECT). */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSpellData.clear();
        ClientCastState.clear();
        ClientLockState.clear();
        ClientMagicData.clear();
        ClientSpellBeams.clear();
        // Umbravolo form seti — bir sonraki sunucu senkronuna kadar bayat id kalmasın.
        ClientUmbraForms.clear();
    }

    // ------------------------------------------------------------- yardımcılar

    private static boolean hasLumos(ItemStack s) {
        return s.getItem() instanceof WandItem
                && s.getOrDefault(ModComponents.LUMOS_ACTIVE.get(), false);
    }

    private static boolean holdsWand(LocalPlayer p) {
        return p.getMainHandItem().getItem() instanceof WandItem
                || p.getOffhandItem().getItem() instanceof WandItem;
    }
}
