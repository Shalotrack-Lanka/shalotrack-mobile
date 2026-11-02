package com.example.letstracklanka.utils;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Small helpers for the API envelope {success, statusCode, message, data, errors, timestamp}.
 * Shared by the newer screens so each one does not carry its own copy.
 */
public final class ApiJson {

    private static final String TAG = "ApiJson";
    private static final Gson GSON = new Gson();

    private ApiJson() {
    }

    /** The "data" object of an envelope (or the root object when there is no envelope). */
    public static <T> T object(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return GSON.fromJson(root.getAsJsonObject("data"), clazz);
            }
            return GSON.fromJson(json, clazz);
        } catch (Exception e) {
            Log.e(TAG, "object parse error", e);
            return null;
        }
    }

    /** The "data" array of an envelope as a list, or null when it is not an array. */
    public static <T> List<T> list(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                Type listType = TypeToken.getParameterized(List.class, clazz).getType();
                return GSON.fromJson(root.getAsJsonArray("data"), listType);
            }
        } catch (Exception e) {
            Log.e(TAG, "list parse error", e);
        }
        return null;
    }

    /** The raw "data" object (case-insensitive key), or null. */
    public static JsonObject dataObject(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return null;
            JsonElement data = get(root, "data");
            return data != null && data.isJsonObject() ? data.getAsJsonObject() : null;
        } catch (Exception e) {
            Log.e(TAG, "dataObject parse error", e);
            return null;
        }
    }

    /** The raw "data" array (case-insensitive key), or null. */
    public static JsonArray dataArray(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return null;
            JsonElement data = get(root, "data");
            return data != null && data.isJsonArray() ? data.getAsJsonArray() : null;
        } catch (Exception e) {
            Log.e(TAG, "dataArray parse error", e);
            return null;
        }
    }

    /**
     * The envelope's human-readable "message", or the fallback. Only for client errors (400, 409,
     * 422 ...) where the API wrote the message for the customer; never show it for 5xx.
     */
    public static String message(String json, String fallback) {
        if (json == null || json.trim().isEmpty()) return fallback;
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return fallback;
            JsonElement m = get(root, "message");
            if (m != null && m.isJsonPrimitive()) {
                String text = m.getAsString().trim();
                if (!text.isEmpty() && text.length() <= 200) return text;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return fallback;
    }

    /** Reads a string field from an object, case-insensitively; null when missing or not a string. */
    public static String string(JsonObject obj, String key) {
        JsonElement e = get(obj, key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    /** Case-insensitive member lookup (the API is camelCase, older models were PascalCase). */
    public static JsonElement get(JsonObject obj, String key) {
        if (obj == null) return null;
        if (obj.has(key) && !obj.get(key).isJsonNull()) return obj.get(key);
        for (String k : obj.keySet()) {
            if (k.equalsIgnoreCase(key) && !obj.get(k).isJsonNull()) return obj.get(k);
        }
        return null;
    }
}