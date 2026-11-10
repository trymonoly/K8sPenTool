package com.k8spen.tool.helper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析K8s API的Pod JSON响应为PodTableItem列表
 */
public class PodJsonParser {

    public static String stripHttpPrefix(String raw) {
        if (raw == null) return "";
        if (raw.startsWith("[HTTP")) {
            int idx = raw.indexOf('\n');
            if (idx >= 0) return raw.substring(idx + 1).trim();
        }
        return raw.trim();
    }

    public static List<PodTableItem> parse(String json) {
        List<PodTableItem> result = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(stripHttpPrefix(json)).getAsJsonObject();

            if (root.has("items")) {
                JsonArray items = root.getAsJsonArray("items");
                for (JsonElement elem : items) {
                    PodTableItem item = parsePod(elem.getAsJsonObject());
                    if (item != null) result.add(item);
                }
            } else if (root.has("kind") && "Pod".equals(root.get("kind").getAsString())) {
                PodTableItem item = parsePod(root);
                if (item != null) result.add(item);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static PodTableItem parsePod(JsonObject pod) {
        try {
            JsonObject meta = pod.getAsJsonObject("metadata");
            JsonObject spec = pod.getAsJsonObject("spec");
            JsonObject statusObj = pod.has("status") ? pod.getAsJsonObject("status") : null;

            String namespace = meta.has("namespace") ? meta.get("namespace").getAsString() : "";
            String name = meta.has("name") ? meta.get("name").getAsString() : "";
            String phase = statusObj != null && statusObj.has("phase")
                    ? statusObj.get("phase").getAsString() : "Unknown";
            String node = spec.has("nodeName") ? spec.get("nodeName").getAsString() : "";
            String podIP = statusObj != null && statusObj.has("podIP")
                    ? statusObj.get("podIP").getAsString() : "";

            StringBuilder containerNames = new StringBuilder();
            StringBuilder imageNames = new StringBuilder();
            if (spec.has("containers")) {
                JsonArray containers = spec.getAsJsonArray("containers");
                for (int i = 0; i < containers.size(); i++) {
                    JsonObject c = containers.get(i).getAsJsonObject();
                    if (i > 0) { containerNames.append(", "); imageNames.append(", "); }
                    containerNames.append(c.get("name").getAsString());
                    if (c.has("image")) imageNames.append(c.get("image").getAsString());
                }
            }

            PodTableItem item = new PodTableItem(namespace, name, phase, node, podIP,
                    containerNames.toString(), imageNames.toString());
            item.setResource(pod);
            return item;
        } catch (Exception e) {
            return null;
        }
    }
}
