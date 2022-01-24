package gpa.faster_minecarts.carts;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import gpa.faster_minecarts.FasterMinecarts;
import gpa.faster_minecarts.KinematicHelper;
import gpa.faster_minecarts.ServerConfig;
import gpa.faster_minecarts.carts.util.FloorType;
import gpa.faster_minecarts.carts.util.VanillaCartReplacer;
import net.minecraft.block.*;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraft.entity.item.minecart.MinecartEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.state.properties.RailShape;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.World;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

import static gpa.faster_minecarts.KinematicHelper.*;
import static java.lang.Math.*;
import static net.minecraft.state.properties.RailShape.ASCENDING_EAST;
import static net.minecraft.util.math.MathHelper.ceil;
import static net.minecraft.util.math.MathHelper.floor;


//To override a new minecart:
//1. copy this class with new name,
//2. change the super class
//3. call init() in the mod loader
//Yes, this is hacky, but its the only known way to do this
public final class MinecartEntityExt extends MinecartEntity {
    public static final double DIST_PER_POUND = 1/10d;
    private static final EntityType<MinecartEntityExt> TYPE;

    private static final double GRAVITY(){
        return 5/20d + -FloorType.NORMAL_RAIL.getAcc();
    };
    
    private double leftoverTime = 0;

    static {
        ResourceLocation resourceLocation = new ResourceLocation(
            FasterMinecarts.MOD_ID,
            MinecartEntityExt.class.getSimpleName().toLowerCase(Locale.US)
        );

        EntityType<MinecartEntityExt> result = EntityType.Builder.of(
                (EntityType.IFactory<MinecartEntityExt>) MinecartEntityExt::new,
            EntityClassification.MISC
        )
        .sized(0.98F, 0.7F)
        .clientTrackingRange(8)
        .build(
            resourceLocation.toString()
        );

        result.setRegistryName(resourceLocation);

        TYPE = result;
    }

    protected MinecartEntityExt(EntityType<?> type, World p_i48538_2_) {
        //The (EntityType<? extends MinecartEntityExt>) is there to make copying the class easier
        super((EntityType<? extends MinecartEntityExt>)type, p_i48538_2_);
    }

    protected MinecartEntityExt(AbstractMinecartEntity oldCart) {
        super(TYPE, oldCart.level);

        setPos(
            oldCart.position().x(),
            oldCart.position().y(),
            oldCart.position().z()
        );

        setDeltaMovement(
            oldCart.getDeltaMovement()
        );

        xRot = oldCart.xRot;
        yRot = oldCart.yRot;
    }

    public static void init() {
        ForgeRegistries.ENTITIES.register(TYPE);
        RenderingRegistry.registerEntityRenderingHandler(TYPE, MinecartRenderer::new);
        VanillaCartReplacer.add(MinecartEntityExt.class, MinecartEntityExt::new);
    }

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public BlockPos getCurrentRailPosition() {
        int x = floor(getMinecart().getX());
        int y = floor(getMinecart().getY());
        int z = floor(getMinecart().getZ());

        BlockPos pos = new BlockPos(x, y, z);

        if (RailBlock.isRail(level, pos)) {
            return pos;
        }

        return pos.below();
    }

    @Override
    protected void moveAlongTrack(BlockPos p_180460_1_, BlockState p_180460_2_) {
        moveOneTick();
    }

    @Override
    protected void comeOffTrack() {
        moveOneTick();
    }

    private void moveOneTick() {
        pushByPlayer();

        Set<BlockPos> blocksToActivate = new HashSet<>();
        BlockPos lastPos;

        double currentSpeed = getHSpeed(getDeltaMovement());
        double totalTime = 0;

        //1 unit of time is one tick
        //1 unit of distance is one block
        //each loop round moves the cart by a constant distance
        //the loop runs until either the cart stops, or the total time exceeds one tick
        while (true) {
            BlockPos pos = getCurrentRailPosition();
            blocksToActivate.add(pos);
            lastPos = pos;

            double acc = getAcc();

            //gets the time it takes to move the cart by the constant distance
            Double time = getTime(acc, currentSpeed, DIST_PER_POUND);
            if (time == null) {
                break;
            }

            totalTime += time;
            if (totalTime > 1 + leftoverTime) {
                leftoverTime = (1 + leftoverTime) - totalTime + time;
                break;
            }

            currentSpeed = getSpeed(currentSpeed, acc, time);
            if (currentSpeed == 0) {
                break;
            }

            moveOneDist();
            if (KinematicHelper.getHSpeed(getDeltaMovement()) == 0) {
                break;
            }
        }

        setDeltaMovement(setHSpeed(getDeltaMovement(), currentSpeed));

        for (BlockPos p: blocksToActivate){
            if (p.equals(lastPos)) continue;

            updateRail(p);
            updateTripWire(p);
        }

        //double dist = sqrt(pow(p1.x()-p2.x(),2)+pow(p1.z()-p2.z(),2));
    }

    private void pushByPlayer(){
        Entity entity = this.getPassengers().isEmpty() ? null : (Entity) this.getPassengers().get(0);

        if (entity instanceof PlayerEntity) {
            Vector3d vector3d2 = entity.getDeltaMovement();
            double d9 = getHorizontalDistanceSqr(vector3d2);
            double d11 = getHorizontalDistanceSqr(this.getDeltaMovement());
            if (d9 > 1.0E-4D && d11 < 0.01D) {
                this.setDeltaMovement(this.getDeltaMovement().add(vector3d2.x * 0.1D, 0.0D, vector3d2.z * 0.1D));
            }
        }
    }

    private double getAcc(){
        BlockPos pos = getCurrentRailPosition();

        BlockState block = level.getBlockState(pos);

        return FloorType.getFromBlock(block).getAcc() + getSlopeAcc(pos);
    }

    private double getSlopeAcc(BlockPos pos){
        if (!ServerConfig.useGravity) return 0;

        BlockState block = level.getBlockState(pos);

        if (block.getBlock() instanceof AbstractRailBlock) {
            RailShape railshape = ((AbstractRailBlock) block.getBlock()).getRailDirection(block, level, pos, this);
            switch (railshape) {
                case ASCENDING_EAST:
                    return -signum(getDeltaMovement().x())*GRAVITY();
                case ASCENDING_WEST:
                    return signum(getDeltaMovement().x())*GRAVITY();
                case ASCENDING_SOUTH:
                    return -signum(getDeltaMovement().z())*GRAVITY();
                case ASCENDING_NORTH:
                    return signum(getDeltaMovement().z())*GRAVITY();
            }
        }
        return 0;
    }

    private double getSpeed(double currentSpeed, double acc, double time){
        double result = currentSpeed + acc * time;

        if (result > ServerConfig.maxSpeed) {
            result = ServerConfig.maxSpeed;
        }
        else if (result < 0) {
            result = 0;
        }
        return result;
    }

    //moves the cart
    //all setDeltaMovement calls are replaced with _setDeltaMovement, which makes the speed always constant
    //copy and past of default motion code, but edited
    private void moveOneDist() {
        BlockPos pos = getCurrentRailPosition();
        BlockState blockState = level.getBlockState(pos);

        _setDeltaMovement(getDeltaMovement());

        if (blockState.getBlock() instanceof AbstractRailBlock){
            this.fallDistance = 0.0F;
            double d0 = this.getX();
            double d1 = this.getY();
            double d2 = this.getZ();
            Vector3d vector3d = this.getPos(d0, d1, d2);
            d1 = (double) pos.getY();
            boolean flag = false;
            boolean flag1 = false;
            AbstractRailBlock abstractrailblock = (AbstractRailBlock) blockState.getBlock();
            if (abstractrailblock instanceof PoweredRailBlock && !((PoweredRailBlock) abstractrailblock).isActivatorRail()) {
                flag = (Boolean) blockState.getValue(PoweredRailBlock.POWERED);
                flag1 = !flag;
            }

            double d3 = 0.0078125D;
            Vector3d vector3d1 = this.getDeltaMovement();
            RailShape railshape = ((AbstractRailBlock) blockState.getBlock()).getRailDirection(blockState, this.level, pos, this);
            switch (railshape) {
                case ASCENDING_EAST:
                    this._setDeltaMovement(vector3d1.add(-1.0D * this.getSlopeAdjustment(), 0.0D, 0.0D));
                    ++d1;
                    break;
                case ASCENDING_WEST:
                    this._setDeltaMovement(vector3d1.add(this.getSlopeAdjustment(), 0.0D, 0.0D));
                    ++d1;
                    break;
                case ASCENDING_NORTH:
                    this._setDeltaMovement(vector3d1.add(0.0D, 0.0D, this.getSlopeAdjustment()));
                    ++d1;
                    break;
                case ASCENDING_SOUTH:
                    this._setDeltaMovement(vector3d1.add(0.0D, 0.0D, -1.0D * this.getSlopeAdjustment()));
                    ++d1;
            }

            vector3d1 = this.getDeltaMovement();
            Pair<Vector3i, Vector3i> pair = exits(railshape);
            Vector3i vector3i = (Vector3i) pair.getFirst();
            Vector3i vector3i1 = (Vector3i) pair.getSecond();
            double d4 = (double) (vector3i1.getX() - vector3i.getX());
            double d5 = (double) (vector3i1.getZ() - vector3i.getZ());
            double d6 = Math.sqrt(d4 * d4 + d5 * d5);
            double d7 = vector3d1.x * d4 + vector3d1.z * d5;
            if (d7 < 0.0D) {
                d4 = -d4;
                d5 = -d5;
            }

            double d8 = Math.min(2.0D, Math.sqrt(getHorizontalDistanceSqr(vector3d1)));
            vector3d1 = new Vector3d(d8 * d4 / d6, vector3d1.y, d8 * d5 / d6);
            this._setDeltaMovement(vector3d1);
            Entity entity = this.getPassengers().isEmpty() ? null : (Entity) this.getPassengers().get(0);
            if (entity instanceof PlayerEntity) {
                Vector3d vector3d2 = entity.getDeltaMovement();
                double d9 = getHorizontalDistanceSqr(vector3d2);
                double d11 = getHorizontalDistanceSqr(this.getDeltaMovement());
                if (d9 > 1.0E-4D && d11 < 0.01D) {
                    this._setDeltaMovement(this.getDeltaMovement().add(vector3d2.x * 0.1D, 0.0D, vector3d2.z * 0.1D));
                    flag1 = false;
                }
            }

            double d23;
            if (flag1 && this.shouldDoRailFunctions()) {
                d23 = Math.sqrt(getHorizontalDistanceSqr(this.getDeltaMovement()));
                if (d23 < 0.03D) {
                    this._setDeltaMovement(Vector3d.ZERO);
                } else {
                    this._setDeltaMovement(this.getDeltaMovement().multiply(0.5D, 0.0D, 0.5D));
                }
            }

            d23 = (double) pos.getX() + 0.5D + (double) vector3i.getX() * 0.5D;
            double d10 = (double) pos.getZ() + 0.5D + (double) vector3i.getZ() * 0.5D;
            double d12 = (double) pos.getX() + 0.5D + (double) vector3i1.getX() * 0.5D;
            double d13 = (double) pos.getZ() + 0.5D + (double) vector3i1.getZ() * 0.5D;
            d4 = d12 - d23;
            d5 = d13 - d10;
            double d14;
            if (d4 == 0.0D) {
                d14 = d2 - (double) pos.getZ();
            } else if (d5 == 0.0D) {
                d14 = d0 - (double) pos.getX();
            } else {
                double d15 = d0 - d23;
                double d16 = d2 - d10;
                d14 = (d15 * d4 + d16 * d5) * 2.0D;
            }

            d0 = d23 + d4 * d14;
            d2 = d10 + d5 * d14;
            this.setPos(d0, d1, d2);
            //this.moveMinecartOnRail(p_180460_1_);
            move(MoverType.SELF, getDeltaMovement().multiply(1, 0, 1));
            if (vector3i.getY() != 0 && MathHelper.floor(this.getX()) - pos.getX() == vector3i.getX() && MathHelper.floor(this.getZ()) - pos.getZ() == vector3i.getZ()) {
                this.setPos(this.getX(), this.getY() + (double) vector3i.getY(), this.getZ());
            } else if (vector3i1.getY() != 0 && MathHelper.floor(this.getX()) - pos.getX() == vector3i1.getX() && MathHelper.floor(this.getZ()) - pos.getZ() == vector3i1.getZ()) {
                this.setPos(this.getX(), this.getY() + (double) vector3i1.getY(), this.getZ());
            }

            //this.applyNaturalSlowdown();
            Vector3d vector3d3 = this.getPos(this.getX(), this.getY(), this.getZ());
            Vector3d vector3d6;
            double d27;
            if (vector3d3 != null && vector3d != null) {
                double d17 = (vector3d.y - vector3d3.y) * 0.05D;
                vector3d6 = this.getDeltaMovement();
                d27 = Math.sqrt(getHorizontalDistanceSqr(vector3d6));
                if (d27 > 0.0D) {
                    this._setDeltaMovement(vector3d6.multiply((d27 + d17) / d27, 1.0D, (d27 + d17) / d27));
                }

                this.setPos(this.getX(), vector3d3.y, this.getZ());
            }

            int j = MathHelper.floor(this.getX());
            int i = MathHelper.floor(this.getZ());
            if (j != pos.getX() || i != pos.getZ()) {
                vector3d6 = this.getDeltaMovement();
                d27 = Math.sqrt(getHorizontalDistanceSqr(vector3d6));
                this._setDeltaMovement(new Vector3d(d27 * (double) (j - pos.getX()), vector3d6.y, d27 * (double) (i - pos.getZ())));
            }

            if (this.shouldDoRailFunctions()) {
                //((AbstractRailBlock) blockState.getBlock()).onMinecartPass(blockState, this.level, pos, this);
            }

            if (flag && this.shouldDoRailFunctions()) {
                vector3d6 = this.getDeltaMovement();
                d27 = Math.sqrt(getHorizontalDistanceSqr(vector3d6));
                if (d27 > 0.01D) {
                    double d19 = 0.06D;
                    this._setDeltaMovement(vector3d6.add(vector3d6.x / d27 * 0.06D, 0.0D, vector3d6.z / d27 * 0.06D));
                } else {
                    Vector3d vector3d7 = this.getDeltaMovement();
                    double d20 = vector3d7.x;
                    double d21 = vector3d7.z;
                    if (railshape == RailShape.EAST_WEST) {
                        if (this.isRedstoneConductor(pos.west())) {
                            d20 = 0.02D;
                        } else if (this.isRedstoneConductor(pos.east())) {
                            d20 = -0.02D;
                        }
                    }
                    if (railshape == RailShape.NORTH_SOUTH) {
                        if (this.isRedstoneConductor(pos.north())) {
                            d21 = 0.02D;
                        } else if (this.isRedstoneConductor(pos.south())) {
                            d21 = -0.02D;
                        }
                    }
                    this._setDeltaMovement(new Vector3d(d20, vector3d7.y, d21));
                }
            }
        } else {
            move(MoverType.SELF, getDeltaMovement());
        }
    }

    private void _setDeltaMovement(Vector3d v) {
        if (getHSpeed(v)==0) return;

        setDeltaMovement(
            setHSpeed(v, DIST_PER_POUND)
        );
    }


    private void updateRail(BlockPos railPos){
        BlockState block = level.getBlockState(railPos);

        if (block.is(Blocks.DETECTOR_RAIL) && !block.getValue(DetectorRailBlock.POWERED)) {
            level.setBlock(railPos, block.setValue(DetectorRailBlock.POWERED, true), 1 | 2);
            level.getBlockTicks().scheduleTick(railPos, Blocks.DETECTOR_RAIL, 20);
        }
        if (block.is(Blocks.ACTIVATOR_RAIL)) {
            activateMinecart(railPos.getX(), railPos.getY(), railPos.getZ(), block.getValue(PoweredRailBlock.POWERED));
        }
        if ((block.getBlock() instanceof AbstractRailBlock)) {
            ((AbstractRailBlock) block.getBlock()).onMinecartPass(block, level, railPos, this);
        }
    }

    private void updateTripWire(BlockPos railPos){
        if (!getPassengers().isEmpty()) {
            Entity entity = getPassengers().get(0);

            for (int y = 1; y <= ceil(entity.getBbHeight())-1; y++) {
                BlockPos checkPos = railPos.above(y);

                BlockState block = level.getBlockState(checkPos);

                if (block.getBlock().equals(Blocks.TRIPWIRE) && block.getValue(TripWireBlock.ATTACHED) && !block.getValue(TripWireBlock.POWERED)){
                    level.setBlock(checkPos, block.setValue(TripWireBlock.POWERED, true), 1 | 3);
                    updateTripWireBlock(level, checkPos, block);
                }
            }
        }
    }

    //copy and past of some tripwire code
    private static void updateTripWireBlock(World world, BlockPos tripWirePos, BlockState p_176286_3_) {
        for (Direction d : new Direction[]{Direction.SOUTH, Direction.WEST}) {
            for (int i = 1; i < 42; ++i) {
                BlockPos hookPos = tripWirePos.relative(d, i);
                BlockState blockState = world.getBlockState(hookPos);
                if (blockState.is(Blocks.TRIPWIRE_HOOK)) {
                    if (blockState.getValue(TripWireHookBlock.FACING) == d.getOpposite()) {
                        ((TripWireHookBlock) Blocks.TRIPWIRE_HOOK).calculateState(world, hookPos, blockState, false, true, i, p_176286_3_);
                        world.getBlockTicks().scheduleTick(hookPos, Blocks.TRIPWIRE_HOOK, 20);
                        world.getBlockTicks().scheduleTick(tripWirePos, Blocks.TRIPWIRE, 20);
                    }
                    break;
                }

                if (!blockState.is(Blocks.TRIPWIRE)) {
                    break;
                }
            }
        }

    }

    private boolean isRedstoneConductor(BlockPos p_213900_1_) {
        return level.getBlockState(p_213900_1_).isRedstoneConductor(level, p_213900_1_);
    }

    private static Pair<Vector3i, Vector3i> exits(RailShape p_226573_0_) {
        return (Pair)EXITS.get(p_226573_0_);
    }

    private static final Map<RailShape, Pair<Vector3i, Vector3i>> EXITS = Util.make(Maps.newEnumMap(RailShape.class), (m) -> {
        m.put(RailShape.NORTH_SOUTH, Pair.of(Direction.NORTH.getNormal(), Direction.SOUTH.getNormal()));
        m.put(RailShape.EAST_WEST, Pair.of(Direction.WEST.getNormal(), Direction.EAST.getNormal()));
        m.put(ASCENDING_EAST, Pair.of(Direction.WEST.getNormal().below(), Direction.EAST.getNormal()));
        m.put(RailShape.ASCENDING_WEST, Pair.of(Direction.WEST.getNormal(), Direction.EAST.getNormal().below()));
        m.put(RailShape.ASCENDING_NORTH, Pair.of(Direction.NORTH.getNormal(), Direction.SOUTH.getNormal().below()));
        m.put(RailShape.ASCENDING_SOUTH, Pair.of(Direction.NORTH.getNormal().below(), Direction.SOUTH.getNormal()));
        m.put(RailShape.SOUTH_EAST, Pair.of(Direction.SOUTH.getNormal(), Direction.EAST.getNormal()));
        m.put(RailShape.SOUTH_WEST, Pair.of(Direction.SOUTH.getNormal(), Direction.WEST.getNormal()));
        m.put(RailShape.NORTH_WEST, Pair.of(Direction.NORTH.getNormal(), Direction.WEST.getNormal()));
        m.put(RailShape.NORTH_EAST, Pair.of(Direction.NORTH.getNormal(), Direction.EAST.getNormal()));
    });
}