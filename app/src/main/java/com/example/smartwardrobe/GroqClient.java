package com.example.smartwardrobe;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

// Talks to the Groq AI (OpenAI-compatible chat API). Every method blocks, so call it from a background Thread.
public class GroqClient {

    private static final String TAG = "GroqClient";
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 60000;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1500;

    private final Context context;

    public GroqClient(Context context) {
        this.context = context.getApplicationContext();
    }

    // Thrown with a message that is ready to show to the user
    public static class AiException extends Exception {
        public AiException(String message) {
            super(message);
        }
    }

    // The AI server is temporarily busy (HTTP 5xx), so the request is worth retrying
    private static class BusyException extends AiException {
        BusyException(String message) {
            super(message);
        }
    }

    // Sends a clothing photo and gets back its category, type, colours, pattern, formality and seasons.
    public JSONObject identifyItem(File imageFile) throws AiException {
        String prompt = context.getString(R.string.prompt_identify,
                joinArray(R.array.categories),
                typesByCategory(),
                joinArray(R.array.colors),
                joinArray(R.array.patterns),
                joinArray(R.array.formality_levels),
                joinArray(R.array.seasons),
                context.getString(R.string.color_none));

        String base64;
        try {
            base64 = ImageUtils.toBase64(imageFile);
        } catch (IOException e) {
            throw new AiException(context.getString(R.string.err_image_load));
        }

        try {
            JSONArray content = new JSONArray();
            content.put(new JSONObject().put("type", "text").put("text", prompt));
            content.put(new JSONObject().put("type", "image_url").put("image_url",
                    new JSONObject().put("url", "data:image/jpeg;base64," + base64)));

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "user").put("content", content));
            return send(messages, 0.2, 600);
        } catch (JSONException e) {
            throw new AiException(context.getString(R.string.err_ai_bad_reply));
        }
    }

    // Asks the AI stylist for up to 3 outfits built from the given wardrobe text.
    public JSONObject suggestOutfits(String occasion, String note, String weather,
                                     String wardrobeText, String avoidText) throws AiException {
        String userPrompt = context.getString(R.string.prompt_stylist_user,
                occasion, note, weather, wardrobeText, avoidText);
        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system")
                    .put("content", context.getString(R.string.prompt_stylist_system)));
            messages.put(new JSONObject().put("role", "user").put("content", userPrompt));
            return send(messages, 0.8, 1500);
        } catch (JSONException e) {
            throw new AiException(context.getString(R.string.err_ai_bad_reply));
        }
    }

    public static boolean hasApiKey() {
        return !BuildConfig.GROQ_API_KEY.isEmpty();
    }

    private JSONObject send(JSONArray messages, double temperature, int maxTokens) throws AiException {
        if (!hasApiKey()) {
            throw new AiException(context.getString(R.string.err_no_api_key));
        }

        // The free model is sometimes "over capacity" for a few seconds, so retry those errors
        BusyException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return sendOnce(messages, temperature, maxTokens);
            } catch (BusyException e) {
                lastError = e;
                Log.w(TAG, "Groq busy, attempt " + attempt + " of " + MAX_ATTEMPTS);
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        }
        throw lastError;
    }

    private JSONObject sendOnce(JSONArray messages, double temperature, int maxTokens) throws AiException {
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject();
            body.put("model", context.getString(R.string.groq_model));
            body.put("messages", messages);
            body.put("temperature", temperature);
            body.put("max_completion_tokens", maxTokens);
            body.put("response_format", new JSONObject().put("type", "json_object"));
            // Answer directly, without spending tokens on a hidden "thinking" step
            body.put("reasoning_effort", "none");
            body.put("reasoning_format", "hidden");

            conn = (HttpURLConnection) new URL(context.getString(R.string.groq_url)).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.GROQ_API_KEY);

            OutputStream out = conn.getOutputStream();
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            out.close();

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw new AiException(context.getString(R.string.err_ai_key_invalid));
            }
            if (code == 429) {
                throw new AiException(context.getString(R.string.err_rate_limit));
            }
            if (code >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
                Log.w(TAG, "Groq error " + code + ": " + readAll(conn.getErrorStream()));
                throw new BusyException(context.getString(R.string.err_ai_busy));
            }
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Groq error " + code + ": " + readAll(conn.getErrorStream()));
                throw new AiException(context.getString(R.string.err_ai_generic, code));
            }

            JSONObject response = new JSONObject(readAll(conn.getInputStream()));
            String content = response.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content");

            // JSON mode should return pure JSON, but cut out the {...} part just in case
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start < 0 || end < start) {
                throw new AiException(context.getString(R.string.err_ai_bad_reply));
            }
            return new JSONObject(content.substring(start, end + 1));
        } catch (IOException e) {
            throw new AiException(context.getString(R.string.err_network));
        } catch (JSONException e) {
            throw new AiException(context.getString(R.string.err_ai_bad_reply));
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // "Top, Bottom, One-piece, ..."
    private String joinArray(int arrayRes) {
        return TextUtils.join(", ", context.getResources().getStringArray(arrayRes));
    }

    // "Top: T-shirt, Shirt, ...; Bottom: Jeans, ...; ..."
    private String typesByCategory() {
        String[] categories = context.getResources().getStringArray(R.array.categories);
        String[] parts = new String[categories.length];
        for (int i = 0; i < categories.length; i++) {
            parts[i] = categories[i] + ": " + joinArray(ItemEditorActivity.TYPE_ARRAYS[i]);
        }
        return TextUtils.join("; ", parts);
    }

    static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder text = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            text.append(line).append('\n');
        }
        reader.close();
        return text.toString();
    }
}
