package com.k8spen.tool.helper;

import com.google.gson.JsonObject;
import javafx.beans.property.SimpleStringProperty;

/**
 * Pod表格行数据模型
 */
public class PodTableItem {
    private final SimpleStringProperty namespace;
    private final SimpleStringProperty name;
    private final SimpleStringProperty status;
    private final SimpleStringProperty node;
    private final SimpleStringProperty podIP;
    private final SimpleStringProperty containers;
    private final SimpleStringProperty images;
    private JsonObject resource;

    public PodTableItem(String namespace, String name, String status, String node,
                        String podIP, String containers, String images) {
        this.namespace = new SimpleStringProperty(namespace);
        this.name = new SimpleStringProperty(name);
        this.status = new SimpleStringProperty(status);
        this.node = new SimpleStringProperty(node);
        this.podIP = new SimpleStringProperty(podIP);
        this.containers = new SimpleStringProperty(containers);
        this.images = new SimpleStringProperty(images);
    }

    public String getNamespace() { return namespace.get(); }
    public String getName() { return name.get(); }
    public String getStatus() { return status.get(); }
    public String getNode() { return node.get(); }
    public String getPodIP() { return podIP.get(); }
    public String getContainers() { return containers.get(); }
    public String getImages() { return images.get(); }

    public JsonObject getResource() { return resource; }
    void setResource(JsonObject resource) { this.resource = resource; }

    public SimpleStringProperty namespaceProperty() { return namespace; }
    public SimpleStringProperty nameProperty() { return name; }
    public SimpleStringProperty statusProperty() { return status; }
    public SimpleStringProperty nodeProperty() { return node; }
    public SimpleStringProperty podIPProperty() { return podIP; }
    public SimpleStringProperty containersProperty() { return containers; }
    public SimpleStringProperty imagesProperty() { return images; }

    public String getFirstContainer() {
        String c = getContainers();
        if (c == null || c.isEmpty()) return "";
        return c.split(",")[0].trim();
    }
}
