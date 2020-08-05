package com.beraldo.hpe;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Handler;
import android.os.Trace;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.TextView;

import org.w3c.dom.Document;

import java.io.File;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import com.beraldo.hpe.dlib.HeadPoseDetector;
import com.beraldo.hpe.dlib.HeadPoseGaze;

import com.beraldo.hpe.utils.FileUtils;
import com.beraldo.hpe.utils.ImageUtils;
import com.beraldo.hpe.utils.XMLWriter;
import com.beraldo.hpe.view.FloatingCameraWindow;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with dlib lib.
 */
public class OnGetImageListener implements OnImageAvailableListener {
    private static final int NUM_CLASSES = 1001;
    private static final int INPUT_SIZE = 240;
    private static final int IMAGE_MEAN = 117;
    private static final String TAG = "OnGetImageListener";

    private int mScreenRotation = 0;

    private int mPreviewWdith = 0;
    private int mPreviewHeight = 0;
    private byte[][] mYUVBytes;
    private int[] mRGBBytes = null;
    private Bitmap mRGBframeBitmap = null;
    private Bitmap mRGBrotatedBitmap = null;
    //private Bitmap mCroppedBitmap = null;

    private boolean mIsComputing = false;
    private Handler mInferenceHandler;

    private Context mContext;
    private HeadPoseDetector mHeadPoseDetector;
    private TextView mPerformanceView;
    private TextView mResultsView;
    private FloatingCameraWindow mWindow;

    private DecimalFormat df;
    private Document detectionDocument;

    private double overallTime = 0;
    private int valid_cycles = 0;

    public void initialize( final Context context, final float[] intrinsics, final float[] distortions, final TextView mPerformanceView, final TextView mResultsView, final Handler handler) {
        this.mContext = context;
        this.mPerformanceView = mPerformanceView;
        this.mResultsView = mResultsView;
        this.mInferenceHandler = handler;
        mHeadPoseDetector = new HeadPoseDetector();
        mWindow = new FloatingCameraWindow(mContext);

        // Ensure the model file is properly deserialized into destination
        File model = new File(FileUtils.getPreference(mContext, FileUtils.DATA_DIR_PREFS_NAME), FileUtils.PREDICTOR_FILE_NAME);
        if (!model.exists()) {
            Log.d(TAG, "Copying landmark model to " + model.getAbsolutePath());
            FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_68_face_landmarks, model.getAbsolutePath());
        }

        // Initialize the headpose detector with its parameters
        mHeadPoseDetector.init(model.getAbsolutePath(), MainActivity.mode, intrinsics, distortions);

        // Initialize the formatter for the strings to be shown
        df = new DecimalFormat("##.##");
        df.setRoundingMode(RoundingMode.DOWN);

        if(MainActivity.saveFile) detectionDocument = XMLWriter.newDocument(MainActivity.mode);
    }

    public void deInitialize() {
        synchronized (OnGetImageListener.this) {
            if(MainActivity.saveFile) {// Update performance info and save the file
                XMLWriter.addTimePerformance(detectionDocument, overallTime / valid_cycles); // Add performance field
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                XMLWriter.saveDocumentToFile(mContext, detectionDocument, "detection_" + sdf.format(new Date(System.currentTimeMillis())) + ".xml");
            }
            if (mHeadPoseDetector != null) {
                mHeadPoseDetector.deInit();
            }

            if (mWindow != null) {
                mWindow.release();
            }
        }
    }

    private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {
        Display display = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);
        int screen_width = point.x;
        int screen_height = point.y;

        if (screen_width < screen_height) { // Screen is in portrait
            mScreenRotation = 0;
        } else { // Screen is in landscape
            mScreenRotation = 90;
        }

        final float minDim = Math.min(src.getWidth(), src.getHeight());

        final Matrix matrix = new Matrix();

        // We only want the center square out of the original rectangle.
        final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);
        final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);
        matrix.preTranslate(translateX, translateY);

        // Set the scale to accomodate the least between height and width of the source
        final float scaleFactor = dst.getHeight() / minDim;
        matrix.postScale(scaleFactor, scaleFactor);

        // Rotate around the center if necessary.
        if (mScreenRotation != 0) {
            matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f);
            matrix.postRotate(mScreenRotation);
            matrix.postTranslate(dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
        }

        final Canvas canvas = new Canvas(dst);
        canvas.drawBitmap(src, matrix, null);
    }

    private void drawUnmirroredRotatedBitmap(final Bitmap src, final Bitmap dst, final int rotation) {
        final Matrix matrix = new Matrix();
        //matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f);
        matrix.postRotate(rotation);
        matrix.setScale(-1, 1);
        matrix.postTranslate(dst.getWidth(), 0);

        final Canvas canvas = new Canvas(dst);
        canvas.drawBitmap(src, matrix, null);
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            // No mutex needed as this method is not reentrant.
            if (mIsComputing) {
                image.close();
                return;
            }
            mIsComputing = true;

            Trace.beginSection("imageAvailable");

            final Plane[] planes = image.getPlanes();

            // Initialize the storage bitmaps once when the resolution is known.
            if (mPreviewWdith != image.getWidth() || mPreviewHeight != image.getHeight()) {
                mPreviewWdith = image.getWidth();
                mPreviewHeight = image.getHeight();

                Log.d(TAG, String.format("Initializing at size %dx%d", mPreviewWdith, mPreviewHeight));
                mRGBBytes = new int[mPreviewWdith * mPreviewHeight];
                mRGBframeBitmap = Bitmap.createBitmap(mPreviewWdith, mPreviewHeight, Config.ARGB_8888);
                mRGBrotatedBitmap = Bitmap.createBitmap(mPreviewWdith, mPreviewHeight, Config.ARGB_8888);
                //mCroppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

                mYUVBytes = new byte[planes.length][];
                for (int i = 0; i < planes.length; ++i) {
                    mYUVBytes[i] = new byte[planes[i].getBuffer().capacity()];
                }
            }

            for (int i = 0; i < planes.length; ++i) {
                planes[i].getBuffer().get(mYUVBytes[i]);
            }

            final int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();
            ImageUtils.convertYUV420ToARGB8888( mYUVBytes[0], mYUVBytes[1], mYUVBytes[2], mRGBBytes, mPreviewWdith, mPreviewHeight, yRowStride, uvRowStride, uvPixelStride, false);

            image.close();
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            Log.e(TAG, "Exception!", e);
            Trace.endSection();
            return;
        }

        mRGBframeBitmap.setPixels(mRGBBytes, 0, mPreviewWdith, 0, 0, mPreviewWdith, mPreviewHeight);
        drawUnmirroredRotatedBitmap(mRGBframeBitmap, mRGBrotatedBitmap, 0);
        //drawResizedBitmap(mRGBframeBitmap, mCroppedBitmap);

        mInferenceHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        final long startTime = System.currentTimeMillis();
                        ArrayList<HeadPoseGaze> results;
                        synchronized (OnGetImageListener.this) {
                            results = mHeadPoseDetector.bitmapDetection(mRGBrotatedBitmap);
                        }
                        final long endTime = System.currentTimeMillis();

                        ((Activity) mContext).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mPerformanceView.setText("Time cost\n" + String.valueOf((endTime - startTime) / 1000f) + " sec");
                            }
                        });

                        // Update the score textview with info on result
                        if(!results.isEmpty()) {
                            // Update performance timing
                            overallTime += ((endTime - startTime) / 1000f);
                            valid_cycles++;

                            final HeadPoseGaze r = results.get(0);
                            ((Activity) mContext).runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mResultsView.setText("Gaze angles\nYaw: " + df.format(r.getYaw()) +
                                            "\nPitch: " + df.format(r.getPitch()) +
                                            "\nRoll: " + df.format(r.getRoll()));
                                }
                            });
                            if(MainActivity.saveFile) XMLWriter.addResult(detectionDocument, System.currentTimeMillis(), r.getYaw(), r.getPitch(), r.getRoll());
                        }

                        mWindow.setRGBBitmap(mRGBrotatedBitmap);
                        mIsComputing = false;
                    }
                });

        Trace.endSection();
    }
}