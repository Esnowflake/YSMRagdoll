package com.ysmragdoll.client;

import net.minecraft.world.phys.Vec3;
import org.junit.Test;
import static org.junit.Assert.*;

public class TractionBeamTest {
    @Test
    public void attachmentsStayExactAndBendingIsConcentratedNearRagdoll() {
        Vec3 from = new Vec3(1, 2, 3);
        Vec3 to = new Vec3(1, 2, 9);
        Vec3 bend = new Vec3(1, 0, 0);
        assertEquals(from, TractionBeam.point(from, to, bend, 0));
        assertEquals(to, TractionBeam.point(from, to, bend, 1));
        double nearHand = TractionBeam.point(from, to, bend, 0.1).x - from.x;
        double nearRagdoll = TractionBeam.point(from, to, bend, 2.0 / 3).x - from.x;
        assertTrue(nearRagdoll > nearHand * 10);
        Vec3 shortEnd = from.add(0, 0, 0.4);
        assertTrue(TractionBeam.point(from, shortEnd, new Vec3(100, 0, 0), 2.0 / 3).x - from.x <= 0.100001);
        assertEquals(from, TractionBeam.point(from, from, bend, 0.5));
    }

    @Test
    public void movementLeavesSoftTrailAndStoppingOrResettingClearsIt() {
        var beam = new TractionBeam();
        beam.update(Vec3.ZERO, Vec3.ZERO, 0);
        Vec3 target = new Vec3(1, 0, 0);
        assertTrue(beam.update(target, target, 1.0 / 60).x < 0);
        beam.reset();
        beam.update(new Vec3(-12, 0, 0), new Vec3(-12, 0, 0), 0);
        Vec3 fullTurn = new Vec3(12, 0, 0);
        assertTrue(beam.update(fullTurn, fullTurn, 1.0 / 60).x < 0);
        beam.reset();
        beam.update(Vec3.ZERO, Vec3.ZERO, 0);
        Vec3 bend = Vec3.ZERO;
        for (int i = 0; i < 120; i++) bend = beam.update(target, target, 1.0 / 60);
        assertTrue(bend.length() < 0.0001);
        beam.update(target.add(2, 0, 0), target, 1.0 / 60);
        beam.reset();
        assertEquals(Vec3.ZERO, beam.update(target, target, 0));
    }

    @Test
    public void similarTrailAcrossFrameRatesAndBoundedOnLargeTurns() {
        Vec3 slow = trail(30);
        Vec3 fast = trail(144);
        assertTrue(slow.distanceTo(fast) < 0.1);
        var beam = new TractionBeam();
        beam.update(Vec3.ZERO, Vec3.ZERO, 0);
        for (int i = 0; i < 100; i++) {
            Vec3 target = new Vec3(i % 2 == 0 ? 6 : -6, 0, 0);
            assertTrue(beam.update(target, Vec3.ZERO, 1.0 / 30).length() <= 1.200001);
        }
    }

    private static Vec3 trail(int fps) {
        var beam = new TractionBeam();
        beam.update(Vec3.ZERO, Vec3.ZERO, 0);
        Vec3 result = Vec3.ZERO;
        for (int i = 1; i <= fps; i++) {
            Vec3 target = new Vec3(6.0 * i / fps, 0, 0);
            result = beam.update(target, target, 1.0 / fps);
        }
        return result;
    }
}
