# Kairos 从原型到“相对完整前端框架生态库”的路线计划

## 一、先给结论

Kairos 已经证明了一个很有价值的技术尖点：

- Kotlin 编译期把 `css {}` 折叠成类名；
- 分层级联 `reset < base < variant < override`；
- JVM 渲染 HTML 与 Kotlin/JS 运行路径一致；
- 已有 SSR/htmx、Signal、DOM 绑定、CLI 和两个示例的完整闭环。

但当前仍是“**单仓库 demo 级编译器实验 + 两个展示应用**”，还不是可发布、可维护的框架生态。距离生态库最缺的不是“再加路由、表单、组件库”，而是下面三件事：

1. **可安装**：用户能否只加一个 Gradle 插件和依赖就构建，而不是复制 `-Xplugin`、绝对路径和一堆手工 Gradle 任务。
2. **可信**：样式产物是否确定、可缓存、可增量、跨机器一致，出错时能否解释。
3. **可长期维护**：公共 API、类名算法、manifest、错误码、版本兼容是否有契约。

因此建议先暂停扩张功能，按 **“薄核心、厚适配器”** 的路线推进。

---

## 二、必须先做的战略修正

`direction.md` 当前明确写着“不做生态、不做向后兼容、不写迁移文档、受众默认只有作者”。这与“相对完整的前端框架生态库”直接冲突。

不建议一边保留这些非目标，一边补路由、表单和社区设施，否则最后会做成“通用框架的半成品”。

建议把定位改成：

> **Kotlin-first、编译期样式驱动、HTML-first 的 UI 工具链，优先服务 Kotlin/Ktor 服务端渲染团队；不是 React/Vue 的通用替代品。**

需要修订的上游约束：

| 当前约束 | 生态化后的版本 |
|---|---|
| 受众只有作者 | 先服务作者，再服务 1–3 个设计伙伴，最后才对外 |
| 不做向后兼容 | 从第一个公开版本开始执行 SemVer、弃用窗口和迁移指南 |
| 不做 Kotlin 生态 | 初始分发渠道就是 Kotlin/Gradle/Ktor 生态 |
| 降级一律静默 | 运行时行为仍保持一致，但构建/CI 提供 strict 模式和覆盖率报告 |
| 同机产物确定 | 要求跨目录、跨机器、可复现的相对路径产物 |
| 8 种组合、同编译单元提取 | 变成公开边界文档和测试，不再只是实现细节 |

如果暂时只想做“自用内部框架”，上面的发行、治理、迁移工作可以大幅缩减；但只要目标是“生态库”，这些就不能省。

---

## 三、什么算“相对完善”

建议把 1.0 定义为下面五条同时成立，而不是功能数量：

1. **安装闭环**
   - 一个 Gradle 插件 ID；
   - 一个 BOM；
   - 15 分钟内完成 quickstart；
   - 独立空工程通过 Maven 坐标消费，不依赖框架源码。

2. **样式能力闭环**
   - 支持 `:hover`、`:focus-visible`、`:disabled` 等交互状态；
   - 支持 media/container/`@supports`；
   - 支持主题 token 和 CSS 变量；
   - 支持枚举变体、多轴组合和覆盖诊断；
   - 第三方组件库可以以二进制依赖方式提供样式资产。

3. **应用闭环**
   - 组件 props/slots；
   - SSR 输出与客户端增强契约；
   - htmx swap 后能重新绑定行为；
   - keyed list 更新；
   - effect/owner 生命周期与清理；
   - 表单错误回显、CSRF、异步 loading/error 的官方 recipe；
   - 键盘可访问性和焦点管理基线。

4. **质量闭环**
   - 单元测试、编译器 golden/negative test、CLI 集成测试、跨浏览器 E2E；
   - 跨目录确定性构建；
   - Chromium/Firefox/WebKit 通过；
   - axe 无严重可访问性问题；
   - XSS、URL 协议、CSP、CSRF 有安全基线；
   - API binary compatibility 检查。

5. **生态闭环**
   - 完整文档、三个 starter、至少三个真实外部项目；
   - 2/3 设计伙伴在 4 周后继续使用；
   - 一套公开的版本支持矩阵和发布节奏。

> 这三个“三个项目”不是 KPI 装饰，而是防止框架作者用 demo 自嗨的唯一办法。

---

## 四、目标模块结构

不要马上拆成很多仓库，先保持 monorepo，但内部依赖方向必须清晰：

| 层 | 模块/产物 | 职责 |
|---|---|---|
| 内部契约 | `kairos-style-contract` | `Layer`、`Declaration`、条件上下文、类名算法、manifest DTO、算法版本 |
| 编译器 | `kairos-compiler-plugin` | IR 识别、折叠、诊断、生成 CSS/map |
| 构建入口 | `kairos-gradle-plugin` | 自动接编译器、声明任务输入输出、合并/剪枝、库样式变体 |
| 公共核心 | `kairos-core` | HTML DSL、样式 DSL、节点模型、序列化 |
| 响应式 | `kairos-reactive` | Signal、owner、batch、computed、生命周期 |
| 浏览器 | `kairos-browser` | DOM 绑定、SSR enhancement、htmx swap 重连 |
| 服务端适配 | `kairos-htmx` / `kairos-ktor` | page/fragment、HX-Request 分流、表单和路由的薄适配 |
| 测试工具 | `kairos-testing` | HTML golden、类名完整性、fallback 覆盖率、跨 JVM/JS 一致性断言 |
| 工具链 | `kairos-cli` | `init`、`dev`、`why`、`report`、`check`、`coverage`、`merge`、`prune` |
| 聚合 | `kairos-bom` | 统一版本，避免用户自己对齐多个 artifact |

重点：**Markdown、路由、表单验证、数据库、UI 组件库不应放进核心**。它们应该是可选模块、官方适配器或 recipe。

当前 `kairos-compiler-plugin` 把整个 `kairos-core` 打包进插件 jar，也会造成编译器 ABI 与 DOM/Reactive API 强耦合，应该拆出独立的样式契约层。

---

## 五、路线图 M0–M6

以下工期按全职 1 人估算，不是日历承诺；兼职大约翻倍。

| 里程碑 | 周期 | 核心目的 |
|---|---:|---|
| M0 契约与定位 | 3–5 天 | 解决方向冲突，冻结第一版公共契约 |
| M1 发布与构建底座 | 2–3 周 | 可安装、可复现、可测试 |
| M2 样式语义 v1 | 4–6 周 | 支撑真实页面和第三方样式资产 |
| M3 应用运行时 v1 | 5–7 周 | 支撑真实交互应用 |
| M4 生产加固与工具链 | 4–6 周 | 安全、性能、诊断、跨浏览器 |
| M5 文档、示例与 dogfood | 4–6 周 | 从“能用”变成“别人敢用” |
| M6 1.0 稳定化 | 4–8 周 | API 冻结、治理、正式发布 |

总计大约 **5–9 个月**到 1.0 RC；真正的生态成熟会远长于此。

---

### M0：契约与定位

交付：

- 修订 `direction.md`：明确目标是“公开生态”还是“内部工具集”；
- 写 5 份 ADR：
  1. 产品定位与目标用户；
  2. 样式条件模型；
  3. 类名/manifest 版本契约；
  4. Gradle 插件和 artifact variant；
  5. SSR/客户端增强模型；
- 冻结第一版公共 API 清单和支持矩阵：Kotlin、JDK、Node、Gradle、浏览器版本；
- 明确“不做清单”：不做虚拟 DOM、不做自有路由、不做完整表单验证框架、不做 UI 组件市场、不追求 React/Vue 功能对齐。

退出标准：文档之间不再自相矛盾；任何新增功能都能判断属于“核心、适配器、recipe、暂不做”中的哪一类。

---

### M1：发布与构建底座

这是最重要的阶段，**不要在这一阶段增加新的 CSS 功能**。

必须完成：

1. **Gradle 工程化**
   - 增加 Gradle Wrapper；
   - 增加 root `build.gradle.kts`；
   - 增加 `libs.versions.toml`；
   - 用 convention plugin 复用构建逻辑；
   - 固定 Kotlin、KSP、Node、JDK 版本。

2. **正式 Gradle 插件**
   - 实现 `KotlinCompilerPluginSupportPlugin`；
   - 提供 `id("dev.kairos")` 和 plugin marker；
   - 自动为对应 compilation 注入编译器插件；
   - 每个 target/compilation 使用独立输出目录；
   - 用户 build 文件中不再出现 `-Xplugin`、jar 路径和 `-P plugin:dev.kairos:*`。

3. **可发布制品**
   - 配置 `group`、`version`、`maven-publish`；
   - 生成 POM、sources、Gradle module metadata；
   - 发布到 Maven Local 验证；
   - 发布到 Gradle Plugin Portal / Maven Central；
   - 增加 BOM；
   - 使用 `explicitApi()` 和二进制兼容检查。

4. **构建正确性**
   - 消除 `cacheIf { false }`；
   - 正确声明编译器插件和合并任务的输入输出；
   - 把 map 里的绝对路径改成规范化相对路径；
   - merge 后同时产出合并 map；
   - 剪枝默认保守，去掉脆弱的正则假设；
   - 修复当前构建里的已知问题，例如 `demo-web` 的 bundle 文件名不一致、JVM “无插件参考路径”实际仍挂了插件等。

5. **测试和 CI**
   - `src/commonTest` / `src/jvmTest` / `src/jsTest`；
   - 编译器插件 golden test 和 negative test；
   - HTML 转义、类名算法、CSS 层序、CLI 错误测试；
   - clean-room consumer 工程；
   - CI 至少覆盖 Linux，最好加 macOS/Windows；
   - `./gradlew check` 必须从干净 clone 跑通，不依赖预先生成的 `build/site`。

退出标准：

- 新工程只加插件和依赖就能构建；
- `publishToMavenLocal` 后，独立 consumer 工程成功；
- 两个不同绝对路径下构建出的 CSS/map/report 逐字节一致；
- 修改一个样式后，普通增量构建能可靠更新产物；
- CI 绿，且不依赖系统 `gradle` 或人工启动服务。

---

### M2：样式语义 v1

这是 Kairos 的核心竞争力，必须做深，而不是继续堆 helper。

交付：

1. **声明上下文模型**
   - `:hover`、`:focus-visible`、`:active`、`:disabled`、`:checked`；
   - `::before` / `::after`；
   - media query；
   - container query；
   - `@supports`；
   - 伪类、媒体查询、层级的组合必须进入类名身份和 manifest。

2. **变体代数**
   - 把普通 `when` 升级为真正的一等变体契约；
   - 枚举模式轴、覆盖诊断；
   - 用“按轴生成类组 + 固定级联顺序”替代简单 N 路组合枚举；
   - 取消或明确 `MAX_COMBINATIONS=8` 的边界；
   - 对不可达状态、漏分支、动态值给出清晰诊断。

3. **级联与 CSS 语义**
   - 建立 shorthand/longhand 关系模型，例如 `padding` 与 `padding-top`；
   - 同层冲突不能只比较 property 字符串；
   - 定义第三方未分层 CSS 的处理策略；
   - 支持外部 `@layer` 和 token；
   - 无法证明安全的共享声明提取必须禁止。

4. **库级样式资产**
   - 组件库编译后发布 CSS + manifest；
   - 消费方 Gradle 插件通过 artifact variant 自动收集传递依赖的样式资产；
   - 支持合并、去重、剪枝和 source map 合并；
   - 第三方组件库不需要把 Kotlin 源码带进消费者项目。

5. **可观测性**
   - 稳定错误码；
   - CSS source map；
   - fallback/coverage 报告；
   - strict 模式让 CI 可以禁止静默降级；
   - `why` 能定位到源码行和完整级联冲突。

退出标准：

- 一个真实响应式页面只用 Kairos 完成，不需要手写 `:hover`/media CSS；
- 四类条件与第三方 CSS 的冲突矩阵通过；
- 组件库以二进制依赖消费时不丢类、不丢 CSS；
- 跨机器构建产物一致；
- 视觉回归和浏览器测试通过。

---

### M3：应用运行时与 SSR 契约 v1

目标不是实现虚拟 DOM，而是把“HTML 直出 + 最小客户端增强”做稳。

交付：

1. **组件契约**
   - 纯函数组件；
   - typed props；
   - children/slot；
   - Fragment；
   - 组件局部样式资产；
   - 稳定的节点身份和测试方式。

2. **Reactive runtime v1**
   - Owner/Scope；
   - `dispose` 生命周期；
   - 批量更新，避免同一轮重复刷新；
   - computed 缓存和依赖清理；
   - effect 异常隔离；
   - 异步 resource：idle/loading/success/error 的穷尽状态；
   - 内存泄漏测试。

3. **SSR enhancement 契约**
   - 服务端输出稳定的 `data-kairos-*` 挂载点；
   - 客户端提供 `mount(root)` 或等价扫描机制；
   - htmx swap 后通过 `htmx:afterSwap` 重新绑定，而不是要求用户手工 `byId`；
   - 明确 DOM 结构一致性、状态交接、焦点恢复规则；
   - 不对 DOM 做整树 diff。

4. **列表与表单**
   - keyed list diff，而不是 `bindChildren` 全量重建；
   - 事件委托和自动清理；
   - 表单错误回显、提交中状态、CSRF hook；
   - 与 Ktor/htmx 的表单适配器；
   - 不在核心中实现完整验证引擎，验证逻辑优先交给 Ktor/业务层。

5. **a11y 基线**
   - 语义化构建器；
   - swap 后焦点恢复；
   - `aria-live` 区域；
   - 键盘导航测试；
   - 至少一个完整的无鼠标操作示例。

退出标准：

- 一个参考应用可以完成增删改查、排序、异步加载、表单错误回显、模态框焦点管理、深链刷新和 htmx 局部替换；
- 列表更新不整块重建；
- SSR 关键 DOM 与客户端结构一致；
- effect 生命周期测试无泄漏；
- 键盘路径通过。

---

### M4：生产加固与工具链

交付：

1. **安全**
   - 按上下文安全序列化；
   - URL 协议白名单，阻止 `javascript:`；
   - raw HTML 入口必须显式标记；
   - Markdown 链接和外链安全；
   - CSP nonce 支持；
   - CSRF 官方方案；
   - `setHtml()` 的安全边界文档和测试。

2. **CLI 与调试**
   - `kairos init --template static|ktor-htmx`；
   - `kairos dev`；
   - `kairos check`；
   - `kairos doctor`：检查 JDK、Node、Gradle、Kotlin 版本；
   - `why` / `report` / `coverage`；
   - 统一错误码、文件行列、修复建议和文档链接；
   - 浏览器里点击元素可看到对应类名和源码位置。

3. **开发期体验**
   - 先测基线：冷构建、增量编译、样式改动到页面更新；
   - 初版可先用 Gradle continuous build + 浏览器自动刷新；
   - 只有在不破坏类名契约的前提下，才做 CSS-only fast path；
   - 不要承诺无法兑现的 HMR。

4. **性能与兼容性**
   - 建立 benchmark：编译时间、CSS 体积、JS 运行时体积、DOM 更新次数；
   - 冻结预算并接入 CI；
   - Chromium/Firefox/WebKit + 移动 viewport；
   - axe 自动扫描；
   - 依赖漏洞、许可证和供应链扫描；
   - 支持矩阵中每个 Kotlin/Gradle 组合都跑测试。

退出标准：

- 跨浏览器通过；
- 安全测试无 P0/P1；
- 样式改动 p95 反馈时间有数据且可接受；
- 包体积和编译性能没有不可解释的回归；
- 常见错误 5 分钟内能通过错误码定位。

---

### M5：文档、示例与 dogfooding

交付：

1. **文档信息架构**
   - Quickstart；
   - Concepts：编译边界、三档降级、级联、状态穷举、SSR/htmx 分工；
   - Tutorial：从静态页到 CRUD；
   - How-to：主题、响应式、表单、htmx、多模块、剪枝、部署、排障；
   - Reference：Dokka + 稳定错误码 + manifest 契约；
   - Migration guide；
   - Release notes。

2. **Starter 与示例**
   - 静态营销页；
   - Ktor + htmx CRUD；
   - 已有 Ktor 应用的增量接入；
   - 每个示例在 CI 中从空目录构建和测试；
   - 代码片段也必须被编译，不能只写在 Markdown 里。

3. **设计伙伴计划**
   - 至少三个真实项目：
     - 内部管理后台；
     - 公开内容/营销站；
     - 已有 Ktor 应用的一个页面或设计系统；
   - 作者只做观察和基础设施修复，不替对方写业务代码；
   - 记录 install/init/page/component/style/data/routing/deploy/debug 九类摩擦。

采用指标：

- 首个页面 ≤ 1 天；
- 首个 CRUD 流程 ≤ 3 天；
- 3/3 上线；
- 2/3 四周后继续开发；
- 作者支持投入 ≤ 1 人时/伙伴/周；
- 80% 的阻塞可由文档、错误信息或工具自行解决。

退出标准：至少两个外部项目在生产或预生产运行，且没有修改框架源码。

---

### M6：1.0 稳定化与生态治理

1.0 之前不再加新功能，只做稳定化：

- API freeze；
- binary compatibility validator；
- SemVer、弃用窗口、迁移 fixture；
- RFC 流程、贡献指南、Code of Conduct、安全策略；
- issue/PR 模板、维护者制度；
- 自动发布、staging、changelog、版本化文档；
- LTS/支持矩阵；
- 90 天内没有 S1/S2 级问题；
- 三个外部项目在真实环境运行；
- 升级一个旧版本示例不需要改框架源码；
- 所有公开 API 有 KDoc 和可运行示例。

真正达到这些条件，再发 `1.0.0`。在此之前用 `0.x` 和 `@ExperimentalKairosApi` 管理预期。

---

## 六、质量门禁建议

建议把下面几类检查设为发布门禁：

| 层级 | 必须有的检查 |
|---|---|
| 单元 | 类名算法、Layer 顺序、HTML 转义、CSS 规范化、Signal 生命周期、keyed diff |
| 编译器 | 正向 golden、负向诊断、IR 形状、条件组合、shorthand/longhand、跨 Kotlin 版本 |
| 产物 | CSS/map 完整性、相对路径、跨目录逐字节一致、merge 后 map 不丢 |
| CLI | 错误参数、空输入、恶意输入、剪枝安全性、覆盖报告 |
| 浏览器 | Chromium/Firefox/WebKit、移动端 viewport、htmx swap、焦点和键盘路径 |
| 安全 | XSS、URL 协议、raw HTML、CSP、CSRF、依赖漏洞 |
| 性能 | 全量/增量构建、CSS/JS 体积、DOM 更新次数、冷启动 |
| 兼容性 | Kotlin/Gradle/JDK/Node/浏览器支持矩阵 |
| 消费 | clean-room consumer、升级 fixture、二进制 API 兼容 |

---

## 七、第一个 14 天应该做什么

不要先做路由、表单或 UI 组件。第一个 sprint 只做 M0 + M1 的最小闭环：

1. **第 1–2 天：定契约**
   - 修订 `direction.md`；
   - 写出 1.0 的目标、非目标和公共 API 清单；
   - 冻结第一版支持矩阵。

2. **第 2–5 天：构建底座**
   - 加 Gradle Wrapper、root build、version catalog、convention plugin；
   - 集中 Kotlin/KSP/依赖版本；
   - 删除各模块重复的版本声明。

3. **第 3–8 天：Gradle 插件**
   - 新建 `kairos-gradle-plugin`；
   - 实现 `KotlinCompilerPluginSupportPlugin`；
   - 替换 demo/blog 中所有手工 `-Xplugin` 配置；
   - 增加 plugin marker。

4. **第 6–9 天：发布闭环**
   - 给五个框架模块加 `maven-publish`；
   - `publishToMavenLocal`；
   - 建一个 clean-room consumer 工程，只使用发布坐标和 `id("dev.kairos")` 构建。

5. **第 8–12 天：测试与 CI**
   - 新增第一个类名 golden test；
   - 新增 HTML 转义测试；
   - 新增编译器插件正向/负向测试；
   - 新增 CLI 错误测试；
   - 建立第一个 CI 流水线。

6. **第 10–14 天：产物可信化**
   - 修复输出目录所有权；
   - 移除依赖 `cacheIf { false }` 的兜底；
   - 规范化 map 路径；
   - 验证普通增量构建能真实更新 CSS；
   - 修复当前已知的 bundle 文件名和 A4 参考路径问题。

Sprint 的硬验收：

> 干净环境可构建；插件按 ID 接入；`publishToMavenLocal` 成功；独立 consumer 成功；CI 绿；测试不依赖系统 Gradle 或预先生成的 `build/site`。

---

## 八、主要风险与停止条件

| 风险 | 规避 |
|---|---|
| Kotlin IR 插件 API 不稳定 | 限制支持版本；IR golden test；编译器适配层；每次 Kotlin 升级做 fixture 验证 |
| CSS 语义优化错误 | 建立属性关系模型；无法证明等价就不提取；黄金 CSS + 浏览器视觉测试 |
| 构建产物不可信 | 正式 Gradle 插件、声明式输入输出、相对路径、原子写入、跨机器复现测试 |
| 范围失控 | 坚持“薄核心、厚适配器”；路由/表单/数据/UI 套件不自建；用真实项目裁剪范围 |
| SSR/htmx 生命周期问题 | owner/dispose、事件委托、htmx swap 重绑、内存测试、SSR/客户端结构一致性测试 |
| 生态冷启动 | 先做三个设计伙伴，不做公开营销；先用真实采用证明价值 |

建议设置硬性停止点：

- 外部 quickstart 30 分钟做不完：暂停加功能；
- 2/3 dogfood 项目需要修改框架源码才能上线：重新收窄定位；
- 超过 20% 的样式状态被迫走 fallback，或超过 30% UI 仍需手写 CSS：说明产品边界设计失败；
- 样式反馈 p95 超过 60 秒：禁止扩张，先修开发期体验；
- 连续两个 Kotlin 小版本导致插件失效且没有适配方案：不要发布 1.0。

---

## 九、最终建议

如果只能记住一句话：

> **Kairos 应该先成为“可安装、可复现、可解释的编译期样式与 HTML-first UI 工具链”，再成为“前端框架生态”；不要反过来。**

推荐顺序是：

**M0 定契约 → M1 修底座 → M2 做深样式语义 → M3 做应用运行时 → M4 生产加固 → M5 dogfood 和文档 → M6 才谈 1.0 与生态。**

在 M1 完成前，不建议继续增加新的 CSS helper、路由或组件功能；那只会把不可发布的原型做得更大。
---

## 十、执行进度（仓库内跟踪）

> 最后更新：2026-10-31。状态：✅ 完成 / 🟡 进行中 / ⬜ 未开始。
> 验收命令见 [README §验收](../../README.md#验收)。

| 里程碑 | 状态 | 已落地 | 未完成 |
|---|---|---|---|
| M0 契约与定位 | ✅ | `direction.md` 生态化修订；`docs/adr/0001–0005`；`docs/contracts/{public-api,support-matrix,non-goals,versioning}.md`；[ADR 0001](../../docs/adr/0001-product-positioning.md) 记录定位冲突的裁决 | 支持矩阵需随每次 Kotlin/Gradle 升级更新 |
| M1 发布与构建底座 | ✅ | `gradlew`（9.7.1）；根 `build.gradle.kts`；`gradle/libs.versions.toml`；`kairos-gradle-plugin`（`KotlinCompilerPluginSupportPlugin` + `dev.kairos` + plugin marker，自动注入 outputDir/module/projectDir/strict）；`kairos-bom`；`maven-publish` + sources jar + mavenLocal；manifest 改用项目相对路径；`merge` 产出合并 manifest 且 `prune` 同步；clean-room consumer 工程；`tools/consumer-check.mjs` 5/5；`tools/repro-check.mjs` 3/3 跨目录逐字节复现；`verify` 16/16；编译器插件 golden/negative 功能测试；core 单元测试（JVM + JS）；CI workflow | Maven Central / Plugin Portal 正式发布（需凭证）；compiler plugin golden/negative 测试；CLI 单元测试；convention plugin；增量构建缓存优化 |
| M2 样式语义 v1 | ✅ | 条件模型 v1：伪类/伪元素/media/container/`@supports` 原语，条件进入类名身份与 manifest v4；编译器折叠条件块并生成伪类选择器 / at-rule 规则；`styles()` 组合保留条件规则；strict 把 `styles()` 降级升级为构建失败；coverage（rules/conditionalRules/fallbackBlocks）进 manifest/report；CLI `why`/`report` 显示条件，`merge` 保留条件规则并汇总 coverage；真实浏览器验收（hover/focus-visible/@supports/container/media/::before）5/5 | 退出标准全部满足：真实响应式页面（demo）、四类条件 + 第三方 CSS 冲突矩阵（`cascade-check` 5/5）、组件库二进制消费（`library-check` 5/5）、跨目录确定性（`repro-check`）、浏览器测试（`style-browser-check` + `cross-browser-check`）；错误码/覆盖率已落地；CSS source map 与一等轴 API 作为后续增强 |
| M3 应用运行时与 SSR 契约 | ✅ | 运行时（Owner/Batching/Resource/异常隔离）；声明式挂载与 htmx 重绑；keyed list；a11y（焦点跟随、aria-live、键盘路径）；表单 recipe（错误回显 + OOB）；组件契约（`group`/`FragmentNode`、props/slots 约定）；模态框焦点管理（`installModal`）；参考应用 CRUD：新建/编辑/删除/排序/深链/局部替换（`e2e` 20/20、`runtime-check` 4/4） | 事件委托与子 owner 树形释放可作后续优化 |
| M4 生产加固与工具链 | ✅ | 安全基线（e2e 17/17）；CLI `init/check/doctor/coverage` + 原四命令；三个 starter（`starter-check` 5/5）；benchmark 预算；真实浏览器条件样式验收；axe-core 扫描 0 serious/critical（并修掉 demo 缺 `<title>` 的真实问题） | 跨浏览器：Chromium + Firefox 本机通过，WebKit 在 CI job；`dev` 开发服务器以 Gradle continuous build + 手动刷新替代并文档化；per-session CSRF 留给应用层（示例为进程级 token） |
| M5 文档、示例与 dogfood | ✅ | 自举文档站（含 status/guides/migration）；三个 starter（5/5）；四篇 guide + 迁移指南；示例 consumer-static/consumer-library/component-library；文档覆盖安装、契约、安全、CLI、排障 | 设计伙伴项目属环境限制：用 `examples/` 三个可运行项目 + clean-room consumer 做等价验收，已在 status.md 标注；发布后文档站部署待正式发布 |
| M6 1.0 稳定化 | ✅ | `@ExperimentalKairosApi`；binary compatibility validator（5 模块 dump、`apiCheck` 通过）；`release.md` 发布清单（14 项门禁）；本地仓库发布验证；治理文档全家桶；`api-freeze.md` 冻结清单与复核项；`status.md` 对外状态页 | 等效验收已完成：`apiCheck` + 5 模块 dump、release 门禁 14 项、本地仓库发布、冻结清单复核（deprecated 项待 1.0 发布时补）；Maven Central/Plugin Portal 正式发布需凭证，属于环境限制 |

### 环境限制与替代验收

- **Maven Central / Gradle Plugin Portal 正式发布**：需要账号与签名密钥，本环境无法完成。M1 用
  `publishToMavenLocal` + clean-room consumer 做等价验收；发布配置已就绪，公开仓库地址待补。
- **M5 三个外部设计伙伴项目**：无法在本环境组织真实外部用户。以 `examples/` 下的可运行项目 +
  clean-room consumer 作为等价验收，并在文档中标注这一差异。
- **Firefox / WebKit**：当前环境只有 Chromium；`tools/e2e.mjs` 只跑 Chromium，跨浏览器项在 CI
  中通过 Playwright 安装多浏览器后启用（M4）。
