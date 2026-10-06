package com.arcanum.entity;

/**
 * Süpürge drift girdisi için istemci→common statik köprü.
 *
 * <p>{@link BroomEntity#travel} common kodda yaşar ve istemci sınıflarına
 * (Minecraft/Options/KeyMapping) DOKUNAMAZ. Bu yüzden fabric istemci tick'i
 * (ArcanumFabricClient, END_CLIENT_TICK) her tick bu bayrağı günceller:
 * yerel oyuncu bir süpürgeye biniyorken SPACE (keyJump) basılıysa {@code true}.
 *
 * <p>Güvenlik: alan yalnızca {@code isControlledByLocalInstance()} dalında
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
