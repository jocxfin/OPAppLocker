package com.android.settingslib.accounts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncAdapterType;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.UserHandle;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class AuthenticatorHelper extends BroadcastReceiver {
    private static final String ONEPLUS_ACCOUNT_TYPE = "com.oneplus.account";
    private static final String TAG = "AuthenticatorHelper";
    private final Map<String, Drawable> mAccTypeIconCache = new HashMap();
    private final HashMap<String, ArrayList<String>> mAccountTypeToAuthorities = new HashMap();
    private final Context mContext;
    private final ArrayList<String> mEnabledAccountTypes = new ArrayList();
    private final OnAccountsUpdateListener mListener;
    private boolean mListeningToAccountUpdates;
    private final Map<String, AuthenticatorDescription> mTypeToAuthDescription = new HashMap();
    private final UserHandle mUserHandle;

    public interface OnAccountsUpdateListener {
        void onAccountsUpdate(UserHandle userHandle);
    }

    public AuthenticatorHelper(Context context, UserHandle userHandle, OnAccountsUpdateListener listener) {
        this.mContext = context;
        this.mUserHandle = userHandle;
        this.mListener = listener;
        onAccountsUpdated(null);
    }

    public String[] getEnabledAccountTypes() {
        return (String[]) this.mEnabledAccountTypes.toArray(new String[this.mEnabledAccountTypes.size()]);
    }

    public void preloadDrawableForType(final Context context, final String accountType) {
        new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... params) {
                AuthenticatorHelper.this.getDrawableForType(context, accountType);
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public android.graphics.drawable.Drawable getDrawableForType(android.content.Context r8, java.lang.String r9) {
        /*
        r7 = this;
        r3 = 0;
        r5 = r7.mAccTypeIconCache;
        monitor-enter(r5);
        r4 = r7.mAccTypeIconCache;	 Catch:{ all -> 0x0056 }
        r4 = r4.containsKey(r9);	 Catch:{ all -> 0x0056 }
        if (r4 == 0) goto L_0x0016;
    L_0x000c:
        r4 = r7.mAccTypeIconCache;	 Catch:{ all -> 0x0056 }
        r4 = r4.get(r9);	 Catch:{ all -> 0x0056 }
        r4 = (android.graphics.drawable.Drawable) r4;	 Catch:{ all -> 0x0056 }
        monitor-exit(r5);
        return r4;
    L_0x0016:
        monitor-exit(r5);
        r4 = r7.mTypeToAuthDescription;
        r4 = r4.containsKey(r9);
        if (r4 == 0) goto L_0x004b;
    L_0x001f:
        r4 = r7.mTypeToAuthDescription;	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
        r1 = r4.get(r9);	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
        r1 = (android.accounts.AuthenticatorDescription) r1;	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
        r4 = r1.packageName;	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
        r5 = r7.mUserHandle;	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
        r6 = 0;
        r0 = r8.createPackageContextAsUser(r4, r6, r5);	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
        r4 = r7.mContext;	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
        r4 = r4.getPackageManager();	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
        r5 = r1.iconId;	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
        r5 = r0.getDrawable(r5);	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
        r6 = r7.mUserHandle;	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
        r3 = r4.getUserBadgedIcon(r5, r6);	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
        r5 = r7.mAccTypeIconCache;	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
        monitor-enter(r5);	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
        r4 = r7.mAccTypeIconCache;	 Catch:{ all -> 0x0059 }
        r4.put(r9, r3);	 Catch:{ all -> 0x0059 }
        monitor-exit(r5);	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
    L_0x004b:
        if (r3 != 0) goto L_0x0055;
    L_0x004d:
        r4 = r8.getPackageManager();
        r3 = r4.getDefaultActivityIcon();
    L_0x0055:
        return r3;
    L_0x0056:
        r4 = move-exception;
        monitor-exit(r5);
        throw r4;
    L_0x0059:
        r4 = move-exception;
        monitor-exit(r5);	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
        throw r4;	 Catch:{ NameNotFoundException -> 0x005c, NameNotFoundException -> 0x005c }
    L_0x005c:
        r2 = move-exception;
        goto L_0x004b;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.settingslib.accounts.AuthenticatorHelper.getDrawableForType(android.content.Context, java.lang.String):android.graphics.drawable.Drawable");
    }

    public CharSequence getLabelForType(Context context, String accountType) {
        CharSequence label = null;
        if (this.mTypeToAuthDescription.containsKey(accountType)) {
            try {
                AuthenticatorDescription desc = (AuthenticatorDescription) this.mTypeToAuthDescription.get(accountType);
                label = context.createPackageContextAsUser(desc.packageName, 0, this.mUserHandle).getResources().getText(desc.labelId);
            } catch (NameNotFoundException e) {
                Log.w(TAG, "No label name for account type " + accountType);
            } catch (NotFoundException e2) {
                Log.w(TAG, "No label icon for account type " + accountType);
            }
        }
        return label;
    }

    public String getPackageForType(String accountType) {
        if (this.mTypeToAuthDescription.containsKey(accountType)) {
            return ((AuthenticatorDescription) this.mTypeToAuthDescription.get(accountType)).packageName;
        }
        return null;
    }

    public int getLabelIdForType(String accountType) {
        if (this.mTypeToAuthDescription.containsKey(accountType)) {
            return ((AuthenticatorDescription) this.mTypeToAuthDescription.get(accountType)).labelId;
        }
        return -1;
    }

    public void updateAuthDescriptions(Context context) {
        AuthenticatorDescription[] authDescs = AccountManager.get(context).getAuthenticatorTypesAsUser(this.mUserHandle.getIdentifier());
        for (int i = 0; i < authDescs.length; i++) {
            this.mTypeToAuthDescription.put(authDescs[i].type, authDescs[i]);
        }
    }

    public boolean containsAccountType(String accountType) {
        return this.mTypeToAuthDescription.containsKey(accountType);
    }

    public AuthenticatorDescription getAccountTypeDescription(String accountType) {
        return (AuthenticatorDescription) this.mTypeToAuthDescription.get(accountType);
    }

    public boolean hasAccountPreferences(String accountType) {
        if (containsAccountType(accountType)) {
            AuthenticatorDescription desc = getAccountTypeDescription(accountType);
            if (!(desc == null || desc.accountPreferencesId == 0)) {
                return true;
            }
        }
        return false;
    }

    void onAccountsUpdated(Account[] accounts) {
        updateAuthDescriptions(this.mContext);
        if (accounts == null) {
            accounts = AccountManager.get(this.mContext).getAccountsAsUser(this.mUserHandle.getIdentifier());
        }
        this.mEnabledAccountTypes.clear();
        this.mAccTypeIconCache.clear();
        for (Account account : accounts) {
            if (ONEPLUS_ACCOUNT_TYPE.equals(account.type)) {
                Log.v(TAG, "Ignore OnePlus account entry point");
            } else if (!this.mEnabledAccountTypes.contains(account.type)) {
                this.mEnabledAccountTypes.add(account.type);
            }
        }
        buildAccountTypeToAuthoritiesMap();
        if (this.mListeningToAccountUpdates) {
            this.mListener.onAccountsUpdate(this.mUserHandle);
        }
    }

    public void onReceive(Context context, Intent intent) {
        onAccountsUpdated(AccountManager.get(this.mContext).getAccountsAsUser(this.mUserHandle.getIdentifier()));
    }

    public void listenToAccountUpdates() {
        if (!this.mListeningToAccountUpdates) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("android.accounts.LOGIN_ACCOUNTS_CHANGED");
            intentFilter.addAction("android.intent.action.DEVICE_STORAGE_OK");
            this.mContext.registerReceiverAsUser(this, this.mUserHandle, intentFilter, null, null);
            this.mListeningToAccountUpdates = true;
        }
    }

    public void stopListeningToAccountUpdates() {
        if (this.mListeningToAccountUpdates) {
            this.mContext.unregisterReceiver(this);
            this.mListeningToAccountUpdates = false;
        }
    }

    public ArrayList<String> getAuthoritiesForAccountType(String type) {
        return (ArrayList) this.mAccountTypeToAuthorities.get(type);
    }

    private void buildAccountTypeToAuthoritiesMap() {
        this.mAccountTypeToAuthorities.clear();
        for (SyncAdapterType sa : ContentResolver.getSyncAdapterTypesAsUser(this.mUserHandle.getIdentifier())) {
            ArrayList<String> authorities = (ArrayList) this.mAccountTypeToAuthorities.get(sa.accountType);
            if (authorities == null) {
                authorities = new ArrayList();
                this.mAccountTypeToAuthorities.put(sa.accountType, authorities);
            }
            if (Log.isLoggable(TAG, 2)) {
                Log.v(TAG, "Added authority " + sa.authority + " to accountType " + sa.accountType);
            }
            authorities.add(sa.authority);
        }
    }
}
