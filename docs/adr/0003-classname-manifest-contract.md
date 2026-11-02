# ADR 0003：类名与 manifest 版本契约

- 状态：已接受（M0 冻结，2026-10-31）
- 决策者：Kairos 维护者
- 上游：[plan-1.0.md](../../.lignting/kairos/plan-1.0.md) M1/M2、[direction.md](../../direction.md) §4
- 相关：[ADR 0002 条件模型](0002-style-condition-model.md)、[ADR 0004 Gradle 插件与 variant](0004-gradle-plugin-artifact-variant.md)、[versioning.md](../contracts/versioning.md)

## 背景

Kairos 的产物不只给人看，还被四个消费方共同依赖：

1. 编译器写出的 CSS 与 manifest；
2. 运行时 fallback 用同一个 `classNameFor` 生成类名；
3. `kairos-cli` 的 `why` / `report` / `merge` / `prune` 按 manifest 工作；
4. 用户可能把类名写进测试、选择器、视觉回归基线。

只要其中任意两方对“同一个类名代表什么”理解不一致，构建就会静默产出错页面。当前实现已经有确定性的雏形（声明按属性排序、规则按类名排序、无时间戳、原子写入），并且在 M1 底座中把 manifest 的源码路径相对化了；但仍有两个硬伤：跨机器/跨目录的逐字节一致还没有正式验收；`merge` 只产出 CSS，不产出合并后的 manifest，导致合并产物不可解释。plan M1 把“跨目录逐字节一致”和“merge 后同时产出合并 map”列为退出标准，本 ADR 把相关规则冻结成契约。

## 决策

### 1. 产物集合与命名

每个 compilation 的输出目录固定包含三个文件：

| 文件 | 作用 | 是否包含源码路径 |
|---|---|---|
| `kairos.css` | 分层样式表 | 否 |
| `kairos.map.json` | 类名、声明、来源、冲突、错误、备注 | 是（必须相对化） |
| `kairos-report.txt` | 人类可读汇总 | 是（错误/冲突行使用相对路径） |

文件名与角色不得随意更改；Gradle 任务、CLI、文档与第三方样式资产都按这三个名字发现产物。

### 2. 类名算法 v1（冻结）

当前算法：

```text
canonical = declarations
  .sortedBy { property }
  .joinToString(";") { "$property:$value" }
className = "k" + layer.letter + fnv1a64Hex(layer.cssName + "#" + canonical).take(12)
```

固定细节：

- 层字母：`kairos.reset → r`、`kairos.base → b`、`kairos.variant → v`、`kairos.override → o`；
- 哈希：FNV-1a 64 位，输入为 UTF-16 字符逐位异或；输出 16 位小写十六进制，取**前 12 位**（48 位有效）；
- 类名正则：`k[robv][0-9a-f]{12}`；CLI 的剪枝与校验都依赖这个形状；
- 声明在进入哈希前必须去重、去首尾空白；同一个块内同一属性重复出现时，后者覆盖前者（`StyleScope.declarations` 是 `LinkedHashMap`）。编译器与运行时路径都会做这个清理；直接调用 `classNameFor` 的低层代码必须自己保证声明已规范化；
- **类列表的顺序不是契约**：DOM 上 `class` 属性的排列顺序不影响 CSS 语义，运行时 fallback 与编译器可以产生不同顺序，但集合必须一致。编译器当前输出按类名排序，是为了构建产物确定性，不是给用户比较字符串用的。

**碰撞处理**：编译器一次性收集同一 compilation 的类；若同一个类名对应不同层或不同声明集合，必须报错并停止构建。FNV-1a 不是密码学哈希，48 位截断存在碰撞可能，因此“碰撞即构建失败”是硬约束，不能降级为警告。运行时 fallback 没有全局视图，不承担碰撞检测职责。

### 3. 类名算法的升级规则

以下任一变化都视为**样式契约主版本升级**，不能以“优化”为名静默发布：

- 更换哈希函数、位宽或截断长度；
- 改变声明规范化（排序键、分隔符、空白处理、值大小写）；
- 改变层集合、层名或层字母；
- 把新的输入（条件上下文、属性关系、轴标识等）纳入哈希前像；
- 改变 `styles()` 组合后的类集合规则。

升级要求：

1. 新增 ADR 记录动机与迁移方式；
2. manifest schema 版本递增，并提供算法版本字段或等价指纹，使 `merge` 能拒绝跨算法合并；
3. 提供新旧类名映射与迁移说明；`why` 至少能解释旧类名在新版本下对应什么；
4. 在 1.0 之后，类名变化按 SemVer 主版本处理；1.0 之前允许在 `0.x` 的次版本内完成，但必须写迁移说明。

### 4. manifest schema v4（2026-10-31 起冻结）

M2 条件模型需要把条件写进 manifest，同时暴露覆盖率，schema 从 v3 升到 v4（旧 CLI 仍接受 v3/v4，见兼容规则）。

| 字段 | 类型 | 语义 |
|---|---|---|
| `version` | int | schema 版本，当前为 `4`；CLI 接受 `3`（旧产物）与 `4` |
| `module` | string | 逻辑模块名，来自插件选项 `module`，用于错误信息与 merge 诊断 |
| `layers` | string[] | 固定为四个层名，顺序即优先级；merge 要求完全一致 |
| `coverage` | object | `{rules, conditionalRules, fallbackBlocks}`；`fallbackBlocks` 是 `styles()` 无法折叠、留给运行时拼接的次数，strict 模式下为 0 |
| `rules` | object[] | 按 `className` 升序；每条含 `className`、`layer`、`layerIndex`、`conditions`、`declarations`、`sources` |
| `conditions` | object[] | 元素为 `{kind, value}`；`kind` ∈ `pseudoClass` / `pseudoElement` / `media` / `container` / `supports`；v3 读取时视为空数组 |
| `declarations` | object[] | 元素为 `{property, value}`，按属性升序 |
| `sources` | object[] | 元素为 `{file, line, column, offset}`，按 file/line/column 升序；`file` 必须是相对路径 |
| `conflicts` | object[] | 跨层同属性冲突；含胜负层、胜负值、可选 `where` |
| `errors` | string[] | 构建期收集的错误文本；非空意味着该模块不应被 merge |
| `notes` | string[] | 诊断备注，允许为空 |

`merge` 产出的合并 manifest 额外带 `modules` 数组，并保留 `coverage` 汇总；`prune` 会按实际保留的规则重写 `rules` 与 `coverage`。

兼容规则：

- **新增可选字段属于兼容变更**，不需要 bump `version`；消费者必须忽略未知字段（CLI 的 JSON 解析已开启 `ignoreUnknownKeys`）；
- **删除字段、重命名字段、改变字段语义或类型属于破坏变更**，必须 bump schema 版本；旧 CLI 必须拒绝未知版本，而不是猜测；
- `sources[].file` 的相对化属于 schema 内语义修正，但会改变字节输出，因此必须和确定性验收一起发布；
- `errors` 非空时，`merge` 必须拒绝该模块；这是当前 CLI 的行为，必须保留。

### 5. 确定性与相对路径

**目标契约**：同一份源码、同一组依赖、同一个算法版本，在不同目录、不同机器上构建，`kairos.css`、`kairos.map.json`、`kairos-report.txt` 必须逐字节一致。

达成条件：

- 所有源码路径转为**相对于模块 Gradle 项目目录的 POSIX 相对路径**（`projectDir` 选项；Gradle 插件默认传模块项目目录），分隔符统一为 `/`，禁止出现绝对路径、盘符和反斜杠；
- 输出中不得出现时间戳、随机 ID、主机名、绝对路径、迭代顺序不稳定的容器；
- CSS：层顺序固定，层内规则按类名升序，声明按属性升序；
- manifest：规则按类名升序，声明按属性升序，来源按 file/line/column 升序；
- report：章节顺序固定，错误与冲突排序稳定；
- 写入使用临时文件 + 原子移动，避免半写文件被下游读到。

**当前状态**：同一台机器上的全量重建可以做到 CSS/manifest 逐字节一致（M1 迁移后 `tools/verify.mjs --rebuild` 14/14，其中 A12 覆盖两次强制构建的一致性）。M1 底座已经给编译器插件加了 `projectDir` 选项，`KairosCollector` 会把 `sources[].file` 相对化到该目录并统一 `/` 分隔符；由 `dev.kairos` Gradle 插件驱动的构建因此不再写入绝对路径，clean-room consumer 的 manifest 检查也验证了 `src/...` 相对路径。但跨目录/跨机器的“逐字节一致”尚未由 clean-room consumer 与确定性验收正式证明，本文不把它写成“已支持”。手工 `-Xplugin` 接线如果不传 `projectDir`，仍会退化为绝对路径。

### 6. merge 与 prune 契约

- `merge` 只接受 schema 版本相同、`layers` 相同的 manifest；类名相同但声明不同视为错误；类名相同的规则合并来源而不是覆盖声明；
- `merge` 必须在 M1 之后同时产出**合并后的 manifest**，否则合并结果无法被 `why`/`report` 解释；当前实现只写 CSS，这是已记录的差距；
- `prune` 依赖“类名以字面量出现在最终产物中”的假设；运行时拼接的类名可能被误删。契约要求：剪枝默认保守，无法证明安全时保留规则；对动态类名，用户必须通过白名单或关闭剪枝来显式承担风险；
- 剪枝不得改变剩余规则的层与声明内容，只允许整条删除；
- 这两个命令当前是 CLI 行为，M1 起由 Gradle 插件调用，但语义不变。

### 7. 现状与契约差距

| 契约要求 | 当前（2026-10-31） | 后续动作 |
|---|---|---|
| 跨机器逐字节一致 | 同机一致；插件构建已相对化路径，跨机器尚未验收 | 用两个不同绝对路径构建并比对产物 |
| merge 后产出合并 map | 只产出 CSS | M1/M2 增加合并 manifest |
| 算法版本可检测 | 无版本字段 | 下一次 schema 变更时加入 |
| 类名形状稳定 | 已冻结为 v1 | 由 golden test 锁定 |
| 碰撞即失败 | 编译器已实现 | 保持，补测试 |
| prune 安全 | 正则 + 字面量假设 | 保守化 + 白名单，M2/M4 |

## 后果

- 正面：运行时、编译器、CLI、第三方库共享同一套类名与 manifest 语义；跨机器缓存与差分才有可能；错误可解释。
- 代价：路径相对化与合并 manifest 需要改动插件与 CLI；类名算法一旦冻结，任何改进都要走版本升级流程。
- 风险：相对路径的基准目录选择不当，会让 `why` 找不到源码；剪枝与动态类名的冲突会在真实项目中暴露；manifest 可能泄露源码结构，因此更要用相对路径。

## 替代方案

1. **把类名改成递增序号**：产物更短，但跨编译、跨模块、缓存全部失效；否决。
2. **用完整 SHA-256**：碰撞概率更低，但类名过长；当前用 48 位 + 构建期碰撞检测，权衡可接受。
3. **manifest 继续保留绝对路径**：本机调试最方便，但直接违反 plan M1 的可复现目标；否决。
4. **merge 只产 CSS、靠源码重新生成解释**：合并后的产物无法独立解释，第三方样式资产也拿不到来源；否决。

## 参考

- [plan-1.0.md M1 发布与构建底座](../../.lignting/kairos/plan-1.0.md)
- [direction.md §4 不可逆的架构契约](../../direction.md)
- [README.md 已知限制](../../README.md)
- [versioning.md](../contracts/versioning.md)
