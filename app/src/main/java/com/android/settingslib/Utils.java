package com.android.settingslib;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.os.UserManager;
import com.android.settingslib.drawable.UserIconDrawable;
import com.android.settingslib.drawable.UserIcons;
import java.text.NumberFormat;

public class Utils {
    private static String sPermissionControllerPackageName;
    private static String sServicesSystemSharedLibPackageName;
    private static String sSharedSystemSharedLibPackageName;
    private static Signature[] sSystemSignature;

    public static int getTetheringLabel(ConnectivityManager cm) {
        String[] usbRegexs = cm.getTetherableUsbRegexs();
        String[] wifiRegexs = cm.getTetherableWifiRegexs();
        String[] bluetoothRegexs = cm.getTetherableBluetoothRegexs();
        boolean usbAvailable = usbRegexs.length != 0;
        boolean wifiAvailable = wifiRegexs.length != 0;
        boolean bluetoothAvailable = bluetoothRegexs.length != 0;
        if (wifiAvailable && usbAvailable && bluetoothAvailable) {
            return R.string.tether_settings_title_all;
        }
        if (wifiAvailable && usbAvailable) {
            return R.string.tether_settings_title_all;
        }
        if (wifiAvailable && bluetoothAvailable) {
            return R.string.tether_settings_title_all;
        }
        if (wifiAvailable) {
            return R.string.tether_settings_title_wifi;
        }
        if (usbAvailable && bluetoothAvailable) {
            return R.string.tether_settings_title_usb_bluetooth;
        }
        if (usbAvailable) {
            return R.string.tether_settings_title_usb;
        }
        return R.string.tether_settings_title_bluetooth;
    }

    public static String getUserLabel(Context context, UserInfo info) {
        String str = info != null ? info.name : null;
        if (info.isManagedProfile()) {
            return context.getString(R.string.managed_user_title);
        }
        if (info.isGuest()) {
            str = context.getString(R.string.user_guest);
        }
        if (str == null && info != null) {
            str = Integer.toString(info.id);
        } else if (info == null) {
            str = context.getString(R.string.unknown);
        }
        return context.getResources().getString(R.string.running_process_item_user_label, new Object[]{str});
    }

    public static UserIconDrawable getUserIcon(Context context, UserManager um, UserInfo user) {
        int iconSize = UserIconDrawable.getSizeForList(context);
        if (user.isManagedProfile()) {
            return new UserIconDrawable(iconSize).setIcon(BitmapFactory.decodeResource(context.getResources(), 17302313)).bake();
        }
        if (user.iconPath != null) {
            Bitmap icon = um.getUserIcon(user.id);
            if (icon != null) {
                return new UserIconDrawable(iconSize).setIcon(icon).bake();
            }
        }
        return new UserIconDrawable(iconSize).setIconDrawable(UserIcons.getDefaultUserIcon(user.id, false)).bake();
    }

    public static String formatPercentage(long amount, long total) {
        return formatPercentage(((double) amount) / ((double) total));
    }

    public static String formatPercentage(int percentage) {
        return formatPercentage(((double) percentage) / 100.0d);
    }

    private static String formatPercentage(double percentage) {
        return NumberFormat.getPercentInstance().format(percentage);
    }

    public static int getBatteryLevel(Intent batteryChangedIntent) {
        int level = batteryChangedIntent.getIntExtra("level", 0);
        return (level * 100) / batteryChangedIntent.getIntExtra("scale", 100);
    }

    public static String getBatteryStatus(Resources res, Intent batteryChangedIntent) {
        return getBatteryStatus(res, batteryChangedIntent, false);
    }

    public static String getBatteryStatus(Resources res, Intent batteryChangedIntent, boolean shortString) {
        int plugType = batteryChangedIntent.getIntExtra("plugged", 0);
        int status = batteryChangedIntent.getIntExtra("status", 1);
        if (status == 2) {
            int resId;
            if (plugType == 1) {
                if (shortString) {
                    resId = R.string.battery_info_status_charging_ac_short;
                } else {
                    resId = R.string.battery_info_status_charging_ac;
                }
            } else if (plugType == 2) {
                if (shortString) {
                    resId = R.string.battery_info_status_charging_usb_short;
                } else {
                    resId = R.string.battery_info_status_charging_usb;
                }
            } else if (plugType != 4) {
                resId = R.string.battery_info_status_charging;
            } else if (shortString) {
                resId = R.string.battery_info_status_charging_wireless_short;
            } else {
                resId = R.string.battery_info_status_charging_wireless;
            }
            return res.getString(resId);
        } else if (status == 3) {
            return res.getString(R.string.battery_info_status_discharging);
        } else {
            if (status == 4) {
                return res.getString(R.string.battery_info_status_not_charging);
            }
            if (status == 5) {
                return res.getString(R.string.battery_info_status_full);
            }
            return res.getString(R.string.battery_info_status_unknown);
        }
    }

    public static int getColorAccent(Context context) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{16843829});
        int colorAccent = ta.getColor(0, 0);
        ta.recycle();
        return colorAccent;
    }

    public static boolean isSystemPackage(Resources resources, PackageManager pm, PackageInfo pkg) {
        if (sSystemSignature == null) {
            sSystemSignature = new Signature[]{getSystemSignature(pm)};
        }
        if (sPermissionControllerPackageName == null) {
            sPermissionControllerPackageName = pm.getPermissionControllerPackageName();
        }
        if (sServicesSystemSharedLibPackageName == null) {
            sServicesSystemSharedLibPackageName = pm.getServicesSystemSharedLibraryPackageName();
        }
        if (sSharedSystemSharedLibPackageName == null) {
            sSharedSystemSharedLibPackageName = pm.getSharedSystemSharedLibraryPackageName();
        }
        if ((sSystemSignature[0] != null && sSystemSignature[0].equals(getFirstSignature(pkg))) || pkg.packageName.equals(sPermissionControllerPackageName) || pkg.packageName.equals(sServicesSystemSharedLibPackageName) || pkg.packageName.equals(sSharedSystemSharedLibPackageName) || pkg.packageName.equals("com.android.printspooler")) {
            return true;
        }
        return isDeviceProvisioningPackage(resources, pkg.packageName);
    }

    private static Signature getFirstSignature(PackageInfo pkg) {
        if (pkg == null || pkg.signatures == null || pkg.signatures.length <= 0) {
            return null;
        }
        return pkg.signatures[0];
    }

    private static Signature getSystemSignature(PackageManager pm) {
        try {
            return getFirstSignature(pm.getPackageInfo("android", 64));
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    public static boolean isDeviceProvisioningPackage(Resources resources, String packageName) {
        String deviceProvisioningPackage = resources.getString(17039486);
        return deviceProvisioningPackage != null ? deviceProvisioningPackage.equals(packageName) : false;
    }
}
