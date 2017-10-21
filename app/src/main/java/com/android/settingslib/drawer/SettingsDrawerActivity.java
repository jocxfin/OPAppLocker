package com.android.settingslib.drawer;

import android.R;
import android.animation.ArgbEvaluator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.DrawerLayout.DrawerListener;
import android.support.v4.widget.ExploreByTouchHelper;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toolbar;
import com.android.settingslib.applications.InterestingConfigChanges;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SettingsDrawerActivity extends Activity implements DrawerListener {
    private static final boolean DEBUG = Log.isLoggable(TAG, 3);
    protected static final boolean DEBUG_TIMING = false;
    public static final String EXTRA_SHOW_MENU = "show_drawer_menu";
    private static final int REFRESH_DRAWER_UI = 1;
    private static final int REFRESH_UI = 0;
    private static final int REFRESH_UI_DELAY = 100;
    private static final int STATUS_BAR_OVERLAY_COLOR = Color.parseColor("#00000000");
    private static final int STATUS_BAR_OVERLAY_TRANSLUCENT_COLOR = Color.parseColor("#15000000");
    private static final String TAG = "SettingsDrawerActivity";
    private static InterestingConfigChanges sConfigTracker;
    private static List<DashboardCategory> sDashboardCategories;
    private static ArraySet<ComponentName> sTileBlacklist = new ArraySet();
    private static HashMap<Pair<String, String>, Tile> sTileCache;
    private ArgbEvaluator mArgbEvaluator;
    private final List<CategoryListener> mCategoryListeners = new ArrayList();
    private FrameLayout mContentHeaderContainer;
    private SettingsDrawerAdapter mDrawerAdapter;
    private DrawerLayout mDrawerLayout;
    private DrawerListener mDrawerListener;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    SettingsDrawerActivity.this.delayInitDrawer();
                    return;
                case 1:
                    new CategoriesUpdater().execute(new Void[0]);
                    return;
                default:
                    return;
            }
        }
    };
    private final PackageReceiver mPackageReceiver = new PackageReceiver();
    private boolean mShowingMenu;
    private UserManager mUserManager;
    private Window mWindow;

    private class CategoriesUpdater extends AsyncTask<Void, Void, List<DashboardCategory>> {
        private CategoriesUpdater() {
        }

        protected List<DashboardCategory> doInBackground(Void... params) {
            if (SettingsDrawerActivity.sConfigTracker.applyNewConfig(SettingsDrawerActivity.this.getResources())) {
                SettingsDrawerActivity.sTileCache.clear();
            }
            return TileUtils.getCategories(SettingsDrawerActivity.this, SettingsDrawerActivity.sTileCache);
        }

        protected void onPreExecute() {
            if (SettingsDrawerActivity.sConfigTracker == null || SettingsDrawerActivity.sTileCache == null) {
                SettingsDrawerActivity.this.getDashboardCategories();
            }
        }

        protected void onPostExecute(List<DashboardCategory> dashboardCategories) {
            for (int i = 0; i < dashboardCategories.size(); i++) {
                DashboardCategory category = (DashboardCategory) dashboardCategories.get(i);
                int j = 0;
                while (j < category.tiles.size()) {
                    if (SettingsDrawerActivity.sTileBlacklist.contains(((Tile) category.tiles.get(j)).intent.getComponent())) {
                        int j2 = j - 1;
                        category.tiles.remove(j);
                        j = j2;
                    }
                    j++;
                }
            }
            SettingsDrawerActivity.sDashboardCategories = dashboardCategories;
            SettingsDrawerActivity.this.onCategoriesChanged();
        }
    }

    public interface CategoryListener {
        void onCategoriesChanged();
    }

    private class PackageReceiver extends BroadcastReceiver {
        private PackageReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            new CategoriesUpdater().execute(new Void[0]);
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        long startTime = System.currentTimeMillis();
        TypedArray theme = getTheme().obtainStyledAttributes(R.styleable.Theme);
        this.mWindow = getWindow();
        if (!theme.getBoolean(38, false)) {
            this.mWindow.addFlags(ExploreByTouchHelper.INVALID_ID);
            this.mWindow.setStatusBarColor(STATUS_BAR_OVERLAY_TRANSLUCENT_COLOR);
            requestWindowFeature(1);
        }
        super.setContentView(com.android.settingslib.R.layout.settings_with_drawer);
        this.mContentHeaderContainer = (FrameLayout) findViewById(com.android.settingslib.R.id.content_header_container);
        this.mDrawerLayout = (DrawerLayout) findViewById(com.android.settingslib.R.id.drawer_layout);
        if (this.mDrawerLayout != null) {
            this.mDrawerLayout.setDrawerListener(this);
            this.mArgbEvaluator = new ArgbEvaluator();
            Toolbar toolbar = (Toolbar) findViewById(com.android.settingslib.R.id.action_bar);
            if (theme.getBoolean(38, false)) {
                toolbar.setVisibility(8);
                this.mDrawerLayout.setDrawerLockMode(1);
                this.mDrawerLayout = null;
                return;
            }
            toolbar.setTitleTextAppearance(this, com.android.settingslib.R.style.Settings_TextAppearance_Material_Widget_ActionBar_Title);
            setActionBar(toolbar);
            this.mUserManager = UserManager.get(this);
            this.mDrawerAdapter = new SettingsDrawerAdapter(this);
            this.mHandler.sendEmptyMessageDelayed(0, 100);
        }
    }

    private void delayInitDrawer() {
        ListView listView = (ListView) findViewById(com.android.settingslib.R.id.left_drawer);
        listView.setAdapter(this.mDrawerAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                SettingsDrawerActivity.this.onTileClicked(SettingsDrawerActivity.this.mDrawerAdapter.getTile(position));
            }
        });
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (!this.mShowingMenu || this.mDrawerLayout == null || item.getItemId() != 16908332 || this.mDrawerAdapter.getCount() == 0) {
            return super.onOptionsItemSelected(item);
        }
        openDrawer();
        return true;
    }

    protected void onResume() {
        super.onResume();
        if (this.mDrawerLayout != null) {
            IntentFilter filter = new IntentFilter("android.intent.action.PACKAGE_ADDED");
            filter.addAction("android.intent.action.PACKAGE_REMOVED");
            filter.addAction("android.intent.action.PACKAGE_CHANGED");
            filter.addAction("android.intent.action.PACKAGE_REPLACED");
            filter.addDataScheme("package");
            registerReceiver(this.mPackageReceiver, filter);
            if (sDashboardCategories == null) {
                this.mHandler.sendEmptyMessageDelayed(1, 100);
            } else {
                new CategoriesUpdater().execute(new Void[0]);
            }
        }
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_SHOW_MENU, false)) {
            showMenuIcon();
        }
    }

    protected void onPause() {
        if (this.mDrawerLayout != null) {
            unregisterReceiver(this.mPackageReceiver);
        }
        super.onPause();
    }

    private boolean isTopLevelTile(Intent intent) {
        ComponentName componentName = intent.getComponent();
        if (componentName == null) {
            return false;
        }
        for (DashboardCategory category : getDashboardCategories()) {
            for (Tile tile : category.tiles) {
                if (TextUtils.equals(tile.intent.getComponent().getClassName(), componentName.getClassName())) {
                    if (DEBUG) {
                        Log.d(TAG, "intent is for top level tile: " + tile.title);
                    }
                    return true;
                }
            }
        }
        if (DEBUG) {
            Log.d(TAG, "Intent is not for top level settings " + intent);
        }
        return false;
    }

    public void addCategoryListener(CategoryListener listener) {
        this.mCategoryListeners.add(listener);
    }

    public void remCategoryListener(CategoryListener listener) {
        this.mCategoryListeners.remove(listener);
    }

    public void setIsDrawerPresent(boolean isPresent) {
        if (isPresent) {
            this.mDrawerLayout = (DrawerLayout) findViewById(com.android.settingslib.R.id.drawer_layout);
            updateDrawer();
        } else if (this.mDrawerLayout != null) {
            this.mDrawerLayout.setDrawerLockMode(1);
            this.mDrawerLayout = null;
        }
    }

    public void openDrawer() {
        if (this.mDrawerLayout != null) {
            this.mDrawerLayout.openDrawer((int) GravityCompat.START);
        }
    }

    public void closeDrawer() {
        if (this.mDrawerLayout != null) {
            this.mDrawerLayout.closeDrawers();
        }
    }

    public void setDrawerListener(DrawerListener listener) {
        this.mDrawerListener = listener;
    }

    public void setContentHeaderView(View headerView) {
        this.mContentHeaderContainer.removeAllViews();
        if (headerView != null) {
            this.mContentHeaderContainer.addView(headerView);
        }
    }

    public void setContentView(int layoutResID) {
        ViewGroup parent = (ViewGroup) findViewById(com.android.settingslib.R.id.content_frame);
        if (parent != null) {
            parent.removeAllViews();
        }
        LayoutInflater.from(this).inflate(layoutResID, parent);
    }

    public void setContentView(View view) {
        ((ViewGroup) findViewById(com.android.settingslib.R.id.content_frame)).addView(view);
    }

    public void setContentView(View view, LayoutParams params) {
        ((ViewGroup) findViewById(com.android.settingslib.R.id.content_frame)).addView(view, params);
    }

    public void updateDrawer() {
        if (this.mDrawerLayout != null && this.mDrawerAdapter != null) {
            this.mDrawerAdapter.updateCategories();
            if (this.mDrawerAdapter.getCount() != 0) {
                this.mDrawerLayout.setDrawerLockMode(0);
            } else {
                this.mDrawerLayout.setDrawerLockMode(1);
            }
        }
    }

    public void onDrawerStateChanged(int newState) {
        if (this.mDrawerListener != null) {
            this.mDrawerListener.onDrawerStateChanged(newState);
        }
    }

    public void onDrawerSlide(View drawerView, float slideOffset) {
    }

    public void onDrawerOpened(View drawerView) {
        if (this.mDrawerListener != null) {
            this.mDrawerListener.onDrawerOpened(drawerView);
        }
    }

    public void onDrawerClosed(View drawerView) {
        if (this.mDrawerListener != null) {
            this.mDrawerListener.onDrawerClosed(drawerView);
        }
    }

    public void showMenuIcon() {
        this.mShowingMenu = true;
        if (getActionBar() != null) {
            getActionBar().setHomeAsUpIndicator(com.android.settingslib.R.drawable.ic_menu);
            getActionBar().setHomeActionContentDescription(com.android.settingslib.R.string.content_description_menu_button);
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    public List<DashboardCategory> getEarlyDashboardCategories() {
        return sDashboardCategories;
    }

    public List<DashboardCategory> getDashboardCategories() {
        if (sDashboardCategories == null) {
            sTileCache = new HashMap();
            sConfigTracker = new InterestingConfigChanges();
            sConfigTracker.applyNewConfig(getResources());
            sDashboardCategories = TileUtils.getCategories(this, sTileCache);
        }
        return sDashboardCategories;
    }

    protected void onCategoriesChanged() {
        updateDrawer();
        int N = this.mCategoryListeners.size();
        for (int i = 0; i < N; i++) {
            ((CategoryListener) this.mCategoryListeners.get(i)).onCategoriesChanged();
        }
    }

    public boolean openTile(Tile tile) {
        new Handler().postDelayed(new Runnable() {
            public void run() {
                SettingsDrawerActivity.this.mDrawerLayout.closeDrawer((int) GravityCompat.START, false);
            }
        }, 300);
        if (tile == null) {
            startActivity(new Intent("android.settings.SETTINGS").addFlags(32768));
            return true;
        }
        try {
            updateUserHandlesIfNeeded(tile);
            int numUserHandles = tile.userHandle.size();
            if (numUserHandles > 1) {
                ProfileSelectDialog.show(getFragmentManager(), tile);
                return false;
            }
            if (numUserHandles == 1) {
                tile.intent.putExtra(EXTRA_SHOW_MENU, true);
                tile.intent.addFlags(32768);
                startActivityAsUser(tile.intent, (UserHandle) tile.userHandle.get(0));
            } else {
                tile.intent.putExtra(EXTRA_SHOW_MENU, true);
                tile.intent.addFlags(32768);
                startActivity(tile.intent);
            }
            return true;
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Couldn't find tile " + tile.intent, e);
        }
    }

    private void updateUserHandlesIfNeeded(Tile tile) {
        List<UserHandle> userHandles = tile.userHandle;
        for (int i = userHandles.size() - 1; i >= 0; i--) {
            if (this.mUserManager.getUserInfo(((UserHandle) userHandles.get(i)).getIdentifier()) == null) {
                if (DEBUG) {
                    Log.d(TAG, "Delete the user: " + ((UserHandle) userHandles.get(i)).getIdentifier());
                }
                userHandles.remove(i);
            }
        }
    }

    protected void onTileClicked(Tile tile) {
        if (openTile(tile)) {
            finish();
        }
    }

    public HashMap<Pair<String, String>, Tile> getTileCache() {
        if (sTileCache == null) {
            getDashboardCategories();
        }
        return sTileCache;
    }

    public void onProfileTileOpen() {
        finish();
    }

    public void setTileEnabled(ComponentName component, boolean enabled) {
        boolean isEnabled;
        PackageManager pm = getPackageManager();
        int state = pm.getComponentEnabledSetting(component);
        if (state == 1) {
            isEnabled = true;
        } else {
            isEnabled = false;
        }
        if (isEnabled != enabled || state == 0) {
            int i;
            if (enabled) {
                sTileBlacklist.remove(component);
            } else {
                sTileBlacklist.add(component);
            }
            if (enabled) {
                i = 1;
            } else {
                i = 2;
            }
            pm.setComponentEnabledSetting(component, i, 1);
            new CategoriesUpdater().execute(new Void[0]);
        }
    }
}
