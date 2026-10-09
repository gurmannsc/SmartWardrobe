package com.example.smartwardrobe;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONException;

import java.io.IOException;

// City for the weather, and a check that the AI key was built into the app.
public class SettingsActivity extends AppCompatActivity {

    EditText etCity;
    Button btnSaveCity;
    TextView tvSettingsStatus, tvAiStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        etCity = findViewById(R.id.etCity);
        btnSaveCity = findViewById(R.id.btnSaveCity);
        tvSettingsStatus = findViewById(R.id.tvSettingsStatus);
        tvAiStatus = findViewById(R.id.tvAiStatus);

        etCity.setText(WeatherClient.getCity(this));
        tvAiStatus.setText(GroqClient.hasApiKey() ? R.string.ai_status_ok : R.string.ai_status_missing);

        btnSaveCity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String city = etCity.getText().toString().trim();
                if (city.isEmpty()) {
                    Toast.makeText(SettingsActivity.this, R.string.err_city_empty, Toast.LENGTH_SHORT).show();
                    return;
                }
                btnSaveCity.setEnabled(false);
                tvSettingsStatus.setText(R.string.status_finding_city);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String found = null;
                        int errorRes = 0;
                        try {
                            found = WeatherClient.findAndSaveCity(SettingsActivity.this, city);
                            if (found == null) {
                                errorRes = R.string.err_city_not_found;
                            }
                        } catch (IOException | JSONException e) {
                            errorRes = R.string.err_network;
                        }
                        final String name = found;
                        final int error = errorRes;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                btnSaveCity.setEnabled(true);
                                if (name != null) {
                                    Toast.makeText(SettingsActivity.this,
                                            getString(R.string.toast_city_saved, name), Toast.LENGTH_SHORT).show();
                                    finish();
                                } else {
                                    tvSettingsStatus.setText(error);
                                }
                            }
                        });
                    }
                }).start();
            }
        });
    }
}
