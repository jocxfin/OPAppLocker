package com.android.settingslib.bluetooth;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.support.v4.widget.ExploreByTouchHelper;
import android.text.TextUtils;
import android.util.Log;
import com.android.settingslib.R;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class BluetoothEventManager {
    private static final String TAG = "BluetoothEventManager";
    private final IntentFilter mAdapterIntentFilter;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
            Handler handler = (Handler) BluetoothEventManager.this.mHandlerMap.get(intent.getAction());
            if (handler != null) {
                handler.onReceive(context, intent, device);
            }
        }
    };
    private final Collection<BluetoothCallback> mCallbacks = new ArrayList();
    private Context mContext;
    private final CachedBluetoothDeviceManager mDeviceManager;
    private final Map<String, Handler> mHandlerMap;
    private final LocalBluetoothAdapter mLocalAdapter;
    private final BroadcastReceiver mProfileBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
            Handler handler = (Handler) BluetoothEventManager.this.mHandlerMap.get(intent.getAction());
            if (handler != null) {
                handler.onReceive(context, intent, device);
            }
        }
    };
    private final IntentFilter mProfileIntentFilter;
    private LocalBluetoothProfileManager mProfileManager;
    private android.os.Handler mReceiverHandler;

    interface Handler {
        void onReceive(Context context, Intent intent, BluetoothDevice bluetoothDevice);
    }

    private class AdapterStateChangedHandler implements Handler {
        private AdapterStateChangedHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", ExploreByTouchHelper.INVALID_ID);
            if (state == 10) {
                context.unregisterReceiver(BluetoothEventManager.this.mProfileBroadcastReceiver);
                BluetoothEventManager.this.registerProfileIntentReceiver();
            }
            BluetoothEventManager.this.mLocalAdapter.setBluetoothStateInt(state);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(BluetoothEventManager.this.mContext);
            boolean isFirstBoot = preferences.getBoolean("is_first_boot", true);
            Log.d(BluetoothEventManager.TAG, "isFirstBoot: " + isFirstBoot + " state: " + state);
            if (isFirstBoot && state == 12) {
                BluetoothEventManager.this.setDefaultBtName();
                Editor edit = preferences.edit();
                edit.putBoolean("is_first_boot", false);
                edit.apply();
            }
            synchronized (BluetoothEventManager.this.mCallbacks) {
                for (BluetoothCallback callback : BluetoothEventManager.this.mCallbacks) {
                    callback.onBluetoothStateChanged(state);
                }
            }
            BluetoothEventManager.this.mDeviceManager.onBluetoothStateChanged(state);
        }
    }

    private class BondStateChangedHandler implements Handler {
        private BondStateChangedHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            if (device == null) {
                Log.e(BluetoothEventManager.TAG, "ACTION_BOND_STATE_CHANGED with no EXTRA_DEVICE");
                return;
            }
            int bondState = intent.getIntExtra("android.bluetooth.device.extra.BOND_STATE", ExploreByTouchHelper.INVALID_ID);
            CachedBluetoothDevice cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
            if (cachedDevice == null) {
                Log.w(BluetoothEventManager.TAG, "CachedBluetoothDevice for device " + device + " not found, calling readPairedDevices().");
                if (BluetoothEventManager.this.readPairedDevices()) {
                    cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
                    if (cachedDevice == null) {
                        Log.e(BluetoothEventManager.TAG, "Got bonding state changed for " + device + ", but device not added in cache.");
                        return;
                    }
                }
                Log.e(BluetoothEventManager.TAG, "Got bonding state changed for " + device + ", but we have no record of that device.");
                return;
            }
            synchronized (BluetoothEventManager.this.mCallbacks) {
                for (BluetoothCallback callback : BluetoothEventManager.this.mCallbacks) {
                    callback.onDeviceBondStateChanged(cachedDevice, bondState);
                }
            }
            cachedDevice.onBondingStateChanged(bondState);
            if (bondState == 10) {
                showUnbondMessage(context, cachedDevice.getName(), intent.getIntExtra("android.bluetooth.device.extra.REASON", ExploreByTouchHelper.INVALID_ID));
            }
        }

        private void showUnbondMessage(Context context, String name, int reason) {
            int errorMsg;
            switch (reason) {
                case 1:
                    errorMsg = R.string.bluetooth_pairing_pin_error_message;
                    break;
                case 2:
                    errorMsg = R.string.bluetooth_pairing_rejected_error_message;
                    break;
                case 4:
                    errorMsg = R.string.bluetooth_pairing_device_down_error_message;
                    break;
                case 5:
                case 6:
                case 7:
                case 8:
                    errorMsg = R.string.bluetooth_pairing_error_message;
                    break;
                default:
                    Log.w(BluetoothEventManager.TAG, "showUnbondMessage: Not displaying any message for reason: " + reason);
                    return;
            }
            Utils.showError(context, name, errorMsg);
        }
    }

    private class ClassChangedHandler implements Handler {
        private ClassChangedHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            BluetoothEventManager.this.mDeviceManager.onBtClassChanged(device);
        }
    }

    private class ConnectionStateChangedHandler implements Handler {
        private ConnectionStateChangedHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            BluetoothEventManager.this.dispatchConnectionStateChanged(BluetoothEventManager.this.mDeviceManager.findDevice(device), intent.getIntExtra("android.bluetooth.adapter.extra.CONNECTION_STATE", ExploreByTouchHelper.INVALID_ID));
        }
    }

    private class DeviceDisappearedHandler implements Handler {
        private DeviceDisappearedHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            CachedBluetoothDevice cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
            if (cachedDevice == null) {
                Log.w(BluetoothEventManager.TAG, "received ACTION_DISAPPEARED for an unknown device: " + device);
                return;
            }
            if (CachedBluetoothDeviceManager.onDeviceDisappeared(cachedDevice)) {
                synchronized (BluetoothEventManager.this.mCallbacks) {
                    for (BluetoothCallback callback : BluetoothEventManager.this.mCallbacks) {
                        callback.onDeviceDeleted(cachedDevice);
                    }
                }
            }
        }
    }

    private class DeviceFoundHandler implements Handler {
        private DeviceFoundHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            short rssi = intent.getShortExtra("android.bluetooth.device.extra.RSSI", Short.MIN_VALUE);
            BluetoothClass btClass = (BluetoothClass) intent.getParcelableExtra("android.bluetooth.device.extra.CLASS");
            String name = intent.getStringExtra("android.bluetooth.device.extra.NAME");
            CachedBluetoothDevice cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
            if (cachedDevice == null) {
                cachedDevice = BluetoothEventManager.this.mDeviceManager.addDevice(BluetoothEventManager.this.mLocalAdapter, BluetoothEventManager.this.mProfileManager, device);
                Log.d(BluetoothEventManager.TAG, "DeviceFoundHandler created new CachedBluetoothDevice: " + cachedDevice);
                BluetoothEventManager.this.dispatchDeviceAdded(cachedDevice);
            }
            cachedDevice.setRssi(rssi);
            cachedDevice.setBtClass(btClass);
            cachedDevice.setNewName(name);
            cachedDevice.setVisible(true);
        }
    }

    private class DockEventHandler implements Handler {
        private DockEventHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            if (intent.getIntExtra("android.intent.extra.DOCK_STATE", 1) == 0 && device != null && device.getBondState() == 10) {
                CachedBluetoothDevice cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
                if (cachedDevice != null) {
                    cachedDevice.setVisible(false);
                }
            }
        }
    }

    private class NameChangedHandler implements Handler {
        private NameChangedHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            BluetoothEventManager.this.mDeviceManager.onDeviceNameUpdated(device);
        }
    }

    private class PairingCancelHandler implements Handler {
        private PairingCancelHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            if (device == null) {
                Log.e(BluetoothEventManager.TAG, "ACTION_PAIRING_CANCEL with no EXTRA_DEVICE");
                return;
            }
            CachedBluetoothDevice cachedDevice = BluetoothEventManager.this.mDeviceManager.findDevice(device);
            if (cachedDevice == null) {
                Log.e(BluetoothEventManager.TAG, "ACTION_PAIRING_CANCEL with no cached device");
                return;
            }
            int errorMsg = R.string.bluetooth_pairing_error_message;
            if (!(context == null || cachedDevice == null)) {
                Utils.showError(context, cachedDevice.getName(), errorMsg);
            }
        }
    }

    private class ScanningStateChangedHandler implements Handler {
        private final boolean mStarted;

        ScanningStateChangedHandler(boolean started) {
            this.mStarted = started;
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            synchronized (BluetoothEventManager.this.mCallbacks) {
                for (BluetoothCallback callback : BluetoothEventManager.this.mCallbacks) {
                    callback.onScanningStateChanged(this.mStarted);
                }
            }
            BluetoothEventManager.this.mDeviceManager.onScanningStateChanged(this.mStarted);
        }
    }

    private class UuidChangedHandler implements Handler {
        private UuidChangedHandler() {
        }

        public void onReceive(Context context, Intent intent, BluetoothDevice device) {
            BluetoothEventManager.this.mDeviceManager.onUuidChanged(device);
        }
    }

    private void addHandler(String action, Handler handler) {
        this.mHandlerMap.put(action, handler);
        this.mAdapterIntentFilter.addAction(action);
    }

    void addProfileHandler(String action, Handler handler) {
        this.mHandlerMap.put(action, handler);
        this.mProfileIntentFilter.addAction(action);
    }

    void setProfileManager(LocalBluetoothProfileManager manager) {
        this.mProfileManager = manager;
    }

    BluetoothEventManager(LocalBluetoothAdapter adapter, CachedBluetoothDeviceManager deviceManager, Context context) {
        this.mLocalAdapter = adapter;
        this.mDeviceManager = deviceManager;
        this.mAdapterIntentFilter = new IntentFilter();
        this.mProfileIntentFilter = new IntentFilter();
        this.mHandlerMap = new HashMap();
        this.mContext = context;
        addHandler("android.bluetooth.adapter.action.STATE_CHANGED", new AdapterStateChangedHandler());
        addHandler("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED", new ConnectionStateChangedHandler());
        addHandler("android.bluetooth.adapter.action.DISCOVERY_STARTED", new ScanningStateChangedHandler(true));
        addHandler("android.bluetooth.adapter.action.DISCOVERY_FINISHED", new ScanningStateChangedHandler(false));
        addHandler("android.bluetooth.device.action.FOUND", new DeviceFoundHandler());
        addHandler("android.bluetooth.device.action.DISAPPEARED", new DeviceDisappearedHandler());
        addHandler("android.bluetooth.device.action.NAME_CHANGED", new NameChangedHandler());
        addHandler("android.bluetooth.device.action.ALIAS_CHANGED", new NameChangedHandler());
        addHandler("android.bluetooth.device.action.BOND_STATE_CHANGED", new BondStateChangedHandler());
        addHandler("android.bluetooth.device.action.PAIRING_CANCEL", new PairingCancelHandler());
        addHandler("android.bluetooth.device.action.CLASS_CHANGED", new ClassChangedHandler());
        addHandler("android.bluetooth.device.action.UUID", new UuidChangedHandler());
        addHandler("android.intent.action.DOCK_EVENT", new DockEventHandler());
        this.mContext.registerReceiver(this.mBroadcastReceiver, this.mAdapterIntentFilter, null, this.mReceiverHandler);
        this.mContext.registerReceiver(this.mProfileBroadcastReceiver, this.mProfileIntentFilter, null, this.mReceiverHandler);
    }

    void registerProfileIntentReceiver() {
        this.mContext.registerReceiver(this.mProfileBroadcastReceiver, this.mProfileIntentFilter, null, this.mReceiverHandler);
    }

    public void setReceiverHandler(android.os.Handler handler) {
        this.mContext.unregisterReceiver(this.mBroadcastReceiver);
        this.mContext.unregisterReceiver(this.mProfileBroadcastReceiver);
        this.mReceiverHandler = handler;
        this.mContext.registerReceiver(this.mBroadcastReceiver, this.mAdapterIntentFilter, null, this.mReceiverHandler);
        registerProfileIntentReceiver();
    }

    private void setDefaultBtName() {
        String name = this.mContext.getResources().getString(17039491);
        Log.d(TAG, "custom bluetooth name: " + name);
        if (!TextUtils.isEmpty(name)) {
            this.mLocalAdapter.setName(name);
        }
    }

    public void registerCallback(BluetoothCallback callback) {
        synchronized (this.mCallbacks) {
            this.mCallbacks.add(callback);
        }
    }

    public void unregisterCallback(BluetoothCallback callback) {
        synchronized (this.mCallbacks) {
            this.mCallbacks.remove(callback);
        }
    }

    private void dispatchConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state) {
        synchronized (this.mCallbacks) {
            for (BluetoothCallback callback : this.mCallbacks) {
                callback.onConnectionStateChanged(cachedDevice, state);
            }
        }
    }

    void dispatchDeviceAdded(CachedBluetoothDevice cachedDevice) {
        synchronized (this.mCallbacks) {
            for (BluetoothCallback callback : this.mCallbacks) {
                callback.onDeviceAdded(cachedDevice);
            }
        }
    }

    boolean readPairedDevices() {
        Set<BluetoothDevice> bondedDevices = this.mLocalAdapter.getBondedDevices();
        if (bondedDevices == null) {
            return false;
        }
        boolean deviceAdded = false;
        for (BluetoothDevice device : bondedDevices) {
            if (this.mDeviceManager.findDevice(device) == null) {
                dispatchDeviceAdded(this.mDeviceManager.addDevice(this.mLocalAdapter, this.mProfileManager, device));
                deviceAdded = true;
            }
        }
        return deviceAdded;
    }
}
