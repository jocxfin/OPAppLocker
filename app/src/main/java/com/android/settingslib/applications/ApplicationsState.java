package com.android.settingslib.applications;

import android.app.ActivityManager;
import android.app.AppGlobals;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.IPackageStatsObserver.Stub;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageStats;
import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.v4.content.IntentCompat;
import android.text.format.Formatter;
import android.util.Log;
import android.util.SparseArray;
import com.android.internal.util.ArrayUtils;
import com.android.settingslib.R;
import java.io.File;
import java.text.Collator;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class ApplicationsState {
    public static final Comparator<AppEntry> ALPHA_COMPARATOR = new Comparator<AppEntry>() {
        private final Collator sCollator = Collator.getInstance();

        public int compare(AppEntry object1, AppEntry object2) {
            int compareResult = this.sCollator.compare(object1.label, object2.label);
            if (compareResult != 0) {
                return compareResult;
            }
            if (!(object1.info == null || object2.info == null)) {
                compareResult = this.sCollator.compare(object1.info.packageName, object2.info.packageName);
                if (compareResult != 0) {
                    return compareResult;
                }
            }
            return object1.info.uid - object2.info.uid;
        }
    };
    static final boolean DEBUG = false;
    static final boolean DEBUG_LOCKING = false;
    public static final Comparator<AppEntry> EXTERNAL_SIZE_COMPARATOR = new Comparator<AppEntry>() {
        public int compare(AppEntry object1, AppEntry object2) {
            if (object1.externalSize < object2.externalSize) {
                return 1;
            }
            if (object1.externalSize > object2.externalSize) {
                return -1;
            }
            return ApplicationsState.ALPHA_COMPARATOR.compare(object1, object2);
        }
    };
    public static final AppFilter FILTER_ALL_ENABLED = new AppFilter() {
        public void init() {
        }

        public boolean filterApp(AppEntry entry) {
            return entry.info.enabled;
        }
    };
    public static final AppFilter FILTER_DISABLED = new AppFilter() {
        public void init() {
        }

        public boolean filterApp(AppEntry entry) {
            return !entry.info.enabled;
        }
    };
    public static final AppFilter FILTER_DOWNLOADED_AND_LAUNCHER = new AppFilter() {
        public void init() {
        }

        public boolean filterApp(AppEntry entry) {
            if ((entry.info.flags & 128) != 0 || (entry.info.flags & 1) == 0 || entry.hasLauncherEntry) {
                return true;
            }
            return (entry.info.flags & 1) != 0 && entry.isHomeApp;
        }
    };
    public static final AppFilter FILTER_EVERYTHING = new AppFilter() {
        public void init() {
        }

        public boolean filterApp(AppEntry entry) {
            return true;
        }
    };
    public static final AppFilter FILTER_NOT_HIDE = new AppFilter() {
        private String[] mHidePackageNames;

        public void init(Context context) {
            this.mHidePackageNames = context.getResources().getStringArray(R.array.config_hideWhenDisabled_packageNames);
        }

        public void init() {
        }

        public boolean filterApp(AppEntry entry) {
            if (!ArrayUtils.contains(this.mHidePackageNames, entry.info.packageName) || (entry.info.enabled && entry.info.enabledSetting != 4)) {
                return true;
            }
            return false;
        }
    };
    public static final AppFilter FILTER_PERSONAL = new AppFilter() {
        private int mCurrentUser;

        public void init() {
            this.mCurrentUser = ActivityManager.getCurrentUser();
        }

        public boolean filterApp(AppEntry entry) {
            return UserHandle.getUserId(entry.info.uid) == this.mCurrentUser;
        }
    };
    public static final AppFilter FILTER_THIRD_PARTY = new AppFilter() {
        public void init() {
        }

        public boolean filterApp(AppEntry entry) {
            return (entry.info.flags & 128) != 0 || (entry.info.flags & 1) == 0;
        }
    };
    public static final AppFilter FILTER_WITHOUT_DISABLED_UNTIL_USED = new AppFilter() {
        public void init() {
        }

        public boolean filterApp(AppEntry entry) {
            return entry.info.enabledSetting != 4;
        }
    };
    public static final AppFilter FILTER_WITH_DOMAIN_URLS = new AppFilter() {
        public void init() {
        }

        public boolean filterApp(AppEntry entry) {
            return (entry.info.privateFlags & 16) != 0;
        }
    };
    public static final AppFilter FILTER_WORK = new AppFilter() {
        private int mCurrentUser;

        public void init() {
            this.mCurrentUser = ActivityManager.getCurrentUser();
        }

        public boolean filterApp(AppEntry entry) {
            return UserHandle.getUserId(entry.info.uid) != this.mCurrentUser;
        }
    };
    public static final Comparator<AppEntry> INTERNAL_SIZE_COMPARATOR = new Comparator<AppEntry>() {
        public int compare(AppEntry object1, AppEntry object2) {
            if (object1.internalSize < object2.internalSize) {
                return 1;
            }
            if (object1.internalSize > object2.internalSize) {
                return -1;
            }
            return ApplicationsState.ALPHA_COMPARATOR.compare(object1, object2);
        }
    };
    static final Pattern REMOVE_DIACRITICALS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    public static final Comparator<AppEntry> SIZE_COMPARATOR = new Comparator<AppEntry>() {
        public int compare(AppEntry object1, AppEntry object2) {
            if (object1.size < object2.size) {
                return 1;
            }
            if (object1.size > object2.size) {
                return -1;
            }
            return ApplicationsState.ALPHA_COMPARATOR.compare(object1, object2);
        }
    };
    public static final int SIZE_INVALID = -2;
    public static final int SIZE_UNKNOWN = -1;
    static final String TAG = "ApplicationsState";
    static ApplicationsState sInstance;
    static final Object sLock = new Object();
    final ArrayList<Session> mActiveSessions = new ArrayList();
    final int mAdminRetrieveFlags;
    final ArrayList<AppEntry> mAppEntries = new ArrayList();
    List<ApplicationInfo> mApplications = new ArrayList();
    final BackgroundHandler mBackgroundHandler;
    final Context mContext;
    String mCurComputingSizePkg;
    int mCurComputingSizeUserId;
    long mCurId = 1;
    final SparseArray<HashMap<String, AppEntry>> mEntriesMap = new SparseArray();
    boolean mHaveDisabledApps;
    final InterestingConfigChanges mInterestingConfigChanges = new InterestingConfigChanges();
    final IPackageManager mIpm;
    final MainHandler mMainHandler = new MainHandler(Looper.getMainLooper());
    PackageIntentReceiver mPackageIntentReceiver;
    final PackageManager mPm;
    final ArrayList<Session> mRebuildingSessions = new ArrayList();
    boolean mResumed;
    final int mRetrieveFlags;
    final ArrayList<Session> mSessions = new ArrayList();
    boolean mSessionsChanged;
    final HandlerThread mThread;
    final UserManager mUm;

    public interface AppFilter {
        boolean filterApp(AppEntry appEntry);

        void init();

        void init(Context context) {
            init();
        }
    }

    public static class SizeInfo {
        public long cacheSize;
        public long codeSize;
        public long dataSize;
        public long externalCacheSize;
        public long externalCodeSize;
        public long externalDataSize;
    }

    public static class AppEntry extends SizeInfo {
        public final File apkFile;
        public long externalSize;
        public String externalSizeStr;
        public Object extraInfo;
        public boolean hasLauncherEntry;
        public Drawable icon;
        public final long id;
        public ApplicationInfo info;
        public long internalSize;
        public String internalSizeStr;
        public boolean isHomeApp;
        public String label;
        public boolean mounted;
        public String normalizedLabel;
        public long size = -1;
        public long sizeLoadStart;
        public boolean sizeStale = true;
        public String sizeStr;

        public String getNormalizedLabel() {
            if (this.normalizedLabel != null) {
                return this.normalizedLabel;
            }
            this.normalizedLabel = ApplicationsState.normalize(this.label);
            return this.normalizedLabel;
        }

        AppEntry(Context context, ApplicationInfo info, long id) {
            this.apkFile = new File(info.sourceDir);
            this.id = id;
            this.info = info;
            ensureLabel(context);
        }

        public void ensureLabel(Context context) {
            if (this.label != null && this.mounted) {
                return;
            }
            if (this.apkFile.exists()) {
                this.mounted = true;
                CharSequence label = this.info.loadLabel(context.getPackageManager());
                this.label = label != null ? label.toString() : this.info.packageName;
                return;
            }
            this.mounted = false;
            this.label = this.info.packageName;
        }

        boolean ensureIconLocked(Context context, PackageManager pm) {
            if (this.icon == null) {
                if (this.apkFile.exists()) {
                    this.icon = getBadgedIcon(pm);
                    return true;
                }
                this.mounted = false;
                this.icon = context.getDrawable(17303367);
            } else if (!this.mounted && this.apkFile.exists()) {
                this.mounted = true;
                this.icon = getBadgedIcon(pm);
                return true;
            }
            return false;
        }

        private Drawable getBadgedIcon(PackageManager pm) {
            return pm.getUserBadgedIcon(pm.loadUnbadgedItemIcon(this.info, this.info), new UserHandle(UserHandle.getUserId(this.info.uid)));
        }

        public String getVersion(Context context) {
            try {
                return context.getPackageManager().getPackageInfo(this.info.packageName, 0).versionName;
            } catch (NameNotFoundException e) {
                return "";
            }
        }
    }

    private class BackgroundHandler extends Handler {
        static final int MSG_LOAD_ENTRIES = 2;
        static final int MSG_LOAD_HOME_APP = 6;
        static final int MSG_LOAD_ICONS = 3;
        static final int MSG_LOAD_LAUNCHER = 5;
        static final int MSG_LOAD_SIZES = 4;
        static final int MSG_REBUILD_LIST = 1;
        boolean mRunning;
        final Stub mStatsObserver = new Stub() {
            /* JADX WARNING: inconsistent code. */
            /* Code decompiled incorrectly, please refer to instructions dump. */
            public void onGetStatsCompleted(android.content.pm.PackageStats r19, boolean r20) {
                /*
                r18 = this;
                r10 = 0;
                r0 = r18;
                r12 = com.android.settingslib.applications.ApplicationsState.BackgroundHandler.this;
                r12 = com.android.settingslib.applications.ApplicationsState.this;
                r13 = r12.mEntriesMap;
                monitor-enter(r13);
                r0 = r18;
                r12 = com.android.settingslib.applications.ApplicationsState.BackgroundHandler.this;	 Catch:{ all -> 0x0164 }
                r12 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0164 }
                r12 = r12.mEntriesMap;	 Catch:{ all -> 0x0164 }
                r0 = r19;
                r14 = r0.userHandle;	 Catch:{ all -> 0x0164 }
                r11 = r12.get(r14);	 Catch:{ all -> 0x0164 }
                r11 = (java.util.HashMap) r11;	 Catch:{ all -> 0x0164 }
                if (r11 != 0) goto L_0x0020;
            L_0x001e:
                monitor-exit(r13);
                return;
            L_0x0020:
                r0 = r19;
                r12 = r0.packageName;	 Catch:{ all -> 0x0164 }
                r2 = r11.get(r12);	 Catch:{ all -> 0x0164 }
                r2 = (com.android.settingslib.applications.ApplicationsState.AppEntry) r2;	 Catch:{ all -> 0x0164 }
                if (r2 == 0) goto L_0x00f2;
            L_0x002c:
                monitor-enter(r2);	 Catch:{ all -> 0x0164 }
                r12 = 0;
                r2.sizeStale = r12;	 Catch:{ all -> 0x0161 }
                r14 = 0;
                r2.sizeLoadStart = r14;	 Catch:{ all -> 0x0161 }
                r0 = r19;
                r14 = r0.externalCodeSize;	 Catch:{ all -> 0x0161 }
                r0 = r19;
                r0 = r0.externalObbSize;	 Catch:{ all -> 0x0161 }
                r16 = r0;
                r4 = r14 + r16;
                r0 = r19;
                r14 = r0.externalDataSize;	 Catch:{ all -> 0x0161 }
                r0 = r19;
                r0 = r0.externalMediaSize;	 Catch:{ all -> 0x0161 }
                r16 = r0;
                r6 = r14 + r16;
                r14 = r4 + r6;
                r0 = r18;
                r12 = com.android.settingslib.applications.ApplicationsState.BackgroundHandler.this;	 Catch:{ all -> 0x0161 }
                r12 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0161 }
                r0 = r19;
                r16 = r12.getTotalInternalSize(r0);	 Catch:{ all -> 0x0161 }
                r8 = r14 + r16;
                r14 = r2.size;	 Catch:{ all -> 0x0161 }
                r12 = (r14 > r8 ? 1 : (r14 == r8 ? 0 : -1));
                if (r12 != 0) goto L_0x006e;
            L_0x0062:
                r14 = r2.cacheSize;	 Catch:{ all -> 0x0161 }
                r0 = r19;
                r0 = r0.cacheSize;	 Catch:{ all -> 0x0161 }
                r16 = r0;
                r12 = (r14 > r16 ? 1 : (r14 == r16 ? 0 : -1));
                if (r12 == 0) goto L_0x012f;
            L_0x006e:
                r2.size = r8;	 Catch:{ all -> 0x0161 }
                r0 = r19;
                r14 = r0.cacheSize;	 Catch:{ all -> 0x0161 }
                r2.cacheSize = r14;	 Catch:{ all -> 0x0161 }
                r0 = r19;
                r14 = r0.codeSize;	 Catch:{ all -> 0x0161 }
                r2.codeSize = r14;	 Catch:{ all -> 0x0161 }
                r0 = r19;
                r14 = r0.dataSize;	 Catch:{ all -> 0x0161 }
                r2.dataSize = r14;	 Catch:{ all -> 0x0161 }
                r2.externalCodeSize = r4;	 Catch:{ all -> 0x0161 }
                r2.externalDataSize = r6;	 Catch:{ all -> 0x0161 }
                r0 = r19;
                r14 = r0.externalCacheSize;	 Catch:{ all -> 0x0161 }
                r2.externalCacheSize = r14;	 Catch:{ all -> 0x0161 }
                r0 = r18;
                r12 = com.android.settingslib.applications.ApplicationsState.BackgroundHandler.this;	 Catch:{ all -> 0x0161 }
                r12 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0161 }
                r14 = r2.size;	 Catch:{ all -> 0x0161 }
                r12 = r12.getSizeStr(r14);	 Catch:{ all -> 0x0161 }
                r2.sizeStr = r12;	 Catch:{ all -> 0x0161 }
                r0 = r18;
                r12 = com.android.settingslib.applications.ApplicationsState.BackgroundHandler.this;	 Catch:{ all -> 0x0161 }
                r12 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0161 }
                r0 = r19;
                r14 = r12.getTotalInternalSize(r0);	 Catch:{ all -> 0x0161 }
                r2.internalSize = r14;	 Catch:{ all -> 0x0161 }
                r0 = r18;
                r12 = com.android.settingslib.applications.ApplicationsState.BackgroundHandler.this;	 Catch:{ all -> 0x0161 }
                r12 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0161 }
                r14 = r2.internalSize;	 Catch:{ all -> 0x0161 }
                r12 = r12.getSizeStr(r14);	 Catch:{ all -> 0x0161 }
                r2.internalSizeStr = r12;	 Catch:{ all -> 0x0161 }
                r0 = r18;
                r12 = com.android.settingslib.applications.ApplicationsState.BackgroundHandler.this;	 Catch:{ all -> 0x0161 }
                r12 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0161 }
                r0 = r19;
                r14 = r12.getTotalExternalSize(r0);	 Catch:{ all -> 0x0161 }
                r2.externalSize = r14;	 Catch:{ all -> 0x0161 }
                r0 = r18;
                r12 = com.android.settingslib.applications.ApplicationsState.BackgroundHandler.this;	 Catch:{ all -> 0x0161 }
                r12 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0161 }
                r14 = r2.externalSize;	 Catch:{ all -> 0x0161 }
                r12 = r12.getSizeStr(r14);	 Catch:{ all -> 0x0161 }
                r2.externalSizeStr = r12;	 Catch:{ all -> 0x0161 }
                r10 = 1;
            L_0x00d3:
                monitor-exit(r2);	 Catch:{ all -> 0x0164 }
                if (r10 == 0) goto L_0x00f2;
            L_0x00d6:
                r0 = r18;
                r12 = com.android.settingslib.applications.ApplicationsState.BackgroundHandler.this;	 Catch:{ all -> 0x0164 }
                r12 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0164 }
                r12 = r12.mMainHandler;	 Catch:{ all -> 0x0164 }
                r0 = r19;
                r14 = r0.packageName;	 Catch:{ all -> 0x0164 }
                r15 = 4;
                r3 = r12.obtainMessage(r15, r14);	 Catch:{ all -> 0x0164 }
                r0 = r18;
                r12 = com.android.settingslib.applications.ApplicationsState.BackgroundHandler.this;	 Catch:{ all -> 0x0164 }
                r12 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0164 }
                r12 = r12.mMainHandler;	 Catch:{ all -> 0x0164 }
                r12.sendMessage(r3);	 Catch:{ all -> 0x0164 }
            L_0x00f2:
                r0 = r18;
                r12 = com.android.settingslib.applications.ApplicationsState.BackgroundHandler.this;	 Catch:{ all -> 0x0164 }
                r12 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0164 }
                r12 = r12.mCurComputingSizePkg;	 Catch:{ all -> 0x0164 }
                if (r12 == 0) goto L_0x012d;
            L_0x00fc:
                r0 = r18;
                r12 = com.android.settingslib.applications.ApplicationsState.BackgroundHandler.this;	 Catch:{ all -> 0x0164 }
                r12 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0164 }
                r12 = r12.mCurComputingSizePkg;	 Catch:{ all -> 0x0164 }
                r0 = r19;
                r14 = r0.packageName;	 Catch:{ all -> 0x0164 }
                r12 = r12.equals(r14);	 Catch:{ all -> 0x0164 }
                if (r12 == 0) goto L_0x012d;
            L_0x010e:
                r0 = r18;
                r12 = com.android.settingslib.applications.ApplicationsState.BackgroundHandler.this;	 Catch:{ all -> 0x0164 }
                r12 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0164 }
                r12 = r12.mCurComputingSizeUserId;	 Catch:{ all -> 0x0164 }
                r0 = r19;
                r14 = r0.userHandle;	 Catch:{ all -> 0x0164 }
                if (r12 != r14) goto L_0x012d;
            L_0x011c:
                r0 = r18;
                r12 = com.android.settingslib.applications.ApplicationsState.BackgroundHandler.this;	 Catch:{ all -> 0x0164 }
                r12 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0164 }
                r14 = 0;
                r12.mCurComputingSizePkg = r14;	 Catch:{ all -> 0x0164 }
                r0 = r18;
                r12 = com.android.settingslib.applications.ApplicationsState.BackgroundHandler.this;	 Catch:{ all -> 0x0164 }
                r14 = 4;
                r12.sendEmptyMessage(r14);	 Catch:{ all -> 0x0164 }
            L_0x012d:
                monitor-exit(r13);
                return;
            L_0x012f:
                r14 = r2.codeSize;	 Catch:{ all -> 0x0161 }
                r0 = r19;
                r0 = r0.codeSize;	 Catch:{ all -> 0x0161 }
                r16 = r0;
                r12 = (r14 > r16 ? 1 : (r14 == r16 ? 0 : -1));
                if (r12 != 0) goto L_0x006e;
            L_0x013b:
                r14 = r2.dataSize;	 Catch:{ all -> 0x0161 }
                r0 = r19;
                r0 = r0.dataSize;	 Catch:{ all -> 0x0161 }
                r16 = r0;
                r12 = (r14 > r16 ? 1 : (r14 == r16 ? 0 : -1));
                if (r12 != 0) goto L_0x006e;
            L_0x0147:
                r14 = r2.externalCodeSize;	 Catch:{ all -> 0x0161 }
                r12 = (r14 > r4 ? 1 : (r14 == r4 ? 0 : -1));
                if (r12 != 0) goto L_0x006e;
            L_0x014d:
                r14 = r2.externalDataSize;	 Catch:{ all -> 0x0161 }
                r12 = (r14 > r6 ? 1 : (r14 == r6 ? 0 : -1));
                if (r12 != 0) goto L_0x006e;
            L_0x0153:
                r14 = r2.externalCacheSize;	 Catch:{ all -> 0x0161 }
                r0 = r19;
                r0 = r0.externalCacheSize;	 Catch:{ all -> 0x0161 }
                r16 = r0;
                r12 = (r14 > r16 ? 1 : (r14 == r16 ? 0 : -1));
                if (r12 == 0) goto L_0x00d3;
            L_0x015f:
                goto L_0x006e;
            L_0x0161:
                r12 = move-exception;
                monitor-exit(r2);	 Catch:{ all -> 0x0164 }
                throw r12;	 Catch:{ all -> 0x0164 }
            L_0x0164:
                r12 = move-exception;
                monitor-exit(r13);
                throw r12;
                */
                throw new UnsupportedOperationException("Method not decompiled: com.android.settingslib.applications.ApplicationsState.BackgroundHandler.1.onGetStatsCompleted(android.content.pm.PackageStats, boolean):void");
            }
        };

        BackgroundHandler(Looper looper) {
            super(looper);
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void handleMessage(android.os.Message r31) {
            /*
            r30 = this;
            r20 = 0;
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;
            r24 = r0;
            r0 = r24;
            r0 = r0.mRebuildingSessions;
            r25 = r0;
            monitor-enter(r25);
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0061 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mRebuildingSessions;	 Catch:{ all -> 0x0061 }
            r24 = r0;
            r24 = r24.size();	 Catch:{ all -> 0x0061 }
            if (r24 <= 0) goto L_0x0047;
        L_0x0021:
            r21 = new java.util.ArrayList;	 Catch:{ all -> 0x0061 }
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0061 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mRebuildingSessions;	 Catch:{ all -> 0x0061 }
            r24 = r0;
            r0 = r21;
            r1 = r24;
            r0.<init>(r1);	 Catch:{ all -> 0x0061 }
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0613 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mRebuildingSessions;	 Catch:{ all -> 0x0613 }
            r24 = r0;
            r24.clear();	 Catch:{ all -> 0x0613 }
            r20 = r21;
        L_0x0047:
            monitor-exit(r25);
            if (r20 == 0) goto L_0x0064;
        L_0x004a:
            r10 = 0;
        L_0x004b:
            r24 = r20.size();
            r0 = r24;
            if (r10 >= r0) goto L_0x0064;
        L_0x0053:
            r0 = r20;
            r24 = r0.get(r10);
            r24 = (com.android.settingslib.applications.ApplicationsState.Session) r24;
            r24.handleRebuildList();
            r10 = r10 + 1;
            goto L_0x004b;
        L_0x0061:
            r24 = move-exception;
        L_0x0062:
            monitor-exit(r25);
            throw r24;
        L_0x0064:
            r0 = r31;
            r0 = r0.what;
            r24 = r0;
            switch(r24) {
                case 1: goto L_0x006d;
                case 2: goto L_0x006e;
                case 3: goto L_0x038a;
                case 4: goto L_0x048b;
                case 5: goto L_0x0280;
                case 6: goto L_0x01f7;
                default: goto L_0x006d;
            };
        L_0x006d:
            return;
        L_0x006e:
            r18 = 0;
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;
            r24 = r0;
            r0 = r24;
            r0 = r0.mEntriesMap;
            r25 = r0;
            monitor-enter(r25);
            r10 = 0;
        L_0x007e:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mApplications;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r24 = r24.size();	 Catch:{ all -> 0x01c4 }
            r0 = r24;
            if (r10 >= r0) goto L_0x01b0;
        L_0x0092:
            r24 = 6;
            r0 = r18;
            r1 = r24;
            if (r0 >= r1) goto L_0x01b0;
        L_0x009a:
            r0 = r30;
            r0 = r0.mRunning;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            if (r24 != 0) goto L_0x00d9;
        L_0x00a2:
            r24 = 1;
            r0 = r24;
            r1 = r30;
            r1.mRunning = r0;	 Catch:{ all -> 0x01c4 }
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mMainHandler;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r26 = 1;
            r26 = java.lang.Integer.valueOf(r26);	 Catch:{ all -> 0x01c4 }
            r27 = 6;
            r0 = r24;
            r1 = r27;
            r2 = r26;
            r15 = r0.obtainMessage(r1, r2);	 Catch:{ all -> 0x01c4 }
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mMainHandler;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r0 = r24;
            r0.sendMessage(r15);	 Catch:{ all -> 0x01c4 }
        L_0x00d9:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mApplications;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r0 = r24;
            r11 = r0.get(r10);	 Catch:{ all -> 0x01c4 }
            r11 = (android.content.pm.ApplicationInfo) r11;	 Catch:{ all -> 0x01c4 }
            r0 = r11.uid;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r23 = android.os.UserHandle.getUserId(r24);	 Catch:{ all -> 0x01c4 }
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mEntriesMap;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r0 = r24;
            r1 = r23;
            r24 = r0.get(r1);	 Catch:{ all -> 0x01c4 }
            r24 = (java.util.HashMap) r24;	 Catch:{ all -> 0x01c4 }
            r0 = r11.packageName;	 Catch:{ all -> 0x01c4 }
            r26 = r0;
            r0 = r24;
            r1 = r26;
            r24 = r0.get(r1);	 Catch:{ all -> 0x01c4 }
            if (r24 != 0) goto L_0x0126;
        L_0x0119:
            r18 = r18 + 1;
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r0 = r24;
            r0.getEntryLocked(r11);	 Catch:{ all -> 0x01c4 }
        L_0x0126:
            if (r23 == 0) goto L_0x01ac;
        L_0x0128:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mEntriesMap;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r26 = 0;
            r0 = r24;
            r1 = r26;
            r24 = r0.indexOfKey(r1);	 Catch:{ all -> 0x01c4 }
            if (r24 < 0) goto L_0x01ac;
        L_0x0140:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mEntriesMap;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r26 = 0;
            r0 = r24;
            r1 = r26;
            r24 = r0.get(r1);	 Catch:{ all -> 0x01c4 }
            r24 = (java.util.HashMap) r24;	 Catch:{ all -> 0x01c4 }
            r0 = r11.packageName;	 Catch:{ all -> 0x01c4 }
            r26 = r0;
            r0 = r24;
            r1 = r26;
            r7 = r0.get(r1);	 Catch:{ all -> 0x01c4 }
            r7 = (com.android.settingslib.applications.ApplicationsState.AppEntry) r7;	 Catch:{ all -> 0x01c4 }
            if (r7 == 0) goto L_0x01ac;
        L_0x0168:
            r0 = r7.info;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r0 = r24;
            r0 = r0.flags;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r26 = 8388608; // 0x800000 float:1.17549435E-38 double:4.144523E-317;
            r24 = r24 & r26;
            if (r24 != 0) goto L_0x01ac;
        L_0x0178:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mEntriesMap;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r26 = 0;
            r0 = r24;
            r1 = r26;
            r24 = r0.get(r1);	 Catch:{ all -> 0x01c4 }
            r24 = (java.util.HashMap) r24;	 Catch:{ all -> 0x01c4 }
            r0 = r11.packageName;	 Catch:{ all -> 0x01c4 }
            r26 = r0;
            r0 = r24;
            r1 = r26;
            r0.remove(r1);	 Catch:{ all -> 0x01c4 }
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mAppEntries;	 Catch:{ all -> 0x01c4 }
            r24 = r0;
            r0 = r24;
            r0.remove(r7);	 Catch:{ all -> 0x01c4 }
        L_0x01ac:
            r10 = r10 + 1;
            goto L_0x007e;
        L_0x01b0:
            monitor-exit(r25);
            r24 = 6;
            r0 = r18;
            r1 = r24;
            if (r0 < r1) goto L_0x01c7;
        L_0x01b9:
            r24 = 2;
            r0 = r30;
            r1 = r24;
            r0.sendEmptyMessage(r1);
            goto L_0x006d;
        L_0x01c4:
            r24 = move-exception;
            monitor-exit(r25);
            throw r24;
        L_0x01c7:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;
            r24 = r0;
            r0 = r24;
            r0 = r0.mMainHandler;
            r24 = r0;
            r25 = 8;
            r24 = r24.hasMessages(r25);
            if (r24 != 0) goto L_0x01ec;
        L_0x01db:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;
            r24 = r0;
            r0 = r24;
            r0 = r0.mMainHandler;
            r24 = r0;
            r25 = 8;
            r24.sendEmptyMessage(r25);
        L_0x01ec:
            r24 = 6;
            r0 = r30;
            r1 = r24;
            r0.sendEmptyMessage(r1);
            goto L_0x006d;
        L_0x01f7:
            r9 = new java.util.ArrayList;
            r9.<init>();
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;
            r24 = r0;
            r0 = r24;
            r0 = r0.mPm;
            r24 = r0;
            r0 = r24;
            r0.getHomeActivities(r9);
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;
            r24 = r0;
            r0 = r24;
            r0 = r0.mEntriesMap;
            r25 = r0;
            monitor-enter(r25);
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x026e }
            r24 = r0;
            r0 = r24;
            r0 = r0.mEntriesMap;	 Catch:{ all -> 0x026e }
            r24 = r0;
            r8 = r24.size();	 Catch:{ all -> 0x026e }
            r10 = 0;
        L_0x022b:
            if (r10 >= r8) goto L_0x0274;
        L_0x022d:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x026e }
            r24 = r0;
            r0 = r24;
            r0 = r0.mEntriesMap;	 Catch:{ all -> 0x026e }
            r24 = r0;
            r0 = r24;
            r22 = r0.valueAt(r10);	 Catch:{ all -> 0x026e }
            r22 = (java.util.HashMap) r22;	 Catch:{ all -> 0x026e }
            r6 = r9.iterator();	 Catch:{ all -> 0x026e }
        L_0x0245:
            r24 = r6.hasNext();	 Catch:{ all -> 0x026e }
            if (r24 == 0) goto L_0x0271;
        L_0x024b:
            r5 = r6.next();	 Catch:{ all -> 0x026e }
            r5 = (android.content.pm.ResolveInfo) r5;	 Catch:{ all -> 0x026e }
            r0 = r5.activityInfo;	 Catch:{ all -> 0x026e }
            r24 = r0;
            r0 = r24;
            r0 = r0.packageName;	 Catch:{ all -> 0x026e }
            r19 = r0;
            r0 = r22;
            r1 = r19;
            r7 = r0.get(r1);	 Catch:{ all -> 0x026e }
            r7 = (com.android.settingslib.applications.ApplicationsState.AppEntry) r7;	 Catch:{ all -> 0x026e }
            if (r7 == 0) goto L_0x0245;
        L_0x0267:
            r24 = 1;
            r0 = r24;
            r7.isHomeApp = r0;	 Catch:{ all -> 0x026e }
            goto L_0x0245;
        L_0x026e:
            r24 = move-exception;
            monitor-exit(r25);
            throw r24;
        L_0x0271:
            r10 = r10 + 1;
            goto L_0x022b;
        L_0x0274:
            monitor-exit(r25);
            r24 = 5;
            r0 = r30;
            r1 = r24;
            r0.sendEmptyMessage(r1);
            goto L_0x006d;
        L_0x0280:
            r24 = new android.content.Intent;
            r25 = "android.intent.action.MAIN";
            r26 = 0;
            r24.<init>(r25, r26);
            r25 = "android.intent.category.LAUNCHER";
            r14 = r24.addCategory(r25);
            r10 = 0;
        L_0x0292:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;
            r24 = r0;
            r0 = r24;
            r0 = r0.mEntriesMap;
            r24 = r0;
            r24 = r24.size();
            r0 = r24;
            if (r10 >= r0) goto L_0x035a;
        L_0x02a6:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;
            r24 = r0;
            r0 = r24;
            r0 = r0.mEntriesMap;
            r24 = r0;
            r0 = r24;
            r23 = r0.keyAt(r10);
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;
            r24 = r0;
            r0 = r24;
            r0 = r0.mPm;
            r24 = r0;
            r25 = 786944; // 0xc0200 float:1.102743E-39 double:3.88802E-318;
            r0 = r24;
            r1 = r25;
            r2 = r23;
            r12 = r0.queryIntentActivitiesAsUser(r14, r1, r2);
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;
            r24 = r0;
            r0 = r24;
            r0 = r0.mEntriesMap;
            r25 = r0;
            monitor-enter(r25);
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0352 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mEntriesMap;	 Catch:{ all -> 0x0352 }
            r24 = r0;
            r0 = r24;
            r22 = r0.valueAt(r10);	 Catch:{ all -> 0x0352 }
            r22 = (java.util.HashMap) r22;	 Catch:{ all -> 0x0352 }
            r4 = r12.size();	 Catch:{ all -> 0x0352 }
            r13 = 0;
        L_0x02f7:
            if (r13 >= r4) goto L_0x0355;
        L_0x02f9:
            r24 = r12.get(r13);	 Catch:{ all -> 0x0352 }
            r24 = (android.content.pm.ResolveInfo) r24;	 Catch:{ all -> 0x0352 }
            r0 = r24;
            r0 = r0.activityInfo;	 Catch:{ all -> 0x0352 }
            r24 = r0;
            r0 = r24;
            r0 = r0.packageName;	 Catch:{ all -> 0x0352 }
            r19 = r0;
            r0 = r22;
            r1 = r19;
            r7 = r0.get(r1);	 Catch:{ all -> 0x0352 }
            r7 = (com.android.settingslib.applications.ApplicationsState.AppEntry) r7;	 Catch:{ all -> 0x0352 }
            if (r7 == 0) goto L_0x0320;
        L_0x0317:
            r24 = 1;
            r0 = r24;
            r7.hasLauncherEntry = r0;	 Catch:{ all -> 0x0352 }
        L_0x031d:
            r13 = r13 + 1;
            goto L_0x02f7;
        L_0x0320:
            r24 = "ApplicationsState";
            r26 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0352 }
            r26.<init>();	 Catch:{ all -> 0x0352 }
            r27 = "Cannot find pkg: ";
            r26 = r26.append(r27);	 Catch:{ all -> 0x0352 }
            r0 = r26;
            r1 = r19;
            r26 = r0.append(r1);	 Catch:{ all -> 0x0352 }
            r27 = " on user ";
            r26 = r26.append(r27);	 Catch:{ all -> 0x0352 }
            r0 = r26;
            r1 = r23;
            r26 = r0.append(r1);	 Catch:{ all -> 0x0352 }
            r26 = r26.toString();	 Catch:{ all -> 0x0352 }
            r0 = r24;
            r1 = r26;
            android.util.Log.w(r0, r1);	 Catch:{ all -> 0x0352 }
            goto L_0x031d;
        L_0x0352:
            r24 = move-exception;
            monitor-exit(r25);
            throw r24;
        L_0x0355:
            monitor-exit(r25);
            r10 = r10 + 1;
            goto L_0x0292;
        L_0x035a:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;
            r24 = r0;
            r0 = r24;
            r0 = r0.mMainHandler;
            r24 = r0;
            r25 = 7;
            r24 = r24.hasMessages(r25);
            if (r24 != 0) goto L_0x037f;
        L_0x036e:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;
            r24 = r0;
            r0 = r24;
            r0 = r0.mMainHandler;
            r24 = r0;
            r25 = 7;
            r24.sendEmptyMessage(r25);
        L_0x037f:
            r24 = 3;
            r0 = r30;
            r1 = r24;
            r0.sendEmptyMessage(r1);
            goto L_0x006d;
        L_0x038a:
            r18 = 0;
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;
            r24 = r0;
            r0 = r24;
            r0 = r0.mEntriesMap;
            r25 = r0;
            monitor-enter(r25);
            r10 = 0;
        L_0x039a:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x043f }
            r24 = r0;
            r0 = r24;
            r0 = r0.mAppEntries;	 Catch:{ all -> 0x043f }
            r24 = r0;
            r24 = r24.size();	 Catch:{ all -> 0x043f }
            r0 = r24;
            if (r10 >= r0) goto L_0x0445;
        L_0x03ae:
            r24 = 2;
            r0 = r18;
            r1 = r24;
            if (r0 >= r1) goto L_0x0445;
        L_0x03b6:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x043f }
            r24 = r0;
            r0 = r24;
            r0 = r0.mAppEntries;	 Catch:{ all -> 0x043f }
            r24 = r0;
            r0 = r24;
            r7 = r0.get(r10);	 Catch:{ all -> 0x043f }
            r7 = (com.android.settingslib.applications.ApplicationsState.AppEntry) r7;	 Catch:{ all -> 0x043f }
            r0 = r7.icon;	 Catch:{ all -> 0x043f }
            r24 = r0;
            if (r24 == 0) goto L_0x03d9;
        L_0x03d0:
            r0 = r7.mounted;	 Catch:{ all -> 0x043f }
            r24 = r0;
            if (r24 == 0) goto L_0x03d9;
        L_0x03d6:
            r10 = r10 + 1;
            goto L_0x039a;
        L_0x03d9:
            monitor-enter(r7);	 Catch:{ all -> 0x043f }
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0442 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mContext;	 Catch:{ all -> 0x0442 }
            r24 = r0;
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0442 }
            r26 = r0;
            r0 = r26;
            r0 = r0.mPm;	 Catch:{ all -> 0x0442 }
            r26 = r0;
            r0 = r24;
            r1 = r26;
            r24 = r7.ensureIconLocked(r0, r1);	 Catch:{ all -> 0x0442 }
            if (r24 == 0) goto L_0x043d;
        L_0x03fc:
            r0 = r30;
            r0 = r0.mRunning;	 Catch:{ all -> 0x0442 }
            r24 = r0;
            if (r24 != 0) goto L_0x043b;
        L_0x0404:
            r24 = 1;
            r0 = r24;
            r1 = r30;
            r1.mRunning = r0;	 Catch:{ all -> 0x0442 }
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0442 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mMainHandler;	 Catch:{ all -> 0x0442 }
            r24 = r0;
            r26 = 1;
            r26 = java.lang.Integer.valueOf(r26);	 Catch:{ all -> 0x0442 }
            r27 = 6;
            r0 = r24;
            r1 = r27;
            r2 = r26;
            r15 = r0.obtainMessage(r1, r2);	 Catch:{ all -> 0x0442 }
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0442 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mMainHandler;	 Catch:{ all -> 0x0442 }
            r24 = r0;
            r0 = r24;
            r0.sendMessage(r15);	 Catch:{ all -> 0x0442 }
        L_0x043b:
            r18 = r18 + 1;
        L_0x043d:
            monitor-exit(r7);	 Catch:{ all -> 0x043f }
            goto L_0x03d6;
        L_0x043f:
            r24 = move-exception;
            monitor-exit(r25);
            throw r24;
        L_0x0442:
            r24 = move-exception;
            monitor-exit(r7);	 Catch:{ all -> 0x043f }
            throw r24;	 Catch:{ all -> 0x043f }
        L_0x0445:
            monitor-exit(r25);
            if (r18 <= 0) goto L_0x046d;
        L_0x0448:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;
            r24 = r0;
            r0 = r24;
            r0 = r0.mMainHandler;
            r24 = r0;
            r25 = 3;
            r24 = r24.hasMessages(r25);
            if (r24 != 0) goto L_0x046d;
        L_0x045c:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;
            r24 = r0;
            r0 = r24;
            r0 = r0.mMainHandler;
            r24 = r0;
            r25 = 3;
            r24.sendEmptyMessage(r25);
        L_0x046d:
            r24 = 2;
            r0 = r18;
            r1 = r24;
            if (r0 < r1) goto L_0x0480;
        L_0x0475:
            r24 = 3;
            r0 = r30;
            r1 = r24;
            r0.sendEmptyMessage(r1);
            goto L_0x006d;
        L_0x0480:
            r24 = 4;
            r0 = r30;
            r1 = r24;
            r0.sendEmptyMessage(r1);
            goto L_0x006d;
        L_0x048b:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;
            r24 = r0;
            r0 = r24;
            r0 = r0.mEntriesMap;
            r25 = r0;
            monitor-enter(r25);
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mCurComputingSizePkg;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            if (r24 == 0) goto L_0x04a8;
        L_0x04a6:
            monitor-exit(r25);
            return;
        L_0x04a8:
            r16 = android.os.SystemClock.uptimeMillis();	 Catch:{ all -> 0x0610 }
            r10 = 0;
        L_0x04ad:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mAppEntries;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r24 = r24.size();	 Catch:{ all -> 0x0610 }
            r0 = r24;
            if (r10 >= r0) goto L_0x05a9;
        L_0x04c1:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mAppEntries;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r0 = r24;
            r7 = r0.get(r10);	 Catch:{ all -> 0x0610 }
            r7 = (com.android.settingslib.applications.ApplicationsState.AppEntry) r7;	 Catch:{ all -> 0x0610 }
            r0 = r7.size;	 Catch:{ all -> 0x0610 }
            r26 = r0;
            r28 = -1;
            r24 = (r26 > r28 ? 1 : (r26 == r28 ? 0 : -1));
            if (r24 == 0) goto L_0x04e5;
        L_0x04df:
            r0 = r7.sizeStale;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            if (r24 == 0) goto L_0x05a5;
        L_0x04e5:
            r0 = r7.sizeLoadStart;	 Catch:{ all -> 0x0610 }
            r26 = r0;
            r28 = 0;
            r24 = (r26 > r28 ? 1 : (r26 == r28 ? 0 : -1));
            if (r24 == 0) goto L_0x04fb;
        L_0x04ef:
            r0 = r7.sizeLoadStart;	 Catch:{ all -> 0x0610 }
            r26 = r0;
            r28 = 20000; // 0x4e20 float:2.8026E-41 double:9.8813E-320;
            r28 = r16 - r28;
            r24 = (r26 > r28 ? 1 : (r26 == r28 ? 0 : -1));
            if (r24 >= 0) goto L_0x05a3;
        L_0x04fb:
            r0 = r30;
            r0 = r0.mRunning;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            if (r24 != 0) goto L_0x053a;
        L_0x0503:
            r24 = 1;
            r0 = r24;
            r1 = r30;
            r1.mRunning = r0;	 Catch:{ all -> 0x0610 }
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mMainHandler;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r26 = 1;
            r26 = java.lang.Integer.valueOf(r26);	 Catch:{ all -> 0x0610 }
            r27 = 6;
            r0 = r24;
            r1 = r27;
            r2 = r26;
            r15 = r0.obtainMessage(r1, r2);	 Catch:{ all -> 0x0610 }
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mMainHandler;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r0 = r24;
            r0.sendMessage(r15);	 Catch:{ all -> 0x0610 }
        L_0x053a:
            r0 = r16;
            r7.sizeLoadStart = r0;	 Catch:{ all -> 0x0610 }
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r0 = r7.info;	 Catch:{ all -> 0x0610 }
            r26 = r0;
            r0 = r26;
            r0 = r0.packageName;	 Catch:{ all -> 0x0610 }
            r26 = r0;
            r0 = r26;
            r1 = r24;
            r1.mCurComputingSizePkg = r0;	 Catch:{ all -> 0x0610 }
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r0 = r7.info;	 Catch:{ all -> 0x0610 }
            r26 = r0;
            r0 = r26;
            r0 = r0.uid;	 Catch:{ all -> 0x0610 }
            r26 = r0;
            r26 = android.os.UserHandle.getUserId(r26);	 Catch:{ all -> 0x0610 }
            r0 = r26;
            r1 = r24;
            r1.mCurComputingSizeUserId = r0;	 Catch:{ all -> 0x0610 }
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mPm;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0610 }
            r26 = r0;
            r0 = r26;
            r0 = r0.mCurComputingSizePkg;	 Catch:{ all -> 0x0610 }
            r26 = r0;
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0610 }
            r27 = r0;
            r0 = r27;
            r0 = r0.mCurComputingSizeUserId;	 Catch:{ all -> 0x0610 }
            r27 = r0;
            r0 = r30;
            r0 = r0.mStatsObserver;	 Catch:{ all -> 0x0610 }
            r28 = r0;
            r0 = r24;
            r1 = r26;
            r2 = r27;
            r3 = r28;
            r0.getPackageSizeInfoAsUser(r1, r2, r3);	 Catch:{ all -> 0x0610 }
        L_0x05a3:
            monitor-exit(r25);
            return;
        L_0x05a5:
            r10 = r10 + 1;
            goto L_0x04ad;
        L_0x05a9:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mMainHandler;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r26 = 5;
            r0 = r24;
            r1 = r26;
            r24 = r0.hasMessages(r1);	 Catch:{ all -> 0x0610 }
            if (r24 != 0) goto L_0x060d;
        L_0x05c1:
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mMainHandler;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r26 = 5;
            r0 = r24;
            r1 = r26;
            r0.sendEmptyMessage(r1);	 Catch:{ all -> 0x0610 }
            r24 = 0;
            r0 = r24;
            r1 = r30;
            r1.mRunning = r0;	 Catch:{ all -> 0x0610 }
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mMainHandler;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r26 = 0;
            r26 = java.lang.Integer.valueOf(r26);	 Catch:{ all -> 0x0610 }
            r27 = 6;
            r0 = r24;
            r1 = r27;
            r2 = r26;
            r15 = r0.obtainMessage(r1, r2);	 Catch:{ all -> 0x0610 }
            r0 = r30;
            r0 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r0 = r24;
            r0 = r0.mMainHandler;	 Catch:{ all -> 0x0610 }
            r24 = r0;
            r0 = r24;
            r0.sendMessage(r15);	 Catch:{ all -> 0x0610 }
        L_0x060d:
            monitor-exit(r25);
            goto L_0x006d;
        L_0x0610:
            r24 = move-exception;
            monitor-exit(r25);
            throw r24;
        L_0x0613:
            r24 = move-exception;
            r20 = r21;
            goto L_0x0062;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.settingslib.applications.ApplicationsState.BackgroundHandler.handleMessage(android.os.Message):void");
        }
    }

    public interface Callbacks {
        void onAllSizesComputed();

        void onLauncherInfoChanged();

        void onLoadEntriesCompleted();

        void onPackageIconChanged();

        void onPackageListChanged();

        void onPackageSizeChanged(String str);

        void onRebuildComplete(ArrayList<AppEntry> arrayList);

        void onRunningStateChanged(boolean z);
    }

    public static class CompoundFilter implements AppFilter {
        private final AppFilter mFirstFilter;
        private final AppFilter mSecondFilter;

        public CompoundFilter(AppFilter first, AppFilter second) {
            this.mFirstFilter = first;
            this.mSecondFilter = second;
        }

        public void init(Context context) {
            this.mFirstFilter.init(context);
            this.mSecondFilter.init(context);
        }

        public void init() {
            this.mFirstFilter.init();
            this.mSecondFilter.init();
        }

        public boolean filterApp(AppEntry info) {
            return this.mFirstFilter.filterApp(info) ? this.mSecondFilter.filterApp(info) : false;
        }
    }

    class MainHandler extends Handler {
        static final int MSG_ALL_SIZES_COMPUTED = 5;
        static final int MSG_LAUNCHER_INFO_CHANGED = 7;
        static final int MSG_LOAD_ENTRIES_COMPLETE = 8;
        static final int MSG_PACKAGE_ICON_CHANGED = 3;
        static final int MSG_PACKAGE_LIST_CHANGED = 2;
        static final int MSG_PACKAGE_SIZE_CHANGED = 4;
        static final int MSG_REBUILD_COMPLETE = 1;
        static final int MSG_RUNNING_STATE_CHANGED = 6;

        public MainHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            ApplicationsState.this.rebuildActiveSessions();
            int i;
            switch (msg.what) {
                case 1:
                    Session s = msg.obj;
                    if (ApplicationsState.this.mActiveSessions.contains(s)) {
                        s.mCallbacks.onRebuildComplete(s.mLastAppList);
                        return;
                    }
                    return;
                case 2:
                    for (i = 0; i < ApplicationsState.this.mActiveSessions.size(); i++) {
                        ((Session) ApplicationsState.this.mActiveSessions.get(i)).mCallbacks.onPackageListChanged();
                    }
                    return;
                case 3:
                    for (i = 0; i < ApplicationsState.this.mActiveSessions.size(); i++) {
                        ((Session) ApplicationsState.this.mActiveSessions.get(i)).mCallbacks.onPackageIconChanged();
                    }
                    return;
                case 4:
                    for (i = 0; i < ApplicationsState.this.mActiveSessions.size(); i++) {
                        ((Session) ApplicationsState.this.mActiveSessions.get(i)).mCallbacks.onPackageSizeChanged((String) msg.obj);
                    }
                    return;
                case 5:
                    for (i = 0; i < ApplicationsState.this.mActiveSessions.size(); i++) {
                        ((Session) ApplicationsState.this.mActiveSessions.get(i)).mCallbacks.onAllSizesComputed();
                    }
                    return;
                case 6:
                    for (i = 0; i < ApplicationsState.this.mActiveSessions.size(); i++) {
                        boolean z;
                        Callbacks callbacks = ((Session) ApplicationsState.this.mActiveSessions.get(i)).mCallbacks;
                        if (msg.arg1 != 0) {
                            z = true;
                        } else {
                            z = false;
                        }
                        callbacks.onRunningStateChanged(z);
                    }
                    return;
                case 7:
                    for (i = 0; i < ApplicationsState.this.mActiveSessions.size(); i++) {
                        ((Session) ApplicationsState.this.mActiveSessions.get(i)).mCallbacks.onLauncherInfoChanged();
                    }
                    return;
                case 8:
                    for (i = 0; i < ApplicationsState.this.mActiveSessions.size(); i++) {
                        ((Session) ApplicationsState.this.mActiveSessions.get(i)).mCallbacks.onLoadEntriesCompleted();
                    }
                    return;
                default:
                    return;
            }
        }
    }

    private class PackageIntentReceiver extends BroadcastReceiver {
        private PackageIntentReceiver() {
        }

        void registerReceiver() {
            IntentFilter filter = new IntentFilter("android.intent.action.PACKAGE_ADDED");
            filter.addAction("android.intent.action.PACKAGE_REMOVED");
            filter.addAction("android.intent.action.PACKAGE_CHANGED");
            filter.addDataScheme("package");
            ApplicationsState.this.mContext.registerReceiver(this, filter);
            IntentFilter sdFilter = new IntentFilter();
            sdFilter.addAction(IntentCompat.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
            sdFilter.addAction(IntentCompat.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
            ApplicationsState.this.mContext.registerReceiver(this, sdFilter);
            IntentFilter userFilter = new IntentFilter();
            userFilter.addAction("android.intent.action.USER_ADDED");
            userFilter.addAction("android.intent.action.USER_REMOVED");
            ApplicationsState.this.mContext.registerReceiver(this, userFilter);
        }

        void unregisterReceiver() {
            ApplicationsState.this.mContext.unregisterReceiver(this);
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void onReceive(android.content.Context r11, android.content.Intent r12) {
            /*
            r10 = this;
            r6 = 0;
            r8 = -10000; // 0xffffffffffffd8f0 float:NaN double:NaN;
            r0 = r12.getAction();
            r7 = "android.intent.action.PACKAGE_ADDED";
            r7 = r7.equals(r0);
            if (r7 == 0) goto L_0x0033;
        L_0x0010:
            r2 = r12.getData();
            r5 = r2.getEncodedSchemeSpecificPart();
            r3 = 0;
        L_0x0019:
            r6 = com.android.settingslib.applications.ApplicationsState.this;
            r6 = r6.mEntriesMap;
            r6 = r6.size();
            if (r3 >= r6) goto L_0x00eb;
        L_0x0023:
            r6 = com.android.settingslib.applications.ApplicationsState.this;
            r7 = com.android.settingslib.applications.ApplicationsState.this;
            r7 = r7.mEntriesMap;
            r7 = r7.keyAt(r3);
            r6.addPackage(r5, r7);
            r3 = r3 + 1;
            goto L_0x0019;
        L_0x0033:
            r7 = "android.intent.action.PACKAGE_REMOVED";
            r7 = r7.equals(r0);
            if (r7 == 0) goto L_0x005f;
        L_0x003c:
            r2 = r12.getData();
            r5 = r2.getEncodedSchemeSpecificPart();
            r3 = 0;
        L_0x0045:
            r6 = com.android.settingslib.applications.ApplicationsState.this;
            r6 = r6.mEntriesMap;
            r6 = r6.size();
            if (r3 >= r6) goto L_0x00eb;
        L_0x004f:
            r6 = com.android.settingslib.applications.ApplicationsState.this;
            r7 = com.android.settingslib.applications.ApplicationsState.this;
            r7 = r7.mEntriesMap;
            r7 = r7.keyAt(r3);
            r6.removePackage(r5, r7);
            r3 = r3 + 1;
            goto L_0x0045;
        L_0x005f:
            r7 = "android.intent.action.PACKAGE_CHANGED";
            r7 = r7.equals(r0);
            if (r7 == 0) goto L_0x008b;
        L_0x0068:
            r2 = r12.getData();
            r5 = r2.getEncodedSchemeSpecificPart();
            r3 = 0;
        L_0x0071:
            r6 = com.android.settingslib.applications.ApplicationsState.this;
            r6 = r6.mEntriesMap;
            r6 = r6.size();
            if (r3 >= r6) goto L_0x00eb;
        L_0x007b:
            r6 = com.android.settingslib.applications.ApplicationsState.this;
            r7 = com.android.settingslib.applications.ApplicationsState.this;
            r7 = r7.mEntriesMap;
            r7 = r7.keyAt(r3);
            r6.invalidatePackage(r5, r7);
            r3 = r3 + 1;
            goto L_0x0071;
        L_0x008b:
            r7 = "android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE";
            r7 = r7.equals(r0);
            if (r7 != 0) goto L_0x009d;
        L_0x0094:
            r7 = "android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE";
            r7 = r7.equals(r0);
            if (r7 == 0) goto L_0x00d6;
        L_0x009d:
            r7 = "android.intent.extra.changed_package_list";
            r4 = r12.getStringArrayExtra(r7);
            if (r4 == 0) goto L_0x00a9;
        L_0x00a6:
            r7 = r4.length;
            if (r7 != 0) goto L_0x00aa;
        L_0x00a9:
            return;
        L_0x00aa:
            r7 = "android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE";
            r1 = r7.equals(r0);
            if (r1 == 0) goto L_0x00eb;
        L_0x00b3:
            r7 = r4.length;
        L_0x00b4:
            if (r6 >= r7) goto L_0x00eb;
        L_0x00b6:
            r5 = r4[r6];
            r3 = 0;
        L_0x00b9:
            r8 = com.android.settingslib.applications.ApplicationsState.this;
            r8 = r8.mEntriesMap;
            r8 = r8.size();
            if (r3 >= r8) goto L_0x00d3;
        L_0x00c3:
            r8 = com.android.settingslib.applications.ApplicationsState.this;
            r9 = com.android.settingslib.applications.ApplicationsState.this;
            r9 = r9.mEntriesMap;
            r9 = r9.keyAt(r3);
            r8.invalidatePackage(r5, r9);
            r3 = r3 + 1;
            goto L_0x00b9;
        L_0x00d3:
            r6 = r6 + 1;
            goto L_0x00b4;
        L_0x00d6:
            r6 = "android.intent.action.USER_ADDED";
            r6 = r6.equals(r0);
            if (r6 == 0) goto L_0x00ec;
        L_0x00df:
            r6 = com.android.settingslib.applications.ApplicationsState.this;
            r7 = "android.intent.extra.user_handle";
            r7 = r12.getIntExtra(r7, r8);
            r6.addUser(r7);
        L_0x00eb:
            return;
        L_0x00ec:
            r6 = "android.intent.action.USER_REMOVED";
            r6 = r6.equals(r0);
            if (r6 == 0) goto L_0x00eb;
        L_0x00f5:
            r6 = com.android.settingslib.applications.ApplicationsState.this;
            r7 = "android.intent.extra.user_handle";
            r7 = r12.getIntExtra(r7, r8);
            r6.removeUser(r7);
            goto L_0x00eb;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.settingslib.applications.ApplicationsState.PackageIntentReceiver.onReceive(android.content.Context, android.content.Intent):void");
        }
    }

    public class Session {
        final Callbacks mCallbacks;
        ArrayList<AppEntry> mLastAppList;
        boolean mRebuildAsync;
        Comparator<AppEntry> mRebuildComparator;
        AppFilter mRebuildFilter;
        boolean mRebuildForeground;
        boolean mRebuildRequested;
        ArrayList<AppEntry> mRebuildResult;
        final Object mRebuildSync = new Object();
        boolean mResumed;

        Session(Callbacks callbacks) {
            this.mCallbacks = callbacks;
        }

        public void resume() {
            synchronized (ApplicationsState.this.mEntriesMap) {
                if (!this.mResumed) {
                    this.mResumed = true;
                    ApplicationsState.this.mSessionsChanged = true;
                    ApplicationsState.this.doResumeIfNeededLocked();
                }
            }
        }

        public void pause() {
            synchronized (ApplicationsState.this.mEntriesMap) {
                if (this.mResumed) {
                    this.mResumed = false;
                    ApplicationsState.this.mSessionsChanged = true;
                    ApplicationsState.this.mBackgroundHandler.removeMessages(1, this);
                    ApplicationsState.this.doPauseIfNeededLocked();
                }
            }
        }

        public ArrayList<AppEntry> getAllApps() {
            ArrayList<AppEntry> arrayList;
            synchronized (ApplicationsState.this.mEntriesMap) {
                arrayList = new ArrayList(ApplicationsState.this.mAppEntries);
            }
            return arrayList;
        }

        public ArrayList<AppEntry> rebuild(AppFilter filter, Comparator<AppEntry> comparator) {
            return rebuild(filter, comparator, true);
        }

        public ArrayList<AppEntry> rebuild(AppFilter filter, Comparator<AppEntry> comparator, boolean foreground) {
            synchronized (this.mRebuildSync) {
                synchronized (ApplicationsState.this.mRebuildingSessions) {
                    ApplicationsState.this.mRebuildingSessions.add(this);
                    this.mRebuildRequested = true;
                    this.mRebuildAsync = true;
                    this.mRebuildFilter = filter;
                    this.mRebuildComparator = comparator;
                    this.mRebuildForeground = foreground;
                    this.mRebuildResult = null;
                    if (!ApplicationsState.this.mBackgroundHandler.hasMessages(1)) {
                        ApplicationsState.this.mBackgroundHandler.sendMessage(ApplicationsState.this.mBackgroundHandler.obtainMessage(1));
                    }
                }
            }
            return null;
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        void handleRebuildList() {
            /*
            r10 = this;
            r8 = r10.mRebuildSync;
            monitor-enter(r8);
            r7 = r10.mRebuildRequested;	 Catch:{ all -> 0x006b }
            if (r7 != 0) goto L_0x0009;
        L_0x0007:
            monitor-exit(r8);
            return;
        L_0x0009:
            r3 = r10.mRebuildFilter;	 Catch:{ all -> 0x006b }
            r1 = r10.mRebuildComparator;	 Catch:{ all -> 0x006b }
            r7 = 0;
            r10.mRebuildRequested = r7;	 Catch:{ all -> 0x006b }
            r7 = 0;
            r10.mRebuildFilter = r7;	 Catch:{ all -> 0x006b }
            r7 = 0;
            r10.mRebuildComparator = r7;	 Catch:{ all -> 0x006b }
            r7 = r10.mRebuildForeground;	 Catch:{ all -> 0x006b }
            if (r7 == 0) goto L_0x0021;
        L_0x001a:
            r7 = -2;
            android.os.Process.setThreadPriority(r7);	 Catch:{ all -> 0x006b }
            r7 = 0;
            r10.mRebuildForeground = r7;	 Catch:{ all -> 0x006b }
        L_0x0021:
            monitor-exit(r8);
            if (r3 == 0) goto L_0x002b;
        L_0x0024:
            r7 = com.android.settingslib.applications.ApplicationsState.this;
            r7 = r7.mContext;
            r3.init(r7);
        L_0x002b:
            r7 = com.android.settingslib.applications.ApplicationsState.this;
            r8 = r7.mEntriesMap;
            monitor-enter(r8);
            r0 = new java.util.ArrayList;	 Catch:{ all -> 0x006e }
            r7 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x006e }
            r7 = r7.mAppEntries;	 Catch:{ all -> 0x006e }
            r0.<init>(r7);	 Catch:{ all -> 0x006e }
            monitor-exit(r8);
            r4 = new java.util.ArrayList;
            r4.<init>();
            r5 = 0;
        L_0x0040:
            r7 = r0.size();
            if (r5 >= r7) goto L_0x0074;
        L_0x0046:
            r2 = r0.get(r5);
            r2 = (com.android.settingslib.applications.ApplicationsState.AppEntry) r2;
            if (r2 == 0) goto L_0x0068;
        L_0x004e:
            if (r3 == 0) goto L_0x0056;
        L_0x0050:
            r7 = r3.filterApp(r2);
            if (r7 == 0) goto L_0x0068;
        L_0x0056:
            r7 = com.android.settingslib.applications.ApplicationsState.this;
            r8 = r7.mEntriesMap;
            monitor-enter(r8);
            if (r1 == 0) goto L_0x0064;
        L_0x005d:
            r7 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x0071 }
            r7 = r7.mContext;	 Catch:{ all -> 0x0071 }
            r2.ensureLabel(r7);	 Catch:{ all -> 0x0071 }
        L_0x0064:
            r4.add(r2);	 Catch:{ all -> 0x0071 }
            monitor-exit(r8);
        L_0x0068:
            r5 = r5 + 1;
            goto L_0x0040;
        L_0x006b:
            r7 = move-exception;
            monitor-exit(r8);
            throw r7;
        L_0x006e:
            r7 = move-exception;
            monitor-exit(r8);
            throw r7;
        L_0x0071:
            r7 = move-exception;
            monitor-exit(r8);
            throw r7;
        L_0x0074:
            if (r1 == 0) goto L_0x0079;
        L_0x0076:
            java.util.Collections.sort(r4, r1);
        L_0x0079:
            r8 = r10.mRebuildSync;
            monitor-enter(r8);
            r7 = r10.mRebuildRequested;	 Catch:{ all -> 0x00b0 }
            if (r7 != 0) goto L_0x008d;
        L_0x0080:
            r10.mLastAppList = r4;	 Catch:{ all -> 0x00b0 }
            r7 = r10.mRebuildAsync;	 Catch:{ all -> 0x00b0 }
            if (r7 != 0) goto L_0x0094;
        L_0x0086:
            r10.mRebuildResult = r4;	 Catch:{ all -> 0x00b0 }
            r7 = r10.mRebuildSync;	 Catch:{ all -> 0x00b0 }
            r7.notifyAll();	 Catch:{ all -> 0x00b0 }
        L_0x008d:
            monitor-exit(r8);
            r7 = 10;
            android.os.Process.setThreadPriority(r7);
            return;
        L_0x0094:
            r7 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x00b0 }
            r7 = r7.mMainHandler;	 Catch:{ all -> 0x00b0 }
            r9 = 1;
            r7 = r7.hasMessages(r9, r10);	 Catch:{ all -> 0x00b0 }
            if (r7 != 0) goto L_0x008d;
        L_0x009f:
            r7 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x00b0 }
            r7 = r7.mMainHandler;	 Catch:{ all -> 0x00b0 }
            r9 = 1;
            r6 = r7.obtainMessage(r9, r10);	 Catch:{ all -> 0x00b0 }
            r7 = com.android.settingslib.applications.ApplicationsState.this;	 Catch:{ all -> 0x00b0 }
            r7 = r7.mMainHandler;	 Catch:{ all -> 0x00b0 }
            r7.sendMessage(r6);	 Catch:{ all -> 0x00b0 }
            goto L_0x008d;
        L_0x00b0:
            r7 = move-exception;
            monitor-exit(r8);
            throw r7;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.settingslib.applications.ApplicationsState.Session.handleRebuildList():void");
        }

        public void release() {
            pause();
            synchronized (ApplicationsState.this.mEntriesMap) {
                ApplicationsState.this.mSessions.remove(this);
            }
        }
    }

    public static class VolumeFilter implements AppFilter {
        private final String mVolumeUuid;

        public VolumeFilter(String volumeUuid) {
            this.mVolumeUuid = volumeUuid;
        }

        public void init() {
        }

        public boolean filterApp(AppEntry info) {
            return Objects.equals(info.info.volumeUuid, this.mVolumeUuid);
        }
    }

    public static ApplicationsState getInstance(Application app) {
        ApplicationsState applicationsState;
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new ApplicationsState(app);
            }
            applicationsState = sInstance;
        }
        return applicationsState;
    }

    private ApplicationsState(Application app) {
        this.mContext = app;
        this.mPm = this.mContext.getPackageManager();
        this.mIpm = AppGlobals.getPackageManager();
        this.mUm = (UserManager) app.getSystemService("user");
        for (int userId : this.mUm.getProfileIdsWithDisabled(UserHandle.myUserId())) {
            this.mEntriesMap.put(userId, new HashMap());
        }
        this.mThread = new HandlerThread("ApplicationsState.Loader", 10);
        this.mThread.start();
        this.mBackgroundHandler = new BackgroundHandler(this.mThread.getLooper());
        this.mAdminRetrieveFlags = 41472;
        this.mRetrieveFlags = 33280;
        synchronized (this.mEntriesMap) {
            try {
                this.mEntriesMap.wait(1);
            } catch (InterruptedException e) {
            }
        }
    }

    public Looper getBackgroundLooper() {
        return this.mThread.getLooper();
    }

    public Session newSession(Callbacks callbacks) {
        Session s = new Session(callbacks);
        synchronized (this.mEntriesMap) {
            this.mSessions.add(s);
        }
        return s;
    }

    void doResumeIfNeededLocked() {
        if (!this.mResumed) {
            int i;
            this.mResumed = true;
            if (this.mPackageIntentReceiver == null) {
                this.mPackageIntentReceiver = new PackageIntentReceiver();
                this.mPackageIntentReceiver.registerReceiver();
            }
            this.mApplications = new ArrayList();
            for (UserInfo user : this.mUm.getProfiles(UserHandle.myUserId())) {
                try {
                    if (this.mEntriesMap.indexOfKey(user.id) < 0) {
                        this.mEntriesMap.put(user.id, new HashMap());
                    }
                    this.mApplications.addAll(this.mIpm.getInstalledApplications(user.isAdmin() ? this.mAdminRetrieveFlags : this.mRetrieveFlags, user.id).getList());
                } catch (RemoteException e) {
                }
            }
            if (this.mInterestingConfigChanges.applyNewConfig(this.mContext.getResources())) {
                clearEntries();
            } else {
                for (i = 0; i < this.mAppEntries.size(); i++) {
                    ((AppEntry) this.mAppEntries.get(i)).sizeStale = true;
                }
            }
            this.mHaveDisabledApps = false;
            i = 0;
            while (i < this.mApplications.size()) {
                ApplicationInfo info = (ApplicationInfo) this.mApplications.get(i);
                if (!info.enabled) {
                    if (info.enabledSetting != 3) {
                        this.mApplications.remove(i);
                        i--;
                        i++;
                    } else {
                        this.mHaveDisabledApps = true;
                    }
                }
                AppEntry entry = (AppEntry) ((HashMap) this.mEntriesMap.get(UserHandle.getUserId(info.uid))).get(info.packageName);
                if (entry != null) {
                    entry.info = info;
                }
                i++;
            }
            if (this.mAppEntries.size() > this.mApplications.size()) {
                clearEntries();
            }
            this.mCurComputingSizePkg = null;
            if (!this.mBackgroundHandler.hasMessages(2)) {
                this.mBackgroundHandler.sendEmptyMessage(2);
            }
        }
    }

    private void clearEntries() {
        for (int i = 0; i < this.mEntriesMap.size(); i++) {
            ((HashMap) this.mEntriesMap.valueAt(i)).clear();
        }
        this.mAppEntries.clear();
    }

    public boolean haveDisabledApps() {
        return this.mHaveDisabledApps;
    }

    void doPauseIfNeededLocked() {
        if (this.mResumed) {
            int i = 0;
            while (i < this.mSessions.size()) {
                if (!((Session) this.mSessions.get(i)).mResumed) {
                    i++;
                } else {
                    return;
                }
            }
            doPauseLocked();
        }
    }

    void doPauseLocked() {
        this.mResumed = false;
        if (this.mPackageIntentReceiver != null) {
            this.mPackageIntentReceiver.unregisterReceiver();
            this.mPackageIntentReceiver = null;
        }
    }

    public AppEntry getEntry(String packageName, int userId) {
        AppEntry entry;
        synchronized (this.mEntriesMap) {
            entry = (AppEntry) ((HashMap) this.mEntriesMap.get(userId)).get(packageName);
            if (entry == null) {
                ApplicationInfo info = getAppInfoLocked(packageName, userId);
                if (info == null) {
                    try {
                        info = this.mIpm.getApplicationInfo(packageName, 0, userId);
                    } catch (RemoteException e) {
                        Log.w(TAG, "getEntry couldn't reach PackageManager", e);
                        return null;
                    }
                }
                if (info != null) {
                    entry = getEntryLocked(info);
                }
            }
        }
        return entry;
    }

    private ApplicationInfo getAppInfoLocked(String pkg, int userId) {
        for (int i = 0; i < this.mApplications.size(); i++) {
            ApplicationInfo info = (ApplicationInfo) this.mApplications.get(i);
            if (pkg.equals(info.packageName) && userId == UserHandle.getUserId(info.uid)) {
                return info;
            }
        }
        return null;
    }

    public void ensureIcon(AppEntry entry) {
        if (entry.icon == null) {
            synchronized (entry) {
                entry.ensureIconLocked(this.mContext, this.mPm);
            }
        }
    }

    public void requestSize(String packageName, int userId) {
        synchronized (this.mEntriesMap) {
            if (((AppEntry) ((HashMap) this.mEntriesMap.get(userId)).get(packageName)) != null) {
                this.mPm.getPackageSizeInfoAsUser(packageName, userId, this.mBackgroundHandler.mStatsObserver);
            }
        }
    }

    long sumCacheSizes() {
        long sum = 0;
        synchronized (this.mEntriesMap) {
            for (int i = this.mAppEntries.size() - 1; i >= 0; i--) {
                sum += ((AppEntry) this.mAppEntries.get(i)).cacheSize;
            }
        }
        return sum;
    }

    int indexOfApplicationInfoLocked(String pkgName, int userId) {
        for (int i = this.mApplications.size() - 1; i >= 0; i--) {
            ApplicationInfo appInfo = (ApplicationInfo) this.mApplications.get(i);
            if (appInfo.packageName.equals(pkgName) && UserHandle.getUserId(appInfo.uid) == userId) {
                return i;
            }
        }
        return -1;
    }

    void addPackage(String pkgName, int userId) {
        try {
            synchronized (this.mEntriesMap) {
                if (!this.mResumed) {
                } else if (indexOfApplicationInfoLocked(pkgName, userId) >= 0) {
                } else {
                    ApplicationInfo info = this.mIpm.getApplicationInfo(pkgName, this.mUm.isUserAdmin(userId) ? this.mAdminRetrieveFlags : this.mRetrieveFlags, userId);
                    if (info == null) {
                        return;
                    }
                    if (!info.enabled) {
                        if (info.enabledSetting != 3) {
                            return;
                        }
                        this.mHaveDisabledApps = true;
                    }
                    this.mApplications.add(info);
                    if (!this.mBackgroundHandler.hasMessages(2)) {
                        this.mBackgroundHandler.sendEmptyMessage(2);
                    }
                    if (!this.mMainHandler.hasMessages(2)) {
                        this.mMainHandler.sendEmptyMessage(2);
                    }
                }
            }
        } catch (RemoteException e) {
        }
    }

    public void removePackage(String pkgName, int userId) {
        synchronized (this.mEntriesMap) {
            int idx = indexOfApplicationInfoLocked(pkgName, userId);
            if (idx >= 0) {
                AppEntry entry = (AppEntry) ((HashMap) this.mEntriesMap.get(userId)).get(pkgName);
                if (entry != null) {
                    ((HashMap) this.mEntriesMap.get(userId)).remove(pkgName);
                    this.mAppEntries.remove(entry);
                }
                ApplicationInfo info = (ApplicationInfo) this.mApplications.get(idx);
                this.mApplications.remove(idx);
                if (!info.enabled) {
                    this.mHaveDisabledApps = false;
                    for (int i = 0; i < this.mApplications.size(); i++) {
                        if (!((ApplicationInfo) this.mApplications.get(i)).enabled) {
                            this.mHaveDisabledApps = true;
                            break;
                        }
                    }
                }
                if (!this.mMainHandler.hasMessages(2)) {
                    this.mMainHandler.sendEmptyMessage(2);
                }
            }
        }
    }

    public void invalidatePackage(String pkgName, int userId) {
        removePackage(pkgName, userId);
        addPackage(pkgName, userId);
    }

    private void addUser(int userId) {
        if (ArrayUtils.contains(this.mUm.getProfileIdsWithDisabled(UserHandle.myUserId()), userId)) {
            synchronized (this.mEntriesMap) {
                this.mEntriesMap.put(userId, new HashMap());
                if (this.mResumed) {
                    doPauseLocked();
                    doResumeIfNeededLocked();
                }
                if (!this.mMainHandler.hasMessages(2)) {
                    this.mMainHandler.sendEmptyMessage(2);
                }
            }
        }
    }

    private void removeUser(int userId) {
        synchronized (this.mEntriesMap) {
            HashMap<String, AppEntry> userMap = (HashMap) this.mEntriesMap.get(userId);
            if (userMap != null) {
                for (AppEntry appEntry : userMap.values()) {
                    this.mAppEntries.remove(appEntry);
                    this.mApplications.remove(appEntry.info);
                }
                this.mEntriesMap.remove(userId);
                if (!this.mMainHandler.hasMessages(2)) {
                    this.mMainHandler.sendEmptyMessage(2);
                }
            }
        }
    }

    private AppEntry getEntryLocked(ApplicationInfo info) {
        int userId = UserHandle.getUserId(info.uid);
        AppEntry entry = (AppEntry) ((HashMap) this.mEntriesMap.get(userId)).get(info.packageName);
        if (entry == null) {
            Context context = this.mContext;
            long j = this.mCurId;
            this.mCurId = 1 + j;
            entry = new AppEntry(context, info, j);
            ((HashMap) this.mEntriesMap.get(userId)).put(info.packageName, entry);
            this.mAppEntries.add(entry);
            return entry;
        } else if (entry.info == info) {
            return entry;
        } else {
            entry.info = info;
            return entry;
        }
    }

    private long getTotalInternalSize(PackageStats ps) {
        if (ps != null) {
            return ps.codeSize + ps.dataSize;
        }
        return -2;
    }

    private long getTotalExternalSize(PackageStats ps) {
        if (ps != null) {
            return (((ps.externalCodeSize + ps.externalDataSize) + ps.externalCacheSize) + ps.externalMediaSize) + ps.externalObbSize;
        }
        return -2;
    }

    private String getSizeStr(long size) {
        if (size >= 0) {
            return Formatter.formatFileSize(this.mContext, size);
        }
        return null;
    }

    void rebuildActiveSessions() {
        synchronized (this.mEntriesMap) {
            if (this.mSessionsChanged) {
                this.mActiveSessions.clear();
                for (int i = 0; i < this.mSessions.size(); i++) {
                    Session s = (Session) this.mSessions.get(i);
                    if (s.mResumed) {
                        this.mActiveSessions.add(s);
                    }
                }
                return;
            }
        }
    }

    public static String normalize(String str) {
        return REMOVE_DIACRITICALS_PATTERN.matcher(Normalizer.normalize(str, Form.NFD)).replaceAll("").toLowerCase();
    }
}
