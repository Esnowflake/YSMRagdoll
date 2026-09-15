package com.ysmragdoll.client.physics;

import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.ysmragdoll.config.YsmRagdollConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.Before;
import org.junit.Test;
import javax.vecmath.Vector3f;
import java.lang.reflect.Field;
import java.util.List;
import static org.junit.Assert.*;

public class BlockCollisionCacheTest {
    private ClientPhysicsWorld physics;
    private BlockCollisionCache cache;

    @Before
    public void setup() throws Exception {
        SharedConstants.tryDetectVersion();
        // Plain JUnit has no Forge event-bus bytecode transformer. Only enable vanilla
        // registry construction here; a full Bootstrap would initialize transformed networking.
        Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        net.minecraft.core.registries.BuiltInRegistries.REGISTRY.keySet();
        CommentedConfig config = CommentedConfig.inMemory();
        YsmRagdollConfig.SPEC.correct(config);
        YsmRagdollConfig.SPEC.setConfig(config);
        physics = new ClientPhysicsWorld();
        cache = new BlockCollisionCache(physics, new Vector3f());
    }

    @Test
    public void grabSweepRefreshesBeyondTickCoverageAndRejectsUnloadedPositions() throws Exception {
        TestBlocks level = new TestBlocks(new CountingBlock(false, Shapes.block()).defaultBlockState());
        Transform pose = new Transform();
        pose.setIdentity();
        pose.origin.set(20.5F, 4.5F, 20.5F);
        var body = new RigidBody(new RigidBodyConstructionInfo(1, new DefaultMotionState(pose),
                new BoxShape(new Vector3f(0.1F, 0.1F, 0.1F)), new Vector3f(1, 1, 1)));
        assertTrue(cache.prepareSweep(level, pos -> true, List.of(body), new Vector3f(0.6F, 0, 0)));
        assertEquals(2, bodies());
        int queries = level.queries;
        assertFalse(cache.prepareSweep(level, pos -> false, List.of(body), new Vector3f(0.6F, 0, 0)));
        assertEquals(queries, level.queries);
        cache.clear();
    }

    @Test
    public void placementDetectedBetweenTicksKeepsItsProtection() throws Exception {
        TestBlocks level = new TestBlocks(new CountingBlock(false, Shapes.empty()).defaultBlockState());
        cache.refresh(level, BlockPos.ZERO);
        level.state = new CountingBlock(false, Shapes.block()).defaultBlockState();
        cache.refresh(level, BlockPos.ZERO);
        cache.update(null, List.of());
        assertEquals(1, ((List<?>) get(cache, "placementProtections")).size());
        assertTrue(((List<?>) get(cache, "newlyFilledBoxes")).isEmpty());
        cache.clear();
    }

    @Test
    public void overlappingRegionsQueryEachLoadedPositionOnlyOnce() {
        TestBlocks level = new TestBlocks(new CountingBlock(false, Shapes.empty()).defaultBlockState());
        cache.scanRegion(level, BlockPos.ZERO, position -> true);
        assertEquals(441, level.queries);
        cache.scanRegion(level, BlockPos.ZERO, position -> true);
        assertEquals(441, level.queries);
        cache.scanRegion(level, new BlockPos(1, 0, 0), position -> true);
        assertEquals(504, level.queries);
        cache.scanRegion(level, new BlockPos(100, 0, 0), position -> false);
        assertEquals(504, level.queries);
        cache.clear();
    }

    @Test
    public void emptyShapeIsCachedUntilStateChanges() throws Exception {
        CountingBlock air = new CountingBlock(false, Shapes.empty());
        TestBlocks level = new TestBlocks(air.defaultBlockState());
        cache.refresh(level, BlockPos.ZERO);
        int calls = air.queries;
        for (int i = 0; i < 100; i++) cache.refresh(level, BlockPos.ZERO);
        assertEquals(calls, air.queries);
        assertEquals(0, bodies());

        level.state = new CountingBlock(false, Shapes.block()).defaultBlockState();
        cache.refresh(level, BlockPos.ZERO);
        assertEquals(1, bodies());
        assertEquals(1, ((List<?>) get(cache, "newlyFilledBoxes")).size());
        level.state = air.defaultBlockState();
        cache.refresh(level, BlockPos.ZERO);
        assertEquals(0, bodies());
        cache.clear();
    }

    @Test
    public void dynamicShapeRefreshesWithoutStateChangeAndReusesUnchangedBodies() throws Exception {
        CountingBlock moving = new CountingBlock(true, Shapes.empty());
        TestBlocks level = new TestBlocks(moving.defaultBlockState());
        cache.refresh(level, BlockPos.ZERO);
        moving.shape = Shapes.block();
        cache.refresh(level, BlockPos.ZERO);
        assertEquals(1, bodies());
        Object body = world().getCollisionObjectArray().getQuick(0);
        cache.refresh(level, BlockPos.ZERO);
        assertSame(body, world().getCollisionObjectArray().getQuick(0));
        moving.shape = Shapes.empty();
        cache.refresh(level, BlockPos.ZERO);
        assertEquals(0, bodies());
        cache.clear();
    }

    @Test
    public void leavingAllCoverageReleasesStaticBodiesAndCachedEmptyEntries() throws Exception {
        TestBlocks level = new TestBlocks(new CountingBlock(false, Shapes.block()).defaultBlockState());
        cache.refresh(level, BlockPos.ZERO);
        level.state = new CountingBlock(false, Shapes.empty()).defaultBlockState();
        cache.refresh(level, new BlockPos(1, 0, 0));
        cache.update(null, List.of());
        assertEquals(0, bodies());
        assertTrue(((java.util.Map<?, ?>) get(cache, "entries")).isEmpty());
    }

    private int bodies() throws Exception { return world().getNumCollisionObjects(); }
    private DiscreteDynamicsWorld world() throws Exception {
        return (DiscreteDynamicsWorld) get(physics, "world");
    }
    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class CountingBlock extends Block {
        private VoxelShape shape;
        private int queries;
        CountingBlock(boolean dynamic, VoxelShape shape) {
            super(dynamic ? Properties.of().dynamicShape() : Properties.of());
            this.shape = shape;
        }
        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                             CollisionContext context) {
            queries++;
            return shape;
        }
    }

    private static final class TestBlocks implements BlockGetter {
        private BlockState state;
        private int queries;
        TestBlocks(BlockState state) { this.state = state; }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public BlockState getBlockState(BlockPos pos) { queries++; return state; }
        public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }
        public int getHeight() { return 384; }
        public int getMinBuildHeight() { return -64; }
    }
}
