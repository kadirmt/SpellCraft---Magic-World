package com.arcanum.item;

import com.arcanum.registry.ModArmorMaterials;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Görünmezlik Pelerini — 0 zırh puanlı göğüslük, giyili olduğu sürece
 * sessizce (partikülsüz/ikonsuz) görünmezlik verir; çıkarılınca birkaç tick
 * içinde kendiliğinden söner.
 *
 * <p>26.x: {@code ArmorItem} sınıfı YOK → düz {@link Item} + zırh bileşenleri
 * ({@link ModArmorMaterials#cloakOfInvisibility}). Dayanıklılık bileşeni EKLENMEZ — pelerin kırılmaz kalır.
 */
public class CloakOfInvisibilityItem extends Item {
    public CloakOfInvisibilityItem(Properties properties) {
        super(ModArmorMaterials.cloakOfInvisibility(properties));
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
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        // 26.x: inventoryTick artık YALNIZ sunucuda çağrılır (ServerLevel parametresi) → eski
        // isClientSide kontrolü gereksiz. AYRICA artık her LivingEntity'nin ekipmanı için de
        // çağrılıyor (EntityEquipment#tick); 1.21.1'de yalnız oyuncu envanteri tick alıyordu →
        // pelerin giyen mob/zırh askısı görünmez OLMASIN diye yalnız oyuncuya uygulanır (parite).
        if (!(entity instanceof Player living)) {
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
