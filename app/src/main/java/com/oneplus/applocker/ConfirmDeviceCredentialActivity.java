package com.oneplus.applocker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class ConfirmDeviceCredentialActivity extends Activity {
    public static final String TAG = ConfirmDeviceCredentialActivity.class;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(2130903043);
        chooseLock(getIntent());
    }

    private void chooseLock(Intent intent) {
        if (!new ChooseLockSettingsHelper(this).launchConfirmationActivity(0, null, intent.getStringExtra("android.app.extra.TITLE"), intent.getStringExtra("android.app.extra.DESCRIPTION"), false, true, intent.getBooleanExtra(ApplockerConfirmActivity.APP_LOCKER, false), intent.getStringExtra(ApplockerConfirmActivity.APP_LOCKER_COMPONENT), intent.getIntExtra(ApplockerConfirmActivity.APP_LOCKER_BLOCKING_APP, 0), intent.getIntExtra(ApplockerConfirmActivity.APP_LOCKER_BLOCKING_UID, 0))) {
            Log.d(TAG, "No pattern, password or PIN set.");
            setResult(-1);
        }
        finish();
    }
}
