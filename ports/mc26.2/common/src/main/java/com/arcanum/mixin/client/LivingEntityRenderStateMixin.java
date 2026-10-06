package com.arcanum.mixin.client;

import com.arcanum.client.duck.ArcanumRenderStateFlags;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * {@link LivingEntityRenderState}'e Arcanum görünmezlik bayraklarını ekler (duck:
 * {@link ArcanumRenderStateFlags}). Alt sınıflar (ArmedEntity/Humanoid/Avatar render-state)
 * alanları miras alır. Varsayılan {@code false}: bayrakları yazan tek yer
 * {@link LivingEntityRendererMixin} — extract edilmemiş (elle yaratılmış) state hiçbir katmanı gizlemez.
 */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements ArcanumRenderStateFlags {

    @Unique
    private boolean arcanum$cloaked;

    @Unique
    private boolean arcanum$umbraForm;

    @Override
    public boolean arcanum$isCloaked() {
        return this.arcanum$cloaked;
    }

    @Override
    public void arcanum$setCloaked(boolean cloaked) {
        this.arcanum$cloaked = cloaked;
    }

    @Override
    public boolean arcanum$isUmbraForm() {
        return this.arcanum$umbraForm;
    }

    @Override
    public void arcanum$setUmbraForm(boolean umbraForm) {
        this.arcanum$umbraForm = umbraForm;
    }
}
