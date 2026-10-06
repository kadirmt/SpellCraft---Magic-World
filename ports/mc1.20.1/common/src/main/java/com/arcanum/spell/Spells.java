package com.arcanum.spell;

import java.util.Comparator;
import java.util.List;

import com.arcanum.entity.DementorEntity;
import com.arcanum.item.WandItem;
import com.arcanum.registry.ModComponents;
import com.arcanum.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Harry Potter büyülerinin Minecraft uygulamaları — oyun etkisi + tam VFX koreografisi.
 * Görsel dil: her büyünün tema rengi (ModSpells) ışın/patlama/rün efektlerine işlenir.
 * Hasar, KAST EDEN asanın kademesiyle (wand parametresi) ölçeklenir — mainhand'e
 * bakılmaz; offhand asayla kast mainhand asanın gücünü çalamaz.
 */
public final class Spells {
    private Spells() {}

    /**
     * "Bu kast bir şeye isabet etti mi?" bayrağı. WandItem her kast ÖNCESİNDE
     * false'a çeker; büyü metodu gerçekten bir hedefe/etkiye vurunca
     * {@link #markHit()} ile true yapılır. İsabet etmeyen kastlarda cooldown
     * yarıya iner (bkz. WandItem.castSelected). Tek oyuncu-thread'inde
     * çalıştığı için (sunucu ana tick'i) statik alan güvenli.
     */
    public static boolean castHitSomething;

    /** Büyü bir hedefe/etkiye vurduğunda çağrılır — cooldown tam kalır. */
    public static void markHit() {
        castHitSomething = true;
    }

    /**
     * ASA KENETLENMESİ SONUCU — kazananın büyüsünü kaybedene doğrudan uygular
     * ({@link com.arcanum.spell.WandLockManager} çağırır). Normal büyü etkileri hedefi
     * kendi ray'iyle bulduğu için retarget edilemez; bu yüzden kazanan büyünün çekirdek
     * etkisi burada {@code loser} üzerinde yeniden uygulanır. AK → ölüm; Expelliarmus →
     * asa uçar; diğer saldırı büyüleri → ölçekli hasar + imza debuff/geri itiş.
     */
    public static void applyClashLoss(ServerLevel level, Player winner, Player loser,
                                      Spell s, ItemStack winnerWand) {
        float pow = (winnerWand != null && winnerWand.getItem() instanceof WandItem w)
                ? w.tier().powerMult() : 1.0f;
        switch (s.id()) {
            case "avada_kedavra" ->
                // ÖLÜM: hasar tavanını aşan tek atış (PvP hedef → undead kontrolü gereksiz).
                loser.hurt(magic(level, winner), 2000.0f);
            case "expelliarmus" -> {
                // Asayı hangi elde tutuyorsa O eli silahsızlandır (offhand dahil) — kenetlenme
                // el-agnostik olduğundan main/off farketmeksizin GERÇEK asa uçmalı, kılıç değil.
                for (net.minecraft.world.InteractionHand h : net.minecraft.world.InteractionHand.values()) {
                    ItemStack held = loser.getItemInHand(h);
                    if (held.getItem() instanceof WandItem) {
                        EquipmentSlot slot = h == net.minecraft.world.InteractionHand.MAIN_HAND
                                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                        loser.setItemSlot(slot, ItemStack.EMPTY);
                        ItemEntity drop = new ItemEntity(level, loser.getX(), loser.getEyeY(), loser.getZ(), held.copy());
                        Vec3 toWinner = winner.position().subtract(loser.position()).normalize().scale(0.7);
                        drop.setDeltaMovement(toWinner.x, 0.3, toWinner.z);
                        drop.setPickUpDelay(15);
                        level.addFreshEntity(drop);
                        break;
                    }
                }
                loser.hurt(magic(level, winner), 4.0f);
                loser.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            }
            default -> {
                loser.hurt(magic(level, winner), 6.9f * pow); // 6.0 × 1.15 — global hasar buff'ıyla tutarlı
                switch (s.id()) {
                    case "crucio" ->
                        // Kenetlenmeyi Crucio ile kazanmak da YENİ kalp-bazlı laneti uygular
                        // (zehir DEĞİL) — ray yolu ile tutarlı.
                        CrucioTracker.afflict(level, loser, winner);
                    case "petrificus_totalus" ->
                        loser.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 250));
                    case "incendio" -> loser.setRemainingFireTicks(120);
                    case "bombarda", "bombarda_maxima" -> {
                        // Kenetlenmeyi bombarda ile kazanmak → kaybedende KONTROLLÜ patlama
                        // hasarı (zırh + Patlama Koruması azaltır) + geri itiş. Üstteki 6.0
                        // hasar zaten uygulandı; blok kırma YOK (grief önlenir).
                        float extra = s.id().equals("bombarda_maxima") ? 10.0f : 5.0f;
                        loser.hurt(level.damageSources().explosion(winner, winner), extra);
                        Vec3 kb = loser.position().subtract(winner.position()).normalize().scale(1.1);
                        loser.push(kb.x, 0.4, kb.z);
                    }
                    case "stupefy", "flipendo", "depulso", "everte_statum" -> {
                        Vec3 kb = loser.position().subtract(winner.position()).normalize().scale(1.4);
                        loser.push(kb.x, 0.42, kb.z);
                        loser.hurtMarked = true;
                    }
                    default -> loser.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                }
            }
        }
    }

    // ===================== OFFENSIVE JINXES & HEXES =====================

    /** Expelliarmus — silahsızlandırma: eldeki eşya büyücüye savrulur + geri itiş. */
    public static void expelliarmus(ServerLevel level, Player player, ItemStack wand) {
        int color = ModSpells.EXPELLIARMUS.color();
        RayResult r = ray(level, player, 16.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.beam(level, player, r.impact(), color);
        if (r.target() != null) {
            markHit();
            LivingEntity t = r.target();
            ItemStack held = t.getMainHandItem();
            if (!held.isEmpty()) {
                t.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                ItemEntity drop = new ItemEntity(level, t.getX(), t.getEyeY(), t.getZ(), held.copy());
                Vec3 toCaster = player.position().subtract(t.position()).normalize().scale(0.7);
                drop.setDeltaMovement(toCaster.x, 0.3, toCaster.z);
                drop.setPickUpDelay(15);
                level.addFreshEntity(drop);
                // savrulan eşyanın izlediği yay
                SpellFx.line(level, t.getEyePosition(), player.position().add(0, 1.0, 0),
                        SpellFx.trail(SpellFx.lighten(color, 0.3f)), 0.5);
            }
            t.hurt(magic(level, player), 3.92f * power(player, wand));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            Vec3 kb = t.position().subtract(player.position()).normalize().scale(0.5);
            t.push(kb.x, 0.1, kb.z);
            SpellFx.burst(level, r.impact(), color, 14, 0.25, 0.10);
        } else {
            SpellFx.burst(level, r.impact(), color, 8, 0.18, 0.06);
        }
        SpellFx.soundAt(level, player, SoundEvents.TRIDENT_RETURN, 0.9f, SpellFx.vary(level, 1.3f));
        SpellFx.sound(level, r.impact(), SoundEvents.AMETHYST_BLOCK_HIT, 0.8f, SpellFx.vary(level, 0.7f));
    }

    /** Stupefy — sersemletme: kızıl şimşek, ağır yavaşlatma, düştüğü yerde kalır. */
    public static void stupefy(ServerLevel level, Player player, ItemStack wand) {
        int color = ModSpells.STUPEFY.color();
        RayResult r = ray(level, player, 18.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.beam(level, player, r.impact(), color);
        if (r.target() != null) {
            markHit();
            LivingEntity t = r.target();
            t.hurt(magic(level, player), 4.2f * power(player, wand));
            // Stupefy = SERSEMLETME: kısa (~1 sn) ağır yavaşlık → yerinde çakılıp kalır.
            // Tester "sadece 1 sn'lik bir stun" istedi; amp 6 pratikte hareketi keser.
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 6));
            SpellFx.shroud(level, t, color, 10);
        }
        SpellFx.burst(level, r.impact(), color, 18, 0.30, 0.14);
        SpellFx.nova(level, r.impact(), SpellFx.lighten(color, 0.3f), 14, 0.22);
        SpellFx.soundAt(level, player, SoundEvents.FIRECHARGE_USE, 0.7f, SpellFx.vary(level, 1.5f));
        SpellFx.sound(level, r.impact(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, SpellFx.vary(level, 1.7f));
    }

    /** Petrificus Totalus — tam vücut bağlama: mavi bağ + rün mührü, saf kontrol. */
    public static void petrificusTotalus(ServerLevel level, Player player, ItemStack wand) {
        int color = ModSpells.PETRIFICUS.color();
        RayResult r = ray(level, player, 18.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.beam(level, player, r.impact(), color);
        if (r.target() != null) {
            markHit();
            LivingEntity t = r.target();
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur(100, player, wand), 9));
            t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, dur(100, player, wand), 2));
            t.addEffect(new MobEffectInstance(MobEffects.GLOWING, dur(100, player, wand), 0));
            // bağlanma: gövde etrafında kapanan halkalar + zeminde mühür
            SpellFx.shroud(level, t, color, 16);
            SpellFx.bodyRunes(level, t, color, 5);
            SpellFx.nova(level, t.position(), SpellFx.lighten(color, 0.4f), 12, -0.15); // içe kapanan halka
        } else {
            SpellFx.burst(level, r.impact(), color, 8, 0.18, 0.05);
        }
        SpellFx.soundAt(level, player, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, SpellFx.vary(level, 1.2f));
        SpellFx.sound(level, r.impact(), SoundEvents.CHAIN_PLACE, 0.9f, SpellFx.vary(level, 0.6f));
    }

    // ===================== ELEMENTAL & NATURE =====================

    /** Incendio — alev: tutuşturur, yanıcı yüzeyi ateşler; çarpmada alev halkası. */
    public static void incendio(ServerLevel level, Player player, ItemStack wand) {
        markHit();
        int color = ModSpells.INCENDIO.color();
        RayResult r = ray(level, player, 12.0);
        SpellFx.muzzle(level, player, color);
        // alev ışını: renkli iz + gerçek alev karışımı
        SpellFx.beam(level, player, r.impact(), color);
        SpellFx.line(level, SpellFx.wandTip(player), r.impact(), ParticleTypes.FLAME, 0.5);
        if (r.target() != null) {
            r.target().setRemainingFireTicks(100);
            r.target().hurt(magic(level, player), 7.84f * power(player, wand));
        }
        if (r.block().getType() == HitResult.Type.BLOCK) {
            BlockPos fp = r.block().getBlockPos().relative(r.block().getDirection());
            if (level.getBlockState(fp).isAir()) {
                level.setBlockAndUpdate(fp, BaseFireBlock.getState(level, fp));
            }
        }
        // çarpma: alev fıskiyesi + genişleyen kor halkası
        level.sendParticles(ParticleTypes.FLAME, r.impact().x, r.impact().y, r.impact().z, 20, 0.25, 0.25, 0.25, 0.06);
        level.sendParticles(ParticleTypes.LAVA, r.impact().x, r.impact().y, r.impact().z, 4, 0.2, 0.2, 0.2, 0.0);
        SpellFx.nova(level, r.impact(), color, 14, 0.25);
        SpellFx.soundAt(level, player, SoundEvents.FIRECHARGE_USE, 0.8f, SpellFx.vary(level, 0.9f));
        SpellFx.sound(level, r.impact(), SoundEvents.FIRE_AMBIENT, 1.2f, SpellFx.vary(level, 1.0f));
    }

    /** Glacius — buz: yavaşlatır, ateş söndürür, kaynak su→buz, kaynak lav→obsidyen (akış→taş). */
    public static void glacius(ServerLevel level, Player player, ItemStack wand) {
        int color = ModSpells.GLACIUS.color();
        RayResult r = ray(level, player, 12.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.beam(level, player, r.impact(), color);
        SpellFx.line(level, SpellFx.wandTip(player), r.impact(), ParticleTypes.SNOWFLAKE, 0.4);
        if (r.target() != null) {
            markHit();
            LivingEntity t = r.target();
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur(100, player, wand), 2));
            t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, dur(60, player, wand), 0));
            t.setRemainingFireTicks(0);
            t.hurt(magic(level, player), 3.92f * power(player, wand));
            SpellFx.shroud(level, t, SpellFx.lighten(color, 0.3f), 12);
        }
        // SU YÜZEYİ DONDURMA (tester): ray() sıvıyı görmez (Fluid.NONE) → ışın suyun
        // içinden geçip DİBİ donduruyordu. Suya nişan alınınca ışın YÜZEYDE durur ve
        // yüzey katmanını (üstü su olmayan kaynak bloklar, 5×5) buza çevirir.
        boolean surfaceFroze = false;
        BlockHitResult fluidHit = level.clip(new ClipContext(player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(
                        12.0 * RANGE_MULT * com.arcanum.config.ArcanumConfig.get().spellRangeMult)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        if (fluidHit.getType() == HitResult.Type.BLOCK
                && level.getFluidState(fluidHit.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER)
                && player.getEyePosition().distanceToSqr(fluidHit.getLocation())
                        <= player.getEyePosition().distanceToSqr(r.impact()) + 1.0e-3) {
            BlockPos fc = fluidHit.getBlockPos();
            int frozen = 0;
            for (BlockPos p : BlockPos.betweenClosed(fc.offset(-2, -1, -2), fc.offset(2, 1, 2))) {
                var st = level.getBlockState(p);
                if (st.is(Blocks.WATER) && st.getFluidState().isSource()
                        && !level.getFluidState(p.above()).is(net.minecraft.tags.FluidTags.WATER)) {
                    level.setBlockAndUpdate(p.immutable(), Blocks.ICE.defaultBlockState());
                    frozen++;
                }
            }
            if (frozen > 0) {
                surfaceFroze = true;
                markHit();
                Vec3 fp = fluidHit.getLocation();
                level.sendParticles(ParticleTypes.SNOWFLAKE, fp.x, fp.y + 0.2, fp.z, 20, 1.2, 0.2, 1.2, 0.03);
            }
        }
        // blok etkileri: 3x3 — yalnız KAYNAK su buza döner (akan su buz→sonsuz kaynak
        // çoğaltmasına açılırdı); kaynak lav obsidyen, akan lav vanilla paritesiyle taş
        if (!surfaceFroze && r.block().getType() == HitResult.Type.BLOCK) {
            BlockPos c = r.block().getBlockPos();
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 1, 1))) {
                var st = level.getBlockState(p);
                FluidState fluid = st.getFluidState();
                if (st.is(Blocks.WATER)) {
                    if (fluid.isSource()) {
                        level.setBlockAndUpdate(p, Blocks.ICE.defaultBlockState());
                    }
                } else if (st.is(Blocks.LAVA)) {
                    level.setBlockAndUpdate(p, fluid.isSource()
                            ? Blocks.OBSIDIAN.defaultBlockState()
                            : Blocks.COBBLESTONE.defaultBlockState());
                } else if (st.is(Blocks.FIRE)) {
                    level.removeBlock(p, false);
                }
            }
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, r.impact().x, r.impact().y, r.impact().z, 24, 0.35, 0.35, 0.35, 0.04);
        SpellFx.nova(level, r.impact(), SpellFx.lighten(color, 0.4f), 14, 0.2);
        SpellFx.soundAt(level, player, SoundEvents.PLAYER_HURT_FREEZE, 0.8f, SpellFx.vary(level, 1.4f));
        SpellFx.sound(level, r.impact(), SoundEvents.GLASS_BREAK, 0.9f, SpellFx.vary(level, 1.5f));
    }

    // ===================== CHARMS (utility & defense) =====================

    /** Wingardium Leviosa — havalandırma: hedef süzülür, altında rün desteği. */
    public static void wingardiumLeviosa(ServerLevel level, Player player, ItemStack wand) {
        int color = ModSpells.WINGARDIUM.color();
        RayResult r = ray(level, player, 12.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.beam(level, player, r.impact(), color);
        if (r.target() != null) {
            markHit();
            LivingEntity t = r.target();
            t.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 120, 0));
            t.resetFallDistance();
            SpellFx.bodyRunes(level, t, color, 5);
            // yükselen taşıma ışıması
            for (int i = 0; i < 8; i++) {
                level.sendParticles(SpellFx.glow(color),
                        t.getX() + (level.random.nextDouble() - 0.5) * t.getBbWidth(),
                        t.getY() + level.random.nextDouble() * 0.3,
                        t.getZ() + (level.random.nextDouble() - 0.5) * t.getBbWidth(),
                        0, 0.0, 1.0, 0.0, 0.08);
            }
        }
        level.sendParticles(ParticleTypes.ENCHANT, r.impact().x, r.impact().y, r.impact().z, 14, 0.3, 0.3, 0.3, 0.4);
        SpellFx.soundAt(level, player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.1f, SpellFx.vary(level, 1.5f));
    }

    /** Lumos / Nox — asa ucu ışığı (aç/kapa). Işık bloğu oyuncuyu takip eder. */
    public static void lumos(ServerLevel level, Player player, ItemStack wand) {
        markHit();
        int color = ModSpells.LUMOS.color();
        boolean now = !ModComponents.isLumosActive(wand); // 1.20.1: NBT facade (mutasyonsuz okuma)
        ModComponents.setLumosActive(wand, now);
        player.displayClientMessage(
                Component.translatable(now ? "arcanum.lumos_on" : "arcanum.lumos_off")
                        .withStyle(ChatFormatting.YELLOW), true);
        Vec3 tip = SpellFx.wandTip(player);
        if (now) {
            SpellFx.burst(level, tip, color, 10, 0.12, 0.03);
            SpellFx.nova(level, tip, SpellFx.lighten(color, 0.5f), 10, 0.12);
            SpellFx.soundAt(level, player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.8f);
        } else {
            level.sendParticles(ParticleTypes.SMOKE, tip.x, tip.y, tip.z, 6, 0.05, 0.05, 0.05, 0.01);
            SpellFx.soundAt(level, player, SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.4f);
        }
    }

    /** Protego — kalkan: direnç + soğurma; rün kubbesi görünümü. */
    public static void protego(ServerLevel level, Player player, ItemStack wand) {
        markHit();
        int color = ModSpells.PROTEGO.color();
        // 15 saniye (300 tick) koruma — kullanıcı isteği
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 2));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 300, 1));
        Vec3 c = player.position();
        SpellFx.dome(level, c, color, 1.7);
        SpellFx.runeRing(level, c, SpellFx.lighten(color, 0.3f), 8, 1.9);
        SpellFx.nova(level, c, color, 16, 0.15);
        SpellFx.soundAt(level, player, SoundEvents.SHIELD_BLOCK, 1.0f, SpellFx.vary(level, 1.2f));
        SpellFx.soundAt(level, player, SoundEvents.BEACON_ACTIVATE, 0.5f, SpellFx.vary(level, 1.6f));
    }

    /** Accio — çağırma: eşyalar sana akar (en yakın 8'i iz çizer), baktığın yaratık çekilir. */
    public static void accio(ServerLevel level, Player player, ItemStack wand) {
        markHit();
        int color = ModSpells.ACCIO.color();
        SpellFx.muzzle(level, player, color);
        // Accio saf yarar büyüsü — HASAR buff'ı (DAMAGE_MULT) menzil/çekişe sızmasın.
        double pw = power(player, wand) / DAMAGE_MULT;
        // GÜÇLENDİRİLDİ (tester "accio çok zayıf"): eskiden yalnız baktığın yönde ~16 blok
        // ve sabit 0.6'lık cılız çekiş vardı — uzaktaki item'lar ele hiç ulaşmıyordu. Artık
        // oyuncunun ETRAFINDA geniş bir küre + uzaklıkla HIZLANAN çekiş (uzaktakiler de
        // birkaç tick'te ele gelir) + asa gücüne göre menzil/hız.
        double range = 20.0 + 6.0 * (pw - 1.0);   // ~20 blok (elder asa ile daha da geniş)
        Vec3 to = player.position().add(0, 1.0, 0); // göğüs/el hizasına çek
        AABB box = player.getBoundingBox().inflate(range);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box);
        // partikül bütçesi: yüzlerce item'lık yığınlarda paket seli olmasın —
        // hepsi çekilir ama yalnız en yakın 8'i iz çizer
        items.sort(Comparator.comparingDouble(ie -> ie.distanceToSqr(player)));
        int trails = 0;
        for (ItemEntity ie : items) {
            Vec3 delta = to.subtract(ie.position());
            double dist = delta.length();
            ie.setPickUpDelay(0);
            if (dist < 1.4) {
                ie.setDeltaMovement(delta.scale(0.5)); // yeterince yakın: ele düş
                continue;
            }
            // Hız DÜZ 2.2 ile sınırlı (güçle ölçeklenmez): item'lar tek tick'te ~1.4 blokluk
            // pickup kutusunu aşıp arkaya tünellemesin (adversarial review: güçlü asada overshoot).
            double speed = Math.min(2.2, 0.8 + dist * 0.16);
            Vec3 v = delta.normalize().scale(speed);
            ie.setDeltaMovement(v.x, v.y + 0.15, v.z);
            if (trails++ < 8) {
                SpellFx.line(level, ie.position(), to, SpellFx.trail(color), 0.6);
            }
        }
        // Hedef varlığı da GÜÇLÜCE çek (Accio broom / yaratığı kendine yak) — hurtMarked
        // ile istemci tahminini ez ki çekiş gerçekten görünsün.
        RayResult r = ray(level, player, 20.0);
        if (r.target() != null && !(r.target() instanceof Player)) {
            // Varlık çekişi 1.8 ile sınırlı: Exstimulo+Elder'da 5+ blok/tick olup mob'u
            // oyuncuyu geçirip uçurmasın; menzile otursun (adversarial review).
            Vec3 v = player.position().subtract(r.target().position()).normalize().scale(Math.min(1.8, 1.4 * pw));
            r.target().push(v.x, 0.35, v.z);
            r.target().hurtMarked = true;
            SpellFx.shroud(level, r.target(), color, 8);
        }
        SpellFx.burst(level, to, color, 10, 0.4, 0.02);
        SpellFx.soundAt(level, player, SoundEvents.ENDERMAN_TELEPORT, 0.4f, SpellFx.vary(level, 1.8f));
        SpellFx.soundAt(level, player, SoundEvents.ITEM_PICKUP, 0.8f, SpellFx.vary(level, 1.0f));
    }

    // ===================== CURSES & DESTRUCTIVE =====================

    /** Reducto — parçalama: küçük küre hacmi toza çevirir + güçlü itiş. */
    public static void reducto(ServerLevel level, Player player, ItemStack wand) {
        markHit();
        int color = ModSpells.REDUCTO.color();
        RayResult r = ray(level, player, 24.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.beam(level, player, r.impact(), color);
        if (r.target() != null) {
            r.target().hurt(magic(level, player), 11.76f * power(player, wand));
            Vec3 kb = r.target().position().subtract(player.position()).normalize().scale(0.6);
            r.target().push(kb.x, 0.2, kb.z);
        }
        BlockPos center = BlockPos.containing(r.impact());
        int rad = 2;
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dy = -rad; dy <= rad; dy++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dy * dy + dz * dz > rad * rad) continue;
                    BlockPos p = center.offset(dx, dy, dz);
                    var st = level.getBlockState(p);
                    if (!st.isAir() && st.getBlock().getExplosionResistance() < 100.0f
                            && st.getDestroySpeed(level, p) >= 0) {
                        level.destroyBlock(p, false);
                    }
                }
            }
        }
        SpellFx.burst(level, r.impact(), color, 26, 0.6, 0.22);
        SpellFx.nova(level, r.impact(), SpellFx.lighten(color, 0.3f), 18, 0.35);
        level.sendParticles(ParticleTypes.POOF, r.impact().x, r.impact().y, r.impact().z, 14, 0.5, 0.5, 0.5, 0.05);
        SpellFx.soundAt(level, player, SoundEvents.GENERIC_EXPLODE, 0.8f, SpellFx.vary(level, 1.4f));
    }

    /** Bombarda — patlatma: konkuzif patlama. Hasar MANUEL/kontrollü (vanilla explode'un
     *  yakın mesafede zırhı yok sayan devasa hasarı yerine) — zırha saygılı, tavanlı, tek atmaz. */
    public static void bombarda(ServerLevel level, Player player, ItemStack wand) {
        markHit();
        int color = ModSpells.BOMBARDA.color();
        RayResult r = ray(level, player, 24.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.beam(level, player, r.impact(), color);
        // radius 3.0, baseDmg 16 (power ile ölçekli — varsayılan ~13 merkez; zırhsızı yaralar,
        // öldürmez; zırhlı çok daha az). krater 1.5 (küçük).
        blast(level, player, wand, r.impact(), 3.0, 16.0f, 1.5, color, 30, 0.8);
    }

    /** Bombarda Maxima — kara büyü: geniş yıkıcı patlama. Hasar MANUEL/kontrollü (zırh +
     *  Patlama Koruması azaltır) → full netherite'ı TEK ATMAZ ama zırhsıza ölümcül. */
    public static void bombardaMaxima(ServerLevel level, Player player, ItemStack wand) {
        markHit();
        int color = ModSpells.BOMBARDA_MAXIMA.color();
        RayResult r = ray(level, player, 28.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.beam(level, player, r.impact(), color);
        // radius 4.5, baseDmg 24 (power ile ölçekli — varsayılan ~19 merkez; zırhsız kıl payı
        // ölmez, full netherite hafif yara alır). krater 3.0 (büyük).
        blast(level, player, wand, r.impact(), 4.5, 24.0f, 3.0, color, 60, 1.6);
        SpellFx.soundAt(level, player, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, SpellFx.vary(level, 0.7f));
    }

    /**
     * Kontrollü patlama — Bombarda ailesi için. Vanilla {@code level.explode}'un yakın
     * mesafede ZIRHI YOK SAYAN devasa hasarı (power 7 → merkezde ~99) yerine MANUEL, zırha +
     * Patlama Koruması'na saygılı, mesafeyle DOĞRUSAL düşen, {@code power()} ile ölçekli hasar
     * + modest krater (yüksek dirençli bloklar korunur) + geri itiş + görsel.
     */
    private static void blast(ServerLevel level, Player player, ItemStack wand, Vec3 imp,
                              double radius, float baseDmg, double craterR, int color,
                              int burstCount, double burstSpread) {
        // görsel
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, imp.x, imp.y, imp.z, 1, 0, 0, 0, 0.0);
        SpellFx.burst(level, imp, color, burstCount, burstSpread, 0.4);
        SpellFx.nova(level, imp, color, 22, 0.55);
        SpellFx.soundAt(level, player, SoundEvents.GENERIC_EXPLODE, 1.0f, SpellFx.vary(level, 1.0f));

        // MANUEL hasar — patlama hasar kaynağı (zırh + Patlama Koruması azaltır) + power ölçeği
        float pow = power(player, wand);
        DamageSource src = level.damageSources().explosion(player, player);
        AABB box = new AABB(imp, imp).inflate(radius);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            double dist = Math.sqrt(t.distanceToSqr(imp.x, imp.y, imp.z));
            double falloff = Math.max(0.0, 1.0 - dist / radius);
            if (falloff <= 0.0) {
                continue;
            }
            float dmg = (float) (baseDmg * falloff) * pow;
            if (dmg < 0.5f) {
                continue;
            }
            t.hurt(src, dmg);
            Vec3 away = t.position().add(0, 0.1, 0).subtract(imp).normalize().scale(0.5 * falloff + 0.2);
            t.push(away.x, 0.35 * falloff + 0.15, away.z);
            t.hurtMarked = true;
        }

        // MODEST krater — taş/toprak/ağaç kırılır; obsidyen/bedrock/güçlendirilmiş korunur (grief yok)
        BlockPos c = BlockPos.containing(imp);
        int ri = (int) Math.ceil(craterR);
        double cr2 = craterR * craterR;
        for (BlockPos bp : BlockPos.betweenClosed(c.offset(-ri, -ri, -ri), c.offset(ri, ri, ri))) {
            if (c.distSqr(bp) > cr2) {
                continue;
            }
            var st = level.getBlockState(bp);
            if (st.isAir() || !st.getFluidState().isEmpty()) {
                continue;
            }
            if (st.getBlock().getExplosionResistance() >= 30f || st.getDestroySpeed(level, bp) < 0f) {
                continue; // yüksek dirençli / kırılamaz blokları koru
            }
            level.destroyBlock(bp, level.random.nextFloat() < 0.5f);
        }
    }

    /**
     * Protego Maxima — sağ tık BASILI TUTARAK kanallanan mavi kalkan (kanal mantığı
     * {@link com.arcanum.item.WandItem}). Bu metot HER kanal tick'inde çağrılır: kalkanı
     * canlı tutar ({@link ProtegoShield}) + periyodik mavi kubbe görseli. Kalkan, Affedilmez
     * 3 lanet (AK/Crucio/Imperio) HARİÇ tüm TEK-HEDEFLİ ışın saldırı büyülerini durdurur
     * (bkz. {@link #ray}). Patlama/AoE büyüleri (bombarda vb.) fiziksel olduğundan kalkan
     * onları DURDURMAZ. Mana onUseTick'te akar (30/sn). Cast time YOK.
     */
    public static void protegoMaxima(ServerLevel level, Player player, ItemStack wand) {
        ProtegoShield.refresh(player);
        int color = 0x4AA3E0; // mavi
        long gt = level.getGameTime();

        // İTİCİ KALKAN (tester isteği): kubbeye giren TÜM canlılar dışarı savrulur
        // (~5-6 blok halkasında tutulur) — yakına sokulan sert, kenardaki hafif itilir.
        // Kanal her tick çağrıldığından kubbe sürekli aktif bir itme alanıdır.
        Vec3 c = player.position().add(0, 0.9, 0);
        AABB dome = new AABB(c, c).inflate(5.5);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, dome,
                e -> e != player && e.isAlive())) {
            Vec3 away = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(c);
            double dist = away.length();
            if (dist > 5.5 || dist < 1.0e-3) {
                continue;
            }
            double strength = 0.35 + (1.0 - dist / 5.5) * 0.9;
            Vec3 push = away.scale(1.0 / dist).scale(strength);
            t.push(push.x, 0.15 + strength * 0.1, push.z);
            t.hurtMarked = true; // istemci tahminini ez — itiş görünür olsun
            if (gt % 4 == 0) {
                SpellFx.shroud(level, t, SpellFx.lighten(color, 0.3f), 4);
            }
        }
        // GELEN OKLAR/MERMİLER SEKER: kubbeye içeri doğru giren her mermi, kubbe
        // normali yönünde geri yansıtılır (sahibi kalkan sahibi değilse).
        for (net.minecraft.world.entity.projectile.Projectile proj
                : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class, dome,
                        p -> p.getOwner() != player)) {
            Vec3 away = proj.position().subtract(c);
            double dist = away.length();
            if (dist < 1.0e-3) {
                continue;
            }
            Vec3 v = proj.getDeltaMovement();
            if (v.dot(away) < 0) { // yalnız İÇERİ doğru gelenler
                Vec3 out = away.scale(1.0 / dist);
                double speed = Math.max(0.6, v.length() * 0.8);
                proj.setDeltaMovement(out.scale(speed).add(0, 0.1, 0));
                proj.hurtMarked = true;
                SpellFx.sound(level, proj.position(), SoundEvents.SHIELD_BLOCK, 0.6f, SpellFx.vary(level, 1.5f));
                SpellFx.burst(level, proj.position(), SpellFx.lighten(color, 0.4f), 6, 0.15, 0.05);
            }
        }

        if (gt % 4 == 0) {
            SpellFx.dome(level, player.position().add(0, 0.1, 0), color, 1.9);
        }
        if (gt % 8 == 0) {
            SpellFx.runeRing(level, player.position(), SpellFx.lighten(color, 0.3f), 10, 1.9);
        }
    }

    /**
     * Protego Diabolica — Grindelwald'ın MAVİ ATEŞ ÇEMBERİ (Fantastic Beasts). Kanal
     * boru hattı {@link #protegoMaxima} ile BİREBİR aynıdır (WandItem.use → startUsingItem,
     * onUseTick her tick burayı çağırır, mana 30/sn orada akar, cast time YOK) ve kalkan
     * da AYNIDIR ({@link ProtegoShield} — Affedilmez 3 lanet HARİÇ tek-hedefli ışın
     * büyülerini durdurur, bkz. {@link #ray}). FARKLAR:
     * <ul>
     *   <li>Çembere giren canlılar dışarı savrulurken TUTUŞUR (~4 sn) + küçük ateş hasarı
     *       (i-frame'ler doğal hız sınırı — pratikte ~2.0/0.5 sn).</li>
     *   <li>Çember bandının dış şeridinde (yarıçap 5.5 ± 1 blok) duran/geçen canlılar
     *       her saniye tutuşur + hafif ateş hasarı (1.0/sn).</li>
     *   <li>Görsel: mavi kubbe YERİNE yere yakın dönen YOĞUN mavi alev halkası.</li>
     *   <li>Görsel (SALT): halkadan yükselen MAVİ ALEV EJDERHASI — çemberin yarıçapında
     *       süzülerek döner, yakma bandındaki hedefe hızlanıp dalar. Hiçbir hasar/itme
     *       ondan GELMEZ; hepsi aşağıdaki döngülerdedir. Bkz. {@link DiabolicaDragonManager}
     *       ve {@link com.arcanum.entity.FiendfyreDragonEntity}.</li>
     * </ul>
     * Kapsam protegoMaxima itmesiyle AYNI: caster hariç TÜM canlılar (PvP'de diğer
     * oyuncular dahil). Kaster (kalkan sahibi) ASLA yanmaz/hasar almaz — döngüler onu dışlar.
     */
    public static void protegoDiabolica(ServerLevel level, Player player, ItemStack wand) {
        ProtegoShield.refresh(player);
        // GÖRSEL KATMAN (mekanik DEĞİL): çemberden yükselen mavi alev ejderhası.
        // İlk tick'te doğar, her tick canlı tutulur; bu çağrı kesilince (kanal/mana/ölüm/
        // boyut değişimi) ejderha kendi watchdog'uyla dağılır. Bkz. DiabolicaDragonManager.
        DiabolicaDragonManager.tick(level, player);
        int color = 0x4FA8FF; // mavi alev
        long gt = level.getGameTime();
        double radius = 5.5; // çember yarıçapı (protegoMaxima kubbesiyle aynı)

        // İTİCİ ÇEMBER (protegoMaxima itmesinin birebir kopyası) + FARK: savrulan TUTUŞUR.
        Vec3 c = player.position().add(0, 0.9, 0);
        AABB dome = new AABB(c, c).inflate(radius + 1.0); // +1: dış yakma bandı da tarama içinde
        DamageSource fire = level.damageSources().inFire();
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, dome,
                // EK (mekanik değişikliği DEĞİL): mavi alev ejderhası tezahürü LivingEntity
                // olduğu için bu taramaya düşüyordu. Salt görsel varlık itilmemeli/
                // yakılmamalı (kendi çemberi ya da yandaki bir casterınki fark etmez).
                e -> e != player && e.isAlive()
                        && !(e instanceof com.arcanum.entity.FiendfyreDragonEntity))) {
            Vec3 away = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(c);
            double dist = away.length();
            if (dist < 1.0e-3) {
                continue;
            }
            if (dist <= radius) {
                // içeri giren → dışarı savrulur (protegoMaxima ile aynı kuvvet eğrisi)
                double strength = 0.35 + (1.0 - dist / radius) * 0.9;
                Vec3 push = away.scale(1.0 / dist).scale(strength);
                t.push(push.x, 0.15 + strength * 0.1, push.z);
                t.hurtMarked = true; // istemci tahminini ez — itiş görünür olsun
                // FARK: savrulan mavi ateşle tutuşur (~4 sn) + küçük ateş hasarı
                if (t.getRemainingFireTicks() < 80) {
                    t.setRemainingFireTicks(80);
                }
                t.hurt(fire, 2.0f); // her tick çağrılsa da i-frame'ler ~0.5 sn'e sınırlar
                if (gt % 4 == 0) {
                    SpellFx.shroud(level, t, SpellFx.lighten(color, 0.3f), 4);
                }
            } else if (dist <= radius + 1.0 && gt % 20 == 0) {
                // YAKLAŞMA YAKMASI: bandın dış şeridinde (radius..radius+1) duran/geçen
                // canlı her saniye tutuşur + hafif hasar (kapsam itmeyle aynı).
                if (t.getRemainingFireTicks() < 80) {
                    t.setRemainingFireTicks(80);
                }
                t.hurt(fire, 1.0f);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
            }
        }
        // GELEN OKLAR/MERMİLER SEKER — protegoMaxima ile aynı (kalkan kiti korunur).
        for (net.minecraft.world.entity.projectile.Projectile proj
                : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class, dome,
                        p -> p.getOwner() != player)) {
            Vec3 away = proj.position().subtract(c);
            double dist = away.length();
            if (dist < 1.0e-3) {
                continue;
            }
            Vec3 v = proj.getDeltaMovement();
            if (v.dot(away) < 0) { // yalnız İÇERİ doğru gelenler
                Vec3 out = away.scale(1.0 / dist);
                double speed = Math.max(0.6, v.length() * 0.8);
                proj.setDeltaMovement(out.scale(speed).add(0, 0.1, 0));
                proj.hurtMarked = true;
                SpellFx.sound(level, proj.position(), SoundEvents.SHIELD_BLOCK, 0.6f, SpellFx.vary(level, 1.5f));
                SpellFx.burst(level, proj.position(), SpellFx.lighten(color, 0.4f), 6, 0.15, 0.05);
            }
        }

        // MAVİ ATEŞ ÇEMBERİ — kubbe YOK: yere yakın, gameTime ile hafifçe dönen ve
        // dalgalanan yoğun soul-fire halkası; alev dilleri 0.5-1.5 blok yukarı süzülür.
        if (gt % 2 == 0) {
            Vec3 base = player.position();
            int flames = 28;
            double spin = gt * 0.02; // halka yavaşça döner
            for (int i = 0; i < flames; i++) {
                double a = spin + (Math.PI * 2.0 * i) / flames;
                double wobble = Math.sin(gt * 0.15 + i * 1.7); // -1..1 dalgalanma
                double r = radius + wobble * 0.35;
                double x = base.x + Math.cos(a) * r;
                double z = base.z + Math.sin(a) * r;
                // ana alev dili — yukarı süzülen SOUL_FIRE_FLAME (mavi)
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, base.y + 0.1, z,
                        2, 0.12, 0.4 + 0.3 * Math.abs(wobble), 0.12, 0.02);
                // az miktarda normal alev karışımı (sıcak çekirdek hissi)
                if (i % 5 == 0) {
                    level.sendParticles(ParticleTypes.FLAME, x, base.y + 0.25, z,
                            1, 0.1, 0.25, 0.1, 0.01);
                }
            }
        }
        if (gt % 8 == 0) {
            SpellFx.runeRing(level, player.position(), SpellFx.lighten(color, 0.3f), 10, radius);
        }
        // Periyodik ateş çıtırtısı (SOUL_ESCAPE DEĞİL) — sabitler her iki MC sürümünde de var.
        if (gt % 20 == 0) {
            SpellFx.soundAt(level, player, SoundEvents.FIRE_AMBIENT, 0.9f, SpellFx.vary(level, 1.0f));
        } else if (gt % 20 == 10) {
            SpellFx.soundAt(level, player, SoundEvents.BLAZE_AMBIENT, 0.3f, SpellFx.vary(level, 1.5f));
        }
    }

    /** Sectumsempra — kara büyü: X biçimli kızıl kesikler + kanama. */
    public static void sectumsempra(ServerLevel level, Player player, ItemStack wand) {
        int color = ModSpells.SECTUMSEMPRA.color();
        RayResult r = ray(level, player, 16.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.beam(level, player, r.impact(), color);
        Vec3 imp = r.impact();
        if (r.target() != null) {
            markHit();
            LivingEntity t = r.target();
            t.hurt(magic(level, player), 13.72f * power(player, wand));
            t.addEffect(new MobEffectInstance(MobEffects.WITHER, 112, 1));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 0));
            imp = t.position().add(0, t.getBbHeight() * 0.6, 0);
        }
        // X biçimli çapraz kesik izleri
        double s = 0.8;
        SpellFx.line(level, imp.add(-s, s, -s * 0.3), imp.add(s, -s, s * 0.3), SpellFx.trail(color), 0.12);
        SpellFx.line(level, imp.add(s, s, s * 0.3), imp.add(-s, -s, -s * 0.3), SpellFx.trail(color), 0.12);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, imp.x, imp.y, imp.z, 10, 0.3, 0.3, 0.3, 0.15);
        SpellFx.soundAt(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, SpellFx.vary(level, 0.8f));
        SpellFx.sound(level, imp, SoundEvents.PLAYER_ATTACK_CRIT, 0.9f, SpellFx.vary(level, 0.7f));
    }

    /**
     * Umbravolo — kara büyü: kaster TAMAMEN kara dumana dönüşür ve uçar (Ölüm Yiyen
     * uçuşu). Tüm form mantığı {@link UmbraFormManager}'dadır: 21 mana/sn drenaj,
     * creative-üstü uçuş hızı, yoğun duman bulutu + kalıcı siyah iz, görünmezlik.
     * Bu metot yalnızca forma SOKAR; ÇIKIŞ asayla tekrar sağ tık ile ANINDA olur
     * (WandItem.use başındaki form kesmesi — cast time/cooldown kontrolüne girmez).
     * Giriş maliyeti (18 mana) + cooldown normal cast boru hattında düşer.
     */
    public static void umbravolo(ServerLevel level, Player player, ItemStack wand) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            markHit(); // form her zaman kurulur — "iskalama" yok, cooldown tam kalır
            UmbraFormManager.enter(level, sp);
        }
    }

    /** Crucio — Cruciatus laneti: titrek kızıl ark + kıvranma (öldürmeyen acı). */
    public static void crucio(ServerLevel level, Player player, ItemStack wand) {
        int color = ModSpells.CRUCIO.color();
        RayResult r = ray(level, player, 20.0, true); // Affedilmez lanet → Protego Maxima'yı DELER
        SpellFx.muzzle(level, player, color);
        SpellFx.jitterBeam(level, player, r.impact(), color);
        if (r.target() != null) {
            markHit();
            LivingEntity t = r.target();
            // Kalp-bazlı işkence: 10 sn boyunca Yavaşlık III + Madenci Yorgunluğu III + CRUCIO
            // işareti (oyuncu sağ/sol tıklayamaz, castleyemez) + kurbanın maks canının ~%90'ı
            // kadar TOPLAM hasar (~7 vuruşta, kalp SAYISINA orantılı). Zehir mantığı KALDIRILDI.
            CrucioTracker.afflict(level, t, player);
            SpellFx.shroud(level, t, color, 20);
            SpellFx.bodyRunes(level, t, SpellFx.darken(color, 0.3f), 5);
        }
        SpellFx.burst(level, r.impact(), color, 16, 0.3, 0.12);
        SpellFx.soundAt(level, player, SoundEvents.ELDER_GUARDIAN_CURSE, 0.5f, SpellFx.vary(level, 1.6f));
        SpellFx.sound(level, r.impact(), SoundEvents.PLAYER_HURT, 0.8f, SpellFx.vary(level, 0.8f));
        curseDread(level, player);
    }

    /**
     * Imperio — İtaat Laneti: hedefi geçici olarak boyun eğdirir. Yaratıkta: mevcut
     * saldırı hedefini keser + birkaç saniye "İtaat Laneti" bayrağı (fiili "hedef
     * seçmeyi engelle" davranışı fabric tarafında bir tick süpürmesiyle uygulanır,
     * bkz. ModMobEffects.IMPERIUS_CURSE javadoc'u) + hafif Zayıflık. Oyuncu hedefine
     * dokunulmaz (PvP kontrolü bu modun kapsamı dışında bırakıldı).
     */
    public static void imperio(ServerLevel level, Player player, ItemStack wand) {
        int color = ModSpells.IMPERIO.color();
        RayResult r = ray(level, player, 18.0, true); // Affedilmez → Protego Maxima'yı DELER
        SpellFx.muzzle(level, player, color);
        SpellFx.jitterBeam(level, player, r.impact(), color);
        // Artık oyuncu hedefleri de KABUL edilir: oyuncuya atılınca kukla olur
        // (tuşları işe yaramaz + caster'ın saldırganına saldırır — kuklalama
        // sunucu-yetkili teleport ile ImperiusCurseTracker.tick içinde işlenir).
        if (r.target() != null) {
            markHit();
            LivingEntity t = r.target();
            int d = dur(350, player, wand);
            if (t instanceof Player) {
                // KUKLA: körlük + karanlık (ekranı karart, "kontrol sende değil"
                // hissi) + İtaat Laneti + Zayıflık. Kuklalama tracker'da yapılır.
                t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, d, 0));
                t.addEffect(new MobEffectInstance(MobEffects.DARKNESS, d, 0));
                t.addEffect(new MobEffectInstance(
                        com.arcanum.registry.ModMobEffects.IMPERIUS_CURSE.get(),
                        d, 0));
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, d, 1));
            } else {
                if (t instanceof net.minecraft.world.entity.Mob mob) {
                    mob.setTarget(null);
                }
                t.addEffect(new MobEffectInstance(
                        com.arcanum.registry.ModMobEffects.IMPERIUS_CURSE.get(),
                        d, 0));
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, d, 1));
            }
            ImperiusCurseTracker.track(level, t, player);
            SpellFx.shroud(level, t, SpellFx.lighten(color, 0.3f), 14);
            SpellFx.bodyRunes(level, t, color, 5);
        }
        SpellFx.burst(level, r.impact(), color, 16, 0.3, 0.10);
        SpellFx.soundAt(level, player, SoundEvents.EVOKER_CAST_SPELL, 0.8f, SpellFx.vary(level, 0.8f));
        curseDread(level, player);
    }

    /** Avada Kedavra — öldürme laneti: yeşil şimşek, kaçışı yok. */
    public static void avadaKedavra(ServerLevel level, Player player, ItemStack wand) {
        int color = ModSpells.AVADA_KEDAVRA.color();
        RayResult r = ray(level, player, 32.0, true); // Affedilmez → Protego Maxima'yı DELER
        SpellFx.muzzle(level, player, color);
        SpellFx.jitterBeam(level, player, r.impact(), SpellFx.lighten(color, 0.15f));
        Vec3 imp = r.impact();
        if (r.target() != null) {
            LivingEntity t = r.target();
            imp = t.position().add(0, t.getBbHeight() * 0.5, 0);
            if (t.getMobType() == MobType.UNDEAD) { // 1.20.1: UNDEAD tag'i yok → MobType (aynı küme)
                // Ölümsüzler zaten ölüdür — Öldürme Laneti onlara işlemez.
                // markHit() ÇAĞRILMAZ: kast boşa gitti sayılır, cooldown yarıya iner.
                SpellFx.burst(level, imp, SpellFx.darken(color, 0.4f), 8, 0.2, 0.04);
                SpellFx.sound(level, imp, SoundEvents.FIRE_EXTINGUISH, 0.7f, SpellFx.vary(level, 0.9f));
                level.sendParticles(ParticleTypes.SMOKE, imp.x, imp.y, imp.z, 8, 0.2, 0.2, 0.2, 0.01);
                curseDread(level, player);
                return;
            }
            markHit();
            t.hurt(magic(level, player), 2000.0f);
            // ruhun sökülüşü: içe çöken koyu halka + yükselen yeşil ışıma
            SpellFx.nova(level, t.position(), SpellFx.darken(color, 0.5f), 16, -0.3);
            for (int i = 0; i < 10; i++) {
                level.sendParticles(SpellFx.glow(color),
                        imp.x + (level.random.nextDouble() - 0.5) * 0.6,
                        imp.y + level.random.nextDouble() * 0.4,
                        imp.z + (level.random.nextDouble() - 0.5) * 0.6,
                        0, 0.0, 1.0, 0.0, 0.12);
            }
        }
        SpellFx.burst(level, imp, color, 34, 0.5, 0.2);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, imp.x, imp.y, imp.z, 12, 0.3, 0.3, 0.3, 0.03);
        SpellFx.soundAt(level, player, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7f, SpellFx.vary(level, 1.3f));
        SpellFx.sound(level, imp, SoundEvents.WITHER_HURT, 0.6f, SpellFx.vary(level, 0.6f));
        curseDread(level, player);
    }

    // ===================== HEALING =====================

    /** Episkey — iyileştirme: altın sarmal + kalpler, anında can + rejenerasyon. */
    public static void episkey(ServerLevel level, Player player, ItemStack wand) {
        markHit();
        int color = ModSpells.EPISKEY.color();
        // İyileştirme HASAR buff'ından (DAMAGE_MULT) etkilenmesin — sadece hasar +%15 istendi.
        player.heal(8.0f * power(player, wand) / DAMAGE_MULT);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
        SpellFx.helix(level, player, color, 2.2, 24);
        SpellFx.runeRing(level, player.position(), color, 6, 1.0);
        Vec3 c = player.position();
        level.sendParticles(ParticleTypes.HEART, c.x, c.y + 1.2, c.z, 6, 0.4, 0.5, 0.4, 0.0);
        SpellFx.soundAt(level, player, SoundEvents.PLAYER_LEVELUP, 0.9f, SpellFx.vary(level, 1.5f));
    }

    // ---- Vulnera Sanentur — KANAL şifa büyüsü (sağ tık basılı-tut) ----

    /** Vulnera Sanentur kayıt no-op'u: kanal büyüleri {@code castSelected}'e hiç girmez
     *  (WandItem.use startUsingItem'a saptırır) ve vulnera düello ATTACK setinde değildir —
     *  bu Effect pratikte çağrılmaz. Gerçek iş {@link #vulneraSanenturTick}'te
     *  (WandItem.onUseTick her tick çağırır). */
    public static void vulneraSanentur(ServerLevel level, Player player, ItemStack wand) {
    }

    /** Hazırlık evresi: 20 tick = 1 sn — mana AKMAZ, yalnız toplanma FX'i. */
    private static final int VULNERA_PREP_TICKS = 20;
    /** Drenaj: 33 mana/sn = protego_maxima'nın (30/sn) %10 fazlası → 1.65 mana/tick. */
    private static final double VULNERA_DRAIN_PER_TICK = 33.0 / 20.0;
    /** Kesirli drenaj akümülatörü (UmbraFormManager.DRAIN_ACC deseniyle aynı fikir). */
    private static final java.util.Map<java.util.UUID, Double> VULNERA_ACC = new java.util.HashMap<>();

    /** Oyuncu çıkışında akümülatör kalıntısını temizle (sızıntı önleme). */
    public static void vulneraForget(java.util.UUID id) {
        VULNERA_ACC.remove(id);
    }

    /**
     * Vulnera Sanentur kanal tick'i — {@code WandItem.onUseTick} çağırır.
     * <p>Akış: ilk 20 tick HAZIRLIK (mana akmaz; asa ucunda toplanan altın-kızıl ışık +
     * hedefe zayıf iplikçikler). Sonrasında her tick 1.65 mana akümüle edilip tam sayı
     * kısmı düşülür (=33/sn; creative'de akmaz); bakılan CANLI hedefe (oyuncu VE mob,
     * diğer büyülerle aynı ray/menzil) 4 tickte 1 HP = 2.5 kalp/sn iyileştirme akar.
     * Hedef nişanda DEĞİLSE şifa işlemez ama drenaj sürer (kullanıcı isteği).
     * Mana bitince kanal kesilir. Şifa miktarı SABİTTİR (episkey'deki gibi güç/hasar
     * çarpanlarından bilinçli arındırılmıştır — denge başka hiçbir yerde değişmesin).
     */
    public static void vulneraSanenturTick(ServerLevel level, Player player, ItemStack wand, int elapsed) {
        int color = ModSpells.VULNERA_SANENTUR.color();
        Vec3 tip = SpellFx.wandTip(player);
        if (elapsed <= 1) {
            VULNERA_ACC.remove(player.getUUID()); // yeni kanal: eski kesir kalıntısını sıfırla
        }

        // ---- 1) HAZIRLIK (ilk 1 sn): mana yok, yalnız toplanma FX'i ----
        if (elapsed < VULNERA_PREP_TICKS) {
            // asa ucunda içe toplanan altın-kızıl ışık (ilerledikçe yoğunlaşır)
            int n = 1 + elapsed / 6;
            level.sendParticles(SpellFx.glow(SpellFx.lighten(color, 0.35f)),
                    tip.x, tip.y, tip.z, n, 0.14, 0.14, 0.14, 0.012);
            // hedefe doğru zayıf iplikçikler (seyrek — 5 tickte bir tek sıra iz)
            if (elapsed % 5 == 0) {
                RayResult pre = ray(level, player, 12.0);
                SpellFx.line(level, tip, pre.impact(), SpellFx.trail(SpellFx.darken(color, 0.15f)), 2.4);
            }
            return;
        }

        // ---- 2) DRENAJ (hazırlık bitti, basılı tutuldukça): 33/sn kesirli akümülatör ----
        if (!player.isCreative()) {
            double acc = VULNERA_ACC.getOrDefault(player.getUUID(), 0.0) + VULNERA_DRAIN_PER_TICK;
            int whole = (int) acc;
            if (whole > 0) {
                com.arcanum.data.ArcanumPlayerData data =
                        com.arcanum.data.ArcanumPlayerData.get(level.getServer());
                if (data.getMana(player) < whole) {
                    // mana bitti → kanal biter (protego kanallarıyla aynı geri bildirim)
                    player.displayClientMessage(
                            Component.translatable("arcanum.no_mana").withStyle(ChatFormatting.RED), true);
                    player.getCooldowns().addCooldown(wand.getItem(), 10);
                    VULNERA_ACC.remove(player.getUUID());
                    player.stopUsingItem();
                    return;
                }
                data.spendMana(player, whole);
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    com.arcanum.network.ArcanumNetwork.syncMagicData(sp);
                }
                acc -= whole;
            }
            VULNERA_ACC.put(player.getUUID(), acc);
        }

        // ---- 3) ŞİFA AKIŞI: bakılan canlıya 1 HP / 4 tick (=2.5 kalp/sn) ----
        RayResult r = ray(level, player, 12.0);
        Vec3 flowEnd = r.impact();
        if (r.target() != null) {
            LivingEntity t = r.target();
            flowEnd = t.position().add(0, t.getBbHeight() * 0.5, 0);
            if (elapsed % 4 == 0) {
                t.heal(1.0f); // SABİT 5 HP/sn — güç çarpanlarından bağımsız
            }
            // hedef üstünde kalp + mutlu partiküller (seyrek)
            if (elapsed % 8 == 0) {
                level.sendParticles(ParticleTypes.HEART,
                        t.getX(), t.getY() + t.getBbHeight() + 0.3, t.getZ(), 1, 0.25, 0.15, 0.25, 0.0);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
            }
        }
        // asadan hedefe süzülen altın-kızıl şifa huzmesi (2 tickte bir, yumuşak iz)
        if (elapsed % 2 == 0) {
            SpellFx.line(level, tip, flowEnd, SpellFx.trail(SpellFx.lighten(color, 0.2f)), 1.8);
            level.sendParticles(SpellFx.glow(color), tip.x, tip.y, tip.z, 1, 0.06, 0.06, 0.06, 0.01);
        }
        // yumuşak periyodik şifa sesi (saniyede bir çıtırtısız çan)
        if (elapsed % 20 == 0) {
            SpellFx.soundAt(level, player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, SpellFx.vary(level, 1.5f));
        }
    }

    /** Expecto Patronum — Patronus: gümüş-mavi koruyucu dalga; Ruh Emicileri yok eder, ölümsüzlere ekstra. */
    public static void expectoPatronum(ServerLevel level, Player player, ItemStack wand) {
        markHit();
        int color = ModSpells.PATRONUM.color();
        // Koruyucu etkiler SABİT (140/200 tick) — patronusDurationTicks YALNIZCA yoldaş
        // ömrünü belirler (tester süreyi kısalttı; buff'lar bundan etkilenmesin).
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 140, 0));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
        AABB box = player.getBoundingBox().inflate(10.0);
        // Ruh Emici ANINDA-YOK ETME yarıçapı dalganın tamamından DAR (tester: −%35):
        // 6.5 bloktan yakın dementor buharlaşır; daha uzaktaki ağır hasar + itilme yer.
        double dementorKillSq = 6.5 * 6.5;
        // Enemy arayüzü Monster olmayan düşmanları da kapsar (Phantom, Ghast...)
        for (LivingEntity m : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e instanceof Enemy && e.isAlive())) {
            if (m instanceof DementorEntity && player.distanceToSqr(m) <= dementorKillSq) {
                // Patronus, Ruh Emicileri lore'a uygun şekilde doğrudan yok eder
                SpellFx.nova(level, m.position().add(0, 1.0, 0), SpellFx.lighten(color, 0.5f), 20, 0.5);
                m.hurt(magic(level, player), Float.MAX_VALUE);
                continue;
            }
            Vec3 kb = m.position().subtract(player.position()).normalize().scale(1.2);
            m.push(kb.x, 0.35, kb.z);
            float dmg = m.getMobType() == MobType.UNDEAD ? 15.68f : 7.84f;
            m.hurt(magic(level, player), dmg * power(player, wand));
            SpellFx.shroud(level, m, SpellFx.lighten(color, 0.4f), 6);
        }
        // koruyucu dalga: ardışık halkalar + kubbe + yukarı süzülen ruh ışımaları
        Vec3 c = player.position();
        SpellFx.nova(level, c, color, 24, 0.55);
        SpellFx.nova(level, c.add(0, 0.6, 0), SpellFx.lighten(color, 0.4f), 18, 0.4);
        SpellFx.dome(level, c, SpellFx.lighten(color, 0.2f), 2.5);
        for (int i = 0; i < 16; i++) {
            level.sendParticles(SpellFx.glow(SpellFx.lighten(color, 0.5f)),
                    c.x + (level.random.nextDouble() - 0.5) * 4.0,
                    c.y + 0.3,
                    c.z + (level.random.nextDouble() - 0.5) * 4.0,
                    0, 0.0, 1.0, 0.0, 0.10);
        }
        SpellFx.soundAt(level, player, SoundEvents.BEACON_POWER_SELECT, 1.1f, SpellFx.vary(level, 1.3f));
        SpellFx.soundAt(level, player, SoundEvents.ALLAY_AMBIENT_WITH_ITEM, 1.0f, SpellFx.vary(level, 0.8f));
        // Cisimleşmiş Patronus yoldaşı çağır (varyant büyücü seviyesine göre: 1-7 baykuş, 8-11 kurt, 12+ geyik)
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            int wizardLevel = com.arcanum.data.ArcanumPlayerData.get(sp.getServer()).getLevel(sp);
            com.arcanum.entity.PatronusEntity.summonFor(sp, wizardLevel, level);
        }
    }

    // ===================== YENİ TILSIMLAR & LANETLER =====================

    /** Flipendo — itme büyüsü: hedefi sertçe geriye savurur. */
    public static void flipendo(ServerLevel level, Player player, ItemStack wand) {
        int color = ModSpells.FLIPENDO.color();
        RayResult r = ray(level, player, 14.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.beam(level, player, r.impact(), color);
        if (r.target() != null) {
            markHit();
            LivingEntity t = r.target();
            t.hurt(magic(level, player), 3.92f * power(player, wand));
            Vec3 kb = t.position().subtract(player.position()).normalize().scale(1.8);
            t.push(kb.x, 0.5, kb.z);
            SpellFx.shroud(level, t, color, 8);
        }
        SpellFx.burst(level, r.impact(), color, 12, 0.3, 0.18);
        SpellFx.nova(level, r.impact(), SpellFx.lighten(color, 0.3f), 10, 0.3);
        SpellFx.soundAt(level, player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, SpellFx.vary(level, 1.2f));
    }

    /** Diffindo — kesme tılsımı: keskin hasar; yaprak/ağ gibi yumuşak blokları biçer. */
    public static void diffindo(ServerLevel level, Player player, ItemStack wand) {
        int color = ModSpells.DIFFINDO.color();
        RayResult r = ray(level, player, 14.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.beam(level, player, r.impact(), color);
        Vec3 imp = r.impact();
        if (r.target() != null) {
            markHit();
            LivingEntity t = r.target();
            t.hurt(magic(level, player), 7.84f * power(player, wand));
            imp = t.position().add(0, t.getBbHeight() * 0.6, 0);
        } else if (r.block().getType() == HitResult.Type.BLOCK) {
            // yumuşak dokuları biç: yaprak, ağ, çim benzeri (makas gibi drop'lu)
            BlockPos bp = r.block().getBlockPos();
            var st = level.getBlockState(bp);
            if (st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(Blocks.COBWEB)
                    || st.is(net.minecraft.tags.BlockTags.REPLACEABLE_BY_TREES)) {
                level.destroyBlock(bp, true);
                markHit(); // yumuşak doku biçildi -> iş yapıldı, iskalama sayılmaz
            }
        }
        // tek keskin çapraz kesik izi
        double s = 0.6;
        SpellFx.line(level, imp.add(-s, s, 0), imp.add(s, -s, 0), SpellFx.trail(color), 0.10);
        SpellFx.soundAt(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, SpellFx.vary(level, 1.4f));
    }

    /** Rictusempra — PASİFLEŞTİRİLDİ ({@link ModSpells#DISABLED}): "güldürme büyüsü"
     *  işlevsiz bulunup oyundan çekildi. Index kayması yasak olduğundan kayıt yerinde
     *  durur; normal yollarla zaten castlenemez (loadout sanitize + WandItem kapısı).
     *  Bu no-op yalnız savunma katmanıdır: bir şekilde çağrılırsa etki uygulamaz,
     *  gri "artık kullanılmıyor" bilgisi gösterir. */
    public static void rictusempra(ServerLevel level, Player player, ItemStack wand) {
        player.displayClientMessage(
                Component.translatable("arcanum.spell_disabled").withStyle(ChatFormatting.GRAY), true);
    }

    /** Arresto Momentum — momentum durdurma: kendine + hedefe yavaş düşüş. */
    public static void arrestoMomentum(ServerLevel level, Player player, ItemStack wand) {
        markHit();
        int color = ModSpells.ARRESTO.color();
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0));
        player.resetFallDistance();
        RayResult r = ray(level, player, 16.0);
        SpellFx.muzzle(level, player, color);
        if (r.target() != null) {
            r.target().addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0));
            r.target().resetFallDistance();
            SpellFx.shroud(level, r.target(), color, 8);
        }
        SpellFx.runeRing(level, player.position(), color, 6, 1.0);
        SpellFx.nova(level, player.position(), SpellFx.lighten(color, 0.4f), 12, 0.12);
        SpellFx.soundAt(level, player, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9f, SpellFx.vary(level, 0.8f));
    }

    /** Immobulus — donma dalgası: çarpma çevresindeki HERKESİ dondurur (AoE kontrol). */
    public static void immobulus(ServerLevel level, Player player, ItemStack wand) {
        markHit();
        int color = ModSpells.IMMOBULUS.color();
        RayResult r = ray(level, player, 16.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.beam(level, player, r.impact(), color);
        Vec3 imp = r.impact();
        AABB box = new AABB(imp, imp).inflate(4.0);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur(100, player, wand), 5));
            t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, dur(100, player, wand), 2));
            SpellFx.shroud(level, t, SpellFx.lighten(color, 0.3f), 8);
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, imp.x, imp.y + 0.5, imp.z, 30, 2.5, 1.0, 2.5, 0.02);
        SpellFx.nova(level, imp, color, 20, 0.45);
        SpellFx.nova(level, imp.add(0, 0.5, 0), SpellFx.lighten(color, 0.4f), 14, 0.3);
        SpellFx.runeRing(level, imp, color, 8, 2.5);
        SpellFx.soundAt(level, player, SoundEvents.PLAYER_HURT_FREEZE, 1.0f, SpellFx.vary(level, 0.9f));
        SpellFx.sound(level, imp, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, SpellFx.vary(level, 0.6f));
    }

    /** Confringo — patlama laneti: alevli patlama, çevreyi tutuşturur. */
    public static void confringo(ServerLevel level, Player player, ItemStack wand) {
        markHit();
        int color = ModSpells.CONFRINGO.color();
        RayResult r = ray(level, player, 20.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.beam(level, player, r.impact(), color);
        Vec3 imp = r.impact();
        level.explode(player, imp.x, imp.y, imp.z, 3.08f, true, Level.ExplosionInteraction.MOB);
        SpellFx.burst(level, imp, color, 26, 0.7, 0.25);
        SpellFx.nova(level, imp, color, 18, 0.5);
        level.sendParticles(ParticleTypes.FLAME, imp.x, imp.y, imp.z, 24, 0.8, 0.5, 0.8, 0.08);
        SpellFx.soundAt(level, player, SoundEvents.FIRECHARGE_USE, 1.0f, SpellFx.vary(level, 0.7f));
    }

    /** Fiendfyre — şeytan ateşi: kontrolsüz lanetli alev fırtınası (kara büyü). */
    public static void fiendfyre(ServerLevel level, Player player, ItemStack wand) {
        markHit(); // her zaman zemine alev fırtınası koyar -> asla "iskalama" sayılmaz
        int color = ModSpells.FIENDFYRE.color();
        RayResult r = ray(level, player, 18.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.jitterBeam(level, player, r.impact(), SpellFx.darken(color, 0.2f));
        Vec3 imp = r.impact();
        // alev fırtınası: çevredeki canlıları tutuştur + ağır hasar. Kullanıcı isteği:
        // çember + yakıcılık ~2× (yanma süresi 112→224 tick, catch alanı 4.0→5.5),
        // hasar −%10 (11.76→10.58).
        AABB box = new AABB(imp, imp).inflate(5.5);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            t.setRemainingFireTicks(224);
            t.hurt(magic(level, player), 10.58f * power(player, wand));
        }
        // zemine alev çemberi — daha geniş ve daha yoğun (yalnız hava bloklarına, yüzeyin üstüne)
        BlockPos center = BlockPos.containing(imp);
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-5, -1, -5), center.offset(5, 1, 5))) {
            double d = Math.sqrt(p.distSqr(center));
            if (d > 5.0 || level.random.nextFloat() > 0.55f) continue;
            if (level.getBlockState(p).isAir() && !level.getBlockState(p.below()).isAir()) {
                level.setBlockAndUpdate(p.immutable(), BaseFireBlock.getState(level, p));
            }
        }
        level.sendParticles(ParticleTypes.FLAME, imp.x, imp.y + 0.5, imp.z, 70, 3.0, 1.4, 3.0, 0.1);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, imp.x, imp.y + 0.5, imp.z, 28, 2.5, 1.2, 2.5, 0.06);
        level.sendParticles(ParticleTypes.LAVA, imp.x, imp.y, imp.z, 8, 1.0, 0.5, 1.0, 0.0);
        SpellFx.burst(level, imp, color, 30, 0.8, 0.3);
        SpellFx.nova(level, imp, color, 22, 0.55);
        SpellFx.nova(level, imp.add(0, 0.7, 0), SpellFx.darken(color, 0.3f), 16, 0.4);
        SpellFx.soundAt(level, player, SoundEvents.BLAZE_SHOOT, 1.0f, SpellFx.vary(level, 0.6f));
        SpellFx.sound(level, imp, SoundEvents.GENERIC_EXPLODE, 0.7f, SpellFx.vary(level, 0.8f));
    }

    /** Depulso — itme tılsımı (Accio'nun tersi): hedefi caster'dan uzağa güçlü savurur. */
    public static void depulso(ServerLevel level, Player player, ItemStack wand) {
        int color = ModSpells.DEPULSO.color();
        RayResult r = ray(level, player, 16.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.beam(level, player, r.impact(), color);
        if (r.target() != null) {
            markHit();
            LivingEntity t = r.target();
            t.hurt(magic(level, player), 4.2f * power(player, wand));
            Vec3 away = t.position().subtract(player.position()).normalize().scale(2.4);
            t.push(away.x, 0.35, away.z);
            SpellFx.shroud(level, t, color, 10);
        }
        SpellFx.burst(level, r.impact(), color, 14, 0.3, 0.2);
        SpellFx.nova(level, r.impact(), SpellFx.lighten(color, 0.3f), 12, 0.32);
        SpellFx.soundAt(level, player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9f, SpellFx.vary(level, 1.4f));
    }

    /** Everte Statum — hedefi geriye ve yukarı fırlatan devirme büyüsü. */
    public static void everteStatum(ServerLevel level, Player player, ItemStack wand) {
        int color = ModSpells.EVERTE_STATUM.color();
        RayResult r = ray(level, player, 16.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.beam(level, player, r.impact(), color);
        if (r.target() != null) {
            markHit();
            LivingEntity t = r.target();
            t.hurt(magic(level, player), 8.4f * power(player, wand));
            Vec3 away = t.position().subtract(player.position()).normalize().scale(1.8);
            t.push(away.x, 0.55, away.z);
            SpellFx.shroud(level, t, color, 12);
        }
        SpellFx.burst(level, r.impact(), color, 16, 0.35, 0.24);
        SpellFx.nova(level, r.impact(), SpellFx.lighten(color, 0.3f), 12, 0.35);
        SpellFx.soundAt(level, player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, SpellFx.vary(level, 1.0f));
    }

    /** Ventus — rüzgâr konisi: bakış yönündeki ~6 bloklık koni içindeki herkesi iter + ateş söndürür. */
    public static void ventus(ServerLevel level, Player player, ItemStack wand) {
        markHit();
        int color = ModSpells.VENTUS.color();
        Vec3 look = player.getLookAngle();
        Vec3 origin = player.getEyePosition();
        SpellFx.muzzle(level, player, color);
        AABB box = player.getBoundingBox().expandTowards(look.scale(6.0)).inflate(3.0);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive())) {
            Vec3 toTarget = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(origin);
            double dist = toTarget.length();
            if (dist > 6.5 || dist < 1.0e-3) {
                continue;
            }
            // koni testi: bakış yönü ile hedef yönü arasındaki açı ~35° (cos ≈ 0.82)
            if (look.dot(toTarget.scale(1.0 / dist)) < 0.82) {
                continue;
            }
            t.setRemainingFireTicks(0);
            t.hurt(magic(level, player), 4.2f * power(player, wand));
            Vec3 push = look.normalize().scale(1.6);
            t.push(push.x, 0.25, push.z);
            SpellFx.shroud(level, t, SpellFx.lighten(color, 0.3f), 8);
        }
        // ATEŞ SÖNDÜRME (tester): bakış hattı boyunca ~15 blok ileriye kadar, hattın
        // çevresindeki (yarıçap 4 ≈ 7-8 blok çap) ateş blokları söner + yanan kamp
        // ateşleri kapanır. Rüzgâr "tünel" gibi ateşleri süpürür.
        for (int step = 0; step <= 15; step += 3) {
            BlockPos wc = BlockPos.containing(origin.add(look.scale(step)));
            for (BlockPos p : BlockPos.betweenClosed(wc.offset(-4, -3, -4), wc.offset(4, 3, 4))) {
                if (p.distSqr(wc) > 17) {
                    continue; // küresel yarıçap ~4
                }
                var st = level.getBlockState(p);
                if (st.is(net.minecraft.tags.BlockTags.FIRE)) {
                    level.removeBlock(p, false);
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            p.getX() + 0.5, p.getY() + 0.3, p.getZ() + 0.5, 2, 0.15, 0.15, 0.15, 0.01);
                } else if (st.is(net.minecraft.tags.BlockTags.CAMPFIRES)
                        && st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                        && st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
                    level.setBlockAndUpdate(p.immutable(), st.setValue(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, false));
                }
            }
        }

        // rüzgâr görseli: koni boyunca bulut + duman + genişleyen halka
        for (int i = 1; i <= 6; i++) {
            Vec3 p = origin.add(look.scale(i));
            level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 10, 0.4, 0.4, 0.4, 0.08);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y, p.z, 3, 0.3, 0.3, 0.3, 0.02);
        }
        Vec3 front = origin.add(look.scale(3.0));
        SpellFx.burst(level, front, color, 18, 0.6, 0.3);
        SpellFx.nova(level, front, SpellFx.lighten(color, 0.4f), 16, 0.4);
        SpellFx.soundAt(level, player, SoundEvents.ENDER_DRAGON_FLAP, 0.9f, SpellFx.vary(level, 1.3f));
        SpellFx.soundAt(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, SpellFx.vary(level, 1.5f));
    }

    /** Duro — taşlaştırma: hedefi ağır yavaşlık + zayıflık + kazma yorgunluğu ile hantallaştırır. */
    public static void duro(ServerLevel level, Player player, ItemStack wand) {
        int color = ModSpells.DURO.color();
        RayResult r = ray(level, player, 16.0);
        SpellFx.muzzle(level, player, color);
        SpellFx.beam(level, player, r.impact(), color);
        if (r.target() != null) {
            markHit();
            LivingEntity t = r.target();
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur(120, player, wand), 5));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, dur(120, player, wand), 2));
            t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, dur(120, player, wand), 3));
            t.hurt(magic(level, player), 5.6f * power(player, wand));
            SpellFx.shroud(level, t, color, 14);
            SpellFx.bodyRunes(level, t, SpellFx.darken(color, 0.2f), 5);
        }
        SpellFx.burst(level, r.impact(), color, 16, 0.35, 0.06);
        SpellFx.nova(level, r.impact(), SpellFx.darken(color, 0.2f), 12, 0.15);
        level.sendParticles(ParticleTypes.POOF, r.impact().x, r.impact().y, r.impact().z, 10, 0.3, 0.3, 0.3, 0.02);
        SpellFx.soundAt(level, player, SoundEvents.STONE_PLACE, 1.0f, SpellFx.vary(level, 0.7f));
        SpellFx.sound(level, r.impact(), SoundEvents.STONE_HIT, 0.9f, SpellFx.vary(level, 0.6f));
    }

    /**
     * Aguamenti — su konjürasyonu: bakılan yüzeyin önüne bir SU KAYNAĞI bırakır
     * (su kovası boşaltmak gibi). Blok raycast'i {@link Player#pick} ile yapılır
     * (fluids=false → sıvıları geçip katı bloğa vurur); vurulan yüzün komşusu hava
     * ya da yerine konabilir bir blok ise oraya varsayılan WATER state'i (kaynak)
     * konur. Havaya kast edildiğinde yalnızca su serpintisi + ses. Null-güvenli.
     */
    public static void aguamenti(ServerLevel level, Player player, ItemStack wand) {
        int color = ModSpells.AGUAMENTI.color();
        SpellFx.muzzle(level, player, color);
        HitResult hit = player.pick(6.0, 1.0f, false);
        Vec3 fx = hit.getLocation();
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
            BlockPos place = bhr.getBlockPos().relative(bhr.getDirection());
            var st = level.getBlockState(place);
            if (st.isAir() || st.canBeReplaced()) {
                level.setBlock(place, Blocks.WATER.defaultBlockState(), 3);
                markHit();
                fx = Vec3.atCenterOf(place);
            }
        }
        SpellFx.line(level, SpellFx.wandTip(player), fx, ParticleTypes.SPLASH, 0.4);
        level.sendParticles(ParticleTypes.SPLASH, fx.x, fx.y + 0.2, fx.z, 20, 0.35, 0.25, 0.35, 0.05);
        level.sendParticles(ParticleTypes.FALLING_WATER, fx.x, fx.y + 0.4, fx.z, 12, 0.3, 0.2, 0.3, 0.02);
        SpellFx.nova(level, fx, SpellFx.lighten(color, 0.4f), 12, 0.18);
        SpellFx.soundAt(level, player, SoundEvents.BUCKET_EMPTY, 0.9f, SpellFx.vary(level, 1.1f));
        SpellFx.sound(level, fx, SoundEvents.BUCKET_EMPTY, 0.7f, SpellFx.vary(level, 0.9f));
    }

    /**
     * Homenum Revelio — insan/canlı ortaya çıkarma: yakın çevredeki (24 blok) TÜM
     * canlılara 10 sn Parlama (spektral yay gibi) verir; caster çevresinde rün
     * çemberi + reveal sesi. Kendini hariç tutar. Null-güvenli.
     */
    public static void homenumRevelio(ServerLevel level, Player player, ItemStack wand) {
        markHit();
        int color = ModSpells.HOMENUM_REVELIO.color();
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(24.0), e -> e != player && e.isAlive())) {
            t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
            SpellFx.shroud(level, t, SpellFx.lighten(color, 0.3f), 4);
        }
        Vec3 c = player.position();
        SpellFx.runeRing(level, c, color, 10, 2.4);
        SpellFx.nova(level, c, SpellFx.lighten(color, 0.4f), 18, 0.22);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.0, c.z, 24, 1.8, 0.8, 1.8, 0.02);
        SpellFx.soundAt(level, player, SoundEvents.BEACON_ACTIVATE, 0.8f, SpellFx.vary(level, 1.4f));
        SpellFx.soundAt(level, player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, SpellFx.vary(level, 1.6f));
    }

    /**
     * Alohomora — kilit açma tılsımı: baktığın Mühürlü Kapı'nın (WardedDoorBlock)
     * büyülü mührünü çözer; sıradan kapı ve kapakları da (demir dahil) uzaktan
     * açıp kapatır. Başka bloklara/boşluğa atılırsa ıskalar (yarım cooldown).
     */
    public static void alohomora(ServerLevel level, Player player, ItemStack wand) {
        int color = ModSpells.ALOHOMORA.color();
        RayResult r = ray(level, player, 10.0);
        SpellFx.muzzle(level, player, color);
        // YILDIRIM YOK (tester: "alohomora yıldırım atması saçma") — kilit açma tılsımı
        // yumuşak altın bir partikül izi + çarptığı yerde sarı parıltıyla belli olur.
        SpellFx.line(level, SpellFx.wandTip(player), r.impact(), SpellFx.trail(color), 0.5);
        level.sendParticles(SpellFx.glow(SpellFx.lighten(color, 0.3f)),
                r.impact().x, r.impact().y, r.impact().z, 6, 0.15, 0.15, 0.15, 0.01);
        if (r.block().getType() == HitResult.Type.BLOCK) {
            BlockPos bp = r.block().getBlockPos();
            var st = level.getBlockState(bp);
            if (st.getBlock() instanceof com.arcanum.block.WardedDoorBlock door) {
                // Mühürlü Kapı: unlock hangi yarım verilirse verilsin çalışır ve
                // kapı seslerini (IRON_DOOR_OPEN + çan) kendisi çalar.
                door.unlock(level, bp, st);
                markHit();
                SpellFx.burst(level, r.impact(), color, 14, 0.4, 0.15);
                SpellFx.runeRing(level, Vec3.atCenterOf(bp), color, 6, 0.9);
            } else if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                    || st.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) {
                // Vanilla kapı/kapak (demir dahil): AÇIK özelliğini uzaktan çevir —
                // kapının diğer yarımı vanilla updateShape senkronuyla kendiliğinden uyar.
                level.setBlockAndUpdate(bp, st.cycle(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN));
                markHit();
                SpellFx.burst(level, r.impact(), color, 10, 0.3, 0.12);
                SpellFx.sound(level, r.impact(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
            }
        }
        SpellFx.soundAt(level, player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, SpellFx.vary(level, 1.5f));
    }

    // ===================== helpers =====================

    private record RayResult(Vec3 impact, LivingEntity target, BlockHitResult block) {}

    private static RayResult ray(ServerLevel level, Player player, double range) {
        return ray(level, player, range, false);
    }

    /**
     * {@code bypassShield=false} (varsayılan): kalkanlı bir oyuncuya atılan büyü hedefe
     * ULAŞMAZ (Protego Maxima). {@code bypassShield=true}: Affedilmez 3 lanet (Avada
     * Kedavra / Crucio / Imperio) kalkanı DELER — bu üçü {@code true} ile çağırır.
     */
    /** Global menzil çarpanı — kullanıcı isteği: tüm büyüler belirgin daha uzağa gitsin (+%50). */
    private static final double RANGE_MULT = 1.50;

    private static RayResult ray(ServerLevel level, Player player, double range, boolean bypassShield) {
        range *= RANGE_MULT * com.arcanum.config.ArcanumConfig.get().spellRangeMult; // kullanıcı config: menzil çarpanı
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult bh = level.clip(
                new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 blockPos = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation() : end;
        double maxSq = eye.distanceToSqr(blockPos);
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                en -> en != player && en.isPickable() && !en.isSpectator(), maxSq);
        if (eh != null && eh.getEntity() instanceof LivingEntity le) {
            // PROTEGO MAXIMA: kalkanlı oyuncuya saldırı büyüsü ulaşmaz — kalkana çarpar.
            if (!bypassShield && le instanceof Player tp && ProtegoShield.isShielded(tp)) {
                Vec3 clank = eh.getLocation();
                SpellFx.dome(level, tp.position().add(0, 0.9, 0), 0x4AA3E0, 1.7);
                SpellFx.nova(level, clank, 0x9FD8FF, 14, 0.2);
                SpellFx.sound(level, clank, SoundEvents.SHIELD_BLOCK, 1.0f, SpellFx.vary(level, 1.2f));
                return new RayResult(clank, null, bh); // hedef YOK → büyü kalkanda ıskalar
            }
            return new RayResult(eh.getLocation(), le, bh);
        }
        return new RayResult(blockPos, null, bh);
    }

    /** Tester isteği: TÜM büyü HASARI ×1.15. Yalnız hasarı büyütmek için power()'a çarpılır;
     *  {@link #dur} bunu geri BÖLER, böylece efekt SÜRELERİ değişmez (yalnızca damage +%15). */
    private static final float DAMAGE_MULT = 1.15f;

    /** Kast eden asanın kademesine göre büyü gücü çarpanı — Exstimulo aktifken ×1.5 ek bonus. */
    private static float power(Player player, ItemStack wand) {
        float mult = 1.0f;
        if (wand.getItem() instanceof WandItem w) {
            mult *= w.tier().powerMult(); // asa güç KALIR
        }
        mult *= 0.8f; // 10. tur GLOBAL denge: tüm güç/hasar %20 azaldı
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            mult *= (float) com.arcanum.data.ArcanumPlayerData.get(sp.getServer()).powerMult(sp); // skill Güç (1.0..1.20)
        }
        if (player.hasEffect(com.arcanum.registry.ModMobEffects.EXSTIMULO.get())) {
            mult *= 2.0f; // Exstimulo: güç ×2.0 (eski 1.5)
        }
        mult *= (float) com.arcanum.config.ArcanumConfig.get().damageMult; // kullanıcı config: genel hasar çarpanı
        return mult * DAMAGE_MULT; // hasar +%15 (dur() bunu böldüğü için süreler etkilenmez)
    }

    private static DamageSource magic(ServerLevel level, Player player) {
        return level.damageSources().indirectMagic(player, player);
    }

    /**
     * Etki süresi ölçekleyici — kast eden asanın güç çarpanını (ve Exstimulo
     * bonusunu) süreye de uygular, böylece güçlü asalar debuff'ları uzatır.
     * En az 1 tick döner.
     */
    public static int dur(int base, Player player, ItemStack wand) {
        // power() artık DAMAGE_MULT + config damageMult içeriyor; süreler hasar
        // buff'ından ETKİLENMESİN diye ikisine de böl (config yalnız HASARI ölçekler).
        return Math.max(1, Math.round(base * power(player, wand)
                / (DAMAGE_MULT * (float) com.arcanum.config.ArcanumConfig.get().damageMult)));
    }

    /**
     * Lanet büyülerinin (Crucio / Imperio / Avada Kedavra) katmanlı, ürkütücü
     * ses koreografisi — "yıldırım gibi ama tam değil" boğuk bir gök gürültüsü,
     * üstüne wither uğultusu ve elder guardian lanet çınlaması. Mevcut büyü
     * seslerine EK olarak caster konumunda çalınır; bağırmasın ama tüyler
     * ürpertsin. AMBIENT_CAVE bir Holder olduğundan burada plain SoundEvent
     * olan ELDER_GUARDIAN_CURSE tercih edildi.
     */
    private static void curseDread(ServerLevel level, Player player) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 0.55f);
        level.playSound(null, x, y, z, SoundEvents.WITHER_SPAWN,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 0.6f);
        level.playSound(null, x, y, z, SoundEvents.ELDER_GUARDIAN_CURSE,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, SpellFx.vary(level, 0.7f));
    }
}
