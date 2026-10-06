package com.arcanum.forge.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraftforge.network.SimpleChannel;

/** Yalnız fiziksel istemcide YÜKLENİR (ForgeNet#canSendToServer yalnız istemci kodundan çağrılır). */
final class ForgeClientNet {
    private ForgeClientNet() {}

    static boolean isRemotePresent(SimpleChannel channel) {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        return listener != null && channel.isRemotePresent(listener.getConnection());
    }
}
