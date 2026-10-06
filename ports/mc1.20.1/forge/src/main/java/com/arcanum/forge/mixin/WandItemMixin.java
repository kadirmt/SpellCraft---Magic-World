package com.arcanum.forge.mixin;

import java.util.function.Consumer;

import com.arcanum.forge.client.render.WandClientExtensions;
import com.arcanum.item.WandItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;

/**
 * WandItem'a (common sınıfı) Forge'un {@code initializeClient} override'ını mixin ile ekler —
 * GeckoLib asa renderer'ının Forge'da bağlanma noktası.
 *
 * <p>NEDEN MİXİN: WandItem common modülünde ve Forge sınıflarını (IClientItemExtensions)
 * göremez; kayıt da common'daki DeferredRegister'da olduğundan Forge modülünde alt-sınıf
 * takası yapılamaz. Forge'un Item patch'i ctor sonunda {@code initClient()} →
 * (yalnız CLIENT dist) sanal {@code initializeClient(consumer)} çağırır (javap ile
 * doğrulandı: Item ctor bytecode'unda {@code invokevirtual initClient}); mixin sınıf
 * yüklenirken uygulandığı ve item kayıtları mixin'lerden sonra koştuğu için bu override
 * kayıt anında devreye girer. initializeClient Forge'un kendi metodu olduğundan
 * (obfuscate edilmez) refmap kaydı gerekmez.
 *
 * <p>Uzantı gövdesi ayrı sınıfta ({@link WandClientExtensions}) — mixin sınıflarında
 * anonim/iç sınıf tanımlanamaz (Mixin kısıtı). Yalnız CLIENT mixin listesinde
 * (arcanum-forge.mixins.json); dedicated server'da initializeClient zaten hiç çağrılmaz.
 */
@Mixin(WandItem.class)
public abstract class WandItemMixin extends Item {

    private WandItemMixin(Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new WandClientExtensions((WandItem) (Object) this));
    }
}
