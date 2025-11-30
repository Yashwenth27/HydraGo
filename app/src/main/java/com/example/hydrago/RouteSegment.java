package com.example.hydrago;

import java.util.List;

public class RouteSegment {
    private String lineName; // e.g. "Red Line"
    private String lineColor; // e.g. "#E31E24"
    private String startStation;
    private String endStation;
    private List<String> intermediateStops;

    public RouteSegment(String lineName, String lineColor, String startStation, String endStation, List<String> intermediateStops) {
        this.lineName = lineName;
        this.lineColor = lineColor;
        this.startStation = startStation;
        this.endStation = endStation;
        this.intermediateStops = intermediateStops;
    }

    public String getLineName() { return lineName; }
    public String getLineColor() { return lineColor; }
    public String getStartStation() { return startStation; }
    public String getEndStation() { return endStation; }
    public List<String> getIntermediateStops() { return intermediateStops; }
}