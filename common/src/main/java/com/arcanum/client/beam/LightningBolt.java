package com.arcanum.client.beam;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Yıldırım-tarzı büyü ışını geometrisi (CLIENT). Sprite/billboard DEĞİL:
 * her segment, yön vektöründen çıkarılan ortonormal bazla inşa edilen gerçek
 * 3D kare-prizma (4 yan yüz) — kameraya bakış yönünden bağımsız, her açıdan dolgun.
 *
 * <p>Yol üretimi momentum-korelasyonlu rastgele yürüyüş: komşu noktalar bağımsız
 * rastgele değil ({@code cum = cum*momentum + kick*(1-momentum)}) — gerçek yıldırımın
 * "pürüzlü ama sürekli" eğrisi. Uçlar {@code sin(pi*t)} zarfıyla SABİTLENİR (from/to
 * noktaları asla oynamaz — düello düğümü/asa ucu temasta kalır, gövde kıvranır).
 *
 * <p>Katmanlama (sahte bloom): aynı yol, 1 beyaza-yakın çekirdek + 2 büyüyen/solan
 * renkli kabuk olarak üst üste additive çizilir — gerçek post-process bloom yok.
 */
public final class LightningBolt {
    private LightningBolt() {}

    /** Yol noktaları: from→to arası {@code segs} parçalı, uçları sabit kırık hat. */
    public static Vec3[] path(Vec3 from, Vec3 to, int segs, double spread, long seed) {
        Vec3[] pts = new Vec3[segs + 1];
        pts[0] = from;
        pts[segs] = to;
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        if (len < 1.0e-5 || segs < 2) {
            for (int i = 1; i < segs; i++) {
                pts[i] = from.lerp(to, (double) i / segs);
            }
            return pts;
        }
        Vec3 dir = delta.scale(1.0 / len);
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(dir).normalize();

        RandomSource r = RandomSource.create(seed);
        final double momentum = 0.55;
        double cr = 0, cu = 0;
        for (int i = 1; i < segs; i++) {
            double kr = (r.nextDouble() * 2 - 1) * spread;
            double ku = (r.nextDouble() * 2 - 1) * spread;
            cr = cr * momentum + kr * (1 - momentum);
            cu = cu * momentum + ku * (1 - momentum);
            double env = Math.sin(Math.PI * i / segs); // uçlar 0 → sabit temas noktaları
            pts[i] = from.add(delta.scale((double) i / segs))
                    .add(right.scale(cr * env)).add(up.scale(cu * env));
        }
        return pts;
    }

    /**
     * Standart 3 katman: beyaza-yakın çekirdek + 2 renkli glow kabuğu.
     * {@code alpha} toplam soluklaştırma (iz sönümü / fade için).
     */
    public static void emitLayers(VertexConsumer vc, Matrix4f mat, Vec3 camPos, Vec3[] pts,
                                  float radius, int rgb, float alpha) {
        int core = blend(rgb, 0xFFFFFF, 0.65f);
        emit(vc, mat, camPos, pts, radius, core, alpha);
        emit(vc, mat, camPos, pts, radius * 2.2f, rgb, alpha * 0.42f);
        emit(vc, mat, camPos, pts, radius * 3.6f, rgb, alpha * 0.18f);
    }

    /** Tek katman: yol boyunca kare-prizma segmentleri (4 yan yüz / segment). */
    public static void emit(VertexConsumer vc, Matrix4f mat, Vec3 camPos, Vec3[] pts,
                            float radius, int rgb, float alpha) {
        int cr = (rgb >> 16) & 0xFF, cg = (rgb >> 8) & 0xFF, cb = rgb & 0xFF;
        int ca = (int) (Math.min(1f, alpha) * 255f);
        if (ca <= 2) {
            return;
        }
        for (int i = 0; i < pts.length - 1; i++) {
            Vec3 p0 = pts[i].subtract(camPos);
            Vec3 p1 = pts[i + 1].subtract(camPos);
            Vec3 d = p1.subtract(p0);
            if (d.lengthSqr() < 1.0e-8) {
                continue;
            }
            Vec3 dir = d.normalize();
            Vec3 right = dir.cross(new Vec3(0, 1, 0));
            right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
            Vec3 up = right.cross(dir).normalize();
            Vec3 rv = right.scale(radius);
            Vec3 uv = up.scale(radius);
            // 4 yan yüz (NO_CULL → sarım yönü önemsiz). Uç kapakları gereksiz (çakışan
            // segmentler additive blend'de zaten kaynaşır).
            quad(vc, mat, p0.add(rv).add(uv), p1.add(rv).add(uv), p1.add(rv).subtract(uv), p0.add(rv).subtract(uv), cr, cg, cb, ca);
            quad(vc, mat, p0.subtract(rv).add(uv), p1.subtract(rv).add(uv), p1.subtract(rv).subtract(uv), p0.subtract(rv).subtract(uv), cr, cg, cb, ca);
            quad(vc, mat, p0.add(rv).add(uv), p1.add(rv).add(uv), p1.subtract(rv).add(uv), p0.subtract(rv).add(uv), cr, cg, cb, ca);
            quad(vc, mat, p0.add(rv).subtract(uv), p1.add(rv).subtract(uv), p1.subtract(rv).subtract(uv), p0.subtract(rv).subtract(uv), cr, cg, cb, ca);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3,
                             int r, int g, int b, int a) {
        vertex(vc, mat, v0, r, g, b, a);
        vertex(vc, mat, v1, r, g, b, a);
        vertex(vc, mat, v2, r, g, b, a);
        vertex(vc, mat, v3, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, Vec3 p, int r, int g, int b, int a) {
        vc.addVertex(mat, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }

    public static int blend(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return (Math.round(r1 + (r2 - r1) * t) << 16)
                | (Math.round(g1 + (g2 - g1) * t) << 8)
                | Math.round(b1 + (b2 - b1) * t);
    }
}
