package com.ysmragdoll.client.physics;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.collision.dispatch.CollisionWorld;
import com.bulletphysics.collision.shapes.ConvexShape;
import com.bulletphysics.dynamics.constraintsolver.Point2PointConstraint;
import com.bulletphysics.dynamics.constraintsolver.TypedConstraint;
import com.bulletphysics.linearmath.Transform;
import javax.vecmath.Vector3f;
import javax.vecmath.Quat4f;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Fixed-step, whole-ragdoll translation assistance with a local point anchor. */
public final class PhysicsGrab implements AutoCloseable {
    static final float MAX_SPEED = 6.0F;
    private static final float MAX_ACCELERATION = 24.0F;
    private static final float MAX_RELATIVE_SPEED = 1.0F;
    private static final float MAX_ANGULAR_SPEED = 1.25F;
    private final RigidBody body;
    private final List<RigidBody> bodies;
    private final DiscreteDynamicsWorld collisionWorld;
    private final Point2PointConstraint constraint;
    private final Consumer<TypedConstraint> remove;
    private final BooleanSupplier valid;
    private final Supplier<Vector3f> offset;
    private final Transform transform = new Transform();
    private final Vector3f localAnchor;
    private final Vector3f desiredWorldPoint = new Vector3f();
    private final Vector3f driveVelocity = new Vector3f();
    private final Quat4f heldOrientation = new Quat4f();
    private boolean closed;

    PhysicsGrab(RigidBody body, Vector3f hit, Consumer<TypedConstraint> add,
                Consumer<TypedConstraint> remove, BooleanSupplier valid, Supplier<Vector3f> offset) {
        this(body, List.of(body), hit, add, remove, valid, offset, null);
    }

    PhysicsGrab(RigidBody body, List<RigidBody> bodies, Vector3f hit, Consumer<TypedConstraint> add,
                Consumer<TypedConstraint> remove, BooleanSupplier valid, Supplier<Vector3f> offset,
                DiscreteDynamicsWorld collisionWorld) {
        this.body = body;
        this.bodies = List.copyOf(bodies);
        this.collisionWorld = collisionWorld;
        this.remove = remove;
        this.valid = valid;
        this.offset = offset;
        body.getWorldTransform(transform);
        transform.getRotation(heldOrientation);
        transform.inverse();
        localAnchor = new Vector3f(hit);
        transform.transform(localAnchor);
        constraint = new Point2PointConstraint(body, localAnchor);
        constraint.setting.tau = 0.15F;
        constraint.setting.damping = 1.0F;
        constraint.setting.impulseClamp = 8.0F;
        constraint.setPivotB(hit);
        desiredWorldPoint.set(hit);
        desiredWorldPoint.sub(offset.get());
        add.accept(constraint);
        for (RigidBody part : bodies) part.activate(true);
    }

    public boolean isActive() { return !closed && valid.getAsBoolean(); }

    public void moveTo(Vector3f worldPoint) {
        if (!isActive()) { close(); return; }
        desiredWorldPoint.set(worldPoint);
    }

    /** Called once for each 120 Hz physics step, never once per input/render event. */
    void beforeStep(float seconds) {
        if (!isActive()) { close(); return; }
        Vector3f target = new Vector3f(desiredWorldPoint);
        target.add(offset.get());
        Vector3f current = new Vector3f(localAnchor);
        body.getWorldTransform(transform);
        transform.transform(current);
        Vector3f requested = new Vector3f();
        requested.sub(target, current);
        requested.scale(8.0F);
        limit(requested, MAX_SPEED);
        Vector3f acceleration = new Vector3f();
        acceleration.sub(requested, driveVelocity);
        limit(acceleration, MAX_ACCELERATION * seconds);
        driveVelocity.add(acceleration);
        clipAssemblyMotion(seconds);

        // Move the entire linked assembly coherently, instead of accelerating a light hand
        // while its heavier torso is left behind. Relative motion still permits articulation.
        Vector3f mean = meanVelocity();
        for (RigidBody part : bodies) {
            Vector3f velocity = part.getLinearVelocity(new Vector3f());
            velocity.sub(mean);
            velocity.scale((float) Math.exp(-10.0F * seconds));
            limit(velocity, MAX_RELATIVE_SPEED);
            velocity.add(driveVelocity);
            part.setLinearVelocity(velocity);
            part.activate(true);
        }
        // Keep the solver target close to the actual anchor even when the mouse jumps 180°.
        // A blocked limb cannot accumulate a long, highly stretched virtual spring.
        current.scaleAdd(seconds / constraint.setting.tau, driveVelocity, current);
        constraint.setPivotB(current);
        dampRotation(seconds);
        Quat4f rotation = transform.getRotation(new Quat4f());
        rotation.conjugate();
        Quat4f error = new Quat4f();
        error.mul(heldOrientation, rotation);
        if (error.w < 0) error.scale(-1);
        Vector3f correction = new Vector3f(error.x, error.y, error.z);
        correction.scale(12.0F);
        limit(correction, MAX_ANGULAR_SPEED);
        body.setAngularVelocity(correction);
    }

    void afterStep(float seconds) {
        if (!isActive()) { close(); return; }
        dampRotation(seconds);
    }

    private void dampRotation(float seconds) {
        for (RigidBody part : bodies) {
            Vector3f angular = part.getAngularVelocity(new Vector3f());
            angular.scale((float) Math.exp(-12.0F * seconds));
            limit(angular, MAX_ANGULAR_SPEED);
            part.setAngularVelocity(angular);
        }
    }

    /** A foot hitting a wall limits the shared drive, rather than stretching the arm chain. */
    private void clipAssemblyMotion(float seconds) {
        if (collisionWorld == null || driveVelocity.lengthSquared() < 1.0E-8F) return;
        for (RigidBody part : bodies) {
            if (!(part.getCollisionShape() instanceof ConvexShape shape)) continue;
            Transform from = part.getWorldTransform(new Transform());
            Transform to = new Transform(from);
            // Small clearance keeps the shared motion out of the contact solver's slop region.
            float duration = seconds + 0.015F / Math.max(0.1F, driveVelocity.length());
            to.origin.scaleAdd(duration, driveVelocity, from.origin);
            var hit = new CollisionWorld.ClosestConvexResultCallback(from.origin, to.origin) {
                @Override
                public float addSingleResult(CollisionWorld.LocalConvexResult result, boolean normalInWorldSpace) {
                    Vector3f normal = new Vector3f(result.hitNormalLocal);
                    if (!normalInWorldSpace) {
                        result.hitCollisionObject.getWorldTransform(new Transform()).basis.transform(normal);
                    }
                    // Contact with the floor must not prevent tangential motion or lifting away.
                    if (normal.dot(driveVelocity) >= -1.0E-5F) return 1.0F;
                    return super.addSingleResult(result, normalInWorldSpace);
                }
            };
            hit.collisionFilterGroup = 2;
            hit.collisionFilterMask = 1;
            collisionWorld.convexSweepTest(shape, from, to, hit);
            if (hit.hasHit()) {
                Vector3f normal = hit.hitNormalWorld;
                normal.normalize();
                float approach = normal.dot(driveVelocity);
                if (approach < 0) driveVelocity.scaleAdd(-approach, normal, driveVelocity);
            }
        }
    }

    private Vector3f meanVelocity() {
        Vector3f mean = new Vector3f();
        float mass = 0;
        for (RigidBody part : bodies) {
            float weight = 1.0F / part.getInvMass();
            mean.scaleAdd(weight, part.getLinearVelocity(new Vector3f()), mean);
            mass += weight;
        }
        mean.scale(1.0F / mass);
        return mean;
    }

    private static void limit(Vector3f vector, float maximum) {
        float squared = vector.lengthSquared();
        if (squared > maximum * maximum) vector.scale(maximum / (float) Math.sqrt(squared));
    }

    public Vector3f anchor() {
        body.getWorldTransform(transform);
        Vector3f result = new Vector3f(localAnchor);
        transform.transform(result);
        result.sub(offset.get());
        return result;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        remove.accept(constraint);
        body.activate(true);
    }
}
