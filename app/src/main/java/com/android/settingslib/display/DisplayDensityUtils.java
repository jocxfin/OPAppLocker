package com.android.settingslib.display;

import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManagerGlobal;
import com.android.settingslib.R;
import java.util.Arrays;

public class DisplayDensityUtils {
    private static final int DEFINDED_DP = 420;
    private static final String LOG_TAG = "DisplayDensityUtils";
    private static final float MAX_SCALE = 1.5f;
    private static final int MIN_DIMENSION_DP = 320;
    private static final float MIN_SCALE = 0.85f;
    private static final float MIN_SCALE_INTERVAL = 0.09f;
    private static final int[] SUMMARIES_LARGER = new int[]{R.string.screen_zoom_summary_large, R.string.screen_zoom_summary_very_large, R.string.screen_zoom_summary_extremely_large};
    private static final int[] SUMMARIES_SMALLER = new int[]{R.string.screen_zoom_summary_small};
    private static final int SUMMARY_CUSTOM = R.string.screen_zoom_summary_custom;
    public static final int SUMMARY_DEFAULT = R.string.screen_zoom_summary_default;
    private final int mCurrentIndex;
    private final int mDefaultDensity;
    private final String[] mEntries;
    private final int[] mValues;

    final /* synthetic */ class -void_clearForcedDisplayDensity_int_displayId_LambdaImpl0 implements Runnable {
        private /* synthetic */ int val$displayId;
        private /* synthetic */ int val$userId;

        public /* synthetic */ -void_clearForcedDisplayDensity_int_displayId_LambdaImpl0(int i, int i2) {
            this.val$displayId = i;
            this.val$userId = i2;
        }

        public void run() {
            DisplayDensityUtils.-com_android_settingslib_display_DisplayDensityUtils_lambda$1(this.val$displayId, this.val$userId);
        }
    }

    final /* synthetic */ class -void_setForcedDisplayDensity_int_displayId_int_density_LambdaImpl0 implements Runnable {
        private /* synthetic */ int val$density;
        private /* synthetic */ int val$displayId;
        private /* synthetic */ int val$userId;

        public /* synthetic */ -void_setForcedDisplayDensity_int_displayId_int_density_LambdaImpl0(int i, int i2, int i3) {
            this.val$displayId = i;
            this.val$density = i2;
            this.val$userId = i3;
        }

        public void run() {
            DisplayDensityUtils.-com_android_settingslib_display_DisplayDensityUtils_lambda$2(this.val$displayId, this.val$density, this.val$userId);
        }
    }

    public DisplayDensityUtils(Context context) {
        int defaultDensity = getDefaultDisplayDensity(0);
        if (defaultDensity <= 0) {
            this.mEntries = null;
            this.mValues = null;
            this.mDefaultDensity = 0;
            this.mCurrentIndex = -1;
            return;
        }
        int i;
        int displayIndex;
        Resources res = context.getResources();
        DisplayMetrics metrics = new DisplayMetrics();
        context.getDisplay().getRealMetrics(metrics);
        int currentDensity = metrics.densityDpi;
        int currentDensityIndex = -1;
        float maxScale = Math.min(MAX_SCALE, ((float) ((Math.min(metrics.widthPixels, metrics.heightPixels) * 160) / MIN_DIMENSION_DP)) / ((float) defaultDensity));
        int numLarger = SUMMARIES_LARGER.length;
        int numSmaller = SUMMARIES_SMALLER.length;
        String[] entries = new String[((numSmaller + 1) + numLarger)];
        int[] values = new int[entries.length];
        int curIndex = 0;
        if (numSmaller > 0) {
            float interval = 0.14999998f / ((float) numSmaller);
            for (i = 0; i < numSmaller; i++) {
                int density;
                density = 420 - ((numSmaller - i) * 40);
                if (currentDensity == density) {
                    currentDensityIndex = curIndex;
                }
                entries[curIndex] = res.getString(SUMMARIES_SMALLER[i]);
                values[curIndex] = density;
                curIndex++;
            }
        }
        if (currentDensity == defaultDensity) {
            currentDensityIndex = curIndex;
        }
        values[curIndex] = defaultDensity;
        entries[curIndex] = res.getString(SUMMARY_DEFAULT);
        curIndex++;
        if (numLarger > 0) {
            interval = (maxScale - 1.0f) / ((float) numLarger);
            for (i = 0; i < numLarger; i++) {
                density = 0;
                if (i == 0) {
                    density = 480;
                } else if (i == 1) {
                    density = 500;
                } else if (i == 2) {
                    density = 540;
                }
                if (currentDensity == density) {
                    currentDensityIndex = curIndex;
                }
                values[curIndex] = density;
                entries[curIndex] = res.getString(SUMMARIES_LARGER[i]);
                curIndex++;
            }
        }
        if (currentDensityIndex >= 0) {
            displayIndex = currentDensityIndex;
        } else {
            int newLength = values.length + 1;
            values = Arrays.copyOf(values, newLength);
            values[curIndex] = currentDensity;
            entries = (String[]) Arrays.copyOf(entries, newLength);
            entries[curIndex] = res.getString(SUMMARY_CUSTOM, new Object[]{Integer.valueOf(currentDensity)});
            displayIndex = curIndex;
        }
        this.mDefaultDensity = defaultDensity;
        this.mCurrentIndex = displayIndex;
        this.mEntries = entries;
        this.mValues = values;
    }

    public String[] getEntries() {
        return this.mEntries;
    }

    public int[] getValues() {
        return this.mValues;
    }

    public int getCurrentIndex() {
        return this.mCurrentIndex;
    }

    public int getDefaultDensity() {
        return this.mDefaultDensity;
    }

    private static int getDefaultDisplayDensity(int displayId) {
        try {
            return WindowManagerGlobal.getWindowManagerService().getInitialDisplayDensity(displayId);
        } catch (RemoteException e) {
            return -1;
        }
    }

    public static void clearForcedDisplayDensity(int displayId) {
        AsyncTask.execute(new -void_clearForcedDisplayDensity_int_displayId_LambdaImpl0(displayId, UserHandle.myUserId()));
    }

    static /* synthetic */ void -com_android_settingslib_display_DisplayDensityUtils_lambda$1(int displayId, int userId) {
        try {
            WindowManagerGlobal.getWindowManagerService().clearForcedDisplayDensityForUser(displayId, userId);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "Unable to clear forced display density setting");
        }
    }

    public static void setForcedDisplayDensity(int displayId, int density) {
        AsyncTask.execute(new -void_setForcedDisplayDensity_int_displayId_int_density_LambdaImpl0(displayId, density, UserHandle.myUserId()));
    }

    static /* synthetic */ void -com_android_settingslib_display_DisplayDensityUtils_lambda$2(int displayId, int density, int userId) {
        try {
            WindowManagerGlobal.getWindowManagerService().setForcedDisplayDensityForUser(displayId, density, userId);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "Unable to save forced display density setting");
        }
    }
}
