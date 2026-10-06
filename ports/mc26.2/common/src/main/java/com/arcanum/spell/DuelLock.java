package com.arcanum.spell;

import java.util.ArrayDeque;
import java.util.UUID;

import net.minecraft.world.phys.Vec3;

/**
 * Bir "asa kenetlenmesi"nin (Priori Incantatem) sunucu-tarafı durum modeli.
 *
 * <p>İki oyuncu karşılıklı SALDIRI büyüsü attığında {@link WandLockManager} bir
 * {@code DuelLock} oluşturur: iki asa ucu arasında bir ışın kurulur, ortadaki
 * "çarpışma düğümü" ({@link #node}) her sunucu tick'inde asa gücü + tıklama hızı
 * (CPS) + kalan manaya göre KAYBEDENE doğru kayar.
 *
 * <p>{@link #node}: {@code 0.0} = {@link #a}'nın asası (A kaybeder) .. {@code 1.0} =
 * {@link #b}'nin asası (B kaybeder). Başlangıç {@code 0.5} (tam ortada).
 *
 * <p>Tüm alanlar YALNIZCA sunucu ana thread'inde okunup yazılır (WandLockManager.tick
 * ve C2S tık alıcısı {@code server.execute} ile ana thread'e taşınır) — bu yüzden ek
 * senkronizasyona gerek yoktur.
 */
public final class DuelLock {
    /** Kenetlenmeyi BAŞLATAN (ateşleyen) oyuncu. */
    public final UUID a;
    /** KARŞI taraf (cast eden / az önce ateşleyen). */
    public final UUID b;
    /** İki tarafın büyü index'i ({@link ModSpells#SPELLS}). Kazananın büyüsü kaybedene uygulanır. */
    public final int spellA;
    public final int spellB;
    /** İki büyünün rengi ({@code spell.color()}) — ışın renk karışımı için. */
    public final int colorA;
    public final int colorB;

    /** Kenetlenme başındaki oyuncu konumları — her tick buraya geri sabitlenir (WASD işlevsiz). */
    public Vec3 posA;
    public Vec3 posB;

    /** Düğüm konumu 0..1 (0 = A kaybeder ucu, 1 = B kaybeder ucu). */
    public float node = 0.5f;
    /** Kenetlenme başından beri geçen sunucu tick sayısı (sudden-death için). */
    public int ticks = 0;
    /** Görüş/menzil bozulunca artan toparlanma payı sayacı. */
    public int outOfSightTicks = 0;
    /** Sonuç çözüldü mü (çift-çözümü önler). */
    public boolean ended = false;

    /** Her iki oyuncunun son ~20 tick içindeki sol-tık zaman damgaları (CPS hesabı). */
    private final ArrayDeque<Long> clicksA = new ArrayDeque<>();
    private final ArrayDeque<Long> clicksB = new ArrayDeque<>();

    public DuelLock(UUID a, UUID b, int spellA, int spellB, int colorA, int colorB) {
        this.a = a;
        this.b = b;
        this.spellA = spellA;
        this.spellB = spellB;
        this.colorA = colorA;
        this.colorB = colorB;
    }

    /** Verilen oyuncunun kenetlenmedeki rakibinin UUID'si. */
    public UUID other(UUID id) {
        return id.equals(a) ? b : a;
    }

    /** Bu kenetlenme verilen oyuncuyu içeriyor mu? */
    public boolean involves(UUID id) {
        return id.equals(a) || id.equals(b);
    }

    /** Bir tık kaydı ekle (oyuncu C2S {@code lock_push} yolladığında). */
    public void addClick(UUID id, long gameTime) {
        (id.equals(a) ? clicksA : clicksB).addLast(gameTime);
    }

    /**
     * Verilen oyuncunun anlık CPS'i = son 20 tick (1 sn) içindeki tık sayısı.
     * Eski kayıtlar temizlenir. Tavan {@link WandLockManager#CPS_CAP} formülde uygulanır.
     */
    public int cps(UUID id, long gameTime) {
        ArrayDeque<Long> d = id.equals(a) ? clicksA : clicksB;
        while (!d.isEmpty() && d.peekFirst() < gameTime - 20) {
            d.removeFirst();
        }
        return d.size();
    }
}
