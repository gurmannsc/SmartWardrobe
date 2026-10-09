package com.example.smartwardrobe;

import android.content.Context;

// One piece of clothing, as stored in the "items" table.
public class ClothingItem {

    public long id = -1;
    public String imagePath;
    public String thumbPath;
    public String category;
    public String type;
    public String color;
    public String secondaryColor;
    public String pattern;
    public String formality;
    public String seasons;      // comma separated, e.g. "Summer,Monsoon"
    public boolean inLaundry;
    public int timesWorn;
    public String lastWorn;     // yyyy-MM-dd, or null if never worn

    // e.g. "Maroon Kurta"
    public String getTitle(Context context) {
        return context.getString(R.string.item_title, color, type);
    }
}
