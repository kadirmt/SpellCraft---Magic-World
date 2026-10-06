package com.arcanum.platform.registry;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Architectury {@code RegistrySupplier} alt kümesinin taklidi (kök {@code registry/} sınıfları yalnız import
 * değiştirerek taşınsın diye).
 *
 * <p><b>FORGE'UN KATI KURALI ORTAK KODDA GEÇERLİDİR:</b> {@link #get()} / {@link #holder()} statik init'te veya
 * mod ctor'unda ÇAĞRILMAZ — yalnız (a) başka bir kaydın fabrika lambda'sı içinde (vanilla kayıt sırası izin veriyorsa),
 * (b) {@link #listen(Consumer)} geri çağrısında, (c) kayıt sonrası olaylarda / oyun çalışırken.
 */
public interface RegistrySupplier<T> extends Supplier<T> {

    /** Kayıtlı nesne. Kayıttan önce çağrılırsa {@link IllegalStateException} (Forge: NullPointerException olabilir). */
    @Override
    T get();

    /**
     * Kayıtlı {@code Holder.Reference}. Tip parametresi Architectury'deki gibi denetimsiz daraltılır
     * (ör. {@code RegistrySupplier<MobEffect>.holder()} → {@code Holder<MobEffect>}).
     */
    Holder<T> holder();

    Identifier getId();

    boolean isPresent();

    /** Kayıt anında bir kez; zaten kayıtlıysa HEMEN çağrılır. */
    void listen(Consumer<? super T> callback);
}
