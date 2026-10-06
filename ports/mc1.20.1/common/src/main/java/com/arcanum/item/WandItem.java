package com.arcanum.item;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.arcanum.client.WandItemRenderer;
import com.arcanum.data.ArcanumPlayerData;
import com.arcanum.spell.ModSpells;
import com.arcanum.spell.Spell;
import com.arcanum.spell.SpellCastTime;
import com.arcanum.spell.SpellFx;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Asa — GeckoLib animasyonlu (3B, ışıyan uç + emissive glowmask + kast animasyonu).
 * Sağ-tık (TEK BASIŞ): aktif büyü için bir cast başlatır ({@link CastManager}); basılı
 * tutmaya gerek YOK — cast time dolunca büyü kendiliğinden atılır. G: radyal büyü menüsü.
 * Kademeler için {@link WandTier}. Büyüler kitapla öğrenilir (ArcanumPlayerData).
 *
 * <p>1.20.1 portu: FabricItem arayüzü kaldırıldı (yalnız Fabric'te derlenir, hiçbir
 * metodu override edilmiyordu — davranış farkı yok); GeckoLib importları 4.8.4'ün
 * {@code core.*} paket düzenine indi ve renderer köprüsü {@code GeoRenderProvider}
 * yerine {@code RenderProvider} + zorunlu {@code createRenderer}/{@code getRenderProvider}
 * çiftine döndü (javap ile doğrulandı, bkz. research-api-diff-1.20.1.md §10).
 */
public class WandItem extends Item implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.wand.idle");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.wand.cast");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /**
     * 1.20.1 GeckoLib: SingletonGeoAnimatable.getRenderProvider abstract — makeRenderer köprüsü
     * zorunlu. DİKKAT: {@code GeoItem.makeRenderer} YALNIZ FABRIC jar'ında var; forge
     * jar'ında yok (Forge köprüsü IClientItemExtensions — forge modülündeki WandItemMixin +
     * WandClientExtensions kurar). Bu yüzden alan TEMBEL doldurulur: makeRenderer çağrısı,
     * yalnızca Fabric GeckoLib'in çağırdığı {@link #getRenderProvider()} içine ertelendi.
     * Eager alan başlatıcı Forge dedicated server'da item kaydı sırasında
     * NoSuchMethodError ile ÇÖKÜYORDU (F5 boot tur 1 kök nedeni).
     */
    private Supplier<Object> renderProvider;
    private final WandTier tier;

    /** Kanal BAŞLANGICINDA not edilen büyü index'i (per-player). onUseTick her tick aktif
     *  slotu yeniden okuduğundan, kanal ORTASINDA slot değiştiren oyuncu (modifier+scroll /
     *  slot keybind) vulnera'nın 1 sn hazırlığını ve >=40 mana başlangıç kapısını
     *  atlatabiliyordu (elapsed zaten >20, use() yeniden çağrılmıyor). Kanal büyüsü
     *  DEĞİŞTİYSE kanal kesilir — yeniden başlamak use() kapılarından geçmek zorunda kalır.
     *  Temizlik: releaseUsing (her kanal bitişi) + {@link #channelForget} (disconnect). */
    private static final java.util.Map<java.util.UUID, Integer> CHANNEL_SPELL =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Oyuncu çıkışında kanal notunu temizle (Spells.vulneraForget deseni — sızıntı önleme). */
    public static void channelForget(java.util.UUID id) {
        CHANNEL_SPELL.remove(id);
        // Protego Diabolica ejderhası: ömrü zaten keepalive'a bağlı (tick akmazsa dağılır),
        // bu yalnız harita hijyeni — çıkan oyuncunun kaydı beklemeden düşsün.
        com.arcanum.spell.DiabolicaDragonManager.forget(id);
    }

    public WandItem(WandTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        // Sunucudan tetiklenen "cast" animasyonunun istemcide bulunabilmesi için
        // zorunlu GeckoLib senkron kaydı — yoksa animasyon hiç oynamaz.
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public WandTier tier() {
        return tier;
    }

    // 10. tur: mana artık ASADA değil OYUNCUDA (ArcanumPlayerData). getMana/inventoryTick
    // (regen) + allowComponentsUpdateAnimation kaldırıldı — mana stack komponentini değiştirmiyor.
    // Regen ManaRegen.tick'te (per-player), mana kontrol/düşme ArcanumPlayerData üzerinden.

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Büyüler TEK BASIŞ + zamanlayıcı ile atılır (basılı tutma YOK). Tüm mantık
        // sunucu-yetkilidir: cast CastManager'da başlatılır, her sunucu tick'inde geri
        // sayılır, süre dolunca ateşlenir.
        if (!(level instanceof ServerLevel server)) {
            // İstemci: kontrol/partikül YOK. Sağ-tık paketi sunucuya yine de gider;
            // gerçek iş orada yapılır. Burada nötr geç.
            return InteractionResultHolder.pass(stack);
        }

        // UMBRAVOLO FORM KESMESİ: kara duman formundayken asayla sağ tık = ANINDA normale
        // dön (cast time YOK, cooldown/mana kontrolüne girmez, hangi büyü seçili olursa
        // olsun). Bu kesme cast boru hattından ÖNCE döndüğü için formdayken başka hiçbir
        // büyü castlenemez (kullanıcı spesifikasyonu). Bkz. UmbraFormManager.
        if (player instanceof net.minecraft.server.level.ServerPlayer spUmbra
                && com.arcanum.spell.UmbraFormManager.isActive(player)) {
            com.arcanum.spell.UmbraFormManager.exit(spUmbra);
            player.swing(hand);
            return InteractionResultHolder.consume(stack);
        }

        // Crucio (Cruciatus Laneti) ile kıvranırken asa castlenemez ("elin işe yaramaz").
        // (1.20.1: hasEffect doğrudan MobEffect alır — holderOf köprüsü kalktı.)
        if (player.hasEffect(com.arcanum.registry.ModMobEffects.CRUCIO.get())) {
            player.swing(hand);
            return InteractionResultHolder.fail(stack);
        }

        // Aynı anda tek cast: hâlihazırda bekleyen bir cast varsa yeni basışı yok say.
        if (CastManager.isCasting(player)) {
            return InteractionResultHolder.pass(stack);
        }

        List<Spell> spells = ModSpells.SPELLS;
        // Cast artık AKTİF DİZİLİM SLOTUNA bağlı (eski SELECTED_SPELL döngüsü kaldırıldı;
        // slot gezinme Shift+tekerlek, sayfa +/- ile client tarafından yapılır).
        int idx = ArcanumPlayerData.get(server.getServer()).activeSpellIndex(player);
        if (idx < 0 || idx >= spells.size()) {
            // aktif slot boş — nazik geri bildirim, kanal başlatma (eski davranış korunur)
            player.displayClientMessage(
                    Component.translatable("arcanum.tooltip.no_spell").withStyle(ChatFormatting.GRAY), true);
            // 1.20.1: UI_BUTTON_CLICK bir Holder<SoundEvent> — playSound düz SoundEvent ister (.value()).
            server.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 0.8f);
            player.swing(hand);
            return InteractionResultHolder.fail(stack);
        }

        Spell s = spells.get(idx);
        // PASİFLEŞTİRİLMİŞ büyü (rictusempra) castlenemez — loadout sanitize bu index'i
        // normalde -1'e çevirir; burası bayat duruma karşı savunma katmanı (gri mesaj).
        if (ModSpells.isDisabled(s.id())) {
            player.displayClientMessage(
                    Component.translatable("arcanum.spell_disabled").withStyle(ChatFormatting.GRAY), true);
            player.swing(hand);
            return InteractionResultHolder.fail(stack);
        }
        // ---- START on-kontrolleri (mana burada DÜŞÜLMEZ; ateşte castSelected düşer ki
        //      iptal mana yakmasın). Biri başarısızsa mevcut geri bildirim + kanal YOK. ----
        if (!knows(server, player, s)) {
            player.displayClientMessage(
                    Component.translatable("arcanum.spell_locked", s.name()).withStyle(ChatFormatting.GRAY), true);
            server.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 0.8f);
            player.getCooldowns().addCooldown(this, 8);
            player.swing(hand);
            return InteractionResultHolder.fail(stack);
        }
        if (!player.isCreative()
                && ArcanumPlayerData.get(server.getServer()).getMana(player) < s.manaCost()) {
            player.displayClientMessage(
                    Component.translatable("arcanum.no_mana").withStyle(ChatFormatting.RED), true);
            server.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.0f);
            player.getCooldowns().addCooldown(this, 6);
            player.swing(hand);
            return InteractionResultHolder.fail(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            // hâlâ beklemede — sessiz reddet, kanal yok
            player.swing(hand);
            return InteractionResultHolder.fail(stack);
        }

        // KANAL BÜYÜLERİ (protego_maxima + protego_diabolica): cast time YOK — sağ tık
        // BASILI TUTARAK kanallanır. CastManager'ı baypas et; mana onUseTick'te akar
        // (30/sn), kalkan orada canlı tutulur.
        if (isChanneledSpell(s.id())) {
            CHANNEL_SPELL.put(player.getUUID(), idx); // kanal hangi büyüyle başladı — onUseTick karşılaştırır
            player.startUsingItem(hand);
            if (s.id().equals("protego_diabolica")) {
                // mavi ateş çemberi — tutuşma sesiyle açılır
                SpellFx.soundAt(server, player, SoundEvents.FIRECHARGE_USE, 0.7f, 1.1f);
            } else if (s.id().equals("vulnera_sanentur")) {
                // şifa kanalı — yumuşak rezonans (hazırlık evresinin başladığını duyurur)
                SpellFx.soundAt(server, player, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.3f);
            } else {
                SpellFx.soundAt(server, player, SoundEvents.BEACON_ACTIVATE, 0.6f, 1.6f);
            }
            return InteractionResultHolder.consume(stack);
        }

        // Hepsi OK → cast'i başlat (mana/cooldown ATEŞTE düşer, iptal yakmasın diye).
        // Cast-time artık ASAYA bağlı: temel süre × wand.castTimeMult (Phoenix hızlı, Elder yavaş).
        CastManager.start(player, idx, Math.max(1, Math.round(SpellCastTime.ticks(s) * tier.castTimeMult())));
        // İlk telegraf: karşı oyuncu "bu AK castliyor!" deyip kaçabilsin diye ANINDA
        // SADECE zeminde tek rün halkası (server-side, herkes görür).
        SpellFx.runeRing(server, player.position(), s.color(), 9, 1.1);
        player.swing(hand);
        return InteractionResultHolder.success(stack);
    }

    // ---- KANAL BÜYÜLERİ basılı-tut kanalı (protego_maxima + protego_diabolica) ----
    // Yalnız kanal büyüleri startUsingItem çağırır; diğer büyüler CastManager kullandığından
    // bu use-item metotları onlar için tetiklenmez (asa "kullanımda" olmaz).

    /** Sağ tık BASILI TUTARAK kanallanan büyüler — startUsingItem/onUseTick boru hattı. */
    private static boolean isChanneledSpell(String id) {
        return id.equals("protego_maxima") || id.equals("protego_diabolica")
                || id.equals("vulnera_sanentur");
    }

    @Override
    public int getUseDuration(ItemStack stack) { // 1.20.1: tek parametreli imza
        return 72000; // pratikte sınırsız kanal — mana bitince ya da bırakınca durur
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        // Her kanal bitişinde (bırakma VEYA stopUsingItem) başlangıç notunu temizle —
        // yeni kanal use() içinde yeniden yazar.
        if (entity instanceof Player p) {
            CHANNEL_SPELL.remove(p.getUUID());
        }
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof Player player)) {
            return;
        }
        int idx = ArcanumPlayerData.get(server.getServer()).activeSpellIndex(player);
        if (idx < 0 || idx >= ModSpells.SPELLS.size()
                || !isChanneledSpell(ModSpells.SPELLS.get(idx).id())) {
            player.stopUsingItem(); // aktif büyü artık bir kanal büyüsü değil → kanalı bitir
            return;
        }
        // KANAL ORTASINDA BÜYÜ DEĞİŞİMİ KORUMASI: kanal use()'daki kapılardan (>=manaCost,
        // hazırlık evresi) yalnız BAŞLANGIÇ büyüsü için geçti. Aktif slot kanal sırasında
        // başka bir kanal büyüsüne çevrildiyse (modifier+scroll / slot keybind) kanalı kes —
        // aksi halde vulnera 1 sn hazırlığı ve 40-mana kapısı atlanırdı.
        Integer startedIdx = CHANNEL_SPELL.get(player.getUUID());
        if (startedIdx == null || startedIdx != idx) {
            player.stopUsingItem();
            return;
        }
        Spell channeled = ModSpells.SPELLS.get(idx);
        // VULNERA SANENTUR: kendi boru hattı (1 sn hazırlık + 33/sn kesirli drenaj + şifa
        // akışı) tamamen Spells.vulneraSanenturTick'te — aşağıdaki 30/sn protego drenajına
        // GİRMEZ. elapsed = toplam kanal süresi (72000 geriye sayar).
        if (channeled.id().equals("vulnera_sanentur")) {
            com.arcanum.spell.Spells.vulneraSanenturTick(
                    server, player, stack, getUseDuration(stack) - remainingUseTicks);
            return;
        }
        // Mana akışı: 30/sn = her 2 tickte 3 mana (oyuncu manasından). Yetmezse kanalı kes.
        // Yaratıcı modda mana akmaz — kalkan bedava canlı kalır (tester isteği).
        if (!player.isCreative() && server.getGameTime() % 2L == 0L) {
            ArcanumPlayerData data = ArcanumPlayerData.get(server.getServer());
            if (data.getMana(player) < 3) {
                player.displayClientMessage(
                        Component.translatable("arcanum.no_mana").withStyle(ChatFormatting.RED), true);
                player.getCooldowns().addCooldown(this, 10);
                player.stopUsingItem();
                return;
            }
            data.spendMana(player, 3);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.arcanum.network.ArcanumNetwork.syncMagicData(sp);
            }
        }
        // Kalkanı canlı tut ({@link ProtegoShield}) + büyüye özgü görsel
        // (maxima: mavi kubbe; diabolica: mavi ateş çemberi).
        if (channeled.id().equals("protego_diabolica")) {
            com.arcanum.spell.Spells.protegoDiabolica(server, player, stack);
        } else {
            com.arcanum.spell.Spells.protegoMaxima(server, player, stack);
        }
    }

    /**
     * Bekleyen bir cast'in ATEŞLENMESİ — {@link CastManager#tick} tarafından çağrılır.
     * Oyuncunun elindeki asayı bulur ve mevcut cast mantığını ({@link #castSelected})
     * çalıştırır: knows / mana-düşür / effect / cooldown / anim. Asa artık elde değilse
     * sessizce iptal (null-güvenli). Mana ve cooldown YALNIZCA burada (ateş anında)
     * uygulanır; erken iptal hiçbir şey yakmaz.
     */
    public static void executeCast(ServerLevel server, Player player, int spellIndex) {
        List<Spell> spells = ModSpells.SPELLS;
        if (spellIndex < 0 || spellIndex >= spells.size()) {
            return;
        }
        Spell s = spells.get(spellIndex);
        ItemStack wandStack = null;
        WandItem wand = null;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof WandItem w) {
                wandStack = held;
                wand = w;
                break;
            }
        }
        if (wand == null) {
            return; // asa artık elde değil → cast iptal
        }
        wand.castSelected(server, player, wandStack, s);
        // atıştan sonraki son çember de AYAK hizasında (telegraf halkasıyla aynı zemin
        // rünü) — göz/el hizasında değil.
        SpellFx.runeRing(server, player.position(), s.color(), 9, 1.1);
    }

    private void castSelected(ServerLevel server, Player player, ItemStack stack, Spell s) {
        // öğrenme kilidi: temel büyüler serbest, diğerleri kitap ister
        if (!knows(server, player, s)) {
            player.displayClientMessage(
                    Component.translatable("arcanum.spell_locked", s.name()).withStyle(ChatFormatting.GRAY), true);
            server.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 0.8f);
            player.getCooldowns().addCooldown(this, 8);
            return;
        }

        ArcanumPlayerData data = ArcanumPlayerData.get(server.getServer());
        // Yaratıcı modda mana harcanmaz (tester isteği) — spendMana hiç çağrılmaz.
        if (!player.isCreative() && !data.spendMana(player, s.manaCost())) { // mana OYUNCUDA
            player.displayClientMessage(
                    Component.translatable("arcanum.no_mana").withStyle(ChatFormatting.RED), true);
            server.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.0f);
            player.getCooldowns().addCooldown(this, 6);
            return;
        }
        // "İskaladın mı?" izleyicisi: cast bir şeye vurmazsa cooldown yarıya iner.
        com.arcanum.spell.Spells.castHitSomething = false;
        s.effect().cast(server, player, stack);

        // Cast başına çok ufak XP + mana/xp client sync (addXp içinde). Protego Maxima
        // CastManager'ı baypas ettiği için burada XP gelmez (spam yok).
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.arcanum.spell.ArcanumLeveling.addXp(sp, 1);
        }

        // YENİ cooldown: temel × 1.2 (global denge) × skill cooldownMult. Asa cooldown YOK, Exstimulo YOK.
        // Cooldown'suz büyüler (umbravolo): vanilla addCooldown HİÇ çağrılmaz — vanilla
        // ItemCooldowns use()'u bloklayıp form-çıkış kesmesini imkânsızlaştırıyordu; 4 tick
        // taban dahi uygulanmaz ki sağ-tık çıkışı her an garantili kalsın.
        double cdMult = 1.2 * data.cooldownMult(player);
        int cd = s.cooldown() <= 0 ? 0 : Math.max(4, (int) Math.round(s.cooldown() * cdMult));
        if (cd > 0 && !com.arcanum.spell.Spells.castHitSomething) {
            cd = Math.max(4, cd / 2); // isabetsiz kast: yarı bekleme
        }
        if (cd > 0) {
            player.getCooldowns().addCooldown(this, cd);
        }
        triggerAnim(player, GeoItem.getOrAssignId(stack, server), "base", "cast");
    }

    public static boolean knows(ServerLevel server, Player player, Spell s) {
        // Artık HİÇBİR kademe (basic dahil) bedava değil — bkz. SpellGating javadoc'u.
        // Player burada ServerPlayer olmak zorunda değildir (bazı çağrılar sahte/simüle
        // oyuncu kullanabilir); bu yüzden SpellGating.knows yerine doğrudan
        // ArcanumPlayerData'ya bakılır (aynı tek-kaynak mantık, ServerPlayer şartı yok).
        return ArcanumPlayerData.get(server.getServer()).knows(player, s.id());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        // 1.20.1: TooltipContext yok — ikinci parametre @Nullable Level (javap ile doğrulandı).
        // 10. tur: asada BÜYÜ/MANA gösterilmez — yalnızca flavor satırı + Power + Cast-Time
        // (kullanıcı isteği). Mana artık oyuncunun kendi değeri; asa yalnız güç+kast süresi.
        tooltip.add(Component.translatable("arcanum.tooltip.tier." + tier.assetId())
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("arcanum.tooltip.wand.power",
                        String.format("%.2f", tier.powerMult()))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("arcanum.tooltip.wand.casttime",
                        String.format("%.2f", tier.castTimeMult()))
                .withStyle(ChatFormatting.AQUA));
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 4, state -> state.setAndContinue(IDLE))
                .triggerableAnim("cast", CAST));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // 1.20.1 GeckoLib 4.8.4: createRenderer + getRenderProvider ABSTRACT'tır (uygulamak
    // zorunlu); 1.21.1'deki createGeoRenderer/GeoRenderProvider'ın birebir karşılığı.
    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private WandItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new WandItemRenderer(tier.assetId());
                }
                return this.renderer;
            }
        });
    }

    @Override
    public Supplier<Object> getRenderProvider() {
        // Tembel init: makeRenderer'a ilk (ve tek) dokunuş burada — bu metodu yalnız
        // Fabric GeckoLib çağırır, Forge'da hiç çağrılmaz → NoSuchMethodError tetiklenmez.
        if (this.renderProvider == null) {
            this.renderProvider = GeoItem.makeRenderer(this);
        }
        return this.renderProvider;
    }
}
