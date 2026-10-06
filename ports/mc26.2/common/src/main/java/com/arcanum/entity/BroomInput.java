package com.arcanum.entity;

/**
 * Süpürge drift girdisi için istemci→common statik köprü.
 *
 * <p>{@link BroomEntity#travel} common kodda yaşar ve istemci sınıflarına
 * (Minecraft/Options/KeyMapping) DOKUNAMAZ. Bu yüzden istemci tick'i
 * (ArcanumClient, loader'ın istemci tick sonu kancası) her tick bu bayrağı günceller:
 * yerel oyuncu bir süpürgeye biniyorken SPACE (keyJump) basılıysa {@code true}.
 *
 * <p>Güvenlik: alan yalnızca {@code isLocalInstanceAuthoritative()} dalında
 * (26.x; 1.21.1'deki {@code isControlledByLocalInstance()} muadili)
 * okunur — sunucu tarafında hiçbir kod bu alana yazmaz, dolayısıyla dedicated
 * sunucuda daima {@code false} kalır ve fizik istemci-otoritesinde kalır
 * (vanilla binek modeliyle aynı).
 */
public final class BroomInput {

    /** SPACE basılı mı — süpürge drift modu (yalnız yerel istemcide anlamlı). */
    public static volatile boolean drifting = false;

    private BroomInput() {
    }
}
