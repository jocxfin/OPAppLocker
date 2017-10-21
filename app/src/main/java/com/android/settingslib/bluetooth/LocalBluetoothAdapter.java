package com.android.settingslib.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.os.ParcelUuid;
import android.support.v4.widget.ExploreByTouchHelper;
import java.util.Set;

public final class LocalBluetoothAdapter {
    private static final int SCAN_EXPIRATION_MS = 300000;
    private static final String TAG = "LocalBluetoothAdapter";
    private static LocalBluetoothAdapter sInstance;
    private final BluetoothAdapter mAdapter;
    private long mLastScan;
    private LocalBluetoothProfileManager mProfileManager;
    private int mState = ExploreByTouchHelper.INVALID_ID;

    private LocalBluetoothAdapter(BluetoothAdapter adapter) {
        this.mAdapter = adapter;
    }

    void setProfileManager(LocalBluetoothProfileManager manager) {
        this.mProfileManager = manager;
    }

    static synchronized LocalBluetoothAdapter getInstance() {
        LocalBluetoothAdapter localBluetoothAdapter;
        synchronized (LocalBluetoothAdapter.class) {
            if (sInstance == null) {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter != null) {
                    sInstance = new LocalBluetoothAdapter(adapter);
                }
            }
            localBluetoothAdapter = sInstance;
        }
        return localBluetoothAdapter;
    }

    public void cancelDiscovery() {
        this.mAdapter.cancelDiscovery();
    }

    public boolean enable() {
        return this.mAdapter.enable();
    }

    public boolean disable() {
        return this.mAdapter.disable();
    }

    void getProfileProxy(Context context, ServiceListener listener, int profile) {
        this.mAdapter.getProfileProxy(context, listener, profile);
    }

    public Set<BluetoothDevice> getBondedDevices() {
        return this.mAdapter.getBondedDevices();
    }

    public String getName() {
        return this.mAdapter.getName();
    }

    public int getScanMode() {
        return this.mAdapter.getScanMode();
    }

    public BluetoothLeScanner getBluetoothLeScanner() {
        return this.mAdapter.getBluetoothLeScanner();
    }

    public int getState() {
        return this.mAdapter.getState();
    }

    public ParcelUuid[] getUuids() {
        return this.mAdapter.getUuids();
    }

    public boolean isDiscovering() {
        return this.mAdapter.isDiscovering();
    }

    public boolean isEnabled() {
        return this.mAdapter.isEnabled();
    }

    public int getConnectionState() {
        return this.mAdapter.getConnectionState();
    }

    public void setDiscoverableTimeout(int timeout) {
        this.mAdapter.setDiscoverableTimeout(timeout);
    }

    public void setName(String name) {
        this.mAdapter.setName(name);
    }

    public void setScanMode(int mode) {
        this.mAdapter.setScanMode(mode);
    }

    public boolean setScanMode(int mode, int duration) {
        return this.mAdapter.setScanMode(mode, duration);
    }

    public void startScanning(boolean force) {
        if (!this.mAdapter.isDiscovering()) {
            if (!force) {
                if (this.mLastScan + 300000 <= System.currentTimeMillis()) {
                    A2dpProfile a2dp = this.mProfileManager.getA2dpProfile();
                    if (a2dp == null || !a2dp.isA2dpPlaying()) {
                        A2dpSinkProfile a2dpSink = this.mProfileManager.getA2dpSinkProfile();
                        if (a2dpSink != null && a2dpSink.isA2dpPlaying()) {
                            return;
                        }
                    }
                    return;
                }
                return;
            }
            if (this.mAdapter.startDiscovery()) {
                this.mLastScan = System.currentTimeMillis();
            }
        }
    }

    public void stopScanning() {
        if (this.mAdapter.isDiscovering()) {
            this.mAdapter.cancelDiscovery();
        }
    }

    public synchronized int getBluetoothState() {
        syncBluetoothState();
        return this.mState;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    void setBluetoothStateInt(int r2) {
        /*
        r1 = this;
        monitor-enter(r1);
        r0 = r1.mState;	 Catch:{ all -> 0x0018 }
        if (r0 != r2) goto L_0x0007;
    L_0x0005:
        monitor-exit(r1);
        return;
    L_0x0007:
        r1.mState = r2;	 Catch:{ all -> 0x0018 }
        monitor-exit(r1);
        r0 = 12;
        if (r2 != r0) goto L_0x0017;
    L_0x000e:
        r0 = r1.mProfileManager;
        if (r0 == 0) goto L_0x0017;
    L_0x0012:
        r0 = r1.mProfileManager;
        r0.setBluetoothStateOn();
    L_0x0017:
        return;
    L_0x0018:
        r0 = move-exception;
        monitor-exit(r1);
        throw r0;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.settingslib.bluetooth.LocalBluetoothAdapter.setBluetoothStateInt(int):void");
    }

    boolean syncBluetoothState() {
        if (this.mAdapter.getState() == this.mState) {
            return false;
        }
        setBluetoothStateInt(this.mAdapter.getState());
        return true;
    }

    public boolean setBluetoothEnabled(boolean enabled) {
        boolean success;
        if (enabled) {
            success = this.mAdapter.enable();
        } else {
            success = this.mAdapter.disable();
        }
        if (success) {
            int i;
            if (enabled) {
                i = 11;
            } else {
                i = 13;
            }
            setBluetoothStateInt(i);
        } else {
            syncBluetoothState();
        }
        return success;
    }

    public BluetoothDevice getRemoteDevice(String address) {
        return this.mAdapter.getRemoteDevice(address);
    }
}
