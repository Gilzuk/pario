package com.example.pairo;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

/** The 57 card symbols: 10 shapes × 6 colours, drawn as vector paths. */
final class Symbols {
    private Symbols() {}

    static final int[] PALETTE = {
            0xFFE63946, // red
            0xFFF2A900, // yellow
            0xFF2A9D50, // green
            0xFF1E78E6, // blue
            0xFF8A46C8, // purple
            0xFF3A3A4A  // black
    };
    static final int SHAPE_COUNT = 10;
    private static final Path[] SHAPES = buildShapes();

    private static final Path tmp = new Path();
    private static final Matrix matrix = new Matrix();
    private static final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * Maps a deck symbol to its look (shape + colour). Without this, symbols on the
     * same deck "line" would get consecutive looks and some cards would be one colour.
     * 13 is coprime with 57, so this is a permutation.
     */
    static int look(int symbol) { return (symbol * 13) % Core.SYMBOLS; }

    /** Draws a deck symbol centred at (cx, cy); size is the symbol's diameter. */
    static void draw(Canvas canvas, int symbol, float cx, float cy, float size, float rotDeg) {
        drawLook(canvas, look(symbol), cx, cy, size, rotDeg);
    }

    static void drawLook(Canvas canvas, int look, float cx, float cy, float size, float rotDeg) {
        int color = PALETTE[look / SHAPE_COUNT];
        matrix.setScale(size, size);
        matrix.postRotate(rotDeg);
        matrix.postTranslate(cx, cy);
        SHAPES[look % SHAPE_COUNT].transform(matrix, tmp);
        fill.setColor(color);
        canvas.drawPath(tmp, fill);
        stroke.setColor(darker(color));
        stroke.setStrokeWidth(Math.max(1.2f, size * 0.045f));
        canvas.drawPath(tmp, stroke);
    }

    static int darker(int c) {
        return Color.argb(Color.alpha(c), (int) (Color.red(c) * 0.7f),
                (int) (Color.green(c) * 0.7f), (int) (Color.blue(c) * 0.7f));
    }

    // Shapes in unit coordinates, centred on the origin, roughly 1 unit across.
    private static Path[] buildShapes() {
        Path[] s = new Path[SHAPE_COUNT];

        s[0] = circle(0, 0, 0.45f);

        s[1] = new Path();
        s[1].addRoundRect(-0.4f, -0.4f, 0.4f, 0.4f, 0.07f, 0.07f, Path.Direction.CW);

        s[2] = poly(0, -0.5f, 0.47f, 0.36f, -0.47f, 0.36f);

        s[3] = star(5, 0.52f, 0.22f);

        Path heart = new Path();
        heart.moveTo(0, 0.45f);
        heart.cubicTo(-0.75f, -0.05f, -0.35f, -0.65f, 0, -0.22f);
        heart.cubicTo(0.35f, -0.65f, 0.75f, -0.05f, 0, 0.45f);
        heart.close();
        s[4] = heart;

        s[5] = poly(0, -0.5f, 0.34f, 0, 0, 0.5f, -0.34f, 0);

        s[6] = star(3, 0.47f, 0.47f); // equal radii: a regular hexagon

        Path cross = new Path();
        cross.addRect(-0.15f, -0.45f, 0.15f, 0.45f, Path.Direction.CW);
        Path bar = new Path();
        bar.addRect(-0.45f, -0.15f, 0.45f, 0.15f, Path.Direction.CW);
        cross.op(bar, Path.Op.UNION);
        s[7] = cross;

        Path moon = circle(0, 0, 0.45f);
        moon.op(circle(0.18f, -0.15f, 0.4f), Path.Op.DIFFERENCE);
        s[8] = moon;

        Path ring = circle(0, 0, 0.45f);
        ring.op(circle(0, 0, 0.22f), Path.Op.DIFFERENCE);
        s[9] = ring;

        return s;
    }

    private static Path circle(float cx, float cy, float r) {
        Path p = new Path();
        p.addCircle(cx, cy, r, Path.Direction.CW);
        return p;
    }

    private static Path poly(float... xy) {
        Path p = new Path();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        p.close();
        return p;
    }

    private static Path star(int points, float outer, float inner) {
        Path p = new Path();
        for (int i = 0; i < points * 2; i++) {
            double a = -Math.PI / 2 + i * Math.PI / points;
            float r = (i % 2 == 0) ? outer : inner;
            float x = (float) (r * Math.cos(a)), y = (float) (r * Math.sin(a));
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
        return p;
    }
}
