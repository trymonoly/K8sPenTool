module com.k8spen.tool {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires java.desktop;
    requires com.google.gson;

    opens com.k8spen.tool to javafx.graphics;
    opens com.k8spen.tool.controller to javafx.fxml;
    opens com.k8spen.tool.view to javafx.fxml;
    exports com.k8spen.tool;
}
