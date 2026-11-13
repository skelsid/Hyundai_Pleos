package dev.vehiclelog.ui

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Figma DROP_SHADOW (퍼짐 0) 을 둥근 사각형 뒤에 그린다. 경계 밖으로 그리므로 부모들이 자식을 자르지 않아야 보인다.
 * Figma 흐림 값은 CSS box-shadow 흐림과 같아 가우스 시그마 = 흐림 / 2 다.
 * 안드로이드 BlurMaskFilter 반지름은 시그마 = 0.57735 x 반지름 + 0.5 로 바뀌므로 거꾸로 풀어 넣는다.
 */
internal class ShadowDrawable(private val cornerRadiusPx: Float, private val shadows: List<Shadow>) : Drawable() {

    /** offsetYPx: 아래로 민 거리, blurPx: Figma 흐림 값, color: 그림자 색(ARGB). */
    class Shadow(val offsetYPx: Float, blurPx: Float, val color: Int) {
        val maskRadius: Float = ((blurPx / 2f - SIGMA_BIAS) / SIGMA_PER_RADIUS).coerceAtLeast(MIN_RADIUS)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        for (shadow in shadows) {
            paint.color = shadow.color
            paint.maskFilter = BlurMaskFilter(shadow.maskRadius, BlurMaskFilter.Blur.NORMAL)
            rect.set(bounds)
            rect.offset(0f, shadow.offsetYPx)
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Drawable.getOpacity 는 API 29 부터 쓰이지 않지만 추상 메서드라 구현한다")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        // Skia SkBlurMask::ConvertRadiusToSigma
        const val SIGMA_PER_RADIUS = 0.57735f
        const val SIGMA_BIAS = 0.5f
        // BlurMaskFilter 는 0 보다 큰 반지름만 받는다
        const val MIN_RADIUS = 0.1f
    }
}
