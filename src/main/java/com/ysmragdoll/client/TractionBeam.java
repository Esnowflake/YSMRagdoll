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
        // Bow towards the pulling direction, then return to the actual anchor at the endpoint.
        // Reversing this displacement makes the beam bulge back towards the old aim position.
        Vec3 desired = target.subtract(previousTarget).scale(0.85)
                .add(target.subtract(anchor).scale(0.4));
        if (desired.lengthSqr() > 1.44) desired = desired.normalize().scale(1.2);
        // Respond to a new pull quickly, but retain the existing arc while the pull settles.
        boolean relaxing = desired.dot(bend) >= 0 && desired.lengthSqr() < bend.lengthSqr();
        bend = bend.lerp(desired, -Math.expm1(-dt / (relaxing ? 0.35 : 0.07)));
        return bend;
    }

    static Curve curve(Vec3 from, Vec3 to, Vec3 bend, Vec3 viewForward, Vec3 viewUp) {
        Vec3 axis = to.subtract(from);
        double length = axis.length();
        if (length < 1.0E-6) return new Curve(from, from, to);
        Vec3 direction = axis.scale(1 / length);
        Vec3 sideways = bend.subtract(direction.scale(bend.dot(direction)));
        double maximum = Math.min(1.2, length * 0.20);
        if (sideways.lengthSqr() > maximum * maximum) sideways = sideways.normalize().scale(maximum);
        // A single quadratic arc has no inflection or short return hump near the attachment.
        Vec3 control = from.lerp(to, 0.70).add(sideways.scale(1.5));
        Vec3 forward = viewForward.normalize();
        Vec3 right = forward.cross(viewUp).normalize();
        Vec3 up = right.cross(forward).normalize();
        double fromDepth = from.dot(forward);
        double toDepth = to.dot(forward);
        double controlDepth = control.dot(forward);
        if (fromDepth > 0.01 && toDepth > 0.01 && controlDepth > 0.01 && right.lengthSqr() > 0.5) {
            // Keep the projected control point between the two visible endpoints. This prevents
            // the last section from rising above the target or curling back in screen space.
            double x = between(control.dot(right) / controlDepth,
                    from.dot(right) / fromDepth, to.dot(right) / toDepth);
            double y = between(control.dot(up) / controlDepth,
                    from.dot(up) / fromDepth, to.dot(up) / toDepth);
            control = forward.scale(controlDepth).add(right.scale(x * controlDepth))
                    .add(up.scale(y * controlDepth));
        }
        return new Curve(from, control, to);
    }

    private static double between(double value, double a, double b) {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), value));
    }

    record Curve(Vec3 from, Vec3 control, Vec3 to) {
        Vec3 point(double t) {
            return from.lerp(control, t).lerp(control.lerp(to, t), t);
        }
    }
}
