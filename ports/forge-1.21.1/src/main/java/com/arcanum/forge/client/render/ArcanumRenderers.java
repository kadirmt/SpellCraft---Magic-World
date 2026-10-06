package com.arcanum.forge.client.render;

import com.arcanum.Arcanum;
import com.arcanum.forge.client.particle.BeamParticle;
import com.arcanum.forge.client.particle.GlowParticle;
import com.arcanum.forge.client.particle.LeafMoteParticle;
import com.arcanum.forge.client.particle.RuneParticle;
import com.arcanum.forge.client.particle.SpellSparkParticle;
import com.arcanum.forge.client.particle.TrailParticle;
import com.arcanum.registry.ModEntities;
import com.arcanum.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * İstemci render kayıtları (MOD bus) — kök fabric {@code ArcanumFabricClient}
 * karşılığı, yalnız RENDER dilimi:
 * <ul>
 *   <li>21 entity renderer (19 GeckoLib + Arcanewood bot/sandıklı bot) —
 *       {@link EntityRenderersEvent.RegisterRenderers}. Model layer kaydı GEREKMEZ:
 *       GeckoLib kendi modellerini yönetir, bot renderer'ları vanilla OAK bot
 *       katmanını yeniden kullanır (kökle aynı) →
 *       {@code EntityRenderersEvent.RegisterLayerDefinitions} bilinçli olarak yok.</li>
 *   <li>6 partikül provider'ı — {@link RegisterParticleProvidersEvent#registerSpriteSet}
 *       (javap: {@code registerSpriteSet(ParticleType<T>,
 *       ParticleEngine.SpriteParticleRegistration<T>)}; Provider ctor'ları
 *       {@code (SpriteSet)} → ctor referansı birebir uyar, Fabric
 *       {@code ParticleFactoryRegistry.register(type, Provider::new)} ile aynı).</li>
 * </ul>
 *
 * <p>Asa item renderer'ı ({@code com.arcanum.client.WandItemRenderer}) BURADA KAYITSIZ —
 * kökle aynı: GeckoLib 4.7 cross-loader köprüsü ({@code WandItem.createGeoRenderer} →
 * {@code GeoRenderProvider.getGeoItemRenderer}) Forge'da GeckoLib'in kendi
 * {@code BlockEntityWithoutLevelRendererMixin}'i üzerinden çalışır (javap ile forge
 * jar'ında doğrulandı); ek Forge kaydı (IClientItemExtensions) GEREKMEZ.
 */
@Mod.EventBusSubscriber(modid = Arcanum.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArcanumRenderers {

    private ArcanumRenderers() {}

    /** Yaratık renderer'ları (kök kayıt sırası korunmuştur). */
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
        // Protego Diabolica alev ejderhası (kökte de listenin SONUNDA kayıtlı)
        event.registerEntityRenderer(ModEntities.DIABOLICA_DRAGON.get(), DiabolicaDragonRenderer::new);
    }

    /** Büyü partikül provider'ları (glow/trail/rune/spark/leaf/beam — kök sırası). */
    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SPELL_SPARK.get(), SpellSparkParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SPELL_GLOW.get(), GlowParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SPELL_TRAIL.get(), TrailParticle.Provider::new);
        event.registerSpriteSet(ModParticles.MAGIC_RUNE.get(), RuneParticle.Provider::new);
        event.registerSpriteSet(ModParticles.LEAF_MOTE.get(), LeafMoteParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SPELL_BEAM.get(), BeamParticle.Provider::new);
    }
}
