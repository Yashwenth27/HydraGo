package com.example.hydrago;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class RouteSegmentAdapter extends RecyclerView.Adapter<RouteSegmentAdapter.ViewHolder> {

    private final List<RouteSegment> segments;

    public RouteSegmentAdapter(List<RouteSegment> segments) {
        this.segments = segments;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_route_segment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RouteSegment segment = segments.get(position);

        holder.tvLineName.setText(segment.getLineName());
        try {
            int color = Color.parseColor(segment.getLineColor());
            holder.tvLineName.setTextColor(color);
            holder.viewLineColor.setBackgroundColor(color);
        } catch (Exception e) {
            // Fallback color
            holder.viewLineColor.setBackgroundColor(Color.GRAY);
        }

        holder.tvStartStation.setText(segment.getStartStation());
        holder.tvEndStation.setText(segment.getEndStation());

        StringBuilder stopsBuilder = new StringBuilder();
        // Limit display to 3 stops + "x more" if too long, or show all
        int count = 0;
        for (String stop : segment.getIntermediateStops()) {
            stopsBuilder.append("• ").append(stop).append("\n");
            count++;
        }

        if (count == 0) {
            holder.tvIntermediateStops.setVisibility(View.GONE);
        } else {
            holder.tvIntermediateStops.setVisibility(View.VISIBLE);
            holder.tvIntermediateStops.setText(stopsBuilder.toString().trim());
        }
    }

    @Override
    public int getItemCount() {
        return segments.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View viewLineColor;
        TextView tvLineName, tvStartStation, tvEndStation, tvIntermediateStops;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewLineColor = itemView.findViewById(R.id.view_line_color);
            tvLineName = itemView.findViewById(R.id.tv_line_name);
            tvStartStation = itemView.findViewById(R.id.tv_start_station);
            tvEndStation = itemView.findViewById(R.id.tv_end_station);
            tvIntermediateStops = itemView.findViewById(R.id.tv_intermediate_stops);
        }
    }
}