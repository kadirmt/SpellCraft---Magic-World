package com.arcanum.client;

import com.arcanum.Arcanum;
import com.arcanum.platform.Platform;
import com.arcanum.platform.client.PlatformClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Arcanum tuş bağlamaları (kökteki {@code ArcanumFabricClient} alanları, AYNI adlarla).
 *
 * <p>{@link KeyMapping} nesneleri burada (ortak) yaratılır; loader yalnız KAYDEDER
 * ({@link PlatformClient#registerKeyMapping}). Kategori 26.x'te {@code KeyMapping.Category} kaydıdır:
 * {@code arcanum:main} → lang anahtarı {@code key.category.arcanum.main}
 * ({@code Category.label()} = {@code id.toLanguageKey("key.category")}).
 *
 * <p>Nesneler sınıf yüklenirken yaratılır (alanlar hiçbir zaman null değildir — mixin/HUD {@link #register()}'dan
 * önce okusa bile güvenli). {@code Category.register} aynı kimliği ikinci kez kabul etmez; sınıf tek kez yüklenir.
 * Tuşları işleyen tick mantığı {@link ArcanumClient#onClientTickEnd}.
 */
public final class ArcanumKeys {
    private ArcanumKeys() {}

    /** Arcanum tuş kategorisi (lang: {@code key.category.arcanum.main}). */
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Arcanum.id("main"));

    /** G: sürükle-bırak büyü dizilim menüsü + skill ağacı. */
    public static final KeyMapping spellWheelKey = new KeyMapping(
            "key.arcanum.spell_wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    // Dizilim sayfası gezinme (numpad -/+); Shift+tekerlek slot gezinmesini MouseHandlerMixin yapar
    public static final KeyMapping pagePrevKey = new KeyMapping(
            "key.arcanum.page_prev", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_SUBTRACT, CATEGORY);
    public static final KeyMapping pageNextKey = new KeyMapping(
            "key.arcanum.page_next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_ADD, CATEGORY);

    /** Doğrudan slot seçimi (aktif sayfanın 1..4 slotu) — varsayılan ATANMAMIŞ (unbound);
     *  binek üzerindeyken tekerlek sorunu yaşayanlar kendi tuşunu bağlar. */
    public static final KeyMapping[] spellSlotKeys = new KeyMapping[4];

    static {
        // Doğrudan slot seçimi (1..4) — varsayılan ATANMAMIŞ; Shift+tekerleğin yaptığı slot
        // değişiminin tuşlu hali (aynı SetActivePayload / sunucu yolu).
        for (int i = 0; i < spellSlotKeys.length; i++) {
            spellSlotKeys[i] = new KeyMapping(
                    "key.arcanum.slot_" + (i + 1), InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
        }
    }

    /** Tekerlek kısayol değiştiricisi — "Shift"+scroll'daki Shift artık bu keybind'in
     *  bağlı tuşudur (varsayılan SOL SHIFT; oyuncu R'ye bağlarsa R+scroll çalışır).
     *  MouseHandlerMixin {@link #isScrollModifierDown} üzerinden okur; fare tuşuna da bağlanabilir. */
    public static final KeyMapping scrollModifierKey = new KeyMapping(
            "key.arcanum.scroll_modifier", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT, CATEGORY);

    /** {@link ArcanumClient#init()} içinden bir kez — kök kayıt sırasıyla aynı. */
    static void register() {
        PlatformClient client = Platform.client();
        client.registerKeyMapping(spellWheelKey);
        client.registerKeyMapping(pagePrevKey);
        client.registerKeyMapping(pageNextKey);
        for (KeyMapping k : spellSlotKeys) {
            client.registerKeyMapping(k);
        }
        client.registerKeyMapping(scrollModifierKey);
    }

    /**
     * Tekerlek kısayol değiştiricisi ŞU AN basılı mı — MouseHandlerMixin buradan okur.
     * BİLEREK KeyMapping.isDown DEĞİL, bağlı tuşun GLFW anlık durumu sorgulanır:
     * varsayılan SOL SHIFT vanilla "Eğil" ile aynı tuştur ve vanilla KeyMapping tuş
     * haritası tuş-başına TEK mapping tuttuğundan çakışan mapping'lerde isDown güvenilmez
     * kalır. GLFW poll'u hem bu çakışmadan etkilenmez hem de fare tuşlarında da çalışır.
     */
    public static boolean isScrollModifierDown(Minecraft mc) {
        if (scrollModifierKey == null || scrollModifierKey.isUnbound()) {
            return false;
        }
        InputConstants.Key key = Platform.client().getBoundKey(scrollModifierKey);
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
        if (key.getType() == InputConstants.Type.MOUSE) {
            // 26.x: Window#getWindow() → Window#handle()
            return GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        // 26.x: InputConstants.isKeyDown(long,int) → isKeyDown(Window,int) (içi glfwGetKey(window.handle(), key) == 1)
        return InputConstants.isKeyDown(mc.getWindow(), key.getValue());
    }
}
