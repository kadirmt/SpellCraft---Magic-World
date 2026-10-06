package com.arcanum.forge.client.render;

import com.arcanum.Arcanum;
import com.arcanum.forge.client.render.particle.BeamParticle;
import com.arcanum.forge.client.render.particle.GlowParticle;
import com.arcanum.forge.client.render.particle.LeafMoteParticle;
import com.arcanum.forge.client.render.particle.RuneParticle;
import com.arcanum.forge.client.render.particle.SpellSparkParticle;
import com.arcanum.forge.client.render.particle.TrailParticle;
import com.arcanum.forge.client.render.renderer.AcromantulaRenderer;
import com.arcanum.forge.client.render.renderer.ArcanewoodBoatRenderer;
import com.arcanum.forge.client.render.renderer.ArcanewoodChestBoatRenderer;
import com.arcanum.forge.client.render.renderer.BasiliskRenderer;
import com.arcanum.forge.client.render.renderer.BowtruckleRenderer;
import com.arcanum.forge.client.render.renderer.BroomRenderer;
import com.arcanum.forge.client.render.renderer.DeathEaterRenderer;
import com.arcanum.forge.client.render.renderer.DementorRenderer;
import com.arcanum.forge.client.render.renderer.DiabolicaDragonRenderer;
import com.arcanum.forge.client.render.renderer.GrindylowRenderer;
import com.arcanum.forge.client.render.renderer.HippogriffRenderer;
import com.arcanum.forge.client.render.renderer.KneazleRenderer;
import com.arcanum.forge.client.render.renderer.MooncalfRenderer;
import com.arcanum.forge.client.render.renderer.PatronusRenderer;
import com.arcanum.forge.client.render.renderer.PhoenixRenderer;
import com.arcanum.forge.client.render.renderer.SnowyOwlRenderer;
import com.arcanum.forge.client.render.renderer.ThestralRenderer;
import com.arcanum.forge.client.render.renderer.ThunderbirdRenderer;
import com.arcanum.forge.client.render.renderer.TrollRenderer;
import com.arcanum.forge.client.render.renderer.UnicornRenderer;
import com.arcanum.forge.client.render.renderer.WerewolfRenderer;
import com.arcanum.forge.client.render.renderer.WizardTraderRenderer;
import com.arcanum.registry.ModEntities;
import com.arcanum.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge istemci RENDER kayıtları — fabric'teki {@code ArcanumFabricClient}'ın
 * renderer + partikül bölümlerinin birebir karşılığı.
 *
 * <ul>
 *   <li>19 entity renderer → {@link EntityRenderersEvent.RegisterRenderers} (MOD bus).
 *       Model layer kaydı GEREKMEZ: GeckoLib renderer'ları kendi geo modelini yükler,
 *       boat renderer'ları vanilla OAK layer'ını bake eder (zaten kayıtlı).</li>
 *   <li>6 partikül provider → {@link RegisterParticleProvidersEvent#registerSpriteSet}
 *       (MOD bus; javap ile doğrulandı: {@code registerSpriteSet(ParticleType<T>,
 *       ParticleEngine.SpriteParticleRegistration<T>)}).</li>
 * </ul>
 *
 * {@code @Mod.EventBusSubscriber} ile otomatik kaydolur — ayrıca init çağrısı gerekmez.
 */
@Mod.EventBusSubscriber(modid = Arcanum.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ArcanumForgeRenderers {

    private ArcanumForgeRenderers() {}

    /** Yaratık renderer'ları (GeckoLib + vanilla boat) — fabric ile aynı sıra. */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DEATH_EATER.get(), DeathEaterRenderer::new);
        event.registerEntityRenderer(ModEntities.DEMENTOR.get(), DementorRenderer::new);
        event.registerEntityRenderer(ModEntities.BOWTRUCKLE.get(), BowtruckleRenderer::new);
        event.registerEntityRenderer(ModEntities.MOONCALF.get(), MooncalfRenderer::new);
        event.registerEntityRenderer(ModEntities.BROOM.get(), BroomRenderer::new);
        event.registerEntityRenderer(ModEntities.TROLL.get(), TrollRenderer::new);
        event.registerEntityRenderer(ModEntities.PHOENIX.get(), PhoenixRenderer::new);
        event.registerEntityRenderer(ModEntities.THUNDERBIRD.get(), ThunderbirdRenderer::new);
        event.registerEntityRenderer(ModEntities.SNOWY_OWL.get(), SnowyOwlRenderer::new);
        event.registerEntityRenderer(ModEntities.THESTRAL.get(), ThestralRenderer::new);
        event.registerEntityRenderer(ModEntities.UNICORN.get(), UnicornRenderer::new);
        event.registerEntityRenderer(ModEntities.ARCANEWOOD_BOAT.get(), ArcanewoodBoatRenderer::new);
        event.registerEntityRenderer(ModEntities.ARCANEWOOD_CHEST_BOAT.get(), ArcanewoodChestBoatRenderer::new);
        event.registerEntityRenderer(ModEntities.HIPPOGRIFF.get(), HippogriffRenderer::new);
        event.registerEntityRenderer(ModEntities.ACROMANTULA.get(), AcromantulaRenderer::new);
        event.registerEntityRenderer(ModEntities.WEREWOLF.get(), WerewolfRenderer::new);
        event.registerEntityRenderer(ModEntities.GRINDYLOW.get(), GrindylowRenderer::new);
        event.registerEntityRenderer(ModEntities.KNEAZLE.get(), KneazleRenderer::new);
        event.registerEntityRenderer(ModEntities.BASILISK.get(), BasiliskRenderer::new);
        event.registerEntityRenderer(ModEntities.PATRONUS.get(), PatronusRenderer::new);
        event.registerEntityRenderer(ModEntities.WIZARD_TRADER.get(), WizardTraderRenderer::new);
        // Protego Diabolica alev ejderhası (translucent-emissive gövde + additive glowmask)
        event.registerEntityRenderer(ModEntities.DIABOLICA_DRAGON.get(), DiabolicaDragonRenderer::new);
    }

    /** Büyü partikül provider'ları (glow/trail/rune/spark/leaf/beam) — fabric ile aynı sıra. */
    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SPELL_SPARK.get(), SpellSparkParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SPELL_GLOW.get(), GlowParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SPELL_TRAIL.get(), TrailParticle.Provider::new);
        event.registerSpriteSet(ModParticles.MAGIC_RUNE.get(), RuneParticle.Provider::new);
        event.registerSpriteSet(ModParticles.LEAF_MOTE.get(), LeafMoteParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SPELL_BEAM.get(), BeamParticle.Provider::new);
    }
}
