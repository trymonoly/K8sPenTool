package com.k8spen.tool.helper;

import javafx.beans.property.SimpleStringProperty;

/**
 * Docker 容器表格行数据模型。
 * 保存完整 ID 供 API 调用使用，表格中只显示短 ID。
 */
public class DockerContainerItem {

    private final String fullId;
    private final SimpleStringProperty id;
    private final SimpleStringProperty name;
    private final SimpleStringProperty image;
    private final SimpleStringProperty state;
    private final SimpleStringProperty status;

    public DockerContainerItem(String fullId, String name, String image, String state, String status) {
        this.fullId = fullId == null ? "" : fullId.trim();
        this.id = new SimpleStringProperty(shortId(this.fullId));
        this.name = new SimpleStringProperty(orElse(name, "未命名容器"));
        this.image = new SimpleStringProperty(orElse(image, "未返回镜像信息"));
        this.state = new SimpleStringProperty(orElse(state, "未知"));
        this.status = new SimpleStringProperty(orElse(status, "未返回状态"));
    }

    public String getFullId() { return fullId; }
    public String getId() { return id.get(); }
    public String getName() { return name.get(); }
    public String getImage() { return image.get(); }
    public String getState() { return state.get(); }
    public String getStatus() { return status.get(); }

    public SimpleStringProperty idProperty() { return id; }
    public SimpleStringProperty nameProperty() { return name; }
    public SimpleStringProperty imageProperty() { return image; }
    public SimpleStringProperty stateProperty() { return state; }
    public SimpleStringProperty statusProperty() { return status; }

    public boolean isRunning() {
        return "running".equalsIgnoreCase(state.get());
    }

    /** ComboBox 中显示 "名称 (短ID)" */
    @Override
    public String toString() {
        return name.get() + " (" + id.get() + ")";
    }

    private static String shortId(String value) {
        if (value == null || value.isEmpty()) return "未返回 ID";
        return value.length() > 12 ? value.substring(0, 12) : value;
    }

    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() || "-".equals(value.trim()) ? fallback : value.trim();
    }
}
