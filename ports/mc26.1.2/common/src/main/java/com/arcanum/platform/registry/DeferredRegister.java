package com.arcanum.platform.registry;

import com.arcanum.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Architectury {@code DeferredRegister} alt kümesinin taklidi + {@code setId} zorunluluğu için anahtar alan overload.
 *
 * <pre>{@code
 * public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Arcanum.MODID, Registries.ITEM);
 * public static final RegistrySupplier<Item> WAND = ITEMS.register("wand",
 *         key -> new WandItem(new Item.Properties().setId(key).stacksTo(1)));
 * public static void init() { ITEMS.register(); }
 * }</pre>
 *
 * <p>Overload seçimi lambda ARİTESİYLE yapılır ({@code () -> ...} vs {@code key -> ...}). Method reference
 * ({@code Foo::new}) iki overload'a da uyabilir → açık lambda yazın.
 *
 * <p>{@code create(...)} {@link Platform#get()} ister → loader giriş sınıfı, {@code registry/} sınıflarına dokunmadan
 * ÖNCE {@code Platform.init(...)} çağırmış olmalıdır.
 */
public final class DeferredRegister<T> {
    private final String modId;
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final RegistryBackend<T> backend;

    private DeferredRegister(String modId, ResourceKey<? extends Registry<T>> registryKey) {
        this.modId = modId;
        this.registryKey = registryKey;
        this.backend = Platform.get().createRegistryBackend(modId, registryKey);
    }

    public static <T> DeferredRegister<T> create(String modId, ResourceKey<? extends Registry<T>> registryKey) {
        return new DeferredRegister<>(modId, registryKey);
    }

    /** Anahtara ihtiyaç duymayan kayıtlar (MobEffect, Potion, ParticleType, MenuType, DataComponentType, CreativeModeTab...). */
    public <R extends T> RegistrySupplier<R> register(String name, Supplier<? extends R> supplier) {
        return backend.add(Identifier.fromNamespaceAndPath(modId, name), key -> supplier.get());
    }

    /** Item / Block / EntityType: {@code Properties.setId(key)} / {@code Builder.build(key)} ZORUNLU (1.21.2+). */
    public <R extends T> RegistrySupplier<R> register(String name, Function<ResourceKey<T>, ? extends R> factory) {
        return backend.add(Identifier.fromNamespaceAndPath(modId, name), factory);
    }

    /** Flush/bağla (Architectury adıyla aynı). */
    public void register() {
        backend.bind();
    }

    public String modId() {
        return modId;
    }

    public ResourceKey<? extends Registry<T>> registryKey() {
        return registryKey;
    }
}
