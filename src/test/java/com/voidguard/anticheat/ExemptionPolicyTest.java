package com.voidguard.anticheat;

import static org.junit.jupiter.api.Assertions.*;

import com.voidguard.anticheat.util.ExemptionPolicy;
import com.voidguard.anticheat.util.ExemptionPolicy.Grace;
import com.voidguard.anticheat.util.ExemptionPolicy.MovementState;
import org.junit.jupiter.api.Test;

class ExemptionPolicyTest {
    private static final Grace GRACE = new Grace(1500, 2000, 2000, 1500);
    private static final long LONG_AGO = 60_000;

    private static MovementState normal() {
        return new MovementState(false, false, false, false, false, false, false, false,
                LONG_AGO, LONG_AGO, LONG_AGO, LONG_AGO, false);
    }

    @Test
    void normalSurvivalPlayerIsChecked() {
        assertFalse(ExemptionPolicy.movementExempt(normal(), GRACE));
    }

    @Test
    void elytraIsExemptAndHasGraceAfterLanding() {
        MovementState gliding = new MovementState(false, false, false, true, false, false, false, false,
                LONG_AGO, LONG_AGO, LONG_AGO, LONG_AGO, false);
        assertTrue(ExemptionPolicy.movementExempt(gliding, GRACE));
        MovementState justLanded = new MovementState(false, false, false, false, false, false, false, false,
                LONG_AGO, LONG_AGO, LONG_AGO, 300, false);
        assertTrue(ExemptionPolicy.movementExempt(justLanded, GRACE));
    }

    @Test
    void permittedFlightIsExempt() {
        MovementState flightAllowed = new MovementState(false, true, true, false, false, false, false, false,
                LONG_AGO, LONG_AGO, LONG_AGO, LONG_AGO, false);
        assertTrue(ExemptionPolicy.movementExempt(flightAllowed, GRACE));
    }

    @Test
    void knockbackVelocityGraceApplies() {
        MovementState hit = new MovementState(false, false, false, false, false, false, false, false,
                LONG_AGO, 500, LONG_AGO, LONG_AGO, false);
        assertTrue(ExemptionPolicy.movementExempt(hit, GRACE));
        MovementState later = new MovementState(false, false, false, false, false, false, false, false,
                LONG_AGO, 2500, LONG_AGO, LONG_AGO, false);
        assertFalse(ExemptionPolicy.movementExempt(later, GRACE));
    }

    @Test
    void teleportGamemodeCreativeVehicleAndEffectAreExempt() {
        assertTrue(ExemptionPolicy.movementExempt(new MovementState(false, false, false, false, false, false,
                false, false, 100, LONG_AGO, LONG_AGO, LONG_AGO, false), GRACE));
        assertTrue(ExemptionPolicy.movementExempt(new MovementState(false, false, false, false, false, false,
                false, false, LONG_AGO, LONG_AGO, 100, LONG_AGO, false), GRACE));
        assertTrue(ExemptionPolicy.movementExempt(new MovementState(true, false, false, false, false, false,
                false, false, LONG_AGO, LONG_AGO, LONG_AGO, LONG_AGO, false), GRACE));
        assertTrue(ExemptionPolicy.movementExempt(new MovementState(false, false, false, false, false, true,
                false, false, LONG_AGO, LONG_AGO, LONG_AGO, LONG_AGO, false), GRACE));
        assertTrue(ExemptionPolicy.movementExempt(new MovementState(false, false, false, false, false, false,
                false, false, LONG_AGO, LONG_AGO, LONG_AGO, LONG_AGO, true), GRACE));
    }

    @Test
    void lagAndHighPingSkipChecks() {
        assertFalse(ExemptionPolicy.shouldSkipForLoad(20.0, 17.5, 50, 450, false));
        assertTrue(ExemptionPolicy.shouldSkipForLoad(15.0, 17.5, 50, 450, false)); // TPS düşüşü
        assertTrue(ExemptionPolicy.shouldSkipForLoad(20.0, 17.5, 900, 450, false)); // yüksek ping
        assertTrue(ExemptionPolicy.shouldSkipForLoad(20.0, 17.5, 50, 450, true));   // lag sıçraması
    }
}
