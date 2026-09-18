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

public class TractionStrengthTest {
    private static final float STEP = 1F / 120;

    @Test
    public void zeroDoesNotAttachAndSoftModeOnlyAddsBoundedMomentum() {
        Fixture disabled = new Fixture(0);
        assertFalse(disabled.grab.isActive());
        disabled.grab.moveTo(new Vector3f(100, 100, 100));
        disabled.grab.beforeStep();
        assertEquals(6, disabled.world.getNumConstraints());
        assertEquals(0, disabled.parts.get(0).getLinearVelocity(new Vector3f()).length(), 0);
        for (int strength : new int[]{1, 35, 70, 99}) {
            Fixture f = new Fixture(strength);
            Vector3f before = f.grab.anchor();
            f.grab.moveTo(new Vector3f(100, 100, 100));
            f.grab.beforeStep();
            assertTrue("Soft pulling must not teleport the anchor", before.epsilonEquals(f.grab.anchor(), 0));
            Vector3f momentum = new Vector3f();
            for (RigidBody part : f.parts) momentum.scaleAdd(1 / part.getInvMass(), part.getLinearVelocity(new Vector3f()), momentum);
            assertTrue(momentum.length() <= TractionForce.maximum(strength) * STEP + 0.0001F);
            assertEquals("No hidden unlimited point motor", 6, f.world.getNumConstraints());
        }
    }

    @Test
    public void softPullsStayConnectedAndReachTargetWhileRigidPullHasNoLag() {
        for (int strength : new int[]{35, 70, 99, 100}) {
            Fixture f = new Fixture(strength);
            Vector3f first = new Vector3f(3, 6, 0);
            f.step(first);
            Vector3f error = f.grab.anchor(); error.sub(first);
            if (strength < 100) assertTrue("Finite force cannot move instantly", error.length() > 2);
            else assertTrue("Rigid mode must track immediately", error.length() < 0.005F);
            float maxGap = 0;
            for (int i = 0; i < 900; i++) {
                f.step(new Vector3f(i / 90 % 2 == 0 ? 8 : -8, 7, i / 60 % 2 == 0 ? 3 : -3));
                maxGap = Math.max(maxGap, f.guard.maximumError());
            }
            System.out.println("Strength=" + strength + " max joint gap=" + maxGap);
            assertTrue("No visible joint separation", maxGap < 0.02F);
            Vector3f target = new Vector3f(2, 7, 0);
            for (int i = 0; i < 1200; i++) f.step(target);
            error = f.grab.anchor(); error.sub(target);
            assertTrue("Must eventually catch up: " + strength + " " + error, error.length() < 0.2F);
            f.grab.releaseWithInertia();
            assertEquals(6, f.world.getNumConstraints());
        }
    }

    @Test
    public void softAndRigidPullsRemainBlockedByTerrain() {
        for (int strength : new int[]{70, 100}) {
            Fixture f = new Fixture(strength);
            Transform wallPose = pose(4); wallPose.origin.x = 2;
            var wall = new RigidBody(new RigidBodyConstructionInfo(0, new DefaultMotionState(wallPose),
                    new BoxShape(new Vector3f(0.2F, 20, 20)), new Vector3f()));
            f.world.addRigidBody(wall, (short) 1, (short) -1);
            for (int i = 0; i < 600; i++) {
                f.step(new Vector3f(8, 5.2F, 0));
                for (RigidBody part : f.parts) {
                    assertTrue("No terrain crossing at strength " + strength + " step=" + i + " position=" + part.getWorldTransform(new Transform()).origin,
                            part.getWorldTransform(new Transform()).origin.x < 1.81F);
                }
                assertTrue("No joint separation when blocked", f.guard.maximumError() < 0.02F);
            }
        }
    }

    @Test
    public void strongerSoftSettingPullsFurtherAndReleaseDoesNotDoubleVelocity() {
        Fixture weak = new Fixture(20);
        Fixture strong = new Fixture(80);
        for (int i = 0; i < 24; i++) {
            weak.step(new Vector3f(4, 5.2F, 0));
            strong.step(new Vector3f(4, 5.2F, 0));
        }
        assertTrue(strong.grab.anchor().x > weak.grab.anchor().x + 0.1F);
        Fixture f = new Fixture(70);
        for (RigidBody part : f.parts) part.setLinearVelocity(new Vector3f(3, 0, 0));
        f.grab.releaseWithInertia();
        for (RigidBody part : f.parts) assertEquals(3, part.getLinearVelocity(new Vector3f()).x, 0.0001F);
    }

    private static Transform pose(float y) {
        Transform result = new Transform(); result.setIdentity(); result.origin.y = y; return result;
    }

    private static final class Fixture {
        final DiscreteDynamicsWorld world;
        final List<RigidBody> parts = new ArrayList<>();
        final GrabJointGuard guard;
        final PhysicsGrab grab;
        Fixture(int strength) {
            var config = new DefaultCollisionConfiguration();
            world = new DiscreteDynamicsWorld(new CollisionDispatcher(config), new DbvtBroadphase(),
                    new SequentialImpulseConstraintSolver(), config);
            world.setGravity(new Vector3f(0, -9.81F, 0));
            world.getSolverInfo().numIterations = 20;
            List<TypedConstraint> joints = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                float mass = i == 3 ? 5 : 0.35F;
                var shape = new BoxShape(new Vector3f(0.1F, 0.25F, 0.1F));
                Vector3f inertia = new Vector3f(); shape.calculateLocalInertia(mass, inertia);
                var body = new RigidBody(new RigidBodyConstructionInfo(mass, new DefaultMotionState(pose(5 - i * 0.5F)), shape, inertia));
                body.setCcdMotionThreshold(0.025F);
                body.setCcdSweptSphereRadius(0.08F);
                world.addRigidBody(body, (short) 2, (short) 1);
                parts.add(body);
                if (i == 0) continue;
                var joint = new Generic6DofConstraint(parts.get(i - 1), body, pose(-0.25F), pose(0.25F), true);
                joint.setLinearLowerLimit(new Vector3f()); joint.setLinearUpperLimit(new Vector3f());
                joint.setAngularLowerLimit(new Vector3f(-1, -1, -1)); joint.setAngularUpperLimit(new Vector3f(1, 1, 1));
                world.addConstraint(joint, true); joints.add(joint);
            }
            guard = new GrabJointGuard(world, joints);
            grab = new PhysicsGrab(parts.get(0), new Vector3f(0, 5.2F, 0), world::addConstraint,
                    world::removeConstraint, () -> true, Vector3f::new, guard, strength);
        }
        void step(Vector3f target) {
            grab.moveTo(target); grab.beforeStep(); world.stepSimulation(STEP, 0, STEP); grab.afterStep();
        }
    }
}
