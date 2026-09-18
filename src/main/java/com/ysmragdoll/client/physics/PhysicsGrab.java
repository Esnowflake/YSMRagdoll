package com.ysmragdoll.client.physics;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.constraintsolver.Point2PointConstraint;
import com.bulletphysics.dynamics.constraintsolver.TypedConstraint;
import com.bulletphysics.linearmath.Transform;
import javax.vecmath.Vector3f;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Point-driven grabbing with optional collision-aware joint repair after each physics step. */
public final class PhysicsGrab implements AutoCloseable {
    private static final float STRAIN_LIMIT = 0.025F;
    private final RigidBody body;
    private final Point2PointConstraint constraint;
    private final Consumer<TypedConstraint> remove;
    private final BooleanSupplier valid;
    private final Supplier<Vector3f> offset;
    private final Transform transform = new Transform();
    private final Vector3f localAnchor;
    private boolean closed;
    private final GrabJointGuard jointGuard;
    private final Vector3f requestedPoint = new Vector3f();
    private final GrabInertia inertia = new GrabInertia();
    private final int strength;
    private final boolean constraintInstalled;

    PhysicsGrab(RigidBody body, Vector3f hit, Consumer<TypedConstraint> add,
                Consumer<TypedConstraint> remove, BooleanSupplier valid, Supplier<Vector3f> offset) {
        this(body, hit, add, remove, valid, offset, null);
    }

    PhysicsGrab(RigidBody body, Vector3f hit, Consumer<TypedConstraint> add,
                Consumer<TypedConstraint> remove, BooleanSupplier valid, Supplier<Vector3f> offset,
                GrabJointGuard jointGuard) {
        this(body, hit, add, remove, valid, offset, jointGuard, 100);
    }

    PhysicsGrab(RigidBody body, Vector3f hit, Consumer<TypedConstraint> add,
                Consumer<TypedConstraint> remove, BooleanSupplier valid, Supplier<Vector3f> offset,
                GrabJointGuard jointGuard, int strength) {
        this.strength = Math.max(0, Math.min(100, strength));
        this.jointGuard = jointGuard;
        this.body = body;
        this.remove = remove;
        this.valid = valid;
        this.offset = offset;
        body.getWorldTransform(transform);
        transform.inverse();
        localAnchor = new Vector3f(hit);
        transform.transform(localAnchor);
        constraint = jointGuard != null && this.strength > 0 && this.strength < 100
                ? new BoundedTractionConstraint(body, localAnchor, jointGuard.mass(body), this.strength)
                : new Point2PointConstraint(body, localAnchor);
        constraint.setting.tau = 0.15F;
        constraint.setting.damping = 1.0F;
        constraint.setting.impulseClamp = 8.0F;
        constraint.setPivotB(hit);
        requestedPoint.set(hit);
        requestedPoint.sub(offset.get());
        constraintInstalled = this.strength > 0;
        if (constraintInstalled) add.accept(constraint);
        body.activate(true);
    }

    public boolean isActive() { return !closed && strength > 0 && valid.getAsBoolean(); }

    public int strength() { return strength; }

    public void moveTo(Vector3f worldPoint) {
        if (!isActive()) { close(); return; }
        requestedPoint.set(worldPoint);
        if (jointGuard != null) return;
        Vector3f target = new Vector3f(worldPoint);
        target.add(offset.get());
        constraint.setPivotB(target);
        body.activate(true);
    }

    void beforeStep() {
        if (!isActive()) { close(); return; }
        if (jointGuard == null) return;
        float error = jointGuard.maximumError();
        Vector3f current = new Vector3f(localAnchor);
        body.getWorldTransform(transform);
        transform.transform(current);
        Vector3f delta = new Vector3f(requestedPoint);
        delta.add(offset.get());
        delta.sub(current);
        if (strength < 100) {
            // Only the attachment receives external traction; the joints transmit it to the limbs.
            BoundedTractionConstraint spring = (BoundedTractionConstraint) constraint;
            current.add(delta);
            spring.setPivotB(current);
            spring.setPulling(error < STRAIN_LIMIT && jointGuard.preparePull(body, delta, strength));
            return;
        }
        Vector3f movement = error < STRAIN_LIMIT ? jointGuard.follow(body, delta, true) : new Vector3f();
        Vector3f feedback = inertia.step(movement);
        if (error < STRAIN_LIMIT) jointGuard.applyInertia(body, feedback);
        current.set(localAnchor);
        body.getWorldTransform(transform);
        transform.transform(current);
        // Anchor only at the position accepted by the assembly sweep. A residual motor
        // toward an obstructed target would keep forcing the picked limb through the wall.
        constraint.setPivotB(current);
        // An obstructed joint takes priority over tracking the crosshair.
        constraint.setting.impulseClamp = error > STRAIN_LIMIT ? 0.05F : 1.0F;
        body.activate(true);
    }

    void afterStep() {
        if (isActive() && jointGuard != null) {
            jointGuard.correct();
            if (strength == 100) {
                Vector3f error = new Vector3f(requestedPoint);
                error.sub(anchor());
                jointGuard.follow(body, error, true);
            }
        }
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
        if (constraintInstalled) remove.accept(constraint);
        body.activate(true);
    }

    /** Explicit use-key release only; closing a screen/world/invalid grab must not throw a corpse. */
    public void releaseWithInertia() {
        if (isActive() && jointGuard != null) {
            // Soft-mode velocities are already real: never add the same movement twice.
            jointGuard.releaseWithVelocity(body, strength == 100 ? inertia.releaseVelocity() : new Vector3f());
        }
        close();
    }
}
