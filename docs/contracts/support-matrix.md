# Kairos 支持矩阵

- 状态：M0 冻结（2026-10-31），与 M1 底座同步更新
- 相关：[versioning.md](versioning.md)、[ADR 0004](../adr/0004-gradle-plugin-artifact-variant.md)、[public-api.md](public-api.md)

这份矩阵分两件事：**当前实测**（这台机器上真的跑过什么）与**承诺范围**（首个公开版本愿意为哪些组合负责）。两者不混写；实测过的不等于承诺，承诺的也不假装已经实测。

## 1. 当前实测（2026-10-31）

| 组件 | 实测版本 | 证据 / 备注 |
|---|---|---|
| Kotlin / Kotlin Gradle Plugin | 2.2.20 | `gradle/libs.versions.toml` 的 `kotlin`；编译器插件 `compileOnly` 同一版本 |
| Gradle | 9.7.1 | `gradle/wrapper/gradle-wrapper.properties` 与系统 `gradle --version` 一致 |
| JDK | 21.0.2（Oracle） | Gradle 启动 JVM；`jvmToolchain(21)` 用于 htmx/cli/compiler-plugin/gradle-plugin |
| Node.js | 26.7.0 | 系统 Node；`kotlin.js.nodejs.download=false`，Kotlin/JS 直接使用它 |
| npm | 11.19.0 | 工具链依赖安装用 |
| Kairos 构建产物 | `dev.kairos:0.1.0` | 根 `build.gradle.kts` 的 group/version；M1 底座刚落地，尚未发布到公共仓库 |
| Gradle 插件 | `dev.kairos`（0.1.0） | `kairos-gradle-plugin`，plugin marker 已生成 |
| htmx | 2.0.4 | `blog-backend/src/main/resources/htmx.min.js` 内嵌版本；仅示例使用 |
| Playwright | 1.64.1（已安装） | `tools/node_modules`；期望 chromium-1248，本机缓存为 chromium-1243；另缺 `libnspr4`/`libnss3`，浏览器无法启动 |
| Chromium（Playwright 渠道） | chromium-1243 缓存 | 当前唯一实测过的浏览器通道；M0 基线 E2E 10/10 使用它；本机不能复跑 |
| jsdom | 29.1.1（已安装） | `tools/node_modules`；DOM 级验收可用 |
| Ktor | 3.6.1 | 仅 blog 示例依赖，不是框架模块依赖 |
| Jimmer / SQLite / Hikari | 0.12.4 / 3.47.1.0 / 6.2.1 | 仅 blog 示例依赖 |
| 操作系统 | Ubuntu 24.04.1 LTS（WSL2，Linux 6.18） | x86_64 |

### 验收现状

- 仓库**没有** `src/commonTest` / `src/jvmTest` / `src/jsTest` 测试源码；`gradle check` 目前不构成测试门禁。Gradle 插件模块已加 JUnit 5.11.4 依赖与 `test` 任务，但还没有测试用例。
- **2026-10-31（M1 迁移后）实测**：`node tools/verify.mjs --rebuild` **14/14**，包含产物自动重建（A11）与两次强制构建逐字节一致（A12）；`node tools/consumer-check.mjs` **5/5**，clean-room consumer 只使用发布坐标 + `id("dev.kairos")` 构建，并检查了相对路径、CSS 完整性与层顺序。
- 历史记录（Phase 6 R1，2026-10-30，M0 基线）：同上的产物级 14/14、`node tools/browser-check.mjs` 8/8（jsdom）、`node tools/e2e.mjs` 10/10（Playwright + Chromium）。浏览器级结果属于 M0 基线，不是 M1 迁移后的重跑结果。
- 浏览器级测试只在 Chromium 上记录过通过；Firefox/WebKit 从未实测。
- 本机 Playwright 已安装（1.64.1），但期望的 Chromium 版本与缓存不一致，且缺少 `libnspr4`/`libnss3`，`node tools/e2e.mjs` 不能直接重跑；这是环境前置条件，不是框架行为问题。
- CI workflow 已建立（`.github/workflows/ci.yml`）：JDK 21 + Node 22，先 `publishToMavenLocal`，再构建、跑 `verify`/`browser-check`/`consumer-check` 与 blog E2E；本轮尚未在托管 CI 上执行，不能声称已通过。
- 跨机器/跨目录的确定性尚未验收；当前证明的是同机双构建一致 + consumer manifest 路径相对。

## 2. 承诺范围（首个公开版本）

“承诺”意味着该组合出现在 CI/发布门禁里；没列出的组合不保证。

| 组件 | 承诺范围 | 说明 |
|---|---|---|
| Kotlin | **2.2.20（精确）** | 编译器插件依赖 K2 IR 实验 API，首个公开版本只支持发布说明中写明的 Kotlin 版本；升级 Kotlin 需要新的 Kairos 版本与 fixture |
| Gradle | **9.7.1（以 wrapper 为准）** | 官方流程只支持 wrapper；不使用系统 `gradle` 作为发布验收依据；更高 Gradle 版本在验证前不承诺 |
| JDK | **21 LTS** | 构建与 JVM 服务端运行都以 21 为基线；JDK 17 及以下不在首个公开版本支持范围 |
| Node.js | **22 LTS / 24 LTS / 26 当前线** | 仅构建期需要；承诺版本要在 CI 中覆盖，当前 CI 固定 Node 22，实测只有 26.7.0，24/26 尚未进门禁 |
| 浏览器 | **Chromium 最近两个稳定版**（首个公开版本）；1.0 起加 Firefox ESR/最新版与 WebKit | 当前只有 Chromium 渠道实测；移动 viewport 与 Firefox/WebKit 属于 M4 门禁 |
| htmx | **2.0.x** | 仅官方 recipe 与示例；`kairos-htmx` 自身不依赖 htmx 运行时；htmx 1.x 不支持 |
| Ktor | **不绑定版本** | 框架模块不依赖 Ktor；官方 recipe 以 3.6.1 为实测基线，Ktor 升级由应用负责 |
| 操作系统 | **Linux x86_64** | 首个公开版本只承诺 Linux CI；macOS/Windows 在 M4 之后评估 |
| 构建入口 | **Gradle Wrapper（`./gradlew`）** | 直接调用系统 `gradle` 不属于支持路径 |

### 浏览器支持分级

| 级别 | 范围 | 验证要求 |
|---|---|---|
| 承诺 | Chromium 最近两个稳定版 | 每次发布跑 E2E + 视觉基线 |
| 目标（1.0） | Firefox ESR/最新版、WebKit、移动 viewport | M4 起进入发布门禁 |
| 尽力 | 其他 evergreen 浏览器 | 接受 issue，不阻塞发布 |
| 不支持 | IE、非 evergreen、旧版移动浏览器 | 不测试、不修复 |

## 3. 兼容性规则

- **编译器插件与 Kotlin 版本绑定**：Kairos 每次发布只对矩阵中的 Kotlin 版本负责；遇到不支持的 Kotlin 版本，构建应给出明确错误，而不是产生不可解释的 IR 崩溃。当前检测能力有限，升级前先看 release notes。
- **Gradle 插件与编译器插件同版本**：插件通过 `SubpluginArtifact("dev.kairos", "kairos-compiler-plugin", version)` 解析编译器插件，版本由构建时写入；不要手工混搭两个版本。
- **JVM 与 JS 的产物一致性**：同一份源码同时编译到两个目标时，两个目标必须使用同一插件版本与同一算法；跨版本比较类名不在支持范围。
- **JDK 21 是构建门槛**：用更低 JDK 启动 Gradle 会在 toolchain 解析阶段失败；这是预期行为，不是待修问题。
- **Node 只影响构建/测试**：浏览器运行时不需要 Node；Kotlin/JS 产物是普通 UMD bundle。
- **支持矩阵随版本更新**：每个公开发布必须在 release notes 中重述矩阵；范围缩小按破坏性变更处理，范围扩大必须先进 CI。

## 4. 不在支持范围

- JDK 17 及以下作为构建或 JVM 运行环境；
- Kotlin 2.2.20 之外的编译器版本（包括 2.3、2.4 预览版）；
- Gradle 8.x 或未经 wrapper 固定的 Gradle；
- htmx 1.x、非 evergreen 浏览器、移动原生/桌面壳；
- macOS/Windows 的一等构建支持（M4 前不承诺）；
- 手工 `-Xplugin` 接线（M1 后不属于官方路径）；
- 框架模块对 Ktor/Jimmer/SQLite 的版本绑定（这些只是示例依赖）。

## 5. 怎么验证

```bash
# 框架与示例构建（M1 后）
./gradlew :demo-web:assembleSite
./gradlew :blog-backend:run

# 产物级验收（需要先构建 demo）
node tools/verify.mjs --rebuild

# DOM 级验收（需先安装 jsdom）
cd tools && npm install && cd ..
node tools/browser-check.mjs

# 端到端（需先启动 blog，且 Chromium 能启动）
node tools/e2e.mjs
```

发布门禁要求：`./gradlew check` 从干净 clone 跑通；`verify.mjs --rebuild` 的确定性检查通过；浏览器矩阵中的承诺项通过。当前仓库尚未满足这些条件，矩阵也不会把“计划”改写成“已通过”。
