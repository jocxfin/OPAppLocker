package com.android.settingslib;

import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources.NotFoundException;
import android.os.UserHandle;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.util.Xml;
import android.view.InflateException;
import com.android.settingslib.drawer.Tile;
import com.android.settingslib.drawer.TileUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class SuggestionParser {
    private static final String DISMISS_INDEX = "_dismiss_index";
    private static final String IS_DISMISSED = "_is_dismissed";
    public static final String META_DATA_DISMISS_CONTROL = "com.android.settings.dismiss";
    private static final String META_DATA_IS_SUPPORTED = "com.android.settings.is_supported";
    private static final String META_DATA_REQUIRE_ACCOUNT = "com.android.settings.require_account";
    public static final String META_DATA_REQUIRE_FEATURE = "com.android.settings.require_feature";
    private static final long MILLIS_IN_DAY = 86400000;
    private static final String SETUP_TIME = "_setup_time";
    private static final String TAG = "SuggestionParser";
    private final ArrayMap<Pair<String, String>, Tile> addCache = new ArrayMap();
    private final Context mContext;
    private final SharedPreferences mSharedPrefs;
    private final List<SuggestionCategory> mSuggestionList;

    private static class SuggestionCategory {
        public String category;
        public boolean multiple;
        public String pkg;

        private SuggestionCategory() {
        }
    }

    private static class SuggestionOrderInflater {
        private static final String ATTR_CATEGORY = "category";
        private static final String ATTR_MULTIPLE = "multiple";
        private static final String ATTR_PACKAGE = "package";
        private static final String TAG_ITEM = "step";
        private static final String TAG_LIST = "optional-steps";
        private final Context mContext;

        public SuggestionOrderInflater(Context context) {
            this.mContext = context;
        }

        public Object parse(int resource) {
            XmlPullParser parser = this.mContext.getResources().getXml(resource);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            do {
                try {
                    type = parser.next();
                    if (type == 2) {
                        break;
                    }
                } catch (Exception e) {
                    Log.w(SuggestionParser.TAG, "Problem parser resource " + resource, e);
                    return null;
                }
            } while (type != 1);
            if (type != 2) {
                throw new InflateException(parser.getPositionDescription() + ": No start tag found!");
            }
            Object xmlRoot = onCreateItem(parser.getName(), attrs);
            rParse(parser, xmlRoot, attrs);
            return xmlRoot;
        }

        private void rParse(XmlPullParser parser, Object parent, AttributeSet attrs) throws XmlPullParserException, IOException {
            int depth = parser.getDepth();
            while (true) {
                int type = parser.next();
                if ((type == 3 && parser.getDepth() <= depth) || type == 1) {
                    return;
                }
                if (type == 2) {
                    Object item = onCreateItem(parser.getName(), attrs);
                    onAddChildItem(parent, item);
                    rParse(parser, item, attrs);
                }
            }
        }

        protected void onAddChildItem(Object parent, Object child) {
            if ((parent instanceof List) && (child instanceof SuggestionCategory)) {
                ((List) parent).add((SuggestionCategory) child);
                return;
            }
            throw new IllegalArgumentException("Parent was not a list");
        }

        protected Object onCreateItem(String name, AttributeSet attrs) {
            if (name.equals(TAG_LIST)) {
                return new ArrayList();
            }
            if (name.equals(TAG_ITEM)) {
                SuggestionCategory category = new SuggestionCategory();
                category.category = attrs.getAttributeValue(null, ATTR_CATEGORY);
                category.pkg = attrs.getAttributeValue(null, ATTR_PACKAGE);
                String multiple = attrs.getAttributeValue(null, ATTR_MULTIPLE);
                category.multiple = !TextUtils.isEmpty(multiple) ? Boolean.parseBoolean(multiple) : false;
                return category;
            }
            throw new IllegalArgumentException("Unknown item " + name);
        }
    }

    public SuggestionParser(Context context, SharedPreferences sharedPrefs, int orderXml) {
        this.mContext = context;
        this.mSuggestionList = (List) new SuggestionOrderInflater(this.mContext).parse(orderXml);
        this.mSharedPrefs = sharedPrefs;
    }

    public List<Tile> getSuggestions() {
        List<Tile> suggestions = new ArrayList();
        int N = this.mSuggestionList.size();
        for (int i = 0; i < N; i++) {
            readSuggestions((SuggestionCategory) this.mSuggestionList.get(i), suggestions);
        }
        return suggestions;
    }

    public boolean dismissSuggestion(Tile suggestion) {
        String keyBase = suggestion.intent.getComponent().flattenToShortString();
        int index = this.mSharedPrefs.getInt(keyBase + DISMISS_INDEX, 0);
        String dismissControl = suggestion.metaData.getString(META_DATA_DISMISS_CONTROL);
        if (dismissControl == null || parseDismissString(dismissControl).length == index) {
            return true;
        }
        this.mSharedPrefs.edit().putBoolean(keyBase + IS_DISMISSED, true).commit();
        return false;
    }

    private void readSuggestions(SuggestionCategory category, List<Tile> suggestions) {
        int countBefore = suggestions.size();
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.addCategory(category.category);
        if (category.pkg != null) {
            intent.setPackage(category.pkg);
        }
        TileUtils.getTilesForIntent(this.mContext, new UserHandle(UserHandle.myUserId()), intent, this.addCache, null, suggestions, true, false);
        int i = countBefore;
        while (i < suggestions.size()) {
            if (!isAvailable((Tile) suggestions.get(i)) || !isSupported((Tile) suggestions.get(i)) || !satisfiesRequiredAccount((Tile) suggestions.get(i)) || isDismissed((Tile) suggestions.get(i))) {
                int i2 = i - 1;
                suggestions.remove(i);
                i = i2;
            }
            i++;
        }
        if (!category.multiple && suggestions.size() > countBefore + 1) {
            Tile item = (Tile) suggestions.remove(suggestions.size() - 1);
            while (suggestions.size() > countBefore) {
                Tile last = (Tile) suggestions.remove(suggestions.size() - 1);
                if (last.priority > item.priority) {
                    item = last;
                }
            }
            if (!isCategoryDone(category.category)) {
                suggestions.add(item);
            }
        }
    }

    private boolean isAvailable(Tile suggestion) {
        String featureRequired = suggestion.metaData.getString(META_DATA_REQUIRE_FEATURE);
        if (featureRequired != null) {
            return this.mContext.getPackageManager().hasSystemFeature(featureRequired);
        }
        return true;
    }

    public boolean satisfiesRequiredAccount(Tile suggestion) {
        boolean z = true;
        String requiredAccountType = suggestion.metaData.getString(META_DATA_REQUIRE_ACCOUNT);
        if (requiredAccountType == null) {
            return true;
        }
        if (AccountManager.get(this.mContext).getAccountsByType(requiredAccountType).length <= 0) {
            z = false;
        }
        return z;
    }

    public boolean isSupported(Tile suggestion) {
        int isSupportedResource = suggestion.metaData.getInt(META_DATA_IS_SUPPORTED);
        try {
            if (suggestion.intent == null) {
                return false;
            }
            return isSupportedResource != 0 ? this.mContext.getPackageManager().getResourcesForActivity(suggestion.intent.getComponent()).getBoolean(isSupportedResource) : true;
        } catch (NameNotFoundException e) {
            Log.w(TAG, "Cannot find resources for " + suggestion.intent.getComponent());
            return false;
        } catch (NotFoundException e2) {
            Log.w(TAG, "Cannot find resources for " + suggestion.intent.getComponent(), e2);
            return false;
        }
    }

    public boolean isCategoryDone(String category) {
        if (Secure.getInt(this.mContext.getContentResolver(), "suggested.completed_category." + category, 0) != 0) {
            return true;
        }
        return false;
    }

    public void markCategoryDone(String category) {
        Secure.putInt(this.mContext.getContentResolver(), "suggested.completed_category." + category, 1);
    }

    private boolean isDismissed(Tile suggestion) {
        Object dismissObj = suggestion.metaData.get(META_DATA_DISMISS_CONTROL);
        if (dismissObj == null) {
            return false;
        }
        String dismissControl = String.valueOf(dismissObj);
        String keyBase = suggestion.intent.getComponent().flattenToShortString();
        if (!this.mSharedPrefs.contains(keyBase + SETUP_TIME)) {
            this.mSharedPrefs.edit().putLong(keyBase + SETUP_TIME, System.currentTimeMillis()).commit();
        }
        if (!this.mSharedPrefs.getBoolean(keyBase + IS_DISMISSED, true)) {
            return false;
        }
        int index = this.mSharedPrefs.getInt(keyBase + DISMISS_INDEX, 0);
        if (System.currentTimeMillis() < getEndTime(this.mSharedPrefs.getLong(keyBase + SETUP_TIME, 0), parseDismissString(dismissControl)[index])) {
            return true;
        }
        this.mSharedPrefs.edit().putBoolean(keyBase + IS_DISMISSED, false).putInt(keyBase + DISMISS_INDEX, index + 1).commit();
        return false;
    }

    private long getEndTime(long startTime, int daysDelay) {
        return startTime + (((long) daysDelay) * MILLIS_IN_DAY);
    }

    private int[] parseDismissString(String dismissControl) {
        String[] dismissStrs = dismissControl.split(",");
        int[] dismisses = new int[dismissStrs.length];
        for (int i = 0; i < dismissStrs.length; i++) {
            dismisses[i] = Integer.parseInt(dismissStrs[i]);
        }
        return dismisses;
    }
}
