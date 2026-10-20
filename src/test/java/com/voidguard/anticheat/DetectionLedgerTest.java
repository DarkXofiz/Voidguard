package com.voidguard.anticheat;

import static org.junit.jupiter.api.Assertions.*;

import com.voidguard.anticheat.punishments.DetectionLedger;
import com.voidguard.anticheat.punishments.DetectionLedger.Outcome;
import com.voidguard.anticheat.punishments.DetectionLedger.Result;
import org.junit.jupiter.api.Test;

class DetectionLedgerTest {
    private static final long MIN = 1500;
    private static final long WIN = 600_000;

    private static Outcome rec(DetectionLedger l, String check, long t) {
        return l.record(check, t, MIN, WIN, 3);
    }

    @Test
    void thirdDetectionTriggersThresholdImmediately() {
        DetectionLedger l = new DetectionLedger();
        assertEquals(1, rec(l, "Fly", 0).count());
        assertEquals(Result.COUNTED, rec(l, "Fly", 2000).result());
        Outcome o = rec(l, "Fly", 4000);
        assertEquals(Result.THRESHOLD, o.result());
        assertEquals(3, o.count());
    }

    @Test
    void sameEventRepeatedIsNotCounted() {
        DetectionLedger l = new DetectionLedger();
        rec(l, "Speed", 0);
        for (int t = 1; t < 100; t += 10) assertEquals(Result.DUPLICATE, rec(l, "Speed", t).result());
        assertEquals(1, l.count("Speed", 100, WIN));
    }

    @Test
    void differentChecksAreCountedSeparately() {
        DetectionLedger l = new DetectionLedger();
        rec(l, "Fly", 0);
        rec(l, "Speed", 2000);
        assertEquals(Result.COUNTED, rec(l, "Reach", 4000).result());
        assertEquals(1, l.count("Fly", 4000, WIN));
    }

    @Test
    void staleDetectionsExpire() {
        DetectionLedger l = new DetectionLedger();
        rec(l, "Fly", 0);
        rec(l, "Fly", 2000);
        Outcome o = rec(l, "Fly", 2000 + WIN + 1);
        assertEquals(Result.COUNTED, o.result());
        assertEquals(1, o.count());
    }

    @Test
    void counterResetsAfterThreshold() {
        DetectionLedger l = new DetectionLedger();
        rec(l, "Fly", 0);
        rec(l, "Fly", 2000);
        assertEquals(Result.THRESHOLD, rec(l, "Fly", 4000).result());
        assertEquals(0, l.count("Fly", 4000, WIN));
    }

    @Test
    void singleSuspiciousFlagNeverPunishes() {
        DetectionLedger l = new DetectionLedger();
        assertEquals(Result.COUNTED, rec(l, "KillAura", 0).result());
        assertTrue(l.idle(WIN + 1, WIN));
    }
}
