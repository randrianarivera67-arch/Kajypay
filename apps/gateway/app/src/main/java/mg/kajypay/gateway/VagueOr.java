package mg.kajypay.gateway;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/** Bandeau de vagues dorées animées (haut ou bas de l'écran). */
public class VagueOr extends View {
    private final Paint p1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint p2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path chemin = new Path();
    private final boolean haut;
    private float phase = 0f;

    public VagueOr(Context c, boolean enHaut) {
        super(c);
        haut = enHaut;
        p1.setStyle(Paint.Style.FILL);
        p2.setStyle(Paint.Style.FILL);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w <= 0) return;
        p1.setShader(new LinearGradient(0, 0, w, 0,
            new int[]{0xFFF6D98B, 0xFFE8B94A, 0xFFC99326, 0xFFE8B94A, 0xFFF6D98B},
            new float[]{0f, 0.25f, 0.5f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        p2.setShader(new LinearGradient(0, 0, w, 0,
            new int[]{0x66E8B94A, 0x99F6D98B, 0x66C99326},
            new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }

    private void vague(Canvas c, Paint p, float amplitude, float decalage, float hauteurBase) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        chemin.reset();
        float base = haut ? h * hauteurBase : h * (1f - hauteurBase);
        if (haut) chemin.moveTo(0, 0); else chemin.moveTo(0, h);
        chemin.lineTo(0, base);
        int pas = Math.max(6, w / 60);
        for (int x = 0; x <= w; x += pas) {
            double t = (double) x / w * Math.PI * 2.4 + phase + decalage;
            float y = base + (float) Math.sin(t) * amplitude;
            chemin.lineTo(x, y);
        }
        if (haut) { chemin.lineTo(w, 0); } else { chemin.lineTo(w, h); }
        chemin.close();
        c.drawPath(chemin, p);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int h = getHeight();
        if (h <= 0) return;
        vague(c, p2, h * 0.16f, 1.1f, 0.52f);
        vague(c, p1, h * 0.13f, 0f, 0.42f);
        phase += 0.018f;
        if (phase > Math.PI * 2) phase -= Math.PI * 2;
        postInvalidateOnAnimation();
    }
}
