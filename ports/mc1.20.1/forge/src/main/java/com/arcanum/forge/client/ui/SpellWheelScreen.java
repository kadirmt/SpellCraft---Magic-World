package com.arcanum.forge.client.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.arcanum.forge.client.ArcanumForgeClient;
import com.arcanum.forge.client.input.SpellHotkeys;
import com.arcanum.forge.client.state.ClientSpellData;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.network.SelectSpellPayload;
import com.arcanum.spell.ModSpells;
import com.arcanum.spell.Spell;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * G ile açılan GERÇEK radyal büyü çarkı. Her büyü, kendi tema renginde pürüzsüz
 * bir halka dilimi olarak çizilir (TRIANGLES + POSITION_COLOR). Dilim ortasında
 * 32x32 büyü ikonu; bilinmeyen büyüler desatüre + kilit ikonu ile gösterilir.
 * Fare açısıyla hover, sol-tık ile seçim → C2S paket. Büyüler 8'erli SAYFALARA
 * bölünür; fare tekerleği sayfalar arasında gezinir (alt merkezde gösterge).
 * Dünya tıkanmaz (isPauseScreen=false).
 *
 * <p>NOT (fabric envanteriyle aynı durum): 8. tur loadout rework'ünden sonra hiçbir
 * yerden açılmıyor (ölü kod) — sıfır özellik kaybı için fabric 1.20.1 modülüyle
 * birebir parite adına taşındı.
 */
public class SpellWheelScreen extends Screen {
    /** Sayfa başına dilim sayısı — 8 dilim hem okunaklı hem tıklanabilir. */
    private static final int PAGE_SIZE = 8;
    private static final ResourceLocation LOCK_TEX =
            new ResourceLocation("arcanum", "textures/gui/lock.png");
    /** Büyü ikonu ResourceLocation önbelleği (her karede yeniden yaratmamak için). */
    private static final Map<String, ResourceLocation> ICON_CACHE = new HashMap<>();

    /** Dilimler arası boşluk (derece) ve yay pürüzsüzlüğü (alt-parça sayısı). */
    private static final float GAP_DEG = 1.5f;
    private static final int ARC_SEGMENTS = 8;
    /** Açılış animasyonu süresi (~6 tick). */
    private static final float OPEN_ANIM_SEC = 0.3f;

    private final long openedAt = System.nanoTime();
    /** Sayfa içi hover index'i (0..sayfadaki dilim sayısı-1), -1 = yok. */
    private int hovered = -1;
    private int page = 0;

    public SpellWheelScreen() {
        super(Component.translatable("key.arcanum.spell_wheel"));
        // açılış sesi: kitap sayfası, tiz — "büyü defteri açıldı" hissi
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.4f));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // 1.20.1 PORT NOTU: renderBlurredBackground override'ı kaldırıldı — 1.20.1'de
    // Screen'de blur mekanizması hiç yok (1.21'de geldi), dünya zaten net kalır.

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Vanilla renderBackground() dünyayı bulanıklaştırır — istemiyoruz.
        // Bunun yerine hafif, NET kalan bir karartma çiziyoruz (dünya görünür kalsın).
        g.fill(0, 0, this.width, this.height, 0x55101018);

        List<Spell> spells = pageSpells();
        int n = spells.size();
        if (n == 0) {
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        int cx = this.width / 2;
        int cy = this.height / 2;

        // halka geometrisi: küçük ekranlarda dış yarıçap kısılır, iç oranla küçülür
        float outerR = Math.min(110f, this.height * 0.36f);
        float innerR = Math.min(42f, outerR * 0.42f);

        // açılış animasyonu: ease-out ölçek 0.85→1.0 + alfa fade (nanoTime tabanlı, pürüzsüz)
        float t = Math.min(1f, (System.nanoTime() - openedAt) / 1.0e9f / OPEN_ANIM_SEC);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        float animScale = 0.85f + 0.15f * ease;
        float animAlpha = ease;

        // hover: fare açısından dilim index'i (dilim i, -90° + i*step açısına ortalanmış)
        double step = Math.PI * 2.0 / n;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        hovered = -1;
        if (Math.sqrt(dx * dx + dy * dy) > 20.0) {
            double ang = Math.atan2(dy, dx) + Math.PI / 2.0;
            if (ang < 0) ang += Math.PI * 2.0;
            hovered = (int) Math.round(ang / step) % n;
        }

        // tüm çark, merkez etrafında animasyon ölçeğiyle çizilir
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(animScale, animScale, 1f);
        pose.translate(-cx, -cy, 0);

        drawSlices(g, spells, cx, cy, innerR, outerR, animAlpha);
        drawIcons(g, spells, cx, cy, innerR, outerR, animAlpha);
        drawCenterText(g, spells, cx, cy, animAlpha);
        drawPageIndicator(g, cx, cy, outerR, animAlpha);

        pose.popPose();
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Alt merkezde "◀ 2/3 ▶" sayfa göstergesi (tek sayfa varsa çizilmez). */
    private void drawPageIndicator(GuiGraphics g, int cx, int cy, float outerR, float animAlpha) {
        int pages = pageCount();
        int a = (int) (255 * animAlpha);
        if (pages <= 1 || a < 8) {
            return;
        }
        int y = cy + (int) outerR + 14;
        g.drawCenteredString(this.font, "◀  " + (page + 1) + " / " + pages + "  ▶", cx, y, (a << 24) | 0xC8CEDC);
        g.drawCenteredString(this.font,
                Component.translatable("arcanum.wheel_scroll"), cx, y + 11, (a << 24) | 0x707890);
    }

    /** Fare tekerleği: sayfalar arasında döngüsel gezinme. (1.20.1: 3 parametreli imza) */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int pages = pageCount();
        if (pages > 1 && scrollY != 0) {
            page = Math.floorMod(page + (scrollY < 0 ? 1 : -1), pages);
            hovered = -1;
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    private static int pageCount() {
        return Math.max(1, (ModSpells.SPELLS.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** Geçerli sayfanın büyü alt listesi. */
    private List<Spell> pageSpells() {
        List<Spell> all = ModSpells.SPELLS;
        int from = Math.min(page * PAGE_SIZE, Math.max(0, all.size() - 1));
        return all.subList(from, Math.min(from + PAGE_SIZE, all.size()));
    }

    /** Renkli halka dilimlerini tek buffer'da TRIANGLES olarak çizer. */
    private void drawSlices(GuiGraphics g, List<Spell> spells, int cx, int cy,
                            float innerR, float outerR, float animAlpha) {
        int n = spells.size();
        double step = Math.PI * 2.0 / n;
        double gap = Math.toRadians(GAP_DEG);
        Matrix4f m = g.pose().last().pose();

        // bekleyen batch'li çizimleri boşalt — özel üçgenlerimiz doğru sırada kalsın
        g.flush();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 1.20.1 PORT: Tesselator.begin(mode,format) (1.21) yerine getBuilder()+begin().
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < n; i++) {
            Spell s = spells.get(i);
            boolean hov = i == hovered;
            boolean known = ClientSpellData.knows(s);

            // renk: bilinen → büyü rengi; bilinmeyen → desatüre gri-koyu
            int rgb = known ? s.color() : desaturateDark(s.color());
            if (hov) {
                rgb = lighten(rgb, known ? 0.35f : 0.15f);
            }
            int alpha = (int) ((hov ? 220 : 150) * animAlpha);
            int cr = (rgb >> 16) & 0xFF;
            int cg = (rgb >> 8) & 0xFF;
            int cb = rgb & 0xFF;

            // hover'da dilim dışa "pop" yapar (yarıçaplar büyür)
            float rIn = innerR + (hov ? 3f : 0f);
            float rOut = outerR + (hov ? 6f : 0f);

            double mid = -Math.PI / 2.0 + i * step;
            double a0 = mid - step / 2.0 + gap / 2.0;
            double a1 = mid + step / 2.0 - gap / 2.0;

            for (int j = 0; j < ARC_SEGMENTS; j++) {
                double sa = a0 + (a1 - a0) * j / ARC_SEGMENTS;
                double sb = a0 + (a1 - a0) * (j + 1) / ARC_SEGMENTS;
                float ix0 = cx + (float) (Math.cos(sa) * rIn);
                float iy0 = cy + (float) (Math.sin(sa) * rIn);
                float ox0 = cx + (float) (Math.cos(sa) * rOut);
                float oy0 = cy + (float) (Math.sin(sa) * rOut);
                float ix1 = cx + (float) (Math.cos(sb) * rIn);
                float iy1 = cy + (float) (Math.sin(sb) * rIn);
                float ox1 = cx + (float) (Math.cos(sb) * rOut);
                float oy1 = cy + (float) (Math.sin(sb) * rOut);
                // halka parçası = 2 üçgen (1.20.1: vertex/color/endVertex zinciri)
                buf.vertex(m, ix0, iy0, 0).color(cr, cg, cb, alpha).endVertex();
                buf.vertex(m, ox0, oy0, 0).color(cr, cg, cb, alpha).endVertex();
                buf.vertex(m, ox1, oy1, 0).color(cr, cg, cb, alpha).endVertex();
                buf.vertex(m, ix0, iy0, 0).color(cr, cg, cb, alpha).endVertex();
                buf.vertex(m, ox1, oy1, 0).color(cr, cg, cb, alpha).endVertex();
                buf.vertex(m, ix1, iy1, 0).color(cr, cg, cb, alpha).endVertex();
            }
        }

        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }

    /** Her dilimin orta açısına 32x32 büyü ikonunu (bilinmeyene kilit) çizer. */
    private void drawIcons(GuiGraphics g, List<Spell> spells, int cx, int cy,
                           float innerR, float outerR, float animAlpha) {
        int n = spells.size();
        double step = Math.PI * 2.0 / n;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int i = 0; i < n; i++) {
            Spell s = spells.get(i);
            boolean hov = i == hovered;
            boolean known = ClientSpellData.knows(s);

            float rIn = innerR + (hov ? 3f : 0f);
            float rOut = outerR + (hov ? 6f : 0f);
            double a = -Math.PI / 2.0 + i * step;
            int x = cx + (int) (Math.cos(a) * (rIn + rOut) / 2.0);
            int y = cy + (int) (Math.sin(a) * (rIn + rOut) / 2.0);

            PoseStack pose = g.pose();
            if (hov) { // hover'da ikon 1.15x büyür
                pose.pushPose();
                pose.translate(x, y, 0);
                pose.scale(1.15f, 1.15f, 1f);
                pose.translate(-x, -y, 0);
            }
            // bilinmeyen büyü ikonu %40 alfa
            RenderSystem.setShaderColor(1f, 1f, 1f, (known ? 1f : 0.4f) * animAlpha);
            g.blit(icon(s), x - 16, y - 16, 0f, 0f, 32, 32, 32, 32);
            if (!known) {
                RenderSystem.setShaderColor(1f, 1f, 1f, animAlpha);
                g.blit(LOCK_TEX, x - 8, y - 8, 0f, 0f, 16, 16, 16, 16);
            }
            if (hov) {
                pose.popPose();
            }
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** Merkez bilgi metni: büyü adı + mana/cooldown/tier, ya da ipucu satırı. */
    private void drawCenterText(GuiGraphics g, List<Spell> spells, int cx, int cy, float animAlpha) {
        int a = (int) (255 * animAlpha);
        if (a < 8) { // MC fontu çok düşük alfayı opak çizer — hiç çizme
            return;
        }
        if (hovered >= 0) {
            Spell s = spells.get(hovered);
            boolean known = ClientSpellData.knows(s);
            g.drawCenteredString(this.font, s.name(), cx, cy - 14, (a << 24) | (s.color() & 0xFFFFFF));
            String info = "✦ " + s.manaCost()
                    + "  ·  " + String.format(Locale.ROOT, "%.1f", s.cooldown() / 20.0) + " sn";
            g.drawCenteredString(this.font,
                    Component.literal(info + "  ·  ")
                            .append(Component.translatable("arcanum.tier." + s.tier())),
                    cx, cy - 2, (a << 24) | 0xBFC4D0);
            if (!known) {
                g.drawCenteredString(this.font,
                        Component.translatable("arcanum.wheel_locked"), cx, cy + 10, (a << 24) | 0xFF6A5E);
            }
        } else {
            g.drawCenteredString(this.font,
                    Component.translatable("arcanum.wheel_hint"), cx, cy - 4, (a << 24) | 0x9098A8);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<Spell> spells = pageSpells();
        if (button == 0 && hovered >= 0 && hovered < spells.size()) {
            Spell s = spells.get(hovered);
            if (!ClientSpellData.knows(s)) {
                // kilitli büyü: seçme, kısa "reddet" sesi çal, menü açık kalsın
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.6f));
                return true;
            }
            // sunucuya GLOBAL index gönderilir (sayfa ofseti + sayfa içi index)
            ArcanumNetwork.sendToServer(new SelectSpellPayload(page * PAGE_SIZE + hovered));
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.6f));
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Numpad 0-9: hover'daki büyüyü o kısayol slotuna ata
        if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
            int slot = keyCode - GLFW.GLFW_KEY_KP_0;
            List<Spell> spells = pageSpells();
            if (hovered >= 0 && hovered < spells.size()) {
                int globalIndex = page * PAGE_SIZE + hovered;
                Spell assigned = spells.get(hovered);
                SpellHotkeys.assign(slot, globalIndex);
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.6f));
                // GÖRÜNÜR onay: "X -> Kısayol N" action-bar mesajı (atamanın kaydolduğu
                // hissedilsin — eskiden yalnızca ses vardı, geri bildirim yoktu).
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.translatable("arcanum.hotkey_assigned", assigned.name(), slot),
                            true);
                }
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        // G (çark tuşu) da menüyü kapatır; ESC'yi super halleder
        if (ArcanumForgeClient.spellWheelKey != null
                && ArcanumForgeClient.spellWheelKey.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- yardımcılar ----

    /** Büyü ikonu dokusu (önbellekli). */
    private static ResourceLocation icon(Spell s) {
        return ICON_CACHE.computeIfAbsent(s.id(), id ->
                new ResourceLocation("arcanum", "textures/gui/spell/" + id + ".png"));
    }

    /** Rengi beyaza doğru f oranında açar (SpellFx.lighten mantığı, istemci lokali). */
    private static int lighten(int rgb, float f) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r += (int) ((255 - r) * f);
        g += (int) ((255 - g) * f);
        b += (int) ((255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }

    /** Bilinmeyen büyü dilimi: rengi desatüre edip koyulaştırır (gri-koyu ton). */
    private static int desaturateDark(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int luma = (int) (r * 0.3f + g * 0.59f + b * 0.11f);
        r = (int) ((r + (luma - r) * 0.8f) * 0.45f);
        g = (int) ((g + (luma - g) * 0.8f) * 0.45f);
        b = (int) ((b + (luma - b) * 0.8f) * 0.45f);
        return (r << 16) | (g << 8) | b;
    }
}
