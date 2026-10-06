package com.arcanum.devtest;

import com.arcanum.client.ClientMagicData;
import com.arcanum.client.ClientSpellData;
import com.arcanum.client.ClientUmbraForms;
import com.arcanum.client.SpellMenuScreen;
import com.arcanum.client.SpellTableScreen;
import com.arcanum.item.WandItem;
import com.arcanum.network.AssignSlotPayload;
import com.arcanum.network.SetActivePayload;
import com.arcanum.spell.Spell;
import com.arcanum.spell.SpellCastTime;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * S1–S9 senaryoları. Sahne: (0.5, {@link #Y}, 0.5) noktasında 41×41 çimen platform (her dünyada aynı —
 * tekil oyunculuk düz dünyası VE çok oyunculu normal dünya); oyuncu +Z'ye (yaw 0) bakar.
 * Sunucu tarafı işler oyuncunun komut yoluyla ({@code /give}, {@code /summon}, {@code /arcanum ...}); büyü
 * dizilimi G menüsünün kullandığı C2S paketleriyle ({@link AssignSlotPayload}, {@link SetActivePayload}); asa
 * kullanımı {@code MultiPlayerGameMode#useItem} / kanal büyülerinde basılı-tut ({@code keyUse.setDown}).
 */
final class Scenarios {
    private Scenarios() {}

    static final List<String> ALL = List.of(
            "hud", "envanter", "gmenu", "masa", "yaratiklar", "pelerin", "buyu", "partikul", "yaprak");

    /** Yalnız ADIYLA istenen G4 ek senaryoları ("all" bunları KAPSAMAZ — varsayılan 108 adım değişmez). */
    static final List<String> OPT_IN = List.of("soyma", "isin", "imperio", "kafa", "ticaret", "tuccar", "jei", "grindylow");

    /** Sahne tabanı (ayak Y'si). Normal dünyada da boş gökyüzü olsun diye yüksekte. */
    static final int Y = 120;
    static final String BASE_TP = "tp @s 0.5 " + Y + " 0.5 ";

    static Plan build(List<String> names) {
        Plan p = new Plan();
        setup(p);
        for (String n : ALL) { // sıra sabit (bağımlılık: setup → hepsi); liste yalnız filtreler
            if (!names.contains(n)) {
                continue;
            }
            switch (n) {
                case "hud" -> hud(p);
                case "envanter" -> envanter(p);
                case "gmenu" -> gmenu(p);
                case "masa" -> masa(p);
                case "yaratiklar" -> yaratiklar(p);
                case "pelerin" -> pelerin(p);
                case "buyu" -> buyu(p);
                case "partikul" -> partikul(p);
                case "yaprak" -> yaprak(p);
                default -> { }
            }
            reset(p, n);
        }
        // Yalnız ADIYLA istenirse (ALL'a dahil DEĞİL — varsayılan 108 adımlık koşu değişmez): G4 soyma kontrolü.
        if (names.contains("soyma")) {
            soyma(p);
            reset(p, "soyma");
        }
        if (names.contains("isin")) {
            isin(p);
            reset(p, "isin");
        }
        if (names.contains("imperio")) {
            imperio(p);
            reset(p, "imperio");
        }
        if (names.contains("kafa")) {
            kafa(p);
            reset(p, "kafa");
        }
        if (names.contains("ticaret")) {
            ticaret(p);
            reset(p, "ticaret");
        }
        // FR3: WizardTraderEntity ticareti. ticaret'ten SONRA: kayıt kontrolünün köylü tanığı o senaryonun köylüsü.
        if (names.contains("tuccar")) {
            tuccar(p);
            reset(p, "tuccar");
        }
        // G8: JEI uyumu (yalniz uretim istemcisinde JEI yukluyse; JEI'ye DERLEME bagimliligi YOK - yansima).
        if (names.contains("jei")) {
            jei(p);
            reset(p, "jei");
        }
        // G9: grindylow doğal doğum kuralı değişti; elle çağırma yolları etkilenmemeli.
        if (names.contains("grindylow")) {
            grindylow(p);
            reset(p, "grindylow");
        }
        return p;
    }

    /** Envanterdeki (ana envanter + hotbar) toplam eşya adedi. */
    private static int invCount(DevTest.Ctx c, String itemId) {
        int n = 0;
        var inv = c.player().getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(itemId)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static net.minecraft.world.entity.npc.villager.@Nullable Villager tradeVillager(DevTest.Ctx c) {
        for (net.minecraft.world.entity.Entity e : c.level().getEntities((net.minecraft.world.entity.Entity) null,
                c.player().getBoundingBox().inflate(12), en -> en instanceof net.minecraft.world.entity.npc.villager.Villager)) {
            return (net.minecraft.world.entity.npc.villager.Villager) e;
        }
        return null;
    }

    /**
     * Opt-in (G6): wizard köylüsüyle GERÇEK ticaret. Hayatta kalma modu; 4 zümrüt; {@code arcanum:wizard} meslekli
     * seviye-1 köylü (teklifler trade_set veri paketinden tembel üretilir) → {@code gameMode.interact} (sağ tık) →
     * MerchantScreen → teklif 0 seçimi (MerchantScreen#postButtonClick'in birebir aynısı) → sonuç yuvasına
     * QUICK_MOVE. Kontrol: zümrüt 4→0, teklif 4→12 kitap (kök: ItemsForEmeralds(book,4,12,5)); teslim edilen adet vanilla
     * MerchantContainer#setItem kırpmasıyla min(12, istif sınırı 1) = 1 (1.21.1'de de aynı kod yolu — javap).
     * Köylü (tag "ticaret") ve eşyalar BİLEREK bırakılır (çok oyunculuda sunucu yeniden başlatma/codec testi için).
     */
    private static int tradeExpect = -1;

    private static void ticaret(Plan p) {
        p.scenario("ticaret");
        p.act("hazirla", 40, c -> {
            c.cmd("kill @e[tag=ticaret]", "gamemode survival @s", "clear @s", "effect clear @s", BASE_TP + "0 20",
                    "item replace entity @s hotbar.8 with minecraft:emerald 4",
                    "summon minecraft:villager 0.5 " + Y + " 2.5 {VillagerData:{profession:\"arcanum:wizard\",level:1,type:\"minecraft:plains\"},"
                            + "NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Rotation:[180f,0f],Tags:[\"ticaret\"]}");
            c.hotbar(0);
        }, c -> {
            if (tradeVillager(c) == null) return "istemcide koylu yok";
            if (invCount(c, "minecraft:emerald") != 4) return "zumrut " + invCount(c, "minecraft:emerald");
            return c.player().getAbilities().instabuild ? "hayatta kalma modunda degil" : null;
        });
        p.shotUntil("ekran", 2, 80, c -> {
            var v = tradeVillager(c);
            if (v != null) {
                var r = c.mc().gameMode.interact(c.player(), v,
                        new net.minecraft.world.phys.EntityHitResult(v, v.position().add(0, 1.0, 0)), InteractionHand.MAIN_HAND);
                c.note("interact=" + r);
            }
        }, c -> c.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu m && !m.getOffers().isEmpty(), c -> {
            if (!(c.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu m)) return "MerchantMenu acilmadi";
            var o = m.getOffers().get(0);
            String d = String.format(Locale.ROOT, "teklif0 %dx%s -> %dx%s maxUses=%d xp=%d",
                    o.getBaseCostA().getCount(), BuiltInRegistries.ITEM.getKey(o.getBaseCostA().getItem()),
                    o.getResult().getCount(), BuiltInRegistries.ITEM.getKey(o.getResult().getItem()), o.getMaxUses(), o.getXp());
            // Vanilla MerchantContainer#setItem sonuc yuvasini limitSize(min(99, esya istif siniri)) ile KIRPAR — 1.21.1'de de
            // ayni (javap: MerchantContainer.setItem -> ItemStack.limitSize). Beklenen teslim = kirpilmis adet (kok paritesi).
            tradeExpect = Math.min(o.getResult().getCount(), Math.min(99, o.getResult().getMaxStackSize()));
            c.note(d + " (teklif sayisi " + m.getOffers().size() + ", beklenen teslim " + tradeExpect + ")");
            boolean ok = o.getBaseCostA().is(net.minecraft.world.item.Items.EMERALD) && o.getBaseCostA().getCount() == 4
                    && BuiltInRegistries.ITEM.getKey(o.getResult().getItem()).toString().equals("arcanum:starter_spell_book")
                    && o.getResult().getCount() == 12 && o.getMaxUses() == 12 && o.getXp() == 5;
            return ok ? null : "beklenmeyen teklif: " + d;
        });
        p.act("sec", 10, c -> {
            if (c.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu m) {
                m.setSelectionHint(0);
                m.tryMoveItems(0);
                c.mc().getConnection().send(new net.minecraft.network.protocol.game.ServerboundSelectTradePacket(0));
            }
        }, c -> {
            if (!(c.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu m)) return "menu kapandi";
            ItemStack res = m.getSlot(2).getItem();
            c.note("sonuc yuvasi=" + res.getCount() + "x" + BuiltInRegistries.ITEM.getKey(res.getItem()));
            return BuiltInRegistries.ITEM.getKey(res.getItem()).toString().equals("arcanum:starter_spell_book")
                    ? null : "sonuc yuvasi bos/yanlis";
        });
        p.shot("al", 20, c -> {
            if (c.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu m) {
                c.mc().gameMode.handleContainerInput(m.containerId, 2, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, c.player());
            }
        }, c -> {
            int books = invCount(c, "arcanum:starter_spell_book");
            int em = invCount(c, "minecraft:emerald");
            c.note("envanter: starter_spell_book=" + books + " emerald=" + em);
            if (c.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu m) {
                c.note("teklif0 uses=" + m.getOffers().get(0).getUses());
            }
            return books == tradeExpect && em == 0 ? null : "ticaret tamamlanmadi: kitap=" + books + " (beklenen " + tradeExpect + ") zumrut=" + em;
        });
        p.act("kapat", 10, c -> c.player().closeContainer(), c -> {
            int books = invCount(c, "arcanum:starter_spell_book");
            return books == tradeExpect ? null : "kapatinca kitap=" + books;
        });
    }

    // ------------------------------------------------------------------ FR3: Wizard tüccarı (WizardTraderEntity) ticareti

    /**
     * Kök 1.21.1 {@code WizardTraderEntity#buildReagentOffers} / {@code #buildPotionOffers} ile birebir beklenen teklif
     * listeleri (A maliyeti | B maliyeti | sonuç | maxUses | xp). İksir sonuçları {@code potion_contents} kimliğiyle.
     */
    private static final List<String> TUCCAR_V0 = List.of(
            "3xminecraft:emerald|-|2xarcanum:practice_chalk|12|3",
            "4xminecraft:emerald|-|2xarcanum:spell_diagram|12|3",
            "6xminecraft:emerald|-|2xarcanum:crushed_amethyst|12|3",
            "8xminecraft:emerald|-|1xarcanum:storm_vial|12|3",
            "8xminecraft:emerald|-|1xarcanum:enchanted_honeycomb|12|3",
            "12xminecraft:emerald|-|1xarcanum:owl_charm|12|3",
            "14xminecraft:emerald|-|1xarcanum:phoenix_quill|12|3",
            "10xminecraft:emerald|-|1xarcanum:starter_spell_book|12|3",
            "1xarcanum:troll_hide|-|2xminecraft:emerald|12|3",
            "2xarcanum:mooncalf_dust|-|3xminecraft:emerald|12|3",
            "1xarcanum:thunderbird_feather|-|4xminecraft:emerald|12|3");
    private static final List<String> TUCCAR_V1 = List.of(
            "8xminecraft:emerald|1xarcanum:crushed_amethyst|1xminecraft:potion{arcanum:mana_haste_potion}|12|3",
            "10xminecraft:emerald|1xarcanum:phoenix_quill|1xminecraft:potion{arcanum:exstimulo_potion}|12|3",
            "8xminecraft:emerald|1xarcanum:troll_hide|1xminecraft:potion{arcanum:girding_potion}|12|3",
            "4xminecraft:glass_bottle|-|1xminecraft:emerald|12|3",
            "3xminecraft:nether_wart|-|1xminecraft:emerald|12|3");

    private static String stackDesc(ItemStack s) {
        if (s.isEmpty()) {
            return "-";
        }
        String d = s.getCount() + "x" + BuiltInRegistries.ITEM.getKey(s.getItem());
        var pc = s.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (pc != null && pc.potion().isPresent()) {
            d += "{" + pc.potion().get().getRegisteredName() + "}";
        }
        return d;
    }

    private static String offerDesc(net.minecraft.world.item.trading.MerchantOffer o) {
        return stackDesc(o.getBaseCostA()) + "|" + stackDesc(o.getCostB()) + "|" + stackDesc(o.getResult())
                + "|" + o.getMaxUses() + "|" + o.getXp();
    }

    private static com.arcanum.entity.@Nullable WizardTraderEntity trader(DevTest.Ctx c) {
        for (net.minecraft.world.entity.Entity e : c.level().getEntities((net.minecraft.world.entity.Entity) null,
                c.player().getBoundingBox().inflate(12), en -> en instanceof com.arcanum.entity.WizardTraderEntity)) {
            return (com.arcanum.entity.WizardTraderEntity) e;
        }
        return null;
    }

    /** Envanterde {@code potion_contents} kimliği verilen iksir adedi. */
    private static int potionCount(DevTest.Ctx c, String potionId) {
        int n = 0;
        var inv = c.player().getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            var pc = s.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            if (!s.isEmpty() && pc != null && pc.potion().isPresent() && pc.potion().get().getRegisteredName().equals(potionId)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static @Nullable String merchantCheck(DevTest.Ctx c, List<String> expected, int variant) {
        if (!(c.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu m)) return "MerchantMenu acilmadi";
        var t = trader(c);
        if (t == null) return "istemcide tuccar yok";
        if (t.getVariant() != variant) return "istemci varyanti " + t.getVariant() + " (beklenen " + variant + ")";
        List<String> got = new ArrayList<>();
        for (var o : m.getOffers()) {
            got.add(offerDesc(o));
        }
        c.note("teklif sayisi " + got.size() + " (beklenen " + expected.size() + "), ilerleme cubugu=" + m.showProgressBar()
                + ", yenileme=" + m.canRestock());
        for (int i = 0; i < Math.max(got.size(), expected.size()); i++) {
            String g = i < got.size() ? got.get(i) : "(yok)";
            String e = i < expected.size() ? expected.get(i) : "(yok)";
            if (!g.equals(e)) {
                return "teklif " + i + " = " + g + " (beklenen " + e + ")";
            }
        }
        c.note("teklifler kokle birebir: " + String.join(" ; ", got));
        return m.showProgressBar() || m.canRestock() ? "ilerleme cubugu/yenileme acik (kokte kapali)" : null;
    }

    /** Teklif seçimi: MerchantScreen#postButtonClick'in birebir aynısı (ticaret senaryosuyla aynı yol). */
    private static void selectOffer(DevTest.Ctx c, int idx) {
        if (c.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu m) {
            m.setSelectionHint(idx);
            m.tryMoveItems(idx);
            c.mc().getConnection().send(new net.minecraft.network.protocol.game.ServerboundSelectTradePacket(idx));
        }
    }

    private static void takeResult(DevTest.Ctx c) {
        if (c.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu m) {
            c.mc().gameMode.handleContainerInput(m.containerId, 2, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, c.player());
        }
    }

    private static int offerUses(DevTest.Ctx c, int idx) {
        return c.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu m ? m.getOffers().get(idx).getUses() : -1;
    }

    private static void openTrader(DevTest.Ctx c) {
        var t = trader(c);
        if (t != null) {
            var r = c.mc().gameMode.interact(c.player(), t,
                    new net.minecraft.world.phys.EntityHitResult(t, t.position().add(0, 1.0, 0)), InteractionHand.MAIN_HAND);
            c.note("interact=" + r);
        }
    }

    /**
     * Sunucu kaydı: {@code data get entity} çıktısında {@code Variant} VAR, {@code Offers} YOK olmalı — kök 1.21.1
     * {@code addAdditionalSaveData} yalnız "Variant" yazar; teklifler her yüklemede {@code getOffers()} ile yeniden kurulur
     * (kullanım sayıları kalıcı değil — kök davranışı). Kontrol: vanilla köylü (ticaret senaryosunun bıraktığı) Offers taşır;
     * yani aynı yöntem Offers VARSA görür.
     */
    private static @Nullable String saveDataCheck(DevTest.Ctx c, int variant) {
        String data = null;
        String ctrl = null;
        for (String s : c.stepChat()) {
            if (s.contains("has the following entity data") && s.contains("Variant")) data = s;
            if (s.contains("has the following entity data") && s.contains("Recipes")) ctrl = s;
        }
        if (data == null) return "data get ciktisi gelmedi: " + c.stepChat();
        boolean hasVariant = data.contains("Variant: " + variant + "b");
        boolean hasOffers = data.contains("Offers");
        c.note("tuccar kaydi: Variant " + variant + "b=" + hasVariant + ", Offers anahtari=" + hasOffers + ", uzunluk=" + data.length());
        if (ctrl != null) {
            int i = ctrl.indexOf("Recipes");
            c.note("kontrol koylu Offers VAR: " + ctrl.substring(i, Math.min(ctrl.length(), i + 160)));
        } else {
            c.note("kontrol koylu Offers: (ticaret koylusu yok/istenmedi)");
        }
        return hasVariant && !hasOffers ? null : "tuccar kaydi beklenmedik (Variant=" + hasVariant + ", Offers=" + hasOffers + ")";
    }

    /** Ticaret öncesi toplam deneyim (MP dünyası kalıcı; komutlar totalExperience'i sıfırlamaz). */
    private static int xpBefore;

    private static void tuccar(Plan p) {
        p.scenario("tuccar");
        // ---- Varyant 0 (malzeme tüccarı): 1 alım (zümrüt -> tebeşir) + 1 satım (trol derisi -> zümrüt)
        p.act("hazirla_v0", 40, c -> {
            c.cmd("kill @e[tag=tuccar]", "gamemode survival @s", "clear @s", "effect clear @s",
                    "tp @s 3.5 " + Y + " 0.5 0 20",
                    "item replace entity @s hotbar.7 with minecraft:emerald 3",
                    "item replace entity @s hotbar.8 with arcanum:troll_hide 1",
                    "summon arcanum:wizard_trader 3.5 " + Y + " 2.5 {Variant:0b,NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,"
                            + "Rotation:[180f,0f],Tags:[\"tuccar\"]}");
            c.hotbar(0);
        }, c -> {
            if (trader(c) == null) return "istemcide tuccar yok";
            if (invCount(c, "minecraft:emerald") != 3 || invCount(c, "arcanum:troll_hide") != 1) return "baslangic envanteri yanlis";
            return c.player().getAbilities().instabuild ? "hayatta kalma modunda degil" : null;
        });
        p.shotUntil("v0_ekran", 2, 80, c -> openTrader(c),
                c -> c.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu m && !m.getOffers().isEmpty(),
                c -> merchantCheck(c, TUCCAR_V0, 0));
        p.act("v0_sec_alim", 10, c -> {
            xpBefore = c.player().totalExperience;
            selectOffer(c, 0);
        }, c -> {
            if (!(c.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu m)) return "menu kapandi";
            String r = stackDesc(m.getSlot(2).getItem());
            c.note("sonuc yuvasi=" + r);
            return r.equals("2xarcanum:practice_chalk") ? null : "sonuc yuvasi " + r;
        });
        p.shot("v0_al_alim", 20, c -> takeResult(c), c -> {
            int chalk = invCount(c, "arcanum:practice_chalk");
            int em = invCount(c, "minecraft:emerald");
            int uses = offerUses(c, 0);
            c.note("envanter: practice_chalk=" + chalk + " emerald=" + em + "; teklif0 uses=" + uses);
            return chalk == 2 && em == 0 && uses == 1 ? null : "alim tamamlanmadi";
        });
        p.act("v0_sec_satim", 10, c -> selectOffer(c, 8), c -> {
            if (!(c.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu m)) return "menu kapandi";
            String r = stackDesc(m.getSlot(2).getItem());
            c.note("sonuc yuvasi=" + r);
            return r.equals("2xminecraft:emerald") ? null : "sonuc yuvasi " + r;
        });
        p.act("v0_al_satim", 20, c -> takeResult(c), c -> {
            int hide = invCount(c, "arcanum:troll_hide");
            int em = invCount(c, "minecraft:emerald");
            int uses = offerUses(c, 8);
            c.note("envanter: troll_hide=" + hide + " emerald=" + em + "; teklif8 uses=" + uses);
            return hide == 0 && em == 2 && uses == 1 ? null : "satim tamamlanmadi";
        });
        // notifyTrade: her ticarette TRADE_XP=3 deneyim küresi (kök ExperienceOrb.award) -> oyuncu toplar
        p.act("v0_kapat", 5, c -> c.player().closeContainer());
        p.shotUntil("v0_xp", 2, 200, null, c -> c.player().totalExperience - xpBefore >= 6, c -> {
            int d = c.player().totalExperience - xpBefore;
            c.note("oyuncu totalExperience " + xpBefore + " -> " + c.player().totalExperience + " (fark " + d + "; 2 ticaret x TRADE_XP 3)");
            return d == 6 ? null : "xp farki " + d + " (beklenen 6)";
        });
        p.act("v0_kayit", 20, c -> c.cmd("data get entity @e[tag=tuccar,limit=1]", "data get entity @e[tag=ticaret,limit=1] Offers"),
                c -> saveDataCheck(c, 0));
        // ---- Varyant 1 (iksirci): çift maliyetli teklif (zümrüt + ezilmiş ametist -> mana_haste iksiri)
        p.act("hazirla_v1", 40, c -> c.cmd("kill @e[tag=tuccar]", "clear @s",
                "item replace entity @s hotbar.7 with minecraft:emerald 8",
                "item replace entity @s hotbar.8 with arcanum:crushed_amethyst 1",
                "summon arcanum:wizard_trader 3.5 " + Y + " 2.5 {Variant:1b,NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,"
                        + "Rotation:[180f,0f],Tags:[\"tuccar\"]}"), c -> {
            var t = trader(c);
            if (t == null) return "istemcide tuccar yok";
            if (t.getVariant() != 1) return "istemci varyanti " + t.getVariant();
            return invCount(c, "minecraft:emerald") == 8 && invCount(c, "arcanum:crushed_amethyst") == 1 ? null : "baslangic envanteri yanlis";
        });
        p.shotUntil("v1_ekran", 2, 80, c -> openTrader(c),
                c -> c.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu m && !m.getOffers().isEmpty(),
                c -> merchantCheck(c, TUCCAR_V1, 1));
        p.act("v1_sec", 10, c -> selectOffer(c, 0), c -> {
            if (!(c.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu m)) return "menu kapandi";
            String a = stackDesc(m.getSlot(0).getItem());
            String b = stackDesc(m.getSlot(1).getItem());
            String r = stackDesc(m.getSlot(2).getItem());
            c.note("maliyet yuvalari A=" + a + " B=" + b + "; sonuc yuvasi=" + r);
            return r.equals("1xminecraft:potion{arcanum:mana_haste_potion}") ? null : "sonuc yuvasi " + r;
        });
        p.shot("v1_al", 20, c -> takeResult(c), c -> {
            int pot = potionCount(c, "arcanum:mana_haste_potion");
            int em = invCount(c, "minecraft:emerald");
            int am = invCount(c, "arcanum:crushed_amethyst");
            int uses = offerUses(c, 0);
            c.note("envanter: mana_haste_potion=" + pot + " emerald=" + em + " crushed_amethyst=" + am + "; teklif0 uses=" + uses);
            return pot == 1 && em == 0 && am == 0 && uses == 1 ? null : "cift maliyetli ticaret tamamlanmadi";
        });
        p.act("v1_kapat", 10, c -> c.player().closeContainer(),
                c -> potionCount(c, "arcanum:mana_haste_potion") == 1 ? null : "kapatinca iksir yok");
        p.act("v1_kayit", 20, c -> c.cmd("data get entity @e[tag=tuccar,limit=1]"), c -> saveDataCheck(c, 1));
        p.act("bitir", 10, c -> c.cmd("kill @e[tag=tuccar]", "clear @s"));
    }

    /**
     * Opt-in G4 ışın kontrolü: S7'deki {@code _cast} karesi render iş parçacığını ~230 ms durdurur (kare kodlama);
     * entegre sunucu gerçek zamanda ilerlediği için büyü o duraklamada ateşlenir ve istemci yakalama tick'lerinde
     * 10 tick'lik ışını ilk kareden önce yarıya kadar yaşlandırır. Burada ateşlemeden ÖNCE hiç kare alınmaz;
     * ışın istemcide belirdiği ilk tick'ten sonraki karede yakalanır (+ 2 ve 5 tick sonra).
     */
    private static void isin(Plan p) {
        p.scenario("isin");
        p.act("hazirla", 20, c -> {
            c.cmd("gamemode creative @s", "clear @s", BASE_TP + "0 0", "time set 6000",
                    "summon minecraft:armor_stand 0.5 " + Y + " 10.5 {Rotation:[180f,0f],Tags:[\"devtest\"],ShowArms:1b}",
                    "item replace entity @s hotbar.0 with arcanum:elder_wand");
            c.hotbar(0);
            c.camera(CameraType.FIRST_PERSON);
            assign(c, 0, 0, "stupefy");
            c.send(new SetActivePayload(0, 0));
        }, c -> checkSlot(c, 0, 0, "stupefy"));
        p.act("ates", 1, c -> c.useItem());
        int[] seen = new int[1];
        // kare = önceki render → ışının görüldüğü tick'ten SONRAKİ tick'te yakala (arada en az bir kare çizilmiş olur)
        p.shotUntil("ilk_kare", 0, 200, null,
                c -> (com.arcanum.client.beam.ClientSpellBeams.isEmpty() ? seen[0] : ++seen[0]) >= 2,
                c -> com.arcanum.client.beam.ClientSpellBeams.isEmpty() ? "isin istemcide yok" : null);
        p.shot("art2", 2, null, null);
        p.shot("art5", 3, null, null);
    }

    /** Entegre sunucu iş parçacığında yapılan {@code ServerPlayerGameMode#destroyBlock} çağrısının sonucu (null = henüz yok). */
    private static volatile @Nullable String serverBreakResult;
    /** İstemci {@code startDestroyBlock} / {@code continueDestroyBlock} dönüşleri (imperio adımları; istemci iş parçacığı). */
    private static boolean clientStart, clientContinue;
    private static float clientAtkBefore, clientAtkAfter;
    private static @Nullable String clientUse;

    /**
     * Opt-in: Imperio girdi engeli. Lanetliyken (1) istemci yolu {@code startDestroyBlock} (yaratıcı anında kırma →
     * LeftClickBlock iptali) ve (2) — yalnız entegre sunucuda — doğrudan {@code ServerPlayerGameMode#destroyBlock}
     * (Forge {@code BlockEvent.BreakEvent} {@code Result.DENY} yolu; Fabric'te {@code PlayerBlockBreakEvents.BEFORE})
     * bloğu KIRAMAMALI; lanet kalkınca aynı çağrı kırmalı (kontrol).
     */
    private static void imperio(Plan p) {
        p.scenario("imperio");
        BlockPos a = new BlockPos(-1, Y, 3);
        BlockPos b = new BlockPos(1, Y, 3);
        java.util.function.BiFunction<DevTest.Ctx, BlockPos, String> id =
                (c, pos) -> BuiltInRegistries.BLOCK.getKey(c.level().getBlockState(pos).getBlock()).toString();
        p.act("lanetli_koy", 20, c -> c.cmd("gamemode creative @s", "clear @s", BASE_TP + "0 25",
                "setblock -1 " + Y + " 3 minecraft:stone replace", "setblock 1 " + Y + " 3 minecraft:stone replace",
                "summon minecraft:armor_stand 0.5 " + Y + " 3.5 {NoGravity:1b,Rotation:[180f,0f],Tags:[\"devtest\",\"imp\"]}",
                "effect give @s arcanum:imperius_curse 120 0 true"), c -> {
            boolean cursed = c.player().getActiveEffects().stream()
                    .anyMatch(e -> e.getEffect().unwrapKey().map(k -> k.identifier().toString().equals("arcanum:imperius_curse")).orElse(false));
            if (!cursed) return "istemcide imperius_curse efekti yok";
            if (impStand(c) == null) return "zirh askisi (imp) istemcide yok";
            return id.apply(c, a).equals("minecraft:stone") && id.apply(c, b).equals("minecraft:stone") ? null : "bloklar konmadi";
        });
        // Fabric paritesi (kök AttackBlockCallback FAIL istemcide de): lanetliyken startDestroyBlock VE yaratıcı
        // continueDestroyBlock false dönmeli — true = istemci kırmayı başlattı/paket yolladı (Forge çatlak/titreme).
        p.act("lanetli_kir_istemci", 20, c -> {
            clientStart = c.mc().gameMode.startDestroyBlock(a, Direction.NORTH);
            clientContinue = c.mc().gameMode.continueDestroyBlock(a, Direction.NORTH);
            c.note("startDestroyBlock=" + clientStart + " continueDestroyBlock=" + clientContinue);
        }, c -> {
            if (!id.apply(c, a).equals("minecraft:stone")) return "LANETLIYKEN kirildi (istemci yolu): " + id.apply(c, a);
            if (clientStart) return "LANETLIYKEN startDestroyBlock=true (istemci kirmayi baslatti)";
            return clientContinue ? "LANETLIYKEN continueDestroyBlock=true (istemci kirmayi surdurdu)" : null;
        });
        p.act("lanetli_kir_sunucu", 20, c -> {
            var srv = c.mc().getSingleplayerServer();
            serverBreakResult = null;
            if (srv == null) {
                serverBreakResult = "atlandi (entegre sunucu yok)";
                return;
            }
            java.util.UUID u = c.player().getUUID();
            srv.execute(() -> {
                var sp = srv.getPlayerList().getPlayer(u);
                if (sp == null) {
                    serverBreakResult = "sunucu oyuncusu yok";
                    return;
                }
                boolean broke = sp.gameMode.destroyBlock(b);
                String now = BuiltInRegistries.BLOCK.getKey(sp.level().getBlockState(b).getBlock()).toString();
                serverBreakResult = "destroyBlock=" + broke + " blok=" + now;
            });
        }, c -> {
            String r = serverBreakResult;
            c.note("sunucu: " + r);
            if (r == null) return "sunucu sonucu gelmedi";
            if (r.startsWith("atlandi")) return null;
            return r.equals("destroyBlock=false blok=minecraft:stone") ? null : "LANETLIYKEN sunucu yolu kirdi: " + r;
        });
        // Fabric paritesi (kök AttackEntityCallback FAIL istemcide de: paket/Player#attack/resetAttackStrengthTicker YOK):
        // lanetliyken gameMode.attack zırh askısını kırmamalı VE saldırı gücü sayacı sıfırlanmamalı.
        p.act("lanetli_vur_istemci", 20, c -> {
            var e = impStand(c);
            clientAtkBefore = c.player().getAttackStrengthScale(0f);
            if (e != null) c.mc().gameMode.attack(c.player(), e);
            clientAtkAfter = c.player().getAttackStrengthScale(0f);
            c.note(String.format(Locale.ROOT, "hedef=%s atkGucu %.2f->%.2f", e != null, clientAtkBefore, clientAtkAfter));
        }, c -> {
            if (impStand(c) == null) return "LANETLIYKEN zirh askisi kirildi (istemci saldiri yolu)";
            if (clientAtkBefore < 0.9f) return "on kosul: saldiri gucu dolu degildi " + clientAtkBefore;
            return clientAtkAfter >= 0.9f ? null : "LANETLIYKEN saldiri gucu sayaci sifirlandi (istemci saldiriyi isledi): " + clientAtkAfter;
        });
        // Sağ tık engelleri (kök UseItemCallback / UseBlockCallback FAIL): istemci dönüşü iki loader'da FAIL olmalı.
        p.act("lanetli_esya", 10, c -> c.cmd("item replace entity @s weapon.mainhand with minecraft:snowball 16"),
                c -> c.player().getMainHandItem().is(net.minecraft.world.item.Items.SNOWBALL) ? null : "kartopu elde degil");
        p.act("lanetli_kullan_istemci", 20, c -> {
            var gm = c.mc().gameMode;
            var r1 = gm.useItem(c.player(), InteractionHand.MAIN_HAND);
            var r2 = gm.useItemOn(c.player(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(a), Direction.NORTH, a, false));
            clientUse = r1.getClass().getSimpleName() + "/" + r2.getClass().getSimpleName();
            c.note("useItem=" + r1 + " useItemOn=" + r2);
        }, c -> "Fail/Fail".equals(clientUse) ? null : "LANETLIYKEN sag tik FAIL donmedi: " + clientUse);
        p.act("esya_temizle", 5, c -> c.cmd("clear @s"));
        p.act("lanet_kaldir", 20, c -> c.cmd("effect clear @s arcanum:imperius_curse"), c -> c.player().getActiveEffects().stream()
                .noneMatch(e -> e.getEffect().unwrapKey().map(k -> k.identifier().toString().equals("arcanum:imperius_curse")).orElse(false))
                ? null : "efekt kalkmadi");
        p.act("lanetsiz_kir_istemci", 20, c -> {
            clientStart = c.mc().gameMode.startDestroyBlock(a, Direction.NORTH);
            c.note("startDestroyBlock=" + clientStart);
        }, c -> {
            if (!id.apply(c, a).equals("minecraft:air")) return "lanetsizken kirilmadi (kontrol): " + id.apply(c, a);
            return clientStart ? null : "lanetsizken startDestroyBlock=false (kontrol)";
        });
        p.act("lanetsiz_vur_istemci", 20, c -> {
            var e = impStand(c);
            clientAtkBefore = c.player().getAttackStrengthScale(0f);
            if (e != null) c.mc().gameMode.attack(c.player(), e);
            clientAtkAfter = c.player().getAttackStrengthScale(0f);
            c.note(String.format(Locale.ROOT, "hedef=%s atkGucu %.2f->%.2f", e != null, clientAtkBefore, clientAtkAfter));
        }, c -> {
            if (impStand(c) != null) return "lanetsizken zirh askisi kirilmadi (kontrol)";
            return clientAtkAfter < 0.9f ? null : "lanetsizken saldiri gucu sifirlanmadi (kontrol): " + clientAtkAfter;
        });
        // Kontrol: aynı sunucu yolu lanetsizken kırmalı (engelin sebebinin lanet olduğunu kanıtlar)
        p.act("lanetsiz_kir_sunucu", 20, c -> {
            var srv = c.mc().getSingleplayerServer();
            serverBreakResult = null;
            if (srv == null) {
                serverBreakResult = "atlandi (entegre sunucu yok)";
                return;
            }
            java.util.UUID u = c.player().getUUID();
            srv.execute(() -> {
                var sp = srv.getPlayerList().getPlayer(u);
                if (sp == null) {
                    serverBreakResult = "sunucu oyuncusu yok";
                    return;
                }
                boolean broke = sp.gameMode.destroyBlock(b);
                serverBreakResult = "destroyBlock=" + broke + " blok="
                        + BuiltInRegistries.BLOCK.getKey(sp.level().getBlockState(b).getBlock());
            });
        }, c -> {
            String r = serverBreakResult;
            c.note("sunucu: " + r);
            if (r == null) return "sunucu sonucu gelmedi";
            if (r.startsWith("atlandi")) return null;
            return r.equals("destroyBlock=true blok=minecraft:air") ? null : "lanetsizken sunucu yolu kiramadi (kontrol): " + r;
        });
        // fill (setblock değil): kırılmış -1 zaten hava → setblock "Could not set the block" verirdi; boş fill zararsız sayılır
        p.act("kaldir", 5, c -> c.cmd("fill -1 " + Y + " 3 1 " + Y + " 3 minecraft:air", "kill @e[tag=imp]"));
    }

    /** imperio sahnesindeki zırh askısı (istemci dünyası; yoksa/kaldırıldıysa null). */
    private static net.minecraft.world.entity.@Nullable Entity impStand(DevTest.Ctx c) {
        var box = new net.minecraft.world.phys.AABB(-1.5, Y - 1, 2.0, 2.5, Y + 3, 5.0);
        var list = c.level().getEntities((net.minecraft.world.entity.Entity) null, box,
                e -> e.getType() == net.minecraft.world.entity.EntityTypes.ARMOR_STAND && e.isAlive());
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Opt-in (G4 düzeltme turu 2): GeckoLib kafa-çevirme anlamı (GL4 mutlak ↔ GL5 dinlenme+snapshot). Kök 1.21.1'de
     * {@code DefaultedEntityGeoModel(id, true)} kullanan 5 varlık yan profilden (varlık yaw 90 = -X'e bakar, oyuncu +Z'ye
     * bakar) 3 DETERMİNİSTİK pozda çekilir. Poz istemci varlığına HER TICK (kare hazırlık tick'leri dahil) yazılır:
     * gövde yaw 90, kafa yaw 90+Δ, bakış eğimi θ; O-alanları eşit (kısmi-tick lerp'i sabit). NoAI → sunucu dokunmaz;
     * istemci BodyRotationControl'ün gövdeyi kafaya çevirmesi (10+ tick) her tick ezilir. İki düzenekte (1.21.1 test yatağı
     * + 26.1.2) BİREBİR aynı pozlar: duz (0/0), asagi_sag (θ=+40 aşağı, Δ=+35), yukari_sol (θ=-40 yukarı, Δ=-35).
     */
    private static void kafa(Plan p) {
        p.scenario("kafa");
        String[] ids = {"basilisk", "bowtruckle", "death_eater", "mooncalf", "wizard_trader"};
        String[] poseNames = {"duz", "asagi_sag", "yukari_sol"};
        float[][] poses = {{0f, 0f}, {40f, 35f}, {-40f, -35f}}; // {bakis egimi (xRot), kafa yaw farki}
        p.act("hazirla", 15, c -> {
            c.cmd("gamemode creative @s", "clear @s", "time set 6000", "kill @e[type=!minecraft:player,distance=..80]");
            c.camera(CameraType.FIRST_PERSON);
            c.hud(false);
        });
        for (String path : ids) {
            Identifier id = Identifier.fromNamespaceAndPath("arcanum", path);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
            EntityDimensions d = type.getDimensions();
            double w = d.width();
            double h = d.height();
            double dist = Math.min(16, Math.max(3.2, Math.max(h * 1.35 + 1.4, w * 1.3 + 1.8)));
            double camPitch = Math.toDegrees(Math.atan2((Y + 1.62) - (Y + h * 0.6), dist));
            String summon = String.format(Locale.ROOT,
                    "summon %s 0.5 %d %.2f {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b,Rotation:[90f,0f],Tags:[\"devtest\"]}",
                    id, Y, 0.5 + dist);
            p.act(path + "_cagir", 30, c -> {
                c.tickHook(null);
                c.cmd("kill @e[tag=devtest]", String.format(Locale.ROOT, BASE_TP + "0 %.1f", camPitch), summon);
                c.note(String.format(Locale.ROOT, "boyut %.2fx%.2f mesafe %.1f", w, h, dist));
            }, c -> c.countNear(type, 24) > 0 ? null : "istemcide " + id + " yok");
            for (int i = 0; i < poses.length; i++) {
                float pitch = poses[i][0];
                float yawOff = poses[i][1];
                int[] n = new int[1];
                p.shotUntil(path + "_" + poseNames[i], 0, 40, c -> {
                    n[0] = 0;
                    c.tickHook(cc -> headPose(cc, type, 90f, yawOff, pitch));
                }, c -> ++n[0] >= 6, c -> {
                    net.minecraft.world.entity.LivingEntity e = firstLiving(c, type);
                    if (e == null) {
                        return "istemcide " + id + " yok";
                    }
                    c.note(String.format(Locale.ROOT, "govde=%.1f kafa=%.1f egim=%.1f", e.yBodyRot, e.yHeadRot, e.getXRot()));
                    return null;
                });
            }
        }
        p.act("son", 10, c -> {
            c.tickHook(null);
            c.cmd("kill @e[tag=devtest]");
        });
    }

    private static net.minecraft.world.entity.@Nullable LivingEntity firstLiving(DevTest.Ctx c, EntityType<?> type) {
        for (net.minecraft.world.entity.Entity e : c.level().getEntities((net.minecraft.world.entity.Entity) null,
                c.player().getBoundingBox().inflate(24), en -> en.getType() == type)) {
            if (e instanceof net.minecraft.world.entity.LivingEntity le) {
                return le;
            }
        }
        return null;
    }

    private static void headPose(DevTest.Ctx c, EntityType<?> type, float body, float yawOff, float pitch) {
        net.minecraft.world.entity.LivingEntity e = firstLiving(c, type);
        if (e == null) {
            return;
        }
        e.setYRot(body);
        e.yRotO = body;
        e.yBodyRot = body;
        e.yBodyRotO = body;
        e.yHeadRot = body + yawOff;
        e.yHeadRotO = body + yawOff;
        e.setXRot(pitch);
        e.xRotO = pitch;
    }

    /** Opt-in: arcanewood kütüğü + balta (useItemOn) → stripped_arcanewood_log; vanilla meşe kontrolü. */
    private static void soyma(Plan p) {
        p.scenario("soyma");
        String[][] cases = {{"arcanum:arcanewood_log", "arcanum:stripped_arcanewood_log"},
                {"minecraft:oak_log", "minecraft:stripped_oak_log"}};
        for (int i = 0; i < cases.length; i++) {
            String log = cases[i][0];
            String want = cases[i][1];
            BlockPos pos = new BlockPos(i * 2 - 1, Y, 3);
            p.act("koy_" + i, 20, c -> {
                c.cmd("gamemode creative @s", "clear @s", BASE_TP + "0 25",
                        "item replace entity @s hotbar.0 with minecraft:iron_axe",
                        "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + log + "[axis=y] replace");
                c.hotbar(0);
            }, c -> {
                Identifier got = BuiltInRegistries.BLOCK.getKey(c.level().getBlockState(pos).getBlock());
                return got.toString().equals(log) ? null : "blok " + got;
            });
            p.shotUntil("soy_" + i, 5, 60, c -> {
                Vec3 hit = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ());
                var r = c.mc().gameMode.useItemOn(c.player(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(hit, Direction.NORTH, pos, false));
                c.note("useItemOn=" + r);
            }, c -> BuiltInRegistries.BLOCK.getKey(c.level().getBlockState(pos).getBlock()).toString().equals(want), c -> {
                String got = BuiltInRegistries.BLOCK.getKey(c.level().getBlockState(pos).getBlock()).toString();
                c.note("sonuc=" + got);
                return got.equals(want) ? null : "soyulmadi: " + got + " (beklenen " + want + ")";
            });
        }
        p.act("kaldir", 5, c -> c.cmd("setblock -1 " + Y + " 3 minecraft:air", "setblock 1 " + Y + " 3 minecraft:air"));
    }

    private static int grindylows(DevTest.Ctx c) {
        int n = 0;
        for (net.minecraft.world.entity.Entity e : c.level().entitiesForRendering()) {
            if (BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals("arcanum:grindylow")) {
                n++;
            }
        }
        return n;
    }

    /** Opt-in (G9): /summon ve spawn yumurtası (oyuncu yolu: useItemOn) grindylow çıkarmalı; doğal doğum kuralı bunlara uygulanmaz. */
    private static void grindylow(Plan p) {
        p.scenario("grindylow");
        // Yumurta hedefi oyuncuya ~4 blok (sunucu etkileşim menzili içinde).
        BlockPos egg = new BlockPos(1, Y - 1, 3);
        String box = " -7 " + (Y - 4) + " 3 -1 ";
        p.act("hazirla", 20, c -> {
            c.cmd("gamemode creative @s", "clear @s", "kill @e[type=arcanum:grindylow]", BASE_TP + "0 40",
                    "fill" + box + (Y - 1) + " 9 minecraft:glass",
                    "fill -6 " + (Y - 3) + " 4 -2 " + (Y - 1) + " 8 minecraft:water",
                    "item replace entity @s hotbar.0 with arcanum:grindylow_spawn_egg");
            c.hotbar(0);
        }, c -> grindylows(c) == 0 ? null : "temizlenmedi: " + grindylows(c));
        p.shotUntil("summon", 5, 60, c -> c.cmd("summon arcanum:grindylow -4 " + (Y - 2) + " 6"),
                c -> grindylows(c) >= 1, c -> grindylows(c) == 1 ? null : "summon sonrasi " + grindylows(c));
        p.shotUntil("yumurta", 5, 60, c -> {
            var r = c.mc().gameMode.useItemOn(c.player(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(new Vec3(egg.getX() + 0.5, egg.getY() + 1, egg.getZ() + 0.5), Direction.UP, egg, false));
            c.note("useItemOn=" + r);
        }, c -> grindylows(c) >= 2, c -> grindylows(c) == 2 ? null : "yumurta sonrasi " + grindylows(c));
        p.act("kaldir", 5, c -> c.cmd("kill @e[type=arcanum:grindylow]",
                "fill" + box + (Y - 2) + " 9 minecraft:air",
                "fill -7 " + (Y - 1) + " 3 -1 " + (Y - 1) + " 9 minecraft:grass_block"));
    }

    // ------------------------------------------------------------------ ortak

    private static void setup(Plan p) {
        p.scenario("hazirlik");
        p.act("komut_izni", 1, c -> { }, c -> {
            var conn = c.mc().getConnection();
            if (conn == null || conn.getCommands().getRoot().getChild("gamerule") == null
                    || conn.getCommands().getRoot().getChild("arcanum") == null) {
                return "oyuncunun komut izni yok (tekil dunyada hileler acik olmali / sunucuda 'op <ad>')";
            }
            return null;
        });
        p.act("kurallar", 20, c -> c.cmd(
                "gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false",
                "gamerule spawn_patrols false", "gamerule spawn_phantoms false", "gamerule spawn_wandering_traders false",
                "gamerule send_command_feedback true", "time set 6000", "weather clear", "gamemode creative @s",
                "clear @s", "effect clear @s", BASE_TP + "0 10"));
        p.act("platform", 40, c -> c.cmd(
                "fill -20 " + (Y - 1) + " -20 20 " + (Y - 1) + " 20 minecraft:grass_block",
                "fill -20 " + Y + " -20 20 " + (Y + 10) + " 20 minecraft:air",
                "fill -20 " + (Y + 11) + " -20 20 " + (Y + 25) + " 20 minecraft:air",
                "kill @e[type=!minecraft:player,distance=..80]",
                BASE_TP + "0 10"));
        p.act("seviye_ve_buyuler", 30, c -> c.cmd("arcanum maxxp", "arcanum spells unlock"), c -> {
            if (ClientMagicData.maxLevel() <= 0 || ClientMagicData.level() < ClientMagicData.maxLevel()) {
                return "seviye " + ClientMagicData.level() + "/" + ClientMagicData.maxLevel();
            }
            if (ClientSpellData.knownSet().size() < 20) {
                return "bilinen buyu sayisi " + ClientSpellData.knownSet().size();
            }
            c.note("seviye " + ClientMagicData.level() + "/" + ClientMagicData.maxLevel()
                    + " bilinen=" + ClientSpellData.knownSet().size() + " mana=" + ClientMagicData.mana() + "/" + ClientMagicData.manaCap());
            return null;
        });
        p.shot("sahne", 5, null, null);
    }

    // ------------------------------------------------------------------ G8: JEI uyumu (opt-in, yansımayla)

    /** Yansıma: ada ve argüman sayısına göre ilk public yöntemi çağırır (JEI'ye derleme bağımlılığı yok). */
    private static @Nullable Object jcall(Object target, String name, Object... args) throws ReflectiveOperationException {
        Class<?> cls = target instanceof Class<?> k ? k : target.getClass();
        Object recv = target instanceof Class<?> ? null : target;
        for (java.lang.reflect.Method m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length && accepts(m, args)) {
                m.setAccessible(true);
                return m.invoke(recv, args);
            }
        }
        throw new NoSuchMethodException(cls.getName() + "#" + name + "/" + args.length);
    }

    /** Aşırı yüklemeler (ör. JEI 30 {@code IRecipesGui#show(IFocus)} / {@code show(List)}) arasında argüman türüne uyanı seç. */
    private static boolean accepts(java.lang.reflect.Method m, Object[] args) {
        Class<?>[] pt = m.getParameterTypes();
        for (int i = 0; i < pt.length; i++) {
            if (args[i] != null && !pt[i].isPrimitive() && !pt[i].isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static Object jstatic(String cls, String field) throws ReflectiveOperationException {
        return Class.forName(cls).getField(field).get(null);
    }

    private static @Nullable Object jeiRuntime() {
        try {
            return jcall(Class.forName("mezz.jei.common.Internal"), "getJeiRuntime");
        } catch (Throwable t) {
            return null;
        }
    }

    private static final String JEI_TARGET = "arcanum:phoenix_wand";

    /**
     * Opt-in (G8): JEI yüklü üretim istemcisinde Arcanum içeriğinin JEI'de görünmesi. Kontroller:
     * JEI çalışma zamanı hazır; JEI malzeme listesinde {@code arcanum} ad alanlı eşya sayısı = kayıttaki Arcanum eşya
     * sayısı; JEI CRAFTING tarif türünde {@code arcanum} kimlikli tarif sayısı = 21 (veri paketi); filtre "@arcanum"
     * ile envanter ekranı (liste karesi); {@code IRecipesGui#show(OUTPUT phoenix_wand)} → tarif ekranı (tarif karesi).
     * JEI'ye derleme bağımlılığı YOK (yansıma) — JEI'siz koşuda "runtime" adımı FAIL verir (opt-in).
     */
    private static void jei(Plan p) {
        p.scenario("jei");
        p.act("runtime", 20, c -> c.cmd("gamemode survival @s", "clear @s"), c -> {
            Object rt = jeiRuntime();
            if (rt == null) return "JEI calisma zamani yok (JEI yuklu degil ya da baslamadi)";
            try {
                Object im = jcall(rt, "getIngredientManager");
                Object itemType = jstatic("mezz.jei.api.constants.VanillaTypes", "ITEM_STACK");
                int jeiItems = 0;
                for (Object o : (java.util.Collection<?>) jcall(im, "getAllIngredients", itemType)) {
                    if (o instanceof ItemStack st && BuiltInRegistries.ITEM.getKey(st.getItem()).getNamespace().equals("arcanum")) {
                        jeiItems++;
                    }
                }
                int regItems = 0;
                for (var id : BuiltInRegistries.ITEM.keySet()) {
                    if (id.getNamespace().equals("arcanum")) regItems++;
                }
                Object rm = jcall(rt, "getRecipeManager");
                Object crafting = jstatic("mezz.jei.api.constants.RecipeTypes", "CRAFTING");
                Object lookup = jcall(rm, "createRecipeLookup", crafting);
                List<String> ids = new ArrayList<>();
                ((java.util.stream.Stream<?>) jcall(lookup, "get")).forEach(h -> {
                    if (h instanceof net.minecraft.world.item.crafting.RecipeHolder<?> rh
                            && rh.id().identifier().getNamespace().equals("arcanum")) {
                        ids.add(rh.id().identifier().getPath());
                    }
                });
                java.util.Collections.sort(ids);
                c.note("JEI arcanum esya=" + jeiItems + " (kayit " + regItems + "), JEI arcanum CRAFTING tarifi=" + ids.size() + " " + ids);
                if (jeiItems != regItems) return "JEI esya listesi eksik: " + jeiItems + "/" + regItems;
                return ids.size() == 21 ? null : "JEI arcanum tarif sayisi " + ids.size() + " (beklenen 21)";
            } catch (Throwable t) {
                return "JEI yansima hatasi: " + t;
            }
        });
        p.shot("liste", 30, c -> {
            try {
                jcall(jcall(jeiRuntime(), "getIngredientFilter"), "setFilterText", "@arcanum");
            } catch (Throwable t) {
                c.note("filtre ayarlanamadi: " + t);
            }
            DevCompat.setScreen(new InventoryScreen(c.player()));
        }, c -> {
            if (!(DevCompat.screen() instanceof InventoryScreen)) return "InventoryScreen acilmadi: " + DevCompat.screen();
            try {
                Object f = jcall(jeiRuntime(), "getIngredientFilter");
                List<?> shown = (List<?>) jcall(f, "getFilteredItemStacks");
                long arc = shown.stream().filter(o -> o instanceof ItemStack st
                        && BuiltInRegistries.ITEM.getKey(st.getItem()).getNamespace().equals("arcanum")).count();
                boolean overlay = (boolean) jcall(jcall(jeiRuntime(), "getIngredientListOverlay"), "isListDisplayed");
                c.note("filtre=" + jcall(f, "getFilterText") + " gosterilen=" + shown.size() + " (arcanum " + arc + "), liste gorunur=" + overlay);
                // "@arcanum" = mod filtresi: Arcanum ESYALARI (57) + Arcanum iksir icerikli vanilla iksir/ok gorunur
                return arc == 57 && overlay ? null : "JEI listesi Arcanum esyalarini gostermiyor (" + arc + "/57)";
            } catch (Throwable t) {
                return "JEI yansima hatasi: " + t;
            }
        });
        p.shotUntil("tarif", 10, 80, c -> {
            closeScreen(c);
            try {
                Object rt = jeiRuntime();
                Object ff = jcall(jcall(rt, "getJeiHelpers"), "getFocusFactory");
                Object role = jstatic("mezz.jei.api.recipe.RecipeIngredientRole", "OUTPUT");
                Object itemType = jstatic("mezz.jei.api.constants.VanillaTypes", "ITEM_STACK");
                ItemStack target = new ItemStack(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(JEI_TARGET)));
                Object focus = null;
                for (java.lang.reflect.Method m : ff.getClass().getMethods()) {
                    if (m.getName().equals("createFocus") && m.getParameterCount() == 3) {
                        m.setAccessible(true);
                        focus = m.invoke(ff, role, itemType, target);
                    }
                }
                jcall(jcall(rt, "getRecipesGui"), "show", List.of(focus));
            } catch (Throwable t) {
                c.note("show hatasi: " + t);
            }
        }, c -> DevCompat.screen() != null && DevCompat.screen().getClass().getName().contains("RecipesGui"), c -> {
            Screen s = DevCompat.screen();
            c.note("ekran=" + (s == null ? "null" : s.getClass().getName()) + ", hedef=" + JEI_TARGET);
            return s != null && s.getClass().getName().contains("RecipesGui") ? null : "JEI tarif ekrani acilmadi";
        });
        p.act("kapat", 10, c -> {
            try {
                jcall(jcall(jeiRuntime(), "getIngredientFilter"), "setFilterText", "");
            } catch (Throwable ignored) {
                // JEI yoksa sessiz
            }
            DevCompat.setScreen(null);
        });
    }

    /** Her senaryodan sonra: ekran kapat, basılı-tutmayı bırak, 1. şahıs, HUD açık, etiketli varlıkları sil. */
    private static void reset(Plan p, String scenario) {
        p.scenario(scenario);
        p.act("temizle", 10, c -> {
            closeScreen(c);
            c.tickHook(null);
            c.hold(false);
            c.camera(CameraType.FIRST_PERSON);
            c.hud(true);
            c.cmd("kill @e[tag=devtest]", "gamemode creative @s", "effect clear @s", "time set 6000");
        });
    }

    private static void closeScreen(DevTest.Ctx c) {
        Screen s = DevCompat.screen();
        if (s instanceof AbstractContainerScreen<?>) {
            c.player().closeContainer();
        } else if (s != null) {
            DevCompat.setScreen(null);
        }
    }

    /** Aktif dizilim slotuna büyü ata (G menüsündeki sürükle-bırakın paket yolu). */
    private static void assign(DevTest.Ctx c, int page, int slot, String spellId) {
        c.send(new AssignSlotPayload(page, slot, c.spellIndex(spellId)));
    }

    private static @Nullable String checkSlot(DevTest.Ctx c, int page, int slot, String spellId) {
        int want = c.spellIndex(spellId);
        int got = ClientSpellData.loadoutSlot(page, slot);
        return got == want ? null : "dizilim " + page + "/" + slot + " = " + got + " (beklenen " + want + " " + spellId + ")";
    }

    private static List<Identifier> arcanumIds(Iterable<Identifier> keys) {
        List<Identifier> out = new ArrayList<>();
        for (Identifier id : keys) {
            if (id.getNamespace().equals("arcanum")) {
                out.add(id);
            }
        }
        out.sort((a, b) -> a.getPath().compareTo(b.getPath()));
        return out;
    }

    /** Elde tutulan asanın bu büyü için cast süresi (tick) — WandItem.use ile aynı formül. */
    private static int castTicks(DevTest.Ctx c, Spell s) {
        ItemStack st = c.player().getMainHandItem();
        float mult = st.getItem() instanceof WandItem w ? w.tier().castTimeMult() : 1f;
        return Math.max(1, Math.round(SpellCastTime.ticks(s) * mult));
    }

    // ------------------------------------------------------------------ S1 hud

    private static void hud(Plan p) {
        p.scenario("hud");
        p.act("asa_ver", 15, c -> {
            c.cmd("gamemode survival @s", "clear @s", "item replace entity @s hotbar.0 with arcanum:arcanewood_wand",
                    BASE_TP + "0 10");
            c.hotbar(0);
        });
        p.act("dizilim", 12, c -> {
            assign(c, 0, 0, "stupefy");
            assign(c, 0, 1, "expelliarmus");
            assign(c, 0, 2, "lumos");
            assign(c, 0, 3, "protego");
            c.send(new SetActivePayload(0, 0));
        }, c -> checkSlot(c, 0, 0, "stupefy"));
        p.shot("mana_ve_slotlar", 10, c -> c.hotbar(0), c -> {
            if (!(c.player().getMainHandItem().getItem() instanceof WandItem)) {
                return "elde asa yok: " + c.player().getMainHandItem();
            }
            if (ClientMagicData.manaCap() <= 0) {
                return "mana kapasitesi 0 (MagicData senk yok)";
            }
            c.note("mana=" + ClientMagicData.mana() + "/" + ClientMagicData.manaCap()
                    + " aktif=" + ClientSpellData.activePage() + "/" + ClientSpellData.activeSlot());
            return null;
        });
        // cast göstergesi (imleç altı) — stupefy'ı başlat, yarısında kare
        int[] hudCast = new int[2];
        p.shotUntil("cast_bar", 2, 100, c -> {
            hudCast[0] = castTicks(c, c.spell("stupefy"));
            hudCast[1] = 0;
            c.useItem();
        }, c -> ++hudCast[1] >= Math.max(2, hudCast[0] / 2), null);
        p.act("cast_bitir", 40, c -> { });
    }

    // ------------------------------------------------------------------ S2 envanter

    private static void envanter(Plan p) {
        p.scenario("envanter");
        List<Identifier> ids = arcanumIds(BuiltInRegistries.ITEM.keySet());
        int half = Math.min(36, ids.size());
        List<Identifier> first = ids.subList(0, half);
        List<Identifier> second = ids.subList(half, ids.size());
        p.act("liste", 1, c -> c.note(ids.size() + " Arcanum esyasi: " + ids.stream().map(Identifier::getPath).toList()),
                c -> ids.size() == 57 ? null : "esya sayisi " + ids.size() + " (beklenen 57)");
        p.act("ver_1", 20, c -> {
            c.cmd("gamemode survival @s", "clear @s");
            for (Identifier id : first) {
                c.cmd("give @s " + id + " 1");
            }
        }, c -> checkInv(c, first));
        p.shot("envanter_1", 10, c -> DevCompat.setScreen(new InventoryScreen(c.player())),
                c -> DevCompat.screen() instanceof InventoryScreen ? null : "InventoryScreen acilmadi: " + DevCompat.screen());
        p.act("ver_2", 20, c -> {
            closeScreen(c);
            c.cmd("clear @s");
            for (Identifier id : second) {
                c.cmd("give @s " + id + " 1");
            }
        }, c -> checkInv(c, second));
        p.shot("envanter_2", 10, c -> DevCompat.setScreen(new InventoryScreen(c.player())),
                c -> DevCompat.screen() instanceof InventoryScreen ? null : "InventoryScreen acilmadi");
        p.act("yaratici_mod", 15, c -> {
            closeScreen(c);
            c.cmd("gamemode creative @s", "clear @s");
        });
        p.act("yaratici_ac", 10, c -> {
            LocalPlayer pl = c.player();
            DevCompat.setScreen(new CreativeModeInventoryScreen(pl, pl.connection.enabledFeatures(),
                    c.mc().options.operatorItemsTab().get()));
        }, c -> DevCompat.screen() instanceof CreativeModeInventoryScreen ? null : "yaratici envanter acilmadi");
        p.shot("yaratici_sekme", 10, c -> {
            String err = selectArcanumTab(c);
            if (err != null) {
                c.error(err);
            }
        }, c -> {
            CreativeModeTab sel = selectedTab();
            CreativeModeTab want = arcanumTab();
            return (sel != null && sel == want) ? null : "secili sekme " + sel + " (beklenen Arcanum)";
        });
        p.shot("yaratici_sekme_kaydir", 8, c -> {
            Screen s = DevCompat.screen();
            if (s != null) {
                s.mouseScrolled(s.width / 2.0, s.height / 2.0, 0, -10);
            }
        }, null);
    }

    private static @Nullable String checkInv(DevTest.Ctx c, List<Identifier> want) {
        Set<Identifier> have = new HashSet<>();
        var inv = c.player().getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty()) {
                have.add(BuiltInRegistries.ITEM.getKey(st.getItem()));
            }
        }
        List<String> missing = new ArrayList<>();
        for (Identifier id : want) {
            if (!have.contains(id)) {
                missing.add(id.getPath());
            }
        }
        c.note("envanterde " + (want.size() - missing.size()) + "/" + want.size());
        return missing.isEmpty() ? null : "envantere gelmeyen: " + missing;
    }

    private static @Nullable CreativeModeTab arcanumTab() {
        return BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.fromNamespaceAndPath("arcanum", "arcanum"));
    }

    private static @Nullable CreativeModeTab selectedTab() {
        try {
            Screen s = DevCompat.screen();
            if (s != null) {
                try {
                    return (CreativeModeTab) s.getClass().getMethod("getSelectedTab").invoke(s);
                } catch (NoSuchMethodException ignored) {
                    // Fabric API yok → vanilla statik alan
                }
            }
            Field f = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
            f.setAccessible(true);
            return (CreativeModeTab) f.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Arcanum sekmesini programatik seç: vanilla {@code private void selectTab(CreativeModeTab)} (26.x resmi adlar →
     * iki loader'da aynı ad; yansıma yalnız bu DEV düzeneğinde). Forge'un sekme SAYFALARI varsa sekmenin sayfasına geç
     * ({@code pages} alanı + {@code setCurrentPage}; ikisi de yansımayla — ortak kodda Forge importu yok).
     */
    private static @Nullable String selectArcanumTab(DevTest.Ctx c) {
        Screen s = DevCompat.screen();
        if (!(s instanceof CreativeModeInventoryScreen scr)) {
            return "yaratici ekran acik degil";
        }
        CreativeModeTab tab = arcanumTab();
        if (tab == null) {
            return "arcanum:arcanum sekmesi kayitta yok";
        }
        try {
            // Fabric (fabric-creative-tab-api-v1): ekrana enjekte edilen PUBLIC setSelectedTab(tab) sayfayı da çevirir;
            // vanilla private selectTab Fabric'te sekme başka sayfadaysa seçimi reddediyor (ilk koşuda ölçüldü).
            try {
                Method fab = scr.getClass().getMethod("setSelectedTab", CreativeModeTab.class);
                Object r = fab.invoke(scr, tab);
                c.note("fabric setSelectedTab=" + r);
                return null;
            } catch (NoSuchMethodException ignored) {
                // Fabric API yok (Forge/vanilla)
            }
            try {
                Field pf = CreativeModeInventoryScreen.class.getDeclaredField("pages");
                pf.setAccessible(true);
                for (Object page : (List<?>) pf.get(scr)) {
                    Method vis = page.getClass().getMethod("getVisibleTabs");
                    if (((List<?>) vis.invoke(page)).contains(tab)) {
                        Method set = CreativeModeInventoryScreen.class.getMethod("setCurrentPage", page.getClass());
                        set.invoke(scr, page);
                        c.note("forge sekme sayfasi secildi");
                    }
                }
            } catch (NoSuchFieldException ignored) {
                // vanilla/Fabric: sayfa yok
            }
            Method m = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
            m.setAccessible(true);
            m.invoke(scr, tab);
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "selectTab yansimasi basarisiz: " + e;
        }
    }

    // ------------------------------------------------------------------ S3 gmenu

    private static void gmenu(Plan p) {
        p.scenario("gmenu");
        p.act("hazirla", 15, c -> {
            c.cmd("gamemode creative @s", "clear @s", "item replace entity @s hotbar.0 with arcanum:elder_wand", BASE_TP + "0 10");
            c.hotbar(0);
            assign(c, 0, 0, "stupefy");
            assign(c, 0, 1, "expecto_patronum");
            assign(c, 0, 2, "protego_diabolica");
            assign(c, 0, 3, "lumos");
            assign(c, 1, 0, "avada_kedavra");
            assign(c, 1, 1, "umbravolo");
            c.send(new SetActivePayload(0, 0));
        }, c -> checkSlot(c, 1, 1, "umbravolo"));
        p.shot("menu", 15, c -> DevCompat.setScreen(new SpellMenuScreen()), c -> {
            if (!(DevCompat.screen() instanceof SpellMenuScreen)) {
                return "SpellMenuScreen acilmadi: " + DevCompat.screen();
            }
            if (ClientMagicData.level() < ClientMagicData.maxLevel()) {
                return "seviye " + ClientMagicData.level();
            }
            c.note("seviye " + ClientMagicData.level() + " harcanmamis puan=" + ClientMagicData.unspent());
            return null;
        });
    }

    // ------------------------------------------------------------------ S4 masa

    private static void masa(Plan p) {
        p.scenario("masa");
        BlockPos pos = new BlockPos(0, Y, 3);
        p.act("masa_koy", 20, c -> {
            c.cmd("gamemode creative @s", "clear @s", BASE_TP + "0 25",
                    "setblock 0 " + Y + " 3 arcanum:spell_table replace");
            c.hotbar(0);
        }, c -> {
            Identifier got = BuiltInRegistries.BLOCK.getKey(c.level().getBlockState(pos).getBlock());
            return got.toString().equals("arcanum:spell_table") ? null : "blok " + got;
        });
        p.shotUntil("ekran", 5, 60, c -> {
            Vec3 hit = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            var r = c.mc().gameMode.useItemOn(c.player(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(hit, Direction.NORTH, pos, false));
            c.note("useItemOn=" + r);
        }, c -> DevCompat.screen() instanceof SpellTableScreen, c ->
                DevCompat.screen() instanceof SpellTableScreen ? null : "SpellTableScreen acilmadi: " + DevCompat.screen());
        p.act("masa_kaldir", 5, c -> {
            closeScreen(c);
            c.cmd("setblock 0 " + Y + " 3 minecraft:air");
        });
    }

    // ------------------------------------------------------------------ S5 yaratiklar

    private static void yaratiklar(Plan p) {
        p.scenario("yaratiklar");
        List<Identifier> ids = arcanumIds(BuiltInRegistries.ENTITY_TYPE.keySet());
        p.act("liste", 1, c -> {
            c.note(ids.size() + " tip: " + ids.stream().map(Identifier::getPath).toList());
            c.cmd("gamemode creative @s", "clear @s", "kill @e[type=!minecraft:player,distance=..80]");
            c.hud(false);
        }, c -> ids.size() == 22 ? null : "entity tipi sayisi " + ids.size() + " (beklenen 22)");
        for (Identifier id : ids) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
            EntityDimensions d = type.getDimensions();
            double w = d.width();
            double h = d.height();
            double dist = Math.min(16, Math.max(3.2, Math.max(h * 1.35 + 1.4, w * 1.3 + 1.8)));
            double eyeY = Y + 1.62;
            double pitch = Math.toDegrees(Math.atan2(eyeY - (Y + h * 0.5), dist));
            double z = 0.5 + dist;
            String summon = String.format(Locale.ROOT,
                    "summon %s 0.5 %d %.2f {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:[\"devtest\"]}",
                    id, Y, z);
            if (!type.canSummon()) {
                // /summon'a kapalı tip (EntityType.Builder.noSummon) — kök tasarımı; S7 büyüyle üretir.
                p.act("entity_" + id.getPath(), 1, c -> c.note("ATLANDI: canSummon=false (/summon ile cagrilamaz; bkz. buyu senaryosu)"));
                continue;
            }
            p.shotUntil("entity_" + id.getPath(), 30, 80, c -> {
                c.cmd("kill @e[tag=devtest]", "kill @e[type=!minecraft:player,distance=..80]",
                        String.format(Locale.ROOT, BASE_TP + "0 %.1f", pitch),
                        summon,
                        "execute as @e[tag=devtest] at @s run tp @s ~ ~ ~ 180 0");
                c.note(String.format(Locale.ROOT, "boyut %.2fx%.2f mesafe %.1f", w, h, dist));
            }, c -> c.countNear(type, 24) > 0, c -> c.countNear(type, 24) > 0 ? null
                    : "istemci dunyasinda " + id + " yok (summon sonrasi kendini silmis olabilir)");
        }
        p.act("son", 25, c -> c.cmd("kill @e[tag=devtest]"));
    }

    // ------------------------------------------------------------------ S6 pelerin

    private static void pelerin(Plan p) {
        p.scenario("pelerin");
        p.act("hazirla", 15, c -> {
            c.cmd("gamemode creative @s", "clear @s", "effect clear @s",
                    "item replace entity @s hotbar.0 with arcanum:arcanewood_wand", BASE_TP + "0 10");
            c.hotbar(0);
            c.camera(CameraType.THIRD_PERSON_FRONT);
        });
        p.shot("pelerinsiz_on", 10, null, c -> c.player().isInvisible() ? "pelerin yokken gorunmez" : null);
        p.shotUntil("pelerinli_on", 20, 80,
                c -> c.cmd("item replace entity @s armor.chest with arcanum:cloak_of_invisibility"),
                c -> c.player().isInvisible(),
                c -> {
                    ItemStack chest = c.player().getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
                    c.note("gogus=" + BuiltInRegistries.ITEM.getKey(chest.getItem()));
                    return c.player().isInvisible() ? null : "pelerin giyiliyken oyuncu gorunmez DEGIL";
                });
        p.shot("pelerinli_arka", 10, c -> c.camera(CameraType.THIRD_PERSON_BACK), null);
        p.act("pelerin_cikar", 20, c -> c.cmd("item replace entity @s armor.chest with minecraft:air", "effect clear @s"));
        // Umbravolo kara duman formu
        p.act("umbravolo_sec", 10, c -> {
            assign(c, 0, 0, "umbravolo");
            c.send(new SetActivePayload(0, 0));
        }, c -> checkSlot(c, 0, 0, "umbravolo"));
        p.shotUntil("umbravolo_arka", 15, 80, DevTest.Ctx::useItem,
                c -> ClientUmbraForms.has(c.player().getId()),
                c -> ClientUmbraForms.has(c.player().getId()) ? null : "Umbravolo formu istemciye gelmedi");
        p.shot("umbravolo_on", 10, c -> c.camera(CameraType.THIRD_PERSON_FRONT), null);
        p.act("umbravolo_cik", 20, DevTest.Ctx::useItem,
                c -> ClientUmbraForms.has(c.player().getId()) ? "Umbravolo formundan cikilmadi" : null);
    }

    // ------------------------------------------------------------------ S7 buyu

    private static void buyu(Plan p) {
        p.scenario("buyu");
        p.act("hazirla", 20, c -> {
            c.cmd("gamemode creative @s", "clear @s", BASE_TP + "0 0",
                    // hedef: ışın bir canlıya çarpsın (armor stand, 10 blok önde, bize dönük)
                    "summon minecraft:armor_stand 0.5 " + Y + " 10.5 {Rotation:[180f,0f],Tags:[\"devtest\"],ShowArms:1b}");
            c.hotbar(0);
        });
        // Her büyü AYRI asa türüyle: 26.x ItemCooldowns asa TÜRÜ başına (patronus 400 tick bekletmesin)
        castSpell(p, "stupefy", "arcanewood_wand", CameraType.FIRST_PERSON, 0, null);
        castSpell(p, "lumos", "thunderbird_wand", CameraType.FIRST_PERSON, 0, "time set 18000");
        castSpell(p, "expecto_patronum", "phoenix_wand", CameraType.THIRD_PERSON_BACK, 10, null);
        p.act("patronus_kontrol", 1, c -> { }, c -> {
            EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("arcanum", "patronus"));
            return c.countNear(t, 32) > 0 ? null : "Patronus varligi istemcide yok";
        });
        // Protego Diabolica — KANAL: sağ tık basılı tut
        p.act("diabolica_sec", 12, c -> {
            c.cmd("time set 6000", "item replace entity @s hotbar.0 with arcanum:troll_wand", BASE_TP + "0 25");
            c.hotbar(0);
            c.camera(CameraType.THIRD_PERSON_BACK);
            assign(c, 0, 0, "protego_diabolica");
            c.send(new SetActivePayload(0, 0));
        }, c -> checkSlot(c, 0, 0, "protego_diabolica"));
        p.shot("diabolica_1", 25, c -> c.hold(true), c -> c.player().isUsingItem() ? null : "kanal basmadi (isUsingItem=false)");
        p.shot("diabolica_2", 30, null, c -> {
            EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("arcanum", "diabolica_dragon"));
            return c.countNear(t, 32) > 0 ? null : "Diabolica ejderhasi istemcide yok";
        });
        p.shot("diabolica_3", 30, c -> c.camera(CameraType.THIRD_PERSON_FRONT), null);
        p.act("diabolica_birak", 30, c -> c.hold(false));
    }

    /**
     * Tek büyü: asa ver → slot 0/0'a ata + aktif et → useItem → cast başında kare → ateşten hemen sonra 4 kare.
     */
    private static void castSpell(Plan p, String spellId, String wand, CameraType cam, int pitch, @Nullable String extraCmd) {
        p.act(spellId + "_sec", 12, c -> {
            c.cmd("item replace entity @s hotbar.0 with arcanum:" + wand, BASE_TP + "0 " + pitch);
            if (extraCmd != null) {
                c.cmd(extraCmd);
            }
            c.hotbar(0);
            c.camera(cam);
            assign(c, 0, 0, spellId);
            c.send(new SetActivePayload(0, 0));
        }, c -> checkSlot(c, 0, 0, spellId));
        int[] cast = new int[1];
        int[] waited = new int[1];
        // cast'in ilk tick'leri: rün telegrafı + imleç altı cast çubuğu
        p.shotUntil(spellId + "_cast", 2, 200, c -> {
            cast[0] = castTicks(c, c.spell(spellId));
            waited[0] = 0;
            c.note("cast=" + cast[0] + " tick");
            c.useItem();
        }, c -> ++waited[0] >= Math.max(2, cast[0] / 2), null);
        // ateş ≈ cast süresi dolunca (sunucu CastManager) + 1 tick paket → hemen ardından kare
        p.shotUntil(spellId + "_1", 1, 200, null, c -> ++waited[0] >= cast[0] + 1, null);
        p.shot(spellId + "_2", 3, null, null);
        p.shot(spellId + "_3", 6, null, null);
        p.shot(spellId + "_4", 12, null, null);
    }

    // ------------------------------------------------------------------ S8 partikul

    private static void partikul(Plan p) {
        p.scenario("partikul");
        List<Identifier> ids = arcanumIds(BuiltInRegistries.PARTICLE_TYPE.keySet());
        p.act("hazirla", 15, c -> {
            c.cmd("gamemode creative @s", "clear @s", BASE_TP + "0 10", "kill @e[type=!minecraft:player,distance=..80]");
            c.hud(false);
            c.note(ids.size() + " partikul tipi: " + ids.stream().map(Identifier::getPath).toList());
        }, c -> ids.size() == 6 ? null : "partikul tipi sayisi " + ids.size() + " (beklenen 6)");
        int[] colors = {0xFF4FA8FF, 0xFFFF6A00, 0xFF3CB043, 0xFFD6F0FF, 0xFFC0392B, 0xFF8A2BE2};
        int ci = 0;
        for (Identifier id : ids) {
            ParticleType<?> t = BuiltInRegistries.PARTICLE_TYPE.getValue(id);
            String opt = (t instanceof SimpleParticleType) ? "" : "{color:" + colors[ci++ % colors.length] + "}";
            String cmd = "particle " + id + opt + " 0.5 " + (Y + 1.5) + " 4.5 0.9 0.7 0.9 0.02 120 force";
            p.shot(id.getPath(), 5, c -> {
                c.cmd(cmd);
                c.note("/" + cmd);
            }, c -> {
                c.note("motor: " + c.mc().particleEngine.countParticles());
                return null;
            });
            p.act(id.getPath() + "_son", 30, c -> { });
        }
    }

    // ------------------------------------------------------------------ S9 yaprak

    private static void yaprak(Plan p) {
        p.scenario("yaprak");
        p.act("koy", 40, c -> {
            c.cmd("gamemode creative @s", "clear @s", BASE_TP + "0 20",
                    "setblock 0 " + Y + " 3 arcanum:arcanewood_leaves[persistent=true]",
                    "setblock 1 " + Y + " 3 arcanum:arcanewood_leaves[persistent=true]",
                    "setblock 1 " + (Y + 1) + " 3 arcanum:arcanewood_leaves[persistent=true]",
                    "setblock 0 " + (Y + 1) + " 4 arcanum:arcanewood_log",
                    "setblock -1 " + Y + " 3 arcanum:arcanewood_sapling",
                    "setblock -2 " + Y + " 3 minecraft:oak_leaves[persistent=true]",
                    "setblock -2 " + (Y + 1) + " 3 minecraft:oak_sapling");
            c.hud(false);
        }, c -> {
            Identifier l = BuiltInRegistries.BLOCK.getKey(c.level().getBlockState(new BlockPos(0, Y, 3)).getBlock());
            Identifier s = BuiltInRegistries.BLOCK.getKey(c.level().getBlockState(new BlockPos(-1, Y, 3)).getBlock());
            if (!l.toString().equals("arcanum:arcanewood_leaves") || !s.toString().equals("arcanum:arcanewood_sapling")) {
                return "bloklar konmadi: " + l + " / " + s;
            }
            return null;
        });
        p.shot("yakin", 5, null, null);
        p.shot("cok_yakin", 20, c -> c.cmd("tp @s 0.5 " + Y + " 1.2 0 30"), null);
        p.act("kaldir", 5, c -> c.cmd("fill -3 " + Y + " 2 3 " + (Y + 3) + " 5 minecraft:air"));
    }

}
