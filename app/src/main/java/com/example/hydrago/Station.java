package com.example.hydrago;

public class Station {
    public String name;
    public String slug;
    public String id;

    public Station(String name, String slug, String id) {
        this.name = name;
        this.slug = slug;
        this.id = id;
    }

    @Override
    public String toString() { return name; } // Important for AutoCompleteTextView
}