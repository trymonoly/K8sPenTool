# K8sPenTool — Agent 指南

## 项目概述

K8sPenTool 是一款基于 **JavaFX 17** 的 Kubernetes 综合渗透测试 GUI 工具（作者：T3Y），将常见 K8s 攻击链（信息搜集 → 初始访问 → 命令执行 → 权限维持 → 权限提升 → 横向移动 → kubectl 操作）集成到统一图形界面中。

⚠️ **安全性质**：这是一个渗透测试/攻击工具，包含后门 Pod 部署、容器逃逸、凭证窃取等功能，且代码中内嵌了默认 SSH 密钥对（`MainController.java` 中的 `DEFAULT_SSH_PUBKEY`/`DEFAULT_SSH_PRIVKEY`）。仅可用于合法授权的安全测试。修改代码时不得删除相关免责声明逻辑（`MainController#showAbout`）。

## 技术栈

- **Java 17**（`maven.compiler.source/target = 17`），JPMS 模块化（`src/main/java/module-info.java`，模块名 `com.k8spen.tool`）
- **JavaFX 17.0.2**（controls / fxml / swing 三个依赖）+ FXML 界面
- **Gson 2.9.0** — JSON 解析/格式化
- **Maven** 构建，`maven-shade-plugin` 打 Fat JAR，主类为 `com.k8spen.tool.Launcher`（非 JavaFX Application 子类，用于绕过 shaded jar 的模块路径问题）
- 内嵌 **Windows 版 `kubectl.exe`（约 61MB，位于 `src/main/resources/`）**：运行期由 `KubectlUtil.init()` 解包到临时目录执行，因此本工具实际上是**面向 Windows 的攻击者机器**设计的

## 构建与运行

```bash
mvn clean package                          # 生成 target/k8s-pen-tool-2.1.jar（Fat JAR）
java -jar target/k8s-pen-tool-2.1.jar    # 运行
mvn javafx:run                             # 开发期通过 javafx-maven-plugin 运行
```

- 当前版本为 v2.1，产物名为 `k8s-pen-tool-2.1.jar`（shade 插件同时生成 `original-*.jar`）。
- 无单元测试、无测试目录、无 CI 配置。验证方式就是 `mvn clean package` 编译通过后启动 GUI 手动确认。

## 代码结构

```
src/main/java/
├── module-info.java                  # JPMS 模块声明：requires javafx.* / com.google.gson
└── com/k8spen/tool/
    ├── Main.java                     # JavaFX Application 入口，加载 /gui.fxml，启动时调用 disableSslVerification()
    ├── Launcher.java                 # shaded jar 的 mainClass，仅委托 Main.main()
    ├── controller/                   # MVC 控制层（FXML 控制器 + 按功能拆分的 Handler）
    │   ├── MainController.java       # 所有 @FXML 字段与事件方法，纯薄委托层
    │   ├── ControllerContext.java    # 共享上下文：目标(协议/地址/端口)/Token/日志/剪贴板/异步 HTTP 封装
    │   ├── InfoHandler.java          # 0.信息搜集（环境辨别、特权检测、Capabilities 解码、端口扫描）
    │   ├── AccessHandler.java        # 1.初始访问（APIServer/Kubelet/Etcd/Dashboard/Kubeconfig，含未授权一键生成高权限Token）
    │   ├── ExecHandler.java          # 2.命令执行（APIServer/Kubelet exec、后门 Pod、反弹 Shell、RBAC）
    │   ├── PersistHandler.java       # 3.权限维持（Admin SA、CronJob/DaemonSet、影子 Kubeconfig）
    │   ├── EscapeHandler.java        # 4.权限提升（特权容器/挂载逃逸/内核漏洞，主要生成 YAML 与命令）
    │   ├── LateralHandler.java       # 5.横向移动（Secret 窃取、内网探测、污点容忍）
    │   ├── KubectlHandler.java       # 6.kubectl 操作（快捷命令、自定义命令、后门 Pod 管理）
    │   ├── DockerHandler.java        # 7.Docker API（连接/概览、容器管理、创建特权容器、镜像列表）
    │   └── DockerExecHandler.java    # 7.Docker API（容器内命令执行、特权容器挂载逃逸）
    ├── helper/                       # 数据模型与解析器
    │   ├── PodTableItem.java / SecretTableItem.java   # TableView 行模型
    │   ├── PodJsonParser.java / PodLineParser.java    # kubectl/API 输出的 Pod 解析
    │   ├── PersistenceHelper.java                     # 持久化 YAML 生成辅助
    │   ├── DockerContainerItem.java / DockerImageItem.java  # Docker 容器/镜像表格行模型
    │   └── DockerInfoParser.java                      # /info + /version 的中文摘要渲染
    └── utils/
        ├── K8sHttpUtil.java          # 基于 HttpURLConnection 的 HTTP 工具（含信任所有证书逻辑）
        ├── K8sJsonRenderer.java      # K8s JSON 输出的渲染/美化（约 600 行）
        ├── KubectlUtil.java          # 解包并调用内嵌 kubectl.exe，管理 kubeconfig/YAML 临时文件
        └── DockerApiUtil.java        # Docker Engine Remote API 客户端（HttpURLConnection，不依赖 docker-java）
src/main/resources/
├── gui.fxml                          # 全部界面布局（约 1280 行，含 Docker API 标签页；fx:id 与 MainController 字段一一对应）
└── kubectl.exe                       # 内嵌的 Windows kubectl 二进制
```

### 架构要点

- **UI 绑定方式**：`gui.fxml` 中的 `fx:id` 对应 `MainController` 的 `@FXML` 私有字段，`onAction="#methodName"` 对应 `@FXML` 事件方法。新增 UI 控件时三处必须同步：FXML、`MainController` 字段声明、事件委托方法。
- **分层模式**：`MainController` 只做字段注入和单行委托（`handler.someMethod()`），业务逻辑全部在各 `XxxHandler` 中。Handler 通过构造器接收 `ControllerContext` 和自己所需的 UI 控件引用。
- **ControllerContext**：所有 Handler 的公共入口，提供 `getHost()`（自动剥离粘贴进来的 `http(s)://` 前缀与 `:port` 后缀）/`getProtocol()`/`getApiPort()`/`getApiBaseUrl()`（APIServer 基础地址，协议与端口来自目标配置栏的 `protocolOpt`/`apiPort` 控件，各 Handler 拼接 APIServer URL 必须用它，禁止再硬编码 `https://host:6443`）/`getToken()`/`getTimeout()`、`log()`（带时间戳写入日志区）、`asyncGet()`/`asyncPost()`（基于 `javafx.concurrent.Task` 的后台 HTTP 请求，成功/失败回调自动更新 UI）。Kubelet（10250）等非 APIServer 端口不在 `getApiBaseUrl()` 范围内。
- **UI 线程规则**：所有网络/进程调用必须放在 `Task` 的后台线程中执行，UI 更新通过 `Platform.runLater` 或 Task 回调完成——遵循现有 `asyncGet`/`asyncPost` 的模式，不要在事件处理方法里直接发请求。
- **kubectl 调用**：`KubectlUtil.exec()` / `execWithKubeconfig()` / `execRaw()` 均使用 `ProcessBuilder` 参数列表（无 shell 拼接），stdin 重定向到空文件防止交互提示，超时可配。临时文件（kubeconfig、YAML）统一 `deleteOnExit()`。
- **HTTPS 处理**：`Main.start()` 与请求重试路径都会调用 `K8sHttpUtil.disableSslVerification()` 全局信任所有证书——这是工具刻意行为（连接自签名集群），不是疏漏，不要"修复"它。
- **Docker API 模块（顶部第 8 个标签 "Docker API"）**：`DockerHandler` 负责连接/概览、容器列表与操作、创建特权容器、镜像列表；`DockerExecHandler` 负责容器内 exec 与特权容器挂载逃逸（对应原始 DockerAPITool v0.1 的功能）。底层 `DockerApiUtil` 直接用 `HttpURLConnection` 调 Docker Engine Remote API（2375/2376），**刻意不引入 docker-java**，因此不要在 pom 里加 docker-java 依赖。exec 采用 `Tty=true` + `Detach=false` 的 chunked raw-stream（无 8 字节多路复用头），读超时见 `DockerExecHandler.EXEC_READ_TIMEOUT_SEC`（300 秒）；容器下拉框共享 `DockerHandler#containers()` 这一个 `ObservableList`。2376 端口的客户端证书认证未实现，只支持跳过服务端证书校验。

## 代码风格约定

- **注释与文档使用中文**；大量中文字符串以 `\uXXXX` Unicode 转义书写（尤其 `MainController.java`、`KubectlUtil.java`），也有直接的中文 UTF-8 字符串（`ControllerContext.java`）。修改文件时**保持该文件既有风格**，源码编码统一 UTF-8（pom 已配置 `project.build.sourceEncoding=UTF-8`）。
- 日志输出格式：`[HH:mm:ss] [*]/[+]/[-] 消息`，通过 `ctx.log()` 输出；状态栏用 `ctx.setStatus()`。
- HTTP 响应统一前缀 `[HTTP <code>]` 后接响应体（`K8sHttpUtil` 约定），`ControllerContext.prettyJson()` 依赖此前缀做 JSON 美化。
- 代码分区使用 `// ==== 区块标题 ====` 注释横幅（见 `MainController`）。
- 缩进 4 空格，无 lint/格式化工具配置。

## 测试

- 项目**没有任何测试框架与测试代码**（无 `src/test`）。
- 变更后的验证：`mvn clean package` 编译通过 + `java -jar` 启动 GUI 人工冒烟。
- 目标 `target/` 已在 `.gitignore` 中忽略，不要提交。

## 安全注意事项（针对代码维护者）

- 代码内含默认 SSH 私钥常量，属工具设计的一部分（用于后门 Pod 注入），审计/重构时勿误删也不要替换成真实私钥。
- `KubectlUtil.execRaw` 的自定义命令直接透传给 kubectl 进程——这是工具的预期功能，不需要加输入过滤。
- 任何对外的 HTTP/kubectl 请求都以用户在 GUI 填写的目标地址为准，工具本身不内置目标。
