package com.k8spen.tool.view;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/** 将命令参考按步骤展示，原始文本用于整页复制。 */
public final class CommandCards extends VBox {
    private final Label count = new Label();
    private final GridPane grid = new GridPane();
    private String source = "";
    private int columns;

    public CommandCards() {
        setSpacing(10);
        setMinWidth(0);
        getStyleClass().add("command-cards");
        count.getStyleClass().add("result-count");
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setMinWidth(0);
        grid.widthProperty().addListener((obs, previous, current) -> layoutColumns());
        getChildren().addAll(count, grid);
    }

    public void setText(String text) {
        source = text == null ? "" : text;
        grid.getChildren().clear();
        int number = 0;
        for (String block : source.strip().split("\\R\\s*\\R")) {
            if (block.isBlank() || block.startsWith("# =====")) continue;
            grid.add(card(block, ++number), 0, number - 1);
        }
        count.setText(number + " 项命令参考");
        columns = 0;
        layoutColumns();
    }

    public String getText() {
        return source;
    }

    private VBox card(String block, int number) {
        String[] lines = block.split("\\R");
        String title = lines[0].replaceFirst("^#\\s*", "").replaceFirst("^\\d+\\.\\s*", "");
        List<String> notes = new ArrayList<>();
        List<String> code = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (code.isEmpty() && line.stripLeading().startsWith("#")) {
                notes.add(line.stripLeading().replaceFirst("^#\\s*", ""));
            } else {
                code.add(line);
            }
        }
        if (code.isEmpty() && lines[0].matches("#\\s+\\d+\\..*")) {
            code.addAll(List.of(lines).subList(1, lines.length));
            notes.clear();
        }
        Label badge = new Label(String.format("%02d", number));
        badge.getStyleClass().add("command-number");
        Label heading = new Label(title);
        heading.setWrapText(true);
        heading.setMinWidth(0);
        heading.setMinHeight(Region.USE_PREF_SIZE);
        heading.setMaxWidth(Double.MAX_VALUE);
        heading.getStyleClass().add("command-card-title");
        HBox.setHgrow(heading, Priority.ALWAYS);
        HBox header = new HBox(10, badge, heading);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(12, header);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("command-card");
        if (!notes.isEmpty()) {
            Label description = new Label(String.join("\n", notes));
            description.setWrapText(true);
            description.setMinHeight(Region.USE_PREF_SIZE);
            description.getStyleClass().add("command-description");
            card.getChildren().add(description);
        }
        if (!code.isEmpty()) {
            String command = String.join("\n", code);
            Button copy = new Button("复制");
            copy.setMinWidth(Region.USE_PREF_SIZE);
            copy.getStyleClass().add("command-copy");
            copy.setOnAction(event -> {
                TableRowActions.copy(command);
                copy.setText("已复制");
            });
            header.getChildren().add(copy);
            Label codeText = new Label(command);
            codeText.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            codeText.getStyleClass().add("command-code-text");
            ScrollPane codeBox = new ScrollPane(codeText);
            codeBox.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            // 预留横向滚动条的高度，长命令也能完整展示末行。
            codeBox.prefViewportHeightProperty().bind(codeText.heightProperty().add(18));
            codeBox.setMinHeight(Region.USE_PREF_SIZE);
            codeBox.setMaxHeight(Region.USE_PREF_SIZE);
            codeBox.getStyleClass().add("command-code-box");
            card.getChildren().add(codeBox);
        }
        return card;
    }

    private void layoutColumns() {
        int desired = grid.getWidth() >= 900 ? 2 : 1;
        if (columns == desired) return;
        columns = desired;
        grid.getColumnConstraints().clear();
        for (int i = 0; i < columns; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / columns);
            column.setMinWidth(0);
            column.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(column);
        }
        for (int i = 0; i < grid.getChildren().size(); i++) {
            GridPane.setColumnIndex(grid.getChildren().get(i), i % columns);
            GridPane.setRowIndex(grid.getChildren().get(i), i / columns);
        }
    }
}
