package com.arcanum.forge.client;

import com.arcanum.client.ArcanumClient;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

/**
 * İSTEMCİ tarafında {@code config/arcanum.json}'ı yeniden okutan komut:
 * {@code /arcanumclient reloadconfig} (Fabric {@code ArcanumClientConfigCommand} paritesi).
 *
 * <p>NEDEN GEREKLİ: {@code /arcanum reloadconfig} bir SUNUCU komutudur ve
 * {@code ArcanumConfig.reload()} yalnız çağrıldığı JVM'in statik örneğini tazeler.
 * ADANMIŞ SUNUCUDA oyuncunun kendi mana barı ayarları oyun yeniden başlatılana kadar
 * bayat kalırdı; bu komut o boşluğu kapatır.
 *
 * <p>İZİN GEREKTİRMEZ (requires yok): salt istemci-yerel, salt görsel ayarlar. Gövde ortak
 * {@link ArcanumClient#reloadClientConfig()}.
 *
 * <p>Forge 64.1.3 (kaynak): {@code RegisterClientCommandsEvent} statik {@code BUS} (DEFAULT grup);
 * dispatcher {@code CommandDispatcher<CommandSourceStack>}; istemci kaynağı {@code ClientCommandSourceStack}
 * {@code sendSuccess}'i sohbete istemci sistem mesajı olarak ekler (Fabric {@code sendFeedback} ile aynı etki).
 */
public final class ArcanumClientConfigCommand {

    private ArcanumClientConfigCommand() {}

    /** {@code ArcanumForgeClient.init} içinden bir kez çağrılır. */
    public static void register() {
        RegisterClientCommandsEvent.BUS.addListener(event ->
                event.getDispatcher().register(Commands.literal("arcanumclient")
                        .then(Commands.literal("reloadconfig")
                                .executes(ctx -> {
                                    Component msg = ArcanumClient.reloadClientConfig();
                                    ctx.getSource().sendSuccess(() -> msg, false);
                                    return 1;
                                }))));
    }
}
