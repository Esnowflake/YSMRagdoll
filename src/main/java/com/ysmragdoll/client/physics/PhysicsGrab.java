package com.ysmragdoll.client.physics;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.constraintsolver.Point2PointConstraint;
import com.bulletphysics.dynamics.constraintsolver.TypedConstraint;
import com.bulletphysics.linearmath.Transform;
import javax.vecmath.Vector3f;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A bounded point constraint: no teleporting, freezing or disabling collisions/gravity. */
public final class PhysicsGrab implements AutoCloseable {
    private final RigidBody body;
    private final Point2PointConstraint constraint;
    private final Consumer<TypedConstraint> remove;
    private final BooleanSupplier valid;
    private final Supplier<Vector3f> offset;
    private final Transform transform = new Transform();
    private final Vector3f localAnchor;
    private boolean closed;

    PhysicsGrab(RigidBody body, Vector3f hit, Consumer<TypedConstraint> add,
                Consumer<TypedConstraint> remove, BooleanSupplier valid, Supplier<Vector3f> offset) {
        this.body = body;
        this.remove = remove;
        this.valid = valid;
        this.offset = offset;
        body.getWorldTransform(transform);
        transform.inverse();
        localAnchor = new Vector3f(hit);
        transform.transform(localAnchor);
        constraint = new Point2PointConstraint(body, localAnchor);
        constraint.setting.tau = 0.15F;
        constraint.setting.damping = 1.0F;
        constraint.setting.impulseClamp = 8.0F;
        constraint.setPivotB(hit);
        add.accept(constraint);
        body.activate(true);
    }

    public boolean isActive() { return !closed && valid.getAsBoolean(); }

    public void moveTo(Vector3f worldPoint) {
        if (!isActive()) { close(); return; }
        Vector3f target = new Vector3f(worldPoint);
        target.add(offset.get());
        constraint.setPivotB(target);
        body.activate(true);
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
