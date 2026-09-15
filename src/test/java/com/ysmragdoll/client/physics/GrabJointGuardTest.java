package com.ysmragdoll.client.physics;

import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.dynamics.constraintsolver.TypedConstraint;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class GrabJointGuardTest {
    private static Transform pose(float x, float y, float z) {
        Transform t = new Transform(); t.setIdentity(); t.origin.set(x, y, z); return t;
    }

    private static DiscreteDynamicsWorld world() {
        var config = new DefaultCollisionConfiguration();
        var world = new DiscreteDynamicsWorld(new CollisionDispatcher(config), new DbvtBroadphase(),
                new SequentialImpulseConstraintSolver(), config);
        world.setGravity(new Vector3f(0, -9.81F, 0));
        world.getSolverInfo().numIterations = 20;
        return world;
    }

    private static RigidBody body(DiscreteDynamicsWorld world, float mass, Transform at, Vector3f half) {
        BoxShape shape = new BoxShape(half);
        Vector3f inertia = new Vector3f();
        if (mass > 0) shape.calculateLocalInertia(mass, inertia);
        RigidBody body = new RigidBody(new RigidBodyConstructionInfo(mass,
                new DefaultMotionState(at), shape, inertia));
        body.setCcdMotionThreshold(0.025F);
        body.setCcdSweptSphereRadius(0.08F);
        world.addRigidBody(body, (short) (mass > 0 ? 2 : 1), (short) (mass > 0 ? 1 : -1));
        return body;
    }

    @Test
    public void rapidHandPullsAndBlockedFeetKeepJointAnchorsTogether() {
        for (boolean wall : new boolean[]{false, true}) {
            var world = world();
            List<RigidBody> bodies = new ArrayList<>();
            List<TypedConstraint> joints = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                RigidBody part = body(world, i == 3 ? 5 : 0.35F, pose(0, 5 - i * 0.5F, 0),
                        new Vector3f(0.1F, 0.25F, 0.1F));
                bodies.add(part);
                if (i == 0) continue;
                var joint = new Generic6DofConstraint(bodies.get(i - 1), part,
                        pose(0, -0.25F, 0), pose(0, 0.25F, 0), true);
                joint.setLinearLowerLimit(new Vector3f());
                joint.setLinearUpperLimit(new Vector3f());
                joint.setAngularLowerLimit(new Vector3f(-1, -1, -1));
                joint.setAngularUpperLimit(new Vector3f(1, 1, 1));
                world.addConstraint(joint, true);
                joints.add(joint);
            }
            if (wall) body(world, 0, pose(2, 2, 0), new Vector3f(0.5F, 1, 3));
            var guard = new GrabJointGuard(world, joints);
            var grab = new PhysicsGrab(bodies.get(0), new Vector3f(0.08F, 5.2F, 0),
                    world::addConstraint, world::removeConstraint, () -> true, Vector3f::new, guard);
            float maxGap = 0;
            for (int frame = 0; frame < 900; frame++) {
                grab.moveTo(wall ? new Vector3f((frame / 180 % 2 == 0) ? 8 : -8, 5.2F, 0)
                        : new Vector3f((frame / 45 % 2 == 0) ? 12 : -12,
                        (frame / 60 % 2 == 0) ? 9 : 2, (frame / 30 % 2 == 0) ? -8 : 8));
                grab.beforeStep();
                world.stepSimulation(1F / 120, 0, 1F / 120);
                grab.afterStep();
                maxGap = Math.max(maxGap, guard.maximumError());
                for (RigidBody part : bodies) assertTrue(Float.isFinite(part.getLinearVelocity(new Vector3f()).length()));
            }
            System.out.println("Joint guard wall=" + wall + " maximum gap=" + maxGap);
            assertTrue("No visible separation: " + maxGap, maxGap < 0.02F);
            if (!wall) {
                float trackingError = 0;
                for (int frame = 0; frame < 720; frame++) {
                    float angle = frame * 2F / 120;
                    Vector3f target = new Vector3f(4 * (float) Math.cos(angle), 8, 4 * (float) Math.sin(angle));
                    grab.moveTo(target);
                    grab.beforeStep();
                    world.stepSimulation(1F / 120, 0, 1F / 120);
                    grab.afterStep();
                    Vector3f error = grab.anchor();
                    error.sub(target);
                    if (frame > 120) trackingError = Math.max(trackingError, error.length());
                    assertTrue(guard.maximumError() < 0.02F);
                }
                System.out.println("Tracking at 8 blocks/second: " + trackingError);
                assertTrue("Must track a moving crosshair instead of trailing meters behind: " + trackingError,
                        trackingError < 0.25F);
            }
            Vector3f restingTarget = new Vector3f(-4, 7, 0);
            grab.moveTo(restingTarget);
            for (int i = 0; i < 720; i++) {
                grab.beforeStep();
                world.stepSimulation(1F / 120, 0, 1F / 120);
                grab.afterStep();
            }
            Vector3f heldError = grab.anchor();
            heldError.sub(restingTarget);
            assertTrue("Must still lift and hold the assembly: " + heldError, heldError.length() < 0.15F);
            float height = bodies.get(0).getWorldTransform(new Transform()).origin.y;
            grab.close();
            assertEquals(joints.size(), world.getNumConstraints());
            for (int i = 0; i < 120; i++) world.stepSimulation(1F / 120, 0, 1F / 120);
            assertTrue(bodies.get(0).getWorldTransform(new Transform()).origin.y < height);
            assertEquals(20, world.getSolverInfo().numIterations);
        }
    }

    @Test
    public void wholeAssemblyStopsWhenOnlyLowerLimbHitsWall() {
        var world = world();
        RigidBody upper = body(world, 1, pose(0, 3, 0), new Vector3f(0.1F, 0.1F, 0.1F));
        RigidBody lower = body(world, 1, pose(0, 1, 0), new Vector3f(0.1F, 0.1F, 0.1F));
        body(world, 0, pose(1, 1, 0), new Vector3f(0.2F, 0.3F, 1));
        var joint = new Generic6DofConstraint(upper, lower, pose(0, -1, 0), pose(0, 1, 0), true);
        var guard = new GrabJointGuard(world, List.of(joint));
        for (int i = 0; i < 10; i++) guard.follow(upper, new Vector3f(2, 0, 0));
        float lowerX = lower.getWorldTransform(new Transform()).origin.x;
        assertTrue(lowerX > 0.5F && lowerX <= 0.701F);
        assertEquals(lowerX, upper.getWorldTransform(new Transform()).origin.x, 0.0001F);
        assertEquals(0, guard.maximumError(), 0.0001F);
        assertEquals(0, upper.getLinearVelocity(new Vector3f()).length(), 0);
        var unloaded = new GrabJointGuard(world, List.of(joint), (bodies, movement) -> false);
        unloaded.follow(upper, new Vector3f(0, 5, 0));
        assertEquals(3, upper.getWorldTransform(new Transform()).origin.y, 0);
    }

    @Test
    public void correctionStopsAtWallWhenJoiningAnchorsWouldCrossIt() {
        var world = world();
        RigidBody a = body(world, 1, pose(0, 3, 0), new Vector3f(0.1F, 0.1F, 0.1F));
        RigidBody b = body(world, 1, pose(2, 3, 0), new Vector3f(0.1F, 0.1F, 0.1F));
        body(world, 0, pose(1, 3, 0), new Vector3f(0.2F, 1, 1));
        var joint = new Generic6DofConstraint(a, b, pose(0, 0, 0), pose(0, 0, 0), true);
        var guard = new GrabJointGuard(world, List.of(joint));
        guard.correct();
        assertTrue(a.getWorldTransform(new Transform()).origin.x <= 0.701F);
        assertTrue(b.getWorldTransform(new Transform()).origin.x >= 1.299F);
        assertTrue("An obstructed correction must leave an error instead of crossing the wall",
                guard.maximumError() > 0.59F);
    }

    @Test
    public void positionCorrectionDoesNotRotateBodiesAndRemovesSeparatingVelocity() {
        var world = world();
        RigidBody a = body(world, 1, pose(0, 3, 0), new Vector3f(0.1F, 0.1F, 0.1F));
        RigidBody b = body(world, 1, pose(0.2F, 3, 0), new Vector3f(0.1F, 0.1F, 0.1F));
        var joint = new Generic6DofConstraint(a, b, pose(0, 0, 0), pose(0, 0, 0), true);
        a.setLinearVelocity(new Vector3f(-1, 0, 0));
        b.setLinearVelocity(new Vector3f(1, 0, 0));
        var guard = new GrabJointGuard(world, List.of(joint));
        guard.correct();
        assertTrue(guard.maximumError() < 0.0011F);
        assertEquals(1, a.getWorldTransform(new Transform()).basis.m00, 0);
        assertEquals(1, b.getWorldTransform(new Transform()).basis.m11, 0);
        assertEquals(0, b.getLinearVelocity(new Vector3f()).x - a.getLinearVelocity(new Vector3f()).x, 0.001F);
    }
}
