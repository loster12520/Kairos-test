# ADR 0004：Gradle 插件与 artifact variant

- 状态：已接受（M0 冻结，2026-10-31）；其 Gradle 插件部分已在同一轮 M1 底座中开始落地
- 决策者：Kairos 维护者
- 上游：[plan-1.0.md](../../.lignting/kairos/plan-1.0.md) M1/M2、§4 目标模块结构
- 相关：[ADR 0003 类名/manifest 契约](0003-classname-manifest-contract.md)、[ADR 0005 SSR 增强模型](0005-ssr-enhancement-model.md)、[支持矩阵](../contracts/support-matrix.md)

## 背景

M0 开始时，仓库**没有 Gradle 插件**。所有使用方都在自己的构建文件里手工接编译器：

```kotlin
compilerOptions.freeCompilerArgs.addAll(
    "-Xplugin=" + kairosPluginJarPath.get().asFile.absolutePath,
    "-P", "plugin:dev.kairos:outputDir=" + kairosOutDir.get().asFile.absolutePath,
    "-P", "plugin:dev.kairos:module=demo-web",
)
```

配套还有 `dependsOn(":kairos-compiler-plugin:jar")`、`inputs.file(...)`、`outputs.dir(...)` 和 `outputs.cacheIf { false }`。这条路径在 demo/blog 里能跑，但它把 jar 路径、输出目录、模块名和缓存策略全部暴露给用户，无法满足 plan M1 的“一个插件 ID 即可构建”。

在本 ADR 写作期间，M1 底座已经并行落地，仓库当前的真实状态是：

- 已有 Gradle Wrapper（9.7.1）、根 `build.gradle.kts`、`gradle/libs.versions.toml`；
- 新模块 `kairos-gradle-plugin` 实现 `KotlinCompilerPluginSupportPlugin`，插件 ID 为 `dev.kairos`，发布 plugin marker；
- `KairosExtension` 暴露 `moduleName`、`outputDirectory`、`disabledTargets`、`strict`；默认输出目录为 `build/kairos/<target>/<compilation>`；
- demo、demo-web-feature、blog-backend、blog-frontend 已改为 `alias(libs.plugins.kairos)`，build 文件里不再出现 `-Xplugin` 与手工任务；
- 根工程统一 `group = "dev.kairos"`、`version = "0.1.0"`，框架模块启用 `maven-publish`，并新增 `kairos-bom`；
- 编译器插件新增 `projectDir` 与 `strict` 选项，`KairosCollector` 已能把 manifest 中的源码路径相对化（前提是 `projectDir` 被传入）；
- `cacheIf { false }` 已从示例构建中移除；`runJsTierOne` 的 bundle 文件名与 `runJvmReference` 的描述已修正。

仍然**没有**落地的部分（截至本文写作）：

- 2026-10-31：库样式资产已落地为 **jar 内嵌**（`META-INF/kairos/<module>/<target>-<compilation>/`），消费方插件用 `kairosCollectStyles` 解压、`kairosMergeStyles` 合并；不再需要手工 `kairosMerge`；
- 还没有 Gradle 侧的 merge/prune 任务，`merge` 仍只产出 CSS、不产出合并 manifest；
- `kairos-compiler-plugin` 仍把 `kairos-core` 的 class 打包进插件 jar；`kairos-style-contract` 尚不存在；
- `strict` 选项已接到 `KairosCollector` 构造器，但折叠失败路径尚未读取它；
- clean-room consumer 已有（`examples/consumer-static` + `tools/consumer-check.mjs`），当前 5/5 通过；CI workflow 已建立（`.github/workflows/ci.yml`），但尚未在托管环境执行，自动化发布也尚未接入。

## 决策

### 1. 正式 Gradle 插件

- 模块名 `kairos-gradle-plugin`，实现 `KotlinCompilerPluginSupportPlugin`；
- 插件 ID 固定为 `dev.kairos`，同时发布 plugin marker；
- 插件自动为项目中的 Kotlin compilation 注入编译器插件与四个选项：`outputDir`、`module`、`projectDir`、`strict`；
- 用户的 build 文件只出现 `plugins { alias(libs.plugins.kairos) }` 或 `id("dev.kairos") version ...` 与普通依赖，不出现 `-Xplugin`、jar 路径或 `-P plugin:dev.kairos:*`；
- 插件版本与编译器插件、运行时、BOM 同版本发布；不提供“插件与运行时各自升级”的官方组合。

### 2. 每 compilation 的输出所有权

- 默认输出目录固定为 `build/kairos/<target>/<compilation>/`，例如 `build/kairos/js/main/`、`build/kairos/jvm/main/`；
- `KairosExtension.outputDirectory` 可覆盖，但一旦覆盖就是整个项目共用；官方文档只推荐默认值；
- 每个编译任务只写自己的目录，禁止两个 compilation 共享同一个输出目录；JVM/JS 双目标项目必须各自有一份完整产物；
- 编译任务的输入必须包含：源码、编译器插件 jar、插件选项、样式契约版本；输出声明为对应目录；
- 已移除 `cacheIf { false }`；构建缓存必须是可重定位的（manifest 路径相对化，见 [ADR 0003](0003-classname-manifest-contract.md)）；
- 同一份源码同时编译到 JVM 与 JS 时，两个目标必须看到同一份模块级声明频次信息；否则静态 HTML 与浏览器端类名会错位。当前示例由插件同时作用于两个目标来保证这一点。

### 3. 编译器插件与样式契约解耦

- 计划模块 `kairos-style-contract` 承载 `Layer`、`Declaration`、条件上下文、`classNameFor`、manifest DTO 与算法版本；
- `kairos-compiler-plugin`、`kairos-core`、`kairos-cli` 都依赖该契约模块，保证类名算法只有一个实现；
- **插件 jar 不得再打包 `kairos-core`**（HTML DSL、Signal、Markdown）。编译器只需要契约类型，不需要 DOM/Reactive；
- 当前插件 jar 仍然 `zipTree` 了 core 的 class，这是 M1 尚未拆除的已知技术债；在契约模块落地前它可以继续工作，但不能成为“插件可以依赖 core”的先例；
- 编译器插件只针对受支持的 Kotlin 版本编译；K2 IR API 属于实验接口，升级 Kotlin 小版本必须跑 golden/negative fixture。

### 4. 库样式资产（artifact variant）

目标能力：第三方组件库以**二进制依赖**发布样式资产，消费方不需要拿到 Kotlin 源码。**2026-10-31 已实现 v1**，实现方式见本节末尾的"实现记录"；冻结的语义是：

- 生产方：带样式的模块在正常 JVM/JS variant 之外，额外发布一个消费型 style variant，内容至少包含 `kairos.css`、`kairos.map.json`，可选 source map；
- 消费方：Gradle 插件注册一个可解析配置，按 variant 属性**传递地**收集自身与依赖树的样式资产；
- 合并：应用级任务把自身 compilation 的资产与解析到的依赖资产合并成一份 CSS + 一份合并 manifest；同层同属性冲突按层级与 `conflicts` 规则处理，类名相同但声明不同直接失败；
- 剪枝：只在应用侧做，库侧不剪枝；剪枝输入是最终发布出去的 HTML/JS；
- 消费方运行时只加载合并后的 CSS，不需要在运行时拉取或拼接样式；
- 库的类名在发布时已由同一算法生成，消费方不得重新哈希或改名。

样式资产用 Gradle variant + attribute 表达，而不是仅靠 classifier：classifier 无法参与依赖解析，不能自动传递；variant 才能让“只依赖组件库坐标”就拿到样式资产。具体 attribute 名称在落地时冻结。

**实现记录（v1，2026-10-31）**：v1 选择了比 variant 更简单的载体——**把编译产物嵌进库 jar 的 `META-INF/kairos/<module>/<target>-<compilation>/`**。

- 为什么够用：jar 本身就是传递依赖的载体，`runtimeClasspath` 解析天然覆盖直接与间接依赖，不需要额外 attribute 或发布配置；离线、mavenLocal、Maven Central 都走同一条路径。
- 消费方：`dev.kairos` 插件给 JVM 项目注册 `kairosCollectStyles`（解压 `META-INF/kairos/**` 到 `build/kairos/dependencies/`）和 `kairosMergeStyles`（CLI 合并本地 + 依赖 manifest，输出 `build/kairos/merged/kairos.css|map.json`）。
- 代价：样式资产只随 jar 走，所以只有产出 jar 的 target 能传递（JVM 库；KMP 库的 JVM jar 同样生效）；JS-only 库需要将来的 variant 方案。库侧不剪枝，剪枝仍在应用侧。

### 5. 发布与安装闭环

- 所有框架模块配置 `group`、`version`、`maven-publish`，产出 POM、sources、module metadata；当前版本为 `0.1.0`；
- 发布 `kairos-bom` 统一版本；BOM 当前约束 core、browser、htmx、cli、compiler-plugin、gradle-plugin；
- 插件通过 plugin marker 解析，不使用 `buildscript` classpath 手工加 jar；
- 示例工程通过 `settings.gradle.kts` 的 `mavenLocal()` 消费已发布插件；`frameworkOnly=true` 可在插件尚未发布时跳过示例工程配置；
- 验收方式是 clean-room consumer：空工程只加插件与依赖，能构建出页面与 CSS；`examples/consumer-static` 已按此方式建立，`tools/consumer-check.mjs` 验证插件 ID、无手工接线、相对路径、CSS 完整性与层顺序；
- `explicitApi()` 与二进制兼容检查在 M1 收尾后启用（见 plan M1）。

### 6. 与手工接线的兼容策略

M1 之后，官方支持路径只有插件。手工 `-Xplugin` 接线不承诺兼容，也不写入 quickstart；示例工程已经迁移，作为插件的第一个消费者。这样可以在 0.x 阶段就暴露插件在真实多模块项目里的问题。

## 现状与目标对照

| 项目 | M0 基线 | 当前（2026-10-31） | 目标 |
|---|---|---|---|
| 接入方式 | 手工 `-Xplugin` + `-P` + jar 路径 | `id("dev.kairos")` | 保持插件 |
| 插件 ID / marker | 只有编译器插件 `pluginId` | Gradle 插件 + marker | 保持 |
| 输出目录 | 每模块一个手工目录 | `build/kairos/<target>/<compilation>` | 保持 |
| 任务缓存 | `cacheIf { false }` | 已移除，声明 outputs | 可重定位缓存验证 |
| 插件 jar | 打包 `kairos-core` 全部 class | 仍打包 | 只含契约/隔离后的 class |
| 库样式资产 | jar 内嵌 + 插件自动收集合并（v1） | 手工 `merge` | 传递依赖自动收集，JS-only 库待 variant 方案 |
| 合并 manifest | 无 | 无 | 与 CSS 一起产出 |
| 发布 | 无 group/version/publish | `dev.kairos:0.1.0` + BOM | Maven Central/Portal |
| JVM/JS 一致性 | 两个目标都挂插件 | 插件统一注入 | 同源同算法验收 |

## 后果

- 正面：安装闭环基本成立；输出目录与缓存责任明确；第三方库可以不交源码就带样式的路径已经确定；JVM/JS 类名错位有机制保障。
- 代价：新增 Gradle 插件模块与新构建逻辑；插件、编译器插件、Kotlin 版本三者强耦合；variant 属性设计错误会导致依赖解析失败且难排查。
- 风险：Kotlin IR API 不稳定会让插件随 Kotlin 升级失效；插件 jar 隔离不当会出现类冲突；合并/剪枝顺序若不稳定会破坏可复现性；多模块下“谁负责 merge/prune”若边界不清会重复产出。

## 替代方案

1. **长期保留手工接线**：实现成本最低，但用户仍要复制 jar 路径与 Gradle 任务，无法满足 M1 退出标准；否决。
2. **做成 fat jar，把 core 永久打进插件**：会继续把编译器 ABI 与 DOM/Reactive API 绑死，也无法让插件在编译器 daemon 里稳定加载；否决。
3. **只用 classifier 发布 CSS**：消费方要手动声明每个库的样式坐标，无法传递收集；否决。
4. **运行时注入样式**：由 JS 在浏览器里插入 CSS，违背 HTML-first 与静态产物目标；否决。

## 参考

- [plan-1.0.md M1](../../.lignting/kairos/plan-1.0.md)
- [plan-1.0.md §4 目标模块结构](../../.lignting/kairos/plan-1.0.md)
- [README.md 架构与已知限制](../../README.md)
- [ADR 0003：类名/manifest 版本契约](0003-classname-manifest-contract.md)
