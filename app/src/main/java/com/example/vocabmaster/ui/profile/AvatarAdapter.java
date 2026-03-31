package com.example.vocabmaster.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import com.example.vocabmaster.R;

public class AvatarAdapter extends BaseAdapter {
    private final int[] avatarResIds;
    private final LayoutInflater inflater;

    public AvatarAdapter(LayoutInflater inflater, int[] avatarResIds) {
        this.inflater = inflater;
        this.avatarResIds = avatarResIds;
    }

    @Override
    public int getCount() {
        return avatarResIds.length;
    }

    @Override
    public Object getItem(int position) {
        return avatarResIds[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_avatar, parent, false);
        }
        ImageView imageView = convertView.findViewById(R.id.img_avatar_item);
        imageView.setImageResource(avatarResIds[position]);
        return convertView;
    }
}
