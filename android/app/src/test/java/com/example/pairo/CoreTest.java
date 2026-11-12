package com.example.pairo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

public class CoreTest {

    @Test
    public void everyDeckIsAValidProjectivePlane() {
        for (Core.Deck deck : Core.DECKS) {
            int n = deck.order;
            assertEquals(n * n + n + 1, deck.cards.length);
            for (int[] card : deck.cards) {
                Set<Integer> s = new HashSet<>();
                for (int x : card) {
                    assertTrue(x >= 0 && x < Core.SYMBOLS);
                    s.add(x);
                }
                assertEquals(n + 1, s.size());
            }
            for (int i = 0; i < deck.cards.length; i++) {
                for (int j = i + 1; j < deck.cards.length; j++) {
                    int shared = 0;
                    for (int x : deck.cards[i]) for (int y : deck.cards[j]) if (x == y) shared++;
                    assertEquals("order " + n + " cards " + i + "," + j, 1, shared);
                }
            }
        }
        assertEquals(57, Core.CARDS.length);
    }

    @Test
    public void symbolLooksArePermutation() {
        Set<Integer> looks = new HashSet<>();
        for (int s = 0; s < Core.SYMBOLS; s++) looks.add((s * 13) % Core.SYMBOLS);
        assertEquals(Core.SYMBOLS, looks.size());
    }

    @Test
    public void layoutsStayOnTheCardAndDoNotOverlap() {
        Random r = new Random(5);
        for (Core.Deck deck : Core.DECKS) {
            for (boolean tiny : new boolean[]{false, true}) {
                for (int trial = 0; trial < 300; trial++) {
                    Core.Slot[] slots = Core.layout(deck.cards[trial % deck.cards.length], tiny, r);
                    for (Core.Slot a : slots) {
                        float reach = (float) Math.hypot(a.x, a.y) + a.size * 0.5f;
                        assertTrue("order " + deck.order + " reach " + reach, reach <= 0.48f);
                    }
                    for (int i = 0; i < slots.length; i++) {
                        for (int j = i + 1; j < slots.length; j++) {
                            Core.Slot a = slots[i], b = slots[j];
                            double dist = Math.hypot(a.x - b.x, a.y - b.y);
                            assertTrue("order " + deck.order + " overlap", dist >= 0.45 * (a.size + b.size));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void wheelSymbolsNeverTouchAtAnyAngle() {
        Random r = new Random(9);
        float[] xy = new float[2];
        Core.Round g = new Core.Round(r, 0, new Core.Progress());
        g.setup(Core.WHEELS, -1);
        for (int trial = 0; trial < 400; trial++) {
            Core.PlayCard card = new Core.PlayCard(Core.FULL, trial % 57, r, Core.WHEELS);
            assertTrue(card.wheel);
            assertEquals(8, card.slots.length);
            assertTrue(card.ringSpeed[1] * card.ringSpeed[2] < 0); // opposite directions
            card.ringAngle[1] = r.nextFloat() * 360;
            card.ringAngle[2] = r.nextFloat() * 360;
            float[][] p = new float[8][];
            for (int i = 0; i < 8; i++) {
                Core.slotXY(card, card.slots[i], xy);
                p[i] = new float[]{xy[0], xy[1], card.slots[i].size};
                assertTrue(Math.hypot(xy[0], xy[1]) + p[i][2] / 2 <= 0.48);
            }
            for (int i = 0; i < 8; i++) {
                for (int j = i + 1; j < 8; j++) {
                    double dist = Math.hypot(p[i][0] - p[j][0], p[i][1] - p[j][1]);
                    assertTrue("touching", dist >= (p[i][2] + p[j][2]) / 2);
                }
            }
        }
    }

    @Test
    public void wheelsModeSpeedsUpAndKeepsItsOwnBest() {
        Core.Round g = new Core.Round(new Random(3), 7, new Core.Progress());
        g.setup(Core.WHEELS, -1);
        assertEquals(0, g.best);
        g.start();
        g.countdownAt = Core.LONG_AGO;
        g.countdown(Core.now());
        float before = g.hand.ringAngle[1];
        g.animate(Core.now(), 0.5f, true);
        assertNotEquals(before, g.hand.ringAngle[1], 1e-6);
        for (int i = 0; i < 12; i++) assertEquals(Core.Result.MATCH, g.tap(g.shared()));
        assertEquals(1.96f, Core.wheelSpeed(12), 1e-4);
        assertEquals(2.6f, Core.wheelSpeed(100), 1e-4);
        assertTrue(g.tick(100));
        assertTrue(g.newBest);
        assertEquals(12, (int) g.bests.get("bestWheels"));
        g.setup(Core.SPRINT, -1);
        assertEquals(7, g.best);
    }

    @Test
    public void sprintScoresMatchesAndPenalisesMisses() {
        Core.Round g = new Core.Round(new Random(1), 0, new Core.Progress());
        assertEquals(Core.Result.IGNORED, g.tap(0));
        g.start();
        assertEquals(Core.Round.Phase.COUNTDOWN, g.phase);
        assertEquals(Core.Result.IGNORED, g.tap(g.shared()));
        assertEquals(Core.Countdown.TICK, g.countdown(Core.now()));
        assertEquals(Core.Countdown.NONE, g.countdown(Core.now()));
        g.countdownAt = Core.LONG_AGO;
        assertEquals(Core.Countdown.GO, g.countdown(Core.now()));
        assertEquals(Core.Round.Phase.PLAYING, g.phase);
        for (int i = 0; i < 1000; i++) {
            g.timeLeft = 60;
            assertEquals(Core.Result.MATCH, g.tap(g.shared()));
            assertNotEquals(g.top.index, g.hand.index);
            assertNotEquals(g.leaving.index, g.top.index);
        }
        assertEquals(1000, g.score);

        int answer = g.shared();
        int wrong = -1;
        for (Core.Slot s : g.hand.slots) if (s.symbol != answer) wrong = s.symbol;
        double before = g.timeLeft;
        assertEquals(Core.Result.MISS, g.tap(wrong));
        assertEquals(before - 3, g.timeLeft, 1e-9);

        g.timeLeft = 4.5;
        assertTrue(g.warnTick());
        assertFalse(g.warnTick());
        assertTrue(g.tick(100));
        assertEquals(Core.Round.Phase.OVER, g.phase);
        assertTrue(g.newBest);
        assertEquals(1000, g.best);
    }

    @Test
    public void levelsAwardStarsUnlockAndUseTheirDeck() {
        Core.Progress progress = new Core.Progress();
        assertTrue(progress.unlocked(0));
        assertFalse(progress.unlocked(1));
        Core.Round g = new Core.Round(new Random(2), 0, progress);

        for (int i = 0; i < Core.LEVELS.length; i++) {
            Core.Level L = Core.LEVELS[i];
            g.setup(L, i);
            assertEquals(L.order, g.deck.order);
            assertEquals(L.order + 1, g.top.slots.length);
            g.start();
            g.countdownAt = Core.LONG_AGO;
            g.countdown(Core.now());
            // Exactly reach two stars.
            for (int m = 0; m < L.stars[1]; m++) {
                g.animate(Core.now() + 10_000, 0.016f, true); // spin and reshuffle must not break matching
                assertEquals(Core.Result.MATCH, g.tap(g.shared()));
            }
            assertTrue(g.tick(1000));
            assertEquals(2, g.stars);
            assertTrue(g.improved);
            assertTrue(g.passed());
            assertTrue(progress.unlocked(Math.min(i + 1, Core.LEVELS.length - 1)));
        }
        assertEquals(2 * Core.LEVELS.length, progress.total());
        assertEquals(Core.LEVELS.length - 1, progress.current());

        // A worse result does not overwrite a better one, and a miss costs the level's penalty.
        g.setup(Core.LEVELS[8], 8);
        g.start();
        g.countdownAt = Core.LONG_AGO;
        g.countdown(Core.now());
        int answer = g.shared(), wrong = -1;
        for (Core.Slot s : g.hand.slots) if (s.symbol != answer) wrong = s.symbol;
        double before = g.timeLeft;
        g.tap(wrong);
        assertEquals(before - 6, g.timeLeft, 1e-9);
        g.tick(1000);
        assertEquals(0, g.stars);
        assertFalse(g.improved);
        assertEquals(2, progress.stars[8]);
    }

    @Test
    public void progressRoundTripsThroughText() {
        Core.Progress p = new Core.Progress();
        p.record(0, 3);
        p.record(1, 1);
        Core.Progress q = Core.Progress.parse(p.serialize());
        assertArrayEquals(p.stars, q.stars);
        assertEquals(0, Core.Progress.parse("garbage,,7").stars[0]);
        assertEquals(3, Core.Progress.parse("garbage,,7").stars[2]);
        assertEquals(0, Core.Progress.parse(null).total());
    }

    @Test
    public void starThresholdsGrowWithGoal() {
        for (Core.Level L : Core.LEVELS) {
            assertEquals(L.goal, L.stars[0]);
            assertTrue(L.stars[1] > L.stars[0]);
            assertTrue(L.stars[2] > L.stars[1]);
            assertEquals(0, L.starsFor(L.goal - 1));
            assertEquals(3, L.starsFor(L.stars[2]));
        }
    }

    @Test
    public void duelReachesTargetWithThreeDistinctCardsInPlay() {
        Core.Duel d = new Core.Duel(new Random(2));
        Random r = new Random(3);
        while (d.winner < 0) {
            int p = r.nextInt(2);
            d.frozenUntil[p] = Core.LONG_AGO;
            d.tap(p, Core.shared(d.center.index, d.hands[p].index));
            Set<Integer> inPlay = new HashSet<>();
            inPlay.add(d.center.index);
            inPlay.add(d.hands[0].index);
            inPlay.add(d.hands[1].index);
            assertEquals(3, inPlay.size());
        }
        assertEquals(Core.Duel.TARGET, d.scores[d.winner]);
        assertEquals(Core.Result.IGNORED, d.tap(0, 0));
    }

    @Test
    public void duelMissFreezesPlayer() {
        Core.Duel d = new Core.Duel(new Random(4));
        int answer = Core.shared(d.center.index, d.hands[0].index);
        int wrong = -1;
        for (Core.Slot s : d.hands[0].slots) if (s.symbol != answer) wrong = s.symbol;
        assertEquals(Core.Result.MISS, d.tap(0, wrong));
        assertTrue(d.frozen(0));
        assertEquals(Core.Result.IGNORED, d.tap(0, answer));
        assertEquals(Core.Result.MATCH, d.tap(1, Core.shared(d.center.index, d.hands[1].index)));
    }
}
