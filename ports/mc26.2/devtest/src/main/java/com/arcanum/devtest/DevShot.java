package com.arcanum.devtest;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;

/**
 * Oyunun KENDİ ana render hedefinden ekran görüntüsü (pencere odağı / masaüstü yakalama GEREKMEZ).
 *
 * <p>26.1.2 ve 26.2 vanilla imzası (genSources {@code net/minecraft/client/Screenshot.java}):
 * {@code public static void takeScreenshot(RenderTarget target, Consumer<NativeImage> callback)} — GPU dokusunu
 * bir tampona kopyalar, kopya bitince callback'i render thread'inde çağırır (NativeImage'ı kapatmak bizim işimiz).
 * İstemci tick'inde çağrıldığında ana hedef bir ÖNCEKİ karenin tamamlanmış görüntüsünü (GUI dahil) taşır
 * ({@code Minecraft.runTick}: önce tick'ler, sonra render) — F2 tuşuyla aynı durum.
 *
 * <p>Piksel istatistiği: her 3. piksel örneklenir; ortalama parlaklık, standart sapma, siyah oranı, mor
 * (eksik doku mor-siyahı) oranı, nicemlenmiş (5 bit/kanal) renk sayısı. "Boş/siyah kare" ölçütü:
 * std &lt; 6 VEYA siyah oranı &gt; %95 VEYA renk sayısı &lt; 8.
 */
final class DevShot {
    private DevShot() {}

    record Result(boolean ok, String detail) {}

    static void take(Path file, Consumer<Result> done) {
        Screenshot.takeScreenshot(DevCompat.mainRenderTarget(), image -> {
            Result r;
            try (NativeImage img = image) {
                String stats = stats(img);
                Files.createDirectories(file.getParent());
                img.writeToFile(file);
                boolean blank = stats.startsWith("BOS");
                r = new Result(!blank, stats + " " + img.getWidth() + "x" + img.getHeight());
            } catch (Throwable t) {
                r = new Result(false, "ekran goruntusu yazilamadi: " + t);
            }
            done.accept(r);
        });
    }

    private static String stats(NativeImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        long n = 0;
        double sum = 0;
        double sumSq = 0;
        long black = 0;
        long magenta = 0;
        Set<Integer> uniq = new HashSet<>();
        for (int y = 0; y < h; y += 3) {
            for (int x = 0; x < w; x += 3) {
                int argb = img.getPixel(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                double l = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                sum += l;
                sumSq += l * l;
                n++;
                if (l < 8) {
                    black++;
                }
                if (r > 200 && g < 40 && b > 200) {
                    magenta++;
                }
                if (uniq.size() < 4096) {
                    uniq.add(((r >> 3) << 10) | ((g >> 3) << 5) | (b >> 3));
                }
            }
        }
        double mean = sum / Math.max(1, n);
        double std = Math.sqrt(Math.max(0, sumSq / Math.max(1, n) - mean * mean));
        double blackPct = 100.0 * black / Math.max(1, n);
        double magPct = 100.0 * magenta / Math.max(1, n);
        boolean blank = std < 6 || blackPct > 95 || uniq.size() < 8;
        return String.format(java.util.Locale.ROOT, "%sort=%.1f std=%.1f siyah=%.1f%% mor=%.2f%% renk=%d",
                blank ? "BOS " : "", mean, std, blackPct, magPct, uniq.size());
    }
}
