# 执行报告 · kairos · Phase 5 · R1

> 层级：权杖
> 核心目的：把"能用"升级为"用得下去"——更少的类、更顺手的 DSL、一套核心两个库，并补上响应式
> 日期：2026-10-30

## 1. 做了什么

| 文件 | 改动 |
|---|---|
| kairos-core（新） | 样式原语与分层；节点模型（属性 setter、`String.text`、`attribute(...)` delegate）；确定性 HTML 序列化（commonMain）；响应式核心 Signal/effect/computed |
| kairos-browser（新） | DOM 绑定：bindText / bindClassName / bindChildren / onClick / mountInto / swapClass |
| kairos-htmx（新） | `page(...)` 完整文档、`fragment(...)`、14 个 htmx 属性 |
| kairos-compiler-plugin | 去掉条件值机制；一个块折叠成**一个类**；类名 = 层 + 排序后的声明组哈希；映射升级 v3（declarations 数组 + conflicts） |
| kairos-cli | 适配 v3；why 打印全部声明与层冲突；merge 支持多条声明 |
| demo-web（原 demo） | 全部改用属性式 DSL；主题/密度改为 `when` 选整组；新增响应式计数器 |
| blog-backend（新） | Ktor + SQLite + Jimmer（SQLiteDialect）；首页/详情/评论/新建均为 Kairos 模板；htmx 片段 |
| blog-frontend（新） | Kotlin/JS 响应式增强：输入即筛选、主题切换、htmx swap 后重绑 |
| tools/e2e.mjs（新） | Playwright + Chromium 端到端 9 条 |

## 2. 验证结果

| 条目 | 命令 | 结果 |
|---|---|---|
| 产物级 | `node tools/verify.mjs --rebuild` | **12/12** |
| 浏览器级 | `node tools/browser-check.mjs` | **8/8** |
| 端到端 | `node tools/e2e.mjs` | **9/9** |
| 合并效果 | 产物统计 | demo-web：**18 个类承载 49 条声明**（原为 47 个类）；博客：后端 15 + 前端 2 → 合并 17 |
| 两路径一致 | A4 | hero/theme/card 在 JVM 全运行时与 JS 全折叠下逐字符相同 |
| 响应式 | B8 | 点击后文本 0→1→2，且页面仍无内联样式 |
| 端到端 | E1–E9 | 首页 3 篇、htmx 片段替换、发评论、新建文章（无整页刷新）、输入筛选、主题切换、0 控制台错误 |

## 3. 与计划的偏离

| # | 计划 | 实际 | 理由 |
|---|---|---|---|
| 1 | 保留档②的"条件值上提为类名" | 移除，改为整组样式选择 | 与"一个块一个类"不可兼得；整组选择同样零样式计算，且消除了部分物化的一整类 bug |
| 2 | htmx 从 CDN 加载 | 改为随包分发 `/htmx.js` | 端到端测试不应依赖外网 |
| 3 | 预计 Jimmer 可能需要回退到 JDBC | 一次成功（只需 Jackson databind） | 0.12.4 有 SQLiteDialect，KSP 与 Kotlin 2.2.20 兼容 |
| 4 | 计划未提 | pageroot 的 `<title>` 与片段装配最初没挂到父节点上 | 构建器是"返回节点"而非"自动追加"，已修正并写进 E1/E4 断言 |

## 4. 影响后续轮次的问题

1. **合并类与剪枝的边界**：一个类现在承载多条声明，剪枝粒度变成"整组样式"。若某元素只用到组里的一条声明，其余声明也会保留 —— 这是减少类数量的直接代价，P4 的剪枝收益会相应变小。
2. **块内动态值被禁止**：状态变化必须整组切换。当状态组合多（如 3 个独立开关 = 8 组样式）时，作者要写 8 个块；未来需要"变体组合"原语，但要先解决类名组合爆炸。
3. **响应式没有 keyed diff**：`bindChildren` 整块重建。博客列表量小没问题，量大会抖。
4. **DevTools 可读性**：合并类的类名仍是哈希。`kairos why` 能反查，但浏览器里看不到源码位置（P3 的 dev 类名方案因两路径不一致已撤除）。
5. **端口/数据**：博客默认 8080，数据库 `blog-backend/build/blog.db`，E2E 会写入测试数据；重跑前可删库。
