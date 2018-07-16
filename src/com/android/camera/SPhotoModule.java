/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013-2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.AudioManager;
import android.media.CameraProfile;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.camera.app.CameraApp;
import com.android.camera.CameraManager.CameraAFCallback;
import com.android.camera.CameraManager.CameraAFMoveCallback;
import com.android.camera.CameraManager.CameraPictureCallback;
import com.android.camera.CameraManager.CameraProxy;
import com.android.camera.CameraManager.CameraShutterCallback;
import com.android.camera.SPhotoModule.SNamedImages.SNamedEntity;
import com.android.camera.exif.ExifInterface;
import com.android.camera.exif.ExifTag;
import com.android.camera.exif.Rational;
import com.android.camera.ui.CountDownView.OnCountDownFinishedListener;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.GcamHelper;
import com.android.camera.util.PersistUtil;
import com.android.camera.util.UsageStatistics;
import org.codeaurora.snapcam.R;
import org.codeaurora.snapcam.wrapper.ParametersWrapper;
import org.codeaurora.snapcam.wrapper.CameraInfoWrapper;
import org.codeaurora.snapcam.filter.GDepth;
import org.codeaurora.snapcam.filter.GImage;

import android.widget.EditText;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.text.TextUtils;

import com.android.internal.util.MemInfoReader;
import android.app.ActivityManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.lang.NumberFormatException;
import java.util.List;
import java.util.Vector;
import java.util.HashMap;
import android.util.AttributeSet;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemProperties;

public class SPhotoModule
        implements CameraModule,
        PhotoController,
        FocusOverlayManager.Listener,
        CameraPreference.OnPreferenceChangedListener,
        ShutterButton.OnShutterButtonListener,
        MediaSaveService.Listener,
        OnCountDownFinishedListener,
        LocationManager.Listener,
        SensorEventListener {

    private static final String TAG = "CAM_SPhotoModule";

   //QCom data members
    private static final int MAX_SHARPNESS_LEVEL = 6;
    private boolean mRestartPreview = false;
    private int mSnapshotMode;
    private int mBurstSnapNum = 1;
    private int mReceivedSnapNum = 0;
    private int mLongshotSnapNum = 0;
    public boolean mFaceDetectionEnabled = false;
    private SCameraGraphView mGraphView;
    private static final int STATS_DATA = 257;
    public static int statsdata[] = new int[STATS_DATA];
    // We number the request code from 1000 to avoid collision with Gallery.
    private static final int REQUEST_CROP = 1000;

    private static final int SETUP_PREVIEW = 1;
    private static final int FIRST_TIME_INIT = 2;
    private static final int CLEAR_SCREEN_DELAY = 3;
    private static final int SET_CAMERA_PARAMETERS_WHEN_IDLE = 4;
    private static final int SHOW_TAP_TO_FOCUS_TOAST = 5;
    private static final int SWITCH_CAMERA = 6;
    private static final int SWITCH_CAMERA_START_ANIMATION = 7;
    private static final int CAMERA_OPEN_DONE = 8;
    private static final int OPEN_CAMERA_FAIL = 9;
    private static final int CAMERA_DISABLED = 10;
    private static final int SET_PHOTO_UI_PARAMS = 11;
    private static final int SWITCH_TO_GCAM_MODULE = 12;
    private static final int ON_PREVIEW_STARTED = 13;
    private static final int INSTANT_CAPTURE = 14;
    private static final int UNLOCK_CAM_SHUTTER = 15;
    private static final int SET_FOCUS_RATIO = 16;

    private static final int NO_DEPTH_EFFECT = 0;
    private static final int DEPTH_EFFECT_SUCCESS = 1;
    private static final int TOO_NEAR = 2;
    private static final int TOO_FAR = 3;
    private static final int LOW_LIGHT = 4;
    private static final int SUBJECT_NOT_FOUND = 5;
    private static final int TOUCH_TO_FOCUS = 6;

    // The subset of parameters we need to update in setCameraParameters().
    private static final int UPDATE_PARAM_INITIALIZE = 1;
    private static final int UPDATE_PARAM_ZOOM = 2;
    private static final int UPDATE_PARAM_PREFERENCE = 4;
    private static final int UPDATE_PARAM_ALL = -1;

    // This is the delay before we execute onResume tasks when coming
    // from the lock screen, to allow time for onPause to execute.
    private static final int ON_RESUME_TASKS_DELAY_MSEC = 20;

    private static final String DEBUG_IMAGE_PREFIX = "DEBUG_";

    // copied from Camera hierarchy
    private CameraActivity mActivity;
    private CameraProxy mCameraDevice;
    private int mCameraId;
    private Parameters mParameters;
    private boolean mPaused;
    private View mRootView;

    private SPhotoUI mUI;

    // The activity is going to switch to the specified camera id. This is
    // needed because texture copy is done in GL thread. -1 means camera is not
    // switching.
    protected int mPendingSwitchCameraId = -1;
    private boolean mOpenCameraFail;
    private boolean mCameraDisabled;

    // When setCameraParametersWhenIdle() is called, we accumulate the subsets
    // needed to be updated in mUpdateSet.
    private int mUpdateSet;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    private int mZoomValue;  // The current zoom value.

    private Parameters mInitialParams;
    private boolean mFocusAreaSupported;
    private boolean mMeteringAreaSupported;
    private boolean mAeLockSupported;
    private boolean mAwbLockSupported;
    private boolean mContinuousFocusSupported;
    private boolean mLongshotSave = false;
    private int mLongShotCaptureCount;
    private int mLongShotCaptureCountLimit;

    // The degrees of the device rotated clockwise from its natural orientation.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private ComboPreferences mPreferences;

    private static final String sTempCropFilename = "crop-temp";

    private boolean mFaceDetectionStarted = false;

    private static final boolean PERSIST_SKIP_MEM_CHECK = PersistUtil.isSkipMemoryCheckEnabled();

    private static final String PERSISI_BOKEH_DEBUG = "persist.camera.bokeh.debug";
    private static final boolean PERSIST_BOKEH_DEBUG_CHECK =
            android.os.SystemProperties.getBoolean(PERSISI_BOKEH_DEBUG, false);
    private static final String PERSIST_LONGSHOT_MAX_SNAP = "persist.camera.longshot.max";
    private static int mLongShotMaxSnap = -1;

    // Constant from android.hardware.Camera.Parameters
    private static final String KEY_PICTURE_FORMAT = "picture-format";
    private static final String KEY_QC_RAW_PICUTRE_SIZE = "raw-size";
    public static final String PIXEL_FORMAT_JPEG = "jpeg";

    private static final int MIN_SCE_FACTOR = -10;
    private static final int MAX_SCE_FACTOR = +10;
    private int SCE_FACTOR_STEP = 10;

    private boolean mPreviewRestartSupport = false;

    // mCropValue and mSaveUri are used only if isImageCaptureIntent() is true.
    private String mCropValue;
    private Uri mSaveUri;

    private Uri mDebugUri;

    // Used for check memory status for longshot mode
    // Currently, this cancel threshold selection is based on test experiments,
    // we can change it based on memory status or other requirements.
    private static final int LONGSHOT_CANCEL_THRESHOLD = 40 * 1024 * 1024;
    private long SECONDARY_SERVER_MEM;
    private boolean mLongshotActive = false;

    // We use a queue to generated names of the images to be used later
    // when the image is ready to be saved.
    private SNamedImages mSNamedImages;

    private byte[] mLastJpegData;
    private int mLastJpegOrientation = 0;

    private static Context mApplicationContext = null;

    private boolean mIsBokehMode = false;
    private TextView mBokehTipText;
    private boolean mDepthSuccess = false;
    private boolean mSaveBokehXmp = false;

    private class OpenCameraThread extends Thread {
        @Override
        public void run() {
            openCamera();
            startPreview();
        }
    }

    private OpenCameraThread mOpenCameraThread = null;
    /**
     * An unpublished intent flag requesting to return as soon as capturing
     * is completed.
     *
     * TODO: consider publishing by moving into MediaStore.
     */
    private static final String EXTRA_QUICK_CAPTURE =
            "android.intent.extra.quickCapture";

    // The display rotation in degrees. This is only valid when mCameraState is
    // not PREVIEW_STOPPED.
    private int mDisplayRotation;
    // The value for android.hardware.Camera.setDisplayOrientation.
    private int mCameraDisplayOrientation;
    // The value for UI components like indicators.
    private int mDisplayOrientation;
    // The value for android.hardware.Camera.Parameters.setRotation.
    private int mJpegRotation;
    // Indicates whether we are using front camera
    private boolean mMirror;
    private boolean mFirstTimeInitialized;
    private boolean mIsImageCaptureIntent;
    private int mOrientationOffset;

    private int mCameraState = INIT;
    private boolean mSnapshotOnIdle = false;

    private ContentResolver mContentResolver;

    private LocationManager mLocationManager;
    private boolean mLocationPromptTriggered = false;

    private final PostViewPictureCallback mPostViewPictureCallback =
            new PostViewPictureCallback();
    private final RawPictureCallback mRawPictureCallback =
            new RawPictureCallback();
    private final AutoFocusCallback mAutoFocusCallback =
            new AutoFocusCallback();
    private final Object mAutoFocusMoveCallback =
            ApiHelper.HAS_AUTO_FOCUS_MOVE_CALLBACK
                    ? new AutoFocusMoveCallback()
                    : null;

    private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();
    private final StatsCallback mStatsCallback = new StatsCallback();
    private final MetaDataCallback mMetaDataCallback = new MetaDataCallback();
    private long mFocusStartTime;
    private long mShutterCallbackTime;
    private long mPostViewPictureCallbackTime;
    private long mRawPictureCallbackTime;
    private long mJpegPictureCallbackTime;
    private long mOnResumeTime;
    private byte[] mJpegImageData;

    // These latency time are for the CameraLatency test.
    public long mAutoFocusTime;
    public long mShutterLag;
    public long mShutterToPictureDisplayedTime;
    public long mPictureDisplayedToJpegCallbackTime;
    public long mJpegCallbackFinishTime;
    public long mCaptureStartTime;

    // This handles everything about focus.
    private FocusOverlayManager mFocusManager;

    private String mSceneMode;
    private String mSavedFlashMode = null;

    private final Handler mHandler = new MainHandler();
    private MessageQueue.IdleHandler mIdleHandler = null;

    private PreferenceGroup mPreferenceGroup;

    private boolean mQuickCapture;
    private SensorManager mSensorManager;
    private float[] mGData = new float[3];
    private float[] mMData = new float[3];
    private float[] mR = new float[16];
    private int mHeading = -1;

    private static final int MAX_ZOOM = 10;
    private int[] mZoomIdxTbl = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
    // True if all the parameters needed to start preview is ready.
    private boolean mCameraPreviewParamsReady = false;

    private boolean mAnimateCapture = true;

    private int mJpegFileSizeEstimation = 0;
    private int mRemainingPhotos = -1;
    private static final int SELFIE_FLASH_DURATION = 680;

    private class SelfieThread extends Thread {
        public void run() {
            try {
                Thread.sleep(SELFIE_FLASH_DURATION);
                mActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        mFocusManager.doSnap();
                    }
                });
            } catch(InterruptedException e) {
            }
            selfieThread = null;
        }
    }
    private SelfieThread selfieThread;

    private class MediaSaveNotifyThread extends Thread
    {
        private Uri uri;
        public MediaSaveNotifyThread(Uri uri)
        {
            this.uri = uri;
        }
        public void setUri(Uri uri)
        {
            this.uri = uri;
        }
        public void run()
        {
            while(mLongshotActive) {
                try {
                    Thread.sleep(10);
                } catch(InterruptedException e) {
                }
            }
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    if (uri != null)
                        mActivity.notifyNewMedia(uri);
                    mActivity.updateStorageSpaceAndHint();
                    updateRemainingPhotos();
                }
            });
            mediaSaveNotifyThread = null;
        }
    }

    private MediaSaveNotifyThread mediaSaveNotifyThread;
    private MediaSaveService.OnMediaSavedListener mOnMediaSavedListener =
            new MediaSaveService.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if(mLongshotActive) {
                        if(mediaSaveNotifyThread == null) {
                            mediaSaveNotifyThread = new MediaSaveNotifyThread(uri);
                            mediaSaveNotifyThread.start();
                        }
                        else
                            mediaSaveNotifyThread.setUri(uri);
                    } else {
                        if (uri != null) {
                            mActivity.notifyNewMedia(uri);
                        }
                    }
                }
            };

    private void checkDisplayRotation() {
        // Set the display orientation if display rotation has changed.
        // Sometimes this happens when the device is held upside
        // down and camera app is opened. Rotation animation will
        // take some time and the rotation value we have got may be
        // wrong. Framework does not have a callback for this now.
        if (CameraUtil.getDisplayRotation(mActivity) != mDisplayRotation) {
            setDisplayOrientation();
        }
        if (SystemClock.uptimeMillis() - mOnResumeTime < 5000) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkDisplayRotation();
                }
            }, 100);
        }
    }

    public Parameters getParameters()
    {
        return mParameters;
    }

    /**
     * This Handler is used to post message back onto the main thread of the
     * application
     */
    private class MainHandler extends Handler {
        public MainHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SETUP_PREVIEW: {
                    setupPreview();
                    break;
                }

                case CLEAR_SCREEN_DELAY: {
                    mActivity.getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }

                case FIRST_TIME_INIT: {
                    initializeFirstTime();
                    break;
                }

                case SET_CAMERA_PARAMETERS_WHEN_IDLE: {
                    setCameraParametersWhenIdle(0);
                    break;
                }

                case SHOW_TAP_TO_FOCUS_TOAST: {
                    showTapToFocusToast();
                    break;
                }

                case SWITCH_CAMERA: {
                    switchCamera();
                    break;
                }

                case SWITCH_CAMERA_START_ANIMATION: {
                    // TODO: Need to revisit
                    // ((CameraScreenNail) mActivity.mCameraScreenNail).animateSwitchCamera();
                    break;
                }

                case CAMERA_OPEN_DONE: {
                    onCameraOpened();
                    break;
                }

                case OPEN_CAMERA_FAIL: {
                    mOpenCameraFail = true;
                    CameraUtil.showErrorAndFinish(mActivity,
                            R.string.cannot_connect_camera);
                    break;
                }

                case CAMERA_DISABLED: {
                    mCameraDisabled = true;
                    CameraUtil.showErrorAndFinish(mActivity,
                            R.string.camera_disabled);
                    break;
                }

               case SET_PHOTO_UI_PARAMS: {
                    setCameraParametersWhenIdle(UPDATE_PARAM_PREFERENCE);
                    break;
               }

                case SWITCH_TO_GCAM_MODULE: {
                    mActivity.onModuleSelected(ModuleSwitcher.GCAM_MODULE_INDEX);
                    break;
                }

                case ON_PREVIEW_STARTED: {
                    onPreviewStarted();
                    break;
                }

                case INSTANT_CAPTURE: {
                    onShutterButtonClick();
                    break;
                }

                case UNLOCK_CAM_SHUTTER: {
                    mUI.enableShutter(true);
                    break;
                }

                case SET_FOCUS_RATIO: {
                    mUI.getFocusRing().setRadiusRatio((Float)msg.obj);
                    break;
                }
            }
        }
    }

    public void reinit() {
        mPreferences = ComboPreferences.get(mActivity);
        if (mPreferences == null) {
            mPreferences = new ComboPreferences(mActivity);
        }
        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal(), mActivity);
        mCameraId = getPreferredCameraId(mPreferences);
        mPreferences.setLocalId(mActivity, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());
    }

    @Override
    public void init(CameraActivity activity, View parent) {
        mActivity = activity;
        mRootView = parent;
        mOrientationOffset = CameraUtil.isDefaultToPortrait(mActivity) ? 0 : 90;
        mPreferences = ComboPreferences.get(mActivity);
        if (mPreferences == null) {
            mPreferences = new ComboPreferences(mActivity);
        }

        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal(), activity);
        mCameraId = getPreferredCameraId(mPreferences);
        mContentResolver = mActivity.getContentResolver();
        mApplicationContext = CameraApp.getContext();

        // Surface texture is from camera screen nail and startPreview needs it.
        // This must be done before startPreview.
        mIsImageCaptureIntent = isImageCaptureIntent();

        mPreferences.setLocalId(mActivity, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        mUI = new SPhotoUI(activity, this, parent);

        // Power shutter
        mActivity.initPowerShutter(mPreferences);

        // Max brightness
        mActivity.initMaxBrightness(mPreferences);

        if (mOpenCameraThread == null) {
            mOpenCameraThread = new OpenCameraThread();
            mOpenCameraThread.start();
        }
        initializeControlByIntent();
        mQuickCapture = mActivity.getIntent().getBooleanExtra(EXTRA_QUICK_CAPTURE, false);
        mLocationManager = new LocationManager(mActivity, this);
        mSensorManager = (SensorManager)(mActivity.getSystemService(Context.SENSOR_SERVICE));

        mBokehTipText = (TextView) mRootView.findViewById(R.id.bokeh_tip_text);

        Storage.setSaveSDCard(
            mPreferences.getString(CameraSettings.KEY_CAMERA_SAVEPATH, "0").equals("1"));
    }

    private void initializeControlByIntent() {
        mUI.initializeControlByIntent();
        if (mIsImageCaptureIntent) {
            setupCaptureParams();
        }
    }

    private void onPreviewStarted() {
        if (mCameraState == SNAPSHOT_IN_PROGRESS) {
            return;
        }
        setCameraState(IDLE);
        mFocusManager.onPreviewStarted();
        startFaceDetection();
        locationFirstRun();
        mUI.enableShutter(true);
    }

    // Prompt the user to pick to record location for the very first run of
    // camera only
    private void locationFirstRun() {
        boolean enableRecordingLocation = false;
        if (mActivity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableRecordingLocation = true;
        }
        /* Do not prompt if the preference is already set, this is a secure
         * camera session, or the prompt has already been triggered. */
        if ((RecordLocationPreference.isSet(
                mPreferences, CameraSettings.KEY_RECORD_LOCATION) && enableRecordingLocation) ||
                mActivity.isSecureCamera() || mLocationPromptTriggered) {
            return;
        }
        // Check if the back camera exists
        int backCameraId = CameraHolder.instance().getBackCameraId();
        if (backCameraId == -1) {
            // If there is no back camera, do not show the prompt.
            return;
        }

        mLocationPromptTriggered = true;

        /* Enable the location at the begining, always.
           If the user denies the permission, it will be disabled
           right away due to exception */
        enableRecordingLocation(enableRecordingLocation);
    }

    @Override
    public void waitingLocationPermissionResult(boolean result) {
        mLocationManager.waitingLocationPermissionResult(result);
    }

    @Override
    public void enableRecordingLocation(boolean enable) {
        setLocationPreference(enable ? RecordLocationPreference.VALUE_ON
                : RecordLocationPreference.VALUE_OFF);
        mLocationManager.recordLocation(enable);
    }

    @Override
    public void setPreferenceForTest(String key, String value) {
        mUI.setPreference(key, value);
        onSharedPreferenceChanged();
    }

    @Override
    public void onPreviewUIReady() {
        if (mPaused || mCameraDevice == null) {
            return;
        }
        Log.v(TAG, "onPreviewUIReady");
        if (mCameraState == PREVIEW_STOPPED) {
            startPreview();
        } else {
            synchronized (mCameraDevice) {
                SurfaceHolder sh = mUI.getSurfaceHolder();
                if (sh == null) {
                    Log.w(TAG, "startPreview: holder for preview are not ready.");
                    return;
                }
                mCameraDevice.setPreviewDisplay(sh);
            }
        }
    }

    @Override
    public void onPreviewUIDestroyed() {
        if (mCameraDevice == null) {
            return;
        }
        try {
            if (mOpenCameraThread != null) {
                mOpenCameraThread.join();
                mOpenCameraThread = null;
            }
        } catch (InterruptedException ex) {
            // ignore
        }
        stopPreview();
    }

    private void setLocationPreference(String value) {
        mPreferences.edit()
                .putString(CameraSettings.KEY_RECORD_LOCATION, value)
                .apply();
        // TODO: Fix this to use the actual onSharedPreferencesChanged listener
        // instead of invoking manually
        if (mUI.mMenuInitialized) {
            onSharedPreferenceChanged();
        }
    }

    private void onCameraOpened() {
        if (mPaused) {
            return;
        }
        Log.v(TAG, "onCameraOpened");
        openCameraCommon();
        resizeForPreviewAspectRatio();
        mFocusManager.setFocusRing(mUI.getFocusRing());
    }

    private void switchCamera() {
        if (mPaused) return;

        mUI.applySurfaceChange(SPhotoUI.SURFACE_STATUS.HIDE);
        Log.v(TAG, "Start to switch camera. id=" + mPendingSwitchCameraId);
        mCameraId = mPendingSwitchCameraId;
        mPendingSwitchCameraId = -1;
        mSnapshotOnIdle = false;
        setCameraId(mCameraId);

        try {
            if (mOpenCameraThread != null) {
                mOpenCameraThread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mOpenCameraThread = null;

        // from onPause
        try {
            if (mOpenCameraThread != null) {
                mOpenCameraThread.join();
                mOpenCameraThread = null;
            }
        } catch (InterruptedException ex) {
            // ignore
        }
        closeCamera();
        mUI.collapseCameraControls();
        if (mFocusManager != null) mFocusManager.removeMessages();

        // Restart the camera and initialize the UI. From onCreate.
        mPreferences.setLocalId(mActivity, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());
        mCameraDevice = CameraUtil.openCamera(
                mActivity, mCameraId, mHandler,
                mActivity.getCameraOpenErrorCallback());

        if (mCameraDevice == null) {
            Log.e(TAG, "Failed to open camera:" + mCameraId + ", aborting.");
            return;
        }
        mParameters = mCameraDevice.getParameters();
        mInitialParams = mCameraDevice.getParameters();
        initializeCapabilities();
        CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
        mMirror = (info.facing == CameraInfo.CAMERA_FACING_FRONT);
        mFocusManager.setMirror(mMirror);
        mFocusManager.setParameters(mInitialParams);
        setupPreview();

        mUI.applySurfaceChange(SPhotoUI.SURFACE_STATUS.SURFACE_VIEW);
        // reset zoom value index
        mZoomValue = 0;
        resizeForPreviewAspectRatio();
        openCameraCommon();

        // Start switch camera animation. Post a message because
        // onFrameAvailable from the old camera may already exist.
        mHandler.sendEmptyMessage(SWITCH_CAMERA_START_ANIMATION);
    }

    protected void setCameraId(int cameraId) {
        ListPreference pref = mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_ID);
        pref.setValue("" + cameraId);
    }

    // either open a new camera or switch cameras
    private void openCameraCommon() {
        loadCameraPreferences();

        mUI.onCameraOpened(mPreferenceGroup, mPreferences, mParameters, this);
        updateCameraSettings();
        showTapToFocusToastIfNeeded();
        resetMiscSettings();
    }

    @Override
    public void onScreenSizeChanged(int width, int height) {
        if (mFocusManager != null) mFocusManager.setPreviewSize(width, height);
    }

    @Override
    public void onPreviewRectChanged(Rect previewRect) {
        if (mFocusManager != null) mFocusManager.setPreviewRect(previewRect);
    }

    private void resetMiscSettings() {
        boolean disableQcomMiscSetting = PersistUtil.isDisableQcomMiscSetting();
        if (disableQcomMiscSetting) {
            mUI.setPreference(CameraSettings.KEY_FACE_DETECTION,
                    ParametersWrapper.FACE_DETECTION_OFF);
            mUI.setPreference(CameraSettings.KEY_FOCUS_MODE,
                    Parameters.FOCUS_MODE_AUTO);
            mUI.setPreference(CameraSettings.KEY_FLASH_MODE,
                    Parameters.FLASH_MODE_OFF);
            onSharedPreferenceChanged();
        }
    }

    void setPreviewFrameLayoutCameraOrientation(){
        CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
        //if camera mount angle is 0 or 180, we want to resize preview
        if (info.orientation % 180 == 0){
            mUI.cameraOrientationPreviewResize(true);
        } else{
            mUI.cameraOrientationPreviewResize(false);
        }
    }

    @Override
    public void resizeForPreviewAspectRatio() {
        if ( mCameraDevice == null || mParameters == null) {
            Log.e(TAG, "Camera not yet initialized");
            return;
        }
        setPreviewFrameLayoutCameraOrientation();
        Size size = mParameters.getPreviewSize();
        Log.i(TAG, "Using preview width = " + size.width + "& height = " + size.height);
        mUI.setAspectRatio((float) size.width / size.height);
    }

    @Override
    public void onSwitchSavePath() {
        if (mUI.mMenuInitialized) {
            mUI.setPreference(CameraSettings.KEY_CAMERA_SAVEPATH, "1");
        } else {
            mPreferences.edit()
                    .putString(CameraSettings.KEY_CAMERA_SAVEPATH, "1")
                    .apply();
        }
        RotateTextToast.makeText(mActivity, R.string.on_switch_save_path_to_sdcard,
                Toast.LENGTH_SHORT).show();
    }

    // Snapshots can only be taken after this is called. It should be called
    // once only. We could have done these things in onCreate() but we want to
    // make preview screen appear as soon as possible.
    private void initializeFirstTime() {
        if (mFirstTimeInitialized || mPaused) {
            return;
        }

        // Initialize location service.
        boolean recordLocation = RecordLocationPreference.get(mPreferences,
                CameraSettings.KEY_RECORD_LOCATION);
        mLocationManager.recordLocation(recordLocation);

        mUI.initializeFirstTime();
        MediaSaveService s = mActivity.getMediaSaveService();
        // We set the listener only when both service and shutterbutton
        // are initialized.
        if (s != null) {
            s.setListener(this);
        }

        mSNamedImages = new SNamedImages();

        mFirstTimeInitialized = true;
        Log.d(TAG, "addIdleHandler in first time initialization");
        addIdleHandler();

    }

    // If the activity is paused and resumed, this method will be called in
    // onResume.
    private void initializeSecondTime() {
        // Start location update if needed.
        boolean recordLocation = RecordLocationPreference.get(mPreferences,
                CameraSettings.KEY_RECORD_LOCATION);
        mLocationManager.recordLocation(recordLocation);
        MediaSaveService s = mActivity.getMediaSaveService();
        if (s != null) {
            s.setListener(this);
        }
        mSNamedImages = new SNamedImages();
        if (!mIsImageCaptureIntent) {
            mUI.showSwitcher();
        }
        mUI.initializeSecondTime(mParameters);
    }

    private void showTapToFocusToastIfNeeded() {
        // Show the tap to focus toast if this is the first start.
        if (mFocusAreaSupported &&
                mPreferences.getBoolean(CameraSettings.KEY_CAMERA_FIRST_USE_HINT_SHOWN, true)) {
            // Delay the toast for one second to wait for orientation.
            mHandler.sendEmptyMessageDelayed(SHOW_TAP_TO_FOCUS_TOAST, 1000);
        }
    }

    private void addIdleHandler() {
        if (mIdleHandler == null) {
            mIdleHandler = new MessageQueue.IdleHandler() {
                @Override
                public boolean queueIdle() {
                    Storage.ensureOSXCompatible();
                    return false;
                }
            };

            MessageQueue queue = Looper.myQueue();
            queue.addIdleHandler(mIdleHandler);
        }
    }

    private void removeIdleHandler() {
        if (mIdleHandler != null) {
            MessageQueue queue = Looper.myQueue();
            queue.removeIdleHandler(mIdleHandler);
            mIdleHandler = null;
        }
    }

    @Override
    public void startFaceDetection() {
        if (mCameraDevice == null) return;

        if (mFaceDetectionEnabled == false
               || mFaceDetectionStarted || mCameraState != IDLE) return;
        if (mParameters.getMaxNumDetectedFaces() > 0) {
            mFaceDetectionStarted = true;
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
            mUI.onStartFaceDetection(mDisplayOrientation,
                    (info.facing == CameraInfo.CAMERA_FACING_FRONT));
            mCameraDevice.setFaceDetectionCallback(mHandler, mUI);
            mCameraDevice.startFaceDetection();
        }
    }

    @Override
    public void stopFaceDetection() {
        if (mFaceDetectionEnabled == false || !mFaceDetectionStarted) return;
        if (mParameters.getMaxNumDetectedFaces() > 0) {
            mFaceDetectionStarted = false;
            mCameraDevice.setFaceDetectionCallback(null, null);
            mCameraDevice.stopFaceDetection();
            mUI.onStopFaceDetection();
        }
    }

    @Override
    public void setFocusRatio(float ratio) {
        mHandler.removeMessages(SET_FOCUS_RATIO);
        Message m = mHandler.obtainMessage(SET_FOCUS_RATIO);
        m.obj = ratio;
        mHandler.sendMessage(m);
    }

    // TODO: need to check cached background apps memory and longshot ION memory
    private boolean isLongshotNeedCancel() {

        if (PERSIST_SKIP_MEM_CHECK == true) {
            return false;
        }

        if (Storage.getAvailableSpace() <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            Log.w(TAG, "current storage is full");
            return true;
        }
        if (SECONDARY_SERVER_MEM == 0) {
            ActivityManager am = (ActivityManager) mActivity.getSystemService(
                    Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memInfo);
            SECONDARY_SERVER_MEM = memInfo.secondaryServerThreshold;
        }

        long totalMemory = Runtime.getRuntime().totalMemory();
        long maxMemory = Runtime.getRuntime().maxMemory();
        long remainMemory = maxMemory - totalMemory;

        MemInfoReader reader = new MemInfoReader();
        reader.readMemInfo();
        long[] info = reader.getRawInfo();
        long availMem = (info[Debug.MEMINFO_FREE] + info[Debug.MEMINFO_CACHED]) * 1024;

        if (availMem <= SECONDARY_SERVER_MEM || remainMemory <= LONGSHOT_CANCEL_THRESHOLD) {
            Log.e(TAG, "cancel longshot: free=" + info[Debug.MEMINFO_FREE] * 1024
                    + " cached=" + info[Debug.MEMINFO_CACHED] * 1024
                    + " threshold=" + SECONDARY_SERVER_MEM);
            mLongshotActive = false;
            RotateTextToast.makeText(mActivity,R.string.msg_cancel_longshot_for_limited_memory,
                Toast.LENGTH_SHORT).show();
            return true;
        }

        return false;
    }

    private final class LongshotShutterCallback
            implements CameraShutterCallback {
        private int mExpectedLongshotSnapNum;

        public LongshotShutterCallback() {
            mExpectedLongshotSnapNum = mLongshotSnapNum;
        }

        @Override
        public void onShutter(CameraProxy camera) {
            mShutterCallbackTime = System.currentTimeMillis();
            mShutterLag = mShutterCallbackTime - mCaptureStartTime;
            Log.e(TAG, "[KPI Perf] PROFILE_SHUTTER_LAG mShutterLag = " + mShutterLag + "ms");
            synchronized(mCameraDevice) {
                if (mExpectedLongshotSnapNum != mLongshotSnapNum) {
                    return;
                }

                if (++mLongshotSnapNum >= mLongShotMaxSnap &&
                    (mLongShotMaxSnap != -1)) {
                    mLongshotActive = false;
                    mUI.enableShutter(false);
                    mCameraDevice.stopLongshot();
                    return;
                }

                if (mCameraState != LONGSHOT ||
                    !mLongshotActive) {
                    mCameraDevice.stopLongshot();
                    return;
                }

                if(isLongshotNeedCancel()) {
                    return;
                }

                if(mLongShotCaptureCount == mLongShotCaptureCountLimit) {
                    mLongshotActive = false;
                    return;
                }

                Location loc = getLocationAccordPictureFormat(mParameters.get(KEY_PICTURE_FORMAT));

                mLongShotCaptureCount++;
                if (mLongshotSave) {
                    mCameraDevice.takePicture(mHandler,
                            new LongshotShutterCallback(),
                            mRawPictureCallback, mPostViewPictureCallback,
                            new LongshotPictureCallback(loc));
                } else {
                    mCameraDevice.takePicture(mHandler,new LongshotShutterCallback(),
                            mRawPictureCallback, mPostViewPictureCallback,
                            new JpegPictureCallback(loc));
                }
            }
        }
    }

    private final class ShutterCallback
            implements CameraShutterCallback {

        private boolean mNeedsAnimation;

        public ShutterCallback(boolean needsAnimation) {
            mNeedsAnimation = needsAnimation;
        }

        @Override
        public void onShutter(CameraProxy camera) {
            mShutterCallbackTime = System.currentTimeMillis();
            mShutterLag = mShutterCallbackTime - mCaptureStartTime;
            Log.e(TAG, "[KPI Perf] PROFILE_SHUTTER_LAG mShutterLag = " + mShutterLag + "ms");
            if (mNeedsAnimation) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        animateAfterShutter();
                    }
                });
            }
        }
    }

    private final class StatsCallback
           implements android.hardware.Camera.CameraDataCallback {
            @Override
        public void onCameraData(int [] data, android.hardware.Camera camera) {
            //if(!mPreviewing || !mFirstTimeInitialized){
            if(!mFirstTimeInitialized){
                return;
            }
            //The first element in the array stores max hist value . Stats data begin from second value
            synchronized(statsdata) {
                System.arraycopy(data,0,statsdata,0,STATS_DATA);
            }
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    if(mGraphView != null)
                        mGraphView.PreviewChanged();
                }
           });
        }
    }

    private final class MetaDataCallback
           implements android.hardware.Camera.CameraMetaDataCallback{
        private static final int QCAMERA_METADATA_HDR = 3;
        private static final int QCAMERA_METADATA_RTB = 5;
        private int mLastMessage = -1;

        @Override
        public void onCameraMetaData (byte[] data, android.hardware.Camera camera) {
            int metadata[] = new int[3];
            if (data.length >= 12) {
                for (int i =0;i<3;i++) {
                    metadata[i] = byteToInt( (byte []) data, i*4);
                }
                /* Checking if the meta data is for auto HDR */
                if (metadata[0] == QCAMERA_METADATA_RTB) {
                    final String tip;
                    Log.d(TAG,"QCAMERA_METADATA_RTB msgtype =" +metadata[2]);
                    switch (metadata[2]) {
                        case TOO_FAR:
                            tip = "Too far";
                            break;
                        case TOO_NEAR:
                            tip = "Too near";
                            break;
                        case LOW_LIGHT:
                            tip = "Low light";
                            break;
                        case SUBJECT_NOT_FOUND:
                            tip = "Object not found";
                            break;
                        case DEPTH_EFFECT_SUCCESS:
                            tip = "Depth effect success";
                            break;
                        case NO_DEPTH_EFFECT:
                            tip = "NO depth effect";
                            break;
                        default:
                            tip = "Message type =" + metadata[2];
                            break;
                    }
                    mDepthSuccess = metadata[2] == DEPTH_EFFECT_SUCCESS;
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mBokehTipText != null) {
                                if (!mDepthSuccess) {
                                    mBokehTipText.setVisibility(View.VISIBLE);
                                    mBokehTipText.setText(tip);
                                } else {
                                    mBokehTipText.setVisibility(View.GONE);
                                }
                            }
                            mUI.enableBokehFocus(mDepthSuccess);
                        }
                    });
                }
            }
        }

        private int byteToInt (byte[] b, int offset) {
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int shift = (4 - 1 - i) * 8;
                value += (b[(3-i) + offset] & 0x000000FF) << shift;
            }
            return value;
        }
    }

    private final class PostViewPictureCallback
            implements CameraPictureCallback {
        @Override
        public void onPictureTaken(byte [] data, CameraProxy camera) {
            Log.d(TAG, "PostViewPictureCallback: onPictureTaken()");
            mPostViewPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToPostViewCallbackTime = "
                    + (mPostViewPictureCallbackTime - mShutterCallbackTime)
                    + "ms");
        }
    }

    private final class RawPictureCallback
            implements CameraPictureCallback {
        @Override
        public void onPictureTaken(byte [] rawData, CameraProxy camera) {
            Log.d(TAG, "RawPictureCallback: onPictureTaken()");
            mRawPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToRawCallbackTime = "
                    + (mRawPictureCallbackTime - mShutterCallbackTime) + "ms");
        }
    }

    private final class LongshotPictureCallback implements CameraPictureCallback {
        Location mLocation;

        public LongshotPictureCallback(Location loc) {
            mLocation = loc;
        }

        @Override
        public void onPictureTaken(final byte [] jpegData, CameraProxy camera) {
            Log.d(TAG, "LongshotPictureCallback: onPictureTaken()");
            if (mPaused) {
                return;
            }

            String jpegFilePath = new String(jpegData);
            mSNamedImages.nameNewImage(mCaptureStartTime);
            SNamedEntity name = mSNamedImages.getNextNameEntity();
            String title = (name == null) ? null : name.title;
            long date = (name == null) ? -1 : name.date;

            if (title == null) {
                Log.e(TAG, "Unbalanced name/data pair");
                return;
            }


            if  (date == -1 ) {
                Log.e(TAG, "Invalid filename date");
                return;
            }

            String dstPath = Storage.DIRECTORY;
            File sdCard = android.os.Environment.getExternalStorageDirectory();
            File dstFile = new File(dstPath);
            if (dstFile == null) {
                Log.e(TAG, "Destination file path invalid");
                return;
            }

            File srcFile = new File(jpegFilePath);
            if (srcFile == null) {
                Log.e(TAG, "Source file path invalid");
                return;
            }

            if ( srcFile.renameTo(dstFile) ) {
                Size s = mParameters.getPictureSize();
                String pictureFormat = mParameters.get(KEY_PICTURE_FORMAT);
                Log.d(TAG, "capture:" + title + "." + pictureFormat);
                mActivity.getMediaSaveService().addImage(
                       null, title, date, mLocation, s.width, s.height,
                       0, null, mOnMediaSavedListener, mContentResolver, pictureFormat);
            } else {
                Log.e(TAG, "Failed to move jpeg file");
            }
        }
    }

    private byte[] flipJpeg(byte[] jpegData, int orientation, int jpegOrientation) {
        Bitmap srcBitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
        Matrix m = new Matrix();
        if(orientation == 270 || orientation == 90) {
            // Judge whether the picture or phone is horizontal screen
            if (jpegOrientation == 0 || jpegOrientation == 180) {
                m.preScale(-1, 1);
            } else { // the picture or phone is Vertical screen
                m.preScale(1, -1);
            }
        }
        Bitmap dstBitmap = Bitmap.createBitmap(srcBitmap, 0, 0, srcBitmap.getWidth(), srcBitmap.getHeight(), m, false);
        dstBitmap.setDensity(DisplayMetrics.DENSITY_DEFAULT);
        int size = dstBitmap.getWidth() * dstBitmap.getHeight();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream(size);
        dstBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream);

        return outStream.toByteArray();
    }

    public static byte[] addExifTags(byte[] jpeg, int orientationInDegree) {
        ExifInterface exif = new ExifInterface();
        exif.addOrientationTag(orientationInDegree);
        ByteArrayOutputStream jpegOut = new ByteArrayOutputStream();
        try {
            exif.writeExif(jpeg, jpegOut);
        } catch (IOException e) {
            Log.e(TAG, "Could not write EXIF", e);
        }
        return jpegOut.toByteArray();
    }

    private final class JpegPictureCallback
            implements CameraPictureCallback {
        public static final int GDEPTH_SIZE = 1280 * 960;
        Location mLocation;
        byte[] mBokeh;
        byte[] mOrigin;
        byte[] mDepth;
        int mCallTime = 0;

        public JpegPictureCallback(Location loc) {
            mLocation = loc;
        }

        @Override
        public void onPictureTaken(byte [] jpegData, CameraProxy camera) {
            mCallTime ++;
            if (mIsBokehMode && !PERSIST_BOKEH_DEBUG_CHECK && mSaveBokehXmp) {
                if (jpegData != null && mCallTime == 1) {
                    mBokeh = jpegData;
                }
                if (jpegData != null && mCallTime == 2 && mOrigin == null) {
                    mOrigin = jpegData;
                }
                if (jpegData != null && mCallTime == 3) {
                    mDepth = jpegData;
                    jpegData = mBokeh;
                }
            }
            mUI.stopSelfieFlash();
            if (mCameraState != LONGSHOT) {
                mUI.enableShutter(true);
            }
            if (mUI.isPreviewCoverVisible()) {
                 // When take picture request is sent before starting preview, onPreviewFrame()
                 // callback doesn't happen so removing preview cover here, instead.
                 mUI.hidePreviewCover();
            }
            if (mPaused) {
                return;
            }
            if (mIsImageCaptureIntent) {
                stopPreview();
            } else if (mSceneMode == CameraUtil.SCENE_MODE_HDR) {
                mUI.showSwitcher();
                mUI.setSwipingEnabled(true);
            }

            ExifInterface exif = Exif.getExif(jpegData);
            boolean overrideMakerAndModelTag = false;
            if (mApplicationContext != null) {
                overrideMakerAndModelTag =
                    mApplicationContext.getResources()
                       .getBoolean(R.bool.override_maker_and_model_tag);
            }

            if (overrideMakerAndModelTag) {
                ExifTag maker = exif.buildTag(ExifInterface.TAG_MAKE, Build.MANUFACTURER);
                exif.setTag(maker);
                ExifTag model = exif.buildTag(ExifInterface.TAG_MODEL, Build.MODEL);
                exif.setTag(model);
            }

            mReceivedSnapNum = mReceivedSnapNum + 1;
            mJpegPictureCallbackTime = System.currentTimeMillis();

            Log.v(TAG, "JpegPictureCallback: Received = " + mReceivedSnapNum + " " +
                      "Burst count = " + mBurstSnapNum);
            // If postview callback has arrived, the captured image is displayed
            // in postview callback. If not, the captured image is displayed in
            // raw picture callback.
            if (mPostViewPictureCallbackTime != 0) {
                mShutterToPictureDisplayedTime =
                        mPostViewPictureCallbackTime - mShutterCallbackTime;
                mPictureDisplayedToJpegCallbackTime =
                        mJpegPictureCallbackTime - mPostViewPictureCallbackTime;
            } else {
                mShutterToPictureDisplayedTime =
                        mRawPictureCallbackTime - mShutterCallbackTime;
                mPictureDisplayedToJpegCallbackTime =
                        mJpegPictureCallbackTime - mRawPictureCallbackTime;
            }
            Log.v(TAG, "mPictureDisplayedToJpegCallbackTime = "
                    + mPictureDisplayedToJpegCallbackTime + "ms");

            if (isLongshotDone()) {
                mCameraDevice.setLongshot(false);
            }

            boolean needRestartPreview = !mIsImageCaptureIntent
                    && !mPreviewRestartSupport
                    && (mCameraState != LONGSHOT)
                    && ((mReceivedSnapNum == mBurstSnapNum) && (mCameraState != LONGSHOT));

            needRestartPreview |= isLongshotDone();

            boolean backCameraRestartPreviewOnPictureTaken = false;
            boolean frontCameraRestartPreviewOnPictureTaken = false;
            boolean additionalCameraRestartPreviewOnPictureTaken = false;
            if (mApplicationContext != null) {
                backCameraRestartPreviewOnPictureTaken =
                    mApplicationContext.getResources().getBoolean(R.bool.back_camera_restart_preview_onPictureTaken);
                frontCameraRestartPreviewOnPictureTaken =
                    mApplicationContext.getResources().getBoolean(R.bool.front_camera_restart_preview_onPictureTaken);
            additionalCameraRestartPreviewOnPictureTaken =
                    mApplicationContext.getResources().getBoolean(R.bool.additional_camera_restart_preview_onPictureTaken);
            }

            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
            if ((info.facing == CameraInfo.CAMERA_FACING_BACK
                    && backCameraRestartPreviewOnPictureTaken && (mCameraState != LONGSHOT))
                    || (info.facing == CameraInfo.CAMERA_FACING_FRONT
                    && frontCameraRestartPreviewOnPictureTaken && (mCameraState != LONGSHOT))
                    || (info.facing > CameraInfo.CAMERA_FACING_FRONT
                    && additionalCameraRestartPreviewOnPictureTaken && (mCameraState != LONGSHOT))) {
                needRestartPreview = true;
            }

            if (needRestartPreview) {
                Log.d(TAG, "JpegPictureCallback: needRestartPreview");
                setupPreview();
                if (CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE.equals(mFocusManager.getFocusMode(false))
                        || CameraUtil.FOCUS_MODE_MW_CONTINUOUS_PICTURE.equals(mFocusManager.getFocusMode(false))) {
                    mCameraDevice.cancelAutoFocus();
                }
            } else if (((mCameraState != LONGSHOT) && (mReceivedSnapNum == mBurstSnapNum))
                        || isLongshotDone()){
                mFocusManager.restartTouchFocusTimer();
                if (CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE.equals(mFocusManager.getFocusMode(false))
                        || CameraUtil.FOCUS_MODE_MW_CONTINUOUS_PICTURE.equals(mFocusManager.getFocusMode(false))) {
                    mCameraDevice.cancelAutoFocus();
                }
                if (!mIsImageCaptureIntent) {
                    setCameraState(IDLE);
                }
                startFaceDetection();
            }

            int orientation = Exif.getOrientation(exif);
            if(mCameraId == CameraHolder.instance().getFrontCameraId()) {
                IconListPreference selfieMirrorPref = (IconListPreference) mPreferenceGroup
                        .findPreference(CameraSettings.KEY_SELFIE_MIRROR);
                if (selfieMirrorPref != null && selfieMirrorPref.getValue() != null &&
                        selfieMirrorPref.getValue().equalsIgnoreCase("enable")) {
                    jpegData = flipJpeg(jpegData, info.orientation, orientation);
                    jpegData = addExifTags(jpegData, orientation);
                }
            }
            if (!mIsImageCaptureIntent) {
                // Burst snapshot. Generate new image name.
                if (mReceivedSnapNum > 1) {
                    mSNamedImages.nameNewImage(mCaptureStartTime);
                }
                // Calculate the width and the height of the jpeg.
                Size s = mParameters.getPictureSize();
                int width, height;
                if ((mJpegRotation + orientation) % 180 == 0) {
                    width = s.width;
                    height = s.height;
                } else {
                    width = s.height;
                    height = s.width;
                }
                String pictureFormat = mParameters.get(KEY_PICTURE_FORMAT);
                if (pictureFormat != null && !pictureFormat.equalsIgnoreCase(PIXEL_FORMAT_JPEG)) {
                    // overwrite width and height if raw picture
                    String pair = mParameters.get(KEY_QC_RAW_PICUTRE_SIZE);
                    if (pair != null) {
                        int pos = pair.indexOf('x');
                        if (pos != -1) {
                            width = Integer.parseInt(pair.substring(0, pos));
                            height = Integer.parseInt(pair.substring(pos + 1));
                        }
                    }
                }
                SNamedEntity name = mSNamedImages.getNextNameEntity();
                String title = (name == null) ? null : name.title;
                long date = (name == null) ? -1 : name.date;
                // Handle debug mode outputs
                if (mDebugUri != null) {
                    // If using a debug uri, save jpeg there.
                    saveToDebugUri(jpegData);
                    // Adjust the title of the debug image shown in mediastore.
                    if (title != null) {
                        title = DEBUG_IMAGE_PREFIX + title;
                    }
                }
                if (title == null) {
                    Log.e(TAG, "Unbalanced name/data pair");
                } else {
                    if (date == -1) {
                        date = mCaptureStartTime;
                    }
                    if (mHeading >= 0) {
                        // heading direction has been updated by the sensor.
                        ExifTag directionRefTag = exif.buildTag(
                                ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
                                ExifInterface.GpsTrackRef.MAGNETIC_DIRECTION);
                        ExifTag directionTag = exif.buildTag(
                                ExifInterface.TAG_GPS_IMG_DIRECTION,
                                new Rational(mHeading, 1));
                        exif.setTag(directionRefTag);
                        exif.setTag(directionTag);
                    }
                    String mPictureFormat = mParameters.get(KEY_PICTURE_FORMAT);
                    Log.d(TAG, "capture:" + title + "." + mPictureFormat);
                    if (mIsBokehMode) {
                        if (!PERSIST_BOKEH_DEBUG_CHECK && mSaveBokehXmp) {
                            if (jpegData != null && mCallTime == 3) {
                                if (mOrigin != null && mBokeh != null) {
                                    GImage gImage = new GImage(mOrigin, "image/jpeg");
                                    GDepth gDepth = GDepth.createGDepth(mDepth);
                                    gDepth.setRoi(new Rect(0,0,width,height));
                                    mActivity.getMediaSaveService().addXmpImage(mBokeh,gImage,
                                            gDepth,"bokeh_"+title,date,mLocation,width,height,
                                            orientation,exif,mOnMediaSavedListener,
                                            mContentResolver,mPictureFormat);
                                }
                            }
                        } else {
                            if (mCallTime == 3) {
                                mActivity.getMediaSaveService().addImage(mDepth,
                                        title, date, mLocation, width, height,
                                        orientation, exif, mOnMediaSavedListener,
                                        mContentResolver, mPictureFormat);
                            } else {
                                mActivity.getMediaSaveService().addImage(
                                        jpegData, title, date, mLocation, width, height,
                                        orientation, exif, mOnMediaSavedListener,
                                        mContentResolver, mPictureFormat);
                            }
                        }
                    } else {
                         mActivity.getMediaSaveService().addImage(
                                 jpegData, title, date, mLocation, width, height,
                                 orientation, exif, mOnMediaSavedListener,
                                 mContentResolver, mPictureFormat);
                    }
                }
                    // Animate capture with real jpeg data instead of a preview frame.
                    if ((mCameraState != LONGSHOT) ||
                        isLongshotDone()) {
                        Size pic_size = mParameters.getPictureSize();
                        if ((pic_size.width <= 352) && (pic_size.height<= 288)) {
                            mUI.setDownFactor(2); //Downsample by 2 for CIF & below
                        } else {
                            mUI.setDownFactor(4);
                        }
                        if (mAnimateCapture) {
                            mUI.animateCapture(jpegData);
                        }
                    } else {
                        // In long shot mode, we do not want to update the preview thumbnail
                        // for each snapshot, instead, keep the last jpeg data and orientation,
                        // use it to show the final one at the end of long shot.
                        mLastJpegData = jpegData;
                        mLastJpegOrientation = orientation;
                    }

                } else {
                    stopPreview();
                    mJpegImageData = jpegData;
                    if (!mQuickCapture) {
                        mUI.showCapturedImageForReview(jpegData, orientation, false);
                    } else {
                        onCaptureDone();
                    }
                }
                if(!mLongshotActive) {
                    mActivity.updateStorageSpaceAndHint(
                            new CameraActivity.OnStorageUpdateDoneListener() {
                        @Override
                        public void onStorageUpdateDone(long storageSpace) {
                            mUI.updateRemainingPhotos(--mRemainingPhotos);
                        }
                    });
                } else {
                    mUI.updateRemainingPhotos(--mRemainingPhotos);
                }
                long now = System.currentTimeMillis();
                mJpegCallbackFinishTime = now - mJpegPictureCallbackTime;
                Log.v(TAG, "mJpegCallbackFinishTime = "
                        + mJpegCallbackFinishTime + "ms");
                if (mReceivedSnapNum == mBurstSnapNum) {
                    mJpegPictureCallbackTime = 0;
                }

                if (isLongshotDone()) {
                    mLongshotSnapNum = 0;
                }
                if ((mSnapshotMode ==CameraInfoWrapper.CAMERA_SUPPORT_MODE_ZSL)) {
                    mActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        if (mGraphView != null) {
                            mGraphView.setVisibility(View.VISIBLE);
                            mGraphView.PreviewChanged();
                        }
                    }
                });
            }
            if (mCameraState != LONGSHOT && mReceivedSnapNum == mBurstSnapNum &&
                    !mIsImageCaptureIntent) {
                cancelAutoFocus();
            }
        }
    }

    private OnSeekBarChangeListener mBlurDegreeListener = new OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
        }
        public void onProgressChanged(SeekBar bar, int progress, boolean fromtouch) {
            if (mParameters != null) {
                mParameters.set(CameraSettings.KEY_QC_BOKEH_BLUR_VALUE, progress);
                mCameraDevice.setParameters(mParameters);
                Log.d(TAG,"seekbar bokeh degree = "+ progress);
                mUI.setBokehRenderDegree(progress);
            }
        }
        public void onStopTrackingTouch(SeekBar bar) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(CameraSettings.KEY_BOKEH_BLUR_VALUE, bar.getProgress());
            editor.apply();
        }
    };

    private final class AutoFocusCallback implements CameraAFCallback {
        @Override
        public void onAutoFocus(
                boolean focused, CameraProxy camera) {
            if (mPaused) return;

            mAutoFocusTime = System.currentTimeMillis() - mFocusStartTime;
            Log.v(TAG, "mAutoFocusTime = " + mAutoFocusTime + "ms");
            //don't reset the camera state while capture is in progress
            //otherwise, it might result in another takepicture
            switch (mCameraState) {
                case PhotoController.LONGSHOT:
                case SNAPSHOT_IN_PROGRESS:
                    break;
                default:
                    setCameraState(IDLE);
                    break;
            }
            mCameraDevice.refreshParameters();
            mFocusManager.setParameters(mCameraDevice.getParameters());
            mFocusManager.onAutoFocus(focused, mUI.isShutterPressed());
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private final class AutoFocusMoveCallback
            implements CameraAFMoveCallback {
        @Override
        public void onAutoFocusMoving(
                boolean moving, CameraProxy camera) {
            mCameraDevice.refreshParameters();
            mFocusManager.setParameters(mCameraDevice.getParameters());
            mFocusManager.onAutoFocusMoving(moving);
        }
    }

    /**
     * This class is just a thread-safe queue for name,date holder objects.
     */
    public static class SNamedImages {
        private Vector<SNamedEntity> mQueue;

        public SNamedImages() {
            mQueue = new Vector<SNamedEntity>();
        }

        public void nameNewImage(long date) {
            SNamedEntity r = new SNamedEntity();
            r.title = CameraUtil.createJpegName(date);
            r.date = date;
            mQueue.add(r);
        }

        public SNamedEntity getNextNameEntity() {
            synchronized(mQueue) {
                if (!mQueue.isEmpty()) {
                    return mQueue.remove(0);
                }
            }
            return null;
        }

        public static class SNamedEntity {
            public String title;
            public long date;
        }
    }

    private void setCameraState(int state) {
        mCameraState = state;
        switch (state) {
            case PhotoController.PREVIEW_STOPPED:
            case PhotoController.SNAPSHOT_IN_PROGRESS:
            case PhotoController.LONGSHOT:
            case PhotoController.SWITCHING_CAMERA:
                mUI.enableGestures(false);
                break;
            case PhotoController.IDLE:
                mUI.enableGestures(true);
                break;
        }
    }

    private void animateAfterShutter() {
        // Only animate when in full screen capture mode
        // i.e. If monkey/a user swipes to the gallery during picture taking,
        // don't show animation
        if (!mIsImageCaptureIntent) {
            mUI.animateFlash();
        }
    }

    @Override
    public boolean capture() {
        // If we are already in the middle of taking a snapshot or the image save request
        // is full then ignore.
        if (mCameraDevice == null || mCameraState == SNAPSHOT_IN_PROGRESS
                || mCameraState == SWITCHING_CAMERA
                || mCameraState == PREVIEW_STOPPED
                || mActivity.getMediaSaveService() == null
                || mActivity.getMediaSaveService().isQueueFull()) {
            return false;
        }
        mCaptureStartTime = System.currentTimeMillis();
        mPostViewPictureCallbackTime = 0;
        mJpegImageData = null;

        final boolean animateBefore = (mSceneMode == CameraUtil.SCENE_MODE_HDR);

        if (animateBefore) {
            animateAfterShutter();
        }

        if (mCameraState == LONGSHOT) {
            mLongshotSnapNum = 0;
            mCameraDevice.setLongshot(true);
        }

        // Set rotation and gps data.
        int orientation = (mOrientation + mOrientationOffset) % 360;
        mJpegRotation = CameraUtil.getJpegRotation(mCameraId, orientation);
        String pictureFormat = mParameters.get(KEY_PICTURE_FORMAT);
        Location loc = getLocationAccordPictureFormat(pictureFormat);

        synchronized (mCameraDevice) {
            mParameters.setRotation(mJpegRotation);
            CameraUtil.setGpsParameters(mParameters, loc);

            mParameters.remove(CameraSettings.KEY_QC_LEGACY_BURST);

            // Unlock AE&AWB, if they continue
            // to be locked during snapshot, then
            // side effects could be triggered w.r.t.
            // flash.
            mFocusManager.setAeAwbLock(false);
            setAutoExposureLockIfSupported();
            setAutoWhiteBalanceLockIfSupported();

            mCameraDevice.setParameters(mParameters);
            mParameters = mCameraDevice.getParameters();
        }

        mBurstSnapNum = CameraUtil.getNumSnapsPerShutter(mParameters);
        mReceivedSnapNum = 0;
        mPreviewRestartSupport = PersistUtil.isPreviewRestartEnabled();
        mPreviewRestartSupport &= CameraSettings.isInternalPreviewSupported(
                mParameters);
        mPreviewRestartSupport &= (mBurstSnapNum == 1);
        // Restart is needed  if HDR is enabled
        mPreviewRestartSupport &= !CameraUtil.SCENE_MODE_HDR.equals(mSceneMode);
        mPreviewRestartSupport &= PIXEL_FORMAT_JPEG.equalsIgnoreCase(
                pictureFormat);

        mUI.enableShutter(false);

        // We don't want user to press the button again while taking a
        // multi-second HDR photo. For longshot, no need to disable.
        if (!CameraUtil.SCENE_MODE_HDR.equals(mSceneMode)) {
            mHandler.sendEmptyMessageDelayed(UNLOCK_CAM_SHUTTER, 120);
        }

        if (!isShutterSoundOn()) {
            mCameraDevice.enableShutterSound(false);
        } else {
            mCameraDevice.enableShutterSound(true);
        }

        mSaveBokehXmp = mIsBokehMode && mDepthSuccess;

        if (mCameraState == LONGSHOT) {
            mLongShotCaptureCountLimit = SystemProperties.getInt(
                                    "persist.sys.camera.longshot.shotnum", 0);
            mLongShotCaptureCount = 1;
            if(mLongshotSave) {
                mCameraDevice.takePicture(mHandler,
                        new LongshotShutterCallback(),
                        mRawPictureCallback, mPostViewPictureCallback,
                        new LongshotPictureCallback(loc));
            } else {
                mCameraDevice.takePicture(mHandler,
                        new LongshotShutterCallback(),
                        mRawPictureCallback, mPostViewPictureCallback,
                        new JpegPictureCallback(loc));
            }
        } else {
            mCameraDevice.takePicture(mHandler,
                    new ShutterCallback(!animateBefore),
                    mRawPictureCallback, mPostViewPictureCallback,
                    new JpegPictureCallback(loc));
            setCameraState(SNAPSHOT_IN_PROGRESS);
        }

        mSNamedImages.nameNewImage(mCaptureStartTime);

        mFaceDetectionStarted = false;

        UsageStatistics.onEvent(UsageStatistics.COMPONENT_CAMERA,
                UsageStatistics.ACTION_CAPTURE_DONE, "Photo", 0,
                UsageStatistics.hashFileName(mSNamedImages.mQueue.lastElement().title + ".jpg"));
        return true;
    }

    @Override
    public void setFocusParameters() {
        setCameraParameters(UPDATE_PARAM_PREFERENCE);
    }

    private Location getLocationAccordPictureFormat(String pictureFormat) {
        if (pictureFormat != null &&
                PIXEL_FORMAT_JPEG.equalsIgnoreCase(pictureFormat)) {
            return mLocationManager.getCurrentLocation();
        }
        return null;
    }

    private int getPreferredCameraId(ComboPreferences preferences) {
        int intentCameraId = CameraUtil.getCameraFacingIntentExtras(mActivity);
        if (intentCameraId != -1) {
            // Testing purpose. Launch a specific camera through the intent
            // extras.
            return intentCameraId;
        } else {
            return CameraSettings.readPreferredCameraId(preferences);
        }
    }

    private void updateLongshotScene() {
        String[] longshotScenes = mActivity.getResources().getStringArray(
                R.array.longshot_scenemodes);
        if (longshotScenes.length == 0) {
            mUI.overrideSettings(CameraSettings.KEY_LONGSHOT, null);
            return;
        }
        boolean useLongshot = false;
        for (String scene : longshotScenes) {
            if (scene.equals(mSceneMode)) {
                useLongshot = true;
                break;
            }
        }
        mUI.overrideSettings(CameraSettings.KEY_LONGSHOT,
                useLongshot ? mActivity.getString(R.string.setting_on_value) :
                              mActivity.getString(R.string.setting_off_value));
    }

    private void updateCameraSettings() {
        String sceneMode = null;
        String flashMode = null;
        String focusMode = null;
        String colorEffect = null;
        boolean disableLongShot = false;

        String continuousShotOn =
                mActivity.getString(R.string.setting_on_value);
        String continuousShot =
                mParameters.get("long-shot");


        if ((continuousShot != null) && continuousShot.equals(continuousShotOn)) {
            String pictureFormat = mActivity.getString(R.string.
                    pref_camera_picture_format_value_jpeg);
            mUI.overrideSettings(CameraSettings.KEY_PICTURE_FORMAT, pictureFormat);
        } else {
            mUI.overrideSettings(CameraSettings.KEY_PICTURE_FORMAT, null);
        }

        if (!Parameters.SCENE_MODE_AUTO.equals(mSceneMode) &&
                !"sports".equals(mSceneMode)) {
            flashMode = Parameters.FLASH_MODE_OFF;
            focusMode = mFocusManager.getFocusMode(false);
            colorEffect = mParameters.getColorEffect();
            String defaultEffect = mActivity.getString(R.string.pref_camera_coloreffect_default);
            if (CameraUtil.SCENE_MODE_HDR.equals(mSceneMode)) {
                disableLongShot = true;
                if (colorEffect != null & !colorEffect.equals(defaultEffect)) {
                    // Change the colorEffect to default(None effect) when HDR ON.
                    colorEffect = defaultEffect;
                    mUI.setPreference(CameraSettings.KEY_COLOR_EFFECT, colorEffect);
                    mParameters.setColorEffect(colorEffect);
                    mCameraDevice.setParameters(mParameters);
                    mParameters = mCameraDevice.getParameters();
                }
            }

        }

        if (disableLongShot || mIsBokehMode) {
            mUI.overrideSettings(CameraSettings.KEY_LONGSHOT,
                    mActivity.getString(R.string.setting_off_value));
        } else {
            updateLongshotScene();
        }

        if (flashMode == null) {
            // Restore saved flash mode or default mode
            if (mSavedFlashMode == null) {
                mSavedFlashMode =  mPreferences.getString(
                    CameraSettings.KEY_FLASH_MODE,
                    mActivity.getString(R.string.pref_camera_flashmode_default));
            }
            mUI.setPreference(CameraSettings.KEY_FLASH_MODE, mSavedFlashMode);
            mSavedFlashMode = null;
        } else {
            // Save the current flash mode
            if (mSavedFlashMode == null) {
                mSavedFlashMode =  mPreferences.getString(
                    CameraSettings.KEY_FLASH_MODE,
                    mActivity.getString(R.string.pref_camera_flashmode_default));
            }
            mUI.overrideSettings(CameraSettings.KEY_FLASH_MODE, flashMode);
        }

        if(mCameraId != CameraHolder.instance().getFrontCameraId()) {
            CameraSettings.removePreferenceFromScreen(mPreferenceGroup, CameraSettings.KEY_SELFIE_FLASH);
            CameraSettings.removePreferenceFromScreen(mPreferenceGroup, CameraSettings.KEY_SELFIE_MIRROR);
        } else {
            ListPreference prefSelfieMirror = mPreferenceGroup.findPreference(CameraSettings.KEY_SELFIE_MIRROR);
            if(prefSelfieMirror != null && prefSelfieMirror.getValue() != null
                    && prefSelfieMirror.getValue().equalsIgnoreCase("enable")) {
                mUI.overrideSettings(CameraSettings.KEY_LONGSHOT, "off");
            }
        }

        String bokehMode = mPreferences.getString(
                CameraSettings.KEY_BOKEH_MODE,
                mActivity.getString(R.string.pref_camera_bokeh_mode_default));
        if (!bokehMode.equals(mActivity.getString(
                R.string.pref_camera_bokeh_mode_entry_value_disable))) {
            mIsBokehMode = true;
            if (mCameraDevice != null) {
                mCameraDevice.setMetadataCb(mMetaDataCallback);
            }
            mUI.overrideSettings(CameraSettings.KEY_FLASH_MODE, Parameters.FLASH_MODE_OFF);
            mUI.overrideSettings(CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO);
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
            final int degree = prefs.getInt(CameraSettings.KEY_BOKEH_BLUR_VALUE,50);
            mUI.getBokehDegreeBar().setProgress(degree);
            mUI.getBokehDegreeBar().setOnSeekBarChangeListener(mBlurDegreeListener);
            mUI.enableBokehRender(true);
            mUI.setBokehRenderDegree(degree);
            mBokehTipText.setVisibility(View.VISIBLE);
        } else {
            mIsBokehMode = false;
            if (mCameraDevice != null) {
                mCameraDevice.setMetadataCb(null);
            }
            mUI.overrideSettings(CameraSettings.KEY_BOKEH_MPO,
                    mActivity.getString(R.string.pref_camera_bokeh_mpo_default));
            mUI.overrideSettings(CameraSettings.KEY_BOKEH_BLUR_VALUE,
                    mActivity.getString(R.string.pref_camera_bokeh_blur_degree_default));
            mUI.getBokehDegreeBar().setOnSeekBarChangeListener(null);
            mUI.getBokehDegreeBar().setVisibility(View.GONE);
            mUI.enableBokehRender(false);
            mBokehTipText.setVisibility(View.GONE);
        }
    }

    private void loadCameraPreferences() {
        CameraSettings settings = new CameraSettings(mActivity, mInitialParams,
                mCameraId, CameraHolder.instance().getCameraInfo());
        mPreferenceGroup = settings.getPreferenceGroup(R.xml.camera_preferences);

        int numOfCams = Camera.getNumberOfCameras();

        Log.e(TAG,"loadCameraPreferences() updating camera_id pref");

        IconListPreference switchIconPref =
                (IconListPreference)mPreferenceGroup.findPreference(
                CameraSettings.KEY_CAMERA_ID);

        //if numOfCams < 2 then switchIconPref will be null as there is no switch icon in this case
        if (switchIconPref == null)
            return;

        int[] iconIds = new int[numOfCams];
        String[] entries = new String[numOfCams];
        String[] labels = new String[numOfCams];
        int[] largeIconIds = new int[numOfCams];

        for(int i=0;i<numOfCams;i++) {
            CameraInfo info = CameraHolder.instance().getCameraInfo()[i];
            if(info.facing == CameraInfo.CAMERA_FACING_BACK) {
                iconIds[i] = R.drawable.ic_switch_back;
                entries[i] = mActivity.getResources().getString(R.string.pref_camera_id_entry_back);
                labels[i] = mActivity.getResources().getString(R.string.pref_camera_id_label_back);
                largeIconIds[i] = R.drawable.ic_switch_back;
            } else {
                iconIds[i] = R.drawable.ic_switch_front;
                entries[i] = mActivity.getResources().getString(R.string.pref_camera_id_entry_front);
                labels[i] = mActivity.getResources().getString(R.string.pref_camera_id_label_front);
                largeIconIds[i] = R.drawable.ic_switch_front;
            }
        }

        switchIconPref.setIconIds(iconIds);
        switchIconPref.setEntries(entries);
        switchIconPref.setLabels(labels);
        switchIconPref.setLargeIconIds(largeIconIds);

    }

    @Override
    public void onOrientationChanged(int orientation) {
        // We keep the last known orientation. So if the user first orient
        // the camera then point the camera to floor or sky, we still have
        // the correct orientation.
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return;
        orientation = (orientation - mOrientationOffset + 360) % 360;
        int oldOrientation = mOrientation;
        mOrientation = CameraUtil.roundOrientation(orientation, mOrientation);
        if (oldOrientation != mOrientation) {
            if (mParameters != null && mCameraDevice != null && mCameraState == IDLE) {
                Log.v(TAG, "onOrientationChanged, update parameters");
                synchronized (mCameraDevice) {
                    setFlipValue();
                    mCameraDevice.setParameters(mParameters);
                }
            }
            mUI.tryToCloseSubList();
            mUI.setOrientation(mOrientation, true);
            if (mGraphView != null) {
                mGraphView.setRotation(-mOrientation);
            }
        }

        // Show the toast after getting the first orientation changed.
        if (mHandler.hasMessages(SHOW_TAP_TO_FOCUS_TOAST)) {
            mHandler.removeMessages(SHOW_TAP_TO_FOCUS_TOAST);
            showTapToFocusToast();
        }
    }

    @Override
    public void onStop() {}

    @Override
    public void onDestroy() {}

    @Override
    public void onCaptureCancelled() {
        mActivity.setResultEx(Activity.RESULT_CANCELED, new Intent());
        mActivity.finish();
    }

    @Override
    public void onCaptureRetake() {
        if (mPaused)
            return;
        mUI.hidePostCaptureAlert();
        setupPreview();
    }

    @Override
    public void onCaptureDone() {
        if (mPaused) {
            return;
        }

        byte[] data = mJpegImageData;

        if (mCropValue == null) {
            // First handle the no crop case -- just return the value.  If the
            // caller specifies a "save uri" then write the data to its
            // stream. Otherwise, pass back a scaled down version of the bitmap
            // directly in the extras.
            if (mSaveUri != null) {
                OutputStream outputStream = null;
                try {
                    outputStream = mContentResolver.openOutputStream(mSaveUri);
                    outputStream.write(data);
                    outputStream.close();

                    mActivity.setResultEx(Activity.RESULT_OK);
                    mActivity.finish();
                } catch (IOException ex) {
                    // ignore exception
                } finally {
                    CameraUtil.closeSilently(outputStream);
                }
            } else {
                ExifInterface exif = Exif.getExif(data);
                int orientation = Exif.getOrientation(exif);
                Bitmap bitmap = CameraUtil.makeBitmap(data, 50 * 1024);
                bitmap = CameraUtil.rotate(bitmap, orientation);
                mActivity.setResultEx(Activity.RESULT_OK,
                        new Intent("inline-data").putExtra("data", bitmap));
                mActivity.finish();
            }
        } else {
            // Save the image to a temp file and invoke the cropper
            Uri tempUri = null;
            FileOutputStream tempStream = null;
            try {
                File path = mActivity.getFileStreamPath(sTempCropFilename);
                path.delete();
                tempStream = mActivity.openFileOutput(sTempCropFilename, 0);
                tempStream.write(data);
                tempStream.close();
                tempUri = Uri.fromFile(path);
            } catch (FileNotFoundException ex) {
                mActivity.setResultEx(Activity.RESULT_CANCELED);
                mActivity.finish();
                return;
            } catch (IOException ex) {
                mActivity.setResultEx(Activity.RESULT_CANCELED);
                mActivity.finish();
                return;
            } finally {
                CameraUtil.closeSilently(tempStream);
            }

            Bundle newExtras = new Bundle();
            if (mCropValue.equals("circle")) {
                newExtras.putString("circleCrop", "true");
            }
            if (mSaveUri != null) {
                newExtras.putParcelable(MediaStore.EXTRA_OUTPUT, mSaveUri);
            } else {
                newExtras.putBoolean(CameraUtil.KEY_RETURN_DATA, true);
            }
            if (mActivity.isSecureCamera()) {
                newExtras.putBoolean(CameraUtil.KEY_SHOW_WHEN_LOCKED, true);
            }

            // TODO: Share this constant.
            final String CROP_ACTION = "com.android.camera.action.CROP";
            Intent cropIntent = new Intent(CROP_ACTION);

            cropIntent.setData(tempUri);
            cropIntent.putExtras(newExtras);

            mActivity.startActivityForResult(cropIntent, REQUEST_CROP);
        }
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        if (mCameraDevice == null
                || mPaused || mUI.collapseCameraControls()
                || (mCameraState == SNAPSHOT_IN_PROGRESS)
                || (mCameraState == PREVIEW_STOPPED)
                || (null == mFocusManager)) {
            Log.v(TAG, "onShutterButtonFocus error case mCameraState = " + mCameraState
                + "mCameraDevice = " + mCameraDevice + "mPaused =" + mPaused);
            return;
        }

        synchronized(mCameraDevice) {
           if (mCameraState == LONGSHOT) {
               mLongshotActive = false;
               mUI.enableShutter(false);
           }
        }

        // Do not do focus if there is not enough storage.
        if (pressed && !canTakePicture()) return;

        if (pressed) {
            mFocusManager.onShutterDown();
        } else {
            // for countdown mode, we need to postpone the shutter release
            // i.e. lock the focus during countdown.
            if (!mUI.isCountingDown()) {
                mFocusManager.onShutterUp();
            }
        }
    }

    @Override
    public synchronized void onShutterButtonClick() {
        if ((mCameraDevice == null)
                || mPaused || mUI.collapseCameraControls()
                || !mUI.mMenuInitialized
                || (mCameraState == SWITCHING_CAMERA)
                || (mCameraState == PREVIEW_STOPPED)
                || (mCameraState == LONGSHOT)
                || (null == mFocusManager)) return;

        // Do not take the picture if there is not enough storage.
        if (mActivity.getStorageSpaceBytes() <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            Log.i(TAG, "Not enough space or storage not ready. remaining="
                    + mActivity.getStorageSpaceBytes());
            return;
        }
        Log.v(TAG, "onShutterButtonClick: mCameraState=" + mCameraState);

        if (mSceneMode == CameraUtil.SCENE_MODE_HDR) {
            mUI.hideSwitcher();
            mUI.setSwipingEnabled(false);
        }

         //Need to disable focus for ZSL mode
        if (mFocusManager != null) {
            mFocusManager.setZslEnable(false);
        }

        // If the user wants to do a snapshot while the previous one is still
        // in progress, remember the fact and do it after we finish the previous
        // one and re-start the preview. Snapshot in progress also includes the
        // state that autofocus is focusing and a picture will be taken when
        // focus callback arrives.
        if ((((mFocusManager != null) && mFocusManager.isFocusingSnapOnFinish())
                || mCameraState == SNAPSHOT_IN_PROGRESS)
                && !mIsImageCaptureIntent) {
            mSnapshotOnIdle = true;
            return;
        }

        String timer = mPreferences.getString(
                CameraSettings.KEY_TIMER,
                mActivity.getString(R.string.pref_camera_timer_default));
        boolean playSound = mPreferences.getString(CameraSettings.KEY_TIMER_SOUND_EFFECTS,
                mActivity.getString(R.string.pref_camera_timer_sound_default))
                .equals(mActivity.getString(R.string.setting_on_value));

        int seconds = Integer.parseInt(timer);
        // When shutter button is pressed, check whether the previous countdown is
        // finished. If not, cancel the previous countdown and start a new one.
        if (mUI.isCountingDown()) {
            mUI.cancelCountDown();
        }

        mSnapshotOnIdle = false;
        initiateSnap();
    }

    private boolean isShutterSoundOn() {
        IconListPreference shutterSoundPref = (IconListPreference) mPreferenceGroup
                .findPreference(CameraSettings.KEY_SHUTTER_SOUND);
        if (shutterSoundPref != null && shutterSoundPref.getValue() != null &&
                shutterSoundPref.getValue().equalsIgnoreCase("disable")) {
            return false;
        }
        return true;
    }

    private void initiateSnap()
    {
        if(mPreferences.getString(CameraSettings.KEY_SELFIE_FLASH,
                mActivity.getString(R.string.pref_selfie_flash_default))
                .equalsIgnoreCase("on") &&
                mCameraId == CameraHolder.instance().getFrontCameraId()) {
            mUI.startSelfieFlash();
            if(selfieThread == null) {
                selfieThread = new SelfieThread();
                selfieThread.start();
            }
        } else {
            mFocusManager.doSnap();
        }
    }

    @Override
    public void onShutterButtonLongClick() {
        // Do not take the picture if there is not enough storage.
        if (mActivity.getStorageSpaceBytes() <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            Log.i(TAG, "Not enough space or storage not ready. remaining="
                    + mActivity.getStorageSpaceBytes());
            return;
        }

        if ((null != mCameraDevice) && ((mCameraState == IDLE) || (mCameraState == FOCUSING))) {
            //Add on/off Menu for longshot
            String longshot_enable = mPreferences.getString(
                CameraSettings.KEY_LONGSHOT,
                mActivity.getString(R.string.pref_camera_longshot_default));

            Log.d(TAG, "longshot_enable = " + longshot_enable);
            if (longshot_enable.equals("on")) {
                boolean enable = PersistUtil.isLongSaveEnabled();
                mLongshotSave = enable;

                //Cancel the previous countdown when long press shutter button for longshot.
                if (mUI.isCountingDown()) {
                    mUI.cancelCountDown();
                }
                //check whether current memory is enough for longshot.
                if(isLongshotNeedCancel()) {
                    return;
                }
                mLongshotActive = true;
                setCameraState(PhotoController.LONGSHOT);
                mFocusManager.doSnap();
            }
        }
    }

    @Override
    public void installIntentFilter() {
        // Do nothing.
    }

    @Override
    public boolean updateStorageHintOnResume() {
        return mFirstTimeInitialized;
    }

    @Override
    public void onResumeBeforeSuper() {
        mPaused = false;
        if (mFocusManager == null) initializeFocusManager();
    }

    private void openCamera() {
        // We need to check whether the activity is paused before long
        // operations to ensure that onPause() can be done ASAP.
        if (mPaused) {
            return;
        }
        Log.v(TAG, "Open camera device.");
        mCameraDevice = CameraUtil.openCamera(
                mActivity, mCameraId, mHandler,
                mActivity.getCameraOpenErrorCallback());
        if (mCameraDevice == null) {
            Log.e(TAG, "Failed to open camera:" + mCameraId);
            mHandler.sendEmptyMessage(OPEN_CAMERA_FAIL);
            return;
        }
        mParameters = mCameraDevice.getParameters();
        mCameraPreviewParamsReady = true;
        mInitialParams = mCameraDevice.getParameters();
        if (mFocusManager == null) {
            initializeFocusManager();
        } else {
            mFocusManager.setParameters(mInitialParams);
        }
        initializeCapabilities();
        mHandler.sendEmptyMessageDelayed(CAMERA_OPEN_DONE, 100);
        return;
    }

    @Override
    public void onResumeAfterSuper() {
        mUI.showSurfaceView();
        // Add delay on resume from lock screen only, in order to to speed up
        // the onResume --> onPause --> onResume cycle from lock screen.
        // Don't do always because letting go of thread can cause delay.
        String action = mActivity.getIntent().getAction();
        if (MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA.equals(action)
                || MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action)) {
            Log.v(TAG, "On resume, from lock screen.");

            // Note: onPauseAfterSuper() will delete this runnable, so we will
            // at most have 1 copy queued up.
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    onResumeTasks();
                }
            }, ON_RESUME_TASKS_DELAY_MSEC);
        } else {
            Log.v(TAG, "On resume.");
            onResumeTasks();
        }

        mUI.setSwitcherIndex();

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mActivity.updateStorageSpaceAndHint();
                updateRemainingPhotos();
            }
        });
    }

    private void updateRemainingPhotos() {
        if (mJpegFileSizeEstimation != 0) {
            mRemainingPhotos = (int)
                    ((mActivity.getStorageSpaceBytes() - Storage.LOW_STORAGE_THRESHOLD_BYTES)
                    / mJpegFileSizeEstimation);
        } else {
            mRemainingPhotos = -1;
        }
        mUI.updateRemainingPhotos(mRemainingPhotos);
    }

    private void onResumeTasks() {
        Log.v(TAG, "Executing onResumeTasks.");
        if (mOpenCameraFail || mCameraDisabled) return;

        if (mOpenCameraThread == null) {
            mOpenCameraThread = new OpenCameraThread();
            mOpenCameraThread.start();
        }

        mUI.applySurfaceChange(SPhotoUI.SURFACE_STATUS.SURFACE_VIEW);

        mJpegPictureCallbackTime = 0;
        mZoomValue = 0;

        // If first time initialization is not finished, put it in the
        // message queue.
        if (!mFirstTimeInitialized) {
            mHandler.sendEmptyMessage(FIRST_TIME_INIT);
        } else {
            initializeSecondTime();
        }
        mUI.initDisplayChangeListener();
        keepScreenOnAwhile();

        UsageStatistics.onContentViewChanged(
                UsageStatistics.COMPONENT_CAMERA, "SPhotoModule");

        Sensor gsensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (gsensor != null) {
            mSensorManager.registerListener(this, gsensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        Sensor msensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (msensor != null) {
            mSensorManager.registerListener(this, msensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        mOnResumeTime = SystemClock.uptimeMillis();
        checkDisplayRotation();

        mAnimateCapture = PersistUtil.isCaptureAnimationEnabled();
    }

    @Override
    public void onPauseBeforeSuper() {
        mPaused = true;
        mUI.applySurfaceChange(SPhotoUI.SURFACE_STATUS.HIDE);

        Sensor gsensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (gsensor != null) {
            mSensorManager.unregisterListener(this, gsensor);
        }

        Sensor msensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (msensor != null) {
            mSensorManager.unregisterListener(this, msensor);
        }

        if(selfieThread != null) {
            selfieThread.interrupt();
        }
        mUI.stopSelfieFlash();

        Log.d(TAG, "remove idle handleer in onPause");
        removeIdleHandler();
    }

    @Override
    public void onPauseAfterSuper() {
        Log.v(TAG, "On pause.");
        mUI.showPreviewCover();
        mUI.hideSurfaceView();

        try {
            if (mOpenCameraThread != null) {
                mOpenCameraThread.join();
            }
        } catch (InterruptedException ex) {
            // ignore
        }
        mOpenCameraThread = null;
        // Reset the focus first. Camera CTS does not guarantee that
        // cancelAutoFocus is allowed after preview stops.
        if (mCameraDevice != null && mCameraState != PREVIEW_STOPPED) {
            mCameraDevice.cancelAutoFocus();
        }
        // If the camera has not been opened asynchronously yet,
        // and startPreview hasn't been called, then this is a no-op.
        // (e.g. onResume -> onPause -> onResume).
        stopPreview();

        // Load the power shutter
        mActivity.initPowerShutter(mPreferences);

        // Load max brightness
        mActivity.initMaxBrightness(mPreferences);

        mSNamedImages = null;

        if (mLocationManager != null) mLocationManager.recordLocation(false);

        // If we are in an image capture intent and has taken
        // a picture, we just clear it in onPause.
        mJpegImageData = null;

        // Remove the messages and runnables in the queue.
        mHandler.removeCallbacksAndMessages(null);

        closeCamera();

        resetScreenOn();
        mUI.onPause();

        mPendingSwitchCameraId = -1;
        if (mFocusManager != null) mFocusManager.removeMessages();
        MediaSaveService s = mActivity.getMediaSaveService();
        if (s != null) {
            s.setListener(null);
        }
        mUI.removeDisplayChangeListener();
    }

    /**
     * The focus manager is the first UI related element to get initialized,
     * and it requires the RenderOverlay, so initialize it here
     */
    private void initializeFocusManager() {
        // Create FocusManager object. startPreview needs it.
        // if mFocusManager not null, reuse it
        // otherwise create a new instance
        if (mFocusManager != null) {
            mFocusManager.removeMessages();
        } else {
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
            mMirror = (info.facing == CameraInfo.CAMERA_FACING_FRONT);
            String[] defaultFocusModes = mActivity.getResources().getStringArray(
                    R.array.pref_camera_focusmode_default_array);
            synchronized (this){
                if (mFocusManager == null) {
                    mFocusManager = new FocusOverlayManager(mPreferences, defaultFocusModes,
                            mInitialParams, this, mMirror,
                            mActivity.getMainLooper(), mUI.getFocusRing(), mActivity);
                }
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.v(TAG, "onConfigurationChanged");
        setDisplayOrientation();
        resizeForPreviewAspectRatio();
    }

    @Override
    public void updateCameraOrientation() {
        if (mDisplayRotation != CameraUtil.getDisplayRotation(mActivity)) {
            setDisplayOrientation();
        }
    }

    @Override
    public void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CROP: {
                Intent intent = new Intent();
                if (data != null) {
                    Bundle extras = data.getExtras();
                    if (extras != null) {
                        intent.putExtras(extras);
                    }
                }
                mActivity.setResultEx(resultCode, intent);
                mActivity.finish();

                File path = mActivity.getFileStreamPath(sTempCropFilename);
                path.delete();

                break;
            }
        }
    }

    protected CameraManager.CameraProxy getCamera() {
        return mCameraDevice;
    }

    private boolean canTakePicture() {
        return isCameraIdle() && (mActivity.getStorageSpaceBytes() > Storage.LOW_STORAGE_THRESHOLD_BYTES);
    }

    @Override
    public void autoFocus() {
        mFocusStartTime = System.currentTimeMillis();
        mCameraDevice.autoFocus(mHandler, mAutoFocusCallback);
        setCameraState(FOCUSING);
    }

    @Override
    public void cancelAutoFocus() {
        if (null != mCameraDevice ) {
            mCameraDevice.cancelAutoFocus();
            setCameraState(IDLE);
            mFocusManager.setAeAwbLock(false);
            setCameraParameters(UPDATE_PARAM_PREFERENCE);
        }
    }

    // Preview area is touched. Handle touch focus.
    @Override
    public void onSingleTapUp(View view, int x, int y) {
        if (mPaused || mCameraDevice == null || !mFirstTimeInitialized
                || mCameraState == SNAPSHOT_IN_PROGRESS
                || mCameraState == SWITCHING_CAMERA
                || mCameraState == PREVIEW_STOPPED) {
            return;
        }
        // Check if metering area or focus area is supported.
        if (!mFocusAreaSupported && !mMeteringAreaSupported) return;
        mFocusManager.onSingleTapUp(x, y);
    }

    @Override
    public boolean onBackPressed() {
        return mUI.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Do not handle any key if the activity is
        // not in active camera/video mode
        if (!mActivity.isInCameraApp()) {
            return false;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (mFirstTimeInitialized && (mUI.mMenuInitialized)) {
                    if (!CameraActivity.mPowerShutter && !CameraUtil.hasCameraKey()) {
                        onShutterButtonFocus(true);
                    } else {
                        mUI.onScaleStepResize(true);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                if (mFirstTimeInitialized && (mUI.mMenuInitialized)) {
                    if (!CameraActivity.mPowerShutter && !CameraUtil.hasCameraKey()) {
                        onShutterButtonFocus(true);
                    } else {
                        mUI.onScaleStepResize(false);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_FOCUS:
                if (mFirstTimeInitialized) {
                    if (event.getRepeatCount() == 0) {
                        onShutterButtonFocus(true);
                    }
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    onShutterButtonClick();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                // If we get a dpad center event without any focused view, move
                // the focus to the shutter button and press it.
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    // Start auto-focus immediately to reduce shutter lag. After
                    // the shutter button gets the focus, onShutterButtonFocus()
                    // will be called again but it is fine.
                    onShutterButtonFocus(true);
                    mUI.pressShutterButton();
                }
                return true;
            case KeyEvent.KEYCODE_POWER:
                if (mFirstTimeInitialized && event.getRepeatCount() == 0
                        && CameraActivity.mPowerShutter && !CameraUtil.hasCameraKey()) {
                    onShutterButtonFocus(true);
                }
                return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                if (!CameraActivity.mPowerShutter && !CameraUtil.hasCameraKey()
                        && mFirstTimeInitialized) {
                    onShutterButtonClick();
                }
                return true;
            case KeyEvent.KEYCODE_FOCUS:
                if (mFirstTimeInitialized) {
                    onShutterButtonFocus(false);
                }
                return true;
            case KeyEvent.KEYCODE_POWER:
                if (CameraActivity.mPowerShutter && !CameraUtil.hasCameraKey()
                        && mFirstTimeInitialized) {
                    onShutterButtonClick();
                }
                return true;
        }
        return false;
    }

    private void closeCamera() {
        Log.v(TAG, "Close camera device.");
        if (mCameraDevice != null) {
            mCameraDevice.setZoomChangeListener(null);
            mCameraDevice.setFaceDetectionCallback(null, null);
            mCameraDevice.setErrorCallback(null);

            if (mActivity.isSecureCamera() || mActivity.isForceReleaseCamera()) {
                // Blocks until camera is actually released.
                CameraHolder.instance().strongRelease();
            } else {
                CameraHolder.instance().release();
            }

            mFaceDetectionStarted = false;
            mCameraDevice = null;
            setCameraState(PREVIEW_STOPPED);
            if (mFocusManager != null) {
                mFocusManager.onCameraReleased();
            }
        }
    }

    private void setDisplayOrientation() {
        mDisplayRotation = CameraUtil.getDisplayRotation(mActivity);
        mDisplayOrientation = CameraUtil.getDisplayOrientation(mDisplayRotation, mCameraId);
        mCameraDisplayOrientation = mDisplayOrientation;
        // This will be called again in checkDisplayRotation(), so there
        // should not be any problem even if mUI is null.
        if (mUI != null) {
            mUI.setDisplayOrientation(mDisplayOrientation);
        }
        if (mFocusManager != null) {
            mFocusManager.setDisplayOrientation(mDisplayOrientation);
        }
        // Change the camera display orientation
        if (mCameraDevice != null) {
            mCameraDevice.setDisplayOrientation(mCameraDisplayOrientation);
        }
    }

    /** Only called by UI thread. */
    private void setupPreview() {
        mFocusManager.resetTouchFocus();
        startPreview();
    }

    /** This can run on a background thread, so don't do UI updates here. Post any
             view updates to MainHandler or do it on onPreviewStarted() .  */
    private void startPreview() {
        if (mPaused || mCameraDevice == null || mParameters == null) {
            return;
        }

        synchronized (mCameraDevice) {
            SurfaceHolder sh = null;
            Log.v(TAG, "startPreview: SurfaceHolder (MDP path)");
            if (mUI != null) {
                sh = mUI.getSurfaceHolder();
            }

            // Let UI set its expected aspect ratio
            mCameraDevice.setPreviewDisplay(sh);
        }

        if (!mCameraPreviewParamsReady) {
            Log.w(TAG, "startPreview: parameters for preview are not ready.");
            return;
        }
        mErrorCallback.setActivity(mActivity);
        mCameraDevice.setErrorCallback(mErrorCallback);

        // Reset camera state after taking a picture
        if (mCameraState != PREVIEW_STOPPED && mCameraState != INIT) {
            setCameraState(IDLE);
        }

        // Preview needs to be stopped when changing resolution
        if (mRestartPreview && mCameraState != PREVIEW_STOPPED && mCameraState != INIT) {
            stopPreview();
            mRestartPreview = false;
        }

        if (mFocusManager == null) initializeFocusManager();

        if (!mSnapshotOnIdle) {
            mFocusManager.setAeAwbLock(false); // Unlock AE and AWB.
        }

        setCameraParameters(UPDATE_PARAM_ALL);
        mCameraDevice.setOneShotPreviewCallback(mHandler,
                new CameraManager.CameraPreviewDataCallback() {
                    @Override
                    public void onPreviewFrame(byte[] data, CameraProxy camera) {
                        mUI.hidePreviewCover();
                    }
                });
        mCameraDevice.startPreview();

        mHandler.sendEmptyMessage(ON_PREVIEW_STARTED);

        setDisplayOrientation();

        if (!mSnapshotOnIdle) {
            // If the focus mode is continuous autofocus, call cancelAutoFocus to
            // resume it because it may have been paused by autoFocus call.
            if (CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE.equals(mFocusManager.getFocusMode(false)) && mCameraState !=INIT ||
                    CameraUtil.FOCUS_MODE_MW_CONTINUOUS_PICTURE.equals(mFocusManager.getFocusMode(false))) {
                mCameraDevice.cancelAutoFocus();
            }
        } else {
            mHandler.sendEmptyMessageDelayed(INSTANT_CAPTURE, 1500);
        }
    }

    @Override
    public void stopPreview() {
        if (mCameraDevice != null && mCameraState != PREVIEW_STOPPED) {
            if (mCameraState == LONGSHOT) {
                mCameraDevice.setLongshot(false);
                mLongshotActive = false;
            }
            Log.v(TAG, "stopPreview");
            mCameraDevice.stopPreview();
        }
        setCameraState(PREVIEW_STOPPED);
        if (mFocusManager != null) mFocusManager.onPreviewStopped();
        stopFaceDetection();
    }

    @SuppressWarnings("deprecation")
    private void updateCameraParametersInitialize() {
        // Reset preview frame rate to the maximum because it may be lowered by
        // video camera application.
        int[] fpsRange = CameraUtil.getPhotoPreviewFpsRange(mParameters);
        if (fpsRange != null && fpsRange.length > 0) {
            mParameters.setPreviewFpsRange(
                    fpsRange[Parameters.PREVIEW_FPS_MIN_INDEX],
                    fpsRange[Parameters.PREVIEW_FPS_MAX_INDEX]);
        }

        mParameters.set(CameraUtil.RECORDING_HINT, CameraUtil.FALSE);

        // Disable video stabilization. Convenience methods not available in API
        // level <= 14
        String vstabSupported = mParameters.get("video-stabilization-supported");
        if ("true".equals(vstabSupported)) {
            mParameters.set("video-stabilization", "false");
        }
    }

    private void updateCameraParametersZoom() {
        // Set zoom.
        if (mParameters.isZoomSupported()) {
            Parameters p = mCameraDevice.getParameters();
            mZoomValue = p.getZoom();
            mParameters.setZoom(mZoomValue);
        }
    }
    private boolean needRestart() {
        mRestartPreview = false;

        if(mCameraState != PREVIEW_STOPPED) {
            //Switch on Normal Camera mode
            Log.v(TAG, "Switching to Normal Camera Mode. Restart Preview");
            mRestartPreview = true;
            return mRestartPreview;
        }
        return mRestartPreview;
    }

    private String getSaturationSafe() {
        String ret = null;
        if (CameraUtil.isSupported(mParameters, "saturation") &&
                CameraUtil.isSupported(mParameters, "max-saturation")) {
            ret = mPreferences.getString(
                    CameraSettings.KEY_SATURATION,
                    mActivity.getString(R.string.pref_camera_saturation_default));
        }
        return ret;
    }

    private String getSharpnessSafe() {
        String ret = null;
        if (CameraUtil.isSupported(mParameters, "sharpness") &&
                CameraUtil.isSupported(mParameters, "max-sharpness")) {
            ret = mPreferences.getString(
                    CameraSettings.KEY_SHARPNESS,
                    mActivity.getString(R.string.pref_camera_sharpness_default));
        }
        return ret;
    }

    /** This can run on a background thread, so don't do UI updates here.*/
    private void qcomUpdateCameraParametersPreference() {
        //qcom Related Parameter update
        String longshot_enable = mPreferences.getString(
                CameraSettings.KEY_LONGSHOT,
                mActivity.getString(R.string.pref_camera_longshot_default));
        mParameters.set("long-shot", longshot_enable);

        // Set Picture Format
        // Picture Formats specified in UI should be consistent with
        // PIXEL_FORMAT_JPEG and PIXEL_FORMAT_RAW constants
        String pictureFormat = mPreferences.getString(
                CameraSettings.KEY_PICTURE_FORMAT,
                mActivity.getString(R.string.pref_camera_picture_format_default));

        //Change picture format to JPEG if camera is start from other APK by intent.
        if (mIsImageCaptureIntent && !pictureFormat.equals(PIXEL_FORMAT_JPEG)) {
            pictureFormat = PIXEL_FORMAT_JPEG;
            Editor editor = mPreferences.edit();
            editor.putString(CameraSettings.KEY_PICTURE_FORMAT,
                mActivity.getString(R.string.pref_camera_picture_format_value_jpeg));
            editor.apply();
        }
        Log.v(TAG, "Picture format value =" + pictureFormat);
        mParameters.set(KEY_PICTURE_FORMAT, pictureFormat);

        // Set JPEG quality.
        String jpegQuality = mPreferences.getString(
                CameraSettings.KEY_JPEG_QUALITY,
                mActivity.getString(R.string.pref_camera_jpegquality_default));
        Size pic_size = mParameters.getPictureSize();
        if (pic_size == null) {
            Log.e(TAG, "error getPictureSize: size is null");
        } else {
            mParameters.setJpegQuality(SJpegEncodingQualityMappings.getQualityNumber(jpegQuality));
            int jpegFileSize = estimateJpegFileSize(pic_size, jpegQuality);
            if (jpegFileSize != mJpegFileSizeEstimation) {
                mJpegFileSizeEstimation = jpegFileSize;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateRemainingPhotos();
                    }
                });
            }
        }

        // Set color effect parameter.
        String colorEffect = mPreferences.getString(
                CameraSettings.KEY_COLOR_EFFECT,
                mActivity.getString(R.string.pref_camera_coloreffect_default));
        Log.v(TAG, "Color effect value =" + colorEffect);
        if (CameraUtil.isSupported(colorEffect, mParameters.getSupportedColorEffects())) {
            mParameters.setColorEffect(colorEffect);
        }

        //Set Saturation
        String saturationStr = getSaturationSafe();
        if (saturationStr != null) {
            int saturation = Integer.parseInt(saturationStr);
            Log.v(TAG, "Saturation value =" + saturation);
            if((0 <= saturation) && (saturation <= ParametersWrapper.getMaxSaturation(mParameters))){
                ParametersWrapper.setSaturation(mParameters, saturation);
            }
        }

        // Set sharpness parameter
        String sharpnessStr = getSharpnessSafe();
        if (sharpnessStr != null) {
            int sharpness = Integer.parseInt(sharpnessStr) *
                    (ParametersWrapper.getMaxSharpness(mParameters)/MAX_SHARPNESS_LEVEL);
            Log.v(TAG, "Sharpness value =" + sharpness);
            if((0 <= sharpness) && (sharpness <= ParametersWrapper.getMaxSharpness(mParameters))){
                ParametersWrapper.setSharpness(mParameters, sharpness);
            }
        }
        // Set Face Recognition
        String faceRC = mPreferences.getString(
                CameraSettings.KEY_FACE_RECOGNITION,
                mActivity.getString(R.string.pref_camera_facerc_default));
        Log.v(TAG, "Face Recognition value = " + faceRC);
        if (CameraUtil.isSupported(faceRC,
                CameraSettings.getSupportedFaceRecognitionModes(mParameters))) {
            mParameters.set(CameraSettings.KEY_QC_FACE_RECOGNITION, faceRC);
        }


        // Set face detetction parameter.
        // clear override to re-enable setting if true portrait is off.
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mUI.overrideSettings(CameraSettings.KEY_FACE_DETECTION, null);
            }
        });

        String faceDetection = mPreferences.getString(
            CameraSettings.KEY_FACE_DETECTION,
            mActivity.getString(R.string.pref_camera_facedetection_default));

        if (CameraUtil.isSupported(faceDetection,
                ParametersWrapper.getSupportedFaceDetectionModes(mParameters))) {
            ParametersWrapper.setFaceDetectionMode(mParameters, faceDetection);
            if(faceDetection.equals("on") && mFaceDetectionEnabled == false) {
                mFaceDetectionEnabled = true;
                startFaceDetection();
            }
            if(faceDetection.equals("off") && mFaceDetectionEnabled == true) {
                stopFaceDetection();
                mFaceDetectionEnabled = false;
            }
        }

        // Set anti banding parameter.
        String antiBanding = mPreferences.getString(
                 CameraSettings.KEY_ANTIBANDING,
                 mActivity.getString(R.string.pref_camera_antibanding_default));
        Log.v(TAG, "antiBanding value =" + antiBanding);
        if (CameraUtil.isSupported(antiBanding, mParameters.getSupportedAntibanding())) {
            mParameters.setAntibanding(antiBanding);
        }

        ParametersWrapper.setCameraMode(mParameters, 0);
        mFocusManager.setZslEnable(false);

        setFlipValue();

        if(!mFocusManager.getFocusMode(false).equals(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) &&
            !mFocusManager.isFocusCompleted()) {
            mUI.getFocusRing().stopFocusAnimations();
        }

        String bokehMode = mPreferences.getString(
                CameraSettings.KEY_BOKEH_MODE,
                mActivity.getString(R.string.pref_camera_bokeh_mode_default));
        String bokehMpo = mPreferences.getString(
                CameraSettings.KEY_BOKEH_MPO,
                mActivity.getString(R.string.pref_camera_bokeh_mpo_default));
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        final int bokehBlurDegree = prefs.getInt(CameraSettings.KEY_BOKEH_BLUR_VALUE,50);
        final boolean supportBokeh = CameraSettings.isBokehModeSupported(mParameters);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mUI.getCameraControls().setBokehMode(supportBokeh);
            }
        });

        if (!bokehMode.equals(mActivity.getString(
                R.string.pref_camera_bokeh_mode_entry_value_disable))) {
            mIsBokehMode = true;
            if(mParameters.getSceneMode() != Parameters.SCENE_MODE_AUTO) {
                mParameters.setSceneMode(Parameters.SCENE_MODE_AUTO);
            }
            if(mParameters.getFlashMode() != Parameters.FLASH_MODE_OFF) {
                mParameters.setFlashMode(Parameters.FLASH_MODE_OFF);
            }
            if(mParameters.get("long-shot").equals(mActivity.getString(R.string.setting_on_value))) {
                mParameters.set("long-shot",mActivity.getString(R.string.setting_off_value));
            }
        } else {
            mIsBokehMode = false;
        }
        mParameters.set(CameraSettings.KEY_QC_BOKEH_MODE, bokehMode);
        mParameters.set(CameraSettings.KEY_QC_BOKEH_MPO_MODE, bokehMpo);
        mParameters.set(CameraSettings.KEY_QC_BOKEH_BLUR_VALUE, bokehBlurDegree);
        Log.v(TAG, "Bokeh Mode = " + bokehMode + " bokehMpo = " + bokehMpo +
                " bokehBlurDegree = " + bokehBlurDegree);

        mLongShotMaxSnap = SystemProperties.getInt(PERSIST_LONGSHOT_MAX_SNAP, -1);
        mParameters.set("max-longshot-snap",mLongShotMaxSnap);
    }

    private int estimateJpegFileSize(final Size size, final String quality) {
        int[] ratios = mActivity.getResources().getIntArray(R.array.jpegquality_compression_ratio);
        String[] qualities = mActivity.getResources().getStringArray(
                R.array.pref_camera_jpegquality_entryvalues);
        int ratio = 0;
        for (int i = ratios.length - 1; i >= 0; --i) {
            if (qualities[i].equals(quality)) {
                ratio = ratios[i];
                break;
            }
        }

        if (ratio == 0) {
            return 0;
        } else {
            return size.width * size.height * 3 / ratio;
        }
    }

    private void setFlipValue() {
        // Read Flip mode from adb command
        //value: 0(default) - FLIP_MODE_OFF
        //value: 1 - FLIP_MODE_H
        //value: 2 - FLIP_MODE_V
        //value: 3 - FLIP_MODE_VH
        PersistUtil myUtil     = new PersistUtil();
        int preview_flip_value = myUtil.getPreviewFlip();
        int video_flip_value   = myUtil.getVideoFlip();
        int picture_flip_value = myUtil.getPictureFlip();

        int rotation = CameraUtil.getJpegRotation(mCameraId, mOrientation);
        mParameters.setRotation(rotation);
        if (rotation == 90 || rotation == 270) {
            // in case of 90 or 270 degree, V/H flip should reverse
            if (preview_flip_value == 1) {
                preview_flip_value = 2;
            } else if (preview_flip_value == 2) {
                preview_flip_value = 1;
            }
            if (video_flip_value == 1) {
                video_flip_value = 2;
            } else if (video_flip_value == 2) {
                video_flip_value = 1;
            }
            if (picture_flip_value == 1) {
                picture_flip_value = 2;
            } else if (picture_flip_value == 2) {
                picture_flip_value = 1;
            }
        }
        String preview_flip = CameraUtil.getFilpModeString(preview_flip_value);
        String video_flip = CameraUtil.getFilpModeString(video_flip_value);
        String picture_flip = CameraUtil.getFilpModeString(picture_flip_value);
        if(CameraUtil.isSupported(preview_flip, CameraSettings.getSupportedFlipMode(mParameters))){
            mParameters.set(CameraSettings.KEY_QC_PREVIEW_FLIP, preview_flip);
        }
        if(CameraUtil.isSupported(video_flip, CameraSettings.getSupportedFlipMode(mParameters))){
            mParameters.set(CameraSettings.KEY_QC_VIDEO_FLIP, video_flip);
        }
        if(CameraUtil.isSupported(picture_flip, CameraSettings.getSupportedFlipMode(mParameters))){
            mParameters.set(CameraSettings.KEY_QC_SNAPSHOT_PICTURE_FLIP, picture_flip);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setAutoExposureLockIfSupported() {
        if (mAeLockSupported) {
            mParameters.setAutoExposureLock(mFocusManager.getAeAwbLock());
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setAutoWhiteBalanceLockIfSupported() {
        if (mAwbLockSupported) {
            mParameters.setAutoWhiteBalanceLock(mFocusManager.getAeAwbLock());
        }
    }

    private void setFocusAreasIfSupported() {
        if (mFocusAreaSupported) {
            mParameters.setFocusAreas(mFocusManager.getFocusAreas());
        }
    }

    private void setMeteringAreasIfSupported() {
        if (mMeteringAreaSupported) {
            mParameters.setMeteringAreas(mFocusManager.getMeteringAreas());
        }
    }
    private void setZoomMenuValue() {
        String zoomMenuValue = mPreferences.getString(CameraSettings.KEY_ZOOM,
                                mActivity.getString(R.string.pref_camera_zoom_default));
        if (!zoomMenuValue.equals("0")) {
            int zoomValue = Integer.parseInt(zoomMenuValue);
            if (mZoomIdxTbl[0] == -1) {
                /* update the index table once */
                Log.d(TAG, "Update the zoom index table.");
                List<Integer> zoomRatios = mParameters.getZoomRatios();
                int lastZoomIdx = 0;
                for (int zoom = 1; zoom <= MAX_ZOOM; zoom++) {
                    int zoomIdx = zoomRatios.indexOf(zoom*100);
                    if (zoomIdx == -1) {
                       Log.d(TAG, "Can't find matching zoom value "+zoom);
                       int nextZoom = 0;
                       while ((++lastZoomIdx < zoomRatios.size()) &&
                              (nextZoom < (zoom*100))){
                           nextZoom = zoomRatios.get(lastZoomIdx);
                           zoomIdx = lastZoomIdx;
                       }
                       if (lastZoomIdx < zoomRatios.size()) {
                           zoomIdx = lastZoomIdx - 1;
                       } else {
                           break;
                       }
                    }
                    mZoomIdxTbl[zoom-1] = zoomIdx;
                    lastZoomIdx = zoomIdx;
                }
            }

            if ((zoomValue <= mZoomIdxTbl.length) &&
                (mZoomIdxTbl[zoomValue-1] != -1)) {
                int step = 1;
                int cur_zoom = mParameters.getZoom();
                Log.d(TAG, "zoom index = "+mZoomIdxTbl[zoomValue-1]+", cur index = "+cur_zoom);
                if (cur_zoom > mZoomIdxTbl[zoomValue-1]) {
                    step = -1;
                }

                /* move zoom slowly */
                while (cur_zoom != mZoomIdxTbl[zoomValue-1]) {
                    cur_zoom += step;
                    mParameters.setZoom(cur_zoom);
                    try {
                        Thread.sleep(25);
                    } catch(InterruptedException e) {
                    }
                }
                mParameters.setZoom(mZoomIdxTbl[zoomValue-1]);
            } else {
                Log.e(TAG, "Zoom value "+zoomValue+" is not supported!");
            }
        }
    }

    /** This can run on a background thread, so don't do UI updates here.*/
    private boolean updateCameraParametersPreference() {
        setAutoExposureLockIfSupported();
        setAutoWhiteBalanceLockIfSupported();
        setFocusAreasIfSupported();
        setMeteringAreasIfSupported();

        // Set picture size.
        String pictureSize = mPreferences.getString(
                CameraSettings.KEY_PICTURE_SIZE, null);
        if (pictureSize == null) {
            CameraSettings.initialCameraPictureSize(mActivity, mParameters);
        } else {
            Size old_size = mParameters.getPictureSize();
            Log.v(TAG, "old picture_size = " + old_size.width + " x " + old_size.height);
            List<Size> supported = mParameters.getSupportedPictureSizes();
            CameraSettings.setCameraPictureSize(
                    pictureSize, supported, mParameters);
            Size size = mParameters.getPictureSize();
            Log.v(TAG, "new picture_size = " + size.width + " x " + size.height);
            if (old_size != null && size != null) {
                if(!size.equals(old_size) && mCameraState != PREVIEW_STOPPED) {
                    Log.v(TAG, "Picture Size changed. Restart Preview.");
                    mRestartPreview = true;
                }
            }
        }
        Size size = mParameters.getPictureSize();

        // Set a preview size that is closest to the viewfinder height and has
        // the right aspect ratio.
        List<Size> sizes = mParameters.getSupportedPreviewSizes();
        Size optimalSize = CameraUtil.getOptimalPreviewSize(mActivity, sizes,
                (double) size.width / size.height);

        Point previewSize = PersistUtil.getCameraPreviewSize();
        if (previewSize != null) {
            optimalSize.width = previewSize.x;
            optimalSize.height = previewSize.y;
        }

        Log.d(TAG, "updateCameraParametersPreference final preview size = "
                + optimalSize.width + ", " + optimalSize.height);

        Size original = mParameters.getPreviewSize();
        if (!original.equals(optimalSize)) {
            mParameters.setPreviewSize(optimalSize.width, optimalSize.height);

            // Zoom related settings will be changed for different preview
            // sizes, so set and read the parameters to get latest values
            if (mHandler.getLooper() == Looper.myLooper()) {
                // On UI thread only, not when camera starts up
                setupPreview();
            } else {
                mCameraDevice.setParameters(mParameters);
            }
            mParameters = mCameraDevice.getParameters();
            Log.v(TAG, "Preview Size changed. Restart Preview");
            mRestartPreview = true;
        }

        Log.v(TAG, "Preview size is " + optimalSize.width + "x" + optimalSize.height);
        size = mParameters.getPictureSize();

        // Set jpegthumbnail size
        // Set a jpegthumbnail size that is closest to the Picture height and has
        // the right aspect ratio.
        List<Size> supported = mParameters.getSupportedJpegThumbnailSizes();
        optimalSize = CameraUtil.getOptimalJpegThumbnailSize(supported,
                (double) size.width / size.height);
        original = mParameters.getJpegThumbnailSize();
        if (!original.equals(optimalSize)) {
            mParameters.setJpegThumbnailSize(optimalSize.width, optimalSize.height);
        }

        Log.v(TAG, "Thumbnail size is " + optimalSize.width + "x" + optimalSize.height);

        // Since changing scene mode may change supported values, set scene mode
        // first. HDR is a scene mode. To promote it in UI, it is stored in a
        // separate preference.
        String onValue = mActivity.getString(R.string.setting_on_value);
        String hdr = mPreferences.getString(CameraSettings.KEY_CAMERA_HDR,
                mActivity.getString(R.string.pref_camera_hdr_default));
        boolean hdrOn = onValue.equals(hdr);


        if (hdrOn) {
            mSceneMode = CameraUtil.SCENE_MODE_HDR;
            if (!(Parameters.SCENE_MODE_AUTO).equals(mParameters.getSceneMode())
                && !(Parameters.SCENE_MODE_HDR).equals(mParameters.getSceneMode())) {
                mParameters.setSceneMode(Parameters.SCENE_MODE_AUTO);
                mCameraDevice.setParameters(mParameters);
                mParameters = mCameraDevice.getParameters();
            }
        } else {
            mSceneMode = mPreferences.getString(
                    CameraSettings.KEY_SCENE_MODE,
                    mActivity.getString(R.string.pref_camera_scenemode_default));
        }

        if (mSceneMode == null) {
            mSceneMode = Parameters.SCENE_MODE_AUTO;
        }

        if (CameraUtil.isSupported(mSceneMode, mParameters.getSupportedSceneModes())) {
            if (!mParameters.getSceneMode().equals(mSceneMode)) {
                mParameters.setSceneMode(mSceneMode);

                // Setting scene mode will change the settings of flash mode,
                // white balance, and focus mode. Here we read back the
                // parameters, so we can know those settings.
                mCameraDevice.setParameters(mParameters);
                mParameters = mCameraDevice.getParameters();
            }
        }

        // Set JPEG quality.
        int jpegQuality;
        if(mCameraId>1) {
            jpegQuality=95; //Temproray Solution for camera ids greater than 1. Proper fix TBD.
        } else {
            jpegQuality = CameraProfile.getJpegEncodingQualityParameter(mCameraId,
                CameraProfile.QUALITY_HIGH);
        }

        mParameters.setJpegQuality(jpegQuality);

        if (Parameters.SCENE_MODE_AUTO.equals(mSceneMode) ||
                "asd".equals(mSceneMode) ||
                "sports".equals(mSceneMode)) {
            // Set flash mode.
            String flashMode;
            if (mSavedFlashMode == null) {
                flashMode = mPreferences.getString(
                    CameraSettings.KEY_FLASH_MODE,
                    mActivity.getString(R.string.pref_camera_flashmode_default));
            } else {
                flashMode = mSavedFlashMode;
            }

            List<String> supportedFlash = mParameters.getSupportedFlashModes();
            if (CameraUtil.isSupported(flashMode, supportedFlash)) {
                mParameters.setFlashMode(flashMode);
            } else {
                flashMode = mParameters.getFlashMode();
                if (flashMode == null) {
                    flashMode = mActivity.getString(
                            R.string.pref_camera_flashmode_no_flash);
                }
            }

            // Set focus time.
            mFocusManager.setFocusTime(Integer.decode(
                    mPreferences.getString(CameraSettings.KEY_FOCUS_TIME,
                    mActivity.getString(R.string.pref_camera_focustime_default))));
        } else {
            mFocusManager.overrideFocusMode(mParameters.getFocusMode());
            String flashMode = Parameters.FLASH_MODE_OFF;
            if (CameraUtil.isSupported(flashMode,
                    mParameters.getSupportedFlashModes())) {
                mParameters.setFlashMode(flashMode);
            }
        }

        if (mContinuousFocusSupported && ApiHelper.HAS_AUTO_FOCUS_MOVE_CALLBACK) {
            updateAutoFocusMoveCallback();
        }

        setZoomMenuValue();

        //QCom related parameters updated here.
        qcomUpdateCameraParametersPreference();
        return false;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void updateAutoFocusMoveCallback() {
        if (mParameters.getFocusMode().equals(CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE) ||
            mParameters.getFocusMode().equals(CameraUtil.FOCUS_MODE_MW_CONTINUOUS_PICTURE)) {
            mCameraDevice.setAutoFocusMoveCallback(mHandler,
                    (CameraAFMoveCallback) mAutoFocusMoveCallback);
        } else {
            mCameraDevice.setAutoFocusMoveCallback(null, null);
        }
    }

    // We separate the parameters into several subsets, so we can update only
    // the subsets actually need updating. The PREFERENCE set needs extra
    // locking because the preference can be changed from GLThread as well.
    private void setCameraParameters(int updateSet) {
        if (mCameraDevice == null) {
            return;
        }
        synchronized (mCameraDevice) {
            boolean doModeSwitch = false;

            if ((updateSet & UPDATE_PARAM_INITIALIZE) != 0) {
                updateCameraParametersInitialize();
            }

            if ((updateSet & UPDATE_PARAM_ZOOM) != 0) {
                updateCameraParametersZoom();
            }

            if ((updateSet & UPDATE_PARAM_PREFERENCE) != 0) {
                doModeSwitch = updateCameraParametersPreference();
            }

            CameraUtil.dumpParameters(mParameters);
            mCameraDevice.setParameters(mParameters);
            mFocusManager.setParameters(mParameters);
        }
    }

    // If the Camera is idle, update the parameters immediately, otherwise
    // accumulate them in mUpdateSet and update later.
    private void setCameraParametersWhenIdle(int additionalUpdateSet) {
        mUpdateSet |= additionalUpdateSet;
        if (mCameraDevice == null) {
            // We will update all the parameters when we open the device, so
            // we don't need to do anything now.
            mUpdateSet = 0;
            return;
        } else if (isCameraIdle()) {
            setCameraParameters(mUpdateSet);
             if(mRestartPreview && mCameraState != PREVIEW_STOPPED) {
                Log.v(TAG, "Restarting Preview...");
                stopPreview();
                resizeForPreviewAspectRatio();
                startPreview();
                setCameraState(IDLE);
            }
            mRestartPreview = false;
            updateCameraSettings();
            mUpdateSet = 0;
        } else {
            if (!mHandler.hasMessages(SET_CAMERA_PARAMETERS_WHEN_IDLE)) {
                mHandler.sendEmptyMessageDelayed(
                        SET_CAMERA_PARAMETERS_WHEN_IDLE, 1000);
            }
        }
    }

    @Override
    public boolean isCameraIdle() {
        return (mCameraState == IDLE) ||
                (mCameraState == PREVIEW_STOPPED) ||
                ((mFocusManager != null) && mFocusManager.isFocusCompleted()
                        && (mCameraState != SWITCHING_CAMERA));
    }

    @Override
    public boolean isImageCaptureIntent() {
        String action = mActivity.getIntent().getAction();
        return (MediaStore.ACTION_IMAGE_CAPTURE.equals(action)
                || CameraActivity.ACTION_IMAGE_CAPTURE_SECURE.equals(action));
    }

    private void setupCaptureParams() {
        Bundle myExtras = mActivity.getIntent().getExtras();
        if (myExtras != null) {
            mSaveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            mCropValue = myExtras.getString("crop");
        }
    }

    // Return true if the preference has the specified key but not the value.
    private static boolean notSame(ListPreference pref, String key, String value) {
        return (key.equals(pref.getKey()) && !value.equals(pref.getValue()));
    }

    @Override
    public void onSharedPreferenceChanged(ListPreference pref) {
        // ignore the events after "onPause()"
        if (mPaused) return;


        if (CameraSettings.KEY_CAMERA_SAVEPATH.equals(pref.getKey())) {
            Storage.setSaveSDCard(
                    mPreferences.getString(CameraSettings.KEY_CAMERA_SAVEPATH, "0").equals("1"));
            mActivity.updateStorageSpaceAndHint();
            updateRemainingPhotos();
        }

        //call generic onSharedPreferenceChanged
        onSharedPreferenceChanged();
    }

    @Override
    public void onSharedPreferenceChanged() {
        // ignore the events after "onPause()"
        if (mPaused) return;

        boolean recordLocation = RecordLocationPreference.get(mPreferences,
                CameraSettings.KEY_RECORD_LOCATION);
        mLocationManager.recordLocation(recordLocation);
        if(needRestart()){
            Log.v(TAG, "Restarting Preview... Camera Mode Changed");
            setCameraParameters(UPDATE_PARAM_PREFERENCE);
            stopPreview();
            startPreview();
            setCameraState(IDLE);
            mRestartPreview = false;
        }
        /* Check if the SPhotoUI Menu is initialized or not. This
         * should be initialized during onCameraOpen() which should
         * have been called by now. But for some reason that is not
         * executed till now, then schedule these functionality for
         * later by posting a message to the handler */
        if (mUI.mMenuInitialized) {
            setCameraParametersWhenIdle(UPDATE_PARAM_PREFERENCE);
            mActivity.initPowerShutter(mPreferences);
            mActivity.initMaxBrightness(mPreferences);
        } else {
            mHandler.sendEmptyMessage(SET_PHOTO_UI_PARAMS);
        }
        resizeForPreviewAspectRatio();
    }

    @Override
    public void onCameraPickerClicked(int cameraId) {
        if (mPaused || mPendingSwitchCameraId != -1) return;

        mPendingSwitchCameraId = cameraId;

        Log.v(TAG, "Start to switch camera. cameraId=" + cameraId);
        // We need to keep a preview frame for the animation before
        // releasing the camera. This will trigger onPreviewTextureCopied.
        //TODO: Need to animate the camera switch
        switchCamera();
    }

    // Preview texture has been copied. Now camera can be released and the
    // animation can be started.
    @Override
    public void onPreviewTextureCopied() {
        mHandler.sendEmptyMessage(SWITCH_CAMERA);
    }

    @Override
    public void onCaptureTextureCopied() {
    }

    @Override
    public void onUserInteraction() {
        if (!mActivity.isFinishing()) keepScreenOnAwhile();
    }

    private void resetScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void keepScreenOnAwhile() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);
    }

    @Override
    public void onOverriddenPreferencesClicked() {
        if (mPaused) return;
        mUI.showPreferencesToast();
    }

    private void showTapToFocusToast() {
        // TODO: Use a toast?
        new RotateTextToast(mActivity, R.string.tap_to_focus, 0).show();
        // Clear the preference.
        Editor editor = mPreferences.edit();
        editor.putBoolean(CameraSettings.KEY_CAMERA_FIRST_USE_HINT_SHOWN, false);
        editor.apply();
    }

    private void initializeCapabilities() {
        mFocusAreaSupported = CameraUtil.isFocusAreaSupported(mInitialParams);
        mMeteringAreaSupported = CameraUtil.isMeteringAreaSupported(mInitialParams);
        mAeLockSupported = CameraUtil.isAutoExposureLockSupported(mInitialParams);
        mAwbLockSupported = CameraUtil.isAutoWhiteBalanceLockSupported(mInitialParams);

        List<String> focusModes = mInitialParams.getSupportedFocusModes();
        if (focusModes != null &&
                (focusModes.contains(CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE) ||
                focusModes.contains(CameraUtil.FOCUS_MODE_MW_CONTINUOUS_PICTURE))) {
            mContinuousFocusSupported = true;
        } else {
            mContinuousFocusSupported = false;
        }
    }

    @Override
    public void onCountDownFinished() {
        mSnapshotOnIdle = false;
        initiateSnap();
        mFocusManager.onShutterUp();
        mUI.showUIAfterCountDown();
    }

    @Override
    public void onShowSwitcherPopup() {
        mUI.onShowSwitcherPopup();
    }

    @Override
    public int onZoomChanged(int index) {
        // Not useful to change zoom value when the activity is paused.
        if (mPaused) return index;
        mZoomValue = index;
        if (mParameters == null || mCameraDevice == null) return index;
        if ( mFocusManager != null
                && mFocusManager.getCurrentFocusState() == FocusOverlayManager.STATE_FOCUSING ) {
            mFocusManager.cancelAutoFocus();
        }
        // Set zoom parameters asynchronously
        synchronized (mCameraDevice) {
            mParameters.setZoom(mZoomValue);
            mCameraDevice.setParameters(mParameters);
            Parameters p = mCameraDevice.getParameters();
            if (p != null) return p.getZoom();
        }
        return index;
    }

    @Override
    public void onZoomChanged(float requestedZoom) {
        if ( mFocusManager != null
                && mFocusManager.getCurrentFocusState() == FocusOverlayManager.STATE_FOCUSING ) {
            mFocusManager.cancelAutoFocus();
        }
    }

    @Override
    public int getCameraState() {
        return mCameraState;
    }

    @Override
    public void onQueueStatus(boolean full) {
        mUI.enableShutter(!full);
    }

    @Override
    public void onMediaSaveServiceConnected(MediaSaveService s) {
        // We set the listener only when both service and shutterbutton
        // are initialized.
        if (mFirstTimeInitialized) {
            s.setListener(this);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        float[] data;
        if (type == Sensor.TYPE_ACCELEROMETER) {
            data = mGData;
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            data = mMData;
        } else {
            // we should not be here.
            return;
        }
        for (int i = 0; i < 3 ; i++) {
            data[i] = event.values[i];
        }
        float[] orientation = new float[3];
        SensorManager.getRotationMatrix(mR, null, mGData, mMData);
        SensorManager.getOrientation(mR, orientation);
        mHeading = (int) (orientation[0] * 180f / Math.PI) % 360;
        if (mHeading < 0) {
            mHeading += 360;
        }
    }
    @Override
    public void onPreviewFocusChanged(boolean previewFocused) {
        mUI.onPreviewFocusChanged(previewFocused);
    }
    // TODO: Delete this function after old camera code is removed
    @Override
    public void onRestorePreferencesClicked() {}

/*
 * Provide a mapping for Jpeg encoding quality levels
 * from String representation to numeric representation.
 */
    @Override
    public boolean arePreviewControlsVisible() {
        return mUI.arePreviewControlsVisible();
    }

    // For debugging only.
    public void setDebugUri(Uri uri) {
        mDebugUri = uri;
    }

    // For debugging only.
    private void saveToDebugUri(byte[] data) {
        if (mDebugUri != null) {
            OutputStream outputStream = null;
            try {
                outputStream = mContentResolver.openOutputStream(mDebugUri);
                outputStream.write(data);
                outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Exception while writing debug jpeg file", e);
            } finally {
                CameraUtil.closeSilently(outputStream);
            }
        }
    }

    @Override
    public void onErrorListener(int error) {
        enableRecordingLocation(false);
    }

    public boolean isLongshotDone() {
        return ((mCameraState == LONGSHOT) && (mLongshotSnapNum == mReceivedSnapNum) &&
                !mLongshotActive);
    }
}

/* Below is no longer needed, except to get rid of compile error
 * TODO: Remove these
 */
class SJpegEncodingQualityMappings {
    private static final String TAG = "SJpegEncodingQualityMappings";
    private static final int DEFAULT_QUALITY = 85;
    private static HashMap<String, Integer> mHashMap =
            new HashMap<String, Integer>();

    static {
        mHashMap.put("normal",    CameraProfile.QUALITY_LOW);
        mHashMap.put("fine",      CameraProfile.QUALITY_MEDIUM);
        mHashMap.put("superfine", CameraProfile.QUALITY_HIGH);
    }

    // Retrieve and return the Jpeg encoding quality number
    // for the given quality level.
    public static int getQualityNumber(String jpegQuality) {
        try{
            int qualityPercentile = Integer.parseInt(jpegQuality);
            if(qualityPercentile >= 0 && qualityPercentile <=100)
                return qualityPercentile;
            else
                return DEFAULT_QUALITY;
        } catch(NumberFormatException nfe){
            //chosen quality is not a number, continue
        }
        Integer quality = mHashMap.get(jpegQuality);
        if (quality == null) {
            Log.w(TAG, "Unknown Jpeg quality: " + jpegQuality);
            return DEFAULT_QUALITY;
        }
        return CameraProfile.getJpegEncodingQualityParameter(quality.intValue());
    }
}

class SCameraGraphView extends View {
    private Bitmap  mBitmap;
    private Paint   mPaint = new Paint();
    private Paint   mPaintRect = new Paint();
    private Canvas  mCanvas = new Canvas();
    private float   mScale = (float)3;
    private float   mWidth;
    private float   mHeight;
    private SPhotoModule mPhotoModule;
    private CameraManager.CameraProxy mGraphCameraDevice;
    private float scaled;
    private static final int STATS_SIZE = 256;


    public SCameraGraphView(Context context, AttributeSet attrs) {
        super(context,attrs);

        mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mPaintRect.setColor(0xFFFFFFFF);
        mPaintRect.setStyle(Paint.Style.FILL);
    }
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        mCanvas.setBitmap(mBitmap);
        mWidth = w;
        mHeight = h;
        super.onSizeChanged(w, h, oldw, oldh);
    }
    @Override
    protected void onDraw(Canvas canvas) {
        if(mPhotoModule == null) {
            return;
        }

        if (mBitmap != null) {
            final Paint paint = mPaint;
            final Canvas cavas = mCanvas;
            final float border = 5;
            float graphheight = mHeight - (2 * border);
            float graphwidth = mWidth - (2 * border);
            float left,top,right,bottom;
            float bargap = 0.0f;
            float barwidth = graphwidth/STATS_SIZE;

            cavas.drawColor(0xFFAAAAAA);
            paint.setColor(Color.BLACK);

            for (int k = 0; k <= (graphheight /32) ; k++) {
                float y = (float)(32 * k)+ border;
                cavas.drawLine(border, y, graphwidth + border , y, paint);
            }
            for (int j = 0; j <= (graphwidth /32); j++) {
                float x = (float)(32 * j)+ border;
                cavas.drawLine(x, border, x, graphheight + border, paint);
            }
            synchronized(SPhotoModule.statsdata) {
                 //Assumption: The first element contains
                //            the maximum value.
                int maxValue = Integer.MIN_VALUE;
                if ( 0 == SPhotoModule.statsdata[0] ) {
                    for ( int i = 1 ; i <= STATS_SIZE ; i++ ) {
                         if ( maxValue < SPhotoModule.statsdata[i] ) {
                             maxValue = SPhotoModule.statsdata[i];
                         }
                    }
                } else {
                    maxValue = SPhotoModule.statsdata[0];
                }
                mScale = ( float ) maxValue;
                for(int i=1 ; i<=STATS_SIZE ; i++)  {
                    scaled = (SPhotoModule.statsdata[i]/mScale)*STATS_SIZE;
                    if(scaled >= (float)STATS_SIZE)
                        scaled = (float)STATS_SIZE;
                    left = (bargap * (i+1)) + (barwidth * i) + border;
                    top = graphheight + border;
                    right = left + barwidth;
                    bottom = top - scaled;
                    cavas.drawRect(left, top, right, bottom, mPaintRect);
                }
            }
            canvas.drawBitmap(mBitmap, 0, 0, null);
        }
    }
    public void PreviewChanged() {
        invalidate();
    }
    public void setSPhotoModuleObject(SPhotoModule photoModule) {
        mPhotoModule = photoModule;
    }
}
