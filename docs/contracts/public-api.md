# Kairos 公共 API（第一版）

- 状态：M0 冻结基线（2026-10-31）
- 依据：当前仓库 `kairos-core`、`kairos-browser`、`kairos-htmx`、`kairos-cli`、`kairos-compiler-plugin` 的真实源码
- 相关：[ADR 0002](../adr/0002-style-condition-model.md)、[ADR 0003](../adr/0003-classname-manifest-contract.md)、[versioning.md](versioning.md)

## 0. 怎么读这份清单

### 稳定性标签

| 标签 | 含义 | 变更规则 |
|---|---|---|
| `stable` | 1.0 目标公共 API，签名与语义冻结 | 只能按 [versioning.md](versioning.md) 弃用后变更；不得静默改变行为 |
| `experimental` | 已可用，但预期在 1.0 前调整形态 | 允许在次版本变更，需在 release notes 与迁移说明中列出 |
| `internal` | 源码可见但不是公共契约 | 随时可改；依赖者自担 |

**现实说明（2026-10-31 更新）**：`@ExperimentalKairosApi`（`@RequiresOptIn`）已加入 `kairos-core`，`classNameFor` 两个重载、`StyleRule`、`MarkdownStyles` 已标注为 experimental；其余上表 `stable` 条目未标注。1.0 发布前会把 `experimental` 条目逐个复核，新增声明默认先标 experimental。

包名（package）是 API 的一部分；把函数从一个包移动到另一个包，按破坏性变更处理。`public` Kotlin 可见性不等于公共 API，以本清单为准。

## 1. 模块现状与目标

### 1.1 当前模块

| 模块 | 平台 | 角色 | 是否发布 |
|---|---|---|---|
| `kairos-core` | JVM + JS | HTML DSL、节点模型与序列化、样式 DSL 与类名、Signal/effect/computed、Markdown | 计划发布 |
| `kairos-browser` | JS | DOM 绑定、事件、客户端渲染辅助 | 计划发布 |
| `kairos-htmx` | JVM | 整页/片段渲染、htmx 属性委托 | 计划发布 |
| `kairos-compiler-plugin` | JVM（编译器插件） | K2 IR 折叠、CSS/manifest/report 产出 | 作为编译器插件构件发布，不提供库 API |
| `kairos-gradle-plugin` | JVM（Gradle 插件） | 自动注入编译器插件、声明每 compilation 输出、暴露 `kairos` 扩展 | M1 新增，随插件发布 |
| `kairos-cli` | JVM | `why` / `report` / `merge` / `prune` | 计划发布 |
| `kairos-bom` | JVM（platform） | 约束同版本构件 | M1 新增，计划发布 |
| `demo-web` / `demo-web-feature` | JVM + JS | 静态站点示例与第二模块 | 示例，不发布 |
| `blog-backend` / `blog-frontend` | JVM / JS | Ktor + htmx 博客示例 | 示例，不发布 |
| `examples/consumer-static` | JVM | clean-room consumer：只依赖发布坐标 + `dev.kairos` 插件 | 验收示例，不发布 |

所有框架模块统一 `group = "dev.kairos"`、`version = "0.1.0"`（M1 底座），通过 `mavenLocal()` 供示例工程消费；公开版本发布前的坐标与仓库见 [support-matrix.md](support-matrix.md)。

### 1.2 计划中新增、当前尚不存在的模块

`kairos-style-contract`、`kairos-reactive`、`kairos-ktor`、`kairos-testing` 只出现在 plan-1.0 的目标结构中，不属于当前公共 API。本清单不虚构它们的存在；它们的 API 在对应里程碑冻结时另行加 ADR 与本文件修订。`kairos-gradle-plugin` 与 `kairos-bom` 已随 M1 出现，但它们目前只是构建底座，不构成生态承诺。

当前 `kairos-core` 直接承载 Signal 与 Markdown，与目标模块划分不一致；这是已知差异，见 [non-goals.md](non-goals.md) 与 [ADR 0004](../adr/0004-gradle-plugin-artifact-variant.md)。

## 2. `dev.kairos.runtime`：样式 DSL

| 声明 | 签名 | 稳定性 | 备注 |
|---|---|---|---|
| `Declaration` | `data class Declaration(val property: String, val value: String)` | `stable` | 类名与 manifest 的声明单元 |
| `Layer` | `enum class Layer(val letter: Char, val cssName: String)` | `stable` | `Reset/Base/Variant/Override`，字母 `r/b/v/o` |
| `KairosDsl` | `@DslMarker annotation class KairosDsl` | `internal` | 作用域标记，不建议引用 |
| `StyleScope` | `class StyleScope internal constructor(conditionStack: List<StyleCondition> = emptyList())` | `stable` | 构造器 internal；自定义 helper 以扩展函数形式添加 |
| `StyleScope.declaration` | `fun declaration(property: String, value: String)` | `stable` | 所有 typed helper 的最终入口；会做空白清理与 CSS 片段校验 |
| `StyleCondition` | `sealed interface StyleCondition`：`PseudoClass` / `PseudoElement` / `Media` / `Container` / `Supports` | `stable` | 条件身份契约见 [ADR 0002](../adr/0002-style-condition-model.md)；提供 `selectorSuffix` / `atRule` / `key` |
| `StyleRule` | `data class StyleRule(val conditions: List<StyleCondition>, val declarations: List<Declaration>)` | `experimental` | 运行时 fallback 的一个条件组；编译器内部有同构表示 |
| `css` | `fun css(layer: Layer = Layer.Base, block: StyleScope.() -> Unit): String` | `stable` | 返回空格分隔的类名列表（base + 每个条件组）；空块返回空串；无插件时运行时 fallback |
| `StyleScope.hover` / `focus` / `focusVisible` / `active` / `disabled` / `checked` | `fun StyleScope.<name>(block: StyleScope.() -> Unit)` | `stable` | 伪类条件组；生成独立的条件类 |
| `StyleScope.before` / `after` | `fun StyleScope.<name>(block: StyleScope.() -> Unit)` | `stable` | 伪元素条件组 |
| `StyleScope.media` / `container` / `supports` | `fun StyleScope.<name>(query: String, block: StyleScope.() -> Unit)` | `stable` | query 必须是编译期常量且不能含 `{}`/`;`/换行；生成 at-rule 包裹的条件类 |
| `styles` | `fun styles(vararg blocks: String): String` | `stable` | 类名集合拼接、去重；同层同属性不同值由编译器报错；操作数的条件规则随块一起保留 |
| `classNameFor` | `fun classNameFor(layer: Layer, declarations: List<Declaration>, conditions: List<StyleCondition> = emptyList()): String` | `experimental` | 算法契约见 [ADR 0003](../adr/0003-classname-manifest-contract.md)；条件为空时与 v1 哈希输入一致 |
| `classNameFor` | `fun classNameFor(layer: Layer, declaration: Declaration): String` | `experimental` | 单声明重载 |
| `Spacing` | `object Spacing`：`NONE/XS/SM/MD/LG/XL/XXL` | `stable` | `0/4px/8px/12px/16px/24px/32px` |
| `Radius` | `object Radius`：`SM/MD/LG/FULL` | `stable` | `4px/8px/14px/999px` |
| `Fonts` | `object Fonts`：`SANS/MONO` | `stable` | 字体栈字符串 |
| `px` / `pct` | `inline val Int.px: String` / `inline val Int.pct: String` | `stable` | `12.px == "12px"`，`12.pct == "12%"` |
| typed helpers | `StyleScope` 的 inline 扩展 | `stable` | 见下方清单 |

typed helpers 全量清单（每个都是 `inline fun StyleScope.<name>(value: String)`）：

`display`、`width`、`height`、`maxWidth`、`minHeight`、`margin`、`marginTop`、`marginBottom`、`padding`、`paddingTop`、`paddingBottom`、`gap`、`color`、`backgroundColor`、`fontFamily`、`fontSize`、`fontWeight`、`lineHeight`、`letterSpacing`、`border`、`borderColor`、`borderTop`、`borderBottom`、`borderRadius`、`opacity`、`cursor`、`alignItems`、`justifyContent`、`textAlign`、`textDecoration`、`flexDirection`、`flexWrap`、`gridTemplateColumns`、`overflow`、`whiteSpace`、`listStyle`、`content`。

条件 DSL 由 `StyleScope` 的成员函数提供（见上表）；条件组里的值必须是编译期常量，且一个块不能同时含 `when` 条件值与条件组（编译器报错要求拆分）。shorthand/longhand 尚未建模。

`KairosRuntimeRegistry`（`record` / `snapshot` / `conditions` / `size` / `clear`）是开发期诊断注册表，**不是公共 API**，不得用于业务逻辑；它只记录运行时 fallback 实际执行过的样式块与条件。

## 3. `dev.kairos.render`：节点与 HTML

| 声明 | 签名 | 稳定性 | 备注 |
|---|---|---|---|
| `Node` | `sealed interface Node` | `stable` | `TextNode` / `ElementNode` |
| `TextNode` | `data class TextNode(val value: String) : Node` | `stable` | 转义发生在序列化器 |
| `ElementNode` | `class ElementNode(val tag: String) : Node` | `stable` | 属性用委托；`style` 是 class 列表 |
| `ElementNode.attributes` | `val attributes: LinkedHashMap<String, String>` | `stable` | 插入顺序即输出顺序 |
| `ElementNode.children` | `val children: MutableList<Node>` | `stable` | 直接操作时自行保证结构合法 |
| `ElementNode.id` | `var id: String` | `stable` | 空串移除属性 |
| `ElementNode.style` | `var style: String` | `stable` | 实际写 `class`；空串移除 |
| `ElementNode.className` | `var className: String` | `stable` | `style` 的别名 |
| `String.text` | `val String.text: Unit`（成员扩展） | `stable` | 仅在 `ElementNode` builder 接收者内可用 |
| `ElementNode.text` | `fun text(value: String)` | `stable` | 追加文本子节点，空串忽略 |
| `ElementNode.cls` | `fun cls(vararg values: String)` | `stable` | 追加类名，空值忽略 |
| `ElementNode.add` | `fun add(node: Node)` | `stable` | 追加单个节点 |
| `ElementNode.addAll` | `fun addAll(nodes: List<Node>)` | `stable` | 追加多个节点 |
| `el` | `fun el(tag: String, block: ElementNode.() -> Unit = {}): ElementNode` | `stable` | 通用构造器 |
| 根构造器 | `html(...)`、`div(...)` | `stable` | 不挂到任何父节点 |
| 追加构造器 | `html`、`head`、`body`、`main`、`div`、`header`、`footer`、`nav`、`section`、`article`、`aside`、`h1`、`h2`、`h3`、`p`、`span`、`a`、`ul`、`ol`、`li`、`button`、`form`、`label`、`textarea`、`select`、`option`、`code`、`pre`、`time`、`strong`、`em`、`blockquote`、`table`、`tr`、`td`、`th`、`small`、`script`、`style`、`meta`、`link`、`input` | `stable` | 均为 `ElementNode.<name>(block: ElementNode.() -> Unit = {}): ElementNode` |
| void 构造器 | `ElementNode.hr()`、`ElementNode.br()` | `stable` | 无子节点 |
| `renderToHtml` | `fun renderToHtml(root: Node, indent: String = "  "): String` | `stable` | 带 `<!DOCTYPE html>` |
| `renderFragment` | `fun renderFragment(node: Node, indent: String = "  "): String` | `stable` | 不带 doctype |

补充约束：

- 只有 `area/base/br/col/embed/hr/img/input/link/meta/source/track/wbr` 是无子节点元素；`script/style/link/meta` 的 builder 允许子节点，序列化器按普通元素处理；
- 元素名与属性名只允许字母、数字、`-`、`:`、`_`；非法名称在序列化时抛异常；
- 文本转义 `& < >`，属性值额外转义 `"`；
- HTML 输出是确定性的：无时间戳、无随机 ID、缩进固定、属性保持插入顺序。

### 3.1 属性委托

| 声明 | 签名 | 稳定性 |
|---|---|---|
| `attribute` | `fun attribute(name: String, fallback: String = ""): AttributeDelegate` | `stable` |
| `flagAttribute` | `fun flagAttribute(name: String): FlagAttributeDelegate` | `stable` |
| `AttributeDelegate` / `FlagAttributeDelegate` | 委托实现类 | `internal` | 不要直接声明这两个类型 |

常见属性委托（均为 `var ElementNode.<name>: String by attribute(...)`）：`href`、`src`、`rel`、`type`、`name`、`value`、`placeholder`、`method`、`action`、`title`、`lang`、`charset`、`content`、`role`、`forId`、`ariaLabel`、`ariaHidden`。

自定义属性只需一行委托：`var ElementNode.dataKairosId: String by attribute("data-kairos-id")`。赋空串移除属性；布尔属性用 `flagAttribute`。

**当前缺口**：没有内置的 `data-kairos-id` 委托；每个应用自行声明。M3 将其提升为 core 的一等属性，见 [ADR 0005](../adr/0005-ssr-enhancement-model.md)。

## 4. `dev.kairos.reactive`：响应式核心

全部为 `experimental`。M3 会补齐 Owner/Scope、batch、resource 与生命周期；当前只有最小闭环。

| 声明 | 签名 |
|---|---|
| `Disposable` | `fun interface Disposable { fun dispose() }` |
| `Signal` | `class Signal<T> internal constructor(initial: T)`；成员 `var current: T`、`fun peek(): T`、`fun update(transform: (T) -> T)`、`fun observe(observer: (T) -> Unit): Disposable` |
| `signal` | `fun <T> signal(initial: T): Signal<T>` |
| `computed` | `fun <T> computed(block: () -> T): Signal<T>` |
| `effect` | `fun effect(block: () -> Unit): Disposable` |
| `Effect` | `class Effect internal constructor(...) : Disposable`；`fun run()`、`override fun dispose()` |
| `Signal.map` | `fun <T> Signal<T>.map(transform: (T) -> String): Signal<String>` |

`Reactivity` 对象的 `track` / `withTracking` 为 internal，不属于公共 API。`computed` 当前用 effect 实现，每次依赖变化重算后按值是否变化决定是否通知。

## 5. `dev.kairos.markdown`：Markdown 子集

全部为 `experimental`，且计划在 M2/M5 从 `kairos-core` 迁出为可选模块（plan §4 明确 Markdown 不进核心）。

| 声明 | 签名 |
|---|---|
| `renderMarkdown` | `fun renderMarkdown(source: String): List<Node>` |
| `markdown` | `fun ElementNode.markdown(source: String)` |

支持：标题（`#`/`##`/`###`）、段落、围栏代码块、无序列表、引用、行内粗体/斜体/行内代码/链接。**不承诺安全**：链接 `href` 未做协议白名单，原始 HTML 不解析；安全边界在 M4 处理。

## 6. `dev.kairos.browser`：浏览器端绑定

全部为 `experimental`。这些函数直接操作 `org.w3c.dom`，没有虚拟 DOM、没有自动清理；M3 的 `mount(root)` 会在此之上提供生命周期。

| 声明 | 签名 | 备注 |
|---|---|---|
| `byId` | `fun byId(id: String): Element?` | 查 `[data-kairos-id="..."]` |
| `swapClass` | `fun swapClass(element: Element, remove: String, add: String)` | 替换一组类名 |
| `toggleClass` | `fun toggleClass(element: Element, name: String): Boolean` | 返回切换后状态 |
| `on` | `fun on(element: Element?, event: String, handler: () -> Unit)` | 无移除句柄 |
| `Node.toDom` | `fun Node.toDom(): org.w3c.dom.Node` | 由共享节点树构造 DOM |
| `ElementNode.mountInto` | `fun ElementNode.mountInto(container: Element)` | 清空容器并整块替换，不是 SSR 增强 |
| `setHtml` | `fun setHtml(element: Element, html: String)` | 裸 `innerHTML`，显式 raw HTML 入口 |
| `elementById` | `fun elementById(id: String): Element?` | 原生 `getElementById` |
| `bindText` | `fun bindText(element: Element, signal: Signal<*>): Disposable` | 写 `textContent` |
| `bindClassName` | `fun <T> bindClassName(element: Element, signal: Signal<T>, render: (T) -> String): Disposable` | 切换整串类名 |
| `bindAttribute` | `fun bindAttribute(element: Element, name: String, signal: Signal<*>): Disposable` | 空值移除属性 |
| `bindChildren` | `fun bindChildren(container: Element, signal: Signal<List<Node>>): Disposable` | 全量重建，无 keyed diff |
| `onClick` | `fun onClick(element: Element?, handler: () -> Unit)` | `preventDefault` 不处理 |
| `onInput` | `fun onInput(element: Element?, handler: (String) -> Unit)` | 从 `HTMLInputElement.value` 取值 |
| `onSubmit` | `fun onSubmit(element: Element?, handler: (Event) -> Unit)` | 先 `preventDefault` 再回调 |
| `inputValue` | `fun inputValue(id: String): String` | 按元素 `id` 读取 |
| `clearInput` | `fun clearInput(id: String)` | 按元素 `id` 清空 |

`byId` 查的是 `data-kairos-id`，`inputValue`/`clearInput` 用的是 `id`，两者不要混用。

## 7. `dev.kairos.htmx`：服务端页面与 htmx 属性

| 声明 | 签名 | 稳定性 |
|---|---|---|
| `page` | `fun page(title: String, stylesheets: List<String>, bodyNode: ElementNode, language: String = "en", scripts: List<String> = emptyList(), headContent: ElementNode? = null): String` | `stable` |
| `fragment` | `fun fragment(node: Node): String` | `stable` |
| htmx 属性 | `hxGet`、`hxPost`、`hxPut`、`hxDelete`、`hxTarget`、`hxSwap`、`hxTrigger`、`hxIndicator`、`hxConfirm`、`hxPushUrl`、`hxInclude`、`hxVals`、`hxBoost` | `stable` |

`page` 固定输出 `meta charset=utf-8`、viewport、`<title>`、样式表、body 节点与脚本；`headContent` 只能追加 `ElementNode`，没有 nonce 参数（M4 补 CSP 支持）。`fragment` 不带 doctype，供 htmx 响应使用。HX-Request 的整页/片段分流当前在应用路由层（blog-backend），不在 `kairos-htmx` 内。

## 8. 工具与构建契约（不是库 API）

### 8.1 CLI

`kairos` 命令（`dev.kairos.cli.MainKt`）：

| 命令 | 必需参数 | 作用 |
|---|---|---|
| `why <class>` | `--map <path>` | 打印类的层、声明、源码位置与相关层冲突 |
| `report` | `--map <path>` | 汇总模块、类数、层计数、冲突、错误、备注 |
| `merge` | `--out <css>`、至少一个 `--map` | 合并多个模块的 CSS；校验 map 版本与层一致 |
| `prune` | `--css <in.css>`、至少一个 `--unit <file>` | 删除未在发布产物中出现的类规则 |

退出码：成功 `0`；已知错误（缺参数、文件不存在、版本不符等）`1`；未知命令 `2`。CLI 只接受 manifest `version = 3`。输出文案不承诺逐字稳定，但字段语义与错误条件属于契约。

### 8.2 编译器插件

- 插件 ID：`dev.kairos`；`supportsK2 = true`；
- 选项：`outputDir`（可选）、`module`（可选，默认 `module`）、`projectDir`（可选，用于把 manifest 源码路径相对化）、`strict`（可选，字符串布尔值）；
- 产物：`kairos.css`、`kairos.map.json`、`kairos-report.txt`；
- 一旦看到源码，即使样式为空也写产物；编译错误先落产物再抛异常，便于解释失败；
- 由 `dev.kairos` Gradle 插件自动注入，选项对用户透明；手工 `-Xplugin` 接线不是官方路径；
- `strict` 目前只是接入参数：折叠失败时仍按运行时 fallback 处理，尚未在失败路径上执行严格模式，使用前不要假设它会阻止构建。

### 8.3 Gradle 插件

- 插件 ID：`dev.kairos`，通过 plugin marker 解析；作用于项目的每个 Kotlin compilation（common metadata 除外，可用 `disabledTargets` 排除）；
- 扩展名：`kairos`，类型 `dev.kairos.gradle.KairosExtension`；
- 默认输出：`build/kairos/<target>/<compilation>/`，编译任务声明为输出；
- 自动传给编译器插件的选项：`outputDir`、`module`、`projectDir`、`strict`；
- 编译器插件坐标与 Gradle 插件同版本（`dev.kairos:kairos-compiler-plugin`）。

| 扩展属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `moduleName` | `String?` | Gradle 项目名 | 写入 manifest 的模块名 |
| `outputDirectory` | `String?` | 每 target/compilation 独立目录 | 显式覆盖输出目录（相对项目目录） |
| `disabledTargets` | `Set<String>` | 空集 | 不运行编译器插件的 target 名 |
| `strict` | `Boolean` | `false` | 预留给严格模式；当前尚未在折叠失败路径生效 |

Gradle 插件本身的稳定性标为 `experimental`：M1 收尾阶段仍在调整，1.0 前可能变更配置项。`kairos-bom` 是纯 platform，不包含可调用 API。

## 9. 变更管理

- 本文件是 M0 冻结基线；新增 API 必须同步更新本文件并标注稳定性；
- `stable` 条目删除/改签名/改行为，按 [versioning.md](versioning.md) 走弃用窗口；
- `experimental` 条目变化必须在 release notes 说明，必要时提供迁移映射；
- 任何类名算法、manifest 字段或产物语义变化，按 [ADR 0003](../adr/0003-classname-manifest-contract.md) 的版本规则处理，不能只改实现。
