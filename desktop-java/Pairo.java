import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.prefs.Preferences;

/**
 * Pairo: a spot-the-match card game (Java / Swing desktop edition).
 *
 *   Run:        java Pairo.java          (JDK 17+, no build step)
 *   Self-test:  java Pairo.java --test
 *
 * Every two cards share exactly one symbol. The deck is the finite projective
 * plane of order 7: 57 symbols, 57 cards, 8 symbols per card.
 */
public class Pairo {

    // ============================================================ Deck

    static final int ORDER = 7;
    static final int PER_CARD = ORDER + 1;
    static final int[][] CARDS = buildDeck();

    static int[][] buildDeck() {
        int n = ORDER;
        List<int[]> out = new ArrayList<>();
        // Lines y = a·x + b, plus the point at infinity for slope a.
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                int[] c = new int[n + 1];
                for (int x = 0; x < n; x++) c[x] = n * x + (a * x + b) % n;
                c[n] = n * n + a;
                out.add(c);
            }
        }
        // Vertical lines x = c, plus the vertical point at infinity.
        for (int c0 = 0; c0 < n; c0++) {
            int[] c = new int[n + 1];
            for (int y = 0; y < n; y++) c[y] = n * c0 + y;
            c[n] = n * n + n;
            out.add(c);
        }
        // The line at infinity.
        int[] inf = new int[n + 1];
        for (int i = 0; i <= n; i++) inf[i] = n * n + i;
        out.add(inf);
        return out.toArray(new int[0][]);
    }

    static int shared(int a, int b) {
        for (int x : CARDS[a]) for (int y : CARDS[b]) if (x == y) return x;
        return -1;
    }

    /** Draws card indices from a shuffled pile, reshuffling when it runs out. */
    static final class Dealer {
        private final Random rnd;
        private final List<Integer> pile = new ArrayList<>();

        Dealer(Random rnd) { this.rnd = rnd; }

        int draw(int... inPlay) {
            while (true) {
                if (pile.isEmpty()) {
                    for (int i = 0; i < CARDS.length; i++) pile.add(i);
                    Collections.shuffle(pile, rnd);
                }
                int next = pile.remove(pile.size() - 1);
                boolean free = true;
                for (int a : inPlay) if (a == next) free = false;
                if (free) return next;
            }
        }
    }

    /** One symbol placed on a card. Positions and sizes are fractions of the card diameter. */
    static final class Slot {
        final int symbol;
        final double x, y, size, rot;

        Slot(int symbol, double x, double y, double size, double rot) {
            this.symbol = symbol; this.x = x; this.y = y; this.size = size; this.rot = rot;
        }
    }

    /** A card in play: its deck index plus a freshly randomised layout. */
    static final class PlayCard {
        final int index;
        final Slot[] slots;
        long born = now();

        PlayCard(int index, Random r) {
            this.index = index;
            List<Integer> syms = new ArrayList<>();
            for (int s : CARDS[index]) syms.add(s);
            Collections.shuffle(syms, r);
            slots = new Slot[syms.size()];
            int ring = syms.size() - 1;
            double base = r.nextDouble() * 2 * Math.PI;
            for (int i = 0; i < syms.size(); i++) {
                double rot = r.nextDouble() * 2 * Math.PI;
                if (i == 0) {
                    slots[i] = new Slot(syms.get(i), 0, 0, 0.21 + r.nextDouble() * 0.05, rot);
                } else {
                    double ang = base + (i - 1) * 2 * Math.PI / ring + (r.nextDouble() - 0.5) * 0.2;
                    double rad = 0.29 + r.nextDouble() * 0.03;
                    slots[i] = new Slot(syms.get(i), rad * Math.cos(ang), rad * Math.sin(ang),
                            0.14 + r.nextDouble() * 0.05, rot);
                }
            }
        }
    }

    static long now() { return System.currentTimeMillis(); }

    // ============================================================ Symbols (10 shapes × 6 colours)

    static final Color[] PALETTE = {
            new Color(0xE63946), new Color(0xF2A900), new Color(0x2A9D50),
            new Color(0x1E78E6), new Color(0x8A46C8), new Color(0x3A3A4A)
    };
    static final String[] COLOR_NAMES = {"red", "yellow", "green", "blue", "purple", "black"};
    static final String[] SHAPE_NAMES = {"circle", "square", "triangle", "star", "heart",
            "diamond", "hexagon", "cross", "moon", "ring"};
    static final Shape[] SHAPES = buildShapes();

    /**
     * Maps a deck symbol to its look (shape + colour). Without this, symbols on the
     * same deck "line" would get consecutive looks and some cards would be one colour.
     * 13 is coprime with 57, so this is a permutation.
     */
    static int look(int s) { return (s * 13) % 57; }

    static String symbolName(int s) {
        int v = look(s);
        return COLOR_NAMES[v / SHAPES.length] + " " + SHAPE_NAMES[v % SHAPES.length];
    }

    static Shape[] buildShapes() {
        Shape[] s = new Shape[10];
        s[0] = new Ellipse2D.Double(-0.45, -0.45, 0.9, 0.9);
        s[1] = new RoundRectangle2D.Double(-0.4, -0.4, 0.8, 0.8, 0.14, 0.14);
        s[2] = poly(0, -0.5, 0.47, 0.36, -0.47, 0.36);
        s[3] = star(5, 0.52, 0.22);
        Path2D heart = new Path2D.Double();
        heart.moveTo(0, 0.45);
        heart.curveTo(-0.75, -0.05, -0.35, -0.65, 0, -0.22);
        heart.curveTo(0.35, -0.65, 0.75, -0.05, 0, 0.45);
        heart.closePath();
        s[4] = heart;
        s[5] = poly(0, -0.5, 0.34, 0, 0, 0.5, -0.34, 0);
        s[6] = star(3, 0.47, 0.47); // equal radii = regular hexagon
        Area plus = new Area(new Rectangle2D.Double(-0.15, -0.45, 0.3, 0.9));
        plus.add(new Area(new Rectangle2D.Double(-0.45, -0.15, 0.9, 0.3)));
        s[7] = plus;
        Area moon = new Area(new Ellipse2D.Double(-0.45, -0.45, 0.9, 0.9));
        moon.subtract(new Area(new Ellipse2D.Double(-0.22, -0.55, 0.8, 0.8)));
        s[8] = moon;
        Area ring = new Area(new Ellipse2D.Double(-0.45, -0.45, 0.9, 0.9));
        ring.subtract(new Area(new Ellipse2D.Double(-0.22, -0.22, 0.44, 0.44)));
        s[9] = ring;
        return s;
    }

    static Shape poly(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        p.closePath();
        return p;
    }

    /** Alternating outer/inner radius polygon (a star, or a hexagon when both radii fit one). */
    static Shape star(int points, double outer, double inner) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < points * 2; i++) {
            double a = -Math.PI / 2 + i * Math.PI / points;
            double r = (i % 2 == 0) ? outer : inner;
            if (i == 0) p.moveTo(r * Math.cos(a), r * Math.sin(a));
            else p.lineTo(r * Math.cos(a), r * Math.sin(a));
        }
        p.closePath();
        return p;
    }

    static void drawSymbol(Graphics2D g, int sym, double cx, double cy, double size, double rot) {
        drawLook(g, look(sym), cx, cy, size, rot);
    }

    static void drawLook(Graphics2D g, int sym, double cx, double cy, double size, double rot) {
        Color col = PALETTE[sym / SHAPES.length];
        AffineTransform at = new AffineTransform();
        at.translate(cx, cy);
        at.rotate(rot);
        at.scale(size, size);
        Shape s = at.createTransformedShape(SHAPES[sym % SHAPES.length]);
        g.setColor(col);
        g.fill(s);
        g.setColor(col.darker());
        g.setStroke(new BasicStroke((float) Math.max(1.2, size * 0.045),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(s);
    }

    // ============================================================ Game

    static final Color INK = new Color(51, 31, 115);
    static final Color G1 = new Color(87, 56, 219), G2 = new Color(240, 87, 140);
    static final double SOLO_SECONDS = 60, PENALTY = 3;
    static final int DUEL_TARGET = 10;
    static final long FREEZE_MS = 1500;

    static final class Game extends JPanel {
        enum Screen { MENU, RULES, SOLO, DUEL }
        enum Phase { READY, PLAYING, OVER }

        record Btn(Rectangle2D r, Runnable action) {}
        record CardHit(PlayCard card, double cx, double cy, double d, IntConsumer onSymbol) {}

        final Random rnd = new Random();
        final Dealer dealer = new Dealer(rnd);
        final Preferences prefs = Preferences.userRoot().node("pairo");
        final List<Btn> buttons = new ArrayList<>();
        final List<CardHit> hits = new ArrayList<>();

        Screen screen = Screen.MENU;
        PlayCard demo = new PlayCard(rnd.nextInt(CARDS.length), rnd);

        // Solo Sprint
        Phase phase = Phase.READY;
        PlayCard top, hand;
        int score, best;
        double timeLeft = SOLO_SECONDS;
        boolean newBest;
        long soloShake, lastTick;

        // Duel
        PlayCard center;
        final PlayCard[] hands = new PlayCard[2];
        final int[] scores = new int[2];
        final long[] frozenUntil = new long[2];
        final long[] duelShake = new long[2];
        int winner = -1;

        Game() {
            best = prefs.getInt("bestSolo", 0);
            setPreferredSize(new Dimension(480, 820));
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { click(e.getX(), e.getY()); }
            });
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) { key(e.getKeyCode()); }
            });
            dealSolo();
            dealDuel();
        }

        void startLoop() {
            lastTick = System.nanoTime();
            new javax.swing.Timer(16, e -> { tick(); repaint(); }).start();
        }

        void tick() {
            long t = System.nanoTime();
            double dt = (t - lastTick) / 1e9;
            lastTick = t;
            if (screen == Screen.SOLO && phase == Phase.PLAYING) {
                timeLeft -= dt;
                if (timeLeft <= 0) finishSolo();
            }
        }

        // ---------- Solo logic

        void dealSolo() {
            int a = dealer.draw();
            int b = dealer.draw(a);
            top = new PlayCard(a, rnd);
            hand = new PlayCard(b, rnd);
        }

        void startSolo() {
            dealSolo();
            score = 0;
            timeLeft = SOLO_SECONDS;
            newBest = false;
            phase = Phase.PLAYING;
            lastTick = System.nanoTime();
        }

        void soloTap(int sym) {
            if (phase != Phase.PLAYING) return;
            if (sym == shared(top.index, hand.index)) {
                score++;
                int next = dealer.draw(top.index, hand.index);
                top = hand;
                top.born = now();
                hand = new PlayCard(next, rnd);
            } else {
                soloShake = now();
                timeLeft -= PENALTY;
                if (timeLeft <= 0) finishSolo();
            }
        }

        void finishSolo() {
            timeLeft = 0;
            phase = Phase.OVER;
            if (score > best) {
                best = score;
                newBest = true;
                prefs.putInt("bestSolo", best);
            }
        }

        // ---------- Duel logic

        void dealDuel() {
            int c = dealer.draw();
            int p0 = dealer.draw(c);
            int p1 = dealer.draw(c, p0);
            center = new PlayCard(c, rnd);
            hands[0] = new PlayCard(p0, rnd);
            hands[1] = new PlayCard(p1, rnd);
            scores[0] = scores[1] = 0;
            frozenUntil[0] = frozenUntil[1] = 0;
            winner = -1;
        }

        void duelTap(int p, int sym) {
            if (winner >= 0 || now() < frozenUntil[p]) return;
            if (sym == shared(center.index, hands[p].index)) {
                scores[p]++;
                int next = dealer.draw(center.index, hands[0].index, hands[1].index);
                center = hands[p];
                center.born = now();
                hands[p] = new PlayCard(next, rnd);
                if (scores[p] >= DUEL_TARGET) winner = p;
            } else {
                duelShake[p] = now();
                frozenUntil[p] = now() + FREEZE_MS;
            }
        }

        // ---------- Input

        void click(int x, int y) {
            requestFocusInWindow();
            for (int i = buttons.size() - 1; i >= 0; i--) {
                if (buttons.get(i).r().contains(x, y)) {
                    buttons.get(i).action().run();
                    repaint();
                    return;
                }
            }
            for (CardHit h : hits) {
                double dx = x - h.cx(), dy = y - h.cy();
                if (dx * dx + dy * dy > h.d() * h.d() / 4) continue;
                Slot hit = null;
                double bestDist = Double.MAX_VALUE;
                for (Slot s : h.card().slots) {
                    double dist = Math.hypot(x - (h.cx() + s.x * h.d()), y - (h.cy() + s.y * h.d()));
                    if (dist < s.size * h.d() * 0.62 && dist < bestDist) { bestDist = dist; hit = s; }
                }
                if (hit != null) h.onSymbol().accept(hit.symbol);
                repaint();
                return;
            }
        }

        void key(int code) {
            if (code == KeyEvent.VK_ESCAPE) {
                if (screen == Screen.SOLO) phase = Phase.READY;
                screen = Screen.MENU;
            } else if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ENTER) {
                if (screen == Screen.SOLO && phase != Phase.PLAYING) startSolo();
                else if (screen == Screen.DUEL && winner >= 0) dealDuel();
            }
            repaint();
        }

        // ---------- Rendering

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            render((Graphics2D) g0.create(), getWidth(), getHeight());
        }

        void render(Graphics2D g, int w, int h) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            buttons.clear();
            hits.clear();
            g.setPaint(new GradientPaint(0, 0, G1, w, h, G2));
            g.fillRect(0, 0, w, h);
            switch (screen) {
                case MENU -> renderMenu(g, w, h);
                case RULES -> renderRules(g, w, h);
                case SOLO -> renderSolo(g, w, h);
                case DUEL -> renderDuel(g, w, h);
            }
            g.dispose();
        }

        void renderMenu(Graphics2D g, int w, int h) {
            double cw = Math.min(w - 48, 440), x0 = (w - cw) / 2;
            g.setColor(Color.WHITE);
            g.setFont(font(Font.BOLD, 64));
            centered(g, "Pairo", w / 2.0, 96);
            g.setFont(font(Font.PLAIN, 17));
            g.setColor(new Color(255, 255, 255, 220));
            centered(g, "Every two cards share exactly one symbol.", w / 2.0, 132);
            centered(g, "Find it first.", w / 2.0, 155);

            double d = Math.min(cw * 0.62, h * 0.30), cy = 180 + d / 2;
            drawCard(g, demo, w / 2.0, cy, d, 0, false, null);
            buttons.add(new Btn(new Rectangle2D.Double(w / 2.0 - d / 2, cy - d / 2, d, d),
                    () -> demo = new PlayCard(rnd.nextInt(CARDS.length), rnd)));

            double y = cy + d / 2 + 34;
            menuButton(g, x0, y, cw, 76, "Solo Sprint",
                    best > 0 ? "Best: " + best + " matches" : "As many matches as you can in 60 s", 13,
                    () -> { screen = Screen.SOLO; phase = Phase.READY; });
            menuButton(g, x0, y + 90, cw, 76, "Duel", "Two players, one screen", 4,
                    () -> { dealDuel(); screen = Screen.DUEL; });
            g.setFont(font(Font.BOLD, 17));
            g.setColor(Color.WHITE);
            centered(g, "How to Play", w / 2.0, y + 200);
            buttons.add(new Btn(new Rectangle2D.Double(w / 2.0 - 70, y + 180, 140, 30),
                    () -> screen = Screen.RULES));
        }

        void renderRules(Graphics2D g, int w, int h) {
            double cw = Math.min(w - 32, 460), x0 = (w - cw) / 2;
            RoundRectangle2D panel = new RoundRectangle2D.Double(x0, 24, cw, h - 48, 28, 28);
            g.setColor(Color.WHITE);
            g.fill(panel);
            double tx = x0 + 24, tw = cw - 48, y = 72;
            g.setColor(INK);
            g.setFont(font(Font.BOLD, 30));
            g.drawString("How to Play", (float) tx, (float) y);
            y += 22;
            String[][] sections = {
                    {"The secret", "There are 57 cards with 8 symbols each. Any two cards share exactly "
                            + "one symbol, never more and never less. Symbols vary in size and rotation, "
                            + "so look carefully."},
                    {"Solo Sprint", "Click the symbol that appears on both cards. Each match scores a "
                            + "point and deals a new card. A wrong click costs 3 seconds. Score as much as "
                            + "you can in 60 seconds. Space starts a round."},
                    {"Duel", "Each player matches their own card against the centre card and clicks "
                            + "the shared symbol on their own card. The winner's card becomes the new "
                            + "centre. A wrong click freezes you for 1.5 seconds. First to 10 wins. "
                            + "Best on a touchscreen."},
                    {"Keys", "Esc returns to the menu. Space or Enter starts a new round."}
            };
            for (String[] s : sections) {
                y += 30;
                g.setFont(font(Font.BOLD, 18));
                g.setColor(INK);
                g.drawString(s[0], (float) tx, (float) y);
                g.setFont(font(Font.PLAIN, 15));
                g.setColor(new Color(51, 31, 115, 200));
                for (String line : wrap(g, s[1], tw)) {
                    y += 21;
                    g.drawString(line, (float) tx, (float) y);
                }
            }
            bigButton(g, x0 + 24, Math.min(h - 100, y + 36), cw - 48, 52, "Back", true,
                    () -> screen = Screen.MENU);
        }

        void renderSolo(Graphics2D g, int w, int h) {
            double cw = Math.min(w - 32, 488), x0 = (w - cw) / 2;
            closeButton(g, x0, 18, () -> { screen = Screen.MENU; phase = Phase.READY; });
            int secs = (int) Math.ceil(Math.max(0, timeLeft));
            double px = pill(g, x0 + cw, 21, "Time " + secs, secs <= 10 && phase == Phase.PLAYING);
            pill(g, px - 10, 21, "Score " + score, false);

            RoundRectangle2D track = new RoundRectangle2D.Double(x0, 70, cw, 8, 8, 8);
            g.setColor(new Color(255, 255, 255, 60));
            g.fill(track);
            g.setColor(Color.WHITE);
            g.fill(new RoundRectangle2D.Double(x0, 70, cw * Math.max(0, timeLeft) / SOLO_SECONDS, 8, 8, 8));

            double d = Math.min(cw * 0.82, (h - 170) / 2.0);
            boolean live = phase == Phase.PLAYING;
            drawCard(g, top, w / 2.0, 92 + d / 2, d, 0, false, live ? this::soloTap : null);
            g.setFont(font(Font.BOLD, 15));
            g.setColor(new Color(255, 255, 255, 220));
            centered(g, "Click the symbol both cards share", w / 2.0, 92 + d + 28);
            drawCard(g, hand, w / 2.0, 92 + d + 44 + d / 2, d, shake(soloShake), false,
                    live ? this::soloTap : null);

            if (phase == Phase.READY) {
                overlay(g, w, h, "Solo Sprint", null,
                        "Click the one symbol that appears on both cards. Every match scores a point. "
                                + "A wrong click costs 3 seconds.",
                        "Start", this::startSolo);
            } else if (phase == Phase.OVER) {
                overlay(g, w, h, newBest ? "New best!" : "Time's up!", String.valueOf(score),
                        "Best: " + best, "Play again", this::startSolo);
            }
        }

        void renderDuel(Graphics2D g, int w, int h) {
            double cw = Math.min(w - 32, 488);
            double handD = Math.min(cw * 0.7, (h - 100) * 0.36);
            double centerD = Math.min(cw * 0.45, (h - 100) * 0.22);
            boolean live = winner < 0;
            long t = now();

            playerLabel(g, 1, w / 2.0, 34);
            drawCard(g, hands[1], w / 2.0, 50 + handD / 2, handD, shake(duelShake[1]), t < frozenUntil[1],
                    live ? s -> duelTap(1, s) : null);
            drawCard(g, center, w / 2.0, h / 2.0, centerD, 0, false, null);
            drawCard(g, hands[0], w / 2.0, h - 46 - handD / 2, handD, shake(duelShake[0]), t < frozenUntil[0],
                    live ? s -> duelTap(0, s) : null);
            playerLabel(g, 0, w / 2.0, h - 18);

            closeButton(g, 16, h / 2.0 - 20, () -> screen = Screen.MENU);

            if (winner >= 0) {
                overlay(g, w, h, "Player " + (winner + 1) + " wins!", scores[0] + " – " + scores[1],
                        null, "Rematch", this::dealDuel);
            }
        }

        // ---------- Drawing helpers

        void drawCard(Graphics2D g, PlayCard c, double cx, double cy, double d, double shakeX,
                      boolean frozen, IntConsumer onSymbol) {
            double t = Math.min(1, Math.max(0, (now() - c.born) / 280.0));
            double scale = 0.6 + 0.4 * easeOutBack(t);
            cx += shakeX;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(cx, cy);
            g2.scale(scale, scale);
            double r = d / 2;
            g2.setColor(new Color(40, 20, 90, 70));
            g2.fill(new Ellipse2D.Double(-r + d * 0.012, -r + d * 0.03, d, d));
            Ellipse2D face = new Ellipse2D.Double(-r, -r, d, d);
            g2.setColor(Color.WHITE);
            g2.fill(face);
            double bw = Math.max(2, d * 0.012);
            g2.setColor(new Color(51, 31, 115, 30));
            g2.setStroke(new BasicStroke((float) bw));
            g2.draw(new Ellipse2D.Double(-r + bw / 2, -r + bw / 2, d - bw, d - bw));
            for (Slot s : c.slots) drawSymbol(g2, s.symbol, s.x * d, s.y * d, s.size * d, s.rot);
            if (frozen) {
                g2.setColor(new Color(255, 255, 255, 170));
                g2.fill(face);
                drawSnowflake(g2, d * 0.32);
            }
            g2.dispose();
            if (onSymbol != null) hits.add(new CardHit(c, cx, cy, d, onSymbol));
        }

        void drawSnowflake(Graphics2D g, double size) {
            g.setColor(new Color(0x1E78E6));
            g.setStroke(new BasicStroke((float) (size * 0.08), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double r = size / 2;
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                double ex = r * Math.cos(a), ey = r * Math.sin(a);
                g.draw(new Line2D.Double(0, 0, ex, ey));
                double bx = 0.6 * ex, by = 0.6 * ey, br = r * 0.3;
                g.draw(new Line2D.Double(bx, by, bx + br * Math.cos(a + 0.7), by + br * Math.sin(a + 0.7)));
                g.draw(new Line2D.Double(bx, by, bx + br * Math.cos(a - 0.7), by + br * Math.sin(a - 0.7)));
            }
        }

        void playerLabel(Graphics2D g, int p, double cx, double baseline) {
            String label = "Player " + (p + 1);
            g.setFont(font(Font.BOLD, 17));
            FontMetrics fm = g.getFontMetrics();
            double total = fm.stringWidth(label) + 14 + DUEL_TARGET * 13 - 4;
            double x = cx - total / 2;
            g.setColor(Color.WHITE);
            g.drawString(label, (float) x, (float) baseline);
            x += fm.stringWidth(label) + 14;
            for (int i = 0; i < DUEL_TARGET; i++) {
                g.setColor(i < scores[p] ? Color.WHITE : new Color(255, 255, 255, 70));
                g.fill(new Ellipse2D.Double(x + i * 13, baseline - 11, 9, 9));
            }
        }

        void overlay(Graphics2D g, int w, int h, String title, String big, String body,
                     String action, Runnable onAction) {
            g.setColor(new Color(0, 0, 0, 115));
            g.fillRect(0, 0, w, h);
            double pw = Math.min(w - 32, 360), tw = pw - 56;
            g.setFont(font(Font.PLAIN, 16));
            List<String> lines = body == null ? List.of() : wrap(g, body, tw);
            double ph = 28 + 38 + (big != null ? 92 : 0) + lines.size() * 22 + 24 + 52 + 44 + 16;
            double px = (w - pw) / 2, py = (h - ph) / 2;
            g.setColor(new Color(51, 31, 115, 235));
            g.fill(new RoundRectangle2D.Double(px, py, pw, ph, 28, 28));
            double y = py + 28 + 30;
            g.setColor(Color.WHITE);
            g.setFont(font(Font.BOLD, 30));
            centered(g, title, w / 2.0, y);
            if (big != null) {
                g.setFont(font(Font.BOLD, 80));
                centered(g, big, w / 2.0, y + 88);
                y += 92;
            }
            g.setFont(font(Font.PLAIN, 16));
            g.setColor(new Color(255, 255, 255, 215));
            for (String l : lines) {
                y += 22;
                centered(g, l, w / 2.0, y);
            }
            y += 24;
            bigButton(g, px + 28, y, pw - 56, 52, action, false, onAction);
            g.setFont(font(Font.BOLD, 16));
            g.setColor(Color.WHITE);
            centered(g, "Menu", w / 2.0, y + 52 + 30);
            buttons.add(new Btn(new Rectangle2D.Double(w / 2.0 - 50, y + 60, 100, 32), () -> {
                screen = Screen.MENU;
                phase = Phase.READY;
            }));
        }

        void menuButton(Graphics2D g, double x, double y, double w, double h, String title, String sub,
                        int iconSymbol, Runnable action) {
            RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, 28, 28);
            g.setColor(Color.WHITE);
            g.fill(rr);
            g.setColor(new Color(51, 31, 115, 25));
            g.fill(new Ellipse2D.Double(x + 14, y + (h - 48) / 2, 48, 48));
            drawLook(g, iconSymbol, x + 38, y + h / 2, 30, 0);
            g.setColor(INK);
            g.setFont(font(Font.BOLD, 20));
            g.drawString(title, (float) (x + 78), (float) (y + h / 2 - 3));
            g.setFont(font(Font.PLAIN, 14));
            g.setColor(new Color(51, 31, 115, 175));
            g.drawString(sub, (float) (x + 78), (float) (y + h / 2 + 19));
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(51, 31, 115, 120));
            double ax = x + w - 26, ay = y + h / 2;
            g.draw(new Line2D.Double(ax - 5, ay - 7, ax + 2, ay));
            g.draw(new Line2D.Double(ax + 2, ay, ax - 5, ay + 7));
            buttons.add(new Btn(rr.getBounds2D(), action));
        }

        void bigButton(Graphics2D g, double x, double y, double w, double h, String label, boolean inverted,
                       Runnable action) {
            RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, 22, 22);
            g.setColor(inverted ? INK : Color.WHITE);
            g.fill(rr);
            g.setColor(inverted ? Color.WHITE : INK);
            g.setFont(font(Font.BOLD, 19));
            centered(g, label, x + w / 2, y + h / 2 + 7);
            buttons.add(new Btn(rr.getBounds2D(), action));
        }

        /** Draws a pill right-aligned at {@code right}; returns its left edge. */
        double pill(Graphics2D g, double right, double y, String text, boolean warn) {
            g.setFont(font(Font.BOLD, 16));
            double tw = g.getFontMetrics().stringWidth(text), pw = tw + 28;
            g.setColor(warn ? new Color(230, 57, 70, 210) : new Color(255, 255, 255, 50));
            g.fill(new RoundRectangle2D.Double(right - pw, y, pw, 34, 34, 34));
            g.setColor(Color.WHITE);
            g.drawString(text, (float) (right - pw + 14), (float) (y + 23));
            return right - pw;
        }

        void closeButton(Graphics2D g, double x, double y, Runnable action) {
            Ellipse2D c = new Ellipse2D.Double(x, y, 40, 40);
            g.setColor(new Color(255, 255, 255, 50));
            g.fill(c);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(x + 14, y + 14, x + 26, y + 26));
            g.draw(new Line2D.Double(x + 26, y + 14, x + 14, y + 26));
            buttons.add(new Btn(c.getBounds2D(), action));
        }

        double shake(long at) {
            long a = now() - at;
            if (a < 0 || a > 300) return 0;
            double t = a / 300.0;
            return 12 * Math.sin(t * Math.PI * 4) * (1 - t);
        }

        static double easeOutBack(double t) {
            double c1 = 1.70158, c3 = c1 + 1;
            return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
        }

        static Font font(int style, int size) { return new Font(Font.SANS_SERIF, style, size); }

        static void centered(Graphics2D g, String s, double cx, double baseline) {
            g.drawString(s, (float) (cx - g.getFontMetrics().stringWidth(s) / 2.0), (float) baseline);
        }

        static List<String> wrap(Graphics2D g, String text, double width) {
            FontMetrics fm = g.getFontMetrics();
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String trial = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(trial) > width && line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(trial);
                }
            }
            if (line.length() > 0) lines.add(line.toString());
            return lines;
        }

        /** Stops entrance animations, for still images. */
        void settle() {
            for (PlayCard c : new PlayCard[]{demo, top, hand, center, hands[0], hands[1]}) c.born = 0;
        }
    }

    // ============================================================ App icon

    static BufferedImage makeIcon(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, G1, s, s, G2));
        g.fill(new RoundRectangle2D.Double(0, 0, s, s, s * 0.45, s * 0.45));
        double r1 = s * 0.29, r2 = s * 0.24;
        g.setColor(new Color(40, 20, 90, 120));
        g.fill(new Ellipse2D.Double(s * 0.36 - r1 + s * 0.015, s * 0.55 - r1 + s * 0.02, 2 * r1, 2 * r1));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(s * 0.36 - r1, s * 0.55 - r1, 2 * r1, 2 * r1));
        g.setColor(new Color(40, 20, 90, 120));
        g.fill(new Ellipse2D.Double(s * 0.67 - r2 + s * 0.015, s * 0.42 - r2 + s * 0.02, 2 * r2, 2 * r2));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(s * 0.67 - r2, s * 0.42 - r2, 2 * r2, 2 * r2));
        drawLook(g, 13, s * 0.33, s * 0.56, s * 0.24, 0.2);
        drawLook(g, 13, s * 0.69, s * 0.41, s * 0.20, -0.3);
        drawLook(g, 4, s * 0.45, s * 0.42, s * 0.12, 0);
        drawLook(g, 33, s * 0.42, s * 0.73, s * 0.10, 0);
        drawLook(g, 27, s * 0.80, s * 0.50, s * 0.09, 0.4);
        g.dispose();
        return img;
    }

    // ============================================================ Entry points

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--test")) { selfTest(); return; }
        if (args.length > 1 && args[0].equals("--snapshot")) { snapshots(new File(args[1])); return; }
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Pairo");
            Game game = new Game();
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            game.setPreferredSize(new Dimension(480, Math.min(820, screen.height - 80)));
            f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            f.setContentPane(game);
            f.pack();
            f.setMinimumSize(new Dimension(360, 600));
            f.setLocationRelativeTo(null);
            f.setIconImage(makeIcon(256));
            f.setVisible(true);
            game.requestFocusInWindow();
            game.startLoop();
        });
    }

    static void selfTest() {
        int fails = 0;
        if (CARDS.length != 57) { System.out.println("FAIL deck size " + CARDS.length); fails++; }
        for (int[] c : CARDS) {
            Set<Integer> s = new HashSet<>();
            for (int x : c) s.add(x);
            if (s.size() != PER_CARD) { System.out.println("FAIL card size"); fails++; }
        }
        for (int i = 0; i < CARDS.length; i++)
            for (int j = i + 1; j < CARDS.length; j++) {
                int n = 0;
                for (int x : CARDS[i]) for (int y : CARDS[j]) if (x == y) n++;
                if (n != 1) { System.out.println("FAIL pair " + i + "," + j + " shares " + n); fails++; }
            }
        Set<String> names = new HashSet<>();
        for (int s = 0; s < 57; s++) names.add(symbolName(s));
        if (names.size() != 57) { System.out.println("FAIL symbols not unique"); fails++; }

        // Play 1000 correct + 200 wrong taps through the real game logic, headless.
        System.setProperty("java.awt.headless", "true");
        Game g = new Game();
        g.startSolo();
        for (int i = 0; i < 1000; i++) {
            g.timeLeft = 60;
            g.soloTap(shared(g.top.index, g.hand.index));
            if (g.top.index == g.hand.index) { System.out.println("FAIL duplicate card"); fails++; break; }
        }
        if (g.score != 1000) { System.out.println("FAIL solo score " + g.score); fails++; }
        int wrong = -1;
        for (Slot s : g.hand.slots) if (s.symbol != shared(g.top.index, g.hand.index)) wrong = s.symbol;
        double before = g.timeLeft;
        g.soloTap(wrong);
        if (Math.abs(before - PENALTY - g.timeLeft) > 1e-9) { System.out.println("FAIL penalty"); fails++; }

        g.dealDuel();
        while (g.winner < 0) {
            int p = g.rnd.nextInt(2);
            g.frozenUntil[p] = 0;
            g.duelTap(p, shared(g.center.index, g.hands[p].index));
            Set<Integer> inPlay = Set.of(g.center.index, g.hands[0].index, g.hands[1].index);
            if (inPlay.size() != 3) { System.out.println("FAIL duel duplicate"); fails++; break; }
        }
        if (g.scores[g.winner] != DUEL_TARGET) { System.out.println("FAIL duel target"); fails++; }

        System.out.println(fails == 0
                ? "OK: 57 cards, 8 symbols each, all 1596 pairs share exactly one symbol; solo and duel logic pass."
                : fails + " failure(s)");
        if (fails > 0) System.exit(1);
    }

    /** Renders every screen to PNGs without opening a window (used for screenshots). */
    static void snapshots(File dir) throws Exception {
        System.setProperty("java.awt.headless", "true");
        dir.mkdirs();
        int w = 480, h = 820, k = 2;
        Game g = new Game();
        g.best = 23;
        String[] names = {"menu", "solo", "solo-over", "duel", "rules"};
        for (String name : names) {
            switch (name) {
                case "menu" -> g.screen = Game.Screen.MENU;
                case "solo" -> { g.screen = Game.Screen.SOLO; g.startSolo(); g.score = 17; g.timeLeft = 34.2;
                                 System.out.println("Solo shot: shared symbol is "
                                         + symbolName(shared(g.top.index, g.hand.index))); }
                case "solo-over" -> { g.phase = Game.Phase.OVER; g.score = 26; g.timeLeft = 0; g.newBest = true; g.best = 26; }
                case "duel" -> { g.screen = Game.Screen.DUEL; g.dealDuel(); g.scores[0] = 6; g.scores[1] = 4;
                                 g.frozenUntil[1] = now() + 60_000; }
                case "rules" -> g.screen = Game.Screen.RULES;
            }
            g.settle();
            BufferedImage img = new BufferedImage(w * k, h * k, BufferedImage.TYPE_INT_RGB);
            Graphics2D gr = img.createGraphics();
            gr.scale(k, k);
            g.render(gr, w, h);
            ImageIO.write(img, "png", new File(dir, name + ".png"));
        }
        ImageIO.write(makeIcon(512), "png", new File(dir, "icon.png"));
        System.out.println("Wrote " + names.length + " screens + icon to " + dir);
    }
}
