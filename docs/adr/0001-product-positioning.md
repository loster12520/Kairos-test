# ADR 0001：产品定位与目标用户

- 状态：已接受（M0 冻结，2026-10-31）
- 决策者：Kairos 维护者
- 上游：[direction.md](../../direction.md) §1–§2、[plan-1.0.md](../../.lignting/kairos/plan-1.0.md) §2–§3
- 相关：[ADR 0002 样式条件模型](0002-style-condition-model.md)、[非目标](../contracts/non-goals.md)、[支持矩阵](../contracts/support-matrix.md)、[公共 API](../contracts/public-api.md)

## 背景

Kairos 当前的真实形态是一个**单仓库编译器原型 + 两个展示应用**：`kairos-core` 的 `css {}` 在 K2 IR 阶段被折叠成类名，`kairos-compiler-plugin` 写出 `kairos.css` / `kairos.map.json` / `kairos-report.txt`，`kairos-htmx` 与 `kairos-browser` 分别覆盖服务端直出和浏览器端最小增强，`demo-web` 与 `blog-backend` 跑通了 SSR、htmx、Signal 和 CLI 的闭环。`README.md` 至今写着“仅为验证原型，不为可使用项目”。

但仓库里同时存在两套互相冲突的自我描述：

1. `direction.md` §1 明确写着受众默认只有作者、不做生态、不做向后兼容、不写迁移文档；
2. `.lignting/kairos/plan-1.0.md` §2 认为这些非目标与“相对完整的前端框架生态库”直接冲突，要求把定位改成“Kotlin-first、编译期样式驱动、HTML-first 的 UI 工具链”。

两者不能同时成立。若保留“只有作者”的前提，发行、治理、兼容与迁移工作都不必做；若目标是对外生态，就必须从第一个公开版本起承诺兼容。M0 的职责不是把功能做大，而是先选定一个能被后续所有文档引用的答案。

## 决策

### 1. 定位

Kairos 定位为：

> **Kotlin-first、编译期样式驱动、HTML-first 的 UI 工具链，优先服务 Kotlin/Ktor 服务端渲染团队；不是 React/Vue 的通用替代品。**

四个词各自的边界：

- **Kotlin-first**：公共 API 的宿主语言是 Kotlin，分发渠道是 Gradle / Maven / Kotlin 生态；不提供 TypeScript、JSX 或其他语言的一等入口。
- **编译期样式驱动**：样式块在编译期折叠为类名，运行时不计算样式值；这条来自 [direction.md](../../direction.md) §2-D，是产品差异化的根。
- **HTML-first**：服务端拥有 HTML，客户端只做类名、文本、属性和事件的最小增强；没有虚拟 DOM，也没有整树 diff。
- **优先 SSR 团队**：首批用户是已经用 Kotlin 写服务端、以 Ktor/htmx 或静态生成交付页面的团队，而不是 SPA 团队。

### 2. 身份与定位不是同一件事

`direction.md` §2-A 的身份是“**状态必须可穷举**”，它回答的是“Kairos 凭什么成立”；本 ADR 的定位回答的是“先服务谁、通过什么渠道交付”。两者兼容：

- 定位中的 Kotlin-first 不是把 Kotlin 当卖点，而是因为可穷举依赖 Kotlin 枚举的封闭性与 `when` 穷尽检查（[direction.md](../../direction.md) §2-C）；
- 因此“不做 Kotlin 生态”这条旧非目标与身份并不等价，可以撤销，而身份本身不变；
- 任何偏离可穷举约束的功能，即使对 Kotlin 用户很方便，也只能落在适配器或 recipe，不能进核心。

### 3. 目标用户与推进阶段

| 阶段 | 用户 | 进入条件 |
|---|---|---|
| 第一阶段 | 作者本人 | 当前状态，继续 dogfood |
| 第二阶段 | 1–3 个设计伙伴 | M1/M2 的安装与样式闭环成立，可只靠发布坐标构建 |
| 第三阶段 | 公开早期用户 | M5 的两个外部项目在预生产/生产运行，且未改框架源码 |

目标场景按优先级排列：内部管理后台、内容/营销站、已有 Ktor 应用的增量页面或设计系统。首批用户必须同时满足“用 Kotlin 写服务端”和“页面以 HTML 直出为主”两个条件。

明确不优先：SPA 优先的 React/Vue 团队、需要原生移动端的团队、不写 Kotlin 的团队、需要与现有 JSX 组件树混排的团队。

### 4. 承诺边界

- 从第一个公开版本开始执行 SemVer、弃用窗口与迁移说明，具体规则见 [versioning.md](../contracts/versioning.md)。
- 冲突裁决沿用 [direction.md](../../direction.md) §2-E：性能与安全优先于样板代码复杂度；不为了 DX 放宽可穷举约束。
- **静默降级与可观测性分开处理**：运行时仍保持“三档只有优化程度差异、没有行为差异”，默认不推送警告；构建期规划 `strict` 模式、覆盖率报告和 `why` 反查，供 CI 主动开启。这保留了 direction 的静默原则，也满足 plan 对 CI 门禁的要求；其中 `strict` 选项已接线但当前尚未在折叠失败路径生效，覆盖率报告仍未实现。
- 第一个公开版本之前使用 `0.x` 与 `@ExperimentalKairosApi` 管理预期；`1.0.0` 只在 plan §9/M6 的条件满足后发布。

### 5. 现状与上游差异（as-built）

- M0 开始时仓库没有 `group`、`version`、`maven-publish`、Gradle Wrapper 和 BOM，二进制不可发布；`README.md` 的原型声明仍然有效。本 ADR 写作期间 M1 底座已并行落地（`dev.kairos:0.1.0`、wrapper、`kairos-gradle-plugin`、`kairos-bom`），clean-room consumer 已验证只靠发布坐标 + 插件可构建；但尚未发布到公共仓库，CI 尚未在托管环境验证，跨机器确定性也未验收，不能据此宣称已经“可发布”。
- `direction.md` 尚未按本 ADR 修订，其 §1/§2-A 仍包含“受众只有作者”“不做 Kotlin 生态”等旧结论。按本轮写作边界，本 ADR 不直接改动该文件；后续修订必须由方向层完成，不能把本 ADR 当作已覆盖上游。
- 计划模块表里的 `kairos-style-contract`、`kairos-reactive`、`kairos-ktor`、`kairos-testing` 在当前仓库中仍不存在；`kairos-gradle-plugin` 与 `kairos-bom` 已随 M1 出现，但只是底座，不是完整的生态承诺。当前模块与目标模块的映射见 [public-api.md](../contracts/public-api.md) 的模块表。
- `kairos-core` 目前包含 Markdown 渲染器，而 plan §4 明确要求 Markdown 不进核心；这是需要迁移的现实差异，见 [non-goals.md](../contracts/non-goals.md)。

## 后果

### 正面

- 后续所有文档可以用同一条定位做取舍：新功能必须能回答“属于核心、适配器、recipe，还是暂不做”（plan M0 退出标准）。
- 支持矩阵、版本契约、非目标清单有了共同前提；不需要在每个文档里重复论证“要不要做生态”。
- 对外沟通可以明确“不是 React/Vue 替代”，减少错误预期与无效比较。

### 代价与风险

- 一旦对外承诺，就不能再随意推翻类名算法、公共 API 与 manifest；M1 的工程化成本会上升。
- `direction.md` 与新定位并存期间，文档读者可能先读到旧结论；需要在方向层完成修订前，把本 ADR 标注为当前对外契约。
- 定位收窄到 Kotlin/Ktor SSR 会放弃一部分潜在用户；这是有意为之，避免做成“通用框架的半成品”。
- 生态冷启动仍是最大风险：定位不会自动带来用户，M5 的设计伙伴计划才是验证手段。

## 替代方案

1. **保持纯自用内部框架**：工程成本最低，但 plan §2 已判定它与生态目标冲突；若选它，本目录的发布、兼容、迁移文档大部分可以删掉。
2. **做通用（非 Kotlin 专属）前端框架**：会失去枚举封闭性这一机制基础，也与现有 Kotlin API 不匹配。
3. **追求 React/Vue 功能对齐**：会直接违反 [non-goals.md](../contracts/non-goals.md)，并把团队拖进路由、表单、组件市场的长期军备竞赛。
4. **做完整全栈 Kotlin 框架**：把数据库、ORM、认证、部署都纳入核心，违背“薄核心、厚适配器”，且与首个版本的交付能力不匹配。

## 参考

- [plan-1.0.md §2 战略修正](../../.lignting/kairos/plan-1.0.md)
- [plan-1.0.md §3 什么算相对完善](../../.lignting/kairos/plan-1.0.md)
- [direction.md §2 关键决策记录](../../direction.md)
- [README.md 已知限制](../../README.md)
