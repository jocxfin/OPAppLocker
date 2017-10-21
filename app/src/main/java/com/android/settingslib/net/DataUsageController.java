package com.android.settingslib.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.INetworkStatsService;
import android.net.INetworkStatsService.Stub;
import android.net.INetworkStatsSession;
import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.net.NetworkStatsHistory;
import android.net.NetworkStatsHistory.Entry;
import android.net.NetworkTemplate;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import com.android.settingslib.R;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;

public class DataUsageController {
    private static final boolean DEBUG = Log.isLoggable(TAG, 3);
    private static final int FIELDS = 10;
    private static final StringBuilder PERIOD_BUILDER = new StringBuilder(50);
    private static final Formatter PERIOD_FORMATTER = new Formatter(PERIOD_BUILDER, Locale.getDefault());
    private static final String TAG = "DataUsageController";
    private Callback mCallback;
    private final ConnectivityManager mConnectivityManager;
    private final Context mContext;
    private NetworkNameProvider mNetworkController;
    private final NetworkPolicyManager mPolicyManager = NetworkPolicyManager.from(this.mContext);
    private INetworkStatsSession mSession;
    private final INetworkStatsService mStatsService = Stub.asInterface(ServiceManager.getService("netstats"));
    private SubscriptionManager mSubscriptionManager = SubscriptionManager.from(this.mContext);
    private final TelephonyManager mTelephonyManager;

    public interface Callback {
        void onMobileDataEnabled(boolean z);
    }

    public static class DataUsageInfo {
        public String carrier;
        public long limitLevel;
        public String period;
        public long startDate;
        public long usageLevel;
        public long warningLevel;
    }

    public interface NetworkNameProvider {
        String getMobileDataNetworkName();
    }

    public DataUsageController(Context context) {
        this.mContext = context;
        this.mTelephonyManager = TelephonyManager.from(context);
        this.mConnectivityManager = ConnectivityManager.from(context);
    }

    public void setNetworkController(NetworkNameProvider networkController) {
        this.mNetworkController = networkController;
    }

    public long getDefaultWarningLevel() {
        return ((long) this.mContext.getResources().getInteger(R.integer.default_data_warning_level_mb)) * 1048576;
    }

    private INetworkStatsSession getSession() {
        if (this.mSession == null) {
            try {
                this.mSession = this.mStatsService.openSession();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to open stats session", e);
            } catch (RuntimeException e2) {
                Log.w(TAG, "Failed to open stats session", e2);
            }
        }
        return this.mSession;
    }

    public void setCallback(Callback callback) {
        this.mCallback = callback;
    }

    private DataUsageInfo warn(String msg) {
        Log.w(TAG, "Failed to get data usage, " + msg);
        return null;
    }

    private static Time addMonth(Time t, int months) {
        Time rt = new Time(t);
        rt.set(t.monthDay, t.month + months, t.year);
        rt.normalize(DEBUG);
        return rt;
    }

    public DataUsageInfo getDataUsageInfo() {
        String subscriberId = getActiveSubscriberId(this.mContext);
        if (subscriberId == null) {
            return warn("no subscriber id");
        }
        return getDataUsageInfo(NetworkTemplate.normalize(NetworkTemplate.buildTemplateMobileAll(subscriberId), this.mTelephonyManager.getMergedSubscriberIds()));
    }

    public DataUsageInfo getWifiDataUsageInfo() {
        return getDataUsageInfo(NetworkTemplate.buildTemplateWifiWildcard());
    }

    public DataUsageInfo getDataUsageInfo(NetworkTemplate template) {
        INetworkStatsSession session = getSession();
        if (session == null) {
            return warn("no stats session");
        }
        NetworkPolicy policy = findNetworkPolicy(template);
        try {
            long end;
            long start;
            NetworkStatsHistory history = session.getHistoryForNetwork(template, 10);
            long now = System.currentTimeMillis();
            if (policy == null || policy.cycleDay <= 0) {
                end = now;
                start = now - 2419200000L;
            } else {
                if (DEBUG) {
                    Log.d(TAG, "Cycle day=" + policy.cycleDay + " tz=" + policy.cycleTimezone);
                }
                Time time = new Time(policy.cycleTimezone);
                time.setToNow();
                time = new Time(time);
                time.set(policy.cycleDay, time.month, time.year);
                time.normalize(DEBUG);
                if (time.after(time)) {
                    start = time.toMillis(DEBUG);
                    end = addMonth(time, 1).toMillis(DEBUG);
                } else {
                    start = addMonth(time, -1).toMillis(DEBUG);
                    end = time.toMillis(DEBUG);
                }
            }
            long callStart = System.currentTimeMillis();
            Entry entry = history.getValues(start, end, now, null);
            long callEnd = System.currentTimeMillis();
            if (DEBUG) {
                Log.d(TAG, String.format("history call from %s to %s now=%s took %sms: %s", new Object[]{new Date(start), new Date(end), new Date(now), Long.valueOf(callEnd - callStart), historyEntryToString(entry)}));
            }
            if (entry == null) {
                return warn("no entry data");
            }
            long totalBytes = entry.rxBytes + entry.txBytes;
            DataUsageInfo usage = new DataUsageInfo();
            usage.startDate = start;
            usage.usageLevel = totalBytes;
            usage.period = formatDateRange(start, end);
            if (policy != null) {
                usage.limitLevel = policy.limitBytes > 0 ? policy.limitBytes : 0;
                usage.warningLevel = policy.warningBytes > 0 ? policy.warningBytes : 0;
            } else {
                usage.warningLevel = getDefaultWarningLevel();
            }
            if (!(usage == null || this.mNetworkController == null)) {
                usage.carrier = this.mNetworkController.getMobileDataNetworkName();
            }
            return usage;
        } catch (RemoteException e) {
            return warn("remote call failed");
        } catch (NullPointerException e2) {
            return warn("NullPointerException");
        }
    }

    private NetworkPolicy findNetworkPolicy(NetworkTemplate template) {
        if (this.mPolicyManager == null || template == null) {
            return null;
        }
        NetworkPolicy[] policies = this.mPolicyManager.getNetworkPolicies();
        if (policies == null) {
            return null;
        }
        for (NetworkPolicy policy : policies) {
            if (policy != null && template.equals(policy.template)) {
                return policy;
            }
        }
        return null;
    }

    private static String historyEntryToString(Entry entry) {
        return entry == null ? null : "Entry[" + "bucketDuration=" + entry.bucketDuration + ",bucketStart=" + entry.bucketStart + ",activeTime=" + entry.activeTime + ",rxBytes=" + entry.rxBytes + ",rxPackets=" + entry.rxPackets + ",txBytes=" + entry.txBytes + ",txPackets=" + entry.txPackets + ",operations=" + entry.operations + ']';
    }

    public void setMobileDataEnabled(boolean enabled) {
        Log.d(TAG, "setMobileDataEnabled: enabled=" + enabled);
        int[] subId = this.mSubscriptionManager.getActiveSubscriptionIdList();
        if (subId.length == 0) {
            this.mTelephonyManager.setDataEnabled(enabled);
        } else {
            for (int i = 0; i < subId.length; i++) {
                if (subId[i] >= 0) {
                    this.mTelephonyManager.setDataEnabled(subId[i], enabled);
                } else {
                    Log.d(TAG, "setMobileDataEnabled: negative subId[i]=" + subId[i]);
                }
            }
        }
        if (this.mCallback != null) {
            this.mCallback.onMobileDataEnabled(enabled);
        }
    }

    public boolean isMobileDataSupported() {
        if (this.mConnectivityManager.isNetworkSupported(0) && this.mTelephonyManager.getSimState() == 5) {
            return true;
        }
        return DEBUG;
    }

    public boolean isMobileDataEnabled() {
        return this.mTelephonyManager.getDataEnabled();
    }

    private static String getActiveSubscriberId(Context context) {
        return TelephonyManager.from(context).getSubscriberId(SubscriptionManager.getDefaultDataSubscriptionId());
    }

    private String formatDateRange(long start, long end) {
        String formatter;
        synchronized (PERIOD_BUILDER) {
            PERIOD_BUILDER.setLength(0);
            formatter = DateUtils.formatDateRange(this.mContext, PERIOD_FORMATTER, start, end, 65552, null).toString();
        }
        return formatter;
    }
}
