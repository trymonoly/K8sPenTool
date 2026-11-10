package com.k8spen.tool.controller;

import com.k8spen.tool.view.ResultPane;
import com.k8spen.tool.utils.KubectlUtil;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.*;

import java.util.ArrayList;
import java.util.List;

/**
 * kubectl操作 — 快捷命令 / 自定义命令 / 后门部署
 */
public class KubectlHandler {

    private final ControllerContext ctx;

    private final ComboBox<String> kubectlConnMode;
    private final TextField kubectlUsername;
    private final PasswordField kubectlPassword;
    private final TextField kubectlExtraArgs;
    private final TextField kubectlCustomCmd;
    private final ResultPane kubectlOutput;

    // 需要从其他handler读取的共享控件
    private final TextArea kubeconfigContent;
    private final TextArea backdoorYamlOutput;
    private final TextField backdoorPodName;

    public KubectlHandler(ControllerContext ctx,
                          ComboBox<String> kubectlConnMode,
                          TextField kubectlUsername, PasswordField kubectlPassword,
                          TextField kubectlExtraArgs, TextField kubectlCustomCmd,
                          ResultPane kubectlOutput,
                          TextArea kubeconfigContent,
                          TextArea backdoorYamlOutput, TextField backdoorPodName) {
        this.ctx = ctx;
        this.kubectlConnMode = kubectlConnMode;
        this.kubectlUsername = kubectlUsername; this.kubectlPassword = kubectlPassword;
        this.kubectlExtraArgs = kubectlExtraArgs; this.kubectlCustomCmd = kubectlCustomCmd;
        this.kubectlOutput = kubectlOutput;
        this.kubeconfigContent = kubeconfigContent;
        this.backdoorYamlOutput = backdoorYamlOutput; this.backdoorPodName = backdoorPodName;
    }

    public void init() {
        new Thread(() -> {
            try {
                KubectlUtil.init();
                Platform.runLater(() -> ctx.log("[+] kubectl已加载: " + KubectlUtil.getPath()));
            } catch (Exception e) {
                Platform.runLater(() -> ctx.log("[-] kubectl加载失败: " + e.getMessage()));
            }
        }).start();
    }

    // ================ 快捷命令 ================

    public void kubectlGetNodes()       { runKubectl("get", "nodes", "-o", "wide"); }
    public void kubectlGetPods()        { runKubectl("get", "pods", "-o", "wide"); }
    public void kubectlGetAllPods()     { runKubectl("get", "pods", "--all-namespaces", "-o", "wide"); }
    public void kubectlGetImages()      { runKubectl("get", "pods", "--all-namespaces", "-o", "jsonpath={range .items[*]}{.metadata.namespace}{'\\t'}{.metadata.name}{'\\t'}{range .spec.containers[*]}{.image}{'\\n'}{end}{end}"); }
    public void kubectlGetServices()    { runKubectl("get", "services", "--all-namespaces", "-o", "wide"); }
    public void kubectlGetSecrets()     { runKubectl("get", "secrets", "--all-namespaces"); }
    public void kubectlGetDeployments() { runKubectl("get", "deployments", "--all-namespaces", "-o", "wide"); }
    public void kubectlClusterInfo()    { runKubectl("cluster-info"); }
    public void kubectlAuthCanI()       { runKubectl("auth", "can-i", "--list"); }
    public void kubectlGetSA()          { runKubectl("get", "serviceaccounts", "--all-namespaces"); }
    public void kubectlGetCRB()         { runKubectl("get", "clusterrolebindings", "-o", "wide"); }

    // ================ 部署后门 ================

    public void kubectlApplyBackdoor() {
        String yaml = backdoorYamlOutput != null ? backdoorYamlOutput.getText() : "";
        if (yaml.trim().isEmpty() || !yaml.contains("apiVersion")) {
            kubectlOutput.setText("[-] 请先在 '2.执行 -> 创建后门Pod' 页签中生成YAML");
            return;
        }
        ctx.setStatus("正在部署后门Pod...");
        ctx.log("[*] 部署后门Pod...");
        kubectlOutput.setText("部署中...\n");
        String mode = kubectlConnMode.getValue();
        String host = ctx.getHost(); String token = ctx.getToken();
        String username = kubectlUsername != null ? kubectlUsername.getText().trim() : "";
        String password = kubectlPassword != null ? kubectlPassword.getText().trim() : "";
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                String yamlPath = KubectlUtil.saveYaml(yaml);
                if (mode.contains("Kubeconfig")) {
                    String kcText = kubeconfigContent != null ? kubeconfigContent.getText() : "";
                    return KubectlUtil.execWithKubeconfig(KubectlUtil.saveKubeconfig(kcText), ctx.getTimeout(), "apply", "-f", yamlPath);
                } else {
                    String useToken = (token != null && !token.isEmpty()) ? token : null;
                    String useUser = !username.isEmpty() ? username : null;
                    String usePass = !password.isEmpty() ? password : null;
                    return KubectlUtil.exec(ctx.getApiBaseUrl(), useToken, useUser, usePass, true, ctx.getTimeout(), "apply", "-f", yamlPath);
                }
            }
        };
        task.setOnSucceeded(e -> {
            kubectlOutput.setText(task.getValue());
            ctx.log("[*] 执行: " + KubectlUtil.lastCommand);
            if (task.getValue().startsWith("[Exit code:")) { ctx.setStatus("后门Pod部署失败"); ctx.log("[-] 后门Pod部署失败"); }
            else { ctx.setStatus("后门Pod部署完成"); ctx.log("[+] 后门Pod部署完成"); }
        });
        task.setOnFailed(e -> {
            kubectlOutput.setText("[-] 部署失败: " + (task.getException() != null ? task.getException().getMessage() : ""));
            ctx.log("[-] 部署失败: " + (task.getException() != null ? task.getException().getMessage() : ""));
        });
        new Thread(task).start();
    }

    public void kubectlDeletePod() {
        String podName = backdoorPodName != null ? backdoorPodName.getText().trim() : "";
        if (podName.isEmpty()) { kubectlOutput.setText("[-] 请在 '创建后门Pod' 页签中填写Pod名"); return; }
        runKubectl("delete", "pod", podName);
    }

    // ================ 自定义命令 ================

    public void kubectlRunCustom() {
        String cmd = kubectlCustomCmd.getText().trim();
        if (cmd.isEmpty()) { kubectlOutput.setText("[-] 请输入kubectl子命令, 例如: get namespaces"); return; }
        runKubectl(cmd.split("\\s+"));
    }

    public void kubectlClearOutput() { kubectlOutput.clear(); }

    // ================ 核心执行 ================

    private void runKubectl(String... args) {
        String mode = kubectlConnMode.getValue();
        String extra = kubectlExtraArgs.getText().trim();
        String host = ctx.getHost(); String token = ctx.getToken();
        String username = kubectlUsername != null ? kubectlUsername.getText().trim() : "";
        String password = kubectlPassword != null ? kubectlPassword.getText().trim() : "";

        List<String> allArgs = new ArrayList<>();
        for (String a : args) allArgs.add(a);
        if (!extra.isEmpty()) { for (String part : extra.split("\\s+")) { if (!part.isEmpty()) allArgs.add(part); } }
        String[] finalArgs = allArgs.toArray(new String[0]);

        // 记录完整命令
        StringBuilder preLog = new StringBuilder("kubectl");
        if (mode.contains("Kubeconfig")) {
            preLog.append(" --kubeconfig=<kubeconfig>");
        } else if (mode.contains("直接")) {
            if (host != null) preLog.append(" --server=").append(ctx.getApiBaseUrl()).append(" --insecure-skip-tls-verify=true");
        } else {
            if (host != null) preLog.append(" --server=").append(ctx.getApiBaseUrl()).append(" --insecure-skip-tls-verify=true");
            if (token != null && !token.isEmpty()) preLog.append(" --token=").append(token.length() > 20 ? token.substring(0, 20) + "..." : token);
        }
        for (String a : finalArgs) preLog.append(" ").append(a);
        String fullCmdStr = preLog.toString();

        ctx.log("[*] " + fullCmdStr);
        ctx.setStatus("正在执行 kubectl...");
        kubectlOutput.setText("执行中...\n" + fullCmdStr + "\n\n");

        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                if (mode.contains("Kubeconfig")) {
                    String kubeconfigText = kubeconfigContent != null ? kubeconfigContent.getText() : "";
                    if (kubeconfigText.trim().isEmpty()) return "[-] 请先在 '1.初始访问 -> Kubeconfig' 页签中粘贴kubeconfig内容";
                    return KubectlUtil.execWithKubeconfig(KubectlUtil.saveKubeconfig(kubeconfigText), ctx.getTimeout(), finalArgs);
                } else if (mode.contains("直接")) {
                    if (host != null && !host.isEmpty()) {
                        List<String> rawArgs = new ArrayList<>();
                        rawArgs.add("--server=" + ctx.getApiBaseUrl());
                        rawArgs.add("--insecure-skip-tls-verify=true");
                        if (!username.isEmpty()) { rawArgs.add("--username=" + username); rawArgs.add("--password=" + password); }
                        for (String a : finalArgs) rawArgs.add(a);
                        return KubectlUtil.execRaw(ctx.getTimeout(), rawArgs.toArray(new String[0]));
                    }
                    return KubectlUtil.execRaw(ctx.getTimeout(), finalArgs);
                } else {
                    if (host == null || host.isEmpty()) return "[-] 请在目标配置中填写目标地址";
                    String useToken = (token != null && !token.isEmpty()) ? token : null;
                    String useUser = !username.isEmpty() ? username : null;
                    String usePass = !password.isEmpty() ? password : null;
                    return KubectlUtil.exec(ctx.getApiBaseUrl(), useToken, useUser, usePass, true, ctx.getTimeout(), finalArgs);
                }
            }
        };
        task.setOnSucceeded(e -> {
            kubectlOutput.showKubectl(task.getValue(), args[0].equals("get") || args[0].equals("auth"));
            ctx.log("[*] 执行: " + KubectlUtil.lastCommand);
            if (task.getValue().startsWith("[Exit code:")) { ctx.setStatus("kubectl执行失败"); ctx.log("[-] kubectl执行失败"); }
            else { ctx.setStatus("kubectl执行完成"); ctx.log("[+] kubectl执行成功"); }
        });
        task.setOnFailed(e -> {
            String errMsg = task.getException() != null ? task.getException().getMessage() : "未知错误";
            kubectlOutput.setText("[-] 执行失败: " + errMsg);
            ctx.setStatus("kubectl执行失败");
            ctx.log("[*] 执行: " + KubectlUtil.lastCommand);
            ctx.log("[-] kubectl失败: " + errMsg);
        });
        new Thread(task).start();
    }
}
