package com.oneplus.applocker.views;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout.LayoutParams;
import com.android.internal.widget.LinearLayoutWithDefaultTouchRecepient;

public class ConfirmPasswordView extends LinearLayoutWithDefaultTouchRecepient {
    private static final boolean DEBUG = Build.DEBUG_ONEPLUS;
    private static final float MAX_REDUCED_RATE = 0.2f;
    private static final float PORTRAIT_INNER_PANEL_RATIO = 0.8f;
    private static final String TAG = "ConfirmPatternView";
    private View mFingerprintIcon;
    private int mFingerprintIconSize;
    private View mInnerPanel;
    private int mInnerPanelHeight;
    private ViewGroup mKeyPad;
    private int mKeyPadHeight;
    private int mKeyPadMarginBottom;
    private int mKeyPadMarginTop;
    private View mPasswordPin;
    private int mPasswordPinHeight;
    private int mPasswordPinMarginTop;
    private View mPkgIcon;
    private int mPkgIconMarginBottom;
    private int mPkgIconMarginTop;
    private int mPkgIconSize;
    private int mStatusBarHeight;
    private View mTextGroup;
    private int mTextGroupMarginTop;
    private int mWindowHeight;
    private WindowManager mWindowManager;
    private int mWindowWidth;

    public ConfirmPasswordView(Context context) {
        super(context);
    }

    public ConfirmPasswordView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mWindowManager = (WindowManager) this.mContext.getSystemService("window");
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            this.mStatusBarHeight = getResources().getDimensionPixelSize(resId);
        }
        this.mInnerPanel = LayoutInflater.from(this.mContext).inflate(2130903044, null);
        updateWindowSize();
        addView(this.mInnerPanel);
        Resources res = this.mContext.getResources();
        this.mPkgIconSize = res.getDimensionPixelSize(2131492907);
        this.mTextGroupMarginTop = res.getDimensionPixelSize(2131492913);
        this.mFingerprintIconSize = res.getDimensionPixelSize(2131492912);
        this.mKeyPadHeight = res.getDimensionPixelSize(2131492920) * 4;
        this.mKeyPadMarginTop = res.getDimensionPixelSize(2131492924);
        this.mKeyPadMarginBottom = res.getDimensionPixelSize(2131492925);
        this.mPasswordPinHeight = res.getDimensionPixelSize(2131492916);
        this.mPasswordPinMarginTop = res.getDimensionPixelSize(2131492917);
        this.mPkgIconMarginTop = res.getDimensionPixelSize(2131492909);
        this.mPkgIconMarginBottom = res.getDimensionPixelSize(2131492910);
        this.mPkgIcon = this.mInnerPanel.findViewById(2131623936);
        this.mFingerprintIcon = this.mInnerPanel.findViewById(2131623939);
        this.mTextGroup = this.mInnerPanel.findViewById(2131623945);
        this.mKeyPad = (ViewGroup) this.mInnerPanel.findViewById(2131623947);
        this.mPasswordPin = this.mInnerPanel.findViewById(2131623946);
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        Log.d(TAG, "onMeasure");
        updateWindowSize();
        int innerPanelH = ((((((this.mFingerprintIconSize + this.mTextGroupMarginTop) + this.mTextGroup.getMeasuredHeight()) + this.mPasswordPinMarginTop) + this.mPasswordPinHeight) + this.mKeyPadMarginTop) + this.mKeyPadHeight) + this.mKeyPadMarginBottom;
        LayoutParams lp;
        if (innerPanelH < this.mInnerPanelHeight) {
            int margin = ((this.mWindowHeight - this.mInnerPanelHeight) - this.mPkgIconSize) / 2;
            lp = (LayoutParams) this.mPkgIcon.getLayoutParams();
            lp.topMargin = margin;
            lp.bottomMargin = margin;
            ((LayoutParams) this.mFingerprintIcon.getLayoutParams()).topMargin = (this.mInnerPanelHeight - innerPanelH) / 2;
        } else {
            int flex = (((this.mTextGroupMarginTop + this.mPasswordPinMarginTop) + this.mKeyPadMarginTop) + this.mKeyPadHeight) + this.mKeyPadMarginBottom;
            int diff = (int) Math.min(((float) flex) * MAX_REDUCED_RATE, (float) (innerPanelH - this.mInnerPanelHeight));
            lp = (LayoutParams) this.mTextGroup.getLayoutParams();
            lp.topMargin = this.mTextGroupMarginTop - ((this.mTextGroupMarginTop * diff) / flex);
            lp = (LayoutParams) this.mPasswordPin.getLayoutParams();
            lp.topMargin = this.mPasswordPinMarginTop - ((this.mPasswordPinMarginTop * diff) / flex);
            lp = (LayoutParams) this.mKeyPad.getLayoutParams();
            lp.topMargin = this.mKeyPadMarginTop - ((this.mKeyPadMarginTop * diff) / flex);
            lp.bottomMargin = this.mKeyPadMarginBottom - ((this.mKeyPadMarginBottom * diff) / flex);
            lp = (LayoutParams) this.mKeyPad.getLayoutParams();
            lp.height = this.mKeyPadHeight - ((this.mKeyPadHeight * diff) / flex);
            lp = (LayoutParams) this.mPkgIcon.getLayoutParams();
            lp.topMargin = this.mPkgIconMarginTop - ((this.mPkgIconMarginTop * diff) / flex);
            lp.bottomMargin = this.mPkgIconMarginBottom - ((this.mPkgIconMarginBottom * diff) / flex);
        }
        ((LayoutParams) this.mKeyPad.getLayoutParams()).width = this.mWindowWidth;
    }

    private void updateWindowSize() {
        DisplayMetrics displaymetrics = this.mContext.getResources().getDisplayMetrics();
        this.mWindowWidth = displaymetrics.widthPixels;
        this.mWindowHeight = displaymetrics.heightPixels - this.mStatusBarHeight;
        Log.d(TAG, "updateWindowSize: (" + this.mWindowWidth + ", " + this.mWindowHeight + ")");
        this.mInnerPanelHeight = (int) (((float) this.mWindowHeight) * PORTRAIT_INNER_PANEL_RATIO);
    }
}
