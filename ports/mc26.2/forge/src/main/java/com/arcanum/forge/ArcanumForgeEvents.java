package com.arcanum.forge;

import com.arcanum.ArcanumEvents;
import com.arcanum.server.ArcanumCommands;
import com.arcanum.server.ArcanumLoot;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.brewing.BrewingRecipeRegisterEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.listener.Priority;

/**
 * Forge oyun-içi olay bağlamaları (EventBus 7, hepsi statik {@code XEvent.BUS} = BusGroup.DEFAULT —
 * hiçbiri {@code IModBusEvent} değil). Gövdeler ortak {@link ArcanumEvents} / {@code com.arcanum.server.*}
 * içinde; burada yalnız Forge olayı → ortak köprü çevirisi var (kök ArcanumFabric kayıtlarının karşılığı).
 *
 * <p>Dinleyiciler METOT REFERANSIYLA eklenir (CancellableEventBus'ta ifade-lambda'sı Consumer/Predicate
 * arasında belirsiz kalabilir — res-forge §B.2). {@code boolean} dönen dinleyici = {@code true} ise iptal.
 */
public final class ArcanumForgeEvents {
    private ArcanumForgeEvents() {}

    /** Mod ctor'unda çağrılır. */
    public static void register() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(ArcanumForgeEvents::onServerTick);
        ServerStartedEvent.BUS.addListener(ArcanumForgeEvents::onServerStarted);
        ServerStoppingEvent.BUS.addListener(ArcanumForgeEvents::onServerStopping);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(ArcanumForgeEvents::onPlayerLoggedIn);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(ArcanumForgeEvents::onPlayerLoggedOut);
        // LOWEST + void dinleyici: Fabric AFTER_DEATH ölüm KESİNLEŞTİKTEN sonra çalışır; LivingDeathEvent
        // iptal edilebilir → en sona kayıt olup iptal edilmiş olayı hiç görmemek (EB7'de iptal yayılımı durdurur,
        // MONITOR dışındaki dinleyiciler iptal edilen olayı almaz) aynı semantiği verir.
        LivingDeathEvent.BUS.addListener(Priority.LOWEST, ArcanumForgeEvents::onLivingDeath);
        // Imperio + Umbravolo girdi kesme (kökteki ImperiusInputBlocker'ın 5 callback'i)
        AttackEntityEvent.BUS.addListener(ArcanumForgeEvents::onAttackEntity);
        PlayerInteractEvent.LeftClickBlock.BUS.addListener(ArcanumForgeEvents::onLeftClickBlock);
        PlayerInteractEvent.RightClickItem.BUS.addListener(ArcanumForgeEvents::onRightClickItem);
        PlayerInteractEvent.RightClickBlock.BUS.addListener(ArcanumForgeEvents::onRightClickBlock);
        BlockEvent.BreakEvent.BUS.addListener(ArcanumForgeEvents::onBlockBreak);
        // Loot / iksir / komut
        LootTableLoadEvent.BUS.addListener(ArcanumForgeEvents::onLootTableLoad);
        BrewingRecipeRegisterEvent.BUS.addListener(ArcanumForgeEvents::onBrewingRegister);
        RegisterCommandsEvent.BUS.addListener(ArcanumForgeEvents::onRegisterCommands);
    }

    // ---- sunucu tick / yaşam döngüsü / bağlantı ----

    /** Kök END_SERVER_TICK sırası tek dinleyicide ({@link ArcanumEvents#onServerTickEnd}). */
    private static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        ArcanumEvents.onServerTickEnd(event.server());
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ArcanumEvents.onServerStarted(event.getServer());
    }

    /** ServerStoppingEvent son saveAll'dan ÖNCE post edilir (Umbravolo exitAll şartı). */
    private static void onServerStopping(ServerStoppingEvent event) {
        ArcanumEvents.onServerStopping(event.getServer());
    }

    /** Sunucu ana thread'inde (PlayerList#placeNewPlayer sonu) post edilir. */
    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ArcanumEvents.onPlayerJoin(player);
        }
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ArcanumEvents.onPlayerLeave(player);
        }
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        ArcanumEvents.onLivingDeath(event.getEntity(), event.getSource());
    }

    // ---- Imperio / Umbravolo girdi kesme ----

    /** Sol tık — varlığa saldırı. true = iptal. */
    private static boolean onAttackEntity(AttackEntityEvent event) {
        return ArcanumEvents.shouldBlockAttackEntity(event.getEntity(), event.getEntity().level(), event.getTarget());
    }

    /** Sol tık — blok kırmayı başlatma. */
    private static boolean onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        return ArcanumEvents.shouldBlockAttackBlock(event.getEntity());
    }

    /** Sağ tık — item kullan (kök UseItemCallback FAIL → iptal + FAIL sonucu). */
    private static boolean onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (ArcanumEvents.shouldBlockUseItem(event.getEntity(), event.getHand())) {
            event.setCancellationResult(InteractionResult.FAIL);
            return true;
        }
        return false;
    }

    /** Sağ tık — bloğa etkileşim (kök UseBlockCallback FAIL → iptal + FAIL sonucu). */
    private static boolean onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (ArcanumEvents.shouldBlockUseBlock(event.getEntity(), event.getHand())) {
            event.setCancellationResult(InteractionResult.FAIL);
            return true;
        }
        return false;
    }

    /**
     * Blok kırmayı tamamen engelle (kök PlayerBlockBreakEvents.BEFORE → false).
     * Forge 26.x: {@code ForgeHooks.onBlockBreakEvent} yalnız {@code getResult().isDenied()}'a bakar →
     * iptal TEK BAŞINA kırmayı engellemez; {@code Result.DENY} ŞART (ARCHITECTURE §5).
     */
    private static boolean onBlockBreak(BlockEvent.BreakEvent event) {
        if (ArcanumEvents.shouldBlockBlockBreak(event.getPlayer())) {
            event.setResult(Result.DENY);
            return true;
        }
        return false;
    }

    // ---- loot / iksir / komut ----

    /**
     * Kök Fabric {@code LootTableEvents.MODIFY}. Bilinçli fark: Fabric'in {@code source.isBuiltin()} filtresinin
     * Forge'da karşılığı yok — datapack override'ları da olaydan geçer (forge-1.21.1 portundaki kabul edilmiş sapma).
     */
    private static void onLootTableLoad(LootTableLoadEvent event) {
        LootTable table = event.getTable();
        if (table == null) return;
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, event.getName());
        ArcanumLoot.modify(key, pool -> table.addPool(pool.build()));
    }

    private static void onBrewingRegister(BrewingRecipeRegisterEvent event) {
        ArcanumEvents.onRegisterBrewing(event.getBuilder());
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        ArcanumCommands.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection());
    }
}
