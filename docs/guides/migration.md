# 迁移指南

本页记录跨版本升级时的破坏性变化与操作步骤。当前版本 `0.1.0`，1.0 之后按 SemVer 执行弃用窗口。

## 从 0.0.x（手工接线原型）到 0.1.0

### 1. 接入方式：删掉手工编译器插件配置

**旧**：`build.gradle.kts` 里手写 `-Xplugin`、jar 路径和 `-P plugin:dev.kairos:*` 参数。

**新**：

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
    id("dev.kairos") version "0.1.0"
}
dependencies { implementation("dev.kairos:kairos-core:0.1.0") }
```

插件会自动注入编译器参数、声明每 compilation 输出目录。删掉所有 `-Xplugin` 配置。

### 2. 产物路径

样式产物从 `build/kairos` 改为 `build/kairos/<target>/<compilation>`（纯 JVM 工程是 `build/kairos/jvm/main`）。读取 `kairos.css` / `kairos.map.json` 的脚本、路由、部署配置都要改。

### 3. manifest 从 v3 升到 v4

- 每条规则新增 `conditions`（`{kind, value}` 数组）；
- 顶层新增 `coverage`（rules / conditionalRules / fallbackBlocks）；
- `merge` 产物新增 `modules`，`prune` 会同步重写 rules 与 coverage；
- 旧 CLI 仍能读 v3，新 CLI 写 v4。

### 4. 条件样式改变"一个块一个类"

`css { }` 现在返回**空格分隔的类名列表**（base 组 + 每个条件组一个类）。直接赋给 `style = ...` 即可；原来的单类名假设（例如手写 `"class=\"" + css{...} + "\""`）需要改成拼接完整返回值。

### 5. 块内条件值的类名变了（轴模型）

旧版把块内 `when` 的**每种组合**折叠成一个类；现在按属性拆成 arm 类（k 个二元条件 = 1+2k 个类）。语义不变，但**类名与类数量会变**，因此:

- 快照测试需要更新；
- 不要在任何地方硬编码旧类名；
- manifest 的 rule 数量会变多，属预期。

### 6. 其他

- 组件库样式资产：库 jar 内新增 `META-INF/kairos/**`；消费方插件新增 `kairosCollectStyles` / `kairosMergeStyles` 任务。
- 运行时新增 `Owner` / `Batching` / `Resource`；`bindChildren` 仍在，但列表建议改用 `bindKeyedChildren`。
- `classNameFor`、`StyleRule`、`MarkdownStyles` 标记为 `@ExperimentalKairosApi`。

## 0.1.0 → 1.0（规划中）

1.0 前还会做一次 API freeze 复核；届时本页补充：

- 被移除或改名的 API 列表与替代品；
- 类名算法（若有变化）的新旧映射；
- manifest schema 版本与 CLI 兼容矩阵；
- 升级命令与回滚方式。
