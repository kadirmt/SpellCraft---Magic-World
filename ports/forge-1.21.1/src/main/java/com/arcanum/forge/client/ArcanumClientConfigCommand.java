package com.arcanum.forge.client;

import com.arcanum.config.ArcanumConfig;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
@Mod.EventBusSubscriber(modid = "arcanum", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ArcanumClientConfigCommand {

    private ArcanumClientConfigCommand() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arcanumclient")
                .then(Commands.literal("reloadconfig")
                        .executes(ctx -> {
                            ArcanumConfig.reload();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[Arcanum] İstemci config yeniden okundu "
                                            + "(mana barı ölçek/saydamlık/asa şartı)."), false);
                            return 1;
                        })));
    }
}
