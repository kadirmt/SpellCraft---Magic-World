package com.arcanum.forge.client.render;

import com.arcanum.client.beam.ArcanumRenderTypes;
import com.arcanum.client.beam.ClientSpellBeams;
import com.arcanum.client.beam.LightningBolt;
import com.arcanum.forge.client.state.ClientLockState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Büyü dünyası VFX render'ı (kök fabric: {@code WorldRenderEvents.AFTER_ENTITIES}):
 * <ul>
 *   <li><b>Serbest büyü ışınları</b> ({@link ClientSpellBeams}): her kast, yıldırım-tarzı
 *       kırık additive prizma katmanları olarak çizilir, ~0.5 sn solarak iz bırakır.</li>
 *   <li><b>Asa kenetlenmesi</b> (Priori Incantatem): iki asa ucundan çarpışma düğümüne
 *       YILDIRIM ışınları — uçlar (asa ucu + düğüm) SABİT temas noktalarıdır, gövde her
 *       2 tick'te yeni tohumla yeniden kırılır ("yıldırım oynar, şekil değiştirir").
 *       Düğümde iki büyünün renginde çarpışma: parlak altın-beyaz çekirdek billboard'ları
 *       + her tick yön değiştiren renkli kısa kıvılcım dalları.</li>
 * </ul>
 * Hepsi custom additive RenderType ({@link ArcanumRenderTypes#SPELL_BEAM}) ile: texture
 * yok, lightmap yok (fullbright), depth-yazma yok — gece/gündüz aynı parlaklıkta "katı ışık".
 *
 * <p><b>Forge portu notları (kök: fabric {@code WorldRenderEvents.AFTER_ENTITIES}):</b>
 * <ul>
 *   <li>Kayıt: {@link RenderLevelStageEvent} {@code Stage.AFTER_ENTITIES} (FORGE bus,
 *       {@code @Mod.EventBusSubscriber} ile otomatik) — Forge'un LevelRenderer patch'i
 *       bu aşamayı vanilla entity endBatch'lerinin hemen ardından tetikler; Fabric'in
 *       AFTER_ENTITIES noktasıyla birebir aynı yer.</li>
 *   <li>Matris: Fabric {@code ctx.matrixStack()} bu noktada BOŞ PoseStack'ti (üst =
 *       identity; kamera dönüşü LevelRenderer başında RenderSystem model-view yığınına
 *       basılır — javap ile doğrulandı: renderLevel {@code getModelViewStack().pushMatrix()
 *       .mul(frustumMatrix)}). Forge {@code event.getPoseStack()} ise frustumMatrix'in
 *       KENDİSİNİ verir — onu kullanmak dönüşü İKİ KEZ uygulardı. Bu yüzden burada
 *       Fabric'le birebir aynı şekilde identity {@link Matrix4f} + kamera-göreli
 *       koordinat kullanılır ({@link LightningBolt} vertexlerden {@code camPos} çıkarır).</li>
 *   <li>partialTick: Forge {@code event.getPartialTick()} bytecode'da
 *       {@code DeltaTracker.getRealtimeDeltaTicks()} — kökteki
 *       {@code getGameTimeDeltaPartialTick(false)} DEĞİL. Davranış eşitliği için
 *       {@code mc.getTimer().getGameTimeDeltaPartialTick(false)} doğrudan çağrılır.</li>
 *   <li>Buffer: Fabric {@code ctx.consumers()} = LevelRenderer'ın ana bufferSource'u →
 *       Forge'da {@code mc.renderBuffers().bufferSource()} aynı nesne; elle
 *       {@code endBatch(ArcanumRenderTypes.SPELL_BEAM)} korunur.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "arcanum", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SpellLockRenderer {
    private static final int GOLD = 0xFFE9B0;

    private SpellLockRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        boolean lockActive = ClientLockState.anyActive();
        if (!lockActive && ClientSpellBeams.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        // Kök davranış: kamera dönüşü global model-view'da; vertexler kamera-göreli,
        // matris identity (bkz. sınıf javadoc'u — event.getPoseStack() BİLEREK kullanılmıyor).
        Matrix4f mat = new Matrix4f();
        MultiBufferSource.BufferSource consumers = mc.renderBuffers().bufferSource();
        Camera cam = event.getCamera();
        Vec3 camPos = cam.getPosition();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        long gameTime = level.getGameTime();
        float time = (gameTime % 100000L) + partial;
        VertexConsumer vc = consumers.getBuffer(ArcanumRenderTypes.SPELL_BEAM);

        // ---- serbest büyü ışınları (yıldırım izleri) ----
        ClientSpellBeams.render(vc, mat, camPos, partial, gameTime);

        // ---- asa kenetlenmesi ----
        if (lockActive) {
            Vector3f leftV = cam.getLeftVector();
            Vector3f upV = cam.getUpVector();
            Vec3 left = new Vec3(leftV.x(), leftV.y(), leftV.z());
            Vec3 up = new Vec3(upV.x(), upV.y(), upV.z());

            for (ClientLockState.View v : ClientLockState.views()) {
                Player pa = level.getPlayerByUUID(v.a);
                Player pb = level.getPlayerByUUID(v.b);
                if (pa == null || pb == null) {
                    continue;
                }
                Vec3 tipA = wandTip(pa, partial);
                Vec3 tipB = wandTip(pb, partial);
                float node = v.displayNode;
                Vec3 nodePos = tipA.lerp(tipB, node);
                float edge = Math.min(1f, Math.abs(node - 0.5f) * 2f);

                // Her yarı: uçları SABİT (asa ucu ↔ düğüm), gövdesi her 2 tick'te yeniden
                // kırılan yıldırım. Tohum taraf+kenetlenme kimliğine bağlı → iki taraf
                // farklı ama her istemcide deterministik aynı şekil.
                long half = gameTime >> 1;
                long seedA = (v.a.getMostSignificantBits() * 0x9E3779B97F4A7C15L) ^ (half * 0xC2B2AE3D27D4EB4FL);
                long seedB = (v.b.getMostSignificantBits() * 0x9E3779B97F4A7C15L) ^ (half * 0xC2B2AE3D27D4EB4FL) ^ 0x5DEECE66DL;
                double spread = 0.22 + edge * 0.10; // düello kızıştıkça daha vahşi kıvranır
                int segsA = segsFor(tipA.distanceTo(nodePos));
                int segsB = segsFor(tipB.distanceTo(nodePos));
                LightningBolt.emitLayers(vc, mat, camPos,
                        LightningBolt.path(tipA, nodePos, segsA, spread, seedA), 0.05f, v.colorA, 1f);
                LightningBolt.emitLayers(vc, mat, camPos,
                        LightningBolt.path(tipB, nodePos, segsB, spread, seedB), 0.05f, v.colorB, 1f);

                // Çarpışma düğümü: iki rengin buluştuğu parlak kaynak. Billboard çekirdek
                // (altın-beyaz) + HER TICK yön değiştiren, dışa fışkıran renkli kıvılcım dalları.
                float pulse = (float) (Math.sin(time * (edge > 0.5f ? 0.9 : 0.5)) * 0.5 + 0.5);
                float baseSize = 0.30f + edge * 0.10f;
                int haloColor = LightningBolt.blend(v.colorA, v.colorB, node);
                Vec3 nodeCam = nodePos.subtract(camPos);
                billboard(vc, mat, nodeCam, left, up, (baseSize + pulse * 0.06f) * 1.8f, haloColor, 0.30f);
                billboard(vc, mat, nodeCam, left, up, baseSize + pulse * 0.05f, GOLD, 0.85f);
                billboard(vc, mat, nodeCam, left, up, (baseSize + pulse * 0.05f) * 0.45f, 0xFFFFFF, 1.0f);
                clashSparks(vc, mat, camPos, nodePos, v.colorA, v.colorB, gameTime, edge);
            }
        }

        consumers.endBatch(ArcanumRenderTypes.SPELL_BEAM);
    }

    /** Mesafeye göre segment sayısı (kısa yarıda az, uzun yarıda çok kırık). */
    private static int segsFor(double dist) {
        return (int) Math.min(14, Math.max(4, dist / 0.9));
    }

    /**
     * Çarpışma noktasından dışa fışkıran kısa yıldırım dalları — her tick yeni tohum,
     * yön ve uzunluk (LightningSparks tekniği: origin→target değil, düğüm-lokal rastgele).
     * Renkler iki büyü arasında dönüşümlü → çarpışma "iki gücün boğuşması" gibi okunur.
     */
    private static void clashSparks(VertexConsumer vc, Matrix4f mat, Vec3 camPos, Vec3 node,
                                    int colorA, int colorB, long gameTime, float edge) {
        RandomSource r = RandomSource.create(gameTime * 0x9E3779B97F4A7C15L ^ 0xA5A5A5A5L);
        int count = 5 + (int) (edge * 3); // kızışınca daha çok kıvılcım
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(r.nextDouble() * 2 - 1, r.nextDouble() * 2 - 1, r.nextDouble() * 2 - 1);
            if (dir.lengthSqr() < 1.0e-4) {
                continue;
            }
            dir = dir.normalize();
            double len = 0.35 + r.nextDouble() * 0.55;
            Vec3 end = node.add(dir.scale(len));
            int color = (i & 1) == 0 ? colorA : colorB;
            Vec3[] pts = LightningBolt.path(node, end, 4, 0.10, r.nextLong());
            LightningBolt.emitLayers(vc, mat, camPos, pts, 0.022f, color, 0.8f);
        }
    }

    /** Kameraya dönük tek quad (düğüm çekirdeği için yeterli ve ucuz; c = kamera-göreli). */
    private static void billboard(VertexConsumer vc, Matrix4f mat, Vec3 c, Vec3 left, Vec3 up,
                                  float size, int rgb, float alpha) {
        Vec3 rx = left.scale(size);
        Vec3 uy = up.scale(size);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int a = (int) (alpha * 255f);
        vertex(vc, mat, c.subtract(rx).subtract(uy), r, g, b, a);
        vertex(vc, mat, c.add(rx).subtract(uy), r, g, b, a);
        vertex(vc, mat, c.add(rx).add(uy), r, g, b, a);
        vertex(vc, mat, c.subtract(rx).add(uy), r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, Vec3 p, int r, int g, int b, int a) {
        vc.addVertex(mat, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }

    /** {@link com.arcanum.spell.SpellFx#wandTip} client kopyası — partialTick enterpolasyonlu. */
    private static Vec3 wandTip(Player p, float partial) {
        Vec3 look = p.getViewVector(partial);
        Vec3 eye = p.getEyePosition(partial);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
        return eye.add(look.scale(0.9)).add(right.scale(0.25)).add(0, -0.25, 0);
    }
}
