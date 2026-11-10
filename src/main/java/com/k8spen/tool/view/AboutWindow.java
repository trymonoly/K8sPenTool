package com.k8spen.tool.view;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

/** 关于窗口，分区展示项目信息与完整免责声明。 */
public final class AboutWindow {
    private static final String PROJECT_URL = "https://github.com/trymonoly/K8sPenTool";
    private static final String DISCLAIMER = "\u2501\u2501\u2501\u2501\u2501 \u514d\u8d23\u58f0\u660e \u2501\u2501\u2501\u2501\u2501\n\n"
              + "\u672c\u5de5\u5177\u4ec5\u7528\u4e8e\u5408\u6cd5\u6388\u6743\u7684\u5b89\u5168\u6d4b\u8bd5\u4e0e\u7814\u7a76\u7528\u9014\u3002\n\n"
              + "\u4f7f\u7528\u8005\u5e94\u786e\u4fdd\u5df2\u83b7\u5f97\u76ee\u6807\u7cfb\u7edf\u6240\u6709\u8005\u7684\u660e\u786e\u4e66\u9762\u6388\u6743\uff0c\n"
              + "\u5e76\u4e14\u5728\u6388\u6743\u8303\u56f4\u5185\u8fdb\u884c\u6d4b\u8bd5\u3002\n\n"
              + "\u26a0 \u98ce\u9669\u8b66\u544a:\n"
              + "\u2022 \u672c\u5de5\u5177\u53ef\u80fd\u5bf9\u76ee\u6807\u7cfb\u7edf\u9020\u6210\u4e0d\u53ef\u9006\u7684\u5f71\u54cd\n"
              + "\u2022 \u5305\u62ec\u4f46\u4e0d\u9650\u4e8e: \u6570\u636e\u6cc4\u9732\u3001\u670d\u52a1\u4e2d\u65ad\u3001\u6743\u9650\u63d0\u5347\u3001\n"
              + "  \u540e\u95e8\u690d\u5165\u3001\u5bb9\u5668\u9003\u9038\u7b49\n"
              + "\u2022 \u672a\u7ecf\u6388\u6743\u4f7f\u7528\u672c\u5de5\u5177\u53ef\u80fd\u8fdd\u53cd\u6cd5\u5f8b\u6cd5\u89c4\n\n"
              + "\u4f5c\u8005\u4e0d\u5bf9\u4efb\u4f55\u6ee5\u7528\u3001\u8bef\u7528\u6216\u975e\u6cd5\u4f7f\u7528\u672c\u5de5\u5177\u6240\u9020\u6210\u7684\n"
              + "\u76f4\u63a5\u6216\u95f4\u63a5\u635f\u5931\u627f\u62c5\u4efb\u4f55\u8d23\u4efb\u3002\n\n"
              + "GitHub: https://github.com/trymonoly/K8sPenTool";

    private AboutWindow() {}

    public static void show(Window owner) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("关于 K8sPenTool");
        if (owner instanceof Stage main) stage.getIcons().addAll(main.getIcons());

        Label logo = new Label("K8");
        logo.getStyleClass().add("brand-mark");
        Label name = new Label("K8sPenTool");
        name.getStyleClass().add("about-title");
        Label caption = new Label("Kubernetes 安全测试工作台");
        caption.getStyleClass().add("about-caption");
        VBox identity = new VBox(4, name, caption);
        HBox.setHgrow(identity, Priority.ALWAYS);
        Label version = new Label("v2.1");
        version.getStyleClass().add("about-version");
        HBox header = new HBox(14, logo, identity, version);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("about-header");

        Label infoTitle = new Label("项目信息");
        infoTitle.getStyleClass().add("about-section-title");
        Label author = new Label("T3Y");
        author.getStyleClass().add("about-author");
        HBox authorRow = new HBox(12, new Label("作者"), author);
        authorRow.setAlignment(Pos.CENTER_LEFT);
        Label link = new Label(PROJECT_URL);
        link.setWrapText(true);
        link.setMinWidth(0);
        link.getStyleClass().add("about-project-link");
        Button copy = new Button("复制链接");
        copy.setMinWidth(Region.USE_PREF_SIZE);
        copy.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(PROJECT_URL);
            Clipboard.getSystemClipboard().setContent(content);
            copy.setText("已复制");
        });
        Region infoSpacer = new Region();
        HBox.setHgrow(infoSpacer, Priority.ALWAYS);
        HBox infoHeader = new HBox(12, infoTitle, infoSpacer, copy);
        infoHeader.setAlignment(Pos.CENTER_LEFT);
        VBox project = new VBox(10, infoHeader, authorRow, link);
        project.getStyleClass().add("about-card");

        // 保留原有声明原文，仅调整排版。
        String[] sections = DISCLAIMER.split("\n\n");
        Label disclaimerTitle = new Label("免责声明");
        disclaimerTitle.getStyleClass().add("about-section-title");
        String[] warning = sections[3].split("\n", 2);
        Label warningTitle = new Label(warning[0]);
        warningTitle.getStyleClass().add("about-warning-title");
        Label warningText = paragraph(warning[1].replace("\n  ", ""), true);
        VBox risks = new VBox(8, warningTitle, warningText);
        risks.getStyleClass().add("about-warning");
        VBox disclaimer = new VBox(14, disclaimerTitle,
                paragraph(sections[1], false), paragraph(sections[2], false),
                risks, paragraph(sections[4], false));
        disclaimer.getStyleClass().add("about-card");
        VBox content = new VBox(14, project, disclaimer);
        content.getStyleClass().add("about-content");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("about-scroll");

        Button close = new Button("关闭");
        close.setDefaultButton(true);
        close.setCancelButton(true);
        close.setOnAction(event -> stage.close());
        HBox footer = new HBox(close);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getStyleClass().add("about-footer");
        BorderPane root = new BorderPane(scroll, header, null, footer, null);
        root.getStyleClass().add("about-window");
        root.getStylesheets().addAll(owner.getScene().getRoot().getStylesheets());
        stage.setScene(new Scene(root));

        Rectangle2D bounds = Screen.getScreensForRectangle(
                owner.getX() + owner.getWidth() / 2, owner.getY() + owner.getHeight() / 2, 1, 1)
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        double width = Math.min(620, bounds.getWidth() * 0.86);
        double height = Math.min(700, bounds.getHeight() * 0.86);
        stage.setMinWidth(Math.min(420, width));
        stage.setMinHeight(Math.min(360, height));
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(Math.max(bounds.getMinX(),
                Math.min(owner.getX() + (owner.getWidth() - width) / 2, bounds.getMaxX() - width)));
        stage.setY(Math.max(bounds.getMinY(),
                Math.min(owner.getY() + (owner.getHeight() - height) / 2, bounds.getMaxY() - height)));
        stage.show();
    }

    private static Label paragraph(String text, boolean keepLines) {
        Label label = new Label(keepLines ? text : text.replace("\n", ""));
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("about-paragraph");
        return label;
    }
}
