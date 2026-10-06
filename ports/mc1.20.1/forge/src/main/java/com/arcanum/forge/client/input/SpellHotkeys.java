package com.arcanum.forge.client.input;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Numpad kısayol atamaları (slot 0-9 → büyü global index'i).
 * Buyu Carki acikken bir numpad tusuna basilinca hover'daki buyu o slota atanir;
 * carki kapaliyken ayni tusa basmak (asa elde iken) o slota atanmis buyuyu secer.
 * Atamalar basit bir .properties dosyasinda kalici tutulur.
 *
 * <p>Forge portu: fabric {@code FabricLoader.getConfigDir()} →
 * {@code FMLPaths.CONFIGDIR.get()} — dosya adı/format birebir aynı.
 */
public final class SpellHotkeys {
    private static final Map<Integer, Integer> SLOTS = new HashMap<>();
    private static final String FILE_NAME = "arcanum-hotkeys.properties";

    private SpellHotkeys() {
    }

    private static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    /** Config dosyasindan atamalari yukler. Dosya yoksa/okunamazsa sessizce bos harita ile devam eder. */
    public static void load() {
        SLOTS.clear();
        Path path = configPath();
        if (!Files.exists(path)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("[Arcanum] Buyu kisayol dosyasi okunamadi: " + e.getMessage());
            return;
        }
        for (String key : props.stringPropertyNames()) {
            try {
                int slot = Integer.parseInt(key.trim());
                int index = Integer.parseInt(props.getProperty(key).trim());
                SLOTS.put(slot, index);
            } catch (NumberFormatException ignored) {
                // gecersiz satir, atla
            }
        }
    }

    /** SLOTS haritasini .properties formatinda diske yazar. IO hatalari sessizce yutulur. */
    public static void save() {
        Properties props = new Properties();
        for (Map.Entry<Integer, Integer> entry : SLOTS.entrySet()) {
            props.setProperty(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        Path path = configPath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "Arcanum spell hotkeys (slot=spellIndex)");
            }
        } catch (IOException e) {
            System.err.println("[Arcanum] Buyu kisayol dosyasi yazilamadi: " + e.getMessage());
        }
    }

    /** Bir slota buyu global index'i atar ve hemen kaydeder. */
    public static void assign(int slot, int spellIndex) {
        SLOTS.put(slot, spellIndex);
        save();
    }

    /** Slota atanmis buyu global index'ini dondurur, atanmamissa null. */
    public static Integer get(int slot) {
        return SLOTS.get(slot);
    }
}
