package com.arcanum.util;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.object.LoopType;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

/**
 * GeckoLib 4.7.7'nin "tetiklenen klip bitince handler'a dön" davranışını 5.5.x'e geri getiren
 * {@link AnimationController}.
 *
 * <p><b>Neden gerekli (kaynaktan kanıtlı, geckolib-*-26.1.2-5.5.2 ve 26.2-5.5.5 decompile):</b>
 * 5.x {@code checkControllerState} handler'ı yalnız
 * {@code triggeredAnimTime == NOT_TRIGGERED || handlesTriggeredAnimations} iken çağırır;
 * {@code triggeredAnimTime} yalnız {@code setAnimation(farklı anim)} (handler içinden),
 * {@code stopTriggeredAnimation()} ve {@code reset()} ile sıfırlanır. Tetiklenen {@code play_once}
 * klip bitince {@code progressExistingAnimation} yalnız {@code timelineTime = FINISHED_ANIMATING (-2)}
 * yazar — tetik alanı SIFIRLANMAZ, handler bir daha ÇAĞRILMAZ, {@code isAnimatingBones()} false
 * döner. Sonuç: tek controller'da idle/walk + tetik tutan varlık ilk saldırıdan sonra bir sonraki
 * tetiğe kadar rest pozunda donar. 4.7.7'de ise {@code handleAnimationState} klip bitince
 * ({@code hasAnimationFinished}) {@code triggeredAnimation = null} yapıp AYNI karede handler'ı
 * çağırıyordu.
 *
 * <p><b>Neden res-geckolib5 §3.4 "güvenlik ağı" değil:</b> {@code receiveTriggeredAnimations()} +
 * {@code isPlayingTriggeredAnimation()} kalıbı tetiğin İLK karesinde bozulur:
 * {@code triggerAnimation()} {@code animationPoint = null} yapar → {@code isAnimatingBones()} false →
 * {@code isPlayingTriggeredAnimation()} false → handler {@code setAnimation(IDLE)} çağırır →
 * tetik daha hiç oynamadan iptal olur.
 *
 * <p><b>Bu sınıf:</b> handler'lara dokunmadan, tetik klibinin SON karesine ulaşıldığında
 * {@code triggeredAnimTime}'ı {@code NOT_TRIGGERED}'a çeker; bir sonraki karede handler çalışır,
 * {@code setAnimation(idle/walk)} {@code transitionFromPoint}'i klibin son pozu olarak alır ve
 * {@code transitionTicks} boyunca harmanlar — 4.7.7 ile aynı (5.x'in eklediği "reset"
 * geçişine girilmez). {@code hold_on_last_frame} klipler 4.7.7'deki gibi (PAUSED) son karede
 * kalır, handler'a dönmez.
 *
 * <p><b>26.2 doğrulaması:</b> kullanılan korumalı üyeler ({@code checkControllerState} imzası,
 * {@code triggeredAnimTime}, {@code timeline}, {@code timelineTime}, {@code animationPoint},
 * {@code NOT_TRIGGERED = -1}, {@code FINISHED_ANIMATING = -2}) fabric 5.5.5 ve forge 5.5.6'da
 * {@code javap -p -constants} ile 5.5.2'ye birebir aynı; {@code AnimationController} 5.5.5 ↔ 5.5.6
 * bayt kodu aynı. 5.5.3+ farkları (STOP dalında nokta yeniden hesabı kalktı, {@code hasAnimationFinished}
 * artık {@code FINISHED_ANIMATING}'i de sayar, reset-geçiş noktası klip-yerel zamana çekildi) bu sınıfın
 * akışını değiştirmez: tetik temizliği yine klibin son karesinde, handler bir sonraki karede çalışır.
 */
public class TriggerReturningController<T extends GeoAnimatable> extends AnimationController<T> {

    public TriggerReturningController(String name, int transitionTicks, AnimationStateHandler<T> stateHandler) {
        super(name, transitionTicks, stateHandler);
    }

    @Override
    protected boolean checkControllerState(T animatable, GeoRenderState renderState,
                                           AnimatableManager<T> manager, GeoModel<T> geoModel) {
        boolean animating = super.checkControllerState(animatable, renderState, manager, geoModel);

        if (this.triggeredAnimTime != NOT_TRIGGERED) {
            if (this.timeline == null) {
                // tetik klibi modelde yok (timeline kurulamadı) → controller'ı handler'a bırak,
                // sonsuza dek boş kalmasın
                this.triggeredAnimTime = NOT_TRIGGERED;
            } else if (this.animationPoint != null && triggeredClipEnded()) {
                this.triggeredAnimTime = NOT_TRIGGERED;
            }
        }
        return animating;
    }

    /** Tetik klibi (son aşaması) bitti mi — 4.7.7 {@code hasAnimationFinished} karşılığı. */
    private boolean triggeredClipEnded() {
        if (this.timelineTime == FINISHED_ANIMATING) {
            return true;
        }
        if (this.timelineTime < this.timeline.lastAnimationEndTime()) {
            return false; // geçiş harmanı ya da klip hâlâ sürüyor
        }
        LoopType loop = this.animationPoint.loopType() == LoopType.DEFAULT
                ? this.animationPoint.animation().loopType()
                : this.animationPoint.loopType();
        return loop != LoopType.HOLD_ON_LAST_FRAME;
    }
}
