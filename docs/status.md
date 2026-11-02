# 项目状态

> 对外展示用的当前状态页。详细路线见 [plan-1.0.md](../.lignting/kairos/plan-1.0.md)，逐项验收见 [release.md](contracts/release.md)。

## 一句话

Kairos 是一个 Kotlin 编译器驱动的 **HTML-first UI 工具链**：样式在编译期折叠成类名，产物是纯 HTML/CSS + 极小的运行时 JS。当前版本 **0.1.0**，1.0 的功能面已经打通，卡在正式发布凭证与外部采用验证。

## 现在能做什么

| 能力 | 状态 |
|---|---|
| 编译期样式折叠（一个块一个类、共享声明提取） | ✅ |
| 条件样式：`hover`/`focus-visible`/`active`/`::before`/`media`/`container`/`supports` | ✅ |
| 块内条件值按属性拆分的轴模型（无组合爆炸） | ✅ |
| 分层级联 + shorthand/longhand 冲突诊断 | ✅ |
| Gradle 插件一键接入（plugin id + marker） | ✅ |
| 组件库以二进制依赖携带样式（jar 内嵌 + 自动合并） | ✅ |
| 响应式：signal / computed / effect / Owner / batch / Resource | ✅ |
| SSR 增强：`data-kairos-mount`、htmx 重绑、焦点跟随、aria-live | ✅ |
| keyed list、模态框焦点管理 | ✅ |
| 安全：转义、URL 白名单、raw HTML 显式入口、CSP nonce、CSRF | ✅ |
| 工具链：`init` / `check` / `doctor` / `coverage` / `why` / `merge` / `prune` | ✅ |
| 文档站（自举）、三套 starter、四篇 guide | ✅ |

## 怎么验证

```bash
./gradlew -PframeworkOnly=true publishToMavenLocal
./gradlew build apiCheck
node tools/verify.mjs --rebuild        # 17 项产物级
node tools/browser-check.mjs           # 9 项浏览器级
node tools/style-browser-check.mjs     # 5 项条件样式
node tools/runtime-check.mjs           # 4 项运行时（keyed list、模态框焦点）
node tools/consumer-check.mjs          # 5 项 clean-room 消费
node tools/library-check.mjs           # 5 项组件库二进制消费
node tools/starter-check.mjs           # 5 项 starter 生成
node tools/docs-check.mjs              # 5 项文档站
node tools/a11y-check.mjs              # axe 扫描
node tools/cascade-check.mjs           # 第三方 CSS 级联契约
node tools/cross-browser-check.mjs     # Chromium / Firefox（CI 含 WebKit）
bash tools/e2e-ci.sh                   # 17 项端到端
node tools/repro-check.mjs             # 跨目录确定性
node tools/benchmark.mjs               # 体积/时间预算
```

## 已知限制（诚实版）

- **没有正式发布到 Maven Central / Plugin Portal**：需要账号与签名密钥，当前只在 mavenLocal 与本地仓库验证。
- **没有外部真实项目**：用 `examples/` 下的 clean-room 工程与组件库示例做等价验收，不代表真实采用。
- **跨浏览器**：Chromium 与 Firefox 在本机通过同一套检查（hover / container query / `::before` / 级联）；WebKit 因本机缺系统库，只在 CI job 中执行。
- **开发期体验是 Gradle continuous build + 手动刷新**，没有 HMR。
- 支持矩阵较窄：Kotlin 2.2.20 / JDK 21 / Gradle 9.7.1；详见 [support-matrix.md](contracts/support-matrix.md)。
- 组件 props/slots 是"普通 Kotlin 函数 + 参数"的约定，没有框架级组件运行时。
