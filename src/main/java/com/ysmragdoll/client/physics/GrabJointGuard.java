package com.ysmragdoll.client.physics;

import com.bulletphysics.collision.dispatch.CollisionWorld;
import com.bulletphysics.collision.shapes.ConvexShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.dynamics.constraintsolver.TypedConstraint;
import com.bulletphysics.linearmath.Transform;
import javax.vecmath.Vector3f;
import java.util.List;

/** Grab-only positional correction of joint anchors; never changes joint angles or body rotations. */
final class GrabJointGuard {
    private static final float TOLERANCE = 0.001F;
    private static final int MAX_PASSES = 32;
    private static final float MAX_CORRECTION = 0.04F;
    private final DiscreteDynamicsWorld world;
    private final List<Generic6DofConstraint> joints;

    GrabJointGuard(DiscreteDynamicsWorld world, List<TypedConstraint> constraints) {
        this.world = world;
        joints = constraints.stream().filter(Generic6DofConstraint.class::isInstance)
                .map(Generic6DofConstraint.class::cast).toList();
    }

    float maximumError() {
        float maximum = 0;
        for (Generic6DofConstraint joint : joints) maximum = Math.max(maximum, separation(joint).length());
        return maximum;
    }

    void correct() {
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            boolean moved = false;
            for (Generic6DofConstraint joint : joints) {
                Vector3f delta = separation(joint);
                float gap = delta.length();
                if (gap <= TOLERANCE) continue;
                RigidBody a = joint.getRigidBodyA();
                RigidBody b = joint.getRigidBodyB();
                float inverseSum = a.getInvMass() + b.getInvMass();
                if (inverseSum <= 0) continue;
                Vector3f normal = new Vector3f(delta);
                normal.scale(1 / gap);
                float correction = Math.min(MAX_CORRECTION, gap - TOLERANCE);
                Vector3f move = new Vector3f(normal);
                move.scale(correction * a.getInvMass() / inverseSum);
                moved |= translateSafely(a, move);
                move.set(normal);
                move.scale(-correction * b.getInvMass() / inverseSum);
                moved |= translateSafely(b, move);
                removeSeparatingVelocity(joint, normal);
            }
            if (!moved) break;
        }
    }

    private Vector3f separation(Generic6DofConstraint joint) {
        joint.calculateTransforms();
        Vector3f delta = joint.getCalculatedTransformB(new Transform()).origin;
        delta.sub(joint.getCalculatedTransformA(new Transform()).origin);
        return delta;
    }

    private void removeSeparatingVelocity(Generic6DofConstraint joint, Vector3f normal) {
        RigidBody a = joint.getRigidBodyA();
        RigidBody b = joint.getRigidBodyB();
        Vector3f pivotA = joint.getFrameOffsetA(new Transform()).origin;
        Vector3f pivotB = joint.getFrameOffsetB(new Transform()).origin;
        a.getWorldTransform(new Transform()).basis.transform(pivotA);
        b.getWorldTransform(new Transform()).basis.transform(pivotB);
        Vector3f relative = b.getVelocityInLocalPoint(pivotB, new Vector3f());
        relative.sub(a.getVelocityInLocalPoint(pivotA, new Vector3f()));
        float separating = relative.dot(normal);
        if (separating <= 0) return;
        Vector3f pointA = new Vector3f(pivotA);
        pointA.add(a.getWorldTransform(new Transform()).origin);
        Vector3f pointB = new Vector3f(pivotB);
        pointB.add(b.getWorldTransform(new Transform()).origin);
        float denominator = a.computeImpulseDenominator(pointA, normal)
                + b.computeImpulseDenominator(pointB, normal);
        // Include angular effective mass: correcting only linear velocity can
        // turn rapid limb rotation into additional translation and instability.
        if (denominator <= 0) return;
        Vector3f impulse = new Vector3f(normal);
        impulse.scale(separating / denominator);
        a.applyImpulse(impulse, pivotA);
        impulse.negate();
        b.applyImpulse(impulse, pivotB);
    }

    private boolean translateSafely(RigidBody body, Vector3f movement) {
        if (movement.lengthSquared() < 1.0E-10F || body.getInvMass() == 0) return false;
        if (!(body.getCollisionShape() instanceof ConvexShape shape)) return false;
        Transform from = body.getWorldTransform(new Transform());
        Transform to = new Transform(from);
        to.origin.add(movement);
        var hit = new CollisionWorld.ClosestConvexResultCallback(from.origin, to.origin) {
            @Override
            public float addSingleResult(CollisionWorld.LocalConvexResult result, boolean worldNormal) {
                Vector3f normal = new Vector3f(result.hitNormalLocal);
                if (!worldNormal) result.hitCollisionObject.getWorldTransform(new Transform()).basis.transform(normal);
                if (normal.dot(movement) >= -1.0E-8F) return 1;
                return super.addSingleResult(result, worldNormal);
            }
        };
        hit.collisionFilterGroup = 2;
        hit.collisionFilterMask = 1;
        world.convexSweepTest(shape, from, to, hit);
        if (hit.hasHit()) {
            float fraction = Math.max(0, hit.closestHitFraction - TOLERANCE / movement.length());
            movement.scale(fraction);
            if (movement.lengthSquared() < 1.0E-10F) return false;
            to.origin.add(from.origin, movement);
        }
        body.setWorldTransform(to);
        body.setInterpolationWorldTransform(to);
        if (body.getMotionState() != null) body.getMotionState().setWorldTransform(to);
        world.updateSingleAabb(body);
        body.activate(true);
        return true;
    }
}
