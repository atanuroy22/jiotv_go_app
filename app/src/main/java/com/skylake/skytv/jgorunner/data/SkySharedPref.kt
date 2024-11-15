package com.skylake.skytv.jgorunner.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

public class SkySharedPref {

    private static final String PREF_NAME = "SkySharedPref";
    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    public SkySharedPref(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    // Method to save key-value pairs
    public void setKey(String key, String value) {
        editor.putString(key, value);
        editor.apply();
    }

    // Overloaded method to retrieve String by key with a default value
    public String getKey(String key) {
        return sharedPreferences.getString(key, null); // Default to null if no value exists
    }

    public String getKey(String key, String defaultValue) {
        return sharedPreferences.getString(key, defaultValue); // Use provided default value
    }

    // Save int key-value pair
    public void setInt(String key, int value) {
        editor.putInt(key, value);
        editor.apply();
    }

    // Overloaded method to retrieve int by key with a default value
    public int getInt(String key) {
        return sharedPreferences.getInt(key, 0); // Default to 0 if no value exists
    }

    public int getInt(String key, int defaultValue) {
        return sharedPreferences.getInt(key, defaultValue); // Use provided default value
    }

    // Save boolean key-value pair
    public void setBoolean(String key, boolean value) {
        editor.putBoolean(key, value);
        editor.apply();
    }

    // Overloaded method to retrieve boolean by key with a default value
    public boolean getBoolean(String key) {
        return sharedPreferences.getBoolean(key, false); // Default to false if no value exists
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return sharedPreferences.getBoolean(key, defaultValue); // Use provided default value
    }

    // Remove a specific key
    public void removeKey(String key) {
        editor.remove(key);
        editor.apply();
    }

    // Clear all preferences
    public void clearAll() {
        editor.clear();
        editor.apply();
    }

    // Check if a key exists
    public boolean containsKey(String key) {
        return sharedPreferences.contains(key);
    }

    // Retrieve all stored preferences (key-value pairs)
    public Map<String, ?> getAll() {
        return sharedPreferences.getAll();
    }
}
