package com.arcanum.client;

import java.util.ArrayList;
import java.util.List;

import com.arcanum.Arcanum;
import com.arcanum.menu.SpellTableMenu;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.network.LearnSpellPayload;
import com.arcanum.spell.Spell;
import com.arcanum.spell.SpellGating;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Büyü Masası GUI'si — sol panelde vanilla tarzı kitap/reagent + oyuncu
 * envanteri slotları, sağ panelde oyuncunun elindeki kitaba göre dinamik
 * "öğrenilebilir büyü" listesi (kaydırılabilir — hepsi sığmayabilir).
 * Tıklanan her buton {@link LearnSpellPayload} gönderir; TÜM doğrulama
 * sunucu tarafında yapılır (bkz. SpellGating). Sonuç mesajı vanilla
 * actionbar/chat yerine {@link #showFeedback} ile doğrudan bu ekranda
 * gösterilir (GUI açıkken HUD görünmüyor).
 */
public class SpellTableScreen extends AbstractContainerScreen<SpellTableMenu> {
    private static final Identifier TEXTURE = Arcanum.id("textures/gui/spell_table.png");

    /** Sağ panelin başlangıç x/y ofseti (leftPos/topPos'a göre) ve satır aralığı. */
    private static final int LIST_X = 184;
    private static final int LIST_Y = 20;
    private static final int LIST_ROW_HEIGHT = 18;
    /** Liste alanının görünür yüksekliği — bunun dışına taşan satırlar gizlenir. */
    private static final int LIST_VISIBLE_HEIGHT = 140;
    private static final int LIST_MAX_SCROLL_ROWS_VISIBLE = LIST_VISIBLE_HEIGHT / LIST_ROW_HEIGHT;

    private final List<SpellButton> spellButtons = new ArrayList<>();
    /** Son build'de kullanılan kitap item'ı + bilinen-büyü sayısı — değişim algılamak için. */
    private ItemStack lastBookSnapshot = ItemStack.EMPTY;
    private int lastKnownCount = -1;
    /** Liste kaydırma ofseti (satır cinsinden, 0 = en üstte). */
    private int scrollRows = 0;

    /** Ekranda kısa süreliğine gösterilen sonuç bildirimi (bkz. showFeedback). */
    private Component feedbackMessage;
    private boolean feedbackSuccess;
    private int feedbackTicks;

    public SpellTableScreen(SpellTableMenu menu, Inventory playerInv, Component title) {
        // 26.1: imageWidth/imageHeight final → boyut ctor'dan verilir (316×166).
        super(menu, playerInv, title, 316, 166);
    }

    @Override
    protected void init() {
        super.init();
        rebuildSpellButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack book = this.menu.getBookStack();
        int knownCount = ClientSpellData.knownSet().size();
        boolean bookChanged = !ItemStack.isSameItemSameComponents(book, lastBookSnapshot);
        if (bookChanged || knownCount != lastKnownCount) {
            rebuildSpellButtons();
        }
        if (feedbackTicks > 0) {
            feedbackTicks--;
        }
    }

    /** Sunucudan gelen öğrenme sonucunu ~2.5 saniyeliğine bu ekranda gösterir. */
    public void showFeedback(Component message, boolean success) {
        this.feedbackMessage = message;
        this.feedbackSuccess = success;
        this.feedbackTicks = 50;
    }

    /** Sağ paneldeki büyü listesini kitap/bilinen-büyü durumuna göre yeniden kurar. */
    private void rebuildSpellButtons() {
        for (SpellButton btn : spellButtons) {
            this.removeWidget(btn);
        }
        spellButtons.clear();

        ItemStack book = this.menu.getBookStack();
        lastBookSnapshot = book.copy();
        lastKnownCount = ClientSpellData.knownSet().size();
        scrollRows = 0;

        List<Spell> eligible = SpellGating.unlockableSpells(book, ClientSpellData.knownSet());

        for (Spell s : eligible) {
            SpellButton btn = new SpellButton(120, LIST_ROW_HEIGHT - 2, s);
            spellButtons.add(btn);
            this.addRenderableWidget(btn);
        }
        repositionButtons();
    }

    /** Kaydırma ofsetine göre her butonun ekran konumunu ve görünürlüğünü günceller. */
    private void repositionButtons() {
        int x = this.leftPos + LIST_X;
        int baseY = this.topPos + LIST_Y;
        for (int i = 0; i < spellButtons.size(); i++) {
            SpellButton btn = spellButtons.get(i);
            int row = i - scrollRows;
            boolean visible = row >= 0 && row < LIST_MAX_SCROLL_ROWS_VISIBLE;
            btn.setX(x);
            btn.setY(baseY + row * LIST_ROW_HEIGHT);
            btn.visible = visible;
            btn.active = visible;
        }
    }

    private int maxScrollRows() {
        return Math.max(0, spellButtons.size() - LIST_MAX_SCROLL_ROWS_VISIBLE);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= this.leftPos + LIST_X && mouseX <= this.leftPos + this.imageWidth
                && mouseY >= this.topPos + LIST_Y && mouseY <= this.topPos + LIST_Y + LIST_VISIBLE_HEIGHT
                && maxScrollRows() > 0) {
            scrollRows = Math.max(0, Math.min(maxScrollRows(), scrollRows - (int) Math.signum(scrollY)));
            repositionButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** 26.1: renderBg kalktı → extractBackground (super: yarı-saydam karartma, sonra doku). */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0f, 0f,
                this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
    }

    /**
     * Başlık ("Büyü Masası") + envanter etiketini AÇIK renkle çizer. Varsayılan
     * {@code renderLabels} 0x404040 (koyu gri) kullanıyordu; masanın mor/koyu dokusunda
     * okunmuyordu (tester). Başlık BEYAZ, envanter etiketi açık lavanta.
     * (26.1: metin renginde alfa ZORUNLU — alfa=0 görünmez çizilir.)
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFFFFF, false);
        g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFFD8CCF0, false);
    }

    /** Sağ paneldeki ipucu metnini panel iç genişliğine SARAR (eskiden tek satır sağdan taşıyordu). */
    private void drawWrappedHint(GuiGraphicsExtractor g, Component text, int x, int y) {
        int wrapWidth = this.imageWidth - LIST_X - 6; // sağ panelin iç genişliği (~126px)
        int line = 0;
        for (net.minecraft.util.FormattedCharSequence seq : this.font.split(text, wrapWidth)) {
            g.text(this.font, seq, x, y + line * (this.font.lineHeight + 1), 0xFFC8B8E0, false);
            line++;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // 26.1: super içerik + taşınan eşya + eşya tooltip'ini (extractTooltip) KENDİSİ çiziyor;
        // eskiden burada yapılan açık renderTooltip çağrısı çift çizim olurdu → kaldırıldı.
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        ItemStack book = this.menu.getBookStack();
        int hintX = this.leftPos + LIST_X;
        int hintY = this.topPos + LIST_Y;
        if (book.isEmpty()) {
            drawWrappedHint(g, Component.translatable("arcanum.spell_table.hint_need_book"), hintX, hintY);
        } else if (spellButtons.isEmpty()) {
            drawWrappedHint(g, Component.translatable("arcanum.spell_table.hint_nothing_left"), hintX, hintY);
        }

        if (feedbackTicks > 0 && feedbackMessage != null) {
            int color = feedbackSuccess ? 0xFFFFD8A6 : 0xFFFF8080;
            int fx = this.leftPos + 8;
            int fy = this.topPos - 12;
            g.fill(fx - 4, fy - 3, fx + this.imageWidth - 8, fy + 11, 0xA0000000);
            g.text(this.font, feedbackMessage, fx, fy, color, true);
        }

        renderSpellButtonTooltip(g, mouseX, mouseY);
    }

    /**
     * Fare üstündeki öğrenilebilir büyü satırı için tooltip: büyünün adı +
     * gerekli reagent + gereken XP seviyesi. Reagent adı client'ta registry'den
     * çözülür (per-spell haritası: {@link SpellGating#requiredReagentId}).
     */
    private void renderSpellButtonTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        SpellButton hovered = null;
        for (SpellButton btn : spellButtons) {
            if (btn.visible && btn.isMouseOver(mouseX, mouseY)) {
                hovered = btn;
                break;
            }
        }
        if (hovered == null) {
            return;
        }
        Spell spell = hovered.spell;
        List<Component> lines = new ArrayList<>();
        lines.add(spell.name());
        // Büyünün ne yaptığını anlatan açıklama satırı (tester "büyü açıklaması yok" dedi).
        lines.add(Component.translatable("spell.arcanum." + spell.id() + ".desc")
                .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
        lines.add(Component.translatable("arcanum.spell_table.req_reagent", reagentName(spell)));
        lines.add(Component.translatable("arcanum.spell_table.req_level", SpellGating.xpCost(spell.tier())));
        g.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    /** Büyünün gerektirdiği reagent'in görünen adını registry'den çözer. */
    private static Component reagentName(Spell spell) {
        String id = SpellGating.requiredReagentId(spell);
        if (id != null) {
            Identifier loc = Identifier.tryParse(id);
            if (loc != null && BuiltInRegistries.ITEM.containsKey(loc)) {
                return BuiltInRegistries.ITEM.getValue(loc).getDefaultInstance().getHoverName();
            }
        }
        return Component.literal(id == null ? "?" : id);
    }

    /** Tek bir öğrenilebilir büyü satırı — tıklanınca LearnSpellPayload gönderir. */
    private static final class SpellButton extends AbstractWidget {
        private final Spell spell;
        private final net.minecraft.client.gui.Font font;

        SpellButton(int width, int height, Spell spell) {
            super(0, 0, width, height, spell.name());
            this.spell = spell;
            this.font = ClientCompat.mc().font;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            if (!this.visible) {
                return;
            }
            // Koyu (dark-tier) renkler GUI zemininde okunmuyordu → parlaklık tabanı uygula.
            int color = readable(spell.color() & 0xFFFFFF);
            if (this.isHovered()) {
                g.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0x40FFFFFF);
                color = lighten(color, 0.25f);
            }
            // Uzun büyü adları buton genişliğini aşıp panelin dışına taşıyordu → sığmıyorsa
            // sağdan "…" ile kırp (tam ad zaten tooltip'te görünür).
            int avail = this.getWidth() - 4;
            String label = spell.name().getString();
            if (font.width(label) > avail) {
                label = font.plainSubstrByWidth(label, avail - font.width("…")) + "…";
            }
            g.text(font, label, this.getX() + 2, this.getY() + 2, 0xFF000000 | color, false);
        }

        /**
         * Metin okunurluk tabanı: en parlak kanal 140'ın altındaysa rengi orantılı
         * yükseltir. Böylece dark/unforgivable büyülerin koyu temaları GUI zemininde
         * kaybolmaz (tester "yazılar çok koyu" dedi).
         */
        private static int readable(int rgb) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            int max = Math.max(r, Math.max(g, b));
            if (max < 140) {
                float f = 140f / Math.max(1, max);
                r = Math.min(255, (int) (r * f));
                g = Math.min(255, (int) (g * f));
                b = Math.min(255, (int) (b * f));
            }
            return (r << 16) | (g << 8) | b;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            ArcanumNetwork.sendToServer(new LearnSpellPayload(spell.id()));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }

        private static int lighten(int rgb, float f) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            r += (int) ((255 - r) * f);
            g += (int) ((255 - g) * f);
            b += (int) ((255 - b) * f);
            return (r << 16) | (g << 8) | b;
        }
    }
}
