import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * GeckoLib 4 → GeckoLib 5 "_glowmask" dokusu fırınlayıcı (yalnız geliştirme aracı; mod jar'ına girmez).
 *
 * <p><b>Neden:</b> kök 1.21.1 (GeckoLib 4.7.7) {@code AutoGlowingTexture.loadTexture} +
 * {@code GeoGlowingTextureMeta.fromExistingImage/createImageMask} glowmask'ı YALNIZ MASKE olarak kullanır:
 * <ul>
 *   <li>maske pikseli = glowmask'ta tam sıfır OLMAYAN her piksel (ABGR int != 0);</li>
 *   <li>parlama dokusu o konumda TABAN dokunun rengini taşır; maske alfası &gt; 0 ise alfa = maske alfası
 *       (taban alfası ezilir — unicorn'da taban alfası 0 olduğu için bu şart), aksi hâlde taban pikseli aynen;</li>
 *   <li>taban dokudan o piksel SİLİNİR (0x00000000) — gövde geçişi maske konumlarını çizmez.</li>
 * </ul>
 * GeckoLib 5.x ({@code RenderUtil.getEmissiveResource} = yalnız ".png"→"_glowmask.png") glowmask'ı DOĞRUDAN
 * parlama dokusu olarak çizer ve tabanı soymaz. Aynı görünüm için dokular GL5 sözleşmesine göre önceden
 * üretilir: bu araç kök 1.21.1 dokularını (kaynak gerçeği) okur, GL4'ün çalışma anında yaptığı dönüşümü birebir
 * uygular ve port dokularını yazar.
 *
 * <p>Kullanım (JDK 11+ tek-dosya):
 * <pre>java GlowmaskBake.java &lt;kök-1.21.1-textures/entity&gt; &lt;port-textures/entity&gt; ad1,ad2,...</pre>
 * Girdi ASLA değiştirilmez; çıktı yazıldıktan sonra geri okunup piksel piksel doğrulanır.
 */
public class GlowmaskBake {
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("kullanim: java GlowmaskBake.java <kaynakDir> <hedefDir> ad1,ad2,...");
            System.exit(2);
        }
        File src = new File(args[0]);
        File dst = new File(args[1]);
        for (String name : args[2].split(",")) {
            int[][] base = read(new File(src, name + ".png"));
            int[][] mask = read(new File(src, name + "_glowmask.png"));
            int h = base.length, w = base[0].length;
            if (mask.length != h || mask[0].length != w) {
                throw new IOException("boyut uyusmuyor: " + name); // GL4 de aynı durumda IOException atar
            }
            int[][] outBase = new int[h][w];
            int[][] outGlow = new int[h][w];
            int n = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int b = base[y][x];
                    int m = mask[y][x];
                    outBase[y][x] = b;
                    outGlow[y][x] = m; // GL4: glowImage = okunan maske görüntüsünün kendisi; yalnız maske konumları ezilir
                    if (m == 0) continue;
                    n++;
                    int maskAlpha = m >>> 24;
                    outGlow[y][x] = maskAlpha > 0 ? (maskAlpha << 24) | (b & 0x00FFFFFF) : b;
                    outBase[y][x] = 0;
                }
            }
            write(outBase, new File(dst, name + ".png"));
            write(outGlow, new File(dst, name + "_glowmask.png"));
            if (!same(read(new File(dst, name + ".png")), outBase)
                    || !same(read(new File(dst, name + "_glowmask.png")), outGlow)) {
                throw new IOException("geri okuma dogrulamasi basarisiz: " + name);
            }
            System.out.printf("%s: %dx%d, maske pikseli=%d -> taban soyuldu + parlama=taban rengi (maske alfasi)%n",
                    name, w, h, n);
        }
    }

    /** Ham ARGB (renk profili dönüşümü YOK — NativeImage/stb gibi). */
    private static int[][] read(File f) throws IOException {
        BufferedImage img = ImageIO.read(f);
        if (img == null) throw new IOException("okunamadi: " + f);
        int w = img.getWidth(), h = img.getHeight();
        int[][] px = new int[h][w];
        var r = img.getRaster();
        int bands = r.getNumBands();
        int[] s = new int[bands];
        boolean indexed = img.getColorModel() instanceof java.awt.image.IndexColorModel;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (indexed) {
                    px[y][x] = img.getColorModel().getRGB(r.getDataElements(x, y, null));
                    continue;
                }
                r.getPixel(x, y, s);
                int a = bands == 4 ? s[3] : bands == 2 ? s[1] : 255;
                int rr = s[0], gg = bands >= 3 ? s[1] : s[0], bb = bands >= 3 ? s[2] : s[0];
                px[y][x] = (a << 24) | (rr << 16) | (gg << 8) | bb;
            }
        }
        return px;
    }

    private static void write(int[][] px, File f) throws IOException {
        int h = px.length, w = px[0].length;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
        var r = img.getRaster();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = px[y][x];
                r.setPixel(x, y, new int[] {(c >>> 16) & 255, (c >>> 8) & 255, c & 255, c >>> 24});
            }
        }
        if (!ImageIO.write(img, "png", f)) throw new IOException("yazilamadi: " + f);
    }

    private static boolean same(int[][] a, int[][] b) {
        if (a.length != b.length) return false;
        for (int y = 0; y < a.length; y++) {
            if (!java.util.Arrays.equals(a[y], b[y])) return false;
        }
        return true;
    }
}
