# kairos · 总计划

> 类型：总览视图（人读；**不是任何命令的输入**，冲突以源文档为准）
> 基准：../../direction.md · phases.md · phase-5/round-1.md
> 最后同步：2026-10-29

## 1. 一句话现状与目标
<!-- owner: /lts 首建；之后由最近一次改动"下一步"的命令覆盖本行 -->

**现状（截至 2026-10-31）**：方向已按 1.0 路线修订（[plan-1.0.md](plan-1.0.md)）；M0 完成、M1 收尾中；大轮次见 §2；当前轮见 §3；回填见 §4
**目标**：用"状态必须可穷举"这条约束，把数据驱动前端框架的静态产物做到极致——绝大部分 UI 由 HTML/CSS 直出，JS 只留事件与最小更新（[direction.md 第 1 节](../../direction.md#1-目标与非目标)）
**待拍板**：见 [direction.md 第 5 节](../../direction.md#5-待决问题)；阶段归属见 [phases.md 第 4 节](phases.md#4-待决问题归属)

## 2. 大轮次
> 派生自 [phases.md](phases.md) · 同步于 2026-10-29；冲突以 phases.md 为准

<!-- owner: /ltc；行可增改；粒度到"阶段" -->

| # | 核心目的 | 要做什么 | 核心验收标准 | 链接 |
|---|---|---|---|---|
| P1 | 整套方向一次真实编译器往返都没有过；不在这里证伪，后面全建在推理上 | enum 模式轴 + 样式原语 → 编译期 CSS/类名 → 可打开的静态页面 | ① 产物 JS 无样式原语运行时调用<br/>② 两次构建逐字节一致<br/>③ 不可折叠块与折叠块行为/类名一致 | [phases.md](phases.md) · [对话](phases.dialogue.md) |
| P2 | P1 只证明"一个样式块能折叠"，任何静态抽取器都能做到，不构成身份 | 四层级联 + styles() 组合 + 枚举驱动变体（条件常量上提为类） | ① 跨层冲突按层序消解<br/>② 只保留胜者（折叠路径）<br/>③ 冲突可反查 | [phases.md](phases.md) · [对话](phases.dialogue.md) |
| P3 | 建模成本落在作者身上；没有零残留封装，作者会绕开约束 | 用户 inline 封装折叠、dev 类名、反查 CLI | ① 用户封装零降级<br/>② 类名可反查到源码行<br/>③ 级联胜负可反查 | [phases.md](phases.md) · [对话](phases.dialogue.md) |
| P4 | 验证信号 5 要求写出第二个项目且不改框架源码，第二个项目必然跨模块 | 第二个模块 + 合并 + 剪枝，接进构建 | ① 合并无重复无遗漏<br/>② 跨模块去重生效<br/>③ 剪枝生效且页面仍可交互 | [phases.md](phases.md) · [对话](phases.dialogue.md) |
| P5 | P1–P4 的产物"能用"但三处结构性缺陷：类太多、DSL 不顺手、平台定位混淆；且响应式缺失 | 一套核心两个库（core/browser/htmx）、一个块一个合并类、属性式 DSL、Signal 响应式，并落到静态站点与 htmx 博客两个示例 | ① 合并类（类数 < 声明数）<br/>② JVM/JS 两路径类名逐字符一致<br/>③ Playwright 端到端 9 条 | [phases.md](phases.md) · [对话](phases.dialogue.md) |
| P6 | P5 的合并类之间仍有重复声明；块内三元被一刀切禁止；博客只有骨架 | 声明提取（共享类 + 残差类）、块内三元折叠为选类名、Markdown 与全局亮暗主题 | ① 同层无重复声明<br/>② 三元产物为条件类名<br/>③ E2E 覆盖 Markdown 与主题持久化 | [phases.md](phases.md) · [对话](phases.dialogue.md) |
| M0 | 原型定位与 1.0 生态目标冲突 | 修订 direction.md、ADR×5、公共 API/支持矩阵/非目标/版本契约 | 文档之间不再自相矛盾 | [plan-1.0.md](plan-1.0.md) · [docs/](../../docs/) |
| M1 | 原型不可安装、不可复现、不可解释 | Gradle 插件、版本目录、发布、BOM、相对路径 manifest、clean-room consumer、测试与 CI | 插件按 ID 接入；mavenLocal + consumer；跨目录逐字节一致；CI 绿 | [plan-1.0.md](plan-1.0.md) |

**边界变更**（只追加，不改历史）
- 2026-10-30 新增 P5：架构重构（合并类 / 双库 / 响应式），由用户对类数量、DX 与平台划分的反馈驱动 → [L4 · 阶段划分与排序理由](phases.dialogue.md#l4--阶段划分与排序理由)
- 2026-10-29 首建，四阶段沿用上游骨架、核心目的重写 → [L4 · 阶段划分与排序理由](phases.dialogue.md#l4--阶段划分与排序理由)

## 3. 当前轮
> 派生自 [plan-1.0.md](plan-1.0.md) · 同步于 2026-10-31；冲突以计划原文为准

<!-- owner: /ltp；整块替换，只保留最近一轮 -->

**M1 · 发布与构建底座**（[计划 §M1](plan-1.0.md)）

- 核心目的：让 Kairos 可安装、可复现、可解释——不再是靠手工 `-Xplugin` 接线的原型
- 已落地：Gradle Wrapper 9.7.1、根 build、版本目录、`kairos-gradle-plugin`（`dev.kairos` + marker）、maven-publish/BOM/mavenLocal、manifest 相对路径、合并 manifest、clean-room consumer、core 单元测试、CI
- 待完成：compiler golden/negative 测试、CLI 测试、Maven Central 发布（需凭证）、跨浏览器验收
- 验收：`node tools/verify.mjs --rebuild`（16 项）、`tools/consumer-check.mjs`（5 项）、`tools/repro-check.mjs`、`tools/e2e-ci.sh`
- 回滚：构建改动按模块独立；发布坐标 0.1.0 可覆盖

## 4. 执行回填
> 事实来源：各轮 report（未落盘时链 round 文档）· 同步于 2026-10-29 · 冲突以 report 为准 · 逐轮追加

<!-- owner: /ltw；每轮一行 -->

| 轮次 | 做了什么 | 验证结果 | 与计划的偏离 | 链接 |
|---|---|---|---|---|
| 更正 P1·R1 | 插件产物已声明为 `compileKotlinJs` 的任务输出，并对该任务关闭构建缓存；产物目录改为单任务拥有 | `gradle clean :demo:assembleSite` 连续两次 → 稳定产出完整站点 | 关闭 R1 报告第 4 节问题 1：缓存命中或删除产物目录都会静默跳过插件 | [R2 report 补记](phase-1/round-2.report.md) |
| P1·R2 | 节点模型 + 确定性 HTML 序列化；真实 demo 页面；静态站点装配；jsdom 浏览器级验收 | `node tools/browser-check.mjs` → 7/7；`node tools/verify.mjs` → 7/7 | 补顶层 html 构建器；颜色移到主题变体；类名替换按组；补 script 标签；单文本叶子单行 | [report](phase-1/round-2.report.md) · [计划](phase-1/round-2.md) |
| P1·R3 | 类型化 helper（27 个）、单位简写、常量刻度；用抽象解释器替换字面量识别器；条件值上提为类名；诊断带行列号 | `node tools/verify.mjs` → 降级仅 1 条刻意 fixture；产物 JS 出现条件类名常量；规则 4 → 47 | 框架 helper 改为"命名即属性"；多分支 when 需整体保留 block（否则后端崩溃） | [report](phase-1/round-3.report.md) · [计划](phase-1/round-3.md) |
| P2·R1 | 四层级联（reset/base/variant/override）、styles() 组合与编译期消解、冲突记录、CSS 按层分组 | `node tools/verify.mjs` → A6/A7/A9/A17 通过；5 条 resolution（override 胜 base、base 胜 reset） | merge 首版按字母排序层 → 改为按 layerIndex 并新增 A9 | [report](phase-2/round-1.report.md) · [计划](phase-2/round-1.md) |
| P3·R1 | 用户 inline 封装零残留、映射行列号、dev 类名、kairos CLI（why/report/merge/prune） | `kairos why` → 输出源码行与 caret、双向级联胜负；dev 构建类名形如 `kb15b4c8c5_Site_L267` | 捕获失败噪音先加后收敛；dev 验证延后到 P4 补做 | [report](phase-3/round-1.report.md) · [计划](phase-3/round-1.md) |
| P4·R1 | 第二个模块 demo-feature；CLI merge 去重合并；CLI prune 按产物剪枝；装配链接入 | `node tools/verify.mjs --rebuild` → **12/12**；`node tools/browser-check.mjs` → **7/7**；63 条模块规则 → 51 去重 → 50 落地 | merge 层序 bug；剪枝实际只删 1 条（规模小）；dev 验证在收尾补跑 | [report](phase-4/round-1.report.md) · [计划](phase-4/round-1.md) |
| P5·R1 | 模块重构为 core/browser/htmx；合并类（一块一类）；属性式 DSL 与 String.text；Signal/effect/computed + DOM 绑定；Ktor+SQLite+Jimmer 博客后端与 Kotlin/JS 前端；Playwright E2E | `node tools/verify.mjs --rebuild` → **12/12**；`node tools/browser-check.mjs` → **8/8**；`node tools/e2e.mjs` → **9/9**；demo-web 49 条声明 → **18 个类**（原 47） | 移除块内条件值（与合并类不可兼得）；htmx 改为随包分发；Jimmer 一次成功无需回退 | [report](phase-5/round-1.report.md) · [计划](phase-5/round-1.md) |
| P6·R1 | 声明提取（两遍扫描）；块内三元/when 枚举组合；Markdown 渲染器；CSS 变量主题 + 持久化；深链返回整页 | `node tools/verify.mjs --rebuild` → **14/14**；`browser-check` → **8/8**；`e2e` → **10/10** | 提取统一为"每种组合一组声明"；styles() 无法解析时不再报错；JVM 目标也挂插件；深链修复 | [report](phase-6/round-1.report.md) · [计划](phase-6/round-1.md) |
| M0 | 生态化定位修订；5 份 ADR；4 份契约 | 118 条相对链接可解析；文档无 TODO | 上游 direction.md 只加修订章节，不删历史 | [plan](plan-1.0.md) · [docs](../../docs/) |
| M1 | Gradle 插件 + 发布 + BOM + 相对路径 manifest + 合并 manifest + consumer + 测试 + CI | `verify` 17/17；`consumer-check` 5/5；`browser-check` 9/9；`style-browser-check` 5/5；`e2e` 10/10；`repro-check` 3/3；core 单测 + 插件 golden/negative 通过 | 用 mavenLocal 发布坐标替代 monorepo project 依赖；插件输出改 `build/kairos/<target>/<compilation>`；正式发布待凭证 | [plan](plan-1.0.md) |
| M2 | 样式只有层与块内常量分支；没有伪类/media/container/supports；库样式靠手工 merge | 条件模型 v1、manifest v4、轴模型、shorthand 诊断、库样式资产、strict/coverage、错误码、级联契约 | `verify` 17/17、`style-browser-check` 5/5、`cascade-check` 5/5、`library-check` 5/5 | [plan](plan-1.0.md) · [ADR 0002](../../docs/adr/0002-style-condition-model.md) |
| M3 | 客户端只有手工 byId + 全量重建；htmx swap 靠 revision signal 兜底 | owner/dispose/batch/resource、声明式 `data-kairos-mount` 挂载、htmx 重绑、keyed list | `runtime-check` 3/3、`e2e` 10/10（swap 后过滤仍生效）；blog 删除 revision signal | [plan](plan-1.0.md) · [ADR 0005](../../docs/adr/0005-ssr-enhancement-model.md) |
| M4 | XSS/CSRF/CSP 完全没管；CLI 只有 why/report/merge/prune | 安全基线、CLI 工具链、benchmark 预算、starter 生成、axe 扫描 | `e2e` 17/17；`starter-check` 5/5；`a11y-check` 0 违规；benchmark 预算通过 | [plan](plan-1.0.md) · [security.md](../../docs/contracts/security.md) |
| M5 | docs 只有契约与计划，没有教程；starter 只有骨架 | 自举文档站、三个 starter、guides、治理文档、status 页 | `docs-check` 5/5（含 status/guides/migration）、`starter-check` 5/5 | [plan](plan-1.0.md) · [status](../../docs/status.md) |
| M6 | 没有 API 契约与发布清单 | `@ExperimentalKairosApi`、二进制兼容、发布门禁、治理文档、本地仓库发布验证 | `apiCheck` 通过；本地仓库 16 jars + plugin marker；治理文档落地 | [plan](plan-1.0.md) · [release.md](../../docs/contracts/release.md) · [governance.md](../../docs/contracts/governance.md) |

> 影响后续轮次的问题：见各轮 report 第 4 节；跨轮最要紧的三条——① 插件产物必须声明为任务输出；② 条件值不能跨 scope 搬移；③ 剪枝对运行时拼接的类名无能为力。


## 5. 决策索引
<!-- owner: 各层；只追加；只收影响边界/方向的决策 -->

- 方向：用"状态必须可穷举"换全静态产物与针对性优化 —— [L10 · 定型相](../../direction.dialogue.md#l10--定型相方向文档成文与落盘)
- 阶段：P1 的核心验收只对样式侧负责，HTML 直出降为实现轮次 —— [L3 · 渲染模型与 P1 的收窄](phases.dialogue.md#l3--渲染模型与-p1-的收窄)
- 阶段：Q9 收窄为两段式（@layer 定类别全序 + 编译期消解同层同属性） —— [L2 · 两段式级联的条件与代价](phases.dialogue.md#l2--两段式级联的条件与代价)
- 轮次：Q14 关闭——渲染模型取"构建期 JVM 渲染静态 HTML" —— [L1 · 渲染模型的三条路与为什么选第一条](phase-1/round-2.dialogue.md#l1--渲染模型的三条路与为什么选第一条)
- 轮次：② 档原语按**类名组**设计，不是单个类名 —— [L3 · 一次切换是多条类的替换](phase-1/round-2.dialogue.md#l3--一次切换是多条类的替换)
- 阶段：变体原语不另造 DSL，直接用 Kotlin 的 `if` / `when`，分支常量上提为类名 —— [P2 report](phase-2/round-1.report.md)
- 阶段：框架 helper 用"名字即属性"（camelCase → kebab-case）解析，不维护映射表 —— [P1·R3 report](phase-1/round-3.report.md)

- 架构：一个**样式块一个合并类**（取代逐声明原子类） —— [P5 report](phase-5/round-1.report.md)
- 架构：块内必须是常量，状态变化用 `when` 选整组样式 —— [P5 report](phase-5/round-1.report.md)
- 架构：一套核心两个库（kairos-core + kairos-browser / kairos-htmx） —— [P5 plan](phase-5/round-1.md)
- 轮次：响应式核心（signal/computed/effect）纳入框架本体 —— [P5 report](phase-5/round-1.report.md)
## 6. 文档地图
<!-- owner: 各层；新文档落盘时追加一行 -->

| 文档 | 层 | 作用 | 链接 |
|---|---|---|---|
| direction.md | 宝剑 | 目标、非目标、关键决策 | [打开](../../direction.md) |
| direction.dialogue.md | 宝剑 | 方向讨论与否决记录 | [打开](../../direction.dialogue.md) |
| phases.md | 圣杯 | 阶段表与核心验收标准 | [打开](phases.md) |
| phases.dialogue.md | 圣杯 | 阶段划分讨论 | [打开](phases.dialogue.md) |
| phase-1/round-1.md | 星币 | R1 改动顺序、回滚、验证 | [打开](phase-1/round-1.md) |
| phase-1/round-1.dialogue.md | 星币 | R1 排期与取舍 | [打开](phase-1/round-1.dialogue.md) |
| phase-1/round-1.report.md | 权杖 | R1 四段式执行汇报 | [打开](phase-1/round-1.report.md) |
| phase-1/round-2.md | 星币 | R2 计划 | [打开](phase-1/round-2.md) |
| phase-1/round-2.dialogue.md | 星币 | R2 渲染模型选型 | [打开](phase-1/round-2.dialogue.md) |
| phase-1/round-2.report.md | 权杖 | R2 执行汇报 | [打开](phase-1/round-2.report.md) |
| phase-1/round-3.md | 星币 | R3 计划（类型化原语） | [打开](phase-1/round-3.md) |
| phase-1/round-3.report.md | 权杖 | R3 执行汇报 | [打开](phase-1/round-3.report.md) |
| phase-2/round-1.md | 星币 | P2 计划（级联代数） | [打开](phase-2/round-1.md) |
| phase-2/round-1.report.md | 权杖 | P2 执行汇报 | [打开](phase-2/round-1.report.md) |
| phase-3/round-1.md | 星币 | P3 计划（封装与反查） | [打开](phase-3/round-1.md) |
| phase-3/round-1.report.md | 权杖 | P3 执行汇报 | [打开](phase-3/round-1.report.md) |
| phase-4/round-1.md | 星币 | P4 计划（跨模块与剪枝） | [打开](phase-4/round-1.md) |
| phase-4/round-1.report.md | 权杖 | P4 执行汇报 | [打开](phase-4/round-1.report.md) |
| phase-5/round-1.md | 星币 | P5 计划（架构重构 + 响应式 + 博客） | [打开](phase-5/round-1.md) |
| phase-5/round-1.report.md | 权杖 | P5 执行汇报 | [打开](phase-5/round-1.report.md) |
| phase-6/round-1.md | 星币 | P6 计划（提取 + 三元 + 博客） | [打开](phase-6/round-1.md) |
| phase-6/round-1.report.md | 权杖 | P6 执行汇报 | [打开](phase-6/round-1.report.md) |
| plan-1.0.md | 总计划 | M0–M6 的 1.0 路线与执行进度 | [打开](plan-1.0.md) |
| ../../docs/ | 契约 | ADR 与公共契约 | [打开](../../docs/) |
