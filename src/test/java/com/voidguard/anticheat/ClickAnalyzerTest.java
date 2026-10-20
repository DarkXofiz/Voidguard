package com.voidguard.anticheat;

import static org.junit.jupiter.api.Assertions.*;

import com.voidguard.anticheat.math.ClickAnalyzer;
import com.voidguard.anticheat.math.RateWindow;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ClickAnalyzerTest {

    @Test
    void perfectlyRegularClicksHaveZeroDeviation() {
        ClickAnalyzer a = new ClickAnalyzer(60);
        long t = 0;
        for (int i = 0; i < 50; i++) { a.add(t); t += 100; }
        assertEquals(0.0, a.stdDev(), 1e-9);
        assertEquals(100.0, a.meanInterval(), 1e-9);
    }

    @Test
    void humanLikeClicksHaveHighDeviation() {
        ClickAnalyzer a = new ClickAnalyzer(60);
        Random r = new Random(1);
        long t = 0;
        for (int i = 0; i < 50; i++) { a.add(t); t += 80 + r.nextInt(140); }
        assertTrue(a.stdDev() > 20.0);
    }

    @Test
    void cpsCountsWindow() {
        ClickAnalyzer a = new ClickAnalyzer(60);
        for (int i = 0; i < 30; i++) a.add(i * 50L); // 20 CPS
        assertTrue(a.cps(29 * 50L, 1000) >= 20);
        assertEquals(0, a.cps(100000, 1000));
    }

    @Test
    void memoryIsBounded() {
        ClickAnalyzer a = new ClickAnalyzer(10);
        for (int i = 0; i < 1000; i++) a.add(i);
        assertTrue(a.intervalCount() <= 11);
    }

    @Test
    void rateWindowPrunesAndCaps() {
        RateWindow w = new RateWindow(1000, 5);
        for (int i = 0; i < 20; i++) w.hit(i);
        assertEquals(5, w.count(20));
        assertEquals(0, w.count(5000));
    }
}
