package com.arcanum.spell;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.arcanum.data.ArcanumPlayerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * TEK sunucu-yetkili büyü öğrenme/bilme kapısı. Eskiden WandItem/SpellBookItem
 * kendi içlerinde ayrı ayrı {@code "basic".equals(tier) || known.contains(id)}
 * kısayolunu kullanıyordu — bu, "oyuna başlar başlamaz her şeyi biliyoruz"
 * hatasının kök nedeniydi. Artık basic dahil HİÇBİR kademe bedava değil;
 * bilgi yalnızca {@link ArcanumPlayerData#knows} kümesinden okunur.
 *
 * <p>Item kimlikleri BİLEREK sınıf/derleme bağımlılığı olmadan, düz kayıt id'si
 * (ResourceLocation) string'leri üzerinden çözülür. Kitap kontrolü artık kademeye
 * değil, kitabın KENDİSİNE (starter/dark spellbook veya TEK Yasak Lanetler Kitabı)
 * göre yapılır (bkz. {@link #bookUnlocks}); reagent kontrolü de kademeye değil,
 * PER-SPELL {@link SpellReagents} haritasına dayanır.
 */
public final class SpellGating {
    private SpellGating() {}

    // ---- Sabit kitap item id'leri (ModItems'a derleme bağımlılığı yaratmadan) ----
    public static final String STARTER_SPELL_BOOK_ID = "arcanum:starter_spell_book";
    public static final String DARK_SPELL_BOOK_ID = "arcanum:dark_spell_book";
    /** TEK Yasak Lanetler Kitabı — 3 affedilmez laneti (avada_kedavra/crucio/imperio) birden açar. */
    public static final String UNFORGIVABLE_GRIMOIRE_ID = "arcanum:unforgivable_grimoire";

    /** tier → (gerekli XP seviyesi). Kesinlikle artan: basic < advanced < dark < unforgivable. */
    public static int xpCost(String tier) {
        return switch (tier) {
            case "basic" -> 1;
            case "advanced" -> 4;
            case "dark" -> 8;
            case "unforgivable" -> 16;
            default -> Integer.MAX_VALUE;
        };
    }

    /** Sunucu-yetkili "bu büyüyü biliyor mu" — artık HİÇBİR kademe bypass edilmez. */
    public static boolean knows(MinecraftServer server, ServerPlayer player, Spell s) {
        return ArcanumPlayerData.get(server).knows(player, s.id());
    }

    /** İtem id'sini (arcanum:xxx) verir, boş/bilinmeyen stack için null. */
    public static String itemIdOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? null : id.toString();
    }

    /**
     * Verilen stack bir büyü kitabı mı — starter/dark spellbook VE tek Yasak
     * Lanetler Kitabı'nı (unforgivable_grimoire) kabul eder. GUI slot mayPlace
     * tahmini için.
     */
    public static boolean isAnySpellBook(ItemStack stack) {
        String idStr = itemIdOf(stack);
        if (idStr == null) {
            return false;
        }
        return idStr.equals(STARTER_SPELL_BOOK_ID)
                || idStr.equals(DARK_SPELL_BOOK_ID)
                || idStr.equals(UNFORGIVABLE_GRIMOIRE_ID);
    }

    /** Verilen stack per-spell haritasındaki reagent'lardan biri mi — GUI slot mayPlace tahmini için. */
    public static boolean isAnyReagent(ItemStack stack) {
        String idStr = itemIdOf(stack);
        return idStr != null && SpellReagents.isReagent(idStr);
    }

    /**
     * Verilen kitap stack'inin bu büyüyü açıp açmadığı. Eşleme:
     * <ul>
     *   <li>starter → basic + advanced kademesi</li>
     *   <li>dark → yalnızca dark kademesi</li>
     *   <li>unforgivable_grimoire → unforgivable kademesinin TAMAMI
     *       (avada_kedavra + crucio + imperio)</li>
     * </ul>
     */
    public static boolean bookUnlocks(ItemStack bookStack, Spell spell) {
        String idStr = itemIdOf(bookStack);
        if (idStr == null) {
            return false;
        }
        String tier = spell.tier();
        return switch (idStr) {
            case STARTER_SPELL_BOOK_ID -> "basic".equals(tier) || "advanced".equals(tier);
            case DARK_SPELL_BOOK_ID -> "dark".equals(tier);
            case UNFORGIVABLE_GRIMOIRE_ID -> "unforgivable".equals(tier);
            default -> false;
        };
    }

    /**
     * Elde tutulan kitabın ŞU AN açabileceği (henüz bilinmeyen, kademe ön-koşulu
     * sağlanan) TÜM büyüleri döndürür. Kademe ön-koşulu: bir büyüyü eklemeden önce
     * {@link SpellTiers#prevTier} != null ise {@link SpellTiers#hasAllOfTier} ile
     * bir alt kademenin TAMAMI bilinmeli. Zaten bilinen büyü eklenmez.
     */
    public static List<Spell> unlockableSpells(ItemStack bookStack, Set<String> known) {
        List<Spell> out = new ArrayList<>();
        if (bookStack.isEmpty()) {
            return out;
        }
        for (Spell s : ModSpells.SPELLS) {
            if (ModSpells.isDisabled(s.id())) {
                continue; // pasifleştirilmiş büyü (rictusempra) masada listelenmez
            }
            if (known.contains(s.id())) {
                continue;
            }
            if (!bookUnlocks(bookStack, s)) {
                continue;
            }
            String prev = SpellTiers.prevTier(s.tier());
            if (prev != null && !SpellTiers.hasAllOfTier(known, prev)) {
                continue;
            }
            out.add(s);
        }
        return out;
    }

    /** Verilen büyünün gerektirdiği reagent item id'si (GUI tooltip'i için public). */
    public static String requiredReagentId(Spell spell) {
        return SpellReagents.reagentFor(spell.id());
    }

    /**
     * Öğrenme denemesi sonucu — mesaj chat/actionbar geri bildirimi için,
     * {@code spell} ise başarı durumunda VFX'in tema rengini seçmek için
     * kullanılır (başarısızlıkta her zaman null).
     */
    public record LearnResult(boolean success, Component message, Spell spell) {
        public static LearnResult ok(Component msg, Spell spell) {
            return new LearnResult(true, msg, spell);
        }

        public static LearnResult fail(Component msg) {
            return new LearnResult(false, msg, null);
        }
    }

    /**
     * TAM doğrulama sırası. İstemciden yalnızca hedef spellId gelir; geri kalan her
     * şey (kitap/XP/malzeme/kademe tamamlanma) burada sunucu durumundan yeniden
     * okunur — istemci iddiasına ASLA güvenilmez. Kitap ve reagent çağıranın verdiği
     * SOMUT slot ItemStack referanslarından (SpellTableMenu'nun kitap/reagent slotları)
     * okunur; bunlar .copy() değildir, reagentStack.shrink(1) doğrudan o slotun
     * içeriğini küçültür ve broadcastChanges() döngüsü bunu istemciye senkronlar.
     */
    public static LearnResult attemptLearn(MinecraftServer server, ServerPlayer player, String spellId,
                                            ItemStack bookStack, net.minecraft.world.Container reagentSlots) {
        Spell target = null;
        for (Spell s : ModSpells.SPELLS) {
            if (s.id().equals(spellId)) {
                target = s;
                break;
            }
        }
        if (target == null) {
            // bozuk/bayat istemci paketi — sessizce yok say, gerçek UI'dan asla gelmemeli
            return LearnResult.fail(null);
        }
        if (ModSpells.isDisabled(target.id())) {
            // pasifleştirilmiş büyü sunucu tarafında da ASLA öğrenilemez (bayat istemci
            // listesine karşı savunma) — gri "artık kullanılmıyor" geri bildirimi.
            return LearnResult.fail(Component.translatable("arcanum.spell_disabled"));
        }

        ArcanumPlayerData data = ArcanumPlayerData.get(server);
        Set<String> known = data.knownOf(player);
        String tier = target.tier();

        // 2) zaten biliniyor mu
        if (known.contains(target.id())) {
            return LearnResult.fail(Component.translatable("arcanum.spell_table.already_known", target.name()));
        }

        // 3) doğru kitap slotta mı (kitap → büyü eşlemesi bookUnlocks ile)
        if (!bookUnlocks(bookStack, target)) {
            return LearnResult.fail(Component.translatable("arcanum.spell_table.need_book",
                    requiredBookName(target)));
        }

        // 4) kademe ön koşulu (basic hariç hepsi bir öncekinin TAMAMINI ister)
        String prev = SpellTiers.prevTier(tier);
        if (prev != null && !SpellTiers.hasAllOfTier(known, prev)) {
            return LearnResult.fail(Component.translatable("arcanum.spell_table.tier_incomplete",
                    Component.translatable("arcanum.tier." + prev)));
        }

        // 5) XP seviyesi yeterli mi
        int cost = xpCost(tier);
        if (player.experienceLevel < cost) {
            return LearnResult.fail(Component.translatable("arcanum.spell_table.need_xp",
                    cost, player.experienceLevel));
        }

        // 6) bu büyünün gerektirdiği reagent (per-spell) 6 slottan HANGİSİNDE varsa onu bul
        String need = SpellReagents.reagentFor(target.id());
        net.minecraft.world.item.ItemStack matched = null;
        if (need != null) {
            for (int i = 0; i < reagentSlots.getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack st = reagentSlots.getItem(i);
                if (!st.isEmpty() && need.equals(itemIdOf(st))) {
                    matched = st;
                    break;
                }
            }
        }
        if (matched == null && !player.isCreative()) {
            return LearnResult.fail(Component.translatable("arcanum.spell_table.need_reagent",
                    reagentDisplayName(need)));
        }

        // 7) TÜM kontroller geçti → öğren. Reagent yalnızca HAYATTA KALMA modunda tüketilir
        // (yaratıcı modda reagent gerekmez ve harcanmaz — tester isteği).
        if (matched != null && !player.isCreative()) {
            matched.shrink(1);
            reagentSlots.setChanged();
        }
        player.giveExperienceLevels(-cost);
        data.learn(player, target.id());

        return LearnResult.ok(Component.translatable("arcanum.spell_table.learned", target.name()), target);
    }

    /** Hedef büyü için gereken kitabın görünen adı (need_book fail mesajı için). */
    private static Component requiredBookName(Spell spell) {
        String bookId = switch (spell.tier()) {
            case "unforgivable" -> UNFORGIVABLE_GRIMOIRE_ID;
            case "dark" -> DARK_SPELL_BOOK_ID;
            default -> STARTER_SPELL_BOOK_ID;
        };
        Item item = itemById(bookId);
        return item != null ? item.getDefaultInstance().getHoverName() : Component.literal(bookId);
    }

    private static Item itemById(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null || !BuiltInRegistries.ITEM.containsKey(loc)) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(loc);
    }

    private static Component reagentDisplayName(String id) {
        if (id == null) {
            return Component.literal("?");
        }
        Item item = itemById(id);
        return item != null ? item.getDefaultInstance().getHoverName() : Component.literal(id);
    }
}
