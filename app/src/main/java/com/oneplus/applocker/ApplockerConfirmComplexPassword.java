package com.oneplus.applocker;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import com.android.internal.widget.LockPatternChecker;
import com.android.internal.widget.LockPatternChecker.OnCheckCallback;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.TextViewInputDisabler;

public class ApplockerConfirmComplexPassword extends ApplockerConfirmActivity implements OnClickListener, OnEditorActionListener {
    private static final long ERROR_MESSAGE_TIMEOUT = 3000;
    private static final int FLAG_HIDE_FORCED = 0;
    private CountDownTimer mCountdownTimer;
    private boolean mDisappearing = false;
    private int mEffectiveUserId;
    private TextView mErrorTextView;
    private Handler mHandler = new Handler();
    private TextView mHeaderTextView;
    private InputMethodManager mImm;
    private EditText mPasswordEntry;
    private TextViewInputDisabler mPasswordEntryInputDisabler;
    private AsyncTask<?, ?, ?> mPendingLockCheck;
    private final Runnable mResetErrorRunnable = new Runnable() {
        public void run() {
            ApplockerConfirmComplexPassword.this.mErrorTextView.setText("");
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(2130903040);
        init();
    }

    private void init() {
        int i;
        Log.d(ApplockerConfirmActivity.TAG, "init");
        if (this.mIsInMultiWindowMode) {
            i = 2130903050;
        } else {
            i = 2130903040;
        }
        setContentView(i);
        this.mLockPatternUtils = new LockPatternUtils(this);
        this.mEffectiveUserId = Utils.getEffectiveUserId(this);
        this.mPasswordEntry = (EditText) findViewById(2131623942);
        this.mPasswordEntry.setOnEditorActionListener(this);
        this.mPasswordEntryInputDisabler = new TextViewInputDisabler(this.mPasswordEntry);
        this.mPackageIcon = (ImageView) findViewById(2131623936);
        preSetBackground();
        setBackgroundColor();
        this.mHeaderTextView = (TextView) findViewById(2131623940);
        this.mErrorTextView = (TextView) findViewById(2131623941);
        this.mFingerprintIcon = (ImageView) findViewById(2131623939);
        this.mFingerprintHelper = new FingerprintUiHelper(this.mFingerprintIcon, this.mErrorTextView, this);
        this.mFingerprintHelper.setHeaderTextView(this.mHeaderTextView);
        this.mImm = (InputMethodManager) getSystemService("input_method");
        if (getIntent() != null) {
            CharSequence headerMessage = "";
            if (TextUtils.isEmpty(headerMessage)) {
                headerMessage = getString(getDefaultHeader());
            }
            this.mHeaderTextView.setText(headerMessage);
        }
        if (this.mCredentialCheckResultTracker == null) {
            this.mCredentialCheckResultTracker = new CredentialCheckResultTracker();
        }
        View ok = findViewById(2131623963);
        if (ok != null) {
            ok.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (ApplockerConfirmComplexPassword.this.mPasswordEntry != null && ApplockerConfirmComplexPassword.this.mPasswordEntry.getText().length() > 0) {
                        ApplockerConfirmComplexPassword.this.handleNext();
                    }
                }
            });
        }
        View deleteOrCancel = findViewById(2131623961);
        if (deleteOrCancel != null) {
            deleteOrCancel.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (ApplockerConfirmComplexPassword.this.mPasswordEntry != null) {
                        String pin = ApplockerConfirmComplexPassword.this.mPasswordEntry.getText().toString();
                        if (pin == null || pin.equals("")) {
                            Intent intent = new Intent().putExtra(ApplockerConfirmActivity.APP_LOCKER_BLOCKING_APP, ApplockerConfirmComplexPassword.this.getBlockingApp());
                            Log.d(ApplockerConfirmActivity.TAG, "Cancel Pressed: hash code = " + ApplockerConfirmComplexPassword.this.getBlockingApp());
                            if (ApplockerConfirmComplexPassword.this.getBlockingApp() == 0) {
                                ApplockerConfirmComplexPassword.this.launchHome();
                            }
                            ApplockerConfirmComplexPassword.this.setResult(0, intent);
                            ApplockerConfirmComplexPassword.this.finish();
                            return;
                        }
                        ApplockerConfirmComplexPassword.this.mPasswordEntry.setText(pin.substring(0, pin.length() - 1));
                        ApplockerConfirmComplexPassword.this.mPasswordEntry.setSelection(pin.length() - 1);
                    }
                }
            });
        }
    }

    public void onResume() {
        super.onResume();
        int passwordQuality = this.mLockPatternUtils.getKeyguardStoredPasswordQuality(this.mEffectiveUserId);
        if (!(passwordQuality == 327680 || passwordQuality == 262144 || passwordQuality == 393216)) {
            setResult(0);
            finish();
        }
        long deadline = this.mLockPatternUtils.getLockoutAttemptDeadline(this.mEffectiveUserId);
        if (deadline != 0) {
            this.mCredentialCheckResultTracker.clearResult();
            handleAttemptLockout(deadline);
        } else {
            resetState();
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
                this.mHeaderTextView.setText(getString(getDefaultHeader()));
            } else {
                int count = 5 - this.mNumWrongConfirmAttempts;
                this.mHeaderTextView.setText(getResources().getQuantityString(2131689473, count, new Object[]{Integer.valueOf(count)}));
            }
        }
        this.mCredentialCheckResultTracker.setListener(this);
    }

    public void onWindowFocusChanged(final boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        this.mPasswordEntry.post(new Runnable() {
            public void run() {
                if (!hasFocus) {
                    ApplockerConfirmComplexPassword.this.mImm.hideSoftInputFromWindow(ApplockerConfirmComplexPassword.this.mPasswordEntry.getWindowToken(), 0);
                } else if (ApplockerConfirmComplexPassword.this.shouldAutoShowSoftKeyboard()) {
                    ApplockerConfirmComplexPassword.this.mImm.showSoftInput(ApplockerConfirmComplexPassword.this.mPasswordEntry, 1);
                }
            }
        });
    }

    private void resetState() {
        this.mPasswordEntry.setEnabled(true);
        this.mPasswordEntryInputDisabler.setInputEnabled(true);
        this.mPasswordEntry.post(new Runnable() {
            public void run() {
                if (ApplockerConfirmComplexPassword.this.shouldAutoShowSoftKeyboard()) {
                    ApplockerConfirmComplexPassword.this.mImm.showSoftInput(ApplockerConfirmComplexPassword.this.mPasswordEntry, 1);
                }
            }
        });
        this.mErrorTextView.setText("");
        this.mHeaderTextView.setText(getString(getDefaultHeader()));
        this.mFingerprintHelper.setLockOut(false);
    }

    private boolean shouldAutoShowSoftKeyboard() {
        return this.mPasswordEntry.isEnabled();
    }

    private int getDefaultHeader() {
        return 2131427683;
    }

    private void showError(int msg) {
        showError(msg, 3000);
    }

    private void showError(CharSequence msg, long timeout) {
        this.mHeaderTextView.setText(msg);
        this.mPasswordEntry.setText(null);
        this.mHandler.removeCallbacks(this.mResetErrorRunnable);
        if (timeout != 0) {
            this.mHandler.postDelayed(this.mResetErrorRunnable, timeout);
        }
    }

    private void showError(int msg, long timeout) {
        showError(getText(msg), timeout);
    }

    public void onPause() {
        super.onPause();
    }

    private void startCheckPassword(final String pin, final Intent intent) {
        final int localEffectiveUserId = this.mEffectiveUserId;
        this.mPendingLockCheck = LockPatternChecker.checkPassword(this.mLockPatternUtils, pin, localEffectiveUserId, new OnCheckCallback() {
            public void onChecked(boolean matched, int timeoutMs) {
                ApplockerConfirmComplexPassword.this.mPendingLockCheck = null;
                if (matched) {
                    intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_TYPE, 0);
                    intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD, pin);
                }
                ApplockerConfirmComplexPassword.this.mCredentialCheckResultTracker.setResult(matched, intent, timeoutMs, localEffectiveUserId, false);
            }
        });
    }

    private void onPasswordChecked(boolean matched, Intent intent, int timeoutMs, int effectiveUserId, boolean isFingerprint) {
        Log.d(ApplockerConfirmActivity.TAG, "onPasswordChecked: matched = " + matched + ", timeoutMs = " + timeoutMs + ", mNumWrongConfirmAttempts = " + this.mNumWrongConfirmAttempts);
        this.mPasswordEntryInputDisabler.setInputEnabled(true);
        if (matched) {
            if (!isFingerprint) {
                this.mNumWrongConfirmAttempts = 0;
            }
            Secure.putIntForUser(getContentResolver(), ApplockerConfirmActivity.KEY_NUM_WRONG_CONFIRM_ATTEMPTS, this.mNumWrongConfirmAttempts, ActivityManager.getCurrentUser());
            this.mImm.hideSoftInputFromWindow(this.mPasswordEntry.getWindowToken(), 0);
            this.mFingerprintHelper.reportStrongAuthenSuccess();
            finishActivity(intent);
            return;
        }
        int i = this.mNumWrongConfirmAttempts + 1;
        this.mNumWrongConfirmAttempts = i;
        if (i >= 5) {
            handleAttemptLockout(this.mLockPatternUtils.setLockoutAttemptDeadline(effectiveUserId, ApplockerConfirmActivity.LOCKOUT_TIME_OUT));
        } else if (this.mNumWrongConfirmAttempts > 2) {
            int count = 5 - this.mNumWrongConfirmAttempts;
            showError(getResources().getQuantityString(2131689473, count, new Object[]{Integer.valueOf(count)}), 3000);
        } else {
            showError(2131427684);
        }
    }

    public void onCredentialChecked(boolean matched, Intent intent, int timeoutMs, int effectiveUserId, boolean isFingerprint) {
        onPasswordChecked(matched, intent, timeoutMs, effectiveUserId, isFingerprint);
    }

    private void handleAttemptLockout(long elapsedRealtimeDeadline) {
        long elapsedRealtime = SystemClock.elapsedRealtime();
        this.mPasswordEntry.setEnabled(false);
        this.mFingerprintIcon.setVisibility(4);
        this.mFingerprintHelper.stopListening();
        this.mFingerprintHelper.setLockOut(true);
        this.mCountdownTimer = new CountDownTimer(elapsedRealtimeDeadline - elapsedRealtime, 1000) {
            public void onTick(long millisUntilFinished) {
                int secondsCountdown = (int) (millisUntilFinished / 1000);
                try {
                    ApplockerConfirmComplexPassword.this.showError(ApplockerConfirmComplexPassword.this.getResources().getQuantityString(2131689472, secondsCountdown, new Object[]{Integer.valueOf(secondsCountdown)}), (long) secondsCountdown);
                } catch (Exception e) {
                    Log.d(ApplockerConfirmActivity.TAG, "Exception e = " + e.toString());
                }
            }

            public void onFinish() {
                ApplockerConfirmComplexPassword.this.resetState();
                if (ApplockerConfirmComplexPassword.this.mHasFocus) {
                    ApplockerConfirmComplexPassword.this.mFingerprintHelper.startListening();
                    ApplockerConfirmComplexPassword.this.mNumWrongConfirmAttempts = 0;
                }
            }
        }.start();
    }

    public void onClick(View v) {
    }

    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId != 0 && actionId != 6 && actionId != 5) {
            return false;
        }
        handleNext();
        return true;
    }

    private void handleNext() {
        if (this.mPendingLockCheck == null && !this.mDisappearing) {
            String pin = this.mPasswordEntry.getText().toString();
            if (pin != null && !pin.equals("")) {
                this.mPasswordEntryInputDisabler.setInputEnabled(false);
                startCheckPassword(pin, new Intent());
            }
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        if (this.mCountdownTimer != null) {
            this.mCountdownTimer.cancel();
            this.mCountdownTimer = null;
        }
    }
}
