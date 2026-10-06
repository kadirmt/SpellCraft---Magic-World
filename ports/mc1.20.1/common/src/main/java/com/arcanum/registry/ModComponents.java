package com.arcanum.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * 1.20.1 FACADE — DataComponent yerine ItemStack NBT (CompoundTag).
 *
 * 1.21.1'deki 3 DataComponent (SELECTED_SPELL:int, LUMOS_ACTIVE:bool, SPELL_ID:String)
 * burada NBT anahtarlarına eşlenir. Kayıt YOKTUR; {@link #init()} geriye-uyum için
 * no-op bırakıldı (Arcanum.init çağrı sırası değişmesin).
 *
 * Ağ senkronu: 1.20.1'de ItemStack tag'i stack ile otomatik senkronlanır — Lumos
 * parıltısının diğer oyuncularda görünmesi (ArcanumFabricClient.hasLumos) yine çalışır.
 *
 * ÖNEMLİ: get/is sorguları stack'i ASLA mutasyona uğratmaz ({@code getTag()} null-check;
 * {@code getOrCreateTag()} yalnızca set'lerde) — aksi halde istemcide "item değişti"
 * animasyonları tetiklenir (bkz. research-api-diff-1.20.1.md §2).
 */
public final class ModComponents {

    /** Asada seçili büyünün ModSpells.SPELLS içindeki index'i. */
    public static final String KEY_SELECTED_SPELL = "arcanum_selected_spell";
    /** Lumos açık mı — açıksa asa elde tutulurken çevreye ışık yayar. */
    public static final String KEY_LUMOS_ACTIVE = "arcanum_lumos_active";
    /** Büyü kitabının öğrettiği büyü id'si (ModSpells id'lerinden biri). */
    public static final String KEY_SPELL_ID = "arcanum_spell_id";

    private ModComponents() {}

    /** No-op — 1.20.1'de kayıt yok; Arcanum.init'teki çağrı derlenmeye devam etsin diye durur. */
    public static void init() {}

    // ===================== SELECTED_SPELL (int) =====================

    /** 1.21.1 karşılığı: {@code wand.set(SELECTED_SPELL.get(), index)}. */
    public static void setSelectedSpell(ItemStack stack, int index) {
        stack.getOrCreateTag().putInt(KEY_SELECTED_SPELL, index);
    }

    /** 1.21.1 karşılığı: {@code wand.getOrDefault(SELECTED_SPELL.get(), def)} — mutasyonsuz. */
    public static int getSelectedSpell(ItemStack stack, int def) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(KEY_SELECTED_SPELL, Tag.TAG_INT)
                ? tag.getInt(KEY_SELECTED_SPELL) : def;
    }

    /** 1.21.1 karşılığı: {@code wand.remove(SELECTED_SPELL.get())}. */
    public static void removeSelectedSpell(ItemStack stack) {
        stack.removeTagKey(KEY_SELECTED_SPELL);
    }

    // ===================== LUMOS_ACTIVE (boolean) =====================

    /** 1.21.1 karşılığı: {@code wand.set(LUMOS_ACTIVE.get(), active)}. */
    public static void setLumosActive(ItemStack stack, boolean active) {
        stack.getOrCreateTag().putBoolean(KEY_LUMOS_ACTIVE, active);
    }

    /** 1.21.1 karşılığı: {@code s.getOrDefault(LUMOS_ACTIVE.get(), false)} — mutasyonsuz. */
    public static boolean isLumosActive(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(KEY_LUMOS_ACTIVE);
    }

    /** 1.21.1 karşılığı: {@code wand.remove(LUMOS_ACTIVE.get())}. */
    public static void removeLumosActive(ItemStack stack) {
        stack.removeTagKey(KEY_LUMOS_ACTIVE);
    }

    // ===================== SPELL_ID (String) =====================

    /** 1.21.1 karşılığı: {@code book.set(SPELL_ID.get(), id)}. */
    public static void setSpellId(ItemStack stack, String spellId) {
        stack.getOrCreateTag().putString(KEY_SPELL_ID, spellId);
    }

    /** 1.21.1 karşılığı: {@code book.get(SPELL_ID.get())} — yoksa NULL döner, mutasyonsuz. */
    public static String getSpellId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(KEY_SPELL_ID, Tag.TAG_STRING)
                ? tag.getString(KEY_SPELL_ID) : null;
    }

    /** 1.21.1 karşılığı: {@code book.getOrDefault(SPELL_ID.get(), def)} — mutasyonsuz. */
    public static String getSpellId(ItemStack stack, String def) {
        String id = getSpellId(stack);
        return id != null ? id : def;
    }

    /** 1.21.1 karşılığı: {@code book.has(SPELL_ID.get())}. */
    public static boolean hasSpellId(ItemStack stack) {
        return getSpellId(stack) != null;
    }

    /** 1.21.1 karşılığı: {@code book.remove(SPELL_ID.get())}. */
    public static void removeSpellId(ItemStack stack) {
        stack.removeTagKey(KEY_SPELL_ID);
    }
}
