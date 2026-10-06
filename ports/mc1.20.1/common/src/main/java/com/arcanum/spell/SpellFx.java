package com.arcanum.spell;

import com.arcanum.registry.ModParticles;
import com.arcanum.util.ArcanumColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Büyü görsel efekt kütüphanesi — tüm choreography burada.
 * Her yardımcı sunucu tarafında çalışır ve partikülleri yakın oyunculara yayınlar.
 * Renkler ARGB değil RGB (0xRRGGBB) alınır; alpha içeride eklenir.
 */
public final class SpellFx {
    private SpellFx() {}

    // ===================== renk & option =====================

    public static ArcanumColorParticleOption glow(int rgb) {
        return ArcanumColorParticleOption.create(ModParticles.SPELL_GLOW.get(), 0xFF000000 | rgb);
    }

    public static ArcanumColorParticleOption trail(int rgb) {
        return ArcanumColorParticleOption.create(ModParticles.SPELL_TRAIL.get(), 0xFF000000 | rgb);
    }

    /** Kalıcı ışın parçası — hat ~2 sn asılı kalır (Avada'nın yeşil izi). */
    public static ArcanumColorParticleOption beamOpt(int rgb) {
        return ArcanumColorParticleOption.create(ModParticles.SPELL_BEAM.get(), 0xFF000000 | rgb);
    }

    public static ArcanumColorParticleOption rune(int rgb) {
        return ArcanumColorParticleOption.create(ModParticles.MAGIC_RUNE.get(), 0xFF000000 | rgb);
    }

    /** Rengi beyaza doğru açar (0..1). Çekirdek/parlama iki-ton efektleri için. */
    public static int lighten(int rgb, float f) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r += (int) ((255 - r) * f);
        g += (int) ((255 - g) * f);
        b += (int) ((255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }

    /** Rengi siyaha doğru koyulaştırır (0..1). */
    public static int darken(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xFF) * (1 - f));
        int g = (int) (((rgb >> 8) & 0xFF) * (1 - f));
        int b = (int) ((rgb & 0xFF) * (1 - f));
        return (r << 16) | (g << 8) | b;
    }

    // ===================== konum =====================

    /** Asa ucunun yaklaşık dünya konumu (birinci/üçüncü şahıs ortalaması). */
    public static Vec3 wandTip(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        return player.getEyePosition()
                .add(look.scale(0.9))
                .add(right.scale(0.25))
                .add(0, -0.25, 0);
    }

    // ===================== temel şekiller =====================

    /** Kast anı: asa ucunda parlama + kıvılcım. */
    public static void muzzle(ServerLevel level, Player player, int color) {
        Vec3 tip = wandTip(player);
        level.sendParticles(glow(lighten(color, 0.35f)), tip.x, tip.y, tip.z, 6, 0.06, 0.06, 0.06, 0.02);
        level.sendParticles(ModParticles.SPELL_SPARK.get(), tip.x, tip.y, tip.z, 4, 0.05, 0.05, 0.05, 0.05);
        // Her büyüde kaster etrafında büyü renginde dönen rün çemberi.
        runeRing(level, player.position(), color, 9, 1.2);
    }

    /**
     * Işın: asa ucundan çarpma noktasına — YILDIRIM görünümü. Görsel tamamen istemcide
     * çizilir ({@code SpellBeamPayload} → ClientSpellBeams/LightningBolt: additive 3D
     * prizma katmanları, momentum-korelasyonlu kırık yol, ~0.5 sn solarak havada iz).
     * Eski adım-başına partikül seli kalktı; kast başına TEK paket + çarpmada ufak glow.
     */
    public static void beam(ServerLevel level, Player player, Vec3 impact, int color) {
        Vec3 tip = wandTip(player);
        if (tip.distanceToSqr(impact) < 1.0e-4) {
            return;
        }
        com.arcanum.network.ArcanumNetwork.sendBeam(level, tip, impact, color, 10, 0.05f, 0f);
        level.sendParticles(glow(color), impact.x, impact.y, impact.z, 5, 0.07, 0.07, 0.07, 0.01);
    }

    /**
     * Titrek/vahşi ışın (Crucio) — aynı yıldırım motoru, daha geniş kırılma genliği
     * ve biraz daha kalın çekirdekle (lanet "kaba" görünsün).
     */
    public static void jitterBeam(ServerLevel level, Player player, Vec3 impact, int color) {
        Vec3 tip = wandTip(player);
        if (tip.distanceToSqr(impact) < 1.0e-4) {
            return;
        }
        com.arcanum.network.ArcanumNetwork.sendBeam(level, tip, impact, color, 10, 0.065f, 0.6f);
        level.sendParticles(glow(color), impact.x, impact.y, impact.z, 5, 0.07, 0.07, 0.07, 0.01);
    }

    /**
     * İki nokta arasına iz partikülü dizer. Adım aralığı ~1/3'e düşürülür (~3x yoğunluk)
     * ve her adımda eksene dik modest yanal saçılım eklenir → ince çizgi yerine dolgun tüp izi.
     */
    public static void line(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions p, double spacing) {
        double dist = a.distanceTo(b);
        int steps = Math.max(1, (int) (dist / (spacing / 3.0)));
        // eksene dik iki taban vektörü (yanal saçılım için)
        Vec3 dir = dist < 1.0e-4 ? new Vec3(0, 1, 0) : b.subtract(a).scale(1.0 / dist);
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0e-4) {
            right = dir.cross(new Vec3(1, 0, 0));
        }
        right = right.normalize();
        Vec3 up = right.cross(dir).normalize();
        for (int i = 0; i <= steps; i++) {
            Vec3 q = a.lerp(b, (double) i / steps);
            double sa = level.random.nextDouble() * Math.PI * 2;
            double sr = 0.10 + level.random.nextDouble() * 0.08; // 0.10..0.18 blok
            Vec3 so = right.scale(Math.cos(sa) * sr).add(up.scale(Math.sin(sa) * sr));
            level.sendParticles(p, q.x + so.x, q.y + so.y, q.z + so.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Küresel patlama — merkezden dışa saçılan ışıma.
     * Çarpma "seyi biraz büyüsün": partikül sayısı ~1.75x, yarıçap ~%30 arttırıldı
     * (fireball kadar değil, orta düzey).
     */
    public static void burst(ServerLevel level, Vec3 at, int color, int count, double spread, double speed) {
        // Çarpma noktasında (çarpılan kişinin etrafında) da büyü renginde rün çemberi.
        runeRing(level, at, color, 7, 0.9);
        int n = Math.max(1, (int) (count * 1.75));
        double r = spread * 1.3;
        level.sendParticles(glow(color), at.x, at.y, at.z, n, r, r, r, speed);
        level.sendParticles(glow(lighten(color, 0.5f)), at.x, at.y, at.z, Math.max(1, n / 3),
                r * 0.4, r * 0.4, r * 0.4, speed * 0.6);
        level.sendParticles(ModParticles.SPELL_SPARK.get(), at.x, at.y, at.z, Math.max(1, n / 2),
                r, r, r, speed);
    }

    /**
     * Yatay genişleyen şok halkası (count=0 hız hilesi ile).
     * Çarpma halkası orta düzeyde büyütüldü: ~1.5x nokta yoğunluğu, ~%30 daha geniş yayılım.
     */
    public static void nova(ServerLevel level, Vec3 at, int color, int points, double speed) {
        ParticleOptions p = glow(color);
        int n = Math.max(1, (int) (points * 1.5));
        double sp = speed * 1.3;
        for (int i = 0; i < n; i++) {
            double a = (Math.PI * 2 * i) / n;
            double vx = Math.cos(a);
            double vz = Math.sin(a);
            level.sendParticles(p, at.x, at.y + 0.1, at.z, 0, vx, 0.02, vz, sp);
        }
    }

    /** Yarımküre kubbe (Protego) — oyuncunun etrafında ışıma kabuğu. */
    public static void dome(ServerLevel level, Vec3 center, int color, double radius) {
        ParticleOptions p = glow(color);
        int rings = 4;
        for (int r = 0; r <= rings; r++) {
            double pitch = (Math.PI / 2) * r / rings;
            double y = Math.sin(pitch) * radius;
            double ringRad = Math.cos(pitch) * radius;
            int n = Math.max(4, (int) (ringRad * 9));
            for (int i = 0; i < n; i++) {
                double a = (Math.PI * 2 * i) / n;
                level.sendParticles(p,
                        center.x + Math.cos(a) * ringRad,
                        center.y + 0.2 + y,
                        center.z + Math.sin(a) * ringRad,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Varlık etrafında yükselen çift sarmal (öğrenme/şifa ritüeli). */
    public static void helix(ServerLevel level, LivingEntity around, int color, double height, int perStrand) {
        Vec3 c = around.position();
        ParticleOptions p1 = glow(color);
        ParticleOptions p2 = glow(lighten(color, 0.4f));
        for (int i = 0; i < perStrand; i++) {
            double t = (double) i / perStrand;
            double ang = t * Math.PI * 4;
            double rad = 0.8 - t * 0.25;
            double y = c.y + t * height;
            level.sendParticles(p1, c.x + Math.cos(ang) * rad, y, c.z + Math.sin(ang) * rad, 1, 0, 0, 0, 0);
            level.sendParticles(p2, c.x - Math.cos(ang) * rad, y, c.z - Math.sin(ang) * rad, 1, 0, 0, 0, 0);
        }
    }

    /** Zeminde rün çemberi — kast çemberi / ritüel işaretleri. */
    public static void runeRing(ServerLevel level, Vec3 center, int color, int count, double radius) {
        ParticleOptions p = rune(color);
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2 * i) / count + level.random.nextDouble() * 0.2;
            level.sendParticles(p,
                    center.x + Math.cos(a) * radius,
                    center.y + 0.15,
                    center.z + Math.sin(a) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Hedefin GÖVDESİ etrafına DAĞINIK, SEYREK rünler saçar (ayak-altı çember DEĞİL) — büyü
     * çarpınca vücudu gevşekçe saran birkaç mistik işaret. {@code count} düşük tutulmalı
     * (seyrek görünüm). Her rün rastgele açı + ayak-baş arası rastgele yükseklikte belirir.
     */
    public static void bodyRunes(ServerLevel level, LivingEntity target, int color, int count) {
        ParticleOptions p = rune(color);
        double w = target.getBbWidth();
        double h = target.getBbHeight();
        for (int i = 0; i < count; i++) {
            double ang = level.random.nextDouble() * Math.PI * 2;
            double rad = w * 0.5 + level.random.nextDouble() * 0.25; // gövde yüzeyi civarı, hafif dağınık
            double y = target.getY() + 0.2 + level.random.nextDouble() * Math.max(0.4, h - 0.2);
            level.sendParticles(p,
                    target.getX() + Math.cos(ang) * rad,
                    y,
                    target.getZ() + Math.sin(ang) * rad,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Hedefin gövdesi boyunca renkli sarmalayıcı ışıma (etki-altında görseli). */
    public static void shroud(ServerLevel level, LivingEntity target, int color, int count) {
        level.sendParticles(glow(color),
                target.getX(), target.getY(0.5), target.getZ(),
                count,
                target.getBbWidth() * 0.45, target.getBbHeight() * 0.35, target.getBbWidth() * 0.45,
                0.01);
    }

    // ===================== ses =====================

    public static void sound(ServerLevel level, Vec3 at, SoundEvent e, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, e, SoundSource.PLAYERS, vol, pitch);
    }

    public static void soundAt(ServerLevel level, Player player, SoundEvent e, float vol, float pitch) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), e, SoundSource.PLAYERS, vol, pitch);
    }

    /** Hafif perde rastgeleliği — aynı büyü arka arkaya robotik durmasın. */
    public static float vary(ServerLevel level, float pitch) {
        return pitch * (0.94f + level.random.nextFloat() * 0.12f);
    }

    public static float clamp01(float v) {
        return Mth.clamp(v, 0f, 1f);
    }
}
