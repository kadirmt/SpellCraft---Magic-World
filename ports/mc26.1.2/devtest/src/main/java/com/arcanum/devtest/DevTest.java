package com.arcanum.devtest;

import com.arcanum.client.ClientCompat;
import com.arcanum.config.ArcanumConfig;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.spell.ModSpells;
import com.arcanum.spell.Spell;
import com.mojang.brigadier.ParseResults;
import com.mojang.logging.LogUtils;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.Commands;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.Util;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Arcanum DEV-ONLY istemci test düzeneği — oyunun İÇİNDEN, pencere odağı/klavye gerektirmeden senaryo koşar ve
 * ekran görüntüsü alır. YAYIN JAR'INA GİRMEZ (ayrı {@code devtest} sourceSet'i; yalnız {@code runDevtestClient}).
 *
 * <p>Etkinleştirme (JVM sistem özellikleri; yoksa HİÇBİR ŞEY yapmaz — tick dinleyicisi bile kaydedilmez):
 * <ul>
 *   <li>{@code arcanum.devtest=<liste>} — virgüllü senaryo listesi ya da {@code all}:
 *       {@code hud,envanter,gmenu,masa,yaratiklar,pelerin,buyu,partikul,yaprak}</li>
 *   <li>{@code arcanum.devtest.out=<dizin>} — kareler + {@code devtest.log} (varsayılan {@code <oyunDizini>/devtest-out})</li>
 *   <li>{@code arcanum.devtest.world=<ad>} — başlık ekranında bu tekil-oyuncu dünyasını AÇ (yoksa düz/yaratıcı/hileli
 *       olarak OLUŞTUR). Verilmezse düzenek oyuncunun dünyaya girmesini bekler ({@code --quickPlaySingleplayer} /
 *       {@code --quickPlayMultiplayer}; çok oyunculuda oyuncuya sunucuda {@code op} verilmiş olmalı).</li>
 *   <li>{@code arcanum.devtest.quit=true|false} — bitince oyunu kapat (varsayılan true)</li>
 * </ul>
 * Log biçimi: {@code [DEVTEST] <senaryo>/<adım> OK|FAIL <ayrıntı> [kare=<dosya> <piksel istatistiği>]}.
 */
public final class DevTest {
    static final Logger LOGGER = LogUtils.getLogger();
    public static final String PROP = "arcanum.devtest";

    private static @Nullable DevTest instance;

    /** Loader giriş sınıfları tick dinleyicisini YALNIZ bu true iken kaydeder. */
    public static boolean enabled() {
        String v = System.getProperty(PROP);
        return v != null && !v.isBlank();
    }

    /** Her istemci tick sonunda (Fabric END_CLIENT_TICK / Forge ClientTickEvent.Post). */
    public static void onClientTick(Minecraft mc) {
        if (instance == null) {
            if (!enabled()) {
                return;
            }
            instance = new DevTest(mc);
        }
        instance.tick(mc);
    }

    // ---------------------------------------------------------------- durum

    private enum State { WAIT_WORLD, SETTLE, RUN, FINISH, DONE }

    private enum Phase { START, WAIT, SHOT_PREP, SHOT_PENDING }

    private final Path outDir;
    private final @Nullable String worldName;
    private final boolean quit;
    private final List<String> scenarioNames;
    private final @Nullable BufferedWriter logFile;

    private State state = State.WAIT_WORLD;
    private int worldTicks;
    private boolean worldRequested;
    private int settleTicks;
    private int stuckScreenTicks;

    private List<Plan.Step> steps = List.of();
    private int stepIdx;
    private Phase phase = Phase.START;
    private int phaseTicks;
    private int shotCounter;
    private @Nullable String stepError;
    private final List<String> stepNotes = new ArrayList<>();
    /** FR3: bu adımda kaydedilen sohbet metinleri (komut geri bildirimi) — kontroller {@link Ctx#stepChat()} ile okur. */
    private final List<String> stepChat = new ArrayList<>();
    private volatile DevShot.@Nullable Result shotResult;
    private @Nullable String shotFile;

    private final Deque<String> cmdQueue = new ArrayDeque<>();
    private boolean holdUse;
    /** Senaryonun her istemci tick'inde (RUN, adım/kare fazlarından ÖNCE) çalıştırdığı kanca; null = yok. */
    private java.util.function.@Nullable Consumer<Ctx> tickHook;

    private int ok;
    private int fail;
    private final List<String> failed = new ArrayList<>();

    // sohbet yakalama (yansıma; dev-only) — komut hatalarını (kırmızı) adıma bağlar
    private @Nullable Field chatField;
    private boolean chatReflectFailed;
    private boolean cursorReflectFailed;
    private final Set<GuiMessage> seenChat = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Ctx ctx = new Ctx();

    private DevTest(Minecraft mc) {
        String list = System.getProperty(PROP, "").trim();
        this.scenarioNames = list.equalsIgnoreCase("all")
                ? Scenarios.ALL
                : Arrays.stream(list.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).toList();
        String out = System.getProperty("arcanum.devtest.out");
        this.outDir = (out == null || out.isBlank())
                ? mc.gameDirectory.toPath().resolve("devtest-out")
                : Path.of(out);
        String w = System.getProperty("arcanum.devtest.world");
        this.worldName = (w == null || w.isBlank()) ? null : w.trim();
        this.quit = Boolean.parseBoolean(System.getProperty("arcanum.devtest.quit", "true"));
        BufferedWriter bw = null;
        try {
            Files.createDirectories(outDir);
            bw = Files.newBufferedWriter(outDir.resolve("devtest.log"), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            LOGGER.error("[DEVTEST] cikti dizini acilamadi: {}", outDir, e);
        }
        this.logFile = bw;
        log("[DEVTEST] BASLADI " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + " senaryolar=" + scenarioNames + " cikti=" + outDir.toAbsolutePath()
                + " dunya=" + (worldName == null ? "(quickPlay/elle)" : worldName) + " quit=" + quit);
        // SAHNE DETERMİNİZMİ: Arcanum yüzey spawner'ı vanilla spawn_mobs kuralına bakmaz (yalnız config +
        // barışçıl zorluk) → sahneye rastgele yaratık düşürür. Bu JVM'deki (tekil oyunculukta entegre sunucunun da
        // okuduğu) config örneğinde BELLEKTE kapatılır; dosyaya yazılmaz. Çok oyunculuda sunucunun kendi config'i
        // geçerlidir. -Darcanum.devtest.spawner=on ile kapatma atlanır.
        if (!"on".equalsIgnoreCase(System.getProperty("arcanum.devtest.spawner", "off"))) {
            ArcanumConfig.get().surfaceSpawnerEnabled = false;
            log("[DEVTEST] sahne: Arcanum yuzey spawner'i bu JVM'de bellekte kapatildi (arcanum.devtest.spawner=on ile acik kalir)");
        }
        for (String s : scenarioNames) {
            if (!Scenarios.ALL.contains(s) && !Scenarios.OPT_IN.contains(s)) {
                log("[DEVTEST] UYARI bilinmeyen senaryo '" + s + "' (gecerli: " + Scenarios.ALL + " + yalniz adiyla: " + Scenarios.OPT_IN + ")");
            }
        }
    }

    // ---------------------------------------------------------------- log

    void log(String line) {
        LOGGER.info(line);
        if (logFile != null) {
            try {
                logFile.write(line);
                logFile.newLine();
                logFile.flush();
            } catch (IOException ignored) {
                // log dosyası yazılamazsa oyun log'u yine var
            }
        }
    }

    // ---------------------------------------------------------------- tick

    private void tick(Minecraft mc) {
        try {
            tickInner(mc);
        } catch (Throwable t) {
            LOGGER.error("[DEVTEST] beklenmeyen hata", t);
            if (state == State.RUN && stepIdx < steps.size()) {
                stepError = "istisna: " + t;
                finishStep(null);
            } else {
                log("[DEVTEST] HATA durum=" + state + " " + t);
                state = State.FINISH;
            }
        }
    }

    private void tickInner(Minecraft mc) {
        switch (state) {
            case WAIT_WORLD -> waitWorld(mc);
            case SETTLE -> settle(mc);
            case RUN -> run(mc);
            case FINISH -> finish(mc);
            case DONE -> { }
        }
    }

    private boolean inWorld(Minecraft mc) {
        return mc.level != null && mc.player != null && mc.getConnection() != null && mc.gameMode != null;
    }

    private void waitWorld(Minecraft mc) {
        worldTicks++;
        if (inWorld(mc)) {
            log("[DEVTEST] dunyaya girildi (" + worldTicks + " tick) oyuncu=" + mc.player.getName().getString()
                    + " yerel=" + mc.isLocalServer());
            state = State.SETTLE;
            settleTicks = 0;
            return;
        }
        if (worldTicks > 20 * 600) {
            fail("hazirlik", "dunya", "10 dk icinde dunyaya girilemedi (ekran="
                    + (DevCompat.screen() == null ? "yok" : DevCompat.screen().getClass().getSimpleName()) + ")");
            state = State.FINISH;
            return;
        }
        if (worldName != null && !worldRequested && mc.getOverlay() == null && DevCompat.screen() instanceof TitleScreen) {
            worldRequested = true;
            if (mc.getLevelSource().levelExists(worldName)) {
                log("[DEVTEST] dunya aciliyor: " + worldName);
                mc.createWorldOpenFlows().openWorld(worldName, () -> DevCompat.setScreen(new TitleScreen()));
            } else {
                log("[DEVTEST] dunya olusturuluyor (duz, the_void biyomu, yaratici, hileler acik, yapilar kapali,"
                        + " spawn_mobs/advance_time/advance_weather=false): " + worldName);
                createStageWorld(mc, worldName);
            }
        }
    }

    /**
     * {@code WorldOpenFlows#createFreshLevel}'in (26.1.2 genSources) birebir kopyası — TEK farkı oyun kurallarını
     * DÜNYANIN İLK TICK'İNDEN ÖNCE vermesi ({@code Minecraft#doWorldLoad(..., Optional<GameRules>, true)}; vanilla
     * {@code createFreshLevel} {@code Optional.empty()} geçer). Neden: sahne dünyası daha ilk tick'te doğal spawn
     * yaparsa (gameTime 0, CREATURE döngüsü) düzeneğin {@code /gamerule spawn_mobs false} komutu yetişmez.
     */
    private static void createStageWorld(Minecraft mc, String name) {
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true, WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(20260923L, false, false);
        LevelStorageSource.LevelStorageAccess access;
        try {
            access = mc.getLevelSource().validateAndCreateAccess(name);
        } catch (Exception e) {
            LOGGER.error("[DEVTEST] dunya klasoru acilamadi {}", name, e);
            return;
        }
        PackRepository packRepository = ServerPacksSource.createPackRepository(access);
        try {
            WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(packRepository, settings.dataConfiguration(), false, false);
            WorldLoader.InitConfig config = new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.INTEGRATED,
                    LevelBasedPermissionSet.GAMEMASTER);
            CompletableFuture<WorldStem> load = WorldLoader.load(config, context -> {
                WorldDimensions dimensions = stageDimensions(context.datapackWorldgen());
                WorldDimensions.Complete complete = dimensions.bake(context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM));
                return new WorldLoader.DataLoadOutput<>(
                        new LevelDataAndDimensions.WorldDataAndGenSettings(
                                new PrimaryLevelData(settings, complete.specialWorldProperty(), complete.lifecycle()),
                                new WorldGenSettings(options, dimensions)),
                        complete.dimensionsRegistryAccess());
            }, WorldStem::new, Util.backgroundExecutor(), mc);
            mc.managedBlock(load::isDone);
            WorldStem stem = load.get();
            GameRules rules = new GameRules(settings.dataConfiguration().enabledFeatures());
            rules.set(GameRules.SPAWN_MOBS, false, null);
            rules.set(GameRules.ADVANCE_TIME, false, null);
            rules.set(GameRules.ADVANCE_WEATHER, false, null);
            mc.doWorldLoad(access, packRepository, stem, Optional.of(rules), true);
        } catch (Exception e) {
            LOGGER.error("[DEVTEST] sahne dunyasi olusturulamadi", e);
            access.safeClose();
            DevCompat.setScreen(new TitleScreen());
        }
    }

    /**
     * Sahne dünyası: vanilla düz dünya katmanları (ana kaya + 2 toprak + çimen) ama biyom {@code minecraft:the_void}
     * ve yapı seti YOK → doğal (chunk üretimi) ve Arcanum yüzey spawner'ı hiçbir yaratık doğurmaz; sahne
     * deterministik kalır. (Varsayılan düz dünyanın plains biyomu Arcanum yaratıklarını doğuruyordu.)
     */
    private static WorldDimensions stageDimensions(HolderLookup.Provider reg) {
        WorldDimensions flat = WorldPresets.createFlatWorldDimensions(reg);
        HolderLookup.RegistryLookup<Biome> biomes = reg.lookupOrThrow(Registries.BIOME);
        FlatLevelGeneratorSettings def = FlatLevelGeneratorSettings.getDefault(biomes,
                reg.lookupOrThrow(Registries.STRUCTURE_SET), reg.lookupOrThrow(Registries.PLACED_FEATURE));
        FlatLevelGeneratorSettings stage = def.withBiomeAndLayers(def.getLayersInfo(), Optional.empty(),
                biomes.getOrThrow(Biomes.THE_VOID));
        return flat.replaceOverworldGenerator(reg, new FlatLevelSource(stage));
    }

    private void settle(Minecraft mc) {
        if (!inWorld(mc)) {
            state = State.WAIT_WORLD;
            return;
        }
        if (DevCompat.screen() != null) {
            settleTicks = 0;
            if (!(DevCompat.screen() instanceof LevelLoadingScreen) && ++stuckScreenTicks > 200) {
                log("[DEVTEST] beklenmeyen ekran kapatiliyor: " + DevCompat.screen().getClass().getName());
                DevCompat.setScreen(null);
                stuckScreenTicks = 0;
            }
            return;
        }
        if (++settleTicks >= 60) {
            // oturumdan önceki sohbeti "görüldü" say
            pollChat(mc, false);
            steps = Scenarios.build(scenarioNames).steps;
            log("[DEVTEST] " + steps.size() + " adim calisacak");
            state = State.RUN;
            stepIdx = 0;
            phase = Phase.START;
        }
    }

    private void run(Minecraft mc) {
        if (!inWorld(mc)) {
            fail("?", "baglanti", "dunyadan dusuldu (adim " + stepIdx + ")");
            state = State.FINISH;
            return;
        }
        // komut kuyruğu: tick başına en fazla 2 (sunucu sohbet-spam eşiği; op'lar muaf ama nazik olalım)
        for (int i = 0; i < 2 && !cmdQueue.isEmpty(); i++) {
            sendNow(mc, cmdQueue.pollFirst());
        }
        if (holdUse) {
            mc.options.keyUse.setDown(true);
        }
        if (tickHook != null) {
            tickHook.accept(ctx);
        }
        if (stepIdx >= steps.size()) {
            state = State.FINISH;
            return;
        }
        Plan.Step step = steps.get(stepIdx);
        switch (phase) {
            case START -> {
                stepError = null;
                stepNotes.clear();
                stepChat.clear();
                phaseTicks = 0;
                phase = Phase.WAIT;
                if (step.action != null) {
                    step.action.accept(ctx);
                }
            }
            case WAIT -> {
                if (!cmdQueue.isEmpty()) {
                    return; // bekleme sayacı komutlar gönderildikten SONRA işler
                }
                phaseTicks++;
                boolean untilOk = step.until == null || step.until.test(ctx);
                if (phaseTicks >= step.wait && untilOk) {
                    afterWait(mc, step);
                } else if (step.until != null && phaseTicks >= Math.max(step.wait, step.maxWait)) {
                    stepError = "zaman asimi (" + step.maxWait + " tick) kosul saglanmadi";
                    afterWait(mc, step);
                }
            }
            case SHOT_PREP -> {
                if (++phaseTicks >= 2) {
                    shotCounter++;
                    String file = String.format(Locale.ROOT, "%03d_%s.png", shotCounter, step.shot);
                    shotFile = file;
                    shotResult = null;
                    phase = Phase.SHOT_PENDING;
                    phaseTicks = 0;
                    DevShot.take(outDir.resolve(file), r -> shotResult = r);
                }
            }
            case SHOT_PENDING -> {
                phaseTicks++;
                DevShot.Result r = shotResult;
                if (r != null) {
                    if (!r.ok() && stepError == null) {
                        stepError = "bos/siyah kare";
                    }
                    finishStep(r.detail());
                } else if (phaseTicks > 100) {
                    if (stepError == null) {
                        stepError = "ekran goruntusu 100 tickte donmedi";
                    }
                    finishStep(null);
                }
            }
        }
    }

    private void afterWait(Minecraft mc, Plan.Step step) {
        pollChat(mc, true);
        if (step.check != null && stepError == null) {
            String err = step.check.apply(ctx);
            if (err != null) {
                stepError = err;
            }
        }
        if (step.shot != null) {
            // Kare temizliği: sohbet (komut geri bildirimi) + toast'lar (ilerleme/öğretici) kareyi örtmesin.
            // Sohbet önce pollChat ile log'a alındı; yalnız GÖRÜNEN liste temizlenir (gönderim geçmişi kalır).
            // Temizlenmiş hâlin en az bir karede çizilmesi için SHOT_PREP 2 tick bekler (kare = önceki render).
            mc.gui.getChat().clearMessages(false);
            ClientCompat.toasts().clear();
            seenChat.clear();
            parkCursor(mc);
            phase = Phase.SHOT_PREP;
            phaseTicks = 0;
        } else {
            shotFile = null;
            finishStep(null);
        }
    }

    /**
     * Ekran açıkken oyunun bildiği imleç konumunu (0,0)'a çek: kullanıcının GERÇEK fare imleci pencerenin
     * üstündeyse ekranlar (G menüsü, envanter) rastgele bir öğenin ipucunu çiziyordu. İşletim sistemi imlecine
     * DOKUNULMAZ — yalnız {@code MouseHandler}'ın özel {@code xpos/ypos} alanları (dev-only yansıma); gerçek bir fare
     * hareketi onları yeniden yazar.
     */
    private void parkCursor(Minecraft mc) {
        if (DevCompat.screen() == null || cursorReflectFailed) {
            return;
        }
        try {
            Field x = MouseHandler.class.getDeclaredField("xpos");
            Field y = MouseHandler.class.getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(mc.mouseHandler, 0.0);
            y.setDouble(mc.mouseHandler, 0.0);
        } catch (Throwable t) {
            cursorReflectFailed = true;
            log("[DEVTEST] UYARI imlec park edilemedi (ipucu kareye girebilir): " + t);
        }
    }

    private void finishStep(@Nullable String shotDetail) {
        Plan.Step step = steps.get(stepIdx);
        StringBuilder sb = new StringBuilder("[DEVTEST] ").append(step.scenario).append('/').append(step.name).append(' ');
        if (stepError == null) {
            sb.append("OK");
            ok++;
        } else {
            sb.append("FAIL ").append(stepError);
            fail++;
            failed.add(step.scenario + "/" + step.name);
        }
        if (!stepNotes.isEmpty()) {
            sb.append(" | ").append(String.join("; ", stepNotes));
        }
        if (shotFile != null) {
            sb.append(" | kare=").append(shotFile);
            if (shotDetail != null) {
                sb.append(' ').append(shotDetail);
            }
        }
        log(sb.toString());
        shotFile = null;
        stepIdx++;
        phase = Phase.START;
    }

    private void finish(Minecraft mc) {
        holdUse = false;
        tickHook = null;
        mc.options.keyUse.setDown(false);
        DevCompat.setHudHidden(false);
        String summary = "[DEVTEST] SONUC ok=" + ok + " fail=" + fail + (failed.isEmpty() ? "" : " basarisiz=" + failed);
        log(summary);
        try {
            Files.writeString(outDir.resolve("devtest-summary.txt"), summary + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // özet satırı log'da zaten var
        }
        state = State.DONE;
        if (logFile != null) {
            try {
                logFile.close();
            } catch (IOException ignored) {
                // kapatılamadıysa önemli değil
            }
        }
        if (quit) {
            LOGGER.info("[DEVTEST] oyun kapatiliyor (arcanum.devtest.quit=true)");
            mc.stop();
        }
    }

    private void fail(String scenario, String name, String why) {
        fail++;
        failed.add(scenario + "/" + name);
        log("[DEVTEST] " + scenario + "/" + name + " FAIL " + why);
    }

    // ---------------------------------------------------------------- komut + sohbet

    private void sendNow(Minecraft mc, String cmd) {
        ClientPacketListener conn = mc.getConnection();
        if (conn == null) {
            return;
        }
        try {
            ParseResults<?> parse = conn.getCommands().parse(cmd, conn.getSuggestionsProvider());
            if (parse.getReader().canRead() || !parse.getExceptions().isEmpty()) {
                stepNotes.add("istemci-ayristirma-uyarisi '" + cmd + "'");
            }
        } catch (Throwable ignored) {
            // yalnız tanı
        }
        conn.sendCommand(cmd);
    }

    @SuppressWarnings("unchecked")
    private void pollChat(Minecraft mc, boolean record) {
        if (chatReflectFailed) {
            return;
        }
        try {
            if (chatField == null) {
                chatField = ChatComponent.class.getDeclaredField("allMessages");
                chatField.setAccessible(true);
            }
            List<GuiMessage> all = (List<GuiMessage>) chatField.get(mc.gui.getChat());
            List<GuiMessage> fresh = new ArrayList<>();
            for (GuiMessage m : all) { // en yeni başta
                if (seenChat.contains(m)) {
                    break;
                }
                fresh.add(m);
            }
            Collections.reverse(fresh);
            for (GuiMessage m : fresh) {
                seenChat.add(m);
                if (!record) {
                    continue;
                }
                String text = m.content().getString();
                boolean red = isRed(m);
                stepChat.add(text);
                if (red && isBenign(text)) {
                    log("[DEVTEST]   sohbet(zararsiz-bos-islem): " + text);
                    continue;
                }
                if (red) {
                    if (stepError == null) {
                        stepError = "komut hatasi: " + text;
                    } else {
                        stepNotes.add("komut hatasi: " + text);
                    }
                }
                log("[DEVTEST]   sohbet" + (red ? "(HATA)" : "") + ": " + text);
            }
        } catch (Throwable t) {
            chatReflectFailed = true;
            log("[DEVTEST] UYARI sohbet yakalama kapali (yansima basarisiz): " + t);
        }
    }

    /**
     * Temizlik/ön-koşul komutlarının "yapacak iş yok" yanıtları (kırmızı ama zararsız): boş envanteri
     * {@code clear}, efektsiz oyuncuya {@code effect clear}, eşleşmeyen {@code kill}/{@code execute as}, zaten hava olan
     * bölgeye {@code fill air}. Bunlar adımı FAIL yapmaz. (Mesajlar vanilla en_us; istemci dili İngilizce varsayılır.)
     */
    private static boolean isBenign(String text) {
        return text.startsWith("No items were found on player")
                || text.equals("Target has no effects to remove")
                || text.equals("No entity was found")
                || text.equals("No blocks were filled");
    }

    private static boolean isRed(GuiMessage m) {
        TextColor red = TextColor.fromLegacyFormat(ChatFormatting.RED);
        if (red == null) {
            return false;
        }
        if (red.equals(m.content().getStyle().getColor())) {
            return true;
        }
        for (var sib : m.content().getSiblings()) {
            if (red.equals(sib.getStyle().getColor())) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- senaryo bağlamı

    /** Senaryo adımlarının kullandığı yardımcılar. */
    final class Ctx {
        Minecraft mc() {
            return Minecraft.getInstance();
        }

        LocalPlayer player() {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null) {
                throw new IllegalStateException("oyuncu yok");
            }
            return p;
        }

        ClientLevel level() {
            ClientLevel l = Minecraft.getInstance().level;
            if (l == null) {
                throw new IllegalStateException("dunya yok");
            }
            return l;
        }

        /** Sunucu komutu kuyruğa (oyuncunun komut yolu: {@code ClientPacketListener#sendCommand}). */
        void cmd(String... cmds) {
            for (String c : cmds) {
                cmdQueue.addLast(c.startsWith("/") ? c.substring(1) : c);
            }
        }

        /** Arcanum C2S paketi (G menüsünün kullandığı yol). */
        void send(CustomPacketPayload payload) {
            ArcanumNetwork.sendToServer(payload);
        }

        /** Sağ tık eşdeğeri: {@code MultiPlayerGameMode#useItem} (ana el). */
        void useItem() {
            Minecraft mc = mc();
            if (mc.gameMode != null) {
                mc.gameMode.useItem(player(), InteractionHand.MAIN_HAND);
            }
        }

        /** Sağ tık BASILI TUT (kanal büyüleri) — her tick {@code keyUse.setDown(true)} yeniden uygulanır. */
        void hold(boolean down) {
            holdUse = down;
            mc().options.keyUse.setDown(down);
        }

        void camera(CameraType type) {
            mc().options.setCameraType(type);
        }

        void hud(boolean visible) {
            DevCompat.setHudHidden(!visible);
        }

        /** Her tick çalışacak kanca (kare hazırlık tick'leri dahil); {@code null} kaldırır. */
        void tickHook(java.util.function.@Nullable Consumer<Ctx> hook) {
            tickHook = hook;
        }

        void hotbar(int slot) {
            player().getInventory().setSelectedSlot(slot);
        }

        int spellIndex(String id) {
            for (int i = 0; i < ModSpells.SPELLS.size(); i++) {
                if (ModSpells.SPELLS.get(i).id().equals(id)) {
                    return i;
                }
            }
            throw new IllegalArgumentException("bilinmeyen buyu " + id);
        }

        Spell spell(String id) {
            return ModSpells.SPELLS.get(spellIndex(id));
        }

        int countNear(EntityType<?> type, double radius) {
            LocalPlayer p = player();
            AABB box = p.getBoundingBox().inflate(radius);
            List<Entity> list = level().getEntities((Entity) null, box, e -> e.getType() == type);
            return list.size();
        }

        /** FR3: bu adımda (eylemden kontrole dek) gelen sohbet satırları. */
        List<String> stepChat() {
            return List.copyOf(stepChat);
        }

        void note(String s) {
            stepNotes.add(s);
        }

        void error(String s) {
            if (stepError == null) {
                stepError = s;
            } else {
                stepNotes.add(s);
            }
        }
    }
}
