package com.oneplus.applocker;

import android.app.ActivityManagerNative;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintManager.AuthenticationCallback;
import android.hardware.fingerprint.FingerprintManager.AuthenticationResult;
import android.hardware.fingerprint.FingerprintManager.LockoutResetCallback;
import android.hardware.fingerprint.IFingerprintService;
import android.hardware.fingerprint.IFingerprintService.Stub;
import android.os.CancellationSignal;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

public class FingerprintUiHelper extends AuthenticationCallback {
    private static final long ERROR_TIMEOUT = 1300;
    private final String TAG = "FingerprintUiHelper";
    private boolean mAuthenticateSuccess;
    private Callback mCallback;
    private CancellationSignal mCancellationSignal;
    private TextView mErrorTextView;
    private FingerprintManager mFingerprintManager;
    private TextView mHeaderText;
    private ImageView mIcon;
    private boolean mLockOut;
    private final LockoutResetCallback mLockoutResetCallback = new LockoutResetCallback() {
        public void onLockoutReset() {
            Log.d("FingerprintUiHelper", "onLockoutReset");
            FingerprintUiHelper.this.handleFingerprintLockoutReset();
        }
    };
    private Runnable mResetErrorTextRunnable = new Runnable() {
        public void run() {
            FingerprintUiHelper.this.mErrorTextView.setText("");
            FingerprintUiHelper.this.mIcon.setImageResource(2130837516);
            if (FingerprintUiHelper.this.mHeaderText != null) {
                FingerprintUiHelper.this.mHeaderText.setVisibility(0);
            }
        }
    };

    public interface Callback {
        void onAuthenticated();

        void onFingerprintIconVisibilityChanged(boolean z);
    }

    public FingerprintUiHelper(ImageView icon, TextView errorTextView, Callback callback) {
        this.mFingerprintManager = (FingerprintManager) icon.getContext().getSystemService(FingerprintManager.class);
        this.mIcon = icon;
        this.mErrorTextView = errorTextView;
        this.mCallback = callback;
    }

    private void handleFingerprintLockoutReset() {
        startListening();
    }

    public void startListening() {
        if (shouldListen()) {
            Log.d("FingerprintUiHelper", "startListening");
            this.mCancellationSignal = new CancellationSignal();
            this.mFingerprintManager.authenticate(null, this.mCancellationSignal, 0, this, null);
            this.mIcon.setImageResource(2130837505);
        }
        updateFingerprintIconVisibility();
    }

    private boolean shouldListen() {
        try {
            if (this.mFingerprintManager.getEnrolledFingerprints().size() <= 0) {
                Log.d("FingerprintUiHelper", "not listen due to no fingerprint record");
                return false;
            } else if (isListening()) {
                Log.d("FingerprintUiHelper", "not listen due to already listening");
                return false;
            } else if (this.mAuthenticateSuccess) {
                Log.d("FingerprintUiHelper", "not listen due to already authenticating success");
                return false;
            } else if (this.mLockOut) {
                Log.d("FingerprintUiHelper", "not listen due to in lockout");
                return false;
            } else if (ActivityManagerNative.getDefault().isKeyguardDone()) {
                return true;
            } else {
                Log.d("FingerprintUiHelper", "not listen due to keyguard lock");
                return false;
            }
        } catch (Exception e) {
            Log.d("FingerprintUiHelper", "Excetpion e = " + e.toString());
            return false;
        }
    }

    public void stopListening() {
        Log.d("FingerprintUiHelper", "stopListening");
        if (this.mCancellationSignal != null) {
            this.mCancellationSignal.cancel();
            this.mCancellationSignal = null;
        }
        updateFingerprintIconVisibility();
    }

    public boolean isListening() {
        return (this.mCancellationSignal == null || this.mCancellationSignal.isCanceled()) ? false : true;
    }

    private String getAuthenticatedPackage() {
        String pkg = "";
        IFingerprintService ifp = Stub.asInterface(ServiceManager.getService("fingerprint"));
        if (ifp == null) {
            return pkg;
        }
        try {
            pkg = ifp.getAuthenticatedPackage();
            Log.d("FingerprintUiHelper", "fp in used: " + pkg);
            return pkg;
        } catch (RemoteException e) {
            Log.w("FingerprintUiHelper", "getAuthenticatedPackage , " + e);
            return pkg;
        }
    }

    private void updateFingerprintIconVisibility() {
        Log.d("FingerprintUiHelper", "updateFingerprintIconVisibility: " + isListening());
        this.mIcon.setVisibility(isListening() ? 0 : 4);
    }

    public void onAuthenticationError(int errMsgId, CharSequence errString) {
        Log.d("FingerprintUiHelper", "onAuthenticationError: " + errMsgId + ", " + errString);
        boolean force = false;
        if (errMsgId == 7) {
            force = true;
            stopListening();
        }
        if (errMsgId != 5) {
            showError(errString, force);
        }
        updateFingerprintIconVisibility();
    }

    public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
        Log.d("FingerprintUiHelper", "onAuthenticationHelp: " + helpString);
        showError(helpString, false);
    }

    public void onAuthenticationFailed() {
        showError(this.mIcon.getResources().getString(2131427685), false);
    }

    public void onAuthenticationSucceeded(AuthenticationResult result) {
        this.mAuthenticateSuccess = true;
        this.mCallback.onAuthenticated();
    }

    private void showError(CharSequence error, boolean force) {
        if (isListening() || force) {
            showError(error);
        }
    }

    private void showError(CharSequence error) {
        if (this.mHeaderText != null) {
            this.mHeaderText.setVisibility(4);
        }
        this.mErrorTextView.setText(error);
        this.mErrorTextView.removeCallbacks(this.mResetErrorTextRunnable);
        this.mErrorTextView.postDelayed(this.mResetErrorTextRunnable, ERROR_TIMEOUT);
    }

    public void setHeaderTextView(TextView headerText) {
        this.mHeaderText = headerText;
    }

    public void reportStrongAuthenSuccess() {
        if (this.mFingerprintManager != null) {
            this.mFingerprintManager.resetTimeout(null);
        }
    }

    public void setLockOut(boolean lockOut) {
        this.mLockOut = lockOut;
    }
}
