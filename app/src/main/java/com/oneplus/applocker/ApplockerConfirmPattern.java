package com.oneplus.applocker;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.internal.widget.LinearLayoutWithDefaultTouchRecepient;
import com.android.internal.widget.LockPatternChecker;
import com.android.internal.widget.LockPatternChecker.OnCheckCallback;
import com.android.internal.widget.LockPatternChecker.OnVerifyCallback;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternView;
import com.android.internal.widget.LockPatternView.Cell;
import com.android.internal.widget.LockPatternView.CellState;
import com.android.internal.widget.LockPatternView.DisplayMode;
import com.android.internal.widget.LockPatternView.OnPatternListener;
import com.android.settingslib.animation.AppearAnimationUtils;
import com.android.settingslib.animation.AppearAnimationUtils.RowTranslationScaler;
import com.android.settingslib.animation.DisappearAnimationUtils;
import com.oneplus.applocker.views.ConfirmPatternView;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ApplockerConfirmPattern extends ApplockerConfirmActivity {
    private static final /* synthetic */ int[] -com-oneplus-applocker-ApplockerConfirmPattern$StageSwitchesValues = null;
    private static final int WRONG_PATTERN_CLEAR_TIMEOUT_MS = 2000;
    private AppearAnimationUtils mAppearAnimationUtils;
    private Runnable mClearPatternRunnable = new Runnable() {
        public void run() {
            ApplockerConfirmPattern.this.mLockPatternView.clearPattern();
            ApplockerConfirmPattern.this.mHeaderTextView.setText(2131427680);
        }
    };
    private OnPatternListener mConfirmExistingLockPatternListener = new OnPatternListener() {
        public void onPatternStart() {
            ApplockerConfirmPattern.this.mLockPatternView.removeCallbacks(ApplockerConfirmPattern.this.mClearPatternRunnable);
        }

        public void onPatternCleared() {
            ApplockerConfirmPattern.this.mLockPatternView.removeCallbacks(ApplockerConfirmPattern.this.mClearPatternRunnable);
        }

        public void onPatternCellAdded(List<Cell> list) {
        }

        public void onPatternDetected(List<Cell> pattern) {
            if (ApplockerConfirmPattern.this.mPendingLockCheck == null && !ApplockerConfirmPattern.this.mDisappearing) {
                ApplockerConfirmPattern.this.mLockPatternView.setEnabled(false);
                boolean verifyChallenge = ApplockerConfirmPattern.this.getIntent().getBooleanExtra(ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, false);
                Intent intent = new Intent();
                if (!verifyChallenge) {
                    startCheckPattern(pattern, intent);
                }
            }
        }

        private void startVerifyPattern(List<Cell> pattern, final Intent intent) {
            final int localEffectiveUserId = ApplockerConfirmPattern.this.mEffectiveUserId;
            List<Cell> list = pattern;
            ApplockerConfirmPattern.this.mPendingLockCheck = LockPatternChecker.verifyPattern(ApplockerConfirmPattern.this.mLockPatternUtils, list, ApplockerConfirmPattern.this.getIntent().getLongExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE, 0), localEffectiveUserId, new OnVerifyCallback() {
                public void onVerified(byte[] token, int timeoutMs) {
                    ApplockerConfirmPattern.this.mPendingLockCheck = null;
                    boolean matched = false;
                    if (token != null) {
                        matched = true;
                        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, token);
                    }
                    ApplockerConfirmPattern.this.mCredentialCheckResultTracker.setResult(matched, intent, timeoutMs, localEffectiveUserId, false);
                }
            });
        }

        private void startCheckPattern(final List<Cell> pattern, final Intent intent) {
            if (pattern.size() < 4) {
                ApplockerConfirmPattern.this.mHeaderTextView.setText(2131427682);
                ApplockerConfirmPattern.this.mLockPatternView.setEnabled(true);
                ApplockerConfirmPattern.this.mLockPatternView.setDisplayMode(DisplayMode.Wrong);
                ApplockerConfirmPattern.this.postClearPatternRunnable();
                return;
            }
            final int localEffectiveUserId = ApplockerConfirmPattern.this.mEffectiveUserId;
            ApplockerConfirmPattern.this.mPendingLockCheck = LockPatternChecker.checkPattern(ApplockerConfirmPattern.this.mLockPatternUtils, pattern, localEffectiveUserId, new OnCheckCallback() {
                public void onChecked(boolean matched, int timeoutMs) {
                    ApplockerConfirmPattern.this.mPendingLockCheck = null;
                    if (matched) {
                        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_TYPE, 2);
                        intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD, LockPatternUtils.patternToString(pattern));
                    }
                    ApplockerConfirmPattern.this.mCredentialCheckResultTracker.setResult(matched, intent, timeoutMs, localEffectiveUserId, false);
                }
            });
        }
    };
    private CountDownTimer mCountdownTimer;
    private DisappearAnimationUtils mDisappearAnimationUtils;
    private boolean mDisappearing = false;
    private int mEffectiveUserId;
    private TextView mErrorTextView;
    private TextView mHeaderTextView;
    private LockPatternView mLockPatternView;
    private AsyncTask<?, ?, ?> mPendingLockCheck;

    private enum Stage {
        NeedToUnlock,
        NeedToUnlockWrong,
        LockedOut
    }

    private static /* synthetic */ int[] -getcom-oneplus-applocker-ApplockerConfirmPattern$StageSwitchesValues() {
        if (-com-oneplus-applocker-ApplockerConfirmPattern$StageSwitchesValues != null) {
            return -com-oneplus-applocker-ApplockerConfirmPattern$StageSwitchesValues;
        }
        int[] iArr = new int[Stage.values().length];
        try {
            iArr[Stage.LockedOut.ordinal()] = 1;
        } catch (NoSuchFieldError e) {
        }
        try {
            iArr[Stage.NeedToUnlock.ordinal()] = 2;
        } catch (NoSuchFieldError e2) {
        }
        try {
            iArr[Stage.NeedToUnlockWrong.ordinal()] = 3;
        } catch (NoSuchFieldError e3) {
        }
        -com-oneplus-applocker-ApplockerConfirmPattern$StageSwitchesValues = iArr;
        return iArr;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(2130903042);
        init(savedInstanceState);
    }

    public void init(Bundle savedInstanceState) {
        int i = (!this.mIsInMultiWindowMode || this.mIsLandscape) ? 2130903042 : 2130903052;
        setContentView(i);
        View contentView = findViewById(2131623938);
        if (contentView != null && (contentView instanceof ConfirmPatternView)) {
            ((ConfirmPatternView) contentView).setArgs(this.mIsInMultiWindowMode, this.mIsLandscape);
        }
        this.mLockPatternUtils = new LockPatternUtils(this);
        this.mEffectiveUserId = Utils.getEffectiveUserId(this);
        this.mPackageIcon = (ImageView) findViewById(2131623936);
        preSetBackground();
        setBackgroundColor();
        this.mHeaderTextView = (TextView) findViewById(2131623940);
        this.mFingerprintIcon = (ImageView) findViewById(2131623939);
        this.mFingerprintHelper = new FingerprintUiHelper(this.mFingerprintIcon, (TextView) findViewById(2131623941), this);
        this.mFingerprintHelper.setHeaderTextView(this.mHeaderTextView);
        this.mLockPatternView = (LockPatternView) findViewById(2131623937);
        this.mErrorTextView = (TextView) findViewById(2131623941);
        ((LinearLayoutWithDefaultTouchRecepient) findViewById(2131623938)).setDefaultTouchRecepient(this.mLockPatternView);
        Intent intent = getIntent();
        this.mLockPatternView.setTactileFeedbackEnabled(this.mLockPatternUtils.isTactileFeedbackEnabled());
        this.mLockPatternView.setInStealthMode(!this.mLockPatternUtils.isVisiblePatternEnabled(this.mEffectiveUserId));
        this.mLockPatternView.setOnPatternListener(this.mConfirmExistingLockPatternListener);
        updateStage(Stage.NeedToUnlock);
        if (savedInstanceState != null) {
            this.mNumWrongConfirmAttempts = savedInstanceState.getInt(ApplockerConfirmActivity.KEY_NUM_WRONG_CONFIRM_ATTEMPTS);
        } else if (!this.mLockPatternUtils.isLockPatternEnabled(this.mEffectiveUserId)) {
            intent.putExtra(ApplockerConfirmActivity.APP_LOCKER_BLOCKING_APP, getBlockingApp());
            setResult(-1, intent);
            finish();
        }
        this.mAppearAnimationUtils = new AppearAnimationUtils(this, 220, 2.0f, 1.3f, AnimationUtils.loadInterpolator(this, 17563662));
        this.mDisappearAnimationUtils = new DisappearAnimationUtils(this, 125, 4.0f, 0.3f, AnimationUtils.loadInterpolator(this, 17563663), new RowTranslationScaler() {
            public float getRowTranslationScale(int row, int numRows) {
                return ((float) (numRows - row)) / ((float) numRows);
            }
        });
        if (this.mCredentialCheckResultTracker == null) {
            this.mCredentialCheckResultTracker = new CredentialCheckResultTracker();
        }
    }

    protected void onResume() {
        super.onResume();
        if (this.mLockPatternUtils.getKeyguardStoredPasswordQuality(this.mEffectiveUserId) != 65536) {
            setResult(0);
            finish();
        }
        long deadline = this.mLockPatternUtils.getLockoutAttemptDeadline(this.mEffectiveUserId);
        if (deadline != 0) {
            this.mCredentialCheckResultTracker.clearResult();
            handleAttemptLockout(deadline);
        } else if (!this.mLockPatternView.isEnabled()) {
            updateStage(Stage.NeedToUnlock);
        }
        this.mNumWrongConfirmAttempts = Secure.getIntForUser(getContentResolver(), ApplockerConfirmActivity.KEY_NUM_WRONG_CONFIRM_ATTEMPTS, 0, ActivityManager.getCurrentUser());
        Log.d(ApplockerConfirmActivity.TAG, "onResume: deadline = " + deadline + ", mNumWrongConfirmAttempts = " + this.mNumWrongConfirmAttempts);
        if (deadline == 0) {
            registerFingerprint();
            if (this.mNumWrongConfirmAttempts >= 5) {
                this.mNumWrongConfirmAttempts = 0;
                this.mCredentialCheckResultTracker.clearResult();
            }
            if (this.mNumWrongConfirmAttempts <= 2) {
                updateStage(Stage.NeedToUnlock);
            } else {
                int count = 5 - this.mNumWrongConfirmAttempts;
                this.mHeaderTextView.setText(getResources().getQuantityString(2131689473, count, new Object[]{Integer.valueOf(count)}));
            }
        }
        this.mCredentialCheckResultTracker.setListener(this);
    }

    public void onCredentialChecked(boolean matched, Intent intent, int timeoutMs, int effectiveUserId, boolean isFingerprint) {
        onPatternChecked(matched, intent, timeoutMs, effectiveUserId, isFingerprint);
    }

    private void onPatternChecked(boolean matched, Intent intent, int timeoutMs, int effectiveUserId, boolean isFingerprint) {
        this.mLockPatternView.setEnabled(true);
        Log.d(ApplockerConfirmActivity.TAG, "onPatternChecked: match = " + matched + ", timeoutMs = " + timeoutMs);
        if (matched) {
            if (!isFingerprint) {
                this.mNumWrongConfirmAttempts = 0;
            }
            this.mFingerprintHelper.reportStrongAuthenSuccess();
            Secure.putIntForUser(getContentResolver(), ApplockerConfirmActivity.KEY_NUM_WRONG_CONFIRM_ATTEMPTS, this.mNumWrongConfirmAttempts, ActivityManager.getCurrentUser());
            finishActivity(intent);
            return;
        }
        Log.d(ApplockerConfirmActivity.TAG, "onPatternChecked: mNumWrongConfirmAttempts = " + this.mNumWrongConfirmAttempts);
        int i = this.mNumWrongConfirmAttempts + 1;
        this.mNumWrongConfirmAttempts = i;
        if (i >= 5) {
            handleAttemptLockout(this.mLockPatternUtils.setLockoutAttemptDeadline(effectiveUserId, ApplockerConfirmActivity.LOCKOUT_TIME_OUT));
            return;
        }
        if (this.mNumWrongConfirmAttempts > 2) {
            int count = 5 - this.mNumWrongConfirmAttempts;
            this.mHeaderTextView.setText(getResources().getQuantityString(2131689473, count, new Object[]{Integer.valueOf(count)}));
        } else {
            this.mHeaderTextView.setText(2131427681);
        }
        updateStage(Stage.NeedToUnlockWrong);
        postClearPatternRunnable();
    }

    private void handleAttemptLockout(long elapsedRealtimeDeadline) {
        updateStage(Stage.LockedOut);
        long elapsedRealtime = SystemClock.elapsedRealtime();
        this.mFingerprintHelper.stopListening();
        this.mCountdownTimer = new CountDownTimer(elapsedRealtimeDeadline - elapsedRealtime, 1000) {
            public void onTick(long millisUntilFinished) {
                int secondsCountdown = (int) (millisUntilFinished / 1000);
                try {
                    ApplockerConfirmPattern.this.mHeaderTextView.setText(ApplockerConfirmPattern.this.getResources().getQuantityString(2131689472, secondsCountdown, new Object[]{Integer.valueOf(secondsCountdown)}));
                } catch (Exception e) {
                    Log.d(ApplockerConfirmActivity.TAG, "Exception e = " + e.toString());
                }
            }

            public void onFinish() {
                if (ApplockerConfirmPattern.this.mHasFocus) {
                    ApplockerConfirmPattern.this.mFingerprintHelper.startListening();
                    ApplockerConfirmPattern.this.mNumWrongConfirmAttempts = 0;
                }
                ApplockerConfirmPattern.this.updateStage(Stage.NeedToUnlock);
            }
        }.start();
    }

    private void updateStage(Stage stage) {
        Log.d(ApplockerConfirmActivity.TAG, "updateStage: " + stage);
        switch (-getcom-oneplus-applocker-ApplockerConfirmPattern$StageSwitchesValues()[stage.ordinal()]) {
            case 1:
                this.mFingerprintHelper.setLockOut(true);
                this.mLockPatternView.clearPattern();
                this.mLockPatternView.setEnabled(false);
                break;
            case 2:
                this.mFingerprintHelper.setLockOut(false);
                this.mHeaderTextView.setText(2131427680);
                this.mErrorTextView.setText("");
                this.mLockPatternView.setEnabled(true);
                this.mLockPatternView.enableInput();
                this.mLockPatternView.clearPattern();
                break;
            case 3:
                this.mLockPatternView.setDisplayMode(DisplayMode.Wrong);
                this.mLockPatternView.setEnabled(true);
                this.mLockPatternView.enableInput();
                break;
        }
        this.mHeaderTextView.announceForAccessibility(this.mHeaderTextView.getText());
    }

    private void postClearPatternRunnable() {
        this.mLockPatternView.removeCallbacks(this.mClearPatternRunnable);
        this.mLockPatternView.postDelayed(this.mClearPatternRunnable, 2000);
    }

    private void startDisappearAnimation(final Intent intent) {
        if (!this.mDisappearing) {
            this.mDisappearing = true;
            this.mLockPatternView.clearPattern();
            this.mDisappearAnimationUtils.startAnimation2d(getActiveViews(), new Runnable() {
                public void run() {
                    if (!ApplockerConfirmPattern.this.isFinishing()) {
                        intent.putExtra(ApplockerConfirmActivity.APP_LOCKER_PACKAGE, ApplockerConfirmPattern.this.getUnlockPackageName());
                        intent.putExtra(ApplockerConfirmActivity.APP_LOCKER_BLOCKING_APP, ApplockerConfirmPattern.this.getBlockingApp());
                        ApplockerConfirmPattern.this.setResult(-1, intent);
                        ApplockerConfirmPattern.this.finish();
                        ApplockerConfirmPattern.this.overridePendingTransition(2130968581, 2130968582);
                    }
                }
            }, this);
        }
    }

    private Object[][] getActiveViews() {
        int i;
        ArrayList<ArrayList<Object>> result = new ArrayList();
        result.add(new ArrayList(Collections.singletonList(this.mPackageIcon)));
        result.add(new ArrayList(Collections.singletonList(this.mHeaderTextView)));
        result.add(new ArrayList(Collections.singletonList(this.mErrorTextView)));
        CellState[][] cellStates = this.mLockPatternView.getCellStates();
        for (i = 0; i < cellStates.length; i++) {
            int j;
            ArrayList<Object> row = new ArrayList();
            for (Object add : cellStates[i]) {
                row.add(add);
            }
            result.add(row);
        }
        Object[][] resultArr = (Object[][]) Array.newInstance(Object.class, new int[]{result.size(), cellStates[0].length});
        for (i = 0; i < result.size(); i++) {
            row = (ArrayList) result.get(i);
            for (j = 0; j < row.size(); j++) {
                resultArr[i][j] = row.get(j);
            }
        }
        return resultArr;
    }

    public void createAnimation(Object obj, long delay, long duration, float translationY, boolean appearing, Interpolator interpolator, Runnable finishListener) {
        if (obj instanceof CellState) {
            float f;
            CellState animatedCell = (CellState) obj;
            LockPatternView lockPatternView = this.mLockPatternView;
            float f2 = appearing ? 1.0f : 0.0f;
            float f3 = appearing ? translationY : 0.0f;
            if (appearing) {
                f = 0.0f;
            } else {
                f = translationY;
            }
            lockPatternView.startCellStateAnimation(animatedCell, 1.0f, f2, f3, f, appearing ? 0.0f : 1.0f, 1.0f, delay, duration, interpolator, finishListener);
            return;
        }
        this.mAppearAnimationUtils.createAnimation((View) obj, delay, duration, translationY, appearing, interpolator, finishListener);
    }

    protected void onDestroy() {
        super.onDestroy();
        if (this.mCountdownTimer != null) {
            this.mCountdownTimer.cancel();
            this.mCountdownTimer = null;
        }
    }
}
