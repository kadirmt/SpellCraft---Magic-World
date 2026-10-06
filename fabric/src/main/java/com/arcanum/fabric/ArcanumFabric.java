package com.arcanum.fabric;

import com.arcanum.Arcanum;
import com.arcanum.data.ArcanumPlayerData;
import com.arcanum.entity.AcromantulaEntity;
import com.arcanum.entity.BasiliskEntity;
import com.arcanum.entity.BowtruckleEntity;
import com.arcanum.entity.BroomEntity;
import com.arcanum.entity.DeathEaterEntity;
import com.arcanum.entity.DementorEntity;
import com.arcanum.entity.FiendfyreDragonEntity;
import com.arcanum.entity.GrindylowEntity;
import com.arcanum.entity.HippogriffEntity;
import com.arcanum.entity.KneazleEntity;
import com.arcanum.entity.MooncalfEntity;
import com.arcanum.entity.PatronusEntity;
import com.arcanum.entity.PhoenixEntity;
import com.arcanum.entity.SnowyOwlEntity;
import com.arcanum.entity.ThestralEntity;
import com.arcanum.entity.ThunderbirdEntity;
import com.arcanum.entity.TrollEntity;
import com.arcanum.entity.ArcanumSurfaceSpawner;
import com.arcanum.entity.UnicornEntity;
import com.arcanum.entity.WerewolfEntity;
import com.arcanum.entity.WizardTraderEntity;
import com.arcanum.fabric.worldgen.ArcanumRegion;
import com.arcanum.fabric.worldgen.ArcanumWorldgenFabric;
import com.arcanum.item.CastManager;
import com.arcanum.item.WandItem;
import com.arcanum.spell.ArcanumLeveling;
import com.arcanum.spell.CrucioTracker;
import com.arcanum.spell.ManaRegen;
import com.arcanum.network.ArcanumNetwork;
import com.arcanum.network.AssignSlotPayload;
import com.arcanum.network.CastStatePayload;
import com.arcanum.network.KnownSpellsPayload;
import com.arcanum.network.LearnSpellPayload;
import com.arcanum.network.LockPushPayload;
import com.arcanum.network.LockStatePayload;
import com.arcanum.network.MagicDataPayload;
import com.arcanum.network.LevelUpPayload;
import com.arcanum.network.RespecPayload;
import com.arcanum.network.SelectSpellPayload;
import com.arcanum.network.SpendPointPayload;
import com.arcanum.network.SetActivePayload;
import com.arcanum.network.SpellLoadoutPayload;
import com.arcanum.network.SpellTableFeedbackPayload;
import com.arcanum.registry.ModArmorMaterials;
import com.arcanum.registry.ModBlocks;
import com.arcanum.registry.ModComponents;
import com.arcanum.registry.ModEntities;
import com.arcanum.registry.ModItems;
import com.arcanum.registry.ModPotions;
import com.arcanum.registry.ModVillagers;
import com.arcanum.spell.ModSpells;
import com.arcanum.spell.Spell;
import com.arcanum.spell.SpellGating;
import com.arcanum.spell.WandLockManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Fabric ana giriş noktası (server + client ortak).
 */
public final class ArcanumFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Arcanum.init();

        // büyü seçim C2S paketi + bilinen büyü S2C senkronu
        PayloadTypeRegistry.playC2S().register(SelectSpellPayload.TYPE, SelectSpellPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LearnSpellPayload.TYPE, LearnSpellPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(KnownSpellsPayload.TYPE, KnownSpellsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpellTableFeedbackPayload.TYPE, SpellTableFeedbackPayload.CODEC);
        // büyü dizilimi (loadout): S2C senkron + C2S atama/aktif-değiştir
        PayloadTypeRegistry.playS2C().register(SpellLoadoutPayload.TYPE, SpellLoadoutPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AssignSlotPayload.TYPE, AssignSlotPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SetActivePayload.TYPE, SetActivePayload.CODEC);
        // cast durumu S2C (imleç altı minik cast göstergesi — CastBarHud buna göre çizer)
        PayloadTypeRegistry.playS2C().register(CastStatePayload.TYPE, CastStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.arcanum.network.SpellBeamPayload.TYPE,
                com.arcanum.network.SpellBeamPayload.CODEC);
        // asa kenetlenmesi (Priori Incantatem): S2C durum + C2S tık ("asayı it")
        PayloadTypeRegistry.playS2C().register(LockStatePayload.TYPE, LockStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LockPushPayload.TYPE, LockPushPayload.CODEC);
        // büyücü level/skill/mana: S2C veri senkron + C2S puan harca/respec
        PayloadTypeRegistry.playS2C().register(MagicDataPayload.TYPE, MagicDataPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SpendPointPayload.TYPE, SpendPointPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RespecPayload.TYPE, RespecPayload.CODEC);
        // seviye atlama olayı S2C → istemci "Seviye Atladın" toast bildirimi çizer
        PayloadTypeRegistry.playS2C().register(LevelUpPayload.TYPE, LevelUpPayload.CODEC);
        // Umbravolo kara duman formu S2C (entityId+aktif) → istemci zırh/eldeki-eşya gizleme seti
        PayloadTypeRegistry.playS2C().register(com.arcanum.network.UmbraFormPayload.TYPE,
                com.arcanum.network.UmbraFormPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ArcanumNetwork.syncKnownSpells(handler.player);
            ArcanumNetwork.syncLoadout(handler.player);
            ArcanumNetwork.syncMagicData(handler.player);
            // Umbravolo sanitize: formdayken logout/crash/sunucu kapanışı NBT'ye 2×
            // flySpeed (0.1) sızdırmış olabilir (vanilla login flySpeed'i SIFIRLAMAZ) —
            // formda olmayan oyuncuda tespit edilirse vanilla 0.05'e geri çekilir.
            com.arcanum.spell.UmbraFormManager.sanitizeOnJoin(handler.player);
            // Umbravolo: o an formda olan oyuncuların setini geç katılan izleyiciye HEMEN
            // gönder — periyodik RESYNC beklenirse 5 sn'ye kadar "süzülen zırh+asa" görünür.
            for (int umbraId : com.arcanum.spell.UmbraFormManager.activeEntityIds(server)) {
                ArcanumNetwork.sendUmbraFormTo(handler.player, umbraId, true);
            }
        });
        // Oyuncu çıkışında cast/kenetlenme/duman-formu yardımcı haritalarını temizle
        // (RECENT sızıntısını kapatır; Umbravolo formu logout'ta temiz kapanır).
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            CastManager.forget(handler.player.getUUID());
            com.arcanum.spell.UmbraFormManager.forget(handler.player.getUUID());
            com.arcanum.spell.Spells.vulneraForget(handler.player.getUUID()); // vulnera drenaj kesiri
            com.arcanum.item.WandItem.channelForget(handler.player.getUUID()); // kanal başlangıç notu
        });
        ServerPlayNetworking.registerGlobalReceiver(SelectSpellPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            int idx = payload.index();
            player.getServer().execute(() -> {
                int clamped = Math.max(0, Math.min(idx, ModSpells.SPELLS.size() - 1));
                ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
                ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
                ItemStack wand = main.getItem() instanceof WandItem ? main
                        : (off.getItem() instanceof WandItem ? off : null);
                if (wand != null) {
                    wand.set(ModComponents.SELECTED_SPELL.get(), clamped);
                }
            });
        });
        // Büyü Masası öğrenme isteği: TÜM doğrulama sunucu tarafında SpellGating'de
        // yapılır — burada yalnızca ana sunucu thread'ine geçiş var (paket network
        // thread'inde gelir, envanter/XP mutasyonu asla oradan yapılmamalı).
        ServerPlayNetworking.registerGlobalReceiver(LearnSpellPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String spellId = payload.spellId();
            player.getServer().execute(() -> ArcanumNetwork.handleLearnSpell(player, spellId));
        });

        // Dizilim: slota büyü ata/temizle (C2S). Sunucu-yetkili — spellIndex>=0 ise büyü
        // gerçekten bilinmeli (SpellGating.knows), yoksa atama YAPMA. Ana thread'e geç.
        ServerPlayNetworking.registerGlobalReceiver(AssignSlotPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            int page = payload.page();
            int slot = payload.slot();
            int spellIndex = payload.spellIndex();
            player.getServer().execute(() -> {
                if (page < 0 || page >= ArcanumPlayerData.PAGES
                        || slot < 0 || slot >= ArcanumPlayerData.SLOTS) {
                    return; // geçersiz konum — sessizce yok say
                }
                ArcanumPlayerData data = ArcanumPlayerData.get(player.getServer());
                if (spellIndex < 0) {
                    data.setSlot(player, page, slot, -1); // temizle
                } else if (spellIndex < ModSpells.SPELLS.size()) {
                    Spell spell = ModSpells.SPELLS.get(spellIndex);
                    if (!SpellGating.knows(player.getServer(), player, spell)) {
                        return; // bilinmeyen büyü — atama yok, resync gereksiz
                    }
                    data.setSlot(player, page, slot, spellIndex);
                } else {
                    return; // aralık dışı index
                }
                ArcanumNetwork.syncLoadout(player);
            });
        });

        // Dizilim: aktif sayfa/slot değiştir (C2S). +/- sayfa, Shift+tekerlek slot.
        ServerPlayNetworking.registerGlobalReceiver(SetActivePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            int page = payload.page();
            int slot = payload.slot();
            player.getServer().execute(() -> {
                ArcanumPlayerData.get(player.getServer()).setActive(player, page, slot);
                ArcanumNetwork.syncLoadout(player);
            });
        });

        // Asa kenetlenmesi: sol-tık "asayı it" sinyali (C2S). Tık SAYIMI ve CPS TAVANI (18)
        // otoritesi sunucuda (WandLockManager); ana thread'e geç, kilit yoksa sessizce yok say.
        ServerPlayNetworking.registerGlobalReceiver(LockPushPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.getServer().execute(() ->
                    WandLockManager.registerClick(player.getUUID(), player.level().getGameTime()));
        });

        // Skill ağacı: puan harca (server-authoritative doğrulama) + respec (ücretsiz). Ana thread hop.
        ServerPlayNetworking.registerGlobalReceiver(SpendPointPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            int track = payload.track();
            player.getServer().execute(() -> {
                if (track < 0 || track > 3) {
                    return;
                }
                // Redde de sync gönder: bayat istemci (ör. runtime'da düşürülen config)
                // "satın alınabilir" görünümünde takılı kalmasın — sunucu-otoriter, zararsız.
                ArcanumPlayerData.get(player.getServer()).trySpendPoint(player, track);
                ArcanumNetwork.syncMagicData(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(RespecPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.getServer().execute(() -> {
                ArcanumPlayerData.get(player.getServer()).respec(player);
                ArcanumNetwork.syncMagicData(player);
            });
        });

        // Arcanewood: baltayla soyma + ağacı ormanlara nadir ekle
        StrippableBlockRegistry.register(ModBlocks.ARCANEWOOD_LOG.get(), ModBlocks.STRIPPED_ARCANEWOOD_LOG.get());

        // Yanabilirlik — vanilla odun paritesi (log 5/5, plank 5/20, yaprak 30/60)
        FlammableBlockRegistry.getDefaultInstance().add(ModBlocks.ARCANEWOOD_LOG.get(), 5, 5);
        FlammableBlockRegistry.getDefaultInstance().add(ModBlocks.STRIPPED_ARCANEWOOD_LOG.get(), 5, 5);
        FlammableBlockRegistry.getDefaultInstance().add(ModBlocks.ARCANEWOOD_PLANKS.get(), 5, 20);
        FlammableBlockRegistry.getDefaultInstance().add(ModBlocks.ARCANEWOOD_LEAVES.get(), 30, 60);

        // KALDIRILDI (oyuncu şikayeti: "trees from your mod are spawning outside of
        // their intended biomes"): arcanewood ağacı #minecraft:is_forest ile TÜM orman
        // biyomlarına ekleniyordu; BiomesOPlenty kendi ormanlarını da bu tag'e eklediği
        // için BOP dünyalarında her ormanda çıkıyordu. Ağaç artık YALNIZ kendi
        // biyomlarında doğar — gloomwood / arcanewood_grove biyom JSON'larının
        // "features" listesindeki arcanum:arcanewood_trees_gloomwood ve
        // arcanum:arcanewood_grove_trees placed feature'ları üzerinden.

        // Arcane ruin vb. yerleşimler + sandık loot enjeksiyonu
        ArcanumWorldgenFabric.register();
        ArcanumLoot.register();

        // Lumos takip-ışığı
        LumosLight.register();

        // Imperio (İtaat Laneti) — "hedeflemeyi durdur" davranışı Mixin'siz, tick süpürmesiyle
        ImperiusCurseTicker.register();

        // Büyü cast zamanlayıcısı — asa artık BASILI-TUT kanallamaz. WandItem.use TEK BASIŞ
        // ile bir cast başlatır, CastManager her sunucu tick'inde geri sayar ve süre dolunca
        // büyüyü ateşler (bkz. CastManager / WandItem). Telegraf yalnızca zeminde tek çember.
        ServerTickEvents.END_SERVER_TICK.register(CastManager::tick);

        // Asa kenetlenmesi (Priori Incantatem) yöneticisi — CastManager kardeşi, her tick
        // düğümü kaydırır/mana yakar/çözer. Kenetlenme CastManager fire kancasında başlatılır.
        ServerTickEvents.END_SERVER_TICK.register(WandLockManager::tick);

        // Crucio (Cruciatus Laneti) — kalp-bazlı DoT + debuff tazeleme (ImperiusCurseTicker deseni).
        ServerTickEvents.END_SERVER_TICK.register(CrucioTracker::tick);

        // 10. tur: oyuncu-bazlı MANA yenilenmesi (mana artık asada değil).
        ServerTickEvents.END_SERVER_TICK.register(ManaRegen::tick);

        // Umbravolo kara duman formu — mana drenajı (21/sn) + duman FX + iz + güvenlik süpürmesi.
        ServerTickEvents.END_SERVER_TICK.register(com.arcanum.spell.UmbraFormManager::tick);

        // Sunucu kapanırken TÜM aktif Umbravolo formlarını temiz kapat: SERVER_STOPPING
        // son saveAll'dan ÖNCE tetiklenir → oyuncu NBT'sine mayfly/flying/flySpeed=0.1
        // yazılmaz (yazılırsa creative uçuşu kalıcı 2× hızlanıyordu; JOIN sanitize yedek ağ).
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING
                .register(com.arcanum.spell.UmbraFormManager::exitAll);

        // Arcanum YÜZEY SPAWNER'ı — troll vb. büyük yüzey moblarının gündüz de garanti
        // görünmesini sağlar (vanilla MONSTER mob-cap yarışını atlar; bkz. sınıf notu).
        ServerTickEvents.END_SERVER_TICK.register(ArcanumSurfaceSpawner::tick);

        // 10. tur: büyücü XP — bir yaratığı öldüren oyuncuya XP ver (hostile/pasif/custom/boss farklı).
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.level().isClientSide) {
                return;
            }
            // Umbravolo: ölen oyuncunun duman formunu ANINDA sök (tick süpürmesini beklemeden
            // izleyen istemcilerin gizleme seti temizlensin; abilities respawn'da zaten sıfırlanır).
            if (entity instanceof ServerPlayer deadSp
                    && com.arcanum.spell.UmbraFormManager.isActive(deadSp)) {
                com.arcanum.spell.UmbraFormManager.exit(deadSp);
            }
            // Öldüren: büyü indirectMagic(player,player) / ok / melee → hepsi source.getEntity()'de
            // oyuncuyu verir. getLastHurtByMob fallback'i KALDIRILDI: mob 5sn içinde ortam/başka-mob
            // ile ölürse en son vuran oyuncuya hak etmediği XP yazıyordu (yanlış-atıf).
            if (source.getEntity() instanceof ServerPlayer p) {
                int xp = ArcanumLeveling.xpForKill(entity);
                if (xp == 2 && p.getRandom().nextBoolean()) {
                    xp = 0; // pasif mob → %50 şans (hostile'ın yarısı)
                }
                if (xp > 0) {
                    ArcanumLeveling.addXp(p, xp);
                }
            }
        });

        // Wizard köylüsü — seviye 1 ticaret: Başlangıç Büyü Kitabı (ucuz, restocklanır).
        // "maxUses" restocklanabilir normal bir sayı (12) — tek kullanımlık olsaydı
        // köy başına yalnızca 1 oyuncu satın alabilirdi, bu istenmiyor.
        TradeOfferHelper.registerVillagerOffers(ModVillagers.WIZARD.get(), 1, factories ->
                factories.add(new VillagerTrades.ItemsForEmeralds(ModItems.STARTER_SPELL_BOOK.get(), 4, 12, 5)));

        // Yaratık attribute'ları (her living entity için ZORUNLU)
        FabricDefaultAttributeRegistry.register(ModEntities.DEATH_EATER.get(), DeathEaterEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.DEMENTOR.get(), DementorEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.BOWTRUCKLE.get(), BowtruckleEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.MOONCALF.get(), MooncalfEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.BROOM.get(), BroomEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.TROLL.get(), TrollEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.PHOENIX.get(), PhoenixEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.THUNDERBIRD.get(), ThunderbirdEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.SNOWY_OWL.get(), SnowyOwlEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.THESTRAL.get(), ThestralEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.UNICORN.get(), UnicornEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.HIPPOGRIFF.get(), HippogriffEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.ACROMANTULA.get(), AcromantulaEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.WEREWOLF.get(), WerewolfEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.GRINDYLOW.get(), GrindylowEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.KNEAZLE.get(), KneazleEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.BASILISK.get(), BasiliskEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.PATRONUS.get(), PatronusEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.WIZARD_TRADER.get(), WizardTraderEntity.createAttributes());
        // Protego Diabolica alev ejderhası — Mob tabanlı olduğu için attribute kaydı ZORUNLU
        // (eksikse doğum anında crash); AI yok, yalnız MAX_HEALTH taşıyıcı olarak gerekli.
        FabricDefaultAttributeRegistry.register(ModEntities.DIABOLICA_DRAGON.get(), FiendfyreDragonEntity.createAttributes());

        // HogCraft portu — doğal spawn'lar HER YARATIK KENDİ uygun vanilla biyomuna
        // dağıtıldı, tek bir "büyücü biyomu"na sıkıştırılmadı. Ağırlıklar kullanıcı
        // geri bildirimi üzerine yükseltildi ("nadir olmamalı") — bkz. gloomwood.json.
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.DARK_FOREST, Biomes.WINDSWEPT_FOREST,
                        Biomes.WINDSWEPT_HILLS),
                MobCategory.MONSTER, ModEntities.TROLL.get(), 66, 1, 1);
        // Troll ovalarda da doğsun (kullanıcı isteği: "Plains'te spawn olsun"). Troll ışık
        // şartsız (checkAnyLight) → Plains'te gündüz+gece görünür; weight orta (baskın olmasın).
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW),
                MobCategory.MONSTER, ModEntities.TROLL.get(), 36, 1, 1);
        // Kuşlar (büyü reagent kaynakları: phoenix_quill / thunderbird_feather / owl_charm).
        // Tester "her biyomda çıksınlar + biraz daha sık" istedi → biyom kısıtı KALDIRILDI
        // (foundInOverworld = tüm overworld). Ağırlıklar KASITLI ılımlı (14/16/18): eski
        // 60-70'lik değerler CREATURE havuzunda vanilla hayvanları (koyun 12 / inek 8)
        // %80+ oranında ezip "her yer baykuş, çiftlik hayvanı yok" yapıyordu (adversarial
        // review bulgusu). 14-18 hâlâ koyundan sık → reagent bulmak kolay ama doğal denge korunur.
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(),
                MobCategory.CREATURE, ModEntities.PHOENIX.get(), 20, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(),
                MobCategory.CREATURE, ModEntities.THUNDERBIRD.get(), 22, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(),
                MobCategory.CREATURE, ModEntities.SNOWY_OWL.get(), 24, 1, 2);
        // Mooncalf (mooncalf_dust reagent'ı) — eskiden YALNIZ gloomwood/arcanewood'da
        // doğuyordu, oyuncular pratikte hiç göremiyordu; artık tüm overworld'de mütevazı
        // ağırlıkla da doğar (+ ArcanumSurfaceSpawner mevcut chunk'larda aktif doğurur).
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(),
                MobCategory.CREATURE, ModEntities.MOONCALF.get(), 10, 1, 2);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.DARK_FOREST),
                MobCategory.CREATURE, ModEntities.THESTRAL.get(), 20, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS,
                        Biomes.FLOWER_FOREST),
                MobCategory.CREATURE, ModEntities.UNICORN.get(), 22, 1, 1);
        // Ölümyiyen (Death Eater) yakın-temalı vanilla karanlık ormana da eklendi.
        // RUH EMİCİ (Dementor) ise YALNIZCA gloomwood'da doğar (kullanıcı isteği) —
        // vanilla biyomlara / overworld geneline EKLENMEZ. gloomwood dementor addSpawn'ı
        // aşağıda (weight 450, sürü 3-6) + gloomwood.json biyom-native listesinden gelir.
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.DARK_FOREST),
                MobCategory.MONSTER, ModEntities.DEATH_EATER.get(), 30, 1, 2);
        // Kurtadam — spawn denetimi bulgusu (kullanıcı: "hiç werewolf görmedim"):
        // vanilla biyomlarda HİÇ addSpawn kaydı yoktu (yalnız gloomwood + dark_forest
        // yüzey spawner'ı). Gece avcısı temasıyla karanlık orman + orman/tayga
        // biyomlarına eklendi; WerewolfEntity.canSpawn zaten geceye kilitli olduğundan
        // gündüz denemeleri elenir, gece MONSTER havuzunda 35 ağırlık makul sıklık verir.
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.DARK_FOREST, Biomes.FOREST,
                        Biomes.BIRCH_FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA,
                        Biomes.OLD_GROWTH_SPRUCE_TAIGA),
                MobCategory.MONSTER, ModEntities.WEREWOLF.get(), 35, 1, 2);

        // Yeni yaratıklar — her biri kendi uygun vanilla biyomuna dağıtıldı.
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS,
                        Biomes.MEADOW, Biomes.WINDSWEPT_FOREST),
                MobCategory.CREATURE, ModEntities.HIPPOGRIFF.get(), 18, 1, 1);
        // NOT: River/Swamp/Frozen River kasıtlı olarak DIŞARIDA — bu biyomların su
        // sütunu genelde 1-2 blok derinliğinde (Frozen River'da üstelik buzla kaplı),
        // ama Grindylow'un IN_WATER + "pos VE pos.above() su" şartı üst üste 2 dolu
        // su bloğu gerektiriyor. Yalnızca gerçekten derin süregelen su sütunu olan
        // okyanus varyantlarına eklendi, aksi halde 3/4 biyom neredeyse hiç doğurmuyordu.
        // Ağırlık 1 + GrindylowEntity.NATURAL_RARITY ölçümle seçildi (yalnız karanlıkta, drowned ölçeğinde seyrek):
        // ports/_planning26/g9-grindylow/26.1.2-fabric/RAPOR.md
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.OCEAN, Biomes.DEEP_OCEAN,
                        Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN,
                        Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN),
                MobCategory.MONSTER, ModEntities.GRINDYLOW.get(), 1, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.FOREST, Biomes.SUNFLOWER_PLAINS),
                MobCategory.CREATURE, ModEntities.KNEAZLE.get(), 14, 1, 1);
        // Bowtruckle — spawn denetimi bulgusu: YALNIZ özel biyomlara (gloomwood /
        // arcanewood_grove) kayıtlıydı; o biyomları hiç bulamayan oyuncu pratikte hiç
        // göremiyordu. Vanilla orman biyomlarına mütevazı ağırlıkla eklendi
        // (+ ArcanumSurfaceSpawner girdisi mevcut chunk'larda aktif doğurur).
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.FOREST, Biomes.BIRCH_FOREST,
                        Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.FLOWER_FOREST, Biomes.DARK_FOREST),
                MobCategory.CREATURE, ModEntities.BOWTRUCKLE.get(), 14, 1, 2);

        // ---- ÖZEL BİYOM SPAWN'LARI (kök neden düzeltmesi) ----
        // Bu blok, gloomwood/arcanewood_grove için arcanum moblarının TEK kayıt
        // noktasıdır. Biyom JSON'larında (gloomwood.json, arcanewood_grove.json)
        // arcanum girdisi BİLEREK YOKTUR — oradaki "spawners" blokları yalnız vanilla
        // mobları taşır. Eskiden hem JSON'da hem burada tanımlıydılar; bu 2x duplikasyon
        // üretiyordu, JSON tarafı kaldırıldı. Ağırlık/min/max değiştirmek gerekirse
        // YALNIZ burayı düzenleyin; JSON'a arcanum girdisi geri EKLEMEYİN.
        // Gloomwood — CREATURE
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(ArcanumRegion.GLOOMWOOD),
                MobCategory.CREATURE, ModEntities.MOONCALF.get(), 50, 3, 5);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(ArcanumRegion.GLOOMWOOD),
                MobCategory.CREATURE, ModEntities.BOWTRUCKLE.get(), 55, 2, 5);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(ArcanumRegion.GLOOMWOOD),
                MobCategory.CREATURE, ModEntities.THESTRAL.get(), 40, 1, 2);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(ArcanumRegion.GLOOMWOOD),
                MobCategory.CREATURE, ModEntities.KNEAZLE.get(), 35, 1, 2);
        // Gloomwood — MONSTER
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(ArcanumRegion.GLOOMWOOD),
                MobCategory.MONSTER, ModEntities.DEATH_EATER.get(), 100, 2, 4);
        // Ruh Emici ağırlğı 450 → 150: 450, gloomwood canavar havuzunun ~%60'ıydı ve
        // biyomdaki her canavar seçimini pratikte tek türe kilitliyordu ("çok sık
        // doguyor" şikayeti). Toplam canavar YOĞUNLUĞUNU ağırlıklar belirlemez
        // (mob-cap belirler) — bu değişiklik KARIŞIMI düzeltir; gerçek yoğunluk tavanı
        // gloomwood.json'daki "spawn_costs" → arcanum:dementor girdisidir.
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(ArcanumRegion.GLOOMWOOD),
                MobCategory.MONSTER, ModEntities.DEMENTOR.get(), 150, 3, 6);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(ArcanumRegion.GLOOMWOOD),
                MobCategory.MONSTER, ModEntities.WEREWOLF.get(), 90, 1, 2);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(ArcanumRegion.GLOOMWOOD),
                MobCategory.MONSTER, ModEntities.ACROMANTULA.get(), 90, 2, 4);
        // Arcanewood Grove — CREATURE
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(ArcanumRegion.ARCANEWOOD_GROVE),
                MobCategory.CREATURE, ModEntities.MOONCALF.get(), 45, 1, 3);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(ArcanumRegion.ARCANEWOOD_GROVE),
                MobCategory.CREATURE, ModEntities.BOWTRUCKLE.get(), 45, 1, 3);

        // Imperio (İtaat Laneti) kurbanının kendi sol/sağ tıklarını iptal et
        ImperiusInputBlocker.register();

        // İksir tarifleri (brewing stand) — normal büyücülük tezgahı reçeteleriyle (crafting)
        // karıştırılmasın: bunlar data-driven değil, kod ile kaydediliyor (vanilla kısıtı).
        FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
            // NOT: ModPotions.holderOf(...) kullanılıyor — RegistrySupplier'ı doğrudan
            // Holder<Potion> olarak geçmek, kayıt/NBT için kanonik olmayan bir referans
            // üretir (bkz. ModPotions.holderOf javadoc'u — mob_effect eşdeğeriyle
            // sunucu çökme hatası bulundu).
            // Mana Hızlandırma İksiri: Garip İksir + Anka tüyü → mana yenilenmesi %40 hızlı
            builder.registerPotionRecipe(Potions.AWKWARD,
                    Ingredient.of(ModItems.PHOENIX_FEATHER.get()), ModPotions.holderOf(ModPotions.MANA_HASTE_POTION));
            // Exstimulo: Güç İksiri + 3 farklı kuş tüyü (sırayla) → güç ×1.5, mana %20 hızlı, cooldown %20 az
            builder.registerPotionRecipe(Potions.STRENGTH,
                    Ingredient.of(ModItems.PHOENIX_FEATHER.get()), ModPotions.holderOf(ModPotions.EXSTIMULO_STAGE1));
            builder.registerPotionRecipe(ModPotions.holderOf(ModPotions.EXSTIMULO_STAGE1),
                    Ingredient.of(ModItems.THUNDERBIRD_FEATHER.get()), ModPotions.holderOf(ModPotions.EXSTIMULO_STAGE2));
            builder.registerPotionRecipe(ModPotions.holderOf(ModPotions.EXSTIMULO_STAGE2),
                    Ingredient.of(ModItems.SNOWY_OWL_FEATHER.get()), ModPotions.holderOf(ModPotions.EXSTIMULO_POTION));
            // Girding: Garip İksir + Troll derisi → %40 dayanıklılık + %20 hız
            builder.registerPotionRecipe(Potions.AWKWARD,
                    Ingredient.of(ModItems.TROLL_HIDE.get()), ModPotions.holderOf(ModPotions.GIRDING_POTION));
        });

        // ---- Hile komutu: yalnızca cheats/OP seviye 2 açıkken kullanılabilir ----
        // '/arcanum spells unlock' → oyuncuya TÜM büyüleri öğretir (test/creative kolaylığı;
        // dev istemcisi her açılışta rastgele kullanıcı adı verdiği için öğrenilen büyüler
        // "kaybolmuş" görünüyor — bu komutla tek seferde hepsi geri açılır).
        // '/arcanum spells lock' → oyuncunun tüm büyülerini sıfırlar (ilerlemeyi baştan test için).
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("arcanum")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("spells")
                                .then(Commands.literal("unlock").executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    ArcanumPlayerData data = ArcanumPlayerData.get(p.getServer());
                                    int added = 0;
                                    for (Spell s : ModSpells.SPELLS) {
                                        if (data.learn(p, s.id())) {
                                            added++;
                                        }
                                    }
                                    ArcanumNetwork.syncKnownSpells(p);
                                    final int n = added;
                                    ctx.getSource().sendSuccess(
                                            () -> Component.translatable("arcanum.command.unlock_all", n), true);
                                    return ModSpells.SPELLS.size();
                                }))
                                .then(Commands.literal("lock").executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    ArcanumPlayerData.get(p.getServer()).forgetAll(p);
                                    ArcanumNetwork.syncKnownSpells(p);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.translatable("arcanum.command.lock_all"), true);
                                    return 1;
                                })))
                        // '/arcanum spawntest' → kaynak konumunun etrafına Arcanum biyom
                        // yaratıklarından 2'şer tane doğurur (doğal spawn oranını beklemeden
                        // görsel doğrulama). Oyuncu GEREKTİRMEZ (getPosition/getLevel) — konsoldan
                        // ve RCON'dan da çalışır.
                        .then(Commands.literal("spawntest").executes(ctx -> {
                            var src = ctx.getSource();
                            net.minecraft.server.level.ServerLevel level = src.getLevel();
                            net.minecraft.world.phys.Vec3 center = src.getPosition();
                            java.util.List<net.minecraft.world.entity.EntityType<?>> types =
                                    java.util.List.<net.minecraft.world.entity.EntityType<?>>of(
                                            ModEntities.MOONCALF.get(), ModEntities.BOWTRUCKLE.get(),
                                            ModEntities.THESTRAL.get(), ModEntities.KNEAZLE.get(),
                                            ModEntities.DEATH_EATER.get(), ModEntities.DEMENTOR.get(),
                                            ModEntities.WEREWOLF.get(), ModEntities.ACROMANTULA.get());
                            int spawned = 0;
                            int idx = 0;
                            for (net.minecraft.world.entity.EntityType<?> type : types) {
                                for (int k = 0; k < 2; k++) {
                                    double ang = (Math.PI * 2.0 * idx) / (types.size() * 2);
                                    net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(
                                            center.x + Math.cos(ang) * 2.5, center.y, center.z + Math.sin(ang) * 2.5);
                                    if (type.spawn(level, pos, net.minecraft.world.entity.MobSpawnType.COMMAND) != null) {
                                        spawned++;
                                    }
                                    idx++;
                                }
                            }
                            final int n = spawned;
                            src.sendSuccess(() -> Component.translatable("arcanum.command.spawntest", n), true);
                            return spawned;
                        }))
                        // '/arcanum maxxp' → büyücü seviyesini direkt config tavanına yapar (test için).
                        .then(Commands.literal("maxxp").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            // Tam tavana yetecek XP'yi hesapla (config tavanı yükseltilmiş
                            // olabilir — sabit 1M yetmeyebilir); addXp ses+mesaj+client sync yapar.
                            int maxLvl = ArcanumPlayerData.maxLevel();
                            ArcanumPlayerData d = ArcanumPlayerData.get(p.getServer());
                            int need = -d.getXp(p);
                            for (int l = d.getLevel(p); l < maxLvl; l++) {
                                need += ArcanumPlayerData.xpToNext(l);
                            }
                            if (need > 0) {
                                ArcanumLeveling.addXp(p, need);
                            }
                            ctx.getSource().sendSuccess(
                                    () -> Component.translatable("arcanum.command.maxxp", maxLvl), true);
                            return maxLvl;
                        }))
                        // '/arcanum spawncheck' → bulunulan yerde hangi Arcanum yaratıklarının
                        // garanti spawnlanabileceğini raporlar + hemen çevrene bir grup doğurmayı
                        // dener (yüzey spawner'ını anında test/kanıt için).
                        .then(Commands.literal("spawncheck").executes(ctx -> {
                            var src = ctx.getSource();
                            net.minecraft.server.level.ServerLevel lvl = src.getLevel();
                            net.minecraft.world.phys.Vec3 pos = src.getPosition();
                            net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(pos);
                            for (String line : ArcanumSurfaceSpawner.eligibilityReport(lvl, bp)) {
                                src.sendSuccess(() -> Component.literal(line), false);
                            }
                            int spawned = ArcanumSurfaceSpawner.forceBurst(lvl, pos.x, pos.z, 16);
                            src.sendSuccess(
                                    () -> Component.literal("-> hemen dogan: " + spawned + " (16 deneme)"), false);
                            return spawned;
                        }))
                        // '/arcanum reloadconfig' → config/arcanum.json'u oyunu kapatmadan yeniden okur.
                        .then(Commands.literal("reloadconfig").executes(ctx -> {
                            com.arcanum.config.ArcanumConfig.reload();
                            // Çevrimiçi oyunculara anında sync: G ekranı "Level X/Y" ve kalan
                            // puan gösterimi bir sonraki magic-data olayını beklemesin.
                            for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                ArcanumNetwork.syncMagicData(p);
                            }
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Arcanum config yeniden yüklendi (config/arcanum.json)."), true);
                            return 1;
                        })));
        });
    }
}
