package com.arcanum.client.beam;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Aktif büyü ışınlarının CLIENT deposu. Sunucu her kastta {@code SpellBeamPayload}
 * gönderir; ışın burada {@code lifeTicks} boyunca yaşar (varsayılan 10 tick = 0.5 sn
 * "havada iz"), yaşlandıkça solar ve her 2 tick'te yeni tohumla YENİDEN KIRILIR
 * (yıldırım titremesi). Render {@code SpellLockRenderer}'ın world-render kancasından
 * çağrılır — ağa partikül seli gitmez, tüm görsel yerelde üretilir.
 */
public final class ClientSpellBeams {
    private ClientSpellBeams() {}

    private static final List<Beam> BEAMS = new ArrayList<>();
    private static long nextId = 1;

    private static final class Beam {
        final long id;
        final Vec3 from;
        final Vec3 to;
        final int color;
        final int life;
        final float width;
        final float spread;
        int age;

        Beam(long id, Vec3 from, Vec3 to, int color, int life, float width, float spread) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.color = color;
            this.life = Math.max(1, life);
            this.width = width;
            this.spread = spread;
        }
    }

    public static void add(Vec3 from, Vec3 to, int color, int life, float width, float spread) {
        synchronized (BEAMS) {
            BEAMS.add(new Beam(nextId++, from, to, color, life, width, spread));
        }
    }

    /** Her client tick'inde çağrılır — yaşlanma + süresi dolanları at. */
    public static void tick() {
        synchronized (BEAMS) {
            Iterator<Beam> it = BEAMS.iterator();
            while (it.hasNext()) {
                Beam b = it.next();
                if (++b.age >= b.life) {
                    it.remove();
                }
            }
        }
    }

    public static void clear() {
        synchronized (BEAMS) {
            BEAMS.clear();
        }
    }

    public static boolean isEmpty() {
        synchronized (BEAMS) {
            return BEAMS.isEmpty();
        }
    }

    /** World-render'dan çağrılır (SPELL_BEAM buffer'ı zaten bağlanmış olarak). */
    public static void render(VertexConsumer vc, Matrix4f mat, Vec3 camPos, float partialTick, long gameTime) {
        synchronized (BEAMS) {
            for (Beam b : BEAMS) {
                float fade = 1f - (b.age + partialTick) / b.life;
                if (fade <= 0f) {
                    continue;
                }
                double dist = b.from.distanceTo(b.to);
                int segs = (int) Math.min(16, Math.max(4, dist / 1.4));
                // spread<=0 → mesafeye göre otomatik (uzun ışın daha geniş sapar)
                double spread = b.spread > 0 ? b.spread : Math.min(0.5, 0.10 + dist * 0.028);
                // Her 2 tick'te yeni tohum → şekil değişir ("yıldırım oynar")
                long seed = (b.id * 0x9E3779B97F4A7C15L) ^ ((gameTime >> 1) * 0xC2B2AE3D27D4EB4FL);
                Vec3[] pts = LightningBolt.path(b.from, b.to, segs, spread, seed);
                LightningBolt.emitLayers(vc, mat, camPos, pts, b.width, b.color, fade);
            }
        }
    }
}
