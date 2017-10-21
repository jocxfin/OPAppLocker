package com.android.settingslib.wifi;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.net.wifi.WifiConfiguration;
import android.os.Looper;
import android.os.UserHandle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.widget.TextView;
import com.android.settingslib.R;

public class AccessPointPreference extends Preference {
    private static final int[] STATE_NONE = new int[0];
    private static final int[] STATE_SECURED = new int[]{R.attr.state_encrypted};
    static final int[] WIFI_CONNECTION_STRENGTH = new int[]{R.string.accessibility_wifi_one_bar, R.string.accessibility_wifi_two_bars, R.string.accessibility_wifi_three_bars, R.string.accessibility_wifi_signal_full};
    private static int[] wifi_signal_attributes = new int[]{R.attr.wifi_signal};
    private AccessPoint mAccessPoint;
    private Drawable mBadge;
    private final UserBadgeCache mBadgeCache;
    private final int mBadgePadding;
    private CharSequence mContentDescription;
    private int mDefaultIconResId;
    private boolean mForSavedNetworks;
    private int mLevel;
    private final Runnable mNotifyChanged;
    private TextView mTitleView;
    private final StateListDrawable mWifiSld;

    public static class UserBadgeCache {
        private final SparseArray<Drawable> mBadges = new SparseArray();
        private final PackageManager mPm;

        public UserBadgeCache(PackageManager pm) {
            this.mPm = pm;
        }

        private Drawable getUserBadge(int userId) {
            int index = this.mBadges.indexOfKey(userId);
            if (index >= 0) {
                return (Drawable) this.mBadges.valueAt(index);
            }
            Drawable badge = this.mPm.getUserBadgeForDensity(new UserHandle(userId), 0);
            this.mBadges.put(userId, badge);
            return badge;
        }
    }

    public AccessPointPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mForSavedNetworks = false;
        this.mNotifyChanged = new Runnable() {
            public void run() {
                AccessPointPreference.this.notifyChanged();
            }
        };
        this.mWifiSld = null;
        this.mBadgePadding = 0;
        this.mBadgeCache = null;
    }

    public AccessPointPreference(AccessPoint accessPoint, Context context, UserBadgeCache cache, boolean forSavedNetworks) {
        super(context);
        this.mForSavedNetworks = false;
        this.mNotifyChanged = /* anonymous class already generated */;
        this.mBadgeCache = cache;
        this.mAccessPoint = accessPoint;
        this.mForSavedNetworks = forSavedNetworks;
        this.mAccessPoint.setTag(this);
        this.mLevel = -1;
        this.mWifiSld = (StateListDrawable) context.getTheme().obtainStyledAttributes(wifi_signal_attributes).getDrawable(0);
        this.mBadgePadding = context.getResources().getDimensionPixelSize(R.dimen.wifi_preference_badge_padding);
        refresh();
    }

    public AccessPointPreference(AccessPoint accessPoint, Context context, UserBadgeCache cache, int iconResId, boolean forSavedNetworks) {
        super(context);
        this.mForSavedNetworks = false;
        this.mNotifyChanged = /* anonymous class already generated */;
        this.mBadgeCache = cache;
        this.mAccessPoint = accessPoint;
        this.mForSavedNetworks = forSavedNetworks;
        this.mAccessPoint.setTag(this);
        this.mLevel = -1;
        this.mDefaultIconResId = iconResId;
        this.mWifiSld = (StateListDrawable) context.getTheme().obtainStyledAttributes(wifi_signal_attributes).getDrawable(0);
        this.mBadgePadding = context.getResources().getDimensionPixelSize(R.dimen.wifi_preference_badge_padding);
    }

    public AccessPoint getAccessPoint() {
        return this.mAccessPoint;
    }

    public void onBindViewHolder(PreferenceViewHolder view) {
        super.onBindViewHolder(view);
        if (this.mAccessPoint != null) {
            Drawable drawable = getIcon();
            if (drawable != null) {
                drawable.setLevel(this.mLevel);
            }
            this.mTitleView = (TextView) view.findViewById(16908310);
            if (this.mTitleView != null) {
                this.mTitleView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, this.mBadge, null);
                this.mTitleView.setCompoundDrawablePadding(this.mBadgePadding);
            }
            view.itemView.setContentDescription(this.mContentDescription);
        }
    }

    protected void updateIcon(int level, Context context) {
        if (level == -1) {
            safeSetDefaultIcon();
        } else if (getIcon() == null) {
            Drawable drawable;
            if (this.mWifiSld != null) {
                if (this.mAccessPoint != null) {
                    try {
                        int[] iArr;
                        StateListDrawable stateListDrawable = this.mWifiSld;
                        if (this.mAccessPoint.getSecurity() != 0) {
                            iArr = STATE_SECURED;
                        } else {
                            iArr = STATE_NONE;
                        }
                        stateListDrawable.setState(iArr);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                drawable = this.mWifiSld.getCurrent();
                if (!(this.mForSavedNetworks || drawable == null)) {
                    setIcon(drawable);
                    return;
                }
            }
            StateListDrawable wifiSld = (StateListDrawable) context.getTheme().obtainStyledAttributes(wifi_signal_attributes).getDrawable(0);
            if (wifiSld != null) {
                drawable = wifiSld.getCurrent();
                if (!(this.mForSavedNetworks || drawable == null)) {
                    setIcon(drawable);
                }
            }
        }
    }

    private void safeSetDefaultIcon() {
        if (this.mDefaultIconResId != 0) {
            setIcon(this.mDefaultIconResId);
        } else {
            setIcon(null);
        }
    }

    protected void updateBadge(Context context) {
        WifiConfiguration config = this.mAccessPoint.getConfig();
        if (config != null) {
            this.mBadge = this.mBadgeCache.getUserBadge(config.creatorUid);
        }
    }

    public void refresh() {
        if (this.mAccessPoint != null) {
            CharSequence savedNetworkSummary;
            if (this.mForSavedNetworks) {
                setTitle(this.mAccessPoint.getConfigName());
            } else {
                setTitle(this.mAccessPoint.getSsid());
            }
            Context context = getContext();
            int level = this.mAccessPoint.getLevel() + 1;
            if (level != this.mLevel) {
                this.mLevel = level;
                updateIcon(this.mLevel, context);
                notifyChanged();
            }
            updateBadge(context);
            if (this.mForSavedNetworks) {
                savedNetworkSummary = this.mAccessPoint.getSavedNetworkSummary();
            } else {
                savedNetworkSummary = this.mAccessPoint.getSettingsSummary();
            }
            setSummary(savedNetworkSummary);
            this.mContentDescription = getTitle();
            if (getSummary() != null) {
                this.mContentDescription = TextUtils.concat(new CharSequence[]{this.mContentDescription, ",", getSummary()});
            }
            if (level >= 0 && level < WIFI_CONNECTION_STRENGTH.length) {
                this.mContentDescription = TextUtils.concat(new CharSequence[]{this.mContentDescription, ",", getContext().getString(WIFI_CONNECTION_STRENGTH[level])});
            }
        }
    }

    protected void notifyChanged() {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            postNotifyChanged();
        } else {
            super.notifyChanged();
        }
    }

    public void onLevelChanged() {
        postNotifyChanged();
    }

    private void postNotifyChanged() {
        try {
            if (this.mTitleView != null) {
                this.mTitleView.post(this.mNotifyChanged);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
