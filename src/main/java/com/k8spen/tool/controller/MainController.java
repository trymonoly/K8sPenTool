package com.k8spen.tool.controller;

import com.k8spen.tool.view.ResultPane;
import com.k8spen.tool.view.AboutWindow;
import com.k8spen.tool.view.CommandCards;
import com.k8spen.tool.helper.DockerContainerItem;
import com.k8spen.tool.helper.DockerImageItem;
import com.k8spen.tool.helper.PodTableItem;
import com.k8spen.tool.helper.SecretTableItem;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;

public class MainController {

    // ===== 目标配置 =====
    @FXML private TextField targetHost;
    @FXML private ComboBox<String> protocolOpt;
    @FXML private TextField apiPort;
    @FXML private TextField timeoutField;
    @FXML private CheckBox sslSkipVerify;
    @FXML private TextField tokenField;
    @FXML private TabPane mainTabPane;
    @FXML private Label statusLabel;
    @FXML private TextArea logTextArea;

    // ===== 0.信息搜集 =====
    @FXML private CommandCards envCheckCmds;
    @FXML private CommandCards privCheckCmds;
    @FXML private TextField capHexInput;
    @FXML private ResultPane capDecodeOutput;
    @FXML private Label capDecodeNotice;
    @FXML private ResultPane portRefArea;
    @FXML private ResultPane portScanResult;
    @FXML private CommandCards saTokenCmds;

    // ===== 1.初始访问 - APIServer =====
    @FXML private TextField customApiPath;
    @FXML private ComboBox<String> apiMethodOpt;
    @FXML private TextArea kubectlCmdHint;
    @FXML private ResultPane apiServerOutput;
    @FXML private TextField privEscNs;
    @FXML private TextField privEscSaName;

    // ===== 1.初始访问 - Kubelet =====
    @FXML private TextField kubeletNs;
    @FXML private TextField kubeletPod;
    @FXML private TextField kubeletContainer;
    @FXML private TextField kubeletCmd;
    @FXML private ResultPane kubeletOutput;

    // ===== 1.初始访问 - Etcd =====
    @FXML private ComboBox<String> etcdVersionOpt;
    @FXML private TextField etcdPort;
    @FXML private TextField etcdKeyInput;
    @FXML private TextArea etcdCmdHint;
    @FXML private ResultPane etcdOutput;

    // ===== 1.初始访问 - Dashboard =====
    @FXML private TextField dashboardPort;
    @FXML private CheckBox dashboardHttps;
    @FXML private TextArea dashboardOutput;

    // ===== 1.初始访问 - Kubeconfig =====
    @FXML private TextArea kubeconfigContent;
    @FXML private TextArea kubeconfigOutput;

    // ===== 2.执行 - APIServer exec =====
    @FXML private TextField apiExecNs;
    @FXML private TextField apiExecPod;
    @FXML private TextField apiExecContainer;
    @FXML private TextField apiExecCmd;
    @FXML private ResultPane apiExecOutput;
    @FXML private TextField apiExecUsername;
    @FXML private PasswordField apiExecPassword;
    @FXML private TableView<PodTableItem> apiPodTable;
    @FXML private Label apiPodCount;
    @FXML private TableColumn<PodTableItem, String> colNs;
    @FXML private TableColumn<PodTableItem, String> colName;
    @FXML private TableColumn<PodTableItem, String> colStatus;
    @FXML private TableColumn<PodTableItem, String> colNode;
    @FXML private TableColumn<PodTableItem, String> colIP;
    @FXML private TableColumn<PodTableItem, String> colContainers;

    // ===== 2.执行 - Kubelet exec =====
    @FXML private TextField execNamespace;
    @FXML private TextField execPodName;
    @FXML private TextField execContainerName;
    @FXML private TextField execCommand;
    @FXML private ResultPane execOutput;
    @FXML private TableView<PodTableItem> kubeletPodTable;
    @FXML private Label kubeletPodCount;
    @FXML private TableColumn<PodTableItem, String> kColNs;
    @FXML private TableColumn<PodTableItem, String> kColName;
    @FXML private TableColumn<PodTableItem, String> kColStatus;
    @FXML private TableColumn<PodTableItem, String> kColNode;
    @FXML private TableColumn<PodTableItem, String> kColIP;
    @FXML private TableColumn<PodTableItem, String> kColContainers;

    // ===== 2.执行 - 后门Pod =====
    @FXML private TextField backdoorImage;
    @FXML private TextField backdoorMountPath;
    @FXML private TextField backdoorNodeName;
    @FXML private TextField backdoorLhost;
    @FXML private TextField backdoorLport;
    @FXML private TextField backdoorPodName;
    @FXML private TextArea sshPubKeyInput;
    @FXML private TextArea backdoorYamlOutput;

    // ===== 2.执行 - 服务账号 =====
    @FXML private TextField saTokenInput;
    @FXML private ResultPane saCheckOutput;

    // ===== 2.执行 - RBAC =====
    @FXML private TextArea rbacCheckCmds;
    @FXML private TextArea rbacOutput;

    // ===== 3.权限维持 =====
    @FXML private TextField persistSANamespace;
    @FXML private TextField persistSAName;
    @FXML private TextField persistSARoleName;
    @FXML private TextArea persistSAOutput;

    @FXML private TextField persistCronNs;
    @FXML private TextField persistCronName;
    @FXML private TextField persistCronImage;
    @FXML private TextField persistCronSchedule;
    @FXML private TextField persistCronCmd;
    @FXML private TextArea persistCronOutput;

    @FXML private TextField persistDsNs;
    @FXML private TextField persistDsName;
    @FXML private TextField persistDsImage;
    @FXML private TextField persistDsMountPath;
    @FXML private TextField persistDsCmd;
    @FXML private TextArea persistDsOutput;

    @FXML private TextField persistKcServer;
    @FXML private TextField persistKcCluster;
    @FXML private TextArea persistKcToken;
    @FXML private TextArea persistKcOutput;

    @FXML private TextField persistHostLhost;
    @FXML private TextField persistHostLport;
    @FXML private TextArea persistHostOutput;

    @FXML private ComboBox<String> persistConnMode;
    @FXML private TextField persistUsername;
    @FXML private PasswordField persistPassword;

    // ===== 权限提升 =====
    @FXML private TextArea escapeCheckCmds;
    @FXML private TextField escapePrivLhost;
    @FXML private TextField escapePrivLport;
    @FXML private TextArea escapePrivOutput;
    @FXML private ComboBox<String> escapeMountType;
    @FXML private TextField escapeMountLhost;
    @FXML private TextField escapeMountLport;
    @FXML private TextArea escapeMountOutput;
    @FXML private TextArea escapeKernelCmds;

    // ===== 横向移动 =====
    @FXML private TextField credNs;
    @FXML private ComboBox<String> credTypeFilter;
    @FXML private TableView<SecretTableItem> credSecretTable;
    @FXML private TableColumn<SecretTableItem, String> credColNs;
    @FXML private TableColumn<SecretTableItem, String> credColName;
    @FXML private TableColumn<SecretTableItem, String> credColType;
    @FXML private TableColumn<SecretTableItem, String> credColAge;
    @FXML private TextArea credOutput;
    @FXML private ResultPane lateralOutput;
    @FXML private TextField lateralTaintNode;
    @FXML private TextField lateralTaintImage;
    @FXML private CheckBox lateralTaintHostMount;
    @FXML private ResultPane lateralTaintOutput;

    // ===== kubectl操作 =====
    @FXML private ComboBox<String> kubectlConnMode;
    @FXML private TextField kubectlUsername;
    @FXML private PasswordField kubectlPassword;
    @FXML private TextField kubectlExtraArgs;
    @FXML private TextField kubectlCustomCmd;
    @FXML private ResultPane kubectlOutput;

    // ===== 2.执行 - 反弹Shell =====
    @FXML private TextField revShellLhost;
    @FXML private TextField revShellLport;
    @FXML private ComboBox<String> revShellType;
    @FXML private TextArea revShellOutput;

    // ===== 7.Docker API - 连接与概览 =====
    @FXML private ResultPane dockerInfoOutput;
    @FXML private CommandCards dockerReference;

    // ===== 7.Docker API - 容器管理 =====
    @FXML private TableView<DockerContainerItem> dockerContainerTable;
    @FXML private TableColumn<DockerContainerItem, String> dockerColContainerId;
    @FXML private TableColumn<DockerContainerItem, String> dockerColContainerName;
    @FXML private TableColumn<DockerContainerItem, String> dockerColContainerImage;
    @FXML private TableColumn<DockerContainerItem, String> dockerColContainerState;
    @FXML private TableColumn<DockerContainerItem, String> dockerColContainerStatus;
    @FXML private Label dockerContainerCount;
    @FXML private ResultPane dockerContainerOutput;
    @FXML private ComboBox<String> dockerCreateImage;
    @FXML private TextField dockerCreateName;
    @FXML private TextField dockerCreateHostDir;
    @FXML private TextField dockerCreateContainerDir;
    @FXML private CheckBox dockerCreatePrivileged;

    // ===== 7.Docker API - 命令执行 =====
    @FXML private ComboBox<DockerContainerItem> dockerExecContainer;
    @FXML private ComboBox<String> dockerShellOpt;
    @FXML private ComboBox<String> dockerExecCmd;
    @FXML private ResultPane dockerExecOutput;

    // ===== 7.Docker API - 镜像管理 =====
    @FXML private TableView<DockerImageItem> dockerImageTable;
    @FXML private TableColumn<DockerImageItem, String> dockerColImageId;
    @FXML private TableColumn<DockerImageItem, String> dockerColImageTag;
    @FXML private TableColumn<DockerImageItem, String> dockerColImageSize;
    @FXML private TableColumn<DockerImageItem, String> dockerColImageCreated;
    @FXML private Label dockerImageCount;

    // ===== 7.Docker API - 容器逃逸 =====
    @FXML private ComboBox<DockerContainerItem> dockerEscapeContainer;
    @FXML private ComboBox<String> dockerEscapeShell;
    @FXML private ComboBox<String> dockerEscapeOs;
    @FXML private TextField dockerEscapeMount;
    @FXML private TextField dockerEscapeHostRoot;
    @FXML private TextField dockerEscapeLhost;
    @FXML private TextField dockerEscapeLport;
    @FXML private ResultPane dockerEscapeCheck;
    @FXML private ResultPane dockerEscapeOutput;

    // ===== 默认SSH密钥对 =====
    private static final String DEFAULT_SSH_PUBKEY = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDk4MhTlzMjBPTrN199hfxwFywjuqwv0d7PTjrC7Al8q0C/LIyZvVnqGmcTNKTeer9ch9ST2SmPGBni7EuvPEzAXB9z4deDRy1d8Fn8sDqC2HJ/xiwKNWjmmCxmbngUHrXBSAC8dGYrS3yZvdvKY6IUpesEnDh7duepf1Y3l7lEwSjK469zD07RhnhbAAIYbBgV5PY9F1N7AjzQbXpSRcw5FykbDMKKr0aulE4G6y0EqH9X3ToXPKWJNrg7WMyY6+HM0IXAfHp8RCm3pR2y973jH7ATuWVJWsCl311SHd2ozKLopvTpOfJJp35qQir967KKKUPAirTQD8SaAXMZFi+7 root@localhost.localdomain";
    private static final String DEFAULT_SSH_PRIVKEY =
            "-----BEGIN OPENSSH PRIVATE KEY-----\n"
            + "b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAABFwAAAAdzc2gtcn\n"
            + "NhAAAAAwEAAQAAAQEA5ODIU5czIwT06zdffYX8cBcsI7qsL9Hez046wuwJfKtAvyyMmb1Z\n"
            + "6hpnEzSk3nq/XIfUk9kpjxgZ4uxLrzxMwFwfc+HXg0ctXfBZ/LA6gthyf8YsCjVo5pgsZm\n"
            + "54FB61wUgAvHRmK0t8mb3bymOiFKXrBJw4e3bnqX9WN5e5RMEoyuOvcw9O0YZ4WwACGGwY\n"
            + "FeT2PRdTewI80G16UkXMORcpGwzCiq9GrpROBustBKh/V906FzyliTa4O1jMmOvhzNCFwH\n"
            + "x6fEQpt6Udsve94x+wE7llSVrApd9dUh3dqMyi6Kb06TnySad+akIq/euyiilDwIq00A/E\n"
            + "mgFzGRYvuwAAA9DjxiUO48YlDgAAAAdzc2gtcnNhAAABAQDk4MhTlzMjBPTrN199hfxwFy\n"
            + "wjuqwv0d7PTjrC7Al8q0C/LIyZvVnqGmcTNKTeer9ch9ST2SmPGBni7EuvPEzAXB9z4deD\n"
            + "Ry1d8Fn8sDqC2HJ/xiwKNWjmmCxmbngUHrXBSAC8dGYrS3yZvdvKY6IUpesEnDh7duepf1\n"
            + "Y3l7lEwSjK469zD07RhnhbAAIYbBgV5PY9F1N7AjzQbXpSRcw5FykbDMKKr0aulE4G6y0E\n"
            + "qH9X3ToXPKWJNrg7WMyY6+HM0IXAfHp8RCm3pR2y973jH7ATuWVJWsCl311SHd2ozKLopv\n"
            + "TpOfJJp35qQir967KKKUPAirTQD8SaAXMZFi+7AAAAAwEAAQAAAQB+vouw3pYO2nvWlb9n\n"
            + "f38fg3WKA6G+iXXdTvDzaEqIoz0joMPrjxPvs9dIp2p1WXwG/aEWjreY6jvLkhcHX1kRXP\n"
            + "J99Z6msA/LaYIrkFuWgc5GO7O/o3wH1lUgFCSi366+7eSad8rsRs0lRiIkna/vx0GyN+B/\n"
            + "XoVDM6TG/Fo4W8Ks0Nea0e8w/JkBaOSlG7poLzIardkT5uLjvNU4gRlglzE/yLoNQE+LWl\n"
            + "hduoVL6G8jfbQ5LTa2pc/nkJ3HoZUT4KtaK2QpXlbGUVmr24qwi3Ssf1pA81dp6rIpLsvN\n"
            + "ka3bWds8eIgX6TQAhGYyFyNNmzDDdgxUIthxRoyGEZuhAAAAgEFTMvHx6nYJesUtAlMrL7\n"
            + "Rjr+tIt/qVN5UQlG+OurXLbzhq5YzMNamVKu8/28chLqCKhM8QGSIyuIUc04vSbW32qZRX\n"
            + "vbouJzc/UlyckkoDDVw227S4do0flgEXUsgVemY9ivtg2avde0WypaYLy1Qz76YuuQre7R\n"
            + "84UC6qAgECAAAAgQD8ThbIbIfBAiHsQSMhUKQ+molaFwq0FW8B4wMWuJiVbKNHGfy3TxHX\n"
            + "6B4M0ksBYN3RCzL/JWLvpsjeXV4UOcCZ6Xu7AlbiDPPZFLf2aMtDPYGKJLgBDCCj3a6URH\n"
            + "H8pplnyxSy7trEVGnZsE7RsbVmAvxHVe8s873VneNGDsLA3QAAAIEA6DrdLgvoZ2NbVb5f\n"
            + "3lb43QmjQurwIGoPyETbjhViti4aUEci4QImwJpRxiDwZO20UAgflLTWlE8VZ8gjB+Grr1\n"
            + "ghpH2ZNNtOK2pabrkb3s/I8U/XVLfs4kEF5ao0aD5J8tHmLkubKXC7NBPe7ghfV8CpV/UR\n"
            + "3kw6mFy0+b1znXcAAAAacm9vdEBsb2NhbGhvc3QubG9jYWxkb21haW4B\n"
            + "-----END OPENSSH PRIVATE KEY-----";

    // ===== 子Handler =====
    private ControllerContext ctx;
    private InfoHandler infoHandler;
    private AccessHandler accessHandler;
    private ExecHandler execHandler;
    private PersistHandler persistHandler;
    private EscapeHandler escapeHandler;
    private LateralHandler lateralHandler;
    private KubectlHandler kubectlHandler;
    private DockerHandler dockerHandler;
    private DockerExecHandler dockerExecHandler;

    // ========================================================================
    //  初始化
    // ========================================================================
    @FXML
    public void initialize() {
        // 协议下拉框 + 目标地址自动解析（粘贴完整URL时回填协议/端口）
        protocolOpt.getItems().addAll("https", "http");
        protocolOpt.setValue("https");
        targetHost.textProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;
            String h = newV.trim();
            String lower = h.toLowerCase(Locale.ROOT);
            boolean hasScheme = lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("tcp://");
            boolean hasPort = h.matches("(?:[^/:]+|\\[[^]]+\\]):\\d+(?:/.*)?");
            if (hasScheme || hasPort) {
                if (hasScheme) {
                    protocolOpt.setValue(lower.startsWith("https://") ? "https" : "http");
                    h = h.substring(h.indexOf("://") + 3);
                }
                int slash = h.indexOf('/');
                if (slash > 0) h = h.substring(0, slash);
                int colon = h.lastIndexOf(':');
                if (colon > 0) {
                    String port = h.substring(colon + 1);
                    if (port.matches("\\d+")) { apiPort.setText(port); h = h.substring(0, colon); }
                }
                String finalH = h;
                Platform.runLater(() -> {
                    if (newV.equals(targetHost.getText())) targetHost.setText(finalH);
                });
            }
        });

        ctx = new ControllerContext(targetHost, protocolOpt, apiPort, timeoutField, tokenField, logTextArea, statusLabel, kubeconfigContent);

        // 0.信息搜集
        infoHandler = new InfoHandler(ctx, envCheckCmds, privCheckCmds, capHexInput, capDecodeOutput, capDecodeNotice, portRefArea, portScanResult, saTokenCmds);
        infoHandler.init();

        // 1.初始访问
        apiMethodOpt.getItems().addAll("GET", "POST", "PUT", "DELETE");
        apiMethodOpt.setValue("GET");
        etcdVersionOpt.getItems().addAll("v2", "v3");
        etcdVersionOpt.setValue("v3");
        accessHandler = new AccessHandler(ctx, customApiPath, apiMethodOpt, kubectlCmdHint, apiServerOutput,
                privEscNs, privEscSaName,
                kubeletNs, kubeletPod, kubeletContainer, kubeletCmd, kubeletOutput,
                etcdVersionOpt, etcdPort, etcdKeyInput, etcdCmdHint, etcdOutput,
                dashboardPort, dashboardHttps, dashboardOutput,
                kubeconfigContent, kubeconfigOutput, mainTabPane,
                DEFAULT_SSH_PUBKEY, DEFAULT_SSH_PRIVKEY);

        // 2.执行
        revShellType.getItems().addAll("Bash -i", "Bash TCP", "Python", "Perl", "NC -e", "NC mkfifo", "PHP", "Ruby", "Lua", "Curl");
        revShellType.setValue("Bash -i");
        sshPubKeyInput.setText(DEFAULT_SSH_PUBKEY);
        execHandler = new ExecHandler(ctx,
                apiExecNs, apiExecPod, apiExecContainer, apiExecCmd, apiExecUsername, apiExecPassword,
                apiExecOutput, apiPodTable, apiPodCount,
                colNs, colName, colStatus, colNode, colIP, colContainers,
                execNamespace, execPodName, execContainerName, execCommand, execOutput,
                kubeletPodTable, kubeletPodCount, kColNs, kColName, kColStatus, kColNode, kColIP, kColContainers,
                backdoorImage, backdoorMountPath, backdoorNodeName,
                backdoorLhost, backdoorLport, backdoorPodName,
                sshPubKeyInput, backdoorYamlOutput,
                saTokenInput, saCheckOutput,
                rbacCheckCmds, rbacOutput,
                revShellLhost, revShellLport, revShellType, revShellOutput,
                DEFAULT_SSH_PUBKEY, DEFAULT_SSH_PRIVKEY);
        execHandler.init();

        // 3.权限维持
        persistConnMode.getItems().addAll("\u76ee\u6807\u914d\u7f6e(Token+Server)", "Kubeconfig\u6587\u4ef6", "\u76f4\u63a5\u6267\u884c(\u65e0\u8ba4\u8bc1)");
        persistConnMode.setValue("\u76ee\u6807\u914d\u7f6e(Token+Server)");
        persistHandler = new PersistHandler(ctx, execHandler,
                persistSANamespace, persistSAName, persistSARoleName, persistSAOutput,
                persistCronNs, persistCronName, persistCronImage, persistCronSchedule, persistCronCmd, persistCronOutput,
                persistDsNs, persistDsName, persistDsImage, persistDsMountPath, persistDsCmd, persistDsOutput,
                persistKcServer, persistKcCluster, persistKcToken, persistKcOutput,
                persistHostLhost, persistHostLport, persistHostOutput,
                persistConnMode, persistUsername, persistPassword, kubeconfigContent);

        // 4.权限提升
        escapeHandler = new EscapeHandler(ctx, escapeCheckCmds,
                escapePrivLhost, escapePrivLport, escapePrivOutput,
                escapeMountType, escapeMountLhost, escapeMountLport, escapeMountOutput,
                escapeKernelCmds);
        escapeHandler.init();

        // 5.横向移动
        lateralHandler = new LateralHandler(ctx,
                credNs, credTypeFilter, credSecretTable,
                credColNs, credColName, credColType, credColAge, credOutput,
                lateralOutput,
                lateralTaintNode, lateralTaintImage, lateralTaintHostMount, lateralTaintOutput);
        lateralHandler.init();

        // 6.kubectl
        kubectlConnMode.getItems().addAll("\u76ee\u6807\u914d\u7f6e(Token+Server)", "Kubeconfig\u6587\u4ef6", "\u76f4\u63a5\u6267\u884c(\u65e0\u8ba4\u8bc1)");
        kubectlConnMode.setValue("\u76ee\u6807\u914d\u7f6e(Token+Server)");
        kubectlHandler = new KubectlHandler(ctx, kubectlConnMode, kubectlUsername, kubectlPassword,
                kubectlExtraArgs, kubectlCustomCmd, kubectlOutput,
                kubeconfigContent, backdoorYamlOutput, backdoorPodName);
        kubectlHandler.init();

        // 7.Docker API
        dockerHandler = new DockerHandler(ctx,
                dockerInfoOutput, dockerReference,
                dockerContainerTable,
                dockerColContainerId, dockerColContainerName, dockerColContainerImage,
                dockerColContainerState, dockerColContainerStatus,
                dockerContainerCount, dockerContainerOutput,
                dockerCreateImage, dockerCreateName, dockerCreateHostDir, dockerCreateContainerDir,
                dockerCreatePrivileged,
                dockerImageTable, dockerColImageId, dockerColImageTag, dockerColImageSize, dockerColImageCreated,
                dockerImageCount);
        dockerHandler.init();
        dockerExecHandler = new DockerExecHandler(ctx, dockerHandler,
                dockerExecContainer, dockerShellOpt, dockerExecCmd, dockerExecOutput,
                dockerEscapeContainer, dockerEscapeShell, dockerEscapeOs,
                dockerEscapeMount, dockerEscapeHostRoot, dockerEscapeLhost, dockerEscapeLport,
                dockerEscapeCheck, dockerEscapeOutput);
        dockerExecHandler.init(DEFAULT_SSH_PUBKEY);

        log("[*] K8s\u7efc\u5408\u6e17\u900f\u6d4b\u8bd5\u5de5\u5177\u521d\u59cb\u5316\u5b8c\u6210");
    }

    // ========================================================================
    //  通用工具方法
    // ========================================================================
    private void log(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());
        Platform.runLater(() -> logTextArea.appendText("[" + ts + "] " + msg + "\n"));
    }

    @FXML
    void decodeTokenBase64(ActionEvent event) {
        String raw = tokenField.getText().trim();
        if (raw.isEmpty()) { log("[-] Token\u5b57\u6bb5\u4e3a\u7a7a"); return; }
        try {
            String cleaned = raw.replace("\\u003d", "=");
            byte[] decoded = Base64.getDecoder().decode(cleaned);
            tokenField.setText(new String(decoded, "UTF-8").trim());
            log("[+] Token\u5df2Base64\u89e3\u7801\u5e76\u586b\u5165");
        } catch (Exception e) { log("[-] Base64\u89e3\u7801\u5931\u8d25: " + e.getMessage()); }
    }

    // ========================================================================
    //  0.信息搜集 - 委托 InfoHandler
    // ========================================================================
    @FXML void copyEnvCmds(ActionEvent e)      { infoHandler.copyEnvCmds(); }
    @FXML void showEnvGuidance(ActionEvent e)    { infoHandler.showEnvGuidance(); }
    @FXML void copyPrivCmds(ActionEvent e)      { infoHandler.copyPrivCmds(); }
    @FXML void decodeCapabilities(ActionEvent e) { infoHandler.decodeCapabilities(); }
    @FXML void quickPortScan(ActionEvent e)     { infoHandler.quickPortScan(); }
    @FXML void copySATokenCmds(ActionEvent e)   { infoHandler.copySATokenCmds(); }

    // ========================================================================
    //  1.初始访问 - 委托 AccessHandler
    // ========================================================================
    @FXML void checkInsecurePort(ActionEvent e)    { accessHandler.checkInsecurePort(); }
    @FXML void checkSecurePort(ActionEvent e)      { accessHandler.checkSecurePort(); }
    @FXML void getNodes(ActionEvent e)             { accessHandler.getNodes(); }
    @FXML void getPods(ActionEvent e)              { accessHandler.getPods(); }
    @FXML void getSecrets(ActionEvent e)           { accessHandler.getSecrets(); }
    @FXML void checkAuth(ActionEvent e)            { accessHandler.checkAuth(); }
    @FXML void sendCustomApi(ActionEvent e)        { accessHandler.sendCustomApi(); }
    @FXML void mintAdminToken(ActionEvent e)       { accessHandler.mintAdminToken(); }
    @FXML void checkKubelet(ActionEvent e)          { accessHandler.checkKubelet(); }
    @FXML void kubeletListPods(ActionEvent e)      { accessHandler.kubeletListPods(); }
    @FXML void kubeletExecCmd(ActionEvent e)       { accessHandler.kubeletExecCmd(); }
    @FXML void kubeletInjectSSHKey(ActionEvent e)  { accessHandler.kubeletInjectSSHKey(); }
    @FXML void checkEtcd(ActionEvent e)            { accessHandler.checkEtcd(); }
    @FXML void etcdGetKeys(ActionEvent e)          { accessHandler.etcdGetKeys(); }
    @FXML void etcdSearchSecrets(ActionEvent e)    { accessHandler.etcdSearchSecrets(); }
    @FXML void etcdReadKey(ActionEvent e)          { accessHandler.etcdReadKey(); }
    @FXML void checkDashboard(ActionEvent e)       { accessHandler.checkDashboard(); }
    @FXML void checkDashboardHttps(ActionEvent e)   { accessHandler.checkDashboardHttps(); }
    @FXML void openDashboardInBrowser(ActionEvent e) { accessHandler.openDashboardInBrowser(); }
    @FXML void loadKubeconfig(ActionEvent e)       { accessHandler.loadKubeconfig(); }
    @FXML void parseKubeconfig(ActionEvent e)      { accessHandler.parseKubeconfig(); }
    @FXML void genKubectlCmd(ActionEvent e)        { accessHandler.genKubectlCmd(); }

    // ========================================================================
    //  2.执行 - 委托 ExecHandler
    // ========================================================================
    @FXML void apiListPods(ActionEvent e)          { execHandler.apiListPods(); }
    @FXML void apiExecInPod(ActionEvent e)         { execHandler.apiExecInPod(); }
    @FXML void apiEnumSATokens(ActionEvent e)      { execHandler.apiEnumSATokens(); }
    @FXML void listPodsForExec(ActionEvent e)      { execHandler.listPodsForExec(); }
    @FXML void execInPod(ActionEvent e)            { execHandler.execInPod(); }
    @FXML void execRevShellInPod(ActionEvent e)    { execHandler.execRevShellInPod(); }
    @FXML void enumSATokensViaExec(ActionEvent e)  { execHandler.enumSATokensViaExec(); }
    @FXML void generateBackdoorYaml(ActionEvent e) { execHandler.generateBackdoorYaml(); }
    @FXML void copyBackdoorYaml(ActionEvent e)     { execHandler.copyBackdoorYaml(); }
    @FXML void generateBackdoorCmd(ActionEvent e)  { execHandler.generateBackdoorCmd(); }
    @FXML void generateSshCmd(ActionEvent e)       { execHandler.generateSshCmd(); }
    @FXML void copySaUtilCmds2(ActionEvent e)      { execHandler.copySaUtilCmds2(); }
    @FXML void showSaUtilArticle(ActionEvent e)    { execHandler.showSaUtilArticle(); }
    @FXML void checkSaPermissions(ActionEvent e)   { execHandler.checkSaPermissions(); }
    @FXML void copyRbacCmds(ActionEvent e)         { execHandler.copyRbacCmds(); }
    @FXML void checkRbacStatus(ActionEvent e)      { execHandler.checkRbacStatus(); }
    @FXML void generateRevShell(ActionEvent e)     { execHandler.generateRevShell(); }
    @FXML void copyRevShell(ActionEvent e)         { execHandler.copyRevShell(); }

    // ========================================================================
    //  3.权限维持 - 委托 PersistHandler
    // ========================================================================
    @FXML void persistGenAdminSA(ActionEvent e)     { persistHandler.persistGenAdminSA(); }
    @FXML void persistApplyAdminSA(ActionEvent e)   { persistHandler.persistApplyAdminSA(); }
    @FXML void persistGetSAToken(ActionEvent e)     { persistHandler.persistGetSAToken(); }
    @FXML void persistCopyOutput(ActionEvent e)     { persistHandler.persistCopyOutput(); }
    @FXML void persistGenCronJob(ActionEvent e)     { persistHandler.persistGenCronJob(); }
    @FXML void persistApplyCronJob(ActionEvent e)   { persistHandler.persistApplyCronJob(); }
    @FXML void persistCopyCronOutput(ActionEvent e) { persistHandler.persistCopyCronOutput(); }
    @FXML void persistGenDaemonSet(ActionEvent e)   { persistHandler.persistGenDaemonSet(); }
    @FXML void persistApplyDaemonSet(ActionEvent e) { persistHandler.persistApplyDaemonSet(); }
    @FXML void persistCopyDsOutput(ActionEvent e)   { persistHandler.persistCopyDsOutput(); }
    @FXML void persistGenKubeconfig(ActionEvent e)  { persistHandler.persistGenKubeconfig(); }
    @FXML void persistFillFromTarget(ActionEvent e) { persistHandler.persistFillFromTarget(); }
    @FXML void persistCopyKcOutput(ActionEvent e)   { persistHandler.persistCopyKcOutput(); }
    @FXML void persistGenHostPersist(ActionEvent e) { persistHandler.persistGenHostPersist(); }
    @FXML void persistCopyHostOutput(ActionEvent e) { persistHandler.persistCopyHostOutput(); }

    // ========================================================================
    //  4.权限提升 - 委托 EscapeHandler
    // ========================================================================
    @FXML void escapeCopyCheck(ActionEvent e)  { escapeHandler.escapeCopyCheck(); }
    @FXML void escapeGenPriv(ActionEvent e)    { escapeHandler.escapeGenPriv(); }
    @FXML void escapeCopyPriv(ActionEvent e)   { escapeHandler.escapeCopyPriv(); }
    @FXML void escapeGenMount(ActionEvent e)   { escapeHandler.escapeGenMount(); }
    @FXML void escapeCopyMount(ActionEvent e)  { escapeHandler.escapeCopyMount(); }
    @FXML void escapeCopyKernel(ActionEvent e) { escapeHandler.escapeCopyKernel(); }

    // ========================================================================
    //  5.横向移动 - 委托 LateralHandler
    // ========================================================================
    @FXML void credListSecrets(ActionEvent e)       { lateralHandler.credListSecrets(); }
    @FXML void credViewSecret(ActionEvent e)        { lateralHandler.credViewSecret(); }
    @FXML void credCopyOutput(ActionEvent e)        { lateralHandler.credCopyOutput(); }
    @FXML void lateralListServices(ActionEvent e)   { lateralHandler.lateralListServices(); }
    @FXML void lateralListEndpoints(ActionEvent e)  { lateralHandler.lateralListEndpoints(); }
    @FXML void lateralListNodes(ActionEvent e)      { lateralHandler.lateralListNodes(); }
    @FXML void lateralListNetPol(ActionEvent e)     { lateralHandler.lateralListNetPol(); }
    @FXML void lateralCopyOutput(ActionEvent e)     { lateralHandler.lateralCopyOutput(); }
    @FXML void lateralShowTaints(ActionEvent e)     { lateralHandler.lateralShowTaints(); }
    @FXML void lateralGenTaintPod(ActionEvent e)    { lateralHandler.lateralGenTaintPod(); }
    @FXML void lateralCopyTaintOutput(ActionEvent e){ lateralHandler.lateralCopyTaintOutput(); }

    // ========================================================================
    //  6.kubectl操作 - 委托 KubectlHandler
    // ========================================================================
    @FXML void kubectlGetNodes(ActionEvent e)       { kubectlHandler.kubectlGetNodes(); }
    @FXML void kubectlGetPods(ActionEvent e)        { kubectlHandler.kubectlGetPods(); }
    @FXML void kubectlGetAllPods(ActionEvent e)     { kubectlHandler.kubectlGetAllPods(); }
    @FXML void kubectlGetImages(ActionEvent e)      { kubectlHandler.kubectlGetImages(); }
    @FXML void kubectlGetServices(ActionEvent e)    { kubectlHandler.kubectlGetServices(); }
    @FXML void kubectlGetSecrets(ActionEvent e)     { kubectlHandler.kubectlGetSecrets(); }
    @FXML void kubectlGetDeployments(ActionEvent e) { kubectlHandler.kubectlGetDeployments(); }
    @FXML void kubectlClusterInfo(ActionEvent e)    { kubectlHandler.kubectlClusterInfo(); }
    @FXML void kubectlAuthCanI(ActionEvent e)       { kubectlHandler.kubectlAuthCanI(); }
    @FXML void kubectlGetSA(ActionEvent e)          { kubectlHandler.kubectlGetSA(); }
    @FXML void kubectlGetCRB(ActionEvent e)         { kubectlHandler.kubectlGetCRB(); }
    @FXML void kubectlApplyBackdoor(ActionEvent e)  { kubectlHandler.kubectlApplyBackdoor(); }
    @FXML void kubectlDeletePod(ActionEvent e)      { kubectlHandler.kubectlDeletePod(); }
    @FXML void kubectlRunCustom(ActionEvent e)      { kubectlHandler.kubectlRunCustom(); }
    @FXML void kubectlClearOutput(ActionEvent e)    { kubectlHandler.kubectlClearOutput(); }

    // ========================================================================
    //  7.Docker API - 委托 DockerHandler / DockerExecHandler
    // ========================================================================
    @FXML void dockerConnect(ActionEvent e)           { dockerHandler.connect(); }
    @FXML void dockerRefreshContainers(ActionEvent e) { dockerHandler.refreshContainers(); }
    @FXML void dockerContainerStart(ActionEvent e)    { dockerHandler.containerStart(); }
    @FXML void dockerContainerStop(ActionEvent e)     { dockerHandler.containerStop(); }
    @FXML void dockerContainerDetail(ActionEvent e)   { dockerHandler.containerDetail(); }
    @FXML void dockerContainerDelete(ActionEvent e)   { dockerHandler.containerDelete(); }
    @FXML void dockerCreateContainer(ActionEvent e)   { dockerHandler.createContainer(); }
    @FXML void dockerRefreshImages(ActionEvent e)     { dockerHandler.refreshImages(); }
    @FXML void dockerExec(ActionEvent e)              { dockerExecHandler.execCommand(); }
    @FXML void dockerClearExec(ActionEvent e)         { dockerExecHandler.clearExecOutput(); }
    @FXML void dockerEscapeInspect(ActionEvent e)     { dockerExecHandler.escapeInspect(); }
    @FXML void dockerEscapeRun(ActionEvent e)         { dockerExecHandler.escapeRun(); }

    // ========================================================================
    //  日志
    // ========================================================================
    @FXML void clearLog(ActionEvent e) { logTextArea.clear(); log("[*] \u65e5\u5fd7\u5df2\u6e05\u7a7a"); }

    // ========================================================================
    //  关于
    // ========================================================================
    @FXML
    void showAbout(ActionEvent e) {
        AboutWindow.show(mainTabPane.getScene().getWindow());
    }
}
