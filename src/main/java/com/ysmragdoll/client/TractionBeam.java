package com.ysmragdoll.client;

import net.minecraft.world.phys.Vec3;

/** Visual-only trailing curve. Both attachment points remain exact, independent of smoothing. */
final class TractionBeam {
    private Vec3 previousTarget;
    private Vec3 bend = Vec3.ZERO;

    void reset() {
        previousTarget = null;
        bend = Vec3.ZERO;
    }

    Vec3 update(Vec3 target, Vec3 anchor, double seconds) {
        // A full turn at the 12-block reach can move the target 24 blocks; only reset for teleports.
        if (previousTarget == null || previousTarget.distanceToSqr(target) > 4096) {
            previousTarget = target;
            bend = Vec3.ZERO;
        }
        double dt = Math.max(0, Math.min(0.05, seconds));
        previousTarget = previousTarget.lerp(target, -Math.expm1(-dt / 0.09));
        Vec3 desired = previousTarget.subtract(target).scale(0.85)
                .add(target.subtract(anchor).scale(0.4));
        if (desired.lengthSqr() > 1.44) desired = desired.normalize().scale(1.2);
        bend = bend.lerp(desired, -Math.expm1(-dt / 0.045));
        return bend;
    }

    static Vec3 point(Vec3 from, Vec3 to, Vec3 bend, double t) {
        Vec3 axis = to.subtract(from);
        double length = axis.length();
        if (length < 1.0E-6) return from;
        Vec3 direction = axis.scale(1 / length);
        Vec3 sideways = bend.subtract(direction.scale(bend.dot(direction)));
        double maximum = Math.min(1.2, length * 0.25);
        if (sideways.lengthSqr() > maximum * maximum) sideways = sideways.normalize().scale(maximum);
        // Zero at both ends, with a straight tangent at the player's end and maximum bend at t=2/3.
        return from.lerp(to, t).add(sideways.scale(6.75 * t * t * (1 - t)));
    }
}
