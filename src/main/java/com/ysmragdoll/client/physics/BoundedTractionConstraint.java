package com.ysmragdoll.client.physics;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.constraintsolver.Point2PointConstraint;
import com.bulletphysics.linearmath.Transform;
import javax.vecmath.Matrix3f;
import javax.vecmath.Vector3f;

/** A compliant point spring solved alongside the joints, with one impulse budget per substep. */
final class BoundedTractionConstraint extends Point2PointConstraint {
    private final float mass;
    private final int strength;
    private final Vector3f accumulated = new Vector3f();
    private final Vector3f arm = new Vector3f();
    private final Vector3f error = new Vector3f();
    private final Matrix3f response = new Matrix3f();
    private final Matrix3f inverse = new Matrix3f();
    private final Vector3f desiredVelocity = new Vector3f();
    private float softness;
    private boolean prepared;
    private boolean pulling = true;

    BoundedTractionConstraint(RigidBody body, Vector3f localAnchor, float mass, int strength) {
        super(body, localAnchor);
        this.mass = mass;
        this.strength = strength;
    }

    void setPulling(boolean pulling) { this.pulling = pulling; }

    @Override
    public void buildJacobian() {
        accumulated.set(0, 0, 0);
        appliedImpulse = 0;
        prepared = false;
        Transform pose = rbA.getWorldTransform(new Transform());
        getPivotInA(arm);
        pose.basis.transform(arm);
        getPivotInB(error);
        error.sub(pose.origin);
        error.sub(arm);
        Matrix3f inertia = rbA.getInvInertiaTensorWorld(new Matrix3f());
        // Full point effective mass, including off-centre angular response.
        for (int axis = 0; axis < 3; axis++) {
            Vector3f unit = new Vector3f();
            if (axis == 0) unit.x = 1;
            else if (axis == 1) unit.y = 1;
            else unit.z = 1;
            Vector3f angular = new Vector3f();
            angular.cross(arm, unit);
            inertia.transform(angular);
            angular.scale(rbA.getAngularFactor());
            Vector3f column = new Vector3f();
            column.cross(angular, arm);
            column.scaleAdd(rbA.getInvMass(), unit, column);
            response.setColumn(axis, column);
        }
    }

    @Override
    public void solveConstraint(float seconds) {
        if (!pulling || seconds <= 0 || rbA.getInvMass() <= 0) return;
        if (!prepared) {
            float spring = TractionForce.spring(strength);
            float damping = TractionForce.damping(strength, mass);
            float denominator = damping + seconds * spring;
            softness = 1 / (seconds * denominator);
            inverse.set(response);
            inverse.m00 += softness;
            inverse.m11 += softness;
            inverse.m22 += softness;
            inverse.invert();
            desiredVelocity.set(error);
            desiredVelocity.scale(spring);
            // Support the assembly at this attachment, not independently at every limb.
            desiredVelocity.y += mass * 9.81F;
            desiredVelocity.scale(1 / denominator);
            // A discontinuous aim jump must not request an explosive attachment speed.
            // This bounds the motor only; free body velocities and damping are untouched.
            GrabInertia.limit(desiredVelocity, 10);
            prepared = true;
        }
        Vector3f increment = new Vector3f(desiredVelocity);
        increment.sub(rbA.getVelocityInLocalPoint(arm, new Vector3f()));
        increment.scaleAdd(-softness, accumulated, increment);
        inverse.transform(increment);
        Vector3f next = new Vector3f(accumulated);
        next.add(increment);
        // Limit the total vector, not each axis or each solver iteration separately.
        GrabInertia.limit(next, TractionForce.maximum(strength) * seconds);
        increment.sub(next, accumulated);
        accumulated.set(next);
        appliedImpulse = accumulated.length();
        rbA.applyImpulse(increment, arm);
    }
}
