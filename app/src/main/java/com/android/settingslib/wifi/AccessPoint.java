package com.android.settingslib.wifi;

import android.app.AppGlobals;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkInfo.State;
import android.net.wifi.IWifiManager.Stub;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.NetworkSelectionStatus;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.support.annotation.NonNull;
import android.support.v4.widget.ExploreByTouchHelper;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TtsSpan.VerbatimBuilder;
import android.util.Log;
import android.util.LruCache;
import com.android.settingslib.R;
import java.util.ArrayList;

public class AccessPoint implements Comparable<AccessPoint> {
    public static final int HIGHER_FREQ_24GHZ = 2500;
    public static final int HIGHER_FREQ_5GHZ = 5900;
    private static final String KEY_ACTIVE = "key_active";
    private static final String KEY_CONFIG = "key_config";
    private static final String KEY_NETWORKINFO = "key_networkinfo";
    private static final String KEY_PSKTYPE = "key_psktype";
    private static final String KEY_SCANRESULT = "key_scanresult";
    private static final String KEY_SCANRESULTCACHE = "key_scanresultcache";
    private static final String KEY_SECURITY = "key_security";
    private static final String KEY_SSID = "key_ssid";
    private static final String KEY_WIFIINFO = "key_wifiinfo";
    public static final int LOWER_FREQ_24GHZ = 2400;
    public static final int LOWER_FREQ_5GHZ = 4900;
    public static final int OEM_SIGNAL_LEVELS = 5;
    private static final int PSK_UNKNOWN = 0;
    private static final int PSK_WPA = 1;
    private static final int PSK_WPA2 = 2;
    private static final int PSK_WPA_WPA2 = 3;
    public static final int SECURITY_EAP = 3;
    public static final int SECURITY_NONE = 0;
    public static final int SECURITY_PSK = 2;
    public static final int SECURITY_WAPI_CERT = 5;
    public static final int SECURITY_WAPI_PSK = 4;
    public static final int SECURITY_WEP = 1;
    public static final int SIGNAL_LEVELS = 4;
    static final String TAG = "SettingsLib.AccessPoint";
    private String bssid;
    public boolean foundInScanResult = false;
    private boolean isCurrentConnected = false;
    private AccessPointListener mAccessPointListener;
    private WifiConfiguration mConfig;
    private final Context mContext;
    private WifiInfo mInfo;
    private NetworkInfo mNetworkInfo;
    private int mRssi = Integer.MAX_VALUE;
    public LruCache<String, ScanResult> mScanResultCache = new LruCache(32);
    private long mSeen = 0;
    private Object mTag;
    private String mWAPIASCertFile;
    private String mWAPIUserCertFile;
    private int networkId = -1;
    private int pskType = 0;
    private int security;
    private String ssid;
    private int wapiPskType;

    public interface AccessPointListener {
        void onAccessPointChanged(AccessPoint accessPoint);

        void onLevelChanged(AccessPoint accessPoint);
    }

    public AccessPoint(Context context, Bundle savedState) {
        this.mContext = context;
        this.mConfig = (WifiConfiguration) savedState.getParcelable(KEY_CONFIG);
        if (this.mConfig != null) {
            loadConfig(this.mConfig);
        }
        if (savedState.containsKey(KEY_SSID)) {
            this.ssid = savedState.getString(KEY_SSID);
        }
        if (savedState.containsKey(KEY_SECURITY)) {
            this.security = savedState.getInt(KEY_SECURITY);
        }
        if (savedState.containsKey(KEY_PSKTYPE)) {
            this.pskType = savedState.getInt(KEY_PSKTYPE);
        }
        this.mInfo = (WifiInfo) savedState.getParcelable(KEY_WIFIINFO);
        if (savedState.containsKey(KEY_NETWORKINFO)) {
            this.mNetworkInfo = (NetworkInfo) savedState.getParcelable(KEY_NETWORKINFO);
        }
        if (savedState.containsKey(KEY_SCANRESULTCACHE)) {
            ArrayList<ScanResult> scanResultArrayList = savedState.getParcelableArrayList(KEY_SCANRESULTCACHE);
            this.mScanResultCache.evictAll();
            for (ScanResult result : scanResultArrayList) {
                this.mScanResultCache.put(result.BSSID, result);
            }
        }
        if (savedState.containsKey(KEY_ACTIVE)) {
            this.isCurrentConnected = savedState.getInt(KEY_ACTIVE) == 1;
        }
        update(this.mConfig, this.mInfo, this.mNetworkInfo);
        this.mRssi = getRssi();
        this.mSeen = getSeen();
    }

    AccessPoint(Context context, ScanResult result) {
        this.mContext = context;
        initWithScanResult(result);
    }

    AccessPoint(Context context, WifiConfiguration config) {
        this.mContext = context;
        loadConfig(config);
    }

    public int compareTo(@NonNull AccessPoint other) {
        if (isActive() && !other.isActive()) {
            return -1;
        }
        if (!isActive() && other.isActive()) {
            return 1;
        }
        if (this.mRssi != Integer.MAX_VALUE && other.mRssi == Integer.MAX_VALUE) {
            return -1;
        }
        if (this.mRssi == Integer.MAX_VALUE && other.mRssi != Integer.MAX_VALUE) {
            return 1;
        }
        if (this.networkId != -1 && other.networkId == -1) {
            return -1;
        }
        if (this.networkId == -1 && other.networkId != -1) {
            return 1;
        }
        int difference = getOemLevel(other.mRssi) - getOemLevel(this.mRssi);
        if (difference != 0) {
            return difference;
        }
        return this.ssid.compareToIgnoreCase(other.ssid);
    }

    public boolean equals(Object other) {
        boolean z = false;
        if (!(other instanceof AccessPoint)) {
            return false;
        }
        if (compareTo((AccessPoint) other) == 0) {
            z = true;
        }
        return z;
    }

    public int hashCode() {
        int result = 0;
        if (this.mInfo != null) {
            result = (this.mInfo.hashCode() * 13) + 0;
        }
        return ((result + (this.mRssi * 19)) + (this.networkId * 23)) + (this.ssid.hashCode() * 29);
    }

    public String toString() {
        StringBuilder builder = new StringBuilder().append("AccessPoint(").append(this.ssid);
        if (isSaved()) {
            builder.append(',').append("saved");
        }
        if (isActive()) {
            builder.append(',').append("active");
        }
        if (isEphemeral()) {
            builder.append(',').append("ephemeral");
        }
        if (isConnectable()) {
            builder.append(',').append("connectable");
        }
        if (this.security != 0) {
            builder.append(',').append(securityToString(this.security, this.pskType));
        }
        return builder.append(')').toString();
    }

    public boolean matches(ScanResult result) {
        return this.ssid.equals(result.SSID) && this.security == getSecurity(result);
    }

    public boolean matches(WifiConfiguration config) {
        boolean z = true;
        if (config.isPasspoint() && this.mConfig != null && this.mConfig.isPasspoint()) {
            return config.FQDN.equals(this.mConfig.FQDN);
        }
        if (!this.ssid.equals(removeDoubleQuotes(config.SSID)) || this.security != getSecurity(config)) {
            z = false;
        } else if (!(this.mConfig == null || this.mConfig.shared == config.shared)) {
            z = false;
        }
        return z;
    }

    public WifiConfiguration getConfig() {
        return this.mConfig;
    }

    public void clearConfig() {
        this.mConfig = null;
        this.networkId = -1;
    }

    public WifiInfo getInfo() {
        return this.mInfo;
    }

    private int getOemLevel(int rssi) {
        int level = WifiManager.calculateSignalLevel(rssi, 5);
        if (level > 0) {
            return level - 1;
        }
        return level;
    }

    private boolean isValiableConnectedBssid() {
        if (!this.isCurrentConnected || this.bssid == null || this.bssid.equals("00:00:00:00:00:00")) {
            return false;
        }
        return true;
    }

    public int getLevel() {
        if (this.mRssi == Integer.MAX_VALUE) {
            return -1;
        }
        return getOemLevel(this.mRssi);
    }

    public int getRssi() {
        int rssi = ExploreByTouchHelper.INVALID_ID;
        for (ScanResult result : this.mScanResultCache.snapshot().values()) {
            if (isValiableConnectedBssid()) {
                if (this.bssid.equals(result.BSSID) && result.level > rssi) {
                    rssi = result.level;
                }
            } else if (result.level > rssi) {
                rssi = result.level;
            }
        }
        return rssi;
    }

    public long getSeen() {
        long seen = 0;
        for (ScanResult result : this.mScanResultCache.snapshot().values()) {
            if (result.timestamp > seen) {
                seen = result.timestamp;
            }
        }
        return seen;
    }

    public NetworkInfo getNetworkInfo() {
        return this.mNetworkInfo;
    }

    public int getSecurity() {
        return this.security;
    }

    public String getSecurityString(boolean concise) {
        Context context = this.mContext;
        String string;
        if (this.mConfig == null || !this.mConfig.isPasspoint()) {
            switch (this.security) {
                case 1:
                    if (concise) {
                        string = context.getString(R.string.wifi_security_short_wep);
                    } else {
                        string = context.getString(R.string.wifi_security_wep);
                    }
                    return string;
                case 2:
                    switch (this.pskType) {
                        case 1:
                            if (concise) {
                                string = context.getString(R.string.wifi_security_short_wpa);
                            } else {
                                string = context.getString(R.string.wifi_security_wpa);
                            }
                            return string;
                        case 2:
                            if (concise) {
                                string = context.getString(R.string.wifi_security_short_wpa2);
                            } else {
                                string = context.getString(R.string.wifi_security_wpa2);
                            }
                            return string;
                        case 3:
                            if (concise) {
                                string = context.getString(R.string.wifi_security_short_wpa_wpa2);
                            } else {
                                string = context.getString(R.string.wifi_security_wpa_wpa2);
                            }
                            return string;
                        default:
                            if (concise) {
                                string = context.getString(R.string.wifi_security_short_psk_generic);
                            } else {
                                string = context.getString(R.string.wifi_security_psk_generic);
                            }
                            return string;
                    }
                case 3:
                    if (concise) {
                        string = context.getString(R.string.wifi_security_short_eap);
                    } else {
                        string = context.getString(R.string.wifi_security_eap);
                    }
                    return string;
                case 4:
                    if (concise) {
                        string = context.getString(R.string.wifi_security_short_WAPI_PSK);
                    } else {
                        string = context.getString(R.string.wifi_security_WAPI_PSK);
                    }
                    return string;
                case 5:
                    if (concise) {
                        string = context.getString(R.string.wifi_security_short_WAPI_CERT);
                    } else {
                        string = context.getString(R.string.wifi_security_WAPI_CERT);
                    }
                    return string;
                default:
                    if (concise) {
                        string = "";
                    } else {
                        string = context.getString(R.string.wifi_security_none);
                    }
                    return string;
            }
        }
        if (concise) {
            string = context.getString(R.string.wifi_security_short_eap);
        } else {
            string = context.getString(R.string.wifi_security_eap);
        }
        return string;
    }

    public String getSsidStr() {
        return this.ssid;
    }

    public String getBssid() {
        return this.bssid;
    }

    public CharSequence getSsid() {
        SpannableString str = new SpannableString(this.ssid);
        str.setSpan(new VerbatimBuilder(this.ssid).build(), 0, this.ssid.length(), 18);
        return str;
    }

    public String getConfigName() {
        if (this.mConfig == null || !this.mConfig.isPasspoint()) {
            return this.ssid;
        }
        return this.mConfig.providerFriendlyName;
    }

    public DetailedState getDetailedState() {
        if (this.mNetworkInfo != null && this.isCurrentConnected) {
            return this.mNetworkInfo.getDetailedState();
        }
        Log.w(TAG, "NetworkInfo is null, cannot return detailed state");
        return null;
    }

    public String getSavedNetworkSummary() {
        WifiConfiguration config = this.mConfig;
        if (config != null) {
            String systemName = this.mContext.getPackageManager().getNameForUid(1000);
            int userId = UserHandle.getUserId(config.creatorUid);
            ApplicationInfo appInfo = null;
            if (config.creatorName == null || !config.creatorName.equals(systemName)) {
                try {
                    appInfo = AppGlobals.getPackageManager().getApplicationInfo(config.creatorName, 0, userId);
                } catch (RemoteException e) {
                }
            } else {
                appInfo = this.mContext.getApplicationInfo();
            }
            if (!(appInfo == null || appInfo.packageName.equals(this.mContext.getString(R.string.settings_package)) || appInfo.packageName.equals(this.mContext.getString(R.string.certinstaller_package)))) {
                return this.mContext.getString(R.string.saved_network, new Object[]{appInfo.loadLabel(pm)});
            }
        }
        return "";
    }

    public String getSummary() {
        return getSettingsSummary(this.mConfig);
    }

    public String getSettingsSummary() {
        return getSettingsSummary(this.mConfig);
    }

    private String getSettingsSummary(WifiConfiguration config) {
        StringBuilder summary = new StringBuilder();
        if (isActive() && config != null && config.isPasspoint()) {
            summary.append(getSummary(this.mContext, getDetailedState(), false, config.providerFriendlyName));
        } else if (isActive()) {
            summary.append(getSummary(this.mContext, getDetailedState(), this.mInfo != null ? this.mInfo.isEphemeral() : false));
        } else if (config != null && config.isPasspoint()) {
            summary.append(String.format(this.mContext.getString(R.string.available_via_passpoint), new Object[]{config.providerFriendlyName}));
        } else if (config == null || !config.hasNoInternetAccess()) {
            if (config != null && !config.getNetworkSelectionStatus().isNetworkEnabled()) {
                switch (config.getNetworkSelectionStatus().getNetworkSelectionDisableReason()) {
                    case 2:
                        summary.append(this.mContext.getString(R.string.wifi_disabled_generic));
                        break;
                    case 3:
                        summary.append(this.mContext.getString(R.string.wifi_disabled_password_failure));
                        break;
                    case 4:
                    case 5:
                        summary.append(this.mContext.getString(R.string.wifi_disabled_network_failure));
                        break;
                    default:
                        break;
                }
            } else if (this.mRssi == Integer.MAX_VALUE) {
                summary.append(this.mContext.getString(R.string.wifi_not_in_range));
            } else if (config != null) {
                summary.append(this.mContext.getString(R.string.wifi_remembered));
            }
        } else {
            int messageID;
            if (config.getNetworkSelectionStatus().isNetworkPermanentlyDisabled()) {
                messageID = R.string.wifi_no_internet_no_reconnect;
            } else {
                messageID = R.string.wifi_no_internet;
            }
            summary.append(this.mContext.getString(messageID));
        }
        if (WifiTracker.sVerboseLogging > 0) {
            if (!(this.mInfo == null || this.mNetworkInfo == null || !this.isCurrentConnected)) {
                summary.append(" f=").append(Integer.toString(this.mInfo.getFrequency()));
            }
            summary.append(" ").append(getVisibilityStatus());
            if (!(config == null || config.getNetworkSelectionStatus().isNetworkEnabled())) {
                summary.append(" (").append(config.getNetworkSelectionStatus().getNetworkStatusString());
                if (config.getNetworkSelectionStatus().getDisableTime() > 0) {
                    long diff = (System.currentTimeMillis() - config.getNetworkSelectionStatus().getDisableTime()) / 1000;
                    long sec = diff % 60;
                    long min = (diff / 60) % 60;
                    long hour = (min / 60) % 60;
                    summary.append(", ");
                    if (hour > 0) {
                        summary.append(Long.toString(hour)).append("h ");
                    }
                    summary.append(Long.toString(min)).append("m ");
                    summary.append(Long.toString(sec)).append("s ");
                }
                summary.append(")");
            }
            if (config != null) {
                NetworkSelectionStatus networkStatus = config.getNetworkSelectionStatus();
                for (int index = 0; index < 10; index++) {
                    if (networkStatus.getDisableReasonCounter(index) != 0) {
                        summary.append(" ").append(NetworkSelectionStatus.getNetworkDisableReasonString(index)).append("=").append(networkStatus.getDisableReasonCounter(index));
                    }
                }
            }
        }
        return summary.toString();
    }

    private String getVisibilityStatus() {
        StringBuilder visibility = new StringBuilder();
        StringBuilder scans24GHz = null;
        StringBuilder scans5GHz = null;
        Object obj = null;
        long now = System.currentTimeMillis();
        if (this.mInfo != null) {
            obj = this.mInfo.getBSSID();
            if (obj != null) {
                visibility.append(" ").append(obj);
            }
            visibility.append(" rssi=").append(this.mInfo.getRssi());
            visibility.append(" ");
            visibility.append(" score=").append(this.mInfo.score);
            visibility.append(String.format(" tx=%.1f,", new Object[]{Double.valueOf(this.mInfo.txSuccessRate)}));
            visibility.append(String.format("%.1f,", new Object[]{Double.valueOf(this.mInfo.txRetriesRate)}));
            visibility.append(String.format("%.1f ", new Object[]{Double.valueOf(this.mInfo.txBadRate)}));
            visibility.append(String.format("rx=%.1f", new Object[]{Double.valueOf(this.mInfo.rxSuccessRate)}));
        }
        int rssi5 = WifiConfiguration.INVALID_RSSI;
        int rssi24 = WifiConfiguration.INVALID_RSSI;
        int num5 = 0;
        int num24 = 0;
        int n24 = 0;
        int n5 = 0;
        for (ScanResult result : this.mScanResultCache.snapshot().values()) {
            if (result.frequency >= 4900 && result.frequency <= 5900) {
                num5++;
            } else if (result.frequency >= 2400 && result.frequency <= 2500) {
                num24++;
            }
            if (result.frequency >= 4900 && result.frequency <= 5900) {
                if (result.level > rssi5) {
                    rssi5 = result.level;
                }
                if (n5 < 4) {
                    if (scans5GHz == null) {
                        scans5GHz = new StringBuilder();
                    }
                    scans5GHz.append(" \n{").append(result.BSSID);
                    if (obj != null && result.BSSID.equals(obj)) {
                        scans5GHz.append("*");
                    }
                    scans5GHz.append("=").append(result.frequency);
                    scans5GHz.append(",").append(result.level);
                    scans5GHz.append("}");
                    n5++;
                }
            } else if (result.frequency >= 2400 && result.frequency <= 2500) {
                if (result.level > rssi24) {
                    rssi24 = result.level;
                }
                if (n24 < 4) {
                    if (scans24GHz == null) {
                        scans24GHz = new StringBuilder();
                    }
                    scans24GHz.append(" \n{").append(result.BSSID);
                    if (obj != null && result.BSSID.equals(obj)) {
                        scans24GHz.append("*");
                    }
                    scans24GHz.append("=").append(result.frequency);
                    scans24GHz.append(",").append(result.level);
                    scans24GHz.append("}");
                    n24++;
                }
            }
        }
        visibility.append(" [");
        if (num24 > 0) {
            visibility.append("(").append(num24).append(")");
            if (n24 > 4) {
                visibility.append("max=").append(rssi24);
                if (scans24GHz != null) {
                    visibility.append(",").append(scans24GHz.toString());
                }
            } else if (scans24GHz != null) {
                visibility.append(scans24GHz.toString());
            }
        }
        visibility.append(";");
        if (num5 > 0) {
            visibility.append("(").append(num5).append(")");
            if (n5 > 4) {
                visibility.append("max=").append(rssi5);
                if (scans5GHz != null) {
                    visibility.append(",").append(scans5GHz.toString());
                }
            } else if (scans5GHz != null) {
                visibility.append(scans5GHz.toString());
            }
        }
        visibility.append("]");
        return visibility.toString();
    }

    public boolean isActive() {
        if (this.mNetworkInfo == null) {
            return false;
        }
        if (this.networkId == -1 && this.mNetworkInfo.getState() == State.DISCONNECTED) {
            return false;
        }
        return this.isCurrentConnected;
    }

    public boolean isConnectable() {
        return getLevel() != -1 && getDetailedState() == null;
    }

    public boolean isEphemeral() {
        if (this.mInfo == null || !this.mInfo.isEphemeral() || this.mNetworkInfo == null || !this.isCurrentConnected || this.mNetworkInfo.getState() == State.DISCONNECTED) {
            return false;
        }
        return true;
    }

    public boolean isPasspoint() {
        return this.mConfig != null ? this.mConfig.isPasspoint() : false;
    }

    private boolean isInfoForThisAccessPoint(WifiConfiguration config, WifiInfo info) {
        if (!isPasspoint() && this.networkId != -1) {
            return this.networkId == info.getNetworkId();
        } else if (config != null) {
            return matches(config);
        } else {
            return this.ssid.equals(removeDoubleQuotes(info.getSSID()));
        }
    }

    public boolean isSaved() {
        return this.networkId != -1;
    }

    public Object getTag() {
        return this.mTag;
    }

    public void setTag(Object tag) {
        this.mTag = tag;
    }

    public void generateOpenNetworkConfig() {
        if (this.security != 0) {
            throw new IllegalStateException();
        } else if (this.mConfig == null) {
            this.mConfig = new WifiConfiguration();
            this.mConfig.SSID = convertToQuotedString(this.ssid);
            this.mConfig.allowedKeyManagement.set(0);
        }
    }

    void loadConfig(WifiConfiguration config) {
        if (config.isPasspoint()) {
            this.ssid = config.providerFriendlyName;
        } else {
            this.ssid = config.SSID == null ? "" : removeDoubleQuotes(config.SSID);
        }
        this.bssid = config.BSSID;
        this.security = getSecurity(config);
        this.networkId = config.networkId;
        this.mConfig = config;
        this.wapiPskType = config.wapiPskType;
        Log.e(TAG, "loadConfig() ssid:" + this.ssid + "  WAPI PSK key type: " + this.wapiPskType);
    }

    private void initWithScanResult(ScanResult result) {
        this.ssid = result.SSID;
        this.bssid = result.BSSID;
        this.security = getSecurity(result);
        if (this.security == 2) {
            this.pskType = getPskType(result);
        }
        this.mRssi = result.level;
        this.mSeen = result.timestamp;
    }

    public void saveWifiState(Bundle savedState) {
        int i;
        if (this.ssid != null) {
            savedState.putString(KEY_SSID, getSsidStr());
        }
        savedState.putInt(KEY_SECURITY, this.security);
        savedState.putInt(KEY_PSKTYPE, this.pskType);
        if (this.mConfig != null) {
            savedState.putParcelable(KEY_CONFIG, this.mConfig);
        }
        savedState.putParcelable(KEY_WIFIINFO, this.mInfo);
        savedState.putParcelableArrayList(KEY_SCANRESULTCACHE, new ArrayList(this.mScanResultCache.snapshot().values()));
        if (this.mNetworkInfo != null) {
            savedState.putParcelable(KEY_NETWORKINFO, this.mNetworkInfo);
        }
        String str = KEY_ACTIVE;
        if (this.isCurrentConnected) {
            i = 1;
        } else {
            i = 0;
        }
        savedState.putInt(str, i);
    }

    public void setListener(AccessPointListener listener) {
        this.mAccessPointListener = listener;
    }

    boolean update(ScanResult result) {
        if (!matches(result)) {
            return false;
        }
        int newRssi;
        this.mScanResultCache.get(result.BSSID);
        this.mScanResultCache.put(result.BSSID, result);
        int oldLevel = getLevel();
        int oldRssi = getRssi();
        if (!isValiableConnectedBssid()) {
            newRssi = result.level;
        } else if (this.bssid.equals(result.BSSID)) {
            newRssi = result.level;
        } else {
            newRssi = oldRssi;
        }
        this.mSeen = getSeen();
        this.mRssi = (newRssi + oldRssi) / 2;
        int newLevel = WifiManager.calculateSignalLevel(this.mRssi, 5);
        if (!(newLevel <= 0 || newLevel == oldLevel || this.mAccessPointListener == null)) {
            this.mAccessPointListener.onLevelChanged(this);
        }
        if (this.security == 2) {
            this.pskType = getPskType(result);
        }
        if (this.mAccessPointListener != null) {
            this.mAccessPointListener.onAccessPointChanged(this);
        }
        return true;
    }

    boolean update(WifiConfiguration config, WifiInfo info, NetworkInfo networkInfo) {
        boolean reorder = false;
        if (info != null && isInfoForThisAccessPoint(config, info)) {
            reorder = !this.isCurrentConnected;
            this.mRssi = info.getRssi();
            this.mInfo = info;
            this.mNetworkInfo = networkInfo;
            this.isCurrentConnected = true;
            this.bssid = info.getBSSID();
            if (this.mAccessPointListener != null) {
                this.mAccessPointListener.onAccessPointChanged(this);
            }
        } else if (this.mInfo != null) {
            reorder = true;
            if (this.isCurrentConnected) {
                this.isCurrentConnected = false;
            }
            if (this.mAccessPointListener != null) {
                this.mAccessPointListener.onAccessPointChanged(this);
            }
        }
        return reorder;
    }

    void update(WifiConfiguration config) {
        this.mConfig = config;
        this.networkId = config.networkId;
        if (this.mAccessPointListener != null) {
            this.mAccessPointListener.onAccessPointChanged(this);
        }
    }

    void setRssi(int rssi) {
        this.mRssi = rssi;
    }

    public static String getSummary(Context context, String ssid, DetailedState state, boolean isEphemeral, String passpointProvider) {
        if (state == DetailedState.CONNECTED && ssid == null) {
            if (!TextUtils.isEmpty(passpointProvider)) {
                return String.format(context.getString(R.string.connected_via_passpoint), new Object[]{passpointProvider});
            } else if (isEphemeral) {
                return context.getString(R.string.connected_via_wfa);
            }
        }
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService("connectivity");
        if (state == DetailedState.CONNECTED) {
            Network currentNetwork;
            try {
                currentNetwork = Stub.asInterface(ServiceManager.getService("wifi")).getCurrentNetwork();
            } catch (RemoteException e) {
                currentNetwork = null;
            }
            NetworkCapabilities nc = cm.getNetworkCapabilities(currentNetwork);
            if (!(nc == null || nc.hasCapability(16))) {
                return context.getString(R.string.wifi_connected_no_internet);
            }
        }
        if (state == null) {
            Log.w(TAG, "state is null, returning empty summary");
            return "";
        }
        String[] formats = context.getResources().getStringArray(ssid == null ? R.array.wifi_status : R.array.wifi_status_with_ssid);
        int index = state.ordinal();
        if (index >= formats.length || formats[index].length() == 0) {
            return "";
        }
        return String.format(formats[index], new Object[]{ssid});
    }

    public static String getSummary(Context context, DetailedState state, boolean isEphemeral) {
        return getSummary(context, null, state, isEphemeral, null);
    }

    public static String getSummary(Context context, DetailedState state, boolean isEphemeral, String passpointProvider) {
        return getSummary(context, null, state, isEphemeral, passpointProvider);
    }

    public static String convertToQuotedString(String string) {
        return "\"" + string + "\"";
    }

    private static int getPskType(ScanResult result) {
        boolean wpa = result.capabilities.contains("WPA-PSK");
        boolean wpa2 = result.capabilities.contains("WPA2-PSK");
        if (wpa2 && wpa) {
            return 3;
        }
        if (wpa2) {
            return 2;
        }
        if (wpa) {
            return 1;
        }
        Log.w(TAG, "Received abnormal flag string: " + result.capabilities);
        return 0;
    }

    private static int getSecurity(ScanResult result) {
        if (result.capabilities.contains("WEP")) {
            return 1;
        }
        if (result.capabilities.contains("PSK")) {
            return 2;
        }
        if (result.capabilities.contains("EAP")) {
            return 3;
        }
        if (result.capabilities.contains("WAPI-KEY")) {
            return 4;
        }
        if (result.capabilities.contains("WAPI-CERT")) {
            return 5;
        }
        return 0;
    }

    static int getSecurity(WifiConfiguration config) {
        int i = 1;
        if (config.allowedKeyManagement.get(1)) {
            return 2;
        }
        if (config.allowedKeyManagement.get(2) || config.allowedKeyManagement.get(3)) {
            return 3;
        }
        if (config.allowedKeyManagement.get(6)) {
            return 4;
        }
        if (config.allowedKeyManagement.get(7)) {
            return 5;
        }
        if (config.wepKeys[0] == null) {
            i = 0;
        }
        return i;
    }

    public static String securityToString(int security, int pskType) {
        if (security == 1) {
            return "WEP";
        }
        if (security == 2) {
            if (pskType == 1) {
                return "WPA";
            }
            if (pskType == 2) {
                return "WPA2";
            }
            if (pskType == 3) {
                return "WPA_WPA2";
            }
            return "PSK";
        } else if (security == 3) {
            return "EAP";
        } else {
            return "NONE";
        }
    }

    static String removeDoubleQuotes(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        int length = string.length();
        if (length > 1 && string.charAt(0) == '\"' && string.charAt(length - 1) == '\"') {
            return string.substring(1, length - 1);
        }
        return string;
    }
}
