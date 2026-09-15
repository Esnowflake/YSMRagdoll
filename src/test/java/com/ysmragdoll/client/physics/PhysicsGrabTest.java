package com.ysmragdoll.client.physics;

import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import org.junit.Test;
import javax.vecmath.Vector3f;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class PhysicsGrabTest {
    private DiscreteDynamicsWorld world() {
        var config = new DefaultCollisionConfiguration();
        var world = new DiscreteDynamicsWorld(new CollisionDispatcher(config), new DbvtBroadphase(),
                new SequentialImpulseConstraintSolver(), config);
        world.setGravity(new Vector3f(0, -9.81F, 0));
        world.getSolverInfo().numIterations = 20;
        return world;
    }

    private RigidBody body(DiscreteDynamicsWorld world, Transform transform) {
        BoxShape shape = new BoxShape(new Vector3f(0.25F, 0.25F, 0.25F));
        Vector3f inertia = new Vector3f();
        shape.calculateLocalInertia(1, inertia);
        RigidBody body = new RigidBody(new RigidBodyConstructionInfo(1,
                new DefaultMotionState(transform), shape, inertia));
        world.addRigidBody(body);
        return body;
    }

    private Transform identity() { Transform t = new Transform(); t.setIdentity(); return t; }

    private void step(DiscreteDynamicsWorld world, int steps) {
        for (int i = 0; i < steps; i++) world.stepSimulation(1F / 120F, 0, 1F / 120F);
    }

    private void step(DiscreteDynamicsWorld world, PhysicsGrab grab, int steps) {
        for (int i = 0; i < steps; i++) {
            grab.beforeStep(1F / 120F);
            world.stepSimulation(1F / 120F, 0, 1F / 120F);
            grab.afterStep(1F / 120F);
        }
    }

    @Test
    public void pullsWithConstraintWithoutTeleportingAndFallsAfterRelease() {
        var world = world();
        var body = body(world, identity());
        var grab = new PhysicsGrab(body, new Vector3f(), world::addConstraint,
                world::removeConstraint, () -> true, Vector3f::new);
        Vector3f target = new Vector3f(2, 3, 0);
        grab.moveTo(target);
        assertEquals(0, body.getWorldTransform(new Transform()).origin.length(), 0);
        step(world, grab, 240);
        Vector3f error = grab.anchor();
        error.sub(target);
        assertTrue("Grab should reach its target under gravity: " + error, error.length() < 0.1F);
        float heldY = body.getWorldTransform(new Transform()).origin.y;
        grab.close();
        grab.close();
        assertEquals(0, world.getNumConstraints());
        assertFalse(grab.isActive());
        step(world, 60);
        assertTrue(body.getWorldTransform(new Transform()).origin.y < heldY - 0.5F);
    }

    @Test
    public void anchorRemainsAttachedToRotatedLimbAndUsesWorldOffset() {
        var world = world();
        Transform transform = identity();
        transform.basis.rotZ((float) Math.PI / 2);
        transform.origin.set(4, 5, 6);
        var body = body(world, transform);
        Vector3f offset = new Vector3f(1, 2, 3);
        var grab = new PhysicsGrab(body, new Vector3f(4, 5.2F, 6), world::addConstraint,
                world::removeConstraint, () -> true, () -> new Vector3f(offset));
        assertTrue(grab.anchor().epsilonEquals(new Vector3f(3, 3.2F, 3), 0.001F));
        transform.origin.x += 2;
        body.setWorldTransform(transform);
        assertTrue(grab.anchor().epsilonEquals(new Vector3f(5, 3.2F, 3), 0.001F));
        grab.close();
    }

    @Test
    public void invalidatedTargetReleasesBeforeFurtherMovement() {
        var world = world();
        var body = body(world, identity());
        AtomicBoolean valid = new AtomicBoolean(true);
        var grab = new PhysicsGrab(body, new Vector3f(), world::addConstraint,
                world::removeConstraint, valid::get, Vector3f::new);
        valid.set(false);
        grab.moveTo(new Vector3f(20, 20, 20));
        assertFalse(grab.isActive());
        assertEquals(0, world.getNumConstraints());
        assertEquals(0, body.getWorldTransform(new Transform()).origin.length(), 0);
    }

    @Test
    public void violentHandTargetsKeepUnequalMassLimbChainConnected() {
        exerciseLimbChain(false);
    }

    @Test
    public void blockedLowerLimbsStayConnectedDuringSidewaysPulls() {
        exerciseLimbChain(true);
    }

    private void exerciseLimbChain(boolean obstacle) {
        var world = world();
        world.getSolverInfo().numIterations = 60;
        List<RigidBody> parts = new ArrayList<>();
        List<Generic6DofConstraint> joints = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Transform pose = identity();
            pose.origin.set(0, 5 - i * 0.5F, 0);
            BoxShape shape = new BoxShape(new Vector3f(0.1F, 0.25F, 0.1F));
            float mass = i == 3 ? 5 : 0.35F;
            Vector3f inertia = new Vector3f();
            shape.calculateLocalInertia(mass, inertia);
            RigidBody part = new RigidBody(new RigidBodyConstructionInfo(mass,
                    new DefaultMotionState(pose), shape, inertia));
            world.addRigidBody(part, (short) 2, (short) 1);
            parts.add(part);
            if (i > 0) {
                Transform a = identity(); a.origin.y = -0.25F;
                Transform b = identity(); b.origin.y = 0.25F;
                var joint = new Generic6DofConstraint(parts.get(i - 1), part, a, b, true);
                joint.setLinearLowerLimit(new Vector3f());
                joint.setLinearUpperLimit(new Vector3f());
                joint.setAngularLowerLimit(new Vector3f(-1.0F, -1.0F, -1.0F));
                joint.setAngularUpperLimit(new Vector3f(1.0F, 1.0F, 1.0F));
                world.addConstraint(joint, true);
                joints.add(joint);
            }
        }
        PhysicsGrab grab = new PhysicsGrab(parts.get(0), parts, new Vector3f(0.08F, 5.2F, 0),
                world::addConstraint, world::removeConstraint, () -> true, Vector3f::new, world);
        if (obstacle) {
            Transform wallPose = identity();
            wallPose.origin.set(2, 2, 0);
            RigidBody wall = new RigidBody(new RigidBodyConstructionInfo(0,
                    new DefaultMotionState(wallPose), new BoxShape(new Vector3f(0.5F, 1, 3)),
                    new Vector3f()));
            world.addRigidBody(wall, (short) 1, (short) -1);
        }
        float maxGap = 0;
        float maxSpin = 0;
        float maxPenetration = 0;
        for (int i = 0; i < 1200; i++) {
            grab.moveTo(obstacle ? new Vector3f((i / 240 % 2 == 0) ? 8 : -8, 5.2F, 0)
                    : new Vector3f((i / 45 % 2 == 0) ? 12 : -12,
                    (i / 60 % 2 == 0) ? 9 : 2, (i / 30 % 2 == 0) ? -8 : 8));
            step(world, grab, 1);
            for (Generic6DofConstraint joint : joints) {
                joint.calculateTransforms();
                Vector3f delta = joint.getCalculatedTransformA(new Transform()).origin;
                delta.sub(joint.getCalculatedTransformB(new Transform()).origin);
                maxGap = Math.max(maxGap, delta.length());
            }
            for (RigidBody part : parts) maxSpin = Math.max(maxSpin,
                    part.getAngularVelocity(new Vector3f()).length());
            for (int manifold = 0; manifold < world.getDispatcher().getNumManifolds(); manifold++) {
                var contacts = world.getDispatcher().getManifoldByIndexInternal(manifold);
                for (int contact = 0; contact < contacts.getNumContacts(); contact++) {
                    maxPenetration = Math.max(maxPenetration, -contacts.getContactPoint(contact).getDistance());
                }
            }
        }
        System.out.println("Grab obstacle=" + obstacle + ": max joint gap=" + maxGap + ", angular speed=" + maxSpin);
        assertTrue("Limb chain must stay connected: " + maxGap, maxGap < 0.02F);
        assertTrue("No runaway spin: " + maxSpin, maxSpin <= 1.251F);
        assertTrue("Keep terrain collisions active: " + maxPenetration, maxPenetration < 0.05F);
        Transform held = parts.get(0).getWorldTransform(new Transform());
        assertTrue("Picked hand retains its orientation", held.basis.m00 > 0.9F && held.basis.m11 > 0.9F);
        grab.close();
        assertEquals(joints.size(), world.getNumConstraints());
    }
}
