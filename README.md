# Kairos

> 0.1.0（1.0 之前的公开预览）：已可安装、可复现、可解释。完整路线见
> [plan-1.0.md](.lignting/kairos/plan-1.0.md)，契约见 [docs/](docs/)。

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

## 快速开始（新工程）

```kotlin
// settings.gradle.kts
pluginManagement { repositories { mavenLocal(); mavenCentral(); gradlePluginPortal() } }

// build.gradle.kts
plugins {
    kotlin("jvm") version "2.2.20"
    id("dev.kairos") version "0.1.0"
}
dependencies {
    implementation("dev.kairos:kairos-core:0.1.0")
    implementation("dev.kairos:kairos-htmx:0.1.0")
}
```

应用插件后，`css { }` 会被折叠成类名，样式写到
`build/kairos/<target>/<compilation>/kairos.css`。用户 build 文件里**没有** `-Xplugin`、jar 路径和
`-P plugin:dev.kairos:*` 参数。

完整可运行例子：[examples/consumer-static](examples/consumer-static)（只消费发布坐标，CI 里由
`node tools/consumer-check.mjs` 验收）。

框架自身开发时，先发布到 mavenLocal 再构建示例（示例通过发布坐标使用插件）：

```bash
./gradlew -PframeworkOnly=true publishToMavenLocal
./gradlew :demo-web:assembleSite
```

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

## 条件样式：hover / media / container / @supports

条件块被编译成**独立的类**，条件本身进入类名身份（同样的声明 + 不同的条件 = 不同的类）：

```kotlin
val buttonStyle: String = css {
    backgroundColor("#ffffff")
    hover { backgroundColor("#f2f3f7") }
    focusVisible { borderColor("#7c5cff") }
    media("(max-width: 640px)") { padding("6px 10px") }
}
```

产物：

```css
.kb3ebe4db7d8db { background-color: #ffffff; }
.kb3ebe4db7d8db:hover { background-color: #f2f3f7; }
.kbede9e7adee13:focus-visible { border-color: #7c5cff; }
@media (max-width: 640px) {
  .kbc46ba925b777 { padding: 6px 10px; }
}
```

支持的条件：`:hover`、`:focus`、`:focus-visible`、`:active`、`:disabled`、`:checked`、
`::before`、`::after`、`@media`、`@container`、`@supports`；可以嵌套（`media { hover { ... } }`）。
条件也写进 manifest 的 `conditions` 字段，`kairos why` 会显示。

```kotlin
val shellStyle: String = css {
    padding("0 24px 64px 24px")
    container("page (max-width: 640px)") { padding("0 16px 40px 16px") }
}

val pageStyle: String = css {
    declaration("container-type", "inline-size")
    declaration("container-name", "page")
}
```

边界：条件组里的值必须是编译期常量；条件块不能和块内的 `when` 值同时出现（编译器会报错让你拆分）。

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
./gradlew :demo-web:assembleSite
demo-web/build/site/index.html
```

构建期在 JVM 上渲染静态 HTML，编译期生成 CSS，客户端 bundle 只做类名与文本更新。

### 2. 博客（服务端 htmx + 客户端增强）

```bash
./gradlew :blog-backend:run
# 打开 http://127.0.0.1:8080
```

- Ktor 后端，SQLite + Jimmer（`SQLiteDialect`）。
- 首页、文章详情、评论、新建文章都是 htmx 片段，服务端用 Kairos 模板渲染；深链刷新返回整页。
- **Markdown**：正文与评论都按 Markdown 渲染（标题/列表/引用/代码块/粗斜体/行内代码/链接）。
- **亮/暗主题**：颜色走 CSS 变量，切换只改 `<html>` 的一个类，并用 localStorage 记住。
- 客户端 bundle（`blog-frontend`）负责输入即筛选、主题切换，signal + effect 驱动。
- 两个模块的样式由构建期 `kairos merge` 合并成一份 CSS 供服务端直接吐出。

**服务器端口：8080**（可用 `PORT` 环境变量改）。数据库文件默认 `blog-backend/build/blog.db`，删掉即重新播种。

## 运行时：声明式挂载与 keyed list

服务端/构建期渲染 HTML，客户端只给声明了 `data-kairos-mount` 的元素绑定行为：

```kotlin
onMount("theme-toggle") { scope ->
    scope.on("click") { /* ... */ }
}
autoMount()   // 首次扫描 + htmx:afterSwap 后自动重扫
```

`autoMount()` 会在每次 htmx swap 后重新扫描，旧子树的行为随 owner 一起 dispose，新片段自动绑定——博客的过滤/主题切换不再需要手工 `byId` 或 revision signal。

列表更新走 keyed diff，DOM 节点按 key 复用：

```kotlin
bindKeyedChildren(container, items, key = { it.id }, render = { item -> el("li") { item.label.text } })
```

生命周期用 owner 管理，`owner.dispose()` 一次性释放它创建的所有 effect；`Batching.batch { }` 把同一轮的多个写入合并成一次 effect 执行。

## 文档站（自举）

`docs-site` 用 Kairos 自己渲染 `README.md`、`direction.md`、`docs/**/*.md` 与 1.0 路线计划：

```bash
./gradlew :docs-site:assembleSite
docs-site/build/site/index.html
```

Markdown 的标题、段落、代码块、列表、引用和链接都挂编译期类（`MarkdownStyles`），页面样式全部由编译器折叠；源的相对 `.md` 链接会被重写成生成页面。

## 可访问性

- htmx 片段替换后焦点跟随新片段（`focusAfterSwap`），键盘用户不会丢位置
- 评论区是 `role=status`、`aria-live=polite`，新评论会被读屏播报
- `e2e` 里有键盘路径验收：Tab 到文章链接、Enter 打开、检查焦点落在 `#detail-title`

## 安全

- 文本/属性默认转义；URL 属性走协议白名单（`javascript:`、`data:`、`vbscript:` 被拒绝），Markdown 里的不安全链接降级为纯文本
- 原始 HTML 只能通过 `unsafeRawHtml(...)` 显式写入
- `page(...)` 支持 CSP nonce；博客示例下发严格 CSP、`nosniff`、`Referrer-Policy`
- 表单带 `_csrf` 隐藏字段，变更路由校验失败返回 403；`htmxHeader(...)` 可为 htmx 请求统一加头

细节与已知限制见 [security.md](docs/contracts/security.md)。

## 验收

| 脚本 | 覆盖 | 结果 |
|---|---|---|
| `node tools/verify.mjs --rebuild` | 折叠、声明提取（无重复）、三元折叠、级联、跨模块合并剪枝、确定性、产物自动重建 | 14/14 |
| `node tools/browser-check.mjs` | jsdom 加载静态站点、类名组切换、响应式计数、无内联样式 | 8/8 |
| `node tools/e2e.mjs` | **Playwright + Chromium**：CRUD（新建/编辑/删除/排序）、htmx 片段、筛选、主题、Markdown、XSS、CSP/CSRF、键盘焦点、aria-live、表单错误回显 | 20/20 |
| `node tools/consumer-check.mjs` | 空工程只用发布坐标 + `id("dev.kairos")` 构建，产物类名/CSS 一致，manifest 只用相对路径 | 5/5 |
| `node tools/repro-check.mjs` | 复制到另一个绝对路径构建，CSS / manifest / report 逐字节一致 | 3/3 |
| `node tools/style-browser-check.mjs` | **Playwright**：真实 hover、focus-visible、`@supports`、container/media 窄视口、`::before` | 5/5 |
| `node tools/library-check.mjs` | 组件库以 jar 形式发布样式资产，消费方插件自动收集并合并（hover 规则不丢） | 5/5 |
| `node tools/runtime-check.mjs` | **Playwright**：keyed list 复用 DOM 节点、模态框焦点陷阱/Escape/焦点恢复、更新后无内联样式/脚本错误 | 4/4 |
| `node tools/starter-check.mjs` | `kairos init` 生成 static / ktor-htmx / ktor-incremental 三个工程并构建；`kairos check` 校验产物 | 5/5 |
| `node tools/benchmark.mjs` | CSS / app bundle / HTML 体积与全量编译时间对照预算 | 预算通过 |
| `node tools/docs-check.mjs` | 自举文档站：17 页、导航/标题/样式、无裸 markdown、内部链接全部可解析 | 5/5 |
| `node tools/a11y-check.mjs` | axe-core 扫描演示页（WCAG 2A/2AA），serious/critical 违规必须为 0 | 0 违规 |
| `node tools/cascade-check.mjs` | 级联契约：Kairos 规则全在 `@layer` 内、第三方未分层 CSS 按规范胜出、条件规则同样在层内 | 5/5 |
| `node tools/cross-browser-check.mjs` | Chromium + Firefox 的 hover / container query / `::before` / 级联一致性；WebKit 在 CI 执行 | 2/2（本机） |
| `./gradlew apiCheck` | 二进制兼容校验（`api/*.api` dump），公共签名变化必须显式更新 | 通过 |

浏览器级验收需要先装依赖：

```bash
cd tools && npm install && npx playwright install chromium && cd ..
# 无 root 的精简环境（容器/WSL）用：
bash tools/install-browser-deps.sh
export LD_LIBRARY_PATH="$HOME/.local/lib/kairos-browser-deps/usr/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
```

## CLI

```bash
kairos init <dir> [--template static|ktor-htmx|ktor-incremental]   # 生成可运行工程
kairos check --map <kairos.map.json> --css <kairos.css> [--strict]  # 校验产物一致性
kairos coverage --map <kairos.map.json>           # 折叠覆盖率与条件统计
kairos doctor                                      # JDK / Node / Gradle / mavenLocal 体检
kairos why <class> --map <kairos.map.json>         # 这个类来自哪一行、赢了哪些层
```

从 Gradle 里调用：`./gradlew -q :kairos-cli:run --args='doctor'`。

## 调试

```bash
./gradlew -q :kairos-cli:run --args='why <class> --map blog-backend/build/kairos/jvm/main/kairos.map.json --root blog-backend'
./gradlew -q :kairos-cli:run --args='report --map blog-backend/build/kairos/jvm/main/kairos.map.json'
```

`why` 会打印类里的每条声明、它对应的源码行与 caret，以及和其它层的冲突。

## 已知限制

- 样式块内的条件值必须是**全常量分支**；一个块最多 8 种组合，超出会报错（M2 轴上模型会替代这个上限）。
- 条件组里的值必须是常量，且不能和块内 `when` 值同时使用；条件组的 query 必须是编译期常量。
- 第三方未分层 CSS 的优先级高于 Kairos 的 `@layer`；需要显式处理时见 [ADR 0002](docs/adr/0002-style-condition-model.md)。
- 声明提取只在同一编译单元内生效；跨层/跨模块的相同声明仍会出现两次。
- 伪类（`:hover`）、媒体查询、`@supports` 还没有原语。
- `styles()` 不建模 shorthand/longhand。
- 剪枝依赖"类名是字面量"，运行时拼接的类名会被误删。
- 映射里的源码路径是**项目相对路径**；同机重复构建逐字节一致（A12）。跨机器一致性受 Kotlin
  编译产物影响，尚未做跨机器复现测试。
- 响应式列表目前整块重建（`bindChildren`），没有 keyed diff。
- Gradle 插件在 9.7.1 + Kotlin 2.2.20 上验证；插件模块因 Gradle 自带 kotlin-stdlib 2.4 使用了
  `-Xskip-metadata-version-check`，运行时二进制兼容。

## 文档

- 项目状态（能做什么 / 已知限制）：[docs/status.md](docs/status.md)
- 快速开始：[docs/guides/quickstart.md](docs/guides/quickstart.md)
- 核心概念：[docs/guides/concepts.md](docs/guides/concepts.md)
- 教程：[Ktor + htmx](docs/guides/tutorial-ktor-htmx.md) · [How-to](docs/guides/how-to.md)
- 治理：[CONTRIBUTING.md](CONTRIBUTING.md) · [SECURITY.md](SECURITY.md) · [governance.md](docs/contracts/governance.md)
- 1.0 路线（M0–M6）：[plan-1.0.md](.lignting/kairos/plan-1.0.md)
- 架构决策记录：[docs/adr/](docs/adr/)
- 公共契约（API / 支持矩阵 / 非目标 / 版本）：[docs/contracts/](docs/contracts/)
- 方向：[direction.md](direction.md)
- 历史阶段表：[phases.md](.lignting/kairos/phases.md)
- 总计划视图：[roadmap.md](.lignting/kairos/roadmap.md)
