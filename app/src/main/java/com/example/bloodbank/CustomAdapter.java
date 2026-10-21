package com.example.bloodbank;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class CustomAdapter extends BaseAdapter {

    private final ArrayList<Integer> idList;
    private final ArrayList<String> nameList;
    private final ArrayList<Integer> ageList;
    private final ArrayList<String> bloodGroupList;
    private final LayoutInflater inflater;

    public CustomAdapter(Context context, ArrayList<Integer> idList, ArrayList<String> nameList, ArrayList<Integer> ageList, ArrayList<String> bloodGroupList) {
        this.idList = idList;
        this.nameList = nameList;
        this.ageList = ageList;
        this.bloodGroupList = bloodGroupList;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return idList.size();
    }

    @Override
    public Object getItem(int position) {
        return nameList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return idList.get(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View itemView = (convertView != null) ? convertView : inflater.inflate(R.layout.row, parent, false);

        ImageView imgIcon = itemView.findViewById(R.id.imgDonorIcon);
        TextView tvName = itemView.findViewById(R.id.tvDonorName);
        TextView tvDetails = itemView.findViewById(R.id.tvDonorDetails);

        tvName.setText(nameList.get(position));
        String details = ageList.get(position) + " | " + bloodGroupList.get(position);
        tvDetails.setText(details);
        imgIcon.setImageResource(R.drawable.ic_blood_drop_pulse);

        return itemView;
    }
}