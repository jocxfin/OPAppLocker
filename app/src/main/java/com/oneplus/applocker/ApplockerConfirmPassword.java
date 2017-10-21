package com.oneplus.applocker;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings.Secure;
import android.support.v4.media.TransportMediator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import com.android.internal.widget.LockPatternChecker;
import com.android.internal.widget.LockPatternChecker.OnCheckCallback;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.TextViewInputDisabler;
import com.oneplus.applocker.OPPasswordTextViewForPin.OnTextEmptyListerner;

public class ApplockerConfirmPassword extends ApplockerConfirmActivity implements OnClickListener, OnEditorActionListener {
    private static final int FLAG_HIDE_FORCED = 0;
    private CountDownTimer mCountdownTimer;
    private boolean mDisappearing = false;
    private TextView mErrorTextView;
    private Handler mHandler = new Handler();
    private TextView mHeaderTextView;
    private InputMethodManager mImm;
    private EditText mPasswordEntry;
    private TextViewInputDisabler mPasswordEntryInputDisabler;
    public OPPasswordInputCountCallBack mPasswordInputCountCallBack = new OPPasswordInputCountCallBack() {
        public void setNumbPadKeyForPinEnable(boolean enable) {
        }

        public boolean checkPassword() {
            return false;
        }
    };
    private OPPasswordTextViewForPin mPasswordTextViewForPin;
    private final Runnable mResetErrorRunnable = new Runnable() {
        public void run() {
            ApplockerConfirmPassword.this.mErrorTextView.setText("");
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
    }

    protected void onPause() {
        super.onPause();
        if (this.mPasswordTextViewForPin != null) {
            this.mPasswordTextViewForPin.reset(true);
        }
    }

    protected void onResume() {
        super.onResume();
        int passwordQuality = this.mLockPatternUtils.getKeyguardStoredPasswordQuality(this.mEffectiveUserId);
        if (!(passwordQuality == 131072 || passwordQuality == 196608)) {
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
                this.mHeaderTextView.setText(getDefaultHeader());
            } else {
                int count = 5 - this.mNumWrongConfirmAttempts;
                this.mHeaderTextView.setText(getResources().getQuantityString(2131689473, count, new Object[]{Integer.valueOf(count)}));
            }
        }
        this.mCredentialCheckResultTracker.setListener(this);
    }

    public void onWindowFocusChanged(final boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (this.mPasswordEntry != null) {
            this.mPasswordEntry.post(new Runnable() {
                public void run() {
                    if (!hasFocus) {
                        ApplockerConfirmPassword.this.mImm.hideSoftInputFromWindow(ApplockerConfirmPassword.this.mPasswordEntry.getWindowToken(), 0);
                    }
                }
            });
        }
    }

    private void init() {
        int i;
        boolean useMultiWindowLayout = !this.mIsInMultiWindowMode ? this.mIsLandscape : true;
        if (useMultiWindowLayout) {
            i = 2130903051;
        } else {
            i = 2130903041;
        }
        setContentView(i);
        Window window = getWindow();
        if (useMultiWindowLayout) {
            i = 20;
        } else {
            i = 3;
        }
        window.setSoftInputMode(i);
        this.mPackageIcon = (ImageView) findViewById(2131623936);
        preSetBackground();
        setBackgroundColor();
        this.mLockPatternUtils = new LockPatternUtils(this);
        this.mEffectiveUserId = Utils.getEffectiveUserId(this);
        this.mHeaderTextView = (TextView) findViewById(2131623940);
        this.mHeaderTextView.setText(getString(getDefaultHeader()));
        this.mErrorTextView = (TextView) findViewById(2131623941);
        this.mFingerprintIcon = (ImageView) findViewById(2131623939);
        this.mFingerprintHelper = new FingerprintUiHelper(this.mFingerprintIcon, this.mErrorTextView, this);
        this.mFingerprintHelper.setHeaderTextView(this.mHeaderTextView);
        this.mImm = (InputMethodManager) getSystemService("input_method");
        this.mPasswordEntry = (EditText) findViewById(2131623942);
        if (this.mPasswordEntry != null) {
            this.mPasswordEntry.setOnEditorActionListener(this);
            this.mPasswordEntryInputDisabler = new TextViewInputDisabler(this.mPasswordEntry);
        }
        View ok = findViewById(2131623963);
        if (ok != null) {
            ok.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (ApplockerConfirmPassword.this.mPasswordTextViewForPin != null && ApplockerConfirmPassword.this.mPasswordTextViewForPin.getText().length() > 0) {
                        ApplockerConfirmPassword.this.mPasswordTextViewForPin.setEnabled(false);
                        ApplockerConfirmPassword.this.handleNext();
                    }
                    if (ApplockerConfirmPassword.this.mPasswordEntry != null && ApplockerConfirmPassword.this.mPasswordEntry.getText().length() > 0) {
                        ApplockerConfirmPassword.this.handleNext();
                    }
                }
            });
        }
        final TextView deleteOrCancel = (TextView) findViewById(2131623961);
        if (deleteOrCancel != null) {
            deleteOrCancel.setOnClickListener(this);
        }
        this.mPasswordTextViewForPin = (OPPasswordTextViewForPin) findViewById(2131623946);
        if (this.mPasswordTextViewForPin != null) {
            this.mPasswordTextViewForPin.setCallBack(this.mPasswordInputCountCallBack);
            this.mPasswordTextViewForPin.setTextEmptyListener(new OnTextEmptyListerner() {
                public void onTextChanged(String text) {
                    if (deleteOrCancel != null) {
                        int i;
                        TextView textView = deleteOrCancel;
                        if (text.equals("")) {
                            i = 2131427688;
                        } else {
                            i = 2131427689;
                        }
                        textView.setText(i);
                    }
                    if (ApplockerConfirmPassword.this.mPasswordTextViewForPin.getText().length() == ApplockerConfirmPassword.this.mPasswordTextViewForPin.getMaxLockPasswordSize()) {
                        ApplockerConfirmPassword.this.mPasswordTextViewForPin.setEnabled(false);
                        ApplockerConfirmPassword.this.handleNext();
                    }
                }
            });
        }
        if (this.mCredentialCheckResultTracker == null) {
            this.mCredentialCheckResultTracker = new CredentialCheckResultTracker();
        }
        final ScrollView scrollView = (ScrollView) findViewById(2131623944);
        if (scrollView != null) {
            scrollView.post(new Runnable() {
                public void run() {
                    scrollView.fullScroll(TransportMediator.KEYCODE_MEDIA_RECORD);
                }
            });
        }
    }

    private void resetState() {
        if (this.mPasswordEntry != null) {
            this.mPasswordEntry.setText(null);
            this.mPasswordEntry.setEnabled(true);
            this.mPasswordEntry.post(new Runnable() {
                public void run() {
                    if (ApplockerConfirmPassword.this.shouldAutoShowSoftKeyboard()) {
                        ApplockerConfirmPassword.this.mImm.showSoftInput(ApplockerConfirmPassword.this.mPasswordEntry, 1);
                    }
                }
            });
        }
        if (this.mPasswordEntryInputDisabler != null) {
            this.mPasswordEntryInputDisabler.setInputEnabled(true);
        }
        if (this.mPasswordTextViewForPin != null) {
            this.mPasswordTextViewForPin.setEnabled(true);
        }
        this.mErrorTextView.setText("");
        this.mHeaderTextView.setText(getDefaultHeader());
        this.mFingerprintHelper.setLockOut(false);
    }

    private boolean shouldAutoShowSoftKeyboard() {
        return this.mPasswordEntry != null ? this.mPasswordEntry.isEnabled() : false;
    }

    private void handleNext() {
        if (this.mPendingLockCheck == null && !this.mDisappearing) {
            String pin = getEditEntryString();
            if (this.mPasswordTextViewForPin != null) {
                this.mPasswordTextViewForPin.reset(true);
            }
            if (this.mPasswordEntry != null) {
                this.mPasswordEntry.setText(null);
            }
            if (pin != null && !pin.equals("")) {
                if (this.mPasswordEntryInputDisabler != null) {
                    this.mPasswordEntryInputDisabler.setInputEnabled(false);
                }
                startCheckPassword(pin, new Intent());
            }
        }
    }

    private String getEditEntryString() {
        if (this.mPasswordTextViewForPin != null) {
            return this.mPasswordTextViewForPin.getText().toString();
        }
        if (this.mPasswordEntry != null) {
            return this.mPasswordEntry.getText().toString();
        }
        return "";
    }

    private void startCheckPassword(final String pin, final Intent intent) {
        final int localEffectiveUserId = this.mEffectiveUserId;
        this.mPendingLockCheck = LockPatternChecker.checkPassword(this.mLockPatternUtils, pin, localEffectiveUserId, new OnCheckCallback() {
            public void onChecked(boolean matched, int timeoutMs) {
                ApplockerConfirmPassword.this.mPendingLockCheck = null;
                if (matched) {
                    intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_TYPE, 3);
                    intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD, pin);
                }
                ApplockerConfirmPassword.this.mCredentialCheckResultTracker.setResult(matched, intent, timeoutMs, localEffectiveUserId, false);
            }
        });
    }

    public void onCredentialChecked(boolean matched, Intent intent, int timeoutMs, int effectiveUserId, boolean isFingerprint) {
        onPasswordChecked(matched, intent, timeoutMs, effectiveUserId, isFingerprint);
    }

    private void onPasswordChecked(boolean matched, Intent intent, int timeoutMs, int effectiveUserId, boolean isFingerprint) {
        Log.d(ApplockerConfirmActivity.TAG, "onPasswordChecked: matched = " + matched + ", timeoutMs = " + timeoutMs + ", mNumWrongConfirmAttempts = " + this.mNumWrongConfirmAttempts);
        if (this.mPasswordEntryInputDisabler != null) {
            this.mPasswordEntryInputDisabler.setInputEnabled(true);
        }
        if (this.mPasswordTextViewForPin != null) {
            this.mPasswordTextViewForPin.setEnabled(true);
        }
        if (matched) {
            if (!isFingerprint) {
                this.mNumWrongConfirmAttempts = 0;
            }
            Secure.putIntForUser(getContentResolver(), ApplockerConfirmActivity.KEY_NUM_WRONG_CONFIRM_ATTEMPTS, this.mNumWrongConfirmAttempts, ActivityManager.getCurrentUser());
            if (this.mPasswordEntry != null) {
                this.mImm.hideSoftInputFromWindow(this.mPasswordEntry.getWindowToken(), 0);
            }
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
            this.mHeaderTextView.setText(getResources().getQuantityString(2131689473, count, new Object[]{Integer.valueOf(count)}));
        } else {
            showError(2131427679);
        }
    }

    private void handleAttemptLockout(long elapsedRealtimeDeadline) {
        long elapsedRealtime = SystemClock.elapsedRealtime();
        if (this.mPasswordEntry != null) {
            this.mPasswordEntry.setEnabled(false);
        }
        if (this.mPasswordTextViewForPin != null) {
            this.mPasswordTextViewForPin.setEnabled(false);
        }
        this.mFingerprintHelper.stopListening();
        this.mFingerprintHelper.setLockOut(true);
        this.mCountdownTimer = new CountDownTimer(elapsedRealtimeDeadline - elapsedRealtime, 1000) {
            public void onTick(long millisUntilFinished) {
                int secondsCountdown = (int) (millisUntilFinished / 1000);
                try {
                    ApplockerConfirmPassword.this.mHeaderTextView.setText(ApplockerConfirmPassword.this.getResources().getQuantityString(2131689472, secondsCountdown, new Object[]{Integer.valueOf(secondsCountdown)}));
                } catch (Exception e) {
                    Log.d(ApplockerConfirmActivity.TAG, "Exception e = " + e.toString());
                }
            }

            public void onFinish() {
                ApplockerConfirmPassword.this.resetState();
                if (ApplockerConfirmPassword.this.mHasFocus) {
                    ApplockerConfirmPassword.this.mFingerprintHelper.startListening();
                    ApplockerConfirmPassword.this.mNumWrongConfirmAttempts = 0;
                }
            }
        }.start();
    }

    private void showError(int msg) {
        showError(msg, (long) ApplockerConfirmActivity.ERROR_MESSAGE_TIMEOUT);
    }

    private void showError(int msg, long timeout) {
        showError(String.format(getResources().getString(msg), new Object[]{getResources().getString(2131427684)}), timeout);
    }

    private void showError(CharSequence msg, long timeout) {
        this.mHeaderTextView.setText(msg);
        this.mHeaderTextView.announceForAccessibility(this.mHeaderTextView.getText());
        this.mHandler.removeCallbacks(this.mResetErrorRunnable);
        if (timeout != 0) {
            this.mHandler.postDelayed(this.mResetErrorRunnable, timeout);
        }
    }

    private int getDefaultHeader() {
        return 2131427678;
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case 2131623961:
                String pin = null;
                if (this.mPasswordTextViewForPin != null) {
                    pin = this.mPasswordTextViewForPin.getText();
                } else if (this.mPasswordEntry != null) {
                    pin = this.mPasswordEntry.getText().toString();
                }
                if (pin == null || pin.equals("")) {
                    Intent intent = new Intent().putExtra(ApplockerConfirmActivity.APP_LOCKER_BLOCKING_APP, getBlockingApp());
                    Log.d(ApplockerConfirmActivity.TAG, "Cancel Pressed: hash code = " + getBlockingApp());
                    if (getBlockingApp() == 0) {
                        launchHome();
                    }
                    setResult(0, intent);
                    finish();
                    return;
                }
                if (this.mPasswordTextViewForPin != null) {
                    this.mPasswordTextViewForPin.deleteLastChar();
                }
                if (this.mPasswordEntry != null) {
                    this.mPasswordEntry.setText(pin.substring(0, pin.length() - 1));
                    this.mPasswordEntry.setSelection(pin.length() - 1);
                    return;
                }
                return;
            default:
                return;
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        if (this.mCountdownTimer != null) {
            this.mCountdownTimer.cancel();
            this.mCountdownTimer = null;
        }
    }

    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId != 0 && actionId != 6 && actionId != 5) {
            return false;
        }
        handleNext();
        return true;
    }
}
