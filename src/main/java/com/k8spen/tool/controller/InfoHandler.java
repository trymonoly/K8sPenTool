package com.k8spen.tool.controller;

import com.k8spen.tool.utils.K8sHttpUtil;
import com.k8spen.tool.view.CommandCards;
import com.k8spen.tool.view.ResultPane;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 0.信息搜集 — 环境辨别、特权检测、端口扫描、SA Token、Capabilities解码
 */
public class InfoHandler {

    private final ControllerContext ctx;

    // FXML controls
    private final CommandCards envCheckCmds;
    private final CommandCards privCheckCmds;
    private final TextField capHexInput;
    private final ResultPane capDecodeOutput;
    private final Label capDecodeNotice;
    private final ResultPane portRefArea;
    private final ResultPane portScanResult;
    private final CommandCards saTokenCmds;

    private static final String[] CAP_NAMES = {
            "CAP_CHOWN", "CAP_DAC_OVERRIDE", "CAP_DAC_READ_SEARCH", "CAP_FOWNER",
            "CAP_FSETID", "CAP_KILL", "CAP_SETGID", "CAP_SETUID",
            "CAP_SETPCAP", "CAP_LINUX_IMMUTABLE", "CAP_NET_BIND_SERVICE", "CAP_NET_BROADCAST",
            "CAP_NET_ADMIN", "CAP_NET_RAW", "CAP_IPC_LOCK", "CAP_IPC_OWNER",
            "CAP_SYS_MODULE", "CAP_SYS_RAWIO", "CAP_SYS_CHROOT", "CAP_SYS_PTRACE",
            "CAP_SYS_PACCT", "CAP_SYS_ADMIN", "CAP_SYS_BOOT", "CAP_SYS_NICE",
            "CAP_SYS_RESOURCE", "CAP_SYS_TIME", "CAP_SYS_TTY_CONFIG", "CAP_MKNOD",
            "CAP_LEASE", "CAP_AUDIT_WRITE", "CAP_AUDIT_CONTROL", "CAP_SETFCAP",
            "CAP_MAC_OVERRIDE", "CAP_MAC_ADMIN", "CAP_SYSLOG", "CAP_WAKE_ALARM",
            "CAP_BLOCK_SUSPEND", "CAP_AUDIT_READ", "CAP_PERFMON", "CAP_BPF",
            "CAP_CHECKPOINT_RESTORE"
    };

    private static final int[][] K8S_PORTS = {
            {6443, 0}, {8080, 1}, {8081, 1}, {10250, 0}, {10255, 1},
            {4149, 1}, {30000, 0}, {2375, 1}, {2379, 0}, {2380, 1},
            {10252, 1}, {10256, 1}, {10251, 1}, {6781, 1}, {6782, 1}, {6783, 1}
    };
    private static final String[] K8S_PORT_NAMES = {
            "kube-apiserver(secure)", "kube-apiserver(insecure)", "kubectl-proxy",
            "kubelet(HTTPS)", "kubelet(readonly)", "kubelet(cAdvisor)",
            "dashboard(NodePort)", "docker-api", "etcd(client)", "etcd(peer)",
            "controller-manager", "kube-proxy(health)", "kube-scheduler",
            "weave", "weave", "weave"
    };

    public InfoHandler(ControllerContext ctx,
                       CommandCards envCheckCmds, CommandCards privCheckCmds,
                       TextField capHexInput, ResultPane capDecodeOutput, Label capDecodeNotice,
                       ResultPane portRefArea, ResultPane portScanResult,
                       CommandCards saTokenCmds) {
        this.ctx = ctx;
        this.envCheckCmds = envCheckCmds;
        this.privCheckCmds = privCheckCmds;
        this.capHexInput = capHexInput;
        this.capDecodeOutput = capDecodeOutput;
        this.capDecodeNotice = capDecodeNotice;
        this.portRefArea = portRefArea;
        this.portScanResult = portScanResult;
        this.saTokenCmds = saTokenCmds;
    }

    public void init() {
        initEnvCheckCmds();
        initPrivCheckCmds();
        initPortRefArea();
        initSATokenCmds();
    }

    // ---- 事件处理 ----

    public void copyEnvCmds()  { ctx.copyToClipboard(envCheckCmds.getText()); }
    public void copyPrivCmds() { ctx.copyToClipboard(privCheckCmds.getText()); }
    public void copySATokenCmds() { ctx.copyToClipboard(saTokenCmds.getText()); }

    public void showEnvGuidance() {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("使用说明");
        alert.setHeaderText("环境辨别命令使用指南");
        alert.setContentText(
                "这些命令需要在目标容器/Pod的Shell中执行。\n\n"
                + "关键判断方法:\n"
                + "1. cat /proc/1/cgroup - 如果包含/kubepods/则为K8s环境\n"
                + "2. 检查是否存在 /var/run/secrets/kubernetes.io/ 目录\n"
                + "3. 环境变量中是否有KUBERNETES_SERVICE_HOST\n\n"
                + "复制命令后在目标Shell中粘贴执行即可。"
        );
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(500);
        alert.show();
    }

    public void decodeCapabilities() {
        capDecodeOutput.setVisible(true);
        capDecodeOutput.setManaged(true);
        capDecodeNotice.setText("");
        capDecodeNotice.setManaged(false);
        capDecodeNotice.setVisible(false);
        String hex = capHexInput.getText().trim();
        if (hex.isEmpty()) {
            capDecodeOutput.setText("[-] 请输入CapEff hex值");
            return;
        }
        hex = hex.replaceAll("^0x", "");
        try {
            long capValue = Long.parseUnsignedLong(hex, 16);
            StringBuilder sb = new StringBuilder();
            List<List<String>> rows = new ArrayList<>();
            sb.append("解码结果 (0x").append(hex).append("):\n\n");
            boolean hasAny = false;
            for (int i = 0; i < CAP_NAMES.length; i++) {
                if ((capValue & (1L << i)) != 0) {
                    sb.append("  [+] ").append(CAP_NAMES[i]).append(" (bit ").append(i).append(")\n");
                    rows.add(List.of(String.valueOf(i), CAP_NAMES[i]));
                    hasAny = true;
                }
            }
            if (!hasAny) sb.append("  无任何Capability\n");
            if ((capValue & (1L << 21)) != 0)
                sb.append("\n[!] 警告: 包含CAP_SYS_ADMIN，可能存在容器逃逸风险!\n");
            if (capValue == 0x3fffffffffL || capValue == 0x1fffffffffL)
                sb.append("\n[!] 警告: 可能是特权容器 (all capabilities)!\n");
            String warnings = sb.toString().lines().filter(line -> line.startsWith("[!]"))
                    .map(line -> line.substring(4)).collect(java.util.stream.Collectors.joining("\n"));
            capDecodeNotice.setText(warnings);
            capDecodeNotice.setManaged(!warnings.isEmpty());
            capDecodeNotice.setVisible(!warnings.isEmpty());
            capDecodeOutput.showTable("Capabilities", List.of("Bit", "Capability"), rows, sb.toString());
            ctx.log("[+] Capabilities解码完成");
        } catch (NumberFormatException e) {
            capDecodeOutput.setText("[-] 无效的hex值: " + hex);
        }
    }

    public void quickPortScan() {
        String host = ctx.getHost();
        if (host == null) return;
        portScanResult.setVisible(true);
        portScanResult.setManaged(true);
        ctx.setStatus("正在扫描端口...");
        portScanResult.setText("扫描中...\n");
        ctx.log("[*] 开始扫描 " + host + " 的K8s常见端口");

        Task<String> task = new Task<String>() {
            @Override
            protected String call() {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("%-28s %-8s %s\n", "组件", "端口", "状态"));
                sb.append(String.format("%-28s %-8s %s\n", "---", "---", "---"));
                for (int i = 0; i < K8S_PORTS.length; i++) {
                    int port = K8S_PORTS[i][0];
                    boolean open = K8sHttpUtil.isPortOpen(host, port, 2);
                    String status = open ? "[OPEN]" : "[CLOSED]";
                    sb.append(String.format("%-28s %-8d %s\n", K8S_PORT_NAMES[i], port, status));
                }
                return sb.toString();
            }
        };
        task.setOnSucceeded(e -> {
            List<List<String>> rows = task.getValue().lines().skip(2)
                    .map(line -> List.of(line.strip().split("\\s{2,}", 3))).toList();
            portScanResult.showTable("端口扫描", List.of("组件", "端口", "状态"), rows, task.getValue());
            ctx.setStatus("扫描完成");
            ctx.log("[+] 端口扫描完成");
        });
        task.setOnFailed(e -> {
            portScanResult.setText("[-] 扫描失败: " + task.getException().getMessage());
            ctx.setStatus("扫描失败");
        });
        new Thread(task).start();
    }

    public void decodeTokenBase64() {
        String raw = ctx.getTokenField().getText().trim();
        if (raw.isEmpty()) {
            ctx.log("[-] Token字段为空，请先粘贴Base64编码的Token");
            return;
        }
        try {
            String cleaned = raw.replace("\\u003d", "=");
            byte[] decoded = Base64.getDecoder().decode(cleaned);
            String token = new String(decoded, "UTF-8").trim();
            ctx.getTokenField().setText(token);
            ctx.log("[+] Token已Base64解码并填入");
        } catch (Exception e) {
            ctx.log("[-] Base64解码失败: " + e.getMessage() + " — 当前内容可能已是明文Token");
        }
    }

    // ---- 初始化文本 ----

    private void initEnvCheckCmds() {
        envCheckCmds.setText(
                "# ======== 容器环境辨别 ========\n\n"
                + "# 1. 检查是否存在 .dockerenv 文件\n"
                + "ls -la /.dockerenv\n\n"
                + "# 2. 检查cgroup信息（关键命令）\n"
                + "#    Docker环境: 会显示 /docker/<container_id>\n"
                + "#    K8s环境:   会显示 /kubepods/...\n"
                + "cat /proc/1/cgroup\n\n"
                + "# 3. 检查环境变量是否包含K8s相关信息\n"
                + "env | grep -i kube\n\n"
                + "# 4. 检查是否存在K8s service account\n"
                + "ls -la /var/run/secrets/kubernetes.io/serviceaccount/\n\n"
                + "# 5. 检查hostname（K8s pod通常有特殊命名格式）\n"
                + "hostname\n\n"
                + "# 6. 检查mount信息\n"
                + "mount | grep -i kube\n\n"
                + "# 7. 检查/etc/resolv.conf（K8s环境通常包含cluster.local）\n"
                + "cat /etc/resolv.conf\n\n"
                + "# 8. 检查内核版本（用于判断内核漏洞可利用性）\n"
                + "uname -r\n"
        );
    }

    private void initPrivCheckCmds() {
        privCheckCmds.setText(
                "# ======== 特权信息搜集 ========\n\n"
                + "# 1. 使用capsh打印当前Capabilities\n"
                + "capsh --print\n\n"
                + "# 2. 如果没有capsh，查看/proc/1/status获取Capabilities hex\n"
                + "cat /proc/1/status | grep -i cap\n\n"
                + "# 3. 在自己的机器上解码CapEff hex值\n"
                + "# capsh --decode=<hex_value>\n\n"
                + "# 4. 检查是否为特权容器\n"
                + "cat /proc/1/status | grep -i seccomp\n\n"
                + "# 5. 检查是否可以访问宿主机设备\n"
                + "ls /dev\n\n"
                + "# 6. 检查是否挂载了敏感目录\n"
                + "mount | grep -E \"(host|docker\\.sock|kubelet)\"\n\n"
                + "# 7. 检查是否有docker.sock\n"
                + "ls -la /var/run/docker.sock\n\n"
                + "# 重点关注的危险Capabilities:\n"
                + "# CAP_SYS_ADMIN  - 可用于容器逃逸\n"
                + "# CAP_NET_ADMIN  - 网络操作\n"
                + "# CAP_SYS_PTRACE - 进程调试\n"
                + "# CAP_DAC_READ_SEARCH - 读取任意文件\n"
        );
    }

    private void initPortRefArea() {
        String reference =
                "# ======== K8s相关端口参考 ========\n\n"
                + String.format("%-28s %-12s %s\n", "组件", "端口", "说明")
                + String.format("%-28s %-12s %s\n", "---", "---", "---")
                + String.format("%-28s %-12s %s\n", "kube-apiserver", "6443", "安全端口(HTTPS)")
                + String.format("%-28s %-12s %s\n", "kube-apiserver", "8080", "非安全端口(HTTP,默认关闭)")
                + String.format("%-28s %-12s %s\n", "kubectl proxy", "8080/8081", "代理端口")
                + String.format("%-28s %-12s %s\n", "kubelet", "10250", "HTTPS API端口")
                + String.format("%-28s %-12s %s\n", "kubelet", "10255", "只读端口(已废弃)")
                + String.format("%-28s %-12s %s\n", "kubelet", "4149", "cAdvisor端口")
                + String.format("%-28s %-12s %s\n", "dashboard", "30000", "NodePort默认端口")
                + String.format("%-28s %-12s %s\n", "docker api", "2375", "Docker远程API")
                + String.format("%-28s %-12s %s\n", "etcd", "2379/2380", "客户端/集群通信")
                + String.format("%-28s %-12s %s\n", "kube-controller-manager", "10252", "监控端口")
                + String.format("%-28s %-12s %s\n", "kube-proxy", "10256", "健康检查端口")
                + String.format("%-28s %-12s %s\n", "kube-scheduler", "10251", "监控端口")
                + String.format("%-28s %-12s %s\n", "weave", "6781-6783", "网络插件")
                + String.format("%-28s %-12s %s\n", "kubeflow-dashboard", "8080", "机器学习平台");
        List<List<String>> rows = reference.lines().skip(4)
                .map(line -> List.of(line.strip().split("\\s{2,}", 3))).toList();
        portRefArea.showTable("常见端口", List.of("组件", "端口", "说明"), rows, reference);
    }

    private void initSATokenCmds() {
        saTokenCmds.setText(
                "# ======== Service Account Token操作 ========\n\n"
                + "# 1. 查看Pod中的ServiceAccount token\n"
                + "cat /var/run/secrets/kubernetes.io/serviceaccount/token\n\n"
                + "# 2. 查看namespace\n"
                + "cat /var/run/secrets/kubernetes.io/serviceaccount/namespace\n\n"
                + "# 3. 查看CA证书\n"
                + "cat /var/run/secrets/kubernetes.io/serviceaccount/ca.crt\n\n"
                + "# 4. 使用token查询当前权限\n"
                + "TOKEN=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)\n"
                + "curl -k -H \"Authorization: Bearer $TOKEN\" \\\n"
                + "  https://kubernetes.default.svc:443/apis/authorization.k8s.io/v1/selfsubjectrulesreviews \\\n"
                + "  -X POST -H \"Content-Type: application/json\" \\\n"
                + "  -d '{\"apiVersion\":\"authorization.k8s.io/v1\",\"kind\":\"SelfSubjectRulesReview\",\"spec\":{\"namespace\":\"default\"}}'\n\n"
                + "# 5. 使用kubectl配合token\n"
                + "kubectl --server=https://<API_SERVER>:6443 \\\n"
                + "  --token=$TOKEN \\\n"
                + "  --insecure-skip-tls-verify=true \\\n"
                + "  auth can-i --list\n\n"
                + "# 6. 用token获取集群信息\n"
                + "kubectl --server=https://<API_SERVER>:6443 \\\n"
                + "  --token=$TOKEN \\\n"
                + "  --insecure-skip-tls-verify=true \\\n"
                + "  get nodes\n"
        );
    }
}
