package com.arcanum.devtest.fabric;

import com.arcanum.devtest.DevTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Fabric ince girişi (dev-only mod "arcanum_devtest", yalnız {@code runDevtestClient}'in sınıf yolunda).
 * {@code -Darcanum.devtest} yoksa tick dinleyicisi bile kaydedilmez.
 */
public final class DevTestFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (!DevTest.enabled()) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(DevTest::onClientTick);
    }
}
