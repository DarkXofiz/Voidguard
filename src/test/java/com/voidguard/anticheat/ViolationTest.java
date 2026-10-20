package com.voidguard.anticheat;

import static org.junit.jupiter.api.Assertions.*;

import com.voidguard.anticheat.punishments.ActionDecider;
import com.voidguard.anticheat.punishments.ActionDecider.Level;
import com.voidguard.anticheat.punishments.ActionDecider.Thresholds;
import com.voidguard.anticheat.punishments.ViolationTracker;
import com.voidguard.anticheat.util.TimeUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ViolationTest {
    private static final Thresholds T = new Thresholds(10, 40, 2, 4.0);

    @Test
    void violationsDecayOverTime() {
        ViolationTracker t = new ViolationTracker(5);
        t.add("Fly", 10, 0, 3.0, "a");
        assertEquals(7.0, t.get("Fly", 60_000, 3.0), 1e-9);
        assertEquals(0.0, t.get("Fly", 10 * 60_000, 3.0), 1e-9);
    }

    @Test
    void evidenceIsBounded() {
        ViolationTracker t = new ViolationTracker(3);
        for (int i = 0; i < 10; i++) t.add("Speed", 1, i, 0, "e" + i);
        assertEquals(3, t.evidence("Speed").size());
        assertEquals("e9", t.evidence("Speed").get(2));
    }

    @Test
    void lowSuspicionOnlyLogs() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Fly", 3.0);
        assertEquals(Level.LOG, ActionDecider.decide(m, T).level());
    }

    @Test
    void mediumSuspicionAlerts() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Fly", 15.0);
        assertEquals(Level.ALERT, ActionDecider.decide(m, T).level());
    }

    @Test
    void singleCheckAboveThresholdDoesNotPunishWithoutExtremeVl() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Fly", 60.0);
        ActionDecider.Decision d = ActionDecider.decide(m, T);
        assertEquals(Level.ALERT, d.level());
    }

    @Test
    void multipleIndependentStrongChecksPunishWithHighConfidence() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Fly", 45.0);
        m.put("Speed", 41.0);
        ActionDecider.Decision d = ActionDecider.decide(m, T);
        assertEquals(Level.PUNISH, d.level());
        assertTrue(d.highConfidence());
        assertEquals("Fly", d.topCheck());
    }

    @Test
    void extremeSingleCheckPunishesButWithoutVisualEffectConfidence() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Reach", 170.0);
        ActionDecider.Decision d = ActionDecider.decide(m, T);
        assertEquals(Level.PUNISH, d.level());
        assertFalse(d.highConfidence());
    }

    @Test
    void emptyIsNone() {
        assertEquals(Level.NONE, ActionDecider.decide(new LinkedHashMap<>(), T).level());
    }

    @Test
    void durations() {
        assertEquals(30 * 60_000L, TimeUtil.parseDuration("30m"));
        assertEquals(86_400_000L, TimeUtil.parseDuration("1d"));
        assertEquals(7 * 86_400_000L, TimeUtil.parseDuration("1w"));
        assertEquals(0, TimeUtil.parseDuration("perm"));
        assertEquals(-1, TimeUtil.parseDuration("abc"));
        assertEquals(-1, TimeUtil.parseDuration("-5d"));
        assertEquals("Kalıcı", TimeUtil.format(0));
        assertEquals("1 gün", TimeUtil.format(86_400_000L));
    }
}
