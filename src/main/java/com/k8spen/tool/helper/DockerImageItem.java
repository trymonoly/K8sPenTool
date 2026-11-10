package com.k8spen.tool.helper;

import javafx.beans.property.SimpleStringProperty;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Docker 镜像表格行数据模型。
 */
public class DockerImageItem {

    private final String fullId;
    private final List<String> tags;
    private final SimpleStringProperty imageId;
    private final SimpleStringProperty repoTags;
    private final SimpleStringProperty size;
    private final SimpleStringProperty created;

    public DockerImageItem(String fullId, String repoTags, long sizeBytes, long createdEpochSec) {
        this.fullId = fullId == null ? "" : fullId.trim();
        this.tags = Arrays.stream((repoTags == null ? "" : repoTags).split(","))
                .map(String::trim).filter(DockerImageItem::hasReference).distinct().toList();
        this.imageId = new SimpleStringProperty(shortId(this.fullId));
        this.repoTags = new SimpleStringProperty(tags.isEmpty()
                ? "未打标签（" + imageId.get() + "）" : String.join(", ", tags));
        this.size = new SimpleStringProperty(sizeMb(sizeBytes));
        this.created = new SimpleStringProperty(created(createdEpochSec));
    }

    public String getFullId() { return fullId; }
    public String getImageId() { return imageId.get(); }
    public String getRepoTags() { return repoTags.get(); }
    public String getSize() { return size.get(); }
    public String getCreated() { return created.get(); }

    public SimpleStringProperty imageIdProperty() { return imageId; }
    public SimpleStringProperty repoTagsProperty() { return repoTags; }
    public SimpleStringProperty sizeProperty() { return size; }
    public SimpleStringProperty createdProperty() { return created; }

    /** 取第一个可用仓库标签，作为创建容器时的镜像名 */
    public String getFirstRepoTag() {
        return tags.isEmpty() ? "" : tags.get(0);
    }

    public boolean hasRepoTags() { return !tags.isEmpty(); }

    /** 下拉框保存可实际提交的标签或完整 ID，展示名称不参与请求。 */
    public List<String> getImageReferences() {
        return !tags.isEmpty() ? tags : hasReference(fullId) ? List.of(fullId) : List.of();
    }

    public String getImageReference() {
        List<String> references = getImageReferences();
        return references.isEmpty() ? "" : references.get(0);
    }

    /** 过滤空值和占位符，允许标签、摘要或镜像 ID。 */
    public static boolean hasReference(String value) {
        return value != null && !value.isBlank() && !"-".equals(value.trim()) && !value.contains("<none>");
    }

    @Override
    public String toString() {
        return repoTags.get();
    }

    private static String shortId(String value) {
        if (!hasReference(value)) return "未返回 ID";
        String stripped = value.startsWith("sha256:") ? value.substring(7) : value;
        return stripped.length() > 12 ? stripped.substring(0, 12) : stripped;
    }

    private static String sizeMb(long bytes) {
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    private static String created(long epochSec) {
        if (epochSec <= 0) return "未返回";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(epochSec * 1000L));
    }

}
