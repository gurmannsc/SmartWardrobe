package com.example.smartwardrobe;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

// SQLite database that stores the wardrobe on the phone.
public class WardrobeDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "wardrobe.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE_ITEMS = "items";
    private static final String COL_ID = "_id";
    private static final String COL_IMAGE = "image_path";
    private static final String COL_THUMB = "thumb_path";
    private static final String COL_CATEGORY = "category";
    private static final String COL_TYPE = "sub_type";
    private static final String COL_COLOR = "color";
    private static final String COL_COLOR2 = "secondary_color";
    private static final String COL_PATTERN = "pattern";
    private static final String COL_FORMALITY = "formality";
    private static final String COL_SEASONS = "seasons";
    private static final String COL_LAUNDRY = "in_laundry";
    private static final String COL_TIMES_WORN = "times_worn";
    private static final String COL_LAST_WORN = "last_worn";
    private static final String COL_CREATED = "created_at";

    private static final String DATE_PATTERN = "yyyy-MM-dd";

    public WardrobeDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_ITEMS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_IMAGE + " TEXT, "
                + COL_THUMB + " TEXT, "
                + COL_CATEGORY + " TEXT, "
                + COL_TYPE + " TEXT, "
                + COL_COLOR + " TEXT, "
                + COL_COLOR2 + " TEXT, "
                + COL_PATTERN + " TEXT, "
                + COL_FORMALITY + " TEXT, "
                + COL_SEASONS + " TEXT, "
                + COL_LAUNDRY + " INTEGER DEFAULT 0, "
                + COL_TIMES_WORN + " INTEGER DEFAULT 0, "
                + COL_LAST_WORN + " TEXT, "
                + COL_CREATED + " INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ITEMS);
        onCreate(db);
    }

    public long insertItem(ClothingItem item) {
        ContentValues values = toValues(item);
        values.put(COL_CREATED, System.currentTimeMillis());
        item.id = getWritableDatabase().insert(TABLE_ITEMS, null, values);
        return item.id;
    }

    public void updateItem(ClothingItem item) {
        getWritableDatabase().update(TABLE_ITEMS, toValues(item),
                COL_ID + "=?", new String[]{String.valueOf(item.id)});
    }

    public void deleteItem(ClothingItem item) {
        getWritableDatabase().delete(TABLE_ITEMS, COL_ID + "=?", new String[]{String.valueOf(item.id)});
        ImageUtils.deleteQuietly(item.imagePath);
        ImageUtils.deleteQuietly(item.thumbPath);
    }

    public ClothingItem getItem(long id) {
        ArrayList<ClothingItem> items = query(COL_ID + "=?", new String[]{String.valueOf(id)});
        if (items.isEmpty()) {
            return null;
        }
        return items.get(0);
    }

    // category == null means every category
    public ArrayList<ClothingItem> getItems(String category) {
        if (category == null) {
            return query(null, null);
        }
        return query(COL_CATEGORY + "=?", new String[]{category});
    }

    // Everything that is not in the laundry, i.e. what can be worn today
    public ArrayList<ClothingItem> getAvailableItems() {
        return query(COL_LAUNDRY + "=0", null);
    }

    public int countItems() {
        return (int) DatabaseUtils.queryNumEntries(getReadableDatabase(), TABLE_ITEMS);
    }

    public int countInLaundry() {
        return (int) DatabaseUtils.queryNumEntries(getReadableDatabase(), TABLE_ITEMS, COL_LAUNDRY + "=1");
    }

    public int countAvailable() {
        return (int) DatabaseUtils.queryNumEntries(getReadableDatabase(), TABLE_ITEMS, COL_LAUNDRY + "=0");
    }

    // Adds one wear to every item of the chosen outfit
    public void markWorn(long[] ids) {
        SQLiteDatabase db = getWritableDatabase();
        String today = today();
        for (long id : ids) {
            db.execSQL("UPDATE " + TABLE_ITEMS + " SET "
                            + COL_TIMES_WORN + " = " + COL_TIMES_WORN + " + 1, "
                            + COL_LAST_WORN + " = ? WHERE " + COL_ID + " = ?",
                    new Object[]{today, id});
        }
    }

    public static String today() {
        return new SimpleDateFormat(DATE_PATTERN, Locale.US).format(new Date());
    }

    private ArrayList<ClothingItem> query(String selection, String[] args) {
        ArrayList<ClothingItem> items = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE_ITEMS, null, selection, args,
                null, null, COL_CREATED + " DESC");
        while (c.moveToNext()) {
            ClothingItem item = new ClothingItem();
            item.id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
            item.imagePath = c.getString(c.getColumnIndexOrThrow(COL_IMAGE));
            item.thumbPath = c.getString(c.getColumnIndexOrThrow(COL_THUMB));
            item.category = c.getString(c.getColumnIndexOrThrow(COL_CATEGORY));
            item.type = c.getString(c.getColumnIndexOrThrow(COL_TYPE));
            item.color = c.getString(c.getColumnIndexOrThrow(COL_COLOR));
            item.secondaryColor = c.getString(c.getColumnIndexOrThrow(COL_COLOR2));
            item.pattern = c.getString(c.getColumnIndexOrThrow(COL_PATTERN));
            item.formality = c.getString(c.getColumnIndexOrThrow(COL_FORMALITY));
            item.seasons = c.getString(c.getColumnIndexOrThrow(COL_SEASONS));
            item.inLaundry = c.getInt(c.getColumnIndexOrThrow(COL_LAUNDRY)) == 1;
            item.timesWorn = c.getInt(c.getColumnIndexOrThrow(COL_TIMES_WORN));
            item.lastWorn = c.getString(c.getColumnIndexOrThrow(COL_LAST_WORN));
            items.add(item);
        }
        c.close();
        return items;
    }

    private ContentValues toValues(ClothingItem item) {
        ContentValues values = new ContentValues();
        values.put(COL_IMAGE, item.imagePath);
        values.put(COL_THUMB, item.thumbPath);
        values.put(COL_CATEGORY, item.category);
        values.put(COL_TYPE, item.type);
        values.put(COL_COLOR, item.color);
        values.put(COL_COLOR2, item.secondaryColor);
        values.put(COL_PATTERN, item.pattern);
        values.put(COL_FORMALITY, item.formality);
        values.put(COL_SEASONS, item.seasons);
        values.put(COL_LAUNDRY, item.inLaundry ? 1 : 0);
        values.put(COL_TIMES_WORN, item.timesWorn);
        values.put(COL_LAST_WORN, item.lastWorn);
        return values;
    }
}
