package com.arcanum.forge.platform;

import com.arcanum.platform.registry.RegistrySupplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Forge {@link RegistryObject} sarmalı. {@code get()/holder()/isPresent()} doğrudan RegistryObject'e gider;
 * {@link #listen(Consumer)} kuyruğu {@link ForgeRegistryBackend} tarafından, Forge {@code DeferredRegister}'ın
 * RegisterEvent dinleyicisinden SONRA (daha düşük öncelikte) boşaltılır → callback anında nesne kayıtlıdır.
 */
final class ForgeRegistrySupplier<T> implements RegistrySupplier<T> {
    private final RegistryObject<T> object;
    private List<Consumer<? super T>> listeners;

    ForgeRegistrySupplier(RegistryObject<T> object) {
        this.object = object;
    }

    @Override
    public T get() {
        if (!object.isPresent()) {
            throw new IllegalStateException("Kayit henuz yapilmadi: " + object.getId()
                    + " — .get() statik init/mod ctor'unda YASAK (ARCHITECTURE §3; Forge RegisterEvent'ten once)");
        }
        return object.get();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Holder<T> holder() {
        return (Holder<T>) (Holder) object.getHolder().orElseThrow(() ->
                new IllegalStateException("Kayit henuz yapilmadi (holder): " + object.getId()));
    }

    @Override
    public Identifier getId() {
        return object.getId();
    }

    @Override
    public boolean isPresent() {
        return object.isPresent();
    }

    @Override
    public void listen(Consumer<? super T> callback) {
        if (object.isPresent()) {
            callback.accept(object.get());
            return;
        }
        if (listeners == null) listeners = new ArrayList<>();
        listeners.add(callback);
    }

    /** Backend çağırır: ilgili registry'nin RegisterEvent'i işlendikten sonra. */
    void fireListeners() {
        if (listeners == null || !object.isPresent()) return;
        List<Consumer<? super T>> pending = listeners;
        listeners = null;
        T value = object.get();
        for (Consumer<? super T> l : pending) l.accept(value);
    }

    @Override
    public String toString() {
        return "RegistrySupplier[" + object.getId() + (object.isPresent() ? "" : ", bos") + "]";
    }
}
