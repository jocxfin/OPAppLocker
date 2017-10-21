package com.android.settingslib.drawer;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings.Global;
import android.text.TextUtils;
import android.util.Log;
import android.util.OpFeatures;
import android.util.Pair;
import com.android.settingslib.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TileUtils {
    private static final Comparator<DashboardCategory> CATEGORY_COMPARATOR = new Comparator<DashboardCategory>() {
        public int compare(DashboardCategory lhs, DashboardCategory rhs) {
            return rhs.priority - lhs.priority;
        }
    };
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_TIMING = false;
    private static final String EXTRA_CATEGORY_KEY = "com.android.settings.category";
    private static final String EXTRA_SETTINGS_ACTION = "com.android.settings.action.EXTRA_SETTINGS";
    private static final String FRAGMENT_KEY = "com.android.settings.FRAGMENT_CLASS";
    private static final String GOOGLE_PACKAGE_NAME = "com.google.android.gms";
    private static final String LOG_TAG = "TileUtils";
    private static final String MANUFACTURER_DEFAULT_CATEGORY = "com.android.settings.category.device";
    private static final String MANUFACTURER_SETTINGS = "com.android.settings.MANUFACTURER_APPLICATION_SETTING";
    public static final String META_DATA_PREFERENCE_ICON = "com.android.settings.icon";
    public static final String META_DATA_PREFERENCE_SUMMARY = "com.android.settings.summary";
    public static final String META_DATA_PREFERENCE_TITLE = "com.android.settings.title";
    private static final String ONEPLUS_SETUPWIZARD = "com.oneplus.setupwizard";
    private static final String OPERATOR_DEFAULT_CATEGORY = "com.android.settings.category.wireless";
    private static final String OPERATOR_SETTINGS = "com.android.settings.OPERATOR_APPLICATION_SETTING";
    private static final String PRIVACYSETTINGS_VALUE = "com.android.settings.PrivacySettings";
    private static final String SETTINGS_ACTION = "com.android.settings.action.SETTINGS";
    private static final String SETTINGS_CUSTOM_CATAGORY = "com.android.settings.category.custom";
    private static final String SETTING_PKG = "com.android.settings";
    public static final Comparator<Tile> TILE_COMPARATOR = new Comparator<Tile>() {
        public int compare(Tile lhs, Tile rhs) {
            return rhs.priority - lhs.priority;
        }
    };

    public static List<DashboardCategory> getCategories(Context context, HashMap<Pair<String, String>, Tile> cache) {
        long startTime = System.currentTimeMillis();
        boolean setup = Global.getInt(context.getContentResolver(), "device_provisioned", 0) != 0;
        ArrayList<Tile> tiles = new ArrayList();
        for (UserHandle user : UserManager.get(context).getUserProfiles()) {
            if (user.getIdentifier() == ActivityManager.getCurrentUser()) {
                getTilesForAction(context, user, SETTINGS_ACTION, cache, null, tiles, true);
                getTilesForAction(context, user, SETTINGS_CUSTOM_CATAGORY, cache, null, tiles, true);
                getTilesForAction(context, user, OPERATOR_SETTINGS, cache, OPERATOR_DEFAULT_CATEGORY, tiles, false);
                getTilesForAction(context, user, MANUFACTURER_SETTINGS, cache, MANUFACTURER_DEFAULT_CATEGORY, tiles, false);
            }
            if (setup) {
                getTilesForAction(context, user, EXTRA_SETTINGS_ACTION, cache, null, tiles, false);
            }
        }
        HashMap<String, DashboardCategory> categoryMap = new HashMap();
        for (Tile tile : tiles) {
            DashboardCategory category = (DashboardCategory) categoryMap.get(tile.category);
            if (category == null) {
                category = createCategory(context, tile.category);
                if (category == null) {
                    Log.w(LOG_TAG, "Couldn't find category " + tile.category);
                } else {
                    categoryMap.put(category.key, category);
                }
            }
            category.addTile(tile);
        }
        ArrayList<DashboardCategory> categories = new ArrayList(categoryMap.values());
        for (DashboardCategory category2 : categories) {
            Collections.sort(category2.tiles, TILE_COMPARATOR);
        }
        Collections.sort(categories, CATEGORY_COMPARATOR);
        return categories;
    }

    private static DashboardCategory createCategory(Context context, String categoryKey) {
        DashboardCategory category = new DashboardCategory();
        category.key = categoryKey;
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> results = pm.queryIntentActivities(new Intent(categoryKey), 0);
        if (results.size() == 0) {
            return null;
        }
        for (ResolveInfo resolved : results) {
            if (resolved.system) {
                int i;
                category.title = resolved.activityInfo.loadLabel(pm);
                if (SETTING_PKG.equals(resolved.activityInfo.applicationInfo.packageName)) {
                    i = resolved.priority;
                } else {
                    i = 0;
                }
                category.priority = i;
            }
        }
        return category;
    }

    private static void getTilesForAction(Context context, UserHandle user, String action, Map<Pair<String, String>, Tile> addedCache, String defaultCategory, ArrayList<Tile> outTiles, boolean requireSettings) {
        Intent intent = new Intent(action);
        if (requireSettings) {
            intent.setPackage(SETTING_PKG);
        }
        getTilesForIntent(context, user, intent, addedCache, defaultCategory, outTiles, requireSettings, true);
    }

    public static void getTilesForIntent(Context context, UserHandle user, Intent intent, Map<Pair<String, String>, Tile> addedCache, String defaultCategory, List<Tile> outTiles, boolean usePriority, boolean checkCategory) {
        PackageManager pm = context.getPackageManager();
        for (ResolveInfo resolved : pm.queryIntentActivitiesAsUser(intent, 128, user.getIdentifier())) {
            if (!ONEPLUS_SETUPWIZARD.equals(resolved.activityInfo.packageName) && resolved.system) {
                ActivityInfo activityInfo = resolved.activityInfo;
                Bundle metaData = activityInfo.metaData;
                String categoryKey = defaultCategory;
                if (!checkCategory || ((metaData != null && metaData.containsKey(EXTRA_CATEGORY_KEY)) || defaultCategory != null)) {
                    categoryKey = metaData.getString(EXTRA_CATEGORY_KEY);
                    Pair<String, String> key = new Pair(activityInfo.packageName, activityInfo.name);
                    Tile tile = (Tile) addedCache.get(key);
                    if (tile == null) {
                        tile = new Tile();
                        tile.intent = new Intent().setClassName(activityInfo.packageName, activityInfo.name);
                        tile.category = categoryKey;
                        tile.priority = usePriority ? resolved.priority : 0;
                        tile.metaData = activityInfo.metaData;
                        updateTileData(context, tile, activityInfo, activityInfo.applicationInfo, pm);
                        addedCache.put(key, tile);
                    }
                    if (!tile.userHandle.contains(user)) {
                        tile.userHandle.add(user);
                    }
                    if (!outTiles.contains(tile)) {
                        outTiles.add(tile);
                    }
                } else {
                    Log.w(LOG_TAG, "Found " + resolved.activityInfo.name + " for intent " + intent + " missing metadata " + (metaData == null ? "" : EXTRA_CATEGORY_KEY));
                }
            }
        }
    }

    private static DashboardCategory getCategory(List<DashboardCategory> target, String categoryKey) {
        for (DashboardCategory category : target) {
            if (categoryKey.equals(category.key)) {
                return category;
            }
        }
        return null;
    }

    private static boolean updateTileData(Context context, Tile tile, ActivityInfo activityInfo, ApplicationInfo applicationInfo, PackageManager pm) {
        if (!applicationInfo.isSystemApp()) {
            return false;
        }
        int icon = 0;
        CharSequence charSequence = null;
        CharSequence charSequence2 = null;
        Object obj = null;
        try {
            Resources res = pm.getResourcesForApplication(applicationInfo.packageName);
            Bundle metaData = activityInfo.metaData;
            obj = (String) metaData.get(FRAGMENT_KEY);
            if (!(res == null || metaData == null)) {
                if (metaData.containsKey(META_DATA_PREFERENCE_ICON)) {
                    icon = metaData.getInt(META_DATA_PREFERENCE_ICON);
                }
                if (metaData.containsKey(META_DATA_PREFERENCE_TITLE)) {
                    if (metaData.get(META_DATA_PREFERENCE_TITLE) instanceof Integer) {
                        charSequence = res.getString(metaData.getInt(META_DATA_PREFERENCE_TITLE));
                    } else {
                        charSequence = metaData.getString(META_DATA_PREFERENCE_TITLE);
                    }
                }
                if (metaData.containsKey(META_DATA_PREFERENCE_SUMMARY)) {
                    if (metaData.get(META_DATA_PREFERENCE_SUMMARY) instanceof Integer) {
                        charSequence2 = res.getString(metaData.getInt(META_DATA_PREFERENCE_SUMMARY));
                    } else {
                        charSequence2 = metaData.getString(META_DATA_PREFERENCE_SUMMARY);
                    }
                }
                if (PRIVACYSETTINGS_VALUE.equals(obj)) {
                    if (!OpFeatures.isSupport(new int[]{1})) {
                        charSequence = context.getText(17039663);
                    }
                }
            }
        } catch (NameNotFoundException e) {
        }
        if (TextUtils.isEmpty(charSequence)) {
            charSequence = activityInfo.loadLabel(pm).toString();
        }
        if (icon == 0) {
            icon = activityInfo.icon;
        }
        if (GOOGLE_PACKAGE_NAME.equals(activityInfo.packageName)) {
            tile.icon = Icon.createWithResource(context, R.drawable.op_ic_google);
        } else {
            tile.icon = Icon.createWithResource(activityInfo.packageName, icon);
        }
        if (PRIVACYSETTINGS_VALUE.equals(obj)) {
            if (!OpFeatures.isSupport(new int[]{1})) {
                tile.icon = Icon.createWithResource(context, R.drawable.op_ic_settings_factory_reset);
            }
        }
        tile.title = charSequence;
        tile.summary = charSequence2;
        tile.intent = new Intent().setClassName(activityInfo.packageName, activityInfo.name);
        return true;
    }
}
