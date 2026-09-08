package com.ysmragdoll.client.physics;

import com.bulletphysics.collision.narrowphase.ConvexCast;
import com.bulletphysics.collision.narrowphase.DiscreteCollisionDetectorInterface;
import com.bulletphysics.collision.narrowphase.GjkEpaPenetrationDepthSolver;
import com.bulletphysics.collision.narrowphase.GjkPairDetector;
import com.bulletphysics.collision.narrowphase.PointCollector;
import com.bulletphysics.collision.narrowphase.SubsimplexConvexCast;
import com.bulletphysics.collision.narrowphase.VoronoiSimplexSolver;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.ConvexShape;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.linearmath.Transform;

import javax.vecmath.Vector3f;
import java.util.List;
import java.util.function.Consumer;

/** Full push response using Bullet's convex sweeps and penetration queries. */
final class PlayerCollisionResponse {
    private static final float SEPARATION = 0.003F;
    /**
     * A ragdoll is a constrained group. Repeating a large correction against
     * alternating limb normals in one substep injects energy into the joints.
     */
    private static final int MAX_CORRECTIONS = 4;
    /** Maximum group translation used to catch a fast limb crossing the player. */
    private static final float MAX_BODY_SWEEP_DISTANCE = 1.5F;
    /** Keep one deeply intersecting contact from teleporting the whole ragdoll. */
    private static final float MAX_CONTACT_CORRECTION = 0.35F;
    private static final float MAX_GROUP_SEPARATION = 1.25F;
    private final List<RigidBody> bodies;
    private final Consumer<RigidBody> updateAabb;
    private final VoronoiSimplexSolver simplex = new VoronoiSimplexSolver();
    private final GjkEpaPenetrationDepthSolver penetration = new GjkEpaPenetrationDepthSolver();
    private final GjkPairDetector detector = new GjkPairDetector();
    private final DiscreteCollisionDetectorInterface.ClosestPointInput input =
            new DiscreteCollisionDetectorInterface.ClosestPointInput();
    private final Transform bodyTransform = new Transform();
    private final Vector3f minimum = new Vector3f();
    private final Vector3f maximum = new Vector3f();
    private final Vector3f playerMinimum = new Vector3f();
    private final Vector3f playerMaximum = new Vector3f();
    private final Transform[] beforeStep;
    private boolean stepCaptured;

    PlayerCollisionResponse(List<RigidBody> bodies, Consumer<RigidBody> updateAabb) {
        this.bodies = List.copyOf(bodies);
        this.updateAabb = updateAabb;
        beforeStep = new Transform[bodies.size()];
        for (int index = 0; index < beforeStep.length; index++) {
            beforeStep[index] = new Transform();
        }
    }

    void captureBeforeStep() {
        for (int index = 0; index < bodies.size(); index++) {
            bodies.get(index).getWorldTransform(beforeStep[index]);
        }
        stepCaptured = true;
    }

    boolean resolve(BoxShape playerShape, Transform from, Transform to,
                    Vector3f playerVelocity, boolean sweep) {
        Vector3f movement = new Vector3f();
        movement.sub(to.origin, from.origin);
        boolean moved = sweep && sweep(playerShape, from, to, movement, playerVelocity);
        if (stepCaptured) {
            stepCaptured = false;
            moved |= sweepBodies(playerShape, to, playerVelocity);
        }
        playerShape.getAabb(to, playerMinimum, playerMaximum);
        for (int iteration = 0; iteration < MAX_CORRECTIONS; iteration++) {
            PointCollector contact = deepestContact(playerShape, to);
            if (contact == null) {
                return moved;
            }
            Vector3f normal = responseNormal(contact.normalOnBInWorld, movement, playerVelocity);
            if (normal == null) {
                // A stationary player inside a vertical-only contact has no
                // meaningful push direction. The group fallback below picks a
                // deterministic horizontal escape direction.
                break;
            }
            Vector3f correction = new Vector3f(normal);
            correction.scale(SEPARATION - contact.distance);
            clampLength(correction, MAX_CONTACT_CORRECTION);
            translate(correction, normal, playerVelocity);
            moved = true;
        }
        // A player can start inside several limbs with opposing normals. Move the
        // connected group to one side instead of oscillating or stretching joints.
        if (deepestContact(playerShape, to) != null) {
            separateGroupHorizontally(to, movement, playerVelocity);
        }
        return moved;
    }

    private boolean sweepBodies(BoxShape playerShape, Transform player, Vector3f playerVelocity) {
        float fraction = 1.0F;
        Vector3f normal = new Vector3f();
        Vector3f correction = new Vector3f();
        Vector3f movement = new Vector3f();
        for (int index = 0; index < bodies.size(); index++) {
            RigidBody body = bodies.get(index);
            body.getWorldTransform(bodyTransform);
            movement.sub(bodyTransform.origin, beforeStep[index].origin);
            if (movement.lengthSquared() < 1.0E-10F) {
                continue;
            }
            ConvexCast.CastResult hit = new ConvexCast.CastResult();
            hit.fraction = 1.0F;
            SubsimplexConvexCast cast = new SubsimplexConvexCast(
                    (ConvexShape) body.getCollisionShape(), playerShape, simplex);
            if (cast.calcTimeOfImpact(beforeStep[index], bodyTransform, player, player, hit)
                    && hit.fraction >= 0.0F && hit.fraction < fraction
                    && hit.normal.dot(movement) < -1.0E-6F) {
                fraction = hit.fraction;
                normal.set(hit.normal);
                correction.scale(fraction - 1.0F, movement);
            }
        }
        if (fraction >= 1.0F) {
            return false;
        }
        Vector3f resolvedNormal = responseNormal(normal, movement, playerVelocity);
        if (resolvedNormal == null) {
            return false;
        }
        normal.set(resolvedNormal);
        correction.scaleAdd(SEPARATION, normal, correction);
        clampLength(correction, MAX_BODY_SWEEP_DISTANCE);
        translate(correction, normal, playerVelocity);
        return true;
    }

    private boolean sweep(BoxShape playerShape, Transform from, Transform to,
                          Vector3f movement, Vector3f playerVelocity) {
        if (movement.lengthSquared() < 1.0E-10F) {
            return false;
        }
        float fraction = 1.0F;
        Vector3f normal = new Vector3f();
        for (RigidBody body : bodies) {
            body.getWorldTransform(bodyTransform);
            ConvexCast.CastResult hit = new ConvexCast.CastResult();
            hit.fraction = 1.0F;
            SubsimplexConvexCast cast = new SubsimplexConvexCast(playerShape,
                    (ConvexShape) body.getCollisionShape(), simplex);
            if (cast.calcTimeOfImpact(from, to, bodyTransform, bodyTransform, hit)
                    && hit.fraction >= 0.0F && hit.fraction < fraction
                    && hit.normal.dot(movement) < -1.0E-6F) {
                fraction = hit.fraction;
                normal.negate(hit.normal);
            }
        }
        if (fraction >= 1.0F) {
            return false;
        }
        Vector3f resolvedNormal = responseNormal(normal, movement, playerVelocity);
        if (resolvedNormal == null) {
            return false;
        }
        normal.set(resolvedNormal);
        Vector3f correction = new Vector3f(movement);
        correction.scale(1.0F - fraction);
        correction.scaleAdd(SEPARATION, normal, correction);
        translate(correction, normal, playerVelocity);
        return true;
    }

    private PointCollector deepestContact(BoxShape playerShape, Transform playerTransform) {
        PointCollector deepest = null;
        for (RigidBody body : bodies) {
            body.getWorldTransform(bodyTransform);
            body.getCollisionShape().getAabb(bodyTransform, minimum, maximum);
            if (minimum.x >= playerMaximum.x || maximum.x <= playerMinimum.x
                    || minimum.y >= playerMaximum.y || maximum.y <= playerMinimum.y
                    || minimum.z >= playerMaximum.z || maximum.z <= playerMinimum.z) {
                continue;
            }
            input.init();
            input.transformA.set(bodyTransform);
            input.transformB.set(playerTransform);
            input.maximumDistanceSquared = 0.0F;
            detector.init((ConvexShape) body.getCollisionShape(), playerShape, simplex, penetration);
            PointCollector contact = new PointCollector();
            detector.getClosestPoints(input, contact, null);
            if (contact.hasResult && contact.distance < 0.0F
                    && (deepest == null || contact.distance < deepest.distance)) {
                deepest = contact;
            }
        }
        return deepest;
    }

    private void separateGroupHorizontally(Transform player, Vector3f movement,
                                           Vector3f playerVelocity) {
        Vector3f groupMinimum = new Vector3f(Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        Vector3f groupMaximum = new Vector3f(Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        for (RigidBody body : bodies) {
            body.getWorldTransform(bodyTransform);
            body.getCollisionShape().getAabb(bodyTransform, minimum, maximum);
            groupMinimum.x = Math.min(groupMinimum.x, minimum.x);
            groupMinimum.z = Math.min(groupMinimum.z, minimum.z);
            groupMaximum.x = Math.max(groupMaximum.x, maximum.x);
            groupMaximum.z = Math.max(groupMaximum.z, maximum.z);
        }
        float dx = movement.x;
        float dz = movement.z;
        if (dx * dx + dz * dz < 1.0E-8F) {
            dx = (groupMinimum.x + groupMaximum.x) * 0.5F - player.origin.x;
            dz = (groupMinimum.z + groupMaximum.z) * 0.5F - player.origin.z;
        }
        Vector3f normal = new Vector3f();
        Vector3f correction = new Vector3f();
        if (Math.abs(dx) >= Math.abs(dz)) {
            normal.x = dx >= 0.0F ? 1.0F : -1.0F;
            correction.x = dx >= 0.0F ? playerMaximum.x - groupMinimum.x + SEPARATION
                    : playerMinimum.x - groupMaximum.x - SEPARATION;
        } else {
            normal.z = dz >= 0.0F ? 1.0F : -1.0F;
            correction.z = dz >= 0.0F ? playerMaximum.z - groupMinimum.z + SEPARATION
                    : playerMinimum.z - groupMaximum.z - SEPARATION;
        }
        clampLength(correction, MAX_GROUP_SEPARATION);
        translate(correction, normal, playerVelocity);
    }

    private static Vector3f responseNormal(Vector3f contactNormal, Vector3f fallbackMovement,
                                           Vector3f playerVelocity) {
        Vector3f normal = new Vector3f(contactNormal);
        // A horizontal walk should never turn a floor contact into an upward
        // impulse for the ragdoll. Keep vertical response for an actual jump.
        if (Math.abs(normal.y) > Math.abs(normal.x) + Math.abs(normal.z)
                && (playerVelocity == null || Math.abs(playerVelocity.y) < 0.05F)) {
            normal.y = 0.0F;
        }
        if (normal.lengthSquared() < 1.0E-8F && fallbackMovement != null) {
            normal.set(fallbackMovement);
        }
        if (normal.lengthSquared() < 1.0E-8F) {
            return null;
        }
        normal.normalize();
        return normal;
    }

    private static void clampLength(Vector3f value, float maximum) {
        float lengthSquared = value.lengthSquared();
        if (lengthSquared > maximum * maximum) {
            value.scale(maximum / (float) Math.sqrt(lengthSquared));
        }
    }

    private void translate(Vector3f correction, Vector3f normal, Vector3f playerVelocity) {
        // Position correction may need a sloped contact normal, but a player
        // walking horizontally must not transfer that normal's upward
        // component to every constrained limb. Otherwise a foot or shoulder
        // contact can turn into a launch. Preserve vertical transfer only for
        // an actual vertical player movement (jump/fall).
        Vector3f velocityNormal = new Vector3f(normal);
        if (Math.abs(playerVelocity.y) < 0.05F) {
            velocityNormal.y = 0.0F;
        }
        float velocityNormalLengthSquared = velocityNormal.lengthSquared();
        float minimumSpeed = velocityNormalLengthSquared < 1.0E-8F
                ? 0.0F : Math.max(0.0F, playerVelocity.dot(velocityNormal)
                / (float) Math.sqrt(velocityNormalLengthSquared));
        if (velocityNormalLengthSquared >= 1.0E-8F) {
            velocityNormal.scale(1.0F / (float) Math.sqrt(velocityNormalLengthSquared));
        }
        Vector3f velocity = new Vector3f();
        for (RigidBody body : bodies) {
            body.getWorldTransform(bodyTransform);
            bodyTransform.origin.add(correction);
            body.setWorldTransform(bodyTransform);
            body.setInterpolationWorldTransform(bodyTransform);
            if (body.getMotionState() != null) {
                body.getMotionState().setWorldTransform(bodyTransform);
            }
            body.getLinearVelocity(velocity);
            float missingSpeed = velocityNormalLengthSquared < 1.0E-8F ? 0.0F
                    : minimumSpeed - velocity.dot(velocityNormal);
            if (missingSpeed > 0.0F) {
                velocity.scaleAdd(missingSpeed, velocityNormal, velocity);
                body.setLinearVelocity(velocity);
            }
            body.activate(true);
            updateAabb.accept(body);
        }
    }
}
