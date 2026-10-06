package com.arcanum.item;

import com.arcanum.entity.ArcanewoodBoatEntity;
import com.arcanum.entity.ArcanewoodChestBoatEntity;
import com.arcanum.registry.ModEntities;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Arcanewood bot/sandıklı bot item'ı — vanilla BoatItem'ın Boat.Type'a bağımlı
 * olması nedeniyle (kapalı enum, yeni değer eklenemiyor) kendi entity
 * tiplerimizi doğrudan yerleştiren basit bir sürüm.
 */
public class ArcanewoodBoatItem extends Item {
    private final boolean hasChest;

    public ArcanewoodBoatItem(boolean hasChest, Properties properties) {
        super(properties);
        this.hasChest = hasChest;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }

        Vec3 pos = hit.getLocation();
        Boat boat = hasChest
                ? new ArcanewoodChestBoatEntity(ModEntities.ARCANEWOOD_CHEST_BOAT.get(), level)
                : new ArcanewoodBoatEntity(ModEntities.ARCANEWOOD_BOAT.get(), level);
        boat.setPos(pos.x, pos.y, pos.z);
        boat.setYRot(player.getYRot());

        if (!level.noCollision(boat, boat.getBoundingBox())) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            level.addFreshEntity(boat);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
