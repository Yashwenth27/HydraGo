package com.example.hydrago;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

// Firebase Imports
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends AppCompatActivity {

    // --- UI Components ---
    // Weather
    private TextView tvLocation, tvDate, tvCurrentTemp, tvCondition;
    private ImageView ivCurrentIcon;
    private LinearLayout layoutHourlyContainer, layoutDailyContainer;

    // AQI
    private TextView tvAqiValue, tvAqiStatus;
    private View viewAqiIndicator;

    // SOS
    private GridLayout gridSos;
    private TextView tvTempNumberDisplay;

    // --- Constants ---
    private static final int MAX_CONTACTS = 3;
    private String tempSelectedNumber = "";

    // API URLs
    private static final String WEATHER_URL = "https://api.open-meteo.com/v1/forecast?latitude=17.375&longitude=78.5&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto";
    private static final String AQI_URL = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=17.375&longitude=78.5&current=us_aqi";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // 1. Initialize Weather Views
        tvLocation = findViewById(R.id.tv_location);
        tvDate = findViewById(R.id.tv_date);
        tvCurrentTemp = findViewById(R.id.tv_current_temp);
        tvCondition = findViewById(R.id.tv_condition);
        ivCurrentIcon = findViewById(R.id.iv_current_icon);
        layoutHourlyContainer = findViewById(R.id.layout_hourly_container);
        layoutDailyContainer = findViewById(R.id.layout_daily_container);

        // 2. Initialize AQI Views
        tvAqiValue = findViewById(R.id.tv_aqi_value);
        tvAqiStatus = findViewById(R.id.tv_aqi_status);
        viewAqiIndicator = findViewById(R.id.view_aqi_indicator);

        // 3. Initialize SOS Views
        gridSos = findViewById(R.id.grid_sos);

        // 4. Start Logic
        fetchWeatherData();
        fetchAirQualityData();

        setupSosLogic();
        refreshSosGrid();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

// 1. Highlight Home
        bottomNav.setSelectedItemId(R.id.nav_home);

// 2. Handle Clicks
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_metro) {
                return true; // Already here
            } else if (id == R.id.nav_home) {
                startActivity(new Intent(getApplicationContext(), MetroActivity.class));
                overridePendingTransition(0, 0); // No animation
                return true;
            }
            // Add other cases (Bus, QR) here later...
            return false;
        });
    }

    // ============================================================================================
    // SECTION 1: WEATHER LOGIC
    // ============================================================================================
    private void fetchWeatherData() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                String result = fetchUrl(WEATHER_URL);
                if (result != null) {
                    handler.post(() -> parseWeatherUI(result));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void parseWeatherUI(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);

            // A. Current Weather
            JSONObject current = jsonObject.getJSONObject("current");
            double temp = current.getDouble("temperature_2m");
            int weatherCode = current.getInt("weather_code");

            tvCurrentTemp.setText(String.format(Locale.getDefault(), "%.1f°", temp));
            tvCondition.setText(getConditionString(weatherCode));
            tvDate.setText(new SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(new Date()));
            ivCurrentIcon.setImageResource(getIconResource(weatherCode));
            ivCurrentIcon.setColorFilter(ContextCompat.getColor(this, R.color.auto_yellow));

            // B. Hourly Forecast
            JSONObject hourly = jsonObject.getJSONObject("hourly");
            JSONArray timeArray = hourly.getJSONArray("time");
            JSONArray tempArray = hourly.getJSONArray("temperature_2m");
            JSONArray codeArray = hourly.getJSONArray("weather_code");

            layoutHourlyContainer.removeAllViews();

            for (int i = 0; i < 24 && i < timeArray.length(); i++) {
                String rawTime = timeArray.getString(i);
                String timeLabel = rawTime.substring(rawTime.indexOf("T") + 1);
                double hourlyTemp = tempArray.getDouble(i);
                int hourlyCode = codeArray.getInt(i);
                addHourlyItem(timeLabel, hourlyTemp, hourlyCode);
            }

            // C. Daily Forecast
            JSONObject daily = jsonObject.getJSONObject("daily");
            JSONArray dailyTime = daily.getJSONArray("time");
            JSONArray dailyMax = daily.getJSONArray("temperature_2m_max");
            JSONArray dailyMin = daily.getJSONArray("temperature_2m_min");
            JSONArray dailyCode = daily.getJSONArray("weather_code");

            layoutDailyContainer.removeAllViews();

            for (int i = 0; i < dailyTime.length(); i++) {
                String dateStr = dailyTime.getString(i);
                SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                SimpleDateFormat outFormat = new SimpleDateFormat("EEE", Locale.getDefault());
                String dayLabel = outFormat.format(inFormat.parse(dateStr));

                double max = dailyMax.getDouble(i);
                double min = dailyMin.getDouble(i);
                int dCode = dailyCode.getInt(i);

                addDailyItem(dayLabel, max, min, dCode);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addHourlyItem(String time, double temp, int weatherCode) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.VERTICAL);
        itemLayout.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 50, 0);
        itemLayout.setLayoutParams(params);

        TextView tvTime = new TextView(this);
        tvTime.setText(time);
        tvTime.setTextSize(10);
        tvTime.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        itemLayout.addView(tvTime);

        ImageView ivIcon = new ImageView(this);
        ivIcon.setImageResource(getIconResource(weatherCode));
        ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.auto_yellow));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(60, 60);
        iconParams.setMargins(0, 10, 0, 10);
        ivIcon.setLayoutParams(iconParams);
        itemLayout.addView(ivIcon);

        TextView tvTemp = new TextView(this);
        tvTemp.setText(String.format(Locale.getDefault(), "%.0f°", temp));
        tvTemp.setTextSize(12);
        tvTemp.setTypeface(null, Typeface.BOLD);
        tvTemp.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        itemLayout.addView(tvTemp);

        layoutHourlyContainer.addView(itemLayout);
    }

    private void addDailyItem(String day, double max, double min, int weatherCode) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.VERTICAL);
        itemLayout.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 32);
        itemLayout.setLayoutParams(params);

        TextView tvDay = new TextView(this);
        tvDay.setText(day);
        tvDay.setTextSize(11);
        tvDay.setTextColor(Color.BLACK);
        itemLayout.addView(tvDay);

        ImageView ivIcon = new ImageView(this);
        ivIcon.setImageResource(getIconResource(weatherCode));
        ivIcon.setColorFilter(Color.BLACK);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(60, 60);
        iconParams.setMargins(0, 8, 0, 8);
        ivIcon.setLayoutParams(iconParams);
        itemLayout.addView(ivIcon);

        TextView tvRange = new TextView(this);
        tvRange.setText(String.format(Locale.getDefault(), "%.0f/%.0f", max, min));
        tvRange.setTextSize(10);
        tvRange.setTextColor(Color.BLACK);
        itemLayout.addView(tvRange);

        layoutDailyContainer.addView(itemLayout);
    }

    // ============================================================================================
    // SECTION 2: AQI LOGIC
    // ============================================================================================
    private void fetchAirQualityData() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                String result = fetchUrl(AQI_URL);
                if (result != null) {
                    handler.post(() -> parseAqiUI(result));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void parseAqiUI(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject current = jsonObject.getJSONObject("current");
            int aqi = current.getInt("us_aqi");

            tvAqiValue.setText(String.valueOf(aqi));

            int colorRes;
            String statusText;

            if (aqi <= 50) {
                colorRes = ContextCompat.getColor(this, R.color.metro_blue);
                statusText = "Good";
            } else if (aqi <= 100) {
                colorRes = ContextCompat.getColor(this, R.color.auto_yellow);
                statusText = "Moderate";
            } else {
                colorRes = Color.RED;
                statusText = "Poor";
            }

            tvAqiStatus.setText(statusText);
            tvAqiStatus.setTextColor(colorRes);

            android.graphics.drawable.GradientDrawable background = (android.graphics.drawable.GradientDrawable) viewAqiIndicator.getBackground();
            background.setColor(colorRes);

        } catch (Exception e) {
            e.printStackTrace();
            tvAqiStatus.setText("Error");
        }
    }

    // ============================================================================================
    // SECTION 3: SOS & CONTACTS LOGIC
    // ============================================================================================
    private void setupSosLogic() {
        findViewById(R.id.card_sos_main).setOnClickListener(v -> showGlobalSosDialog());
    }

    private void refreshSosGrid() {
        // Clear all except the first one (Static SOS Card)
        int childCount = gridSos.getChildCount();
        if (childCount > 1) {
            gridSos.removeViews(1, childCount - 1);
        }

        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        int savedCount = prefs.getInt("sos_count", 0);

        for (int i = 0; i < savedCount; i++) {
            String fName = prefs.getString("sos_" + i + "_fname", "");
            String lName = prefs.getString("sos_" + i + "_lname", "");
            String number = prefs.getString("sos_" + i + "_num", "");
            addContactCard(fName, lName, number);
        }

        if (savedCount < MAX_CONTACTS) {
            addPlusCard();
        }
    }

    private void addContactCard(String fName, String lName, String number) {
        CardView card = new CardView(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = (int) (100 * getResources().getDisplayMetrics().density);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(12, 12, 12, 12);
        card.setLayoutParams(params);
        card.setRadius(50);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white));
        card.setCardElevation(6);

        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView tvInitials = new TextView(this);
        String initials = (fName.isEmpty() ? "" : fName.substring(0, 1)) + (lName.isEmpty() ? "" : lName.substring(0, 1));
        tvInitials.setText(initials.toUpperCase());
        tvInitials.setTextSize(18);
        tvInitials.setTypeface(null, Typeface.BOLD);
        tvInitials.setTextColor(Color.WHITE);
        tvInitials.setGravity(Gravity.CENTER);
        tvInitials.setBackgroundResource(R.drawable.shape_circle_blue);
        tvInitials.setLayoutParams(new LinearLayout.LayoutParams(90, 90));

        layout.addView(tvInitials);
        card.addView(layout);
        card.setOnClickListener(v -> makeCall(number));
        gridSos.addView(card);
    }

    private void addPlusCard() {
        CardView card = new CardView(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = (int) (100 * getResources().getDisplayMetrics().density);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(12, 12, 12, 12);
        card.setLayoutParams(params);
        card.setRadius(50);
        card.setCardBackgroundColor(Color.parseColor("#F5F5F5"));
        card.setCardElevation(0);

        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        iv.setImageResource(R.drawable.ic_add_circle);
        iv.setLayoutParams(new LinearLayout.LayoutParams(60, 60));

        layout.addView(iv);
        card.addView(layout);
        card.setOnClickListener(v -> showAddContactDialog());
        gridSos.addView(card);
    }

    // ============================================================================================
    // SECTION 4: DIALOGS & SAVE LOGIC
    // ============================================================================================

    private void showGlobalSosDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_sos_list, null);
        view.findViewById(R.id.btn_call_police).setOnClickListener(v -> makeCall("100"));
        view.findViewById(R.id.btn_call_ambulance).setOnClickListener(v -> makeCall("108"));
        builder.setView(view);
        builder.show();
    }

    private void showAddContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null);
        EditText etFirst = view.findViewById(R.id.et_first_name);
        EditText etLast = view.findViewById(R.id.et_last_name);
        tvTempNumberDisplay = view.findViewById(R.id.tv_selected_number);

        // 1. Pick Contact Button
        view.findViewById(R.id.btn_pick_contact).setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                launchContactPicker();
            } else {
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 101);
            }
        });

        // 2. Save Button
        view.findViewById(R.id.btn_save_contact).setOnClickListener(v -> saveContact(etFirst.getText().toString(), etLast.getText().toString(), tempSelectedNumber));
        builder.setView(view);
        builder.show();
    }

    // Handle Permission Callback
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchContactPicker();
            } else {
                Toast.makeText(this, "Permission Required to pick contacts", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final ActivityResultLauncher<Intent> contactPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri contactUri = result.getData().getData();
                    Cursor cursor = getContentResolver().query(contactUri, null, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        if (numberIndex != -1) {
                            tempSelectedNumber = cursor.getString(numberIndex);
                            // Sanitize Number (Remove spaces/dashes)
                            tempSelectedNumber = tempSelectedNumber.replaceAll("[^0-9+]", "");
                            if (tvTempNumberDisplay != null) tvTempNumberDisplay.setText(tempSelectedNumber);
                        }
                        cursor.close();
                    }
                }
            }
    );

    private void launchContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void saveContact(String fName, String lName, String number) {
        if (number.isEmpty()) {
            Toast.makeText(this, "Select Number", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);

        // A. SAVE LOCALLY (SharedPrefs)
        int currentCount = prefs.getInt("sos_count", 0);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("sos_" + currentCount + "_fname", fName);
        editor.putString("sos_" + currentCount + "_lname", lName);
        editor.putString("sos_" + currentCount + "_num", number);
        editor.putInt("sos_count", currentCount + 1);
        editor.apply();

        // B. SAVE TO FIREBASE
        String userEmail = prefs.getString("user_email", "");
        if (!userEmail.isEmpty()) {
            // Firebase keys cannot have ".", replace with ","
            String sanitizedEmail = userEmail.replace(".", ",");

            try {
                DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("users");
                Map<String, Object> contactData = new HashMap<>();
                contactData.put("firstName", fName);
                contactData.put("lastName", lName);
                contactData.put("phone", number);

                dbRef.child(sanitizedEmail)
                        .child("emergency")
                        .child("contact_" + currentCount)
                        .setValue(contactData);
            } catch (Exception e) {
                e.printStackTrace(); // Handle Firebase not initialized or other errors
            }
        }

        Toast.makeText(this, "Contact Saved", Toast.LENGTH_SHORT).show();
        refreshSosGrid();
    }

    private void makeCall(String number) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + number));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to make call", Toast.LENGTH_SHORT).show();
        }
    }

    // ============================================================================================
    // SECTION 5: HELPERS
    // ============================================================================================
    private String fetchUrl(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("GET");
        int responseCode = urlConnection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return response.toString();
        }
        return null;
    }

    private int getIconResource(int weatherCode) {
        if (weatherCode == 0 || weatherCode == 1) return R.drawable.ic_weather_sun;
        if (weatherCode == 2 || weatherCode == 3) return R.drawable.ic_weather_cloudy;
        if (weatherCode >= 51) return R.drawable.ic_weather_rain;
        return R.drawable.ic_weather_cloudy;
    }

    private String getConditionString(int weatherCode) {
        if (weatherCode == 0) return "Clear Sky";
        if (weatherCode == 1) return "Mainly Clear";
        if (weatherCode == 2) return "Partly Cloudy";
        if (weatherCode == 3) return "Overcast";
        if (weatherCode >= 51) return "Rainy";
        return "Unknown";
    }
}