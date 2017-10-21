package com.android.settingslib.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkRequest;
import android.net.NetworkRequest.Builder;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings.Global;
import android.widget.Toast;
import com.android.settingslib.R;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

public class WifiTracker {
    private static final boolean DBG = false;
    private static final int NUM_SCANS_TO_CONFIRM_AP_LOSS = 3;
    private static final String TAG = "WifiTracker";
    private static final int WIFI_RESCAN_INTERVAL_MS = 10000;
    public static int sVerboseLogging = 0;
    private ArrayList<AccessPoint> mAccessPoints;
    private final AtomicBoolean mConnected;
    private final ConnectivityManager mConnectivityManager;
    private final Context mContext;
    private final IntentFilter mFilter;
    private final boolean mIncludePasspoints;
    private final boolean mIncludeSaved;
    private final boolean mIncludeScans;
    private WifiInfo mLastInfo;
    private NetworkInfo mLastNetworkInfo;
    private final WifiListener mListener;
    private final MainHandler mMainHandler;
    private WifiTrackerNetworkCallback mNetworkCallback;
    private final NetworkRequest mNetworkRequest;
    final BroadcastReceiver mReceiver;
    private boolean mRegistered;
    private boolean mSavedNetworksExist;
    private Integer mScanId;
    private HashMap<String, ScanResult> mScanResultCache;
    Scanner mScanner;
    private HashMap<String, Integer> mSeenBssids;
    private final WifiManager mWifiManager;
    private final WorkHandler mWorkHandler;

    private final class MainHandler extends Handler {
        private static final int MSG_ACCESS_POINT_CHANGED = 2;
        private static final int MSG_CONNECTED_CHANGED = 0;
        private static final int MSG_PAUSE_SCANNING = 4;
        private static final int MSG_RESUME_SCANNING = 3;
        private static final int MSG_WIFI_STATE_CHANGED = 1;

        public MainHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            if (WifiTracker.this.mListener != null) {
                switch (msg.what) {
                    case 0:
                        WifiTracker.this.mListener.onConnectedChanged();
                        break;
                    case 1:
                        WifiTracker.this.mListener.onWifiStateChanged(msg.arg1);
                        break;
                    case 2:
                        WifiTracker.this.mListener.onAccessPointsChanged();
                        break;
                    case 3:
                        if (WifiTracker.this.mScanner != null) {
                            WifiTracker.this.mScanner.resume();
                            break;
                        }
                        break;
                    case 4:
                        if (WifiTracker.this.mScanner != null) {
                            WifiTracker.this.mScanner.pause();
                            break;
                        }
                        break;
                }
            }
        }
    }

    private static class Multimap<K, V> {
        private final HashMap<K, List<V>> store;

        private Multimap() {
            this.store = new HashMap();
        }

        List<V> getAll(K key) {
            List<V> values = (List) this.store.get(key);
            return values != null ? values : Collections.emptyList();
        }

        void put(K key, V val) {
            List<V> curVals = (List) this.store.get(key);
            if (curVals == null) {
                curVals = new ArrayList(3);
                this.store.put(key, curVals);
            }
            curVals.add(val);
        }
    }

    class Scanner extends Handler {
        static final int MSG_SCAN = 0;
        private int mRetry = 0;

        Scanner() {
        }

        void resume() {
            if (!hasMessages(0)) {
                sendEmptyMessage(0);
            }
        }

        void forceScan() {
            removeMessages(0);
            sendEmptyMessage(0);
        }

        void pause() {
            this.mRetry = 0;
            removeMessages(0);
        }

        boolean isScanning() {
            return hasMessages(0);
        }

        public void handleMessage(Message message) {
            if (message.what == 0) {
                if (WifiTracker.this.mWifiManager.startScan()) {
                    this.mRetry = 0;
                } else {
                    int i = this.mRetry + 1;
                    this.mRetry = i;
                    if (i >= 3) {
                        this.mRetry = 0;
                        if (WifiTracker.this.mContext != null) {
                            Toast.makeText(WifiTracker.this.mContext, R.string.wifi_fail_to_scan, 1).show();
                        }
                        return;
                    }
                }
                sendEmptyMessageDelayed(0, 10000);
            }
        }
    }

    public interface WifiListener {
        void onAccessPointsChanged();

        void onConnectedChanged();

        void onWifiStateChanged(int i);
    }

    private final class WifiTrackerNetworkCallback extends NetworkCallback {
        private WifiTrackerNetworkCallback() {
        }

        public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
            if (network.equals(WifiTracker.this.mWifiManager.getCurrentNetwork())) {
                WifiTracker.this.mWorkHandler.sendEmptyMessage(1);
            }
        }
    }

    private final class WorkHandler extends Handler {
        private static final int MSG_RESUME = 2;
        private static final int MSG_UPDATE_ACCESS_POINTS = 0;
        private static final int MSG_UPDATE_NETWORK_INFO = 1;
        private static final int MSG_UPDATE_WIFI_STATE = 3;

        public WorkHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    WifiTracker.this.updateAccessPoints();
                    return;
                case 1:
                    WifiTracker.this.updateNetworkInfo((NetworkInfo) msg.obj);
                    return;
                case 2:
                    WifiTracker.this.handleResume();
                    return;
                case 3:
                    if (msg.arg1 != 3) {
                        WifiTracker.this.mLastInfo = null;
                        WifiTracker.this.mLastNetworkInfo = null;
                        if (WifiTracker.this.mScanner != null) {
                            WifiTracker.this.mScanner.pause();
                        }
                    } else if (WifiTracker.this.mScanner != null) {
                        WifiTracker.this.mScanner.resume();
                    }
                    WifiTracker.this.mMainHandler.obtainMessage(1, msg.arg1, 0).sendToTarget();
                    return;
                default:
                    return;
            }
        }
    }

    public WifiTracker(Context context, WifiListener wifiListener, boolean includeSaved, boolean includeScans) {
        this(context, wifiListener, null, includeSaved, includeScans);
    }

    public WifiTracker(Context context, WifiListener wifiListener, Looper workerLooper, boolean includeSaved, boolean includeScans) {
        this(context, wifiListener, workerLooper, includeSaved, includeScans, DBG);
    }

    public WifiTracker(Context context, WifiListener wifiListener, boolean includeSaved, boolean includeScans, boolean includePasspoints) {
        this(context, wifiListener, null, includeSaved, includeScans, includePasspoints);
    }

    public WifiTracker(Context context, WifiListener wifiListener, Looper workerLooper, boolean includeSaved, boolean includeScans, boolean includePasspoints) {
        this(context, wifiListener, workerLooper, includeSaved, includeScans, includePasspoints, (WifiManager) context.getSystemService(WifiManager.class), (ConnectivityManager) context.getSystemService(ConnectivityManager.class), Looper.myLooper());
    }

    WifiTracker(Context context, WifiListener wifiListener, Looper workerLooper, boolean includeSaved, boolean includeScans, boolean includePasspoints, WifiManager wifiManager, ConnectivityManager connectivityManager, Looper currentLooper) {
        this.mConnected = new AtomicBoolean(DBG);
        this.mAccessPoints = new ArrayList();
        this.mSeenBssids = new HashMap();
        this.mScanResultCache = new HashMap();
        this.mScanId = Integer.valueOf(0);
        this.mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("android.net.wifi.WIFI_STATE_CHANGED".equals(action)) {
                    WifiTracker.this.updateWifiState(intent.getIntExtra("wifi_state", 4));
                } else if ("android.net.wifi.SCAN_RESULTS".equals(action) || "android.net.wifi.CONFIGURED_NETWORKS_CHANGE".equals(action) || "android.net.wifi.LINK_CONFIGURATION_CHANGED".equals(action)) {
                    WifiTracker.this.mWorkHandler.sendEmptyMessage(0);
                } else if ("android.net.wifi.STATE_CHANGE".equals(action)) {
                    NetworkInfo info = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                    WifiTracker.this.mConnected.set(info.isConnected());
                    WifiTracker.this.mMainHandler.sendEmptyMessage(0);
                    WifiTracker.this.mWorkHandler.sendEmptyMessage(0);
                    WifiTracker.this.mWorkHandler.obtainMessage(1, info).sendToTarget();
                } else if ("Auth_password_wrong".equals(action)) {
                    Toast.makeText(context, R.string.wifi_auth_password_wrong, 0).show();
                }
            }
        };
        if (includeSaved || includeScans) {
            this.mContext = context;
            if (currentLooper == null) {
                currentLooper = Looper.getMainLooper();
            }
            this.mMainHandler = new MainHandler(currentLooper);
            if (workerLooper == null) {
                workerLooper = currentLooper;
            }
            this.mWorkHandler = new WorkHandler(workerLooper);
            this.mWifiManager = wifiManager;
            this.mIncludeSaved = includeSaved;
            this.mIncludeScans = includeScans;
            this.mIncludePasspoints = includePasspoints;
            this.mListener = wifiListener;
            this.mConnectivityManager = connectivityManager;
            sVerboseLogging = this.mWifiManager.getVerboseLoggingLevel();
            this.mFilter = new IntentFilter();
            this.mFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
            this.mFilter.addAction("android.net.wifi.SCAN_RESULTS");
            this.mFilter.addAction("android.net.wifi.NETWORK_IDS_CHANGED");
            this.mFilter.addAction("android.net.wifi.supplicant.STATE_CHANGE");
            this.mFilter.addAction("android.net.wifi.CONFIGURED_NETWORKS_CHANGE");
            this.mFilter.addAction("android.net.wifi.LINK_CONFIGURATION_CHANGED");
            this.mFilter.addAction("android.net.wifi.STATE_CHANGE");
            this.mFilter.addAction("Auth_password_wrong");
            this.mNetworkRequest = new Builder().clearCapabilities().addTransportType(1).build();
            return;
        }
        throw new IllegalArgumentException("Must include either saved or scans");
    }

    private boolean isScanAlwaysAvailable() {
        return Global.getInt(this.mContext.getContentResolver(), "wifi_scan_always_enabled", 0) == 1 ? true : DBG;
    }

    public void forceUpdate() {
        updateAccessPoints();
    }

    public void forceScan() {
        if (this.mWifiManager.isWifiEnabled() && this.mScanner != null) {
            this.mScanner.forceScan();
        }
    }

    public void pauseScanning() {
        if (this.mScanner != null) {
            this.mScanner.pause();
            this.mScanner = null;
        }
    }

    public void resumeScanning() {
        if (this.mScanner == null) {
            this.mScanner = new Scanner();
        }
        this.mWorkHandler.sendEmptyMessage(2);
        if (this.mWifiManager.isWifiEnabled()) {
            this.mScanner.resume();
        } else if (!isScanAlwaysAvailable()) {
            this.mAccessPoints.clear();
        }
        this.mWorkHandler.sendEmptyMessage(0);
    }

    public void startTracking() {
        resumeScanning();
        if (!this.mRegistered) {
            this.mContext.registerReceiver(this.mReceiver, this.mFilter);
            this.mNetworkCallback = new WifiTrackerNetworkCallback();
            this.mConnectivityManager.registerNetworkCallback(this.mNetworkRequest, this.mNetworkCallback);
            this.mRegistered = true;
        }
    }

    public void stopTracking() {
        if (this.mRegistered) {
            this.mWorkHandler.removeMessages(0);
            this.mWorkHandler.removeMessages(1);
            this.mContext.unregisterReceiver(this.mReceiver);
            this.mConnectivityManager.unregisterNetworkCallback(this.mNetworkCallback);
            this.mRegistered = DBG;
        }
        pauseScanning();
    }

    public List<AccessPoint> getAccessPoints() {
        List arrayList;
        synchronized (this.mAccessPoints) {
            arrayList = new ArrayList(this.mAccessPoints);
        }
        return arrayList;
    }

    public WifiManager getManager() {
        return this.mWifiManager;
    }

    public boolean isWifiEnabled() {
        return this.mWifiManager.isWifiEnabled();
    }

    public boolean doSavedNetworksExist() {
        return this.mSavedNetworksExist;
    }

    public boolean isConnected() {
        return this.mConnected.get();
    }

    public void dump(PrintWriter pw) {
        pw.println("  - wifi tracker ------");
        for (AccessPoint accessPoint : getAccessPoints()) {
            pw.println("  " + accessPoint);
        }
    }

    private void handleResume() {
        this.mScanResultCache.clear();
        this.mSeenBssids.clear();
        this.mScanId = Integer.valueOf(0);
    }

    private Collection<ScanResult> fetchScanResults() {
        this.mScanId = Integer.valueOf(this.mScanId.intValue() + 1);
        for (ScanResult newResult : this.mWifiManager.getScanResults()) {
            if (!(newResult.SSID == null || newResult.SSID.isEmpty())) {
                this.mScanResultCache.put(newResult.BSSID, newResult);
                this.mSeenBssids.put(newResult.BSSID, this.mScanId);
            }
        }
        if (this.mScanId.intValue() > 3) {
            Integer threshold = Integer.valueOf(this.mScanId.intValue() - 3);
            Iterator<Entry<String, Integer>> it = this.mSeenBssids.entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, Integer> e = (Entry) it.next();
                if (((Integer) e.getValue()).intValue() < threshold.intValue()) {
                    ScanResult result = (ScanResult) this.mScanResultCache.get(e.getKey());
                    this.mScanResultCache.remove(e.getKey());
                    it.remove();
                }
            }
        }
        return this.mScanResultCache.values();
    }

    private WifiConfiguration getWifiConfigurationForNetworkId(int networkId) {
        List<WifiConfiguration> configs = this.mWifiManager.getConfiguredNetworks();
        if (configs != null) {
            for (WifiConfiguration config : configs) {
                if (this.mLastInfo != null && networkId == config.networkId) {
                    if (!config.selfAdded || config.numAssociation != 0) {
                        return config;
                    }
                }
            }
        }
        return null;
    }

    private void updateAccessPoints() {
        AccessPoint accessPoint;
        WifiConfiguration config;
        boolean found;
        List<AccessPoint> cachedAccessPoints = getAccessPoints();
        ArrayList<AccessPoint> accessPoints = new ArrayList();
        for (AccessPoint accessPoint2 : cachedAccessPoints) {
            accessPoint2.clearConfig();
        }
        Multimap<String, AccessPoint> apMap = new Multimap();
        WifiConfiguration connectionConfig = null;
        if (this.mLastInfo != null) {
            connectionConfig = getWifiConfigurationForNetworkId(this.mLastInfo.getNetworkId());
        }
        Collection<ScanResult> results = fetchScanResults();
        try {
            List<WifiConfiguration> configs = this.mWifiManager.getConfiguredNetworks();
            if (configs != null) {
                this.mSavedNetworksExist = configs.size() != 0 ? true : DBG;
                for (WifiConfiguration config2 : configs) {
                    if (!config2.selfAdded || config2.numAssociation != 0) {
                        accessPoint2 = getCachedOrCreate(config2, (List) cachedAccessPoints);
                        accessPoint2.foundInScanResult = DBG;
                        if (!(this.mLastInfo == null || this.mLastNetworkInfo == null || config2.isPasspoint())) {
                            accessPoint2.update(connectionConfig, this.mLastInfo, this.mLastNetworkInfo);
                        }
                        if (this.mIncludeSaved) {
                            if (!config2.isPasspoint() || this.mIncludePasspoints) {
                                boolean apFound = DBG;
                                for (ScanResult result : results) {
                                    if (result.SSID.equals(accessPoint2.getSsidStr())) {
                                        apFound = true;
                                        break;
                                    }
                                }
                                if (!apFound) {
                                    accessPoint2.setRssi(Integer.MAX_VALUE);
                                }
                                accessPoints.add(accessPoint2);
                            }
                            if (!config2.isPasspoint()) {
                                apMap.put(accessPoint2.getSsidStr(), accessPoint2);
                            }
                        } else {
                            cachedAccessPoints.add(accessPoint2);
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
        if (results != null) {
            for (ScanResult result2 : results) {
                if (!(result2.SSID == null || result2.SSID.length() == 0 || result2.capabilities.contains("[IBSS]"))) {
                    found = DBG;
                    for (AccessPoint accessPoint22 : apMap.getAll(result2.SSID)) {
                        if (accessPoint22.update(result2)) {
                            accessPoint22.foundInScanResult = true;
                            found = true;
                            break;
                        }
                    }
                    if (!found && this.mIncludeScans) {
                        accessPoint22 = getCachedOrCreate(result2, (List) cachedAccessPoints);
                        if (!(this.mLastInfo == null || this.mLastNetworkInfo == null)) {
                            accessPoint22.update(connectionConfig, this.mLastInfo, this.mLastNetworkInfo);
                        }
                        if (result2.isPasspointNetwork()) {
                            config2 = this.mWifiManager.getMatchingWifiConfig(result2);
                            if (config2 != null && config2.SSID.equals(result2.SSID)) {
                                accessPoint22.update(config2);
                            }
                        }
                        if (!(this.mLastInfo == null || this.mLastInfo.getBSSID() == null || !this.mLastInfo.getBSSID().equals(result2.BSSID) || connectionConfig == null || !connectionConfig.isPasspoint())) {
                            accessPoint22.update(connectionConfig);
                        }
                        accessPoints.add(accessPoint22);
                        apMap.put(accessPoint22.getSsidStr(), accessPoint22);
                    }
                }
            }
        }
        Collections.sort(accessPoints);
        for (AccessPoint prevAccessPoint : this.mAccessPoints) {
            if (prevAccessPoint.getSsid() != null) {
                String prevSsid = prevAccessPoint.getSsidStr();
                found = DBG;
                for (AccessPoint newAccessPoint : accessPoints) {
                    if (newAccessPoint.getSsid() != null && newAccessPoint.getSsid().equals(prevSsid)) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                }
            }
        }
        this.mAccessPoints = accessPoints;
        this.mMainHandler.sendEmptyMessage(2);
    }

    private AccessPoint getCachedOrCreate(ScanResult result, List<AccessPoint> cache) {
        int N = cache.size();
        for (int i = 0; i < N; i++) {
            if (((AccessPoint) cache.get(i)).matches(result)) {
                AccessPoint ret = (AccessPoint) cache.remove(i);
                ret.update(result);
                return ret;
            }
        }
        return new AccessPoint(this.mContext, result);
    }

    private AccessPoint getCachedOrCreate(WifiConfiguration config, List<AccessPoint> cache) {
        int N = cache.size();
        for (int i = 0; i < N; i++) {
            if (((AccessPoint) cache.get(i)).matches(config)) {
                AccessPoint ret = (AccessPoint) cache.remove(i);
                ret.loadConfig(config);
                return ret;
            }
        }
        return new AccessPoint(this.mContext, config);
    }

    private void updateNetworkInfo(NetworkInfo networkInfo) {
        if (this.mWifiManager.isWifiEnabled()) {
            if (networkInfo == null || networkInfo.getDetailedState() != DetailedState.OBTAINING_IPADDR) {
                this.mMainHandler.sendEmptyMessage(3);
            } else {
                this.mMainHandler.sendEmptyMessage(4);
            }
            if (networkInfo != null) {
                this.mLastNetworkInfo = networkInfo;
            }
            WifiConfiguration connectionConfig = null;
            this.mLastInfo = this.mWifiManager.getConnectionInfo();
            if (this.mLastInfo != null) {
                connectionConfig = getWifiConfigurationForNetworkId(this.mLastInfo.getNetworkId());
            }
            boolean reorder = DBG;
            for (int i = this.mAccessPoints.size() - 1; i >= 0; i--) {
                if (((AccessPoint) this.mAccessPoints.get(i)).update(connectionConfig, this.mLastInfo, this.mLastNetworkInfo)) {
                    reorder = true;
                }
            }
            if (reorder) {
                synchronized (this.mAccessPoints) {
                    Collections.sort(this.mAccessPoints);
                }
                this.mMainHandler.sendEmptyMessage(2);
            }
            return;
        }
        this.mMainHandler.sendEmptyMessage(4);
    }

    private void updateWifiState(int state) {
        this.mWorkHandler.obtainMessage(3, state, 0).sendToTarget();
    }

    public static List<AccessPoint> getCurrentAccessPoints(Context context, boolean includeSaved, boolean includeScans, boolean includePasspoints) {
        WifiTracker tracker = new WifiTracker(context, null, null, includeSaved, includeScans, includePasspoints);
        tracker.forceUpdate();
        return tracker.getAccessPoints();
    }
}
