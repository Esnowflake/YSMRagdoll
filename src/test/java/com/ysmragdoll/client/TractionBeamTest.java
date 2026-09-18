package com.ysmragdoll.client;

import net.minecraft.world.phys.Vec3;
import org.junit.Test;
import static org.junit.Assert.*;

public class TractionBeamTest {
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    @Test
    public void attachmentsStayExactAndShortBeamsStayBounded() {
        Vec3 from = Vec3.ZERO;
        Vec3 to = new Vec3(0, 0, 6);
        Vec3 bend = new Vec3(1, 0, 0);
        var curve = TractionBeam.curve(from, to, bend, FORWARD, UP);
        assertEquals(from, curve.point(0));
        assertEquals(to, curve.point(1));
        assertTrue(curve.point(0.5).x > 0);
        var shortCurve = TractionBeam.curve(from, new Vec3(0, 0, 0.4),
                new Vec3(100, 0, 0), FORWARD, UP);
        assertTrue(shortCurve.point(0.5).x <= 0.061);
        assertEquals(from, TractionBeam.curve(from, from, bend, FORWARD, UP).point(0.5));
    }

    @Test
    public void projectedArcNeverOvershootsOrTurnsBackAtTarget() {
        for (boolean rotated : new boolean[]{false, true}) {
            Vec3 forward = rotate(FORWARD, rotated);
            Vec3 right = forward.cross(UP);
            Vec3 from = rotate(new Vec3(0.4, -0.27, 0.3), rotated);
            Vec3 to = rotate(new Vec3(-0.1, 0.2, 6), rotated);
            for (Vec3 bend : new Vec3[]{new Vec3(0.5, 1, 0), new Vec3(-1, -1, 0), new Vec3(0, 4, 0)}) {
                var curve = TractionBeam.curve(from, to, rotate(bend, rotated), forward, UP);
                double oldX = from.dot(right) / from.dot(forward);
                double oldY = from.dot(UP) / from.dot(forward);
                for (int i = 1; i <= 100; i++) {
                    Vec3 point = curve.point(i / 100.0);
                    double x = point.dot(right) / point.dot(forward);
                    double y = point.dot(UP) / point.dot(forward);
                    assertTrue("Must progress towards target without a sideways hook", x >= oldX - 1.0E-9);
                    assertTrue("Must not rise above target and return downwards", y >= oldY - 1.0E-9);
                    oldX = x;
                    oldY = y;
                }
                assertEquals(to.dot(right) / to.dot(forward), oldX, 1.0E-9);
                assertEquals(to.dot(UP) / to.dot(forward), oldY, 1.0E-9);
            }
        }
    }

    @Test
    public void stoppingRetainsArcThenGraduallyStraightensWithoutRebound() {
        var beam = new TractionBeam();
        beam.update(Vec3.ZERO, Vec3.ZERO, 0);
        Vec3 bend = Vec3.ZERO;
        for (int i = 1; i <= 60; i++) {
            Vec3 target = new Vec3(i * 0.1, 0, 0);
            bend = beam.update(target, target.subtract(0.6, 0, 0), 1.0 / 60);
        }
        double movingBend = bend.length();
        Vec3 stopped = new Vec3(6, 0, 0);
        double previous = movingBend;
        for (int i = 1; i <= 180; i++) {
            bend = beam.update(stopped, stopped, 1.0 / 60);
            assertTrue(bend.x >= 0);
            assertTrue(bend.length() <= previous + 1.0E-8);
            if (i == 6) assertTrue("Still visibly curved 100 ms after stopping", bend.length() > movingBend * 0.6);
            previous = bend.length();
        }
        assertTrue(bend.length() < 0.001);
        beam.reset();
        assertEquals(Vec3.ZERO, beam.update(stopped, stopped, 0));
    }

    @Test
    public void curveBowsTowardsPullOnEitherSideWhileKeepingAnchorAttached() {
        Vec3 from = Vec3.ZERO;
        Vec3 anchor = new Vec3(0, 0, 6);
        for (Vec3 movement : new Vec3[]{new Vec3(-1, 0, 0), new Vec3(1, 0, 0),
                new Vec3(0, -1, 0), new Vec3(0, 1, 0)}) {
            var beam = new TractionBeam();
            beam.update(anchor, anchor, 0);
            Vec3 bend = beam.update(anchor.add(movement), anchor, 1.0 / 60);
            var curve = TractionBeam.curve(from, anchor, bend, FORWARD, UP);
            assertTrue(curve.point(0.5).dot(movement) > 0);
            assertEquals(anchor, curve.point(1));
        }
        var beam = new TractionBeam();
        beam.update(new Vec3(-12, 0, 0), new Vec3(-12, 0, 0), 0);
        Vec3 fullTurn = new Vec3(12, 0, 0);
        assertTrue(beam.update(fullTurn, new Vec3(10, 0, 0), 1.0 / 60).x > 0);
    }

    @Test
    public void rigidBeamIsStraightAndStationaryAimStillBendsWhileBodyLags() {
        var beam = new TractionBeam();
        Vec3 target = new Vec3(1, 0, 6);
        Vec3 anchor = new Vec3(0, 0, 6);
        Vec3 bend = Vec3.ZERO;
        for (int i = 0; i < 120; i++) bend = beam.update(target, anchor, 1.0 / 60, 70);
        assertTrue(bend.x > 0.7);
        assertEquals(Vec3.ZERO, beam.update(target, anchor, 1.0 / 60, 100));
        assertEquals(Vec3.ZERO, beam.update(target, anchor, 1.0 / 60, 0));
        beam.reset();
        assertEquals(Vec3.ZERO, beam.update(target, target, 1.0 / 60, 70));
    }

    @Test
    public void similarTrailAcrossFrameRatesAndBoundedOnLargeTurns() {
        assertTrue(trail(30).distanceTo(trail(144)) < 0.1);
        var beam = new TractionBeam();
        beam.update(Vec3.ZERO, Vec3.ZERO, 0);
        for (int i = 0; i < 100; i++) {
            Vec3 target = new Vec3(i % 2 == 0 ? 6 : -6, 0, 0);
            assertTrue(beam.update(target, Vec3.ZERO, 1.0 / 30).length() <= 1.200001);
        }
    }

    private static Vec3 rotate(Vec3 value, boolean rotate) {
        return rotate ? new Vec3(value.z, value.y, -value.x) : value;
    }

    private static Vec3 trail(int fps) {
        var beam = new TractionBeam();
        beam.update(Vec3.ZERO, Vec3.ZERO, 0);
        Vec3 result = Vec3.ZERO;
        for (int i = 1; i <= fps; i++) {
            Vec3 target = new Vec3(6.0 * i / fps, 0, 0);
            result = beam.update(target, target.subtract(0.6, 0, 0), 1.0 / fps);
        }
        return result;
    }
}
