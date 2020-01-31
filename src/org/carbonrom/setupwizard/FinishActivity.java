/*
 * Copyright (C) 2016 The CyanogenMod Project
 * Copyright (C) 2017-2019 The LineageOS Project
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

package org.carbonrom.setupwizard;

import static org.carbonrom.setupwizard.SetupWizardApp.DISABLE_NAV_KEYS;
import static org.carbonrom.setupwizard.SetupWizardApp.KEY_BUTTON_BACKLIGHT;
import static org.carbonrom.setupwizard.SetupWizardApp.KEY_SEND_METRICS;
import static org.carbonrom.setupwizard.SetupWizardApp.KEY_DARK_THEME;
import static org.carbonrom.setupwizard.SetupWizardApp.LOGV;

import android.animation.Animator;
import android.app.Activity;
import android.app.WallpaperManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.ImageView;

import com.google.android.setupcompat.util.WizardManagerHelper;

import org.carbonrom.setupwizard.util.EnableAccessibilityController;

public class FinishActivity extends BaseSetupWizardActivity {

    public static final String TAG = FinishActivity.class.getSimpleName();

    private EnableAccessibilityController mEnableAccessibilityController;

    private SetupWizardApp mSetupWizardApp;

    private final Handler mHandler = new Handler();

    private volatile boolean mIsFinishing = false;

    FrameLayout mContent;
    final static int SOLID_BGCOLOR = 0xFF1F1F1F;
    final static int CLEAR_BGCOLOR = 0x00000000;

    static final String STATS_COLLECTION = "stats_collection";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (LOGV) {
            logActivityState("onCreate savedInstanceState=" + savedInstanceState);
        }
        mSetupWizardApp = (SetupWizardApp) getApplication();
        mEnableAccessibilityController =
                EnableAccessibilityController.getInstance(getApplicationContext());
        setNextText(R.string.start);
        animateLogo();
    }

    private void animateLogo() {
    	DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        RelativeLayout root = findViewById(R.id.root);
        mContent = findViewById(R.id.animContainer);
        mContent.setBackgroundColor(CLEAR_BGCOLOR);

        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;

        final ImageView bglogo = new ImageView(this);
        bglogo.setImageResource(R.drawable.carbon_bg_logo);
        bglogo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        bglogo.setAlpha(0f);

        final ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.carbon_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setAlpha(0f);

        final int p = (int)(4 * metrics.density);

        mContent.addView(bglogo, lp);
        mContent.addView(logo, lp);

      	android.util.Log.i("CarbonLogoActivity", "Starting Animation");
        bglogo.setVisibility(View.VISIBLE);
        bglogo.setScaleX(0.5f);
        bglogo.setScaleY(0.5f);
        bglogo.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(600).setStartDelay(500)
            .start();
        logo.setVisibility(View.VISIBLE);
        logo.setScaleX(0.5f);
        logo.setScaleY(0.5f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(1000).setStartDelay(800)
            .setInterpolator(new AnticipateOvershootInterpolator())
            .start();
    }

    @Override
    protected int getTransition() {
        return TRANSITION_ID_SLIDE;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.finish_activity;
    }

    @Override
    protected int getTitleResId() {
        return R.string.setup_complete;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.translucent_enter, R.anim.translucent_exit);
    }

    @Override
    public void onNavigateNext() {
        applyForwardTransition(TRANSITION_ID_NONE);
        startFinishSequence();
    }

    private void finishSetup() {
        if (!mIsFinishing) {
            mIsFinishing = true;
        }
    }

    private void startFinishSequence() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        hideBackButton();
        hideNextButton();
        finishSetup();
        completeSetup();
    }

    private void handleDarkTheme(SetupWizardApp setupWizardApp) {
        Bundle mData = setupWizardApp.getSettingsBundle();
        if (mData != null && mData.containsKey(KEY_DARK_THEME)) {
            UiModeManager mUiModeManager = this.getSystemService(UiModeManager.class);
            mUiModeManager.setNightMode(mData.getBoolean(KEY_DARK_THEME)
                    ? UiModeManager.MODE_NIGHT_YES
                    : UiModeManager.MODE_NIGHT_NO);
        }
    }

    private void handleSendMetrics(SetupWizardApp setupWizardApp) {
        Bundle mData = setupWizardApp.getSettingsBundle();
        if (mData != null && mData.containsKey(KEY_DARK_THEME)) {
            Secure.putInt(this.getContentResolver(), STATS_COLLECTION,
                    mData.getBoolean(KEY_SEND_METRICS) ? 1 : 0);
        }
    }



    private void completeSetup() {
        if (mEnableAccessibilityController != null) {
            mEnableAccessibilityController.onDestroy();
        }
        handleDarkTheme(mSetupWizardApp);
        handleSendMetrics(mSetupWizardApp);
        final WallpaperManager wallpaperManager =
                WallpaperManager.getInstance(mSetupWizardApp);
        wallpaperManager.forgetLoadedWallpaper();
        finishAllAppTasks();
        Intent intent = WizardManagerHelper.getNextIntent(getIntent(),
                Activity.RESULT_OK);
        startActivityForResult(intent, NEXT_REQUEST);
    }
}
