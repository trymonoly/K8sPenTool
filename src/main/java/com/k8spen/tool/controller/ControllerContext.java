package com.k8spen.tool.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.k8spen.tool.utils.K8sHttpUtil;
import com.k8spen.tool.utils.K8sJsonRenderer;
import com.k8spen.tool.view.ResultPane;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 共享上下文：所有子Handler通过此对象访问公共状态与工具方法。
 */
public class ControllerContext {

    private final TextField targetHost;
    private final ComboBox<String> protocolOpt;
    private final TextField apiPort;
    private final TextField timeoutField;
    private final TextField tokenField;
    private final TextArea logTextArea;
    private final Label statusLabel;
    private final TextArea kubeconfigContent; // 供Kubeconfig模式使用

    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    public ControllerContext(TextField targetHost, ComboBox<String> protocolOpt, TextField apiPort,
                             TextField timeoutField, TextField tokenField,
                             TextArea logTextArea, Label statusLabel, TextArea kubeconfigContent) {
        this.targetHost = targetHost;
        this.protocolOpt = protocolOpt;
        this.apiPort = apiPort;
        this.timeoutField = timeoutField;
        this.tokenField = tokenField;
        this.logTextArea = logTextArea;
        this.statusLabel = statusLabel;
        this.kubeconfigContent = kubeconfigContent;
    }

    public String getHost() {
        String h = targetHost.getText().trim();
        if (h.isEmpty()) {
            log("[-] 请先填写目标地址");
            return null;
        }
        // 归一化：剥掉用户可能粘贴的 http(s):// 前缀和 :port 后缀
        String lower = h.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://")) h = h.substring(7);
        else if (lower.startsWith("https://")) h = h.substring(8);
        else if (lower.startsWith("tcp://")) h = h.substring(6);
        int slash = h.indexOf('/');
        if (slash > 0) h = h.substring(0, slash);
        if (h.startsWith("[") && h.indexOf(']') > 0) return h.substring(0, h.indexOf(']') + 1);
        int colon = h.lastIndexOf(':');
        if (colon > 0) h = h.substring(0, colon);
        return h;
    }

    public String getProtocol() {
        String p = protocolOpt != null ? protocolOpt.getValue() : null;
        return (p == null || p.isEmpty()) ? "https" : p;
    }

    public int getApiPort() {
        try {
            return Integer.parseInt(apiPort.getText().trim());
        } catch (Exception e) {
            return 6443;
        }
    }

    /**
     * 顶部目标配置对应的 API 基础地址，协议和端口由用户指定。
     */
    public String getApiBaseUrl() {
        String host = getHost();
        if (host == null || host.isEmpty()) return null;
        return getProtocol() + "://" + host + ":" + getApiPort();
    }

    public int getTimeout() {
        try {
            return Integer.parseInt(timeoutField.getText().trim());
        } catch (Exception e) {
            return 5;
        }
    }

    public String getToken() {
        return tokenField.getText().trim();
    }

    public TextField getTokenField() { return tokenField; }
    public TextArea getKubeconfigContent() { return kubeconfigContent; }

    public void log(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());
        Platform.runLater(() -> logTextArea.appendText("[" + ts + "] " + msg + "\n"));
    }

    public void setStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
    }

    public void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        log("[+] 已复制到剪贴板");
    }

    public void asyncGet(String url, ResultPane output) {
        asyncGet(url, output, null, null);
    }

    public void asyncGet(String url, ResultPane output, String cmdHint, TextArea cmdHintArea) {
        if (cmdHintArea != null && cmdHint != null) {
            cmdHintArea.setText(cmdHint);
        }
        setStatus("正在请求: " + url);
        output.setText("请求中...\n");
        log("[*] GET " + url);

        Task<String> task = new Task<String>() {
            @Override
            protected String call() throws Exception {
                return K8sHttpUtil.sendRequest(url, "GET", getToken(), getTimeout());
            }
        };
        task.setOnSucceeded(e -> {
            showHttpResponse(output, task.getValue());
            setStatus("请求完成");
            log("[+] 请求成功: " + url);
        });
        task.setOnFailed(e -> {
            String error = task.getException().getMessage();
            output.setText("[-] 请求失败: " + error);
            setStatus("请求失败");
            log("[-] 请求失败: " + url + " - " + error);
        });
        new Thread(task).start();
    }

    public void asyncPost(String url, String body, String contentType, ResultPane output) {
        setStatus("正在请求: " + url);
        output.setText("请求中...\n");
        log("[*] POST " + url);

        Task<String> task = new Task<String>() {
            @Override
            protected String call() throws Exception {
                return K8sHttpUtil.sendPost(url, body, contentType, getToken(), getTimeout());
            }
        };
        task.setOnSucceeded(e -> {
            showHttpResponse(output, task.getValue());
            setStatus("请求完成");
            log("[+] POST请求成功: " + url);
        });
        task.setOnFailed(e -> {
            String error = task.getException().getMessage();
            output.setText("[-] 请求失败: " + error);
            setStatus("请求失败");
            log("[-] POST请求失败: " + url + " - " + error);
        });
        new Thread(task).start();
    }

    void showHttpResponse(ResultPane output, String raw) {
        output.showResponse(raw, K8sJsonRenderer.render(raw));
    }

    public String prettyJson(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        String trimmed = raw.trim();
        String prefix = "";
        String json = trimmed;
        if (trimmed.startsWith("[HTTP")) {
            int newline = trimmed.indexOf('\n');
            if (newline > 0) {
                prefix = trimmed.substring(0, newline + 1);
                json = trimmed.substring(newline + 1).trim();
            }
        }
        if ((json.startsWith("{") && json.endsWith("}")) || (json.startsWith("[") && json.endsWith("]"))) {
            try {
                Object parsed = JsonParser.parseString(json);
                return prefix + PRETTY_GSON.toJson(parsed);
            } catch (Exception e) {
                return raw;
            }
        }
        return raw;
    }

    public String buildApiServerUrl(String path) {
        String base = getApiBaseUrl();
        if (base == null) return null;
        return base + path;
    }
}
