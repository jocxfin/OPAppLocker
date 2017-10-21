package com.oneplus.applocker;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Typeface;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.internal.widget.LockPatternUtils;

public class OPNumPadKeyForPin extends ViewGroup {
    private int mDigit;
    private TextView mDigitText;
    private boolean mEnableHaptics;
    private OnClickListener mListener;
    private PowerManager mPM;
    private Paint mPaint;
    private int mTextHeight;
    private float mTextSize;
    private OPPasswordTextViewForPin mTextView;
    private int mTextViewResId;

    public void userActivity() {
        this.mPM.userActivity(SystemClock.uptimeMillis(), false);
    }

    public OPNumPadKeyForPin(Context context) {
        this(context, null);
    }

    public OPNumPadKeyForPin(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OPNumPadKeyForPin(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mDigit = -1;
        this.mListener = new OnClickListener() {
            public void onClick(View thisView) {
                if (OPNumPadKeyForPin.this.mTextView == null && OPNumPadKeyForPin.this.mTextViewResId > 0) {
                    View v = OPNumPadKeyForPin.this.getRootView().findViewById(OPNumPadKeyForPin.this.mTextViewResId);
                    if (v != null && (v instanceof OPPasswordTextViewForPin)) {
                        OPNumPadKeyForPin.this.mTextView = (OPPasswordTextViewForPin) v;
                    }
                }
                if (OPNumPadKeyForPin.this.mTextView != null && OPNumPadKeyForPin.this.mTextView.isEnabled()) {
                    OPNumPadKeyForPin.this.mTextView.append(Character.forDigit(OPNumPadKeyForPin.this.mDigit, 10));
                }
                OPNumPadKeyForPin.this.userActivity();
                OPNumPadKeyForPin.this.doHapticKeyClick();
            }
        };
        setFocusable(true);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NumPadKey);
        try {
            this.mDigit = a.getInt(0, this.mDigit);
            this.mTextViewResId = a.getResourceId(1, 0);
            setOnClickListener(this.mListener);
            this.mEnableHaptics = new LockPatternUtils(context).isTactileFeedbackEnabled();
            this.mPM = (PowerManager) this.mContext.getSystemService("power");
            ((LayoutInflater) getContext().getSystemService("layout_inflater")).inflate(2130903049, this, true);
            this.mDigitText = (TextView) findViewById(2131623966);
            this.mDigitText.setText(Integer.toString(this.mDigit));
            this.mTextSize = this.mDigitText.getTextSize();
            this.mPaint = new Paint();
            this.mPaint.setTextSize(this.mTextSize);
            this.mPaint.setTypeface(Typeface.create("sans-serif-light", 0));
            FontMetrics matrics = this.mPaint.getFontMetrics();
            this.mTextHeight = (int) (matrics.descent - matrics.ascent);
            setBackground(this.mContext.getDrawable(2130837525));
        } finally {
            a.recycle();
        }
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        measureChildren(widthMeasureSpec, heightMeasureSpec);
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int viewHeight = getHeight();
        int tvHeight = this.mTextHeight;
        int top = (viewHeight / 2) - (tvHeight / 2);
        int left = (getWidth() / 2) - (this.mDigitText.getMeasuredWidth() / 2);
        this.mDigitText.layout(left, top, this.mDigitText.getMeasuredWidth() + left, top + tvHeight);
    }

    public boolean hasOverlappingRendering() {
        return false;
    }

    public void doHapticKeyClick() {
        if (this.mEnableHaptics) {
            performHapticFeedback(1, 3);
        }
    }
}
