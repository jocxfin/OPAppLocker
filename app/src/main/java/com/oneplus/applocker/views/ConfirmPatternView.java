package com.oneplus.applocker.views;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import com.android.internal.widget.LinearLayoutWithDefaultTouchRecepient;
import com.android.internal.widget.LockPatternView;

public class ConfirmPatternView extends LinearLayoutWithDefaultTouchRecepient {
    private static final boolean DEBUG = Build.DEBUG_ONEPLUS;
    private static final String TAG = "ConfirmPatternView";
    private final float PORTRAIT_INNER_PANEL_RATIO = 0.8f;
    private int mFingerprintIconSize;
    private LinearLayout mInnerPanel;
    private boolean mIsInMultiWindowMode = DEBUG;
    private boolean mIsLandscape = DEBUG;
    private LockPatternView mLockPattern;
    private int mPatternHeight;
    private int mPatternMarginStart;
    private int mPatternMarginTop;
    private ImageView mPkgIcon;
    private int mPkgIconSize;
    private int mReducedPkgIconHeight;
    private View mTextGroup;
    private int mTextGroupMarginTop;

    public ConfirmPatternView(Context context) {
        super(context);
    }

    public ConfirmPatternView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources res = this.mContext.getResources();
        this.mInnerPanel = (LinearLayout) LayoutInflater.from(this.mContext).inflate(2130903045, null);
        addView(this.mInnerPanel);
        this.mPkgIcon = new ImageView(this.mContext);
        this.mPkgIcon.setId(2131623936);
        this.mPkgIcon.setImageResource(2130837504);
        addView(this.mPkgIcon);
        this.mPkgIconSize = res.getDimensionPixelSize(2131492907);
        this.mPatternHeight = res.getDimensionPixelSize(2131492927);
        this.mPatternMarginTop = res.getDimensionPixelSize(2131492930);
        this.mPatternMarginStart = res.getDimensionPixelSize(2131492928);
        this.mTextGroupMarginTop = res.getDimensionPixelSize(2131492913);
        this.mFingerprintIconSize = res.getDimensionPixelSize(2131492912);
        this.mTextGroup = this.mInnerPanel.findViewById(2131623945);
        this.mLockPattern = (LockPatternView) this.mInnerPanel.findViewById(2131623937);
    }

    public void setArgs(boolean isInMultiWindowMode, boolean isLandscape) {
        if (this.mIsInMultiWindowMode != isInMultiWindowMode || this.mIsLandscape != isLandscape) {
            this.mIsInMultiWindowMode = isInMultiWindowMode;
            this.mIsLandscape = isLandscape;
            boolean portraitNormal = (this.mIsInMultiWindowMode || this.mIsLandscape) ? DEBUG : true;
            this.mPkgIcon.setVisibility(portraitNormal ? 0 : 8);
            this.mInnerPanel.setGravity(portraitNormal ? 17 : 80);
            requestLayout();
        }
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        boolean showAppIcon = DEBUG;
        super.onLayout(changed, l, t, r, b);
        Log.d(TAG, "onLayout: landscape = " + this.mIsLandscape + ", " + l + ", " + t + ", " + r + ", " + b);
        if (this.mPkgIcon.getVisibility() == 0) {
            showAppIcon = true;
        }
        int top = showAppIcon ? (b - this.mInnerPanel.getMeasuredHeight()) - this.mReducedPkgIconHeight : t;
        this.mInnerPanel.layout(l, top, r, b);
        if (showAppIcon) {
            int w = this.mPkgIcon.getMeasuredWidth();
            int h = this.mPkgIcon.getMeasuredHeight();
            int left = (r - w) / 2;
            int icon_top = (top - h) / 2;
            this.mPkgIcon.layout(left, icon_top, left + w, icon_top + h);
        }
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        boolean showAppIcon = this.mPkgIcon.getVisibility() == 0 ? true : DEBUG;
        Log.d(TAG, "onMeasure: landscape = " + this.mIsLandscape + ", (" + width + "," + height + ")");
        int innerSpace = showAppIcon ? (int) (((float) height) * 0.8f) : height - this.mPatternMarginTop;
        if (showAppIcon) {
            this.mPkgIcon.measure(exactly(this.mPkgIconSize), exactly(this.mPkgIconSize));
        }
        int inner = (((this.mPatternHeight + this.mPatternMarginTop) + this.mTextGroup.getMeasuredHeight()) + this.mTextGroupMarginTop) + this.mFingerprintIconSize;
        Log.d(TAG, "inner = " + inner + ", innerSpace = " + innerSpace);
        LayoutParams lp;
        if (inner > innerSpace) {
            int diff = inner - innerSpace;
            int accu = 0;
            int icon_margin = showAppIcon ? (height - innerSpace) - this.mPkgIconSize : 0;
            int flex = ((this.mTextGroupMarginTop + icon_margin) + this.mPatternMarginTop) + this.mPatternHeight;
            if (showAppIcon) {
                this.mReducedPkgIconHeight = (diff * icon_margin) / flex;
                accu = this.mReducedPkgIconHeight + 0;
            }
            int reduced = (this.mTextGroupMarginTop * diff) / flex;
            ((LayoutParams) this.mTextGroup.getLayoutParams()).topMargin = this.mTextGroupMarginTop - reduced;
            accu += reduced;
            reduced = (this.mPatternMarginTop * diff) / flex;
            lp = (LayoutParams) this.mLockPattern.getLayoutParams();
            lp.topMargin = this.mPatternMarginTop - reduced;
            lp.height = this.mPatternHeight - (diff - (accu + reduced));
        } else {
            ((LayoutParams) this.mTextGroup.getLayoutParams()).topMargin = this.mTextGroupMarginTop;
            lp = (LayoutParams) this.mLockPattern.getLayoutParams();
            lp.topMargin = this.mPatternMarginTop;
            lp.height = this.mPatternHeight;
        }
        this.mInnerPanel.measure(exactly(width), exactly(innerSpace));
    }

    private int exactly(int size) {
        return MeasureSpec.makeMeasureSpec(size, 1073741824);
    }
}
