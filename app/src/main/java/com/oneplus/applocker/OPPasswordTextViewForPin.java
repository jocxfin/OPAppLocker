package com.oneplus.applocker;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings.System;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import java.util.ArrayList;
import java.util.Stack;

public class OPPasswordTextViewForPin extends View {
    private static final long APPEAR_DURATION = 160;
    private static final long DISAPPEAR_DURATION = 160;
    private static final long DOT_APPEAR_DURATION_OVERSHOOT = 320;
    private static final long DOT_APPEAR_TEXT_DISAPPEAR_OVERLAP_DURATION = 130;
    private static final float DOT_OVERSHOOT_FACTOR = 1.5f;
    private static final int MAX_LOCK_PASSWORD_SIZE = 16;
    private static final float OVERSHOOT_TIME_POSITION = 0.5f;
    private static final long RESET_DELAY_PER_ELEMENT = 40;
    private static final long RESET_MAX_DELAY = 200;
    private static final long TEXT_REST_DURATION_AFTER_APPEAR = 100;
    private static final long TEXT_VISIBILITY_DURATION = 1300;
    private CharState charState;
    private CharState charState2;
    private CharState charState3;
    private CharState charState4;
    private int inputCount;
    private boolean isAllowDelete;
    private boolean isDelete;
    private boolean isDrawEmptyCircleAfterDelete;
    private AccelerateInterpolator mAccelerateInterpolator;
    private Interpolator mAppearInterpolator;
    private OPPasswordInputCountCallBack mCallBack;
    private int mCharPadding;
    private Stack<CharState> mCharPool;
    private Interpolator mDisappearInterpolator;
    private int mDotSize;
    private int mDotSizeEmpty;
    private final Paint mDrawAlphaPaint1;
    private final Paint mDrawAlphaPaint2;
    private final Paint mDrawAlphaPaint3;
    private final Paint mDrawAlphaPaint4;
    private final Paint mDrawEmptyCirclePaint;
    private final Paint mDrawPaint;
    private int mEmptyCircleWidth;
    private Interpolator mFastOutSlowInInterpolator;
    private Handler mHandler;
    public OnTextEmptyListerner mOnTextEmptyListerner;
    private PowerManager mPM;
    private boolean mPasswordCheckState;
    private float mScreenDensity;
    private boolean mShowPassword;
    private String mText;
    private ArrayList<CharState> mTextChars;
    private final int mTextHeightRaw;

    public interface OnTextEmptyListerner {
        void onTextChanged(String str);
    }

    private class CharState {
        float currentDotSizeFactor;
        float currentDotSizeFactor2;
        float currentDotSizeFactor3;
        float currentDotSizeFactor4;
        float currentEmptyCircleSizeFactor;
        float currentTextSizeFactor;
        float currentTextTranslationY;
        float currentWidthFactor;
        boolean dotAnimationIsGrowing;
        Animator dotAnimator;
        AnimatorListener dotFinishListener;
        private AnimatorUpdateListener dotSizeUpdater;
        private AnimatorUpdateListener dotSizeUpdater2;
        private AnimatorUpdateListener dotSizeUpdater3;
        private AnimatorUpdateListener dotSizeUpdater4;
        private Runnable dotSwapperRunnable;
        boolean emptyCircleAnimationIsGrowing;
        ValueAnimator emptyCircleAnimator;
        AnimatorListener emptyCircleFinishListener;
        private AnimatorUpdateListener emptyCircleSizeUpdater;
        boolean isDotSwapPending;
        AnimatorListener passwordErrorFinishListener;
        AnimatorListener removeEndListener;
        boolean textAnimationIsGrowing;
        ValueAnimator textAnimator;
        AnimatorListener textFinishListener;
        private AnimatorUpdateListener textSizeUpdater;
        ValueAnimator textTranslateAnimator;
        AnimatorListener textTranslateFinishListener;
        private AnimatorUpdateListener textTranslationUpdater;
        char whichChar;
        boolean widthAnimationIsGrowing;
        ValueAnimator widthAnimator;
        AnimatorListener widthFinishListener;
        private AnimatorUpdateListener widthUpdater;

        private CharState() {
            this.currentEmptyCircleSizeFactor = 1.0f;
            this.currentTextTranslationY = 1.0f;
            this.removeEndListener = new AnimatorListenerAdapter() {
                private boolean mCancelled;

                public void onAnimationCancel(Animator animation) {
                    this.mCancelled = true;
                }

                public void onAnimationEnd(Animator animation) {
                    if (!this.mCancelled) {
                        OPPasswordTextViewForPin.this.mTextChars.remove(CharState.this);
                        OPPasswordTextViewForPin.this.mCharPool.push(CharState.this);
                        CharState.this.resetState();
                        CharState.this.cancelAnimator(CharState.this.textTranslateAnimator);
                        CharState.this.textTranslateAnimator = null;
                    }
                }

                public void onAnimationStart(Animator animation) {
                    this.mCancelled = false;
                }
            };
            this.dotFinishListener = new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator animation) {
                    CharState.this.dotAnimator = null;
                }
            };
            this.passwordErrorFinishListener = new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator animation) {
                    OPPasswordTextViewForPin.this.reset(true);
                }
            };
            this.textFinishListener = new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator animation) {
                    CharState.this.textAnimator = null;
                }
            };
            this.textTranslateFinishListener = new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator animation) {
                    CharState.this.textTranslateAnimator = null;
                }
            };
            this.emptyCircleFinishListener = new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator animation) {
                    CharState.this.emptyCircleAnimator = null;
                }
            };
            this.widthFinishListener = new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator animation) {
                    CharState.this.widthAnimator = null;
                }
            };
            this.dotSizeUpdater = new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    CharState.this.currentDotSizeFactor = ((Float) animation.getAnimatedValue()).floatValue();
                    OPPasswordTextViewForPin.this.invalidate();
                }
            };
            this.dotSizeUpdater2 = new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    CharState.this.currentDotSizeFactor2 = ((Float) animation.getAnimatedValue()).floatValue();
                    OPPasswordTextViewForPin.this.invalidate();
                }
            };
            this.dotSizeUpdater3 = new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    CharState.this.currentDotSizeFactor3 = ((Float) animation.getAnimatedValue()).floatValue();
                    OPPasswordTextViewForPin.this.invalidate();
                }
            };
            this.dotSizeUpdater4 = new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    CharState.this.currentDotSizeFactor4 = ((Float) animation.getAnimatedValue()).floatValue();
                    OPPasswordTextViewForPin.this.invalidate();
                }
            };
            this.textSizeUpdater = new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    CharState.this.currentTextSizeFactor = ((Float) animation.getAnimatedValue()).floatValue();
                    OPPasswordTextViewForPin.this.invalidate();
                }
            };
            this.textTranslationUpdater = new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    CharState.this.currentTextTranslationY = ((Float) animation.getAnimatedValue()).floatValue();
                    OPPasswordTextViewForPin.this.invalidate();
                }
            };
            this.emptyCircleSizeUpdater = new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    CharState.this.currentEmptyCircleSizeFactor = ((Float) animation.getAnimatedValue()).floatValue();
                    OPPasswordTextViewForPin.this.invalidate();
                }
            };
            this.widthUpdater = new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    CharState.this.currentWidthFactor = ((Float) animation.getAnimatedValue()).floatValue();
                    OPPasswordTextViewForPin.this.invalidate();
                }
            };
            this.dotSwapperRunnable = new Runnable() {
                public void run() {
                    CharState.this.performSwap();
                    CharState.this.isDotSwapPending = false;
                }
            };
        }

        void resetState() {
            this.whichChar = '\u0000';
            this.currentTextSizeFactor = 0.0f;
            this.currentDotSizeFactor = 0.0f;
            this.currentDotSizeFactor2 = 0.0f;
            this.currentDotSizeFactor3 = 0.0f;
            this.currentDotSizeFactor4 = 0.0f;
            this.currentWidthFactor = 0.0f;
            cancelAnimator(this.textAnimator);
            this.textAnimator = null;
            cancelAnimator(this.dotAnimator);
            this.dotAnimator = null;
            cancelAnimator(this.widthAnimator);
            this.widthAnimator = null;
            cancelAnimator(this.emptyCircleAnimator);
            this.emptyCircleAnimator = null;
            this.currentEmptyCircleSizeFactor = 1.0f;
            this.currentTextTranslationY = 1.0f;
            removeDotSwapCallbacks();
        }

        void startRemoveAnimation(long startDelay, long widthDelay) {
            boolean dotNeedsAnimation = (this.currentDotSizeFactor <= 0.0f || this.dotAnimator != null) ? this.dotAnimator != null ? this.dotAnimationIsGrowing : false : true;
            boolean textNeedsAnimation = (this.currentTextSizeFactor <= 0.0f || this.textAnimator != null) ? this.textAnimator != null ? this.textAnimationIsGrowing : false : true;
            boolean widthNeedsAnimation = (this.currentWidthFactor <= 0.0f || this.widthAnimator != null) ? this.widthAnimator != null ? this.widthAnimationIsGrowing : false : true;
            if (dotNeedsAnimation) {
                startDotDisappearAnimation(startDelay);
            }
            if (textNeedsAnimation) {
                startTextDisappearAnimation(startDelay);
            }
            startEmptyCircleAppearAnimation(264);
            if (widthNeedsAnimation) {
                startWidthDisappearAnimation(widthDelay);
            }
        }

        void startAppearAnimation() {
            cancelAnimator(this.dotAnimator);
            cancelAnimator(this.textAnimator);
            boolean dotNeedsAnimation = !OPPasswordTextViewForPin.this.mShowPassword ? this.dotAnimator == null || !this.dotAnimationIsGrowing : false;
            boolean textNeedsAnimation = OPPasswordTextViewForPin.this.mShowPassword ? this.textAnimator == null || !this.textAnimationIsGrowing : false;
            boolean widthNeedsAnimation = this.widthAnimator == null || !this.widthAnimationIsGrowing;
            if (dotNeedsAnimation) {
                startDotAppearAnimation(0);
            }
            if (textNeedsAnimation) {
                startTextAppearAnimation();
            }
            if (widthNeedsAnimation) {
                startWidthAppearAnimation();
            }
            if (OPPasswordTextViewForPin.this.mShowPassword) {
                postDotSwap(250);
            }
        }

        private void postDotSwap(long delay) {
            removeDotSwapCallbacks();
            OPPasswordTextViewForPin.this.postDelayed(this.dotSwapperRunnable, delay);
            this.isDotSwapPending = true;
        }

        private void removeDotSwapCallbacks() {
            OPPasswordTextViewForPin.this.removeCallbacks(this.dotSwapperRunnable);
            this.isDotSwapPending = false;
        }

        void swapToDotWhenAppearFinished() {
            removeDotSwapCallbacks();
            if (this.textAnimator != null) {
                postDotSwap(OPPasswordTextViewForPin.TEXT_REST_DURATION_AFTER_APPEAR + (this.textAnimator.getDuration() - this.textAnimator.getCurrentPlayTime()));
            } else {
                performSwap();
            }
        }

        private void performSwap() {
            startTextDisappearAnimation(0);
            startDotAppearAnimation(30);
        }

        private void startWidthDisappearAnimation(long widthDelay) {
            cancelAnimator(this.widthAnimator);
            this.widthAnimator = ValueAnimator.ofFloat(new float[]{this.currentWidthFactor, 0.0f});
            this.widthAnimator.addUpdateListener(this.widthUpdater);
            this.widthAnimator.addListener(this.widthFinishListener);
            this.widthAnimator.addListener(this.removeEndListener);
            this.widthAnimator.setDuration((long) (this.currentWidthFactor * 160.0f));
            this.widthAnimator.setStartDelay(widthDelay);
            this.widthAnimator.start();
            this.widthAnimationIsGrowing = false;
        }

        private void startTextDisappearAnimation(long startDelay) {
            cancelAnimator(this.textAnimator);
            this.textAnimator = ValueAnimator.ofFloat(new float[]{this.currentTextSizeFactor, 0.0f});
            this.textAnimator.addUpdateListener(this.textSizeUpdater);
            this.textAnimator.addListener(this.textFinishListener);
            this.textAnimator.setInterpolator(OPPasswordTextViewForPin.this.mDisappearInterpolator);
            this.textAnimator.setDuration((long) (this.currentTextSizeFactor * 160.0f));
            this.textAnimator.setStartDelay(startDelay);
            this.textAnimator.start();
            this.textAnimationIsGrowing = false;
        }

        private void startDotDisappearAnimation(long startDelay) {
            cancelAnimator(this.dotAnimator);
            ValueAnimator animator = ValueAnimator.ofFloat(new float[]{this.currentDotSizeFactor, 0.0f});
            animator.addUpdateListener(this.dotSizeUpdater);
            animator.addListener(this.dotFinishListener);
            animator.setInterpolator(OPPasswordTextViewForPin.this.mDisappearInterpolator);
            animator.setDuration((long) (Math.min(this.currentDotSizeFactor, 1.0f) * 160.0f));
            animator.setStartDelay(startDelay);
            animator.start();
            this.dotAnimator = animator;
            this.dotAnimationIsGrowing = false;
        }

        private void startPasswordErrorAnimation() {
            this.currentDotSizeFactor = 1.0f;
            ValueAnimator animator = ValueAnimator.ofFloat(new float[]{1.0f, 0.0f});
            animator.addUpdateListener(this.dotSizeUpdater);
            animator.setInterpolator(OPPasswordTextViewForPin.this.mAccelerateInterpolator);
            animator.setDuration(OPPasswordTextViewForPin.RESET_MAX_DELAY);
            animator.start();
        }

        private void startPasswordErrorAnimation2() {
            this.currentDotSizeFactor2 = 1.0f;
            ValueAnimator animator2 = ValueAnimator.ofFloat(new float[]{1.0f, 0.0f});
            animator2.addUpdateListener(this.dotSizeUpdater2);
            animator2.setInterpolator(OPPasswordTextViewForPin.this.mAccelerateInterpolator);
            animator2.setDuration(OPPasswordTextViewForPin.RESET_MAX_DELAY);
            animator2.setStartDelay(66);
            animator2.start();
        }

        private void startPasswordErrorAnimation3() {
            this.currentDotSizeFactor3 = 1.0f;
            ValueAnimator animator3 = ValueAnimator.ofFloat(new float[]{1.0f, 0.0f});
            animator3.addUpdateListener(this.dotSizeUpdater3);
            animator3.setInterpolator(OPPasswordTextViewForPin.this.mAccelerateInterpolator);
            animator3.setDuration(OPPasswordTextViewForPin.RESET_MAX_DELAY);
            animator3.setStartDelay(132);
            animator3.start();
        }

        private void startPasswordErrorAnimation4() {
            this.currentDotSizeFactor4 = 1.0f;
            ValueAnimator animator4 = ValueAnimator.ofFloat(new float[]{1.0f, 0.0f});
            animator4.addUpdateListener(this.dotSizeUpdater4);
            animator4.setInterpolator(OPPasswordTextViewForPin.this.mAccelerateInterpolator);
            animator4.addListener(this.passwordErrorFinishListener);
            animator4.setDuration(OPPasswordTextViewForPin.RESET_MAX_DELAY);
            animator4.setStartDelay(198);
            animator4.start();
        }

        private void startEmptyCircleAppearAnimation(long startDelay) {
            cancelAnimator(this.emptyCircleAnimator);
            ValueAnimator animator = ValueAnimator.ofFloat(new float[]{1.1f, 1.0f});
            animator.addUpdateListener(this.emptyCircleSizeUpdater);
            animator.addListener(this.emptyCircleFinishListener);
            animator.setInterpolator(OPPasswordTextViewForPin.this.mAppearInterpolator);
            animator.setDuration((long) (Math.min(this.currentEmptyCircleSizeFactor, 1.0f) * 250.0f));
            animator.setStartDelay(startDelay);
            animator.start();
            this.emptyCircleAnimator = animator;
        }

        private void startWidthAppearAnimation() {
            cancelAnimator(this.widthAnimator);
            this.widthAnimator = ValueAnimator.ofFloat(new float[]{this.currentWidthFactor, 1.0f});
            this.widthAnimator.addUpdateListener(this.widthUpdater);
            this.widthAnimator.addListener(this.widthFinishListener);
            this.widthAnimator.setDuration((long) ((1.0f - this.currentWidthFactor) * 160.0f));
            this.widthAnimator.start();
            this.widthAnimationIsGrowing = true;
        }

        private void startTextAppearAnimation() {
            cancelAnimator(this.textAnimator);
            this.textAnimator = ValueAnimator.ofFloat(new float[]{this.currentTextSizeFactor, 1.0f});
            this.textAnimator.addUpdateListener(this.textSizeUpdater);
            this.textAnimator.addListener(this.textFinishListener);
            this.textAnimator.setInterpolator(OPPasswordTextViewForPin.this.mAppearInterpolator);
            this.textAnimator.setDuration((long) ((1.0f - this.currentTextSizeFactor) * 160.0f));
            this.textAnimator.start();
            this.textAnimationIsGrowing = true;
            if (this.textTranslateAnimator == null) {
                this.textTranslateAnimator = ValueAnimator.ofFloat(new float[]{0.8f, 0.0f});
                this.textTranslateAnimator.addUpdateListener(this.textTranslationUpdater);
                this.textTranslateAnimator.addListener(this.textTranslateFinishListener);
                this.textTranslateAnimator.setInterpolator(OPPasswordTextViewForPin.this.mAppearInterpolator);
                this.textTranslateAnimator.setDuration(160);
                this.textTranslateAnimator.start();
            }
        }

        private void startDotAppearAnimation(long delay) {
            cancelAnimator(this.dotAnimator);
            if (OPPasswordTextViewForPin.this.mShowPassword) {
                ValueAnimator growAnimator = ValueAnimator.ofFloat(new float[]{this.currentDotSizeFactor, 1.0f});
                growAnimator.addUpdateListener(this.dotSizeUpdater);
                growAnimator.setDuration((long) ((1.0f - this.currentDotSizeFactor) * 160.0f));
                growAnimator.addListener(this.dotFinishListener);
                growAnimator.setStartDelay(delay);
                growAnimator.start();
                this.dotAnimator = growAnimator;
            } else {
                ValueAnimator overShootAnimator = ValueAnimator.ofFloat(new float[]{this.currentDotSizeFactor, OPPasswordTextViewForPin.DOT_OVERSHOOT_FACTOR});
                overShootAnimator.addUpdateListener(this.dotSizeUpdater);
                overShootAnimator.setInterpolator(OPPasswordTextViewForPin.this.mAppearInterpolator);
                overShootAnimator.setDuration(160);
                ValueAnimator settleBackAnimator = ValueAnimator.ofFloat(new float[]{OPPasswordTextViewForPin.DOT_OVERSHOOT_FACTOR, 1.0f});
                settleBackAnimator.addUpdateListener(this.dotSizeUpdater);
                settleBackAnimator.setDuration(160);
                settleBackAnimator.addListener(this.dotFinishListener);
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playSequentially(new Animator[]{overShootAnimator, settleBackAnimator});
                animatorSet.setStartDelay(delay);
                animatorSet.start();
                this.dotAnimator = animatorSet;
            }
            this.dotAnimationIsGrowing = true;
        }

        private void cancelAnimator(Animator animator) {
            if (animator != null) {
                animator.cancel();
            }
        }

        public float draw(Canvas canvas, float currentDrawPosition, int charHeight, float yPosition, float charLength) {
            boolean textVisible = this.currentTextSizeFactor > 0.0f;
            boolean dotVisible = this.currentDotSizeFactor > 0.0f;
            float charWidth = charLength * this.currentWidthFactor;
            if (textVisible) {
                float currYPosition = (((((float) charHeight) / 2.0f) * this.currentTextSizeFactor) + yPosition) + ((((float) charHeight) * this.currentTextTranslationY) * 0.8f);
                canvas.save();
                canvas.translate(currentDrawPosition + (charWidth / 2.0f), currYPosition);
                canvas.scale(this.currentTextSizeFactor, this.currentTextSizeFactor);
                canvas.drawText(Character.toString(this.whichChar), 0.0f, 0.0f, OPPasswordTextViewForPin.this.mDrawPaint);
                canvas.restore();
            }
            if (dotVisible) {
                canvas.save();
                canvas.translate(currentDrawPosition + (charWidth / 2.0f), yPosition);
                canvas.drawCircle(0.0f, 0.0f, ((float) (OPPasswordTextViewForPin.this.mDotSize / 2)) * this.currentDotSizeFactor, OPPasswordTextViewForPin.this.mDrawPaint);
                canvas.restore();
            }
            return (((float) OPPasswordTextViewForPin.this.mCharPadding) * this.currentWidthFactor) + charWidth;
        }

        public float drawErrorAnimation(Canvas canvas, float currentDrawPosition, int charHeight, float yPosition, float charLength) {
            float centerX;
            if (this.currentEmptyCircleSizeFactor > 1.0f) {
            }
            boolean dotVisible = this.currentDotSizeFactor > 0.0f;
            boolean dot2Visible = this.currentDotSizeFactor2 > 0.0f;
            boolean dot3Visible = this.currentDotSizeFactor3 > 0.0f;
            boolean dot4Visible = this.currentDotSizeFactor4 > 0.0f;
            float charWidth = charLength * this.currentWidthFactor;
            if (dotVisible) {
                canvas.save();
                centerX = currentDrawPosition;
                canvas.translate(currentDrawPosition, yPosition);
                OPPasswordTextViewForPin.this.mDrawAlphaPaint1.setAlpha((int) (this.currentDotSizeFactor * 255.0f));
                canvas.drawCircle(0.0f, 0.0f, ((float) OPPasswordTextViewForPin.this.mDotSize) * this.currentDotSizeFactor, OPPasswordTextViewForPin.this.mDrawAlphaPaint1);
                canvas.restore();
            } else {
                canvas.save();
                centerX = currentDrawPosition;
                canvas.translate(currentDrawPosition, yPosition);
                canvas.drawCircle(0.0f, 0.0f, (float) OPPasswordTextViewForPin.this.mDotSizeEmpty, OPPasswordTextViewForPin.this.mDrawEmptyCirclePaint);
                canvas.restore();
            }
            if (dot2Visible) {
                canvas.save();
                centerX = currentDrawPosition;
                canvas.translate(currentDrawPosition, yPosition);
                OPPasswordTextViewForPin.this.mDrawAlphaPaint2.setAlpha((int) (this.currentDotSizeFactor2 * 255.0f));
                canvas.drawCircle(0.0f, 0.0f, ((float) OPPasswordTextViewForPin.this.mDotSize) * this.currentDotSizeFactor2, OPPasswordTextViewForPin.this.mDrawAlphaPaint2);
                canvas.restore();
            } else {
                canvas.save();
                centerX = currentDrawPosition;
                canvas.translate(currentDrawPosition, yPosition);
                canvas.drawCircle(0.0f, 0.0f, (float) OPPasswordTextViewForPin.this.mDotSizeEmpty, OPPasswordTextViewForPin.this.mDrawEmptyCirclePaint);
                canvas.restore();
            }
            if (dot3Visible) {
                canvas.save();
                centerX = currentDrawPosition;
                canvas.translate(currentDrawPosition, yPosition);
                OPPasswordTextViewForPin.this.mDrawAlphaPaint3.setAlpha((int) (this.currentDotSizeFactor3 * 255.0f));
                canvas.drawCircle(0.0f, 0.0f, ((float) OPPasswordTextViewForPin.this.mDotSize) * this.currentDotSizeFactor3, OPPasswordTextViewForPin.this.mDrawAlphaPaint3);
                canvas.restore();
            } else {
                canvas.save();
                centerX = currentDrawPosition;
                canvas.translate(currentDrawPosition, yPosition);
                canvas.drawCircle(0.0f, 0.0f, (float) OPPasswordTextViewForPin.this.mDotSizeEmpty, OPPasswordTextViewForPin.this.mDrawEmptyCirclePaint);
                canvas.restore();
            }
            if (dot4Visible) {
                canvas.save();
                centerX = currentDrawPosition;
                canvas.translate(currentDrawPosition, yPosition);
                OPPasswordTextViewForPin.this.mDrawAlphaPaint4.setAlpha((int) (this.currentDotSizeFactor4 * 255.0f));
                canvas.drawCircle(0.0f, 0.0f, ((float) OPPasswordTextViewForPin.this.mDotSize) * this.currentDotSizeFactor4, OPPasswordTextViewForPin.this.mDrawAlphaPaint4);
                canvas.restore();
            } else {
                canvas.save();
                centerX = currentDrawPosition;
                canvas.translate(currentDrawPosition, yPosition);
                canvas.drawCircle(0.0f, 0.0f, (float) OPPasswordTextViewForPin.this.mDotSizeEmpty, OPPasswordTextViewForPin.this.mDrawEmptyCirclePaint);
                canvas.restore();
            }
            return (((float) OPPasswordTextViewForPin.this.mCharPadding) * this.currentWidthFactor) + charWidth;
        }
    }

    public OPPasswordTextViewForPin(Context context) {
        this(context, null);
    }

    public OPPasswordTextViewForPin(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OPPasswordTextViewForPin(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public OPPasswordTextViewForPin(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        boolean z = true;
        super(context, attrs, defStyleAttr, defStyleRes);
        this.mTextChars = new ArrayList();
        this.mCharPool = new Stack();
        this.mText = "";
        this.mDrawPaint = new Paint();
        this.mDrawEmptyCirclePaint = new Paint();
        this.mDrawAlphaPaint1 = new Paint();
        this.mDrawAlphaPaint2 = new Paint();
        this.mDrawAlphaPaint3 = new Paint();
        this.mDrawAlphaPaint4 = new Paint();
        this.inputCount = 0;
        this.mCallBack = null;
        this.mHandler = new Handler();
        this.isAllowDelete = true;
        this.isDelete = false;
        this.isDrawEmptyCircleAfterDelete = true;
        this.charState = new CharState();
        this.charState2 = new CharState();
        this.charState3 = new CharState();
        this.charState4 = new CharState();
        this.mPasswordCheckState = true;
        setFocusableInTouchMode(true);
        setFocusable(true);
        this.mTextHeightRaw = 28;
        this.mEmptyCircleWidth = getContext().getResources().getDimensionPixelSize(2131492904);
        this.mDrawPaint.setFlags(129);
        this.mDrawPaint.setTextAlign(Align.CENTER);
        this.mDrawPaint.setColor(context.getResources().getColor(17170443));
        this.mDrawPaint.setTypeface(Typeface.create("sans-serif-light", 0));
        this.mDrawPaint.setStrokeJoin(Join.ROUND);
        this.mDrawPaint.setStrokeCap(Cap.ROUND);
        this.mDrawPaint.setStrokeWidth((float) this.mEmptyCircleWidth);
        this.mDrawPaint.setStyle(Style.FILL);
        this.mDrawPaint.setTextSize((float) getContext().getResources().getDimensionPixelSize(2131492905));
        this.mDrawPaint.setAntiAlias(true);
        this.mDotSize = getContext().getResources().getDimensionPixelSize(2131492902);
        this.mDotSizeEmpty = getContext().getResources().getDimensionPixelSize(2131492903);
        this.mDrawEmptyCirclePaint.setStrokeWidth((float) this.mEmptyCircleWidth);
        this.mDrawEmptyCirclePaint.setStyle(Style.STROKE);
        this.mDrawEmptyCirclePaint.setStrokeJoin(Join.ROUND);
        this.mDrawEmptyCirclePaint.setStrokeCap(Cap.ROUND);
        this.mDrawEmptyCirclePaint.setColor(-587202560);
        this.mDrawEmptyCirclePaint.setColor(context.getResources().getColor(17170443));
        this.mDrawEmptyCirclePaint.setAntiAlias(true);
        this.mDrawAlphaPaint1.setStyle(Style.FILL);
        this.mDrawAlphaPaint1.setStrokeJoin(Join.ROUND);
        this.mDrawAlphaPaint1.setStrokeCap(Cap.ROUND);
        this.mDrawAlphaPaint1.setColor(ViewCompat.MEASURED_SIZE_MASK);
        this.mDrawAlphaPaint1.setStrokeWidth((float) this.mEmptyCircleWidth);
        this.mDrawAlphaPaint2.setStyle(Style.FILL);
        this.mDrawAlphaPaint2.setStrokeJoin(Join.ROUND);
        this.mDrawAlphaPaint2.setStrokeCap(Cap.ROUND);
        this.mDrawAlphaPaint2.setColor(ViewCompat.MEASURED_SIZE_MASK);
        this.mDrawAlphaPaint2.setStrokeWidth((float) this.mEmptyCircleWidth);
        this.mDrawAlphaPaint3.setStyle(Style.FILL);
        this.mDrawAlphaPaint3.setStrokeJoin(Join.ROUND);
        this.mDrawAlphaPaint3.setStrokeCap(Cap.ROUND);
        this.mDrawAlphaPaint3.setColor(ViewCompat.MEASURED_SIZE_MASK);
        this.mDrawAlphaPaint3.setStrokeWidth((float) this.mEmptyCircleWidth);
        this.mDrawAlphaPaint4.setStyle(Style.FILL);
        this.mDrawAlphaPaint4.setStrokeJoin(Join.ROUND);
        this.mDrawAlphaPaint4.setStrokeCap(Cap.ROUND);
        this.mDrawAlphaPaint4.setColor(ViewCompat.MEASURED_SIZE_MASK);
        this.mDrawAlphaPaint4.setStrokeWidth((float) this.mEmptyCircleWidth);
        this.mCharPadding = getContext().getResources().getDimensionPixelSize(2131492906);
        if (System.getInt(this.mContext.getContentResolver(), "show_password", 1) != 1) {
            z = false;
        }
        this.mShowPassword = z;
        this.mAccelerateInterpolator = new AccelerateInterpolator();
        this.mAppearInterpolator = AnimationUtils.loadInterpolator(this.mContext, 17563662);
        this.mDisappearInterpolator = AnimationUtils.loadInterpolator(this.mContext, 17563663);
        this.mFastOutSlowInInterpolator = AnimationUtils.loadInterpolator(this.mContext, 17563661);
        this.mPM = (PowerManager) this.mContext.getSystemService("power");
        this.mScreenDensity = context.getResources().getDisplayMetrics().density;
    }

    public void setCallBack(OPPasswordInputCountCallBack back) {
        this.mCallBack = back;
    }

    protected void onDraw(Canvas canvas) {
        float currentDrawPosition = ((float) (getWidth() / 2)) - (getDrawingWidth() / 2.0f);
        int length = Math.min(this.mTextChars.size(), 16);
        Rect bounds = getCharBounds();
        int charHeight = bounds.bottom - bounds.top;
        float yPosition = (float) (getHeight() / 2);
        float charLength = (float) (bounds.right - bounds.left);
        System.out.println("zhuyang--onDraw--mPasswordCheckState:" + this.mPasswordCheckState);
        this.inputCount = this.mText.length();
        System.out.println("zhuyang--onDraw--inputCount:" + this.inputCount);
        System.out.println("zhuyang--onDraw--length:" + length);
        for (int i = 0; i < length; i++) {
            CharState charState = (CharState) this.mTextChars.get(i);
            System.out.println("zhuyang--onDraw-currentDrawPosition:" + currentDrawPosition);
            System.out.println("zhuyang--onDraw-charHeight:" + charHeight);
            System.out.println("zhuyang--onDraw-yPosition:" + yPosition);
            System.out.println("zhuyang--onDraw-charLength:" + charLength);
            System.out.println("zhuyang--onDraw========================================================");
            currentDrawPosition += charState.draw(canvas, currentDrawPosition, charHeight, yPosition, charLength);
        }
    }

    public boolean hasOverlappingRendering() {
        return false;
    }

    private Rect getCharBounds() {
        Rect bounds = new Rect();
        this.mDrawPaint.getTextBounds("0", 0, 1, bounds);
        return bounds;
    }

    private float getDrawingWidth() {
        int width = 0;
        int length = Math.min(this.mTextChars.size(), 16);
        Rect bounds = getCharBounds();
        int charLength = bounds.right - bounds.left;
        for (int i = 0; i < length; i++) {
            CharState charState = (CharState) this.mTextChars.get(i);
            if (i != 0) {
                width = (int) (((float) width) + (((float) this.mCharPadding) * charState.currentDotSizeFactor));
            }
            width = (int) (((float) width) + (((float) charLength) * charState.currentDotSizeFactor));
        }
        return (float) width;
    }

    public void append(char c) {
        CharState charState;
        this.isAllowDelete = true;
        int visibleChars = Math.min(this.mTextChars.size(), 16);
        String textbefore = this.mText;
        this.mText += c;
        int newLength = this.mText.length();
        if (newLength > visibleChars) {
            charState = obtainCharState(c);
            this.mTextChars.add(charState);
        } else {
            charState = (CharState) this.mTextChars.get(newLength - 1);
            charState.whichChar = c;
        }
        charState.startAppearAnimation();
        if (newLength > 1) {
            CharState previousState = (CharState) this.mTextChars.get(newLength - 2);
            if (previousState.isDotSwapPending) {
                previousState.swapToDotWhenAppearFinished();
            }
        }
        if (this.mOnTextEmptyListerner != null) {
            this.mOnTextEmptyListerner.onTextChanged(this.mText);
        }
        System.out.println("zhuyang--newLength:" + newLength);
        if (newLength == 16) {
            this.mCallBack.setNumbPadKeyForPinEnable(false);
        }
        userActivity();
    }

    private CharState obtainCharState(char c) {
        CharState charState = new CharState();
        this.mCharPool.push(charState);
        charState.whichChar = c;
        return charState;
    }

    private void userActivity() {
        this.mPM.userActivity(SystemClock.uptimeMillis(), false);
    }

    public void deleteLastChar() {
        int length = this.mText.length();
        String textbefore = this.mText;
        if (length > 0) {
            this.mText = this.mText.substring(0, length - 1);
            ((CharState) this.mTextChars.get(length - 1)).startRemoveAnimation(0, 0);
        }
        if (this.mOnTextEmptyListerner != null) {
            this.mOnTextEmptyListerner.onTextChanged(this.mText);
        }
        userActivity();
    }

    public String getText() {
        return this.mText;
    }

    public void reset(boolean animated) {
        this.mText = "";
        this.inputCount = 0;
        this.mTextChars.clear();
        this.mCharPool.clear();
        this.mPasswordCheckState = true;
        this.mCallBack.setNumbPadKeyForPinEnable(true);
        if (this.mOnTextEmptyListerner != null) {
            this.mOnTextEmptyListerner.onTextChanged(this.mText);
        }
        invalidate();
    }

    public void setTextEmptyListener(OnTextEmptyListerner listener) {
        this.mOnTextEmptyListerner = listener;
    }

    public int getMaxLockPasswordSize() {
        return 16;
    }
}
