package com.arcanum.fabric.client;

import com.arcanum.client.ArcanumClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;

/**
 * İSTEMCİ tarafında {@code config/arcanum.json}'ı yeniden okutan komut:
 * {@code /arcanumclient reloadconfig}.
 *
 * <p>NEDEN GEREKLİ: {@code /arcanum reloadconfig} bir SUNUCU komutudur ve
 * {@code ArcanumConfig.reload()} yalnız çağrıldığı JVM'in statik örneğini tazeler.
 * Tek oyunculu/LAN'da istemci ile entegre sunucu aynı JVM olduğu için bar anında
 * güncellenir; ADANMIŞ SUNUCUDA ise komut yalnız sunucuyu tazeler ve oyuncunun
 * kendi {@code manaHudScale}/{@code manaHudOpacity}/{@code manaHudRequiresWand}
 * değerleri oyun yeniden başlatılana kadar bayat kalırdı. Bu komut o boşluğu
 * kapatır.
 *
 * <p>İZİN GEREKTİRMEZ: salt istemci-yerel, salt görsel ayarları okur — op olmayan
 * oyuncular da kullanabilir. Sunucu-yetkili alanlar (hasar/mana/menzil çarpanları,
 * spawn ayarları …) bu çağrıdan ETKİLENMEZ; onları sunucunun kendi örneği belirler.
 *
 * <p>Disk I/O render thread'inde DEĞİL, komut thread'inde olur. Gövde ortak
 * {@link ArcanumClient#reloadClientConfig()} (Forge karşılığı aynı metodu çağırır).
 * 26.x: {@code ClientCommandManager} → {@code ClientCommands} (fabric-command-api-v2 3.0.5, javap).
 */
public final class ArcanumClientConfigCommand {

    private ArcanumClientConfigCommand() {}

    /** {@code onInitializeClient()} içinden bir kez çağrılır. */
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("arcanumclient")
                        .then(ClientCommands.literal("reloadconfig")
                                .executes(ctx -> {
                                    ctx.getSource().sendFeedback(ArcanumClient.reloadClientConfig());
                                    return 1;
                                }))));
    }
}
