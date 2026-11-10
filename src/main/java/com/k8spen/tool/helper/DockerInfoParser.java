package com.k8spen.tool.helper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 把 Docker "/info" 与 "/version" 的 JSON 渲染成中文摘要，供 ResultPane 的“文本摘要”视图使用。
 * 字段对齐原始 DockerAPITool 的 DockerInfoParser（基础信息 / 容器 / 镜像 / 插件 / 安全选项）。
 */
public final class DockerInfoParser {

    private DockerInfoParser() {
    }

    public static String format(String infoBody, String versionBody) {
        JsonObject info = parse(infoBody);
        if (info == null) return infoBody == null ? "" : infoBody;
        JsonObject version = parse(versionBody);

        StringBuilder sb = new StringBuilder();
        sb.append("===== 基本信息 =====\n");
        line(sb, "Docker 版本", text(info, "serverVersion") + apiSuffix(version));
        line(sb, "操作系统", text(info, "operatingSystem") + " " + text(info, "osVersion"));
        line(sb, "内核版本", text(info, "kernelVersion"));
        line(sb, "架构", text(info, "architecture"));
        line(sb, "存储驱动", text(info, "driver"));
        line(sb, "Cgroup 驱动", text(info, "cGroupDriver"));
        line(sb, "CPU 核心", number(info, "nCPU"));
        line(sb, "内存总量", memoryMb(info, "memTotal"));
        line(sb, "Docker 根目录", text(info, "dockerRootDir"));
        sb.append("\n");

        sb.append("===== 容器统计 =====\n");
        line(sb, "总数", number(info, "containers"));
        line(sb, "运行中", number(info, "containersRunning"));
        line(sb, "已暂停", number(info, "containersPaused"));
        line(sb, "已停止", number(info, "containersStopped"));
        sb.append("\n");

        sb.append("===== 镜像统计 =====\n");
        line(sb, "总数", number(info, "images"));
        sb.append("\n");

        JsonObject plugins = object(info, "plugins");
        if (plugins != null) {
            sb.append("===== 插件 =====\n");
            for (String key : plugins.keySet()) {
                sb.append(key).append(" : ").append(plugins.get(key)).append("\n");
            }
            sb.append("\n");
        }

        JsonArray security = array(info, "securityOptions");
        if (security != null && security.size() > 0) {
            sb.append("===== 安全选项 =====\n");
            for (JsonElement option : security) {
                sb.append("  ").append(option.toString().replace("\"", "")).append("\n");
            }
        }
        return sb.toString();
    }

    private static String apiSuffix(JsonObject version) {
        if (version == null) return "";
        String api = text(version, "ApiVersion");
        return api.isBlank() ? "" : " (API " + api + ")";
    }

    private static void line(StringBuilder sb, String name, String value) {
        sb.append(String.format("%-14s: %s%n", name, value));
    }

    private static JsonObject parse(String body) {
        if (body == null) return null;
        String text = body.trim();
        if (!text.startsWith("{")) return null;
        try {
            JsonElement element = JsonParser.parseString(text);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? "-" : value.getAsString();
    }

    private static String number(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? "-" : String.valueOf(value.getAsLong());
    }

    private static String memoryMb(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || value.isJsonNull()) return "-";
        try {
            return (value.getAsLong() / (1024 * 1024)) + " MB";
        } catch (Exception e) {
            return "-";
        }
    }

    private static JsonObject object(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }
}
