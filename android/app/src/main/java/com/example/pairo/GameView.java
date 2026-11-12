package com.example.pairo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The whole game UI in one custom view: menu, level select, rules, timed rounds
 * (Solo Sprint and levels) and Duel. All layout is done in dp: the canvas is
 * scaled by the screen density and touch points are divided by it, so the
 * numbers below match on every phone.
 */
public final class GameView extends View {

    private enum Screen { MENU, LEVELS, RULES, SOLO, DUEL }

    private interface SymbolTap { void onTap(int symbol, float sx, float sy); }

    private static final class Btn {
        final RectF r;
        final Runnable action;
        Btn(RectF r, Runnable action) { this.r = r; this.action = action; }
    }

    /** A tappable card; hit-testing follows the same rotation the card was drawn with. */
    private static final class CardHit {
        final Core.PlayCard card;
        final float cx, cy, d, rotDeg;
        final SymbolTap onTap;
        CardHit(Core.PlayCard card, float cx, float cy, float d, float rotDeg, SymbolTap onTap) {
            this.card = card; this.cx = cx; this.cy = cy; this.d = d; this.rotDeg = rotDeg; this.onTap = onTap;
        }
    }

    /** The dialog shown before and after a round. */
    private static final class Panel {
        String title, subtitle, big, body, primary;
        Runnable primaryAction;
        /** Earned stars to reveal (results), or -1. */
        int stars = -1;
        /** Star thresholds to show (level intro), or null. */
        int[] goals;
        final List<String> links = new ArrayList<>();
        final List<Runnable> linkActions = new ArrayList<>();

        Panel link(String label, Runnable action) {
            links.add(label);
            linkActions.add(action);
            return this;
        }
    }

    /** One visual effect: burst ring, burst dot, floating text, miss cross, or confetti piece. */
    private static final class Fx {
        static final int RING = 0, DOT = 1, TEXT = 2, CROSS = 3, CONFETTI = 4;
        int kind, color;
        float x, y, vx, vy, r, life, max, rot, vr, w, h;
        String str;
        boolean flipped;
    }

    /** A symbol drifting slowly behind the menu. */
    private static final class Drift {
        int look;
        float x, y, vx, vy, rot, vr, size;
    }

    private static final int G1 = 0xFF5738DB, G2 = 0xFFF0578C, INK = 0xFF331F73, GOLD = 0xFFF2B600;
    private static final String PREFS = "pairo", BEST_KEY = "bestSolo", LEVELS_KEY = "levelStars";
    private static final long FLY_MS = 320, LEAVE_MS = 260;

    private final float density;
    private final boolean reduced;
    private final Random rnd = new Random();
    private final SharedPreferences prefs;
    private final Sound sound;
    private final Core.Progress progress;
    private final Core.Round round;
    private final Core.Duel duel;
    private Core.PlayCard demo;
    private Screen screen = Screen.MENU;
    private int starsShown;

    private final List<Btn> buttons = new ArrayList<>();
    private final List<CardHit> hits = new ArrayList<>();
    private final List<Fx> fx = new ArrayList<>();
    private final List<Drift> drift = new ArrayList<>();

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path starPath = unitStar();
    private final Path tmpPath = new Path();
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF(0, 0, 0, 0);
    private final float[] xy = new float[2];
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface black = Typeface.create("sans-serif-black", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private LinearGradient gradient;
    private int gradW, gradH;

    // System bar insets, in px.
    private int insetL, insetT, insetR, insetB;
    private long lastTick = Core.now();
    private boolean running;
    private float viewW, viewH; // layout size in dp

    public GameView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        // Respect "Remove animations" / animator scale 0 in system settings.
        reduced = Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sound = new Sound(context, prefs);
        progress = Core.Progress.parse(prefs.getString(LEVELS_KEY, ""));
        round = new Core.Round(rnd, prefs.getInt(BEST_KEY, 0), progress);
        round.bests.put(Core.WHEELS.bestKey, prefs.getInt(Core.WHEELS.bestKey, 0));
        duel = new Core.Duel(rnd);
        demo = new Core.PlayCard(rnd.nextInt(Core.CARDS.length), rnd);
        text.setTextAlign(Paint.Align.CENTER);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeJoin(Paint.Join.ROUND);
        outline.setTextAlign(Paint.Align.CENTER);
        setHapticFeedbackEnabled(true);
        if (!reduced) {
            for (int i = 0; i < 16; i++) {
                Drift s = new Drift();
                s.look = rnd.nextInt(Core.SYMBOLS);
                s.x = rnd.nextFloat(); s.y = rnd.nextFloat();
                s.vx = (rnd.nextFloat() - 0.5f) * 14; s.vy = -6 - rnd.nextFloat() * 14;
                s.rot = rnd.nextFloat() * 360; s.vr = (rnd.nextFloat() - 0.5f) * 34;
                s.size = 22 + rnd.nextFloat() * 30;
                drift.add(s);
            }
        }

        setOnApplyWindowInsetsListener((v, insets) -> {
            readInsets(insets);
            invalidate();
            return insets;
        });
    }

    @SuppressWarnings("deprecation")
    private void readInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets i = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            insetL = i.left; insetT = i.top; insetR = i.right; insetB = i.bottom;
        } else {
            insetL = insets.getSystemWindowInsetLeft();
            insetT = insets.getSystemWindowInsetTop();
            insetR = insets.getSystemWindowInsetRight();
            insetB = insets.getSystemWindowInsetBottom();
        }
    }

    // ------------------------------------------------------------------ Lifecycle

    void resume() {
        running = true;
        lastTick = Core.now(); // don't charge time spent in the background
        sound.resume();
        invalidate();
    }

    void pause() {
        running = false;
        sound.pause();
    }

    void release() {
        sound.release();
    }

    /** Handles the system Back gesture. Returns false when the app should close. */
    boolean goBack() {
        if (screen == Screen.MENU) return false;
        if (screen == Screen.SOLO) {
            round.phase = Core.Round.Phase.READY;
            screen = round.isLevel() ? Screen.LEVELS : Screen.MENU;
        } else {
            screen = Screen.MENU;
        }
        invalidate();
        return true;
    }

    // ------------------------------------------------------------------ Input

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int i = e.getActionIndex();
            tap((e.getX(i) - insetL) / density, (e.getY(i) - insetT) / density);
            invalidate();
        }
        return true;
    }

    private void tap(float x, float y) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Btn b = buttons.get(i);
            if (b.r.contains(x, y)) {
                b.action.run();
                return;
            }
        }
        for (CardHit h : hits) {
            float dx = x - h.cx, dy = y - h.cy;
            if (dx * dx + dy * dy > h.d * h.d / 4) continue;
            double a = Math.toRadians(h.rotDeg);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            Core.Slot best = null;
            float bestDist = Float.MAX_VALUE, bx = 0, by = 0;
            for (Core.Slot s : h.card.slots) {
                Core.slotXY(h.card, s, xy);
                float px = h.cx + (xy[0] * cos - xy[1] * sin) * h.d;
                float py = h.cy + (xy[0] * sin + xy[1] * cos) * h.d;
                float dist = (float) Math.hypot(x - px, y - py);
                if (dist < s.size * h.d * 0.62f && dist < bestDist) {
                    bestDist = dist;
                    best = s;
                    bx = px;
                    by = py;
                }
            }
            if (best != null) h.onTap.onTap(best.symbol, bx, by);
            return;
        }
    }

    private void haptic(boolean good) {
        if (good) {
            performHapticFeedback(Build.VERSION.SDK_INT >= 30
                    ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
        } else {
            performHapticFeedback(Build.VERSION.SDK_INT >= 30
                    ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS);
        }
    }

    private void soloTap(int symbol, float sx, float sy) {
        Core.Result r = round.tap(symbol);
        if (r == Core.Result.MATCH) {
            sound.play(Sound.Effect.MATCH);
            haptic(true);
            burst(sx, sy, colorOf(symbol));
            floatText("+1", sx, sy - 12, Color.WHITE, false);
            if (round.isLevel()) {
                for (int s : round.cfg.stars) if (s == round.score) sound.play(Sound.Effect.STAR);
            }
        } else if (r == Core.Result.MISS) {
            sound.play(Sound.Effect.MISS);
            haptic(false);
            cross(sx, sy, false);
            floatText("−" + round.cfg.penalty + "s", sx, sy - 12, 0xFFFFD1D6, false);
            if (round.phase == Core.Round.Phase.OVER) onRoundOver();
        }
    }

    private void duelTap(int player, int symbol, float sx, float sy) {
        Core.Result r = duel.tap(player, symbol);
        boolean flipped = player == 1;
        if (r == Core.Result.MATCH) {
            sound.play(Sound.Effect.MATCH);
            haptic(true);
            burst(sx, sy, colorOf(symbol));
            floatText("+1", sx, sy + (flipped ? 12 : -12), Color.WHITE, flipped);
            if (duel.winner >= 0) {
                sound.play(Sound.Effect.WIN);
                confetti();
            }
        } else if (r == Core.Result.MISS) {
            sound.play(Sound.Effect.FREEZE);
            haptic(false);
            cross(sx, sy, flipped);
        }
    }

    private void startRound() {
        round.start();
        starsShown = 0;
        lastTick = Core.now();
    }

    private void openLevel(int i) {
        round.setup(Core.LEVELS[i], i);
        screen = Screen.SOLO;
    }

    private void onRoundOver() {
        starsShown = 0;
        if (!round.isLevel()) {
            if (round.newBest) {
                prefs.edit().putInt(round.cfg.bestKey, round.best).apply();
                sound.play(Sound.Effect.WIN);
                confetti();
            } else {
                sound.play(Sound.Effect.TIMEUP);
            }
            return;
        }
        if (round.improved) prefs.edit().putString(LEVELS_KEY, progress.serialize()).apply();
        if (round.passed()) {
            sound.play(Sound.Effect.WIN);
            if (round.stars < 3) confetti();
        } else {
            sound.play(Sound.Effect.TIMEUP);
        }
    }

    // ------------------------------------------------------------------ Frame

    @Override
    protected void onDraw(Canvas canvas) {
        long t = Core.now();
        float dt = Math.min(0.1f, (t - lastTick) / 1000f); // clamp hiccups
        lastTick = t;
        updateGame(t, dt);
        updateFx(dt);
        sound.setHurry(screen == Screen.SOLO && round.phase == Core.Round.Phase.PLAYING && round.timeLeft <= 10);

        int pw = getWidth(), ph = getHeight();
        if (gradient == null || gradW != pw || gradH != ph) {
            gradient = new LinearGradient(0, 0, pw, ph, G1, G2, Shader.TileMode.CLAMP);
            gradW = pw;
            gradH = ph;
        }
        paint.setShader(gradient);
        canvas.drawRect(0, 0, pw, ph, paint);
        paint.setShader(null);

        buttons.clear();
        hits.clear();
        canvas.save();
        canvas.translate(insetL, insetT);
        canvas.scale(density, density);
        float w = (pw - insetL - insetR) / density;
        float h = (ph - insetT - insetB) / density;
        viewW = w;
        viewH = h;
        if (screen == Screen.MENU || screen == Screen.RULES || screen == Screen.LEVELS) drawDrift(canvas, w, h, dt);
        switch (screen) {
            case MENU: drawMenu(canvas, w, h, t); break;
            case LEVELS: drawLevels(canvas, w, h, t); break;
            case RULES: drawRules(canvas, w, h); break;
            case SOLO: drawRound(canvas, w, h, t); break;
            case DUEL: drawDuel(canvas, w, h, t); break;
        }
        drawFx(canvas);
        canvas.restore();

        if (running) postInvalidateOnAnimation();
    }

    private void updateGame(long t, float dt) {
        if (screen != Screen.SOLO) return;
        Core.Countdown c = round.countdown(t);
        if (c == Core.Countdown.TICK) sound.play(Sound.Effect.TICK);
        else if (c == Core.Countdown.GO) sound.play(Sound.Effect.GO);
        round.animate(t, dt, !reduced);
        if (round.tick(dt)) onRoundOver();
        if (round.warnTick()) sound.play(Sound.Effect.TICK);
        // Results: reveal earned stars one by one.
        if (round.phase == Core.Round.Phase.OVER && round.isLevel() && starsShown < round.stars
                && t - round.overAt > 450 + starsShown * 380L) {
            starsShown++;
            sound.play(Sound.Effect.STAR);
            if (starsShown == 3) confetti();
        }
    }

    // ------------------------------------------------------------------ Screens

    private void drawMenu(Canvas c, float w, float h, long t) {
        float cw = Math.min(w - 48, 440), x0 = (w - cw) / 2;
        soundButton(c, w - 56, 14);
        text(c, "Pairo", w / 2, 96, 64, black, Color.WHITE);
        text(c, "Every two cards share exactly one symbol.", w / 2, 132, 16, regular, 0xDDFFFFFF);
        text(c, "Find it first.", w / 2, 155, 16, regular, 0xDDFFFFFF);

        float block = 3 * 84 + 48;
        float d = Math.max(110, Math.min(Math.min(cw * 0.58f, h * 0.28f), h - 180 - 30 - block - 16));
        float cy = 176 + d / 2;
        float bob = reduced ? 0 : (float) Math.sin(t / 900.0) * 4;
        float spin = reduced ? 0 : (t / 1000f) * 7f; // degrees
        drawCard(c, demo, w / 2, cy + bob, d, 0, spin, false, 1f, false, null);
        buttons.add(new Btn(new RectF(w / 2 - d / 2, cy - d / 2, w / 2 + d / 2, cy + d / 2), () -> {
            demo = new Core.PlayCard(rnd.nextInt(Core.CARDS.length), rnd);
            sound.play(Sound.Effect.CLICK);
        }));

        float y = cy + d / 2 + 26;
        int cur = progress.current();
        menuButton(c, x0, y, cw, 72, "Levels", "Level " + (cur + 1) + ": " + Core.LEVELS[cur].name
                + "  ·  " + progress.total() + "/" + Core.LEVELS.length * 3 + " stars", 13,
                () -> screen = Screen.LEVELS, false);
        float half = (cw - 12) / 2;
        int bestSprint = round.bests.getOrDefault(Core.SPRINT.bestKey, 0);
        int bestWheels = round.bests.getOrDefault(Core.WHEELS.bestKey, 0);
        menuButton(c, x0, y + 84, half, 72, "Sprint", bestSprint > 0 ? "Best: " + bestSprint : "60 seconds", 32,
                () -> { round.setup(Core.SPRINT, -1); screen = Screen.SOLO; }, false);
        menuButton(c, x0 + half + 12, y + 84, half, 72, "Wheels", bestWheels > 0 ? "Best: " + bestWheels : "Rings spin", 19,
                () -> { round.setup(Core.WHEELS, -1); screen = Screen.SOLO; }, true);
        menuButton(c, x0, y + 168, cw, 72, "Duel", "Two players, one phone", 4,
                () -> { duel.reset(); screen = Screen.DUEL; }, false);
        text(c, "How to Play", w / 2, y + 274, 17, bold, Color.WHITE);
        buttons.add(new Btn(new RectF(w / 2 - 80, y + 248, w / 2 + 80, y + 288), () -> screen = Screen.RULES));
    }

    private void drawLevels(Canvas c, float w, float h, long t) {
        float cw = Math.min(w - 32, 488), x0 = (w - cw) / 2;
        closeButton(c, x0, 14, () -> screen = Screen.MENU);
        text(c, "Levels", w / 2, 44, 26, black, Color.WHITE);
        String total = progress.total() + "/" + Core.LEVELS.length * 3;
        text.setTextSize(16);
        text.setTypeface(bold);
        float tw = text.measureText(total);
        starIcon(c, x0 + cw - tw - 14, 39, 20, true, false, 1);
        textLeft(c, total, x0 + cw - tw, 45, 16, bold, Color.WHITE);

        int cols = 3;
        float gap = 12, tileW = (cw - gap * (cols - 1)) / cols;
        int rows = (Core.LEVELS.length + cols - 1) / cols;
        float tileH = Math.max(84, Math.min(118, (h - 92 - 16 - gap * (rows - 1)) / rows));
        int cur = progress.current();
        for (int i = 0; i < Core.LEVELS.length; i++) {
            final int level = i;
            Core.Level L = Core.LEVELS[i];
            float x = x0 + (i % cols) * (tileW + gap), y = 80 + (i / cols) * (tileH + gap);
            int stars = progress.stars[i];
            if (progress.unlocked(i)) {
                paint.setColor(Color.WHITE);
                c.drawRoundRect(x, y, x + tileW, y + tileH, 18, 18, paint);
                if (i == cur && stars == 0 && !reduced) {
                    float pulse = 0.5f + 0.5f * (float) Math.sin(t / 260.0);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(4);
                    paint.setColor(withAlpha(GOLD, 0.5f + 0.5f * pulse));
                    c.drawRoundRect(x - 2, y - 2, x + tileW + 2, y + tileH + 2, 20, 20, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                text(c, String.valueOf(i + 1), x + tileW / 2, y + tileH * 0.38f, 28, black, INK);
                text.setTextSize(12);
                text.setTypeface(bold);
                float fs = text.measureText(L.name) > tileW - 12 ? 10.5f : 12;
                text(c, L.name, x + tileW / 2, y + tileH * 0.38f + 20, fs, bold, 0xB3331F73);
                for (int s = 0; s < 3; s++) starIcon(c, x + tileW / 2 + (s - 1) * 22, y + tileH - 18, 18, s < stars, true, 1);
                buttons.add(new Btn(new RectF(x, y, x + tileW, y + tileH), () -> {
                    sound.play(Sound.Effect.CLICK);
                    openLevel(level);
                }));
            } else {
                paint.setColor(0x29FFFFFF);
                c.drawRoundRect(x, y, x + tileW, y + tileH, 18, 18, paint);
                text(c, String.valueOf(i + 1), x + tileW / 2, y + tileH * 0.38f, 28, black, 0x73FFFFFF);
                lockIcon(c, x + tileW / 2, y + tileH - 26, 20, 0x8CFFFFFF);
            }
        }
    }

    private void drawRules(Canvas c, float w, float h) {
        float cw = Math.min(w - 32, 460), x0 = (w - cw) / 2;
        paint.setColor(Color.WHITE);
        c.drawRoundRect(x0, 16, x0 + cw, h - 16, 28, 28, paint);
        float tx = x0 + 24, tw = cw - 48, y = 60;
        textLeft(c, "How to Play", tx, y, 28, black, INK);
        y += 8;
        String[][] sections = {
                {"The secret", "Any two cards share exactly one symbol, never more and never less. Symbols vary "
                        + "in size and rotation, so look carefully."},
                {"Levels", "Twelve levels: small decks first, then the full 8-symbol deck, spinning cards, tiny "
                        + "symbols and reshuffles. Reach the goal before time runs out for one star; score more "
                        + "for two or three. Each level unlocks the next."},
                {"Sprint and Wheels", "Tap the symbol both cards share. A wrong tap costs 3 seconds. Score as "
                        + "much as you can in 60 seconds. In Wheels, each card is two rings turning in opposite "
                        + "directions, and they speed up as you score."},
                {"Duel", "Lay the phone flat between two players. Each finds the symbol shared by their own card "
                        + "and the centre card and taps it on their own card. A wrong tap freezes you for 1.5 "
                        + "seconds. First to 10 wins."}
        };
        for (String[] s : sections) {
            y += 28;
            textLeft(c, s[0], tx, y, 17, bold, INK);
            text.setTextSize(14);
            text.setTypeface(regular);
            for (String line : wrap(s[1], tw)) {
                y += 19;
                textLeft(c, line, tx, y, 14, regular, 0xCC331F73);
            }
        }
        bigButton(c, x0 + 24, Math.min(h - 80, y + 26), cw - 48, 50, "Back", true, () -> screen = Screen.MENU);
    }

    private void drawRound(Canvas c, float w, float h, long t) {
        Core.Round r = round;
        Core.Level cfg = r.cfg;
        boolean isLevel = r.isLevel();
        float cw = Math.min(w - 32, 488), x0 = (w - cw) / 2;
        closeButton(c, x0, 14, () -> {
            r.phase = Core.Round.Phase.READY;
            screen = isLevel ? Screen.LEVELS : Screen.MENU;
        });
        soundButton(c, x0 + 50, 14);
        int secs = (int) Math.ceil(Math.max(0, r.timeLeft));
        boolean playing = r.phase == Core.Round.Phase.PLAYING;
        boolean warn = secs <= 10 && playing;
        float frac = (float) (r.timeLeft - Math.floor(r.timeLeft));
        float pulse = warn && !reduced ? 1 + 0.16f * Math.max(0, (frac - 0.7f) / 0.3f) : 1;
        float left = pill(c, x0 + cw, 17, "Time " + secs, warn, pulse);
        float bump = (t - r.bumpAt) / 250f;
        float bumpScale = bump >= 0 && bump < 1 && !reduced ? 1 + 0.25f * (1 - easeOut(bump)) : 1;
        if (isLevel) scorePill(c, left - 10, 17, r.score, cfg, bumpScale);
        else pill(c, left - 10, 17, "Score " + r.score, false, bumpScale);

        paint.setColor(0x3CFFFFFF);
        c.drawRoundRect(x0, 66, x0 + cw, 74, 4, 4, paint);
        paint.setColor(warn ? 0xFFFFD1D6 : Color.WHITE);
        float barFrac = (float) (Math.max(0, r.timeLeft) / cfg.seconds);
        if (barFrac > 0) c.drawRoundRect(x0, 66, x0 + Math.max(8, cw * barFrac), 74, 4, 4, paint);

        float d = Math.min(cw * 0.82f, (h - 170) / 2f);
        float topCy = 88 + d / 2, handCy = 88 + d + 44 + d / 2;
        boolean hidden = r.phase == Core.Round.Phase.COUNTDOWN;
        SymbolTap onTap = playing ? this::soloTap : null;

        // The previous top card shrinks away upward while the matched card flies up to replace it.
        if (r.leaving != null) {
            float k = (t - r.leavingAt) / (float) LEAVE_MS;
            if (k >= 1 || reduced) r.leaving = null;
            else drawCard(c, r.leaving, w / 2, topCy - 60 * easeOut(k), d * (1 - 0.35f * easeOut(k)),
                    0, r.leaving.angle, false, 1 - k, false, null);
        }
        float fly = Math.min(1, (t - r.top.flyAt) / (float) FLY_MS);
        float e = reduced ? 1 : easeOut(fly);
        drawCard(c, r.top, w / 2, lerp(handCy, topCy, e), d, 0, r.top.angle, false, 1f, hidden,
                fly >= 1 ? onTap : null);
        if (!hidden) {
            String hint = isLevel ? "Level " + (r.level + 1) + ": " + cfg.name
                    : cfg.wheels ? String.format(java.util.Locale.US, "Wheel speed ×%.1f", Core.wheelSpeed(r.score))
                    : "Tap the symbol both cards share";
            text(c, hint, w / 2, 88 + d + 28, 15, bold, 0xDDFFFFFF);
        }
        drawCard(c, r.hand, w / 2, handCy, d, shake(r.shakeAt), r.hand.angle, false, 1f, hidden, onTap);

        if (hidden) {
            float el = (t - r.countdownAt) / 1000f;
            int n = Core.Round.COUNTDOWN_S - (int) Math.floor(el);
            bigNumber(c, String.valueOf(n), w / 2, (topCy + handCy) / 2, el - (float) Math.floor(el));
        } else if (t - r.goAt < 500) {
            bigNumber(c, "Go!", w / 2, (topCy + handCy) / 2, (t - r.goAt) / 500f);
        }

        if (r.phase == Core.Round.Phase.READY) {
            Panel p = new Panel();
            if (isLevel) {
                p.title = "Level " + (r.level + 1);
                p.subtitle = cfg.name;
                p.body = "Get " + cfg.goal + " matches in " + cfg.seconds + " seconds. " + cfg.tip;
                p.goals = cfg.stars;
                p.link("Levels", () -> screen = Screen.LEVELS);
            } else {
                p.title = cfg.name;
                p.body = cfg.wheels
                        ? "Each card is two wheels turning in opposite directions. Tap the symbol both cards "
                        + "share before it spins away. The wheels speed up with every match. A wrong tap costs 3 seconds."
                        : "Tap the one symbol that appears on both cards. Every match scores a point. "
                        + "A wrong tap costs 3 seconds.";
                p.link("Menu", () -> screen = Screen.MENU);
            }
            p.primary = "Start";
            p.primaryAction = this::startRound;
            panel(c, w, h, p);
        } else if (r.phase == Core.Round.Phase.OVER) {
            Panel p = new Panel();
            if (!isLevel) {
                p.title = r.newBest ? "New best!" : "Time's up!";
                p.big = String.valueOf(r.score);
                p.body = "Best: " + r.best;
                p.primary = "Play again";
                p.primaryAction = this::startRound;
                p.link("Menu", () -> screen = Screen.MENU);
            } else {
                int next = r.level + 1 < Core.LEVELS.length && progress.unlocked(r.level + 1) ? r.level + 1 : -1;
                boolean passed = r.passed();
                p.title = passed
                        ? (r.level == Core.LEVELS.length - 1 && r.stars == 3 ? "Grandmaster!" : "Level complete!")
                        : "Not quite";
                p.subtitle = "Level " + (r.level + 1) + ": " + cfg.name;
                p.stars = r.stars;
                if (passed) {
                    p.body = r.score + " matches." + (r.stars < 3
                            ? " " + cfg.stars[r.stars] + " for the next star." : " All three stars!");
                } else {
                    p.body = r.score + " of " + cfg.goal + " matches. Try again!";
                }
                if (passed && next >= 0) {
                    p.primary = "Next level";
                    p.primaryAction = () -> openLevel(next);
                    p.link("Retry", this::startRound);
                } else {
                    p.primary = "Try again";
                    p.primaryAction = this::startRound;
                }
                p.link("Levels", () -> screen = Screen.LEVELS);
            }
            panel(c, w, h, p);
        }
    }

    private void drawDuel(Canvas c, float w, float h, long t) {
        float cw = Math.min(w - 32, 488);
        float handD = Math.min(cw * 0.7f, (h - 100) * 0.36f);
        float centerD = Math.min(cw * 0.45f, (h - 100) * 0.22f);
        boolean live = duel.winner < 0;
        // Each player's spot: x, y, diameter, rotation in degrees. Player 2 sits across the table.
        float[][] spots = {
                {w / 2, h - 46 - handD / 2, handD, 0},
                {w / 2, 46 + handD / 2, handD, 180}
        };

        playerLabel(c, 1, w / 2, 22, true);
        playerLabel(c, 0, w / 2, h - 22, false);
        for (int p = 1; p >= 0; p--) {
            final int player = p;
            float[] s = spots[p];
            drawCard(c, duel.hands[p], s[0], s[1], s[2], (p == 1 ? -1 : 1) * shake(duel.shakeAt[p]), s[3],
                    duel.frozen(p), 1f, false,
                    live ? (sym, sx, sy) -> duelTap(player, sym, sx, sy) : null);
        }

        if (duel.leaving != null) {
            float k = (t - duel.leavingAt) / (float) LEAVE_MS;
            if (k >= 1 || reduced) duel.leaving = null;
            else drawCard(c, duel.leaving, w / 2, h / 2, centerD * (1 - 0.6f * easeOut(k)),
                    0, 0, false, 1 - k, false, null);
        }
        // The winning card flies (and turns, for Player 2) from the player's spot into the centre.
        Core.PlayCard cc = duel.center;
        float fly = Math.min(1, (t - cc.flyAt) / (float) FLY_MS);
        if (fly < 1 && cc.flyFrom >= 0 && !reduced) {
            float[] from = spots[cc.flyFrom];
            float e = easeOut(fly);
            drawCard(c, cc, lerp(from[0], w / 2, e), lerp(from[1], h / 2, e), lerp(from[2], centerD, e),
                    0, lerp(from[3], 0, e), false, 1f, false, null);
        } else {
            drawCard(c, cc, w / 2, h / 2, centerD, 0, 0, false, 1f, false, null);
        }

        closeButton(c, 16, h / 2 - 20, () -> screen = Screen.MENU);
        soundButton(c, w - 56, h / 2 - 20);

        if (duel.winner >= 0) {
            paint.setColor(0x80000000);
            c.drawRect(-insetL / density, -insetT / density, w + insetR / density, h + insetB / density, paint);
            String title = "Player " + (duel.winner + 1) + " wins!";
            String score = duel.scores[0] + " – " + duel.scores[1];
            float bw = Math.min(cw, 320);
            // One banner per player, each facing its reader.
            c.save();
            c.rotate(180, w / 2, h * 0.2f);
            winBanner(c, title, score, w / 2, h * 0.2f, bw);
            c.restore();
            winBanner(c, title, score, w / 2, h * 0.8f, bw);

            paint.setColor(0xEB331F73);
            c.drawRoundRect((w - bw) / 2, h / 2 - 66, (w + bw) / 2, h / 2 + 66, 28, 28, paint);
            bigButton(c, (w - bw) / 2 + 24, h / 2 - 46, bw - 48, 52, "Rematch", false, duel::reset);
            text(c, "Menu", w / 2, h / 2 + 40, 17, bold, Color.WHITE);
            buttons.add(new Btn(new RectF(w / 2 - 60, h / 2 + 14, w / 2 + 60, h / 2 + 60),
                    () -> screen = Screen.MENU));
        }
    }

    // ------------------------------------------------------------------ Pieces

    /**
     * Draws a card turned by {@code rotDeg}; taps are matched against the same rotation.
     * {@code hidden} shows a blank face (during the countdown).
     */
    private void drawCard(Canvas c, Core.PlayCard card, float cx, float cy, float d, float shakeX, float rotDeg,
                          boolean frozen, float alpha, boolean hidden, SymbolTap onTap) {
        float t = Math.min(1f, Math.max(0f, (Core.now() - card.born) / 280f));
        float scale = reduced ? 1 : 0.6f + 0.4f * easeOutBack(t);
        float r = d / 2;
        int saved = alpha < 1
                ? c.saveLayerAlpha(cx + shakeX - d, cy - d, cx + shakeX + d, cy + d, (int) (alpha * 255))
                : c.save();
        c.translate(cx + shakeX, cy);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x46281A5A);
        c.drawCircle(d * 0.012f * scale, d * 0.03f * scale, r * scale, paint);
        c.scale(scale, scale);
        c.rotate(rotDeg);
        paint.setColor(Color.WHITE);
        c.drawCircle(0, 0, r, paint);
        float bw = Math.max(2, d * 0.012f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(bw);
        paint.setColor(0x1E331F73);
        c.drawCircle(0, 0, r - bw / 2, paint);
        paint.setStyle(Paint.Style.FILL);

        if (hidden) {
            c.rotate(-rotDeg);
            text(c, "?", 0, d * 0.14f, d * 0.4f, black, 0x24331F73);
        } else {
            if (card.wheel) drawWheelTracks(c, card, d);
            for (Core.Slot s : card.slots) {
                Core.slotXY(card, s, xy);
                float turn = card.wheel && s.ring > 0 ? card.ringAngle[s.ring] : 0;
                Symbols.draw(c, s.symbol, xy[0] * d, xy[1] * d, s.size * d, s.rotDeg + turn);
            }
        }

        if (frozen) {
            paint.setColor(0xAAFFFFFF);
            c.drawCircle(0, 0, r, paint);
            drawSnowflake(c, d * 0.32f);
        }
        c.restoreToCount(saved);
        if (onTap != null) hits.add(new CardHit(card, cx + shakeX, cy, d, rotDeg, onTap));
    }

    /** Two faint tracks, each with tick marks that turn with its ring, so the motion reads at a glance. */
    private void drawWheelTracks(Canvas c, Core.PlayCard card, float d) {
        for (int ring = 1; ring <= 2; ring++) {
            float r = Core.WHEEL_R[ring] * d;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2, d * 0.09f));
            paint.setColor(ring == 1 ? 0x295738DB : 0x2EF0578C);
            c.drawCircle(0, 0, r, paint);
            paint.setStrokeWidth(Math.max(1.5f, d * 0.008f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(ring == 1 ? 0x595738DB : 0x66F0578C);
            int ticks = ring == 1 ? 12 : 20;
            double a0 = Math.toRadians(card.ringAngle[ring]);
            for (int i = 0; i < ticks; i++) {
                double a = a0 + (i + 0.5) * 2 * Math.PI / ticks;
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                c.drawLine(cos * (r - d * 0.03f), sin * (r - d * 0.03f), cos * (r + d * 0.03f), sin * (r + d * 0.03f), paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x2E331F73);
        c.drawCircle(0, 0, d * 0.035f, paint);
    }

    private void drawSnowflake(Canvas c, float size) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(size * 0.08f);
        paint.setColor(0xFF1E78E6);
        float r = size / 2;
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            float ex = (float) (r * Math.cos(a)), ey = (float) (r * Math.sin(a));
            c.drawLine(0, 0, ex, ey, paint);
            float bx = 0.6f * ex, by = 0.6f * ey, br = r * 0.3f;
            c.drawLine(bx, by, bx + (float) (br * Math.cos(a + 0.7)), by + (float) (br * Math.sin(a + 0.7)), paint);
            c.drawLine(bx, by, bx + (float) (br * Math.cos(a - 0.7)), by + (float) (br * Math.sin(a - 0.7)), paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void bigNumber(Canvas c, String s, float x, float y, float k) {
        float sc = reduced ? 1 : 1.5f - 0.5f * easeOut(Math.min(1, k * 2.5f));
        float a = 1 - Math.max(0, k - 0.6f) / 0.4f * 0.85f;
        c.save();
        c.translate(x, y);
        c.scale(sc, sc);
        outlinedText(c, s, 0, 36, 96, a);
        c.restore();
    }

    /** White text with a soft ink outline, centred on x. */
    private void outlinedText(Canvas c, String s, float x, float baseline, float size, float alpha) {
        int a = (int) (Math.max(0, Math.min(1, alpha)) * 255);
        outline.setTextSize(size);
        outline.setTypeface(black);
        outline.setStrokeWidth(size * 0.1f);
        outline.setColor(Color.argb((int) (a * 0.6f), 0x33, 0x1F, 0x73));
        c.drawText(s, x, baseline, outline);
        text(c, s, x, baseline, size, black, Color.argb(a, 255, 255, 255));
    }

    private static Path unitStar() {
        Path p = new Path();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float r = i % 2 == 0 ? 0.5f : 0.21f;
            float x = (float) (r * Math.cos(a)), y = (float) (r * Math.sin(a));
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    /** A gold star when filled; an empty one is pale on dark backgrounds and faint ink on light ones. */
    private void starIcon(Canvas c, float x, float y, float size, boolean filled, boolean onLight, float scale) {
        if (scale <= 0) return;
        matrix.setScale(size * scale, size * scale);
        matrix.postTranslate(x, y);
        starPath.transform(matrix, tmpPath);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(filled ? GOLD : onLight ? 0x24331F73 : 0x47FFFFFF);
        c.drawPath(tmpPath, paint);
        if (filled) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(Math.max(1, size * scale * 0.06f));
            paint.setColor(0xFFB57F00);
            c.drawPath(tmpPath, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void lockIcon(Canvas c, float x, float y, float s, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(s * 0.14f);
        rect.set(x - s * 0.28f, y - s * 0.43f, x + s * 0.28f, y + s * 0.13f);
        c.drawArc(rect, 180, 180, false, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawRect(x - s * 0.4f, y - s * 0.15f, x + s * 0.4f, y + s * 0.45f, paint);
    }

    private void playerLabel(Canvas c, int p, float cx, float cy, boolean flipped) {
        String label = "Player " + (p + 1);
        text.setTextSize(17);
        text.setTypeface(bold);
        float lw = text.measureText(label);
        float total = lw + 14 + Core.Duel.TARGET * 13 - 4;
        float x = cx - total / 2;
        c.save();
        if (flipped) c.rotate(180, cx, cy);
        textLeft(c, label, x, cy + 6, 17, bold, Color.WHITE);
        x += lw + 14;
        for (int i = 0; i < Core.Duel.TARGET; i++) {
            paint.setColor(i < duel.scores[p] ? Color.WHITE : 0x46FFFFFF);
            c.drawCircle(x + i * 13 + 4.5f, cy, 4.5f, paint);
        }
        c.restore();
    }

    private void winBanner(Canvas c, String title, String score, float cx, float cy, float bw) {
        paint.setColor(0xEB331F73);
        c.drawRoundRect(cx - bw / 2, cy - 52, cx + bw / 2, cy + 52, 28, 28, paint);
        text(c, title, cx, cy - 2, 32, black, Color.WHITE);
        text(c, score, cx, cy + 32, 22, bold, 0xDDFFFFFF);
    }

    private void panel(Canvas c, float w, float h, Panel o) {
        paint.setColor(0x73000000);
        c.drawRect(-insetL / density, -insetT / density, w + insetR / density, h + insetB / density, paint);
        float pw = Math.min(w - 32, 360), tw = pw - 56;
        text.setTextSize(16);
        text.setTypeface(regular);
        List<String> lines = o.body == null ? new ArrayList<>() : wrap(o.body, tw);
        float ph = 28 + 36 + (o.subtitle != null ? 26 : 0) + (o.big != null ? 92 : 0) + (o.stars >= 0 ? 78 : 0)
                + (o.goals != null ? 44 : 0) + lines.size() * 22 + 24 + 52 + 44 + 16;
        float px = (w - pw) / 2, py = Math.max(8, (h - ph) / 2);
        paint.setColor(0xF0331F73);
        c.drawRoundRect(px, py, px + pw, py + ph, 28, 28, paint);
        float y = py + 56;
        text(c, o.title, w / 2, y, 30, black, Color.WHITE);
        if (o.subtitle != null) {
            y += 26;
            text(c, o.subtitle, w / 2, y, 16, bold, 0xBFFFFFFF);
        }
        if (o.big != null) {
            text(c, o.big, w / 2, y + 88, 80, black, Color.WHITE);
            y += 92;
        }
        if (o.stars >= 0) {
            long t = Core.now();
            for (int i = 0; i < 3; i++) {
                float sx = w / 2 + (i - 1) * 62, sy = y + 46 - (i == 1 ? 8 : 0);
                starIcon(c, sx, sy, 50, false, false, 1);
                if (i < starsShown) {
                    float k = (t - (round.overAt + 450 + i * 380L)) / 300f;
                    float sc = reduced ? 1 : k < 0 ? 0 : k < 1 ? easeOutBack(k) : 1;
                    starIcon(c, sx, sy, 50, true, false, sc);
                }
            }
            y += 78;
        }
        if (o.goals != null) {
            y += 30;
            float gw = 92, gx = w / 2 - gw;
            for (int i = 0; i < o.goals.length; i++) {
                float cx = gx + i * gw;
                for (int s = 0; s <= i; s++) starIcon(c, cx - i * 8 + s * 16, y - 6, 14, true, false, 1);
                text(c, String.valueOf(o.goals[i]), cx, y + 14, 14, bold, 0xD9FFFFFF);
            }
            y += 14;
        }
        for (String l : lines) {
            y += 22;
            text(c, l, w / 2, y, 16, regular, 0xE0FFFFFF);
        }
        y += 24;
        bigButton(c, px + 28, y, pw - 56, 52, o.primary, false, o.primaryAction);
        int n = o.links.size();
        for (int i = 0; i < n; i++) {
            float lx = w / 2 + (i - (n - 1) / 2f) * 110;
            Runnable run = o.linkActions.get(i);
            text(c, o.links.get(i), lx, y + 82, 16, bold, Color.WHITE);
            buttons.add(new Btn(new RectF(lx - 52, y + 58, lx + 52, y + 98), () -> {
                round.phase = Core.Round.Phase.READY;
                run.run();
            }));
        }
    }

    private void menuButton(Canvas c, float x, float y, float w, float h, String title, String sub,
                            int iconLook, Runnable action, boolean isNew) {
        boolean narrow = w < 250; // half-width buttons drop the arrow to make room for the subtitle
        paint.setColor(Color.WHITE);
        c.drawRoundRect(x, y, x + w, y + h, 22, 22, paint);
        paint.setColor(0x19331F73);
        c.drawCircle(x + 38, y + h / 2, 24, paint);
        Symbols.drawLook(c, iconLook, x + 38, y + h / 2, 30, 0);
        textLeft(c, title, x + 78, y + h / 2 - 3, 20, bold, INK);
        text.setTextSize(14);
        text.setTypeface(regular);
        String s = sub;
        while (s.length() > 4 && text.measureText(s) > w - 78 - (narrow ? 10 : 40)) {
            s = s.substring(0, s.length() - 2) + "…";
        }
        textLeft(c, s, x + 78, y + h / 2 + 19, 14, regular, 0xAF331F73);
        if (!narrow) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0x78331F73);
            float ax = x + w - 26, ay = y + h / 2;
            c.drawLine(ax - 5, ay - 7, ax + 2, ay, paint);
            c.drawLine(ax + 2, ay, ax - 5, ay + 7, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if (isNew) {
            text.setTextSize(11);
            text.setTypeface(black);
            float bw = text.measureText("NEW") + 14;
            paint.setColor(GOLD);
            c.drawRoundRect(x + w - bw - 10, y - 8, x + w - 10, y + 12, 10, 10, paint);
            text(c, "NEW", x + w - bw / 2 - 10, y + 6, 11, black, INK);
        }
        buttons.add(new Btn(new RectF(x, y, x + w, y + h), action));
    }

    private void bigButton(Canvas c, float x, float y, float w, float h, String label, boolean inverted,
                           Runnable action) {
        paint.setColor(inverted ? INK : Color.WHITE);
        c.drawRoundRect(x, y, x + w, y + h, 18, 18, paint);
        text(c, label, x + w / 2, y + h / 2 + 7, 19, bold, inverted ? Color.WHITE : INK);
        buttons.add(new Btn(new RectF(x, y, x + w, y + h), action));
    }

    /** Draws a pill right-aligned at {@code right}, optionally scaled about its centre; returns its left edge. */
    private float pill(Canvas c, float right, float y, String label, boolean warn, float scale) {
        text.setTextSize(16);
        text.setTypeface(bold);
        float pw = text.measureText(label) + 28;
        c.save();
        c.scale(scale, scale, right - pw / 2, y + 17);
        paint.setColor(warn ? 0xD2E63946 : 0x32FFFFFF);
        c.drawRoundRect(right - pw, y, right, y + 34, 17, 17, paint);
        text(c, label, right - pw / 2, y + 23, 16, bold, Color.WHITE);
        c.restore();
        return right - pw;
    }

    /** Level score: "6/8" plus three small stars that light up as thresholds are reached. */
    private void scorePill(Canvas c, float right, float y, int score, Core.Level cfg, float scale) {
        String label = score + "/" + cfg.goal;
        text.setTextSize(16);
        text.setTypeface(bold);
        float lw = text.measureText(label), pw = lw + 28 + 3 * 15 + 4;
        c.save();
        c.scale(scale, scale, right - pw / 2, y + 17);
        paint.setColor(0x32FFFFFF);
        c.drawRoundRect(right - pw, y, right, y + 34, 17, 17, paint);
        textLeft(c, label, right - pw + 14, y + 23, 16, bold, Color.WHITE);
        for (int i = 0; i < 3; i++) {
            starIcon(c, right - pw + 14 + lw + 12 + i * 15, y + 17, 14, score >= cfg.stars[i], false, 1);
        }
        c.restore();
    }

    private void closeButton(Canvas c, float x, float y, Runnable action) {
        paint.setColor(0x32FFFFFF);
        c.drawCircle(x + 20, y + 20, 20, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.WHITE);
        c.drawLine(x + 14, y + 14, x + 26, y + 26, paint);
        c.drawLine(x + 26, y + 14, x + 14, y + 26, paint);
        paint.setStyle(Paint.Style.FILL);
        // Generous touch target (48 dp).
        buttons.add(new Btn(new RectF(x - 4, y - 4, x + 44, y + 44), action));
    }

    /** Speaker icon that toggles music and sound effects. */
    private void soundButton(Canvas c, float x, float y) {
        paint.setColor(0x32FFFFFF);
        c.drawCircle(x + 20, y + 20, 20, paint);
        paint.setColor(Color.WHITE);
        path.reset();
        path.moveTo(x + 10, y + 16);
        path.lineTo(x + 15, y + 16);
        path.lineTo(x + 21, y + 11);
        path.lineTo(x + 21, y + 29);
        path.lineTo(x + 15, y + 24);
        path.lineTo(x + 10, y + 24);
        path.close();
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.2f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (sound.isMuted()) {
            c.drawLine(x + 24, y + 16, x + 31, y + 24, paint);
            c.drawLine(x + 31, y + 16, x + 24, y + 24, paint);
        } else {
            rect.set(x + 16, y + 15, x + 26, y + 25);
            c.drawArc(rect, -52, 104, false, paint);
            rect.set(x + 12, y + 11, x + 30, y + 29);
            c.drawArc(rect, -52, 104, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        buttons.add(new Btn(new RectF(x - 4, y - 4, x + 44, y + 44), () -> sound.setMuted(!sound.isMuted())));
    }

    // ------------------------------------------------------------------ Effects

    private static int colorOf(int symbol) {
        return Symbols.PALETTE[Symbols.look(symbol) / Symbols.SHAPE_COUNT];
    }

    private Fx fx(int kind, float x, float y, float life) {
        Fx e = new Fx();
        e.kind = kind; e.x = x; e.y = y; e.life = life; e.max = life;
        fx.add(e);
        return e;
    }

    private void burst(float x, float y, int color) {
        fx(Fx.RING, x, y, 0.45f).color = color;
        int n = reduced ? 6 : 14;
        for (int i = 0; i < n; i++) {
            Fx e = fx(Fx.DOT, x, y, 0.55f);
            double a = rnd.nextDouble() * Math.PI * 2;
            float sp = 120 + rnd.nextFloat() * 160;
            e.vx = (float) Math.cos(a) * sp;
            e.vy = (float) Math.sin(a) * sp;
            e.r = 2.5f + rnd.nextFloat() * 3.5f;
            e.color = rnd.nextFloat() < 0.35f ? Color.WHITE : color;
        }
    }

    private void floatText(String s, float x, float y, int color, boolean flipped) {
        Fx e = fx(Fx.TEXT, x, y, 0.8f);
        e.str = s;
        e.color = color;
        e.flipped = flipped;
    }

    private void cross(float x, float y, boolean flipped) {
        fx(Fx.CROSS, x, y, 0.45f).flipped = flipped;
    }

    private void confetti() {
        if (reduced) return;
        for (int i = 0; i < 140; i++) {
            Fx e = fx(Fx.CONFETTI, rnd.nextFloat() * viewW, -20 - rnd.nextFloat() * 200, 3.2f + rnd.nextFloat());
            e.max = 4.2f;
            e.vx = (rnd.nextFloat() - 0.5f) * 90;
            e.vy = 60 + rnd.nextFloat() * 140;
            e.rot = rnd.nextFloat() * 6;
            e.vr = (rnd.nextFloat() - 0.5f) * 10;
            e.w = 6 + rnd.nextFloat() * 5;
            e.h = 9 + rnd.nextFloat() * 6;
            e.color = rnd.nextFloat() < 0.2f ? Color.WHITE : Symbols.PALETTE[rnd.nextInt(5)];
        }
    }

    private void updateFx(float dt) {
        for (int i = fx.size() - 1; i >= 0; i--) {
            Fx e = fx.get(i);
            e.life -= dt;
            if (e.life <= 0) {
                fx.remove(i);
                continue;
            }
            if (e.kind == Fx.DOT) {
                e.x += e.vx * dt;
                e.y += e.vy * dt;
                e.vx *= 0.9f;
                e.vy = e.vy * 0.9f + 400 * dt;
            } else if (e.kind == Fx.CONFETTI) {
                e.x += (e.vx + (float) Math.sin(e.life * 3 + e.rot) * 30) * dt;
                e.y += e.vy * dt;
                e.rot += e.vr * dt;
            }
        }
    }

    private void drawFx(Canvas c) {
        for (Fx e : fx) {
            float k = e.life / e.max;
            c.save();
            switch (e.kind) {
                case Fx.RING:
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(4 * k + 1);
                    paint.setColor(withAlpha(e.color, k));
                    c.drawCircle(e.x, e.y, 10 + 46 * easeOut(1 - k), paint);
                    paint.setStyle(Paint.Style.FILL);
                    break;
                case Fx.DOT:
                    paint.setColor(withAlpha(e.color, Math.min(1, k * 1.5f)));
                    c.drawCircle(e.x, e.y, e.r * (0.4f + 0.6f * k), paint);
                    break;
                case Fx.TEXT: {
                    float t = 1 - k;
                    c.translate(e.x, e.y);
                    if (e.flipped) c.rotate(180);
                    float s = 0.7f + 0.5f * easeOutBack(Math.min(1, t * 4));
                    c.translate(0, -48 * easeOut(t));
                    c.scale(s, s);
                    float a = Math.min(1, k * 2);
                    outline.setTextSize(26);
                    outline.setTypeface(black);
                    outline.setStrokeWidth(5);
                    outline.setColor(Color.argb((int) (a * 140), 0x33, 0x1F, 0x73));
                    c.drawText(e.str, 0, 0, outline);
                    text(c, e.str, 0, 0, 26, black, withAlpha(e.color, a));
                    break;
                }
                case Fx.CROSS: {
                    c.translate(e.x, e.y);
                    if (e.flipped) c.rotate(180);
                    float s = 14 + 10 * (1 - k);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(6);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    paint.setColor(withAlpha(0xFFE63946, k));
                    c.drawLine(-s, -s, s, s, paint);
                    c.drawLine(s, -s, -s, s, paint);
                    paint.setStyle(Paint.Style.FILL);
                    break;
                }
                case Fx.CONFETTI:
                    c.translate(e.x, e.y);
                    c.rotate((float) Math.toDegrees(e.rot));
                    c.scale(1, (float) Math.abs(Math.cos(e.rot * 1.7)) * 0.8f + 0.2f);
                    paint.setColor(withAlpha(e.color, Math.min(1, e.life)));
                    c.drawRect(-e.w / 2, -e.h / 2, e.w / 2, e.h / 2, paint);
                    break;
            }
            c.restore();
        }
    }

    private void drawDrift(Canvas c, float w, float h, float dt) {
        if (drift.isEmpty()) return;
        int saved = c.saveLayerAlpha(-insetL / density, -insetT / density, w + insetR / density,
                h + insetB / density, 43);
        for (Drift s : drift) {
            s.x += s.vx * dt / w;
            s.y += s.vy * dt / h;
            s.rot += s.vr * dt;
            if (s.y < -0.08f) { s.y = 1.08f; s.x = rnd.nextFloat(); }
            if (s.x < -0.08f) s.x = 1.08f;
            else if (s.x > 1.08f) s.x = -0.08f;
            Symbols.drawLook(c, s.look, s.x * w, s.y * h, s.size, s.rot);
        }
        c.restoreToCount(saved);
    }

    // ------------------------------------------------------------------ Helpers

    private void text(Canvas c, String s, float cx, float baseline, float size, Typeface tf, int color) {
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(size);
        text.setTypeface(tf);
        text.setColor(color);
        c.drawText(s, cx, baseline, text);
    }

    private void textLeft(Canvas c, String s, float x, float baseline, float size, Typeface tf, int color) {
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(size);
        text.setTypeface(tf);
        text.setColor(color);
        c.drawText(s, x, baseline, text);
        text.setTextAlign(Paint.Align.CENTER);
    }

    /** Word-wraps using the text paint's current size and typeface. */
    private List<String> wrap(String s, float width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String trial = line.length() == 0 ? word : line + " " + word;
            if (text.measureText(trial) > width && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(trial);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private static int withAlpha(int color, float a) {
        int alpha = (int) (Math.max(0, Math.min(1, a)) * Color.alpha(color));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static float shake(long at) {
        long a = Core.now() - at;
        if (a < 0 || a > 300) return 0;
        float t = a / 300f;
        return (float) (12 * Math.sin(t * Math.PI * 4) * (1 - t));
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float easeOut(float t) { return 1 - (float) Math.pow(1 - t, 3); }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }
}
