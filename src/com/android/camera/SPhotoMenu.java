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

import java.util.HashSet;
import java.util.Locale;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.Camera.Parameters;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewPropertyAnimator;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.app.CameraApp;
import com.android.camera.ui.CameraControls;
import com.android.camera.ui.CountdownTimerPopup;
import com.android.camera.ui.ListSubMenu;
import com.android.camera.ui.ListMenu;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.RotateTextToast;
import android.widget.HorizontalScrollView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.Display;
import com.android.camera.util.CameraUtil;
import java.util.Locale;

import org.codeaurora.snapcam.R;
import org.codeaurora.snapcam.wrapper.ParametersWrapper;

public class SPhotoMenu extends MenuController
        implements ListMenu.Listener,
        CountdownTimerPopup.Listener,
        ListSubMenu.Listener {
    private static String TAG = "SPhotoMenu";

    private final String mSettingOff;
    private final String mSettingOn;

    private String[] mOtherKeys1;
    private String[] mOtherKeys2;
    private ListMenu mListMenu;
    private View mPreviewMenu;
    private static final int POPUP_NONE = 0;
    private static final int POPUP_FIRST_LEVEL = 1;
    private static final int POPUP_SECOND_LEVEL = 2;
    private static final int POPUP_IN_ANIMATION_SLIDE = 3;
    private static final int POPUP_IN_ANIMATION_FADE = 4;
    private static final int PREVIEW_MENU_NONE = 0;
    private static final int PREVIEW_MENU_IN_ANIMATION = 1;
    private static final int PREVIEW_MENU_ON = 2;
    private static final int MODE_SCENE = 0;
    private static final int MODE_FILTER = 1;
    private static final int DEVELOPER_MENU_TOUCH_COUNT = 7;
    private int mSceneStatus;
    private View mHdrSwitcher;
    private View mMeteringSwitcher;
    private View mBokehSwitcher;
    private View mFrontBackSwitcher;
    private View mSceneModeSwitcher;
    private View mFilterModeSwitcher;
    private View mCameraSwitcher;
    private View mSettingMenu;
    private View mPreviewThumbnail;
    private SPhotoUI mUI;
    private int mPopupStatus;
    private int mPreviewMenuStatus;
    private ListSubMenu mListSubMenu;
    private CameraActivity mActivity;
    private int mPrivateCounter = 0;
    private static final int ANIMATION_DURATION = 300;
    private static final int CLICK_THRESHOLD = 200;
    private int previewMenuSize;
    private HashSet<View> mWasVisibleSet = new HashSet<View>();

    public SPhotoMenu(CameraActivity activity, SPhotoUI ui) {
        super(activity);
        mUI = ui;
        mSettingOff = activity.getString(R.string.setting_off_value);
        mSettingOn = activity.getString(R.string.setting_on_value);
        mActivity = activity;
        mFrontBackSwitcher = ui.getRootView().findViewById(R.id.front_back_switcher);
        mHdrSwitcher = ui.getRootView().findViewById(R.id.hdr_switcher);
        mMeteringSwitcher = ui.getRootView().findViewById(R.id.metering_switcher);
        mSceneModeSwitcher = ui.getRootView().findViewById(R.id.scene_mode_switcher);
        mBokehSwitcher = ui.getRootView().findViewById(R.id.bokeh_switcher);
        mFilterModeSwitcher = ui.getRootView().findViewById(R.id.filter_mode_switcher);
        mSettingMenu = ui.getRootView().findViewById(R.id.menu);
        mCameraSwitcher = ui.getRootView().findViewById(R.id.camera_switcher);
        mPreviewThumbnail = ui.getRootView().findViewById(R.id.preview_thumb);
    }

    public void initialize(PreferenceGroup group) {
        super.initialize(group);
        mListSubMenu = null;
        mListMenu = null;
        mPopupStatus = POPUP_NONE;
        mPreviewMenuStatus = POPUP_NONE;
        final Resources res = mActivity.getResources();
        Locale locale = res.getConfiguration().locale;
        // The order is from left to right in the menu.

        initSceneModeButton(mSceneModeSwitcher);
        initFilterModeButton(mFilterModeSwitcher);
        initBokehModeButton(mBokehSwitcher);

        mFrontBackSwitcher.setVisibility(View.INVISIBLE);

        if (group.findPreference(CameraSettings.KEY_EXYNOS_CAMERA_RT_HDR) != null) {
            mHdrSwitcher.setVisibility(View.VISIBLE);
            initSwitchItem(CameraSettings.KEY_EXYNOS_CAMERA_RT_HDR, mHdrSwitcher);
        } else {
            mHdrSwitcher.setVisibility(View.INVISIBLE);
        }

        if (group.findPreference(CameraSettings.KEY_EXYNOS_METERING_MODE) != null) {
            mMeteringSwitcher.setVisibility(View.VISIBLE);
            initSwitchItem(CameraSettings.KEY_EXYNOS_METERING_MODE, mMeteringSwitcher);
        } else {
            mMeteringSwitcher.setVisibility(View.INVISIBLE);
        }

        mOtherKeys1 = new String[] {
                CameraSettings.KEY_SELFIE_FLASH,
                CameraSettings.KEY_FLASH_MODE,
                CameraSettings.KEY_RECORD_LOCATION,
                CameraSettings.KEY_PICTURE_SIZE,
                CameraSettings.KEY_TIMER,
                CameraSettings.KEY_CAMERA_SAVEPATH,
                CameraSettings.KEY_LONGSHOT,
                CameraSettings.KEY_FACE_DETECTION,
                CameraSettings.KEY_FOCUS_TIME,
                CameraSettings.KEY_SELFIE_MIRROR,
                CameraSettings.KEY_SHUTTER_SOUND,
                CameraSettings.KEY_POWER_SHUTTER,
                CameraSettings.KEY_EXYNOS_EXPOSURE_COMPENSATION,
                CameraSettings.KEY_EXYNOS_SATURATION,
                CameraSettings.KEY_EXYNOS_SHARPNESS,
                CameraSettings.KEY_ANTIBANDING,
        };

        mOtherKeys2 = new String[] {
                CameraSettings.KEY_SELFIE_FLASH,
                CameraSettings.KEY_FLASH_MODE,
                CameraSettings.KEY_RECORD_LOCATION,
                CameraSettings.KEY_PICTURE_SIZE,
                CameraSettings.KEY_TIMER,
                CameraSettings.KEY_CAMERA_SAVEPATH,
                CameraSettings.KEY_LONGSHOT,
                CameraSettings.KEY_FACE_DETECTION,
                CameraSettings.KEY_FOCUS_MODE,
                CameraSettings.KEY_FOCUS_TIME,
                CameraSettings.KEY_POWER_SHUTTER,
                CameraSettings.KEY_MAX_BRIGHTNESS,
                CameraSettings.KEY_EXYNOS_EXPOSURE_COMPENSATION,
                CameraSettings.KEY_EXYNOS_SATURATION,
                CameraSettings.KEY_EXYNOS_SHARPNESS,
                CameraSettings.KEY_ANTIBANDING,
                CameraSettings.KEY_TIMER_SOUND_EFFECTS,
                CameraSettings.KEY_FACE_RECOGNITION,
                CameraSettings.KEY_PICTURE_FORMAT,
                CameraSettings.KEY_SELFIE_MIRROR,
                CameraSettings.KEY_SHUTTER_SOUND,
                CameraSettings.KEY_ZOOM
        };

        initSwitchItem(CameraSettings.KEY_CAMERA_ID, mFrontBackSwitcher);
    }

    @Override
    // Hit when an item in a popup gets selected
    public void onListPrefChanged(ListPreference pref) {
        onSettingChanged(pref);
        closeView();
    }

    public boolean handleBackKey() {
        if (mPreviewMenuStatus == PREVIEW_MENU_ON) {
            animateSlideOut(mPreviewMenu);
            return true;
        }
        if (mPopupStatus == POPUP_NONE)
            return false;
        if (mPopupStatus == POPUP_FIRST_LEVEL) {
            animateSlideOut(mListMenu, 1);
        } else if (mPopupStatus == POPUP_SECOND_LEVEL) {
            animateFadeOut(mListSubMenu, 2);
            ((ListMenu) mListMenu).resetHighlight();
        }
        return true;
    }

    public void closeSceneMode() {
        mUI.removeSceneModeMenu();
    }

    public void tryToCloseSubList() {
        if (mListMenu != null)
            ((ListMenu) mListMenu).resetHighlight();

        if (mPopupStatus == POPUP_SECOND_LEVEL) {
            mUI.dismissLevel2();
            mPopupStatus = POPUP_FIRST_LEVEL;
        }
    }

    private void animateFadeOut(final ListView v, final int level) {
        if (v == null || mPopupStatus == POPUP_IN_ANIMATION_FADE)
            return;
        mPopupStatus = POPUP_IN_ANIMATION_FADE;

        ViewPropertyAnimator vp = v.animate();
        vp.alpha(0f).setDuration(ANIMATION_DURATION);
        vp.setListener(new AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (level == 1) {
                    mUI.dismissLevel1();
                    initializePopup();
                    mPopupStatus = POPUP_NONE;
                    mUI.cleanupListview();
                }
                else if (level == 2) {
                    mUI.dismissLevel2();
                    mPopupStatus = POPUP_FIRST_LEVEL;
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (level == 1) {
                    mUI.dismissLevel1();
                    initializePopup();
                    mPopupStatus = POPUP_NONE;
                    mUI.cleanupListview();
                }
                else if (level == 2) {
                    mUI.dismissLevel2();
                    mPopupStatus = POPUP_FIRST_LEVEL;
                }

            }
        });
        vp.start();
    }

    private void animateSlideOut(final ListView v, final int level) {
        if (v == null || mPopupStatus == POPUP_IN_ANIMATION_SLIDE)
            return;
        mPopupStatus = POPUP_IN_ANIMATION_SLIDE;

        ViewPropertyAnimator vp = v.animate();
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            switch (mUI.getOrientation()) {
                case 0:
                    vp.translationXBy(v.getWidth());
                    break;
                case 90:
                    vp.translationYBy(-2 * v.getHeight());
                    break;
                case 180:
                    vp.translationXBy(-2 * v.getWidth());
                    break;
                case 270:
                    vp.translationYBy(v.getHeight());
                    break;
            }
        } else {
            switch (mUI.getOrientation()) {
                case 0:
                    vp.translationXBy(-v.getWidth());
                    break;
                case 90:
                    vp.translationYBy(2 * v.getHeight());
                    break;
                case 180:
                    vp.translationXBy(2 * v.getWidth());
                    break;
                case 270:
                    vp.translationYBy(-v.getHeight());
                    break;
            }
        }
        vp.setListener(new AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (level == 1) {
                    mUI.dismissLevel1();
                    initializePopup();
                    mPopupStatus = POPUP_NONE;
                    mUI.cleanupListview();
                } else if (level == 2) {
                    mUI.dismissLevel2();
                    mPopupStatus = POPUP_FIRST_LEVEL;
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (level == 1) {
                    mUI.dismissLevel1();
                    initializePopup();
                    mPopupStatus = POPUP_NONE;
                    mUI.cleanupListview();
                } else if (level == 2) {
                    mUI.dismissLevel2();
                    mPopupStatus = POPUP_FIRST_LEVEL;
                }

            }
        });
        vp.setDuration(ANIMATION_DURATION).start();
    }

    public void animateFadeIn(final ListView v) {
        ViewPropertyAnimator vp = v.animate();
        vp.alpha(1f).setDuration(ANIMATION_DURATION);
        vp.start();
    }

    public void animateSlideIn(final View v, int delta, boolean forcePortrait) {
        int orientation = mUI.getOrientation();
        if (!forcePortrait)
            orientation = 0;

        ViewPropertyAnimator vp = v.animate();
        float dest;
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            switch (orientation) {
                case 0:
                    dest = v.getX();
                    v.setX(-(dest - delta));
                    vp.translationX(dest);
                    break;
                case 90:
                    dest = v.getY();
                    v.setY(-(dest + delta));
                    vp.translationY(dest);
                    break;
                case 180:
                    dest = v.getX();
                    v.setX(-(dest + delta));
                    vp.translationX(dest);
                    break;
                case 270:
                    dest = v.getY();
                    v.setY(-(dest - delta));
                    vp.translationY(dest);
                    break;
            }
        } else {
            switch (orientation) {
                case 0:
                    dest = v.getX();
                    v.setX(dest - delta);
                    vp.translationX(dest);
                    break;
                case 90:
                    dest = v.getY();
                    v.setY(dest + delta);
                    vp.translationY(dest);
                    break;
                case 180:
                    dest = v.getX();
                    v.setX(dest + delta);
                    vp.translationX(dest);
                    break;
                case 270:
                    dest = v.getY();
                    v.setY(dest - delta);
                    vp.translationY(dest);
                    break;
            }
        }
        vp.setDuration(ANIMATION_DURATION).start();
    }

    public void animateSlideOutPreviewMenu() {
        if (mPreviewMenu == null)
            return;
        animateSlideOut(mPreviewMenu);
    }

    private void animateSlideOut(final View v) {
        if (v == null || mPreviewMenuStatus == PREVIEW_MENU_IN_ANIMATION)
            return;
        mPreviewMenuStatus = PREVIEW_MENU_IN_ANIMATION;

        ViewPropertyAnimator vp = v.animate();
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            vp.translationXBy(v.getWidth()).setDuration(ANIMATION_DURATION);
        } else {
            vp.translationXBy(-v.getWidth()).setDuration(ANIMATION_DURATION);
        }
        vp.setListener(new AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                closeSceneMode();
                mPreviewMenuStatus = PREVIEW_MENU_NONE;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                closeSceneMode();
                mPreviewMenuStatus = PREVIEW_MENU_NONE;

            }
        });
        vp.start();
    }

    private void buttonSetEnabled(View v, boolean enable) {
        v.setEnabled(enable);
        if (v instanceof ViewGroup) {
            View v2 = ((ViewGroup) v).getChildAt(0);
            if (v2 != null)
                v2.setEnabled(enable);
        }
    }

    public boolean isOverMenu(MotionEvent ev) {
        if (mPopupStatus == POPUP_NONE
                || mPopupStatus == POPUP_IN_ANIMATION_SLIDE
                || mPopupStatus == POPUP_IN_ANIMATION_FADE)
            return false;
        if (mUI.getMenuLayout() == null)
            return false;
        Rect rec = new Rect();
        mUI.getMenuLayout().getChildAt(0).getHitRect(rec);
        return rec.contains((int) ev.getX(), (int) ev.getY());
    }

    public boolean isOverPreviewMenu(MotionEvent ev) {
        if (mPreviewMenuStatus != PREVIEW_MENU_ON)
            return false;
        if (mUI.getPreviewMenuLayout() == null)
            return false;
        Rect rec = new Rect();
        mUI.getPreviewMenuLayout().getChildAt(0).getHitRect(rec);
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            rec.left = mUI.getRootView().getWidth() - (rec.right-rec.left);
            rec.right = mUI.getRootView().getWidth();
        }
        rec.top += (int) mUI.getPreviewMenuLayout().getY();
        rec.bottom += (int) mUI.getPreviewMenuLayout().getY();
        return rec.contains((int) ev.getX(), (int) ev.getY());
    }

    public boolean isMenuBeingShown() {
        return mPopupStatus != POPUP_NONE;
    }

    public boolean isMenuBeingAnimated() {
        return mPopupStatus == POPUP_IN_ANIMATION_SLIDE || mPopupStatus == POPUP_IN_ANIMATION_FADE;
    }

    public boolean isPreviewMenuBeingShown() {
        return mPreviewMenuStatus == PREVIEW_MENU_ON;
    }

    public boolean isPreviewMenuBeingAnimated() {
        return mPreviewMenuStatus == PREVIEW_MENU_IN_ANIMATION;
    }

    public boolean sendTouchToPreviewMenu(MotionEvent ev) {
        return mUI.sendTouchToPreviewMenu(ev);
    }

    public boolean sendTouchToMenu(MotionEvent ev) {
        return mUI.sendTouchToMenu(ev);
    }

    @Override
    public void overrideSettings(final String... keyvalues) {
        for (int i = 0; i < keyvalues.length; i += 2) {
            if (keyvalues[i].equals(CameraSettings.KEY_EXYNOS_SCENE_MODE)) {
                buttonSetEnabled(mSceneModeSwitcher, keyvalues[i + 1] == null);
            }
        }
        super.overrideSettings(keyvalues);
        if ((mListMenu == null))
            initializePopup();
        mListMenu.overrideSettings(keyvalues);
    }

    protected void initializePopup() {
        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        ListMenu popup1 = (ListMenu) inflater.inflate(
                R.layout.list_menu, null, false);

        popup1.setSettingChangedListener(this);

        String[] keys = mOtherKeys1;
        if (mActivity.isDeveloperMenuEnabled())
            keys = mOtherKeys2;
        popup1.initialize(mPreferenceGroup, keys);
        if (mActivity.isSecureCamera()) {
            // Prevent location preference from getting changed in secure camera
            // mode
            popup1.setPreferenceEnabled(CameraSettings.KEY_RECORD_LOCATION, false);
        }
        mListMenu = popup1;

        ListPreference pref = mPreferenceGroup.findPreference(
                CameraSettings.KEY_EXYNOS_SCENE_MODE);
        updateFilterModeIcon(pref, mPreferenceGroup.findPreference(CameraSettings.KEY_EXYNOS_CAMERA_RT_HDR));
        String sceneMode = (pref != null) ? pref.getValue() : null;
        pref = mPreferenceGroup.findPreference(CameraSettings.KEY_FACE_DETECTION);
        String faceDetection = (pref != null) ? pref.getValue() : null;
        if ((sceneMode != null) && !(sceneMode.equals("auto") || sceneMode.equals("pro-mode"))) {
            mHdrSwitcher.setVisibility(View.GONE);
            mUI.getCameraControls().removeFromViewList(mHdrSwitcher);
        } else {
            mHdrSwitcher.setVisibility(View.VISIBLE);
        }
        if ((sceneMode != null) && !sceneMode.equals("pro-mode")) {
            popup1.setPreferenceEnabled(CameraSettings.KEY_FOCUS_MODE, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_EXYNOS_EXPOSURE_COMPENSATION, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_EXYNOS_SATURATION, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_EXYNOS_SHARPNESS, false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_FLASH_MODE, false);
            mMeteringSwitcher.setVisibility(View.GONE);
            mUI.getCameraControls().removeFromViewList(mMeteringSwitcher);
        } else {
            mMeteringSwitcher.setVisibility(View.VISIBLE);
        }

        if ((faceDetection != null) && !ParametersWrapper.FACE_DETECTION_ON.equals(faceDetection)) {
            popup1.setPreferenceEnabled(CameraSettings.KEY_FACE_RECOGNITION, false);
        }

        pref = mPreferenceGroup.findPreference(CameraSettings.KEY_BOKEH_MODE);
        String bokeh = (pref != null) ? pref.getValue() : null;
        if ("1".equals(bokeh)) {
            buttonSetEnabled(mHdrSwitcher,false);
            buttonSetEnabled(mSceneModeSwitcher,false);
            buttonSetEnabled(mFilterModeSwitcher,false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_EXYNOS_SCENE_MODE,false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_EXYNOS_CAMERA_RT_HDR,false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_FLASH_MODE,false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_LONGSHOT,false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_EXYNOS_COLOR_EFFECT,false);
            popup1.setPreferenceEnabled(CameraSettings.KEY_PICTURE_SIZE,false);

            setPreference(CameraSettings.KEY_EXYNOS_SCENE_MODE,
                    mActivity.getString(R.string.pref_camera_scenemode_default));
            setPreference(CameraSettings.KEY_EXYNOS_CAMERA_RT_HDR,"off");
            setPreference(CameraSettings.KEY_FLASH_MODE, "off");
            setPreference(CameraSettings.KEY_LONGSHOT, "off");
            setPreference(CameraSettings.KEY_EXYNOS_COLOR_EFFECT,"none");
            ListPreference picSize =
                    mPreferenceGroup.findPreference(CameraSettings.KEY_PICTURE_SIZE);
            CharSequence maxSize = picSize.getEntryValues()[0];
            if (maxSize != null) {
                setPreference(CameraSettings.KEY_PICTURE_SIZE,maxSize.toString());
            }
        }

        if (mListener != null) {
            mListener.onSharedPreferenceChanged();
        }
    }

    private void updateFilterModeIcon(ListPreference scenePref, ListPreference hdrPref) {
        if (scenePref == null || hdrPref == null) return;
        if (notSame(scenePref, CameraSettings.KEY_EXYNOS_SCENE_MODE, Parameters.SCENE_MODE_AUTO)) {
            mFilterModeSwitcher.setVisibility(View.GONE);
            mUI.getCameraControls().removeFromViewList(mFilterModeSwitcher);
        } else {
            mFilterModeSwitcher.setVisibility(View.VISIBLE);
            if (notSame(hdrPref, CameraSettings.KEY_EXYNOS_CAMERA_RT_HDR, mSettingOff)) {
                buttonSetEnabled(mFilterModeSwitcher, false);
                changeFilterModeControlIcon("none");
            } else {
                buttonSetEnabled(mFilterModeSwitcher, true);
            }
        }
    }

    public void initSwitchItem(final String prefKey, View switcher) {
        final IconListPreference pref =
                (IconListPreference) mPreferenceGroup.findPreference(prefKey);
        if (pref == null)
            return;

        int[] iconIds = pref.getLargeIconIds();
        int resid = -1;
        int index = pref.findIndexOfValue(pref.getValue());
        if (!pref.getUseSingleIcon() && iconIds != null) {
            // Each entry has a corresponding icon.
            index = index % iconIds.length;
            resid = iconIds[index];
        } else {
            // The preference only has a single icon to represent it.
            resid = pref.getSingleIcon();
        }
        ((ImageView) switcher).setImageResource(resid);
        switcher.setVisibility(View.VISIBLE);
        mPreferences.add(pref);
        mPreferenceMap.put(pref, switcher);
        switcher.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                IconListPreference pref = (IconListPreference) mPreferenceGroup
                        .findPreference(prefKey);
                if (pref == null)
                    return;
                if (prefKey.equals(CameraSettings.KEY_CAMERA_ID)) {
                    // Hide the camera control while switching the camera.
                    // The camera control will be added back when
                    // onCameraPickerClicked is completed
                    mUI.hideUI();
                }
                int index = pref.findIndexOfValue(pref.getValue());
                CharSequence[] values = pref.getEntryValues();
                index = (index + 1) % values.length;
                pref.setValueIndex(index);
                int iconListLength = ((IconListPreference) pref).getLargeIconIds().length;
                ((ImageView) v).setImageResource(
                        ((IconListPreference) pref).getLargeIconIds()[index % iconListLength]);
                if (prefKey.equals(CameraSettings.KEY_CAMERA_ID))
                    mListener.onCameraPickerClicked(index);
                reloadPreference(pref);
                onSettingChanged(pref);
            }
        });
    }

    public void initBokehModeButton(View button) {
        button.setVisibility(View.INVISIBLE);
        final IconListPreference pref = (IconListPreference) mPreferenceGroup.findPreference(
                CameraSettings.KEY_BOKEH_MODE);
        if (pref == null) {
            button.setVisibility(View.GONE);
            return;
        }

        int[] iconIds = pref.getLargeIconIds();
        int resid = -1;
        int index = pref.findIndexOfValue(pref.getValue());
        if (!pref.getUseSingleIcon() && iconIds != null) {
            resid = iconIds[index];
        } else {
            resid = pref.getSingleIcon();
        }
        ImageView iv = (ImageView) button;
        iv.setImageResource(resid);

        button.setVisibility(View.VISIBLE);

        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ListPreference bokehPref =
                        mPreferenceGroup.findPreference(CameraSettings.KEY_BOKEH_MODE);
                String bokeh = (bokehPref != null) ? bokehPref.getValue() : null;
                if (bokeh != null) {
                    CharSequence[] values = bokehPref.getEntryValues();
                    int index = (bokehPref.getCurrentIndex() + 1) % values.length;
                    bokehPref.setValueIndex(index);
                    ((ImageView) v).setImageResource(
                            ((IconListPreference) pref).getLargeIconIds()[index]);
                    reloadPreference(pref);
                    initializePopup();
                    onSettingChanged(bokehPref);
                } else {

                }
            }
        });
    }

    public void initSceneModeButton(View button) {
        button.setVisibility(View.INVISIBLE);
        final IconListPreference pref = (IconListPreference) mPreferenceGroup
                .findPreference(CameraSettings.KEY_EXYNOS_SCENE_MODE);
        if (pref == null)
            return;
        updateSceneModeIcon(pref);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                addSceneMode();
                ViewGroup menuLayout = mUI.getPreviewMenuLayout();
                if (menuLayout != null) {
                    View view = menuLayout.getChildAt(0);
                    mUI.adjustOrientation();
                    animateSlideIn(view, previewMenuSize, false);
                }
            }
        });
    }

    public void addModeBack() {
        if (mSceneStatus == MODE_SCENE) {
            addSceneMode();
        }
        if (mSceneStatus == MODE_FILTER) {
            addFilterMode();
        }
    }

    public void addSceneMode() {
        final IconListPreference pref = (IconListPreference) mPreferenceGroup
                .findPreference(CameraSettings.KEY_EXYNOS_SCENE_MODE);
        if (pref == null)
            return;

        int rotation = CameraUtil.getDisplayRotation(mActivity);
        boolean mIsDefaultToPortrait = CameraUtil.isDefaultToPortrait(mActivity);
        if (!mIsDefaultToPortrait) {
            rotation = (rotation + 90) % 360;
        }
        WindowManager wm = (WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        CharSequence[] entries = pref.getEntries();
        CharSequence[] entryValues = pref.getEntryValues();

        int[] thumbnails = pref.getThumbnailIds();

        Resources r = mActivity.getResources();
        int height = (int) (r.getDimension(R.dimen.scene_mode_height) + 2
                * r.getDimension(R.dimen.scene_mode_padding) + 1);
        int width = (int) (r.getDimension(R.dimen.scene_mode_width) + 2
                * r.getDimension(R.dimen.scene_mode_padding) + 1);

        int gridRes = 0;
        boolean portrait = (rotation == 0) || (rotation == 180);
        int size = height;
        if (portrait) {
            gridRes = R.layout.vertical_grid;
            size = width;
        } else {
            gridRes = R.layout.horiz_grid;
        }
        previewMenuSize = size;
        mUI.hideUI();
        mPreviewMenuStatus = PREVIEW_MENU_ON;
        mSceneStatus = MODE_SCENE;

        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        FrameLayout basic = (FrameLayout) inflater.inflate(
                gridRes, null, false);

        mUI.dismissSceneModeMenu();
        LinearLayout previewMenuLayout = new LinearLayout(mActivity);
        mUI.setPreviewMenuLayout(previewMenuLayout);
        ViewGroup.LayoutParams params = null;
        if (portrait) {
            params = new ViewGroup.LayoutParams(size, LayoutParams.MATCH_PARENT);
            previewMenuLayout.setLayoutParams(params);
            ((ViewGroup) mUI.getRootView()).addView(previewMenuLayout);
        } else {
            params = new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, size);
            previewMenuLayout.setLayoutParams(params);
            ((ViewGroup) mUI.getRootView()).addView(previewMenuLayout);
            previewMenuLayout.setY(display.getHeight() - size);
        }
        basic.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        LinearLayout layout = (LinearLayout) basic.findViewById(R.id.layout);

        final View[] views = new View[entries.length];
        int init = pref.getCurrentIndex();
        for (int i = 0; i < entries.length; i++) {
            RotateLayout layout2 = (RotateLayout) inflater.inflate(
                    R.layout.scene_mode_view, null, false);

            ImageView imageView = (ImageView) layout2.findViewById(R.id.image);
            TextView label = (TextView) layout2.findViewById(R.id.label);
            final int j = i;

            layout2.setOnTouchListener(new View.OnTouchListener() {
                private long startTime;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        startTime = System.currentTimeMillis();
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (System.currentTimeMillis() - startTime < CLICK_THRESHOLD) {
                            pref.setValueIndex(j);
                            onSettingChanged(pref);
                            updateSceneModeIcon(pref);
                            for (View v1 : views) {
                                v1.setActivated(v1 == v);
                            }
                        }

                    }
                    return true;
                }
            });

            views[j] = layout2;
            layout2.setActivated(i == init);
            imageView.setImageResource(thumbnails[i]);
            label.setText(entries[i]);
            layout.addView(layout2);
        }
        previewMenuLayout.addView(basic);
        mPreviewMenu = basic;
    }

    public void updateSceneModeIcon(IconListPreference pref) {
        int[] thumbnails = pref.getThumbnailIds();
        int ind = pref.getCurrentIndex();
        if (ind == -1)
            ind = 0;
        ((ImageView) mSceneModeSwitcher).setImageResource(thumbnails[ind]);
    }

    public void initFilterModeButton(View button) {
        button.setVisibility(View.INVISIBLE);
        final IconListPreference pref = (IconListPreference) mPreferenceGroup
                .findPreference(CameraSettings.KEY_EXYNOS_COLOR_EFFECT);
        if (pref == null || pref.getValue() == null)
            return;
        changeFilterModeControlIcon(pref.getValue());
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                addFilterMode();
                ViewGroup menuLayout = mUI.getPreviewMenuLayout();
                if (menuLayout != null) {
                    View view = mUI.getPreviewMenuLayout().getChildAt(0);
                    mUI.adjustOrientation();
                    animateSlideIn(view, previewMenuSize, false);
                }
            }
        });
    }

    public void addFilterMode() {
        final IconListPreference pref = (IconListPreference) mPreferenceGroup
                .findPreference(CameraSettings.KEY_EXYNOS_COLOR_EFFECT);
        if (pref == null)
            return;

        int rotation = CameraUtil.getDisplayRotation(mActivity);
        boolean mIsDefaultToPortrait = CameraUtil.isDefaultToPortrait(mActivity);
        if (!mIsDefaultToPortrait) {
            rotation = (rotation + 90) % 360;
        }
        WindowManager wm = (WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        CharSequence[] entries = pref.getEntries();

        Resources r = mActivity.getResources();
        int height = (int) (r.getDimension(R.dimen.filter_mode_height) + 2
                * r.getDimension(R.dimen.filter_mode_padding) + 1);
        int width = (int) (r.getDimension(R.dimen.filter_mode_width) + 2
                * r.getDimension(R.dimen.filter_mode_padding) + 1);

        int gridRes = 0;
        boolean portrait = (rotation == 0) || (rotation == 180);
        int size = height;
        if (portrait) {
            gridRes = R.layout.vertical_grid;
            size = width;
        } else {
            gridRes = R.layout.horiz_grid;
        }
        previewMenuSize = size;
        mUI.hideUI();
        mPreviewMenuStatus = PREVIEW_MENU_ON;
        mSceneStatus = MODE_FILTER;

        int[] thumbnails = pref.getThumbnailIds();

        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        FrameLayout basic = (FrameLayout) inflater.inflate(
                gridRes, null, false);

        mUI.dismissSceneModeMenu();
        LinearLayout previewMenuLayout = new LinearLayout(mActivity);
        mUI.setPreviewMenuLayout(previewMenuLayout);
        ViewGroup.LayoutParams params = null;
        if (portrait) {
            params = new ViewGroup.LayoutParams(size, LayoutParams.MATCH_PARENT);
            previewMenuLayout.setLayoutParams(params);
            ((ViewGroup) mUI.getRootView()).addView(previewMenuLayout);
        } else {
            params = new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, size);
            previewMenuLayout.setLayoutParams(params);
            ((ViewGroup) mUI.getRootView()).addView(previewMenuLayout);
            previewMenuLayout.setY(display.getHeight() - size);
        }
        basic.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        LinearLayout layout = (LinearLayout) basic.findViewById(R.id.layout);
        final View[] views = new View[entries.length];
        int init = pref.getCurrentIndex();
        for (int i = 0; i < entries.length; i++) {
            RotateLayout layout2 = (RotateLayout) inflater.inflate(
                    R.layout.filter_mode_view, null, false);
            ImageView imageView = (ImageView) layout2.findViewById(R.id.image);
            final int j = i;

            layout2.setOnTouchListener(new View.OnTouchListener() {
                private long startTime;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        startTime = System.currentTimeMillis();
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (System.currentTimeMillis() - startTime < CLICK_THRESHOLD) {
                            pref.setValueIndex(j);
                            changeFilterModeControlIcon(pref.getValue());
                            onSettingChanged(pref);
                            for (View v1 : views) {
                                v1.setActivated(v1 == v);
                            }
                        }
                    }
                    return true;
                }
            });

            views[j] = layout2;
            layout2.setActivated(i == init);
            TextView label = (TextView) layout2.findViewById(R.id.label);
            imageView.setImageResource(thumbnails[i]);
            label.setText(entries[i]);
            layout.addView(layout2);
        }
        previewMenuLayout.addView(basic);
        mPreviewMenu = basic;
    }

    private void changeFilterModeControlIcon(String value) {
        if(!value.equals("")) {
            if(value.equalsIgnoreCase("none")) {
                value = "Off";
            } else {
                value = "On";
            }
            final IconListPreference pref = (IconListPreference) mPreferenceGroup
                    .findPreference(CameraSettings.KEY_FILTER_MODE);
            pref.setValue(value);
            int index = pref.getCurrentIndex();
            ImageView iv = (ImageView) mFilterModeSwitcher;
            iv.setImageResource(((IconListPreference) pref).getLargeIconIds()[index]);
        }
    }

    public void openFirstLevel() {
        if (isMenuBeingShown() || CameraControls.isAnimating()) {
            return;
        }
        if (mListMenu == null || mPopupStatus != POPUP_FIRST_LEVEL) {
            initializePopup();
            mPopupStatus = POPUP_FIRST_LEVEL;
        }
        mUI.showPopup(mListMenu, 1, true);
    }

    public void popupDismissed(boolean dismissAll) {
        if (!dismissAll && mPopupStatus == POPUP_SECOND_LEVEL) {
            initializePopup();
            mPopupStatus = POPUP_FIRST_LEVEL;
            mUI.showPopup(mListMenu, 1, false);
            if (mListMenu != null)
                mListMenu = null;

        } else {
            initializePopup();
        }

    }

    @Override
    // Hit when an item in the first-level popup gets selected, then bring up
    // the second-level popup
    public void onPreferenceClicked(ListPreference pref) {
        onPreferenceClicked(pref, 0);
    }

    public void onPreferenceClicked(ListPreference pref, int y) {
        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        ListSubMenu basic = (ListSubMenu) inflater.inflate(
                R.layout.list_sub_menu, null, false);
        basic.initialize(pref, y);
        basic.setSettingChangedListener(this);
        basic.setAlpha(0f);
        mListSubMenu = basic;
        mUI.removeLevel2();
        if (mPopupStatus == POPUP_SECOND_LEVEL) {
            mUI.showPopup(mListSubMenu, 2, false);
        } else {
            mUI.showPopup(mListSubMenu, 2, true);
        }
        mPopupStatus = POPUP_SECOND_LEVEL;

        // Developer menu
        if (pref.getKey().equals(CameraSettings.KEY_MAX_BRIGHTNESS)) {
            mPrivateCounter++;
            if (mPrivateCounter >= DEVELOPER_MENU_TOUCH_COUNT) {
                SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(mActivity);
                if (!mActivity.isDeveloperMenuEnabled()) {
                    mActivity.enableDeveloperMenu();
                    prefs.edit().putBoolean(CameraSettings.KEY_DEVELOPER_MENU, true).apply();
                    closeAllView();
                    RotateTextToast.makeText(mActivity,
                            R.string.developer_menu_enabled, Toast.LENGTH_SHORT).show();
                } else {
                    mActivity.disableDeveloperMenu();
                    prefs.edit().putBoolean(CameraSettings.KEY_DEVELOPER_MENU, false).apply();
                    closeAllView();
                    RotateTextToast.makeText(mActivity,
                            R.string.developer_menu_disabled, Toast.LENGTH_SHORT).show();
                }
                mPrivateCounter = 0;
            }
        } else {
            mPrivateCounter = 0;
        }
    }

    public void onListMenuTouched() {
        mUI.removeLevel2();
        mPopupStatus = POPUP_FIRST_LEVEL;
    }

    public void removeAllView() {
        if (mUI != null)
            mUI.removeLevel2();

        if (mListMenu != null) {
            mUI.dismissLevel1();
            mPopupStatus = POPUP_NONE;
        }
        closeSceneMode();
        mPreviewMenuStatus = PREVIEW_MENU_NONE;
    }

    public void closeAllView() {
        if (mUI != null)
            mUI.removeLevel2();

        if (mListMenu != null) {
            animateSlideOut(mListMenu, 1);
        }
        animateSlideOutPreviewMenu();
    }

    public void closeView() {
        if (mUI != null)
            mUI.removeLevel2();

        if (mListMenu != null && mPopupStatus != POPUP_NONE)
            animateSlideOut(mListMenu, 1);
    }

    // Return true if the preference has the specified key but not the value.
    private static boolean notSame(ListPreference pref, String key, String value) {
        return (key.equals(pref.getKey()) && !value.equals(pref.getValue()));
    }

    // Return true if the preference has the specified key and the value.
    private static boolean same(ListPreference pref, String key, String value) {
        return (key.equals(pref.getKey()) && value.equals(pref.getValue()));
    }

    public void setPreference(String key, String value) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        if (pref != null && !value.equals(pref.getValue())) {
            pref.setValue(value);
            reloadPreferences();
        }
    }

    @Override
    public void onSettingChanged(ListPreference pref) {

        ListPreference scenePref = mPreferenceGroup.findPreference(CameraSettings.KEY_EXYNOS_SCENE_MODE);
        ListPreference hdrPref = mPreferenceGroup.findPreference(CameraSettings.KEY_EXYNOS_CAMERA_RT_HDR);
        ListPreference meteringPref = mPreferenceGroup.findPreference(CameraSettings.KEY_EXYNOS_METERING_MODE);
        IconListPreference colorpref = (IconListPreference) mPreferenceGroup
                .findPreference(CameraSettings.KEY_EXYNOS_COLOR_EFFECT);


        if (notSame(pref, CameraSettings.KEY_EXYNOS_SCENE_MODE, "auto")) {
            setPreference(CameraSettings.KEY_EXYNOS_COLOR_EFFECT,
                    mActivity.getString(R.string.pref_camera_coloreffect_default));
        }


        if (notSame(hdrPref, CameraSettings.KEY_EXYNOS_CAMERA_RT_HDR, "off")) {
            if (colorpref != null && notSame(colorpref, CameraSettings.KEY_EXYNOS_COLOR_EFFECT,
                    mActivity.getString(R.string.pref_camera_coloreffect_default))) {
                setPreference(CameraSettings.KEY_EXYNOS_COLOR_EFFECT,
                        mActivity.getString(R.string.pref_camera_coloreffect_default));
            }
        }

        updateFilterModeIcon(scenePref, hdrPref);

        if ((same(scenePref, CameraSettings.KEY_EXYNOS_SCENE_MODE, "auto"))
            || (same(scenePref, CameraSettings.KEY_EXYNOS_SCENE_MODE, "pro-mode"))) {
            mHdrSwitcher.setVisibility(View.VISIBLE);
        } else {
            if (hdrPref != null && notSame(hdrPref, CameraSettings.KEY_EXYNOS_CAMERA_RT_HDR,
                    mActivity.getString(R.string.pref_camera_exy_rthdr_default))) {
                setPreference(CameraSettings.KEY_EXYNOS_CAMERA_RT_HDR,
                        mActivity.getString(R.string.pref_camera_exy_rthdr_default));
            }
            mHdrSwitcher.setVisibility(View.GONE);
            mUI.getCameraControls().removeFromViewList(mHdrSwitcher);
        }

        if (same(scenePref, CameraSettings.KEY_EXYNOS_SCENE_MODE, "pro-mode")) {
            mMeteringSwitcher.setVisibility(View.VISIBLE);
        } else {
            if (meteringPref != null && notSame(meteringPref, CameraSettings.KEY_EXYNOS_METERING_MODE,
                    mActivity.getString(R.string.pref_camera_exy_metering_mode_default))) {
                setPreference(CameraSettings.KEY_EXYNOS_METERING_MODE,
                        mActivity.getString(R.string.pref_camera_exy_metering_mode_default));
            }
            mMeteringSwitcher.setVisibility(View.GONE);
            mUI.getCameraControls().removeFromViewList(mMeteringSwitcher);
        }

        if (same(pref, CameraSettings.KEY_RECORD_LOCATION, "on")) {
            mActivity.requestLocationPermission();
        }

        if (same(pref, CameraSettings.KEY_BOKEH_MODE, "1")) {
            ListPreference scene =
                    mPreferenceGroup.findPreference(CameraSettings.KEY_EXYNOS_SCENE_MODE);
            updateSceneModeIcon((IconListPreference)scene);
            changeFilterModeControlIcon("none");
            buttonSetEnabled(mHdrSwitcher,false);
            buttonSetEnabled(mSceneModeSwitcher,false);
            buttonSetEnabled(mFilterModeSwitcher,false);
        }
        super.onSettingChanged(pref);
    }

    public int getOrientation() {
        return mUI == null ? 0 : mUI.getOrientation();
    }

    public void hideTopMenu(boolean hide) {
        if (hide) {
            mSceneModeSwitcher.setVisibility(View.GONE);
            mFilterModeSwitcher.setVisibility(View.GONE);
            mFrontBackSwitcher.setVisibility(View.GONE);
        } else {
            mSceneModeSwitcher.setVisibility(View.VISIBLE);
            mFilterModeSwitcher.setVisibility(View.VISIBLE);
            mFrontBackSwitcher.setVisibility(View.VISIBLE);
        }
    }

    public void hideCameraControls(boolean hide) {
        final int status = (hide) ? View.INVISIBLE : View.VISIBLE;
        mSettingMenu.setVisibility(status);
        mFrontBackSwitcher.setVisibility(status);
        mHdrSwitcher.setVisibility(status);
        mSceneModeSwitcher.setVisibility(status);
        mFilterModeSwitcher.setVisibility(status);
        if(status == View.INVISIBLE) {
            if(mCameraSwitcher.getVisibility() == View.VISIBLE) {
                mWasVisibleSet.add(mCameraSwitcher);
            }
            mCameraSwitcher.setVisibility(status);
        } else {
            if(mWasVisibleSet.contains(mCameraSwitcher)) {
                mCameraSwitcher.setVisibility(status);
                mWasVisibleSet.remove(mCameraSwitcher);
            }
        }
        mPreviewThumbnail.setVisibility(status);
    }
}
