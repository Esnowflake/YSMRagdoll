package com.ysmragdoll.client.physics;

import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.vecmath.Vector3f;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class FluidBuoyancyTest {
    private static final float STEP = 1F / 120;

    @BeforeAll
    public static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        // Same registry-only bootstrap as BlockCollisionCacheTest; no Forge transformer in plain JUnit.
        Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        net.minecraft.core.registries.BuiltInRegistries.REGISTRY.keySet();
    }

    private static DiscreteDynamicsWorld world() {
        var config = new DefaultCollisionConfiguration();
        var world = new DiscreteDynamicsWorld(new CollisionDispatcher(config), new DbvtBroadphase(),
                new SequentialImpulseConstraintSolver(), config);
        world.setGravity(new Vector3f(0, -9.81F, 0));
        world.getSolverInfo().numIterations = 20;
        return world;
    }

    private static RigidBody body(DiscreteDynamicsWorld world, float mass, float y) {
        BoxShape shape = new BoxShape(new Vector3f(0.4F, 0.4F, 0.4F));
        Vector3f inertia = new Vector3f();
        shape.calculateLocalInertia(mass, inertia);
        Transform transform = new Transform();
        transform.setIdentity();
        transform.origin.set(0, y, 0);
        RigidBody body = new RigidBody(new RigidBodyConstructionInfo(mass,
                new DefaultMotionState(transform), shape, inertia));
        world.addRigidBody(body);
        return body;
    }

    @Test
    public void waterAndLavaRaiseLightAndHeavyPartsAndSettleAtSurface() {
        for (FluidState fluid : new FluidState[]{Fluids.WATER.defaultFluidState(), Fluids.LAVA.defaultFluidState()}) {
            for (float mass : new float[]{0.35F, 5F}) {
                var world = world();
                var body = body(world, mass, -3);
                body.setLinearVelocity(new Vector3f(2, -1, 0));
                var level = new LiquidLevel(fluid);
                var buoyancy = new FluidBuoyancy();
                for (int step = 0; step < 2400; step++) {
                    if (step % 6 == 0) buoyancy.beginTick(level, pos -> true, new Vector3f());
                    buoyancy.apply(body, STEP, false);
                    world.stepSimulation(STEP, 0, STEP);
                }
                float height = body.getWorldTransform(new Transform()).origin.y;
                assertTrue("Must rise and settle near surface, height=" + height, height > -0.7F && height < 0.2F);
                assertTrue("Must settle instead of oscillating indefinitely", body.getLinearVelocity(new Vector3f()).length() < 0.1F);
                level.fluid = Fluids.EMPTY.defaultFluidState();
                buoyancy.beginTick(level, pos -> true, new Vector3f());
                for (int step = 0; step < 120; step++) {
                    assertFalse(buoyancy.apply(body, STEP, false));
                    world.stepSimulation(STEP, 0, STEP);
                }
                assertTrue("Removing liquid restores gravity", body.getWorldTransform(new Transform()).origin.y < height - 3);
            }
        }
    }

    @Test
    public void connectedPartsFloatTogetherWithFreeJoints() {
        var world = world();
        var upper = body(world, 0.35F, -3);
        var lower = body(world, 5, -3.8F);
        Transform a = new Transform(); a.setIdentity(); a.origin.y = -0.4F;
        Transform b = new Transform(); b.setIdentity(); b.origin.y = 0.4F;
        var joint = new Generic6DofConstraint(upper, lower, a, b, true);
        joint.setLinearLowerLimit(new Vector3f());
        joint.setLinearUpperLimit(new Vector3f());
        world.addConstraint(joint, true);
        var level = new LiquidLevel(Fluids.WATER.defaultFluidState());
        var buoyancy = new FluidBuoyancy();
        float maximumGap = 0;
        for (int step = 0; step < 2400; step++) {
            if (step % 6 == 0) buoyancy.beginTick(level, pos -> true, new Vector3f());
            buoyancy.apply(upper, STEP, false);
            buoyancy.apply(lower, STEP, false);
            world.stepSimulation(STEP, 0, STEP);
            joint.calculateTransforms();
            Vector3f gap = joint.getCalculatedTransformA(new Transform()).origin;
            gap.sub(joint.getCalculatedTransformB(new Transform()).origin);
            maximumGap = Math.max(maximumGap, gap.length());
        }
        assertTrue(upper.getWorldTransform(new Transform()).origin.y > -0.8F);
        assertTrue(lower.getWorldTransform(new Transform()).origin.y > -1.6F);
        assertTrue("Buoyancy should not pull joints apart: " + maximumGap, maximumGap < 0.02F);
    }

    @Test
    public void samplesFluidHeightAndRefreshesCacheWithoutLoadingMissingChunks() {
        var level = new LiquidLevel(Fluids.WATER.defaultFluidState());
        var buoyancy = new FluidBuoyancy();
        Vector3f min = new Vector3f(0.1F, -0.5F, 0.1F);
        Vector3f max = new Vector3f(0.9F, 0.5F, 0.9F);
        buoyancy.beginTick(level, pos -> true, new Vector3f());
        assertEquals(0.388889F, buoyancy.sample(min, max).fraction(), 0.0001F);
        int reads = level.reads;
        buoyancy.sample(min, max);
        assertEquals(reads, level.reads);
        level.fluid = Fluids.FLOWING_WATER.getFlowing(4, false);
        buoyancy.beginTick(level, pos -> true, new Vector3f());
        assertEquals(4F / 9, buoyancy.sample(new Vector3f(0.1F, -1, 0.1F),
                new Vector3f(0.9F, 0, 0.9F)).fraction(), 0.0001F);
        level.fluid = Fluids.LAVA.defaultFluidState();
        buoyancy.beginTick(level, pos -> true, new Vector3f());
        var lava = buoyancy.sample(min, max);
        assertEquals(lava.fraction(), lava.lavaFraction(), 0);
        buoyancy.beginTick(level, pos -> false, new Vector3f());
        reads = level.reads;
        assertEquals(0, buoyancy.sample(min, max).fraction(), 0);
        assertEquals(reads, level.reads);
        buoyancy.clear();
        assertEquals(0, buoyancy.sample(min, max).fraction(), 0);
    }

    @Test
    public void collisionOffsetAndGrabbedPartsStillReceiveBuoyancyWithoutFluidDrag() {
        var world = world();
        var body = body(world, 2, 17);
        body.setLinearVelocity(new Vector3f(4, 0, 0));
        var buoyancy = new FluidBuoyancy();
        buoyancy.beginTick(new LiquidLevel(Fluids.WATER.defaultFluidState()), pos -> true, new Vector3f(0, 20, 0));
        assertTrue(buoyancy.apply(body, STEP, true));
        Vector3f velocity = body.getLinearVelocity(new Vector3f());
        assertEquals(4, velocity.x, 0);
        assertTrue(velocity.y > 0);
    }

    private static final class LiquidLevel implements BlockGetter {
        FluidState fluid;
        int reads;
        LiquidLevel(FluidState fluid) { this.fluid = fluid; }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public BlockState getBlockState(BlockPos pos) { return Blocks.AIR.defaultBlockState(); }
        public FluidState getFluidState(BlockPos pos) {
            reads++;
            return pos.getY() < 0 ? fluid : Fluids.EMPTY.defaultFluidState();
        }
        public int getHeight() { return 384; }
        public int getMinBuildHeight() { return -64; }
    }
}
