package com.example.smartwardrobe;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// Pick the occasion (and an optional note) before asking the AI for outfits.
public class OccasionActivity extends AppCompatActivity {

    TextView tvWeatherNote;
    RadioGroup rgOccasion;
    EditText etNote;
    Button btnSuggest;
    WardrobeDbHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_occasion);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvWeatherNote = findViewById(R.id.tvWeatherNote);
        rgOccasion = findViewById(R.id.rgOccasion);
        etNote = findViewById(R.id.etNote);
        btnSuggest = findViewById(R.id.btnSuggest);
        db = new WardrobeDbHelper(this);

        btnSuggest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (rgOccasion.getCheckedRadioButtonId() == -1) {
                    Toast.makeText(OccasionActivity.this, R.string.err_pick_occasion, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (db.countAvailable() < 2) {
                    Toast.makeText(OccasionActivity.this, R.string.err_wardrobe_small, Toast.LENGTH_LONG).show();
                    return;
                }
                RadioButton rx = findViewById(rgOccasion.getCheckedRadioButtonId());
                Intent intent = new Intent(OccasionActivity.this, OutfitsActivity.class);
                intent.putExtra(OutfitsActivity.EXTRA_OCCASION, rx.getText().toString());
                intent.putExtra(OutfitsActivity.EXTRA_NOTE, etNote.getText().toString().trim());
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (WeatherClient.hasCity(this)) {
            tvWeatherNote.setText(getString(R.string.occasion_weather_city, WeatherClient.getCity(this)));
        } else {
            tvWeatherNote.setText(R.string.occasion_weather_none);
        }
    }
}
