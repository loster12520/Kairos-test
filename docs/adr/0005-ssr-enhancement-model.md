# ADR 0005：SSR 与客户端增强模型

- 状态：已接受（M0 冻结，2026-10-31）
- 决策者：Kairos 维护者
- 上游：[plan-1.0.md](../../.lignting/kairos/plan-1.0.md) M3、[direction.md](../../direction.md) §3
- 相关：[ADR 0002 条件模型](0002-style-condition-model.md)、[ADR 0004 Gradle 插件与 variant](0004-gradle-plugin-artifact-variant.md)、[公共 API](../contracts/public-api.md)

## 背景

Kairos 服务端与浏览器端共享同一个节点模型与序列化器：`renderToHtml` / `renderFragment` 在 `commonMain`，浏览器端 `toDom` 用 DOM API 构造同一棵树。这是“SSR 与客户端结构一致”的现有基础。

但**增强模型目前不存在**，只有手工绑定：

- `kairos-htmx.page()` 输出整页，`fragment()` 输出片段；HX-Request 分流写在应用路由里（blog-backend 的 `/posts/{id}`），不在框架里；
- `data-kairos-id` 是各应用自己用 `attribute("data-kairos-id")` 声明的约定，不在 `kairos-core` 中；
- **2026-10-31 已落地**：`kairos-browser` 提供 `onMount(name, handler)` 注册表、`mount(root)` / `autoMount()`、`unmount`、`pruneDetached`、`mountedCount()`；元素用 `data-kairos-mount="<name>"` 声明行为，`data-kairos-mounted` 保证幂等；`autoMount()` 在 `htmx:afterSwap` 后重扫、`htmx:beforeSwap` 后清理已脱离文档的 owner。
- 每个挂载点有独立 `Owner`：`scope.effect` / `scope.computed` 创建的订阅在该节点被替换时统一 dispose；blog 前端已迁移到这套机制，原来的 `revision` signal 已删除。
- `bindKeyedChildren` 提供 keyed list：按 `data-kairos-key` 复用节点、移动、增删，不再全量重建。
- 旧的 `bindChildren`（全量重建）、`mountInto`（整块替换）、`setHtml`（写 innerHTML）保留，但它们**不是** SSR 增强路径。

plan M3 要求：服务端输出稳定的 `data-kairos-*` 挂载点，客户端提供 `mount(root)` 或等价扫描机制，htmx swap 后重绑而不是要求用户手工 `byId`，并明确 DOM 一致性、状态交接、焦点恢复规则。这份 ADR 冻结这些行为。

## 决策

### 1. 职责边界

- **服务端拥有 HTML**：页面结构、文本、类名、表单值、htmx 属性都由服务端渲染；增强不重写结构。
- **客户端只做增强**：读挂载点、绑事件、写类名/文本/属性、管理 effect 生命周期；不计算样式值，不做整树 diff，不接管路由。
- **SSR 路径不调用客户端渲染**：`mountInto` 是“客户端自行渲染一棵新树”的独立能力，不属于 SSR 增强路径；在 SSR 页面里使用它意味着放弃服务端结构，必须显式说明。
- 没有虚拟 DOM；如果未来需要局部重排，走 keyed list（M3），不是整树 patch。

### 2. 挂载点契约

`data-kairos-` 前缀保留给框架，应用不得用该前缀表达业务数据。当前只冻结一个：

- **`data-kairos-id`**：增强挂载点的稳定标识，服务端与客户端使用同一个值；
- 在同一个挂载根内必须唯一；跨 swap 必须稳定，不能用随机数、时间戳或数据行内容生成；
- 不使用 `id` 属性承担挂载职责（`id` 用于锚点、label、htmx target，语义不同）；
- 服务端序列化时按普通属性转义，不允许出现会截断选择器的字符；
- 当前 `data-kairos-id` 由各应用自行声明；M3 把它提升为 `kairos-core` 的一等属性委托，应用不再重复声明。

v1 **不把状态序列化进 DOM**（不写 JSON blob、不写 `data-kairos-state`）。增强需要的初始状态要么由服务端再次提供，要么由客户端从表单/页面读取；引入状态交接时必须另行定义转义、版本与安全边界。

### 3. `mount(root)` 行为契约

目标 API 形如 `mount(root): Disposable`（具体名称以 M3 公共 API 契约为准），行为固定：

- 扫描 `root` 及其子树中的 `[data-kairos-id]`，按注册表绑定行为；
- **幂等**：对已挂载的节点再次调用不得重复添加监听器或 effect；
- **不替换节点**：只在现有 DOM 上绑定与写入；需要替换内容时必须走独立 API；
- 返回的句柄负责释放该次挂载创建的监听器、effect 与内部索引；
- 单个挂载点绑定失败不得中断其他挂载点；错误应可见（console + 开发期诊断），不能静默吞掉；
- 绑定顺序按 DOM 顺序，保证同一输入下行为确定。

### 4. htmx swap 重绑

- 框架监听 `htmx:afterSwap`（以及应用显式调用 `mount` 的场景），对**被替换的子树**重新扫描并绑定新挂载点，而不是要求用户在每个页面手工 `byId`；
- 重绑前先处理旧节点：释放其归属的绑定与 effect；已被移除的节点不得继续持有监听器或 signal 订阅；
- 对局部 swap（`innerHTML`/`outerHTML`/`beforeend` 等）只扫描受影响区域；整页级 swap 才回退到全文档扫描；
- 同一节点被连续 swap 时，旧绑定必须先释放再新建，不能叠加；
- 焦点规则：被替换的焦点元素若在新片段中有同 `data-kairos-id`/`id` 的对应节点，应恢复焦点（M3 实现）；否则焦点回到合理的容器或触发元素；`aria-live` 区域用于播报异步结果（M4 基线）；
- htmx 的 `hx-preserve` 等机制不被框架改写；应用需要保留节点时按 htmx 语义处理。

当前 blog 的 `revision` signal 是这一契约的临时替代：它只能让筛选 effect 重跑，不能恢复事件绑定。M3 落地后应删除该手工模式，或把它降级为应用自己的补充逻辑。

### 5. DOM 一致性契约

- 服务端 `renderToHtml` / `renderFragment` 与客户端 `toDom` 必须从同一个 `Node` 树产出**结构等价**的 DOM：元素、属性、文本、顺序一致；
- “等价”不要求 HTML 字符串与 `outerHTML` 逐字节相等；HTML 解析会规范化大小写、实体与空元素，比较应按结构；
- 类名列表的顺序不构成契约，按集合比较；
- 服务端只能输出 `data-kairos-*` 这一类框架属性；不得输出只在服务端存在、客户端无法解释的结构标记；
- 文本与属性转义由序列化器统一负责；客户端 `toDom` 使用 DOM API，不拼接 HTML 字符串；
- `page()` 与 `fragment()` 只决定是否带 doctype/框架外壳，不改变节点树语义。

### 6. 安全边界

- `setHtml(element, html)` 是裸 `innerHTML`，属于**显式 raw HTML 入口**；调用方必须保证输入可信。M4 之前不得把它包装成“方便 API”推广；
- URL 协议白名单、CSP nonce、CSRF 方案不属于本 ADR，但 `page()` 的 `scripts`/`headContent` 必须为 M4 的 nonce 注入留出位置；
- 挂载点属性值来自服务端模板，必须由模板层转义；不得把用户输入直接拼进 `data-kairos-id`。

### 7. 当前实现与目标差距

| 能力 | 当前（0.1.0，2026-10-31） | M3 剩余目标 |
|---|---|---|
| 挂载点 | 各应用自行声明 `data-kairos-id` | core 提供稳定属性与唯一性约定 |
| 挂载 API | ✅ `onMount` + `mount`/`autoMount` + owner | `data-kairos-id` 提升为一等属性委托 |
| htmx 重绑 | ✅ 框架级 beforeSwap 清理 + afterSwap 重扫 | 焦点恢复规则与测试 |
| 列表更新 | ✅ `bindKeyedChildren`（keyed 复用） | 子组件 owner 的树形释放（当前每个挂载点一个 owner） |
| 生命周期 | effect 句柄未统一释放 | 挂载句柄统一 dispose |
| DOM 一致性 | 共享节点模型与序列化器 | 结构等价验收测试 |
| 焦点/播报 | 无 | M3/M4 基线 |
| 列表更新 | `bindChildren` 全量重建 | keyed list |

## 后果

- 正面：增强从“每个页面手写 byId”变成可复用契约；htmx swap 不再依赖用户记忆；SSR 与客户端结构有可测试的一致性定义；effect 泄漏有明确责任方。
- 代价：需要引入注册表、挂载句柄与重绑逻辑；局部 swap 的事件来源与边界需要适配；焦点恢复是浏览器行为，跨引擎差异要测。
- 风险：重复挂载与旧节点泄漏是最可能的缺陷；htmx 版本差异可能改变事件时序；`data-kairos-id` 被应用滥用为业务数据会导致契约失效；整页扫描在大型 DOM 上可能变慢，需要按 swap 范围优化。

## 替代方案

1. **完整客户端 hydration / 虚拟 DOM**：能处理任意状态，但与 HTML-first、零运行时样式计算的目标冲突；否决。
2. **保持手工 `byId` 作为唯一方式**：实现最少，但每个页面都要重复接线，htmx swap 后必然漏绑；否决作为默认路径。
3. **morphdom 式 DOM diff**：能保留节点状态，但引入整树 diff 与“服务端/客户端结构必须完全一致”的额外负担，且不是本版本目标；暂不采用。
4. **纯客户端渲染替代 SSR**：直接放弃服务端 HTML 与 htmx 分工；否决。

## 参考

- [plan-1.0.md M3 应用运行时与 SSR 契约](../../.lignting/kairos/plan-1.0.md)
- [README.md 两个可运行示例](../../README.md)
- [ADR 0002：样式条件模型](0002-style-condition-model.md)
- [ADR 0004：Gradle 插件与 artifact variant](0004-gradle-plugin-artifact-variant.md)
