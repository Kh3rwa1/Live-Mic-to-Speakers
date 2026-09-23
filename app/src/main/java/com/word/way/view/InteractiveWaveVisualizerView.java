package com.word.way.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * High-fidelity interactive soundwave visualizer matching the 3D studio screens.
 * Features:
 * - Fluid pastel wave ribbon backdrop flowing across the stage
 * - 14 contoured vertical soundwave bars (7 on left, 7 on right)
 *   that dynamically react to real-time audio volume and frequency with physics spring damping
 * - Symmetrical floating 3D spheres (blue and pink) and outer particle beads
 * - Soft concentric halo rings around the central 3D glass bubble
 * - Color theme support for Live Mic vs Hold to Speak
 */
public class InteractiveWaveVisualizerView extends View {

    public static final int THEME_LIVE = 0;
    public static final int THEME_HOLD_TO_SPEAK = 1;

    private static final int BARS_PER_SIDE = 7;
    // Gaussian/acoustic contour profiles (innermost i=0 near bubble to outermost i=6)
    private static final float[] LEFT_PROFILE = {0.22f, 0.40f, 0.68f, 0.98f, 0.70f, 0.42f, 0.20f};
    private static final float[] RIGHT_PROFILE = {0.22f, 0.45f, 0.72f, 0.98f, 0.65f, 0.38f, 0.20f};

    // Frequency perturbation factors for organic voice flutter
    private static final float[] LEFT_FREQ = {1.8f, 2.3f, 3.1f, 2.7f, 3.4f, 2.1f, 1.6f};
    private static final float[] RIGHT_FREQ = {2.9f, 3.5f, 2.4f, 3.8f, 2.6f, 1.9f, 2.2f};

    private int colorTheme = THEME_LIVE;

    private final Paint ribbonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint leftBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rightBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spherePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path ribbonPath = new Path();
    private final RectF barRect = new RectF();
    private final float[] currentLeftHeights = new float[BARS_PER_SIDE];
    private final float[] currentRightHeights = new float[BARS_PER_SIDE];

    private float smoothedLevel = 0.0f;
    private float targetLevel = 0.0f;
    private boolean isRecording = false;
    private boolean isRunning = false;

    private long lastFrameTime = 0;
    private float idlePhase = 0.0f;

    private float density = 1.0f;
    private float barWidth;
    private float barSpacing;
    private float minBarHeight;
    private float maxBarHeight;
    private float centerRadius;

    public InteractiveWaveVisualizerView(Context context) {
        super(context);
        init();
    }

    public InteractiveWaveVisualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public InteractiveWaveVisualizerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        barWidth = 6.0f * density;
        barSpacing = 11.5f * density;
        minBarHeight = 10.0f * density;
        maxBarHeight = 64.0f * density;
        centerRadius = 78.0f * density;

        ribbonPaint.setStyle(Paint.Style.FILL);

        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStrokeWidth(1.5f * density);

        spherePaint.setStyle(Paint.Style.FILL);
        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setColor(0xD0FFFFFF);

        leftBarPaint.setStyle(Paint.Style.FILL);
        rightBarPaint.setStyle(Paint.Style.FILL);

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /**
     * Sets the color palette theme (THEME_LIVE or THEME_HOLD_TO_SPEAK).
     */
    public void setColorTheme(int theme) {
        this.colorTheme = theme;
        updateThemeShaders();
        postInvalidateOnAnimation();
    }

    /**
     * Updates the visualizer with live audio peak level (0 to 100).
     */
    public void setAudioLevel(int level) {
        int clamped = Math.max(0, Math.min(100, level));
        targetLevel = clamped / 100.0f;
        if (targetLevel > 0.05f && !isRecording) {
            isRecording = true;
        }
        postInvalidateOnAnimation();
    }

    /**
     * Toggles recording state for dynamic vs idle wave modes.
     */
    public void setRecording(boolean recording) {
        this.isRecording = recording;
        if (!recording) {
            targetLevel = 0.0f;
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateThemeShaders();
    }

    private void updateThemeShaders() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cy = h / 2.0f;
        float top = cy - maxBarHeight;
        float bottom = cy + maxBarHeight;

        // Background fluid wave ribbon: ice blue to steel
        ribbonPaint.setShader(new LinearGradient(
                0, 0, w, 0,
                new int[]{0x228FC6F8, 0x301A9BF0, 0x304A7AB5, 0x22D6EAFD},
                new float[]{0.0f, 0.35f, 0.70f, 1.0f},
                Shader.TileMode.CLAMP));

        int[] purpleGrad = new int[]{0xFF7CC4F8, 0xFF1A9BF0, 0xFF0B6FD6};
        int[] coralGrad = new int[]{0xFFA9C6E4, 0xFF4A7AB5, 0xFF232B3B};
        float[] gradPositions = new float[]{0.0f, 0.5f, 1.0f};

        if (colorTheme == THEME_HOLD_TO_SPEAK) {
            // Left is coral, right is purple
            leftBarPaint.setShader(new LinearGradient(0, top, 0, bottom, coralGrad, gradPositions, Shader.TileMode.CLAMP));
            rightBarPaint.setShader(new LinearGradient(0, top, 0, bottom, purpleGrad, gradPositions, Shader.TileMode.CLAMP));
        } else {
            // Left is purple, right is coral
            leftBarPaint.setShader(new LinearGradient(0, top, 0, bottom, purpleGrad, gradPositions, Shader.TileMode.CLAMP));
            rightBarPaint.setShader(new LinearGradient(0, top, 0, bottom, coralGrad, gradPositions, Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        isRunning = true;
        lastFrameTime = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        isRunning = false;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        isRunning = (visibility == VISIBLE) && getVisibility() == VISIBLE;
        if (isRunning) {
            lastFrameTime = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        // View.GONE still stays attached; stop the frame loop to save CPU/battery.
        boolean shouldRun = visibility == VISIBLE && getWindowVisibility() == VISIBLE;
        if (isRunning && !shouldRun) isRunning = false;
        else if (!isRunning && shouldRun) {
            isRunning = true;
            lastFrameTime = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        long now = SystemClock.uptimeMillis();
        float dt = (lastFrameTime > 0) ? Math.min(0.06f, (now - lastFrameTime) / 1000.0f) : 0.016f;
        lastFrameTime = now;

        idlePhase += dt * 2.2f;

        // Smooth physics spring/decay for input audio level
        if (targetLevel > smoothedLevel) {
            // Fast responsive attack
            smoothedLevel += (targetLevel - smoothedLevel) * Math.min(1.0f, dt * 18.0f);
        } else {
            // Smooth natural decay
            smoothedLevel += (targetLevel - smoothedLevel) * Math.min(1.0f, dt * 6.5f);
        }

        float cx = width / 2.0f;
        float cy = height / 2.0f;

        // 1. Draw flowing pastel fluid ribbon backdrop
        drawFluidRibbon(canvas, width, cy);

        // 2. Draw subtle concentric halo rings around center bubble
        drawRippleRings(canvas, cx, cy);

        // 3. Compute and draw left soundwave bars
        drawSideBars(canvas, cx, cy, true);

        // 4. Compute and draw right soundwave bars
        drawSideBars(canvas, cx, cy, false);

        // 5. Draw floating 3D spheres and outer beads
        drawFloatingSpheres(canvas, cx, cy);

        if (isRunning) {
            postInvalidateOnAnimation();
        }
    }

    private void drawFluidRibbon(Canvas canvas, int width, float cy) {
        float waveOffset = (float) Math.sin(idlePhase * 0.7f) * (6.0f * density);
        ribbonPath.reset();
        ribbonPath.moveTo(0, cy - (20.0f * density) + waveOffset);
        ribbonPath.cubicTo(
                width * 0.25f, cy - (35.0f * density) + waveOffset,
                width * 0.45f, cy + (45.0f * density) - waveOffset,
                width * 0.70f, cy + (15.0f * density) + waveOffset);
        ribbonPath.cubicTo(
                width * 0.85f, cy - (10.0f * density) + waveOffset,
                width * 0.95f, cy - (30.0f * density) - waveOffset,
                width, cy - (20.0f * density));
        ribbonPath.lineTo(width, cy + (55.0f * density));
        ribbonPath.cubicTo(
                width * 0.80f, cy + (75.0f * density) - waveOffset,
                width * 0.50f, cy + (80.0f * density) + waveOffset,
                width * 0.20f, cy + (50.0f * density) - waveOffset);
        ribbonPath.lineTo(0, cy + (30.0f * density));
        ribbonPath.close();

        canvas.drawPath(ribbonPath, ribbonPaint);
    }

    private void drawRippleRings(Canvas canvas, float cx, float cy) {
        float dynamicGlow = smoothedLevel * 10.0f * density;
        float r1 = centerRadius + 6.0f * density + dynamicGlow * 0.4f;
        float r2 = centerRadius + 18.0f * density + dynamicGlow * 0.8f;

        // Inner soft ripple
        ripplePaint.setColor(Color.argb(Math.min(255, (int) (26 + smoothedLevel * 45)), 168, 85, 247));
        canvas.drawCircle(cx, cy, r1, ripplePaint);

        // Outer soft ripple
        ripplePaint.setColor(Color.argb(Math.min(255, (int) (18 + smoothedLevel * 35)), 244, 63, 94));
        canvas.drawCircle(cx, cy, r2, ripplePaint);
    }

    private void drawSideBars(Canvas canvas, float cx, float cy, boolean isLeft) {
        float[] profile = isLeft ? LEFT_PROFILE : RIGHT_PROFILE;
        float[] freq = isLeft ? LEFT_FREQ : RIGHT_FREQ;
        float[] currentHeights = isLeft ? currentLeftHeights : currentRightHeights;
        Paint paint = isLeft ? leftBarPaint : rightBarPaint;

        for (int i = 0; i < BARS_PER_SIDE; i++) {
            // Gentle breathing wave modulation
            float breath = (float) (Math.sin(idlePhase + i * 0.5f) * 0.5f + 0.5f);

            float targetHeight;
            if (isRecording && smoothedLevel > 0.01f) {
                // Interactive frequency flutter synced to voice amplitude
                float voiceFlutter = (float) (Math.sin(idlePhase * freq[i] + i * 0.9f) * 0.35f + 0.65f);
                float activeRange = (maxBarHeight - minBarHeight);
                targetHeight = minBarHeight + activeRange * profile[i] * smoothedLevel * voiceFlutter
                        + breath * (4.0f * density);
            } else {
                // Calm idle breathing wave
                targetHeight = minBarHeight + (profile[i] * 22.0f * density) * breath;
            }

            // Low-pass filter bar heights for fluid organic movement
            currentHeights[i] += (targetHeight - currentHeights[i]) * 0.28f;
            float h = Math.max(minBarHeight, Math.min(maxBarHeight, currentHeights[i]));

            // Bar X placement: step outward from the circular glass bubble edge
            float offsetX = centerRadius + (8.0f * density) + (i * barSpacing);
            float barX = isLeft ? (cx - offsetX) : (cx + offsetX);

            float halfH = h / 2.0f;
            barRect.set(barX - barWidth / 2.0f, cy - halfH, barX + barWidth / 2.0f, cy + halfH);
            float cornerRadius = barWidth / 2.0f;
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, paint);
        }
    }

    private void drawFloatingSpheres(Canvas canvas, float cx, float cy) {
        float blueBob = (float) (Math.sin(idlePhase * 1.3f) * (4.0f * density));
        float pinkBob = (float) (Math.sin(idlePhase * 1.6f + 1.2f) * (5.0f * density));

        float blueX, blueY, pinkX, pinkY;
        int leftBeadColor, rightBeadColor;

        if (colorTheme == THEME_HOLD_TO_SPEAK) {
            // In Hold to Speak: Blue sphere top-left, Pink sphere bottom-right
            blueX = cx - centerRadius - (26.0f * density);
            blueY = cy - (44.0f * density) + blueBob;

            pinkX = cx + centerRadius + (26.0f * density);
            pinkY = cy + (44.0f * density) + pinkBob;

            leftBeadColor = 0xC0F43F5E;
            rightBeadColor = 0xC0A855F7;
        } else {
            // In Live Mic: Blue sphere bottom-left, Pink sphere top-right
            blueX = cx - centerRadius - (24.0f * density);
            blueY = cy + (44.0f * density) + blueBob;

            pinkX = cx + centerRadius + (28.0f * density);
            pinkY = cy - (46.0f * density) + pinkBob;

            leftBeadColor = 0xC0A855F7;
            rightBeadColor = 0xC0F43F5E;
        }

        // Draw Blue Sphere
        float blueRadius = 10.0f * density;
        spherePaint.setShader(new RadialGradient(
                blueX - blueRadius * 0.35f, blueY - blueRadius * 0.35f, blueRadius * 1.25f,
                new int[]{0xFFBFDBFE, 0xFF60A5FA, 0xFF2563EB},
                new float[]{0.0f, 0.55f, 1.0f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(blueX, blueY, blueRadius, spherePaint);
        canvas.drawCircle(blueX - blueRadius * 0.32f, blueY - blueRadius * 0.32f, blueRadius * 0.28f, highlightPaint);

        // Draw Steel Sphere
        float pinkRadius = 13.0f * density;
        spherePaint.setShader(new RadialGradient(
                pinkX - pinkRadius * 0.35f, pinkY - pinkRadius * 0.35f, pinkRadius * 1.25f,
                new int[]{0xFFD6EAFD, 0xFF4A7AB5, 0xFF232B3B},
                new float[]{0.0f, 0.55f, 1.0f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(pinkX, pinkY, pinkRadius, spherePaint);
        canvas.drawCircle(pinkX - pinkRadius * 0.32f, pinkY - pinkRadius * 0.32f, pinkRadius * 0.28f, highlightPaint);

        // Outer wave bead dots
        float outerOffset = centerRadius + (8.0f * density) + (BARS_PER_SIDE * barSpacing);

        spherePaint.setShader(null);
        spherePaint.setColor(leftBeadColor);
        canvas.drawCircle(cx - outerOffset, cy, 3.5f * density, spherePaint);

        spherePaint.setColor(rightBeadColor);
        canvas.drawCircle(cx + outerOffset, cy, 3.5f * density, spherePaint);
    }
}
