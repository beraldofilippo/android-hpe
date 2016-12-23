package com.beraldo.hpe.dlib;

public class HeadPoseGaze {
    private double[] ypr;

    public HeadPoseGaze() {
        ypr = new double[3];
    }

    public HeadPoseGaze(double y, double p, double r) {
        ypr = new double[3];
        ypr[0] = y;
        ypr[1] = p;
        ypr[2] = r;
    }

    // Convenience method to produce a new object (always calling it when adding a new item fro JNI!)
    public static HeadPoseGaze newInstance(double y, double p, double r) {
        return new HeadPoseGaze(y, p, r);
    }

    public void setGaze(double y, double p, double r) {
        ypr[0] = y;
        ypr[1] = p;
        ypr[2] = r;
    }

    public double[] getGaze() {
        return ypr;
    }

    // According to my new frame of refence, I must add 180 degrees to pitch and yaw in order to geto (0,0,0) for a front looking fcae.
    public double getYaw() {
        return ypr[0] - 180;
    }

    public double getPitch() {
        return ypr[1] - 180;
    }

    public double getRoll() {
        return ypr[2];
    }

    public String toString() {
        return "(" + getYaw() + ", " + getPitch() + ", " + getRoll() + ")";
    }
}
