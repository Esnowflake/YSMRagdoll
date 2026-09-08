package com.ysmragdoll.client.physics;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

/** YSM 物理骨架：pivot 使用像素，几何边界、center 和 halfExtents 使用方块。 */
public record RagdollDefinition(List<Bone> bones, Map<Role, Part> parts) {
    public enum Role {
        BODY, HEAD,
        LEFT_UPPER_ARM, RIGHT_UPPER_ARM,
        LEFT_FOREARM, RIGHT_FOREARM,
        LEFT_HAND, RIGHT_HAND,
        LEFT_THIGH, RIGHT_THIGH,
        LEFT_SHIN, RIGHT_SHIN,
        LEFT_FOOT, RIGHT_FOOT
    }

    public record Bone(int index, String name, int parentIndex, Vector3f pivot,
                       Vector3f minimum, Vector3f maximum, Matrix4f initialGlobal) {
        public boolean hasGeometry() {
            return Float.isFinite(minimum.x) && Float.isFinite(maximum.x);
        }

        public Vector3f center() {
            return hasGeometry()
                    ? new Vector3f(minimum).add(maximum).mul(0.5F)
                    : new Vector3f(pivot).mul(1.0F / 16.0F);
        }

        public Vector3f sizeInBlocks() {
            if (!hasGeometry()) {
                return new Vector3f(0.25F, 0.25F, 0.25F);
            }
            return new Vector3f(maximum).sub(minimum);
        }
    }

    public record Part(Role role, int boneIndex, Vector3f center,
                       Vector3f halfExtents, float mass) {
    }
}
