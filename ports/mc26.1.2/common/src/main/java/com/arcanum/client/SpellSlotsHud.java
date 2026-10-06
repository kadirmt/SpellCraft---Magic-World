package com.arcanum.client;

import java.util.HashMap;
import java.util.Map;

import com.arcanum.Arcanum;
import com.arcanum.item.WandItem;
import com.arcanum.spell.ModSpells;
import com.arcanum.spell.Spell;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
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
    /** Büyü ikonu Identifier önbelleği (her karede yeniden yaratmamak için). */
    private static final Map<String, Identifier> ICON_CACHE = new HashMap<>();

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

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = ClientCompat.mc();
        Player player = mc.player;
        if (player == null || ClientCompat.hudHidden()) {
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
                ArcanumKeys.scrollModifierKey != null
                        ? ArcanumKeys.scrollModifierKey.getTranslatedKeyMessage()
                        : net.minecraft.network.chat.Component.literal("Shift"));
        g.pose().pushMatrix();
        g.pose().translate((float) x0, (float) (slotY - 18));
        g.pose().scale(0.55f, 0.55f);
        g.text(mc.font, hint, 0, 0, HINT_COLOR, true);
        g.pose().popMatrix();

        // Slotların üstünde AKTİF büyünün adı (kendi renginde) — yoksa çizme.
        int activeIdx = ClientSpellData.activeSpellIndex();
        if (activeIdx >= 0 && activeIdx < ModSpells.SPELLS.size()) {
            Spell s = ModSpells.SPELLS.get(activeIdx);
            g.text(mc.font, s.name(), x0, slotY - 11, 0xFF000000 | s.color(), true);
        }

        // (26.1: GUI pipeline'ları alfa karıştırmayı kendisi yapar — enableBlend gereksiz.)

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
                // 26.1 imzası: (pipeline, doku, x, y, u, v, hedefW, hedefH, kaynakW, kaynakH, dokuW, dokuH)
                g.blit(RenderPipelines.GUI_TEXTURED, icon(s), cx + 1, cy + 1, 0f, 0f,
                        16, 16, 32, 32, 32, 32);

                // cooldown overlay: yalnızca AKTİF slotta, aşağıdan yukarı dolan koyu perde
                if (active) {
                    // 1.21.2+: cooldown ItemStack (cooldown grubu) üzerinden sorgulanır
                    float cd = player.getCooldowns().getCooldownPercent(
                            wand, delta.getGameTimeDeltaPartialTick(true));
                    if (cd > 0f) {
                        int h = Math.round(cd * 16f);
                        g.fill(cx + 1, cy + 1 + (16 - h), cx + 1 + 16, cy + 1 + 16, COOLDOWN_OVERLAY);
                    }
                }
            }
        }

        // Sağda "1 / 2 / 3" sayfa göstergesi (aktif sayfa altın-parlak), dikey ortalı
        int px = x0 + rowW + 6;
        int py = slotY + (CELL - 8) / 2;
        // 26.1: text() void döner. Kökteki drawString dönüşü (javap 1.21.1 Font.drawInternal)
        // = x + metin genişliği + 1 (gölge=true) idi; üstüne +1 boşluk → aynı adım burada.
        for (int p = 0; p < 3; p++) {
            if (p > 0) {
                g.text(mc.font, "/", px, py, PAGE_SEP, true);
                px += mc.font.width("/") + 1 + 1;
            }
            boolean on = p == activePage;
            String num = String.valueOf(p + 1);
            g.text(mc.font, num, px, py, on ? PAGE_ON : PAGE_OFF, true);
            px += mc.font.width(num) + 1 + 1;
        }
    }

    /** 1 px kalınlığında hücre çerçevesi (4 kenar fill). */
    private static void drawBorder(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        g.fill(x, y, x + size, y + 1, color);                 // üst
        g.fill(x, y + size - 1, x + size, y + size, color);   // alt
        g.fill(x, y, x + 1, y + size, color);                 // sol
        g.fill(x + size - 1, y, x + size, y + size, color);   // sağ
    }

    /** Büyü ikonu dokusu (önbellekli). */
    private static Identifier icon(Spell s) {
        return ICON_CACHE.computeIfAbsent(s.id(), id ->
                Arcanum.id("textures/gui/spell/" + id + ".png"));
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
