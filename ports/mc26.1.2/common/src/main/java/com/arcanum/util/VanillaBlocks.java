package com.arcanum.util;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Renkli vanilla blokları (yün, halı, boyalı pişmiş kil, mum) ve zincir için
 * TEK erişim noktası (26.2 ileri-uyum kuralı §1-8).
 *
 * <p>26.1.2'de {@code Blocks.BLACK_WOOL} gibi renk-başına alanlar duruyor; 26.2'de
 * bunlar {@code ColorCollection<Block>}'a taşındı ({@code Blocks.WOOL.pick(color)},
 * {@code Blocks.CARPET}, {@code Blocks.DYED_TERRACOTTA}, {@code Blocks.DYED_CANDLE}).
 * Yapı kodu ({@code HogsmeadePiece}, {@code DeathEaterCampFeature} vb.) renkli bloğu
 * YALNIZ bu sınıftan alır → 26.2 portunda yalnız bu dosya değişir.</p>
 *
 * <p>{@code Blocks.CHAIN} 26.x'te {@code Blocks.IRON_CHAIN} oldu (bakır zincir
 * varyantları eklendi); {@link #ironChain()} bunu sarar.</p>
 */
public final class VanillaBlocks {

    private VanillaBlocks() {
    }

    /** Renkli yün ({@code <renk>_wool}). */
    public static Block wool(DyeColor color) {
        return switch (color) {
            case WHITE -> Blocks.WHITE_WOOL;
            case ORANGE -> Blocks.ORANGE_WOOL;
            case MAGENTA -> Blocks.MAGENTA_WOOL;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_WOOL;
            case YELLOW -> Blocks.YELLOW_WOOL;
            case LIME -> Blocks.LIME_WOOL;
            case PINK -> Blocks.PINK_WOOL;
            case GRAY -> Blocks.GRAY_WOOL;
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_WOOL;
            case CYAN -> Blocks.CYAN_WOOL;
            case PURPLE -> Blocks.PURPLE_WOOL;
            case BLUE -> Blocks.BLUE_WOOL;
            case BROWN -> Blocks.BROWN_WOOL;
            case GREEN -> Blocks.GREEN_WOOL;
            case RED -> Blocks.RED_WOOL;
            case BLACK -> Blocks.BLACK_WOOL;
        };
    }

    /** Renkli halı ({@code <renk>_carpet}). */
    public static Block carpet(DyeColor color) {
        return switch (color) {
            case WHITE -> Blocks.WHITE_CARPET;
            case ORANGE -> Blocks.ORANGE_CARPET;
            case MAGENTA -> Blocks.MAGENTA_CARPET;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_CARPET;
            case YELLOW -> Blocks.YELLOW_CARPET;
            case LIME -> Blocks.LIME_CARPET;
            case PINK -> Blocks.PINK_CARPET;
            case GRAY -> Blocks.GRAY_CARPET;
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_CARPET;
            case CYAN -> Blocks.CYAN_CARPET;
            case PURPLE -> Blocks.PURPLE_CARPET;
            case BLUE -> Blocks.BLUE_CARPET;
            case BROWN -> Blocks.BROWN_CARPET;
            case GREEN -> Blocks.GREEN_CARPET;
            case RED -> Blocks.RED_CARPET;
            case BLACK -> Blocks.BLACK_CARPET;
        };
    }

    /** Boyalı pişmiş kil ({@code <renk>_terracotta}; düz {@code Blocks.TERRACOTTA} DEĞİL). */
    public static Block terracotta(DyeColor color) {
        return switch (color) {
            case WHITE -> Blocks.WHITE_TERRACOTTA;
            case ORANGE -> Blocks.ORANGE_TERRACOTTA;
            case MAGENTA -> Blocks.MAGENTA_TERRACOTTA;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_TERRACOTTA;
            case YELLOW -> Blocks.YELLOW_TERRACOTTA;
            case LIME -> Blocks.LIME_TERRACOTTA;
            case PINK -> Blocks.PINK_TERRACOTTA;
            case GRAY -> Blocks.GRAY_TERRACOTTA;
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_TERRACOTTA;
            case CYAN -> Blocks.CYAN_TERRACOTTA;
            case PURPLE -> Blocks.PURPLE_TERRACOTTA;
            case BLUE -> Blocks.BLUE_TERRACOTTA;
            case BROWN -> Blocks.BROWN_TERRACOTTA;
            case GREEN -> Blocks.GREEN_TERRACOTTA;
            case RED -> Blocks.RED_TERRACOTTA;
            case BLACK -> Blocks.BLACK_TERRACOTTA;
        };
    }

    /** Renkli mum ({@code <renk>_candle}; düz {@code Blocks.CANDLE} DEĞİL). */
    public static Block candle(DyeColor color) {
        return switch (color) {
            case WHITE -> Blocks.WHITE_CANDLE;
            case ORANGE -> Blocks.ORANGE_CANDLE;
            case MAGENTA -> Blocks.MAGENTA_CANDLE;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_CANDLE;
            case YELLOW -> Blocks.YELLOW_CANDLE;
            case LIME -> Blocks.LIME_CANDLE;
            case PINK -> Blocks.PINK_CANDLE;
            case GRAY -> Blocks.GRAY_CANDLE;
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_CANDLE;
            case CYAN -> Blocks.CYAN_CANDLE;
            case PURPLE -> Blocks.PURPLE_CANDLE;
            case BLUE -> Blocks.BLUE_CANDLE;
            case BROWN -> Blocks.BROWN_CANDLE;
            case GREEN -> Blocks.GREEN_CANDLE;
            case RED -> Blocks.RED_CANDLE;
            case BLACK -> Blocks.BLACK_CANDLE;
        };
    }

    /** Demir zincir (1.21.1'deki {@code Blocks.CHAIN}). */
    public static Block ironChain() {
        return Blocks.IRON_CHAIN;
    }
}
