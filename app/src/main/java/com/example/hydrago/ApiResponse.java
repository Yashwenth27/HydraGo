package com.example.hydrago;


import java.util.List;
import com.google.gson.annotations.SerializedName;

public class ApiResponse {
    @SerializedName("basic_info") public BasicInfo basicInfo;
    @SerializedName("journey_stats") public Stats journeyStats;
    @SerializedName("fares") public Fares fares;
    @SerializedName("route_sequence") public List<RouteLeg> routeSequence;

    public static class BasicInfo { public String source; public String destination; }
    public static class Stats {
        public String travel_time;
        public String distance;
        public String total_stops;
        public String interchanges;
    }
    public static class Fares { public String token_fare; }
    public static class RouteLeg {
        public String line_info;
        public List<String> stations;
    }
}