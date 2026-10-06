package com.arcanum.fabric.platform;

import com.arcanum.platform.registry.RegistryBackend;
import com.arcanum.platform.registry.RegistrySupplier;
import com.arcanum.platform.registry.SimpleRegistrySupplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Fabric: kuyruğa al → {@link #bind()} anında vanilla {@code Registry.registerForHolder} ile SIRAYLA kaydet.
 * bind sonrası gelen {@code add} anında kaydedilir (Architectury davranışı).
 */
final class FabricRegistryBackend<T> implements RegistryBackend<T> {
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final List<Entry<T, ?>> pending = new ArrayList<>();
    private Registry<T> registry;
    private boolean bound;

    FabricRegistryBackend(ResourceKey<? extends Registry<T>> registryKey) {
        this.registryKey = registryKey;
    }

    @Override
    public <R extends T> RegistrySupplier<R> add(Identifier id, Function<ResourceKey<T>, ? extends R> factory) {
        Entry<T, R> entry = new Entry<>(id, factory, new SimpleRegistrySupplier<>(id));
        if (bound) {
            registerNow(entry);
        } else {
            pending.add(entry);
        }
        return entry.supplier;
    }

    @Override
    public void bind() {
        if (bound) throw new IllegalStateException("DeferredRegister.register() iki kez cagrildi: " + registryKey.identifier());
        bound = true;
        for (Entry<T, ?> e : pending) registerNow(e);
        pending.clear();
    }

    @SuppressWarnings("unchecked")
    private Registry<T> registry() {
        if (registry == null) {
            Registry<?> r = BuiltInRegistries.REGISTRY.getValue(registryKey.identifier());
            if (r == null) throw new IllegalStateException("Yerlesik registry yok: " + registryKey.identifier());
            registry = (Registry<T>) r;
        }
        return registry;
    }

    @SuppressWarnings("unchecked")
    private <R extends T> void registerNow(Entry<T, R> e) {
        ResourceKey<T> key = ResourceKey.create(registryKey, e.id);
        R value = e.factory.apply(key);
        Holder.Reference<T> ref = Registry.registerForHolder(registry(), key, value);
        e.supplier.bind(value, (Holder<R>) (Holder<?>) ref);
    }

    private record Entry<T, R extends T>(Identifier id, Function<ResourceKey<T>, ? extends R> factory,
                                         SimpleRegistrySupplier<R> supplier) {}
}
