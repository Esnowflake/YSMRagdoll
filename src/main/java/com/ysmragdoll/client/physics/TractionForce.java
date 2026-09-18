package com.ysmragdoll.client.physics;

import javax.vecmath.Vector3f;

/** Spring force and damping share one total force budget, including gravity compensation. */
final class TractionForce {
    static float maximum(int strength) {
        float ratio = Math.max(0, Math.min(99, strength)) / 100F;
        return 1800 * ratio * ratio;
    }

    static Vector3f calculate(Vector3f error, Vector3f velocity, float mass, int strength) {
        if (strength <= 0 || mass <= 0) return new Vector3f();
        float ratio = Math.min(99, strength) / 100F;
        float spring = 40 + 1200 * ratio * ratio;
        float damping = 1.8F * (float) Math.sqrt(spring * mass);
        Vector3f force = new Vector3f(error);
        force.scale(spring);
        force.scaleAdd(-damping, velocity, force);
        force.y += mass * 9.81F;
        GrabInertia.limit(force, maximum(strength));
        return force;
    }
}
