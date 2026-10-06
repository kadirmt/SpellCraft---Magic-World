package com.arcanum.devtest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Senaryo adım listesi (sıralı). Her adım: EYLEM → (komut kuyruğu boşalana ve en az {@code wait} tick geçene /
 * {@code until} sağlanana dek) BEKLE → KONTROL → (varsa) EKRAN GÖRÜNTÜSÜ → log satırı.
 */
final class Plan {
    /** Kontrol: {@code null} = OK, aksi hâlde FAIL gerekçesi. */
    interface Check extends Function<DevTest.Ctx, @Nullable String> {}

    static final class Step {
        final String scenario;
        final String name;
        final @Nullable Consumer<DevTest.Ctx> action;
        final int wait;
        final @Nullable Predicate<DevTest.Ctx> until;
        final int maxWait;
        final @Nullable Check check;
        final @Nullable String shot;

        Step(String scenario, String name, @Nullable Consumer<DevTest.Ctx> action, int wait,
             @Nullable Predicate<DevTest.Ctx> until, int maxWait, @Nullable Check check, @Nullable String shot) {
            this.scenario = scenario;
            this.name = name;
            this.action = action;
            this.wait = wait;
            this.until = until;
            this.maxWait = maxWait;
            this.check = check;
            this.shot = shot;
        }
    }

    final List<Step> steps = new ArrayList<>();
    private String scenario = "?";

    Plan scenario(String name) {
        this.scenario = name;
        return this;
    }

    /** Ekran görüntüsüz eylem. */
    Plan act(String name, int wait, Consumer<DevTest.Ctx> action) {
        steps.add(new Step(scenario, name, action, wait, null, 0, null, null));
        return this;
    }

    /** Ekran görüntüsüz eylem + kontrol. */
    Plan act(String name, int wait, Consumer<DevTest.Ctx> action, @Nullable Check check) {
        steps.add(new Step(scenario, name, action, wait, null, 0, check, null));
        return this;
    }

    /** Eylem → bekle → kontrol → kare ({@code <scenario>_<name>.png}). */
    Plan shot(String name, int wait, @Nullable Consumer<DevTest.Ctx> action, @Nullable Check check) {
        steps.add(new Step(scenario, name, action, wait, null, 0, check, scenario + "_" + name));
        return this;
    }

    /** Eylem → koşul sağlanana dek bekle (en fazla maxWait; aşılırsa FAIL) → kontrol → kare. */
    Plan shotUntil(String name, int minWait, int maxWait, @Nullable Consumer<DevTest.Ctx> action,
                   Predicate<DevTest.Ctx> until, @Nullable Check check) {
        steps.add(new Step(scenario, name, action, minWait, until, maxWait, check, scenario + "_" + name));
        return this;
    }
}
