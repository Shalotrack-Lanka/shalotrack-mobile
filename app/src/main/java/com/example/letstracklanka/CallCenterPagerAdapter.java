package com.example.letstracklanka;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class CallCenterPagerAdapter extends RecyclerView.Adapter<CallCenterPagerAdapter.SlideViewHolder> {

    private String[] titles = {
            "24/7 Call Center Monitoring",
            "SOS Emergency",
            "Towing Alert",
            "Device Disconnection",
            "Driver Recognition"
    };

    private String[] descriptions = {
            "A Secure Operating Center monitor your vehicle round-the-clock, ensuring immediate action",
            "Press the SOS button to send a distress signal. Monitoring centre contacts emergency services instantly",
            "Movement without ignition triggers a tow-away alert. Monitoring centre verifies and escalates to police if needed",
            "Tampering or unplugging the tracker sends an instant alert to Monitoring centre for investigation",
            "If the vehicle starts without a valid ID tag or phone recognition, Monitoring centre receives a silent theft alert"
    };

    // ඔයා හදාගත්ත අයිකන් ටික මෙතනට දාන්න (දැනට මම Android Default අයිකන් දීලා තියෙනවා)
    private int[] icons = {
            android.R.drawable.ic_menu_call,
            android.R.drawable.ic_dialog_alert,
            android.R.drawable.ic_menu_directions,
            android.R.drawable.ic_delete,
            android.R.drawable.ic_menu_myplaces
    };

    @NonNull
    @Override
    public SlideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_call_center_slide, parent, false);
        return new SlideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlideViewHolder holder, int position) {
        holder.tvTitle.setText(titles[position]);
        holder.tvDescription.setText(descriptions[position]);
        holder.imgIcon.setImageResource(icons[position]);
        // අයිකන් එක නිල් පාට කරන්න
        holder.imgIcon.setColorFilter(android.graphics.Color.parseColor("#1877F2"));
    }

    @Override
    public int getItemCount() {
        return titles.length;
    }

    static class SlideViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDescription;
        ImageView imgIcon;

        SlideViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSlideTitle);
            tvDescription = itemView.findViewById(R.id.tvSlideDescription);
            imgIcon = itemView.findViewById(R.id.imgSlideIcon);
        }
    }
}