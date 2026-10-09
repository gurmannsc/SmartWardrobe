package com.example.smartwardrobe;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

// Today's weather from Open-Meteo (free, no API key). Network methods block: call them from a background Thread.
public class WeatherClient {

    private static final String PREFS = "settings";
    private static final String KEY_CITY = "city";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static final int TIMEOUT_MS = 15000;

    public static class WeatherInfo {
        public double temperature;
        public double maxTemp;
        public double minTemp;
        public int rainChance;
        public String description;
    }

    public static boolean hasCity(Context context) {
        return prefs(context).contains(KEY_LAT);
    }

    public static String getCity(Context context) {
        return prefs(context).getString(KEY_CITY, "");
    }

    // Looks the city up and saves its coordinates. Returns the matched city name, or null if not found.
    public static String findAndSaveCity(Context context, String query) throws IOException, JSONException {
        String url = context.getString(R.string.geocode_url, URLEncoder.encode(query, "UTF-8"));
        JSONObject json = new JSONObject(httpGet(url));
        JSONArray results = json.optJSONArray("results");
        if (results == null || results.length() == 0) {
            return null;
        }
        JSONObject place = results.getJSONObject(0);
        String name = place.getString("name");
        prefs(context).edit()
                .putString(KEY_CITY, name)
                .putString(KEY_LAT, String.valueOf(place.getDouble("latitude")))
                .putString(KEY_LON, String.valueOf(place.getDouble("longitude")))
                .apply();
        return name;
    }

    // Returns null if no city is set or the request fails
    public static WeatherInfo fetchToday(Context context) {
        if (!hasCity(context)) {
            return null;
        }
        try {
            String url = context.getString(R.string.forecast_url,
                    prefs(context).getString(KEY_LAT, ""), prefs(context).getString(KEY_LON, ""));
            JSONObject json = new JSONObject(httpGet(url));
            JSONObject current = json.getJSONObject("current");
            JSONObject daily = json.getJSONObject("daily");

            WeatherInfo w = new WeatherInfo();
            w.temperature = current.getDouble("temperature_2m");
            w.maxTemp = daily.getJSONArray("temperature_2m_max").getDouble(0);
            w.minTemp = daily.getJSONArray("temperature_2m_min").getDouble(0);
            w.rainChance = daily.getJSONArray("precipitation_probability_max").optInt(0, 0);
            w.description = describe(context, current.getInt("weather_code"));
            return w;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    // One line the AI stylist can use
    public static String forAi(Context context, WeatherInfo w) {
        if (w == null) {
            return context.getString(R.string.weather_for_ai_none);
        }
        return context.getString(R.string.weather_for_ai,
                w.temperature, w.maxTemp, w.minTemp, w.description, w.rainChance);
    }

    // Open-Meteo uses WMO weather codes
    private static String describe(Context context, int code) {
        int res;
        if (code == 0) {
            res = R.string.weather_clear;
        } else if (code <= 2) {
            res = R.string.weather_partly_cloudy;
        } else if (code == 3) {
            res = R.string.weather_cloudy;
        } else if (code <= 48) {
            res = R.string.weather_fog;
        } else if (code <= 57) {
            res = R.string.weather_drizzle;
        } else if (code <= 67) {
            res = R.string.weather_rain;
        } else if (code <= 77) {
            res = R.string.weather_snow;
        } else if (code <= 82) {
            res = R.string.weather_rain;
        } else if (code <= 86) {
            res = R.string.weather_snow;
        } else {
            res = R.string.weather_thunder;
        }
        return context.getString(res);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        try {
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + conn.getResponseCode());
            }
            return GroqClient.readAll(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }
}
