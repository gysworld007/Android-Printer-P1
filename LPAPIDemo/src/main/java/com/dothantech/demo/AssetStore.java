package com.dothantech.demo;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AssetStore {
    public static final String PREFS_NAME = "asset_label_prefs";
    public static final String PREF_ASSET_LABELS = "asset_labels_json";

    public static List<AssetLabel> load(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return fromAssetArrayString(preferences.getString(PREF_ASSET_LABELS, "[]"));
    }

    public static void save(Context context, List<AssetLabel> assets) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(PREF_ASSET_LABELS, toAssetArray(assets).toString())
                .apply();
    }

    public static JSONArray toAssetArray(List<AssetLabel> assets) {
        JSONArray array = new JSONArray();
        for (AssetLabel asset : assets) {
            array.put(toJson(asset));
        }
        return array;
    }

    public static JSONObject toJson(AssetLabel asset) {
        JSONObject object = new JSONObject();
        try {
            object.put("code", safeText(asset.code));
            object.put("sn", safeText(asset.sn));
            object.put("name", safeText(asset.name));
            object.put("user", safeText(asset.user));
            object.put("buyDate", safeText(asset.buyDate));
            object.put("remark", safeText(asset.remark));
            object.put("eventsJson", safeText(asset.eventsJson));
            object.put("createdAt", asset.createdAt);
            object.put("updatedAt", asset.updatedAt);
            object.put("archived", asset.archived);
            object.put("archivedAt", asset.archivedAt);
            object.put("archiveReason", safeText(asset.archiveReason));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return object;
    }

    public static AssetLabel fromJson(JSONObject object) {
        AssetLabel asset = new AssetLabel();
        asset.code = jsonString(object, "code");
        asset.sn = jsonString(object, "sn");
        asset.name = jsonString(object, "name");
        asset.user = jsonString(object, "user");
        asset.buyDate = jsonString(object, "buyDate");
        asset.remark = jsonString(object, "remark");
        asset.eventsJson = jsonString(object, "eventsJson");
        asset.createdAt = object.optLong("createdAt", System.currentTimeMillis());
        asset.updatedAt = object.optLong("updatedAt", asset.createdAt);
        asset.archived = object.optBoolean("archived", false);
        asset.archivedAt = object.optLong("archivedAt", 0);
        asset.archiveReason = jsonString(object, "archiveReason");
        return asset;
    }

    public static List<AssetLabel> fromAssetArrayString(String json) {
        List<AssetLabel> assets = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < array.length(); i++) {
                AssetLabel asset = fromJson(array.getJSONObject(i));
                if (!TextUtils.isEmpty(asset.code)) {
                    assets.add(asset);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return assets;
    }

    public static String toBackupJson(List<AssetLabel> assets) {
        JSONObject object = new JSONObject();
        try {
            object.put("schemaVersion", 1);
            object.put("exportedAt", System.currentTimeMillis());
            object.put("assets", toAssetArray(assets));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return object.toString();
    }

    public static List<AssetLabel> fromBackupJson(String json) throws JSONException {
        JSONArray assets;
        String text = json == null ? "" : json.trim();
        if (text.length() > 0 && text.charAt(0) == 0xFEFF) {
            text = text.substring(1).trim();
        }
        if (text.startsWith("[")) {
            assets = new JSONArray(text);
        } else {
            JSONObject object = new JSONObject(text);
            assets = object.optJSONArray("assets");
            if (assets == null) {
                assets = new JSONArray();
            }
        }
        List<AssetLabel> result = new ArrayList<>();
        for (int i = 0; i < assets.length(); i++) {
            AssetLabel asset = fromJson(assets.getJSONObject(i));
            if (!TextUtils.isEmpty(asset.code)) {
                result.add(asset);
            }
        }
        return result;
    }

    public static List<AssetLabel> activeAssets(List<AssetLabel> assets) {
        List<AssetLabel> activeAssets = new ArrayList<>();
        for (AssetLabel asset : assets) {
            if (!asset.archived) {
                activeAssets.add(asset);
            }
        }
        return activeAssets;
    }

    public static List<AssetLabel> archivedAssets(List<AssetLabel> assets) {
        List<AssetLabel> archivedAssets = new ArrayList<>();
        for (AssetLabel asset : assets) {
            if (asset.archived) {
                archivedAssets.add(asset);
            }
        }
        return archivedAssets;
    }

    public static AssetLabel findActiveBySn(List<AssetLabel> assets, String sn, AssetLabel exceptAsset) {
        String normalizedSn = normalizeScannedSn(sn);
        if (TextUtils.isEmpty(normalizedSn)) {
            return null;
        }
        for (AssetLabel asset : assets) {
            if (!asset.archived && asset != exceptAsset && TextUtils.equals(normalizedSn, safeText(asset.sn).trim())) {
                return asset;
            }
        }
        return null;
    }

    public static List<AssetLabel> search(List<AssetLabel> assets, String keyword) {
        String value = safeText(keyword).trim().toLowerCase(Locale.US);
        if (TextUtils.isEmpty(value)) {
            return new ArrayList<>(assets);
        }
        List<AssetLabel> result = new ArrayList<>();
        for (AssetLabel asset : assets) {
            String searchable = (safeText(asset.code) + "\n" + safeText(asset.sn) + "\n" + safeText(asset.name) + "\n" + safeText(asset.user)).toLowerCase(Locale.US);
            if (searchable.contains(value)) {
                result.add(asset);
            }
        }
        return result;
    }

    public static void addEvent(AssetLabel asset, String type, String note) {
        JSONArray events = eventsArray(asset);
        JSONObject event = new JSONObject();
        try {
            event.put("type", safeText(type));
            event.put("note", safeText(note));
            event.put("time", System.currentTimeMillis());
            events.put(event);
            asset.eventsJson = events.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static JSONArray eventsArray(AssetLabel asset) {
        try {
            return new JSONArray(safeText(asset.eventsJson));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public static String normalizeScannedSn(String value) {
        String text = safeText(value).trim();
        int separator = text.indexOf('|');
        if (separator >= 0 && separator < text.length() - 1) {
            text = text.substring(separator + 1).trim();
        }
        return text;
    }

    public static String formatTime(long time) {
        if (time <= 0) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(time));
    }

    public static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static String jsonString(JSONObject object, String name) {
        String value = object.optString(name, "");
        return "null".equals(value) ? "" : value;
    }
}
