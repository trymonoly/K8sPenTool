package com.k8spen.tool.view;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayDeque;

public final class ResourceDetails {
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();

    private ResourceDetails() {}

    public static void show(Node owner, String title, JsonElement fields, JsonElement raw) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner.getScene().getWindow());
        dialog.setTitle("资源详情");
        dialog.setHeaderText(title.isBlank() ? "所选记录" : title);
        dialog.getDialogPane().getStyleClass().add("resource-dialog");
        dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getRoot().getStylesheets());
        dialog.getDialogPane().setPrefSize(
                Math.min(900, owner.getScene().getWidth() * 0.9),
                Math.min(680, owner.getScene().getHeight() * 0.85));
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TreeTableView<Field> tree = new TreeTableView<>(field("记录", fields));
        tree.setId("resource-field-tree");
        tree.setMinHeight(70);
        tree.setFixedCellSize(35);
        tree.setShowRoot(tree.getRoot().isLeaf());
        tree.getRoot().setExpanded(true);
        tree.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
        TreeTableColumn<Field, String> key = new TreeTableColumn<>("字段");
        key.setMinWidth(160);
        key.setPrefWidth(180);
        key.setMaxWidth(250);
        key.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getValue().key()));
        TreeTableColumn<Field, String> value = new TreeTableColumn<>("值");
        value.setPrefWidth(600);
        value.setCellValueFactory(cell -> new ReadOnlyStringWrapper(preview(cell.getValue().getValue().value())));
        tree.getColumns().add(key);
        tree.getColumns().add(value);
        TabPane tabs = new TabPane(new Tab("字段浏览", browser(tree)));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        if (raw != null) {
            TextArea json = new TextArea(pretty(raw));
            json.setEditable(false);
            json.getStyleClass().add("code-area");
            tabs.getTabs().add(new Tab("原始 JSON", json));
            ButtonType copy = new ButtonType("复制 JSON");
            dialog.getDialogPane().getButtonTypes().add(0, copy);
            Button copyButton = (Button) dialog.getDialogPane().lookupButton(copy);
            copyButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                TableRowActions.copy(json.getText());
                event.consume();
            });
        }
        dialog.getDialogPane().setContent(tabs);
        dialog.show();
    }

    private static SplitPane browser(TreeTableView<Field> tree) {
        TextArea content = new TextArea();
        content.setId("resource-field-content");
        content.setEditable(false);
        content.setMinHeight(60);
        content.getStyleClass().add("code-area");
        Label path = new Label();
        path.setId("resource-field-path");
        path.setMinWidth(0);
        path.getStyleClass().add("field-path");
        Label heading = new Label("字段内容");
        heading.setMinWidth(Region.USE_PREF_SIZE);
        heading.getStyleClass().add("field-heading");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        ToggleButton wrap = new ToggleButton("自动换行");
        wrap.setId("resource-field-wrap");
        wrap.setSelected(true);
        content.wrapTextProperty().bind(wrap.selectedProperty());
        Button copyValue = new Button("复制值");
        copyValue.setId("resource-field-copy");
        copyValue.disableProperty().bind(tree.getSelectionModel().selectedItemProperty().isNull());
        copyValue.setOnAction(event -> TableRowActions.copy(content.getText()));
        HBox toolbar = new HBox(8, heading, path, spacer, wrap, copyValue);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("field-content-toolbar");
        VBox viewer = new VBox(toolbar, content);
        viewer.getStyleClass().add("field-content-pane");
        viewer.setMinHeight(100);
        VBox.setVgrow(content, Priority.ALWAYS);
        SplitPane split = new SplitPane(tree, viewer);
        split.setOrientation(Orientation.VERTICAL);
        split.setMinHeight(0);
        split.setDividerPositions(0.43);
        split.getStyleClass().add("field-browser");
        tree.getSelectionModel().selectedItemProperty().addListener((obs, previous, selected) -> {
            path.setText(selected == null ? "" : path(selected));
            content.setText(selected == null ? "" : content(selected.getValue().value()));
            content.positionCaret(0);
        });
        // 优先打开键值记录的 value；其他资源显示首个字段，可继续展开浏览。
        TreeItem<Field> initial = tree.getRoot().getChildren().stream()
                .filter(item -> "value".equals(item.getValue().key())).findFirst()
                .orElse(tree.getRoot().getChildren().isEmpty() ? tree.getRoot() : tree.getRoot().getChildren().get(0));
        tree.getSelectionModel().select(initial);
        return split;
    }

    private static String content(JsonElement value) {
        return value != null && value.isJsonPrimitive() ? value.getAsString() : pretty(value);
    }

    private static String preview(JsonElement value) {
        String text = ResultTable.display(value);
        if (text.length() > 160) text = text.substring(0, 160) + "…";
        return text.replace("\r\n", " ↵ ").replace("\n", " ↵ ").replace("\r", " ↵ ");
    }

    private static String path(TreeItem<Field> item) {
        ArrayDeque<String> parts = new ArrayDeque<>();
        for (TreeItem<Field> current = item; current.getParent() != null; current = current.getParent()) {
            parts.addFirst(current.getValue().key());
        }
        return parts.isEmpty() ? item.getValue().key() : String.join(".", parts).replace(".[", "[");
    }

    static String pretty(JsonElement json) {
        return PRETTY.toJson(json);
    }

    private record Field(String key, JsonElement value) {}

    // 展开时才创建子项，避免大对象打开详情时阻塞界面。
    private static TreeItem<Field> field(String key, JsonElement value) {
        TreeItem<Field> item = new TreeItem<>(new Field(key, value)) {
            @Override public boolean isLeaf() {
                if (value instanceof JsonObject object) return object.size() == 0;
                if (value instanceof JsonArray array) return array.isEmpty();
                return true;
            }
        };
        if (!item.isLeaf()) {
            item.expandedProperty().addListener((obs, previous, expanded) -> {
                if (!expanded || !item.getChildren().isEmpty()) return;
                if (value instanceof JsonObject object) {
                    object.entrySet().forEach(entry -> item.getChildren().add(field(entry.getKey(), entry.getValue())));
                } else if (value instanceof JsonArray array) {
                    for (int i = 0; i < array.size(); i++) item.getChildren().add(field("[" + i + "]", array.get(i)));
                }
            });
        }
        return item;
    }
}
