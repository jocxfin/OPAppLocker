package com.android.settingslib;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.telephony.CarrierConfigManager;

public class TetherUtil {
    public static boolean setWifiTethering(boolean enable, Context context) {
        return ((WifiManager) context.getSystemService("wifi")).setWifiApEnabled(null, enable);
    }

    private static boolean isEntitlementCheckRequired(Context context) {
        try {
            return ((CarrierConfigManager) context.getSystemService("carrier_config")).getConfig().getBoolean("require_entitlement_checks_bool");
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public static boolean isProvisioningNeeded(android.content.Context r4) {
        /*
        r1 = 0;
        r2 = r4.getResources();
        r3 = 17235994; // 0x107001a float:2.4795657E-38 double:8.5157125E-317;
        r0 = r2.getStringArray(r3);
        r2 = "net.tethering.noprovisioning";
        r2 = android.os.SystemProperties.getBoolean(r2, r1);
        if (r2 != 0) goto L_0x0017;
    L_0x0015:
        if (r0 != 0) goto L_0x0018;
    L_0x0017:
        return r1;
    L_0x0018:
        r2 = isEntitlementCheckRequired(r4);
        if (r2 != 0) goto L_0x001f;
    L_0x001e:
        return r1;
    L_0x001f:
        r2 = r0.length;
        r3 = 2;
        if (r2 != r3) goto L_0x0024;
    L_0x0023:
        r1 = 1;
    L_0x0024:
        return r1;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.settingslib.TetherUtil.isProvisioningNeeded(android.content.Context):boolean");
    }
}
