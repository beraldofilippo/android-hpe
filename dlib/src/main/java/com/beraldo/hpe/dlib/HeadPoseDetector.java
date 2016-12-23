package com.beraldo.hpe.dlib;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;

import hugo.weaving.DebugLog;

public class HeadPoseDetector {
    private static final String TAG = "HeadPoseDetector";
    protected static boolean initialized = false;
    protected static boolean initializing = false;

    static {
        try {
            System.loadLibrary("head_pose_det"); // Load native library
        } catch (UnsatisfiedLinkError e) {
            Log.d(TAG, " ### Native library not found! ###");
        }
    }

    public static void init(String model_path, int alg_mode, float[] intrinsics, float[] distortions) {
        if (!initializing) {
            Log.d(TAG, " *** Requested a fresh initialization with jniInit() ***");
            initializing = true;
            if (jniInit(model_path,
                    alg_mode,
                    intrinsics[0],
                    intrinsics[1],
                    intrinsics[2],
                    intrinsics[3],
                    distortions[0],
                    distortions[1],
                    distortions[2],
                    distortions[3],
                    distortions[4]) == 0) { // If all went ok, state as initialized true
                initializing = false;
                initialized = true;
            } else {
                initializing = false;
                initialized = false;
                Log.d(TAG, " *** jniInit() ERROR ***");
            }
        } else {
            Log.d(TAG, " *** Requested initialization with jniInit() while already initializing ***");
        }
    }

    public static void deInit() {
        Log.d(TAG, " *** Requested deinitialization with jniDeInit() ***");
        if (jniDeInit() == 0) {
            Log.d(TAG, " *** jniDeInit() OK ***");
            initialized = false;
        } else {
            Log.d(TAG, " *** jniDeInit() ERROR ***");
        }
    }

    @NonNull
    @DebugLog
    public ArrayList<HeadPoseGaze> bitmapDetection(@NonNull Bitmap bitmap) {
        if (!initialized) {
            Log.e(TAG, " *** HeadPoseDetector is not initialized, use jniInit() ***");
            return new ArrayList<>();
        }

        // A set of results, say a set of gazes, populated by the jni call
        ArrayList<HeadPoseGaze> gazes_set = new ArrayList<>();
        jniBitmapExtractFaceGazes(bitmap, gazes_set);

        return gazes_set;
    }

    private static native int jniInit(String landmarkModelPath, int alg_mode,
                                      float fx, float fy, float cx, float cy,
                                      float k1, float k2, float p1, float p2, float k3);

    private static native int jniDeInit();

    private native int jniBitmapExtractFaceGazes(Bitmap bitmap, ArrayList<HeadPoseGaze> set);
}