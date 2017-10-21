package com.android.settingslib;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.net.Uri;
import android.net.Uri.Builder;
import android.provider.Settings.Global;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import com.android.internal.logging.MetricsLogger;
import java.net.URISyntaxException;
import java.util.Locale;

public class HelpUtils {
    private static final String EXTRA_BACKUP_URI = "EXTRA_BACKUP_URI";
    private static final String EXTRA_CONTEXT = "EXTRA_CONTEXT";
    private static final String EXTRA_PRIMARY_COLOR = "EXTRA_PRIMARY_COLOR";
    private static final String EXTRA_THEME = "EXTRA_THEME";
    private static final int MENU_HELP = 101;
    private static final String PARAM_LANGUAGE_CODE = "hl";
    private static final String PARAM_VERSION = "version";
    private static final String TAG = HelpUtils.class;
    private static String sCachedVersionCode = null;

    private HelpUtils() {
    }

    public static boolean prepareHelpMenuItem(Activity activity, Menu menu, String helpUri, String backupContext) {
        return false;
    }

    public static boolean prepareHelpMenuItem(Activity activity, Menu menu, int helpUriResource, String backupContext) {
        return false;
    }

    public static boolean prepareHelpMenuItem(final Activity activity, MenuItem helpMenuItem, String helpUriString, String backupContext) {
        if (Global.getInt(activity.getContentResolver(), "device_provisioned", 0) == 0) {
            return false;
        }
        if (TextUtils.isEmpty(helpUriString)) {
            helpMenuItem.setVisible(false);
            return false;
        }
        final Intent intent = getHelpIntent(activity, helpUriString, backupContext);
        if (intent != null) {
            helpMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    MetricsLogger.action(activity, 496, intent.getStringExtra(HelpUtils.EXTRA_CONTEXT));
                    try {
                        activity.startActivityForResult(intent, 0);
                    } catch (ActivityNotFoundException e) {
                        Log.e(HelpUtils.TAG, "No activity found for intent: " + intent);
                    }
                    return true;
                }
            });
            helpMenuItem.setShowAsAction(0);
            helpMenuItem.setVisible(true);
            return true;
        }
        helpMenuItem.setVisible(false);
        return false;
    }

    public static Intent getHelpIntent(Context context, String helpUriString, String backupContext) {
        if (Global.getInt(context.getContentResolver(), "device_provisioned", 0) == 0) {
            return null;
        }
        Intent intent;
        try {
            intent = Intent.parseUri(helpUriString, 3);
            addIntentParameters(context, intent, backupContext, true);
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                return intent;
            }
            if (intent.hasExtra(EXTRA_BACKUP_URI)) {
                return getHelpIntent(context, intent.getStringExtra(EXTRA_BACKUP_URI), backupContext);
            }
            return null;
        } catch (URISyntaxException e) {
            intent = new Intent("android.intent.action.VIEW", uriWithAddedParameters(context, Uri.parse(helpUriString)));
            intent.setFlags(276824064);
            return intent;
        }
    }

    public static void addIntentParameters(Context context, Intent intent, String backupContext, boolean sendPackageName) {
        if (!intent.hasExtra(EXTRA_CONTEXT)) {
            intent.putExtra(EXTRA_CONTEXT, backupContext);
        }
        Resources resources = context.getResources();
        boolean includePackageName = resources.getBoolean(R.bool.config_sendPackageName);
        if (sendPackageName && includePackageName) {
            String[] packageNameKey = new String[]{resources.getString(R.string.config_helpPackageNameKey)};
            String[] packageNameValue = new String[]{resources.getString(R.string.config_helpPackageNameValue)};
            String intentExtraKey = resources.getString(R.string.config_helpIntentExtraKey);
            String intentNameKey = resources.getString(R.string.config_helpIntentNameKey);
            intent.putExtra(intentExtraKey, packageNameKey);
            intent.putExtra(intentNameKey, packageNameValue);
        }
        intent.putExtra(EXTRA_THEME, 1);
        TypedArray array = context.obtainStyledAttributes(new int[]{16843827});
        intent.putExtra(EXTRA_PRIMARY_COLOR, array.getColor(0, 0));
        array.recycle();
    }

    public static Uri uriWithAddedParameters(Context context, Uri baseUri) {
        Builder builder = baseUri.buildUpon();
        builder.appendQueryParameter(PARAM_LANGUAGE_CODE, Locale.getDefault().toString());
        if (sCachedVersionCode == null) {
            try {
                sCachedVersionCode = Integer.toString(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode);
                builder.appendQueryParameter(PARAM_VERSION, sCachedVersionCode);
            } catch (NameNotFoundException e) {
                Log.wtf(TAG, "Invalid package name for context", e);
            }
        } else {
            builder.appendQueryParameter(PARAM_VERSION, sCachedVersionCode);
        }
        return builder.build();
    }
}
