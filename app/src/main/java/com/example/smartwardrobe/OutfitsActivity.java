package com.example.smartwardrobe;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

// Asks the AI stylist for outfits and shows them. Tap an outfit to log that you wore it.
public class OutfitsActivity extends AppCompatActivity {

    public static final String EXTRA_OCCASION = "occasion";
    public static final String EXTRA_NOTE = "note";

    private static final int MAX_ITEMS_PER_OUTFIT = 5;
    private static final String KEY_NAME = "name";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_REASON = "reason";
    private static final String[] IMAGE_KEYS = {"img1", "img2", "img3", "img4", "img5"};
    private static final int[] IMAGE_VIEWS = {R.id.ivOutfit1, R.id.ivOutfit2, R.id.ivOutfit3, R.id.ivOutfit4, R.id.ivOutfit5};

    TextView tvOutfitsTitle, tvOutfitStatus, tvTip;
    ProgressBar pbOutfits;
    ListView lvOutfits;
    Button btnTryAgain;

    WardrobeDbHelper db;
    String occasion, note;
    ArrayList<HashMap<String, Object>> outfitList = new ArrayList<>();
    ArrayList<long[]> outfitItemIds = new ArrayList<>();       // item ids of each outfit on screen
    ArrayList<String> alreadyShown = new ArrayList<>();         // so "try again" gives new combinations

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_outfits);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvOutfitsTitle = findViewById(R.id.tvOutfitsTitle);
        tvOutfitStatus = findViewById(R.id.tvOutfitStatus);
        tvTip = findViewById(R.id.tvTip);
        pbOutfits = findViewById(R.id.pbOutfits);
        lvOutfits = findViewById(R.id.lvOutfits);
        btnTryAgain = findViewById(R.id.btnTryAgain);
        db = new WardrobeDbHelper(this);

        occasion = getIntent().getStringExtra(EXTRA_OCCASION);
        note = getIntent().getStringExtra(EXTRA_NOTE);
        if (note == null || note.isEmpty()) {
            note = getString(R.string.prompt_no_notes);
        }
        tvOutfitsTitle.setText(getString(R.string.outfits_title, occasion));

        btnTryAgain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                suggest();
            }
        });

        lvOutfits.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                confirmWear(position);
            }
        });

        suggest();
    }

    private void suggest() {
        setBusy(true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<ClothingItem> items = db.getAvailableItems();
                String weather = WeatherClient.forAi(OutfitsActivity.this, WeatherClient.fetchToday(OutfitsActivity.this));
                try {
                    JSONObject result = new GroqClient(OutfitsActivity.this).suggestOutfits(
                            occasion, note, weather, wardrobeText(items), avoidText());
                    showOutfits(result, items);
                } catch (final GroqClient.AiException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setBusy(false);
                            tvOutfitStatus.setText(e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    // Turns the AI's JSON into list rows. Only ids that really exist in the wardrobe are used.
    private void showOutfits(JSONObject result, ArrayList<ClothingItem> items) {
        HashMap<Long, ClothingItem> byId = new HashMap<>();
        for (ClothingItem it : items) {
            byId.put(it.id, it);
        }

        final ArrayList<HashMap<String, Object>> rows = new ArrayList<>();
        final ArrayList<long[]> ids = new ArrayList<>();
        JSONArray outfits = result.optJSONArray("outfits");
        if (outfits != null) {
            for (int i = 0; i < outfits.length(); i++) {
                JSONObject outfit = outfits.optJSONObject(i);
                if (outfit == null) {
                    continue;
                }
                ArrayList<ClothingItem> chosen = new ArrayList<>();
                JSONArray itemIds = outfit.optJSONArray("item_ids");
                if (itemIds != null) {
                    for (int j = 0; j < itemIds.length() && chosen.size() < MAX_ITEMS_PER_OUTFIT; j++) {
                        ClothingItem it = byId.get(itemIds.optLong(j, -1));
                        if (it != null && !chosen.contains(it)) {
                            chosen.add(it);
                        }
                    }
                }
                if (chosen.isEmpty()) {
                    continue;
                }

                HashMap<String, Object> row = new HashMap<>();
                row.put(KEY_NAME, outfit.optString("name", getString(R.string.outfit_default_name, rows.size() + 1)));
                row.put(KEY_REASON, outfit.optString("reason"));
                String[] titles = new String[chosen.size()];
                long[] chosenIds = new long[chosen.size()];
                for (int k = 0; k < chosen.size(); k++) {
                    titles[k] = chosen.get(k).getTitle(this);
                    chosenIds[k] = chosen.get(k).id;
                }
                row.put(KEY_ITEMS, TextUtils.join(getString(R.string.item_separator), titles));
                // SimpleAdapter shows a file path as an image; 0 leaves the slot empty
                for (int k = 0; k < MAX_ITEMS_PER_OUTFIT; k++) {
                    if (k < chosen.size()) {
                        row.put(IMAGE_KEYS[k], chosen.get(k).thumbPath);
                    } else {
                        row.put(IMAGE_KEYS[k], 0);
                    }
                }
                rows.add(row);
                ids.add(chosenIds);
                alreadyShown.add(idsText(chosenIds));
            }
        }
        final String tip = result.optString("missing", "").trim();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setBusy(false);
                outfitList.clear();
                outfitList.addAll(rows);
                outfitItemIds.clear();
                outfitItemIds.addAll(ids);

                String[] from = {KEY_NAME, KEY_ITEMS, KEY_REASON, IMAGE_KEYS[0], IMAGE_KEYS[1], IMAGE_KEYS[2], IMAGE_KEYS[3], IMAGE_KEYS[4]};
                int[] to = {R.id.tvOutfitName, R.id.tvOutfitItems, R.id.tvOutfitReason,
                        IMAGE_VIEWS[0], IMAGE_VIEWS[1], IMAGE_VIEWS[2], IMAGE_VIEWS[3], IMAGE_VIEWS[4]};
                SimpleAdapter adapter = new SimpleAdapter(OutfitsActivity.this, outfitList, R.layout.list_item_outfit, from, to);
                lvOutfits.setAdapter(adapter);

                tvOutfitStatus.setText(rows.isEmpty() ? getString(R.string.outfits_none) : "");
                if (tip.isEmpty()) {
                    tvTip.setVisibility(View.GONE);
                } else {
                    tvTip.setText(getString(R.string.outfit_tip, tip));
                    tvTip.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void confirmWear(final int position) {
        String name = outfitList.get(position).get(KEY_NAME).toString();
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.wear_confirm_title, name))
                .setMessage(R.string.wear_confirm_msg)
                .setPositiveButton(R.string.btn_wear, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        db.markWorn(outfitItemIds.get(position));
                        Toast.makeText(OutfitsActivity.this, R.string.toast_worn, Toast.LENGTH_SHORT).show();
                        // Back to the home screen, closing the occasion and outfit screens
                        Intent intent = new Intent(OutfitsActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    // One line per item for the AI prompt
    private String wardrobeText(ArrayList<ClothingItem> items) {
        String[] lines = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            ClothingItem it = items.get(i);
            lines[i] = getString(R.string.prompt_wardrobe_line,
                    it.id, it.category, it.type, it.color, it.secondaryColor, it.pattern,
                    it.formality, it.seasons, it.timesWorn,
                    it.lastWorn == null ? getString(R.string.never) : it.lastWorn);
        }
        return TextUtils.join("\n", lines);
    }

    private String avoidText() {
        if (alreadyShown.isEmpty()) {
            return "";
        }
        return getString(R.string.prompt_avoid, TextUtils.join("; ", alreadyShown));
    }

    private String idsText(long[] ids) {
        String[] parts = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            parts[i] = String.valueOf(ids[i]);
        }
        return "[" + TextUtils.join(", ", parts) + "]";
    }

    private void setBusy(boolean busy) {
        pbOutfits.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnTryAgain.setEnabled(!busy);
        if (busy) {
            tvOutfitStatus.setText(R.string.status_styling);
            tvTip.setVisibility(View.GONE);
            outfitList.clear();
            lvOutfits.setAdapter(null);
        }
    }
}
