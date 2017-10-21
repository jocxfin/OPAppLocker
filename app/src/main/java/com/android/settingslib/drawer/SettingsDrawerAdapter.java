package com.android.settingslib.drawer;

import android.graphics.drawable.Icon;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.settingslib.R;
import java.util.ArrayList;
import java.util.List;

public class SettingsDrawerAdapter extends BaseAdapter {
    private final SettingsDrawerActivity mActivity;
    private final ArrayList<Item> mItems = new ArrayList();

    private static class Item {
        public Icon icon;
        public CharSequence label;
        public Tile tile;

        private Item() {
        }
    }

    private static class ViewHolder {
        ImageView icon;
        TextView title;

        private ViewHolder() {
        }
    }

    public SettingsDrawerAdapter(SettingsDrawerActivity activity) {
        this.mActivity = activity;
    }

    void updateCategories() {
        List<DashboardCategory> categories = this.mActivity.getDashboardCategories();
        this.mItems.clear();
        this.mItems.add(null);
        Item tile = new Item();
        tile.label = this.mActivity.getString(R.string.home);
        tile.icon = Icon.createWithResource(this.mActivity, R.drawable.home);
        this.mItems.add(tile);
        for (int i = 0; i < categories.size(); i++) {
            Item category = new Item();
            category.icon = null;
            DashboardCategory dashboardCategory = (DashboardCategory) categories.get(i);
            category.label = dashboardCategory.title;
            this.mItems.add(category);
            for (int j = 0; j < dashboardCategory.tiles.size(); j++) {
                tile = new Item();
                Tile dashboardTile = (Tile) dashboardCategory.tiles.get(j);
                tile.label = dashboardTile.title;
                tile.icon = dashboardTile.icon;
                tile.tile = dashboardTile;
                this.mItems.add(tile);
            }
        }
        notifyDataSetChanged();
    }

    public Tile getTile(int position) {
        return this.mItems.get(position) != null ? ((Item) this.mItems.get(position)).tile : null;
    }

    public int getCount() {
        return this.mItems.size();
    }

    public Object getItem(int position) {
        return this.mItems.get(position);
    }

    public long getItemId(int position) {
        return (long) position;
    }

    public boolean isEnabled(int position) {
        return (this.mItems.get(position) == null || ((Item) this.mItems.get(position)).icon == null) ? false : true;
    }

    public int getItemViewType(int position) {
        Item item = (Item) this.mItems.get(position);
        if (item == null) {
            return 0;
        }
        if (item.icon != null) {
            return 1;
        }
        return 2;
    }

    public int getViewTypeCount() {
        return 3;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        Item item = (Item) this.mItems.get(position);
        if (item == null) {
            return LayoutInflater.from(this.mActivity).inflate(R.layout.drawer_spacer, parent, false);
        }
        ViewHolder holder;
        if (convertView != null && convertView.getId() == R.id.spacer) {
            convertView = null;
        }
        boolean isTile = item.icon != null;
        if (convertView == null) {
            int i;
            holder = new ViewHolder();
            LayoutInflater from = LayoutInflater.from(this.mActivity);
            if (isTile) {
                i = R.layout.drawer_item;
            } else {
                i = R.layout.drawer_category;
            }
            convertView = from.inflate(i, parent, false);
            if (isTile) {
                holder.icon = (ImageView) convertView.findViewById(16908294);
            }
            holder.title = (TextView) convertView.findViewById(16908310);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        if (isTile) {
            holder.icon.setImageIcon(item.icon);
        }
        holder.title.setText(item.label);
        return convertView;
    }
}
