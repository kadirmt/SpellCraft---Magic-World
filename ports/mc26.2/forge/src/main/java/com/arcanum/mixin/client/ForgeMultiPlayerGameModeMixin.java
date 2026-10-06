package com.arcanum.mixin.client;

import com.arcanum.ArcanumEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * YALNIZ FORGE — Imperio / Umbravolo sol-tık engelinin İSTEMCİ tarafı paritesi.
 *
 * <p>Kök 1.21.1 (ve 26.x) Fabric'te {@code AttackBlockCallback} / {@code AttackEntityCallback} fabric-api'nin
 * {@code MultiPlayerGameModeMixin}'i ile istemcide de çalışır; FAIL dönünce [javap fabric-events-interaction-v0 5.2.8]:
 * <ul>
 *   <li>{@code startDestroyBlock}: ilk {@code LocalPlayer#getAbilities()} çağrısında (kısıtlama + dünya sınırı
 *       kontrollerinden SONRA) {@code false} döner — paket YOK, tahmin YOK, kırma ilerlemesi YOK;</li>
 *   <li>{@code continueDestroyBlock}: aynı noktada, yalnız {@code instabuild} iken (yaratıcı tekrar-kırma) {@code false};
 *       hayatta kalmada zaten {@code startDestroyBlock}'a düşer;</li>
 *   <li>{@code attack}: ilk {@code ClientPacketListener#send} öncesinde iptal — saldırı paketi, istemci
 *       {@code Player#attack} ve {@code resetAttackStrengthTicker} YOK.</li>
 * </ul>
 * Forge 64'te ise {@code PlayerInteractEvent.LeftClickBlock} iptali [64.1.3 yaması, MultiPlayerGameMode.java.patch]
 * yalnız yerel kırmayı/ilerlemeyi atlar: {@code startDestroyBlock}/{@code continueDestroyBlock} yine
 * {@code START_DESTROY_BLOCK} paketini yollar ve {@code true} döner → {@code Minecraft#continueAttack} her tick
 * {@code addBreakingBlockEffect} + kol sallama yapar (kısa çatlak/titreme). {@code AttackEntityEvent} ise
 * istemcide paket GÖNDERİLDİKTEN sonra {@code Player#attack} içinde iptal edilir ve saldırı gücü sayacı yine sıfırlanır.
 * Bu mixin Fabric'in enjeksiyon noktalarını ve FAIL anlamını birebir kurar. Sunucu tarafındaki Forge dinleyicileri
 * ({@code ArcanumForgeEvents}) DEĞİŞMEDİ — asıl otorite yine sunucu.
 *
 * <p>Karar mantığı ortak {@link ArcanumEvents} yüklemleri (Fabric kayıtlarıyla aynı çağrılar); lanetsiz oyuncuda
 * hiçbir şey değişmez. Yalnız {@code forge/.../arcanum.mixins.json} "client" listesinde (Fabric'te fabric-api zaten yapıyor).
 *
 * <p>26.2 (Forge 65.1.3): Forge-yamalı {@code MultiPlayerGameMode#startDestroyBlock/continueDestroyBlock/attack} bayt kodu
 * 64.1.3 ile BİREBİR aynı (javap -c, sabit-havuzu indeksleri hariç) → üç enjeksiyon noktası ve ordinal'lar geçerli;
 * fabric-events-interaction-v0 26.2'de de 5.2.8.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class ForgeMultiPlayerGameModeMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "startDestroyBlock", cancellable = true,
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/player/LocalPlayer;getAbilities()Lnet/minecraft/world/entity/player/Abilities;"))
    private void arcanum$blockStartDestroy(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (ArcanumEvents.shouldBlockAttackBlock(this.minecraft.player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueDestroyBlock", cancellable = true,
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/player/LocalPlayer;getAbilities()Lnet/minecraft/world/entity/player/Abilities;"))
    private void arcanum$blockContinueDestroy(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.player.getAbilities().instabuild && ArcanumEvents.shouldBlockAttackBlock(this.minecraft.player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "attack", cancellable = true,
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void arcanum$blockAttackEntity(Player player, Entity target, CallbackInfo ci) {
        if (ArcanumEvents.shouldBlockAttackEntity(player, player.level(), target)) {
            ci.cancel();
        }
    }
}
