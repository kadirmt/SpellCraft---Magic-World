package com.arcanum.forge;

import com.arcanum.registry.ModItems;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Vanilla loot tablolarına Arcanum enjeksiyonu — fabric/ArcanumLoot'un
 * (fabric-loot-api-v2 {@code LootTableEvents.MODIFY}) Forge 1.20.1 karşılığı:
 * {@code LootTableLoadEvent} (FORGE bus). Havuz içerikleri/şansları fabric ile
 * BİREBİR aynı.
 *
 * <p>Eski "tek-büyülük spell_book" dropları KALDIRILMIŞTI — büyü öğrenme artık
 * yalnızca Büyü Masası'nda (reagent + XP maliyetli) olduğu için tematik reagent
 * + deneyim şişesi dolgusu konur; Karanlık Büyü Kitabı (dark + unforgivable
 * erişimi) yalnızca terk edilmiş maden ocaklarında nadir kalır.
 *
 * <p>Bilinçli fark: Fabric'teki {@code source.isBuiltin()} guard'ının Forge'da
 * karşılığı yok — datapack override'ları da event'ten geçer (envanter §2.4/10'da
 * kabul edilen sapma; pratikte vanilla tablolar override edilmedikçe davranış aynı).
 */
public final class ArcanumForgeLoot {
    private ArcanumForgeLoot() {}

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();
        LootTable table = event.getTable();

        if (BuiltInLootTables.ABANDONED_MINESHAFT.equals(name)
                || BuiltInLootTables.SIMPLE_DUNGEON.equals(name)) {
            // %30 şansla basic/advanced reagent + deneyim şişesi (eski advanced kitap havuzunun yerine)
            table.addPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .when(LootItemRandomChanceCondition.randomChance(0.30F))
                    .add(reagent(ModItems.PRACTICE_CHALK, 1, 2).setWeight(4))
                    .add(reagent(ModItems.SPELL_DIAGRAM, 1, 2).setWeight(3))
                    .add(reagent(ModItems.MOONCALF_DUST, 1, 1).setWeight(3))
                    .add(experience(1, 2).setWeight(4))
                    .build());
            // Başlangıç Büyü Kitabı zindan/madende de %20 (tester: "çıkma rate'ini arttır")
            table.addPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .when(LootItemRandomChanceCondition.randomChance(0.20F))
                    .add(LootItem.lootTableItem(ModItems.STARTER_SPELL_BOOK.get()))
                    .build());
            if (BuiltInLootTables.ABANDONED_MINESHAFT.equals(name)) {
                // Karanlık Büyü Kitabı — yalnızca terk edilmiş maden ocaklarında, %5 şansla
                table.addPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(LootItemRandomChanceCondition.randomChance(0.05F))
                        .add(LootItem.lootTableItem(ModItems.DARK_SPELL_BOOK.get()))
                        .build());
                // Seçmen Şapka — craftlanamaz, tek edinim yolu loot: maden ocağı %20
                // (+ Hogsmeade bina sandıkları, bkz. data/arcanum/loot_tables/chests).
                table.addPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(LootItemRandomChanceCondition.randomChance(0.20F))
                        .add(LootItem.lootTableItem(ModItems.SORTING_HAT.get()))
                        .build());
            }
        } else if (BuiltInLootTables.STRONGHOLD_LIBRARY.equals(name)) {
            // Kütüphane: %45 advanced reagent + deneyim şişesi
            table.addPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .when(LootItemRandomChanceCondition.randomChance(0.45F))
                    .add(reagent(ModItems.MOONCALF_DUST, 1, 2).setWeight(4))
                    .add(reagent(ModItems.ACROMANTULA_SILK, 1, 2).setWeight(3))
                    .add(experience(1, 3).setWeight(3))
                    .build());
            // Başlangıç Kitabı kütüphanede %35 (tester istegi)
            table.addPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .when(LootItemRandomChanceCondition.randomChance(0.35F))
                    .add(LootItem.lootTableItem(ModItems.STARTER_SPELL_BOOK.get()))
                    .build());
        } else if (BuiltInLootTables.ANCIENT_CITY.equals(name)) {
            // Antik şehir: %25 dark reagent + deneyim şişesi
            table.addPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .when(LootItemRandomChanceCondition.randomChance(0.25F))
                    .add(reagent(ModItems.BASILISK_FANG_SHARD, 1, 1).setWeight(6))
                    .add(experience(1, 3).setWeight(4))
                    .build());
        } else if (BuiltInLootTables.VILLAGE_CARTOGRAPHER.equals(name)
                || BuiltInLootTables.VILLAGE_TOOLSMITH.equals(name)
                || BuiltInLootTables.VILLAGE_PLAINS_HOUSE.equals(name)
                || BuiltInLootTables.VILLAGE_DESERT_HOUSE.equals(name)
                || BuiltInLootTables.VILLAGE_SAVANNA_HOUSE.equals(name)
                || BuiltInLootTables.VILLAGE_SNOWY_HOUSE.equals(name)
                || BuiltInLootTables.VILLAGE_TAIGA_HOUSE.equals(name)) {
            // Köy sandığı (haritacı/aletçi): %45 şansla Başlangıç Büyü Kitabı — yeni
            // oyuncu ilk büyü kitabına köyden de ulaşabilsin (tek kaynak wizard köylü olmasın).
            table.addPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .when(LootItemRandomChanceCondition.randomChance(0.60F))
                    .add(LootItem.lootTableItem(ModItems.STARTER_SPELL_BOOK.get()))
                    .build());
        }
    }

    /** Sayısı [min,max] aralığında olan bir reagent loot girdisi. */
    private static LootPoolSingletonContainer.Builder<?> reagent(
            RegistrySupplier<Item> item, int min, int max) {
        return LootItem.lootTableItem(item.get())
                .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)));
    }

    private static LootPoolSingletonContainer.Builder<?> experience(int min, int max) {
        return LootItem.lootTableItem(Items.EXPERIENCE_BOTTLE)
                .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)));
    }
}
