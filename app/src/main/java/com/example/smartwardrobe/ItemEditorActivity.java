package com.example.smartwardrobe;

import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

// Shows an item's details. Used to confirm a new item (pre-filled by the AI) and to edit a saved one.
public class ItemEditorActivity extends AppCompatActivity {

    public static final String EXTRA_ITEM_ID = "item_id";
    public static final String EXTRA_IMAGE_PATH = "image_path";
    public static final String EXTRA_THUMB_PATH = "thumb_path";
    public static final String EXTRA_FROM_AI = "from_ai";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_COLOR = "color";
    public static final String EXTRA_COLOR2 = "color2";
    public static final String EXTRA_PATTERN = "pattern";
    public static final String EXTRA_FORMALITY = "formality";
    public static final String EXTRA_SEASONS = "seasons";

    private static final String SEASON_SEPARATOR = ",";

    // Type list for each category, in the same order as R.array.categories
    public static final int[] TYPE_ARRAYS = {
            R.array.types_top,
            R.array.types_bottom,
            R.array.types_one_piece,
            R.array.types_layer,
            R.array.types_footwear,
            R.array.types_accessory
    };

    TextView tvEditorTitle, tvAiNote, tvWornStats;
    ImageView ivItem;
    Spinner spCategory, spType, spColor, spColor2, spPattern;
    RadioGroup rgFormality;
    CheckBox cbSummer, cbMonsoon, cbWinter, cbLaundry;
    Button btnSave, btnDelete;

    WardrobeDbHelper db;
    ClothingItem item;
    boolean editMode;
    String pendingType;   // type to select once the type list for the category is loaded

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_item_editor);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvEditorTitle = findViewById(R.id.tvEditorTitle);
        tvAiNote = findViewById(R.id.tvAiNote);
        tvWornStats = findViewById(R.id.tvWornStats);
        ivItem = findViewById(R.id.ivItem);
        spCategory = findViewById(R.id.spCategory);
        spType = findViewById(R.id.spType);
        spColor = findViewById(R.id.spColor);
        spColor2 = findViewById(R.id.spColor2);
        spPattern = findViewById(R.id.spPattern);
        rgFormality = findViewById(R.id.rgFormality);
        cbSummer = findViewById(R.id.cbSummer);
        cbMonsoon = findViewById(R.id.cbMonsoon);
        cbWinter = findViewById(R.id.cbWinter);
        cbLaundry = findViewById(R.id.cbLaundry);
        btnSave = findViewById(R.id.btnSave);
        btnDelete = findViewById(R.id.btnDelete);
        db = new WardrobeDbHelper(this);

        spCategory.setAdapter(ArrayAdapter.createFromResource(this, R.array.categories,
                androidx.appcompat.R.layout.support_simple_spinner_dropdown_item));
        spColor.setAdapter(ArrayAdapter.createFromResource(this, R.array.colors,
                androidx.appcompat.R.layout.support_simple_spinner_dropdown_item));
        spPattern.setAdapter(ArrayAdapter.createFromResource(this, R.array.patterns,
                androidx.appcompat.R.layout.support_simple_spinner_dropdown_item));

        // Second colour = "None" + all colours
        ArrayList<String> secondColors = new ArrayList<>();
        secondColors.add(getString(R.string.color_none));
        secondColors.addAll(Arrays.asList(getResources().getStringArray(R.array.colors)));
        spColor2.setAdapter(new ArrayAdapter<>(this,
                androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, secondColors));

        // Changing the category changes which types can be picked
        spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                spType.setAdapter(ArrayAdapter.createFromResource(ItemEditorActivity.this, TYPE_ARRAYS[position],
                        androidx.appcompat.R.layout.support_simple_spinner_dropdown_item));
                if (pendingType != null) {
                    selectValue(spType, pendingType);
                    pendingType = null;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        long itemId = getIntent().getLongExtra(EXTRA_ITEM_ID, -1);
        if (itemId != -1) {
            editMode = true;
            item = db.getItem(itemId);
            if (item == null) {
                finish();
                return;
            }
        } else {
            item = new ClothingItem();
            item.imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
            item.thumbPath = getIntent().getStringExtra(EXTRA_THUMB_PATH);
            item.category = getIntent().getStringExtra(EXTRA_CATEGORY);
            item.type = getIntent().getStringExtra(EXTRA_TYPE);
            item.color = getIntent().getStringExtra(EXTRA_COLOR);
            item.secondaryColor = getIntent().getStringExtra(EXTRA_COLOR2);
            item.pattern = getIntent().getStringExtra(EXTRA_PATTERN);
            item.formality = getIntent().getStringExtra(EXTRA_FORMALITY);
            ArrayList<String> seasons = getIntent().getStringArrayListExtra(EXTRA_SEASONS);
            if (seasons != null) {
                item.seasons = TextUtils.join(SEASON_SEPARATOR, seasons);
            }
        }
        fillForm();

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });

        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete();
            }
        });
    }

    private void fillForm() {
        tvEditorTitle.setText(editMode ? R.string.editor_title_edit : R.string.editor_title_new);
        if (getIntent().getBooleanExtra(EXTRA_FROM_AI, false)) {
            tvAiNote.setVisibility(View.VISIBLE);
        }
        if (item.imagePath != null) {
            ivItem.setImageURI(Uri.fromFile(new File(item.imagePath)));
        }

        pendingType = item.type;
        selectValue(spCategory, item.category);
        selectValue(spColor, item.color);
        selectValue(spColor2, item.secondaryColor);
        selectValue(spPattern, item.pattern);

        // Tick the formality radio button whose text matches
        for (int i = 0; i < rgFormality.getChildCount(); i++) {
            RadioButton rb = (RadioButton) rgFormality.getChildAt(i);
            if (rb.getText().toString().equalsIgnoreCase(item.formality)) {
                rb.setChecked(true);
            }
        }

        if (item.seasons != null) {
            String seasons = item.seasons.toLowerCase();
            cbSummer.setChecked(seasons.contains(getString(R.string.season_summer).toLowerCase()));
            cbMonsoon.setChecked(seasons.contains(getString(R.string.season_monsoon).toLowerCase()));
            cbWinter.setChecked(seasons.contains(getString(R.string.season_winter).toLowerCase()));
        }

        if (editMode) {
            cbLaundry.setVisibility(View.VISIBLE);
            cbLaundry.setChecked(item.inLaundry);
            tvWornStats.setVisibility(View.VISIBLE);
            if (item.timesWorn == 0) {
                tvWornStats.setText(R.string.worn_never);
            } else {
                tvWornStats.setText(getString(R.string.worn_stats, item.timesWorn, item.lastWorn));
            }
            btnDelete.setVisibility(View.VISIBLE);
        }
    }

    private void save() {
        if (rgFormality.getCheckedRadioButtonId() == -1) {
            Toast.makeText(this, R.string.err_pick_formality, Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> seasons = new ArrayList<>();
        if (cbSummer.isChecked()) {
            seasons.add(cbSummer.getText().toString());
        }
        if (cbMonsoon.isChecked()) {
            seasons.add(cbMonsoon.getText().toString());
        }
        if (cbWinter.isChecked()) {
            seasons.add(cbWinter.getText().toString());
        }
        if (seasons.isEmpty()) {
            Toast.makeText(this, R.string.err_pick_season, Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton rx = findViewById(rgFormality.getCheckedRadioButtonId());
        item.category = spCategory.getSelectedItem().toString();
        item.type = spType.getSelectedItem().toString();
        item.color = spColor.getSelectedItem().toString();
        item.secondaryColor = spColor2.getSelectedItem().toString();
        item.pattern = spPattern.getSelectedItem().toString();
        item.formality = rx.getText().toString();
        item.seasons = TextUtils.join(SEASON_SEPARATOR, seasons);

        if (editMode) {
            item.inLaundry = cbLaundry.isChecked();
            db.updateItem(item);
            Toast.makeText(this, R.string.toast_updated, Toast.LENGTH_SHORT).show();
        } else {
            db.insertItem(item);
        }
        setResult(RESULT_OK);
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_msg)
                .setPositiveButton(R.string.btn_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        db.deleteItem(item);
                        Toast.makeText(ItemEditorActivity.this, R.string.toast_deleted, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    // Selects the spinner entry whose text matches value (ignoring case). Leaves it unchanged if none match.
    private void selectValue(Spinner spinner, String value) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < spinner.getAdapter().getCount(); i++) {
            if (spinner.getAdapter().getItem(i).toString().equalsIgnoreCase(value.trim())) {
                spinner.setSelection(i);
                return;
            }
        }
    }
}
