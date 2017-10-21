package com.oneplus.applocker;

import android.content.Context;
import android.graphics.Color;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

public final class Utils {
    private static final String TAG = "Utils";

    public static int getEffectiveUserId(Context context) {
        UserManager um = UserManager.get(context);
        if (um != null) {
            return um.getCredentialOwnerProfile(UserHandle.myUserId());
        }
        Log.e(TAG, "Unable to acquire UserManager");
        return UserHandle.myUserId();
    }

    private static int compositeColorComponent(int c1, int a1, int c2, int a2, int a) {
        if (a == 0) {
            return 0;
        }
        return ((((c2 * 255) * a2) + ((c1 * a1) * (255 - a2))) / a) / 255;
    }

    public static int compositeColor(int argb1, int argb2) {
        int a1 = Color.alpha(argb1);
        int a2 = Color.alpha(argb2);
        int a = 255 - (((255 - a2) * (255 - a1)) / 255);
        return Color.argb(a, compositeColorComponent(Color.red(argb1), a1, Color.red(argb2), a2, a), compositeColorComponent(Color.green(argb1), a1, Color.green(argb2), a2, a), compositeColorComponent(Color.blue(argb1), a1, Color.blue(argb2), a2, a));
    }
}
