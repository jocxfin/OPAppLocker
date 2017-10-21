package com.android.settingslib.drawer;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import com.android.settingslib.R;

public class ProfileSelectDialog extends DialogFragment implements OnClickListener {
    private static final String ARG_SELECTED_TILE = "selectedTile";
    private Tile mSelectedTile;

    public static void show(FragmentManager manager, Tile tile) {
        ProfileSelectDialog dialog = new ProfileSelectDialog();
        Bundle args = new Bundle();
        args.putParcelable(ARG_SELECTED_TILE, tile);
        dialog.setArguments(args);
        dialog.show(manager, "select_profile");
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mSelectedTile = (Tile) getArguments().getParcelable(ARG_SELECTED_TILE);
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Context context = getActivity();
        Builder builder = new Builder(context);
        builder.setTitle(R.string.choose_profile).setAdapter(UserAdapter.createUserAdapter(UserManager.get(context), context, this.mSelectedTile.userHandle), this);
        return builder.create();
    }

    public void onClick(DialogInterface dialog, int which) {
        UserHandle user = (UserHandle) this.mSelectedTile.userHandle.get(which);
        this.mSelectedTile.intent.putExtra(SettingsDrawerActivity.EXTRA_SHOW_MENU, true);
        this.mSelectedTile.intent.addFlags(32768);
        getActivity().startActivityAsUser(this.mSelectedTile.intent, user);
        ((SettingsDrawerActivity) getActivity()).onProfileTileOpen();
    }
}
