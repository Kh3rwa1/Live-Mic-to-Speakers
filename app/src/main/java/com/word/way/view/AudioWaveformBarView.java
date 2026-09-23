package com.word.way.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

public class AudioWaveformBarView extends View {
    public static final int THEME_PEACH = 0;
    public static final int THEME_PURPLE = 1;
    public static final int THEME_MINT = 2;

    private static final int NUM_BARS = 32;
    private final float[] barRatios = new float[NUM_BARS];
    private final RectF rect = new RectF();
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int theme = THEME_PEACH;
    private float progress = 0.35f;

    public AudioWaveformBarView(Context context) {
        super(context);
        init();
    }

    public AudioWaveformBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AudioWaveformBarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        activePaint.setStyle(Paint.Style.FILL);
        inactivePaint.setStyle(Paint.Style.FILL);
        setWaveformSeed(42);
        updateThemeColors();
    }

    public void setTheme(int theme) {
        this.theme = theme;
        updateThemeColors();
        invalidate();
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0.0f, Math.min(1.0f, progress));
        invalidate();
    }

    public void setWaveformSeed(long seed) {
        java.util.Random rnd = new java.util.Random(seed);
        float prev = 0.4f;
        for (int i = 0; i < NUM_BARS; i++) {
            float target = 0.25f + rnd.nextFloat() * 0.70f;
            float smooth = (prev + target) / 2.0f;
            barRatios[i] = Math.max(0.20f, Math.min(1.0f, smooth));
            prev = target;
        }
        invalidate();
    }

    private void updateThemeColors() {
        switch (theme) {
            case THEME_PURPLE:
                activePaint.setColor(0xFF0B6FD6);
                inactivePaint.setColor(0xFFC9DFF8);
                break;
            case THEME_MINT:
                activePaint.setColor(0xFF1A9BF0);
                inactivePaint.setColor(0xFFD6EAFD);
                break;
            case THEME_PEACH:
            default:
                activePaint.setColor(0xFF4A7AB5);
                inactivePaint.setColor(0xFFD8E4F2);
                break;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float totalSpacing = (float) w / NUM_BARS;
        float barWidth = Math.max(2.0f, totalSpacing * 0.55f);
        float radius = barWidth / 2.0f;
        float cy = h / 2.0f;
        float maxHalfHeight = (h / 2.0f) - 2.0f;

        for (int i = 0; i < NUM_BARS; i++) {
            float cx = i * totalSpacing + totalSpacing / 2.0f;
            float halfHeight = Math.max(radius * 1.5f, barRatios[i] * maxHalfHeight);
            rect.set(cx - radius, cy - halfHeight, cx + radius, cy + halfHeight);

            float barProgress = (float) (i + 1) / NUM_BARS;
            Paint paint = barProgress <= progress ? activePaint : inactivePaint;
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }
}
