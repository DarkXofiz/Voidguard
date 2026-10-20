package com.voidguard.anticheat;

import static org.junit.jupiter.api.Assertions.*;

import com.voidguard.anticheat.math.MoveMath;
import org.junit.jupiter.api.Test;

class MoveMathTest {

    private static final double SPRINT_JUMP_REAL = 0.3556; // vanilla sprint-zıplama tepe yatay hızı (yaklaşık)

    @Test
    void normalSprintJumpIsNotAboveLimit() {
        double limit = MoveMath.maxHorizontalSpeed(0.36, -1, 1.0, false, 1.6, 0.05);
        assertTrue(SPRINT_JUMP_REAL < limit);
    }

    @Test
    void speedHackIsAboveLimit() {
        double limit = MoveMath.maxHorizontalSpeed(0.36, -1, 1.0, false, 1.6, 0.05);
        assertTrue(0.8 > limit);
    }

    @Test
    void speedEffectAndIceRaiseTheLimit() {
        double normal = MoveMath.maxHorizontalSpeed(0.36, -1, 1.0, false, 1.6, 0.05);
        double speed2 = MoveMath.maxHorizontalSpeed(0.36, 1, 1.0, false, 1.6, 0.05);
        double ice = MoveMath.maxHorizontalSpeed(0.36, -1, 1.0, true, 1.6, 0.05);
        assertTrue(speed2 > normal);
        assertTrue(ice > normal);
    }

    @Test
    void lagScalesToleranceUpToCap() {
        assertEquals(1.0, MoveMath.tickScale(50, 5), 1e-9);
        assertEquals(4.0, MoveMath.tickScale(200, 5), 1e-9);
        assertEquals(5.0, MoveMath.tickScale(5000, 5), 1e-9);
        assertEquals(1.0, MoveMath.tickScale(10, 5), 1e-9); // paket birikmesi toleransı azaltmaz
    }

    @Test
    void highPingWidensReachAllowance() {
        double low = MoveMath.allowedReach(3.1, 20, 0.1, 1.0);
        double high = MoveMath.allowedReach(3.1, 300, 0.1, 1.0);
        assertTrue(high > low);
        assertTrue(3.0 <= low);          // normal vuruş düşük pingde geçerli
        assertTrue(3.5 <= high);         // aynı vuruş yüksek pingde de geçerli
        assertTrue(4.4 > high);          // reach hilesi yüksek pingde bile yakalanır
        assertEquals(4.1, MoveMath.allowedReach(3.1, 100000, 0.1, 1.0), 1e-9); // sınır
    }

    @Test
    void jumpHeightMatchesVanillaAndBoostRaisesIt() {
        assertEquals(1.2522, MoveMath.maxJumpHeight(-1), 0.01);
        assertTrue(MoveMath.maxJumpHeight(0) > MoveMath.maxJumpHeight(-1) + 0.4);
    }

    @Test
    void gravityPrediction() {
        assertEquals(-0.0784, MoveMath.predictedFallVelocity(0.0), 1e-9);
        double v = 0;
        for (int i = 0; i < 1000; i++) v = MoveMath.predictedFallVelocity(v);
        assertEquals(-3.92, v, 0.05); // terminal hız
    }

    @Test
    void knockbackIgnoredDetection() {
        assertTrue(MoveMath.knockbackIgnored(0.4, 0.02, 0.2, 0.2));   // geri tepme yok sayıldı
        assertFalse(MoveMath.knockbackIgnored(0.4, 0.6, 0.2, 0.2));   // normal geri tepme
        assertFalse(MoveMath.knockbackIgnored(0.1, 0.0, 0.2, 0.2));   // çok zayıf hız değerlendirilmez
        assertFalse(MoveMath.knockbackIgnored(0.4, 0.12, 0.2, 0.2));  // kısmen engellenmiş (duvar vb.) tolerans
    }

    @Test
    void knockbackDelayIncludesPing() {
        assertEquals(5, MoveMath.knockbackDelayTicks(20, 4, 10));
        assertEquals(8, MoveMath.knockbackDelayTicks(200, 4, 10));
        assertEquals(14, MoveMath.knockbackDelayTicks(10000, 4, 10));
    }

    @Test
    void breakTimeLowerBound() {
        assertEquals(187.5, MoveMath.minBreakTicks(50, 8, 0, -1), 1e-9);
        assertEquals(7.5, MoveMath.minBreakTicks(1.5, 6, 0, -1), 1e-9);
        assertTrue(MoveMath.minBreakTicks(1.5, 6, 5, 1) < 7.5);
        assertEquals(0.0, MoveMath.minBreakTicks(0, 1, 0, -1), 1e-9);
    }

    @Test
    void stdDev() {
        assertEquals(0.0, MoveMath.stdDev(new double[]{5, 5, 5, 5}, 4), 1e-9);
        assertTrue(MoveMath.stdDev(new double[]{1, 9, 1, 9}, 4) > 3.9);
        assertEquals(Double.MAX_VALUE, MoveMath.stdDev(new double[]{1}, 1));
    }
}
