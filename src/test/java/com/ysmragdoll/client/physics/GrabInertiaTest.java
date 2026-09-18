package com.ysmragdoll.client.physics;

import javax.vecmath.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;

public class GrabInertiaTest {
    @Test
    public void startingStoppingAndReversingGiveOppositeFeedbackWithoutUnboundedKicks() {
        var inertia = new GrabInertia();
        Vector3f start = inertia.step(new Vector3f(0.05F, 0, 0));
        assertTrue(start.x < 0);
        for (int i = 0; i < 120; i++) inertia.step(new Vector3f(0.05F, 0, 0));
        assertTrue(inertia.releaseVelocity().x > 4);
        assertTrue(inertia.step(new Vector3f()).x > 0);
        assertTrue(inertia.step(new Vector3f(-0.05F, 0, 0)).x > 0);
        for (int i = 0; i < 1000; i++) {
            Vector3f feedback = inertia.step(new Vector3f(i % 2 == 0 ? 100 : -100, 70, -80));
            assertTrue(feedback.length() <= 0.06001F);
            assertTrue(inertia.releaseVelocity().length() <= 6.0001F);
        }
    }

    @Test
    public void blockedOrStationaryMovementLosesStoredThrowVelocity() {
        var inertia = new GrabInertia();
        for (int i = 0; i < 120; i++) inertia.step(new Vector3f(0.1F, 0, 0));
        for (int i = 0; i < 60; i++) inertia.step(new Vector3f());
        assertEquals(0, inertia.releaseVelocity().length(), 0);
    }
}
