# 执行报告 · kairos · Phase 1 · R2

> 层级：权杖
> 核心目的：让折叠出来的类名被一个真实页面消费，且页面是静态的——HTML 构建期生成、CSS 编译期生成、运行时只写类名
> 日期：2026-10-29

## 1. 做了什么

| 文件 | 改了什么 |
|---|---|
| `kairos-runtime/src/commonMain/kotlin/dev/kairos/render/Node.kt` | 新增与渲染器无关的节点模型（`Node` / `TextNode` / `ElementNode`）与元素构建器；`html` 同时提供顶层与接收者两个版本（顶层版用于页面根） |
| `kairos-runtime/src/jvmMain/kotlin/dev/kairos/render/Html.kt` | 确定性 HTML 序列化：固定缩进、属性保持插入顺序、void 元素、单文本叶子单行渲染、`& < > "` 转义 |
| `demo/src/commonMain/kotlin/Site.kt` | 真实页面：13 个 `css { }` 样式块 + 两个枚举驱动变体（主题、密度）；三个卡片、页头页脚；两个交互控件带 `data-kairos-id` |
| `demo/src/jvmMain/kotlin/JvmMain.kt` | 用未挂插件的 JVM 目标渲染静态 `index.html`，同时打印参照类名（运行时降级路径） |
| `demo/src/jsMain/kotlin/Main.kt` | 事件接线：两个按钮各做一次类名组替换；无 DOM 时跳过并打印折叠诊断 |
| `demo/web/site.css` | 手写基础样式（reset、栅格、按钮间距），证明项目可以保留普通样式表 |
| `demo/build.gradle.kts` | 新增 `renderSite`（JVM 渲染）与 `assembleSite`（装配 `build/site`） |
| `tools/browser-check.mjs` | 用 jsdom 加载产物、点击、断言：类名组替换、无内联样式、CSS 覆盖完整、无脚本错误（7 条） |

## 2. 验证结果

| 验收条目 | 命令 | 结果 |
|---|---|---|
| A6 页面用到的类都在编译期 CSS 里 / 无内联样式 | `node tools/browser-check.mjs` | **通过** — B1 / B3 / B6 |
| A7 交互只改类名 | `node tools/browser-check.mjs` | **通过** — B4（`c4b4c07dc` ↔ `cb566f910`）、B5（`c0bec07b3` ↔ `c9214426e`）、B6（0 个 `style` 属性） |
| A8 页面 HTML 确定性 | `gradle :demo:renderSite` 两次比对 | **通过** — 两次渲染 `index.html` 内容一致（同一份节点树 → 同一串字节） |
| A9 页面可加载、初始主题构建期已定 | `node tools/browser-check.mjs` | **通过** — B1 / B7 |
| 浏览器级总计 | `node tools/browser-check.mjs` | **7/7 通过** |
| R1 回归 | `node tools/verify.mjs --rebuild` | **7/7 通过**（规则数从 4 升到 44，两次干净构建仍逐字节一致） |

真实产物（`demo/build/site`）：`index.html` 2.8 KB、`kairos.css` 1.6 KB（44 条原子规则）、手写 `site.css` 0.5 KB、`kairos-demo.js` 9.0 KB + 运行时 5.8 KB + stdlib 121 KB。

浏览器检查里最关键的一条：

```
PASS  B4  clicking the theme button swaps the light class group for the dark one  -> c4b4c07dc <-> cb566f910
PASS  B6  the runtime never writes an inline style  -> 0 elements with a style attribute
```

## 3. 与计划的偏离

| # | 计划 | 实际 | 理由 |
|---|---|---|---|
| 1 | S1 的构建器同时提供顶层与接收者版本 | 首版只写了接收者版本，`buildPage()` 在无接收者处无法调用 `html {}`，编译失败 | 页面根是一个没有环境接收者的位置；补了顶层 `html` 构建器 |
| 2 | 页面样式放在 `body` 上 | 主题变体与页面样式在 `body` 上撞车（同一条 `background-color` 让深色主题失效） | 把颜色从页面样式里移除，交给主题变体；顺带证明了原子类去重确实是按声明内容合并的（两个块的相同声明产出了同一批类名） |
| 3 | S5 的类名替换按"单个类名"处理 | 一个变体是多条声明 → 多个类名，按单类名替换会半个不换 | 改成按**类名组**替换；这条会直接影响 P2 的②档原语设计（选择器必须能整组替换），已记入影响后续 |
| 4 | 计划未提 `<script>` 标签 | 首版 HTML 没有脚本，页面静态可看但不可交互 | 页面构造里按 UMD 加载顺序追加三个 `script`；顺序错了会报 "dependency was not found" |
| 5 | 计划未提文本叶子的渲染形态 | 首版把 `<title>` 渲染成三行 | 序列化器加"单文本叶子单行"规则 |

## 4. 影响后续轮次的问题

1. **② 档原语必须按"类名组"设计**：一次模式切换会替换多条原子类（本页主题 = 背景 + 前景两条）。P2 的原语与 CSS 变体不能用"一个状态一个类名"的理想化模型，否则要么漏切、要么引入运行时过滤。这是本轮最实质的发现。
2. **JS 体积**：`kotlin-stdlib` 121 KB 占了产物的 88%。P3 若要把"极小 JS"当成卖点，需要评估单文件打包 / 更激进的 DCE，否则"JS 只留事件"这句话在体积上不成立。
3. **`@layer kairos` 与手写样式的关系**：无 layer 的手写 CSS 永远压过分层样式。本轮当作特性（布局归人写），但 P2 做级联代数时必须明确"kairos 层应该在哪一段"，否则用户加一行手写样式就能悄悄改掉变体。
4. **jsdom 不是浏览器**：层叠、`@layer`、容器查询等真实渲染行为 jsdom 不计算。B 系列检查证明的是"DOM 与脚本行为正确"，不是"像素正确"；P2 需要一次真实浏览器的人工验收。
5. **Q14 已定**：渲染模型取"构建期 JVM 渲染静态 HTML"。若 P2 需要按状态生成多份 HTML（增量重渲染），这条选型要重新评估。
## 补记（同日，收尾时发现并修复）

在最后一次从零验证里发现：`gradle clean build :demo:assembleSite` 会产出 **6 个**文件而不是 8 个——
`kairos.css` 与 `kairos.map.json` 缺失。两个独立原因：

1. **构建缓存绕过了插件**：`compileKotlinJs` 从缓存恢复时不会执行编译器，而插件的产物是编译的副作用，
   不在任何任务输出里 → CSS 静默缺失。修复：把产物目录声明为该编译任务的输出，并对其
   `outputs.cacheIf { false }`（缓存一个"带未声明副产物"的任务本身就是不安全的）。
2. **两个任务声明了同一个输出目录**：klib 编译刚写完，link 任务把它当作自己的过期输出清掉。
   修复：产物目录只由**一个**任务拥有（能看见 Kotlin 源码的那个 klib 编译）。

修复后连续两次 `clean build` 均稳定产出 8 个文件；单独删除产物目录后再构建也会正确重跑插件。
这条同时关闭了 R1 报告第 4 节的问题 1（"插件产物不是 Gradle 任务输出"）。
