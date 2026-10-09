package com.example.smartwardrobe;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

// List of every saved item, with a category filter. Tap an item to edit it.
public class WardrobeActivity extends AppCompatActivity {

    private static final String KEY_ID = "id";
    private static final String KEY_THUMB = "thumb";
    private static final String KEY_TITLE = "title";
    private static final String KEY_SUBTITLE = "subtitle";
    private static final String KEY_STATUS = "status";

    Spinner spFilter;
    TextView tvCount, tvEmpty;
    ListView lvItems;
    WardrobeDbHelper db;
    ArrayList<HashMap<String, Object>> itemList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_wardrobe);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        spFilter = findViewById(R.id.spFilter);
        tvCount = findViewById(R.id.tvCount);
        tvEmpty = findViewById(R.id.tvEmpty);
        lvItems = findViewById(R.id.lvItems);
        db = new WardrobeDbHelper(this);

        // Filter = "All" + every category
        ArrayList<String> filters = new ArrayList<>();
        filters.add(getString(R.string.filter_all));
        filters.addAll(Arrays.asList(getResources().getStringArray(R.array.categories)));
        spFilter.setAdapter(new ArrayAdapter<>(this,
                androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, filters));

        spFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadItems();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        lvItems.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                long itemId = (Long) itemList.get(position).get(KEY_ID);
                Intent intent = new Intent(WardrobeActivity.this, ItemEditorActivity.class);
                intent.putExtra(ItemEditorActivity.EXTRA_ITEM_ID, itemId);
                startActivity(intent);
            }
        });
    }

    // Reload when coming back from the editor, in case something was changed or deleted
    @Override
    protected void onResume() {
        super.onResume();
        loadItems();
    }

    private void loadItems() {
        String category = null;
        if (spFilter.getSelectedItemPosition() > 0) {
            category = spFilter.getSelectedItem().toString();
        }
        ArrayList<ClothingItem> items = db.getItems(category);

        itemList.clear();
        for (ClothingItem it : items) {
            HashMap<String, Object> row = new HashMap<>();
            row.put(KEY_ID, it.id);
            row.put(KEY_THUMB, it.thumbPath);
            row.put(KEY_TITLE, it.getTitle(this));
            row.put(KEY_SUBTITLE, getString(R.string.item_subtitle, it.category, it.pattern, it.formality));
            if (it.inLaundry) {
                row.put(KEY_STATUS, getString(R.string.status_in_laundry));
            } else if (it.timesWorn == 0) {
                row.put(KEY_STATUS, getString(R.string.status_new));
            } else {
                row.put(KEY_STATUS, getString(R.string.status_worn, it.timesWorn));
            }
            itemList.add(row);
        }

        String[] from = {KEY_THUMB, KEY_TITLE, KEY_SUBTITLE, KEY_STATUS};
        int[] to = {R.id.ivThumb, R.id.tvItemTitle, R.id.tvItemSubtitle, R.id.tvItemStatus};
        SimpleAdapter adapter = new SimpleAdapter(this, itemList, R.layout.list_item_wardrobe, from, to);
        lvItems.setAdapter(adapter);

        tvCount.setText(getResources().getQuantityString(R.plurals.wardrobe_count, items.size(), items.size()));
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
