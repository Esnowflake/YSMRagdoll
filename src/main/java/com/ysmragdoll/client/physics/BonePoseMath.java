package com.ysmragdoll.client.physics;

import com.bulletphysics.linearmath.Transform;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** YSM 2.6.5: pivots/translations are pixels, vertices are blocks, rotations are Z/Y/X. */
final class BonePoseMath {
    private BonePoseMath() {}

    static Matrix4f local(Vector3f pivot, float[] parameters, int index) {
        int p = index * 12;
        return new Matrix4f().translate((pivot.x - parameters[p + 3]) / 16.0F,
                        (pivot.y + parameters[p + 4]) / 16.0F,
                        (pivot.z + parameters[p + 5]) / 16.0F)
                .rotateZ(parameters[p + 2]).rotateY(parameters[p + 1]).rotateX(parameters[p])
                .scale(parameters[p + 6], parameters[p + 7], parameters[p + 8])
                .translate(-pivot.x / 16.0F, -pivot.y / 16.0F, -pivot.z / 16.0F);
    }

    static Matrix4f rigid(Transform transform) {
        return new Matrix4f().set(
                transform.basis.m00, transform.basis.m10, transform.basis.m20, 0,
                transform.basis.m01, transform.basis.m11, transform.basis.m21, 0,
                transform.basis.m02, transform.basis.m12, transform.basis.m22, 0,
                transform.origin.x, transform.origin.y, transform.origin.z, 1);
    }

    /** Preserve the entire bind transform, including root scale and handedness. */
    static Matrix4f bind(Transform body, Matrix4f boneWorld) {
        return rigid(body).invert().mul(boneWorld);
    }

    /** Fit the model-space collision box in the orthonormal rigid body's coordinates. */
    static Vector3f halfExtents(Matrix4f boneToBody, Vector3f half) {
        return new Vector3f(
                Math.abs(boneToBody.m00()) * half.x + Math.abs(boneToBody.m10()) * half.y + Math.abs(boneToBody.m20()) * half.z,
                Math.abs(boneToBody.m01()) * half.x + Math.abs(boneToBody.m11()) * half.y + Math.abs(boneToBody.m21()) * half.z,
                Math.abs(boneToBody.m02()) * half.x + Math.abs(boneToBody.m12()) * half.y + Math.abs(boneToBody.m22()) * half.z);
    }

    /**
     * Decompose to YSM's TRS parameters. A rotated nonuniform parent can introduce shear,
     * which YSM's parameter array cannot represent. Keep the physical geometry center exact
     * even then, and use the closest quaternion orientation for the remaining shape.
     */
    static void writeLocal(Matrix4f target, Vector3f pivotPixels, Vector3f center,
                           float[] parameters, float[] captured, int index) {
        int p = index * 12;
        Vector3f scale = target.getScale(new Vector3f());
        scale.x = Math.copySign(scale.x, captured[p + 6]);
        scale.y = Math.copySign(scale.y, captured[p + 7]);
        scale.z = Math.copySign(scale.z, captured[p + 8]);
        if (Math.abs(scale.x * scale.y * scale.z) < 1.0E-12F) return;
        if (target.determinant3x3() * scale.x * scale.y * scale.z < 0) scale.z = -scale.z;
        Matrix3f rotation = target.get3x3(new Matrix3f());
        // JOML mXY: X is the column. Divide columns, not rows.
        rotation.m00(rotation.m00() / scale.x); rotation.m01(rotation.m01() / scale.x); rotation.m02(rotation.m02() / scale.x);
        rotation.m10(rotation.m10() / scale.y); rotation.m11(rotation.m11() / scale.y); rotation.m12(rotation.m12() / scale.y);
        rotation.m20(rotation.m20() / scale.z); rotation.m21(rotation.m21() / scale.z); rotation.m22(rotation.m22() / scale.z);
        // Read Z/Y/X from the rotation matrix explicitly. The bundled JOML 1.10.5
        // quaternion Euler helper does not round-trip these combined rotations reliably.
        new Quaternionf().setFromNormalized(rotation).normalize().get(rotation);
        float ry = (float) Math.asin(Math.max(-1.0F, Math.min(1.0F, -rotation.m02())));
        float rx;
        float rz;
        if (Math.abs(Math.cos(ry)) > 1.0E-5) {
            rx = (float) Math.atan2(rotation.m12(), rotation.m22());
            rz = (float) Math.atan2(rotation.m01(), rotation.m00());
        } else {
            rx = 0.0F;
            rz = (float) Math.atan2(-rotation.m10(), rotation.m11());
        }
        Vector3f euler = new Vector3f(rx, ry, rz);
        parameters[p] = euler.x;
        parameters[p + 1] = euler.y;
        parameters[p + 2] = euler.z;
        parameters[p + 6] = scale.x;
        parameters[p + 7] = scale.y;
        parameters[p + 8] = scale.z;

        Matrix4f linear = new Matrix4f().rotateZ(euler.z).rotateY(euler.y).rotateX(euler.x)
                .scale(scale);
        Vector3f pivot = new Vector3f(pivotPixels).div(16.0F);
        Vector3f d = target.transformPosition(new Vector3f(center))
                .sub(linear.transformDirection(new Vector3f(center).sub(pivot)));
        parameters[p + 3] = (pivot.x - d.x) * 16.0F;
        parameters[p + 4] = (d.y - pivot.y) * 16.0F;
        parameters[p + 5] = (d.z - pivot.z) * 16.0F;
    }
}
