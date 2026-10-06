package com.arcanum.spell;

import com.arcanum.data.ArcanumPlayerData;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.registry.ModEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;

/**
 * Büyücü XP/leveling yardımcıları (stateless). State {@link ArcanumPlayerData}'da.
 *
 * <p>XP kaynakları (kullanıcı spec'i): büyü yapınca çok ufak (+1); öldürme — hostile taban,
 * pasif %50, Dementor daha çok, Ölümyiyen en çok mob, Basilisk en çok (boss).
 */
public final class ArcanumLeveling {
    private ArcanumLeveling() {}

    /** Bir yaratığı öldürmenin verdiği XP. */
    public static int xpForKill(LivingEntity e) {
        EntityType<?> t = e.getType();
        if (t == ModEntities.BASILISK.get()) return 150;
        if (t == ModEntities.DEATH_EATER.get()) return 25;
        if (t == ModEntities.DEMENTOR.get()) return 12;
        if (t == ModEntities.WEREWOLF.get() || t == ModEntities.ACROMANTULA.get()
                || t == ModEntities.TROLL.get() || t == ModEntities.GRINDYLOW.get()) {
            return 8;
        }
        MobCategory c = t.getCategory();
        if (c == MobCategory.MONSTER) return 4;
        return 2; // pasif — çağıran %50 şans uygular
    }

    /** XP ekle + level atlarsa ses/mesaj + client sync. */
    public static void addXp(ServerPlayer p, int amount) {
        ArcanumPlayerData data = ArcanumPlayerData.get(p.level().getServer());
        int newLevel = data.addXp(p, amount);
        if (newLevel > 0) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                    p.getSoundSource(), 1.0f, 1.0f);
            // Sağ-üstte cilalı toast bildirimi ("Büyücü Seviyesi X — Yetenek ağacını aç").
            ArcanumNetwork.sendLevelUp(p, newLevel);
        }
        ArcanumNetwork.syncMagicData(p);
    }

    /** WandLockManager.powerOf + Spells.power ile ortak skill güç bonusu (0..0.20). */
    public static float powerBonus(ServerPlayer p) {
        return (float) ArcanumPlayerData.get(p.level().getServer()).powerBonus(p);
    }
}
