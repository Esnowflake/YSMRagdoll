package com.ysmragdoll.client.physics;

import com.bulletphysics.collision.narrowphase.DiscreteCollisionDetectorInterface;
import com.bulletphysics.collision.narrowphase.GjkEpaPenetrationDepthSolver;
import com.bulletphysics.collision.narrowphase.GjkPairDetector;
import com.bulletphysics.collision.narrowphase.PointCollector;
import com.bulletphysics.collision.narrowphase.VoronoiSimplexSolver;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.ConvexShape;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import org.junit.jupiter.api.Test;

import javax.vecmath.Vector3f;
import java.util.List;

import static org.junit.Assert.*;

public class PlayerCollisionResponseTest {
    private final BoxShape player = box(0.3F, 0.9F, 0.3F);

    @Test
    public void fastPlayerCannotPassThroughThinLimb() {
        RigidBody limb = body(0, 0, 0, 0.04F, 0.25F, 0.1F);
        PlayerCollisionResponse response = response(limb);
        Transform from = transform(-1, 0, 0);
        Transform to = transform(1, 0, 0);
        assertTrue(response.resolve(player, from, to, new Vector3f(40, 0, 0), true));
        assertTrue(position(limb).x >= 1.34F);
        assertTrue(limb.getLinearVelocity(new Vector3f()).x >= 39.99F);
        assertSeparated(limb, to);
    }

    @Test
    public void stationaryPlayerExpelsOverlappingBodyAndCancelsInwardSpeed() {
        RigidBody limb = body(0.35F, 0, 0, 0.2F, 0.2F, 0.2F);
        limb.setLinearVelocity(new Vector3f(-4, 0, 0));
        Transform at = transform(0, 0, 0);
        assertTrue(response(limb).resolve(player, at, at, new Vector3f(), false));
        assertTrue(limb.getLinearVelocity(new Vector3f()).x >= -0.001F);
        assertSeparated(limb, at);
    }

    @Test
    public void retreatingPlayerDoesNotDragBody() {
        RigidBody limb = body(0.6F, 0, 0, 0.2F, 0.2F, 0.2F);
        assertFalse(response(limb).resolve(player, transform(0, 0, 0), transform(-1, 0, 0),
                new Vector3f(-20, 0, 0), true));
        assertEquals(0.6F, position(limb).x, 0.00001F);
    }

    @Test
    public void wholeGroupKeepsRelativeJointPositions() {
        RigidBody first = body(0.35F, 0, 0, 0.2F, 0.2F, 0.2F);
        RigidBody second = body(0.9F, 0.4F, 0, 0.2F, 0.2F, 0.2F);
        Vector3f relative = position(second);
        relative.sub(position(first));
        Transform at = transform(0, 0, 0);
        assertTrue(response(first, second).resolve(player, at, at, new Vector3f(), false));
        Vector3f after = position(second);
        after.sub(position(first));
        assertTrue(relative.epsilonEquals(after, 0.0001F));
        assertSeparated(first, at);
        assertSeparated(second, at);
    }

    @Test
    public void playerInsideOpposingLimbsDoesNotLeavePersistentOverlap() {
        RigidBody left = body(-0.35F, 0, 0, 0.2F, 0.2F, 0.2F);
        RigidBody right = body(0.35F, 0, 0, 0.2F, 0.2F, 0.2F);
        Transform at = transform(0, 0, 0);
        assertTrue(response(left, right).resolve(player, at, at, new Vector3f(), false));
        assertSeparated(left, at);
        assertSeparated(right, at);
        assertEquals(0.7F, position(right).x - position(left).x, 0.0001F);
    }

    @Test
    public void rotatedLimbUsesItsActualConvexShape() {
        RigidBody limb = body(0.5F, 0, 0, 0.1F, 0.3F, 0.45F);
        Transform rotated = limb.getWorldTransform(new Transform());
        rotated.basis.rotY((float) Math.PI / 4);
        limb.setWorldTransform(rotated);
        Transform at = transform(0, 0, 0);
        assertTrue(response(limb).resolve(player, at, at, new Vector3f(), false));
        assertSeparated(limb, at);
    }

    @Test
    public void verticalContactAndTranslatedCoordinatesRemainSeparated() {
        RigidBody limb = body(10, 4.8F, -12, 0.2F, 0.2F, 0.2F);
        Transform at = transform(10, 4, -12);
        assertTrue(response(limb).resolve(player, at, at, new Vector3f(0, 2, 0), false));
        assertSeparated(limb, at);
        assertTrue(limb.getLinearVelocity(new Vector3f()).y >= 1.99F);
    }

    @Test
    public void movingBodyCannotTunnelThroughStationaryPlayerBetweenSubsteps() {
        RigidBody limb = body(-1, 0, 0, 0.1F, 0.1F, 0.1F);
        PlayerCollisionResponse response = response(limb);
        response.captureBeforeStep();
        limb.setWorldTransform(transform(1, 0, 0));
        limb.setLinearVelocity(new Vector3f(240, 0, 0));
        Transform at = transform(0, 0, 0);
        assertTrue(response.resolve(player, at, at, new Vector3f(), false));
        assertTrue(position(limb).x <= -0.4F);
        assertEquals(0.0F, limb.getLinearVelocity(new Vector3f()).x, 0.0001F);
        assertSeparated(limb, at);
    }

    @Test
    public void identicalCentersHaveAStableEscape() {
        RigidBody limb = body(0, 0, 0, 0.2F, 0.2F, 0.2F);
        Transform at = transform(0, 0, 0);
        assertTrue(response(limb).resolve(player, at, at, new Vector3f(), false));
        assertSeparated(limb, at);
    }

    @Test
    public void repeatedPushMatchesPlayerSpeedWithoutArbitraryVelocityCap() {
        RigidBody limb = body(0.55F, 0, 0, 0.2F, 0.2F, 0.2F);
        PlayerCollisionResponse response = response(limb);
        for (int tick = 0; tick < 20; tick++) {
            Transform from = transform(tick * 0.5F, 0, 0);
            Transform to = transform((tick + 1) * 0.5F, 0, 0);
            response.resolve(player, from, to, new Vector3f(10, 0, 0), true);
            assertSeparated(limb, to);
        }
        assertTrue(limb.getLinearVelocity(new Vector3f()).x >= 9.99F);
    }

    private static PlayerCollisionResponse response(RigidBody... bodies) {
        return new PlayerCollisionResponse(List.of(bodies), ignored -> { });
    }

    private void assertSeparated(RigidBody body, Transform at) {
        GjkPairDetector detector = new GjkPairDetector();
        detector.init((ConvexShape) body.getCollisionShape(), player,
                new VoronoiSimplexSolver(), new GjkEpaPenetrationDepthSolver());
        DiscreteCollisionDetectorInterface.ClosestPointInput input =
                new DiscreteCollisionDetectorInterface.ClosestPointInput();
        input.init();
        body.getWorldTransform(input.transformA);
        input.transformB.set(at);
        PointCollector result = new PointCollector();
        detector.getClosestPoints(input, result, null);
        assertTrue("penetration=" + result.distance, !result.hasResult || result.distance >= -0.0001F);
    }

    private static BoxShape box(float x, float y, float z) {
        BoxShape shape = new BoxShape(new Vector3f(x, y, z));
        shape.setMargin(0.0F);
        return shape;
    }

    private static RigidBody body(float x, float y, float z, float hx, float hy, float hz) {
        BoxShape shape = box(hx, hy, hz);
        Vector3f inertia = new Vector3f();
        shape.calculateLocalInertia(1.0F, inertia);
        return new RigidBody(new RigidBodyConstructionInfo(1.0F,
                new DefaultMotionState(transform(x, y, z)), shape, inertia));
    }

    private static Transform transform(float x, float y, float z) {
        Transform transform = new Transform();
        transform.setIdentity();
        transform.origin.set(x, y, z);
        return transform;
    }

    private static Vector3f position(RigidBody body) {
        return new Vector3f(body.getWorldTransform(new Transform()).origin);
    }
}
