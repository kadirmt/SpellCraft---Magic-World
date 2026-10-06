package com.arcanum.registry;

import com.arcanum.Arcanum;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Map;

/**
 * Özel zırh malzemeleri (ArmorMaterial kayıtları, 1.21.1 registry-tabanlı sistem).
 */
public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, Arcanum.MODID);

    /** Görünmezlik Pelerini — 0 zırh puanı, sadece efekt taşıyıcısı (kaynak: HogCraft). */
    public static final RegistryObject<ArmorMaterial> CLOAK_OF_INVISIBILITY =
            ARMOR_MATERIALS.register("cloak_of_invisibility", () -> new ArmorMaterial(
                    Map.of(
                            ArmorItem.Type.HELMET, 0,
                            ArmorItem.Type.CHESTPLATE, 0,
                            ArmorItem.Type.LEGGINGS, 0,
                            ArmorItem.Type.BOOTS, 0,
                            ArmorItem.Type.BODY, 0),
                    9,
                    SoundEvents.ARMOR_EQUIP_GENERIC,
                    () -> Ingredient.EMPTY,
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(Arcanum.MODID, "cloak_of_invisibility"))),
                    0.0f,
                    0.0f));

    private ModArmorMaterials() {}

    public static void register(IEventBus bus) {
        ARMOR_MATERIALS.register(bus);
    }
}
