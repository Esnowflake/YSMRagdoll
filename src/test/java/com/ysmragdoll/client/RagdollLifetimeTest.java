package com.ysmragdoll.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class RagdollLifetimeTest {
    @Test
    public void deadlineAndDisplayUseTheSameRemainingTime() {
        assertEquals(20000, RagdollLifetime.remainingMillis(1000, 1000, 20, false));
        assertEquals(1, RagdollLifetime.remainingMillis(1000, 20999, 20, false));
        assertEquals(0, RagdollLifetime.remainingMillis(1000, 21000, 20, false));
        assertEquals(0, RagdollLifetime.remainingMillis(1000, 50000, 20, false));
        assertEquals(0, RagdollLifetime.remainingMillis(1000, 1000, 0, false));
    }

    @Test
    public void manualAndPermanentModesNeverReportTimedExpiry() {
        assertEquals(-1, RagdollLifetime.remainingMillis(0, 90000000, 0, true));
        assertEquals(-1, RagdollLifetime.remainingMillis(0, 90000000, 10001, false));
        assertEquals(0, RagdollLifetime.remainingMillis(0, 90000000, 10000, false));
        // Disabling manual removal resumes the original deadline, matching existing behavior.
        assertEquals(0, RagdollLifetime.remainingMillis(0, 90000000, 20, false));
    }

    @Test
    public void voidBoundaryIsStrictAndIndependentOfLifetime() {
        assertFalse(RagdollLifetime.belowVoid(-64));
        assertFalse(RagdollLifetime.belowVoid(0));
        assertTrue(RagdollLifetime.belowVoid(-64.0001));
        assertTrue(RagdollLifetime.belowVoid(-1000));
    }
}
