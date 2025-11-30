package com.example.hydrago;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hydrago.ApiResponse;
import com.example.hydrago.Station;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class MetroActivity extends AppCompatActivity {

    // 1. API Interface Definition
    interface HydraApiService {
        @GET("scrape-route")
        Call<ApiResponse> getRoute(@Query("url") String targetUrl);
    }

    private AutoCompleteTextView inputSource, inputDest;
    private MaterialButton btnSearch;
    private ProgressBar progressBar;
    private View resultsLayout;
    private TextView txtTime, txtFare, txtChange;
    private RecyclerView recyclerRoute;
    private RouteAdapter routeAdapter;

    private List<Station> stationList = new ArrayList<>();
    private Station selectedSource, selectedDest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metro);

        // Force Light Mode for this screen to ensure colors match exactly what you defined
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_metro);

        // Init Views
        ExtendedFloatingActionButton fabMap = findViewById(R.id.fabMap);
        ExtendedFloatingActionButton fabTicket = findViewById(R.id.fabTicket);

        // 1. Handle Map Click
        fabMap.setOnClickListener(v -> showMapDialog());

        // 2. Handle Ticket Click (WhatsApp Booking)
        fabTicket.setOnClickListener(v -> openBookingSystem());
        inputSource = findViewById(R.id.inputSource);
        inputDest = findViewById(R.id.inputDest);
        btnSearch = findViewById(R.id.btnSearch);
        progressBar = findViewById(R.id.progressBar);
        resultsLayout = findViewById(R.id.resultsLayout);
        txtTime = findViewById(R.id.txtTime);
        txtFare = findViewById(R.id.txtFare);
        txtChange = findViewById(R.id.txtChange);
        recyclerRoute = findViewById(R.id.recyclerRoute);

        // Setup RecyclerView
        routeAdapter = new RouteAdapter();
        recyclerRoute.setLayoutManager(new LinearLayoutManager(this));
        recyclerRoute.setAdapter(routeAdapter);

        // Load CSV Data
        loadStationsFromCSV();

        // Setup Dropdowns
        ArrayAdapter<Station> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, stationList);
        inputSource.setAdapter(adapter);
        inputDest.setAdapter(adapter);

        // Handle Selection
        inputSource.setOnItemClickListener((parent, view, position, id) -> selectedSource = (Station) parent.getItemAtPosition(position));
        inputDest.setOnItemClickListener((parent, view, position, id) -> selectedDest = (Station) parent.getItemAtPosition(position));

        // Search Button Logic
        btnSearch.setOnClickListener(v -> {
            if (selectedSource == null || selectedDest == null) {
                Toast.makeText(this, "Please select valid stations", Toast.LENGTH_SHORT).show();
                return;
            }
            fetchRoute(selectedSource, selectedDest);
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

// 1. Highlight Home
        bottomNav.setSelectedItemId(R.id.nav_metro);

// 2. Handle Clicks
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                return true; // Already here
            } else if (id == R.id.nav_metro) {
                startActivity(new Intent(getApplicationContext(), MetroActivity.class));
                overridePendingTransition(0, 0); // No animation
                return true;
            }
            // Add other cases (Bus, QR) here later...
            return false;
        });
    }

    private void loadStationsFromCSV() {
        try {
            InputStream is = getResources().openRawResource(R.raw.stations);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            reader.readLine(); // Skip header
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length >= 3) {
                    // CSV Format: Name, Slug, ID
                    stationList.add(new Station(tokens[0], tokens[1], tokens[2]));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading stations", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchRoute(Station source, Station dest) {
        // UI Loading State
        progressBar.setVisibility(View.VISIBLE);
        resultsLayout.setVisibility(View.GONE);
        btnSearch.setEnabled(false);

        // Construct Yometro URL dynamically
        String dynamicUrl = "https://yometro.com/from-" + source.slug + "-hyderabad-to-" + dest.slug + "-hyderabad";

        // Retrofit Setup
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://hydrago.onrender.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        HydraApiService api = retrofit.create(HydraApiService.class);

        // API Call
        api.getRoute(dynamicUrl).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                progressBar.setVisibility(View.GONE);
                btnSearch.setEnabled(true);

                if (response.isSuccessful() && response.body() != null) {
                    updateUI(response.body());
                } else {
                    Toast.makeText(MetroActivity.this, "Failed to get route", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                btnSearch.setEnabled(true);
                Toast.makeText(MetroActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUI(ApiResponse data) {
        resultsLayout.setVisibility(View.VISIBLE);

        // Clean up text (remove emojis if API sends them)
        String time = data.journeyStats.travel_time.replaceAll("[^0-9:]", "").trim();
        String fare = data.fares.token_fare;
        String change = data.journeyStats.interchanges.replaceAll("[^0-9]", "").trim();

        txtTime.setText(time + " min");
        txtFare.setText(fare);
        txtChange.setText(change);

        // Update Timeline
        routeAdapter.setData(data.routeSequence, this);
    }

    private void showMapDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Use WebView because it supports built-in Pinch-to-Zoom for images
        WebView webView = new WebView(this);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false); // Hide the ugly +/- buttons
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);

        // Load the official Map Image
        // We use a Google Docs viewer or direct load. Direct load works best for images.
        webView.loadUrl("https://yometro.com/images/maps/hyderabad-metro-route-map.jpg");

        dialog.setContentView(webView);
        dialog.show();
    }

    private void openBookingSystem() {
        // Official L&T Metro Hyderabad WhatsApp Ticketing Number
        String phoneNumber = "918341146468";
        String message = "Hi"; // Trigger word to skip the "Hi" menu

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://wa.me/" + phoneNumber + "?text=" + message));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
        }
    }
}