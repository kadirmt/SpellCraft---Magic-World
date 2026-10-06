package com.arcanum.platform;

import com.arcanum.platform.client.PlatformClient;

/**
 * Ortak kodun loader'a dokunduğu TEK yer (ARCHITECTURE.md §3).
 *
 * <p>ServiceLoader YOK (Forge modül katmanında sorun çıkarır): loader giriş sınıfı HER ŞEYDEN ÖNCE
 * {@link #init(PlatformBackend)} çağırır; istemci giriş noktası ayrıca {@link #initClient(PlatformClient)} çağırır.
 * {@code PlatformClient} ayrı tutulur ki dedicated sunucuda istemci sınıflarına referans veren backend hiç yüklenmesin.
 *
 * <p>Sıra (iki loader'da AYNI):
 * <pre>
 *   Platform.init(backend)  →  Arcanum.init()  →  [kayıtlar bağlanır]  →  Arcanum.commonSetup()
 *   (istemci) Platform.initClient(clientBackend)  →  ArcanumClient.init()
 * </pre>
 */
public final class Platform {
    private static PlatformBackend backend;
    private static PlatformClient client;

    private Platform() {}

    public static void init(PlatformBackend impl) {
        if (backend != null) throw new IllegalStateException("Platform.init iki kez cagrildi");
        backend = java.util.Objects.requireNonNull(impl, "impl");
    }

    public static void initClient(PlatformClient impl) {
        if (client != null) throw new IllegalStateException("Platform.initClient iki kez cagrildi");
        client = java.util.Objects.requireNonNull(impl, "impl");
    }

    public static PlatformBackend get() {
        if (backend == null) {
            throw new IllegalStateException("Platform.init(...) henuz cagrilmadi — loader giris sinifi ILK is olarak cagirmali");
        }
        return backend;
    }

    public static PlatformNet net() {
        return get().net();
    }

    /** Yalnız fiziksel istemcide ve {@link #initClient} sonrasında geçerli. */
    public static PlatformClient client() {
        if (client == null) {
            throw new IllegalStateException("Platform.initClient(...) cagrilmadi (dedicated sunucuda client() KULLANILMAZ)");
        }
        return client;
    }

    public static boolean isInitialized() {
        return backend != null;
    }
}
