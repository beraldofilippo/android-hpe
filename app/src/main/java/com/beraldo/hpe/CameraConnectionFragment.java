package com.beraldo.hpe;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


import com.beraldo.hpe.utils.XMLReader;
import com.beraldo.hpe.view.AutoFitTextureView;
import hugo.weaving.DebugLog;
public class CameraConnectionFragment extends Fragment {
    /**
     * The camera preview size will be chosen to be the smallest frame by pixel size capable of
     * containing a DESIRED_SIZE x DESIRED_SIZE square.
     */
    private static final int MINIMUM_PREVIEW_SIZE = 320;
    private static final String TAG = "CameraConnFragment";
    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final String FRAGMENT_DIALOG = "dialog";
    static CameraCharacteristics mCameraCharacteristics;

    /*
    * <VIZARIO.Cam>
        <device type="HUAWEI HUAWEI VNS-L31" version="23"/>
        <camera type="Front" resolution="640x480"/>
        <target type="checkerboard" hcorners="7" vcorners="5"/>
        <aspect ratio="1,0000000000" squaresize="27,5000000000" fixprinciple="false" fixtangent="true"/>
        <calibration focus="1,0" numimages="50">
            <error value="0,1756639928"/>
            <kmatrix k00="502,1289672852" k10="0,0000000000" k20="320,2968750000" k01="0,0000000000" k11="501,8489990234" k21="245,9884185791" k02="0,0000000000" k12="0,0000000000" k22="1,0000000000"/>
            <distortion d0="-0,0105959345" d1="1,3034974337" d2="0,0000000000" d3="0,0000000000" d4="-5,9595479965"/>
        </calibration>
     </VIZARIO.Cam>

    * */

    /*
    * ANDROID
    *       Four radial distortion coefficients [kappa_0, kappa_1, kappa_2, kappa_3] and
    *       two tangential distortion coefficients [kappa_4, kappa_5] that can be used to correct the lens's
    *       geometric distortion with the mapping equations:

            x_c = x_i * ( kappa_0 + kappa_1 * r^2 + kappa_2 * r^4 + kappa_3 * r^6 ) +
                kappa_4 * (2 * x_i * y_i) + kappa_5 * ( r^2 + 2 * x_i^2 )

            y_c = y_i * ( kappa_0 + kappa_1 * r^2 + kappa_2 * r^4 + kappa_3 * r^6 ) +
                kappa_5 * (2 * x_i * y_i) + kappa_4 * ( r^2 + 2 * y_i^2 )
    *
    * */

    /*
    * OPENCV
    *       For the distortion OpenCV takes into account the radial and tangential factors.
    *       For the radial factor one uses the following formula:

            x_{corrected} = x( 1 + k_1 r^2 + k_2 r^4 + k_3 r^6) \\
            y_{corrected} = y( 1 + k_1 r^2 + k_2 r^4 + k_3 r^6)

            So for an old pixel point at (x,y) coordinates in the input image, its position on the corrected output
            image will be (x_{corrected} y_{corrected}). The presence of the radial distortion manifests in form of the
            “barrel” or “fish-eye” effect.

            Tangential distortion occurs because the image taking lenses are not perfectly parallel to the imaging plane.
            It can be corrected via the formulas:

            x_{corrected} = x + [ 2p_1xy + p_2(r^2+2x^2)] \\
            y_{corrected} = y + [ p_1(r^2+ 2y^2)+ 2p_2xy]

            So we have five distortion parameters which in OpenCV are presented as one row matrix with 5 columns:

            Distortion_{coefficients}=(k_1  k_2  p_1  p_2  k_3)
    *
    * */


    /* Android gives [f_x, f_y, c_x, c_y, s] */
    static float[] mCameraIntrinsics = new float[5];

    /* Android gives [k_0, k_1, k_2, k_3, p_1, p_2] */
    /* OpenCV wants [k_1, k_2, p_1, p_2, k_3] */
    static float[] mCameraDistortions = new float[5];

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    private final OnGetImageListener mOnGetPreviewListener = new OnGetImageListener();
    private final CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureProgressed(
                        final CameraCaptureSession session,
                        final CaptureRequest request,
                        final CaptureResult partialResult) {
                }

                @Override
                public void onCaptureCompleted(
                        final CameraCaptureSession session,
                        final CaptureRequest request,
                        final TotalCaptureResult result) {
                }
            };
    private TextView mPerformanceView;
    private TextView mResultsView;
    private TextView mInfoView;

    private boolean buttonsClickable = false;
    protected Button stopButton;
    protected Button discardButton;
    /**
     * ID of the current {@link CameraDevice}.
     */
    private String cameraId;
    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView textureView;
    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession captureSession;
    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice cameraDevice;
    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size previewSize;
    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread backgroundThread;
    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler backgroundHandler;
    /**
     * An additional thread for running inference so as not to block the camera.
     */
    private HandlerThread inferenceThread;
    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler inferenceHandler;
    /**
     * An {@link ImageReader} that handles preview frame capture.
     */
    private ImageReader previewReader;
    /**
     * {@link android.hardware.camera2.CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder previewRequestBuilder;
    /**
     * {@link CaptureRequest} generated by {@link #previewRequestBuilder}
     */
    private CaptureRequest previewRequest;
    /**
     * {@link android.hardware.camera2.CameraDevice.StateCallback}
     * is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback stateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(final CameraDevice cd) {
                    // This method is called when the camera is opened.  We start camera preview here.
                    cameraOpenCloseLock.release();
                    cameraDevice = cd;
                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(final CameraDevice cd) {
                    cameraOpenCloseLock.release();
                    cd.close();
                    cameraDevice = null;

                    if (mOnGetPreviewListener != null) {
                        mOnGetPreviewListener.deInitialize();
                    }
                }

                @Override
                public void onError(final CameraDevice cd, final int error) {
                    cameraOpenCloseLock.release();
                    cd.close();
                    cameraDevice = null;
                    final Activity activity = getActivity();
                    if (null != activity) {
                        activity.finish();
                    }

                    if (mOnGetPreviewListener != null) {
                        mOnGetPreviewListener.deInitialize();
                    }
                }
            };
    /**
     * {@link android.view.TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(
                        final SurfaceTexture texture, final int width, final int height) {
                    openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(
                        final SurfaceTexture texture, final int width, final int height) {
                    configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
                }
            };

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    @SuppressLint("LongLogTag")
    @DebugLog
    private static Size chooseOptimalSize(
            final Size[] choices, final int width, final int height, final Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        final List<Size> bigEnough = new ArrayList<Size>();
        for (final Size option : choices) {
            if (option.getHeight() >= MINIMUM_PREVIEW_SIZE && option.getWidth() >= MINIMUM_PREVIEW_SIZE) {
                Log.i(TAG, "Adding size: " + option.getWidth() + "x" + option.getHeight());
                bigEnough.add(option);
            } else {
                Log.i(TAG, "Not adding size: " + option.getWidth() + "x" + option.getHeight());
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
            Log.i(TAG, "Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
            return chosenSize;
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static CameraConnectionFragment newInstance() {
        return new CameraConnectionFragment();
    }

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.camera_connection_fragment, container, false);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        stopButton = (Button) view.findViewById(R.id.stop_capt);
        discardButton = (Button) view.findViewById(R.id.discard_capt);
        textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        mPerformanceView = (TextView) view.findViewById(R.id.performance_tv);
        mResultsView = (TextView) view.findViewById(R.id.results_tv);
        mInfoView = (TextView) view.findViewById(R.id.info_tv);

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(buttonsClickable) getActivity().onBackPressed();
            }
        });

        discardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.saveFile = false;
                if(buttonsClickable) getActivity().onBackPressed();
            }
        });
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    @DebugLog
    @SuppressLint("LongLogTag")
    private void setUpCameraOutputs(final int width, final int height) {
        final Activity activity = getActivity();
        final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (final String cameraId : manager.getCameraIdList()) { // Cycle through all available cameras
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId); // Inspect this camera characteristics
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING); // Get which facing it is
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) { // If it faces front, we 've found it
                    mCameraCharacteristics = characteristics; // Get the characteristics of the camera and set them as an attribute

                    // See if it complies with what we need, otherwise skip to another camera
                    final StreamConfigurationMap map =
                            mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map == null) {
                        continue;
                    } // SKIP

                    // For still image captures, we use the largest available size.
                    final Size largest = Collections.max(
                            Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
                            new CompareSizesByArea());

                    // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                    // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                    // garbage capture data.
                    previewSize =
                            chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, largest);

                    // Set aspect ratio for the textureView in order to comply with landscape mode
                    textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());

                    // Set the camera as the selected
                    CameraConnectionFragment.this.cameraId = cameraId;

                    if(Build.VERSION.SDK_INT >= 23) {
                        if(mCameraCharacteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION) != null) {
                            mCameraIntrinsics =  mCameraCharacteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION);
                            mInfoView.setText("Camera intrinsics available!\n[f_x, f_y, c_x, c_y, s]\n" + mCameraIntrinsics);
                        }
                        else {
                            mCameraIntrinsics = XMLReader.loadIntrinsicParams(getActivity());
                            mInfoView.setText("No camera intrinsics prebuilt values available for this device! Using:\n" +
                                    "[f_x] = " + mCameraIntrinsics[0] + " [f_y] = " + mCameraIntrinsics[1] + " [c_x] = " + mCameraIntrinsics[2] + " [c_y] = " + mCameraIntrinsics[2]);
                        }

                        if(mCameraCharacteristics.get(CameraCharacteristics.LENS_RADIAL_DISTORTION) != null) {
                            float[] values = mCameraCharacteristics.get(CameraCharacteristics.LENS_RADIAL_DISTORTION);
                            mCameraDistortions[0] = values[1]; // k1
                            mCameraDistortions[1] = values[2]; // k2
                            mCameraDistortions[2] = values[4]; // p1
                            mCameraDistortions[3] = values[5]; // p2
                            mCameraDistortions[4] = values[3]; // k3
                            mInfoView.append("Camera distortions available!\n[k_1, k_2, p_1, p_2, k_3]\n" + mCameraDistortions);
                        }
                        else {
                            mCameraDistortions = XMLReader.loadDistortionParams(getActivity());
                            mInfoView.append("\nNo camera distortions prebuilt values available for this device! Using:\n" +
                                    "[k_1] = " + mCameraDistortions[0] + " [k_2] = " + mCameraDistortions[1] + " [p_1] = " + mCameraDistortions[2] + " [p_2] = " + mCameraDistortions[3] + " k_3] = " + mCameraDistortions[4]);
                        }
                    } else {
                        mCameraIntrinsics = XMLReader.loadIntrinsicParams(getActivity());
                        mInfoView.setText("No camera intrinsics prebuilt values available for this device (API is < 23)! Using:\n" +
                                "[f_x] = " + mCameraIntrinsics[0] + " [f_y] = " + mCameraIntrinsics[1] + " [c_x] = " + mCameraIntrinsics[2] + " [c_y] = " + mCameraIntrinsics[2]);
                        mCameraDistortions = XMLReader.loadDistortionParams(getActivity());
                        mInfoView.append("\nNo camera intrinsics prebuilt values available for this device (API is < 23)! Using:\n" +
                                "[k_1] = " + mCameraDistortions[0] + " [k_2] = " + mCameraDistortions[1] + " [p_1] = " + mCameraDistortions[2] + " [p_2] = " + mCameraDistortions[3] + " k_3] = " + mCameraDistortions[4]);
                    }

                    return;
                }
            }
        } catch (final CameraAccessException e) {
            Log.e(TAG, "Exception!", e);
        } catch (final NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    /**
     * Opens the camera specified by {@link CameraConnectionFragment#cameraId}.
     */
    @SuppressLint("LongLogTag")
    @DebugLog
    private void openCamera(final int width, final int height) {
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        final Activity activity = getActivity();
        final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            if (ActivityCompat.checkSelfPermission(this.getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "checkSelfPermission CAMERA");
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
            Log.d(TAG, "open Camera");
        } catch (final CameraAccessException e) {
            Log.e(TAG, "Exception!", e);
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    @DebugLog
    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (null != captureSession) {
                captureSession.close();
                captureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != previewReader) {
                previewReader.close();
                previewReader = null;
            }
            if (null != mOnGetPreviewListener) {
                mOnGetPreviewListener.deInitialize();
            }
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    @DebugLog
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("ImageListener");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        inferenceThread = new HandlerThread("InferenceThread");
        inferenceThread.start();
        inferenceHandler = new Handler(inferenceThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    @SuppressLint("LongLogTag")
    @DebugLog
    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        inferenceThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;

            inferenceThread.join();
            inferenceThread = null;
            inferenceThread = null;
        } catch (final InterruptedException e) {
            Log.e(TAG, "error", e);
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    @SuppressLint("LongLogTag")
    @DebugLog
    private void createCameraPreviewSession() {
        try {
            final SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            // This is the output Surface we need to start preview.
            final Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            Log.i(TAG, "Opening camera preview: " + previewSize.getWidth() + "x" + previewSize.getHeight());

            // Create the reader for the preview frames.
            previewReader =
                    ImageReader.newInstance(
                            previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);

            previewReader.setOnImageAvailableListener(mOnGetPreviewListener, backgroundHandler);
            previewRequestBuilder.addTarget(previewReader.getSurface());

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(
                    Arrays.asList(surface, previewReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                // Finally, we start displaying the camera preview.
                                previewRequest = previewRequestBuilder.build();
                                captureSession.setRepeatingRequest(
                                        previewRequest, captureCallback, backgroundHandler);

                            } catch (final CameraAccessException e) {
                                Log.e(TAG, "Exception!", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
                            showToast("onConfigureFailed()");
                        }
                    },
                    null);
        } catch (final CameraAccessException e) {
            Log.e(TAG, "Exception!", e);
        }

        mOnGetPreviewListener.initialize(getActivity(), mCameraIntrinsics, mCameraDistortions, mPerformanceView, mResultsView, inferenceHandler);
        buttonsClickable = true;
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    @DebugLog
    private void configureTransform(final int viewWidth, final int viewHeight) {
        final Activity activity = getActivity();
        if (textureView == null || previewSize == null || activity == null) {
            return;
        }

        final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        final Matrix matrix = new Matrix();
        final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        final float centerX = viewRect.centerX();
        final float centerY = viewRect.centerY();
        if(rotation == Surface.ROTATION_90) {
            //Log.d(TAG, "Rotation is Surface.ROTATION_90");
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            final float scale =
                    Math.max(
                            (float) viewHeight / previewSize.getHeight(),
                            (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(-90, centerX, centerY);
        } /*else if(rotation == Surface.ROTATION_180) {
            Log.d(TAG, "Rotation is Surface.ROTATION_180");
            matrix.postRotate(180, centerX, centerY);
        } else if(rotation == Surface.ROTATION_270) {
            Log.d(TAG, "Rotation is Surface.ROTATION_270");
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            final float scale =
                    Math.max(
                            (float) viewHeight / previewSize.getHeight(),
                            (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }*/
        textureView.setTransform(matrix);
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(final Size lhs, final Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum(
                    (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {
        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(final String message) {
            final ErrorDialog dialog = new ErrorDialog();
            final Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(
                            android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(final DialogInterface dialogInterface, final int i) {
                                    activity.finish();
                                }
                            })
                    .create();
        }
    }
}
