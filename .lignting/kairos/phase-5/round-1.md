# 方案文档 · kairos · Phase 5 · R1

> 层级：星币
> 上游：../phases.md
> 生成日期：2026-10-30

## 1. 核心目的

P1–P4 交付的是一条**能用**的管线，但它有三处结构性问题，继续往上堆功能只会把它们放大：

1. **类太多**：一条声明一个类。一个 5 条声明的样式块产出 5 个类名，HTML 和映射都被撑大。
2. **使用不人性化**：`attr(...)` / `cls(...)` / `text(...)` 是过程式调用，和"写 Kotlin"的直觉相反。
3. **平台定位被混在一起**：`demo` 同时有 jsMain 和 jvmMain，看起来像"一个库在两个平台跑"，而真实意图是**一套核心 + 两个完全不同的库**（浏览器端 / 服务端 htmx）。

同时，路线里最重要的另一半——**响应式**——只有"切换类名"的手工代码，没有 API。

这一轮把这三件事一次做完，并用两个真实示例（静态站点 + 博客）证明两种用法都成立。

## 2. 本轮范围

- 模块重构：`kairos-core`（common）+ `kairos-browser`（JS）+ `kairos-htmx`（JVM）；demo 拆成 `demo-web` 与 `demo-web-feature`。
- **合并类**：一个 `css { }` 块 → 一个类；类名由"层 + 全部声明"哈希得到；CSS 规则带多条声明。
- **块内不允许动态值**：需要按状态变化时用 `when` 选择整组样式（这是合并类成立的前提，也消灭了"部分物化"的整类 bug）。
- **DX**：`style` / `id` / `href` 等属性 setter；`"literal".text`；`var ElementNode.x by attribute("...")` 自定义属性。
- **响应式**：`signal` / `computed` / `effect`（依赖追踪、精确通知）+ DOM 绑定（`bindText` / `bindClassName` / `bindChildren`）。
- **博客示例**：Ktor + SQLite + Jimmer 后端（htmx 片段），Kotlin/JS 前端（响应式增强），样式由构建期合并。
- **Playwright 端到端验收**。

明确不做：伪类/媒体查询原语、列表 keyed diff、跨机器可复现的映射路径。

## 3. 依赖闭包结论

| 契约 | 生产方 | 消费方 | 结论 |
|---|---|---|---|
| 类名 | 运行时 `classNameFor(layer, declarations)` | 编译器 / CLI / 验收脚本 | 由"排序后的声明组"决定，三方各自实现必须一致 |
| 序列化 | `kairos-core` 的 `renderToHtml` | 服务端 / 静态生成 / 客户端 mount | 一份实现，两端逐字符一致 |
| 响应式 | `kairos-core` | `kairos-browser` 的绑定 | 核心不认识 DOM；绑定只做单属性写 |
| 样式来源 | 各模块编译器 | `kairos merge` | 跨模块按类名去重；冲突即哈希碰撞，必须报错 |

## 4. 改动顺序与步骤

| # | 改动 | 回滚点 | 验证 |
|---|---|---|---|
| S1 | 建 kairos-core / browser / htmx，删 kairos-runtime | 旧模块可恢复 | 各模块单独 build |
| S2 | 运行时改成合并类命名 + 块内禁止动态值 | 改回逐声明类名 | `gradle :demo-web:assembleSite` 类数骤降 |
| S3 | 插件去掉条件值机制，改为整块折叠 | 保留旧分支即回退 | A3 断言"类数 < 声明数" |
| S4 | 新 DSL（属性 setter、String.text、属性 delegate） | 旧调用式 API 可并存 | demo 页面重写并通过 |
| S5 | 响应式核心 + DOM 绑定 | 纯新增 | B8 断言点击后只有文本变化 |
| S6 | 博客后端（Jimmer/SQLite/Ktor）+ 前端 + 构建期合并 | 删模块 | E1–E9 |
| S7 | Playwright E2E | 删脚本 | 9/9 |

## 5. 验收标准细化

| # | 条目 | 命令 | 期望 |
|---|---|---|---|
| A13 | 合并类生效 | `node tools/verify.mjs` | 类数 < 声明数，且有多个类承载多条声明 |
| A14 | 两路径一致 | 同上 | JVM 全运行时 vs JS 全折叠，类名逐字符相同 |
| B8 | 响应式精确更新 | `node tools/browser-check.mjs` | 点击后文本 0→1→2，无内联样式 |
| E1–E9 | 真实浏览器端到端 | `node tools/e2e.mjs` | 9/9，含 htmx 片段、评论、新建、筛选、主题 |

## 6. 边界情况与失败场景

| 场景 | 预期 | 处理 |
|---|---|---|
| 块内出现运行时值 | 编译失败并提示改用整组选择 | 插件 reportError |
| 同层同属性重复声明 | 编译失败 | 组合检查 |
| htmx 目标选择器不存在 | 浏览器报 htmx:targetError | E2E 断言零控制台错误 |
| SQLite 方言 | Jimmer 0.12.4 自带 SQLiteDialect | 已实测 |
| 前端 bundle 未加载 | htmx 仍可用，只是没有筛选/主题 | 页面不依赖前端 bundle 才能工作 |

## 7. 与上游的偏离

| # | 上游 | 本层 | 理由 | 建议 |
|---|---|---|---|---|
| 1 | D7 原子化：一条声明一个类 | 改为**一个样式块一个类** | 用户明确要求减少类数量；合并后仍可按类名去重与剪枝 | 建议修订 direction.md §2-D |
| 2 | 档②"条件值上提为类名" | 改为**整组样式选择** | 块内动态值与"一个块一个类"不可兼得；整组选择同样零样式计算且更简单 | 建议修订 L9 的档位表 |
| 3 | 单一 kairos-runtime | 拆成 core + browser + htmx | 一套核心两个库，才是这条路线的真实形态 | 建议修订 phases.md 阶段 3 |
