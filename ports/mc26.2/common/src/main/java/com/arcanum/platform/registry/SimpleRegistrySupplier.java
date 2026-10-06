package com.arcanum.platform.registry;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Backend'lerin paylaşabileceği hazır {@link RegistrySupplier}: kayıt anında {@link #bind(Object, Holder)} çağrılır.
 * (Fabric backend bunu kullanır; Forge backend isterse kullanır, isterse RegistryObject sarar.)
 */
public final class SimpleRegistrySupplier<T> implements RegistrySupplier<T> {
    private final Identifier id;
    private T value;
    private Holder<T> holder;
    private List<Consumer<? super T>> listeners;

    public SimpleRegistrySupplier(Identifier id) {
        this.id = id;
    }

    /** Backend çağırır: nesne registry'ye girdikten HEMEN sonra. */
    public void bind(T value, Holder<T> holder) {
        if (this.value != null) throw new IllegalStateException("Zaten bagli: " + id);
        this.value = value;
        this.holder = holder;
        if (listeners != null) {
            List<Consumer<? super T>> pending = listeners;
            listeners = null;
            for (Consumer<? super T> l : pending) l.accept(value);
        }
    }

    @Override
    public T get() {
        if (value == null) {
            throw new IllegalStateException("Kayit henuz yapilmadi: " + id
                    + " — .get() statik init/ctor'da YASAK (ARCHITECTURE §3)");
        }
        return value;
    }

    @Override
    public Holder<T> holder() {
        if (holder == null) {
            throw new IllegalStateException("Kayit henuz yapilmadi (holder): " + id);
        }
        return holder;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public boolean isPresent() {
        return value != null;
    }

    @Override
    public void listen(Consumer<? super T> callback) {
        if (value != null) {
            callback.accept(value);
            return;
        }
        if (listeners == null) listeners = new ArrayList<>();
        listeners.add(callback);
    }

    @Override
    public String toString() {
        return "RegistrySupplier[" + id + (value != null ? "" : ", bos") + "]";
    }
}
