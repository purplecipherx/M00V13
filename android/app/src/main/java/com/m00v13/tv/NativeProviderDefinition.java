package com.m00v13.tv;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NativeProviderDefinition {
    public final String id;
    public final String name;
    public final List<String> mirrors;
    public final String searchPath;
    public final String rowSelector;
    public final String titleSelector;
    public final String detailsSelector;
    public final String detailsAttribute;
    public final String seedersSelector;
    public final String sizeSelector;
    public final String rowMagnetSelector;
    public final String detailMagnetSelector;
    public final int maxResults;

    public NativeProviderDefinition(String id, String name, List<String> mirrors, String searchPath,
                                    String rowSelector, String titleSelector, String detailsSelector,
                                    String detailsAttribute, String seedersSelector, String sizeSelector,
                                    String rowMagnetSelector, String detailMagnetSelector, int maxResults) {
        this.id = id == null || id.isEmpty() ? "unknown" : id;
        this.name = name == null || name.isEmpty() ? this.id : name;
        this.mirrors = mirrors == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(mirrors));
        this.searchPath = searchPath;
        this.rowSelector = rowSelector;
        this.titleSelector = titleSelector;
        this.detailsSelector = detailsSelector == null || detailsSelector.isEmpty() ? titleSelector : detailsSelector;
        this.detailsAttribute = detailsAttribute == null || detailsAttribute.isEmpty() ? "href" : detailsAttribute;
        this.seedersSelector = seedersSelector;
        this.sizeSelector = sizeSelector;
        this.rowMagnetSelector = rowMagnetSelector == null ? "" : rowMagnetSelector;
        this.detailMagnetSelector = detailMagnetSelector == null ? "" : detailMagnetSelector;
        this.maxResults = Math.max(1, maxResults);
    }

    public static List<NativeProviderDefinition> load(Context context) {
        ArrayList<NativeProviderDefinition> out = new ArrayList<>();
        try (InputStream in = context.getAssets().open("cardigann_providers.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) >= 0) bytes.write(b, 0, n);
            JSONObject root = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            JSONArray providers = root.optJSONArray("providers");
            if (providers != null) for (int i = 0; i < providers.length(); i++) {
                JSONObject o = providers.optJSONObject(i); if (o == null) continue;
                NativeProviderDefinition d = new NativeProviderDefinition(
                    o.optString("id"), o.optString("name"), strings(o.optJSONArray("mirrors")),
                    o.optString("searchPath"), o.optString("rowSelector"), o.optString("titleSelector"),
                    o.optString("detailsSelector"), o.optString("detailsAttribute", "href"),
                    o.optString("seedersSelector"), o.optString("sizeSelector"),
                    o.optString("rowMagnetSelector"),
                    o.optString("detailMagnetSelector", o.optString("magnetSelector")),
                    o.optInt("maxResults", 16));
                boolean hasMagnet = !d.rowMagnetSelector.isEmpty() || !d.detailMagnetSelector.isEmpty();
                if (!d.mirrors.isEmpty() && !d.searchPath.isEmpty() && !d.rowSelector.isEmpty() &&
                    !d.titleSelector.isEmpty() && hasMagnet) out.add(d);
            }
        } catch (Exception ignored) {}
        return Collections.unmodifiableList(out);
    }

    private static List<String> strings(JSONArray a) {
        if (a == null) return Collections.emptyList();
        ArrayList<String> out = new ArrayList<>();
        for (int i=0; i<a.length(); i++) { String s=a.optString(i, ""); if(!s.isEmpty()) out.add(s); }
        return out;
    }
}
