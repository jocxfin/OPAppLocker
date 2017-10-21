package com.android.settingslib.drawable;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;

public class UserIcons {
    private static final int[] USER_ICON_COLORS = new int[]{Color.parseColor("#FFCC6F4E"), Color.parseColor("#FFEB9413"), Color.parseColor("#FF8BC34A"), Color.parseColor("#FF673AB7"), Color.parseColor("#FF02BCD4"), Color.parseColor("#FFE91E63"), Color.parseColor("#FF9C27B0")};

    public static Bitmap convertToBitmap(Drawable icon) {
        if (icon == null) {
            return null;
        }
        int width = icon.getIntrinsicWidth();
        int height = icon.getIntrinsicHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        icon.setBounds(0, 0, width, height);
        icon.draw(canvas);
        return bitmap;
    }

    public static Drawable getDefaultUserIcon(int userId, boolean light) {
        int colorResId = light ? Color.parseColor("#FFFFFFFF") : Color.parseColor("#FF9E9E9E");
        if (userId != -10000) {
            if (userId == 0) {
                colorResId = Color.parseColor("#FF2196F3");
            } else {
                colorResId = USER_ICON_COLORS[userId % USER_ICON_COLORS.length];
            }
        }
        Drawable icon = Resources.getSystem().getDrawable(17302246, null).mutate();
        icon.setColorFilter(colorResId, Mode.SRC_IN);
        icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        return icon;
    }
}
