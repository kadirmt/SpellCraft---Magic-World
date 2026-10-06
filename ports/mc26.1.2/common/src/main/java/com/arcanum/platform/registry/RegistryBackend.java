package com.arcanum.platform.registry;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.function.Function;

/**
 * Tek bir registry için loader kayıt arka ucu. {@link DeferredRegister} ince bir sarmaldır; iş burada yapılır.
 *
 * <p>Fabric: {@link #add} kuyruğa alır, {@link #bind()} anında {@code Registry.registerForHolder} ile SIRAYLA kaydeder
 * (bind sonrası gelen add anında kaydedilir). Forge: Forge {@code DeferredRegister}/{@code RegistryObject} sarmalı;
 * {@link #bind()} mod ctor'unda BusGroup'a bağlar, fabrikalar RegisterEvent'te çalışır.
 */
public interface RegistryBackend<T> {

    /**
     * @param id      tam kimlik (modId:name)
     * @param factory kayıt anahtarını alıp nesneyi üreten fabrika (Item/Block/EntityType {@code setId}/{@code build(key)} için).
     *                Fabrika EN FAZLA bir kez ve yalnız kayıt anında çağrılır.
     */
    <R extends T> RegistrySupplier<R> add(Identifier id, Function<ResourceKey<T>, ? extends R> factory);

    /** {@code DeferredRegister.register()} — flush/bağla. İkinci çağrı hata. */
    void bind();
}
