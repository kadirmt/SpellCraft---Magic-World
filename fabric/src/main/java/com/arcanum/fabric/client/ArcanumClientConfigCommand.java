package com.arcanum.fabric.client;

import com.arcanum.config.ArcanumConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

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
 * <p>Disk I/O render thread'inde DEĞİL, komut thread'inde olur.
 */
public final class ArcanumClientConfigCommand {

    private ArcanumClientConfigCommand() {}

    /** {@code onInitializeClient()} içinden bir kez çağrılır. */
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("arcanumclient")
                        .then(ClientCommandManager.literal("reloadconfig")
                                .executes(ctx -> {
                                    ArcanumConfig.reload();
                                    ctx.getSource().sendFeedback(Component.literal(
                                            "[Arcanum] İstemci config yeniden okundu "
                                                    + "(mana barı ölçek/saydamlık/asa şartı)."));
                                    return 1;
                                }))));
    }
}
