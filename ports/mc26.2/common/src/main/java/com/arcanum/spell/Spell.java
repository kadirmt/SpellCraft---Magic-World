package com.arcanum.spell;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Bir büyünün tanımı: kimlik, görünen ad, cooldown (tick), mana maliyeti,
 * tema rengi (RGB), kademe ve etki. Etki yalnızca sunucu tarafında çalışır;
 * kast eden asa stack'i güç çarpanı ve bileşen erişimi için iletilir
 * (mainhand/offhand karışıklığına karşı — hangi asa kast ettiyse o).
 */
public record Spell(String id, Component name, int cooldown, int manaCost, int color, String tier, Spell.Effect effect) {

    @FunctionalInterface
    public interface Effect {
        void cast(ServerLevel level, Player player, ItemStack wand);
    }
}
