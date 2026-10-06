package com.arcanum.forge.platform;

import com.arcanum.Arcanum;
import com.arcanum.platform.registry.RegistryBackend;
import com.arcanum.platform.registry.RegistrySupplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Forge registry backend'i: Forge {@link DeferredRegister} + {@link RegistryObject} ince sarmalı.
 *
 * <ul>
 *   <li>{@link #add}: {@code dr.register(path, () -> factory.apply(key))} — fabrika Forge'un RegisterEvent'inde,
 *       o registry'nin sırası geldiğinde çalışır (Forge sırası = vanilla registry sırası, bkz. g2-platform-contract §11).</li>
 *   <li>{@link #bind()}: mod ctor'unda {@code dr.register(modBusGroup)} (RegisterEvent mod-bus olayıdır) + aynı olaya
 *       {@link Priority#LOW} ile ikinci dinleyici → Forge DR'nin NORMAL dinleyicisi nesneleri kaydedip RegistryObject'leri
 *       güncelledikten SONRA {@code listen()} kuyrukları boşaltılır.</li>
 *   <li>bind() sonrası add: RegisterEvent gelmeden önce serbest (Forge DR kabul eder); sonrası Forge'da hata.</li>
 * </ul>
 */
final class ForgeRegistryBackend<T> implements RegistryBackend<T> {
    /** Tanı: bizim registry'lerimizin RegisterEvent'i hangi sırayla geldi (P4 kayıt-sırası sorusunun kanıtı). */
    private static final AtomicInteger EVENT_ORDER = new AtomicInteger();
    private static final Set<Identifier> LOGGED = ConcurrentHashMap.newKeySet();

    private final String modId;
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final BusGroup modBusGroup;
    private final DeferredRegister<T> forgeRegister;
    private final List<ForgeRegistrySupplier<? extends T>> suppliers = new ArrayList<>();
    private boolean bound;

    ForgeRegistryBackend(String modId, ResourceKey<? extends Registry<T>> registryKey, BusGroup modBusGroup) {
        this.modId = modId;
        this.registryKey = registryKey;
        this.modBusGroup = modBusGroup;
        this.forgeRegister = DeferredRegister.create(registryKey, modId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R extends T> RegistrySupplier<R> add(Identifier id, Function<ResourceKey<T>, ? extends R> factory) {
        if (!modId.equals(id.getNamespace())) {
            throw new IllegalArgumentException("Forge DeferredRegister yalniz kendi namespace'ini kaydeder: " + id);
        }
        ResourceKey<T> key = ResourceKey.create((ResourceKey<? extends Registry<T>>) registryKey, id);
        RegistryObject<R> object = forgeRegister.register(id.getPath(), () -> factory.apply(key));
        ForgeRegistrySupplier<R> supplier = new ForgeRegistrySupplier<>(object);
        suppliers.add(supplier);
        return supplier;
    }

    @Override
    public void bind() {
        if (bound) {
            throw new IllegalStateException("DeferredRegister.register() iki kez cagrildi: " + registryKey.identifier());
        }
        bound = true;
        forgeRegister.register(modBusGroup);
        RegisterEvent.getBus(modBusGroup).addListener(Priority.LOW, this::afterRegister);
    }

    private void afterRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(registryKey)) return;
        if (LOGGED.add(registryKey.identifier())) {
            Arcanum.LOGGER.info("[Arcanum/Forge] RegisterEvent sirasi #{}: {}", EVENT_ORDER.incrementAndGet(),
                    registryKey.identifier());
        }
        for (ForgeRegistrySupplier<? extends T> s : suppliers) s.fireListeners();
    }
}
