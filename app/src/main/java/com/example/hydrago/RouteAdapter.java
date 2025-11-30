package com.example.hydrago;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.hydrago.ApiResponse;
import java.util.ArrayList;
import java.util.List;

public class RouteAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // Internal model to hold data + color for every row
    private static class DisplayItem {
        int type; // 0 = Header, 1 = Station
        String text;
        int color; // The resolved integer color (e.g., Red/Green)

        DisplayItem(int type, String text, int color) {
            this.type = type;
            this.text = text;
            this.color = color;
        }
    }

    private final List<DisplayItem> items = new ArrayList<>();
    private Context context;

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        this.context = parent.getContext();
        if (viewType == 0) { // Header
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_line_header, parent, false);
            return new HeaderVH(v);
        } else { // Station
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_station, parent, false);
            return new StationVH(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DisplayItem item = items.get(position);

        if (holder instanceof HeaderVH) {
            HeaderVH vh = (HeaderVH) holder;
            vh.text.setText(item.text);

            // Set Header Text Color
            vh.text.setTextColor(item.color);
            // Optional: Set a light background tint based on the color
            vh.text.setBackgroundTintList(ColorStateList.valueOf(item.color));
            vh.text.setBackgroundTintMode(android.graphics.PorterDuff.Mode.SRC_ATOP);
            vh.text.getBackground().setAlpha(30); // Make background very light (10-15% opacity)

        } else if (holder instanceof StationVH) {
            StationVH vh = (StationVH) holder;
            vh.text.setText(item.text);

            // 1. Color the Vertical Line
            vh.topLine.setBackgroundColor(item.color);

            // 2. Color the Dot
            vh.dot.setBackgroundTintList(ColorStateList.valueOf(item.color));

            // Logic to hide top line for first item after a header
            boolean isFirstInSection = position == 0 || items.get(position - 1).type == 0;
            vh.topLine.setVisibility(isFirstInSection ? View.INVISIBLE : View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    @Override
    public int getItemViewType(int position) { return items.get(position).type; }

    // DATA PROCESSING LOGIC
    public void setData(List<ApiResponse.RouteLeg> legs, Context ctx) {
        items.clear();

        for (ApiResponse.RouteLeg leg : legs) {
            // 1. Determine Color based on Line Info string
            int legColor = ContextCompat.getColor(ctx, R.color.line_default);
            String info = leg.line_info != null ? leg.line_info.toLowerCase() : "";

            if (info.contains("red")) legColor = ContextCompat.getColor(ctx, R.color.line_red);
            else if (info.contains("green")) legColor = ContextCompat.getColor(ctx, R.color.line_green);
            else if (info.contains("blue")) legColor = ContextCompat.getColor(ctx, R.color.line_blue);

            // 2. Add Header
            if (leg.line_info != null) {
                items.add(new DisplayItem(0, leg.line_info, legColor));
            }

            // 3. Add Stations (passing the same color down)
            for (String station : leg.stations) {
                items.add(new DisplayItem(1, station, legColor));
            }
        }
        notifyDataSetChanged();
    }

    // ViewHolders
    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView text;
        HeaderVH(View v) { super(v); text = v.findViewById(R.id.headerText); }
    }

    static class StationVH extends RecyclerView.ViewHolder {
        TextView text;
        View topLine, dot;
        StationVH(View v) {
            super(v);
            text = v.findViewById(R.id.stationName);
            topLine = v.findViewById(R.id.lineTop);
            dot = v.findViewById(R.id.dot);
        }
    }
}