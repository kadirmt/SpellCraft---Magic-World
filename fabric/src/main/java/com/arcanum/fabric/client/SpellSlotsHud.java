package com.arcanum.fabric.client;

import java.util.HashMap;
import java.util.Map;

import com.arcanum.item.WandItem;
import com.arcanum.spell.ModSpells;
import com.arcanum.spell.Spell;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * SOL-ALT büyü dizilim HUD'u: mana barının HEMEN ÜSTÜNDE, aktif SAYFANIN 4 slotu
 * yatay çizilir. Her slot 18x18 koyu arkane hücre; dolu slotta büyünün 32x32 ikonu
 * 16x16'ya küçültülür. AKTİF slot lavanta çerçeve + parlak arka planla vurgulanır;
 * aktif slotta büyü varsa üstüne (aşağıdan dolan) cooldown overlay çizilir. Slotların
 * üstünde aktif büyünün adı kendi renginde, sağında "1 / 2 / 3" sayfa göstergesi
 * (aktif sayfa altın renginde parlak) yer alır.
 *
 * Veri {@link ClientSpellData}'dan (sunucu S2C senkronu) okunur. Yalnızca asa elde
 * tutulurken ve HUD gizli değilken çizilir.
 */
public final class SpellSlotsHud {
    /** Büyü ikonu ResourceLocation önbelleği (her karede yeniden yaratmamak için). */
    private static final Map<String, ResourceLocation> ICON_CACHE = new HashMap<>();

    private static final int SLOTS = 4;
    private static final int CELL = 18;   // hücre kenarı
    private static final int GAP = 3;     // hücreler arası boşluk
    private static final int MARGIN = 6;  // sol/alt kenar boşluğu (ManaHud ile ortak)

    // Tema renkleri (ARGB)
    private static final int BG_INACTIVE = 0xA0000000;   // yarı-saydam siyah
    private static final int BG_ACTIVE = 0xC0201A2E;     // koyu arkane mor
    private static final int BORDER_INACTIVE = 0x80555066;
    private static final int BORDER_ACTIVE = 0xFFC8B8E0;  // lavanta
    private static final int COOLDOWN_OVERLAY = 0xB0101018;
    private static final int PAGE_ON = 0xFFFFD8A6;        // altın (aktif sayfa)
    private static final int PAGE_OFF = 0xFF6A6478;       // soluk mor (pasif sayfa)
    private static final int PAGE_SEP = 0xFF4A4658;       // ayraç "/"

    private static final int HINT_COLOR = 0xFF9A93A8;     // soluk mor-gri

    private SpellSlotsHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }
        ItemStack wand = findWand(player);
        if (wand == null) {
            return;
        }

        int activePage = ClientSpellData.activePage();
        int activeSlot = ClientSpellData.activeSlot();

        // Yerleşim: mana barının üst kenarı = guiHeight() - bar yüksekliği - MARGIN.
        // Yükseklik SABİT DEĞİL: config'teki manaHudScale ile değişir, o yüzden
        // ManaHud.effectiveHeight() sözleşmesinden okunur (aynı paket, import yok) —
        // bar büyüyünce slot satırı da onunla birlikte yukarı kayar.
        // NOT: mana barı asa şartıyla gizlenmiş olsa bile bu hesap doğru kalır;
        // slotlar zaten yalnız asa eldeyken çizildiği için ikisi hep birlikte görünür.
        int manaBarTop = g.guiHeight() - ManaHud.effectiveHeight() - MARGIN;
        int x0 = MARGIN;
        int slotY = manaBarTop - 5 - CELL;
        int rowW = SLOTS * CELL + (SLOTS - 1) * GAP;

        // En üstte ufak yerelleştirilmiş ipucu: <değiştirici>+Tekerlek ile büyü değiştirme.
        // DİNAMİK: %s = scroll_modifier keybind'inin ANLIK bağlı tuş adı (oyuncu R'ye
        // bağlarsa "R + Fare Tekerleği: ..." yazar). Her karede üretmek ucuz (tek
        // translatable) — statik önbellek keybind değişimini kaçırırdı.
        net.minecraft.network.chat.Component hint = net.minecraft.network.chat.Component.translatable(
                "hud.arcanum.scroll_hint",
                ArcanumFabricClient.scrollModifierKey != null
                        ? ArcanumFabricClient.scrollModifierKey.getTranslatedKeyMessage()
                        : net.minecraft.network.chat.Component.literal("Shift"));
        g.pose().pushPose();
        g.pose().translate(x0, slotY - 18, 0);
        g.pose().scale(0.55f, 0.55f, 1f);
        g.drawString(mc.font, hint, 0, 0, HINT_COLOR, true);
        g.pose().popPose();

        // Slotların üstünde AKTİF büyünün adı (kendi renginde) — yoksa çizme.
        int activeIdx = ClientSpellData.activeSpellIndex();
        if (activeIdx >= 0 && activeIdx < ModSpells.SPELLS.size()) {
            Spell s = ModSpells.SPELLS.get(activeIdx);
            g.drawString(mc.font, s.name(), x0, slotY - 11, 0xFF000000 | s.color(), true);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (int i = 0; i < SLOTS; i++) {
            int cx = x0 + i * (CELL + GAP);
            int cy = slotY;
            boolean active = i == activeSlot;

            // koyu arkane hücre + çerçeve (aktif slot vurgulu)
            g.fill(cx, cy, cx + CELL, cy + CELL, active ? BG_ACTIVE : BG_INACTIVE);
            drawBorder(g, cx, cy, CELL, active ? BORDER_ACTIVE : BORDER_INACTIVE);

            int spellIndex = ClientSpellData.loadoutSlot(activePage, i);
            if (spellIndex >= 0 && spellIndex < ModSpells.SPELLS.size()) {
                Spell s = ModSpells.SPELLS.get(spellIndex);
                // 32x32 ikon -> 16x16 (hücre içinde 1 px iç boşluk)
                g.blit(icon(s), cx + 1, cy + 1, 16, 16, 0f, 0f, 32, 32, 32, 32);

                // cooldown overlay: yalnızca AKTİF slotta, aşağıdan yukarı dolan koyu perde
                if (active) {
                    float cd = player.getCooldowns().getCooldownPercent(
                            wand.getItem(), delta.getGameTimeDeltaPartialTick(true));
                    if (cd > 0f) {
                        int h = Math.round(cd * 16f);
                        g.fill(cx + 1, cy + 1 + (16 - h), cx + 1 + 16, cy + 1 + 16, COOLDOWN_OVERLAY);
                    }
                }
            }
        }
        RenderSystem.disableBlend();

        // Sağda "1 / 2 / 3" sayfa göstergesi (aktif sayfa altın-parlak), dikey ortalı
        int px = x0 + rowW + 6;
        int py = slotY + (CELL - 8) / 2;
        for (int p = 0; p < 3; p++) {
            if (p > 0) {
                px = g.drawString(mc.font, "/", px, py, PAGE_SEP, true) + 1;
            }
            boolean on = p == activePage;
            px = g.drawString(mc.font, String.valueOf(p + 1), px, py, on ? PAGE_ON : PAGE_OFF, true) + 1;
        }
    }

    /** 1 px kalınlığında hücre çerçevesi (4 kenar fill). */
    private static void drawBorder(GuiGraphics g, int x, int y, int size, int color) {
        g.fill(x, y, x + size, y + 1, color);                 // üst
        g.fill(x, y + size - 1, x + size, y + size, color);   // alt
        g.fill(x, y, x + 1, y + size, color);                 // sol
        g.fill(x + size - 1, y, x + size, y + size, color);   // sağ
    }

    /** Büyü ikonu dokusu (önbellekli). */
    private static ResourceLocation icon(Spell s) {
        return ICON_CACHE.computeIfAbsent(s.id(), id ->
                ResourceLocation.fromNamespaceAndPath("arcanum", "textures/gui/spell/" + id + ".png"));
    }

    private static ItemStack findWand(Player player) {
        if (player.getMainHandItem().getItem() instanceof WandItem) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().getItem() instanceof WandItem) {
            return player.getOffhandItem();
        }
        return null;
    }
}
