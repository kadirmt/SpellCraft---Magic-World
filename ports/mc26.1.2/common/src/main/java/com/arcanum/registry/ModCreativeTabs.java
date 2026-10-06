package com.arcanum.registry;

import com.arcanum.Arcanum;
import com.arcanum.platform.registry.DeferredRegister;
import com.arcanum.platform.registry.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Arcanum yaratıcı (creative) sekmesi — asalar, büyü kitapları, bloklar.
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Arcanum.MODID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> ARCANUM_TAB =
            TABS.register("arcanum", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.arcanum"))
                    .icon(() -> new ItemStack(ModItems.ARCANEWOOD_WAND.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.ARCANEWOOD_WAND.get());
                        output.accept(ModItems.THUNDERBIRD_WAND.get());
                        output.accept(ModItems.TROLL_WAND.get());
                        output.accept(ModItems.PHOENIX_WAND.get());
                        output.accept(ModItems.ELDER_WAND.get());
                        output.accept(ModItems.BROOM.get());
                        output.accept(ModItems.DEATH_EATER_SPAWN_EGG.get());
                        output.accept(ModItems.DEMENTOR_SPAWN_EGG.get());
                        output.accept(ModItems.BOWTRUCKLE_SPAWN_EGG.get());
                        output.accept(ModItems.MOONCALF_SPAWN_EGG.get());
                        output.accept(ModItems.CLOAK_OF_INVISIBILITY.get());
                        output.accept(ModItems.SORTING_HAT.get());
                        output.accept(ModItems.TROLL_SPAWN_EGG.get());
                        output.accept(ModItems.PHOENIX_SPAWN_EGG.get());
                        output.accept(ModItems.THUNDERBIRD_SPAWN_EGG.get());
                        output.accept(ModItems.SNOWY_OWL_SPAWN_EGG.get());
                        output.accept(ModItems.THESTRAL_SPAWN_EGG.get());
                        output.accept(ModItems.UNICORN_SPAWN_EGG.get());
                        output.accept(ModBlocks.ARCANEWOOD_LOG.get());
                        output.accept(ModBlocks.STRIPPED_ARCANEWOOD_LOG.get());
                        output.accept(ModBlocks.ARCANEWOOD_PLANKS.get());
                        output.accept(ModBlocks.ARCANEWOOD_LEAVES.get());
                        output.accept(ModBlocks.ARCANEWOOD_SAPLING.get());
                        output.accept(ModBlocks.ARCANEWOOD_DOOR.get());
                        output.accept(ModBlocks.WARDED_DOOR.get());
                        output.accept(ModItems.ARCANEWOOD_BOAT.get());
                        output.accept(ModItems.ARCANEWOOD_CHEST_BOAT.get());
                        output.accept(ModBlocks.GLOOM_GRASS_BLOCK.get());
                        output.accept(ModBlocks.GLOOM_SOIL.get());
                        output.accept(ModItems.HIPPOGRIFF_SPAWN_EGG.get());
                        output.accept(ModItems.ACROMANTULA_SPAWN_EGG.get());
                        output.accept(ModItems.WEREWOLF_SPAWN_EGG.get());
                        output.accept(ModItems.GRINDYLOW_SPAWN_EGG.get());
                        output.accept(ModItems.KNEAZLE_SPAWN_EGG.get());
                        output.accept(ModItems.WIZARD_TRADER_SPAWN_EGG.get());
                        output.accept(ModItems.TROLL_HIDE.get());
                        output.accept(ModItems.PHOENIX_FEATHER.get());
                        output.accept(ModItems.THUNDERBIRD_FEATHER.get());
                        output.accept(ModItems.SNOWY_OWL_FEATHER.get());
                        output.accept(PotionContents.createItemStack(Items.POTION, ModPotions.MANA_HASTE_POTION.holder()));
                        output.accept(PotionContents.createItemStack(Items.POTION, ModPotions.EXSTIMULO_POTION.holder()));
                        output.accept(PotionContents.createItemStack(Items.POTION, ModPotions.GIRDING_POTION.holder()));
                        // Büyü Masası ilerleme sistemi: kara büyü kitabı + reagent'lar
                        output.accept(ModItems.DARK_SPELL_BOOK.get());
                        output.accept(ModItems.PRACTICE_CHALK.get());
                        output.accept(ModItems.SPELL_DIAGRAM.get());
                        output.accept(ModItems.MOONCALF_DUST.get());
                        output.accept(ModItems.ACROMANTULA_SILK.get());
                        output.accept(ModItems.DEMENTOR_ESSENCE.get());
                        output.accept(ModItems.BASILISK_FANG_SHARD.get());
                        output.accept(ModItems.HORCRUX_FRAGMENT.get());
                        // Craftlanabilir combo reagent'lar
                        output.accept(ModItems.ENCHANTED_HONEYCOMB.get());
                        output.accept(ModItems.OWL_CHARM.get());
                        output.accept(ModItems.CRUSHED_AMETHYST.get());
                        output.accept(ModItems.PHOENIX_QUILL.get());
                        output.accept(ModItems.STORM_VIAL.get());
                        // Büyü Masası bloğu + kitaplar (dark yukarıda; starter + Yasak Lanetler Kitabı)
                        output.accept(ModBlocks.SPELL_TABLE.get());
                        output.accept(ModItems.STARTER_SPELL_BOOK.get());
                        output.accept(ModItems.UNFORGIVABLE_GRIMOIRE.get());
                        // Süpürge kademeleri
                        output.accept(ModItems.OAKSHAFT_BROOM.get());
                        output.accept(ModItems.COMET_BROOM.get());
                    })
                    .build());

    private ModCreativeTabs() {}

    public static void init() {
        TABS.register();
    }
}
