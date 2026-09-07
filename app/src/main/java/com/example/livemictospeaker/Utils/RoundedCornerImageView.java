package com.example.livemictospeaker.Utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;


public class RoundedCornerImageView extends AppCompatImageView {
    private Path path;
    private float radius = 15.0f;
     RectF rect;

    public RoundedCornerImageView(Context context) {
        super(context);
        init();
    }

    public RoundedCornerImageView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        init();
    }

    public RoundedCornerImageView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        init();
    }

    private void init() {
        this.path = new Path();
    }

    public void onDraw(Canvas canvas) {
        RectF rectF = new RectF(0.0f, 0.0f, (float) getWidth(), (float) getHeight());
        this.rect = rectF;
        Path path2 = this.path;
        float f = this.radius;
        path2.addRoundRect(rectF, f, f, Path.Direction.CW);
        canvas.clipPath(this.path);
        super.onDraw(canvas);
    }
}
