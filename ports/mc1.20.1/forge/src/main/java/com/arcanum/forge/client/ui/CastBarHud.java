package com.arcanum.forge.client.ui;

import com.arcanum.forge.client.state.ClientCastState;
import com.arcanum.spell.ModSpells;
import com.arcanum.spell.Spell;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * İmlecin (crosshair) HEMEN ALTINDA çizilen COK UFAK, yarı-saydam cast göstergesi
 * (Forge portu — fabric CastBarHud birebir). Vanilla balta/attack-cooldown çubuğu gibi
 * minik ve dikkat dağıtmayan.
 *
 * <p>Ekran ortasında, crosshair'in ~8 px altında; ~16 px genişlikte, 3 px yükseklikte
 * yatay ince çubuk. Önce koyu yarı-saydam iz, üstüne {@link ClientCastState#progress()}
 * kadar dolan, büyünün kendi renginde (yarı-saydam) ince çubuk. Metin YOK.
 *
 * <p>Cast yoksa ({@link ClientCastState#isCasting()} = false) veya HUD gizliyse çizmez.
 */
public final class CastBarHud {
    /** Çubuk genişliği (px). */
    private static final int BAR_W = 16;
    /** Çubuk yüksekliği (px). */
    private static final int BAR_H = 3;
    /** Crosshair merkezinin altındaki dikey boşluk (px). */
    private static final int BELOW_CROSSHAIR = 8;

    /** Koyu yarı-saydam iz rengi (ARGB). */
    private static final int TRACK_COLOR = 0x80000000;

    private CastBarHud() {}

    public static void render(GuiGraphics g, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !ClientCastState.isCasting()) {
            return;
        }
        int idx = ClientCastState.spellIndex();
        if (idx < 0 || idx >= ModSpells.SPELLS.size()) {
            return;
        }
        Spell s = ModSpells.SPELLS.get(idx);
        float progress = ClientCastState.progress();

        // Ekran ortası, crosshair'in ~8 px altı; çubuk yatay ortalanmış.
        int x = (g.guiWidth() - BAR_W) / 2;
        int y = g.guiHeight() / 2 + BELOW_CROSSHAIR;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Koyu yarı-saydam iz (tam genişlik).
        g.fill(x, y, x + BAR_W, y + BAR_H, TRACK_COLOR);

        // İlerleme kadar dolan, büyü renginde yarı-saydam çubuk.
        int fillW = Math.round(BAR_W * progress);
        if (fillW > 0) {
            int color = 0xC0000000 | (s.color() & 0xFFFFFF);
            g.fill(x, y, x + fillW, y + BAR_H, color);
        }

        RenderSystem.disableBlend();
    }
}
