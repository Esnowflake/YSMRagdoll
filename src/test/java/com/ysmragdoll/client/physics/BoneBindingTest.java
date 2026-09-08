package com.ysmragdoll.client.physics;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.linearmath.Transform;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.ysmragdoll.client.OpenYsmModelAdapter;
import com.ysmragdoll.config.YsmRagdollConfig;
import com.ysmragdoll.network.PlayerDeathSnapshot;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.Assert.*;

public class BoneBindingTest {
    private static final float EPS = 0.0003F;

    @Test
    public void pixelTranslationRotationAndSignedNonuniformScaleRoundTrip() {
        Random random = new Random(1782);
        for (int i = 0; i < 100; i++) {
            float[] captured = parameters(1);
            captured[0] = random.nextFloat() * 2 - 1;
            captured[1] = random.nextFloat() * 2 - 1;
            captured[2] = random.nextFloat() * 2 - 1;
            captured[3] = 7; captured[4] = -11; captured[5] = 3;
            captured[6] = i % 2 == 0 ? -0.7F : 0.7F;
            captured[7] = 1.8F; captured[8] = 1.2F;
            captured[9] = 42;
            Vector3f pivot = new Vector3f(-5, 23, 2);
            Matrix4f target = rendererLocal(pivot, captured, 0);
            float[] output = captured.clone();
            BonePoseMath.writeLocal(target, pivot, new Vector3f(0.4F, 1.1F, -0.2F), output, captured, 0);
            assertMatrix(target, rendererLocal(pivot, output, 0));
            assertEquals(42, output[9], 0);
            assertEquals(captured[3], output[3], EPS);
            assertEquals(captured[4], output[4], EPS);
            assertEquals(captured[5], output[5], EPS);
        }
    }

    @Test
    public void physicalCentersAndVerticesFollowIndependentLimbRotations() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.verify(true);
            fixture.moveBodies();
            fixture.verify(true);
            fixture.ragdoll.correctGroundPenetration(0.2F);
            fixture.verify(true);
            fixture.ragdoll.translateCollisionWorld(new javax.vecmath.Vector3f(0.5F, -0.25F, 0.7F));
            fixture.verify(true);
        }
    }

    @Test
    public void nonuniformParentKeepsChildCentersBoundAndHiddenBonesHidden() throws Exception {
        try (Fixture fixture = new Fixture(true)) {
            fixture.verify(true);
            fixture.moveBodies();
            // A sheared target cannot be represented by YSM TRS, but its center must stay bound.
            fixture.verify(false);
        }
    }

    @Test
    public void collisionExtentsIncludeRootAndBoneScale() {
        Matrix4f model = new Matrix4f().translation(13, 64, -8).rotateY(0.7F).scale(-2, 3, 4);
        Vector3f center = new Vector3f(0.2F, 1.2F, -0.3F);
        Vector3f worldCenter = model.transformPosition(new Vector3f(center));
        Transform body = new Transform();
        body.setIdentity();
        body.basis.rotY(0.7F);
        body.origin.set(worldCenter.x, worldCenter.y, worldCenter.z);
        Matrix4f binding = BonePoseMath.bind(body, model);
        Vector3f half = BonePoseMath.halfExtents(binding, new Vector3f(0.1F, 0.2F, 0.3F));
        assertVector(new Vector3f(0.2F, 0.6F, 1.2F), half);
        assertVector(new Vector3f(), binding.transformPosition(new Vector3f(center)));
    }

    /** Independent transcription of OpenYSM NativeModelRenderer.calculateBoneMatrix. */
    private static Matrix4f rendererLocal(Vector3f pivot, float[] p, int bone) {
        int i = bone * 12;
        Matrix4f matrix = new Matrix4f();
        matrix.translate((pivot.x - p[i + 3]) * 0.0625F,
                (pivot.y + p[i + 4]) * 0.0625F, (pivot.z + p[i + 5]) * 0.0625F);
        matrix.rotateZ(p[i + 2]); matrix.rotateY(p[i + 1]); matrix.rotateX(p[i]);
        matrix.scale(p[i + 6], p[i + 7], p[i + 8]);
        matrix.translate(-pivot.x / 16, -pivot.y / 16, -pivot.z / 16);
        return matrix;
    }

    private static float[] parameters(int count) {
        float[] p = new float[count * 12];
        for (int i = 0; i < count; i++) p[i * 12 + 6] = p[i * 12 + 7] = p[i * 12 + 8] = 1;
        return p;
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, EPS);
        assertEquals(expected.y, actual.y, EPS);
        assertEquals(expected.z, actual.z, EPS);
    }

    private static void assertMatrix(Matrix4f expected, Matrix4f actual) {
        for (Vector3f point : List.of(new Vector3f(), new Vector3f(1, 0, 0),
                new Vector3f(0, 1, 0), new Vector3f(0, 0, 1))) {
            assertVector(expected.transformPosition(new Vector3f(point)),
                    actual.transformPosition(new Vector3f(point)));
        }
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static Object component(Object record, String name) throws Exception {
        Method method = record.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(record);
    }

    private static final class Fixture implements AutoCloseable {
        final float[] pose = parameters(6);
        final float[] captured;
        final List<RagdollDefinition.Bone> bones = new ArrayList<>();
        final Matrix4f root = new Matrix4f().translate(0.1F, 1.5F, -0.2F)
                .rotateY(0.6F).scale(-1.3F, 1.3F, 1.3F);
        final PlayerDeathSnapshot death = new PlayerDeathSnapshot(1, UUID.randomUUID(),
                10, 64, -12, 0, 0, 0, 33);
        final ClientPhysicsWorld world;
        final PhysicsRagdoll ragdoll;
        final Map<?, ?> parts;
        final Map<Integer, Matrix4f> expectedBinding = new HashMap<>();

        Fixture(boolean nonuniform) throws Exception {
            System.setProperty("ysmragdoll.projectDir", "build/binding-test");
            CommentedConfig config = CommentedConfig.inMemory();
            YsmRagdollConfig.SPEC.correct(config);
            YsmRagdollConfig.SPEC.setConfig(config);
            YsmRagdollConfig.COLLISION_OFFSET_X.set(0.25);
            YsmRagdollConfig.COLLISION_OFFSET_Y.set(-0.1);
            for (int i = 0; i < 5; i++) {
                pose[i * 12] = 0.12F * i;
                pose[i * 12 + 1] = -0.08F * i;
                pose[i * 12 + 2] = 0.06F * i;
                pose[i * 12 + 3] = 0.7F * i;
                pose[i * 12 + 4] = -0.4F * i;
                pose[i * 12 + 5] = 0.3F * i;
            }
            if (nonuniform) { pose[18] = 0.8F; pose[19] = 1.5F; pose[20] = 1.1F; }
            pose[66] = pose[67] = pose[68] = 0;
            captured = pose.clone();
            int[] parents = {-1, 0, 1, 2, 3, 3};
            for (int i = 0; i < 6; i++) {
                Vector3f pivot = new Vector3f(i * -2, 18 - i * 2, i);
                Matrix4f global = parents[i] < 0 ? new Matrix4f()
                        : new Matrix4f(bones.get(parents[i]).initialGlobal());
                global.mul(rendererLocal(pivot, pose, i));
                bones.add(new RagdollDefinition.Bone(i, "bone" + i, parents[i], pivot,
                        new Vector3f(-0.3F, 0.6F, -0.1F), new Vector3f(0.1F, 1.1F, 0.2F), global));
            }
            Map<RagdollDefinition.Role, RagdollDefinition.Part> definitions = new EnumMap<>(RagdollDefinition.Role.class);
            int[] indices = {1, 3, 4};
            RagdollDefinition.Role[] roles = {RagdollDefinition.Role.BODY,
                    RagdollDefinition.Role.LEFT_UPPER_ARM, RagdollDefinition.Role.LEFT_FOREARM};
            for (int i = 0; i < indices.length; i++) {
                definitions.put(roles[i], new RagdollDefinition.Part(roles[i], indices[i],
                        bones.get(indices[i]).center(), new Vector3f(0.19F, 0.2375F, 0.1425F), 2));
            }
            world = new ClientPhysicsWorld();
            Constructor<PhysicsRagdoll> constructor = PhysicsRagdoll.class.getDeclaredConstructor(
                    ClientPhysicsWorld.class, PlayerDeathSnapshot.class,
                    OpenYsmModelAdapter.PhysicsModelView.class, RagdollDefinition.class);
            constructor.setAccessible(true);
            ragdoll = constructor.newInstance(world, death,
                    new OpenYsmModelAdapter.PhysicsModelView(new Object(), pose, root, List.of()),
                    new RagdollDefinition(bones, definitions));
            parts = (Map<?, ?>) field(ragdoll, "bodies");
            // Derive the expected binding independently from the captured world pose and
            // constructor's uniform floor correction, rather than reading boneToBody.
            for (Object part : parts.values()) {
                RagdollDefinition.Part definition = (RagdollDefinition.Part) component(part, "part");
                Transform initial = ((RigidBody) component(part, "body")).getWorldTransform(new Transform());
                Matrix4f original = new Matrix4f().translation(10.25F, 63.9F, -12).mul(root)
                        .mul(bones.get(definition.boneIndex()).initialGlobal());
                Vector3f originalCenter = original.transformPosition(new Vector3f(definition.center()));
                original.m31(original.m31() + initial.origin.y - originalCenter.y);
                expectedBinding.put(definition.boneIndex(), BonePoseMath.rigid(initial).invert().mul(original));
            }
        }

        void moveBodies() throws Exception {
            int i = 1;
            for (Object part : parts.values()) {
                RigidBody body = (RigidBody) component(part, "body");
                Transform transform = body.getWorldTransform(new Transform());
                transform.basis.rotX(i * 0.4F);
                javax.vecmath.Matrix3f yaw = new javax.vecmath.Matrix3f();
                yaw.rotZ(-i * 0.3F); transform.basis.mul(yaw);
                transform.origin.add(new javax.vecmath.Vector3f(i * 0.1F, -i * 0.15F, i * 0.2F));
                body.setWorldTransform(transform);
                i++;
            }
        }

        void verify(boolean vertices) throws Exception {
            ragdoll.writePose();
            var displacement = ragdoll.renderDisplacement();
            Matrix4f base = new Matrix4f().translation((float) (death.x() + displacement.x),
                    (float) (death.y() + displacement.y), (float) (death.z() + displacement.z)).mul(root);
            Matrix4f[] globals = new Matrix4f[bones.size()];
            for (int i = 0; i < bones.size(); i++) {
                var bone = bones.get(i);
                globals[i] = new Matrix4f(bone.parentIndex() < 0 ? base : globals[bone.parentIndex()])
                        .mul(rendererLocal(bone.pivot(), pose, i));
            }
            for (Object part : parts.values()) {
                var definition = (RagdollDefinition.Part) component(part, "part");
                Transform transform = ((RigidBody) component(part, "body")).getWorldTransform(new Transform());
                Matrix4f actual = globals[definition.boneIndex()];
                assertVector(new Vector3f(transform.origin.x, transform.origin.y, transform.origin.z),
                        actual.transformPosition(new Vector3f(definition.center())));
                if (vertices) {
                    assertMatrix(BonePoseMath.rigid(transform).mul(expectedBinding.get(definition.boneIndex())), actual);
                }
            }
            for (int index : new int[]{0, 2, 5}) {
                for (int p = 0; p < 12; p++) assertEquals(captured[index * 12 + p], pose[index * 12 + p], 0);
            }
        }

        @Override public void close() {
            ragdoll.dispose(); world.clear();
            YsmRagdollConfig.COLLISION_OFFSET_X.set(0.0);
            YsmRagdollConfig.COLLISION_OFFSET_Y.set(0.0);
        }
    }
}
