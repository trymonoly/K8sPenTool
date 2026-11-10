package com.k8spen.tool.view;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public final class TableRowActions {
    private TableRowActions() {}

    public record Action<T>(String title, Consumer<T> run) {}

    public static <T> void install(TableView<T> table, String detailsLabel, Consumer<T> details,
                                   Function<T, List<Action<T>>> extraActions) {
        install(table, item -> detailsLabel, details, extraActions);
    }

    public static <T> void install(TableView<T> table, Function<T, String> detailsLabel, Consumer<T> details,
                                   Function<T, List<Action<T>>> extraActions) {
        table.setRowFactory(view -> {
            TableRow<T> row = new TableRow<>();
            row.setOnContextMenuRequested(event -> {
                T item = row.getItem();
                if (row.isEmpty() || item == null) return;
                table.getSelectionModel().select(row.getIndex());
                ContextMenu menu = new ContextMenu(menuItem(detailsLabel.apply(item), () -> details.accept(item)));
                for (Action<T> action : extraActions.apply(item)) {
                    menu.getItems().add(menuItem(action.title(), () -> action.run().accept(item)));
                }
                menu.show(row, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    details.accept(row.getItem());
                }
            });
            return row;
        });
        table.setOnKeyPressed(event -> {
            T selected = table.getSelectionModel().getSelectedItem();
            if (event.getCode() == KeyCode.ENTER && selected != null) {
                details.accept(selected);
                event.consume();
            }
        });
    }

    private static MenuItem menuItem(String title, Runnable action) {
        MenuItem item = new MenuItem(title);
        item.setOnAction(event -> action.run());
        return item;
    }

    static void copy(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
