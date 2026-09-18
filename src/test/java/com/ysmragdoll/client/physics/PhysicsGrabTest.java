package com.ysmragdoll.client.physics;

import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import org.junit.jupiter.api.Test;
import javax.vecmath.Vector3f;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test
    public void pullsWithConstraintWithoutTeleportingAndFallsAfterRelease() {
        var world = world();
        var body = body(world, identity());
        var grab = new PhysicsGrab(body, new Vector3f(), world::addConstraint,
                world::removeConstraint, () -> true, Vector3f::new);
        Vector3f target = new Vector3f(2, 3, 0);
        grab.moveTo(target);
        assertEquals(0, body.getWorldTransform(new Transform()).origin.length(), 0);
        step(world, 240);
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
}
