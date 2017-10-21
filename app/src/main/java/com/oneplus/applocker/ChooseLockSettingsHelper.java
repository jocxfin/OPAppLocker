package com.oneplus.applocker;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Fragment;
import android.content.Intent;
import com.android.internal.widget.LockPatternUtils;

public final class ChooseLockSettingsHelper {
    public static final String EXTRA_KEY_CHALLENGE = "challenge";
    public static final String EXTRA_KEY_CHALLENGE_TOKEN = "hw_auth_token";
    public static final String EXTRA_KEY_FOR_FINGERPRINT = "for_fingerprint";
    public static final String EXTRA_KEY_HAS_CHALLENGE = "has_challenge";
    public static final String EXTRA_KEY_PASSWORD = "password";
    public static final String EXTRA_KEY_TYPE = "type";
    public static final String TAG = "ChooseLockSettingsHelper";
    private Activity mActivity;
    private Fragment mFragment;
    private LockPatternUtils mLockPatternUtils;

    public ChooseLockSettingsHelper(Activity activity) {
        this.mActivity = activity;
        this.mLockPatternUtils = new LockPatternUtils(activity);
    }

    public ChooseLockSettingsHelper(Activity activity, Fragment fragment) {
        this(activity);
        this.mFragment = fragment;
    }

    public LockPatternUtils utils() {
        return this.mLockPatternUtils;
    }

    boolean launchConfirmationActivity(int request, CharSequence title) {
        return launchConfirmationActivity(request, title, null, null, false, false);
    }

    boolean launchConfirmationActivity(int request, CharSequence title, boolean returnCredentials) {
        return launchConfirmationActivity(request, title, null, null, returnCredentials, false);
    }

    boolean launchConfirmationActivity(int request, CharSequence title, CharSequence header, CharSequence description, boolean returnCredentials, boolean external) {
        return launchConfirmationActivity(request, title, header, description, returnCredentials, external, false, 0, false, "", 0, 0);
    }

    boolean launchConfirmationActivity(int request, CharSequence title, CharSequence header, CharSequence description, boolean returnCredentials, boolean external, boolean isFromAppLocker, String packageComponent, int blockApp, int uid) {
        return launchConfirmationActivity(request, title, header, description, returnCredentials, external, false, 0, isFromAppLocker, packageComponent, blockApp, uid);
    }

    public boolean launchConfirmationActivity(int request, CharSequence title, CharSequence header, CharSequence description, long challenge) {
        return launchConfirmationActivity(request, title, header, description, false, false, true, challenge, false, "", 0, 0);
    }

    private boolean launchConfirmationActivity(int request, CharSequence title, CharSequence header, CharSequence description, boolean returnCredentials, boolean external, boolean hasChallenge, long challenge, boolean isFromAppLocker, String packageComponent, int blockApp, int uid) {
        switch (this.mLockPatternUtils.getKeyguardStoredPasswordQuality(Utils.getEffectiveUserId(this.mActivity))) {
            case 65536:
                return launchConfirmationActivity(request, title, header, description, ApplockerConfirmPattern.class, external, hasChallenge, challenge, isFromAppLocker, packageComponent, blockApp, uid);
            case 131072:
            case 196608:
                return launchConfirmationActivity(request, title, header, description, ApplockerConfirmPassword.class, external, hasChallenge, challenge, isFromAppLocker, packageComponent, blockApp, uid);
            case 262144:
            case 327680:
            case 393216:
                return launchConfirmationActivity(request, title, header, description, ApplockerConfirmComplexPassword.class, external, hasChallenge, challenge, isFromAppLocker, packageComponent, blockApp, uid);
            default:
                return false;
        }
    }

    private boolean launchConfirmationActivity(int request, CharSequence title, CharSequence header, CharSequence message, Class<?> activityClass, boolean external, boolean hasChallenge, long challenge, boolean isFromAppLocker, String packageComponent, int blockApp, int uid) {
        Intent intent = new Intent(this.mActivity, activityClass);
        intent.putExtra(ApplockerConfirmActivity.APP_LOCKER, isFromAppLocker);
        intent.putExtra(ApplockerConfirmActivity.APP_LOCKER_COMPONENT, packageComponent);
        intent.putExtra(ApplockerConfirmActivity.APP_LOCKER_BLOCKING_APP, blockApp);
        intent.putExtra(ApplockerConfirmActivity.APP_LOCKER_BLOCKING_UID, uid);
        intent.setFlags(536870912);
        intent.setFlags(131072);
        intent.addFlags(33554432);
        this.mActivity.startActivity(intent, ActivityOptions.makeClipRevealAnimation(this.mActivity.findViewById(2131623943), 540, 960, 0, 0).toBundle());
        return true;
    }

    public int lockMode() {
        switch (this.mLockPatternUtils.getKeyguardStoredPasswordQuality(Utils.getEffectiveUserId(this.mActivity))) {
            case 65536:
                return 1;
            case 131072:
                return 2;
            default:
                return 0;
        }
    }

    public boolean isPasswordLockMode() {
        switch (this.mLockPatternUtils.getKeyguardStoredPasswordQuality(Utils.getEffectiveUserId(this.mActivity))) {
            case 131072:
            case 196608:
            case 262144:
            case 327680:
            case 393216:
                return true;
            default:
                return false;
        }
    }

    public boolean launchConfirmationActivityExt(int request, CharSequence message, CharSequence details) {
        return launchConfirmationActivity(request, message, false);
    }
}
