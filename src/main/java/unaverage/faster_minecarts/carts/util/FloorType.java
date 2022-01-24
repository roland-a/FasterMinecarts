package gpa.faster_minecarts.carts.util;

import gpa.faster_minecarts.KinematicHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.tags.BlockTags;

import static gpa.faster_minecarts.ServerConfig.*;

public enum FloorType {
    POWERED_RAIL {
        @Override
        public boolean contains(BlockState b) {
            if (!b.is(Blocks.POWERED_RAIL)) return false;

            if (((PoweredRailBlock)b.getBlock()).isActivatorRail()) return false;

            return b.getValue(PoweredRailBlock.POWERED);
        }

        @Override
        public double getAcc() {
            return KinematicHelper.getAcc(0, maxSpeed, 0.5);
        }
    },
    UNPOWERED_RAIL {
        @Override
        public boolean contains(BlockState b) {
            if (!b.is(Blocks.POWERED_RAIL)) return false;

            if (((PoweredRailBlock)b.getBlock()).isActivatorRail()) return false;

            return !b.getValue(PoweredRailBlock.POWERED);
        }

        @Override
        public double getAcc() {
            return KinematicHelper.getAcc(maxSpeed, maxSpeed/2, 1);
        }
    },
    NORMAL_RAIL {
        @Override
        public boolean contains(BlockState b) {
            return b.is(BlockTags.RAILS);
        }

        @Override
        public double getAcc() {
            return KinematicHelper.getAcc(maxSpeed, 0, slowDownDist+.5);
        }
    },
    AIR{
        @Override
        public boolean contains(BlockState b) {
            if (b.is(Blocks.AIR)) return true;

            if (b.is(Blocks.CAVE_AIR)) return true;

            if (b.is(Blocks.VOID_AIR)) return true;

            return false;
        }

        @Override
        public double getAcc() {
            return KinematicHelper.getAcc(maxSpeed, 0, 100);
        }
    },
    ICE{
        @Override
        public boolean contains(BlockState b) {
            return b.is(BlockTags.ICE);
        }

        @Override
        public double getAcc() {
            return KinematicHelper.getAcc(maxSpeed, 0, 50);
        }
    },
    BARE{
        @Override
        public boolean contains(BlockState b) {
            return true;
        }

        @Override
        public double getAcc() {
            return KinematicHelper.getAcc(maxSpeed, 0, 0.5);
        }
    };

    public abstract boolean contains(BlockState b);

    public abstract double getAcc();

    public static FloorType getFromBlock(BlockState rail) {
        for (FloorType t : values()) {
            if (t.contains(rail)) return t;
        }
        throw new RuntimeException();
    }




}
