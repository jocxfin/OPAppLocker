package com.android.settingslib.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDun;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.Context;
import android.util.Log;
import com.android.settingslib.R;

final class DunServerProfile implements LocalBluetoothProfile {
    static final String NAME = "DUN Server";
    private static final int ORDINAL = 11;
    private static final String TAG = "DunServerProfile";
    private static boolean V = true;
    private boolean mIsProfileReady;
    private BluetoothDun mService;

    private final class DunServiceListener implements ServiceListener {
        private DunServiceListener() {
        }

        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (DunServerProfile.V) {
                Log.d(DunServerProfile.TAG, "Bluetooth service connected");
            }
            DunServerProfile.this.mService = (BluetoothDun) proxy;
            DunServerProfile.this.mIsProfileReady = true;
        }

        public void onServiceDisconnected(int profile) {
            if (DunServerProfile.V) {
                Log.d(DunServerProfile.TAG, "Bluetooth service disconnected");
            }
            DunServerProfile.this.mIsProfileReady = false;
        }
    }

    public boolean isProfileReady() {
        return this.mIsProfileReady;
    }

    DunServerProfile(Context context) {
        BluetoothAdapter.getDefaultAdapter().getProfileProxy(context, new DunServiceListener(), 21);
    }

    public boolean isConnectable() {
        return true;
    }

    public boolean isAutoConnectable() {
        return false;
    }

    public boolean connect(BluetoothDevice device) {
        return false;
    }

    public boolean disconnect(BluetoothDevice device) {
        if (this.mService == null) {
            return false;
        }
        return this.mService.disconnect(device);
    }

    public int getConnectionStatus(BluetoothDevice device) {
        if (this.mService == null) {
            return 0;
        }
        return this.mService.getConnectionState(device);
    }

    public boolean isPreferred(BluetoothDevice device) {
        return true;
    }

    public int getPreferred(BluetoothDevice device) {
        return -1;
    }

    public void setPreferred(BluetoothDevice device, boolean preferred) {
    }

    public String toString() {
        return NAME;
    }

    public int getOrdinal() {
        return 11;
    }

    public int getNameResource(BluetoothDevice device) {
        return R.string.bluetooth_profile_dun;
    }

    public int getSummaryResourceForDevice(BluetoothDevice device) {
        int state = getConnectionStatus(device);
        switch (state) {
            case 0:
                return R.string.bluetooth_dun_profile_summary_use_for;
            case 2:
                return R.string.bluetooth_dun_profile_summary_connected;
            default:
                return Utils.getConnectionStateSummary(state);
        }
    }

    public int getDrawableResource(BluetoothClass btClass) {
        return R.drawable.ic_bt_network_pan;
    }

    protected void finalize() {
        if (V) {
            Log.d(TAG, "finalize()");
        }
        if (this.mService != null) {
            try {
                BluetoothAdapter.getDefaultAdapter().closeProfileProxy(21, this.mService);
                this.mService = null;
            } catch (Throwable t) {
                Log.w(TAG, "Error cleaning up DUN proxy", t);
            }
        }
    }
}
