package com.example.smartwardrobe;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// Home screen: today's weather, wardrobe stats and the main buttons.
public class MainActivity extends AppCompatActivity {

    TextView tvWeatherMain, tvWeatherRange, tvStats;
    LinearLayout weatherCard;
    Button btnDressMe, btnAddItem, btnWardrobe, btnSettings;
    WardrobeDbHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvWeatherMain = findViewById(R.id.tvWeatherMain);
        tvWeatherRange = findViewById(R.id.tvWeatherRange);
        tvStats = findViewById(R.id.tvStats);
        weatherCard = findViewById(R.id.weatherCard);
        btnDressMe = findViewById(R.id.btnDressMe);
        btnAddItem = findViewById(R.id.btnAddItem);
        btnWardrobe = findViewById(R.id.btnWardrobe);
        btnSettings = findViewById(R.id.btnSettings);
        db = new WardrobeDbHelper(this);

        btnDressMe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, OccasionActivity.class);
                startActivity(intent);
            }
        });

        btnAddItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, AddItemActivity.class);
                startActivity(intent);
            }
        });

        btnWardrobe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, WardrobeActivity.class);
                startActivity(intent);
            }
        });

        View.OnClickListener openSettings = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        };
        btnSettings.setOnClickListener(openSettings);
        weatherCard.setOnClickListener(openSettings);
    }

    // Runs every time we come back to this screen, so the numbers stay up to date
    @Override
    protected void onResume() {
        super.onResume();
        tvStats.setText(getString(R.string.stats_summary, db.countItems(), db.countInLaundry()));
        loadWeather();
    }

    private void loadWeather() {
        if (!WeatherClient.hasCity(this)) {
            tvWeatherMain.setText(R.string.weather_set_city);
            tvWeatherRange.setVisibility(View.GONE);
            return;
        }
        tvWeatherMain.setText(R.string.weather_loading);

        // Network calls are not allowed on the main thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                final WeatherClient.WeatherInfo w = WeatherClient.fetchToday(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (w == null) {
                            tvWeatherMain.setText(R.string.weather_unavailable);
                            tvWeatherRange.setVisibility(View.GONE);
                        } else {
                            tvWeatherMain.setText(getString(R.string.weather_summary,
                                    WeatherClient.getCity(MainActivity.this), w.temperature, w.description));
                            tvWeatherRange.setText(getString(R.string.weather_range,
                                    w.maxTemp, w.minTemp, w.rainChance));
                            tvWeatherRange.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        }).start();
    }
}
