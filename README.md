# Kairos

用 Kotlin 写界面，编译期把样式算完。

- **CSS in Kotlin**：`css { padding(16.px); borderRadius(12.px) }` 是一个真函数，编译器把它折叠成**一个类名**，并在编译期写出分层 CSS。
- **响应式**：`signal` / `computed` / `effect` 驱动最小更新，绑定 DOM 时只改类名和文本节点。
- **一套核心，两个库**：`kairos-browser` 给浏览器端；`kairos-htmx` 给服务端（直接渲染 HTML 字符串，喂给 htmx）。

## 架构

| 模块 | 平台 | 作用 |
|---|---|---|
| `kairos-core` | JVM + JS | 样式原语与分层、节点模型与 HTML 序列化、响应式核心（Signal/effect/computed） |
| `kairos-browser` | JS | DOM 绑定：`bindText` / `bindClassName` / `mountInto` / 事件 |
| `kairos-htmx` | JVM | 服务端页面与片段渲染、htmx 属性 |
| `kairos-compiler-plugin` | 编译器 | K2 IR 插件：折叠样式块、生成合并类、写出 CSS 与映射 |
| `kairos-cli` | JVM | `why` / `report` / `merge` / `prune` |

同一份节点模型与序列化器同时服务两端：服务端渲染的 HTML 和浏览器端 mount 出来的 DOM 逐字符一致。

## 一个样式块 = 一个类

```kotlin
val cardStyle: String = css {
    backgroundColor("#ffffff")
    border("1px solid #e3e5ea")
    borderRadius(Radius.LG)
    padding(Spacing.XL)
}
```

五个声明，**一个类名**。在此之上还有第二级压缩：**同一个编译单元内出现两次的声明会被提取成独立类**，
原类只保留残差，元素多挂一个类：

```
.kb7bc5... { color: #16181d; }          <- 块类，font-size 被提走了
.kbbd7e... { font-size: 14px; }          <- 共享类，全站只此一份
```

效果（demo-web）：**49 条声明 → 25 个类**，同一层内不存在重复声明。

> 提取的边界：只在**同一编译单元**内做。跨层不合（层是类身份的一部分），跨模块也不合
> （需要全程序知识，事后合并会破坏运行时切换的类）。

层级决定优先级，永远不靠书写顺序：

```
kairos.reset < kairos.base < kairos.variant < kairos.override
```

同一层里同一个属性写两次 = **编译错误**（一个 class 列表无法表达"参数里的先后"），而不是悄悄按样式表顺序决定。

**块内的三元表达式也会被优化**——分支全为常量时，编译器为每种组合生成一个类，再生成一个选类名的条件：

```kotlin
fun badgeClass(urgent: Boolean): String = css {
    fontSize(if (urgent) "16px" else "12px")
    fontWeight("700")
}

// 产物 JS：
// return urgent ? 'kb9e64c8828818 kbcc4519ce1390' : 'kb9e64c8828818 kbf030c5ce2844';
```

样式值从未进入运行时，条件只负责选类名。

## 写起来是什么样

```kotlin
div {
    style = cardStyle                 // 属性赋值，不是 cls(...)
    id = "post-1"
    "hello".text                      // String 上的扩展属性
    button {
        dataKairosId = "toggle"       // 自定义属性：var ElementNode.dataKairosId by attribute("data-kairos-id")
        type = "button"
        "Click".text
    }
}
```

属性都是**属性**：`style`、`id`、`href`、`type`… 自定义属性用一行 delegate 就能定义，全局复用。

响应式绑定：

```kotlin
val count = signal(0)
byId("counter")?.let { bindText(it, count) }
onClick(byId("increment")) { count.update { it + 1 } }
```

## 两个可运行的示例

### 1. 静态站点（浏览器端）

```bash
gradle :demo-web:assembleSite
demo-web/build/site/index.html
```

构建期在 JVM 上渲染静态 HTML，编译期生成 CSS，客户端 bundle 只做类名与文本更新。

### 2. 博客（服务端 htmx + 客户端增强）

```bash
gradle :blog-backend:run
# 打开 http://127.0.0.1:8080
```

- Ktor 后端，SQLite + Jimmer（`SQLiteDialect`）。
- 首页、文章详情、评论、新建文章都是 htmx 片段，服务端用 Kairos 模板渲染；深链刷新返回整页。
- **Markdown**：正文与评论都按 Markdown 渲染（标题/列表/引用/代码块/粗斜体/行内代码/链接）。
- **亮/暗主题**：颜色走 CSS 变量，切换只改 `<html>` 的一个类，并用 localStorage 记住。
- 客户端 bundle（`blog-frontend`）负责输入即筛选、主题切换，signal + effect 驱动。
- 两个模块的样式由构建期 `kairos merge` 合并成一份 CSS 供服务端直接吐出。

**服务器端口：8080**（可用 `PORT` 环境变量改）。数据库文件默认 `blog-backend/build/blog.db`，删掉即重新播种。

## 验收

| 脚本 | 覆盖 | 结果 |
|---|---|---|
| `node tools/verify.mjs --rebuild` | 折叠、声明提取（无重复）、三元折叠、级联、跨模块合并剪枝、确定性、产物自动重建 | 14/14 |
| `node tools/browser-check.mjs` | jsdom 加载静态站点、类名组切换、响应式计数、无内联样式 | 8/8 |
| `node tools/e2e.mjs` | **Playwright + Chromium**：首页渲染、htmx 片段替换、发表评论、新建文章、输入筛选、主题切换与持久化、Markdown 渲染、零控制台错误 | 10/10 |

浏览器级验收需要先装依赖：

```bash
cd tools && npm install && npx playwright install chromium && cd ..
```

## 调试

```bash
gradle -q :kairos-cli:run --args='why <class> --map blog-backend/build/kairos/kairos.map.json'
gradle -q :kairos-cli:run --args='report --map blog-backend/build/kairos/kairos.map.json'
```

`why` 会打印类里的每条声明、它对应的源码行与 caret，以及和其它层的冲突。

## 已知限制

- 样式块内的条件值必须是**全常量分支**；一个块最多 8 种组合，超出会报错（需要变体合取的类名代数）。
- 声明提取只在同一编译单元内生效；跨层/跨模块的相同声明仍会出现两次。
- 伪类（`:hover`）、媒体查询、```supports` 还没有原语。
- `styles()` 不建模 shorthand/longhand。
- 剪枝依赖"类名是字面量"，运行时拼接的类名会被误删。
- 映射记录绝对路径，跨机器不逐字节一致（同机全量构建一致）。
- 响应式列表目前整块重建（`bindChildren`），没有 keyed diff。

## 文档

- 方向：[direction.md](direction.md)
- 阶段表：[phases.md](.lignting/kairos/phases.md)
- 总计划：[roadmap.md](.lignting/kairos/roadmap.md)
- 最近一轮：[P5 计划](.lignting/kairos/phase-5/round-1.md) · [执行报告](.lignting/kairos/phase-5/round-1.report.md)
