/*
 *       This file was started on 14:00 20th November.
 * */

package com.example.waste;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Categorizer {
    private static final String labelFile = "labels_without_background.txt";
    private static final String categoryFile = "label_categorization.txt";
    private static final int NUM_CLASSES = 1000;
    private static final int NUM_CATEGORIES = 10;
    private static final String TAG = "WASTE";
    public Map<String, Integer> categorizedMap;

    public enum Categories {
        paper,
        glass,
        metal,
        plastic,
        trash,
        electronic,
        timber,
        textile,
        organic,
        rubble;

        private final int value;

        private Categories() {
            this.value = ordinal();
        }
    }

    public Categorizer(Context context) {
        Log.i(TAG,"Constructor called");
        categorizedMap = new HashMap<String, Integer>();
        InputStream labelStream, categoryStream;
        StringBuilder labelBuilder = new StringBuilder();
        StringBuilder categoryBuilder = new StringBuilder();
        int counter = 0;
        try {
            labelStream = context.getAssets().open(labelFile);
            categoryStream = context.getAssets().open(categoryFile);
            try {
                String labelText, categoryText;
                BufferedReader labelReader = new BufferedReader(new InputStreamReader(labelStream, "UTF-8"));
                BufferedReader categoryReader = new BufferedReader(new InputStreamReader(categoryStream, "UTF-8"));
                while ((counter < NUM_CLASSES) && ((labelText = labelReader.readLine()) != null) && ((categoryText = categoryReader.readLine()) != null)) {
                    counter++;
                    int category = 4;
                    try {
                        category = Integer.parseInt(categoryText);
                    } catch (Exception e) {
                        Log.e(TAG, "Fail to parse category: " + e.toString());
                    }
                    categorizedMap.put(labelText, category);
                }
            } catch (Exception e) {
                Log.e(TAG, "Fail to read text files: " + e.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "Fail to open text files: " + e.toString());
        }
    }

    public String getCategory(int ctg) {
        if (ctg >= 0 && ctg < NUM_CATEGORIES) return Categories.values()[ctg].name();
        return null;
    }
}
