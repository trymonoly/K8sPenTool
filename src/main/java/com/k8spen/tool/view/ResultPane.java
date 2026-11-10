package com.k8spen.tool.view;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** 支持资源表格、原始响应和已有文本摘要。 */
public final class ResultPane extends VBox {
    // 文本区不解析终端样式，清除 CSI 控制序列与 OSC 超链接包装。
    private static final Pattern ANSI_CONTROL = Pattern.compile(
            "(?:\\x1B\\[|\\x9B)[0-?]*[ -/]*[@-~]"
            + "|(?:\\x1B\\]|\\x9D)[^\\x07\\x1B\\x9C]*(?:\\x07|\\x1B\\\\|\\x9C)");

    private final Label resultTitle = new Label();
    private final Label http = new Label();
    private final Label count = new Label();
    private final Label hint = new Label();
    private final TextField filter = new TextField();
    private final Button details = new Button("查看详情");
    private final Button copy = new Button("复制输出");
    private final ToggleButton tableMode = new ToggleButton("表格");
    private final ToggleButton rawMode = new ToggleButton("原始响应");
    private final ToggleButton summaryMode = new ToggleButton("文本摘要");
    private final TableView<ResultTable.Row> table = new TableView<>();
    private final TextArea text = new TextArea();
    private final VBox empty = new VBox(8, new Label("等待结果"), new Label("执行查询后，资源将在这里展示。"));
    private final HBox toolbar;
    private final Button directoryUp = new Button("上一级");
    private final Button directoryRoot = new Button("根目录");
    private final Button directoryRefresh = new Button("刷新目录");
    private final Label directoryPath = new Label();
    private final HBox directoryNavigation;
    private final ObservableList<ResultTable.Row> rows = FXCollections.observableArrayList();
    private final FilteredList<ResultTable.Row> filtered = new FilteredList<>(rows);

    private ResultTable result;
    private String raw = "";
    private String summary = "";
    private Consumer<String> directoryOpenAction;

    public ResultPane() {
        getStyleClass().add("result-pane");
        setMinHeight(144);
        setPrefHeight(360);

        Label section = new Label("结果");
        section.getStyleClass().add("result-heading");
        resultTitle.getStyleClass().add("result-type");
        http.getStyleClass().add("http-badge");
        http.setMinWidth(Region.USE_PREF_SIZE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        ToggleGroup modes = new ToggleGroup();
        for (ToggleButton mode : List.of(tableMode, rawMode, summaryMode)) mode.setToggleGroup(modes);
        HBox switches = new HBox(3, tableMode, rawMode, summaryMode);
        switches.getStyleClass().add("result-switch");
        HBox heading = new HBox(10, section, resultTitle, http, spacer, switches, copy);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.getStyleClass().add("result-heading-bar");

        filter.setPromptText("筛选当前表格…");
        filter.setPrefWidth(250);
        filter.textProperty().addListener((obs, old, value) -> applyFilter());
        details.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        details.setOnAction(event -> openRow(table.getSelectionModel().getSelectedItem()));
        table.getSelectionModel().selectedItemProperty().addListener((obs, previous, selected) ->
                details.setText(openLabel(selected)));
        count.getStyleClass().add("result-count");
        Region toolbarSpacer = new Region();
        HBox.setHgrow(toolbarSpacer, Priority.ALWAYS);
        toolbar = new HBox(10, filter, count, toolbarSpacer, details);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("result-toolbar");

        directoryUp.setId("etcd-directory-up");
        directoryRoot.setId("etcd-directory-root");
        directoryRefresh.setId("etcd-directory-refresh");
        directoryPath.setId("etcd-directory-path");
        directoryPath.setMinWidth(0);
        directoryPath.setMaxWidth(Double.MAX_VALUE);
        directoryPath.getStyleClass().add("directory-path");
        HBox.setHgrow(directoryPath, Priority.ALWAYS);
        for (Button button : List.of(directoryUp, directoryRoot, directoryRefresh)) button.setMinWidth(Region.USE_PREF_SIZE);
        directoryUp.setOnAction(event -> openDirectory(parentDirectory()));
        directoryRoot.setOnAction(event -> openDirectory("/"));
        directoryRefresh.setOnAction(event -> openDirectory(currentDirectory()));
        directoryNavigation = new HBox(8, directoryUp, directoryRoot, new Label("位置"), directoryPath, directoryRefresh);
        directoryNavigation.setAlignment(Pos.CENTER_LEFT);
        directoryNavigation.getStyleClass().add("directory-navigation");

        SortedList<ResultTable.Row> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(35);
        TableRowActions.install(table, this::openLabel, this::openRow, this::rowActions);
        text.setEditable(false);
        text.getStyleClass().add("code-area");
        text.setMinHeight(0);
        empty.setAlignment(Pos.CENTER);
        empty.getStyleClass().add("result-empty");
        empty.getChildren().get(0).getStyleClass().add("empty-title");
        StackPane body = new StackPane(table, text, empty);
        VBox.setVgrow(body, Priority.ALWAYS);
        hint.getStyleClass().add("result-footer");
        hint.setMaxWidth(Double.MAX_VALUE);
        getChildren().addAll(heading, directoryNavigation, toolbar, body, hint);
        modes.selectedToggleProperty().addListener((obs, previous, selected) -> {
            if (selected == null) modes.selectToggle(previous);
            else refreshMode();
        });
        copy.setOnAction(event -> TableRowActions.copy(
                text.isEditable() ? text.getText() : summaryMode.isSelected() ? summary : raw));
        setText("");
    }

    public void setText(String text) {
        show(text, text, ResultTable.parseJson(text == null ? "" : text));
    }

    /** 纯文本输出（不尝试解析为 JSON 表格），只读。 */
    public void setPlainText(String output) {
        String readable = output == null ? "" : ANSI_CONTROL.matcher(output).replaceAll("");
        show(readable, readable, null);
    }

    public void setEditableText(String output) {
        show(output, output, null);
        text.setEditable(true);
    }

    public void showResponse(String raw, String summary) {
        show(raw, summary, ResultTable.parseJson(raw == null ? "" : raw));
    }

    public void showKubectl(String raw, boolean expectedTable) {
        ResultTable data = ResultTable.parseJson(raw);
        if (data == null) data = ResultTable.parseKubectl(raw, expectedTable);
        show(raw, raw, data);
    }

    public void showTable(String title, List<String> columns, List<List<String>> values, String original) {
        List<ResultTable.Row> data = new ArrayList<>();
        for (List<String> cells : values) {
            JsonObject fields = new JsonObject();
            for (int i = 0; i < columns.size(); i++) fields.addProperty(columns.get(i), cells.get(i));
            data.add(new ResultTable.Row(cells.get(0), List.copyOf(cells), fields));
        }
        show(original, original, new ResultTable(title, List.copyOf(columns), data));
    }

    public String getText() {
        return text.isEditable() ? text.getText() : summary;
    }

    public void clear() {
        setText("");
    }

    public void setEtcdDirectoryOpenAction(Consumer<String> action) {
        directoryOpenAction = action;
        refreshMode();
    }

    private void show(String raw, String summary, ResultTable result) {
        text.setEditable(false);
        this.raw = raw == null ? "" : raw;
        this.summary = summary == null ? this.raw : summary;
        this.result = result;
        tableMode.setText(isEtcdDirectory() ? "目录" : "表格");
        filter.setPromptText(isEtcdDirectory() ? "筛选当前目录…" : "筛选当前表格…");
        directoryPath.setText(isEtcdDirectory() ? currentDirectory() : "");
        Tooltip pathTooltip = new Tooltip(directoryPath.getText());
        pathTooltip.setWrapText(true);
        pathTooltip.setMaxWidth(640);
        directoryPath.setTooltip(pathTooltip);
        resultTitle.setText(result != null ? result.title() : "文本输出");
        String status = ResultTable.httpStatus(this.raw);
        http.setText(status.replace("[", "").replace("]", ""));
        http.getStyleClass().removeAll("http-ok", "http-error");
        if (!status.isEmpty()) http.getStyleClass().add(status.startsWith("[HTTP 2") ? "http-ok" : "http-error");
        showNode(http, !status.isEmpty());
        showNode(tableMode, result != null);
        showNode(summaryMode, !this.raw.equals(this.summary));
        copy.setDisable(this.raw.isBlank() && this.summary.isBlank());
        table.getSelectionModel().clearSelection();
        table.getSortOrder().clear();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.getColumns().clear();
        filter.clear();
        if (result != null) {
            for (int i = 0; i < result.columns().size(); i++) addColumn(result.columns().get(i), i);
        }
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        rows.setAll(result == null ? List.of() : result.rows());
        // 保持 FilteredList 和排序比较器的绑定，切换数据时重置筛选。
        applyFilter();
        (result != null ? tableMode : summaryMode.isVisible() ? summaryMode : rawMode).setSelected(true);
        refreshMode();
    }

    private void addColumn(String title, int index) {
        TableColumn<ResultTable.Row, String> column = new TableColumn<>(title);
        column.setPrefWidth(index == 0 ? 280 : 135);
        column.setMinWidth(index == 0 ? 240 : 65);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                index < cell.getValue().cells().size() ? cell.getValue().cells().get(index) : ""));
        column.setCellFactory(col -> new TableCell<>() {
            private final Label status = new Label();

            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(null);
                setText(empty || value == null ? null : value.replace('\n', ' ').replace('\r', ' '));
                if (!empty && value != null && isEtcdDirectory() && index == 0) {
                    int slash = value.lastIndexOf('/');
                    setText(slash >= 0 && slash < value.length() - 1 ? value.substring(slash + 1) : value);
                }
                setTooltip(empty || value == null || value.isBlank() || value.length() > 320 ? null : new Tooltip(value));
                if (!empty && value != null && ("状态".equals(title) || "STATUS".equals(title))) {
                    status.setText(value);
                    status.getStyleClass().setAll("status-badge", statusStyle(value));
                    setText(null);
                    setGraphic(status);
                }
                if (!empty && value != null && isEtcdDirectory() && index == 1) {
                    status.setText(value);
                    status.getStyleClass().setAll("etcd-kind", "目录".equals(value) ? "etcd-directory" : "etcd-key");
                    setText(null);
                    setGraphic(status);
                }
            }
        });
        table.getColumns().add(column);
    }

    private static String statusStyle(String value) {
        return switch (value) {
            case "Running", "Ready", "Succeeded", "Completed", "Active", "[OPEN]" -> "status-ok";
            case "Pending", "ContainerCreating", "Terminating" -> "status-pending";
            case "Failed", "Error", "NotReady", "CrashLoopBackOff", "ImagePullBackOff" -> "status-error";
            default -> "status-neutral";
        };
    }

    private void applyFilter() {
        String query = filter.getText().strip().toLowerCase(Locale.ROOT);
        filtered.setPredicate(row -> query.isEmpty() || row.cells().stream()
                .anyMatch(cell -> cell.toLowerCase(Locale.ROOT).contains(query)));
        int total = result == null ? 0 : result.rows().size();
        count.setText(query.isEmpty() ? total + " 条记录" : filtered.size() + " / " + total + " 条记录");
        table.setPlaceholder(new Label(query.isEmpty() ? isEtcdDirectory() ? "此目录下没有 Key" : "没有返回记录" : "没有匹配的记录"));
    }

    private void refreshMode() {
        boolean tabular = tableMode.isSelected() && result != null;
        String output = summaryMode.isSelected() ? summary : raw;
        showNode(table, tabular);
        showNode(toolbar, tabular);
        showNode(directoryNavigation, tabular && isEtcdDirectory());
        boolean root = "/".equals(currentDirectory());
        directoryUp.setDisable(root || directoryOpenAction == null);
        directoryRoot.setDisable(root || directoryOpenAction == null);
        directoryRefresh.setDisable(directoryOpenAction == null);
        showNode(text, !tabular && !output.isBlank());
        showNode(empty, !tabular && output.isBlank());
        if (!tabular && !output.equals(text.getText())) text.setText(output);
        hint.setText(tabular ? isEtcdDirectory() ? "双击目录进入  ·  双击 Key 查看内容  ·  上一级返回父目录" : "右键或双击记录查看详情  ·  点击列标题排序"
                : summaryMode.isSelected() ? "文本摘要" : "原始输出");
        showNode(hint, tabular || !output.isBlank());
    }

    private List<TableRowActions.Action<ResultTable.Row>> rowActions(ResultTable.Row row) {
        List<TableRowActions.Action<ResultTable.Row>> actions = new ArrayList<>();
        if (isDirectoryRow(row)) actions.add(new TableRowActions.Action<>("目录详情", this::showDetails));
        if (!row.title().isBlank()) actions.add(new TableRowActions.Action<>(isEtcdDirectory() ? "复制 Key" : "复制名称 / 字段", item -> TableRowActions.copy(item.title())));
        if (row.json() != null) actions.add(new TableRowActions.Action<>("复制此行 JSON", item -> TableRowActions.copy(ResourceDetails.pretty(item.json()))));
        actions.add(new TableRowActions.Action<>("复制此行表格数据", item -> TableRowActions.copy(String.join("\t", item.cells()))));
        return actions;
    }

    private boolean isEtcdDirectory() {
        return result != null && result.directoryRoot() != null;
    }

    private boolean isDirectoryRow(ResultTable.Row row) {
        return isEtcdDirectory() && row != null && ResultTable.isDirectory(row.json());
    }

    private String openLabel(ResultTable.Row row) {
        return isDirectoryRow(row) && directoryOpenAction != null ? "打开目录" : isEtcdDirectory() ? "查看内容" : "查看详情";
    }

    private String currentDirectory() {
        return isEtcdDirectory() ? result.directoryRoot().get("key").getAsString() : "/";
    }

    private String parentDirectory() {
        String path = currentDirectory();
        while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "/" : path.substring(0, slash);
    }

    private void openDirectory(String key) {
        if (directoryOpenAction != null) directoryOpenAction.accept(key);
    }

    private void openRow(ResultTable.Row row) {
        if (isDirectoryRow(row) && directoryOpenAction != null) openDirectory(row.title());
        else showDetails(row);
    }

    private void showDetails(ResultTable.Row row) {
        if (row == null) return;
        JsonElement fields = row.fields();
        if (fields == null) {
            JsonObject cells = new JsonObject();
            for (int i = 0; i < result.columns().size(); i++) cells.addProperty(result.columns().get(i), row.cells().get(i));
            fields = cells;
        }
        ResourceDetails.show(this, row.title(), fields, row.json());
    }

    private static void showNode(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
