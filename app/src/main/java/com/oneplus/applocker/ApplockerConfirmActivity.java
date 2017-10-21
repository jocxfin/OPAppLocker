package com.oneplus.applocker;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.Settings.Secure;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.Palette.Swatch;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import com.android.internal.widget.LockPatternUtils;
import com.android.settingslib.animation.AppearAnimationCreator;
import com.oneplus.applocker.CredentialCheckResultTracker.Listener;
import com.oneplus.applocker.FingerprintUiHelper.Callback;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ApplockerConfirmActivity extends Activity implements Listener, Callback, AppearAnimationCreator<Object> {
    private static final String ACTION_DISMISS_APPLOCKER = "com.android.settings.action.DISMISS_APPLOCKER";
    public static final String APP_LOCKER = "OP_APP_LOCKER";
    public static final String APP_LOCKER_BLOCKING_APP = "OP_APP_LOCKER_BLOCKING_APP";
    public static final String APP_LOCKER_BLOCKING_UID = "OP_APP_LOCKER_BLOCKING_UID";
    public static final String APP_LOCKER_COMPONENT = "OP_APP_LOCKER_COMPONENT";
    public static final String APP_LOCKER_MODE = "OP_APP_LOCKER_MODE";
    public static final String APP_LOCKER_PACKAGE = "OP_APP_LOCKER_PACKAGE";
    public static final String COLUMN_COMPONENT = "componentName";
    public static final String COLUMN_ICON = "icon";
    public static final long ERROR_MESSAGE_TIMEOUT = 3000;
    public static final int FAILED_ATTEMPTS_BEFORE_TIMEOUT = 5;
    public static final String KEY_NUM_WRONG_CONFIRM_ATTEMPTS = "confirm_lock_password_fragment.key_num_wrong_confirm_attempts";
    public static final int LOCKOUT_TIME_OUT = 30000;
    private static final int NONUI_MSG_GET_BITMAP = 1;
    public static final String TAG = "ApplockerActivity";
    private static final int UI_MSG_GET_COLOR_AND_SETBACKGROUND = 4096;
    private static boolean mIsFromAppLocker;
    private final String KEY_BACKGROUND_COLOR = "background_color";
    private final String KEY_DISMISS_APPLOCKER = "applocker_package_name";
    private final String KEY_DISMISS_APPLOCKER_ALL = "applocker_dismiss_all";
    private final String KEY_PACKAGE_ICON = "package_icon";
    private final String KEY_SAVE_STATE = "save_state";
    private Activity mActivity;
    ActivityManager mAm;
    public int mBackgroundColor;
    public int mBackgroundMaskColor;
    private int mBlockingApp;
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            Log.d(ApplockerConfirmActivity.TAG, "onReceive: " + intent.getAction() + "focus = " + ApplockerConfirmActivity.this.mHasFocus);
            if (intent.getAction().equals(ApplockerConfirmActivity.ACTION_DISMISS_APPLOCKER)) {
                String packageName = intent.getStringExtra("applocker_package_name");
                boolean dismissAll = intent.getBooleanExtra("applocker_dismiss_all", false);
                Log.d(ApplockerConfirmActivity.TAG, "user turn off applocker: " + packageName + ", current Package = " + ApplockerConfirmActivity.this.mPackageName + ", dismiss all = " + dismissAll);
                if (dismissAll || (packageName != null && ApplockerConfirmActivity.this.mPackageName.equals(packageName))) {
                    ApplockerConfirmActivity.this.mActivity.setResult(0);
                    ApplockerConfirmActivity.this.finish();
                }
            } else if (ApplockerConfirmActivity.this.mHasFocus) {
                ApplockerConfirmActivity.this.registerFingerprint();
            }
        }
    };
    private String mComponent;
    public CredentialCheckResultTracker mCredentialCheckResultTracker;
    private String mCurrentHome = "";
    public int mEffectiveUserId;
    public FingerprintUiHelper mFingerprintHelper;
    public ImageView mFingerprintIcon;
    public Handler mHandler;
    private HandlerThread mHandlerThread;
    public boolean mHasFocus;
    protected boolean mIsInMultiWindowMode = false;
    protected boolean mIsLandscape = false;
    KeyguardManager mKm;
    protected int mLockMode;
    public LockPatternUtils mLockPatternUtils;
    private Handler mNonUIHandler;
    public int mNumWrongConfirmAttempts;
    public ImageView mPackageIcon;
    private String mPackageName = "";
    public int mPackageUId;
    public AsyncTask<?, ?, ?> mPendingLockCheck;

    private android.graphics.Bitmap getPackageIcon() {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.JadxRuntimeException: Can't find block by offset: 0x00c1 in list []
	at jadx.core.utils.BlockUtils.getBlockByOffset(BlockUtils.java:42)
	at jadx.core.dex.instructions.IfNode.initBlocks(IfNode.java:60)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.initBlocksInIfNodes(BlockFinish.java:48)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.visit(BlockFinish.java:33)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:31)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:17)
	at jadx.core.ProcessClass.process(ProcessClass.java:37)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
*/
        /*
        r12 = this;
        r5 = 1;
        r4 = 0;
        r11 = 0;
        r0 = new java.lang.StringBuilder;
        r0.<init>();
        r3 = "content://";
        r0 = r0.append(r3);
        r3 = r12.mCurrentHome;
        r0 = r0.append(r3);
        r3 = ".IconProvider/data";
        r0 = r0.append(r3);
        r0 = r0.toString();
        r1 = android.net.Uri.parse(r0);
        r2 = new java.lang.String[r5];
        r0 = "icon";
        r2[r4] = r0;
        r7 = 0;
        r0 = "ApplockerActivity";
        r3 = "getPackageIcon";
        android.util.Log.d(r0, r3);
        r8 = 0;
        r0 = r12.getContentResolver();	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r3 = "componentName=?";	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r4 = 1;	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r4 = new java.lang.String[r4];	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r5 = r12.mComponent;	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r10 = 0;	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r4[r10] = r5;	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r5 = 0;	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r8 = r0.query(r1, r2, r3, r4, r5);	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        if (r8 == 0) goto L_0x0079;	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
    L_0x004c:
        r0 = r8.getCount();	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        if (r0 != 0) goto L_0x0079;	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
    L_0x0052:
        r0 = r12.getContentResolver();	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r3 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r3.<init>();	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r4 = "componentName like '";	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r3 = r3.append(r4);	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r4 = r12.mPackageName;	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r3 = r3.append(r4);	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r4 = "%'";	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r3 = r3.append(r4);	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r3 = r3.toString();	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r4 = 0;	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r5 = 0;	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r8 = r0.query(r1, r2, r3, r4, r5);	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
    L_0x0079:
        if (r8 != 0) goto L_0x0081;
    L_0x007b:
        if (r8 == 0) goto L_0x0080;
    L_0x007d:
        r8.close();
    L_0x0080:
        return r11;
    L_0x0081:
        r8.moveToNext();	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r0 = "icon";	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r0 = r8.getColumnIndex(r0);	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r6 = r8.getBlob(r0);	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        if (r6 == 0) goto L_0x0097;	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
    L_0x0091:
        r0 = r6.length;	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r3 = 0;	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r7 = android.graphics.BitmapFactory.decodeByteArray(r6, r3, r0);	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
    L_0x0097:
        if (r8 == 0) goto L_0x009c;
    L_0x0099:
        r8.close();
    L_0x009c:
        return r7;
    L_0x009d:
        r9 = move-exception;
        r0 = "ApplockerActivity";	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r3 = new java.lang.StringBuilder;	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r3.<init>();	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r4 = "getPackageIcon: Exception e = ";	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r3 = r3.append(r4);	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r4 = r9.toString();	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r3 = r3.append(r4);	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        r3 = r3.toString();	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        android.util.Log.d(r0, r3);	 Catch:{ Exception -> 0x009d, all -> 0x00c2 }
        if (r8 == 0) goto L_0x00c1;
    L_0x00be:
        r8.close();
    L_0x00c1:
        return r11;
    L_0x00c2:
        r0 = move-exception;
        if (r8 == 0) goto L_0x00c8;
    L_0x00c5:
        r8.close();
    L_0x00c8:
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.oneplus.applocker.ApplockerConfirmActivity.getPackageIcon():android.graphics.Bitmap");
    }

    protected void onCreate(Bundle savedInstanceState) {
        boolean z;
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            Bundle b = savedInstanceState.getBundle("save_state");
            if (b != null) {
                this.mBackgroundColor = b.getInt("background_color");
            }
        }
        startHandlderThread();
        this.mActivity = this;
        mIsFromAppLocker = true;
        Intent intent = getIntent();
        this.mBlockingApp = intent.getIntExtra(APP_LOCKER_BLOCKING_APP, 0);
        this.mComponent = intent.getStringExtra(APP_LOCKER_COMPONENT);
        this.mPackageUId = intent.getIntExtra(APP_LOCKER_BLOCKING_UID, 0);
        this.mLockMode = intent.getIntExtra(APP_LOCKER_MODE, 0);
        this.mCredentialCheckResultTracker = new CredentialCheckResultTracker();
        this.mAm = (ActivityManager) getSystemService("activity");
        this.mKm = (KeyguardManager) getSystemService("keyguard");
        if (this.mComponent != null) {
            this.mPackageName = this.mComponent.split("/")[0];
        }
        this.mBackgroundMaskColor = getResources().getColor(2131230733, getTheme());
        getWindow().setStatusBarColor(0);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.SCREEN_ON");
        intentFilter.addAction(ACTION_DISMISS_APPLOCKER);
        registerReceiver(this.mBroadcastReceiver, intentFilter);
        Display display = getWindowManager().getDefaultDisplay();
        int rotation = display.getRotation();
        Point physicalSize = new Point();
        Point windowSize = new Point();
        display.getRealSize(physicalSize);
        display.getSize(windowSize);
        if ((physicalSize.x / 3) * 2 >= windowSize.x || (physicalSize.y / 3) * 2 >= windowSize.y) {
            this.mIsInMultiWindowMode = true;
        } else {
            this.mIsInMultiWindowMode = false;
        }
        if (rotation == 1 || rotation == 3) {
            z = true;
        } else {
            z = false;
        }
        this.mIsLandscape = z;
    }

    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle b = new Bundle();
        b.putInt("background_color", this.mBackgroundColor);
        outState.putBundle("save_state", b);
    }

    public void preSetBackground() {
        if (this.mBackgroundColor != 0) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Utils.compositeColor(this.mBackgroundColor, this.mBackgroundMaskColor)));
        }
    }

    protected void onResume() {
        super.onResume();
        shouldFinishSelf();
        View innerView = findViewById(2131623938);
        if (innerView != null) {
            Log.d(TAG, "innner view(" + innerView.getMeasuredWidth() + ", " + innerView.getMeasuredHeight() + ", " + innerView.getVisibility() + ")");
        }
    }

    private void shouldFinishSelf() {
        if (this.mLockPatternUtils.getKeyguardStoredPasswordQuality(this.mEffectiveUserId) == 0) {
            Log.d(TAG, "shouldFinishSelf: no security");
            setResult(0);
            finish();
        }
        try {
            if (!ActivityManagerNative.getDefault().isAppLocked(this.mPackageName, this.mPackageUId)) {
                Log.d(TAG, "shouldFinishSelf: the lock apps has been unlocked. Package = " + this.mPackageName + ", mPackageUId = " + this.mPackageUId);
                setResult(0);
                finish();
            }
        } catch (Exception e) {
            Log.d(TAG, "Exception e = " + e.toString());
        }
    }

    public void registerFingerprint() {
        try {
            if (this.mFingerprintHelper == null || !ActivityManagerNative.getDefault().isKeyguardDone()) {
                Log.d(TAG, "registerFingerprint: fail due to keyguard locked");
            } else {
                this.mFingerprintHelper.startListening();
            }
        } catch (Exception e) {
            Log.d(TAG, "Exception e = " + e.toString());
        }
    }

    private void startHandlderThread() {
        this.mHandlerThread = new HandlerThread("ThreadBackground");
        this.mHandlerThread.start();
        this.mNonUIHandler = new Handler(this.mHandlerThread.getLooper()) {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 1:
                        Log.d(ApplockerConfirmActivity.TAG, "NONUI_MSG_GET_BITMAP");
                        Bitmap b = ApplockerConfirmActivity.this.getBitmap();
                        msg = ApplockerConfirmActivity.this.mHandler.obtainMessage(4096);
                        msg.obj = b;
                        ApplockerConfirmActivity.this.mHandler.sendMessage(msg);
                        return;
                    default:
                        return;
                }
            }
        };
        this.mHandler = new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 4096:
                        Log.d(ApplockerConfirmActivity.TAG, "UI_MSG_GET_COLOR_AND_SETBACKGROUND");
                        ApplockerConfirmActivity.this.getColorAndSetBackground((Bitmap) msg.obj);
                        return;
                    default:
                        return;
                }
            }
        };
    }

    public void onAuthenticated() {
        if (this.mLockPatternUtils.getLockoutAttemptDeadline(this.mEffectiveUserId) == 0) {
            this.mCredentialCheckResultTracker.setResult(true, new Intent(), 0, this.mEffectiveUserId, true);
        }
    }

    public void onFingerprintIconVisibilityChanged(boolean visible) {
    }

    public void onCredentialChecked(boolean matched, Intent intent, int timeoutMs, int effectiveUserId, boolean inFingerprint) {
    }

    protected void onPause() {
        super.onPause();
        if (this.mCredentialCheckResultTracker != null) {
            this.mCredentialCheckResultTracker.clearResult();
            this.mCredentialCheckResultTracker.setListener(null);
        }
        if (this.mFingerprintHelper != null) {
            this.mFingerprintHelper.stopListening();
        }
        Secure.putIntForUser(getContentResolver(), KEY_NUM_WRONG_CONFIRM_ATTEMPTS, this.mNumWrongConfirmAttempts, ActivityManager.getCurrentUser());
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.d(TAG, "onWindowFocusChanged: " + hasFocus);
        this.mHasFocus = hasFocus;
        if (this.mFingerprintHelper != null && hasFocus) {
            registerFingerprint();
        }
    }

    public void setBackgroundColor() {
        Log.d(TAG, "setBackgroundColor: component = " + this.mComponent);
        if (this.mComponent != null) {
            this.mNonUIHandler.sendMessage(this.mNonUIHandler.obtainMessage(1));
        }
    }

    private void getColorAndSetBackground(Bitmap bitmap) {
        Log.d(TAG, "getColorAndSetBackground: bitmap = " + bitmap + " mIcon = " + this.mPackageIcon);
        if (bitmap != null) {
            if (this.mPackageIcon != null) {
                this.mPackageIcon.setImageBitmap(bitmap);
            }
            Palette palette = Palette.from(bitmap).generate();
            List<Swatch> swatches = new ArrayList(palette.getSwatches());
            Collections.sort(swatches, new Comparator<Swatch>() {
                public int compare(Swatch lhs, Swatch rhs) {
                    return rhs.getPopulation() - lhs.getPopulation();
                }
            });
            Swatch swatch = !swatches.isEmpty() ? (Swatch) swatches.get(0) : palette.getVibrantSwatch();
            if (swatch != null) {
                this.mBackgroundColor = swatch.getRgb();
                getWindow().setBackgroundDrawable(new ColorDrawable(Utils.compositeColor(this.mBackgroundColor, this.mBackgroundMaskColor)));
            }
        }
    }

    private Bitmap getBitmap() {
        if (!isOPHomeExist()) {
            return getIconFromPM();
        }
        Bitmap bitmap = getPackageIcon();
        if (bitmap == null) {
            return getIconFromPM();
        }
        return bitmap;
    }

    private Bitmap getIconFromPM() {
        Log.d(TAG, "getIconFromPM: " + this.mPackageName);
        try {
            Drawable d = getPackageManager().getApplicationIcon(this.mPackageName);
            int w = d.getIntrinsicWidth();
            int h = d.getIntrinsicHeight();
            Bitmap bitmap = Bitmap.createBitmap(w, h, d.getOpacity() != -1 ? Config.ARGB_8888 : Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            d.setBounds(0, 0, w, h);
            d.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isOPHomeExist() {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.addCategory("android.intent.category.HOME");
        ResolveInfo res = getPackageManager().resolveActivity(intent, 128);
        Log.d(TAG, "isOPHomeExist: current home = " + res.activityInfo.packageName);
        this.mCurrentHome = "";
        if (res.activityInfo == null || (!"net.oneplus.launcher".equals(res.activityInfo.packageName) && !"net.oneplus.h2launcher".equals(res.activityInfo.packageName))) {
            return false;
        }
        this.mCurrentHome = res.activityInfo.packageName;
        return true;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode != 4) {
            return super.onKeyDown(keyCode, event);
        }
        Intent intent = new Intent();
        intent.putExtra(APP_LOCKER_BLOCKING_APP, this.mBlockingApp);
        setResult(0, intent);
        Log.d(TAG, "onKeyDown: back key pressed, hash code = " + this.mBlockingApp);
        finish();
        if (this.mBlockingApp == 0) {
            launchHome();
        }
        return true;
    }

    public void launchHome() {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.addCategory("android.intent.category.HOME");
        intent.setFlags(268435456);
        startActivity(intent);
    }

    public String getUnlockPackageName() {
        return this.mPackageName;
    }

    public int getBlockingApp() {
        return this.mBlockingApp;
    }

    public static boolean isFromAppLocker() {
        return mIsFromAppLocker;
    }

    protected void onDestroy() {
        super.onDestroy();
        mIsFromAppLocker = false;
        if (this.mHandler != null) {
            this.mHandler.removeMessages(4096);
        }
        if (this.mNonUIHandler != null) {
            this.mNonUIHandler.removeMessages(1);
        }
        if (this.mHandlerThread != null) {
            this.mHandlerThread.quitSafely();
        }
        unregisterReceiver(this.mBroadcastReceiver);
    }

    public boolean isFingerprintListening() {
        return this.mFingerprintHelper.isListening();
    }

    public void createAnimation(Object obj, long delay, long duration, float translationY, boolean appearing, Interpolator interpolator, Runnable finishListener) {
    }

    public void finishActivity(Intent intent) {
        intent.putExtra(APP_LOCKER_PACKAGE, getUnlockPackageName());
        intent.putExtra(APP_LOCKER_BLOCKING_APP, getBlockingApp());
        setResult(-1, intent);
        finish();
    }
}
