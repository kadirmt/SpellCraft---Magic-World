package com.arcanum.server;

import java.util.List;

import com.arcanum.config.ArcanumConfig;
import com.arcanum.data.ArcanumPlayerData;
import com.arcanum.entity.ArcanumSurfaceSpawner;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.registry.ModEntities;
import com.arcanum.spell.ArcanumLeveling;
import com.arcanum.spell.ModSpells;
import com.arcanum.spell.Spell;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /arcanum} komut ağacı — kök {@code ArcanumFabric} {@code CommandRegistrationCallback} gövdesi.
 * Loader bağlama: Fabric {@code CommandRegistrationCallback.EVENT.register(ArcanumCommands::register)};
 * Forge {@code RegisterCommandsEvent} → {@code register(getDispatcher(), getBuildContext(), getCommandSelection())}.
 *
 * <p>Hile komutu: yalnızca cheats/OP seviye 2 açıkken kullanılabilir
 * (kök {@code src.hasPermission(2)} → 26.x {@code Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)}).
 */
public final class ArcanumCommands {
    private ArcanumCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext buildContext,
                                Commands.CommandSelection selection) {
        // '/arcanum spells unlock' → oyuncuya TÜM büyüleri öğretir (test/creative kolaylığı;
        // dev istemcisi her açılışta rastgele kullanıcı adı verdiği için öğrenilen büyüler
        // "kaybolmuş" görünüyor — bu komutla tek seferde hepsi geri açılır).
        // '/arcanum spells lock' → oyuncunun tüm büyülerini sıfırlar (ilerlemeyi baştan test için).
        dispatcher.register(Commands.literal("arcanum")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("spells")
                        .then(Commands.literal("unlock").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ArcanumPlayerData data = ArcanumPlayerData.get(ctx.getSource().getServer());
                            int added = 0;
                            for (Spell s : ModSpells.SPELLS) {
                                if (data.learn(p, s.id())) {
                                    added++;
                                }
                            }
                            ArcanumNetwork.syncKnownSpells(p);
                            final int n = added;
                            ctx.getSource().sendSuccess(
                                    () -> Component.translatable("arcanum.command.unlock_all", n), true);
                            return ModSpells.SPELLS.size();
                        }))
                        .then(Commands.literal("lock").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ArcanumPlayerData.get(ctx.getSource().getServer()).forgetAll(p);
                            ArcanumNetwork.syncKnownSpells(p);
                            ctx.getSource().sendSuccess(
                                    () -> Component.translatable("arcanum.command.lock_all"), true);
                            return 1;
                        })))
                // '/arcanum spawntest' → kaynak konumunun etrafına Arcanum biyom
                // yaratıklarından 2'şer tane doğurur (doğal spawn oranını beklemeden
                // görsel doğrulama). Oyuncu GEREKTİRMEZ (getPosition/getLevel) — konsoldan
                // ve RCON'dan da çalışır.
                .then(Commands.literal("spawntest").executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerLevel level = src.getLevel();
                    Vec3 center = src.getPosition();
                    List<EntityType<?>> types = List.<EntityType<?>>of(
                            ModEntities.MOONCALF.get(), ModEntities.BOWTRUCKLE.get(),
                            ModEntities.THESTRAL.get(), ModEntities.KNEAZLE.get(),
                            ModEntities.DEATH_EATER.get(), ModEntities.DEMENTOR.get(),
                            ModEntities.WEREWOLF.get(), ModEntities.ACROMANTULA.get());
                    int spawned = 0;
                    int idx = 0;
                    for (EntityType<?> type : types) {
                        for (int k = 0; k < 2; k++) {
                            double ang = (Math.PI * 2.0 * idx) / (types.size() * 2);
                            BlockPos pos = BlockPos.containing(
                                    center.x + Math.cos(ang) * 2.5, center.y, center.z + Math.sin(ang) * 2.5);
                            if (type.spawn(level, pos, EntitySpawnReason.COMMAND) != null) {
                                spawned++;
                            }
                            idx++;
                        }
                    }
                    final int n = spawned;
                    src.sendSuccess(() -> Component.translatable("arcanum.command.spawntest", n), true);
                    return spawned;
                }))
                // '/arcanum maxxp' → büyücü seviyesini direkt config tavanına yapar (test için).
                .then(Commands.literal("maxxp").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    // Tam tavana yetecek XP'yi hesapla (config tavanı yükseltilmiş
                    // olabilir — sabit 1M yetmeyebilir); addXp ses+mesaj+client sync yapar.
                    int maxLvl = ArcanumPlayerData.maxLevel();
                    ArcanumPlayerData d = ArcanumPlayerData.get(ctx.getSource().getServer());
                    int need = -d.getXp(p);
                    for (int l = d.getLevel(p); l < maxLvl; l++) {
                        need += ArcanumPlayerData.xpToNext(l);
                    }
                    if (need > 0) {
                        ArcanumLeveling.addXp(p, need);
                    }
                    ctx.getSource().sendSuccess(
                            () -> Component.translatable("arcanum.command.maxxp", maxLvl), true);
                    return maxLvl;
                }))
                // '/arcanum spawncheck' → bulunulan yerde hangi Arcanum yaratıklarının
                // garanti spawnlanabileceğini raporlar + hemen çevrene bir grup doğurmayı
                // dener (yüzey spawner'ını anında test/kanıt için).
                .then(Commands.literal("spawncheck").executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerLevel lvl = src.getLevel();
                    Vec3 pos = src.getPosition();
                    BlockPos bp = BlockPos.containing(pos);
                    for (String line : ArcanumSurfaceSpawner.eligibilityReport(lvl, bp)) {
                        src.sendSuccess(() -> Component.literal(line), false);
                    }
                    int spawned = ArcanumSurfaceSpawner.forceBurst(lvl, pos.x, pos.z, 16);
                    src.sendSuccess(
                            () -> Component.literal("-> hemen dogan: " + spawned + " (16 deneme)"), false);
                    return spawned;
                }))
                // '/arcanum reloadconfig' → config/arcanum.json'u oyunu kapatmadan yeniden okur.
                .then(Commands.literal("reloadconfig").executes(ctx -> {
                    ArcanumConfig.reload();
                    // Çevrimiçi oyunculara anında sync: G ekranı "Level X/Y" ve kalan
                    // puan gösterimi bir sonraki magic-data olayını beklemesin.
                    for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                        ArcanumNetwork.syncMagicData(p);
                    }
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("Arcanum config yeniden yüklendi (config/arcanum.json)."), true);
                    return 1;
                })));
    }
}
