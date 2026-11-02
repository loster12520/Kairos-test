# Kairos 版本契约

- 状态：M0 冻结（2026-10-31），与 M1 底座同步
- 相关：[public-api.md](public-api.md)、[support-matrix.md](support-matrix.md)、[ADR 0003](../adr/0003-classname-manifest-contract.md)

## 1. 版本对象

Kairos 的“版本”不止一个，必须分开管理：

| 对象 | 版本形式 | 当前值 | 谁消费 |
|---|---|---|---|
| 框架构件（core/browser/htmx/cli/compiler-plugin/gradle-plugin/BOM） | SemVer | `0.1.0`（M1 底座，未公开发布） | Gradle/Maven 用户 |
| Gradle 插件 marker | 与框架构件同版本 | `dev.kairos` 0.1.0 | `plugins {}` 解析 |
| manifest schema | 整数 | `3` | CLI、合并/剪枝、第三方工具 |
| 类名/样式契约 | 独立主版本 | v1（冻结于 [ADR 0003](../adr/0003-classname-manifest-contract.md)） | 运行时、编译器、CSS、用户测试 |
| 公共 API 稳定性 | 注解 + 文档标签 | 尚无 `@ExperimentalKairosApi` 注解 | Kotlin 调用方 |
| Kotlin 编译器兼容 | 与 Kairos 版本绑定 | Kotlin 2.2.20 | 编译器插件 |

**发布时所有框架构件使用同一个版本号**，由 `kairos-bom` 对齐；不允许 core 0.3.0 配 gradle-plugin 0.2.0 这种组合进入官方支持。

## 2. SemVer 规则

### 2.1 `1.0.0` 之后

- **MAJOR**：删除/重命名公共 API、改变公共 API 语义、改变类名算法或 manifest 不兼容变更、移除已弃用项；
- **MINOR**：新增向后兼容的 API、新增可选 manifest 字段、新增 recipe/适配器、标记弃用；
- **PATCH**：只修 bug、文档、内部实现；不得改变合法输入的类名、CSS 语义或 manifest 语义。

### 2.2 `0.x` 阶段（当前）

`0.x` 允许在不发 MAJOR 的情况下调整 API，但不等于可以随意破坏：

- 对 `stable` 标签的公共 API：至少先在一个已发布次版本中标记 `@Deprecated(WARNING)`，给出替代写法与迁移说明，才允许在后续次版本移除；
- 对 `experimental` API：可在次版本直接变更，但 release notes 必须列出变化；能提供迁移映射的必须提供；
- PATCH 仍然不允许破坏：不得在补丁版本里改动类名算法、manifest 字段语义或已记录的行为；
- 每个 `0.x` 次版本发布前必须更新 [public-api.md](public-api.md) 与 [support-matrix.md](support-matrix.md)。

## 3. 弃用窗口

| 阶段 | 弃用方式 | 最早移除 |
|---|---|---|
| `0.x` | `@Deprecated(level = WARNING)` + 迁移说明 | 下一个已发布次版本 |
| `1.x` | `@Deprecated(level = WARNING)` + 迁移说明 | 下一个 MAJOR；且距弃用至少 6 个月、至少两个次版本 |
| 安全修复 | 可直接移除或禁用，release notes 说明 | 不受窗口限制 |

弃用要求：

- `@Deprecated` 的 `message` 必须给出替代 API 或明确说明“无替代”；
- 至少在移除前一个版本把级别提升到 `ERROR`，让用户构建期发现；
- 迁移说明放在 release notes 或 migration guide，不能只写在 commit 里；
- 被弃用的 API 在移除前仍需保持行为正确，不能“软废弃”。

## 4. 实验性 API

- 从 M1 首次发布起，`experimental` 条目应标注 `@ExperimentalKairosApi`（`@RequiresOptIn`），调用方必须显式 opt-in；
- 当前仓库**还没有**这个注解，[public-api.md](public-api.md) 的标签先作为契约分类；M1 发布时必须补齐；
- 实验性 API 可以在次版本变更或移除，但必须遵循第 2.2 节的 release notes 要求；
- 1.0 前对每个实验性 API 做一次毕业评审：升级为 `stable`、继续实验、或移除；
- 实验性不等于“内部”：它是对外可见、允许用户使用的，只是不承诺长期形态。

## 5. manifest 与样式契约

### 5.1 manifest schema

- 当前 `version = 3`；CLI 只接受 3，其他版本报错退出；
- **兼容变更**（新增可选字段、备注字段）不 bump；消费者必须忽略未知字段（CLI 已用 `ignoreUnknownKeys`）；
- **破坏变更**（删字段、改字段语义/类型、改排序或路径语义）必须 bump，并提供迁移工具；
- 生产者可以在同一 schema 下修正明显 bug，但不得改变既有字段的含义；
- `errors` 非空时 `merge` 必须拒绝（当前行为），不得把带错误的模块合并进产物。

### 5.2 类名/样式契约

- 类名算法 v1 冻结；任何可能改变“同一份源码的类名”的修改都属于**样式契约 MAJOR**（见 [ADR 0003](../adr/0003-classname-manifest-contract.md) §3）；
- `0.x` 内可以发生一次算法升级，但必须：写 ADR、bump manifest schema、提供新旧映射、发布迁移说明；
- `1.0` 之后，算法升级只能跟随 MAJOR 版本；
- `merge` 必须拒绝 schema 版本不同、层集合不同、或算法指纹不同的 manifest；当前已实现前两项，算法指纹仍未落地；
- 生成物（CSS/manifest）不单独版本化，它们的兼容性由 manifest schema 与样式契约决定。

## 6. 编译器插件兼容

- 每个 Kairos 版本只支持 [support-matrix.md](support-matrix.md) 中列出的 Kotlin 版本；首个公开版本是 Kotlin 2.2.20；
- 编译器插件与 Gradle 插件、运行时同版本；Gradle 插件通过 `SubpluginArtifact` 解析对应编译器插件，禁止手工混搭；
- Kotlin 小版本升级可能改变 IR 形状；升级必须跑 golden/negative fixture，必要时发布新的 Kairos 次版本，而不是让旧插件“试试看”；
- 不支持的 Kotlin 版本应在构建期给出明确错误；当前检测能力有限，文档与 release notes 是主要防线。

## 7. 生成物与消费方兼容

- CSS + manifest 是**一起发布、一起消费**的；不允许只拷 CSS 不看 manifest（`why`/`merge`/`prune` 会失真）；
- 应用从旧版本升级时，必须重新生成所有模块的样式资产；跨版本手工拼接 CSS 不在支持范围；
- 第三方组件库的样式资产必须与自身 Kairos 版本匹配；消费方插件拒绝 manifest 版本/层不匹配的资产；
- 剪枝后的 CSS 不具备可再合并性：剪枝丢掉的规则无法从 CSS 本身恢复，只能回到未剪枝产物；
- `kairos.css` 不包含来源路径；`kairos.map.json` 包含相对源码路径，属于构建产物，不是运行时资源。

## 8. 升级验证与发布门禁

- **迁移 fixture**：从 M6 起，至少覆盖前两个次版本的升级路径；fixture 不修改框架源码即可升级；
- **clean-room consumer**：空工程只加插件与依赖，能构建页面与 CSS；M1 的 `examples/consumer-static` 已通过 5/5；CI workflow 已建立（`.github/workflows/ci.yml`），托管环境结果尚未验证；
- **确定性**：跨目录/跨机器构建逐字节一致；`tools/verify.mjs --rebuild` 覆盖同机确定性（M1 迁移后 14/14），跨机器验收尚未完成；
- **二进制兼容**：`explicitApi()` 与二进制兼容检查在 M1 启用；公共 API 删除/改签名必须被工具拦住；
- 发布前检查：版本号一致、BOM 约束完整、plugin marker 可解析、支持矩阵已更新、release notes 包含弃用与迁移项。

## 9. 发布节奏

- `0.x` 阶段不承诺固定日历节奏；按里程碑与安全/正确性问题发布；
- 每次发布会同时更新：构件、BOM、插件 marker、公共 API 清单、支持矩阵、release notes；
- `1.0.0` 只在 plan M6 的条件满足后发布，不因“版本号好看”提前；
- 版本化文档与长期支持（LTS）策略属于 M6，不在 M0 冻结范围内。

## 10. 一页速查

| 改动 | 版本动作 |
|---|---|
| 新增可选 manifest 字段 | MINOR（schema 不变） |
| 删除/重命名字段 | MAJOR + schema bump |
| 新增 API | MINOR |
| 删除 `stable` API | 0.x：弃用一个次版本后；1.x：MAJOR |
| 改类名算法 | 样式契约 MAJOR + schema bump + 迁移说明 |
| 修 bug 且类名不变 | PATCH |
| 修 bug 导致类名变化 | 样式契约 MAJOR，不能当 PATCH |
| 升级支持的 Kotlin | 新的 Kairos 次版本，不宣称向后兼容旧 Kotlin |
| 扩浏览器矩阵 | 先 CI，再 MINOR 更新矩阵 |
