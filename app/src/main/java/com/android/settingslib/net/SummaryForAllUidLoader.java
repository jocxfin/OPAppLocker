package com.android.settingslib.net;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.net.INetworkStatsSession;
import android.net.NetworkStats;
import android.net.NetworkTemplate;
import android.os.Bundle;
import android.os.RemoteException;

public class SummaryForAllUidLoader extends AsyncTaskLoader<NetworkStats> {
    private static final String KEY_END = "end";
    private static final String KEY_START = "start";
    private static final String KEY_TEMPLATE = "template";
    private final Bundle mArgs;
    private final INetworkStatsSession mSession;

    public static Bundle buildArgs(NetworkTemplate template, long start, long end) {
        Bundle args = new Bundle();
        args.putParcelable(KEY_TEMPLATE, template);
        args.putLong(KEY_START, start);
        args.putLong(KEY_END, end);
        return args;
    }

    public SummaryForAllUidLoader(Context context, INetworkStatsSession session, Bundle args) {
        super(context);
        this.mSession = session;
        this.mArgs = args;
    }

    protected void onStartLoading() {
        super.onStartLoading();
        forceLoad();
    }

    public NetworkStats loadInBackground() {
        try {
            return this.mSession.getSummaryForAllUid((NetworkTemplate) this.mArgs.getParcelable(KEY_TEMPLATE), this.mArgs.getLong(KEY_START), this.mArgs.getLong(KEY_END), false);
        } catch (RemoteException e) {
            return null;
        }
    }

    protected void onStopLoading() {
        super.onStopLoading();
        cancelLoad();
    }

    protected void onReset() {
        super.onReset();
        cancelLoad();
    }
}
