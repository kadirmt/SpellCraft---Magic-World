package com.arcanum.fabric;

import com.arcanum.spell.ImperiusCurseTracker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Imperio takibinin loader-taraflı tetikleyicisi. Asıl mantık (tracked-set +
 * tick gövdesi) {@link ImperiusCurseTracker} içinde, loader-bağımsız (common)
 * olarak yaşıyor — burada yalnızca Fabric event kayıtları var.
 */
public final class ImperiusCurseTicker {
    private ImperiusCurseTicker() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ImperiusCurseTracker::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ImperiusCurseTracker.clear());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ImperiusCurseTracker.clear());
    }
}
