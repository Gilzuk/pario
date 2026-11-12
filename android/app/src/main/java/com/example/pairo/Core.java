package com.example.pairo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Pure-Java game core: decks, dealing, levels, progress, and both game modes.
 * No Android imports, so it runs and unit-tests on a plain JVM.
 *
 * A deck is the finite projective plane of prime order n: n²+n+1 cards with
 * n+1 symbols each, and any two cards share exactly one symbol. Levels use
 * n = 2, 3, 5 and 7; Solo Sprint and Duel use the full order-7 deck
 * (57 cards, 8 symbols per card).
 */
final class Core {
    private Core() {}

    static final int SYMBOLS = 57; // symbols in the largest deck; the drawing code has a look for each

    /** Monotonic milliseconds. */
    static long now() { return System.nanoTime() / 1_000_000L; }

    /** A time far enough in the past that no animation is running. */
    static final long LONG_AGO = Long.MIN_VALUE / 4;

    // ------------------------------------------------------------------ Decks

    static final class Deck {
        final int order;
        final int[][] cards;

        Deck(int order) {
            this.order = order;
            this.cards = build(order);
        }

        int perCard() { return order + 1; }

        int shared(int a, int b) {
            for (int x : cards[a]) for (int y : cards[b]) if (x == y) return x;
            return -1;
        }

        private static int[][] build(int n) {
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
    }

    static final Deck[] DECKS = {new Deck(2), new Deck(3), new Deck(5), new Deck(7)};
    static final Deck FULL = DECKS[3];
    /** The full deck's cards (kept for the original tests and callers). */
    static final int[][] CARDS = FULL.cards;

    static Deck deck(int order) {
        for (Deck d : DECKS) if (d.order == order) return d;
        throw new IllegalArgumentException("No deck of order " + order);
    }

    static int shared(int a, int b) { return FULL.shared(a, b); }

    /** Draws card indices from a shuffled pile, reshuffling when it runs out. */
    static final class Dealer {
        private final Deck deck;
        private final Random rnd;
        private final List<Integer> pile = new ArrayList<>();

        Dealer(Deck deck, Random rnd) { this.deck = deck; this.rnd = rnd; }

        Dealer(Random rnd) { this(FULL, rnd); }

        int draw(int... inPlay) {
            while (true) {
                if (pile.isEmpty()) {
                    for (int i = 0; i < deck.cards.length; i++) pile.add(i);
                    Collections.shuffle(pile, rnd);
                }
                int next = pile.remove(pile.size() - 1);
                boolean free = true;
                for (int a : inPlay) if (a == next) free = false;
                if (free) return next;
            }
        }
    }

    // ------------------------------------------------------------------ Cards

    /** One symbol placed on a card. Position and size are fractions of the card diameter. */
    static final class Slot {
        final int symbol;
        final float x, y, size, rotDeg;
        /** Wheel cards: 1 = inner ring, 2 = outer ring; 0 for ordinary cards. */
        final int ring;

        Slot(int symbol, float x, float y, float size, float rotDeg) { this(symbol, x, y, size, rotDeg, 0); }

        Slot(int symbol, float x, float y, float size, float rotDeg, int ring) {
            this.symbol = symbol; this.x = x; this.y = y; this.size = size; this.rotDeg = rotDeg; this.ring = ring;
        }
    }

    /** Ring radii for wheel cards (fractions of the card diameter). */
    static final float[] WHEEL_R = {0, 0.15f, 0.34f};

    /**
     * Wheel cards: 3 symbols on an inner ring and 5 on an outer ring that turn in opposite
     * directions. The rings sit far enough apart that symbols never touch at any angle.
     */
    static Slot[] layoutWheel(int[] symbols, Random r) {
        List<Integer> syms = new ArrayList<>();
        for (int s : symbols) syms.add(s);
        Collections.shuffle(syms, r);
        int[] counts = {0, 3, 5};
        Slot[] slots = new Slot[symbols.length];
        int i = 0;
        for (int ring = 1; ring <= 2; ring++) {
            double base = r.nextDouble() * 2 * Math.PI;
            for (int j = 0; j < counts[ring]; j++, i++) {
                double ang = base + j * 2 * Math.PI / counts[ring];
                slots[i] = new Slot(syms.get(i), (float) (WHEEL_R[ring] * Math.cos(ang)),
                        (float) (WHEEL_R[ring] * Math.sin(ang)), 0.15f + r.nextFloat() * 0.03f,
                        r.nextFloat() * 360f, ring);
            }
        }
        return slots;
    }

    /** A slot's position on its card, in card units, after its ring has turned. Writes into out[0..1]. */
    static void slotXY(PlayCard c, Slot s, float[] out) {
        if (!c.wheel || s.ring == 0) {
            out[0] = s.x;
            out[1] = s.y;
            return;
        }
        double a = Math.toRadians(c.ringAngle[s.ring]);
        float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
        out[0] = s.x * cos - s.y * sin;
        out[1] = s.x * sin + s.y * cos;
    }

    /** Wheels speed up as you score: x1 at the start, +8% per match, at most x2.6. */
    static float wheelSpeed(int score) { return Math.min(2.6f, 1 + score * 0.08f); }

    /**
     * Places a card's symbols. Cards with fewer symbols draw them bigger, all on a ring;
     * "tiny" widens the size range so some symbols are very small and others large.
     */
    static Slot[] layout(int[] symbols, boolean tiny, Random r) {
        List<Integer> syms = new ArrayList<>();
        for (int s : symbols) syms.add(s);
        Collections.shuffle(syms, r);
        int k = syms.size();
        float mul = k >= 8 ? 1f : k >= 6 ? 1.15f : k == 4 ? 1.4f : 1.5f;
        boolean ringOnly = k <= 4;
        float ringR = k == 3 ? 0.24f : k == 4 ? 0.26f : k == 6 ? 0.3f : 0.29f;
        int ring = ringOnly ? k : k - 1;
        double base = r.nextDouble() * 2 * Math.PI;
        Slot[] slots = new Slot[k];
        for (int i = 0; i < k; i++) {
            float rot = r.nextFloat() * 360f;
            if (!ringOnly && i == 0) {
                slots[i] = new Slot(syms.get(i), 0, 0, size(0.21f, 0.26f, tiny, r) * mul, rot);
            } else {
                int j = ringOnly ? i : i - 1;
                double ang = base + j * 2 * Math.PI / ring + (r.nextDouble() - 0.5) * 0.2;
                double rad = ringR + r.nextDouble() * 0.03;
                slots[i] = new Slot(syms.get(i), (float) (rad * Math.cos(ang)), (float) (rad * Math.sin(ang)),
                        size(0.14f, 0.19f, tiny, r) * mul, rot);
            }
        }
        return slots;
    }

    private static float size(float lo, float hi, boolean tiny, Random r) {
        return tiny ? lo * 0.6f + r.nextFloat() * (hi * 1.25f - lo * 0.6f) : lo + r.nextFloat() * (hi - lo);
    }

    /** A card in play: its deck index plus a randomised layout, spin and animation timing. */
    static final class PlayCard {
        final int index;
        final int[] symbols;
        Slot[] slots;
        /** When the pop-in animation started. */
        long born = now();
        /** When the card started flying to a new spot, and from which player's spot (-1 = solo). */
        long flyAt = LONG_AGO;
        int flyFrom = -1;
        /** Spinning levels: degrees per second, and the current angle. */
        final float spin;
        float angle;
        /** Reshuffle levels: when the symbols last moved. */
        long layoutAt;
        /** Wheel cards: each ring's angle and speed (degrees, degrees per second at speed x1). */
        final boolean wheel;
        final float[] ringAngle = new float[3], ringSpeed = new float[3];

        PlayCard(Deck deck, int index, Random r, Level cfg) {
            this.index = index;
            this.symbols = deck.cards[index];
            this.wheel = cfg != null && cfg.wheels && deck.order == 7;
            this.slots = wheel ? layoutWheel(symbols, r) : layout(symbols, cfg != null && cfg.tiny, r);
            if (wheel) {
                ringAngle[1] = r.nextFloat() * 360;
                ringAngle[2] = r.nextFloat() * 360;
                float dir = r.nextBoolean() ? 1 : -1;
                ringSpeed[1] = dir * (24 + r.nextFloat() * 8);
                ringSpeed[2] = -dir * (16 + r.nextFloat() * 6);
            }
            boolean spinning = cfg != null && cfg.spin;
            this.spin = spinning ? (r.nextBoolean() ? -1 : 1) * (14 + r.nextFloat() * 10) : 0;
            this.angle = spinning ? r.nextFloat() * 360 : 0;
            this.layoutAt = now() - (long) (r.nextFloat() * 1300);
        }

        PlayCard(int index, Random r) { this(FULL, index, r, null); }
    }

    enum Result { IGNORED, MATCH, MISS }

    enum Countdown { NONE, TICK, GO }

    // ------------------------------------------------------------------ Levels

    static final class Level {
        final String name, tip;
        final int order, seconds, goal, penalty;
        final boolean spin, tiny, reshuffle, wheels;
        /** Where the best score of an endless mode is saved; null for levels. */
        final String bestKey;
        /** Matches needed for one, two and three stars. */
        final int[] stars;

        Level(String name, int order, int seconds, int goal, int penalty,
              boolean spin, boolean tiny, boolean reshuffle, String tip) {
            this(name, order, seconds, goal, penalty, spin, tiny, reshuffle, false, null, tip);
        }

        Level(String name, int order, int seconds, int goal, int penalty,
              boolean spin, boolean tiny, boolean reshuffle, boolean wheels, String bestKey, String tip) {
            this.name = name; this.order = order; this.seconds = seconds; this.goal = goal;
            this.penalty = penalty; this.spin = spin; this.tiny = tiny; this.reshuffle = reshuffle;
            this.wheels = wheels; this.bestKey = bestKey; this.tip = tip;
            this.stars = goal > 0
                    ? new int[]{goal, (int) Math.ceil(goal * 1.35), (int) Math.ceil(goal * 1.7)}
                    : new int[0];
        }

        int starsFor(int score) {
            int n = 0;
            for (int s : stars) if (score >= s) n++;
            return n;
        }
    }

    static final Level SPRINT = new Level("Solo Sprint", 7, 60, 0, 3, false, false, false, false, "bestSolo", "");
    static final Level WHEELS = new Level("Wheels", 7, 60, 0, 3, false, false, false, true, "bestWheels", "");

    static final Level[] LEVELS = {
            new Level("Warm-up", 2, 30, 8, 3, false, false, false, "Three symbols per card."),
            new Level("Four's Company", 3, 30, 8, 3, false, false, false, "Four symbols per card."),
            new Level("Six Sense", 5, 40, 8, 3, false, false, false, "Six symbols per card."),
            new Level("Full Deck", 7, 45, 8, 3, false, false, false, "The full deck: eight symbols per card."),
            new Level("Spin Cycle", 5, 40, 8, 3, true, false, false, "The cards spin."),
            new Level("Tiny Print", 7, 45, 9, 3, false, true, false, "Some symbols are very small."),
            new Level("Whirlpool", 7, 45, 9, 3, true, false, false, "Eight symbols on spinning cards."),
            new Level("Reshuffle", 7, 45, 9, 3, false, false, true, "Symbols jump to new spots every few seconds."),
            new Level("Sharp Eyes", 7, 50, 10, 6, false, false, false, "A wrong tap costs 6 seconds."),
            new Level("Pressure", 7, 30, 9, 3, false, false, false, "Only 30 seconds on the clock."),
            new Level("Chaos", 7, 50, 10, 3, true, true, false, "Spinning cards and tiny symbols."),
            new Level("Grandmaster", 7, 50, 11, 5, true, true, true,
                    "Everything at once. A wrong tap costs 5 seconds."),
    };

    /** Stars earned per level. Stored as text like "3,2,0,..." by the caller. */
    static final class Progress {
        final int[] stars = new int[LEVELS.length];

        static Progress parse(String saved) {
            Progress p = new Progress();
            if (saved == null) return p;
            String[] parts = saved.split(",");
            for (int i = 0; i < parts.length && i < p.stars.length; i++) {
                try {
                    p.stars[i] = Math.max(0, Math.min(3, Integer.parseInt(parts[i].trim())));
                } catch (NumberFormatException ignored) {
                    // leave 0
                }
            }
            return p;
        }

        String serialize() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < stars.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(stars[i]);
            }
            return sb.toString();
        }

        boolean unlocked(int i) { return i == 0 || stars[i - 1] > 0; }

        int total() {
            int t = 0;
            for (int s : stars) t += s;
            return t;
        }

        /** The first level without a star (the last level once all have one). */
        int current() {
            for (int i = 0; i < stars.length; i++) if (stars[i] == 0) return i;
            return stars.length - 1;
        }

        /** Keeps the better result. Returns true if this improved the level. */
        boolean record(int level, int earned) {
            if (earned > stars[level]) {
                stars[level] = earned;
                return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ Timed round (Sprint or a level)

    static final class Round {
        static final int COUNTDOWN_S = 3;
        static final long RESHUFFLE_MS = 2600, FLY_MS = 320;

        enum Phase { READY, COUNTDOWN, PLAYING, OVER }

        private final Random rnd;
        final Progress progress;
        Level cfg = SPRINT;
        /** -1 for Solo Sprint, otherwise the level index. */
        int level = -1;
        Deck deck = FULL;
        private Dealer dealer;
        Phase phase = Phase.READY;
        PlayCard top, hand;
        int score, best;
        double timeLeft;
        boolean newBest, improved;
        int stars;
        long shakeAt = LONG_AGO;
        long countdownAt = LONG_AGO, goAt = LONG_AGO, bumpAt = LONG_AGO, overAt = LONG_AGO;
        /** The card that was on top before the last match; drawn leaving the table. */
        PlayCard leaving;
        long leavingAt = LONG_AGO;
        private int lastCount = -1, lastWarnSec = -1;
        /** Best scores of the endless modes, by their save key. */
        final Map<String, Integer> bests = new HashMap<>();

        Round(Random rnd, int best, Progress progress) {
            this.rnd = rnd;
            this.progress = progress;
            bests.put(SPRINT.bestKey, best);
            setup(SPRINT, -1);
        }

        /** Chooses Solo Sprint (level -1) or a level, and shows its intro. */
        void setup(Level cfg, int level) {
            this.cfg = cfg;
            this.level = level;
            this.deck = deck(cfg.order);
            this.dealer = new Dealer(deck, rnd);
            if (cfg.bestKey != null) best = bests.getOrDefault(cfg.bestKey, 0);
            phase = Phase.READY;
            score = 0;
            timeLeft = cfg.seconds;
            leaving = null;
            deal();
        }

        private void deal() {
            int a = dealer.draw();
            int b = dealer.draw(a);
            top = new PlayCard(deck, a, rnd, cfg);
            hand = new PlayCard(deck, b, rnd, cfg);
        }

        boolean isLevel() { return level >= 0; }

        boolean passed() { return isLevel() && stars > 0; }

        int shared() { return deck.shared(top.index, hand.index); }

        /** Deals new cards and starts the 3-2-1 countdown. */
        void start() {
            deal();
            score = 0;
            timeLeft = cfg.seconds;
            newBest = false;
            improved = false;
            stars = 0;
            leaving = null;
            phase = Phase.COUNTDOWN;
            countdownAt = now();
            lastCount = -1;
            lastWarnSec = -1;
        }

        /** Advances the countdown; reports each new number (TICK) and the moment play begins (GO). */
        Countdown countdown(long t) {
            if (phase != Phase.COUNTDOWN) return Countdown.NONE;
            long e = t - countdownAt;
            if (e >= COUNTDOWN_S * 1000L) {
                phase = Phase.PLAYING;
                goAt = t;
                top.born = t;
                hand.born = t;
                return Countdown.GO;
            }
            int n = COUNTDOWN_S - (int) (e / 1000);
            if (n != lastCount) {
                lastCount = n;
                return Countdown.TICK;
            }
            return Countdown.NONE;
        }

        /** Spins cards and, on reshuffle levels, moves symbols every few seconds. */
        void animate(long t, float dt, boolean spinAllowed) {
            if (phase != Phase.PLAYING) return;
            if (spinAllowed) {
                for (PlayCard c : new PlayCard[]{top, hand, leaving}) if (c != null) c.angle += c.spin * dt;
            }
            if (cfg.wheels) {
                float k = wheelSpeed(score) * (spinAllowed ? 1 : 0.4f);
                for (PlayCard c : new PlayCard[]{top, hand, leaving}) {
                    if (c != null && c.wheel) {
                        c.ringAngle[1] += c.ringSpeed[1] * k * dt;
                        c.ringAngle[2] += c.ringSpeed[2] * k * dt;
                    }
                }
            }
            if (cfg.reshuffle) {
                for (PlayCard c : new PlayCard[]{top, hand}) {
                    if (t - c.layoutAt > RESHUFFLE_MS && t - c.flyAt > FLY_MS) {
                        c.slots = layout(c.symbols, cfg.tiny, rnd);
                        c.layoutAt = t;
                        c.born = t;
                    }
                }
            }
        }

        /** True once per second during the final five seconds of a round. */
        boolean warnTick() {
            if (phase != Phase.PLAYING) return false;
            int secs = (int) Math.ceil(timeLeft);
            if (secs <= 5 && secs != lastWarnSec) {
                lastWarnSec = secs;
                return true;
            }
            return false;
        }

        Result tap(int symbol) {
            if (phase != Phase.PLAYING) return Result.IGNORED;
            if (symbol == shared()) {
                score++;
                int next = dealer.draw(top.index, hand.index);
                leaving = top;
                leavingAt = now();
                top = hand;
                top.born = LONG_AGO;
                top.flyAt = now();
                hand = new PlayCard(deck, next, rnd, cfg);
                bumpAt = now();
                return Result.MATCH;
            }
            shakeAt = now();
            timeLeft -= cfg.penalty;
            if (timeLeft <= 0) finish();
            return Result.MISS;
        }

        /** Advances the clock. Returns true if this call ended the round. */
        boolean tick(double dtSeconds) {
            if (phase != Phase.PLAYING) return false;
            timeLeft -= dtSeconds;
            if (timeLeft <= 0) {
                finish();
                return true;
            }
            return false;
        }

        private void finish() {
            timeLeft = 0;
            phase = Phase.OVER;
            overAt = now();
            if (isLevel()) {
                stars = cfg.starsFor(score);
                improved = progress.record(level, stars);
            } else if (score > best) {
                best = score;
                newBest = true;
                if (cfg.bestKey != null) bests.put(cfg.bestKey, best);
            }
        }
    }

    // ------------------------------------------------------------------ Duel

    /** Duel: two players on one device race to match the centre card. First to 10 wins. */
    static final class Duel {
        static final int TARGET = 10;
        static final long FREEZE_MS = 1500;

        private final Random rnd;
        private final Dealer dealer;
        PlayCard center;
        final PlayCard[] hands = new PlayCard[2];
        final int[] scores = new int[2];
        final long[] frozenUntil = new long[2];
        final long[] shakeAt = {LONG_AGO, LONG_AGO};
        int winner = -1;
        /** The previous centre card; drawn shrinking away. */
        PlayCard leaving;
        long leavingAt = LONG_AGO;

        Duel(Random rnd) {
            this.rnd = rnd;
            this.dealer = new Dealer(rnd);
            reset();
        }

        void reset() {
            int c = dealer.draw();
            int p0 = dealer.draw(c);
            int p1 = dealer.draw(c, p0);
            center = new PlayCard(c, rnd);
            hands[0] = new PlayCard(p0, rnd);
            hands[1] = new PlayCard(p1, rnd);
            scores[0] = scores[1] = 0;
            frozenUntil[0] = frozenUntil[1] = LONG_AGO;
            winner = -1;
            leaving = null;
        }

        boolean frozen(int p) { return now() < frozenUntil[p]; }

        Result tap(int p, int symbol) {
            if (winner >= 0 || frozen(p)) return Result.IGNORED;
            if (symbol == shared(center.index, hands[p].index)) {
                scores[p]++;
                int next = dealer.draw(center.index, hands[0].index, hands[1].index);
                leaving = center;
                leavingAt = now();
                center = hands[p];
                center.born = LONG_AGO;
                center.flyAt = now();
                center.flyFrom = p;
                hands[p] = new PlayCard(next, rnd);
                if (scores[p] >= TARGET) winner = p;
                return Result.MATCH;
            }
            shakeAt[p] = now();
            frozenUntil[p] = now() + FREEZE_MS;
            return Result.MISS;
        }
    }
}
