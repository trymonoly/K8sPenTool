package com.k8spen.tool.view;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

record ResultTable(String title, List<String> columns, List<Row> rows, JsonObject directoryRoot) {
    ResultTable(String title, List<String> columns, List<Row> rows) {
        this(title, columns, rows, null);
    }

    record Row(String title, List<String> cells, JsonElement json, JsonElement fields) {
        Row(String title, List<String> cells, JsonElement json) {
            this(title, cells, json, json);
        }
    }

    private record Column(String title, Function<JsonElement, String> value) {}

    private static final Gson GSON = new Gson();
    private static final Pattern HTTP = Pattern.compile("^\\[HTTP \\d{3}\\]");
    private static final Pattern COLUMN_GAP = Pattern.compile(" {2,}|\\t+");
    private static final Column NAME = column("名称", "metadata.name");
    private static final Column NAMESPACE = column("命名空间", "metadata.namespace");
    private static final Column CREATED = column("创建时间", "metadata.creationTimestamp");

    static String httpStatus(String raw) {
        var match = HTTP.matcher(raw.stripLeading());
        return match.find() ? match.group().strip() : "";
    }

    static ResultTable parseJson(String raw) {
        String body = HTTP.matcher(raw.strip()).replaceFirst("").stripLeading();
        if (!body.startsWith("{") && !body.startsWith("[")) return null;
        try (JsonReader reader = new JsonReader(new StringReader(body))) {
            JsonElement json = GSON.getAdapter(JsonElement.class).read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) return null;
            if (json.isJsonArray()) return list("JSON 数组", "", json.getAsJsonArray());
            if (json.isJsonObject()) return object(json.getAsJsonObject());
        } catch (IOException | IllegalStateException | NumberFormatException ignored) {
            // 非 JSON 输出由原始响应视图显示。
        }
        return null;
    }

    private static ResultTable object(JsonObject root) {
        String kind = value(root, "kind");
        if ("Table".equals(kind) && at(root, "columnDefinitions") instanceof JsonArray definitions
                && at(root, "rows") instanceof JsonArray rows && !definitions.isEmpty()) {
            List<String> headers = stream(definitions).map(def -> value(def, "name")).toList();
            List<Row> data = stream(rows).map(row -> {
                List<String> cells = stream(array(row, "cells")).map(ResultTable::display).toList();
                return new Row(value(row, "object.metadata.name"), cells, row);
            }).toList();
            return new ResultTable("Kubernetes Table", headers, data);
        }
        if (at(root, "items") instanceof JsonArray items) {
            return list(kind.isEmpty() ? "资源列表" : kind, kind.replaceFirst("List$", ""), items);
        }
        if ("SelfSubjectRulesReview".equals(kind)) {
            List<Row> rows = new ArrayList<>();
            for (JsonElement rule : array(root, "status.resourceRules")) {
                rows.add(ruleRow("资源", rule, "apiGroups", "resources", "resourceNames"));
            }
            for (JsonElement rule : array(root, "status.nonResourceRules")) {
                rows.add(ruleRow("非资源", rule, "", "nonResourceURLs", ""));
            }
            return new ResultTable(kind, List.of("规则类型", "API 组", "资源 / URL", "资源名称", "操作"), rows);
        }
        if (at(root, "kvs") instanceof JsonArray kvs) {
            return mapped("Etcd 键值", kvs, ResultTable::etcdKeyFields,
                    new Column("键", item -> decodeEtcdKey(value(item, "key"))), column("版本", "version"),
                    column("修订", "mod_revision"), column("租约", "lease"));
        }
        if (at(root, "node.key") != null) {
            JsonObject node = root.getAsJsonObject("node");
            JsonArray entries = isDirectory(node) ? array(node, "nodes") : new JsonArray();
            if (!isDirectory(node)) entries.add(node);
            ResultTable directory = mapped("Etcd v2 目录", entries, column("名称", "key"),
                    new Column("类型", item -> isDirectory(item) ? "目录" : "Key"),
                    column("修订", "modifiedIndex"), column("过期时间", "expiration"));
            List<Row> rows = directory.rows().stream()
                    .sorted(Comparator.comparing((Row row) -> !isDirectory(row.json())).thenComparing(Row::title)).toList();
            return new ResultTable(directory.title(), directory.columns(), rows, node);
        }
        for (String field : List.of("resources", "groups", "versions")) {
            if (at(root, field) instanceof JsonArray items) {
                return list(kind.isEmpty() ? field : kind, "", items);
            }
        }
        if (!value(root, "metadata.name").isEmpty()) {
            JsonArray items = new JsonArray();
            items.add(root);
            return list(kind.isEmpty() ? "资源详情" : kind, kind, items);
        }
        List<Row> fields = root.entrySet().stream()
                .map(entry -> new Row(entry.getKey(), List.of(entry.getKey(), display(entry.getValue())), entry.getValue()))
                .toList();
        return new ResultTable(kind.isEmpty() ? "JSON 对象" : kind, List.of("字段", "值"), fields);
    }

    private static ResultTable list(String title, String kind, JsonArray items) {
        if (kind.isEmpty() && !items.isEmpty()) kind = value(items.get(0), "kind");
        return switch (kind) {
            case "Pod" -> mapped(title, items, NAME, NAMESPACE, column("状态", "status.phase"),
                    column("节点", "spec.nodeName"), column("Pod IP", "status.podIP"),
                    new Column("容器", item -> join(item, "spec.containers", "name")),
                    new Column("镜像", item -> join(item, "spec.containers", "image")));
            case "Node" -> mapped(title, items, NAME,
                    new Column("状态", item -> stream(array(item, "status.conditions"))
                            .filter(condition -> "Ready".equals(value(condition, "type")))
                            .map(condition -> "True".equals(value(condition, "status")) ? "Ready" : "NotReady")
                            .findFirst().orElse("Unknown")),
                    new Column("内部 IP", item -> stream(array(item, "status.addresses"))
                            .filter(address -> "InternalIP".equals(value(address, "type")))
                            .map(address -> value(address, "address")).collect(Collectors.joining(", "))),
                    column("系统", "status.nodeInfo.osImage"), column("Kubelet 版本", "status.nodeInfo.kubeletVersion"));
            case "Service" -> mapped(title, items, NAME, NAMESPACE, column("类型", "spec.type"),
                    column("Cluster IP", "spec.clusterIP"),
                    new Column("端口", item -> stream(array(item, "spec.ports"))
                            .map(port -> value(port, "port") + "/" + value(port, "protocol"))
                            .collect(Collectors.joining(", "))));
            case "Endpoints" -> mapped(title, items, NAME, NAMESPACE,
                    new Column("地址", item -> stream(array(item, "subsets"))
                            .flatMap(subset -> stream(array(subset, "addresses")))
                            .map(address -> value(address, "ip")).collect(Collectors.joining(", "))), CREATED);
            case "Secret" -> mapped(title, items, NAME, NAMESPACE, column("类型", "type"),
                    new Column("数据项数", item -> at(item, "data") instanceof JsonObject data
                            ? String.valueOf(data.size()) : "0"), CREATED);
            case "Deployment", "DaemonSet", "StatefulSet", "ReplicaSet" ->
                    mapped(title, items, NAME, NAMESPACE, column("就绪副本", "status.readyReplicas"),
                            new Column("镜像", item -> join(item, "spec.template.spec.containers", "image")), CREATED);
            case "Namespace" -> mapped(title, items, NAME, column("状态", "status.phase"), CREATED);
            case "ClusterRoleBinding", "RoleBinding" -> mapped(title, items, NAME, NAMESPACE,
                    column("角色", "roleRef.name"), new Column("主体", item -> join(item, "subjects", "name")), CREATED);
            default -> genericList(title, items);
        };
    }

    private static ResultTable genericList(String title, JsonArray items) {
        if (stream(items).anyMatch(item -> at(item, "metadata.name") != null)) {
            return mapped(title, items, NAME, NAMESPACE, column("类型", "kind"), CREATED);
        }
        if (!items.isEmpty() && stream(items).allMatch(JsonElement::isJsonObject)) {
            var keys = new LinkedHashSet<String>();
            stream(items).forEach(item -> keys.addAll(item.getAsJsonObject().keySet()));
            if (!keys.isEmpty()) {
                return mapped(title, items, keys.stream().limit(8)
                        .map(key -> new Column(key, item -> display(item.getAsJsonObject().get(key))))
                        .toArray(Column[]::new));
            }
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String number = String.valueOf(i + 1);
            rows.add(new Row(number, List.of(number, display(items.get(i))), items.get(i)));
        }
        return new ResultTable(title, List.of("序号", "值"), rows);
    }

    // 仅在已知 kubectl 列表操作或符合列标题特征时解析等宽文本。
    static ResultTable parseKubectl(String raw, boolean expectedTable) {
        List<String> lines = raw.lines().filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty()) return null;
        List<String> headers = List.of(COLUMN_GAP.split(lines.get(0).strip()));
        if (headers.size() < 2 || (!expectedTable && !headers.contains("NAME"))) return null;
        if (headers.stream().anyMatch(header -> !header.matches("[A-Z][A-Z0-9 /%()_-]*|Non-Resource URLs|Resource Names|Resources|Verbs"))) {
            return null;
        }
        List<Row> rows = new ArrayList<>();
        int nameIndex = Math.max(0, headers.indexOf("NAME"));
        for (String line : lines.subList(1, lines.size())) {
            List<String> cells = List.of(COLUMN_GAP.split(line.strip(), headers.size()));
            if (cells.size() != headers.size()) return null;
            rows.add(new Row(cells.get(nameIndex), cells, null));
        }
        return new ResultTable("kubectl 输出", headers, rows);
    }

    private static Row ruleRow(String kind, JsonElement rule, String group, String resource, String name) {
        return new Row(join(rule, resource, ""), List.of(kind, join(rule, group, ""), join(rule, resource, ""),
                join(rule, name, ""), join(rule, "verbs", "")), rule);
    }

    static boolean isDirectory(JsonElement node) {
        return "true".equals(value(node, "dir"));
    }

    private static ResultTable mapped(String title, JsonArray items, Column... columns) {
        return mapped(title, items, Function.identity(), columns);
    }

    private static ResultTable mapped(String title, JsonArray items,
                                      Function<JsonElement, JsonElement> fields, Column... columns) {
        List<Column> schema = List.of(columns);
        List<Row> rows = stream(items).map(item -> {
            List<String> values = schema.stream().map(column -> column.value().apply(item)).toList();
            String name = value(item, "metadata.name");
            if (name.isEmpty()) name = value(item, "name");
            if (name.isEmpty()) name = values.get(0);
            return new Row(name, values, item, fields.apply(item));
        }).toList();
        return new ResultTable(title, schema.stream().map(Column::title).toList(), rows);
    }

    // 仅 Etcd v3 的 kvs.key 使用 Base64，v2 与普通 JSON 的 key 保持原样。
    private static String decodeEtcdKey(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            String decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            // 二进制 Key 或无效编码保留原值，避免把不可读字节显示为乱码。
            return decoded.codePoints().anyMatch(Character::isISOControl) ? encoded : decoded;
        } catch (IllegalArgumentException | CharacterCodingException ignored) {
            return encoded;
        }
    }

    private static JsonElement etcdKeyFields(JsonElement item) {
        if (!(item instanceof JsonObject object)) return item;
        String encoded = value(item, "key");
        String decoded = decodeEtcdKey(encoded);
        if (decoded.equals(encoded)) return item;
        JsonObject fields = object.deepCopy();
        fields.addProperty("key", decoded);
        fields.addProperty("key（原始 Base64）", encoded);
        return fields;
    }

    private static Column column(String title, String path) {
        return new Column(title, item -> value(item, path));
    }

    private static String join(JsonElement item, String path, String field) {
        return stream(array(item, path)).map(entry -> value(entry, field)).filter(text -> !text.isEmpty())
                .collect(Collectors.joining(", "));
    }

    private static Stream<JsonElement> stream(JsonArray array) {
        return StreamSupport.stream(array.spliterator(), false);
    }

    private static JsonArray array(JsonElement item, String path) {
        return at(item, path) instanceof JsonArray array ? array : new JsonArray();
    }

    private static JsonElement at(JsonElement item, String path) {
        if (path.isEmpty()) return item;
        for (String part : path.split("\\.")) {
            if (!(item instanceof JsonObject object)) return null;
            item = object.get(part);
        }
        return item;
    }

    private static String value(JsonElement item, String path) {
        JsonElement field = at(item, path);
        return field != null && field.isJsonPrimitive() ? field.getAsString() : "";
    }

    static String display(JsonElement item) {
        if (item == null) return "";
        if (item instanceof JsonObject object) return object.size() + " 个字段";
        if (item instanceof JsonArray array) return array.size() + " 项";
        return item.isJsonNull() ? "null" : item.getAsString();
    }
}
