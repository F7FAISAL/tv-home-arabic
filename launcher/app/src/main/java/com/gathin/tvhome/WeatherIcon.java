package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * The clean view's weather icon, drawn from a WMO code in plain white strokes: sun or moon, cloud, rain, snow,
 * fog, thunder. Code instead of image files, so there is nothing more to license or ship.
 */
final class WeatherIcon extends Drawable {

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int code;
    private boolean day = true;

    WeatherIcon() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(0xFFFFFFFF);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xFFFFFFFF);
    }

    void set(int code, boolean day) {
        this.code = code;
        this.day = day;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas c) {
        float s = Math.min(getBounds().width(), getBounds().height());
        float x0 = getBounds().left, y0 = getBounds().top;
        stroke.setStrokeWidth(s * 0.07f);
        c.save();
        c.translate(x0, y0);
        boolean clear = code == 0;
        boolean partly = code == 1 || code == 2;
        if (clear) {
            sky(c, s / 2, s / 2, s * 0.22f, s);
        } else {
            if (partly) sky(c, s * 0.36f, s * 0.34f, s * 0.15f, s * 0.7f);
            cloud(c, s);
            float top = s * 0.74f;
            if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) {
                for (int i = 0; i < 3; i++) {
                    float x = s * (0.34f + 0.16f * i);
                    c.drawLine(x, top, x - s * 0.06f, top + s * 0.16f, stroke);
                }
            } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
                for (int i = 0; i < 3; i++) c.drawCircle(s * (0.34f + 0.16f * i), top + s * 0.09f, s * 0.035f, fill);
            } else if (code == 45 || code == 48) {
                c.drawLine(s * 0.2f, top + s * 0.04f, s * 0.8f, top + s * 0.04f, stroke);
                c.drawLine(s * 0.28f, top + s * 0.16f, s * 0.72f, top + s * 0.16f, stroke);
            } else if (code >= 95) {
                Path bolt = new Path();
                bolt.moveTo(s * 0.54f, top - s * 0.02f);
                bolt.lineTo(s * 0.43f, top + s * 0.12f);
                bolt.lineTo(s * 0.53f, top + s * 0.12f);
                bolt.lineTo(s * 0.45f, top + s * 0.24f);
                c.drawPath(bolt, stroke);
            }
        }
        c.restore();
    }

    /** The sun with rays by day, a crescent outline by night: line icons, like the cloud. */
    private void sky(Canvas c, float cx, float cy, float r, float s) {
        if (day) {
            c.drawCircle(cx, cy, r, stroke);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                float in = r * 1.45f, out = r * 1.95f;
                c.drawLine(cx + (float) Math.cos(a) * in, cy + (float) Math.sin(a) * in,
                        cx + (float) Math.cos(a) * out, cy + (float) Math.sin(a) * out, stroke);
            }
        } else {
            Path moon = new Path();
            moon.addCircle(cx, cy, r * 1.3f, Path.Direction.CW);
            Path bite = new Path();
            bite.addCircle(cx + r * 0.7f, cy - r * 0.5f, r * 1.1f, Path.Direction.CW);
            moon.op(bite, Path.Op.DIFFERENCE);
            c.drawPath(moon, stroke);
        }
    }

    private void cloud(Canvas c, float s) {
        Path p = new Path();
        p.addArc(new RectF(s * 0.18f, s * 0.42f, s * 0.46f, s * 0.70f), 90, 180);
        p.arcTo(new RectF(s * 0.32f, s * 0.26f, s * 0.70f, s * 0.64f), 200, 140);
        p.arcTo(new RectF(s * 0.56f, s * 0.42f, s * 0.84f, s * 0.70f), 270, 180);
        p.close();
        c.drawPath(p, stroke);
    }

    @Override
    public void setAlpha(int alpha) {
        stroke.setAlpha(alpha);
        fill.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        stroke.setColorFilter(cf);
        fill.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
