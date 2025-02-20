/*
 * Copyright (C) 2016 The CyanogenMod Project
 * Copyright (C) 2017 The LineageOS Project
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

import static com.google.android.setupcompat.util.ResultCodes.RESULT_ACTIVITY_NOT_FOUND;
import static com.google.android.setupcompat.util.ResultCodes.RESULT_RETRY;
import static com.google.android.setupcompat.util.ResultCodes.RESULT_SKIP;

import static org.carbonrom.setupwizard.SetupWizardApp.ACTION_EMERGENCY_DIAL;
import static org.carbonrom.setupwizard.SetupWizardApp.ACTION_NEXT;
import static org.carbonrom.setupwizard.SetupWizardApp.EXTRA_ACTION_ID;
import static org.carbonrom.setupwizard.SetupWizardApp.EXTRA_FIRST_RUN;
import static org.carbonrom.setupwizard.SetupWizardApp.EXTRA_HAS_MULTIPLE_USERS;
import static org.carbonrom.setupwizard.SetupWizardApp.EXTRA_RESULT_CODE;
import static org.carbonrom.setupwizard.SetupWizardApp.EXTRA_SCRIPT_URI;
import static org.carbonrom.setupwizard.SetupWizardApp.EXTRA_USE_IMMERSIVE;
import static org.carbonrom.setupwizard.SetupWizardApp.LOGV;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.setupdesign.view.NavigationBar;
import com.google.android.setupdesign.view.NavigationBar.NavigationBarListener;
import com.google.android.setupcompat.util.SystemBarHelper;
import com.google.android.setupcompat.util.WizardManagerHelper;

import org.carbonrom.setupwizard.util.SetupWizardUtils;

import java.util.List;

public abstract class BaseSetupWizardActivity extends Activity implements NavigationBarListener,
        ViewTreeObserver.OnPreDrawListener {

    public static final String TAG = BaseSetupWizardActivity.class.getSimpleName();

    protected static final int TRANSITION_ID_NONE = -1;
    protected static final int TRANSITION_ID_DEFAULT = 1;
    protected static final int TRANSITION_ID_SLIDE = 2;
    protected static final int TRANSITION_ID_FADE = 3;

    protected static final int NEXT_REQUEST = 10000;
    protected static final int EMERGENCY_DIAL_ACTIVITY_REQUEST = 10038;
    protected static final int WIFI_ACTIVITY_REQUEST = 10004;
    protected static final int BLUETOOTH_ACTIVITY_REQUEST = 10100;
    protected static final int FINGERPRINT_ACTIVITY_REQUEST = 10101;
    protected static final int SCREENLOCK_ACTIVITY_REQUEST = 10102;

    private static final int IMMERSIVE_FLAGS =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    private int mSystemUiFlags = IMMERSIVE_FLAGS | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;

    private NavigationBar mNavigationBar;

    protected boolean mIsActivityVisible = false;
    protected boolean mIsExiting = false;
    private boolean mIsFirstRun = true;
    protected boolean mIsGoingBack = false;
    private boolean mIsPrimaryUser;
    private int mResultCode = 0;
    private Intent mResultData;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (LOGV) {
            logActivityState("onCreate savedInstanceState=" + savedInstanceState);
        }
        super.onCreate(savedInstanceState);
        mIsPrimaryUser = UserHandle.myUserId() == 0;
        initLayout();
        mNavigationBar = getNavigationBar();
        if (mNavigationBar != null) {
            mNavigationBar.setNavigationBarListener(this);
            mNavigationBar.addOnLayoutChangeListener((View view,
                    int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) -> {
                view.requestApplyInsets();
            });
            mNavigationBar.setSystemUiVisibility(mSystemUiFlags);
            // Set the UI flags before draw because the visibility might change in unexpected /
            // undetectable times, like transitioning from a finishing activity that had a keyboard
            ViewTreeObserver viewTreeObserver = mNavigationBar.getViewTreeObserver();
            viewTreeObserver.addOnPreDrawListener(this);
        }
    }

    @Override
    protected void onStart() {
        if (LOGV) {
            logActivityState("onStart");
        }
        super.onStart();
        exitIfSetupComplete();
    }

    @Override
    protected void onRestart() {
        if (LOGV) {
            logActivityState("onRestart");
        }
        super.onRestart();
    }

    @Override
    protected void onResume() {
        if (LOGV) {
            logActivityState("onResume");
        }
        super.onResume();
        if (mIsGoingBack) {
            if (!mIsExiting) {
                applyBackwardTransition(getTransition());
            }
        } else if (!mIsExiting) {
            applyForwardTransition(getTransition());
        }
    }

    @Override
    protected void onPause() {
        if (LOGV) {
            logActivityState("onPause");
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (LOGV) {
            logActivityState("onStop");
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (LOGV) {
            logActivityState("onDestroy");
        }
        super.onDestroy();
    }

    @Override
    public void onAttachedToWindow() {
        if (LOGV) {
            logActivityState("onAttachedToWindow");
        }
        mIsActivityVisible = true;
        super.onAttachedToWindow();
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        if (LOGV) {
            Log.v(TAG, "onRestoreInstanceState(" + savedInstanceState + ")");
        }
        super.onRestoreInstanceState(savedInstanceState);
        int currentId = savedInstanceState.getInt("currentFocus", -1);
        if (currentId != -1) {
            View view = findViewById(currentId);
            if (view != null) {
                view.requestFocus();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        View current = getCurrentFocus();
        outState.putInt("currentFocus", current != null ? current.getId() : -1);
        if (LOGV) {
            Log.v(TAG, "onSaveInstanceState(" + outState + ")");
        }
    }

    @Override
    public boolean onPreDraw() {
        // View.setSystemUiVisibility checks if the visibility changes before applying them
        // so the performance impact is contained
        mNavigationBar.setSystemUiVisibility(mSystemUiFlags);
        return true;
    }

    /**
     * @return The navigation bar instance in the layout, or null if the layout does not have a
     *     navigation bar.
     */
    public NavigationBar getNavigationBar() {
        final View view = findViewById(R.id.navigation_bar);
        return view instanceof NavigationBar ? (NavigationBar) view : null;
    }

    /**
     * Sets whether system navigation bar should be hidden.
     * @param useImmersiveMode True to activate immersive mode and hide the system navigation bar
     */
    public void setUseImmersiveMode(boolean useImmersiveMode) {
        // By default, enable layoutHideNavigation if immersive mode is used
        setUseImmersiveMode(useImmersiveMode, useImmersiveMode);
    }

    public void setUseImmersiveMode(boolean useImmersiveMode, boolean layoutHideNavigation) {
        if (useImmersiveMode) {
            mSystemUiFlags |= IMMERSIVE_FLAGS;
            if (layoutHideNavigation) {
                mSystemUiFlags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            }
        } else {
            mSystemUiFlags &= ~(IMMERSIVE_FLAGS | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        if (mNavigationBar != null) {
            mNavigationBar.setSystemUiVisibility(mSystemUiFlags);
        }
    }

    protected void setBackDrawable(Drawable drawable) {
        if (mNavigationBar != null) {
            mNavigationBar.getBackButton().setCompoundDrawables(drawable, null, null, null);
        }
    }

    protected void setNextDrawable(Drawable drawable) {
        if (mNavigationBar != null) {
            mNavigationBar.getBackButton().setCompoundDrawables(null, null, drawable, null);
        }
    }

    public void setBackAllowed(boolean allowed) {
        SystemBarHelper.setBackButtonVisible(getWindow(), allowed);
        if (mNavigationBar != null) {
            Button backButton = mNavigationBar.getBackButton();
            backButton.setEnabled(allowed);
        }
    }

    protected boolean isBackAllowed() {
        if (mNavigationBar != null) {
            mNavigationBar.getBackButton().isEnabled();
        }
        return false;
    }

    public void setNextAllowed(boolean allowed) {
        if (mNavigationBar != null) {
            mNavigationBar.getNextButton().setEnabled(allowed);
        }
    }

    protected boolean isNextAllowed() {
        if (mNavigationBar != null) {
            mNavigationBar.getNextButton().isEnabled();
        }
        return false;
    }

    protected void onNextPressed() {
        nextAction(NEXT_REQUEST);
    }

    protected void setNextText(int resId) {
        if (mNavigationBar != null) {
            mNavigationBar.getNextButton().setText(resId);
        }
    }

    protected void setBackText(int resId) {
        if (mNavigationBar != null) {
            mNavigationBar.getBackButton().setText(resId);
        }
    }

    protected void hideNextButton() {
        if (mNavigationBar != null) {
            Animation fadeOut = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);
            final Button next = mNavigationBar.getNextButton();
            next.startAnimation(fadeOut);
            next.setVisibility(View.INVISIBLE);
        }
    }

    protected Intent getResultData() {
        return null;
    }

    @Override
    public void onBackPressed() {
        if (LOGV) {
            Log.v(TAG, "onBackPressed()");
        }
        setResultCode(RESULT_CANCELED, getResultData());
        super.onBackPressed();
    }

    public void onNavigateBack() {
        onBackPressed();
    }

    public void onNavigateNext() {
        onNextPressed();
    }

    protected void startEmergencyDialer() {
        try {
            startFirstRunActivityForResult(new Intent(ACTION_EMERGENCY_DIAL),
                    EMERGENCY_DIAL_ACTIVITY_REQUEST);
            applyForwardTransition(TRANSITION_ID_DEFAULT);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Can't find the emergency dialer: com.android.phone.EmergencyDialer.DIAL");
        }
    }

    protected void onSetupStart() {
        SetupWizardUtils.disableCaptivePortalDetection(getApplicationContext());
        SetupWizardUtils.disableStatusBar(getApplicationContext());
        SystemBarHelper.hideSystemBars(getWindow());
        tryEnablingWifi();
    }


    protected void exitIfSetupComplete() {
        if (WizardManagerHelper.isUserSetupComplete(this)) {
            Log.i(TAG, "Starting activity with USER_SETUP_COMPLETE=true");
            startSetupWizardExitActivity();
            setResult(RESULT_CANCELED, null);
            finishAllAppTasks();
        }
    }

    protected void finishAllAppTasks() {
        List<ActivityManager.AppTask> appTasks =
                getSystemService(ActivityManager.class).getAppTasks();

        for (ActivityManager.AppTask task : appTasks) {
            if (LOGV) {
                Log.v(TAG, "Finishing task=" + task.toString());
            }
            task.finishAndRemoveTask();
        }
        finish();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (LOGV) {
            Log.v(TAG, "onActivityResult(" + getRequestName(requestCode) + ", " +
                    getResultName(requestCode, resultCode) + ")");
        }
        mIsGoingBack = true;
        if (requestCode != NEXT_REQUEST || resultCode != RESULT_CANCELED) {
            if (requestCode == EMERGENCY_DIAL_ACTIVITY_REQUEST) {
                applyBackwardTransition(TRANSITION_ID_DEFAULT);
                return;
            }
            if (resultCode == RESULT_CANCELED) {
                finish();
            } else {
                nextAction(resultCode);
            }
        }
    }

    public void finish() {
        if (LOGV) {
            Log.v(TAG, "finish");
        }
        super.finish();
        if (isResumed() && mResultCode == RESULT_CANCELED) {
            applyBackwardTransition(getTransition());
        }
        mIsExiting = true;
    }

    protected void finishAction() {
        finishAction(RESULT_CANCELED);
    }

    protected void finishAction(int resultCode) {
        finishAction(resultCode, null);
    }

    protected void finishAction(int resultCode, Intent data) {
        if (resultCode != 0) {
            nextAction(resultCode, data);
        }
        finish();
    }

    protected void setResultCode(int resultCode) {
        setResultCode(resultCode, getResultData());
    }

    protected void setResultCode(int resultCode, Intent data) {
        if (LOGV) {
            Log.v(TAG, "setResultCode result=" + getResultName(0, resultCode) + " data=" + data);
        }
        mResultCode = resultCode;
        mResultData = data;
        setResult(resultCode, data);
    }

    protected void nextAction(int resultCode) {
        nextAction(resultCode, null);
    }

    protected void nextAction(int resultCode, Intent data) {
        if (LOGV) {
            Log.v(TAG, "nextAction resultCode=" + resultCode +
                    " data=" + data + " this=" + this);
        }
        if (resultCode == 0) {
            throw new IllegalArgumentException("Cannot call nextAction with RESULT_CANCELED");
        }
        setResultCode(resultCode, data);
        sendActionResults();
    }

    public void startActivity(Intent intent) {
        super.startActivity(intent);
        if (isResumed() && mIsActivityVisible) {
            applyForwardTransition(getTransition());
        }
        mIsExiting = true;
    }

    public void startActivityForResult(Intent intent, int requestCode) {
        super.startActivityForResult(intent, requestCode);
        if (isResumed() && mIsActivityVisible) {
            applyForwardTransition(getTransition());
        }
        mIsExiting = true;
    }

    protected void sendActionResults() {
        if (LOGV) {
            Log.v(TAG, "sendActionResults resultCode=" + mResultCode + " data=" + mResultData);
        }
        Intent intent = new Intent(ACTION_NEXT);
        intent.putExtra(EXTRA_SCRIPT_URI, getIntent().getStringExtra(EXTRA_SCRIPT_URI));
        intent.putExtra(EXTRA_ACTION_ID, getIntent().getStringExtra(EXTRA_ACTION_ID));
        intent.putExtra(EXTRA_RESULT_CODE, mResultCode);
        if (!(mResultData == null || mResultData.getExtras() == null)) {
            intent.putExtras(mResultData.getExtras());
        }
        startActivityForResult(intent, NEXT_REQUEST);
    }

    protected void applyForwardTransition(int transitionId) {
        if (transitionId == TRANSITION_ID_SLIDE) {
            overridePendingTransition(R.anim.sud_slide_next_in, R.anim.sud_slide_next_out);
        } else if (transitionId == TRANSITION_ID_FADE) {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } else if (transitionId == TRANSITION_ID_DEFAULT) {
            TypedArray typedArray = obtainStyledAttributes(android.R.style.Animation_Activity,
                    new int[]{android.R.attr.activityOpenEnterAnimation,
                            android.R.attr.activityOpenExitAnimation});
            overridePendingTransition(typedArray.getResourceId(0, 0),
                    typedArray.getResourceId(1, 0));
            typedArray.recycle();
        } else if (transitionId == TRANSITION_ID_NONE) {
            overridePendingTransition(0, 0);
        }
    }

    protected void applyBackwardTransition(int transitionId) {
        if (transitionId == TRANSITION_ID_SLIDE) {
            overridePendingTransition(R.anim.sud_slide_back_in, R.anim.sud_slide_back_out);
        } else if (transitionId == TRANSITION_ID_FADE) {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } else if (transitionId == TRANSITION_ID_DEFAULT) {
            TypedArray typedArray = obtainStyledAttributes(android.R.style.Animation_Activity,
                    new int[]{android.R.attr.activityCloseEnterAnimation,
                    android.R.attr.activityCloseExitAnimation});
            overridePendingTransition(typedArray.getResourceId(0, 0),
                    typedArray.getResourceId(1, 0));
            typedArray.recycle();
        } else if (transitionId == TRANSITION_ID_NONE) {
            overridePendingTransition(0, 0);
        }
    }

    protected void hideBackButton() {
        if (mNavigationBar != null) {
            Animation fadeOut = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);
            final Button back = mNavigationBar.getBackButton();
            back.startAnimation(fadeOut);
            back.setVisibility(View.INVISIBLE);
        }
    }

    protected int getTransition() {
        return TRANSITION_ID_DEFAULT;
    }

    protected boolean tryEnablingWifi() {
        WifiManager wifiManager = getSystemService(WifiManager.class);
        if (wifiManager != null && !wifiManager.isWifiEnabled()) {
            return wifiManager.setWifiEnabled(true);
        }
        return false;
    }

    private void startSetupWizardExitActivity() {
        if (LOGV) {
            Log.v(TAG, "startSetupWizardExitActivity()");
        }
        startFirstRunActivity(new Intent(this, SetupWizardExitActivity.class));
    }

    protected void startFirstRunActivity(Intent intent) {
        if (LOGV) {
            Log.v(TAG, "starting activity " + intent);
        }
        intent.putExtra(EXTRA_FIRST_RUN, isFirstRun());
        intent.putExtra(EXTRA_HAS_MULTIPLE_USERS, hasMultipleUsers());
        intent.putExtra(EXTRA_USE_IMMERSIVE, true);
        startActivity(intent);
    }

    protected void startFirstRunActivityForResult(Intent intent, int requestCode) {
        if (LOGV) {
            Log.v(TAG, "startFirstRunActivityForResult requestCode=" + requestCode);
        }
        intent.putExtra(EXTRA_FIRST_RUN, isFirstRun());
        intent.putExtra(EXTRA_HAS_MULTIPLE_USERS, hasMultipleUsers());
        intent.putExtra(EXTRA_USE_IMMERSIVE, true);
        startActivityForResult(intent, requestCode);
    }


    protected boolean isFirstRun() {
        return mIsFirstRun;
    }

    protected boolean isPrimaryUser() {
        return mIsPrimaryUser;
    }

    public boolean hasMultipleUsers() {
        return ((UserManager) getSystemService(USER_SERVICE)).getUsers().size() > 1;
    }

    protected void logActivityState(String prefix) {
        Log.v(TAG, prefix + " isResumed=" + isResumed() + " isFinishing=" +
                isFinishing() + " isDestroyed=" + isDestroyed());
    }

    protected static String getRequestName(int requestCode) {
        StringBuilder sb = new StringBuilder();
        switch (requestCode) {
            case NEXT_REQUEST:
                sb.append("NEXT_REQUEST");
                break;
            case EMERGENCY_DIAL_ACTIVITY_REQUEST:
                sb.append("EMERGENCY_DIAL_ACTIVITY_REQUEST");
                break;
            case WIFI_ACTIVITY_REQUEST:
                sb.append("WIFI_ACTIVITY_REQUEST");
                break;
            case BLUETOOTH_ACTIVITY_REQUEST:
                sb.append("BLUETOOTH_ACTIVITY_REQUEST");
                break;
            case FINGERPRINT_ACTIVITY_REQUEST:
                sb.append("FINGERPRINT_ACTIVITY_REQUEST");
                break;
            case SCREENLOCK_ACTIVITY_REQUEST:
                sb.append("SCREENLOCK_ACTIVITY_REQUEST");
                break;
        }
        sb.append("(").append(requestCode).append(")");
        return sb.toString();
    }

    protected static String getResultName(int requestCode, int resultCode) {
        StringBuilder sb = new StringBuilder();
        switch (requestCode) {
            case WIFI_ACTIVITY_REQUEST:
                switch (resultCode) {
                    case RESULT_OK:
                        sb.append("RESULT_OK");
                        break;
                    case RESULT_CANCELED:
                        sb.append("RESULT_CANCELED");
                        break;
                    case RESULT_SKIP:
                        sb.append("RESULT_WIFI_SKIP");
                        break;
                    default:
                        break;
                }
            case BLUETOOTH_ACTIVITY_REQUEST:
                switch (resultCode) {
                    case RESULT_OK:
                        sb.append("RESULT_OK");
                        break;
                    case RESULT_CANCELED:
                        sb.append("RESULT_CANCELED");
                        break;
                    case RESULT_SKIP:
                        sb.append("RESULT_BLUETOOTH_SKIP");
                        break;
                    default:
                        break;
                }
            case FINGERPRINT_ACTIVITY_REQUEST:
                switch (resultCode) {
                    case RESULT_OK:
                        sb.append("RESULT_OK");
                        break;
                    case RESULT_CANCELED:
                        sb.append("RESULT_CANCELED");
                        break;
                    case RESULT_SKIP:
                        sb.append("RESULT_FINGERPRINT_SKIP");
                        break;
                    default:
                        break;
                }
            case SCREENLOCK_ACTIVITY_REQUEST:
                switch (resultCode) {
                    case RESULT_OK:
                        sb.append("RESULT_OK");
                        break;
                    case RESULT_CANCELED:
                        sb.append("RESULT_CANCELED");
                        break;
                    case RESULT_SKIP:
                        sb.append("RESULT_SCREENLOCK_SKIP");
                        break;
                    default:
                        break;
                }
            default:
                switch (resultCode) {
                    case RESULT_OK:
                        sb.append("RESULT_OK");
                        break;
                    case RESULT_CANCELED:
                        sb.append("RESULT_CANCELED");
                        break;
                    case RESULT_SKIP:
                        sb.append("RESULT_SKIP");
                        break;
                    case RESULT_RETRY:
                        sb.append("RESULT_RETRY");
                        break;
                    case RESULT_ACTIVITY_NOT_FOUND:
                        sb.append("RESULT_ACTIVITY_NOT_FOUND");
                        break;
                }
                break;
        }
        sb.append("(").append(resultCode).append(")");
        return sb.toString();
    }

    private void initLayout() {
        if (getLayoutResId() != -1) {
            setContentView(getLayoutResId());
        }
        if (getTitleResId() != -1) {
            TextView title = (TextView) findViewById(android.R.id.title);
            title.setText(getTitleResId());
        }
        if (getIconResId() != -1) {
            ImageView icon = (ImageView) findViewById(R.id.header_icon);
            icon.setImageResource(getIconResId());
            icon.setVisibility(View.VISIBLE);
        }
    }

    protected int getLayoutResId() {
        return -1;
    }

    protected int getTitleResId() {
        return -1;
    }

    protected int getIconResId() {
        return -1;
    }
}
