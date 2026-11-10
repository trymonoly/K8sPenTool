package com.k8spen.tool;

import com.k8spen.tool.utils.K8sHttpUtil;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.embed.swing.SwingFXUtils;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

public class Main extends Application {

    private static final PseudoClass COMPACT = PseudoClass.getPseudoClass("compact");

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/gui.fxml"));
        primaryStage.setTitle("K8s\u7efc\u5408\u6e17\u900f\u6d4b\u8bd5\u5de5\u5177 v2.1");

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.getIcons().add(buildAppIcon());
        ChangeListener<Number> resizeListener = (obs, previous, current) ->
                root.pseudoClassStateChanged(COMPACT,
                        primaryStage.getWidth() < 1100 || primaryStage.getHeight() < 720);
        primaryStage.widthProperty().addListener(resizeListener);
        primaryStage.heightProperty().addListener(resizeListener);
        fitToScreen(primaryStage, screenBounds);
        primaryStage.show();
        K8sHttpUtil.disableSslVerification();
    }

    static void fitToScreen(Stage stage, Rectangle2D screenBounds) {
        // JavaFX 使用逻辑像素，完整窗口尺寸包含标题栏并为任务栏保留空间。
        double width = Math.min(1180, screenBounds.getWidth() * 0.86);
        double height = Math.min(900, screenBounds.getHeight() * 0.94);
        stage.setMinWidth(Math.min(800, width));
        stage.setMinHeight(Math.min(480, height));
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(screenBounds.getMinX() + (screenBounds.getWidth() - width) / 2);
        stage.setY(screenBounds.getMinY() + (screenBounds.getHeight() - height) / 2);
    }

    private Image buildAppIcon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(50, 108, 229), 64, 64, new Color(30, 60, 150)));
            g.fill(new RoundRectangle2D.Double(0, 0, 64, 64, 16, 16));

            g.setColor(new Color(255, 255, 255, 220));
            g.setStroke(new BasicStroke(3f));
            g.drawOval(16, 16, 32, 32);
            g.fillOval(28, 28, 8, 8);
            for (int i = 0; i < 7; i++) {
                double angle = Math.toRadians(i * 360.0 / 7 - 90);
                int x1 = 32 + (int) (14 * Math.cos(angle));
                int y1 = 32 + (int) (14 * Math.sin(angle));
                g.drawLine(32, 32, x1, y1);
            }

            g.setColor(new Color(255, 255, 255, 100));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(3, 3, 58, 58, 14, 14);
        } finally {
            g.dispose();
        }
        return SwingFXUtils.toFXImage(image, null);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
