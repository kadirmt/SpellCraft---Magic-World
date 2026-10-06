package com.arcanum.mixin.datafix;

import com.arcanum.util.ArcanumDataFixerEntities;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.schemas.V1460;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Arcanum varlık kimliklerini DFU varlık seçimine ekler — gerekçe ve şablonlar {@link ArcanumDataFixerEntities}.
 * Hedef: {@code public Map<String, Supplier<TypeTemplate>> V1460#registerEntities(Schema)} (26.1.2 vanilla kaynak;
 * 26.2'de sınıf/imza değişmedi). V1460 haritayı {@code Maps.newHashMap()} ile kurar; sonraki tüm şemalar (V1510 … V4656)
 * {@code super.registerEntities(schema)} zinciriyle bu çağrıya iner → tek kanca her şemayı kapsar.
 */
@Mixin(V1460.class)
public abstract class V1460EntitiesMixin {
    @Inject(method = "registerEntities", at = @At("RETURN"))
    private void arcanum$registerArcanumEntities(Schema schema, CallbackInfoReturnable<Map<String, Supplier<TypeTemplate>>> cir) {
        ArcanumDataFixerEntities.register(schema, cir.getReturnValue());
    }
}
