package com.arcanum.devtest.forge;

import com.arcanum.devtest.DevTest;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge ince girişi (dev-only mod "arcanum_devtest", yalnız {@code runDevtestClient}'in sınıf yolunda).
 * {@code -Darcanum.devtest} yoksa veya dedicated sunucudaysa hiçbir dinleyici kaydedilmez.
 * Olay: {@code TickEvent.ClientTickEvent.Post.BUS} (DEFAULT grup — ArcanumForgeClient ile aynı).
 */
@Mod("arcanum_devtest")
public final class DevTestForge {
    public DevTestForge(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT && DevTest.enabled()) {
            Client.register();
        }
    }

    /** İstemci sınıflarına dokunan kısım ayrı sınıfta (sunucuda yüklenmesin). */
    private static final class Client {
        static void register() {
            TickEvent.ClientTickEvent.Post.BUS.addListener(Client::onTick);
        }

        private static void onTick(TickEvent.ClientTickEvent.Post event) {
            DevTest.onClientTick(Minecraft.getInstance());
        }
    }
}
