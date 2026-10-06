package com.arcanum.client.beam;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix4fc;

/**
 * Tek karenin büyü-ışını geometrisi — 26.x "submit → feature render" ayrımına uyarlama.
 *
 * <p>1.21.1'de ışınlar {@code WorldRenderEvents.AFTER_ENTITIES} içinde doğrudan olayın tampon kaynağından
 * alınan SPELL_BEAM tüketicisine yazılıp {@code endBatch} ile hemen çiziliyordu (26.2'de o sınıf yok). 26.x'te
 * dünya-uzayı özel geometrinin TEK yolu {@code SubmitNodeCollector.submitCustomGeometry(PoseStack, RenderType,
 * CustomGeometryRenderer)}: geri çağrı ERTELENİR ve aynı karenin translucent feature geçişinde çalışır.
 * Bu yüzden tüm dünya/durum okuması (ışın listesi, kenetlenme görünümleri, oyuncu konumları, kırık yol
 * tohumları) SUBMIT anında yapılır ve sonuç burada değişmez "yayıcılar" olarak saklanır; {@link #render}
 * yalnız köşeleri döker — paylaşılan hiçbir duruma dokunmaz.
 *
 * <p>Ekleme sırası korunur (kökteki çizim sırası: serbest ışınlar → kenetlenme yarıları → düğüm billboard'ları
 * → kıvılcımlar). Additive + derinliğe-yazmayan tipte sıra zaten sonucu değiştirmez.
 */
public final class BeamBatch implements SubmitNodeCollector.CustomGeometryRenderer {

    /** Tek bir çizim komutu: verilen tüketiciye verilen matrisle köşe yazar. */
    @FunctionalInterface
    public interface Emitter {
        void emit(VertexConsumer vc, Matrix4fc mat);
    }

    private final List<Emitter> emitters = new ArrayList<>();

    /** Çizim komutu ekler — yakalanan tüm değerler submit anında HESAPLANMIŞ olmalı (canlı alan okuma yok). */
    public void add(Emitter emitter) {
        emitters.add(emitter);
    }

    public boolean isEmpty() {
        return emitters.isEmpty();
    }

    @Override
    public void render(PoseStack.Pose pose, VertexConsumer buffer) {
        Matrix4fc mat = pose.pose();
        for (Emitter e : emitters) {
            e.emit(buffer, mat);
        }
    }
}
