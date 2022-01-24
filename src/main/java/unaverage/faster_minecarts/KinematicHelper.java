package gpa.faster_minecarts;

import net.minecraft.util.math.vector.Vector3d;

import javax.annotation.Nullable;

import static java.lang.Math.sqrt;

public final class KinematicHelper {
    private KinematicHelper(){}

    public static double sq(double v){
        return v*v;
    }

    public static double getHSpeed(Vector3d v){
        return sqrt(v.x()*v.x()+v.z()*v.z());
    }

    public static Vector3d setHSpeed(Vector3d v, double newSpeed){
        double oldSpeed = getHSpeed(v);

        if (oldSpeed == 0) return v;

        return v.multiply(newSpeed/oldSpeed, 1, newSpeed/oldSpeed);
    }

    @Nullable
    public static Double getFinalV(double initV, double dist, double acc){
        double discriminate = sq(initV) + 2 * acc * dist;

        if (discriminate < 0) return null;

        return sqrt(discriminate);
    }

    @Nullable
    public static Double getInitV(double finalV, double dist, double acc){
        double discriminate = sq(finalV) + -2 * acc * dist;

        if (discriminate < 0) return null;

        return sqrt(discriminate);
    }

    public static double getAcc(double initV, double finalV, double dist){
        return (sq(finalV) - sq(initV))/(2*dist);
    }

    @Nullable
    public static Double getTime(double acc, double speed, double dist) {
        if (acc == 0) return null;

        double discriminator = 2 * dist * acc + speed * speed;

        if (discriminator < 0) {
            //System.out.println("x " + acc + " " + currentSpeed + " " + DISTANCE);
            return null;
        }

        double result = (sqrt(discriminator) - speed) / acc;

        if (result < 0) {
            //System.out.println("y " + acc + " " + currentSpeed + " " + DISTANCE);
            return null;
        }


        return result;
    }
}
