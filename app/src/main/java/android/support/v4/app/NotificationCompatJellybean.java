package android.support.v4.app;

import android.app.Notification;
import android.app.Notification.BigPictureStyle;
import android.app.Notification.BigTextStyle;
import android.app.Notification.InboxStyle;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.NotificationCompatBase.Action;
import android.support.v4.app.NotificationCompatBase.Action.Factory;
import android.support.v4.app.RemoteInputCompatBase.RemoteInput;
import android.util.Log;
import android.util.SparseArray;
import android.widget.RemoteViews;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

class NotificationCompatJellybean {
    static final String EXTRA_ACTION_EXTRAS = "android.support.actionExtras";
    static final String EXTRA_ALLOW_GENERATED_REPLIES = "android.support.allowGeneratedReplies";
    static final String EXTRA_GROUP_KEY = "android.support.groupKey";
    static final String EXTRA_GROUP_SUMMARY = "android.support.isGroupSummary";
    static final String EXTRA_LOCAL_ONLY = "android.support.localOnly";
    static final String EXTRA_REMOTE_INPUTS = "android.support.remoteInputs";
    static final String EXTRA_SORT_KEY = "android.support.sortKey";
    static final String EXTRA_USE_SIDE_CHANNEL = "android.support.useSideChannel";
    private static final String KEY_ACTION_INTENT = "actionIntent";
    private static final String KEY_ALLOW_GENERATED_REPLIES = "allowGeneratedReplies";
    private static final String KEY_EXTRAS = "extras";
    private static final String KEY_ICON = "icon";
    private static final String KEY_REMOTE_INPUTS = "remoteInputs";
    private static final String KEY_TITLE = "title";
    public static final String TAG = "NotificationCompat";
    private static Class<?> sActionClass;
    private static Field sActionIconField;
    private static Field sActionIntentField;
    private static Field sActionTitleField;
    private static boolean sActionsAccessFailed;
    private static Field sActionsField;
    private static final Object sActionsLock = new Object();
    private static Field sExtrasField;
    private static boolean sExtrasFieldAccessFailed;
    private static final Object sExtrasLock = new Object();

    public static class Builder implements NotificationBuilderWithBuilderAccessor, NotificationBuilderWithActions {
        private android.app.Notification.Builder b;
        private List<Bundle> mActionExtrasList = new ArrayList();
        private RemoteViews mBigContentView;
        private RemoteViews mContentView;
        private final Bundle mExtras;

        public Builder(Context context, Notification n, CharSequence contentTitle, CharSequence contentText, CharSequence contentInfo, RemoteViews tickerView, int number, PendingIntent contentIntent, PendingIntent fullScreenIntent, Bitmap largeIcon, int progressMax, int progress, boolean progressIndeterminate, boolean useChronometer, int priority, CharSequence subText, boolean localOnly, Bundle extras, String groupKey, boolean groupSummary, String sortKey, RemoteViews contentView, RemoteViews bigContentView) {
            boolean z;
            android.app.Notification.Builder lights = new android.app.Notification.Builder(context).setWhen(n.when).setSmallIcon(n.icon, n.iconLevel).setContent(n.contentView).setTicker(n.tickerText, tickerView).setSound(n.sound, n.audioStreamType).setVibrate(n.vibrate).setLights(n.ledARGB, n.ledOnMS, n.ledOffMS);
            if ((n.flags & 2) != 0) {
                z = true;
            } else {
                z = false;
            }
            lights = lights.setOngoing(z);
            if ((n.flags & 8) != 0) {
                z = true;
            } else {
                z = false;
            }
            lights = lights.setOnlyAlertOnce(z);
            if ((n.flags & 16) != 0) {
                z = true;
            } else {
                z = false;
            }
            lights = lights.setAutoCancel(z).setDefaults(n.defaults).setContentTitle(contentTitle).setContentText(contentText).setSubText(subText).setContentInfo(contentInfo).setContentIntent(contentIntent).setDeleteIntent(n.deleteIntent);
            if ((n.flags & 128) != 0) {
                z = true;
            } else {
                z = false;
            }
            this.b = lights.setFullScreenIntent(fullScreenIntent, z).setLargeIcon(largeIcon).setNumber(number).setUsesChronometer(useChronometer).setPriority(priority).setProgress(progressMax, progress, progressIndeterminate);
            this.mExtras = new Bundle();
            if (extras != null) {
                this.mExtras.putAll(extras);
            }
            if (localOnly) {
                this.mExtras.putBoolean("android.support.localOnly", true);
            }
            if (groupKey != null) {
                this.mExtras.putString("android.support.groupKey", groupKey);
                if (groupSummary) {
                    this.mExtras.putBoolean("android.support.isGroupSummary", true);
                } else {
                    this.mExtras.putBoolean("android.support.useSideChannel", true);
                }
            }
            if (sortKey != null) {
                this.mExtras.putString("android.support.sortKey", sortKey);
            }
            this.mContentView = contentView;
            this.mBigContentView = bigContentView;
        }

        public void addAction(Action action) {
            this.mActionExtrasList.add(NotificationCompatJellybean.writeActionAndGetExtras(this.b, action));
        }

        public android.app.Notification.Builder getBuilder() {
            return this.b;
        }

        public Notification build() {
            Notification notif = this.b.build();
            Bundle extras = NotificationCompatJellybean.getExtras(notif);
            Bundle mergeBundle = new Bundle(this.mExtras);
            for (String key : this.mExtras.keySet()) {
                if (extras.containsKey(key)) {
                    mergeBundle.remove(key);
                }
            }
            extras.putAll(mergeBundle);
            SparseArray<Bundle> actionExtrasMap = NotificationCompatJellybean.buildActionExtrasMap(this.mActionExtrasList);
            if (actionExtrasMap != null) {
                NotificationCompatJellybean.getExtras(notif).putSparseParcelableArray("android.support.actionExtras", actionExtrasMap);
            }
            if (this.mContentView != null) {
                notif.contentView = this.mContentView;
            }
            if (this.mBigContentView != null) {
                notif.bigContentView = this.mBigContentView;
            }
            return notif;
        }
    }

    NotificationCompatJellybean() {
    }

    public static void addBigTextStyle(NotificationBuilderWithBuilderAccessor b, CharSequence bigContentTitle, boolean useSummary, CharSequence summaryText, CharSequence bigText) {
        BigTextStyle style = new BigTextStyle(b.getBuilder()).setBigContentTitle(bigContentTitle).bigText(bigText);
        if (useSummary) {
            style.setSummaryText(summaryText);
        }
    }

    public static void addBigPictureStyle(NotificationBuilderWithBuilderAccessor b, CharSequence bigContentTitle, boolean useSummary, CharSequence summaryText, Bitmap bigPicture, Bitmap bigLargeIcon, boolean bigLargeIconSet) {
        BigPictureStyle style = new BigPictureStyle(b.getBuilder()).setBigContentTitle(bigContentTitle).bigPicture(bigPicture);
        if (bigLargeIconSet) {
            style.bigLargeIcon(bigLargeIcon);
        }
        if (useSummary) {
            style.setSummaryText(summaryText);
        }
    }

    public static void addInboxStyle(NotificationBuilderWithBuilderAccessor b, CharSequence bigContentTitle, boolean useSummary, CharSequence summaryText, ArrayList<CharSequence> texts) {
        InboxStyle style = new InboxStyle(b.getBuilder()).setBigContentTitle(bigContentTitle);
        if (useSummary) {
            style.setSummaryText(summaryText);
        }
        for (CharSequence text : texts) {
            style.addLine(text);
        }
    }

    public static SparseArray<Bundle> buildActionExtrasMap(List<Bundle> actionExtrasList) {
        SparseArray<Bundle> actionExtrasMap = null;
        int count = actionExtrasList.size();
        for (int i = 0; i < count; i++) {
            Bundle actionExtras = (Bundle) actionExtrasList.get(i);
            if (actionExtras != null) {
                if (actionExtrasMap == null) {
                    actionExtrasMap = new SparseArray();
                }
                actionExtrasMap.put(i, actionExtras);
            }
        }
        return actionExtrasMap;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public static android.os.Bundle getExtras(android.app.Notification r8) {
        /*
        r7 = 0;
        r5 = sExtrasLock;
        monitor-enter(r5);
        r4 = sExtrasFieldAccessFailed;	 Catch:{ all -> 0x0067 }
        if (r4 == 0) goto L_0x000a;
    L_0x0008:
        monitor-exit(r5);
        return r7;
    L_0x000a:
        r4 = sExtrasField;	 Catch:{ IllegalAccessException -> 0x005c, NoSuchFieldException -> 0x004d }
        if (r4 != 0) goto L_0x0037;
    L_0x000e:
        r4 = android.app.Notification.class;
        r6 = "extras";
        r3 = r4.getDeclaredField(r6);	 Catch:{ IllegalAccessException -> 0x005c, NoSuchFieldException -> 0x004d }
        r4 = android.os.Bundle.class;
        r6 = r3.getType();	 Catch:{ IllegalAccessException -> 0x005c, NoSuchFieldException -> 0x004d }
        r4 = r4.isAssignableFrom(r6);	 Catch:{ IllegalAccessException -> 0x005c, NoSuchFieldException -> 0x004d }
        if (r4 != 0) goto L_0x0031;
    L_0x0023:
        r4 = "NotificationCompat";
        r6 = "Notification.extras field is not of type Bundle";
        android.util.Log.e(r4, r6);	 Catch:{ IllegalAccessException -> 0x005c, NoSuchFieldException -> 0x004d }
        r4 = 1;
        sExtrasFieldAccessFailed = r4;	 Catch:{ IllegalAccessException -> 0x005c, NoSuchFieldException -> 0x004d }
        monitor-exit(r5);
        return r7;
    L_0x0031:
        r4 = 1;
        r3.setAccessible(r4);	 Catch:{ IllegalAccessException -> 0x005c, NoSuchFieldException -> 0x004d }
        sExtrasField = r3;	 Catch:{ IllegalAccessException -> 0x005c, NoSuchFieldException -> 0x004d }
    L_0x0037:
        r4 = sExtrasField;	 Catch:{ IllegalAccessException -> 0x005c, NoSuchFieldException -> 0x004d }
        r2 = r4.get(r8);	 Catch:{ IllegalAccessException -> 0x005c, NoSuchFieldException -> 0x004d }
        r2 = (android.os.Bundle) r2;	 Catch:{ IllegalAccessException -> 0x005c, NoSuchFieldException -> 0x004d }
        if (r2 != 0) goto L_0x004b;
    L_0x0041:
        r2 = new android.os.Bundle;	 Catch:{ IllegalAccessException -> 0x005c, NoSuchFieldException -> 0x004d }
        r2.<init>();	 Catch:{ IllegalAccessException -> 0x005c, NoSuchFieldException -> 0x004d }
        r4 = sExtrasField;	 Catch:{ IllegalAccessException -> 0x005c, NoSuchFieldException -> 0x004d }
        r4.set(r8, r2);	 Catch:{ IllegalAccessException -> 0x005c, NoSuchFieldException -> 0x004d }
    L_0x004b:
        monitor-exit(r5);
        return r2;
    L_0x004d:
        r1 = move-exception;
        r4 = "NotificationCompat";
        r6 = "Unable to access notification extras";
        android.util.Log.e(r4, r6, r1);	 Catch:{ all -> 0x0067 }
    L_0x0057:
        r4 = 1;
        sExtrasFieldAccessFailed = r4;	 Catch:{ all -> 0x0067 }
        monitor-exit(r5);
        return r7;
    L_0x005c:
        r0 = move-exception;
        r4 = "NotificationCompat";
        r6 = "Unable to access notification extras";
        android.util.Log.e(r4, r6, r0);	 Catch:{ all -> 0x0067 }
        goto L_0x0057;
    L_0x0067:
        r4 = move-exception;
        monitor-exit(r5);
        throw r4;
        */
        throw new UnsupportedOperationException("Method not decompiled: android.support.v4.app.NotificationCompatJellybean.getExtras(android.app.Notification):android.os.Bundle");
    }

    public static Action readAction(Factory factory, RemoteInput.Factory remoteInputFactory, int icon, CharSequence title, PendingIntent actionIntent, Bundle extras) {
        RemoteInput[] remoteInputArr = null;
        boolean allowGeneratedReplies = false;
        if (extras != null) {
            remoteInputArr = RemoteInputCompatJellybean.fromBundleArray(BundleUtil.getBundleArrayFromBundle(extras, "android.support.remoteInputs"), remoteInputFactory);
            allowGeneratedReplies = extras.getBoolean(EXTRA_ALLOW_GENERATED_REPLIES);
        }
        return factory.build(icon, title, actionIntent, extras, remoteInputArr, allowGeneratedReplies);
    }

    public static Bundle writeActionAndGetExtras(android.app.Notification.Builder builder, Action action) {
        builder.addAction(action.getIcon(), action.getTitle(), action.getActionIntent());
        Bundle actionExtras = new Bundle(action.getExtras());
        if (action.getRemoteInputs() != null) {
            actionExtras.putParcelableArray("android.support.remoteInputs", RemoteInputCompatJellybean.toBundleArray(action.getRemoteInputs()));
        }
        actionExtras.putBoolean(EXTRA_ALLOW_GENERATED_REPLIES, action.getAllowGeneratedReplies());
        return actionExtras;
    }

    public static int getActionCount(Notification notif) {
        int length;
        synchronized (sActionsLock) {
            Object[] actionObjects = getActionObjectsLocked(notif);
            length = actionObjects != null ? actionObjects.length : 0;
        }
        return length;
    }

    public static Action getAction(Notification notif, int actionIndex, Factory factory, RemoteInput.Factory remoteInputFactory) {
        Action readAction;
        synchronized (sActionsLock) {
            try {
                Object actionObject = getActionObjectsLocked(notif)[actionIndex];
                Bundle bundle = null;
                Bundle extras = getExtras(notif);
                if (extras != null) {
                    SparseArray<Bundle> actionExtrasMap = extras.getSparseParcelableArray("android.support.actionExtras");
                    if (actionExtrasMap != null) {
                        bundle = (Bundle) actionExtrasMap.get(actionIndex);
                    }
                }
                readAction = readAction(factory, remoteInputFactory, sActionIconField.getInt(actionObject), (CharSequence) sActionTitleField.get(actionObject), (PendingIntent) sActionIntentField.get(actionObject), bundle);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Unable to access notification actions", e);
                sActionsAccessFailed = true;
                return null;
            }
        }
        return readAction;
    }

    private static Object[] getActionObjectsLocked(Notification notif) {
        synchronized (sActionsLock) {
            if (ensureActionReflectionReadyLocked()) {
                try {
                    Object[] objArr = (Object[]) sActionsField.get(notif);
                    return objArr;
                } catch (IllegalAccessException e) {
                    Log.e(TAG, "Unable to access notification actions", e);
                    sActionsAccessFailed = true;
                    return null;
                }
            }
            return null;
        }
    }

    private static boolean ensureActionReflectionReadyLocked() {
        boolean z = false;
        if (sActionsAccessFailed) {
            return false;
        }
        try {
            if (sActionsField == null) {
                sActionClass = Class.forName("android.app.Notification$Action");
                sActionIconField = sActionClass.getDeclaredField("icon");
                sActionTitleField = sActionClass.getDeclaredField(KEY_TITLE);
                sActionIntentField = sActionClass.getDeclaredField(KEY_ACTION_INTENT);
                sActionsField = Notification.class.getDeclaredField("actions");
                sActionsField.setAccessible(true);
            }
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Unable to access notification actions", e);
            sActionsAccessFailed = true;
        } catch (NoSuchFieldException e2) {
            Log.e(TAG, "Unable to access notification actions", e2);
            sActionsAccessFailed = true;
        }
        if (!sActionsAccessFailed) {
            z = true;
        }
        return z;
    }

    public static Action[] getActionsFromParcelableArrayList(ArrayList<Parcelable> parcelables, Factory actionFactory, RemoteInput.Factory remoteInputFactory) {
        if (parcelables == null) {
            return null;
        }
        Action[] actions = actionFactory.newArray(parcelables.size());
        for (int i = 0; i < actions.length; i++) {
            actions[i] = getActionFromBundle((Bundle) parcelables.get(i), actionFactory, remoteInputFactory);
        }
        return actions;
    }

    private static Action getActionFromBundle(Bundle bundle, Factory actionFactory, RemoteInput.Factory remoteInputFactory) {
        return actionFactory.build(bundle.getInt("icon"), bundle.getCharSequence(KEY_TITLE), (PendingIntent) bundle.getParcelable(KEY_ACTION_INTENT), bundle.getBundle(KEY_EXTRAS), RemoteInputCompatJellybean.fromBundleArray(BundleUtil.getBundleArrayFromBundle(bundle, KEY_REMOTE_INPUTS), remoteInputFactory), bundle.getBoolean(KEY_ALLOW_GENERATED_REPLIES));
    }

    public static ArrayList<Parcelable> getParcelableArrayListForActions(Action[] actions) {
        if (actions == null) {
            return null;
        }
        ArrayList<Parcelable> parcelables = new ArrayList(actions.length);
        for (Action action : actions) {
            parcelables.add(getBundleForAction(action));
        }
        return parcelables;
    }

    private static Bundle getBundleForAction(Action action) {
        Bundle bundle = new Bundle();
        bundle.putInt("icon", action.getIcon());
        bundle.putCharSequence(KEY_TITLE, action.getTitle());
        bundle.putParcelable(KEY_ACTION_INTENT, action.getActionIntent());
        bundle.putBundle(KEY_EXTRAS, action.getExtras());
        bundle.putParcelableArray(KEY_REMOTE_INPUTS, RemoteInputCompatJellybean.toBundleArray(action.getRemoteInputs()));
        return bundle;
    }

    public static boolean getLocalOnly(Notification notif) {
        return getExtras(notif).getBoolean("android.support.localOnly");
    }

    public static String getGroup(Notification n) {
        return getExtras(n).getString("android.support.groupKey");
    }

    public static boolean isGroupSummary(Notification n) {
        return getExtras(n).getBoolean("android.support.isGroupSummary");
    }

    public static String getSortKey(Notification n) {
        return getExtras(n).getString("android.support.sortKey");
    }
}
