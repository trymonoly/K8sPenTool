package com.k8spen.tool.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Docker Engine Remote API 客户端。
 *
 * 只用 HttpURLConnection + Gson 实现，不引入 docker-java 及其传递依赖；
 * 输出统一带 "[HTTP code]" 前缀（与 K8sHttpUtil 一致），便于复用 ResultPane 的
 * 状态徽章、JSON 美化与表格视图。
 */
public class DockerApiUtil {

    private static final String USER_AGENT = "K8sPenTool/2.1";

    /** 单次 API 调用结果 */
    public record Response(int code, String body) {

        public boolean ok() {
            return code >= 200 && code < 300;
        }

        /** 与 K8sHttpUtil 一致的展示格式 */
        public String display() {
            return "[HTTP " + code + "]\n" + (body == null ? "" : body);
        }

        public JsonObject asObject() {
            JsonElement element = parse();
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        }

        public JsonArray asArray() {
            JsonElement element = parse();
            return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
        }

        private JsonElement parse() {
            String text = body == null ? "" : body.trim();
            if (!text.startsWith("{") && !text.startsWith("[")) return null;
            try {
                return JsonParser.parseString(text);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private final String baseUrl;
    private final int timeoutSec;

    public DockerApiUtil(String baseUrl, int timeoutSec) {
        this.baseUrl = baseUrl;
        this.timeoutSec = Math.max(timeoutSec, 1);
    }

    public static String buildBaseUrl(String protocol, String host, int port) {
        String scheme = protocol == null || protocol.isBlank() ? "http" : protocol.trim().toLowerCase();
        return scheme + "://" + host.trim() + ":" + port;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Response request(String method, String path, String jsonBody) throws IOException {
        return request(method, path, jsonBody, timeoutSec * 1000);
    }

    /**
     * 发送一次 Docker API 请求。
     *
     * @param readTimeoutMs 读取超时；exec start 这类长流式响应需要比普通请求更长
     */
    public Response request(String method, String path, String jsonBody, int readTimeoutMs) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(timeoutSec * 1000);
            conn.setReadTimeout(readTimeoutMs);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");
            if (jsonBody != null) {
                byte[] payload = jsonBody.getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setFixedLengthStreamingMode(payload.length);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(payload);
                }
            }
            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            return new Response(code, stream == null ? "" : readAll(stream));
        } catch (SocketTimeoutException e) {
            throw new IOException("请求超时（" + readTimeoutMs / 1000 + " 秒）: " + method + " " + path, e);
        } catch (ConnectException e) {
            throw new IOException("连接失败: " + e.getMessage()
                    + " — 请确认 Docker API 端口已暴露且未启用客户端证书认证", e);
        } catch (javax.net.ssl.SSLException e) {
            throw new IOException("TLS 握手失败: " + e.getMessage()
                    + " — 2376 端口需要 tls 选项；本工具仅支持跳过服务端证书校验", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 在容器内执行一条命令并读取输出。
     *
     * 与原始 DockerAPITool 的实现保持一致：exec 创建时 AttachStdout/AttachStderr + Tty，
     * 启动时 Detach=false，因此 daemon 会以 chunked 流返回原始输出（无 8 字节多路复用头）。
     *
     * @return 命令的原始输出（stdout 与 stderr 混合）
     */
    public String execCapture(String containerId, String shell, String command, int readTimeoutSec) throws IOException {
        String shellCmd = shell == null || shell.isBlank() ? "sh" : shell.trim();

        JsonObject create = new JsonObject();
        create.addProperty("AttachStdout", true);
        create.addProperty("AttachStderr", true);
        create.addProperty("Tty", true);
        JsonArray argv = new JsonArray();
        argv.add(shellCmd);
        argv.add("-c");
        argv.add(command);
        create.add("Cmd", argv);

        Response created = request("POST", "/containers/" + encode(containerId) + "/exec", create.toString());
        if (!created.ok()) throw new IOException("创建 exec 失败: " + created.display());
        JsonObject createdJson = created.asObject();
        String execId = createdJson != null && createdJson.has("Id") ? createdJson.get("Id").getAsString() : "";
        if (execId.isEmpty()) throw new IOException("创建 exec 的响应缺少 Id: " + created.body());

        JsonObject start = new JsonObject();
        start.addProperty("Detach", false);
        start.addProperty("Tty", true);
        Response started = request("POST", "/exec/" + encode(execId) + "/start", start.toString(),
                Math.max(readTimeoutSec, 1) * 1000);
        if (!started.ok()) throw new IOException("执行命令失败: " + started.display());
        // TTY 模式下 daemon 输出的换行是 \r\n，统一成 \n 便于在文本区显示
        return started.body().replace("\r\n", "\n");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
