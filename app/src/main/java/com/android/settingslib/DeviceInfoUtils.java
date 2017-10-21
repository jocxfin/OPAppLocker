package com.android.settingslib;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.Build.VERSION;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeviceInfoUtils {
    private static final String FILENAME_MSV = "/sys/board_properties/soc/msv";
    private static final String FILENAME_PROC_VERSION = "/proc/version";
    private static final String TAG = "DeviceInfoUtils";

    private static String readLine(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename), 256);
        try {
            String readLine = reader.readLine();
            return readLine;
        } finally {
            reader.close();
        }
    }

    public static String getFormattedKernelVersion() {
        try {
            return formatKernelVersion(readLine(FILENAME_PROC_VERSION));
        } catch (IOException e) {
            Log.e(TAG, "IO Exception when getting kernel version for Device Info screen", e);
            return "Unavailable";
        }
    }

    public static String formatKernelVersion(String rawKernelVersion) {
        String PROC_VERSION_REGEX = "Linux version (\\S+) \\((\\S+?)\\) (?:\\(gcc.+? \\)) (#\\d+) (?:.*?)?((Sun|Mon|Tue|Wed|Thu|Fri|Sat).+)";
        Matcher m = Pattern.compile("Linux version (\\S+) \\((\\S+?)\\) (?:\\(gcc.+? \\)) (#\\d+) (?:.*?)?((Sun|Mon|Tue|Wed|Thu|Fri|Sat).+)").matcher(rawKernelVersion);
        if (!m.matches()) {
            Log.e(TAG, "Regex did not match on /proc/version: " + rawKernelVersion);
            return "Unavailable";
        } else if (m.groupCount() >= 4) {
            return m.group(1) + "\n" + m.group(2) + " " + m.group(3) + "\n" + m.group(4);
        } else {
            Log.e(TAG, "Regex match on /proc/version only returned " + m.groupCount() + " groups");
            return "Unavailable";
        }
    }

    public static String customizeFormatKernelVersion(boolean hideVersionName) {
        if (!hideVersionName) {
            return getFormattedKernelVersion();
        }
        try {
            String strVersion = readLine(FILENAME_PROC_VERSION);
            String PROC_VERSION_REGEX = "Linux version (\\S+) \\((\\S+?)\\) (?:\\(gcc.+? \\)) (#\\d+) (?:.*?)?((Sun|Mon|Tue|Wed|Thu|Fri|Sat).+)";
            Matcher m = Pattern.compile("Linux version (\\S+) \\((\\S+?)\\) (?:\\(gcc.+? \\)) (#\\d+) (?:.*?)?((Sun|Mon|Tue|Wed|Thu|Fri|Sat).+)").matcher(strVersion);
            if (!m.matches()) {
                Log.e(TAG, "Regex did not match on /proc/version: " + strVersion);
                return "Unavailable";
            } else if (m.groupCount() >= 4) {
                return m.group(1) + "\n" + m.group(4);
            } else {
                Log.e(TAG, "Regex match on /proc/version only returned " + m.groupCount() + " groups");
                return "Unavailable";
            }
        } catch (IOException e) {
            Log.e(TAG, "IO Exception when getting kernel version for Device Info screen", e);
            return "Unavailable";
        }
    }

    public static String getMsvSuffix() {
        try {
            if (Long.parseLong(readLine(FILENAME_MSV), 16) == 0) {
                return " (ENGINEERING)";
            }
        } catch (IOException e) {
        }
        return "";
    }

    public static String getFeedbackReporterPackage(Context context) {
        String feedbackReporter = context.getResources().getString(R.string.oem_preferred_feedback_reporter);
        if (TextUtils.isEmpty(feedbackReporter)) {
            return feedbackReporter;
        }
        Intent intent = new Intent("android.intent.action.BUG_REPORT");
        PackageManager pm = context.getPackageManager();
        for (ResolveInfo info : pm.queryIntentActivities(intent, 64)) {
            if (!(info.activityInfo == null || TextUtils.isEmpty(info.activityInfo.packageName))) {
                try {
                    if ((pm.getApplicationInfo(info.activityInfo.packageName, 0).flags & 1) != 0 && TextUtils.equals(info.activityInfo.packageName, feedbackReporter)) {
                        return feedbackReporter;
                    }
                } catch (NameNotFoundException e) {
                }
            }
        }
        return null;
    }

    public static String getSecurityPatch() {
        String patch = VERSION.SECURITY_PATCH;
        if ("".equals(patch)) {
            return null;
        }
        try {
            patch = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMMMyyyy"), new SimpleDateFormat("yyyy-MM-dd").parse(patch)).toString();
        } catch (ParseException e) {
        }
        return patch;
    }

    public static String getFormattedPhoneNumber(Context context, SubscriptionInfo subscriptionInfo) {
        if (subscriptionInfo == null) {
            return null;
        }
        String rawNumber = ((TelephonyManager) context.getSystemService("phone")).getLine1Number(subscriptionInfo.getSubscriptionId());
        if (TextUtils.isEmpty(rawNumber)) {
            return null;
        }
        return PhoneNumberUtils.formatNumber(rawNumber);
    }

    public static String getFormattedPhoneNumbers(Context context, List<SubscriptionInfo> subscriptionInfo) {
        StringBuilder sb = new StringBuilder();
        if (subscriptionInfo != null) {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService("phone");
            int count = subscriptionInfo.size();
            for (int i = 0; i < count; i++) {
                String rawNumber = telephonyManager.getLine1Number(((SubscriptionInfo) subscriptionInfo.get(i)).getSubscriptionId());
                if (!TextUtils.isEmpty(rawNumber)) {
                    sb.append(PhoneNumberUtils.formatNumber(rawNumber));
                    if (i < count - 1) {
                        sb.append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }
}
