package com.k8spen.tool.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

/** 独立文章阅读窗口，按章节展示说明与可选择复制的命令块。 */
public final class ArticleWindow {
    private ArticleWindow() {}

    public static Stage create(Node owner, String title, String content, Runnable copyArticle) {
        Window parent = owner.getScene().getWindow();
        Stage stage = new Stage();
        stage.initOwner(parent);
        stage.setTitle(title);

        Label heading = new Label(title);
        heading.setWrapText(true);
        heading.setMinWidth(0);
        heading.getStyleClass().add("article-title");
        HBox.setHgrow(heading, Priority.ALWAYS);
        Button copy = new Button("复制全文");
        copy.setMinWidth(Region.USE_PREF_SIZE);
        copy.setOnAction(event -> copyArticle.run());
        HBox header = new HBox(16, heading, copy);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("article-header");

        VBox article = new VBox(22);
        article.setMaxWidth(860);
        article.getStyleClass().add("article-body");
        for (String block : content.strip().split("\\R\\s*\\R")) {
            if (block.startsWith("# =====")) continue;
            String[] section = block.split("\\R", 2);
            if (section[0].startsWith("# ")) {
                Label subtitle = new Label(section[0].substring(2));
                subtitle.setWrapText(true);
                subtitle.getStyleClass().add("article-section-title");
                VBox chapter = new VBox(10, subtitle);
                if (section.length == 2) chapter.getChildren().add(codeBlock(section[1]));
                article.getChildren().add(chapter);
            } else {
                article.getChildren().add(codeBlock(block));
            }
        }
        StackPane body = new StackPane(article);
        StackPane.setAlignment(article, Pos.TOP_CENTER);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("article-scroll");

        Button close = new Button("关闭");
        close.setOnAction(event -> stage.close());
        HBox footer = new HBox(close);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(10, 20, 10, 20));
        BorderPane root = new BorderPane(scroll, header, null, footer, null);
        root.getStyleClass().add("article-window");
        root.getStylesheets().addAll(owner.getScene().getRoot().getStylesheets());
        stage.setScene(new Scene(root));

        Rectangle2D bounds = Screen.getScreensForRectangle(
                parent.getX() + parent.getWidth() / 2, parent.getY() + parent.getHeight() / 2, 1, 1)
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        double width = Math.min(900, bounds.getWidth() * 0.86);
        double height = Math.min(760, bounds.getHeight() * 0.86);
        stage.setMinWidth(Math.min(560, width));
        stage.setMinHeight(Math.min(360, height));
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(Math.max(bounds.getMinX(),
                Math.min(parent.getX() + (parent.getWidth() - width) / 2, bounds.getMaxX() - width)));
        stage.setY(Math.max(bounds.getMinY(),
                Math.min(parent.getY() + (parent.getHeight() - height) / 2, bounds.getMaxY() - height)));
        return stage;
    }

    private static TextArea codeBlock(String code) {
        TextArea area = new TextArea(code);
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefRowCount((int) code.lines().count());
        area.setMinHeight(Region.USE_PREF_SIZE);
        area.setMaxHeight(Region.USE_PREF_SIZE);
        area.getStyleClass().add("article-code");
        return area;
    }
}
