package com.arcanum.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.arcanum.Arcanum;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.network.AssignSlotPayload;
import com.arcanum.network.RespecPayload;
import com.arcanum.network.SpendPointPayload;
import com.arcanum.spell.ModSpells;
import com.arcanum.spell.Spell;
import com.arcanum.spell.SpellTiers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;

/**
 * G ile açılan büyü DİZİLİM menüsü — radyal çarkın yerini alan sürükle-bırak ekranı.
 *
 * <p>SOL PANEL: oyuncunun 3 SAYFA × 4 SLOT (12 hücre) dizilimi. Her satır bir sayfa
 * (1/2/3 etiketi); aktif sayfa/slot altın çerçeveyle vurgulanır. Dolu bir hücrede o
 * büyünün ikonu görünür. SAĞ PANEL: TÜM 30 büyü, kademeye ({@link SpellTiers#TIER_ORDER})
 * göre gruplu, kaydırılabilir bir liste; kilitli büyüler %40 alfa + kilit ikonuyla
 * soluk gösterilir ve SÜRÜKLENEMEZ.
 *
 * <p>Akış: sağ panelde BİLİNEN bir büyüye sol-tık → sürükleme başlar, ikon imleçte
 * çizilir; sol paneldeki bir slota bırakılınca {@link AssignSlotPayload} ile sunucuya
 * atama isteği gider (sunucu doğrular + {@code SpellLoadoutPayload} ile geri sync eder,
 * UI kendiliğinden güncellenir). Sol paneldeki dolu bir slota SAĞ-TIK slotu temizler.
 * Tüm veri {@link ClientSpellData}'dan okunur; bu ekran hiçbir yerel durum tutmaz
 * (yalnızca geçici sürükleme + kaydırma). Dünya net kalır (isPauseScreen=false).
 */
public class SpellMenuScreen extends Screen {

    // ---- dizilim boyutları (SÖZLEŞME: PAGES=3, SLOTS=4, TOTAL=12) ----
    private static final int PAGES = 3;
    private static final int SLOTS = 4;

    // ---- sol panel yerleşimi ----
    private static final int SLOT = 22;      // slot hücresi kenarı
    private static final int SLOT_GAP = 6;   // slotlar arası yatay boşluk
    private static final int ROW_GAP = 8;    // sayfalar arası dikey boşluk
    private static final int LABEL_W = 12;   // "1/2/3" sayfa etiketi genişliği
    private static final int LEFT_ROWS_H = PAGES * SLOT + (PAGES - 1) * ROW_GAP;      // 82
    private static final int LEFT_PANEL_W = LABEL_W + 6 + (SLOTS * SLOT + (SLOTS - 1) * SLOT_GAP); // 124

    // ---- sağ panel yerleşimi ----
    private static final int GAP = 26;          // sol/sağ panel arası
    private static final int RIGHT_W = 176;     // liste genişliği
    private static final int LIST_VISIBLE_H = 168;
    private static final int ROW_H = 20;        // büyü satırı yüksekliği
    private static final int TIER_H = 18;       // kademe başlığı yüksekliği
    private static final int ICON_ROW = 16;     // sağ liste ikon boyutu
    private static final int ICON_SLOT = 18;    // sol slot ikon boyutu
    private static final int SCROLL_STEP = 20;

    private static final int HEADER_H = 22;     // panel başlığı için üst boşluk

    // ---- skill ağacı paneli (SAĞ, 3. panel) ----
    private static final int SKILL_W = 150;
    private static final int NODE = 11;         // düğüm hücresi kenarı
    private static final int NODE_GAP = 2;
    private static final int RESET_W = 66;
    private static final int RESET_H = 14;
    /** bölüm başına düğüm sayısı: kapasite, regen, cooldown, güç. */
    private static final int[] SECTION_MAX = {7, 7, 10, 7};
    /** bölüm renkleri: mana-mavi, dolma-camgöbeği, cooldown-altın, güç-mor/kırmızı. */
    private static final int[] SECTION_COLOR = {0xFF5AA0E0, 0xFF57CFC9, 0xFFE0B040, 0xFFD05A80};
    private static final String[] SECTION_KEY = {"mana_cap", "mana_regen", "cooldown", "power"};
    /** Düğüm hover tooltip değerleri (kapasite tam sayı; regen/güç metin). */
    private static final int[] CAP_VALS = {10, 15, 20, 20, 20, 20, 20};
    private static final String[] STEP_VALS = {"0.05", "0.10", "0.15", "0.20", "0.20", "0.20", "0.20"};

    private static final int TOTAL_W = LEFT_PANEL_W + GAP + RIGHT_W + GAP + SKILL_W;
    private static final int CONTENT_H = HEADER_H + LIST_VISIBLE_H;

    /** Son çizilen düğüm hücreleri (hit-test): her biri {section, index, x, y}. */
    private final List<int[]> nodeHits = new ArrayList<>();
    /** Son çizilen bölüm başlıkları (hit-test): her biri {section, x, y, width}. */
    private final List<int[]> headerHits = new ArrayList<>();
    private int resetX;
    private int resetY;

    // ---- tema renkleri ----
    private static final int C_OVERLAY = 0xA0000000;
    private static final int C_PANEL_BG = 0xB01A1226;
    private static final int C_PANEL_BORDER = 0xFF2E2440;
    private static final int C_SLOT_BG = 0x66120C1C;
    private static final int C_SLOT_BORDER = 0xFF3A3350;
    private static final int C_SLOT_BORDER_PAGE = 0xFF5A4E76; // aktif sayfanın slot çerçevesi
    private static final int C_SLOT_ACTIVE = 0xFFFFD8A6;      // altın: aktif slot
    private static final int C_SLOT_HOVER = 0xFFC8B8E0;       // lavanta: fare üstünde
    private static final int C_DROP_OK = 0xFF8CE0A0;          // yeşil: geçerli bırakma hedefi
    // 26.1: metin renginde alfa ZORUNLU (alfa=0 -> görünmez); eski 0xRRGGBB -> 0xFFRRGGBB
    private static final int C_LAVENDER = 0xFFC8B8E0;
    private static final int C_GOLD = 0xFFFFD8A6;
    private static final int C_HINT = 0xFF9098A8;
    private static final int C_ROW_HOVER = 0x30FFFFFF;

    private static final Identifier LOCK_TEX = Arcanum.id("textures/gui/lock.png");
    /** Büyü ikonu Identifier önbelleği (her karede yeniden yaratmamak için). */
    private static final Map<String, Identifier> ICON_CACHE = new HashMap<>();

    /** Sağ paneldeki tek bir görsel satır: başlık (spell==null) veya büyü. */
    private record ListRow(Spell spell, String tier, int globalIndex) {
        boolean isHeader() {
            return spell == null;
        }
    }

    private final List<ListRow> rows = new ArrayList<>();
    /** Sağ listenin toplam (kaydırılabilir) pixel yüksekliği. */
    private int contentHeight;
    /** Sağ liste kaydırma ofseti (pixel, 0 = en üstte). */
    private int scroll = 0;
    /** Sürüklenen büyünün GLOBAL index'i (ModSpells.SPELLS içinde); -1 = sürükleme yok. */
    private int draggingIndex = -1;

    public SpellMenuScreen() {
        super(Component.translatable("arcanum.menu.title"));
        buildRows();
        // açılış: kitap sayfası sesi ("büyü defteri açıldı")
        ClientCompat.mc().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.3f));
    }

    /** Sağ panel satırlarını (kademe başlıkları + büyüler) sabit sırayla kurar. */
    private void buildRows() {
        rows.clear();
        int h = 0;
        for (String tier : SpellTiers.TIER_ORDER) {
            List<Spell> tierSpells = SpellTiers.allOfTier(tier);
            if (tierSpells.isEmpty()) {
                continue;
            }
            rows.add(new ListRow(null, tier, -1));
            h += TIER_H;
            for (Spell s : tierSpells) {
                rows.add(new ListRow(s, tier, ModSpells.SPELLS.indexOf(s)));
                h += ROW_H;
            }
        }
        contentHeight = h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Vanilla arka plan blur'unu kapat — dünya net kalsın. */
    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor g) {
    }

    // ---------------------------------------------------------------- render

    /**
     * 26.1: arka plan ayrı katmanda (extractBackground) çiziliyor. Eski sıra korunur:
     * önce net kalan hafif karartma (tam ekran, ölçeksiz), SONRA vanilla menü arka planı.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, C_OVERLAY);
        super.extractBackground(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        // dar pencerede tüm menüyü küçült + fareyi aynı orana çevir (skill paneli taşmasın)
        float s = uiScale();
        int mx = (int) (mouseX / s);
        int my = (int) (mouseY / s);
        boolean scaled = s < 0.999f;
        if (scaled) {
            g.pose().pushMatrix();
            g.pose().scale(s, s);
        }

        drawPanelBoxes(g);
        drawLeftPanel(g, mx, my);
        drawRightPanel(g, mx, my);
        drawSkillPanel(g, mx, my);
        drawFooter(g);

        // sürüklenen ikon her zaman en üstte, imleçte
        if (draggingIndex >= 0 && draggingIndex < ModSpells.SPELLS.size()) {
            drawSpellIcon(g, ModSpells.SPELLS.get(draggingIndex), mx - 9, my - 9, 18, 0.9f);
        } else {
            // sürükleme yokken hover tooltip'i. 26.1: tooltip ertelenmiş çiziliyor (kare sonunda,
            // ölçeksiz pose) -> isabet testi sanal (mx,my), konum GERÇEK fare koordinatı.
            drawTooltips(g, mx, my, mouseX, mouseY);
        }

        if (scaled) {
            g.pose().popMatrix();
        }
    }

    /** Sol (dizilim) ve sağ (büyüler) panellerinin yarı-saydam kutularını çizer. */
    private void drawPanelBoxes(GuiGraphicsExtractor g) {
        int cl = contentLeft();
        int ct = contentTop();

        int lx0 = cl - 8;
        int ly0 = ct - 8;
        int lx1 = cl + LEFT_PANEL_W + 8;
        int ly1 = rowsTop() + LEFT_ROWS_H + 8;
        g.fill(lx0, ly0, lx1, ly1, C_PANEL_BG);
        drawBorder(g, lx0, ly0, lx1, ly1, C_PANEL_BORDER);

        int rx0 = listX() - 8;
        int rx1 = listX() + RIGHT_W + 8;
        int ry1 = listY() + LIST_VISIBLE_H + 8;
        g.fill(rx0, ly0, rx1, ry1, C_PANEL_BG);
        drawBorder(g, rx0, ly0, rx1, ry1, C_PANEL_BORDER);

        // skill ağacı kutusu (aynı üst/alt hizada)
        int sx0 = skillX() - 8;
        int sx1 = skillX() + SKILL_W + 8;
        g.fill(sx0, ly0, sx1, ry1, C_PANEL_BG);
        drawBorder(g, sx0, ly0, sx1, ry1, C_PANEL_BORDER);
    }

    /** Sol panel: 3 sayfa × 4 slot dizilimi + sayfa etiketleri + aktif vurgu. */
    private void drawLeftPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int cl = contentLeft();
        g.text(this.font, Component.translatable("arcanum.menu.loadout"), cl, contentTop(), C_LAVENDER, false);

        int activePage = ClientSpellData.activePage();
        int activeSlot = ClientSpellData.activeSlot();
        int[] hov = slotAt(mouseX, mouseY);

        for (int p = 0; p < PAGES; p++) {
            // sayfa etiketi "1/2/3" (aktif sayfa altın)
            int labelColor = (p == activePage) ? C_GOLD : 0xFF8890A0;
            int ly = slotY(p) + (SLOT - this.font.lineHeight) / 2 + 1;
            g.text(this.font, String.valueOf(p + 1), cl, ly, labelColor, false);

            for (int s = 0; s < SLOTS; s++) {
                int sx = slotX(s);
                int sy = slotY(p);
                g.fill(sx, sy, sx + SLOT, sy + SLOT, C_SLOT_BG);

                boolean isHov = hov != null && hov[0] == p && hov[1] == s;
                int border;
                if (isHov) {
                    border = (draggingIndex >= 0) ? C_DROP_OK : C_SLOT_HOVER;
                } else if (p == activePage && s == activeSlot) {
                    border = C_SLOT_ACTIVE;
                } else if (p == activePage) {
                    border = C_SLOT_BORDER_PAGE;
                } else {
                    border = C_SLOT_BORDER;
                }
                drawBorder(g, sx, sy, sx + SLOT, sy + SLOT, border);

                int gi = ClientSpellData.loadoutSlot(p, s);
                if (gi >= 0 && gi < ModSpells.SPELLS.size()) {
                    drawSpellIcon(g, ModSpells.SPELLS.get(gi), sx + 2, sy + 2, ICON_SLOT, 1f);
                }
            }
        }
    }

    /** Sağ panel: kademeye göre gruplu, kaydırılabilir tüm büyü listesi (scissor ile kırpılı). */
    private void drawRightPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, Component.translatable("arcanum.menu.spells"), listX(), contentTop(), C_LAVENDER, false);

        clampScroll();
        int x = listX();
        int yTop = listY();
        boolean over = inList(mouseX, mouseY);

        g.enableScissor(x, yTop, x + RIGHT_W, yTop + LIST_VISIBLE_H);
        int y = yTop - scroll;
        for (ListRow row : rows) {
            if (row.isHeader()) {
                if (y + TIER_H > yTop && y < yTop + LIST_VISIBLE_H) {
                    g.text(this.font, Component.translatable("arcanum.tier." + row.tier()),
                            x + 2, y + 6, C_GOLD, false);
                    g.fill(x + 2, y + TIER_H - 1, x + RIGHT_W - 2, y + TIER_H, 0x40FFD8A6);
                }
                y += TIER_H;
            } else {
                if (y + ROW_H > yTop && y < yTop + LIST_VISIBLE_H) {
                    Spell s = row.spell();
                    boolean known = ClientSpellData.knows(s);
                    boolean rowHover = over && mouseX >= x && mouseX < x + RIGHT_W
                            && mouseY >= y && mouseY < y + ROW_H;
                    if (rowHover && known) {
                        g.fill(x, y, x + RIGHT_W, y + ROW_H, C_ROW_HOVER);
                    }
                    drawSpellIcon(g, s, x + 2, y + 2, ICON_ROW, known ? 1f : 0.4f);
                    if (!known) {
                        drawLock(g, x + 2 + ICON_ROW - 10, y + 2 + ICON_ROW - 10, 10);
                    }
                    // bilinen: tam alfa kendi rengi; kilitli: soluk (~%40 alfa)
                    int nameColor = known
                            ? (0xFF000000 | (s.color() & 0xFFFFFF))
                            : (0x66000000 | (s.color() & 0xFFFFFF));
                    g.text(this.font, s.name(), x + ICON_ROW + 8,
                            y + (ROW_H - this.font.lineHeight) / 2 + 1, nameColor, false);
                }
                y += ROW_H;
            }
        }
        g.disableScissor();

        drawScrollbar(g);
    }

    /** İçerik görünür alandan taşıyorsa sağ kenarda ince kaydırma çubuğu. */
    private void drawScrollbar(GuiGraphicsExtractor g) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int trackX = listX() + RIGHT_W - 2;
        int trackTop = listY();
        int thumbH = Math.max(16, (int) ((float) LIST_VISIBLE_H / contentHeight * LIST_VISIBLE_H));
        int thumbY = trackTop + (int) ((LIST_VISIBLE_H - thumbH) * (scroll / (float) max));
        g.fill(trackX, trackTop, trackX + 2, trackTop + LIST_VISIBLE_H, 0x30FFFFFF);
        g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x90C8B8E0);
    }

    /** Sağ-3. panel: büyücü level/XP + 3 bölmeli skill ağacı (düğüm satın alma + reset). */
    private void drawSkillPanel(GuiGraphicsExtractor g, int mx, int my) {
        nodeHits.clear();
        headerHits.clear();
        int x = skillX();
        g.text(this.font, Component.translatable("arcanum.skill.title"), x, contentTop(), C_LAVENDER, false);

        int y = skillY();
        int lvl = ClientMagicData.level();
        g.text(this.font, Component.translatable("arcanum.skill.level", lvl, ClientMagicData.maxLevel()),
                x, y, C_GOLD, false);
        // XP bar
        int barY = y + 11;
        int xpNext = Math.max(1, ClientMagicData.xpToNext());
        float xpFrac = lvl >= ClientMagicData.maxLevel() ? 1f
                : Math.max(0f, Math.min(1f, ClientMagicData.xp() / (float) xpNext));
        g.fill(x, barY, x + SKILL_W, barY + 4, 0xFF1B1428);
        g.fill(x, barY, x + Math.round(SKILL_W * xpFrac), barY + 4, 0xFF9FE0FF);
        // kalan puan
        int pts = ClientMagicData.unspent();
        g.text(this.font, Component.translatable("arcanum.skill.points", pts),
                x, barY + 8, pts > 0 ? C_GOLD : C_HINT, false);

        int rowY = barY + 22;
        for (int sec = 0; sec < 4; sec++) {
            int col = SECTION_COLOR[sec];
            Component label = Component.translatable("arcanum.skill." + SECTION_KEY[sec]);
            g.text(this.font, label, x, rowY, col, false);
            // başlık hover → bu bölümün ne işe yaradığını açıklayan tooltip (hit-rect kaydı)
            headerHits.add(new int[]{sec, x, rowY, this.font.width(label)});
            rowY += 11;
            int cur = ClientMagicData.nodes(sec);
            int max = SECTION_MAX[sec];
            for (int i = 0; i < max; i++) {
                int nx = x + i * (NODE + NODE_GAP);
                int ny = rowY;
                boolean filled = i < cur;
                boolean isNext = i == cur && pts > 0;   // satın alınabilir bir sonraki düğüm
                g.fill(nx, ny, nx + NODE, ny + NODE, filled ? col : C_SLOT_BG);
                drawBorder(g, nx, ny, nx + NODE, ny + NODE, isNext ? 0xFFFFFFFF : C_SLOT_BORDER);
                nodeHits.add(new int[]{sec, i, nx, ny});
            }
            rowY += NODE + 8;
        }

        // Reset (ücretsiz respec) butonu
        resetX = x;
        resetY = rowY + 2;
        boolean hovR = mx >= resetX && mx < resetX + RESET_W && my >= resetY && my < resetY + RESET_H;
        g.fill(resetX, resetY, resetX + RESET_W, resetY + RESET_H, hovR ? C_ROW_HOVER : C_SLOT_BG);
        drawBorder(g, resetX, resetY, resetX + RESET_W, resetY + RESET_H, C_SLOT_BORDER);
        g.text(this.font, Component.translatable("arcanum.skill.reset"), resetX + 6, resetY + 3, C_GOLD, false);
    }

    /** Alt orta: kullanım ipucu satırı. */
    private void drawFooter(GuiGraphicsExtractor g) {
        int leftBottom = rowsTop() + LEFT_ROWS_H + 8;
        int rightBottom = listY() + LIST_VISIBLE_H + 8;
        int y = Math.max(leftBottom, rightBottom) + 8;
        g.centeredText(this.font,
                Component.translatable("arcanum.menu.hints"),
                this.width / 2, y, C_HINT);
    }

    /** Fare üstündeki slot/büyü için tooltip (sürükleme yokken çağrılır). */
    private void drawTooltips(GuiGraphicsExtractor g, int mouseX, int mouseY, int tipX, int tipY) {
        // skill düğümü hover → ne verdiğini göster
        for (int[] h : nodeHits) {
            if (mouseX >= h[2] && mouseX < h[2] + NODE && mouseY >= h[3] && mouseY < h[3] + NODE) {
                int sec = h[0];
                int i = h[1];
                Component c = switch (sec) {
                    case 0 -> Component.translatable("arcanum.skill.desc.cap", CAP_VALS[i]);
                    case 1 -> Component.translatable("arcanum.skill.desc.regen", STEP_VALS[i]);
                    case 2 -> Component.translatable("arcanum.skill.desc.cooldown");
                    default -> Component.translatable("arcanum.skill.desc.power", STEP_VALS[i]);
                };
                g.setTooltipForNextFrame(this.font, c, tipX, tipY);
                return;
            }
        }
        // bölüm BAŞLIĞI hover → bu yeteneğin ne işe yaradığını açıklayan tooltip
        // ("Cooldown" gibi tek kelimeler kafa karıştırmasın diye).
        for (int[] h : headerHits) {
            int sec = h[0];
            if (mouseX >= h[1] && mouseX < h[1] + h[3] && mouseY >= h[2] - 1 && mouseY < h[2] + 9) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("arcanum.skill." + SECTION_KEY[sec])
                        .withStyle(ChatFormatting.WHITE));
                lines.add(Component.translatable("arcanum.skill.info." + SECTION_KEY[sec])
                        .withStyle(ChatFormatting.GRAY));
                g.setComponentTooltipForNextFrame(this.font, lines, tipX, tipY);
                return;
            }
        }
        int[] hov = slotAt(mouseX, mouseY);
        if (hov != null) {
            int gi = ClientSpellData.loadoutSlot(hov[0], hov[1]);
            if (gi >= 0 && gi < ModSpells.SPELLS.size()) {
                g.setTooltipForNextFrame(this.font, ModSpells.SPELLS.get(gi).name(), tipX, tipY);
            }
            return;
        }
        ListRow row = rowAt(mouseX, mouseY);
        if (row != null && !row.isHeader()) {
            Spell s = row.spell();
            List<Component> lines = new ArrayList<>();
            lines.add(s.name());
            // Büyünün ne yaptığını anlatan açıklama (tester "büyü açıklaması yok" dedi) —
            // kilitli büyülerde de görünür ki oyuncu ne öğreneceğine karar verebilsin.
            lines.add(Component.translatable("spell.arcanum." + s.id() + ".desc")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            if (!ClientSpellData.knows(s)) {
                lines.add(Component.translatable("arcanum.wheel_locked").withStyle(ChatFormatting.RED));
            }
            g.setComponentTooltipForNextFrame(this.font, lines, tipX, tipY);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x() / uiScale(); // sanal (ölçek-öncesi) koordinata çevir
        double mouseY = event.y() / uiScale();
        int button = event.button();
        if (button == 0) {
            // sağ panelde BİLİNEN bir büyüye bas → sürüklemeyi başlat
            ListRow row = rowAt(mouseX, mouseY);
            if (row != null && !row.isHeader()) {
                if (ClientSpellData.knows(row.spell())) {
                    draggingIndex = row.globalIndex();
                    playClick(1.4f);
                } else {
                    playClick(0.6f); // kilitli: sürüklenemez, nazik "reddet"
                }
                return true;
            }
            // Skill: Reset (ücretsiz respec)
            if (mouseX >= resetX && mouseX < resetX + RESET_W && mouseY >= resetY && mouseY < resetY + RESET_H) {
                ArcanumNetwork.sendToServer(new RespecPayload());
                playClick(0.8f);
                return true;
            }
            // Skill: düğüm hücresine tık → o bölüme puan harca (sunucu cap/puan doğrular)
            for (int[] h : nodeHits) {
                if (mouseX >= h[2] && mouseX < h[2] + NODE && mouseY >= h[3] && mouseY < h[3] + NODE) {
                    if (ClientMagicData.unspent() > 0) {
                        ArcanumNetwork.sendToServer(new SpendPointPayload(h[0]));
                        playClick(1.2f);
                    } else {
                        playClick(0.5f); // puan yok
                    }
                    return true;
                }
            }
        } else if (button == 1) {
            // sol paneldeki dolu slota sağ-tık → temizle
            int[] hov = slotAt(mouseX, mouseY);
            if (hov != null && ClientSpellData.loadoutSlot(hov[0], hov[1]) >= 0) {
                ArcanumNetwork.sendToServer(new AssignSlotPayload(hov[0], hov[1], -1));
                playClick(0.9f);
                return true;
            }
        }
        return super.mouseClicked(new MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingIndex >= 0) {
            return true; // sürüklenen ikon extractRenderState()'te imleçte çizilir
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x() / uiScale();
        double mouseY = event.y() / uiScale();
        int button = event.button();
        if (button == 0 && draggingIndex >= 0) {
            int[] hov = slotAt(mouseX, mouseY);
            if (hov != null) {
                // sunucu atar + SpellLoadoutPayload ile geri sync eder
                ArcanumNetwork.sendToServer(new AssignSlotPayload(hov[0], hov[1], draggingIndex));
                playClick(1.6f);
            } else {
                playClick(0.5f); // boşa bırakıldı
            }
            draggingIndex = -1;
            return true;
        }
        return super.mouseReleased(new MouseButtonEvent(mouseX, mouseY, event.buttonInfo()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        mouseX /= uiScale();
        mouseY /= uiScale();
        if (inList(mouseX, mouseY) && maxScroll() > 0 && scrollY != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY) * SCROLL_STEP));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // G (menü tuşu) menüyü kapatır; ESC'yi super halleder
        if (ArcanumKeys.spellWheelKey.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    // ---------------------------------------------------------------- geometri

    /**
     * Pencere TOTAL_W'den darsa (yüksek guiScale / küçük ekran) tüm menü bu oranla
     * KÜÇÜLTÜLÜR ki 3. panel (skill ağacı) ekran dışına taşmasın. Fare koordinatları da
     * aynı oranla bölünür (bkz. extractRenderState/mouse* + sanal genişlik).
     */
    private float uiScale() {
        return Math.min(1f, (this.width - 8f) / TOTAL_W);
    }

    /** Ölçek-öncesi (sanal) genişlik — layout bunu kullanır; ölçek altında ekrana sığar. */
    private int vWidth() {
        return Math.round(this.width / uiScale());
    }

    private int vHeight() {
        return Math.round(this.height / uiScale());
    }

    private int contentLeft() {
        return Math.max(4, (vWidth() - TOTAL_W) / 2);
    }

    private int contentTop() {
        return Math.max(20, (vHeight() - (CONTENT_H + 30)) / 2);
    }

    private int skillX() {
        return listX() + RIGHT_W + GAP;
    }

    private int skillY() {
        return contentTop() + HEADER_H;
    }

    private int rowsTop() {
        return contentTop() + HEADER_H;
    }

    private int slotsX() {
        return contentLeft() + LABEL_W + 6;
    }

    private int slotX(int slot) {
        return slotsX() + slot * (SLOT + SLOT_GAP);
    }

    private int slotY(int page) {
        return rowsTop() + page * (SLOT + ROW_GAP);
    }

    private int listX() {
        return contentLeft() + LEFT_PANEL_W + GAP;
    }

    private int listY() {
        return contentTop() + HEADER_H;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - LIST_VISIBLE_H);
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    /** Fare hangi sol slotun üstünde → {page, slot}, hiçbiri değilse null. */
    private int[] slotAt(double mx, double my) {
        for (int p = 0; p < PAGES; p++) {
            for (int s = 0; s < SLOTS; s++) {
                int sx = slotX(s);
                int sy = slotY(p);
                if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) {
                    return new int[]{p, s};
                }
            }
        }
        return null;
    }

    private boolean inList(double mx, double my) {
        int x = listX();
        int y = listY();
        return mx >= x && mx < x + RIGHT_W && my >= y && my < y + LIST_VISIBLE_H;
    }

    /** Fare hangi sağ liste satırının üstünde (kaydırma dahil) → ListRow, yoksa null. */
    private ListRow rowAt(double mx, double my) {
        if (!inList(mx, my)) {
            return null;
        }
        int y = listY() - scroll;
        for (ListRow row : rows) {
            int h = row.isHeader() ? TIER_H : ROW_H;
            if (my >= y && my < y + h) {
                return row;
            }
            y += h;
        }
        return null;
    }

    // ---------------------------------------------------------------- çizim yardımcıları

    private static void drawBorder(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);      // üst
        g.fill(x0, y1 - 1, x1, y1, color);      // alt
        g.fill(x0, y0, x0 + 1, y1, color);      // sol
        g.fill(x1 - 1, y0, x1, y1, color);      // sağ
    }

    /**
     * 32×32 büyü ikonunu verilen boyutta (alfa ile) çizer. 26.1: RenderSystem blend/shaderColor
     * kalktı -> alfa blit'in ARGB renk parametresiyle (GUI_TEXTURED zaten harmanlı).
     */
    private static void drawSpellIcon(GuiGraphicsExtractor g, Spell s, int x, int y, int size, float alpha) {
        g.blit(RenderPipelines.GUI_TEXTURED, icon(s), x, y, 0f, 0f, size, size, 32, 32, 32, 32,
                ARGB.white(alpha));
    }

    /** 16×16 kilit dokusunu verilen boyutta çizer (kilitli büyü rozeti). */
    private static void drawLock(GuiGraphicsExtractor g, int x, int y, int size) {
        g.blit(RenderPipelines.GUI_TEXTURED, LOCK_TEX, x, y, 0f, 0f, size, size, 16, 16, 16, 16);
    }

    private static Identifier icon(Spell s) {
        return ICON_CACHE.computeIfAbsent(s.id(), id -> Arcanum.id("textures/gui/spell/" + id + ".png"));
    }

    private static void playClick(float pitch) {
        ClientCompat.mc().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }
}
