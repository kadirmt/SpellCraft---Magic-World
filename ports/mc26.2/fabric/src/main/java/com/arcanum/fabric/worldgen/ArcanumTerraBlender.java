package com.arcanum.fabric.worldgen;

import com.arcanum.worldgen.ArcanumRegions;
import terrablender.api.TerraBlenderApi;

/**
 * TerraBlender giriş noktası (fabric.mod.json "terrablender" entrypoint).
 * Arcanum biyom bölgesini overworld'e kaydeder.
 *
 * <p>DİKKAT: TerraBlender bu entrypoint'i kendi {@code onInitialize}'ı içinden çağırır ve Fabric mod init sırası
 * garantili değildir → arcanum'un ANA entrypoint'inden (Platform.init) ÖNCE koşabilir. Bu yüzden burada
 * yalnız {@code ResourceKey} ile çalışan bölge kaydı yapılır; {@code Platform.get()}/{@code RegistrySupplier.get()}
 * YASAK. Gloomwood yüzey kuralları (blok ister) {@code Arcanum.commonSetup()} içinde
 * ({@link ArcanumRegions#registerSurfaceRules()}) — kökteki {@code GLOOM_GRASS_BLOCK.listen(...)} eşdeğeri.
 */
public final class ArcanumTerraBlender implements TerraBlenderApi {
    @Override
    public void onTerraBlenderInitialized() {
        ArcanumRegions.registerRegions();
    }
}
