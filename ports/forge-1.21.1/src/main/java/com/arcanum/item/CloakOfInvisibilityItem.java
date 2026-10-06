package com.arcanum.item;

import com.arcanum.registry.ModArmorMaterials;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Görünmezlik Pelerini — 0 zırh puanlı göğüslük, giyili olduğu sürece
 * sessizce (partikülsüz/ikonsuz) görünmezlik verir; çıkarılınca birkaç tick
 * içinde kendiliğinden söner.
 */
public class CloakOfInvisibilityItem extends ArmorItem {
    public CloakOfInvisibilityItem(Properties properties) {
        // Forge notu: Arch RegistrySupplier'ın aksine RegistryObject bir Holder değildir.
        // getHolder(), kayıt sırası fark etmeksizin (ITEM eventi ARMOR_MATERIAL'den önce
        // ateşlenir) bağlanabilir bir Holder.Reference döndürür — RegistryObject,
        // oluşturulurken vanilla registry'ye createRegistrationLookup üzerinden bağlanır
        // ve ArmorItem holder'ı tembel (memoize) kullandığı için bu güvenlidir.
        super(ModArmorMaterials.CLOAK_OF_INVISIBILITY.getHolder().orElseThrow(), Type.CHESTPLATE, properties);
    }

    /**
     * Verilen varlık göğüs slotunda Görünmezlik Pelerini giyiyor mu?
     * Client render mixin'leri (zırh/eldeki eşya katmanları) bu koşulla iptal edilir,
     * böylece pelerin giyildiğinde zırh, eldeki eşya ve pelerinin kendi modeli de
     * gizlenerek TAM görünmezlik sağlanır.
     */
    public static boolean isWearing(LivingEntity e) {
        return e != null
                && e.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof CloakOfInvisibilityItem;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide || !(entity instanceof LivingEntity living)) {
            return;
        }
        // inventoryTick, envanterdeki HER kopya için (giyili olsun olmasın) her
        // tick çağrılır — bu yüzden yalnızca GİYİLİYKEN efekt uyguluyoruz;
        // giyili değilken kaldırma işlemi YAPMIYORUZ, aksi halde oyuncunun
        // ayrıca içtiği gerçek bir görünmezlik iksirini her tick silmiş oluruz.
        // Süre kısa (10 tick) olduğu için çıkarılınca zaten kendiliğinden söner.
        if (living.getItemBySlot(EquipmentSlot.CHEST).is(this)) {
            living.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 10, 0, false, false, false));
        }
    }
}
