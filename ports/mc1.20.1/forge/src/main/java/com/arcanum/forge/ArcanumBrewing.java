package com.arcanum.forge;

import java.lang.reflect.Method;

import com.arcanum.Arcanum;
import com.arcanum.registry.ModItems;
import com.arcanum.registry.ModPotions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;

/**
 * İksir tarifleri (brewing stand) — fabric'teki
 * {@code FabricBrewingRecipeRegistry.registerPotionRecipe(Potion, Ingredient, Potion)}
 * zincirinin Forge 1.20.1 karşılığı. Normal büyücülük tezgahı reçeteleriyle (crafting)
 * karıştırılmasın: bunlar data-driven değil, kod ile kaydediliyor (vanilla kısıtı).
 *
 * <p>MEKANİZMA: Forge 47.x'te brewing kayıt EVENT'İ YOK (BrewingRecipeRegisterEvent
 * 1.20.2+'da geldi — forge-1.20.1-47.4.10 merged jar javap ile doğrulandı; sadece
 * PotionBrewEvent/PlayerBrewedPotionEvent var). Forge'un kendi
 * {@code BrewingRecipeRegistry.addRecipe(Ingredient,Ingredient,ItemStack)} yolu ise
 * girdi potion'ını NBT'siz eşler (her iksir + tüy → çıktı olurdu) — YANLIŞ.
 * Fabric'in yaptığının birebir aynısı yapılır: vanilla
 * {@code PotionBrewing.addMix(Potion, Item, Potion)} çağrılır (Fabric API de aynı
 * vanilla mix listesine yazar). Metot 1.20.1'de PRIVATE → {@code setFlammable}
 * kalıbındaki çift-isimli yansıma: dev ortamı mojmap ("addMix"), üretim jar'ı SRG
 * ("m_43513_" — srg-mapped jar'dan javap ile doğrulandı:
 * {@code private static void m_43513_(Potion, Item, Potion)}).
 *
 * <p>Kayıt yeri: {@code FMLCommonSetupEvent.enqueueWork} (ana thread, registry'ler
 * tamam — statik POTION_MIXES listesine yazmanın güvenli anı).
 * ModPotions.holderOf köprüsü 1.20.1'de gereksiz — Potion doğrudan geçer,
 * "Unregistered holder" çökmesi bu sürümde olmaz (bkz. f3-core-contracts.md §3).
 */
public final class ArcanumBrewing {
    private ArcanumBrewing() {}

    public static void register() {
        // Mana Hızlandırma İksiri: Garip İksir + Anka tüyü → mana yenilenmesi %40 hızlı
        addMix(Potions.AWKWARD, ModItems.PHOENIX_FEATHER.get(), ModPotions.MANA_HASTE_POTION.get());
        // Exstimulo: Güç İksiri + 3 farklı kuş tüyü (sırayla) → güç ×1.5, mana %20 hızlı, cooldown %20 az
        addMix(Potions.STRENGTH, ModItems.PHOENIX_FEATHER.get(), ModPotions.EXSTIMULO_STAGE1.get());
        addMix(ModPotions.EXSTIMULO_STAGE1.get(), ModItems.THUNDERBIRD_FEATHER.get(), ModPotions.EXSTIMULO_STAGE2.get());
        addMix(ModPotions.EXSTIMULO_STAGE2.get(), ModItems.SNOWY_OWL_FEATHER.get(), ModPotions.EXSTIMULO_POTION.get());
        // Girding: Garip İksir + Troll derisi → %40 dayanıklılık + %20 hız
        addMix(Potions.AWKWARD, ModItems.TROLL_HIDE.get(), ModPotions.GIRDING_POTION.get());
    }

    /**
     * {@code PotionBrewing.addMix(Potion,Item,Potion)} — vanilla, PRIVATE.
     * Fabric'in registerPotionRecipe'i ile aynı listeye (POTION_MIXES) yazar;
     * girdi iksiri NBT'den doğru eşlenir, REI/JEI de mix'i görür.
     */
    private static void addMix(Potion from, Item ingredient, Potion to) {
        for (String name : new String[] {"addMix", "m_43513_"}) {
            try {
                Method m = PotionBrewing.class.getDeclaredMethod(name, Potion.class, Item.class, Potion.class);
                m.setAccessible(true);
                m.invoke(null, from, ingredient, to);
                return;
            } catch (ReflectiveOperationException ignored) {
                // diğer ismi dene
            }
        }
        Arcanum.LOGGER.warn("[Arcanum] PotionBrewing.addMix erişilemedi — {} -> {} iksir tarifi kaydedilemedi",
                from, to);
    }
}
