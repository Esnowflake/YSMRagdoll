package com.ysmragdoll.client.physics;

/** Spring force and damping share one total force budget, including gravity compensation. */
final class TractionForce {
    static float maximum(int strength) {
        float ratio = Math.max(0, Math.min(99, strength)) / 100F;
        return 1800 * ratio * ratio;
    }

    static float spring(int strength) {
        float ratio = Math.min(99, strength) / 100F;
        return 40 + 1200 * ratio * ratio;
    }

    static float damping(int strength, float mass) {
        return 1.8F * (float) Math.sqrt(spring(strength) * mass);
    }
}
